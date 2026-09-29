package zcylas.totality.api.voice.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class WavPcmReaderTest {

    /** Builds a WAV with the given fmt fields and raw data bytes; optional extra chunk before data. */
    static byte[] wav(int formatTag, int channels, int rate, int bits, byte[] data, boolean extraChunk, long declaredDataSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int blockAlign = channels * bits / 8;
        ByteBuffer fmt = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        fmt.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) formatTag)
                .putShort((short) channels).putInt(rate).putInt(rate * blockAlign).putShort((short) blockAlign)
                .putShort((short) bits);
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32(0));
        out.writeBytes("WAVE".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(fmt.array());
        if (extraChunk) {
            out.writeBytes("LIST".getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(le32(3));
            out.writeBytes(new byte[]{1, 2, 3, 0}); // odd size + pad byte
        }
        out.writeBytes("data".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(le32((int) declaredDataSize));
        out.writeBytes(data);
        return out.toByteArray();
    }

    static byte[] wav(int channels, int rate, int bits, short... samples) {
        ByteBuffer d = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : samples) d.putShort(s);
        return wav(1, channels, rate, bits, d.array(), false, d.capacity());
    }

    private static byte[] le32(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    @Test
    void readsMono16kPcm() throws Exception {
        PcmAudio a = WavPcmReader.read(wav(1, 16_000, 16, (short) 1, (short) -2, (short) 32767, (short) -32768));
        assertEquals(16_000, a.sampleRate());
        assertArrayEquals(new short[]{1, -2, 32767, -32768}, a.samples());
        assertEquals(4 / 16_000.0, a.durationSeconds(), 1e-12);
    }

    @Test
    void downmixesStereoAndSkipsUnknownChunks() throws Exception {
        ByteBuffer d = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        d.putShort((short) 100).putShort((short) 300).putShort((short) -10).putShort((short) 10);
        PcmAudio a = WavPcmReader.read(wav(1, 2, 16_000, 16, d.array(), true, 8));
        assertArrayEquals(new short[]{200, 0}, a.samples());
    }

    @Test
    void acceptsStreamingRecorderSizePlaceholders() throws Exception {
        byte[] data = new byte[3200];
        assertEquals(1600, WavPcmReader.read(wav(1, 1, 16_000, 16, data, false, 0xFFFFFFFFL)).samples().length);
        assertEquals(1600, WavPcmReader.read(wav(1, 1, 16_000, 16, data, false, 0)).samples().length);
    }

    @Test
    void rejectsUnsupportedFormatsWithAReason() {
        assertMessage("sample rate 44100", wav(1, 44_100, 16, new short[10]));
        assertMessage("sample rate 8000", wav(1, 8_000, 16, new short[10]));
        assertMessage("8-bit", wav(1, 1, 16_000, 8, new byte[10], false, 10));
        assertMessage("format tag 3", wav(3, 1, 16_000, 32, new byte[16], false, 16));
        assertMessage("channel count 6", wav(1, 6, 16_000, 16, new byte[24], false, 24));
    }

    @Test
    void rejectsMalformedInput() {
        assertMessage("Not a RIFF/WAVE", new byte[0]);
        assertMessage("Not a RIFF/WAVE", "hello, this is not audio".getBytes(StandardCharsets.US_ASCII));
        assertMessage("No fmt chunk", "RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.US_ASCII));
        byte[] valid = wav(1, 16_000, 16, new short[100]);
        assertMessage("truncated", Arrays.copyOf(valid, valid.length - 50));
        byte[] noData = Arrays.copyOf(valid, 36); // header + fmt only
        assertMessage("No data chunk", noData);
    }

    private static void assertMessage(String expected, byte[] bytes) {
        UnsupportedAudioException e = assertThrows(UnsupportedAudioException.class, () -> WavPcmReader.read(bytes));
        assertTrue(e.getMessage().contains(expected), () -> "expected '" + expected + "' in: " + e.getMessage());
    }

    @Test
    void writerOutputReadsBackIdentically() throws Exception {
        short[] samples = {0, 1, -1, 32767, -32768, 1234};
        byte[] wav = WavPcmWriter.encode(new PcmAudio(samples, 16_000));
        PcmAudio back = WavPcmReader.read(wav);
        assertArrayEquals(samples, back.samples());
        assertEquals(16_000, back.sampleRate());
    }
}
