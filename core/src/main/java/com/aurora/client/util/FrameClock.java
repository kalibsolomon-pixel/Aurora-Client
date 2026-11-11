package com.aurora.client.util;

import com.aurora.client.port.ClockPort;
import java.util.Objects;

/** Clock used by the pacing policies. The adapter installs the native deadline epoch at startup. */
public final class FrameClock {
    private static volatile ClockPort clock = () -> System.nanoTime() * 1.0e-9;
    private FrameClock() {}
    public static void install(ClockPort nativeClock) { clock = Objects.requireNonNull(nativeClock); }
    public static double seconds() { return clock.seconds(); }
}
