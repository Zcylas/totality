package zcylas.totality.api.voice.audio;

/**
 * Mono signed 16-bit PCM audio, the only format a {@code RecognitionSession} accepts.
 */
public record PcmAudio(short[] samples, int sampleRate) {

    public double durationSeconds() {
        return sampleRate == 0 ? 0 : (double) samples.length / sampleRate;
    }
}
