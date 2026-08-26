package com.aurora.client.screen;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.FeatureSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FeatureMetadata {
    public final String id;
    public final String displayName;
    public final String description;
    public final BooleanSupplier getEnabled;
    public final Consumer<Boolean> setEnabled;
    public final List<FeatureSetting> settings;

    /**
     * Optional explicit list of {@link AuroraConfig} field name prefixes
     * to reset when the user picks "Reset" on this feature. If empty, the
     * heuristic in {@link #reset()} falls back to a single camelCase
     * prefix derived from {@link #id}.
     *
     * <p>Use this for combined features whose underlying fields don't
     * share an id-derived prefix (e.g. {@code item_scaling} which owns
     * both {@code offHandScale*} and {@code mainHandScale*}).
     */
    public final List<String> resetPrefixes;

    public FeatureMetadata(String id, String displayName, String description,
                           BooleanSupplier getEnabled, Consumer<Boolean> setEnabled) {
        this(id, displayName, description, getEnabled, setEnabled, new ArrayList<>(), Collections.emptyList());
    }

    public FeatureMetadata(String id, String displayName, String description,
                           BooleanSupplier getEnabled, Consumer<Boolean> setEnabled,
                           List<FeatureSetting> settings) {
        this(id, displayName, description, getEnabled, setEnabled, settings, Collections.emptyList());
    }

    public FeatureMetadata(String id, String displayName, String description,
                           BooleanSupplier getEnabled, Consumer<Boolean> setEnabled,
                           List<FeatureSetting> settings,
                           List<String> resetPrefixes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.getEnabled = getEnabled;
        this.setEnabled = setEnabled;
        this.settings = settings;
        this.resetPrefixes = resetPrefixes;
    }

    public boolean isEnabled() { return getEnabled.getAsBoolean(); }
    public void setEnabled(boolean v) { setEnabled.accept(v); }
    public boolean hasDetail() { return !settings.isEmpty(); }

    /**
     * Reset this feature's config fields to their factory defaults.
     * Uses {@link #resetPrefixes} if specified, otherwise falls back to
     * one prefix derived from {@link #id} via snake_case → camelCase.
     */
    public void reset() {
        if (resetPrefixes != null && !resetPrefixes.isEmpty()) {
            AuroraConfig.resetByPrefixes(resetPrefixes);
        } else {
            AuroraConfig.resetByPrefix(toCamelCase(id));
        }
    }

    private static String toCamelCase(String snake) {
        if (snake == null || snake.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upperNext = false;
        for (int i = 0; i < snake.length(); i++) {
            char c = snake.charAt(i);
            if (c == '_') { upperNext = true; continue; }
            sb.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return sb.toString();
    }
}