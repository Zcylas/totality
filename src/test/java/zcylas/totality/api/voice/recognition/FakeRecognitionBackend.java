package zcylas.totality.api.voice.recognition;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Injectable test backend: all lifecycle behavior comes from the real {@link ManagedRecognitionBackend};
 * this class only scripts what "loading" returns and records what happened.
 */
final class FakeRecognitionBackend extends ManagedRecognitionBackend {

    final List<String> events = new ArrayList<>();
    final List<FakeSession> sessions = new ArrayList<>();
    volatile Supplier<BackendStatus> loadBehavior = () -> BackendStatus.ready("fake");
    volatile CountDownLatch loadGate;
    volatile String loadThreadName;
    volatile String scriptedText = "hello this is a test";
    volatile boolean failNextOpen;
    int loadCalls;
    int releaseCalls;

    FakeRecognitionBackend(Executor executor) {
        super(executor);
    }

    @Override
    public String id() {
        return "fake";
    }

    @Override
    protected BackendStatus doLoad() throws Exception {
        synchronized (this) {
            loadCalls++;
        }
        loadThreadName = Thread.currentThread().getName();
        CountDownLatch gate = loadGate;
        if (gate != null && !gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("gate timeout");
        return loadBehavior.get();
    }

    @Override
    protected RecognitionSession doOpenSession(RecognitionRequest request) {
        if (failNextOpen) {
            failNextOpen = false;
            throw new IllegalStateException("engine refused");
        }
        FakeSession s = new FakeSession(request);
        synchronized (events) {
            sessions.add(s);
            events.add("open:" + request.mode());
        }
        return s;
    }

    int closedWhileLoadingCalls;

    @Override
    protected void onClosedWhileLoading() {
        closedWhileLoadingCalls++;
    }

    @Override
    protected void doRelease() {
        synchronized (events) {
            releaseCalls++;
            events.add("release");
        }
    }

    final class FakeSession implements RecognitionSession {
        private final RecognitionRequest request;
        int samplesAccepted;
        boolean finished;
        boolean closed;

        FakeSession(RecognitionRequest request) {
            this.request = request;
        }

        @Override
        public RecognitionRequest request() {
            return request;
        }

        @Override
        public void acceptAudio(short[] samples, int count) {
            if (closed) throw new RecognitionException("closed");
            if (finished) throw new RecognitionException("finished");
            samplesAccepted += count;
        }

        @Override
        public String partialText() {
            return samplesAccepted > 0 ? scriptedText.split(" ")[0] : "";
        }

        @Override
        public Transcript finish() {
            if (closed) throw new RecognitionException("closed");
            if (finished) throw new RecognitionException("finished");
            finished = true;
            String text = samplesAccepted > 0 ? scriptedText : "";
            boolean unrecognized = text.isEmpty()
                    || (request.mode() == RecognitionMode.GRAMMAR && !request.phrases().contains(text));
            return new Transcript(text, request.mode(), List.of(), unrecognized);
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            synchronized (events) {
                events.add("close-session");
            }
            sessionClosed(this);
        }
    }
}
