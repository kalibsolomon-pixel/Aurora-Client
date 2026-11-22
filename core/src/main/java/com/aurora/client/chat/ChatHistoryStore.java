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
    private static final AtomicJsonWriter WRITER = new AtomicJsonWriter("Aurora-ChatHistory");

    public record Entry(long epochMillis, String text) {}

    private record HistoryFile(int version, String scope, List<Entry> messages) {}

    private ChatHistoryStore() {}

    public static Path fileForScope(String scopeKey) {
        return ConfigDirectory.get()
                .resolve("chat-history")
                .resolve(ChatFormatting.sanitizeScopeKey(scopeKey) + ".json");
    }

    /**
     * Loads up to {@code maxEntries} saved chat lines (oldest first) for the
     * given server/world scope. Returns an empty list on missing or corrupt files.
     */
    public static List<Entry> load(String scopeKey, int maxEntries) {
        if (scopeKey == null || scopeKey.isBlank() || maxEntries <= 0) return List.of();
        Path path = fileForScope(scopeKey);
        if (!Files.isRegularFile(path)) return List.of();
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
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
        WRITER.submit(fileForScope(scopeKey), json);
    }

    /** Waits boundedly for any queued history writes to hit disk. */
    public static boolean flush(long timeoutMs) {
        return WRITER.flush(timeoutMs);
    }
}
