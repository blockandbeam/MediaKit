package dev.blockandbeam.mediakit.api.media;

/** Formats media names as {@code Uploader - Title}, deduplicating a matching artist tag in the title. */
public final class MediaName {
    private MediaName() {
    }

    /** Composes a display name from a title and its uploader, e.g. {@code "Weird Al Yankovic - Polkamania!"}. */
    public static String of(String title, String uploader) {
        if (uploader == null || uploader.isBlank()) {
            return title;
        }
        return uploader + " - " + stripRedundantArtist(title, uploader);
    }

    /** Strips a "{@code Artist - }" prefix or "{@code - Artist}" suffix from the title when it matches the uploader. */
    private static String stripRedundantArtist(String title, String uploader) {
        String prefix = uploader + " - ";
        if (title.regionMatches(true, 0, prefix, 0, prefix.length())) {
            String stripped = title.substring(prefix.length()).trim();
            return stripped.isBlank() ? title : stripped;
        }
        String suffix = " - " + uploader;
        if (title.length() > suffix.length()
                && title.regionMatches(true, title.length() - suffix.length(), suffix, 0, suffix.length())) {
            String stripped = title.substring(0, title.length() - suffix.length()).trim();
            return stripped.isBlank() ? title : stripped;
        }
        return title;
    }
}
