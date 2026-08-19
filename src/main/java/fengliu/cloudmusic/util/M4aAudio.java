package fengliu.cloudmusic.util;

import net.sourceforge.jaad.SampleBuffer;
import net.sourceforge.jaad.aac.AudioDecoderInfo;
import net.sourceforge.jaad.aac.ChannelConfiguration;
import net.sourceforge.jaad.aac.Decoder;
import net.sourceforge.jaad.aac.Profile;
import net.sourceforge.jaad.aac.SampleFrequency;
import net.sourceforge.jaad.mp4.MP4Container;
import net.sourceforge.jaad.mp4.MP4InputStream;
import net.sourceforge.jaad.mp4.api.AudioTrack;
import net.sourceforge.jaad.mp4.api.Movie;
import net.sourceforge.jaad.mp4.api.Type;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decodes AAC audio stored in an MP4/M4A container into the Java Sound PCM stream used by the player. */
public final class M4aAudio {
    private static final Logger LOGGER = LoggerFactory.getLogger("cloudmusic");

    private M4aAudio() { }

    public static AudioInputStream open(File file) throws IOException {
        DecoderInputStream input = null;
        try {
            input = new DecoderInputStream(file, false);
            AudioFormat format = input.format();
            input.prime();
            return new AudioInputStream(input, format, AudioSystem.NOT_SPECIFIED);
        } catch (IOException | RuntimeException directFailure) {
            if (input != null) input.close();
            LOGGER.debug("[CloudMusic][Player] AAC metadata decoder failed for {}, retrying with MP4 track metadata", file.getName(), directFailure);
            try {
                input = new DecoderInputStream(file, true);
                AudioFormat format = input.format();
                input.prime();
                LOGGER.info("[CloudMusic][Player] AAC compatibility decoder selected for {}: {}", file.getName(), format);
                return new AudioInputStream(input, format, AudioSystem.NOT_SPECIFIED);
            } catch (IOException | RuntimeException compatibilityFailure) {
                if (input != null) input.close();
                compatibilityFailure.addSuppressed(directFailure);
                if (compatibilityFailure instanceof IOException ioException) throw ioException;
                throw (RuntimeException) compatibilityFailure;
            }
        }
    }

    public static long durationMs(File file) throws IOException {
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            MP4Container container = new MP4Container(MP4InputStream.open(randomAccessFile));
            return (long) (container.getMovie().getDuration() * 1000d);
        }
    }

    public static boolean isM4a(File file) {
        if (!file.isFile() || file.length() < 12) return false;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(4);
            byte[] marker = new byte[4];
            input.readFully(marker);
            return "ftyp".equals(new String(marker, StandardCharsets.US_ASCII));
        } catch (IOException ignored) {
            return false;
        }
    }

    private static final class DecoderInputStream extends InputStream {
        private final MP4InputStream input;
        private final AudioTrack track;
        private final Decoder decoder;
        private final SampleBuffer samples = new SampleBuffer();
        private byte[] decoded = new byte[0];
        private int position;

        private DecoderInputStream(File file, boolean useTrackMetadata) throws IOException {
            this.input = MP4InputStream.open(new RandomAccessFile(file, "r"));
            MP4Container container = new MP4Container(input);
            Movie movie = container.getMovie();
            this.track = movie.getTracks(Type.AUDIO).stream()
                    .filter(AudioTrack.class::isInstance)
                    .map(AudioTrack.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IOException("M4A file has no AAC audio track"));
            if (useTrackMetadata) {
                this.decoder = Decoder.create(new TrackAudioDecoderInfo(track));
            } else {
                this.decoder = Decoder.create(track.getDecoderSpecificInfo().getData());
            }
        }

        private AudioFormat format() {
            return decoder.getAudioFormat();
        }

        private void prime() throws IOException {
            decodeNextFrame();
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) return 0;
            int total = 0;
            while (total < length) {
                if (position >= decoded.length && !decodeNextFrame()) break;
                int copied = Math.min(length - total, decoded.length - position);
                System.arraycopy(decoded, position, buffer, offset + total, copied);
                position += copied;
                total += copied;
            }
            return total == 0 ? -1 : total;
        }

        private boolean decodeNextFrame() throws IOException {
            while (track.hasMoreFrames()) {
                decoder.decodeFrame(track.readNextFrame().getData(), samples);
                decoded = samples.getData();
                position = 0;
                if (decoded.length > 0) return true;
            }
            return false;
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    /**
     * Some CDN M4A files omit a usable AudioSpecificConfig. Their MP4 audio
     * track still contains enough information for regular AAC-LC playback.
     */
    private record TrackAudioDecoderInfo(AudioTrack track) implements AudioDecoderInfo {
        @Override
        public Profile getProfile() {
            return Profile.AAC_LC;
        }

        @Override
        public SampleFrequency getSampleFrequency() {
            return SampleFrequency.nominalFrequency(track.getSampleRate());
        }

        @Override
        public ChannelConfiguration getChannelConfiguration() {
            return ChannelConfiguration.forChannelCount(track.getChannelCount());
        }
    }
}
