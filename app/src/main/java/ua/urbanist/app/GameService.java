package ua.urbanist.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import ua.urbanist.core.AssetSource;
import ua.urbanist.core.Discovery;
import ua.urbanist.core.Game;
import ua.urbanist.core.NetInfo;
import ua.urbanist.core.WebServer;

/** Фонова служба: тримає ігровий HTTP-сервер, поки телефон є господарем кімнати. */
public class GameService extends Service {
    public static volatile boolean running = false;
    public static volatile int port = 0;
    public static volatile String error = null;
    public static volatile List<String> urls = new ArrayList<String>();

    private static final String CHANNEL = "tu_server";
    private static final int NOTIF_ID = 1;

    private WebServer server;
    private Discovery.Responder responder;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tu:server");
            wakeLock.acquire();
        } catch (Exception ignored) {
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "tu:wifi");
            wifiLock.acquire();
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel();
        Notification n = buildNotification("Запуск сервера…");
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }
        if (!running) {
            error = null;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    startServer();
                }
            }, "tu-start").start();
        }
        return START_NOT_STICKY;
    }

    private void startServer() {
        try {
            final AssetManager am = getAssets();
            AssetSource src = new AssetSource() {
                @Override
                public byte[] read(String path) {
                    InputStream in = null;
                    try {
                        in = am.open("web/" + path);
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int k;
                        while ((k = in.read(buf)) > 0) out.write(buf, 0, k);
                        return out.toByteArray();
                    } catch (IOException e) {
                        return null;
                    } finally {
                        if (in != null) try { in.close(); } catch (IOException ignored) { }
                    }
                }
            };
            server = new WebServer(new Game(), src);
            int p = server.start(8000, 20);
            List<String> list = new ArrayList<String>();
            for (String ip : NetInfo.ips()) list.add("http://" + ip + ":" + p);
            urls = list;
            port = p;
            try {
                responder = Discovery.startResponder(Build.MODEL == null ? "Телефон" : Build.MODEL, p, Discovery.PORT);
            } catch (Exception ignored) {
                // пошук у мережі — необов'язковий, адресу можна ввести вручну
            }
            running = true;
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(NOTIF_ID, buildNotification(list.isEmpty() ? "Сервер працює (немає Wi-Fi адреси)" : "Сервер: " + list.get(0)));
        } catch (Exception e) {
            error = String.valueOf(e.getMessage());
            running = false;
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Сервер гри", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setContentTitle("Тактичний урбаніст — сервер")
                .setContentText(text)
                .setSmallIcon(getApplicationInfo().icon)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }

    @Override
    public void onDestroy() {
        running = false;
        port = 0;
        urls = new ArrayList<String>();
        if (responder != null) responder.stop();
        if (server != null) server.stop();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
