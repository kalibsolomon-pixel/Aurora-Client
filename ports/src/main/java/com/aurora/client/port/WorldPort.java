package com.aurora.client.port;

/** Current world identity, queried on the client thread without retaining game objects.
 * A null name/address means unavailable. Integrated saves take priority over LAN server addresses. */
public interface WorldPort {
    boolean loaded();
    String singleplayerName();
    String serverAddress();
}
