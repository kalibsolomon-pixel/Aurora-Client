package parityprobe;

import java.util.concurrent.atomic.AtomicLong;

/** Work counters filled by the probe's mixins. */
public final class Counters {
    private Counters() {}

    public static final AtomicLong LISTENER_UPDATES = new AtomicLong();
    public static final AtomicLong TEXT_EFFECTS = new AtomicLong();
    public static final AtomicLong ATLAS_DRAWS = new AtomicLong();
    /** Render-thread time inside GUI item atlas slot draws (CPU, including the driver calls they make). */
    public static final AtomicLong ATLAS_NANOS = new AtomicLong();
    /** Quads written or left out inside GUI item atlas drawings, and those GUI Flat Faces left out. */
    public static final AtomicLong ATLAS_QUADS = new AtomicLong();
    public static final AtomicLong ATLAS_QUADS_SKIPPED = new AtomicLong();
    /**
     * GUI Flat Faces drawing mode inside atlas drawings with the gate on ("flat-faces-mode"):
     * 0 as shipped, 1 only the quads it leaves out, 2 no item quads at all.
     */
    public static volatile int FLAT_FACES_MODE;
}
