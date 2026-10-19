package com.aurora.client.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in A/B stress (run with env AURORA_PERSISTENCE_STRESS=1): the legacy
 * save sequence versus {@link AtomicJsonWriter} while a second thread keeps
 * briefly opening the destination — the condition reproduced on Windows.
 * Results are printed for CONFIG_PERSISTENCE_RELIABILITY_REPORT.md.
 */
class PersistenceStressComparison {
    @TempDir Path dir;

    @Test void legacyVersusWriterUnderTransientOpens() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("AURORA_PERSISTENCE_STRESS")));
        int saves = 2000;
        String body = "{\n" + "  \"k\": 1,\n".repeat(400) + "}";

        Path legacyTarget = dir.resolve("legacy/aurora.json");
        Files.createDirectories(legacyTarget.getParent());
        Files.writeString(legacyTarget, body);
        int legacyFailures;
        try (var opener = new Opener(legacyTarget)) {
            legacyFailures = 0;
            Path tmp = legacyTarget.resolveSibling("aurora.json.tmp");
            for (int i = 0; i < saves; i++) {
                try {
                    Files.writeString(tmp, body + i);
                    Files.move(tmp, legacyTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    legacyFailures++;
                    Files.deleteIfExists(tmp); // what the old code did: the save is dropped
                }
            }
        }
        boolean legacyFinal = Files.readString(legacyTarget).endsWith(String.valueOf(saves - 1));

        Path target = dir.resolve("writer/aurora.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, body);
        var writer = new AtomicJsonWriter("stress");
        boolean flushed;
        long worstFlushMs = 0;
        try (var opener = new Opener(target)) {
            long[] submitNs = new long[saves];
            for (int i = 0; i < saves; i++) {
                long s0 = System.nanoTime();
                writer.submit(target, body + i);
                submitNs[i] = System.nanoTime() - s0;
                if (i % 50 == 49) { // also exercise the blocking path repeatedly
                    long t0 = System.nanoTime();
                    assertTrue(writer.flush(10_000));
                    worstFlushMs = Math.max(worstFlushMs, (System.nanoTime() - t0) / 1_000_000);
                }
            }
            flushed = writer.flush(10_000);
            java.util.Arrays.sort(submitNs);
            System.out.printf("[persistence-stress] submit (calling-thread cost) p50=%.1fus p99=%.1fus max=%.1fus%n",
                    submitNs[saves / 2] / 1e3, submitNs[saves * 99 / 100] / 1e3, submitNs[saves - 1] / 1e3);
        }
        String last = Files.readString(target);
        System.out.printf("[persistence-stress] saves=%d legacyDropped=%d legacyFinalIsNewest=%s | writerFailures=%d "
                        + "writerFlushed=%s writerFinalIsNewest=%s worstFlushMs=%d openerOpens=%d%n",
                saves, legacyFailures, legacyFinal, writer.failureCount(), flushed,
                last.endsWith(String.valueOf(saves - 1)), worstFlushMs, Opener.opens);
        assertTrue(flushed);
        assertEquals(0, writer.failureCount());
        assertTrue(last.endsWith(String.valueOf(saves - 1)), "the newest snapshot is what is on disk");
    }

    /** Repeatedly opens the destination briefly, like an external scanner/indexer. */
    private static final class Opener implements AutoCloseable {
        static volatile long opens;
        final AtomicBoolean running = new AtomicBoolean(true);
        final Thread thread;
        Opener(Path target) {
            opens = 0;
            thread = new Thread(() -> {
                while (running.get()) {
                    try (FileChannel ch = FileChannel.open(target, StandardOpenOption.READ)) {
                        opens++;
                        Thread.onSpinWait();
                    } catch (IOException ignored) {
                    }
                    try { Thread.sleep(1); } catch (InterruptedException e) { return; }
                }
            }, "opener");
            thread.setDaemon(true);
            thread.start();
        }
        @Override public void close() throws InterruptedException { running.set(false); thread.join(); }
    }
}
