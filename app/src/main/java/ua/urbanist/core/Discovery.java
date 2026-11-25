package ua.urbanist.core;

import java.net.*;
import java.util.*;

/** Пошук ігор у Wi-Fi: господар відповідає на UDP-запит, клієнт розсилає запит і збирає відповіді. */
public final class Discovery {
    public static final int PORT = 47321;
    private static final String ASK = "TU-SCAN-1";
    private static final String ANS = "TU-HOST-1|";

    public static final class Found {
        public final String ip, name; public final int port;
        public Found(String ip, int port, String name) { this.ip = ip; this.port = port; this.name = name; }
        @Override public String toString() { return name + " (" + ip + ":" + port + ")"; }
    }

    public static final class Responder {
        private final DatagramSocket sock; private volatile boolean run = true;
        Responder(DatagramSocket s, final String name, final int httpPort) {
            sock = s;
            Thread t = new Thread(new Runnable() { public void run() {
                byte[] buf = new byte[256];
                while (run) {
                    try {
                        DatagramPacket p = new DatagramPacket(buf, buf.length); sock.receive(p);
                        String m = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        if (m.equals(ASK)) {
                            byte[] a = (ANS + httpPort + "|" + name).getBytes("UTF-8");
                            sock.send(new DatagramPacket(a, a.length, p.getAddress(), p.getPort()));
                        }
                    } catch (Exception e) { if (!run) return; }
                }
            } }, "tu-discovery");
            t.setDaemon(true); t.start();
        }
        public void stop() { run = false; sock.close(); }
    }

    public static Responder startResponder(String name, int httpPort, int udpPort) throws SocketException {
        DatagramSocket s = new DatagramSocket(null); s.setReuseAddress(true); s.bind(new InetSocketAddress(udpPort));
        return new Responder(s, name, httpPort);
    }

    /** Розсилає запит у підмережі й збирає відповіді timeoutMs. extra — додаткові адреси (для тестів). */
    public static List<Found> scan(int udpPort, int timeoutMs, List<InetAddress> extra) {
        Map<String, Found> res = new LinkedHashMap<String, Found>();
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(); s.setBroadcast(true); s.setSoTimeout(300);
            byte[] q = ASK.getBytes("UTF-8");
            List<InetAddress> targets = new ArrayList<InetAddress>(NetInfo.broadcasts());
            try { targets.add(InetAddress.getByName("255.255.255.255")); } catch (UnknownHostException ignored) { }
            if (extra != null) targets.addAll(extra);
            long end = System.currentTimeMillis() + timeoutMs; long nextSend = 0;
            byte[] buf = new byte[256];
            while (System.currentTimeMillis() < end) {
                if (System.currentTimeMillis() >= nextSend) {
                    for (InetAddress t : targets) { try { s.send(new DatagramPacket(q, q.length, t, udpPort)); } catch (Exception ignored) { } }
                    nextSend = System.currentTimeMillis() + 500;
                }
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length); s.receive(p);
                    String m = new String(p.getData(), 0, p.getLength(), "UTF-8");
                    if (m.startsWith(ANS)) {
                        String[] parts = m.substring(ANS.length()).split("\\|", 2);
                        int port = Integer.parseInt(parts[0]); String ip = p.getAddress().getHostAddress();
                        res.put(ip + ":" + port, new Found(ip, port, parts.length > 1 ? parts[1] : "Гра"));
                    }
                } catch (SocketTimeoutException ignored) { }
            }
        } catch (Exception ignored) {
        } finally { if (s != null) s.close(); }
        return new ArrayList<Found>(res.values());
    }
}
