package com.aurora.client.launcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivitySnapshotTest {
    @Test void noLevelIsMainMenuEvenWithStaleServerData() {
        assertEquals(ActivitySnapshot.mainMenu(), ActivitySnapshot.project(false, true, "World", "Server", "example.invalid"));
    }

    @Test void loadedIntegratedServerIsSingleplayerIncludingLanHost() {
        var activity = ActivitySnapshot.project(true, true, "My World", "LAN", "127.0.0.1");
        assertEquals(ActivitySnapshot.State.SINGLEPLAYER, activity.state());
        assertEquals("My World", activity.worldDisplayName());
        assertNull(activity.serverDisplayName());
        assertNull(activity.serverAddress());
    }

    @Test void multiplayerKeepsDisplayNameAndAddressDistinct() {
        var activity = ActivitySnapshot.project(true, false, "ignored", "My SMP", "example.invalid:25565");
        assertEquals(ActivitySnapshot.State.MULTIPLAYER, activity.state());
        assertEquals("My SMP", activity.serverDisplayName());
        assertEquals("example.invalid:25565", activity.serverAddress());
        assertNull(activity.worldDisplayName());
    }

    @Test void missingWorldIdentityDoesNotInventDirectoryName() {
        var activity = ActivitySnapshot.project(true, true, null, null, null);
        assertEquals(ActivitySnapshot.State.SINGLEPLAYER, activity.state());
        assertNull(activity.worldDisplayName());
    }

    @Test void missingServerIdentityStillReportsMultiplayer() {
        var activity = ActivitySnapshot.project(true, false, null, null, null);
        assertEquals(ActivitySnapshot.State.MULTIPLAYER, activity.state());
        assertNull(activity.serverDisplayName());
        assertNull(activity.serverAddress());
    }

    @Test void controlsFormattingAndBrokenSurrogatesAreRemoved() {
        assertEquals("ABC", ActivitySnapshot.displayText(" A\r\n\t\0\u007f\u0085\u202eB\u2028\u2029\ud800C ", 128));
        assertNull(ActivitySnapshot.displayText(" \r\n\u202e ", 128));
        assertEquals("World", ActivitySnapshot.displayText("\u00a0\u2007World\u202f", 128));
    }

    @Test void unicodeBoundsAreCodePointsAndDoNotSplitSupplementaryCharacters() {
        var snapshot = ActivitySnapshot.project(true, false, null, "😀".repeat(129), "a".repeat(256));
        assertEquals(128, snapshot.serverDisplayName().codePointCount(0, snapshot.serverDisplayName().length()));
        assertEquals(255, snapshot.serverAddress().length());
    }

    @Test void adversarialInputWorkIsBounded() {
        assertNull(ActivitySnapshot.displayText("\0".repeat(5000) + "hidden", 128));
        assertNull(ActivitySnapshot.displayText("\0".repeat(4095) + "😀", 128));
        assertEquals(128, ActivitySnapshot.project(true, true, "a".repeat(10000), null, null).worldDisplayName().length());
    }

    @Test void snapshotsDoNotExposePrivateValuesThroughToString() {
        assertFalse(ActivitySnapshot.project(true, true, "Private World", null, null).toString().contains("Private"));
    }

    @Test void saveIdentityIsDistinctFromDisplayAndRejectsTraversal() {
        var first = ActivitySnapshot.project(true, true, "Old", null, null, "save-1", null);
        var renamed = ActivitySnapshot.project(true, true, "New", null, null, "save-1", null);
        assertEquals(first.worldSaveId(), renamed.worldSaveId());
        for (String id : new String[]{"..", "../other", "C:drive", "a\\b", "bad.", "bad\0name"}) {
            assertNull(ActivitySnapshot.project(true, true, "Display", null, null, id, null).worldSaveId());
        }
    }

    @Test void aLongServerAddressCannotBeTruncatedIntoAnotherTarget() {
        var snapshot = ActivitySnapshot.project(true, false, null, "Display", "example.invalid",
                null, "a".repeat(256));
        assertNull(snapshot.serverTarget());
        assertEquals("example.invalid", snapshot.serverAddress());
    }
}
