package dev.blockandbeam.mediakit.api.media;

import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Plays a loaded {@link Media} on the default audio device. Client-side only. */
public final class MediaPlayer {
    public static final MediaPlayer INSTANCE = new MediaPlayer();

    private static final int BUFFER_SIZE = 4096;

    private volatile Media current;
    private volatile boolean playing;
    private volatile SourceDataLine line;
    private Thread thread;

    private MediaPlayer() {
    }

    /** Starts playing a media handle, stopping anything currently playing. */
    public void play(Media media) throws MediaException {
        play(media, 1.0f, false, 0.0f);
    }

    /**
     * Starts playing a media handle, stopping anything currently playing.
     *
     * @param volume  gain from 0.0 (mute) to 1.0 (full)
     * @param loop    whether to restart from the beginning when the media ends
     * @param start   playback start position in seconds
     */
    public void play(Media media, float volume, boolean loop, float start) throws MediaException {
        stop();
        AudioInputStream first = openPcm(media);
        SourceDataLine out = openLine(first.getFormat(), volume);
        this.line = out;
        this.current = media;
        this.playing = true;
        media.setState(MediaState.PLAYING);
        this.thread = new Thread(() -> stream(media, first, loop, start, out), "MediaKit Player");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Stops playback and releases the audio device. */
    public void stop() {
        playing = false;
        SourceDataLine openLine = line;
        if (openLine != null) {
            openLine.close();
        }
        Thread worker = thread;
        if (worker != null) {
            try {
                worker.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
        Media stopped = current;
        if (stopped != null) {
            stopped.setState(MediaState.STOPPED);
        }
        current = null;
    }

    /** The media currently loaded for playback, or {@code null}. */
    public Media current() {
        return current;
    }

    public boolean isPlaying() {
        return playing;
    }

    private void stream(Media media, AudioInputStream initial, boolean loop, float start, SourceDataLine out) {
        out.start();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            AudioInputStream next = initial;
            do {
                if (next == null) {
                    next = openPcm(media);
                }
                try (AudioInputStream pcm = next) {
                    next = null;
                    skipTo(pcm, start);
                    int read;
                    while (playing && (read = pcm.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            } while (playing && loop);
            if (playing) {
                out.drain();
            }
        } catch (MediaException | IOException e) {
            fail(media);
        } catch (IllegalArgumentException e) {
            // Line was closed by stop().
            if (playing) {
                fail(media);
            }
        } finally {
            out.stop();
            out.close();
            line = null;
        }
        if (playing && current == media) {
            playing = false;
            current = null;
            media.setState(MediaState.STOPPED);
        }
    }

    private AudioInputStream openPcm(Media media) throws MediaException {
        try {
            AudioInputStream in = AudioSystem.getAudioInputStream(media.file().toFile());
            AudioFormat base = in.getFormat();
            if (base.getSampleRate() <= 0 || base.getChannels() <= 0) {
                in.close();
                throw new MediaException("Unknown audio format: " + media.name());
            }
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(), 16, base.getChannels(),
                    base.getChannels() * 2, base.getSampleRate(), false);
            return base.matches(pcm) ? in : AudioSystem.getAudioInputStream(pcm, in);
        } catch (UnsupportedAudioFileException e) {
            throw new MediaException("Unsupported audio format: " + media.name(), e);
        } catch (IOException | IllegalArgumentException e) {
            throw new MediaException("Could not open " + media.name(), e);
        }
    }

    private SourceDataLine openLine(AudioFormat format, float volume) throws MediaException {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            line.open(format);
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                float clamped = Math.clamp(volume, 0.0f, 1.0f);
                float value = clamped <= 0.0f ? gain.getMinimum() : (float) (20.0 * Math.log10(clamped));
                gain.setValue(Math.clamp(value, gain.getMinimum(), gain.getMaximum()));
            }
            return line;
        } catch (LineUnavailableException e) {
            throw new MediaException("No audio device available", e);
        } catch (IllegalArgumentException e) {
            throw new MediaException("Audio format not supported: " + format, e);
        }
    }

    private void skipTo(AudioInputStream pcm, float start) throws IOException {
        if (start > 0.0f) {
            AudioFormat format = pcm.getFormat();
            long bytes = Math.round(start * format.getSampleRate() * format.getFrameSize());
            pcm.skip(bytes);
        }
    }

    private void fail(Media media) {
        if (playing && current == media) {
            playing = false;
            current = null;
            media.setState(MediaState.ERROR);
        }
    }
}
