package com.aurora.client.launcher;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ActivityProtocolTest {
    static Map<String, String> environment(int port) {
        return Map.of(BridgeBootstrap.ENDPOINT, "127.0.0.1:" + port,
                BridgeBootstrap.SESSION, "4b609826-a9a8-4fa6-a7d4-57e7f373900e",
                BridgeBootstrap.CAPABILITY, "ab".repeat(32), BridgeBootstrap.VERSION, "1");
    }

    @Test void validBootstrapResolvesOnlyLiteralIpv4Loopback() {
        var bootstrap = BridgeBootstrap.fromEnvironment(environment(12345));
        assertTrue(bootstrap.endpoint.getAddress().isLoopbackAddress());
        assertEquals(12345, bootstrap.endpoint.getPort());
        assertEquals(64, bootstrap.capability.length());
        assertEquals("BridgeBootstrap[redacted]", bootstrap.toString());
    }

    @Test void absentBootstrapDoesNothing() { assertNull(BridgeBootstrap.fromEnvironment(Map.of())); }

    @Test void partialBootstrapIsRejected() {
        for (String key : environment(12345).keySet()) {
            var env = new HashMap<>(environment(12345));
            env.remove(key);
            assertThrows(IllegalArgumentException.class, () -> BridgeBootstrap.fromEnvironment(env));
        }
    }

    @Test void remoteDnsAlternateLoopbackAndMalformedEndpointsAreRejected() {
        for (String endpoint : new String[]{"localhost:12", "127.0.0.2:12", "[::1]:12", "0.0.0.0:12",
                "192.168.1.1:12", "example.invalid:12", "127.0.0.1:0", "127.0.0.1:65536",
                "127.0.0.1:012", "127.0.0.1:12\n", "127.0.0.1:1".repeat(1000)}) {
            var env = new HashMap<>(environment(12345));
            env.put(BridgeBootstrap.ENDPOINT, endpoint);
            assertThrows(IllegalArgumentException.class, () -> BridgeBootstrap.fromEnvironment(env), endpoint);
        }
    }

    @Test void capabilityMustBeExactly256BitsLowercaseHex() {
        for (String capability : new String[]{"", "a".repeat(63), "a".repeat(65), "G".repeat(64), "AB".repeat(32), "a\n".repeat(32)}) {
            var env = new HashMap<>(environment(12345));
            env.put(BridgeBootstrap.CAPABILITY, capability);
            assertThrows(IllegalArgumentException.class, () -> BridgeBootstrap.fromEnvironment(env));
        }
    }

    @Test void protocolVersionAndSessionAreStrict() {
        for (String version : new String[]{"0", "2", "01", "1\n"}) {
            var env = new HashMap<>(environment(12345));
            env.put(BridgeBootstrap.VERSION, version);
            assertThrows(IllegalArgumentException.class, () -> BridgeBootstrap.fromEnvironment(env));
        }
        var env = new HashMap<>(environment(12345));
        env.put(BridgeBootstrap.SESSION, "x".repeat(36));
        assertThrows(IllegalArgumentException.class, () -> BridgeBootstrap.fromEnvironment(env));
    }

    @Test void handshakeIsBoundedAndCapabilityNeverAppearsInActivity() throws Exception {
        var bootstrap = BridgeBootstrap.fromEnvironment(environment(12345));
        byte[] hello = ActivityProtocol.hello(bootstrap);
        assertTrue(hello.length <= ActivityProtocol.HANDSHAKE_BYTES);
        var json = JsonParser.parseString(new String(hello, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(bootstrap.capability, json.get("capability").getAsString());
        assertEquals("hello", json.get("type").getAsString());
        String activity = new String(ActivityProtocol.activity(bootstrap.sessionId, 1, ActivitySnapshot.mainMenu()), StandardCharsets.UTF_8);
        assertFalse(activity.contains(bootstrap.capability));
        assertFalse(activity.contains("capability"));
        assertFalse(activity.contains("worldDisplayName"));
    }

    @Test void hostileMinecraftStringsCannotInjectProtocolStructure() throws Exception {
        var snapshot = ActivitySnapshot.project(true, false, null, "\"},\"state\":\"MAIN_MENU\"\n", "example.invalid");
        byte[] bytes = ActivityProtocol.activity("session", 7, snapshot);
        String line = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(1, line.chars().filter(cp -> cp == '\n').count());
        var json = JsonParser.parseString(line).getAsJsonObject();
        assertEquals("MULTIPLAYER", json.get("state").getAsString());
        assertEquals(snapshot.serverDisplayName(), json.get("serverDisplayName").getAsString());
        assertEquals(7, json.get("sequence").getAsLong());
    }

    @Test void worstCaseEscapingFitsFrameBound() throws Exception {
        var snapshot = ActivitySnapshot.project(true, false, null, "\\".repeat(128), "😀".repeat(255));
        assertTrue(ActivityProtocol.activity("4b609826-a9a8-4fa6-a7d4-57e7f373900e", Long.MAX_VALUE, snapshot).length <= 4096);
        assertThrows(java.io.IOException.class, () -> ActivityProtocol.activity("session", 0, snapshot));
    }
}
