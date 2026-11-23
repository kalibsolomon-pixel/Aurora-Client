package com.aurora.client.chat;

import com.aurora.client.config.AtomicJsonWriter;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    void fastReconnectReadsQueuedSnapshotBuffersDuringLoadAndRejectsStaleGeneration(@TempDir Path tempDir) throws Exception {
        Path prev = null;
        try { prev = ConfigDirectory.get(); } catch (IllegalStateException ignored) {}
        ConfigDirectory.set(tempDir);

        CountDownLatch writeEntered = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        AtomicJsonWriter.FileOps blockingOps = new AtomicJsonWriter.FileOps() {
            private boolean blockedOnce;
            @Override public void writeDurably(Path f, String c) throws IOException {
                AtomicJsonWriter.REAL_FILES.writeDurably(f, c);
            }
            @Override public void replace(Path s, Path t) throws IOException {
                if (!blockedOnce) {
                    blockedOnce = true;
                    writeEntered.countDown();
                    try { releaseWrite.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
                }
                AtomicJsonWriter.REAL_FILES.replace(s, t);
            }
            @Override public void deleteIfExists(Path f) throws IOException { AtomicJsonWriter.REAL_FILES.deleteIfExists(f); }
        };
        AtomicJsonWriter testWriter = new AtomicJsonWriter("chat-test", blockingOps, ms -> {}, 0);
        ChatHistoryStore.setWriterForTest(testWriter);
        try {
            String scope = "mp:fast.example.net";
            ChatHistorySession session = new ChatHistorySession();

            // Visit 1: receive message A, disconnect (write blocks before hitting disk).
            long g1 = session.beginSession(scope, true, 100, CompletableFuture.completedFuture(List.of()));
            assertTrue(session.completeLoad(g1, List.of(), 1000L));
            session.recordIncoming(scope, "msg-1", 1000L, true, 100);
            session.disconnect().join();
            assertTrue(writeEntered.await(5, TimeUnit.SECONDS));

            // Visit 2 starts while visit 1's snapshot is still in flight (not yet on disk):
            // load() MUST read-your-writes from AtomicJsonWriter.latest().
            CompletableFuture<List<ChatHistoryStore.Entry>> visit2Load = new CompletableFuture<>();
            long g2 = session.beginSession(scope, true, 100, visit2Load);
            assertNotEquals(g1, g2);

            // A message arrives before visit 2's load returns; it must buffer without overwriting disk.
            session.recordIncoming(scope, "msg-2", 2000L, true, 100);

            // A delayed completion from visit 1 (same scope, older generation) must be rejected.
            assertFalse(session.completeLoad(g1, List.of(new ChatHistoryStore.Entry(500L, "stale")), 2100L));

            // Now visit 2's load reads from ChatHistoryStore (getting msg-1 via read-your-writes).
            List<ChatHistoryStore.Entry> loaded = ChatHistoryStore.load(scope, 100);
            assertEquals(List.of("msg-1"), loaded.stream().map(ChatHistoryStore.Entry::text).toList());
            visit2Load.complete(loaded);
            assertTrue(session.completeLoad(g2, loaded, 2200L));

            // Disconnect visit 2 and release writer -> reopen must show msg-1 then msg-2, each once.
            session.disconnect().join();
            releaseWrite.countDown();
            assertTrue(testWriter.flush(5000));

            List<ChatHistoryStore.Entry> finalHistory = ChatHistoryStore.load(scope, 100);
            assertEquals(List.of("msg-1", "msg-2"), finalHistory.stream().map(ChatHistoryStore.Entry::text).toList());
        } finally {
            releaseWrite.countDown();
            ChatHistoryStore.setWriterForTest(null);
            if (prev != null) ConfigDirectory.set(prev);
        }
    }

    @Test
    void disconnectFlushesWithOutgoingSessionPolicyEvenIfLiveConfigDisablesHistory(@TempDir Path tempDir) {
        Path prev = null;
        try { prev = ConfigDirectory.get(); } catch (IllegalStateException ignored) {}
        ConfigDirectory.set(tempDir);
        try {
            String scope = "mp:profile-restore.example.net";
            ChatHistorySession session = new ChatHistorySession();
            long gen = session.beginSession(scope, true, 120, CompletableFuture.completedFuture(List.of()));
            assertTrue(session.completeLoad(gen, List.of(), 1000L));

            // Simulate messages arriving (fewer than periodic save threshold of 5, within 5 s).
            session.recordIncoming(scope, "first line", 1000L, true, 120);
            session.recordIncoming(scope, "final line before disconnect", 1500L, true, 120);

            // Simulate ServerProfileSwitcher restoring another profile with history OFF and lower limit.
            AuroraConfig cfg = AuroraConfig.get();
            boolean oldChat = cfg.chatEnabled;
            boolean oldHist = cfg.chatPerServerHistory;
            int oldMax = cfg.chatPerServerMaxSaved;
            try {
                cfg.chatEnabled = false;
                cfg.chatPerServerHistory = false;
                cfg.chatPerServerMaxSaved = 50;

                // Disconnect MUST still flush the outgoing session's buffered final messages.
                session.disconnect().join();
                assertTrue(ChatHistoryStore.flush(3000L));
            } finally {
                cfg.chatEnabled = oldChat;
                cfg.chatPerServerHistory = oldHist;
                cfg.chatPerServerMaxSaved = oldMax;
            }

            List<ChatHistoryStore.Entry> reloaded = ChatHistoryStore.load(scope, 200);
            assertEquals(List.of("first line", "final line before disconnect"),
                    reloaded.stream().map(ChatHistoryStore.Entry::text).toList());
        } finally {
            if (prev != null) ConfigDirectory.set(prev);
        }
    }

    @Test
    void singleplayerSavesWithSameDisplayNameStayIsolatedAndSurviveDisplayRename(@TempDir Path tempDir) throws Exception {
        Path prev = null;
        try { prev = ConfigDirectory.get(); } catch (IllegalStateException ignored) {}
        ConfigDirectory.set(tempDir);
        try {
            Path saves = tempDir.resolve("saves");
            Path saveA = saves.resolve("New World");
            Path saveB = saves.resolve("New World (1)");
            Files.createDirectories(saveA);
            Files.createDirectories(saveB);

            String scopeA = ChatFormatting.singleplayerScope(saveA);
            String scopeB = ChatFormatting.singleplayerScope(saveB);
            assertNotNull(scopeA);
            assertNotNull(scopeB);
            assertNotEquals(scopeA, scopeB);
            assertNotEquals(ChatHistoryStore.fileForScope(scopeA), ChatHistoryStore.fileForScope(scopeB));

            ChatHistoryStore.saveAsync(scopeA, List.of(new ChatHistoryStore.Entry(1L, "hello from A")), 50);
            ChatHistoryStore.saveAsync(scopeB, List.of(new ChatHistoryStore.Entry(2L, "hello from B")), 50);
            assertTrue(ChatHistoryStore.flush(3000L));

            // Changing display name does not change the save-directory scope; both worlds keep their own history.
            assertEquals(List.of("hello from A"),
                    ChatHistoryStore.load(ChatFormatting.singleplayerScope(saveA), 50).stream().map(ChatHistoryStore.Entry::text).toList());
            assertEquals(List.of("hello from B"),
                    ChatHistoryStore.load(ChatFormatting.singleplayerScope(saveB), 50).stream().map(ChatHistoryStore.Entry::text).toList());

            // Legacy name-based file: auto-migrates ONLY when unambiguous, preserving legacy file on disk.
            String legacyScope = "sp:Legacy World";
            ChatHistoryStore.saveAsync(legacyScope, List.of(new ChatHistoryStore.Entry(3L, "old world chat")), 50);
            assertTrue(ChatHistoryStore.flush(3000L));

            Path ambiguousSave = saves.resolve("Ambiguous");
            Files.createDirectories(ambiguousSave);
            String ambScope = ChatFormatting.singleplayerScope(ambiguousSave);
            assertTrue(ChatHistoryStore.loadOrMigrateLegacy(ambScope, legacyScope, false, 50).isEmpty(),
                    "ambiguous legacy file must not be auto-migrated");
            assertTrue(Files.exists(ChatHistoryStore.fileForScope(legacyScope)), "legacy file stays preserved");

            Path uniqueSave = saves.resolve("Legacy World");
            Files.createDirectories(uniqueSave);
            String uniqueScope = ChatFormatting.singleplayerScope(uniqueSave);
            List<ChatHistoryStore.Entry> migrated = ChatHistoryStore.loadOrMigrateLegacy(uniqueScope, legacyScope, true, 50);
            assertTrue(ChatHistoryStore.flush(3000L));
            assertEquals(List.of("old world chat"), migrated.stream().map(ChatHistoryStore.Entry::text).toList());
            assertTrue(Files.exists(ChatHistoryStore.fileForScope(uniqueScope)), "migrated copy written to save scope");
            assertTrue(Files.exists(ChatHistoryStore.fileForScope(legacyScope)), "legacy file kept for recovery");
        } finally {
            if (prev != null) ConfigDirectory.set(prev);
        }
    }

    @Test
    void chatSearchFiltersDisplayOnlyAndAttachesMetadataToAcceptedMessage() throws Exception {
        String screenSrc = Files.readString(Path.of(
                "adapters/shared/src/client/java/com/aurora/client/mixin/ChatScreenSuiteMixin.java"));
        String compSrc = Files.readString(Path.of(
                "adapters/shared/src/client/java/com/aurora/client/mixin/ChatComponentSuiteMixin.java"));

        assertFalse(screenSrc.contains("setVisibleMessageFilter"),
                "Chat search must not overwrite vanilla visibleMessageFilter");
        assertTrue(screenSrc.contains("aurora$setSearchQuery"),
                "Chat search routes queries through ChatSuiteAccess");
        assertTrue(compSrc.contains("method = \"addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V\""),
                "Search filters visible lines in addMessageToDisplayQueue while keeping ingestion intact");
        assertTrue(compSrc.contains("method = \"addMessageToQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V\""),
                "Metadata attaches to the exact accepted GuiMessage in addMessageToQueue");
        assertFalse(compSrc.contains("allMessages.getFirst()"),
                "Must not blindly attach metadata to allMessages.getFirst() after addMessage");
    }
}
