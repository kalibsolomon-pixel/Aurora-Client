package com.aurora.client.config;

import com.aurora.client.AuroraClient;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ordered, coalescing, crash-safe JSON file writer shared by Aurora's config
 * and profile persistence (config reliability initiative, 2026-10-19 — see
 * CONFIG_PERSISTENCE_RELIABILITY_REPORT.md).
 *
 * <h2>Why this exists</h2>
 * The previous per-owner {@code writeString(tmp) → move(tmp, target,
 * REPLACE_EXISTING, ATOMIC_MOVE)} failed intermittently on Windows with
 * {@code AccessDeniedException: aurora.json.tmp -> aurora.json}. Reproduced
 * outside Minecraft: {@code MoveFileExW} replacing a destination that ANY
 * process holds open — even with full FILE_SHARE_READ|WRITE|DELETE sharing —
 * fails with ERROR_ACCESS_DENIED, and on this machine a background process
 * briefly opens freshly written files (19/3000 back-to-back replaces failed
 * with nothing else in the JVM touching the file). The old code then deleted
 * the temp file and dropped that save. Separately, shutdown wrote inline on a
 * second thread to the SAME temp path while an older queued async write could
 * still land afterwards, and the profile "blocking" save was not blocking.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>One worker thread per writer: writes to a destination are strictly
 *       ordered; a newer submit for a destination supersedes a queued older
 *       one (latest wins), so a stale snapshot can never land after a newer
 *       one.</li>
 *   <li>Content goes to a unique sibling temp file, is forced to disk, then
 *       atomically moved over the destination. The destination is never
 *       truncated or partially written; a failed save leaves the previous
 *       valid file intact.</li>
 *   <li>Only the demonstrated transient condition (access denied / sharing
 *       violation on replace) is retried, with a short bounded backoff. If it
 *       persists, the content stays pending and is re-attempted a bounded
 *       number of later rounds (or superseded by a newer save) — never
 *       silently dropped; final failure is logged as an error.</li>
 *   <li>{@link #flush(long)} waits, bounded, until everything submitted before
 *       the call is written (or definitively failed) and reports which.</li>
 * </ul>
 */
public final class AtomicJsonWriter {

    /** File-system seam (tests inject deterministic faults). */
    public interface FileOps {
        void writeDurably(Path file, String content) throws IOException;
        void replace(Path source, Path target) throws IOException;
        void deleteIfExists(Path file) throws IOException;
    }

    /** Backoff seam (tests use a no-op sleeper). */
    public interface Sleeper { void sleep(long ms) throws InterruptedException; }

    public static final FileOps REAL_FILES = new FileOps() {
        @Override public void writeDurably(Path file, String content) throws IOException {
            Files.createDirectories(file.getParent());
            try (FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buf = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }
        }
        @Override public void replace(Path source, Path target) throws IOException {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        @Override public void deleteIfExists(Path file) throws IOException { Files.deleteIfExists(file); }
    };

    /** In-burst backoff for a transient replace failure (~250 ms total). */
    static final long[] BURST_BACKOFF_MS = {1, 2, 4, 8, 16, 32, 64, 125};
    /** Later rounds for a destination that stayed locked through a whole burst. */
    static final int MAX_ROUNDS = 4;

    private final String name;
    private final FileOps files;
    private final Sleeper sleeper;
    private final long roundDelayMs;

    private final Object lock = new Object();
    private final LinkedHashMap<Path, Pending> queue = new LinkedHashMap<>();
    private Path inFlight;
    private String inFlightContent;
    private long nextGeneration = 1, settledThrough;
    private long tempCounter;
    private int failures;
    private Thread worker;

    private static final class Pending {
        final Path target; String content; long generation; int round; long notBeforeNanos;
        Pending(Path target, String content, long generation) { this.target = target; this.content = content; this.generation = generation; }
    }

    public AtomicJsonWriter(String name) { this(name, REAL_FILES, Thread::sleep, 1000); }

    public AtomicJsonWriter(String name, FileOps files, Sleeper sleeper, long roundDelayMs) {
        this.name = name; this.files = files; this.sleeper = sleeper; this.roundDelayMs = roundDelayMs;
    }

    /** Returned by {@link #latest(Path)} when the newest queued operation for a path is a delete. */
    public static final String DELETED = new String("<deleted>");

    /**
     * Read-your-writes view: the newest content queued or being written for
     * {@code target} ({@link #DELETED} for a queued delete), or null when the
     * file on disk is already current. Owners that read their own files back
     * (profiles) must consult this first, or they can observe a stale file
     * while a newer write is still pending.
     */
    public String latest(Path target) {
        synchronized (lock) {
            Pending p = queue.get(target);
            if (p != null) return p.content == null ? DELETED : p.content;
            return target.equals(inFlight) ? (inFlightContent == null ? DELETED : inFlightContent) : null;
        }
    }

    /** {@link #latest(Path)} for every path with a pending or in-flight operation. */
    public Map<Path, String> latestAll() {
        synchronized (lock) {
            Map<Path, String> out = new LinkedHashMap<>();
            if (inFlight != null) out.put(inFlight, inFlightContent == null ? DELETED : inFlightContent);
            for (Pending p : queue.values()) out.put(p.target, p.content == null ? DELETED : p.content);
            return out;
        }
    }

    /** Queues deletion of {@code target}, ordered after any write already queued for it. */
    public void delete(Path target) { submit(target, null); }

    /** Queues {@code content} for {@code target}; replaces any not-yet-started older content for it. */
    public void submit(Path target, String content) {
        synchronized (lock) {
            long generation = nextGeneration++;
            Pending existing = queue.get(target);
            if (existing != null) {
                existing.content = content;
                existing.generation = generation;
                existing.round = 0;
                existing.notBeforeNanos = 0;
            } else {
                queue.put(target, new Pending(target, content, generation));
            }
            ensureWorker();
            lock.notifyAll();
        }
    }

    /**
     * Waits until every submit made before this call has settled (written, or
     * failed after all rounds). Pending later rounds are expedited.
     *
     * @return true if everything settled AND no write failed during the wait
     */
    public boolean flush(long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        synchronized (lock) {
            long target = nextGeneration - 1;
            int failuresBefore = failures;
            for (Pending p : queue.values()) p.notBeforeNanos = 0; // shutdown: no waiting between rounds
            lock.notifyAll();
            while (!settled(target)) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    AuroraClient.LOGGER.error("[{}] flush timed out with {} write(s) pending", name, queue.size() + (inFlight != null ? 1 : 0));
                    return false;
                }
                try { lock.wait(Math.max(1, left / 1_000_000L)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
            }
            return failures == failuresBefore;
        }
    }

    /** Number of writes that failed definitively since construction (diagnostics/tests). */
    public int failureCount() { synchronized (lock) { return failures; } }

    private boolean settled(long generation) {
        if (inFlight != null) return false;
        for (Pending p : queue.values()) if (p.generation <= generation) return false;
        return true;
    }

    private void ensureWorker() {
        if (worker != null && worker.isAlive()) return;
        worker = new Thread(this::run, name);
        worker.setDaemon(true);
        worker.start();
    }

    private void run() {
        while (true) {
            Pending job;
            synchronized (lock) {
                job = null;
                while (job == null) {
                    long now = System.nanoTime(), wait = Long.MAX_VALUE;
                    for (Iterator<Pending> it = queue.values().iterator(); it.hasNext(); ) {
                        Pending p = it.next();
                        if (p.notBeforeNanos <= now) { job = p; it.remove(); break; }
                        wait = Math.min(wait, p.notBeforeNanos - now);
                    }
                    if (job != null) break;
                    try {
                        if (wait == Long.MAX_VALUE) lock.wait();
                        else lock.wait(Math.max(1, wait / 1_000_000L));
                    } catch (InterruptedException e) { return; }
                }
                inFlight = job.target;
                inFlightContent = job.content;
            }
            boolean ok = writeWithBurstRetry(job);
            synchronized (lock) {
                inFlight = null;
                inFlightContent = null;
                if (!ok) {
                    if (queue.containsKey(job.target)) {
                        // Superseded while we were failing: the newer content will be written instead.
                    } else if (job.round + 1 < MAX_ROUNDS) {
                        job.round++;
                        job.notBeforeNanos = System.nanoTime() + roundDelayMs * job.round * 1_000_000L;
                        queue.put(job.target, job);
                        AuroraClient.LOGGER.warn("[{}] {} still locked; keeping the new content pending (round {}/{})",
                                name, job.target.getFileName(), job.round + 1, MAX_ROUNDS);
                    } else {
                        failures++;
                        AuroraClient.LOGGER.error("[{}] giving up on {} after {} rounds; the previous file is intact, "
                                + "the next save will try again", name, job.target.getFileName(), MAX_ROUNDS);
                    }
                }
                lock.notifyAll();
            }
        }
    }

    /** One write attempt + bounded in-burst retries of the replace step only. */
    private boolean writeWithBurstRetry(Pending job) {
        if (job.content == null) return deleteWithBurstRetry(job);
        Path tmp;
        synchronized (lock) { tmp = job.target.resolveSibling(job.target.getFileName() + "." + (++tempCounter) + ".tmp"); }
        try {
            files.writeDurably(tmp, job.content);
        } catch (IOException | RuntimeException e) {
            AuroraClient.LOGGER.error("[{}] could not write {}", name, tmp.getFileName(), e);
            deleteQuietly(tmp);
            return false;
        }
        IOException last = null;
        for (int attempt = 0; attempt <= BURST_BACKOFF_MS.length; attempt++) {
            try {
                files.replace(tmp, job.target);
                if (attempt > 0) AuroraClient.LOGGER.info("[{}] {} replaced after {} transient retr{}", name,
                        job.target.getFileName(), attempt, attempt == 1 ? "y" : "ies");
                return true;
            } catch (IOException e) {
                last = e;
                if (!isTransientReplaceFailure(e) || attempt == BURST_BACKOFF_MS.length) break;
                try { sleeper.sleep(BURST_BACKOFF_MS[attempt]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            } catch (RuntimeException e) {
                last = new IOException(e);
                break;
            }
        }
        AuroraClient.LOGGER.warn("[{}] replacing {} failed: {}", name, job.target.getFileName(), String.valueOf(last));
        deleteQuietly(tmp);
        return false;
    }

    private boolean deleteWithBurstRetry(Pending job) {
        IOException last = null;
        for (int attempt = 0; attempt <= BURST_BACKOFF_MS.length; attempt++) {
            try {
                files.deleteIfExists(job.target);
                return true;
            } catch (IOException e) {
                last = e;
                if (!isTransientReplaceFailure(e) || attempt == BURST_BACKOFF_MS.length) break;
                try { sleeper.sleep(BURST_BACKOFF_MS[attempt]); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        AuroraClient.LOGGER.warn("[{}] deleting {} failed: {}", name, job.target.getFileName(), String.valueOf(last));
        return false;
    }

    /**
     * The demonstrated Windows condition: MoveFileEx replacing a destination
     * that another process briefly holds open reports ERROR_ACCESS_DENIED
     * (AccessDeniedException) or a sharing violation ("used by another
     * process"). Anything else (disk full, invalid path…) is not retried.
     */
    static boolean isTransientReplaceFailure(IOException e) {
        if (e instanceof AccessDeniedException) return true;
        return e instanceof FileSystemException fse && fse.getReason() != null
                && fse.getReason().contains("used by another process");
    }

    private void deleteQuietly(Path file) {
        try { files.deleteIfExists(file); } catch (IOException ignored) {}
    }

    /** Removes temp files left by a crash mid-write (legacy fixed name and numbered names). */
    public static void deleteStaleTemps(Path target) {
        String base = target.getFileName().toString();
        try (DirectoryStream<Path> stale = Files.newDirectoryStream(target.getParent(), base + "*.tmp")) {
            for (Path p : stale) {
                String n = p.getFileName().toString();
                String middle = n.substring(base.length(), n.length() - 4);
                if (middle.isEmpty() || middle.matches("\\.\\d+")) Files.deleteIfExists(p);
            }
        } catch (IOException | RuntimeException ignored) {
            // Best-effort hygiene only; a leftover temp never affects the real file.
        }
    }

}
