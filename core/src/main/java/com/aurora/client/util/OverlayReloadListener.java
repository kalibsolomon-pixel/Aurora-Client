/*
 * Ported from harimasa/HitColor (MIT licensed).
 * https://github.com/harimasa/HitColor
 */
package com.aurora.client.util;

import java.util.ArrayList;
import java.util.List;

public interface OverlayReloadListener {
    List<OverlayReloadListener> listeners = new ArrayList<>();

    void aurora$onOverlayReload();

    /**
     * Per-frame hook for time-driven hit color modes (pulse, rainbow): the
     * tick-level reload runs at 20 Hz, so those modes would visibly step.
     * Callers pass the current wall-clock in milliseconds; implementations
     * must be cheap and must no-op when their mode is not time-driven.
     */
    default void aurora$onOverlayFrameRefresh(long nowMillis) {}

    static void register(OverlayReloadListener listener) {
        listeners.add(listener);
    }

    static void callEvent() {
        for (OverlayReloadListener listener : listeners) {
            listener.aurora$onOverlayReload();
        }
    }

    static void callFrameRefresh(long nowMillis) {
        for (OverlayReloadListener listener : listeners) {
            listener.aurora$onOverlayFrameRefresh(nowMillis);
        }
    }
}