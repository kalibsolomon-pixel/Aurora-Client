package com.aurora.client.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Pure, Minecraft-free connection-scoped chat history session manager.
 *
 * <p>Prevents three classes of history corruption:
 * <ol>
 *   <li><b>Stale loads across fast reconnects:</b> each connection gets its own
 *       monotonic generation, so a delayed load from an earlier visit to the
 *       same server is rejected.</li>
 *   <li><b>Destructive writes before merge:</b> incoming messages arriving
 *       while the initial async load is in flight are buffered in memory and
 *       merged after the stored history loads, never overwriting prior disk
 *       entries with a partial buffer.</li>
 *   <li><b>Disconnect policy drift:</b> the outgoing session carries its own
 *       capture flag and saved-message limit, so a profile restore that runs
 *       earlier during disconnect cannot discard final messages or trim the
 *       server's history with another profile's limit.</li>
 * </ol>
 */
public final class ChatHistorySession {
    public static final int MIN_SAVED = 50;
    public static final int MAX_SAVED = 500;
    private static final int PERIODIC_SAVE_COUNT = 5;
    private static final long PERIODIC_SAVE_INTERVAL_MS = 5000L;

    private static final class Session {
        final long generation;
        final String scope;
        boolean captureEnabled;
        int maxSaved;
        boolean loaded;
        final List<ChatHistoryStore.Entry> entries = new ArrayList<>();
        int unsavedMessages;
        long lastSaveMs;
        CompletableFuture<List<ChatHistoryStore.Entry>> pendingLoad;

        Session(long generation, String scope, boolean captureEnabled, int maxSaved) {
            this.generation = generation;
            this.scope = scope;
            this.captureEnabled = captureEnabled;
            this.maxSaved = clampLimit(maxSaved);
        }
    }

    private final Object lock = new Object();
    private long nextGeneration = 1L;
    private Session active;

    public static int clampLimit(int maxSaved) {
        return Math.max(MIN_SAVED, Math.min(MAX_SAVED, maxSaved));
    }

    /**
     * Begins a history session for a world/server join, or keeps the existing
     * session when the same scope is re-entered without a disconnect (e.g.
     * dimension respawn).
     *
     * @return the new session generation if a load should proceed, or {@code -1}
     *         when the existing session was kept as-is.
     */
    public long beginSession(String scope, boolean captureEnabled, int maxSaved,
                             CompletableFuture<List<ChatHistoryStore.Entry>> loadFuture) {
        if (scope == null || scope.isBlank()) return -1L;
        int limit = clampLimit(maxSaved);
        synchronized (lock) {
            if (active != null && scope.equals(active.scope)) {
                if (captureEnabled) {
                    active.captureEnabled = true;
                    active.maxSaved = limit;
                }
                if (active.loaded || active.pendingLoad != null) {
                    return -1L;
                }
                // Implicit session created by an early message before onWorldJoin: attach the load.
                active.pendingLoad = loadFuture;
                return active.generation;
            }
            long gen = nextGeneration++;
            Session session = new Session(gen, scope, captureEnabled, limit);
            session.pendingLoad = loadFuture;
            active = session;
            return gen;
        }
    }

    /**
     * Merges asynchronously loaded history entries into the matching session.
     * Returns {@code true} only when {@code generation} still matches the
     * active connection.
     */
    public boolean completeLoad(long generation, List<ChatHistoryStore.Entry> loadedEntries, long nowMs) {
        synchronized (lock) {
            if (active == null || active.generation != generation) return false;
            List<ChatHistoryStore.Entry> prior = loadedEntries != null ? loadedEntries : List.of();
            if (!prior.isEmpty()) {
                active.entries.addAll(0, prior);
                trimToLimit(active.entries, active.maxSaved);
            }
            active.loaded = true;
            active.pendingLoad = null;
            if (active.captureEnabled && active.unsavedMessages > 0 && !active.entries.isEmpty()) {
                if (active.unsavedMessages >= PERIODIC_SAVE_COUNT
                        || nowMs - active.lastSaveMs >= PERIODIC_SAVE_INTERVAL_MS) {
                    ChatHistoryStore.saveAsync(active.scope, active.entries, active.maxSaved);
                    active.lastSaveMs = nowMs;
                    active.unsavedMessages = 0;
                }
            }
            return true;
        }
    }

    /**
     * Records a live incoming message into the current session. Buffers in
     * memory while the initial history load is still pending, avoiding
     * destructive overwrites before disk history is merged.
     */
    public void recordIncoming(String scope, String plainText, long nowMs,
                               boolean captureEnabled, int maxSaved) {
        if (!captureEnabled || plainText == null || plainText.isBlank() || scope == null || scope.isBlank()) {
            return;
        }
        int limit = clampLimit(maxSaved);
        synchronized (lock) {
            if (active == null || !scope.equals(active.scope)) {
                // Message arrived before onWorldJoin initialized the session;
                // keep loaded=false so onWorldJoin (or disconnect) merges disk history first.
                long gen = nextGeneration++;
                active = new Session(gen, scope, true, limit);
            } else {
                active.captureEnabled = true;
                active.maxSaved = limit;
            }
            active.entries.add(new ChatHistoryStore.Entry(nowMs, plainText));
            trimToLimit(active.entries, active.maxSaved);
            active.unsavedMessages++;
            if (active.loaded
                    && (active.unsavedMessages >= PERIODIC_SAVE_COUNT
                    || nowMs - active.lastSaveMs >= PERIODIC_SAVE_INTERVAL_MS)) {
                ChatHistoryStore.saveAsync(active.scope, active.entries, active.maxSaved);
                active.lastSaveMs = nowMs;
                active.unsavedMessages = 0;
            }
        }
    }

    /**
     * Flushes the outgoing session using the capture policy and limit recorded
     * for that session, unaffected by any profile switch that already ran
     * during disconnect.
     *
     * @return a future that completes once the final save has been submitted to
     *         {@link ChatHistoryStore} (already completed in the common case).
     */
    public CompletableFuture<Void> disconnect() {
        Session outgoing;
        List<ChatHistoryStore.Entry> buffered;
        CompletableFuture<List<ChatHistoryStore.Entry>> pendingLoad;
        synchronized (lock) {
            outgoing = active;
            active = null;
            nextGeneration++;
            if (outgoing == null) {
                return CompletableFuture.completedFuture(null);
            }
            buffered = List.copyOf(outgoing.entries);
            pendingLoad = outgoing.pendingLoad;
            outgoing.entries.clear();
            outgoing.unsavedMessages = 0;
            outgoing.pendingLoad = null;
        }
        if (!outgoing.captureEnabled || buffered.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (outgoing.loaded) {
            ChatHistoryStore.saveAsync(outgoing.scope, buffered, outgoing.maxSaved);
            return CompletableFuture.completedFuture(null);
        }
        // Disconnected before the initial load merged: merge prior history before saving.
        CompletableFuture<List<ChatHistoryStore.Entry>> source = pendingLoad != null
                ? pendingLoad
                : CompletableFuture.supplyAsync(() -> ChatHistoryStore.load(outgoing.scope, outgoing.maxSaved));
        return source.handle((prior, err) -> {
            List<ChatHistoryStore.Entry> merged = new ArrayList<>();
            if (err == null && prior != null) merged.addAll(prior);
            merged.addAll(buffered);
            trimToLimit(merged, outgoing.maxSaved);
            ChatHistoryStore.saveAsync(outgoing.scope, merged, outgoing.maxSaved);
            return null;
        });
    }

    /** Resets session state cleanly (for tests). */
    public void reset() {
        synchronized (lock) {
            active = null;
            nextGeneration++;
        }
    }

    private static void trimToLimit(List<ChatHistoryStore.Entry> list, int limit) {
        int excess = list.size() - limit;
        if (excess > 0) {
            list.subList(0, excess).clear();
        }
    }
}
