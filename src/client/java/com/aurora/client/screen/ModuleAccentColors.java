package com.aurora.client.screen;

import java.util.Map;

/**
 * Per-module accent color overrides. When a module id is present in
 * this map, the modules grid (AuroraScreen's tile renderer) uses the
 * mapped color (ARGB) for both the ON-state outline and the ON-state
 * icon tint, replacing the default {@code AuroraTheme.IOS_BLUE}.
 *
 * <p>This indirection exists so the per-module color palette can be
 * tweaked from one place during the design phase. Deleting an entry
 * (or this whole class) reverts a module to the default blue accent
 * without touching any rendering code.
 */
public final class ModuleAccentColors {

    /**
     * Per-module overrides. Empty by default — every enabled module
     * uses {@code AuroraTheme.MODULE_ACCENT_ON}. Add entries to give
     * specific modules a different accent.
     */
    private static final Map<String, Integer> ACCENTS = Map.of();

    private ModuleAccentColors() {}

    /** Returns the override ARGB color for {@code moduleId}, or {@code 0} if none. */
    public static int get(String moduleId) {
        if (moduleId == null) return 0;
        Integer c = ACCENTS.get(moduleId);
        return c == null ? 0 : c;
    }

    public static boolean has(String moduleId) {
        return moduleId != null && ACCENTS.containsKey(moduleId);
    }
}
