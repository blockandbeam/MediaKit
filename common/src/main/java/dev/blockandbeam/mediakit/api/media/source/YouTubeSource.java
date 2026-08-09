package dev.blockandbeam.mediakit.api.media.source;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import dev.blockandbeam.mediakit.api.YtDlp;
import dev.blockandbeam.mediakit.api.media.MediaException;
import dev.blockandbeam.mediakit.api.media.MediaName;
import dev.blockandbeam.mediakit.api.media.MediaSource;

/**
 * Resolves YouTube video URLs to a direct audio stream via yt-dlp. Blocking;
 * call off the main thread.
 */
public final class YouTubeSource implements MediaSource {
    private static final Component BRAND = Component.literal("YouTube")
            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF0000)));

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        String lower = host.toLowerCase();
        return lower.equals("youtube.com") || lower.endsWith(".youtube.com")
                || lower.equals("youtu.be") || lower.equals("youtube-nocookie.com")
                || lower.endsWith(".youtube-nocookie.com");
    }

    @Override
    public Optional<Component> brand() {
        return Optional.of(BRAND);
    }

    @Override
    public ResolvedSource resolve(URI url) throws MediaException {
        JsonObject info;
        try {
            String json = YtDlp.dumpJson(url);
            JsonElement element = JsonParser.parseString(json);
            if (!element.isJsonObject()) {
                throw new MediaException("yt-dlp returned no metadata for " + url);
            }
            info = element.getAsJsonObject();
        } catch (IOException e) {
            throw new MediaException("Could not resolve YouTube video: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new MediaException("Bad response from yt-dlp for " + url, e);
        }

        if (!info.has("url")) {
            throw new MediaException("No playable stream for YouTube video '" + title(info) + "'");
        }
        String title = title(info);
        String uploader = info.has("uploader") && !info.get("uploader").isJsonNull()
                ? info.get("uploader").getAsString() : null;
        long duration = info.has("duration") && !info.get("duration").isJsonNull()
                ? Math.round(info.get("duration").getAsDouble() * 1000.0) : -1;
        try {
            return new ResolvedSource(MediaName.of(title, uploader),
                    URI.create(info.get("url").getAsString()), duration);
        } catch (IllegalArgumentException e) {
            throw new MediaException("yt-dlp returned a malformed stream URL for " + url, e);
        }
    }

    private static String title(JsonObject info) {
        return info.has("title") && !info.get("title").isJsonNull()
                ? info.get("title").getAsString() : "YouTube video";
    }
}
