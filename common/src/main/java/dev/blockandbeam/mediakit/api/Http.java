package dev.blockandbeam.mediakit.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

import dev.architectury.platform.Platform;

import dev.blockandbeam.mediakit.MediaKit;

/** Minimal HTTP helpers for fetching remote media. Blocking; call off the main thread. */
public final class Http {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String USER_AGENT = "MediaKit/" + Platform.getMod(MediaKit.MOD_ID).getVersion()
            + " (Minecraft/" + Platform.getMinecraftVersion() + "; " + loaderName() + ")";

    private static String loaderName() {
        if (Platform.isNeoForge()) {
            return "NeoForge";
        }
        if (Platform.isFabric()) {
            return "Fabric";
        }
        return "Unknown";
    }

    private Http() {
    }

    /** The shared HTTP client. */
    public static HttpClient client() {
        return CLIENT;
    }

    /** The MediaKit user agent string. */
    public static String userAgent() {
        return USER_AGENT;
    }

    /** Result of a HEAD probe. */
    public record Head(int statusCode, String contentType, String lastModified) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /** A plain text response. */
    public record Text(int status, String body) {
        public boolean isSuccess() {
            return status >= 200 && status < 300;
        }
    }

    /** Result of following a URL's redirects. */
    public record Redirect(URI uri, int status) {
    }

    /** GETs a URL as text with the MediaKit user agent; empty if the request could not be made. */
    public static Optional<Text> getText(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return Optional.of(new Text(response.statusCode(), response.body()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /** Lightweight HEAD request; empty if the server is unreachable. */
    public static Optional<Head> head(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(TIMEOUT)
                    .build();
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String lastModified = response.headers().firstValue("Last-Modified").orElse(null);
            return Optional.of(new Head(response.statusCode(), contentType, lastModified));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /** Follows a URL's redirects, discarding the body; empty if the request could not be made. */
    public static Optional<Redirect> redirect(URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("User-Agent", USER_AGENT)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
            return Optional.of(new Redirect(response.uri(), response.statusCode()));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * Downloads a URL to {@code destination}.
     *
     * @return the destination on success
     * @throws IOException if the server returned an error
     */
    public static Path download(URI uri, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<Path> response;
        try {
            response = CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofFile(destination, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading " + uri, e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            Files.deleteIfExists(destination);
            throw new IOException("HTTP " + response.statusCode() + " for " + uri);
        }
        return destination;
    }
}
