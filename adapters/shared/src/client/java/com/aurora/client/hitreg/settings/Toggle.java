package com.aurora.client.hitreg.settings;

import com.aurora.client.config.AuroraConfig;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Boolean switches of the Better Hitreg core (from BetterHitreg by Jass,
 * integrated with permission). The kept constants, their original property
 * keys and defaults are unchanged; each maps onto one {@link AuroraConfig}
 * field instead of a Properties entry. Only the custom client-side hitreg
 * switches remain (2026-11-15); the tracking, audio, render and
 * practice-arena toggles were removed, and the timing core behaves as if
 * each were at its default (off).
 *
 * <p>{@link #key()} is the exact string the original hitreg.properties
 * used; the migrator matches on these strings verbatim.
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
            c -> c.hitregIgnoreShieldHolders, (c, v) -> c.hitregIgnoreShieldHolders = v);

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
