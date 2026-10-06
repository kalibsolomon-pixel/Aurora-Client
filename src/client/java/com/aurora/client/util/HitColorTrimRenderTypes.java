package com.aurora.client.util;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Overlay-aware decal trims retain vanilla's equal-depth test and armor view offset. */
public final class HitColorTrimRenderTypes {
    private HitColorTrimRenderTypes() {}

    public static RenderType decal() { return Holder.DECAL; }

    private static final class Holder {
        private static final RenderType DECAL = RenderType.create("aurora_hit_color_trim_decal",
                RenderSetup.builder(RenderPipelines.ENTITY_DECAL)
                        .withTexture("Sampler0", Sheets.ARMOR_TRIMS_SHEET)
                        .useLightmap().useOverlay()
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .affectsCrumbling()
                        .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                        .createRenderSetup());
    }
}
