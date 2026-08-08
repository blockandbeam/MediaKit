package dev.blockandbeam.mediakit.api.media;

/** Lifecycle state of a {@link Media} handle. */
public enum MediaState {
    IDLE,
    LOADING,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR
}
