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

    static void register(OverlayReloadListener listener) {
        listeners.add(listener);
    }

    static void callEvent() {
        for (OverlayReloadListener listener : listeners) {
            listener.aurora$onOverlayReload();
        }
    }
}