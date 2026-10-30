package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ShieldStatusFeature;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.MaterialSet;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.core.component.DataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Submits a FLAT TRANSLUCENT STATUS OVERLAY over the shield model — a second
 * pass of the same model parts through a pure-white atlas sprite and an
 * {@code entityTranslucent} render type, vertex-colored with the status ARGB
 * (owner ruling 2026-10-30: the picker's alpha is the source of truth —
 * max covers the shield texture with solid color, min submits nothing).
 *
 * <p>The previous approach multiplied a tint through the shield's cutout
 * render type, which can only ever darken/tint the texture — dark texels
 * always read through, so the overlay never reached full vibrancy or full
 * coverage. A translucent overlay pass blends real alpha over the textured
 * base instead.
 *
 * <p>Scope: only the no-pattern shield path (the same scope as the old
 * tint) — banner-patterned shields submit through
 * {@code BannerRenderer.submitPatterns} and stay vanilla.
 *
 * <p>Injected just before vanilla's {@code popPose} so the pose stack still
 * carries the shield transform (at TAIL the transform would already be
 * popped).
 */
@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {

    @Shadow @Final private MaterialSet materials;
    @Shadow @Final private ShieldModel model;

    /** Flat white sprite stitched into the shield atlas by
     *  {@code assets/aurora/atlases/shield_patterns.json}. */
    private static final Material AURORA$WHITE =
            new Material(ModelBakery.NO_PATTERN_SHIELD.atlasLocation(),
                    Identifier.fromNamespaceAndPath("aurora", "shield/white"));

    @Inject(method = "submit", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void aurora$statusOverlay(DataComponentMap argument, ItemDisplayContext displayContext,
                                      PoseStack poseStack, SubmitNodeCollector collector,
                                      int packedLight, int packedOverlay, boolean flat, int seed,
                                      CallbackInfo ci) {
        int color = ShieldStatusFeature.resolveOverlayArgb();
        if ((color >>> 24) == 0) return; // off or zero opacity — vanilla shield
        // Banner-patterned shields keep their patterns visible (the overlay
        // would cover them) — same scope ruling as the old tint path.
        if (!argument.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                .layers().isEmpty()) return;

        TextureAtlasSprite white = materials.get(AURORA$WHITE);
        if (white == null) return;
        RenderType type = RenderTypes.entityTranslucent(AURORA$WHITE.atlasLocation());
        ModelPart handle = model.handle();
        ModelPart plate = model.plate();
        collector.submitModelPart(handle, poseStack, type, packedLight, packedOverlay,
                white, false, false, color, null, seed);
        collector.submitModelPart(plate, poseStack, type, packedLight, packedOverlay,
                white, false, false, color, null, seed);
    }
}
