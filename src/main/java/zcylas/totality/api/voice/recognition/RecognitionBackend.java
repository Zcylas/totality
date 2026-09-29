package zcylas.totality.api.voice.recognition;

import java.util.concurrent.CompletableFuture;

/**
 * A replaceable speech-recognition engine (Vosk today; possibly sherpa-onnx later). Consumers depend
 * only on this interface, {@link RecognitionSession} and {@link Transcript} — never on engine types.
 *
 * <p>Lifecycle: {@code UNLOADED → load() → LOADING → READY | FAILED}, then {@code close() → CLOSED}.
 * Loading runs off the calling thread and is idempotent. A FAILED backend stays failed; it never
 * throws into its caller and never blocks gameplay.
 */
public interface RecognitionBackend extends AutoCloseable {

    /** Stable identifier, e.g. {@code "vosk"}. */
    String id();

    BackendStatus status();

    /** Starts loading if not already started; the future completes with the resulting status. */
    CompletableFuture<BackendStatus> load();

    /**
     * Opens a new session. Requires {@link BackendStatus.State#READY}.
     *
     * @throws RecognitionException if the backend is not ready or the engine rejects the request
     */
    RecognitionSession openSession(RecognitionRequest request);

    /** Closes every open session and releases the engine. Idempotent. */
    @Override
    void close();
}
