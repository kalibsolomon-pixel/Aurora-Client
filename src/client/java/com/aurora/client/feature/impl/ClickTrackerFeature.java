package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;
import com.aurora.client.hud.CpsTracker;
import net.minecraft.client.Minecraft;

/**
 * Holds the global CPS trackers. Clicks are recorded directly from the
 * {@code MouseHandler.onMouseButton} GLFW callback via {@link com.aurora.client.mixin.MouseClickTrackerMixin},
 * not polled from key state. That avoids two bugs:
 * <ul>
 *   <li>{@code KeyMapping#isPressed()} consumes presses, racing vanilla's own callers.</li>
 *   <li>Polling once per tick (50ms) caps CPS at 20.</li>
 * </ul>
 */
public class ClickTrackerFeature implements Feature {
    public static final String ID = "click_tracker";

    public static final CpsTracker LEFT  = new CpsTracker();
    public static final CpsTracker RIGHT = new CpsTracker();

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        // No-op; clicks are recorded by MouseClickTrackerMixin.
    }
}