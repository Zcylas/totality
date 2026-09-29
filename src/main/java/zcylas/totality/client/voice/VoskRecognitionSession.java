package zcylas.totality.client.voice;

import org.vosk.Recognizer;
import zcylas.totality.api.voice.recognition.RecognitionException;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.function.Consumer;

/**
 * One Vosk {@link Recognizer}, owned exclusively by this session and freed exactly once.
 *
 * <p>The recognizer's grammar is fixed at construction and never changed: Vosk's
 * {@code vosk_recognizer_set_grm} throws a C++ exception on a running recognizer, which aborts the
 * JVM. Every method is synchronized, so {@link #close()} from another thread (e.g. backend
 * shutdown) waits for an in-flight native call instead of freeing the recognizer under it.
 */
final class VoskRecognitionSession implements RecognitionSession {

    private final Recognizer recognizer;
    private final RecognitionRequest request;
    private final Consumer<RecognitionSession> onClose;
    private boolean finished;
    private boolean closed;

    VoskRecognitionSession(Recognizer recognizer, RecognitionRequest request, Consumer<RecognitionSession> onClose) {
        this.recognizer = recognizer;
        this.request = request;
        this.onClose = onClose;
    }

    @Override
    public RecognitionRequest request() {
        return request;
    }

    @Override
    public synchronized void acceptAudio(short[] samples, int count) {
        ensureOpen();
        if (finished) throw new RecognitionException("Session already finished");
        if (count < 0 || count > samples.length) throw new IllegalArgumentException("count " + count);
        if (count > 0) recognizer.acceptWaveForm(samples, count);
    }

    @Override
    public synchronized String partialText() {
        ensureOpen();
        return finished ? "" : VoskResultParser.partialText(recognizer.getPartialResult());
    }

    @Override
    public synchronized Transcript finish() {
        ensureOpen();
        if (finished) throw new RecognitionException("Session already finished");
        finished = true;
        return VoskResultParser.parseFinal(recognizer.getFinalResult(), request);
    }

    @Override
    public synchronized boolean isOpen() {
        return !closed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            recognizer.close();
        } finally {
            onClose.accept(this);
        }
    }

    private void ensureOpen() {
        if (closed) throw new RecognitionException("Session is closed");
    }
}
