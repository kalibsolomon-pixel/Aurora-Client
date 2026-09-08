package com.aurora.client.hitreg.settings;

import com.aurora.client.config.AuroraConfig;

import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Overlay colors of the Better Hitreg core (from BetterHitreg by Jass,
 * integrated with permission). Upstream stored each as two Properties
 * keys — {@code <name>_color} (bare hex, no {@code #}) and
 * {@code <name>_opacity} (0..255); here each is one ARGB
 * {@link AuroraConfig} field. The five UI-chrome colors of the retired
 * hand-drawn menu ({@code background/border/text/hovered/highlighted}) are
 * gone with it and are not represented.
 *
 * <p>{@link #colorKey()} / {@link #opacityKey()} reproduce the original
 * property keys for the migrator.
 */
public enum Color {
    CROSS_FAR("FFFFFF", 255, c -> c.hitregColorCrossFar, (c, v) -> c.hitregColorCrossFar = v),
    CROSS_NEAR("FF0000", 255, c -> c.hitregColorCrossNear, (c, v) -> c.hitregColorCrossNear = v),
    CROSS_FAR_WITH_HITBOX("0000FF", 255, c -> c.hitregColorCrossFarWithHitbox, (c, v) -> c.hitregColorCrossFarWithHitbox = v),
    CROSS_NEAR_WITH_HITBOX("0000FF", 255, c -> c.hitregColorCrossNearWithHitbox, (c, v) -> c.hitregColorCrossNearWithHitbox = v),
    HITBOX_FAR("FFFFFF", 255, c -> c.hitregColorHitboxFar, (c, v) -> c.hitregColorHitboxFar = v),
    HITBOX_NEAR("FF0000", 255, c -> c.hitregColorHitboxNear, (c, v) -> c.hitregColorHitboxNear = v),
    SERVER_HITBOX("7F00FF", 125, c -> c.hitregColorServerHitbox, (c, v) -> c.hitregColorServerHitbox = v),
    YOUR_REACH_FAR("FFFFFF", 255, c -> c.hitregColorYourReachFar, (c, v) -> c.hitregColorYourReachFar = v),
    YOUR_REACH_NEAR("FF0000", 255, c -> c.hitregColorYourReachNear, (c, v) -> c.hitregColorYourReachNear = v),
    THEIR_REACH_FAR("FFFFFF", 255, c -> c.hitregColorTheirReachFar, (c, v) -> c.hitregColorTheirReachFar = v),
    THEIR_REACH_NEAR("FF0000", 255, c -> c.hitregColorTheirReachNear, (c, v) -> c.hitregColorTheirReachNear = v),
    YOUR_JUMP_FAR("007FFF", 255, c -> c.hitregColorYourJumpFar, (c, v) -> c.hitregColorYourJumpFar = v),
    YOUR_JUMP_NEAR("007FFF", 255, c -> c.hitregColorYourJumpNear, (c, v) -> c.hitregColorYourJumpNear = v),
    THEIR_JUMP_FAR("007FFF", 255, c -> c.hitregColorTheirJumpFar, (c, v) -> c.hitregColorTheirJumpFar = v),
    THEIR_JUMP_NEAR("007FFF", 255, c -> c.hitregColorTheirJumpNear, (c, v) -> c.hitregColorTheirJumpNear = v),
    JUMP_RESET("FFFF00", 255, c -> c.hitregColorJumpReset, (c, v) -> c.hitregColorJumpReset = v),
    PERFECT_HIT("00FF00", 255, c -> c.hitregColorPerfectHit, (c, v) -> c.hitregColorPerfectHit = v),
    GRID("FFFFFF", 255, c -> c.hitregColorGrid, (c, v) -> c.hitregColorGrid = v),
    FLOOR("000000", 255, c -> c.hitregColorFloor, (c, v) -> c.hitregColorFloor = v);

    private final String hex;
    private final int opacity;
    private final ToIntFunction<AuroraConfig> getter;
    private final ObjIntConsumer<AuroraConfig> setter;

    Color(String hex, int opacity, ToIntFunction<AuroraConfig> getter, ObjIntConsumer<AuroraConfig> setter) {
        this.hex = hex;
        this.opacity = opacity;
        this.getter = getter;
        this.setter = setter;
    }

    /** Upstream default RGB as bare hex. */
    public String hex() {
        return hex;
    }

    /** Upstream default opacity 0..255. */
    public int opacity() {
        return opacity;
    }

    public String colorKey() {
        return name().toLowerCase() + "_color";
    }

    public String opacityKey() {
        return name().toLowerCase() + "_opacity";
    }

    /** Upstream default as ARGB. */
    public int defaultArgb() {
        return argb(hex, opacity);
    }

    /** Current ARGB value from the config. */
    public int argb() {
        return getter.applyAsInt(Settings.cfg());
    }

    public void set(int argb) {
        setter.accept(Settings.cfg(), argb);
    }

    /** Combine bare hex RGB + 0..255 opacity into ARGB; {@code null}/bad hex falls back to the default RGB. */
    public int argb(String bareHex, int alpha) {
        int rgb;
        try {
            String h = bareHex == null ? hex : bareHex.trim().replace("#", "");
            rgb = Integer.parseInt(h, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            rgb = Integer.parseInt(hex, 16) & 0xFFFFFF;
        }
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | rgb;
    }
}
