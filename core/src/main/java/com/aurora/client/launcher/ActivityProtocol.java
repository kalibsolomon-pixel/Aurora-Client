package com.aurora.client.launcher;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Negotiated v1 display or v2 identity snapshots; bounded UTF-8 JSON lines. */
final class ActivityProtocol {
    static final int HANDSHAKE_BYTES = 1024;
    static final int ACK_BYTES = 128;
    static final int ACTIVITY_BYTES = 4096;
    static final String ACCEPTED = "{\"type\":\"accepted\",\"schemaVersion\":1}\n";
    static final String ACCEPTED_V2 = "{\"type\":\"accepted\",\"schemaVersion\":2}\n";

    private ActivityProtocol() {}

    static byte[] hello(BridgeBootstrap bootstrap) throws IOException {
        JsonObject message = envelope("hello", bootstrap.sessionId, bootstrap.protocol);
        message.addProperty("capability", bootstrap.capability);
        return encode(message, HANDSHAKE_BYTES);
    }

    static byte[] activity(String sessionId, long sequence, ActivitySnapshot snapshot) throws IOException {
        return activity(sessionId, sequence, snapshot, 1);
    }

    static byte[] activity(String sessionId, long sequence, ActivitySnapshot snapshot, int protocol) throws IOException {
        if (sequence < 1) throw new IOException("Invalid activity sequence");
        JsonObject message = envelope("activity", sessionId, protocol);
        message.addProperty("sequence", sequence);
        message.addProperty("state", snapshot.state().name());
        if (snapshot.worldDisplayName() != null) message.addProperty("worldDisplayName", snapshot.worldDisplayName());
        if (snapshot.serverDisplayName() != null) message.addProperty("serverDisplayName", snapshot.serverDisplayName());
        if (snapshot.serverAddress() != null) message.addProperty("serverAddress", snapshot.serverAddress());
        if (protocol == 2) {
            if (snapshot.worldSaveId() != null) message.addProperty("worldSaveId", snapshot.worldSaveId());
            if (snapshot.serverTarget() != null) message.addProperty("serverTarget", snapshot.serverTarget());
        }
        return encode(message, ACTIVITY_BYTES);
    }

    private static JsonObject envelope(String type, String sessionId, int protocol) {
        if (protocol != 1 && protocol != 2) throw new IllegalArgumentException("Invalid activity protocol");
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        message.addProperty("schemaVersion", protocol);
        message.addProperty("sessionId", sessionId);
        return message;
    }

    private static byte[] encode(JsonObject message, int limit) throws IOException {
        byte[] bytes = (message.toString() + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit) throw new IOException("Activity bridge frame exceeds limit");
        return bytes;
    }
}
