package dev.blockandbeam.mediakit.api.media.source;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.blockandbeam.mediakit.api.Http;
import dev.blockandbeam.mediakit.api.media.MediaException;

/** Scrapes SoundCloud's client id from its site and caches it. Blocking; call off the main thread. */
public final class SoundCloudIdTracker {
    private static final String HOME = "https://soundcloud.com/";
    private static final Pattern APP_SCRIPT = Pattern.compile("https://[A-Za-z0-9-.]+/assets/[a-f0-9-]+\\.js");
    private static final Pattern CLIENT_ID = Pattern.compile(",client_id:\"([a-zA-Z0-9-_]+)\"");

    private static volatile String currentId;

    private SoundCloudIdTracker() {
    }

    /** The current client id, scraping it from SoundCloud's site if needed. */
    public static String fetch() throws MediaException {
        if (currentId != null) {
            return currentId;
        }
        String flag = System.getProperty("mediakit.soundcloud.client-id");
        if (flag != null && !flag.isBlank()) {
            currentId = flag;
            return currentId;
        }
        String found = scrape();
        if (found == null) {
            throw new MediaException("Could not find a SoundCloud client id (set -Dmediakit.soundcloud.client-id)");
        }
        currentId = found;
        return found;
    }

    /** Drops the cached id so the next fetch re-scrapes. */
    public static void invalidate() {
        currentId = null;
    }

    private static String scrape() throws MediaException {
        Optional<Http.Text> page = Http.getText(HOME);
        if (page.isEmpty()) {
            return null;
        }
        Matcher matcher = APP_SCRIPT.matcher(page.get().body());
        String script = null;
        for (int i = 0; matcher.find() && i < 9; i++) {
            script = matcher.group();
        }
        if (script == null) {
            return null;
        }
        Matcher idMatcher = CLIENT_ID.matcher(Http.getText(script).map(Http.Text::body).orElse(""));
        return idMatcher.find() ? idMatcher.group(1) : null;
    }
}
