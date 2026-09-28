package com.aurora.client.launcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(12)
class LauncherActivityBridgeTest {
    private static ServerSocket listener() throws IOException {
        var server = new ServerSocket(0, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
        server.setSoTimeout(4000);
        return server;
    }

    private static JsonObject read(Socket socket) throws IOException {
        socket.setSoTimeout(4000);
        var bytes = new ByteArrayOutputStream();
        int ch;
        while ((ch = socket.getInputStream().read()) != -1) {
            if (ch == '\n') return JsonParser.parseString(bytes.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            bytes.write(ch);
            if (bytes.size() >= 4096) throw new IOException("Test frame exceeds limit");
        }
        throw new IOException("Test EOF");
    }

    private static void accepted(Socket socket) throws IOException {
        socket.getOutputStream().write(ActivityProtocol.ACCEPTED.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static void terminated(LauncherActivityBridge bridge) throws InterruptedException {
        bridge.worker().join(4500);
        assertFalse(bridge.worker().isAlive(), "worker must terminate without leaking");
        assertTrue(bridge.isStopped());
    }

    @Test void authenticatedTransitionsAreOrderedAndDuplicatesAreSuppressed() throws Exception {
        try (var server = listener()) {
            var bootstrap = BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort()));
            try (var bridge = new LauncherActivityBridge(bootstrap, ignored -> {}); var socket = server.accept()) {
                var hello = read(socket);
                assertEquals(bootstrap.capability, hello.get("capability").getAsString());
                assertEquals(bootstrap.sessionId, hello.get("sessionId").getAsString());
                accepted(socket);
                var sp = ActivitySnapshot.project(true, true, "Fixture World", null, null);
                var mpA = ActivitySnapshot.project(true, false, null, "Fixture A", "a.invalid");
                var mpB = ActivitySnapshot.project(true, false, null, "Fixture B", "b.invalid");
                bridge.publish(sp); bridge.publish(sp); bridge.publish(ActivitySnapshot.mainMenu());
                bridge.publish(mpA); bridge.publish(ActivitySnapshot.mainMenu()); bridge.publish(mpB);
                bridge.publish(ActivitySnapshot.mainMenu());
                String[] states = {"MAIN_MENU", "SINGLEPLAYER", "MAIN_MENU", "MULTIPLAYER", "MAIN_MENU", "MULTIPLAYER", "MAIN_MENU"};
                for (int i = 0; i < states.length; i++) {
                    var update = read(socket);
                    assertEquals(i + 1, update.get("sequence").getAsLong());
                    assertEquals(states[i], update.get("state").getAsString());
                    assertEquals(bootstrap.sessionId, update.get("sessionId").getAsString());
                    if (i == 1) assertEquals("Fixture World", update.get("worldDisplayName").getAsString());
                    if (i == 3) {
                        assertEquals("Fixture A", update.get("serverDisplayName").getAsString());
                        assertEquals("a.invalid", update.get("serverAddress").getAsString());
                    }
                }
                bridge.close(); terminated(bridge);
                assertEquals(-1, socket.getInputStream().read());
            }
        }
    }

    @Test void v2WorkerTransportsIdentitySeparatelyFromDisplay() throws Exception {
        try (var server = listener()) {
            var env = new java.util.HashMap<>(ActivityProtocolTest.environment(server.getLocalPort()));
            env.put(BridgeBootstrap.VERSION, "2");
            var bootstrap = BridgeBootstrap.fromEnvironment(env);
            try (var bridge = new LauncherActivityBridge(bootstrap, ignored -> {}); var socket = server.accept()) {
                assertEquals(2, read(socket).get("schemaVersion").getAsInt());
                socket.getOutputStream().write(ActivityProtocol.ACCEPTED_V2.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().flush();
                assertEquals("MAIN_MENU", read(socket).get("state").getAsString());
                bridge.publish(ActivitySnapshot.project(true, true, "Renamed World", null, null, "stable-save", null));
                var world = read(socket);
                assertEquals(2, world.get("schemaVersion").getAsInt());
                assertEquals("Renamed World", world.get("worldDisplayName").getAsString());
                assertEquals("stable-save", world.get("worldSaveId").getAsString());
                bridge.publish(ActivitySnapshot.mainMenu());
                assertFalse(read(socket).has("worldSaveId"));
                bridge.publish(ActivitySnapshot.project(true, false, null, "Friendly Server", "example.invalid", null, "EXAMPLE.invalid"));
                var multiplayer = read(socket);
                assertEquals("Friendly Server", multiplayer.get("serverDisplayName").getAsString());
                assertEquals("EXAMPLE.invalid", multiplayer.get("serverTarget").getAsString());
                bridge.close(); terminated(bridge);
            }
        }
    }

    @Test void refusedConnectionDisablesWithoutRetry() throws Exception {
        int port;
        try (var unused = listener()) { port = unused.getLocalPort(); }
        try (var bridge = new LauncherActivityBridge(BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(port)), ignored -> {})) {
            terminated(bridge);
            bridge.publish(ActivitySnapshot.project(true, true, "World", null, null));
            assertTrue(bridge.isStopped());
        }
    }

    @Test void laterIdentityAvailabilityUpdatesTheSameGameplayState() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket); accepted(socket); read(socket);
            bridge.publish(ActivitySnapshot.project(true, false, null, null, null));
            bridge.publish(ActivitySnapshot.project(true, false, null, "Fixture SMP", "example.invalid"));
            var pending = read(socket);
            assertEquals(2, pending.get("sequence").getAsLong());
            assertEquals("MULTIPLAYER", pending.get("state").getAsString());
            assertFalse(pending.has("serverDisplayName"));
            assertFalse(pending.has("serverAddress"));
            var identified = read(socket);
            assertEquals(3, identified.get("sequence").getAsLong());
            assertEquals("MULTIPLAYER", identified.get("state").getAsString());
            assertEquals("Fixture SMP", identified.get("serverDisplayName").getAsString());
            assertEquals("example.invalid", identified.get("serverAddress").getAsString());
            bridge.close(); terminated(bridge);
        }
    }

    @Test void rejectionSendsNoActivityOrPrivateIdentity() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket);
            bridge.publish(ActivitySnapshot.project(true, true, "Private World", null, null));
            socket.shutdownOutput(); // Receiver rejected capability: no acknowledgement.
            terminated(bridge);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test void invalidVersionMalformedOversizedOrUnsolicitedAckDisables() throws Exception {
        for (String reply : new String[]{"{\"type\":\"accepted\",\"schemaVersion\":2}\n", "not json\n",
                "x".repeat(129), ActivityProtocol.ACCEPTED + "command\n"}) {
            try (var server = listener(); var bridge = new LauncherActivityBridge(
                    BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
                 var socket = server.accept()) {
                read(socket);
                socket.getOutputStream().write(reply.getBytes(StandardCharsets.UTF_8));
                terminated(bridge);
            }
        }
    }

    @Test void handshakeHasTotalDeadlineEvenWhenReceiverStalls() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket);
            socket.getOutputStream().write('{'); // Partial acceptance cannot keep connection alive.
            terminated(bridge);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test void launcherExitIsDetectedDuringIdleGameplay() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {})) {
            try (var socket = server.accept()) { read(socket); accepted(socket); read(socket); }
            terminated(bridge);
        }
    }

    @Test void shutdownWhileWorldActiveClosesWithoutFakeMainMenu() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket); accepted(socket); read(socket);
            bridge.publish(ActivitySnapshot.project(true, true, "Fixture", null, null));
            assertEquals("SINGLEPLAYER", read(socket).get("state").getAsString());
            bridge.close(); bridge.close(); terminated(bridge);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test void shutdownDuringAuthenticationDoesNotWaitForDeadline() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket);
            bridge.close();
            bridge.worker().join(1000);
            assertFalse(bridge.worker().isAlive());
        }
    }

    @Test void queueOverflowTerminatesInsteadOfDroppingTransitionsOrBlocking() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> {});
             var socket = server.accept()) {
            read(socket); // Hold handshake, ensuring FIFO cannot drain.
            for (int i = 0; i <= LauncherActivityBridge.QUEUE_CAPACITY; i++) {
                bridge.publish(ActivitySnapshot.project(true, true, "Fixture " + i, null, null));
            }
            terminated(bridge);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test void writeFailureAndDiagnosticsNeverExposeBootstrapOrIdentity() throws Exception {
        var messages = new CopyOnWriteArrayList<String>();
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), messages::add)) {
            try (var socket = server.accept()) {
                read(socket); accepted(socket); read(socket);
                socket.setSoLinger(true, 0); // RST: subsequent writes/reads must fail nonfatally.
            }
            bridge.publish(ActivitySnapshot.project(true, true, "Private Fixture", null, null));
            terminated(bridge);
            assertTrue(messages.stream().noneMatch(s -> s.contains("Private") || s.contains("abab") || s.contains("127.0.0.1")));
        }
    }

    @Test void unexpectedWorkerExceptionIsIsolatedAndTerminates() throws Exception {
        try (var bridge = new LauncherActivityBridge(null, ignored -> {})) { terminated(bridge); }
    }

    @Test void nonreadingPeerCannotBlockWritesBeyondTotalDeadline() throws Exception {
        try (var server = listener(); var channel = SocketChannel.open(); var selector = Selector.open()) {
            channel.socket().setSendBufferSize(4096);
            channel.connect(server.getLocalSocketAddress());
            try (var peer = server.accept()) {
                peer.setReceiveBufferSize(4096);
                channel.configureBlocking(false);
                var key = channel.register(selector, SelectionKey.OP_WRITE);
                // Saturate the kernel buffer to exercise the production write helper's
                // timeout. Normal messages are separately bounded to 4096 bytes.
                var error = assertThrows(IOException.class,
                        () -> BridgeIo.write(channel, key, new byte[16 * 1024 * 1024], () -> false));
                assertEquals("Activity bridge I/O timed out", error.getMessage());
            }
        }
    }

    @Test void brokenDiagnosticConsumerCannotBreakBridge() throws Exception {
        try (var server = listener(); var bridge = new LauncherActivityBridge(
                BridgeBootstrap.fromEnvironment(ActivityProtocolTest.environment(server.getLocalPort())), ignored -> { throw new IllegalStateException(); });
             var socket = server.accept()) {
            read(socket); accepted(socket);
            assertEquals("MAIN_MENU", read(socket).get("state").getAsString());
            bridge.close(); terminated(bridge);
        }
    }
}
