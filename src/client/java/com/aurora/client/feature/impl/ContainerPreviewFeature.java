package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;

/**
 * No-op marker feature for the rich container tooltip preview. Actual
 * detection lives in {@code ItemTooltipImageMixin} and rendering in
 * {@code AuroraContainerTooltipComponent}; this exists so the toggle
 * surfaces in the Aurora UI alongside other modules.
 */
public class ContainerPreviewFeature implements Feature {
    public static final String ID = "container_preview";

    @Override
    public String id() { return ID; }
}
