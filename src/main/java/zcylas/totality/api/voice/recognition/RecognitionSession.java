package zcylas.totality.api.voice.recognition;

/**
 * One utterance being recognized. A session is created fresh for every utterance (its request —
 * mode and phrases — is fixed at creation and never changes), is used by a single thread, and must
 * be closed; {@code close()} is idempotent.
 *
 * <p>Audio is always 16 kHz, mono, signed 16-bit PCM ({@link #SAMPLE_RATE}).
 */
public interface RecognitionSession extends AutoCloseable {

    int SAMPLE_RATE = 16_000;

    RecognitionRequest request();

    /** Feeds {@code count} samples from {@code samples}. Not allowed after {@link #finish()}. */
    void acceptAudio(short[] samples, int count);

    /** Best current guess while audio is still arriving; may change. Empty when nothing yet. */
    String partialText();

    /** Ends the utterance and returns its transcript. Can be called once. */
    Transcript finish();

    boolean isOpen();

    @Override
    void close();
}
