package com.aurora.client.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StreamerPrivacyTest {

    @BeforeEach
    void resetSession() {
        StreamerPrivacy.resetSession();
    }

    @Test
    void sanitizeAliasStripsCodesAndFallsBackWhenBlank() {
        assertEquals("You", StreamerPrivacy.sanitizeAlias(null, "You"));
        assertEquals("You", StreamerPrivacy.sanitizeAlias("   \t\n ", "You"));
        assertEquals("Streamer", StreamerPrivacy.sanitizeAlias("  \u00A7cStreamer\u00A7r  ", "You"));
        assertEquals("123456789012345678901234",
                StreamerPrivacy.sanitizeAlias("123456789012345678901234567890", "You"));
    }

    @Test
    void replaceNameMatchesWordBoundariesCaseInsensitively() {
        assertEquals("<You> hello", StreamerPrivacy.replaceName("<Alex> hello", "Alex", "You"));
        assertEquals("<You> hi You!", StreamerPrivacy.replaceName("<alex> hi ALEX!", "Alex", "You"));
        assertEquals("You's diamond sword", StreamerPrivacy.replaceName("Alex's diamond sword", "Alex", "You"));
        // Must not partially mangle longer identifiers.
        String longer = "Alexander and super_alex_99 joined";
        assertSame(longer, StreamerPrivacy.replaceName(longer, "Alex", "You"));
        // Zero-allocation fast-path when no match exists.
        String unrelated = "Welcome to the lobby";
        assertSame(unrelated, StreamerPrivacy.replaceName(unrelated, "Alex", "You"));
    }

    @Test
    void maskEditTextPreservesCharacterCount() {
        assertEquals("", StreamerPrivacy.maskEditText(""));
        assertEquals("", StreamerPrivacy.maskEditText(null));
        assertEquals("\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022",
                StreamerPrivacy.maskEditText("127.0.0.1"));
    }

    @Test
    void looksLikeServerAddressDistinguishesAddressesFromFriendlyNames() {
        assertTrue(StreamerPrivacy.looksLikeServerAddress("play.hypixel.net"));
        assertTrue(StreamerPrivacy.looksLikeServerAddress("eu.minemen.club:25565"));
        assertTrue(StreamerPrivacy.looksLikeServerAddress("192.168.1.42"));
        assertTrue(StreamerPrivacy.looksLikeServerAddress("127.0.0.1:25565"));
        assertTrue(StreamerPrivacy.looksLikeServerAddress("localhost"));
        assertTrue(StreamerPrivacy.looksLikeServerAddress("localhost:25565"));

        assertFalse(StreamerPrivacy.looksLikeServerAddress("Hypixel Network"));
        assertFalse(StreamerPrivacy.looksLikeServerAddress("My Survival Server"));
        assertFalse(StreamerPrivacy.looksLikeServerAddress("Practice"));
        assertFalse(StreamerPrivacy.looksLikeServerAddress(""));
    }

    @Test
    void redactServerAddressesMasksActiveIpAndInlineHosts() {
        assertEquals("Connecting to Hidden Server...",
                StreamerPrivacy.redactServerAddresses("Connecting to play.myprivate.gg:25565...", "play.myprivate.gg:25565"));
        assertEquals("Connecting to Hidden Server...",
                StreamerPrivacy.redactServerAddresses("Connecting to play.myprivate.gg...", "play.myprivate.gg:25565"));
        assertEquals("Server 10.0.0.5 -> Hidden Server".replace("10.0.0.5", "Hidden Server"),
                StreamerPrivacy.redactServerAddresses("Server 10.0.0.5 -> 192.168.0.1:25565", null));
        assertEquals("Join Hidden Server now",
                StreamerPrivacy.redactServerAddresses("Join play.hypixel.net now", null));
        String plain = "Connecting to server...";
        assertSame(plain, StreamerPrivacy.redactServerAddresses(plain, null));
    }

    @Test
    void opponentTagAssignsStableSessionNumbersAndSupportsCustomLabels() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals("Opponent 1", StreamerPrivacy.opponentTag(a, "Opponent", true));
        assertEquals("Opponent 2", StreamerPrivacy.opponentTag(b, "Opponent", true));
        assertEquals("Opponent 1", StreamerPrivacy.opponentTag(a, "Opponent", true));
        assertEquals("Rival 2", StreamerPrivacy.opponentTag(b, "Rival", true));
        assertEquals("Enemy", StreamerPrivacy.opponentTag(a, "Enemy", false));

        StreamerPrivacy.resetSession();
        assertEquals("Opponent 1", StreamerPrivacy.opponentTag(b, "Opponent", true));
    }
}
