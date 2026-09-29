package zcylas.totality.api.voice.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a RIFF/WAVE file into {@link PcmAudio} for recognition.
 *
 * <p>Accepted: uncompressed PCM (format tag 1, or WAVE_FORMAT_EXTENSIBLE with a PCM sub-format),
 * signed 16-bit, 1 or 2 channels (stereo is averaged to mono), at exactly {@link #REQUIRED_SAMPLE_RATE}.
 * Everything else is rejected with a message saying what was found, rather than resampled, so a
 * recording made at the wrong rate is never silently decoded badly. Record test clips with e.g.
 * {@code pw-record --rate 16000 --channels 1 --format s16 clip.wav}.
 *
 * <p>Pure Java on purpose: {@code javax.sound} lives in the {@code java.desktop} module, which this
 * code does not want to depend on.
 */
public final class WavPcmReader {

    public static final int REQUIRED_SAMPLE_RATE = 16_000;
    /** Upper bound so a bogus file cannot allocate unbounded memory. */
    public static final int MAX_SECONDS = 300;
    public static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    private static final int FORMAT_PCM = 1;
    private static final int FORMAT_EXTENSIBLE = 0xFFFE;
    private static final long SIZE_UNKNOWN = 0xFFFFFFFFL;

    private WavPcmReader() {}

    public static PcmAudio read(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_FILE_BYTES) {
            throw new UnsupportedAudioException("WAV file too large: " + size + " bytes (limit " + MAX_FILE_BYTES + ")");
        }
        return read(Files.readAllBytes(file));
    }

    public static PcmAudio read(byte[] bytes) throws UnsupportedAudioException {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || !"RIFF".equals(fourCc(buf, 0)) || !"WAVE".equals(fourCc(buf, 8))) {
            throw new UnsupportedAudioException("Not a RIFF/WAVE file");
        }

        int formatTag = -1, channels = 0, sampleRate = 0, blockAlign = 0, bitsPerSample = 0;
        int pos = 12;
        while (pos + 8 <= bytes.length) {
            String id = fourCc(buf, pos);
            long chunkSize = Integer.toUnsignedLong(buf.getInt(pos + 4));
            int body = pos + 8;
            long available = bytes.length - body;

            if (id.equals("fmt ")) {
                if (chunkSize < 16 || chunkSize > available) {
                    throw new UnsupportedAudioException("Malformed fmt chunk (size " + chunkSize + ")");
                }
                formatTag = Short.toUnsignedInt(buf.getShort(body));
                channels = Short.toUnsignedInt(buf.getShort(body + 2));
                sampleRate = buf.getInt(body + 4);
                blockAlign = Short.toUnsignedInt(buf.getShort(body + 12));
                bitsPerSample = Short.toUnsignedInt(buf.getShort(body + 14));
                if (formatTag == FORMAT_EXTENSIBLE) {
                    if (chunkSize < 40) throw new UnsupportedAudioException("Malformed WAVE_FORMAT_EXTENSIBLE fmt chunk");
                    formatTag = Short.toUnsignedInt(buf.getShort(body + 24)); // first two bytes of the sub-format GUID
                }
            } else if (id.equals("data")) {
                if (formatTag < 0) throw new UnsupportedAudioException("data chunk appears before fmt chunk");
                validateFormat(formatTag, channels, sampleRate, blockAlign, bitsPerSample);
                long dataSize = chunkSize;
                if (chunkSize == 0 || chunkSize == SIZE_UNKNOWN) {
                    // Streaming recorders that were interrupted before finalizing the header leave 0 or
                    // 0xFFFFFFFF here: the audio is everything that follows.
                    dataSize = available;
                } else if (chunkSize > available) {
                    throw new UnsupportedAudioException("WAV data truncated: header declares " + chunkSize
                            + " bytes, file has " + available);
                }
                return decode(buf, body, dataSize, channels, sampleRate);
            }
            // Chunks are word-aligned; skip the pad byte after an odd-sized chunk.
            long next = body + chunkSize + (chunkSize & 1);
            if (next > bytes.length) break;
            pos = (int) next;
        }
        throw new UnsupportedAudioException(formatTag < 0 ? "No fmt chunk found" : "No data chunk found");
    }

    private static void validateFormat(int formatTag, int channels, int sampleRate, int blockAlign, int bits)
            throws UnsupportedAudioException {
        if (formatTag != FORMAT_PCM) {
            throw new UnsupportedAudioException("Unsupported WAV encoding (format tag " + formatTag + "); need PCM");
        }
        if (bits != 16) {
            throw new UnsupportedAudioException("Unsupported sample size " + bits + "-bit; need 16-bit PCM");
        }
        if (channels != 1 && channels != 2) {
            throw new UnsupportedAudioException("Unsupported channel count " + channels + "; need mono or stereo");
        }
        if (sampleRate != REQUIRED_SAMPLE_RATE) {
            throw new UnsupportedAudioException("Unsupported sample rate " + sampleRate + " Hz; need "
                    + REQUIRED_SAMPLE_RATE + " Hz");
        }
        if (blockAlign != channels * 2) {
            throw new UnsupportedAudioException("Inconsistent block alignment " + blockAlign + " for " + channels
                    + " channel(s) of 16-bit audio");
        }
    }

    private static PcmAudio decode(ByteBuffer buf, int offset, long dataSize, int channels, int sampleRate)
            throws UnsupportedAudioException {
        long frames = dataSize / (2L * channels);
        if (frames > (long) MAX_SECONDS * sampleRate) {
            throw new UnsupportedAudioException("WAV too long: " + (frames / sampleRate) + " s (limit " + MAX_SECONDS + " s)");
        }
        short[] samples = new short[(int) frames];
        int p = offset;
        for (int i = 0; i < samples.length; i++) {
            if (channels == 1) {
                samples[i] = buf.getShort(p);
                p += 2;
            } else {
                samples[i] = (short) ((buf.getShort(p) + buf.getShort(p + 2)) / 2);
                p += 4;
            }
        }
        return new PcmAudio(samples, sampleRate);
    }

    private static String fourCc(ByteBuffer buf, int at) {
        byte[] id = new byte[4];
        for (int i = 0; i < 4; i++) id[i] = buf.get(at + i);
        return new String(id, StandardCharsets.US_ASCII);
    }
}
