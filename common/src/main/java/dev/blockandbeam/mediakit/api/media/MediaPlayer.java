package dev.blockandbeam.mediakit.api.media;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import dev.blockandbeam.mediakit.api.FFmpeg;

/** Plays a loaded {@link Media} on the default audio device. Client-side only. */
public final class MediaPlayer {
    public static final MediaPlayer INSTANCE = new MediaPlayer();

    private static final int BUFFER_SIZE = 4096;
    private static final AudioFormat STREAM_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            44100, 16, 2, 4, 44100, false);

    private volatile Media current;
    private volatile boolean playing;
    private volatile SourceDataLine line;
    private volatile PlaybackSource source;
    private Thread thread;

    private MediaPlayer() {
    }

    /** Starts playing a media handle, stopping anything currently playing. */
    public void play(Media media) throws MediaException {
        play(media, 1.0f, false, 0.0f);
    }

    /** Starts playing a media handle with the given volume, loop, and start offset. */
    public void play(Media media, float volume, boolean loop, float start) throws MediaException {
        stop();
        PlaybackSource opened = openSource(media);
        SourceDataLine out;
        try {
            out = openLine(opened.format(), volume);
        } catch (MediaException e) {
            opened.close();
            throw e;
        }
        this.line = out;
        this.source = opened;
        this.current = media;
        this.playing = true;
        media.setState(MediaState.PLAYING);
        this.thread = new Thread(() -> stream(media, opened, loop, start, out), "MediaKit Player");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** Stops playback and releases the audio device. */
    public void stop() {
        playing = false;
        PlaybackSource openSource = source;
        source = null;
        if (openSource != null) {
            openSource.close();
        }
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

    /** Whether media is currently playing. */
    public boolean isPlaying() {
        return playing;
    }

    private void stream(Media media, PlaybackSource source, boolean loop, float start, SourceDataLine out) {
        out.start();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            do {
                try (AudioInputStream pcm = source.openPcm(start)) {
                    int read;
                    while (playing && (read = pcm.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    if (source.failed()) {
                        throw new IOException("Stream ended unexpectedly");
                    }
                }
            } while (playing && loop);
            if (playing) {
                out.drain();
            }
        } catch (MediaException | IOException e) {
            fail(media);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Line was closed by stop().
            if (playing) {
                fail(media);
            }
        } finally {
            source.close();
            out.stop();
            out.close();
            line = null;
            this.source = null;
        }
        if (playing && current == media) {
            playing = false;
            current = null;
            media.setState(MediaState.STOPPED);
        }
    }

    private PlaybackSource openSource(Media media) throws MediaException {
        URI stream = media.stream();
        if (stream != null) {
            return new StreamPlayback(stream);
        }
        LocalPlayback local = new LocalPlayback(media.file());
        try {
            local.format();
            return local;
        } catch (MediaException e) {
            if (!(e.getCause() instanceof UnsupportedAudioFileException)) {
                throw e;
            }
            return new LocalPlayback(transcode(media));
        }
    }

    /** Transcodes the media to a cached WAV, reusing an existing transcode when possible. */
    private Path transcode(Media media) throws MediaException {
        Path source = media.file();
        try {
            Path dir = MediaAPI.transcodeDir();
            String name = source.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String stem = dot > 0 ? name.substring(0, dot) : name;
            Path target = dir.resolve(stem + "-" + source.toFile().lastModified() + ".wav");
            if (!Files.exists(target)) {
                FFmpeg.transcode(source, target);
            }
            return target;
        } catch (IOException e) {
            throw new MediaException("Could not transcode " + media.name(), e);
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

    private void fail(Media media) {
        if (playing && current == media) {
            playing = false;
            current = null;
            media.setState(MediaState.ERROR);
        }
    }

    /** A source of PCM audio for the output line, reopened once per loop iteration. */
    private interface PlaybackSource {
        /** The format of the PCM produced by {@link #openPcm(float)}. */
        AudioFormat format() throws MediaException;

        /** Opens a fresh audio stream, seeking to {@code start} seconds in. */
        AudioInputStream openPcm(float start) throws MediaException;

        /** Whether the last {@link #openPcm(float)} stream ended in an error. */
        default boolean failed() {
            return false;
        }

        /** Releases any resources held by this source (e.g. kills a spawned process). */
        default void close() {
        }
    }

    /** Plays a local file, decoding with Java Sound and falling back to a pre-transcoded WAV. */
    private static final class LocalPlayback implements PlaybackSource {
        private final Path file;

        LocalPlayback(Path file) {
            this.file = file;
        }

        @Override
        public AudioFormat format() throws MediaException {
            try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
                AudioFormat base = in.getFormat();
                if (base.getSampleRate() <= 0 || base.getChannels() <= 0) {
                    throw new MediaException("Unknown audio format: " + file.getFileName());
                }
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        base.getSampleRate(), 16, base.getChannels(),
                        base.getChannels() * 2, base.getSampleRate(), false);
            } catch (UnsupportedAudioFileException e) {
                throw new MediaException("Unsupported audio format: " + file.getFileName(), e);
            } catch (IOException | IllegalArgumentException e) {
                throw new MediaException("Could not open " + file.getFileName(), e);
            }
        }

        @Override
        public AudioInputStream openPcm(float start) throws MediaException {
            AudioInputStream in;
            try {
                in = AudioSystem.getAudioInputStream(file.toFile());
            } catch (UnsupportedAudioFileException e) {
                throw new MediaException("Unsupported audio format: " + file.getFileName(), e);
            } catch (IOException | IllegalArgumentException e) {
                throw new MediaException("Could not open " + file.getFileName(), e);
            }
            try {
                if (start > 0.0f) {
                    AudioFormat format = in.getFormat();
                    long bytes = Math.round(start * format.getSampleRate() * format.getFrameSize());
                    in.skip(bytes);
                }
            } catch (IOException e) {
                try {
                    in.close();
                } catch (IOException ignored) {
                    e.addSuppressed(ignored);
                }
                throw new MediaException("Could not seek in " + file.getFileName(), e);
            }
            return in;
        }
    }

    /**
     * Streams a remote URL by piping ffmpeg's raw PCM output to the audio line,
     * so playback starts without downloading the whole file.
     */
    private static final class StreamPlayback implements PlaybackSource {
        private final URI stream;
        private volatile Process process;

        StreamPlayback(URI stream) {
            this.stream = stream;
        }

        @Override
        public AudioFormat format() {
            return STREAM_FORMAT;
        }

        @Override
        public AudioInputStream openPcm(float start) throws MediaException {
            try {
                List<String> args = new ArrayList<>(List.of(
                        FFmpeg.resolve().toString(), "-nostdin", "-hide_banner", "-loglevel", "error"));
                if (start > 0.0f) {
                    args.add("-ss");
                    args.add(String.valueOf(start));
                }
                args.addAll(List.of("-i", stream.toString(), "-vn", "-ac", "2", "-ar", "44100",
                        "-c:a", "pcm_s16le", "-f", "s16le", "-"));
                Process proc = new ProcessBuilder(args)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
                this.process = proc;
                return new AudioInputStream(proc.getInputStream(), STREAM_FORMAT, AudioSystem.NOT_SPECIFIED);
            } catch (IOException e) {
                throw new MediaException("Could not start ffmpeg for " + stream, e);
            }
        }

        @Override
        public boolean failed() {
            Process proc = process;
            if (proc == null) {
                return false;
            }
            // Wait briefly so a just-exited process is reaped before checking the
            // exit status; isAlive() can otherwise still report true in the window
            // between the process dying and the JVM observing the exit.
            try {
                proc.waitFor(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return !proc.isAlive() && proc.exitValue() != 0;
        }

        @Override
        public void close() {
            Process proc = process;
            if (proc != null) {
                proc.destroy();
                process = null;
            }
        }
    }
}
