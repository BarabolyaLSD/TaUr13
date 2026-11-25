package ua.urbanist.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Мінімальний HTTP-сервер (ServerSocket) для гри та клієнтської сторінки. Працює і на JVM, і на Android. */
public final class WebServer {
    private final Game game; private final AssetSource assets;
    private ServerSocket ss; private volatile boolean running; private int port;
    private final ExecutorService pool = Executors.newCachedThreadPool(new ThreadFactory() {
        public Thread newThread(Runnable r) { Thread t = new Thread(r, "tu-http"); t.setDaemon(true); return t; }
    });

    public WebServer(Game game, AssetSource assets) { this.game = game; this.assets = assets; }
    public int port() { return port; }
    public Game game() { return game; }

    /** Запускає сервер на першому вільному порту з діапазону [port, port+tries). */
    public int start(int preferred, int tries) throws IOException {
        IOException last = null;
        for (int p = preferred; p < preferred + Math.max(1, tries); p++) {
            try {
                ServerSocket s = new ServerSocket(); s.setReuseAddress(true); s.bind(new InetSocketAddress(p), 64);
                ss = s; port = s.getLocalPort(); running = true; break;
            } catch (IOException e) { last = e; }
        }
        if (!running) throw last != null ? last : new IOException("порт недоступний");
        Thread acc = new Thread(new Runnable() { public void run() { acceptLoop(); } }, "tu-accept"); acc.setDaemon(true); acc.start();
        return port;
    }

    public void stop() {
        running = false;
        try { if (ss != null) ss.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket s = ss.accept();
                pool.execute(new Runnable() { public void run() { handle(s); } });
            } catch (IOException e) { if (!running) return; }
            catch (RejectedExecutionException e) { return; }
        }
    }

    private static final class Req { String method, path, query; Map<String, String> headers = new HashMap<String, String>(); byte[] body = new byte[0]; }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(40000); s.setTcpNoDelay(true);
            InputStream in = new BufferedInputStream(s.getInputStream()); OutputStream out = s.getOutputStream();
            Req r = readRequest(in);
            if (r == null) return;
            try { route(r, out); }
            catch (GameError e) { json(out, e.code, err(e.getMessage())); }
            catch (IllegalArgumentException e) { json(out, 400, err("Некоректний запит")); }
            catch (RuntimeException e) { json(out, 500, err("server: " + e)); }
        } catch (IOException ignored) {
        } finally { try { s.close(); } catch (IOException ignored) { } }
    }

    private static Map<String, Object> err(String m) { Map<String, Object> e = new LinkedHashMap<String, Object>(); e.put("error", m); return e; }

    private Req readRequest(InputStream in) throws IOException {
        String line = readLine(in);
        if (line == null || line.isEmpty()) return null;
        String[] p = line.split(" ");
        if (p.length < 2) return null;
        Req r = new Req(); r.method = p[0]; String target = p[1];
        int q = target.indexOf('?');
        r.path = q < 0 ? target : target.substring(0, q); r.query = q < 0 ? "" : target.substring(q + 1);
        String h;
        while ((h = readLine(in)) != null && !h.isEmpty()) {
            int c = h.indexOf(':');
            if (c > 0) r.headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
        }
        String cl = r.headers.get("content-length");
        if (cl != null) {
            int n; try { n = Integer.parseInt(cl); } catch (NumberFormatException e) { return null; }
            if (n < 0 || n > 65536) return null;
            byte[] b = new byte[n]; int off = 0;
            while (off < n) { int k = in.read(b, off, n - off); if (k < 0) return null; off += k; }
            r.body = b;
        }
        return r;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 16384) return null;
        }
        if (c < 0 && b.size() == 0) return null;
        return new String(b.toByteArray(), "ISO-8859-1");
    }

    private static Map<String, String> query(String q) {
        Map<String, String> m = new HashMap<String, String>();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int e = kv.indexOf('=');
            try {
                if (e < 0) m.put(URLDecoder.decode(kv, "UTF-8"), "");
                else m.put(URLDecoder.decode(kv.substring(0, e), "UTF-8"), URLDecoder.decode(kv.substring(e + 1), "UTF-8"));
            } catch (UnsupportedEncodingException ignored) { }
        }
        return m;
    }

    private void route(Req r, OutputStream out) throws IOException {
        if (r.method.equals("GET") && r.path.equals("/api/state")) {
            Map<String, String> q = query(r.query); String tok = q.containsKey("token") ? q.get("token") : "";
            long since = 0; try { since = Long.parseLong(q.containsKey("v") ? q.get("v") : "0"); } catch (NumberFormatException ignored) { }
            if (!tok.isEmpty() && !game.validToken(tok)) { json(out, 401, err("bad_token")); return; }
            json(out, 200, since > 0 ? game.waitView(tok, since, 20000) : game.view(tok)); return;
        }
        if (r.method.equals("GET") && r.path.equals("/api/info")) {
            List<String> urls = new ArrayList<String>();
            for (String ip : NetInfo.ips()) urls.add("http://" + ip + ":" + port);
            Map<String, Object> m = new LinkedHashMap<String, Object>(); m.put("urls", urls); json(out, 200, m); return;
        }
        if (r.method.equals("POST") && r.path.equals("/api/join")) {
            Map<String, Object> d = Json.parseObject(new String(r.body, "UTF-8"));
            Player p = game.join(Json.strVal(d.get("name")), Json.intVal(d.get("seat")));
            Map<String, Object> m = new LinkedHashMap<String, Object>(); m.put("token", p.token); m.put("id", p.id); json(out, 200, m); return;
        }
        if (r.method.equals("POST") && r.path.equals("/api/action")) {
            Map<String, Object> d = Json.parseObject(new String(r.body, "UTF-8"));
            Object tk = d.get("token");
            Player p = tk instanceof String ? findByToken((String) tk) : null;
            if (p == null) { json(out, 401, err("bad_token")); return; }
            game.act(p, d);
            Map<String, Object> m = new LinkedHashMap<String, Object>(); m.put("ok", Boolean.TRUE); json(out, 200, m); return;
        }
        if (r.method.equals("GET") || r.method.equals("HEAD")) { staticFile(r, out); return; }
        json(out, 404, err("not found"));
    }

    private Player findByToken(String tok) {
        synchronized (game.lock) { for (Player p : game.playersUnsafe()) if ("human".equals(p.kind) && tok.equals(p.token)) return p; }
        return null;
    }

    private void staticFile(Req r, OutputStream out) throws IOException {
        String path = r.path.equals("/") || r.path.isEmpty() ? "/index.html" : r.path;
        if (path.contains("..") || path.contains("\\") || path.indexOf('\0') >= 0) { send(out, 404, "text/plain", new byte[0], "no-store"); return; }
        byte[] b = assets.read(path.substring(1));
        if (b == null) { send(out, 404, "text/plain", new byte[0], "no-store"); return; }
        String ct = contentType(path);
        send(out, 200, ct, r.method.equals("HEAD") ? new byte[0] : b, path.startsWith("/img/") ? "public, max-age=3600" : "no-cache");
    }

    static String contentType(String p) {
        if (p.endsWith(".html")) return "text/html; charset=utf-8";
        if (p.endsWith(".webp")) return "image/webp"; if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".js")) return "application/javascript; charset=utf-8"; if (p.endsWith(".css")) return "text/css; charset=utf-8";
        if (p.endsWith(".json")) return "application/json; charset=utf-8"; if (p.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void json(OutputStream out, int code, Object o) throws IOException {
        send(out, code, "application/json; charset=utf-8", Json.stringify(o).getBytes("UTF-8"), "no-store");
    }

    private void send(OutputStream out, int code, String ct, byte[] body, String cache) throws IOException {
        String status = code == 200 ? "OK" : code == 400 ? "Bad Request" : code == 401 ? "Unauthorized" : code == 404 ? "Not Found" : "Internal Server Error";
        String head = "HTTP/1.1 " + code + " " + status + "\r\nContent-Type: " + ct + "\r\nContent-Length: " + body.length +
                "\r\nCache-Control: " + cache + "\r\nConnection: close\r\n\r\n";
        out.write(head.getBytes("ISO-8859-1")); out.write(body); out.flush();
    }
}
