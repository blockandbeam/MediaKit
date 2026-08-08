package dev.blockandbeam.mediakit.api.media.source;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import dev.blockandbeam.mediakit.api.Http;
import dev.blockandbeam.mediakit.api.media.MediaException;
import dev.blockandbeam.mediakit.api.media.MediaName;
import dev.blockandbeam.mediakit.api.media.MediaSource;

/** Resolves SoundCloud track URLs to temporary signed streams. Blocking; call off the main thread. */
public final class SoundCloudSource implements MediaSource {
    private static final String API = "https://api-v2.soundcloud.com";
    private static final Component BRAND = Component.literal("SoundCloud")
            .withStyle(style -> style.withColor(TextColor.fromRgb(0xFF5500)));

    @Override
    public boolean supports(URI uri) {
        String host = uri.getHost();
        return host != null
                && (host.equals("soundcloud.com") || host.endsWith(".soundcloud.com")
                        || host.equals("soundcloud.app.goog"));
    }

    @Override
    public Optional<Component> brand() {
        return Optional.of(BRAND);
    }

    @Override
    public ResolvedSource resolve(URI url) throws MediaException {
        try {
            return doResolve(url);
        } catch (AuthException e) {
            SoundCloudIdTracker.invalidate(); // The id rotated; re-scrape and retry once.
            try {
                return doResolve(url);
            } catch (AuthException retry) {
                throw new MediaException("SoundCloud rejected the client id", retry);
            }
        }
    }

    private ResolvedSource doResolve(URI url) throws MediaException, AuthException {
        URI canonical = canonical(url);
        String id = SoundCloudIdTracker.fetch();
        String encoded = URLEncoder.encode(canonical.toString(), StandardCharsets.UTF_8);
        JsonObject track = object(API + "/resolve?url=" + encoded + "&client_id=" + id);

        JsonElement kind = track.get("kind");
        if (kind == null || !"track".equals(kind.getAsString())) {
            throw new MediaException("Not a single SoundCloud track: " + canonical);
        }

        String title = track.get("title").getAsString();
        String artist = null;
        JsonElement userElement = track.get("user");
        if (userElement != null && userElement.isJsonObject()) {
            JsonObject user = userElement.getAsJsonObject();
            if (user.has("username")) {
                artist = user.get("username").getAsString();
            }
        }
        String name = MediaName.of(title, artist);
        long duration = track.has("duration") ? track.get("duration").getAsLong() : -1;

        JsonObject media = track.has("media") ? track.getAsJsonObject("media") : null;
        if (media == null || !media.has("transcodings")) {
            throw new MediaException("No playable stream for SoundCloud track '" + title + "'");
        }
        String transcodingUrl = null;
        for (JsonElement element : media.getAsJsonArray("transcodings")) {
            JsonObject transcoding = element.getAsJsonObject();
            if ("progressive".equals(transcoding.getAsJsonObject("format").get("protocol").getAsString())) {
                transcodingUrl = transcoding.get("url").getAsString();
                break;
            }
        }
        if (transcodingUrl == null) {
            throw new MediaException("No progressive stream for SoundCloud track '" + title + "'");
        }

        JsonObject stream = object(transcodingUrl + "?client_id=" + id);
        return new ResolvedSource(name, URI.create(stream.get("url").getAsString()), duration);
    }

    /** Short links redirect to the track page; the resolve API wants the canonical URL. */
    private static URI canonical(URI url) throws MediaException {
        String host = url.getHost();
        if (host == null || (!host.equals("on.soundcloud.com") && !host.equals("soundcloud.app.goog"))) {
            return url;
        }
        Http.Redirect redirect = Http.redirect(url)
                .orElseThrow(() -> new MediaException("Could not reach " + url));
        if (redirect.status() == 404) {
            throw new MediaException("The SoundCloud link is dead or expired (HTTP 404): " + url);
        }
        if (redirect.status() != 200) {
            throw new MediaException("SoundCloud link returned HTTP " + redirect.status() + " for " + url);
        }
        return redirect.uri();
    }

    private static JsonObject object(String url) throws MediaException, AuthException {
        String body = getBody(url);
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new MediaException("Bad response from SoundCloud", e);
        }
    }

    private static String getBody(String url) throws MediaException, AuthException {
        Http.Text response = Http.getText(url).orElseThrow(() -> new MediaException("Could not reach " + url));
        if (response.status() == 401 || response.status() == 403) {
            throw new AuthException();
        }
        if (response.status() == 404) {
            throw new MediaException("No SoundCloud track found at " + url + " (HTTP 404)");
        }
        if (!response.isSuccess()) {
            throw new MediaException("SoundCloud API returned HTTP " + response.status() + " for " + url);
        }
        return response.body();
    }

    private static final class AuthException extends Exception {
    }
}
