package com.aurora.client.screen.setting;

import java.util.List;

/**
 * Smart search + filterable list container for the Particles module.
 *
 * <p>Mirrors the UX of {@link ItemScaleSetting}: a search field at the top
 * that filters the list of {@link ParticleRowSetting} rows below in real
 * time. Unlike the item-scale variant, particles are a fixed set (every
 * vanilla particle type) so the search bar <b>filters</b> the existing
 * list rather than adding new entries.
 *
 * <p>Matching is case-insensitive against both the registry id
 * (e.g. {@code "minecraft:flame"}) and the humanized display name
 * (e.g. {@code "Flame"}). An empty query shows all particles.
 *
 * <p>The row list is built once at construction from the particle registry
 * (passed in by {@code FeatureRegistry}) and reused for the lifetime of
 * this setting — filtering only changes which rows are rendered, not the
 * backing list. All of the search/filter machinery lives in the shared
 * {@link SearchListSetting} base (extracted 2026-09-12 when the Alerts
 * per-effect list needed the identical container); this subclass is only
 * the particle-specific match rule.
 */
public class ParticleConfigSetting extends SearchListSetting<ParticleRowSetting> {

    public ParticleConfigSetting(List<ParticleRowSetting> rows) {
        super("Particle Search", rows, "Search particles... (e.g. flame)");
    }

    /** True if the humanized label or registry id contains the query token. */
    @Override
    protected boolean matches(ParticleRowSetting row, String clean) {
        if (clean.isEmpty()) return true;
        if (row.label.toLowerCase().contains(clean)) return true;
        // Also match the raw registry id (e.g. "minecraft:flame") so a
        // query like "dripping_water" still hits even though the label
        // is humanized to "Dripping Water".
        return row.particleId().toLowerCase().contains(clean);
    }
}
