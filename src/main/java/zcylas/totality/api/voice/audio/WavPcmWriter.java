package zcylas.totality.api.voice.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes mono signed 16-bit PCM as a WAV file (used only for opt-in debug recordings). */
public final class WavPcmWriter {

    private WavPcmWriter() {}

    public static byte[] encode(PcmAudio audio) {
        int dataSize = audio.samples().length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataSize).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(audio.sampleRate()).putInt(audio.sampleRate() * 2).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataSize);
        for (short s : audio.samples()) b.putShort(s);
        return b.array();
    }

    public static void write(Path file, PcmAudio audio) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, encode(audio));
    }
}
