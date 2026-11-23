package com.aurora.client.chat;

import com.aurora.client.config.AtomicJsonWriter;
import com.aurora.client.config.ConfigDirectory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded, atomic per-server chat history persistence backed by
 * {@link AtomicJsonWriter}. All writes occur off the client tick thread.
 */
public final class ChatHistoryStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Aurora");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final AtomicJsonWriter DEFAULT_WRITER = new AtomicJsonWriter("Aurora-ChatHistory");
    private static volatile AtomicJsonWriter writer = DEFAULT_WRITER;

    public record Entry(long epochMillis, String text) {}

    private record HistoryFile(int version, String scope, List<Entry> messages) {}

    private ChatHistoryStore() {}

    /** Replaces the writer for deterministic tests; null restores the default writer. */
    static void setWriterForTest(AtomicJsonWriter testWriter) {
        writer = testWriter != null ? testWriter : DEFAULT_WRITER;
    }

    public static Path fileForScope(String scopeKey) {
        return ConfigDirectory.get()
                .resolve("chat-history")
                .resolve(ChatFormatting.sanitizeScopeKey(scopeKey) + ".json");
    }

    /** True when {@code scopeKey} has queued/in-flight content or an on-disk history file. */
    public static boolean exists(String scopeKey) {
        if (scopeKey == null || scopeKey.isBlank()) return false;
        Path path = fileForScope(scopeKey);
        String pending = writer.latest(path);
        if (pending == AtomicJsonWriter.DELETED) return false;
        return pending != null || Files.isRegularFile(path);
    }

    /**
     * Loads up to {@code maxEntries} saved chat lines (oldest first) for the
     * given server/world scope, consulting queued/in-flight writes first
     * (read-your-writes) so a fast reconnect never observes a stale disk file.
     * Returns an empty list on missing or corrupt files.
     */
    public static List<Entry> load(String scopeKey, int maxEntries) {
        if (scopeKey == null || scopeKey.isBlank() || maxEntries <= 0) return List.of();
        Path path = fileForScope(scopeKey);
        String pending = writer.latest(path);
        if (pending == AtomicJsonWriter.DELETED) return List.of();
        if (pending == null && !Files.isRegularFile(path)) return List.of();
        try {
            String raw = pending != null ? pending : Files.readString(path, StandardCharsets.UTF_8);
            HistoryFile file = GSON.fromJson(raw, HistoryFile.class);
            if (file == null || file.messages() == null || file.messages().isEmpty()) return List.of();
            List<Entry> valid = new ArrayList<>(Math.min(file.messages().size(), maxEntries));
            for (Entry e : file.messages()) {
                if (e != null && e.text() != null && !e.text().isEmpty()) {
                    valid.add(e);
                }
            }
            if (valid.size() > maxEntries) {
                return List.copyOf(valid.subList(valid.size() - maxEntries, valid.size()));
            }
            return List.copyOf(valid);
        } catch (Exception e) {
            LOGGER.warn("[Chat] failed to load per-server history for '{}': {}", scopeKey, e.getMessage());
            return List.of();
        }
    }

    /**
     * Loads history for {@code scopeKey}. When no file exists yet for the
     * save-based scope and {@code allowAutoMigrate} confirms unambiguous
     * ownership of {@code legacyScopeKey}, copies the legacy history into
     * {@code scopeKey} while preserving the legacy file on disk for recovery.
     */
    public static List<Entry> loadOrMigrateLegacy(String scopeKey, String legacyScopeKey,
                                                  boolean allowAutoMigrate, int maxEntries) {
        if (scopeKey == null || scopeKey.isBlank() || maxEntries <= 0) return List.of();
        if (exists(scopeKey)) {
            return load(scopeKey, maxEntries);
        }
        if (allowAutoMigrate && legacyScopeKey != null && !legacyScopeKey.isBlank()
                && !legacyScopeKey.equals(scopeKey) && exists(legacyScopeKey)) {
            List<Entry> legacy = load(legacyScopeKey, maxEntries);
            if (!legacy.isEmpty()) {
                saveAsync(scopeKey, legacy, maxEntries);
            }
            return legacy;
        }
        return List.of();
    }

    /**
     * Persists the newest {@code maxEntries} messages for {@code scopeKey}
     * asynchronously and atomically via {@link AtomicJsonWriter}.
     */
    public static void saveAsync(String scopeKey, List<Entry> entries, int maxEntries) {
        if (scopeKey == null || scopeKey.isBlank() || entries == null || maxEntries <= 0) return;
        List<Entry> snapshot;
        if (entries.size() > maxEntries) {
            snapshot = new ArrayList<>(entries.subList(entries.size() - maxEntries, entries.size()));
        } else {
            snapshot = new ArrayList<>(entries);
        }
        String json = GSON.toJson(new HistoryFile(1, scopeKey, snapshot));
        writer.submit(fileForScope(scopeKey), json);
    }

    /** Waits boundedly for any queued history writes to hit disk. */
    public static boolean flush(long timeoutMs) {
        return writer.flush(timeoutMs);
    }
}
