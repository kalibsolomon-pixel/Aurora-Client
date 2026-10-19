package com.aurora.client.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the Windows "Access denied" config-save failure
 * (CONFIG_PERSISTENCE_RELIABILITY_REPORT.md). Faults are injected through the
 * writer's FileOps seam and synchronized with latches — no timing luck.
 */
class AtomicJsonWriterTest {
    @TempDir Path dir;
    private static final AtomicJsonWriter.Sleeper NO_SLEEP = ms -> {};

    /** Real file ops with an optional hook before each replace. */
    private static class Ops implements AtomicJsonWriter.FileOps {
        final List<String> replaced = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger replaceCalls = new AtomicInteger();
        volatile ReplaceHook hook = (s, t, n) -> {};
        interface ReplaceHook { void before(Path source, Path target, int call) throws IOException; }
        @Override public void writeDurably(Path f, String c) throws IOException { AtomicJsonWriter.REAL_FILES.writeDurably(f, c); }
        @Override public void replace(Path s, Path t) throws IOException {
            int n = replaceCalls.incrementAndGet();
            hook.before(s, t, n);
            AtomicJsonWriter.REAL_FILES.replace(s, t);
            replaced.add(Files.readString(t));
        }
        @Override public void deleteIfExists(Path f) throws IOException { Files.deleteIfExists(f); }
    }

    private static AccessDeniedException denied(Path s, Path t) {
        return new AccessDeniedException(s.toString(), t.toString(), null);
    }

    private List<Path> temps() throws IOException {
        try (var s = Files.list(dir)) { return s.filter(p -> p.toString().endsWith(".tmp")).toList(); }
    }

    @Test void normalSaveLandsAndLeavesNoTemp() throws Exception {
        Path target = dir.resolve("aurora.json");
        var writer = new AtomicJsonWriter("t", new Ops(), NO_SLEEP, 0);
        writer.submit(target, "{\"v\":1}");
        assertTrue(writer.flush(5000));
        assertEquals("{\"v\":1}", Files.readString(target));
        assertTrue(temps().isEmpty());
    }

    @Test void transientAccessDeniedOnReplaceIsRetriedAndTheNewContentLands() throws Exception {
        Path target = dir.resolve("aurora.json");
        Files.writeString(target, "old");
        var ops = new Ops();
        ops.hook = (s, t, n) -> { if (n <= 3) throw denied(s, t); };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "new");
        assertTrue(writer.flush(5000));
        assertEquals("new", Files.readString(target));
        assertEquals(4, ops.replaceCalls.get());
        assertEquals(0, writer.failureCount());
        assertTrue(temps().isEmpty());
    }

    @Test void persistentLockKeepsThePreviousFileIntactReportsFailureAndLeavesNoTemp() throws Exception {
        Path target = dir.resolve("aurora.json");
        Files.writeString(target, "previous valid");
        var ops = new Ops();
        ops.hook = (s, t, n) -> { throw denied(s, t); };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "new");
        assertFalse(writer.flush(5000), "a write that never landed must not be reported as saved");
        assertEquals("previous valid", Files.readString(target));
        assertEquals(1, writer.failureCount());
        assertEquals(AtomicJsonWriter.MAX_ROUNDS * (AtomicJsonWriter.BURST_BACKOFF_MS.length + 1), ops.replaceCalls.get(),
                "bounded: every round is one burst of the fixed retry budget");
        assertTrue(temps().isEmpty(), "failed attempts clean up their temp files");
    }

    @Test void nonTransientFailureIsNotRetriedWithinABurst() throws Exception {
        Path target = dir.resolve("aurora.json");
        var ops = new Ops();
        ops.hook = (s, t, n) -> { throw new IOException("disk full"); };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "x");
        assertFalse(writer.flush(5000));
        assertEquals(AtomicJsonWriter.MAX_ROUNDS, ops.replaceCalls.get(), "one attempt per round, no burst");
    }

    @Test void writeFailureNeverTouchesTheDestination() throws Exception {
        Path target = dir.resolve("aurora.json");
        Files.writeString(target, "previous valid");
        var ops = new Ops() {
            @Override public void writeDurably(Path f, String c) throws IOException {
                Files.writeString(f, c.substring(0, 2)); // partial write, then the device fails
                throw new IOException("interrupted write");
            }
        };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "new content");
        assertFalse(writer.flush(5000));
        assertEquals("previous valid", Files.readString(target), "no truncated or partial JSON reaches the real file");
        assertEquals(0, ops.replaceCalls.get());
        assertTrue(temps().isEmpty());
    }

    @Test void rapidSavesAreOrderedAndAStaleSnapshotNeverLandsAfterANewerOne() throws Exception {
        Path target = dir.resolve("aurora.json");
        var inReplace = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ops = new Ops();
        ops.hook = (s, t, n) -> {
            if (n == 1) {
                inReplace.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
            }
        };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "v1");
        assertTrue(inReplace.await(5, TimeUnit.SECONDS));
        for (int i = 2; i <= 50; i++) writer.submit(target, "v" + i); // queued while v1 is mid-replace
        release.countDown();
        assertTrue(writer.flush(5000));
        assertEquals(List.of("v1", "v50"), ops.replaced, "intermediate snapshots coalesce; order is preserved");
        assertEquals("v50", Files.readString(target));
    }

    @Test void flushIsBoundedAndReportsAWriteThatHasNotLanded() throws Exception {
        Path target = dir.resolve("aurora.json");
        var inReplace = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ops = new Ops();
        ops.hook = (s, t, n) -> {
            inReplace.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
        };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "v1");
        assertTrue(inReplace.await(5, TimeUnit.SECONDS));
        assertFalse(writer.flush(50), "a stuck write is reported, not waited on forever");
        release.countDown();
        assertTrue(writer.flush(5000));
        assertEquals("v1", Files.readString(target));
    }

    @Test void multipleDestinationsAreIndependentAndAllLand() throws Exception {
        var writer = new AtomicJsonWriter("t", new Ops(), NO_SLEEP, 0);
        Path profiles = dir.resolve("profiles");
        for (int i = 0; i < 20; i++)
            for (String n : List.of("default", "pvp", "build")) writer.submit(profiles.resolve(n + ".json"), n + i);
        assertTrue(writer.flush(5000));
        for (String n : List.of("default", "pvp", "build")) assertEquals(n + 19, Files.readString(profiles.resolve(n + ".json")));
        try (var s = Files.list(profiles)) { assertEquals(3, s.count(), "no temp accumulation"); }
    }

    /**
     * The exact Windows condition from the field logs, on the real filesystem:
     * another handle is open on the destination while it is replaced. On
     * Windows the first replace fails with AccessDeniedException; the handle
     * is released during the writer's backoff (deterministically, from the
     * sleeper) and the save lands. On other platforms rename succeeds anyway.
     */
    @Test void openHandleOnDestinationDuringReplaceNoLongerLosesTheSave() throws Exception {
        Path target = dir.resolve("aurora.json");
        Files.writeString(target, "old");
        FileChannel holder = FileChannel.open(target, StandardOpenOption.READ);
        AtomicInteger sleeps = new AtomicInteger();
        AtomicJsonWriter.Sleeper releaseOnFirstBackoff = ms -> {
            if (sleeps.getAndIncrement() == 0) {
                try { holder.close(); } catch (IOException ignored) {}
            }
        };
        var writer = new AtomicJsonWriter("t", AtomicJsonWriter.REAL_FILES, releaseOnFirstBackoff, 0);
        try {
            writer.submit(target, "new");
            assertTrue(writer.flush(5000));
        } finally { holder.close(); }
        assertEquals("new", Files.readString(target));
        if (System.getProperty("os.name", "").startsWith("Windows"))
            assertEquals(1, sleeps.get(), "Windows: exactly one transient AccessDenied was absorbed");
        assertTrue(temps().isEmpty());
    }

    /** The pre-fix sequence under the same condition, kept as the documented baseline. */
    @Test void legacySequenceFailsWithTheFieldExceptionOnWindows() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name", "").startsWith("Windows"));
        Path target = dir.resolve("aurora.json"), tmp = dir.resolve("aurora.json.tmp");
        Files.writeString(target, "old");
        Files.writeString(tmp, "new");
        try (FileChannel holder = FileChannel.open(target, StandardOpenOption.READ)) {
            assertThrows(AccessDeniedException.class, () -> Files.move(tmp, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE));
        }
        assertEquals("old", Files.readString(target));
    }

    @Test void transientClassificationIsNarrow() {
        assertTrue(AtomicJsonWriter.isTransientReplaceFailure(new AccessDeniedException("a", "b", null)));
        assertTrue(AtomicJsonWriter.isTransientReplaceFailure(new FileSystemException("a", null,
                "The process cannot access the file because it is being used by another process")));
        assertFalse(AtomicJsonWriter.isTransientReplaceFailure(new FileSystemException("a", null, "There is not enough space on the disk")));
        assertFalse(AtomicJsonWriter.isTransientReplaceFailure(new IOException("x")));
    }

    @Test void staleTempHygieneRemovesOnlyOurTemps() throws Exception {
        Path target = dir.resolve("aurora.json");
        for (String n : List.of("aurora.json.tmp", "aurora.json.7.tmp", "aurora.json.backup.tmp", "other.json.tmp", "aurora.json"))
            Files.writeString(dir.resolve(n), "x");
        AtomicJsonWriter.deleteStaleTemps(target);
        assertFalse(Files.exists(dir.resolve("aurora.json.tmp")));
        assertFalse(Files.exists(dir.resolve("aurora.json.7.tmp")));
        assertTrue(Files.exists(dir.resolve("aurora.json.backup.tmp")), "unrelated names are left alone");
        assertTrue(Files.exists(dir.resolve("other.json.tmp")));
        assertTrue(Files.exists(target));
    }

    @Test void readYourWritesSeesQueuedAndInFlightContentBeforeTheDisk() throws Exception {
        Path target = dir.resolve("profiles/new.json");
        var inReplace = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ops = new Ops();
        ops.hook = (s, t, n) -> {
            inReplace.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
        };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        assertNull(writer.latest(target), "nothing pending: the disk is authoritative");
        writer.submit(target, "first");
        assertTrue(inReplace.await(5, TimeUnit.SECONDS));
        assertFalse(Files.exists(target), "not on disk yet");
        assertEquals("first", writer.latest(target), "in-flight content is visible (create -> switchTo)");
        writer.submit(target, "second");
        assertEquals("second", writer.latest(target), "the newest queued content wins");
        release.countDown();
        assertTrue(writer.flush(5000));
        assertNull(writer.latest(target));
        assertEquals("second", Files.readString(target));
    }

    @Test void aQueuedDeleteCannotBeUndoneByAnOlderWriteAndIsVisibleImmediately() throws Exception {
        Path target = dir.resolve("profiles/old.json");
        var inReplace = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var ops = new Ops();
        ops.hook = (s, t, n) -> {
            inReplace.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
        };
        var writer = new AtomicJsonWriter("t", ops, NO_SLEEP, 0);
        writer.submit(target, "saved before delete");
        assertTrue(inReplace.await(5, TimeUnit.SECONDS)); // that save is mid-replace
        writer.delete(target);
        assertSame(AtomicJsonWriter.DELETED, writer.latest(target));
        release.countDown();
        assertTrue(writer.flush(5000));
        assertFalse(Files.exists(target), "the delete is ordered after the in-flight save");
    }
}
