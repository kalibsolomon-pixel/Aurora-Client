package com.aurora.client.health;

/**
 * Lets tests in other packages put the health layer into a known state.
 * Production code records failures only from the mixin plugin and error
 * handler, so those entry points stay package-private.
 */
public final class HealthTestAccess {
    private HealthTestAccess() {}

    /** Record that this mixin could not be applied. */
    public static void fail(String mixin) {
        MixinHealth.noteFailure(mixin, "some.Target", MixinHealth.Cause.APPLY_FAILED, "injection point not found");
    }

    /** Record that this mixin stood down for another mod (ADR 0009). */
    public static void standDown(String mixin, String reason) {
        MixinHealth.noteStoodDown(mixin, reason);
    }

    /**
     * Forget every recorded failure, then give {@code config} back any switch
     * that was being held off.
     */
    public static void reset(Object config) {
        MixinHealth.resetForTests();
        if (config != null) FeatureHealth.enforce(config);
        FeatureHealth.resetForTests();
    }
}
