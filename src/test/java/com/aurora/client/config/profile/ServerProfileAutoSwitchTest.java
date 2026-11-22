package com.aurora.client.config.profile;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.config.ConfigDirectory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

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
}
