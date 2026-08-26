package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;

/**
 * Marker feature for the item-physics tweak. The actual work lives in
 * {@code ItemEntityRendererExtractMixin} (capturing per-frame ground +
 * motion info) and {@code ItemEntityRendererSubmitMixin} (modifying
 * the vanilla translate / spin calls). This class exists only so the
 * toggle surfaces in the Aurora UI alongside other modules.
 */
public class ItemPhysicsFeature implements Feature {
    public static final String ID = "item_physics";

    @Override
    public String id() { return ID; }
}
