package dev.blockandbeam.mediakit.api;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.architectury.platform.Platform;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/**
 * Locates or downloads an FFmpeg binary for the current OS/arch. Resolution
 * order: the {@code mediakit.ffmpeg} JVM flag, PATH (unless
 * {@code mediakit.ffmpeg.detect=false}), then the latest download.
 */
public final class FFmpeg {
    private static final String BASE_URL = "https://github.com/BtbN/FFmpeg-Builds/releases/latest/download/";
    private static final Duration UPDATE_CHECK_INTERVAL = Duration.ofHours(1);

    private static volatile Instant lastChecked;

    private FFmpeg() {
    }

    /**
     * Resolves an ffmpeg binary, downloading the latest build if none is found.
     * Checks the {@code mediakit.ffmpeg} JVM flag, then PATH (unless
     * {@code mediakit.ffmpeg.detect=false}), then the download.
     */
    public static Path resolve() throws IOException {
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
        return download();
    }

    /**
     * Auto-detects an ffmpeg binary: the {@code mediakit.ffmpeg} JVM flag, then
     * PATH (unless {@code mediakit.ffmpeg.detect=false}), then a previous
     * download. Never downloads.
     */
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

    /** The downloaded ffmpeg, or {@code null} if it has not been downloaded yet. */
    public static Path fromDownload() {
        Path executable = binDir().resolve(executableName());
        return Files.exists(executable) ? executable : null;
    }

    /**
     * Ensures a working ffmpeg is downloaded, re-downloading when a newer build
     * is available (checked at most once per hour). Returns the executable path.
     */
    public static Path download() throws IOException {
        Asset asset = assetFor();
        Path dir = binDir();
        Files.createDirectories(dir);
        Path executable = dir.resolve(asset.executableName());
        if (Files.exists(executable) && runs(executable) && !isStale(executable, asset)) {
            return executable;
        }
        Files.deleteIfExists(executable);
        Path archive = dir.resolve(asset.fileName());
        try {
            if (Http.download(URI.create(BASE_URL + asset.fileName()), archive).isEmpty()) {
                throw new IOException("Download failed for " + asset.fileName());
            }
            extract(archive, dir, asset.executableName());
        } finally {
            Files.deleteIfExists(archive);
        }
        if (!runs(executable)) {
            throw new IOException("Downloaded ffmpeg failed to run: " + executable);
        }
        return executable;
    }

    private static boolean autoDetect() {
        return !Boolean.getBoolean("mediakit.ffmpeg.detect");
    }

    private static Path fromFlag() {
        String path = System.getProperty("mediakit.ffmpeg");
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

    private static boolean isStale(Path executable, Asset asset) throws IOException {
        Instant now = Instant.now();
        if (lastChecked != null && now.isBefore(lastChecked.plus(UPDATE_CHECK_INTERVAL))) {
            return false;
        }
        lastChecked = now;
        Optional<Http.Head> head = Http.head(URI.create(BASE_URL + asset.fileName()));
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

    /**
     * Transcodes {@code input} to a WAV file at {@code output}, dropping any
     * video streams. Requires ffmpeg (see {@link #resolve()}).
     */
    public static void transcode(Path input, Path output) throws IOException {
        Process process = new ProcessBuilder(resolve().toString(), "-y", "-hide_banner", "-loglevel", "error",
                "-i", input.toString(), "-vn", "-c:a", "pcm_s16le", "-f", "wav", output.toString())
                .redirectErrorStream(true)
                .start();
        boolean finished;
        try {
            finished = process.waitFor(2, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while transcoding " + input.getFileName(), e);
        }
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg timed out transcoding " + input.getFileName());
        }
        String error = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg failed (" + process.exitValue() + ") for " + input.getFileName()
                    + (error.isEmpty() ? "" : ": " + error));
        }
    }

    private static Asset assetFor() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean windows = os.contains("win");
        boolean mac = os.contains("mac");
        String arch = switch (System.getProperty("os.arch").toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64", "x64" -> "x64";
            case "aarch64", "arm64" -> "arm64";
            default -> throw new IOException("Unsupported OS/arch: " + System.getProperty("os.name")
                    + " " + System.getProperty("os.arch"));
        };
        if (windows) {
            return arch.equals("x64")
                    ? new Asset("ffmpeg-master-latest-win64-gpl.zip", "ffmpeg.exe")
                    : new Asset("ffmpeg-master-latest-winarm64-gpl.zip", "ffmpeg.exe");
        }
        if (mac) {
            return arch.equals("x64")
                    ? new Asset("ffmpeg-master-latest-macos64-gpl.zip", "ffmpeg")
                    : new Asset("ffmpeg-master-latest-macosarm64-gpl.zip", "ffmpeg");
        }
        return arch.equals("x64")
                ? new Asset("ffmpeg-master-latest-linux64-gpl.tar.xz", "ffmpeg")
                : new Asset("ffmpeg-master-latest-linuxarm64-gpl.tar.xz", "ffmpeg");
    }

    private static void extract(Path archive, Path dir, String executableName) throws IOException {
        Path target = dir.resolve(executableName);
        String name = archive.getFileName().toString();
        if (name.endsWith(".zip")) {
            extractZip(archive, target, executableName);
        } else if (name.endsWith(".tar.xz")) {
            extractTarXz(archive, target, executableName);
        } else {
            throw new IOException("Unsupported archive: " + name);
        }
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            Files.setPosixFilePermissions(target, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    private static void extractZip(Path archive, Path target, String executableName) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith("/bin/" + executableName)) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return;
                }
            }
        }
        throw new IOException("No ffmpeg binary found in " + archive.getFileName());
    }

    private static void extractTarXz(Path archive, Path target, String executableName) throws IOException {
        try (InputStream fileIn = Files.newInputStream(archive);
                XZCompressorInputStream xzIn = new XZCompressorInputStream(fileIn);
                TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith("/bin/" + executableName)) {
                    Files.copy(tarIn, target, StandardCopyOption.REPLACE_EXISTING);
                    return;
                }
            }
        }
        throw new IOException("No ffmpeg binary found in " + archive.getFileName());
    }

    private static boolean runs(Path executable) {
        try {
            Process process = new ProcessBuilder(executable.toString(), "-version")
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
        return Platform.getConfigFolder().resolve("mediakit").resolve("ffmpeg");
    }

    private static String executableName() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "ffmpeg.exe" : "ffmpeg";
    }

    private record Asset(String fileName, String executableName) {
    }
}
