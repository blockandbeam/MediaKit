package dev.blockandbeam.mediakit.api.media;

import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;

/** A handle to a successfully loaded media source. */
public final class Media {
    private final UUID id;
    private final String name;
    private final URI source;
    private final Path file;
    private final URI stream;
    private final MediaFormat format;
    private final long durationMillis;
    private volatile MediaState state;

    Media(UUID id, String name, URI source, Path file, MediaFormat format, long durationMillis, MediaState state) {
        this(id, name, source, file, null, format, durationMillis, state);
    }

    Media(UUID id, String name, URI source, URI stream, MediaFormat format, long durationMillis, MediaState state) {
        this(id, name, source, null, stream, format, durationMillis, state);
    }

    private Media(UUID id, String name, URI source, Path file, URI stream, MediaFormat format,
            long durationMillis, MediaState state) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.source = Objects.requireNonNull(source, "source");
        if (file == null && stream == null) {
            throw new IllegalArgumentException("Media needs a file or a stream");
        }
        this.file = file;
        this.stream = stream;
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

    /** The local file backing this media, or {@code null} for streamed media. */
    public Path file() {
        return file;
    }

    /** The remote stream this media plays from, or {@code null} for local media. */
    public URI stream() {
        return stream;
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
        Optional<Component> brand = MediaSourceManager.brand(source);
        String platform = brand.map(Component::getString).map(s -> ", Platform=" + s.toUpperCase()).orElse("");
        return "Media{name='" + name + "', format=" + format + platform + ", state=" + state + '}';
    }
}
