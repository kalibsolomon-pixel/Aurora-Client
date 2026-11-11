package com.aurora.client.port;

/** Physical key state and menu suppression. Keys use persisted SDL scancode literals;
 * an adapter translates platform bindings. Calls occur on the client thread. */
public interface InputPort {
    boolean isDown(int scancode);
    boolean suppressed();
}
