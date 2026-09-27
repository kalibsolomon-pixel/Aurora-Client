package com.aurora.client.launcher;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.UUID;

/** Process-scoped bootstrap. Deliberately not a record: never print the capability. */
final class BridgeBootstrap {
    static final String ENDPOINT = "AURORA_ACTIVITY_ENDPOINT";
    static final String SESSION = "AURORA_ACTIVITY_SESSION_ID";
    static final String CAPABILITY = "AURORA_ACTIVITY_CAPABILITY";
    static final String VERSION = "AURORA_ACTIVITY_PROTOCOL";

    final InetSocketAddress endpoint;
    final String sessionId;
    final String capability;

    private BridgeBootstrap(int port, String sessionId, String capability) {
        try {
            // Literal bytes: no DNS, alternate host syntax or non-loopback fallback.
            endpoint = new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port);
        } catch (UnknownHostException impossible) {
            throw new IllegalStateException("Invalid loopback constant");
        }
        this.sessionId = sessionId;
        this.capability = capability;
    }

    static BridgeBootstrap fromEnvironment(Map<String, String> env) {
        String endpoint = env.get(ENDPOINT);
        String session = env.get(SESSION);
        String capability = env.get(CAPABILITY);
        String version = env.get(VERSION);
        if (endpoint == null && session == null && capability == null && version == null) return null;
        if (endpoint == null || endpoint.length() > 21 || !endpoint.matches("127\\.0\\.0\\.1:[1-9][0-9]{0,4}")
                || session == null || session.length() != 36
                || capability == null || !capability.matches("[0-9a-f]{64}") || !"1".equals(version)) {
            throw new IllegalArgumentException("Invalid activity bridge bootstrap");
        }
        if (!UUID.fromString(session).toString().equals(session)) {
            throw new IllegalArgumentException("Invalid activity bridge session");
        }
        int port = Integer.parseInt(endpoint.substring(10));
        if (port > 65535) throw new IllegalArgumentException("Invalid activity bridge port");
        return new BridgeBootstrap(port, session, capability);
    }

    @Override public String toString() { return "BridgeBootstrap[redacted]"; }
}
