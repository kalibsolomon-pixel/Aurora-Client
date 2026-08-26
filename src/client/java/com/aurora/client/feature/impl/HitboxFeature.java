package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.Minecraft;

/**
 * Hitbox feature. Drawing happens in {@code HitboxRenderer}; vanilla's own
 * AABB drawing is suppressed by {@code EntityRenderDispatcherMixin} when our
 * hitboxes are enabled, so Aurora's visualization completely overwrites
 * vanilla's. F3+B is not consulted — visibility is driven solely by the
 * Aurora feature toggle, with an optional rebindable keybind
 * ({@code cfg.hitboxToggleKey}) that flips that toggle on/off.
 */
public class HitboxFeature implements Feature {
    public static final String ID = "hitbox";

    private final AuroraKey.EdgeDetector toggleEdge = new AuroraKey.EdgeDetector();

    @Override public String id() { return ID; }

    @Override
    public void onTick(Minecraft client) {
        AuroraConfig cfg = AuroraConfig.get();
        // Optional master keybind — toggles both self and target hitbox
        // halves together so the feature-tile state matches Aurora UI.
        if (toggleEdge.justPressed(cfg.hitboxToggleKey)) {
            boolean on = !(cfg.hitboxEnabled || cfg.hitboxTargetEnabled);
            cfg.hitboxEnabled = on;
            cfg.hitboxTargetEnabled = on;
        }
    }
}