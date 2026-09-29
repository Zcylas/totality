package zcylas.totality.api.voice.recognition;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Lifecycle contract shared by every recognition backend, exercised through an injected fake. */
class ManagedRecognitionBackendTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "voice-test-loader"));

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static BackendStatus await(CompletableFuture<BackendStatus> f) throws Exception {
        return f.get(10, TimeUnit.SECONDS);
    }

    @Test
    void startsUnloadedAndRefusesSessions() {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        assertEquals(BackendStatus.State.UNLOADED, backend.status().state());
        assertThrows(RecognitionException.class, () -> backend.openSession(RecognitionRequest.freeform()));
        assertEquals(0, backend.loadCalls, "constructing a backend must not load anything");
    }

    @Test
    void loadRunsOnceOffTheCallingThread() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        CompletableFuture<BackendStatus> first = backend.load();
        CompletableFuture<BackendStatus> second = backend.load();
        assertSame(first, second);
        assertTrue(await(first).isReady());
        assertEquals(1, backend.loadCalls);
        assertEquals("voice-test-loader", backend.loadThreadName);
        assertNotEquals(Thread.currentThread().getName(), backend.loadThreadName);
    }

    @Test
    void statusIsLoadingWhileLoadIsInProgress() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        backend.loadGate = new CountDownLatch(1);
        CompletableFuture<BackendStatus> f = backend.load();
        assertEquals(BackendStatus.State.LOADING, backend.status().state());
        assertThrows(RecognitionException.class, () -> backend.openSession(RecognitionRequest.freeform()));
        backend.loadGate.countDown();
        assertTrue(await(f).isReady());
    }

    @Test
    void nativeLinkErrorDuringLoadBecomesFailedNotACrash() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        backend.loadBehavior = () -> {
            throw new UnsatisfiedLinkError("libvosk not found");
        };
        BackendStatus s = await(backend.load());
        assertEquals(BackendStatus.State.FAILED, s.state());
        assertEquals(BackendStatus.FailureReason.INTERNAL_ERROR, s.reason());
        assertTrue(s.detail().contains("libvosk not found"));
        assertThrows(RecognitionException.class, () -> backend.openSession(RecognitionRequest.freeform()));
    }

    @Test
    void reportedFailureIsKeptAndNotRetried() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        backend.loadBehavior = () -> BackendStatus.failed(BackendStatus.FailureReason.MODEL_INVALID, "bad model");
        assertEquals(BackendStatus.FailureReason.MODEL_INVALID, await(backend.load()).reason());
        assertEquals(BackendStatus.FailureReason.MODEL_INVALID, await(backend.load()).reason());
        assertEquals(1, backend.loadCalls);
        backend.close();
        assertEquals(0, backend.releaseCalls, "a backend that never became ready has nothing to release");
    }

    @Test
    void sessionsAreTrackedAndClosedIdempotently() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        await(backend.load());
        RecognitionSession a = backend.openSession(RecognitionRequest.freeform());
        RecognitionSession b = backend.openSession(RecognitionRequest.grammar(Set.of("confirm", "cancel")));
        assertEquals(2, backend.openSessionCount());
        a.close();
        a.close();
        assertEquals(1, backend.openSessionCount());
        assertFalse(a.isOpen());
        assertTrue(b.isOpen());
        b.close();
        assertEquals(0, backend.openSessionCount());
    }

    @Test
    void closingTheBackendClosesSessionsBeforeReleasingTheEngine() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        await(backend.load());
        RecognitionSession a = backend.openSession(RecognitionRequest.freeform());
        backend.openSession(RecognitionRequest.freeform());
        backend.close();
        backend.close();
        assertEquals(0, backend.closedWhileLoadingCalls, "closing a READY backend is not an interrupted load");
        assertFalse(a.isOpen());
        assertEquals(0, backend.openSessionCount());
        assertEquals(1, backend.releaseCalls);
        assertEquals(List.of("open:FREEFORM", "open:FREEFORM", "close-session", "close-session", "release"), backend.events);
        assertEquals(BackendStatus.State.CLOSED, backend.status().state());
        assertThrows(RecognitionException.class, () -> backend.openSession(RecognitionRequest.freeform()));
        assertEquals(BackendStatus.State.CLOSED, await(backend.load()).state());
    }

    @Test
    void closeDuringLoadReleasesWhatWasLoadedAndStaysClosed() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        backend.loadGate = new CountDownLatch(1);
        CompletableFuture<BackendStatus> f = backend.load();
        backend.close();
        backend.loadGate.countDown();
        assertEquals(BackendStatus.State.CLOSED, await(f).state());
        assertEquals(BackendStatus.State.CLOSED, backend.status().state());
        assertEquals(1, backend.releaseCalls, "the engine the late load created must be freed");
        assertEquals(1, backend.closedWhileLoadingCalls, "the engine is told the close interrupted a load");
    }

    @Test
    void engineRefusingASessionSurfacesAsRecognitionException() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        await(backend.load());
        backend.failNextOpen = true;
        RecognitionException e = assertThrows(RecognitionException.class,
                () -> backend.openSession(RecognitionRequest.freeform()));
        assertTrue(e.getMessage().contains("engine refused"));
        assertEquals(0, backend.openSessionCount());
    }

    @Test
    void sessionLifecycleThroughTheInterface() throws Exception {
        FakeRecognitionBackend backend = new FakeRecognitionBackend(executor);
        await(backend.load());
        try (RecognitionSession s = backend.openSession(RecognitionRequest.freeform())) {
            assertEquals("", s.partialText());
            s.acceptAudio(new short[1600], 1600);
            assertEquals("hello", s.partialText());
            Transcript t = s.finish();
            assertEquals("hello this is a test", t.text());
            assertFalse(t.unrecognized());
            assertThrows(RecognitionException.class, s::finish);
            assertThrows(RecognitionException.class, () -> s.acceptAudio(new short[10], 10));
        }
        assertEquals(0, backend.openSessionCount());

        try (RecognitionSession g = backend.openSession(RecognitionRequest.grammar(Set.of("Confirm", " cancel ")))) {
            g.acceptAudio(new short[1600], 1600);
            assertTrue(g.finish().unrecognized(), "text outside the grammar must never count as a command");
        }
    }
}
