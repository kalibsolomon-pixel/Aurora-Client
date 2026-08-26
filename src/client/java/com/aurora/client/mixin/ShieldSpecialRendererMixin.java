package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ShieldStatusFeature;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Replaces the hardcoded {@code -1} (vanilla "no tint") color int passed
 * to {@code SubmitNodeCollector.submitModelPart} inside
 * {@link ShieldSpecialRenderer#submit} with the user-configured tint
 * resolved by {@link ShieldStatusFeature#resolveTintArgb()}.
 *
 * <p>The color int is multiplied by the texture sample, so the shield
 * texture's detail stays visible — RGB modulates color, alpha modulates
 * overall opacity. When the feature is off, {@code resolveTintArgb()}
 * returns {@code 0xFFFFFFFF} (== -1) so behavior is bit-for-bit vanilla.
 *
 * <p><b>Scope:</b> only the no-pattern shield path (handle + plate
 * direct submits) is tinted. Banner-patterned shields submit through
 * {@code BannerRenderer.submitPatterns} which doesn't take a tint arg,
 * so those layers stay vanilla. Acceptable for v1 — most PvP shields
 * are blank.
 *
 * <p>Targets {@code submitModelPart} by name only (no descriptor) so the
 * annotation processor doesn't have to remap a long argument list. There
 * are two matching invokes (handle, plate); both are rewritten by the
 * single {@link ModifyArg} thanks to the default broadcast behavior.
 */
@Mixin(ShieldSpecialRenderer.class)
public abstract class ShieldSpecialRendererMixin {

    @ModifyArg(
            method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"),
            index = 8)
    private int aurora$tintShieldColor(int original) {
        int tint = ShieldStatusFeature.resolveTintArgb();
        return tint == ShieldStatusFeature.NO_TINT ? original : tint;
    }
}
