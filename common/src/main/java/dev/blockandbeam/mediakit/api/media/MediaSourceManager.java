package dev.blockandbeam.mediakit.api.media;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;

import dev.blockandbeam.mediakit.api.media.source.SoundCloudSource;

/** Registry of media sources that resolve URLs to direct streams. */
public final class MediaSourceManager {
    private static final Set<MediaSource> SOURCES = new LinkedHashSet<>();

    static {
        register(new SoundCloudSource());
    }

    private MediaSourceManager() {
    }

    /** Registers a media source. */
    public static synchronized void register(MediaSource source) {
        SOURCES.add(source);
    }

    /** Finds the first source that supports the given URL. */
    public static Optional<MediaSource> find(URI uri) {
        return SOURCES.stream().filter(source -> source.supports(uri)).findFirst();
    }

    /** The brand text for media from the given source URL. */
    public static Optional<Component> brand(URI uri) {
        return find(uri).flatMap(MediaSource::brand);
    }
}
