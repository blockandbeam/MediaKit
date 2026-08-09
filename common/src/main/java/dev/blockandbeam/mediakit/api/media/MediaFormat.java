package dev.blockandbeam.mediakit.api.media;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** The container/codec format of a media source. */
public enum MediaFormat {
    WAV("audio/wav", "audio/x-wav", "audio/vnd.wave"),
    AIFF("audio/aiff", "audio/x-aiff"),
    AU("audio/basic"),
    OGG("audio/ogg", "application/ogg"),
    OPUS("audio/opus"),
    FLAC("audio/flac", "audio/x-flac"),
    MP3("audio/mpeg", "audio/mp3", "audio/x-mpeg"),
    AAC("audio/aac", "audio/mp4", "audio/x-m4a"),
    MP4("video/mp4"),
    WEBM("video/webm", "audio/webm"),
    MKV("video/x-matroska"),
    MOV("video/quicktime"),
    AVI("video/x-msvideo", "video/avi"),
    MPEG("video/mpeg"),
    UNKNOWN;

    private final String[] contentTypes;

    MediaFormat(String... contentTypes) {
        this.contentTypes = contentTypes;
    }

    /** Detects the format of a file, preferring the extension over the magic bytes. */
    public static MediaFormat detect(Path file) throws IOException {
        MediaFormat byExtension = fromFileName(file.getFileName().toString());
        return byExtension != UNKNOWN ? byExtension : detectMagic(file);
    }

    /** Detects the format from a file name (or any path string). */
    public static MediaFormat fromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return UNKNOWN;
        }
        return fromExtension(fileName.substring(dot + 1));
    }

    /** Detects the format from a file extension (without the leading dot). */
    public static MediaFormat fromExtension(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "wav" -> WAV;
            case "aiff", "aif", "aifc" -> AIFF;
            case "au", "snd" -> AU;
            case "ogg", "oga" -> OGG;
            case "opus" -> OPUS;
            case "flac" -> FLAC;
            case "mp3" -> MP3;
            case "aac", "m4a" -> AAC;
            case "mp4", "m4v" -> MP4;
            case "webm" -> WEBM;
            case "mkv" -> MKV;
            case "mov" -> MOV;
            case "avi" -> AVI;
            case "mpg", "mpeg", "mpv" -> MPEG;
            default -> UNKNOWN;
        };
    }

    /** Detects the format from an HTTP {@code Content-Type} header value. */
    public static MediaFormat fromContentType(String contentType) {
        String base = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        for (MediaFormat format : values()) {
            for (String candidate : format.contentTypes) {
                if (candidate.equals(base)) {
                    return format;
                }
            }
        }
        return UNKNOWN;
    }

    /** Detects the format from the first bytes of a file. */
    public static MediaFormat detectMagic(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return detectMagic(in.readNBytes(12));
        }
    }

    /** Detects the format from raw header bytes (at least 4, ideally 12). */
    public static MediaFormat detectMagic(byte[] magic) {
        if (magic.length < 4) {
            return UNKNOWN;
        }
        if (magic[0] == 'R' && magic[1] == 'I' && magic[2] == 'F' && magic[3] == 'F' && magic.length >= 12) {
            if (magic[8] == 'W' && magic[9] == 'A' && magic[10] == 'V' && magic[11] == 'E') {
                return WAV;
            }
            if (magic[8] == 'A' && magic[9] == 'V' && magic[10] == 'I' && magic[11] == ' ') {
                return AVI;
            }
        }
        if (magic[0] == 'F' && magic[1] == 'O' && magic[2] == 'R' && magic[3] == 'M'
                && magic.length >= 12 && magic[8] == 'A' && magic[9] == 'I' && magic[10] == 'F'
                && (magic[11] == 'F' || magic[11] == 'C')) {
            return AIFF;
        }
        if (magic[0] == '.' && magic[1] == 's' && magic[2] == 'n' && magic[3] == 'd') {
            return AU;
        }
        if (magic.length >= 8 && magic[4] == 'f' && magic[5] == 't' && magic[6] == 'y' && magic[7] == 'p') {
            return MP4; // ISO BMFF container (MP4/MOV)
        }
        if (magic[0] == (byte) 0x1A && magic[1] == (byte) 0x45 && magic[2] == (byte) 0xDF && magic[3] == (byte) 0xA3) {
            return MKV; // EBML container (MKV/WebM)
        }
        if (magic[0] == 0 && magic[1] == 0 && magic[2] == 1
                && ((magic[3] & 0xFF) == 0xBA || (magic[3] & 0xFF) == 0xB3)) {
            return MPEG; // MPEG program/video stream
        }
        if (magic[0] == 'O' && magic[1] == 'g' && magic[2] == 'g' && magic[3] == 'S') {
            return OGG;
        }
        if (magic[0] == 'f' && magic[1] == 'L' && magic[2] == 'a' && magic[3] == 'C') {
            return FLAC;
        }
        if (magic[0] == 'I' && magic[1] == 'D' && magic[2] == '3') {
            return MP3;
        }
        if ((magic[0] & 0xFF) == 0xFF && (magic[1] & 0xE0) == 0xE0) {
            return MP3; // MPEG frame sync word
        }
        return UNKNOWN;
    }
}
