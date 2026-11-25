package ua.urbanist.core;

/** Джерело статичних файлів клієнта (на Android — assets, на JVM — папка). */
public interface AssetSource {
    /** @return вміст файлу або null, якщо його немає. */
    byte[] read(String path);
}
