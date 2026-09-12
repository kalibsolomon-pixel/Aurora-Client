package com.aurora.client.screen.setting;

import java.util.List;

/**
 * Search + filterable per-effect list for the Alerts feature's Effect
 * Expiry section — every registered potion effect as an
 * {@link EffectRowSetting} (sprite + name + on/off toggle), behind the
 * shared {@link SearchListSetting} search container (the Particles list's
 * mechanism, extracted rather than duplicated).
 *
 * <p>Toggles write the config's exclusion set: on (the default) means the
 * effect raises the expiry alert, off excludes it. The section's master
 * "Effect Expiry Alert" toggle still gates the whole feature — exclusions
 * only refine which effects fire while it is on.
 *
 * <p>Design language: the owning registry entry wraps this in a
 * {@code SectionHeaderSetting} ("Per-Effect Alerts"); rows carry no §4
 * subtitles (toggle state is visible at a glance) and no §5 preview (an
 * effect's name + icon is self-explanatory). Ordering is alphabetical by
 * display name, matching how the Particles list orders its rows.
 */
public class EffectExpiryListSetting extends SearchListSetting<EffectRowSetting> {

    public EffectExpiryListSetting(List<EffectRowSetting> rows) {
        super("Effect Search", rows, "Search effects... (e.g. strength)");
    }

    /** True if the localized label or registry id contains the query token. */
    @Override
    protected boolean matches(EffectRowSetting row, String clean) {
        if (clean.isEmpty()) return true;
        if (row.label.toLowerCase().contains(clean)) return true;
        // Also match the raw registry id (e.g. "minecraft:strength") so a
        // query like "fire_resistance" still hits even though the label
        // is the localized "Fire Resistance".
        return row.effectId().toLowerCase().contains(clean);
    }
}
