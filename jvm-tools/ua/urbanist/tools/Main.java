package ua.urbanist.tools;

import ua.urbanist.core.*;
import java.io.*;
import java.nio.file.*;

/** Запуск того самого сервера на ПК: java -cp out ua.urbanist.tools.Main --port 8000 --assets path/to/web */
public final class Main {
    public static void main(String[] a) throws Exception {
        int port = 8000; String assets = "app/src/main/assets/web";
        for (int i = 0; i < a.length - 1; i++) { if (a[i].equals("--port")) port = Integer.parseInt(a[i + 1]); if (a[i].equals("--assets")) assets = a[i + 1]; }
        final Path root = Paths.get(assets).toAbsolutePath().normalize();
        AssetSource src = new AssetSource() { public byte[] read(String p) {
            try { Path f = root.resolve(p).normalize(); if (!f.startsWith(root) || !Files.isRegularFile(f)) return null; return Files.readAllBytes(f); } catch (IOException e) { return null; } } };
        WebServer s = new WebServer(new Game(), src);
        int p = s.start(port, 1);
        Discovery.startResponder("Тест-сервер", p, Discovery.PORT + 1);
        System.out.println("READY " + p); System.out.flush();
        Thread.currentThread().join();
    }
}
