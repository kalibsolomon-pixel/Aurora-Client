package com.aurora.client.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.FeatureMetadata;
import com.aurora.client.screen.FeatureRegistry;

/**
 * Grid-tile representation of an Aurora feature for the main settings screen.
 *
 * <p>{@link #isEnabled()} / {@link #setEnabled(boolean)} delegate to the
 * matching {@link FeatureMetadata} entry in {@link FeatureRegistry}, which
 * is the <b>single source of truth</b> for which config fields each feature
 * reads and what side-effects toggling triggers (e.g. theme color refresh,
 * water-transparency reapply). This eliminates the class of bug where the
 * grid tile state drifts out of sync with the detail screen or the actual
 * renderers.
 */
public class Module {
    public final String id;
    public final String name;
    public final String description;

    /** Lazily-resolved metadata handle; cached after first lookup. */
    private FeatureMetadata cachedMeta;

    public Module(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    /**
     * Resolve this module's {@link FeatureMetadata} from the registry.
     * Searches both the modules and settings lists, caching the result.
     * Returns {@code null} only if the id is absent from both registries
     * (which would indicate a registration bug).
     */
    private FeatureMetadata meta() {
        if (cachedMeta == null) {
            for (FeatureMetadata fm : FeatureRegistry.modules()) {
                if (fm.id.equals(this.id)) { cachedMeta = fm; return fm; }
            }
            for (FeatureMetadata fm : FeatureRegistry.settings()) {
                if (fm.id.equals(this.id)) { cachedMeta = fm; return fm; }
            }
        }
        return cachedMeta;
    }

    public boolean isEnabled() {
        FeatureMetadata fm = meta();
        return fm != null && fm.isEnabled();
    }

    public void setEnabled(boolean value) {
        FeatureMetadata fm = meta();
        if (fm != null) {
            fm.setEnabled(value);
            AuroraConfig.save();
        }
    }

    public void toggle() {
        setEnabled(!isEnabled());
    }
}
