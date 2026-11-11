package com.aurora.client.port;

/** Monotonic seconds in the same epoch as the native frame deadline.
 * Install before frame pacing starts; never change the epoch during a session. */
@FunctionalInterface
public interface ClockPort {
    double seconds();
}
