package dev.blockandbeam.mediakit.api;

import java.nio.file.Path;

import dev.architectury.platform.Platform;

/**
 * Where MediaKit keeps its runtime files. Downloads and caches live in the
 * game instance ({@code <instance>/mediakit/}); only the consent config stays
 * in {@code config/}.
 */
public final class MediaKitPaths {
    private MediaKitPaths() {
    }

    public static Path instanceDir() {
        return Platform.getGameFolder().resolve("mediakit");
    }

    public static Path binDir(String name) {
        return instanceDir().resolve(name);
    }

    public static Path cacheDir() {
        return instanceDir().resolve("cache");
    }
}
