package dev.blockandbeam.mediakit.api.media;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** A handle to a successfully loaded media source. */
public final class Media {
    private final UUID id;
    private final String name;
    private final URI source;
    private final Path file;
    private final MediaFormat format;
    private final long durationMillis;
    private volatile MediaState state;

    Media(UUID id, String name, URI source, Path file, MediaFormat format, long durationMillis, MediaState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        this.file = Objects.requireNonNull(file, "file");
        this.format = Objects.requireNonNull(format, "format");
        this.durationMillis = durationMillis;
        this.state = Objects.requireNonNull(state, "state");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public URI source() {
        return source;
    }

    /** The local file backing this media (a cache copy for remote or resource-pack sources). */
    public Path file() {
        return file;
    }

    public MediaFormat format() {
        return format;
    }

    /** Duration in milliseconds, or {@code -1} if unknown. */
    public long durationMillis() {
        return durationMillis;
    }

    public MediaState state() {
        return state;
    }

    void setState(MediaState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public String toString() {
        return "Media{name='" + name + "', format=" + format + ", state=" + state + '}';
    }
}
