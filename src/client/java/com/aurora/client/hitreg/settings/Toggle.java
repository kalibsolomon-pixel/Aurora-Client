package com.aurora.client.hitreg.settings;

import com.aurora.client.config.AuroraConfig;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Boolean switches of the Better Hitreg core (from BetterHitreg by Jass,
 * integrated with permission). The enum constants, their original
 * property keys and defaults are unchanged; each now maps onto one
 * {@link AuroraConfig} field instead of a Properties entry.
 *
 * <p>{@link #key()} is the exact string the original hitreg.properties
 * used — note the deliberately inconsistent casing ({@code safeRegsOnly}
 * vs {@code RenderServerHitbox}); the migrator matches on these strings
 * verbatim. {@code TRACK_FIGHTS} replaces the old {@code alertFights}
 * under a key that never existed in the file, so it is never migrated
 * (see {@link AuroraConfig#hitregTrackFights}).
 *
 * <p>{@link #toggled()} is what the timing core and mixins read: the stored
 * value ANDed with the feature card's master enable. {@link #get()} is the
 * raw stored value for the settings UI.
 */
public enum Toggle {
    TOGGLE("toggle", "custom hitreg", true,
            c -> c.hitregCustomHitreg, (c, v) -> c.hitregCustomHitreg = v),
    SAFE_REGS_ONLY("safeRegsOnly", "safe regs only", true,
            c -> c.hitregSafeRegsOnly, (c, v) -> c.hitregSafeRegsOnly = v),
    IGNORE_SHIELD_HOLDERS("ignoreShieldHolders", "ignore shield holders", false,
            c -> c.hitregIgnoreShieldHolders, (c, v) -> c.hitregIgnoreShieldHolders = v),
    ALERT_DELAYS("alertDelays", "alert delays", false,
            c -> c.hitregAlertDelays, (c, v) -> c.hitregAlertDelays = v),
    ALERT_GHOSTS("alertGhosts", "alert ghosts", false,
            c -> c.hitregAlertGhosts, (c, v) -> c.hitregAlertGhosts = v),
    ALERT_INCONSISTENCIES("alertInconsistencies", "alert inconsistencies", false,
            c -> c.hitregAlertInconsistencies, (c, v) -> c.hitregAlertInconsistencies = v),
    TRACK_FIGHTS("trackFights", "track fight statistics", true,
            c -> c.hitregTrackFights, (c, v) -> c.hitregTrackFights = v),
    LEGACY_SOUNDS("legacySounds", "1.8 sounds", false,
            c -> c.hitregLegacySounds, (c, v) -> c.hitregLegacySounds = v),
    HIDE_ANIMATIONS("hideAnimations", "hide animations", false,
            c -> c.hitregHideAnimations, (c, v) -> c.hitregHideAnimations = v),
    HIDE_ARMOR("hideArmor", "hide armor", false,
            c -> c.hitregHideArmor, (c, v) -> c.hitregHideArmor = v),
    HIDE_ALL_PARTICLES("hideAllParticles", "hide crit particles", false,
            c -> c.hitregHideAllParticles, (c, v) -> c.hitregHideAllParticles = v),
    HIDE_OTHER_PARTICLES("hideOtherParticles", "only crit/sweep particles", false,
            c -> c.hitregHideOtherParticles, (c, v) -> c.hitregHideOtherParticles = v),
    PARTICLES_EVERY_HIT("particlesEveryHit", "particles on every hit", false,
            c -> c.hitregParticlesEveryHit, (c, v) -> c.hitregParticlesEveryHit = v),
    SILENCE_OTHER_FIGHTS("silenceOtherFights", "silence other fights", false,
            c -> c.hitregSilenceOtherFights, (c, v) -> c.hitregSilenceOtherFights = v),
    SILENCE_SELF("silenceSelf", "silence your hits", false,
            c -> c.hitregSilenceSelf, (c, v) -> c.hitregSilenceSelf = v),
    SILENCE_THEM("silenceThem", "silence their hits", false,
            c -> c.hitregSilenceThem, (c, v) -> c.hitregSilenceThem = v),
    SILENCE_NON_HITS("silenceNonHits", "silence non-hits", false,
            c -> c.hitregSilenceNonHits, (c, v) -> c.hitregSilenceNonHits = v),
    HIDE_OTHER_FIGHTS("hideOtherFights", "hide other fights", false,
            c -> c.hitregHideOtherFights, (c, v) -> c.hitregHideOtherFights = v),
    RENDER_HITBOX("renderHitbox", "render target hitbox", false,
            c -> c.hitregRenderHitbox, (c, v) -> c.hitregRenderHitbox = v),
    RENDER_CROSS("renderCross", "render target cross", false,
            c -> c.hitregRenderCross, (c, v) -> c.hitregRenderCross = v),
    RENDER_SERVER_HITBOX("RenderServerHitbox", "render server hitbox", false,
            c -> c.hitregRenderServerHitbox, (c, v) -> c.hitregRenderServerHitbox = v),
    RENDER_YOUR_REACH("RenderYourReach", "render your reach", false,
            c -> c.hitregRenderYourReach, (c, v) -> c.hitregRenderYourReach = v),
    RENDER_THEIR_REACH("RenderTheirReach", "render their reach", false,
            c -> c.hitregRenderTheirReach, (c, v) -> c.hitregRenderTheirReach = v),
    RENDER_YOUR_JUMP("RenderYourJump", "render your jump range", false,
            c -> c.hitregRenderYourJump, (c, v) -> c.hitregRenderYourJump = v),
    RENDER_THEIR_JUMP("RenderTheirJump", "render their jump range", false,
            c -> c.hitregRenderTheirJump, (c, v) -> c.hitregRenderTheirJump = v),
    PERFECT_HIT_COLOR("PerfectHitColor", "color first tick hits", false,
            c -> c.hitregPerfectHitColor, (c, v) -> c.hitregPerfectHitColor = v),
    JUMP_RESET_COLOR("JumpResetColor", "color jump resets", false,
            c -> c.hitregJumpResetColor, (c, v) -> c.hitregJumpResetColor = v),
    VOID_WORLD("VoidWorld", "unrender world", false,
            c -> c.hitregVoidWorld, (c, v) -> c.hitregVoidWorld = v),
    SOLID_FLOOR("SolidFloor", "render solid floor", false,
            c -> c.hitregSolidFloor, (c, v) -> c.hitregSolidFloor = v);

    private final String key;
    private final String label;
    private final boolean defaultValue;
    private final Predicate<AuroraConfig> getter;
    private final BiConsumer<AuroraConfig, Boolean> setter;

    Toggle(String key, String label, boolean defaultValue,
           Predicate<AuroraConfig> getter, BiConsumer<AuroraConfig, Boolean> setter) {
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue;
        this.getter = getter;
        this.setter = setter;
    }

    /** Exact hitreg.properties key (casing preserved). */
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    /** Effective value read by the timing core and mixins: stored value AND master enable. */
    public boolean toggled() {
        return Settings.masterEnabled() && getter.test(Settings.cfg());
    }

    /** Raw stored value (what the settings UI displays and edits). */
    public boolean get() {
        return getter.test(Settings.cfg());
    }

    public void set(boolean value) {
        setter.accept(Settings.cfg(), value);
    }

    /** Flip the stored value. No chat announcement — the settings UI is the feedback. */
    public boolean toggle() {
        boolean v = !get();
        set(v);
        return v;
    }
}
