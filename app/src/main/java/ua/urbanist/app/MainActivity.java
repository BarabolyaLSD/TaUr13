package ua.urbanist.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import ua.urbanist.core.Discovery;

/** Головне меню: створити гру (запустити сервер) або приєднатися до чужої. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF0A1018, PANEL = 0xFF121C2B, CYAN = 0xFF7FD6FF, TEXT = 0xFFE4ECF8, MUTED = 0xFF8FA3BF;

    private TextView hostInfo;
    private Button startBtn, openBtn, stopBtn, scanBtn;
    private EditText addr;
    private LinearLayout foundBox;
    private SharedPreferences prefs;
    private boolean starting = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshHost();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("tu", MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, int bg, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(bg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(PANEL);
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        c.addView(text(title, 13, CYAN, true));
        return c;
    }

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(28));
        sv.addView(root);

        TextView title = text("⚡ Тактичний урбаніст", 28, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView sub = text("Гра по Wi-Fi: один телефон — господар, решта приєднуються", 14, MUTED, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        LinearLayout host = card("Я ГОСПОДАР");
        hostInfo = text("Сервер не запущено.", 15, TEXT, false);
        hostInfo.setPadding(0, dp(8), 0, 0);
        host.addView(hostInfo);
        startBtn = button("Створити гру (запустити сервер)", 0xFF1C5AA0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startHost();
            }
        });
        openBtn = button("Відкрити гру", 0xFF1C7A4C, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openGame("http://127.0.0.1:" + GameService.port + "/");
            }
        });
        stopBtn = button("Зупинити сервер", 0xFF7A2A35, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, GameService.class));
                starting = false;
            }
        });
        host.addView(startBtn);
        host.addView(openBtn);
        host.addView(stopBtn);
        root.addView(host);

        LinearLayout join = card("ПРИЄДНАТИСЯ ДО ГРИ");
        addr = new EditText(this);
        addr.setHint("192.168.43.1:8000");
        addr.setHintTextColor(MUTED);
        addr.setTextColor(Color.WHITE);
        addr.setSingleLine(true);
        addr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        addr.setText(prefs.getString("addr", ""));
        join.addView(addr);
        join.addView(button("Підключитися за адресою", 0xFF1C5AA0, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect(addr.getText().toString());
            }
        }));
        scanBtn = button("Знайти ігри в мережі", 0xFF2A3F5E, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan();
            }
        });
        join.addView(scanBtn);
        foundBox = new LinearLayout(this);
        foundBox.setOrientation(LinearLayout.VERTICAL);
        join.addView(foundBox);
        root.addView(join);

        LinearLayout help = card("ПІДКАЗКА");
        TextView h = text("Немає роутера? Увімкніть на телефоні-господарі точку доступу Wi-Fi, підключіть до неї інші телефони й "
                + "введіть адресу господаря (зазвичай 192.168.43.1:8000). Адреса показується в блоці «Я господар» після запуску сервера.", 14, MUTED, false);
        h.setPadding(0, dp(8), 0, 0);
        help.addView(h);
        root.addView(help);

        setContentView(sv);
        refreshHost();
    }

    private void refreshHost() {
        boolean run = GameService.running;
        startBtn.setVisibility(run || starting ? View.GONE : View.VISIBLE);
        openBtn.setVisibility(run ? View.VISIBLE : View.GONE);
        stopBtn.setVisibility(run || starting ? View.VISIBLE : View.GONE);
        if (run) {
            StringBuilder sb = new StringBuilder("Сервер працює. Інші гравці відкривають цю адресу (в додатку: «Приєднатися» або в браузері):\n");
            List<String> u = GameService.urls;
            if (u.isEmpty()) sb.append("\n(Wi-Fi адресу не знайдено: увімкніть Wi-Fi або точку доступу)");
            for (String s : u) sb.append("\n").append(s);
            hostInfo.setText(sb.toString());
        } else if (starting) {
            hostInfo.setText(GameService.error != null ? "Помилка запуску: " + GameService.error : "Запуск сервера…");
        } else {
            hostInfo.setText("Сервер не запущено.");
        }
    }

    private void startHost() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
        starting = true;
        Intent i = new Intent(this, GameService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        refreshHost();
    }

    private void openGame(String url) {
        Intent g = new Intent(this, GameActivity.class);
        g.putExtra("url", url);
        startActivity(g);
    }

    private void connect(String raw) {
        String a = raw == null ? "" : raw.trim();
        if (a.isEmpty()) {
            Toast.makeText(this, "Введіть адресу господаря", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString("addr", a).apply();
        if (!a.startsWith("http")) a = "http://" + a;
        String rest = a.substring(a.indexOf("://") + 3);
        if (!rest.contains(":")) a = a + ":8000";
        if (!a.endsWith("/")) a = a + "/";
        openGame(a);
    }

    private void scan() {
        scanBtn.setEnabled(false);
        scanBtn.setText("Пошук…");
        foundBox.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Discovery.Found> res = Discovery.scan(Discovery.PORT, 2500, null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scanBtn.setEnabled(true);
                        scanBtn.setText("Знайти ігри в мережі");
                        if (res.isEmpty()) {
                            foundBox.addView(text("Ігор не знайдено. Введіть адресу вручну.", 14, MUTED, false));
                        }
                        for (final Discovery.Found f : res) {
                            foundBox.addView(button(f.name + " — " + f.ip + ":" + f.port, 0xFF1C7A4C, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    connect(f.ip + ":" + f.port);
                                }
                            }));
                        }
                    }
                });
            }
        }, "tu-scan").start();
    }
}
