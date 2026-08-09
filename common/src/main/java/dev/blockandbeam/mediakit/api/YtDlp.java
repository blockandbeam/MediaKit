package dev.blockandbeam.mediakit.api;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dev.blockandbeam.mediakit.client.ClientNotifications;
import dev.blockandbeam.mediakit.client.DownloadConsent;

/**
 * Locates or downloads a yt-dlp binary for the current OS/arch. Resolution
 * order: the {@code mediakit.ytdlp} JVM flag, PATH (unless
 * {@code mediakit.ytdlp.detect=false}), then the latest download.
 */
public final class YtDlp {
    private static final String BASE_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final Duration UPDATE_CHECK_INTERVAL = Duration.ofHours(1);
    private static final Duration RESOLVE_TIMEOUT = Duration.ofSeconds(60);

    private static volatile Instant lastChecked;

    private YtDlp() {
    }

    public static ExternalDependency dependency() {
        return new ExternalDependency("yt-dlp", "YouTube and other media sources", find(),
                "https://github.com/yt-dlp/yt-dlp#installation", YtDlp::resolve);
    }

    /** Resolves a yt-dlp binary, downloading the latest release if none is found. */
    public static Path resolve() throws IOException {
        Path found = find();
        if (found != null) {
            ClientNotifications.announce("Using yt-dlp from " + found);
            return found;
        }
        Path downloaded = download();
        ClientNotifications.announce("Downloaded yt-dlp to " + downloaded);
        return downloaded;
    }

    /** Finds an existing yt-dlp binary (flag, PATH, or a previous download); never downloads. */
    public static Path find() {
        Path fromFlag = fromFlag();
        if (fromFlag != null) {
            return fromFlag;
        }
        if (autoDetect()) {
            Path onPath = onPath();
            if (onPath != null) {
                return onPath;
            }
        }
        return fromDownload();
    }

    /** The downloaded yt-dlp, or {@code null} if it has not been downloaded yet. */
    public static Path fromDownload() {
        Path executable = binDir().resolve(executableName());
        return Files.exists(executable) ? executable : null;
    }

    /**
     * Ensures a working yt-dlp is downloaded, re-downloading when a newer
     * release is available (checked at most once per hour). Returns the
     * executable path.
     */
    public static Path download() throws IOException {
        String asset = assetFor();
        Path dir = binDir();
        Files.createDirectories(dir);
        Path executable = dir.resolve(executableName());
        if (Files.exists(executable) && runs(executable) && !isStale(executable, asset)) {
            return executable;
        }
        if (!DownloadConsent.await()) {
            throw new IOException("yt-dlp download needs consent; delete config/mediakit/consent.json to re-ask");
        }
        Files.deleteIfExists(executable);
        Http.download(URI.create(BASE_URL + asset), executable);
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            Files.setPosixFilePermissions(executable, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
        if (!runs(executable)) {
            Files.deleteIfExists(executable);
            throw new IOException("Downloaded yt-dlp failed to run: " + executable);
        }
        return executable;
    }

    /**
     * Runs yt-dlp against {@code url}, returning the extracted metadata as a
     * JSON document. Requires yt-dlp (see {@link #resolve()}).
     */
    public static String dumpJson(URI url) throws IOException {
        Path output = Files.createTempFile("mediakit-ytdlp", ".json");
        try {
            Process process = new ProcessBuilder(resolve().toString(), "--no-playlist",
                    "-f", "bestaudio", "--dump-single-json", "--no-warnings", url.toString())
                    .redirectOutput(output.toFile())
                    .start();
            boolean finished;
            try {
                finished = process.waitFor(RESOLVE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while resolving " + url, e);
            }
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("yt-dlp timed out resolving " + url);
            }
            String json = Files.readString(output, StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException("yt-dlp failed (" + process.exitValue() + ") for " + url
                        + (error.isEmpty() ? "" : ": " + error));
            }
            return json;
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static boolean autoDetect() {
        return !Boolean.getBoolean("mediakit.ytdlp.detect");
    }

    private static Path fromFlag() {
        String path = System.getProperty("mediakit.ytdlp");
        if (path == null || path.isBlank()) {
            return null;
        }
        Path executable = Path.of(path);
        return Files.exists(executable) ? executable : null;
    }

    private static Path onPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        String name = executableName();
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir).resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isStale(Path executable, String asset) throws IOException {
        Instant now = Instant.now();
        if (lastChecked != null && now.isBefore(lastChecked.plus(UPDATE_CHECK_INTERVAL))) {
            return false;
        }
        lastChecked = now;
        Optional<Http.Head> head = Http.head(URI.create(BASE_URL + asset));
        if (head.isEmpty()) {
            return false; // Offline; keep the working binary.
        }
        String lastModified = head.get().lastModified();
        if (lastModified == null) {
            return false;
        }
        try {
            Instant remote = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(lastModified));
            return remote.isAfter(Files.getLastModifiedTime(executable).toInstant());
        } catch (DateTimeParseException | IOException e) {
            return false;
        }
    }

    private static String assetFor() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "yt-dlp.exe";
        }
        if (os.contains("mac")) {
            return "yt-dlp_macos"; // Universal2; covers x64 and arm64.
        }
        return switch (System.getProperty("os.arch").toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x64" -> "yt-dlp_linux";
            case "aarch64", "arm64" -> "yt-dlp_linux_aarch64";
            default -> throw new IOException("Unsupported OS/arch: " + System.getProperty("os.name")
                    + " " + System.getProperty("os.arch"));
        };
    }

    private static boolean runs(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "--version")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static Path binDir() {
        return MediaKitPaths.binDir("ytdlp");
    }

    private static String executableName() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "yt-dlp.exe" : "yt-dlp";
    }
}
