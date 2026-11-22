package com.aurora.client.chat;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatSuiteTest {

    @Test
    void formatsTimestampsInAllModes() {
        LocalTime t = LocalTime.of(21, 5, 9);
        assertEquals("[21:05] ", ChatFormatting.formatTimestamp(t, AuroraConfig.ChatTimestampFormat.HH_MM));
        assertEquals("[21:05:09] ", ChatFormatting.formatTimestamp(t, AuroraConfig.ChatTimestampFormat.HH_MM_SS));
        assertEquals("[9:05 PM] ", ChatFormatting.formatTimestamp(t, AuroraConfig.ChatTimestampFormat.H_MM_A));
    }

    @Test
    void normalizesDuplicatesAndExtractsCopyText() {
        assertEquals("gg", ChatFormatting.normalizeForDuplicate("[21:05] gg [x3]"));
        assertEquals("gg", ChatFormatting.normalizeForDuplicate("[9:05 PM] \u00A7agg [x12]"));
        assertEquals("<Alex> hello", ChatFormatting.extractCopyText("[21:05:09] <Alex> hello [x2]"));
        assertEquals(" [x4]", ChatFormatting.duplicateBadge(4));
        assertEquals("", ChatFormatting.duplicateBadge(1));
    }

    @Test
    void extractsOnlineSenderFromCommonChatFormats() {
        List<String> online = List.of("Alex", "Notch", "PvP_King");
        assertEquals("Alex", ChatFormatting.extractSenderName("<Alex> hello everyone", online));
        assertEquals("PvP_King", ChatFormatting.extractSenderName("[MVP+] PvP_King: gg!", online));
        assertEquals("Notch", ChatFormatting.extractSenderName("[21:05] [Lobby] Notch \u00BB welcome", online));
        assertNull(ChatFormatting.extractSenderName("Server will restart in 5 minutes", online));
        // Mentioning a player late in a system message must not misattribute the head.
        assertNull(ChatFormatting.extractSenderName("The match has ended! Winner of the tournament was Alex", online));
    }

    @Test
    void matchesMultiTermSearchCaseInsensitively() {
        assertTrue(ChatFormatting.matchesSearch("[21:05] <Alex> Diamond sword trade", "alex sword"));
        assertTrue(ChatFormatting.matchesSearch("<Alex> hello", ""));
        assertFalse(ChatFormatting.matchesSearch("<Alex> hello", "alex goodbye"));
    }

    @Test
    void persistsAndTruncatesPerServerHistoryAtomically(@TempDir Path tempDir) {
        Path prev = null;
        try {
            prev = ConfigDirectory.get();
        } catch (IllegalStateException ignored) {}
        ConfigDirectory.set(tempDir);
        try {
            List<ChatHistoryStore.Entry> entries = List.of(
                    new ChatHistoryStore.Entry(1000L, "line 1"),
                    new ChatHistoryStore.Entry(2000L, "line 2"),
                    new ChatHistoryStore.Entry(3000L, "line 3"));
            ChatHistoryStore.saveAsync("play.example.net:25565", entries, 2);
            assertTrue(ChatHistoryStore.flush(3000L));

            List<ChatHistoryStore.Entry> loaded = ChatHistoryStore.load("play.example.net:25565", 10);
            assertEquals(2, loaded.size());
            assertEquals("line 2", loaded.get(0).text());
            assertEquals("line 3", loaded.get(1).text());
        } finally {
            if (prev != null) ConfigDirectory.set(prev);
        }
    }
}
