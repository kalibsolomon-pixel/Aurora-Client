package com.aurora.client.media;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Windows media helper failed silently twice (2026-11-22): a quoted
 * {@code -Command} lost its inner quotes, and Task.IsCompletedSuccessfully is
 * missing from the .NET Framework that Windows PowerShell 5.1 runs on.
 */
class WindowsMediaHelperContractTest {
    private static final Path SCRIPT = Path.of(
            "adapters/shared/src/client/resources/com/aurora/client/media/windows-media-helper.ps1");
    private static final Path BRIDGE = Path.of(
            "adapters/shared/src/client/java/com/aurora/client/media/SystemMediaBridge.java");

    @Test void theScriptRunsOnWindowsPowerShell51() throws Exception {
        String script = Files.readString(SCRIPT);
        assertFalse(script.contains(".IsCompletedSuccessfully"), "absent on .NET Framework; test Task.Status");
        assertTrue(script.contains("RanToCompletion"));
        assertTrue(script.contains("TryTogglePlayPauseAsync") && script.contains("TrySkipNextAsync")
                && script.contains("TrySkipPreviousAsync"), "commands go to the media session first");
    }

    @Test void coversComeAsSmallPngsOnlyWhenAskedFor() throws Exception {
        String script = Files.readString(SCRIPT);
        assertTrue(script.contains("$line -eq 'POLL ART'"));
        assertTrue(script.contains("$ART_SIZE = 96"));
        assertTrue(script.contains("GetMethod('AsStreamForRead', [type[]]@($inputType))"),
                "PowerShell cannot pick the overload for a WinRT stream itself");
        assertTrue(script.contains("$lastArt = $null }"), "a new track re-sends its cover even when the album repeats");
        String bridge = Files.readString(BRIDGE);
        assertTrue(bridge.contains("public static final int COVER_SIZE = 96;"), "the texture size matches the helper's");
        assertTrue(bridge.contains("if (line.startsWith(\"ART=\")) art = line.substring(4);"));
    }

    @Test void aTrackKeyIgnoresPlayback() {
        var a = new MediaTrackState(true, true, "Bones", "Imagine Dragons", "Mercury", 1_000L, 165_000L, 5L);
        var b = new MediaTrackState(true, false, "Bones", "Imagine Dragons", "Mercury", 90_000L, 165_000L, 9L);
        assertEquals(a.trackKey(), b.trackKey());
        assertNotEquals(a.trackKey(), new MediaTrackState(true, true, "Bones", "Imagine Dragons", "Other", 0L, 0L, 0L).trackKey());
    }

    @Test void theBridgePassesTheScriptEncodedAndDrainsBothStreams() throws Exception {
        String bridge = Files.readString(BRIDGE);
        assertTrue(bridge.contains("\"-EncodedCommand\""));
        assertFalse(bridge.contains("\"-Command\""));
        assertTrue(bridge.contains("UTF_16LE"));
        assertTrue(bridge.contains("process.getErrorStream()"), "an undrained stderr pipe blocks the helper");
        assertFalse(bridge.contains("private static synchronized void dispatchWindows"),
                "helper reads must not hold the lock worker() takes on the tick thread");
    }
}
