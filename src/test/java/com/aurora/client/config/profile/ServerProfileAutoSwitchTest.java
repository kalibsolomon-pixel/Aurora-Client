package com.aurora.client.config.profile;

import com.aurora.client.config.AtomicJsonWriter;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ServerProfileAutoSwitchTest {
    @TempDir static Path temporary;

    @BeforeAll
    static void isolate() {
        ConfigDirectory.set(temporary);
        ProfileManager.getInstance().load();
    }

    @AfterAll
    static void drain() {
        ProfileManager.getInstance().switchTo("default");
        ProfileManager.getInstance().saveCurrentBlocking();
        assertTrue(AuroraConfig.flushPending(5000));
    }

    private static String unique(String prefix) {
        return prefix + "-" + System.nanoTime();
    }

    @Test
    void parsesFormatsAndMatchesServerPatterns() {
        assertEquals(List.of("*.hypixel.net", "minemen.club", "play.example.gg:25566"),
                ServerPatternMatcher.parsePatterns(" *.Hypixel.NET, minemen.club:25565; play.example.gg:25566, minemen.club "));
        assertEquals("*.hypixel.net, minemen.club",
                ServerPatternMatcher.formatPatterns(List.of("*.hypixel.net", "minemen.club")));

        assertTrue(ServerPatternMatcher.matches("*.hypixel.net", "mc.hypixel.net"));
        assertTrue(ServerPatternMatcher.matches("*.hypixel.net", "play.hypixel.net:25565"));
        assertTrue(ServerPatternMatcher.matches("*.hypixel.net", "hypixel.net"));
        assertFalse(ServerPatternMatcher.matches("*.hypixel.net", "fakehypixel.net"));

        assertTrue(ServerPatternMatcher.matches("minemen.club", "minemen.club:25565"));
        assertTrue(ServerPatternMatcher.matches("minemen.club", "minemen.club:25566"));
        assertFalse(ServerPatternMatcher.matches("minemen.club:25566", "minemen.club:25565"));
        assertTrue(ServerPatternMatcher.matches("minemen.club:25566", "minemen.club:25566"));

        assertTrue(ServerPatternMatcher.specificity("eu.minemen.club")
                > ServerPatternMatcher.specificity("*.minemen.club"));
    }

    @Test
    void autoSwitchesOnJoinAndRestoresPreviousProfileOnDisconnect() {
        var manager = ProfileManager.getInstance();
        String base = unique("base");
        String hypixel = unique("hypixel");
        String specific = unique("specific");

        assertTrue(manager.create(base));
        assertTrue(manager.create(hypixel));
        assertTrue(manager.create(specific));

        assertTrue(manager.setServers(hypixel, List.of("*.hypixel.net")));
        assertTrue(manager.setServers(specific, List.of("alpha.hypixel.net")));
        assertEquals(List.of("*.hypixel.net"), manager.servers(hypixel));

        // Start on base profile
        assertTrue(manager.switchTo(base));
        assertEquals(base, manager.currentProfile());

        // Specific host beats wildcard
        assertEquals(specific, manager.matchProfileForServer("alpha.hypixel.net:25565"));
        assertEquals(hypixel, manager.matchProfileForServer("mc.hypixel.net"));

        // Joining mc.hypixel.net auto-switches to hypixel
        assertEquals(hypixel, manager.onServerJoin("mc.hypixel.net"));
        assertEquals(hypixel, manager.currentProfile());

        // Disconnecting restores base
        assertEquals(base, manager.onServerDisconnect());
        assertEquals(base, manager.currentProfile());

        // Calling disconnect again is a no-op
        assertNull(manager.onServerDisconnect());
    }

    @Test
    void manualSwitchDuringSessionIsRespectedOnDisconnectAndServersSurviveSaves() {
        var manager = ProfileManager.getInstance();
        String pvp = unique("pvp");
        String manual = unique("manual");
        assertTrue(manager.create(pvp));
        assertTrue(manager.create(manual));
        assertTrue(manager.setServers(pvp, List.of("*.minemen.club")));

        // Verify metadata survives saveCurrent + switch
        assertTrue(manager.switchTo(pvp));
        manager.saveCurrentBlocking();
        assertEquals(List.of("*.minemen.club"), manager.servers(pvp));

        // Duplicate starts with no server bindings
        String copy = unique("copy");
        assertTrue(manager.duplicate(pvp, copy));
        assertTrue(manager.servers(copy).isEmpty());

        // Switch back to default, join minemen -> auto-switches to pvp
        assertTrue(manager.switchTo("default"));
        assertEquals(pvp, manager.onServerJoin("na.minemen.club"));

        // Player manually switches to another profile mid-session
        assertTrue(manager.switchTo(manual));
        assertEquals(manual, manager.currentProfile());

        // Disconnect must NOT override the player's manual choice
        assertNull(manager.onServerDisconnect());
        assertEquals(manual, manager.currentProfile());
    }

    @Test
    void manualSwitchBackToAutoSelectedProfileStillPreventsDisconnectOverride() {
        var manager = ProfileManager.getInstance();
        String a = unique("profA");
        String b = unique("profB");
        String c = unique("profC");
        assertTrue(manager.create(a));
        assertTrue(manager.create(b));
        assertTrue(manager.create(c));
        assertTrue(manager.setServers(b, List.of("play.backtest.example")));

        // Start on A -> auto-switch to B -> manually switch to C -> manually return to B
        assertTrue(manager.switchTo(a));
        assertEquals(b, manager.onServerJoin("play.backtest.example"));
        assertTrue(manager.switchTo(c));
        assertTrue(manager.switchTo(b));

        // Disconnect must respect the player's last manual selection (B), not restore A
        assertNull(manager.onServerDisconnect());
        assertEquals(b, manager.currentProfile());
    }

    @Test
    void renamingActiveOrPreviousProfileDuringAutoSwitchPreservesDisconnectRestore() {
        var manager = ProfileManager.getInstance();
        String a = unique("origA");
        String a2 = unique("renamedA");
        String b = unique("origB");
        String b2 = unique("renamedB");
        assertTrue(manager.create(a));
        assertTrue(manager.create(b));
        assertTrue(manager.setServers(b, List.of("rename-test.example")));
        assertTrue(manager.flushPending(5000));

        // 1. Rename the auto-selected profile during session -> disconnect still restores A
        assertTrue(manager.switchTo(a));
        assertEquals(b, manager.onServerJoin("rename-test.example"));
        assertTrue(manager.renameAsync(b, b2).join());
        assertTrue(manager.flushPending(5000));
        assertEquals(b2, manager.currentProfile());
        assertEquals(a, manager.onServerDisconnect());
        assertEquals(a, manager.currentProfile());

        // 2. Rename the previous profile during session -> disconnect restores renamed A2
        assertEquals(b2, manager.onServerJoin("rename-test.example"));
        assertTrue(manager.renameAsync(a, a2).join());
        assertTrue(manager.flushPending(5000));
        assertEquals(a2, manager.onServerDisconnect());
        assertEquals(a2, manager.currentProfile());
    }

    @Test
    void failedProfileRenamePreservesOldFileAndActivePointerUntilRetrySucceeds() throws Exception {
        var manager = ProfileManager.getInstance();
        String oldName = unique("keepme");
        String newName = unique("blocked");
        Path profilesDir = manager.profilesDirectory();
        Path oldFile = profilesDir.resolve(oldName + ".json");
        Path newFile = profilesDir.resolve(newName + ".json");

        assertTrue(manager.create(oldName));
        assertTrue(manager.switchTo(oldName));
        assertTrue(manager.flushPending(5000));
        assertTrue(Files.exists(oldFile));

        AtomicBoolean blockNew = new AtomicBoolean(true);
        AtomicJsonWriter.FileOps faultOps = new AtomicJsonWriter.FileOps() {
            @Override public void writeDurably(Path f, String c) throws IOException {
                AtomicJsonWriter.REAL_FILES.writeDurably(f, c);
            }
            @Override public void replace(Path s, Path t) throws IOException {
                if (blockNew.get() && t.equals(newFile)) {
                    throw new AccessDeniedException(s.toString(), t.toString(), "simulated lock");
                }
                AtomicJsonWriter.REAL_FILES.replace(s, t);
            }
            @Override public void deleteIfExists(Path f) throws IOException {
                AtomicJsonWriter.REAL_FILES.deleteIfExists(f);
            }
        };
        AtomicJsonWriter faultWriter = new AtomicJsonWriter("test-rename-fault", faultOps, ms -> {}, 0);
        manager.setWriterForTest(faultWriter);
        try {
            Boolean failed = manager.renameAsync(oldName, newName).join();
            assertFalse(failed, "rename must report failure when new path is denied");
            assertTrue(Files.exists(oldFile), "old profile file must not be deleted on rename failure");
            assertFalse(Files.exists(newFile), "blocked new file must not exist");
            assertEquals(oldName, manager.currentProfile(), "active profile remains unchanged");
            assertEquals(oldName, AuroraConfig.get().activeProfile, "config activeProfile pointer stays intact");

            // Lift the fault, retry rename, flush, and reload from disk
            blockNew.set(false);
            Boolean succeeded = manager.renameAsync(oldName, newName).join();
            assertTrue(succeeded, "rename retry must succeed once destination is writable");
            assertTrue(faultWriter.flush(5000));
            assertTrue(Files.exists(newFile));
            assertFalse(Files.exists(oldFile));
            assertEquals(newName, manager.currentProfile());
            assertEquals(newName, AuroraConfig.get().activeProfile);

            // Reopen / reload verifies persistence
            manager.load();
            assertEquals(newName, manager.currentProfile());
            assertTrue(manager.listProfileNames().contains(newName));
            assertFalse(manager.listProfileNames().contains(oldName));
        } finally {
            manager.setWriterForTest(null);
        }
    }

    @Test
    void matchesIpv6WithAndWithoutBracketsPortsAndRegressionControls() {
        // IPv6 bracketed / unbracketed / canonical spelling equivalence
        assertTrue(ServerPatternMatcher.matches("[::1]", "::1"));
        assertTrue(ServerPatternMatcher.matches("::1", "[::1]"));
        assertTrue(ServerPatternMatcher.matches("::1", "[::1]:25566"));
        assertTrue(ServerPatternMatcher.matches("[::1]", "[::1]:25565"));
        assertTrue(ServerPatternMatcher.matches("[0:0:0:0:0:0:0:1]", "::1"));
        assertTrue(ServerPatternMatcher.matches("[0:0:0:0:0:0:0:1]:25565", "[::1]"));
        assertTrue(ServerPatternMatcher.matches("2001:db8::1", "[2001:0DB8::0001]:25566"));

        // Explicit non-default IPv6 port
        assertTrue(ServerPatternMatcher.matches("[::1]:25566", "[::1]:25566"));
        assertFalse(ServerPatternMatcher.matches("[::1]:25566", "::1"));
        assertFalse(ServerPatternMatcher.matches("[::1]:25566", "[::1]:25565"));
        assertFalse(ServerPatternMatcher.matches("[::1]:25566", "[::1]:25567"));

        // Normalization deduplicates equivalent IPv6 spellings and keeps non-default ports bracketed
        assertEquals(List.of("::1", "[::1]:25566"),
                ServerPatternMatcher.parsePatterns("[::1], ::1, [0:0:0:0:0:0:0:1]:25565, [::1]:25566"));

        // Specificity orders exact IPv6+port > exact IPv6 > wildcard IPv6
        assertTrue(ServerPatternMatcher.specificity("[::1]:25566")
                > ServerPatternMatcher.specificity("::1"));
        assertTrue(ServerPatternMatcher.specificity("2001:db8::1")
                > ServerPatternMatcher.specificity("2001:db8::*"));

        // IPv4 and hostname regression controls
        assertTrue(ServerPatternMatcher.matches("127.0.0.1", "127.0.0.1:25565"));
        assertTrue(ServerPatternMatcher.matches("127.0.0.1", "127.0.0.1:25566"));
        assertTrue(ServerPatternMatcher.matches("127.0.0.1:25566", "127.0.0.1:25566"));
        assertFalse(ServerPatternMatcher.matches("127.0.0.1:25566", "127.0.0.1:25565"));
        assertTrue(ServerPatternMatcher.matches("*.example.net", "play.example.net.:25565"));
    }
}
