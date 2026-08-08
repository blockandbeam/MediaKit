package dev.blockandbeam.mediakit.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import dev.architectury.platform.Platform;

import dev.blockandbeam.mediakit.api.Http;
import dev.blockandbeam.mediakit.MediaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/** Entry point for loading media sources. All loads are blocking; call off the main thread. */
public final class MediaAPI {
    private MediaAPI() {
    }

    /** Loads media from a URL, file path, or resource pack path. */
    public static Media load(String source) throws MediaException {
        String trimmed = source.trim();
        if (isUrl(trimmed)) {
            try {
                return load(new URI(trimmed));
            } catch (URISyntaxException e) {
                throw new MediaException("Invalid URI: " + trimmed, e);
            }
        }
        Path file = Path.of(trimmed);
        if (Files.exists(file)) {
            return load(file);
        }
        return loadResource(trimmed);
    }

    /** Loads media from an http(s) or file URI. */
    public static Media load(URI uri) throws MediaException {
        if (uri == null) {
            throw new MediaException("URI must not be null");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new MediaException("URI has no scheme: " + uri);
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http", "https" -> loadRemote(uri);
            case "file" -> load(Path.of(uri));
            default -> throw new MediaException("Unsupported URI scheme '" + scheme + "': " + uri);
        };
    }

    /** Loads a local file. */
    public static Media load(Path file) throws MediaException {
        Path absolute = file.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new MediaException("No such file: " + absolute);
        }
        if (!Files.isRegularFile(absolute)) {
            throw new MediaException("Not a regular file: " + absolute);
        }
        if (!Files.isReadable(absolute)) {
            throw new MediaException("File is not readable: " + absolute);
        }

        MediaFormat format;
        try {
            format = MediaFormat.detect(absolute);
        } catch (IOException e) {
            throw new MediaException("Could not read " + absolute, e);
        }
        if (format == MediaFormat.UNKNOWN) {
            throw new MediaException("Unsupported media format: " + absolute);
        }

        long durationMillis = probeDuration(absolute);
        String name = absolute.getFileName().toString();
        return new Media(UUID.randomUUID(), name, absolute.toUri(), absolute, format, durationMillis, MediaState.READY);
    }

    /** Loads a file from a resource pack, e.g. {@code media/song.mp3} or {@code pack:media/song.mp3}. Client-side only. */
    public static Media loadResource(String path) throws MediaException {
        ResourceLocation location = parseLocation(path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            throw new MediaException("No resource found: " + location);
        }
        try (InputStream in = resource.get().open()) {
            Path cached = cacheDir().resolve(hash(location.toString()) + ".media");
            Files.createDirectories(cached.getParent());
            Files.copy(in, cached, StandardCopyOption.REPLACE_EXISTING);
            return load(cached);
        } catch (IOException e) {
            throw new MediaException("Could not read resource " + location, e);
        }
    }

    private static Media loadRemote(URI uri) throws MediaException {
        URI clean = stripTracking(uri);
        URI source = clean;
        String name = fileNameOf(clean);
        long knownDuration = -1;

        Optional<MediaSource> mediaSource = MediaSourceManager.find(clean);
        if (mediaSource.isPresent()) {
            MediaSource.ResolvedSource resolved = mediaSource.get().resolve(clean);
            clean = resolved.stream();
            name = resolved.name();
            knownDuration = resolved.durationMillis();
        }

        Http.Head probe = Http.head(clean).orElse(null);
        if (probe != null && !probe.isSuccess()) {
            throw new MediaException("Server returned " + probe.statusCode() + " for " + hostOf(clean));
        }

        MediaFormat format = MediaFormat.UNKNOWN;
        if (probe != null) {
            format = MediaFormat.fromContentType(probe.contentType());
        }
        if (format == MediaFormat.UNKNOWN && clean.getPath() != null) {
            format = MediaFormat.fromFileName(clean.getPath());
        }

        String extension = format == MediaFormat.UNKNOWN ? ".media" : "." + format.name().toLowerCase(Locale.ROOT);
        Path destination = cacheDir().resolve(hash(source.toString()) + extension);
        try {
            Files.createDirectories(destination.getParent());
            Path downloaded = Http.download(clean, destination);
            if (format == MediaFormat.UNKNOWN) {
                format = MediaFormat.detectMagic(downloaded);
                if (format == MediaFormat.UNKNOWN) {
                    throw new MediaException("Unsupported media format: " + fileNameOf(clean));
                }
            }
        } catch (IOException e) {
            throw new MediaException("Could not download " + hostOf(clean) + ": " + e.getMessage(), e);
        }

        long durationMillis = knownDuration >= 0 ? knownDuration : probeDuration(destination);
        return new Media(UUID.randomUUID(), name, source, destination, format, durationMillis, MediaState.READY);
    }

    private static boolean isUrl(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("file://");
    }

    private static ResourceLocation parseLocation(String path) {
        if (path.contains(":")) {
            return ResourceLocation.parse(path);
        }
        return ResourceLocation.fromNamespaceAndPath(MediaKit.MOD_ID, path);
    }

    static Path cacheDir() {
        return Platform.getConfigFolder().resolve("mediakit").resolve("cache");
    }

    /** The transcode directory, creating it if needed. */
    static Path transcodeDir() throws IOException {
        Path dir = cacheDir().resolve("transcode");
        Files.createDirectories(dir);
        return dir;
    }

    private static String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String fileNameOf(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            return uri.toString();
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.isEmpty() ? uri.toString() : name;
    }

    /** Drops {@code utm_*} tracking parameters from a URL's query string. */
    private static URI stripTracking(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isEmpty()) {
            return uri;
        }
        StringBuilder kept = new StringBuilder();
        for (String param : query.split("&")) {
            if (param.startsWith("utm_")) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(param);
        }
        String cleaned = kept.toString();
        if (cleaned.equals(query)) {
            return uri;
        }
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(),
                    cleaned.isEmpty() ? null : cleaned, uri.getFragment());
        } catch (URISyntaxException e) {
            return uri;
        }
    }

    private static String hostOf(URI uri) {
        return uri.getHost() != null ? uri.getHost() : uri.toString();
    }

    private static long probeDuration(Path file) {
        try {
            AudioFileFormat format = AudioSystem.getAudioFileFormat(file.toFile());
            float frameRate = format.getFormat().getFrameRate();
            int frameLength = format.getFrameLength();
            if (frameRate > 0 && frameLength != AudioSystem.NOT_SPECIFIED) {
                return Math.round(frameLength / frameRate * 1000.0);
            }
        } catch (UnsupportedAudioFileException | IOException | IllegalArgumentException ignored) {
            // Not decodable by plain Java Sound; duration stays unknown.
        }
        return -1;
    }
}
