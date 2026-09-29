package zcylas.totality.api.voice.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * The lifecycle every {@link RecognitionBackend} shares, so each engine only supplies its own
 * {@link #doLoad()}, {@link #doOpenSession} and {@link #doRelease()}.
 *
 * <ul>
 *   <li>{@link #load()} runs {@link #doLoad()} once, on the supplied executor (never the caller's
 *       thread), and records the resulting status. Any {@link Throwable} — including the
 *       {@code UnsatisfiedLinkError}/{@code NoClassDefFoundError} a missing native library throws —
 *       becomes {@code FAILED}; nothing propagates to the game.</li>
 *   <li>Sessions are tracked from open to close. {@link #close()} closes every still-open session
 *       <i>before</i> {@link #doRelease()} frees the engine, so no session outlives its model.</li>
 *   <li>Closing while a load is still running is safe: a load that finishes after close releases
 *       what it loaded and the backend stays {@code CLOSED}.</li>
 * </ul>
 *
 * <p>This cannot protect against a native library that aborts the whole process — no Java code can.
 * That case is handled by {@link NativeCrashGuard}.
 */
public abstract class ManagedRecognitionBackend implements RecognitionBackend {

    private final Executor loadExecutor;
    private final Object lock = new Object();
    private final Set<RecognitionSession> openSessions = ConcurrentHashMap.newKeySet();

    private volatile BackendStatus status = BackendStatus.UNLOADED;
    private CompletableFuture<BackendStatus> loadFuture;

    protected ManagedRecognitionBackend(Executor loadExecutor) {
        this.loadExecutor = loadExecutor;
    }

    /** Loads the engine. Runs on the load executor. Return READY or FAILED; may throw. */
    protected abstract BackendStatus doLoad() throws Exception;

    /**
     * Creates the engine session for {@code request}. Called with the backend READY. The session
     * must call {@link #sessionClosed(RecognitionSession)} exactly once when it closes.
     */
    protected abstract RecognitionSession doOpenSession(RecognitionRequest request) throws Exception;

    /** Frees the engine after every session is closed. Only called if {@link #doLoad()} returned READY. */
    protected abstract void doRelease();

    /**
     * Called when {@link #close()} happens while {@link #doLoad()} is still running (e.g. the client
     * is shutting down during model loading). The load keeps running and releases its result when
     * it returns; this hook lets an engine record that the interruption was orderly.
     */
    protected void onClosedWhileLoading() {}

    @Override
    public final BackendStatus status() {
        return status;
    }

    @Override
    public final CompletableFuture<BackendStatus> load() {
        synchronized (lock) {
            if (status.state() == BackendStatus.State.CLOSED) {
                return CompletableFuture.completedFuture(status);
            }
            if (loadFuture != null) return loadFuture;
            status = BackendStatus.LOADING;
            loadFuture = CompletableFuture.supplyAsync(this::runLoad, loadExecutor);
            return loadFuture;
        }
    }

    private BackendStatus runLoad() {
        BackendStatus result;
        try {
            result = doLoad();
            if (result == null) {
                result = BackendStatus.failed(BackendStatus.FailureReason.INTERNAL_ERROR, "doLoad returned null");
            }
        } catch (Throwable t) {
            result = BackendStatus.failed(BackendStatus.FailureReason.INTERNAL_ERROR, describe(t));
        }
        synchronized (lock) {
            if (status.state() == BackendStatus.State.CLOSED) {
                if (result.isReady()) releaseQuietly();
                return status;
            }
            status = result;
            return result;
        }
    }

    @Override
    public final RecognitionSession openSession(RecognitionRequest request) {
        if (request == null) throw new IllegalArgumentException("request");
        synchronized (lock) {
            if (!status.isReady()) {
                throw new RecognitionException("Recognition backend '" + id() + "' is not ready: " + status);
            }
            RecognitionSession session;
            try {
                session = doOpenSession(request);
            } catch (RecognitionException e) {
                throw e;
            } catch (Throwable t) {
                throw new RecognitionException("Could not open a " + request.mode() + " session: " + describe(t), t);
            }
            openSessions.add(session);
            return session;
        }
    }

    /** Called by a session when it closes. */
    protected final void sessionClosed(RecognitionSession session) {
        openSessions.remove(session);
    }

    /** Number of sessions opened and not yet closed. */
    public final int openSessionCount() {
        return openSessions.size();
    }

    @Override
    public final void close() {
        List<RecognitionSession> toClose;
        boolean release;
        boolean wasLoading;
        synchronized (lock) {
            if (status.state() == BackendStatus.State.CLOSED) return;
            release = status.isReady();
            wasLoading = status.state() == BackendStatus.State.LOADING;
            status = BackendStatus.CLOSED;
            toClose = new ArrayList<>(openSessions);
        }
        if (wasLoading) {
            try {
                onClosedWhileLoading();
            } catch (Throwable ignored) {
                // Best-effort bookkeeping during shutdown.
            }
        }
        for (RecognitionSession session : toClose) {
            try {
                session.close();
            } catch (Throwable ignored) {
                // Keep closing the rest; the engine is released below regardless.
            }
        }
        openSessions.clear();
        if (release) releaseQuietly();
    }

    private void releaseQuietly() {
        try {
            doRelease();
        } catch (Throwable ignored) {
            // Nothing useful to do while shutting down.
        }
    }

    protected static String describe(Throwable t) {
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
