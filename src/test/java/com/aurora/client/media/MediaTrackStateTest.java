package com.aurora.client.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaTrackStateTest {

    @Test
    void parseLinesHandlesPlayingPausedStoppedAndMalformed() {
        String raw = "STATUS=Playing\nTITLE=Wet Hands\nARTIST=C418\nALBUM=Minecraft\nPOS=45000\nDUR=90000\n";
        MediaTrackState state = MediaTrackState.parseLines(raw, 1000L);
        assertTrue(state.active());
        assertTrue(state.playing());
        assertEquals("Wet Hands", state.title());
        assertEquals("C418", state.artist());
        assertEquals(45_000L, state.positionMs());
        assertEquals(90_000L, state.durationMs());
        assertEquals("Wet Hands \u2014 C418", state.compactLine());

        MediaTrackState paused = MediaTrackState.parseLines("STATUS=Paused\nTITLE=Subwoofer Lullaby\nPOS=bad\nDUR=120000\n", 2000L);
        assertTrue(paused.active());
        assertFalse(paused.playing());
        assertEquals(0L, paused.positionMs());
        assertEquals("Subwoofer Lullaby", paused.compactLine());

        assertEquals(MediaTrackState.EMPTY, MediaTrackState.parseLines("STATUS=Stopped\nTITLE=Old\n", 3000L));
        assertEquals(MediaTrackState.EMPTY, MediaTrackState.parseLines("", 3000L));
        assertEquals(MediaTrackState.EMPTY, MediaTrackState.parseLines(null, 3000L));
    }

    @Test
    void effectivePositionInterpolatesOnlyWhilePlayingAndClamps() {
        MediaTrackState playing = new MediaTrackState(true, true, "Track", "Artist", "", 10_000L, 15_000L, 1_000L);
        assertEquals(12_500L, playing.effectivePositionMs(3_500L));
        assertEquals(15_000L, playing.effectivePositionMs(9_000L));
        assertEquals(1.0f, playing.progress(9_000L), 0.001f);

        MediaTrackState paused = new MediaTrackState(true, false, "Track", "Artist", "", 10_000L, 20_000L, 1_000L);
        assertEquals(10_000L, paused.effectivePositionMs(8_000L));
        assertEquals(0.5f, paused.progress(8_000L), 0.001f);

        MediaTrackState liveStream = new MediaTrackState(true, true, "Radio", "", "", 5_000L, 0L, 1_000L);
        assertEquals(-1f, liveStream.progress(3_000L));
    }

    @Test
    void formatTimeHandlesMinutesAndHours() {
        assertEquals("0:00", MediaTrackState.formatTime(0L));
        assertEquals("0:09", MediaTrackState.formatTime(9_400L));
        assertEquals("3:35", MediaTrackState.formatTime(215_000L));
        assertEquals("1:05:09", MediaTrackState.formatTime(3_909_000L));
    }
}
