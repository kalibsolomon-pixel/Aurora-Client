package com.aurora.client.media;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SystemMediaBridgeLifecycleTest {

    @AfterEach
    void cleanup() {
        SystemMediaBridge.resetForTest();
    }

    private static final class FakeHelperProcess extends Process {
        private final PipedOutputStream hostToProcOut = new PipedOutputStream();
        private final PipedInputStream hostToProcIn;
        private final PipedOutputStream procToHostOut = new PipedOutputStream();
        private final PipedInputStream procToHostIn;
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final CountDownLatch readyGate;
        private final CountDownLatch attachedGate;
        private final AtomicInteger commandCount = new AtomicInteger();

        FakeHelperProcess(CountDownLatch readyGate, CountDownLatch attachedGate) throws IOException {
            this.readyGate = readyGate;
            this.attachedGate = attachedGate;
            this.hostToProcIn = new PipedInputStream(hostToProcOut);
            this.procToHostIn = new PipedInputStream(procToHostOut);
            Thread t = new Thread(this::serve, "fake-media-helper");
            t.setDaemon(true);
            t.start();
        }

        private void serve() {
            try {
                if (readyGate != null) readyGate.await(5, TimeUnit.SECONDS);
                if (!alive.get()) return;
                writeLine("READY gsmtc=1 keys=1");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(hostToProcIn, StandardCharsets.UTF_8))) {
                    String line;
                    while (alive.get() && (line = reader.readLine()) != null) {
                        if (line.startsWith("CMD ")) {
                            commandCount.incrementAndGet();
                            writeLine("OK 1");
                        } else if (line.startsWith("POLL")) {
                            writeLine("ACTIVE=1");
                            writeLine("TITLE=Song");
                            writeLine("---END---");
                        } else if ("QUIT".equals(line)) {
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                alive.set(false);
                try { procToHostOut.close(); } catch (IOException ignored) {}
            }
        }

        private void writeLine(String s) throws IOException {
            procToHostOut.write((s + "\n").getBytes(StandardCharsets.UTF_8));
            procToHostOut.flush();
        }

        @Override public OutputStream getOutputStream() { return hostToProcOut; }
        @Override public InputStream getInputStream() {
            if (attachedGate != null) attachedGate.countDown();
            return procToHostIn;
        }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }
        @Override public boolean isAlive() { return alive.get(); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() { return 0; }
        @Override public void destroy() { destroyForcibly(); }
        @Override public Process destroyForcibly() {
            alive.set(false);
            try { hostToProcIn.close(); } catch (IOException ignored) {}
            try { procToHostOut.close(); } catch (IOException ignored) {}
            return this;
        }
    }

    @Test
    void isolatedMediaActionsWithHudOffReuseSingleWindowsHelperUntilIdleOrDisabled() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicLong now = new AtomicLong(10_000L);
        SystemMediaBridge.configureForTest(() -> {
            starts.incrementAndGet();
            return new FakeHelperProcess(null, null);
        }, now::get, true);

        // Controls ON, Now Playing HUD OFF: two isolated keypresses with ticks in between must start helper ONCE.
        SystemMediaBridge.tick(true, false);
        SystemMediaBridge.dispatch(MediaAction.PLAY_PAUSE);
        assertTrue(SystemMediaBridge.awaitWorkerIdleForTest(5000));
        for (int i = 0; i < 10; i++) {
            now.addAndGet(50L);
            SystemMediaBridge.tick(true, false);
        }
        SystemMediaBridge.dispatch(MediaAction.NEXT);
        assertTrue(SystemMediaBridge.awaitWorkerIdleForTest(5000));
        assertEquals(1, starts.get(), "disabled HUD ticks must not kill and restart the media helper");

        // Advancing past IDLE_SHUTDOWN_MS shuts down the warm helper cleanly.
        now.addAndGet(SystemMediaBridge.IDLE_SHUTDOWN_MS + 1000L);
        SystemMediaBridge.tick(true, false);
        assertTrue(SystemMediaBridge.awaitWorkerIdleForTest(5000));

        // A later keypress starts a fresh helper cleanly.
        SystemMediaBridge.dispatch(MediaAction.PREVIOUS);
        assertTrue(SystemMediaBridge.awaitWorkerIdleForTest(5000));
        assertEquals(2, starts.get());
    }

    @Test
    void stalledHelperCoalescesRepeatedDisableCloseRequests() throws Exception {
        CountDownLatch helperAttached = new CountDownLatch(1);
        CountDownLatch releaseReady = new CountDownLatch(1);
        AtomicLong now = new AtomicLong(10_000L);

        SystemMediaBridge.configureForTest(() -> new FakeHelperProcess(releaseReady, helperAttached), now::get, true);

        try {
            SystemMediaBridge.tick(true, false);
            SystemMediaBridge.dispatch(MediaAction.PLAY_PAUSE);
            assertTrue(helperAttached.await(5, TimeUnit.SECONDS));

            // Helper is stalled waiting for READY while Media Controls is toggled OFF across 50 ticks.
            for (int i = 0; i < 50; i++) {
                now.addAndGet(50L);
                SystemMediaBridge.tick(false, false);
            }
            assertEquals(1, SystemMediaBridge.scheduledCloseCountForTest(),
                    "close requests while worker is busy must coalesce instead of accumulating");
        } finally {
            releaseReady.countDown();
        }
        assertTrue(SystemMediaBridge.awaitWorkerIdleForTest(5000));
    }
}
