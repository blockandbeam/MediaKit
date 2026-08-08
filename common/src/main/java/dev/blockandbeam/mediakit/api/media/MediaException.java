package dev.blockandbeam.mediakit.api.media;

/** Thrown when a media source cannot be loaded. */
public class MediaException extends Exception {
    private static final long serialVersionUID = 1L;

    public MediaException(String message) {
        super(message);
    }

    public MediaException(String message, Throwable cause) {
        super(message, cause);
    }
}
