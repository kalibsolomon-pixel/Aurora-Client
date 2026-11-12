package parityprobe;

import java.util.concurrent.atomic.AtomicLong;

/** Work counters filled by the probe's mixins. */
public final class Counters {
    private Counters() {}

    public static final AtomicLong LISTENER_UPDATES = new AtomicLong();
    public static final AtomicLong TEXT_EFFECTS = new AtomicLong();
    public static final AtomicLong ATLAS_DRAWS = new AtomicLong();
}
