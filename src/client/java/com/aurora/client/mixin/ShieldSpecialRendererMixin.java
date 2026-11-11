package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ShieldStatusFeature;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
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
 * <p>Injected at TAIL, where the pose stack still carries the shield
 * transform: 26.1's {@code submit} no longer pushes or pops it (1.21.11
 * injected just before its {@code popPose}).
 */
@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {

    @Shadow @Final private SpriteGetter sprites;
    @Shadow @Final private ShieldModel model;

    /** Flat white sprite stitched into the shield atlas by
     *  {@code assets/aurora/atlases/shield_patterns.json}. */
    private static final SpriteId AURORA$WHITE =
            new SpriteId(Sheets.SHIELD_BASE_NO_PATTERN.atlasLocation(),
                    Identifier.fromNamespaceAndPath("aurora", "shield/white"));

    /** One-shot diagnostics for the sprite-resolution fallback. */
    private static boolean aurora$warnedMissingSprite;
    private static boolean aurora$loggedResolved;

    // 26.1's submit no longer pushes or pops the pose stack, so TAIL still carries the
    // shield transform. Exact descriptor: the Object-typed bridge must not get a second overlay.
    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At("TAIL"))
    private void aurora$statusOverlay(DataComponentMap argument,
                                      PoseStack poseStack, SubmitNodeCollector collector,
                                      int packedLight, int packedOverlay, boolean flat, int seed,
                                      CallbackInfo ci) {
        int color = ShieldStatusFeature.resolveOverlayArgb();
        if ((color >>> 24) == 0) return; // off or zero opacity — vanilla shield
        // Banner-patterned shields keep their patterns visible (the overlay
        // would cover them) — same scope ruling as the old tint path.
        if (!argument.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)
                .layers().isEmpty()) return;

        TextureAtlasSprite white = sprites.get(AURORA$WHITE);
        // AtlasManager.get falls back to the atlas's MISSING sprite for any
        // material that did not stitch — a missing white sprite tinted the
        // shield black (the 2026-10-30 evening report). Detect it by name
        // (a real resolution always echoes the requested texture id) and
        // fail safe to the vanilla shield instead.
        if (!AURORA$WHITE.texture().equals(white.contents().name())) {
            if (!aurora$warnedMissingSprite) {
                aurora$warnedMissingSprite = true;
                com.aurora.client.AuroraClient.LOGGER.warn(
                        "[ShieldStatus] white overlay sprite {} did not stitch into {} (resolved {}); overlay disabled",
                        AURORA$WHITE.texture(), AURORA$WHITE.atlasLocation(), white.contents().name());
            }
            return;
        }
        if (!aurora$loggedResolved) {
            aurora$loggedResolved = true;
            com.aurora.client.AuroraClient.LOGGER.info(
                    "[ShieldStatus] overlay sprite resolved: {}", white.contents().name());
        }
        RenderType type = RenderTypes.entityTranslucent(AURORA$WHITE.atlasLocation());
        ModelPart handle = model.handle();
        ModelPart plate = model.plate();
        collector.submitModelPart(handle, poseStack, type, packedLight, packedOverlay,
                white, color, seed);
        collector.submitModelPart(plate, poseStack, type, packedLight, packedOverlay,
                white, color, seed);
    }
}
