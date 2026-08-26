package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;

/**
 * Reader-only feature exposing per-particle visibility and scale config
 * to the {@link com.aurora.client.mixin.ParticleEngineMixin}.
 *
 * <p>No tick logic — this feature exists purely so the registry / settings
 * UI has a {@link Feature} to attach to. All real work happens in the
 * mixin via the {@link #isVisible(String)} / {@link #getScale(String)}
 * static lookups.
 */
public class ParticleControlFeature implements Feature {
    public static final String ID = "particles";

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Particles"; }
    @Override public boolean enabledByDefault() { return true; }

    /** Returns true if a particle id should render. Absent key ⇒ visible. */
    public static boolean isVisible(String particleId) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.particleControlsEnabled) return true;
        Boolean v = cfg.particleVisibility.get(particleId);
        return v == null || v;
    }

    /** Returns the user-configured scale multiplier for a particle id. Absent key ⇒ 1.0. */
    public static float getScale(String particleId) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.particleControlsEnabled) return 1.0f;
        Float s = cfg.particleScale.get(particleId);
        return s == null ? 1.0f : s;
    }

    /**
     * Returns the user-configured ARGB color overlay for a particle id, or
     * {@code 0} if no tint is configured (absent key ⇒ no tint). The caller
     * ({@code ParticleEngineMixin}) interprets the value: {@code 0} means
     * "leave vanilla colors alone"; otherwise the RGB channels are blended
     * into the particle's {@code rCol/gCol/bCol} with the alpha channel as
     * tint strength (0–255 → 0.0–1.0).
     */
    public static int getColor(String particleId) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.particleControlsEnabled) return 0;
        Integer c = cfg.particleColor.get(particleId);
        return c == null ? 0 : c;
    }
}
