package dev.blockandbeam.mediakit.api.media;

import java.net.URI;
import java.util.Optional;

import net.minecraft.network.chat.Component;

/** A provider that resolves a media URL to a direct stream. Blocking; call off the main thread. */
public interface MediaSource {
    /** Whether this source can resolve the given URL. */
    boolean supports(URI uri);

    /** Resolves a source URL to a direct stream. */
    ResolvedSource resolve(URI uri) throws MediaException;

    /** A URL resolved to a direct stream. */
    record ResolvedSource(String name, URI stream, long durationMillis) {
    }

    /** Brand text shown next to media from this source; empty if none. */
    default Optional<Component> brand() {
        return Optional.empty();
    }
}
