package com.aurora.client.mixin;

import com.aurora.client.util.AnimationCurves;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Curves only the SWING component of the held-item renderer. The bob component
 * is left untouched so it stays in sync with view bob (curving both produces
 * phase mismatch and visible jitter when view bob is on).
 *
 * <p>Targets the {@code swingArm(float, PoseStack, int, HumanoidArm)} call
 * inside {@code renderArmWithItem}; the first arg is swing progress (0..1).
 * View bob applies via a separate path already curved by
 * {@code GameRendererBobMixin}.
 *
 * <p>1.21.11: method renamed from {@code applySwingOffset} to {@code swingArm}
 * and {@code Arm} replaced by {@code HumanoidArm}; swing progress moved from
 * index 2 to index 0.
 *
 * <p><b>1.8 Swing Style</b> is layered on in
 * {@code HeldItemRendererTweaksMixin#aurora$applyTweaks}, which fires just
 * before vanilla's {@code renderItem} call — after the swing transform has
 * been applied to the matrix stack. That injection is the reliable place to
 * add the legacy arc because it targets a well-known public method with a
 * single matching {@code INVOKE} and {@code require = 1}. (The previous
 * implementation {@code @Inject}ed the private {@code swingArm} method with
 * {@code require = 0}, so a signature drift would make it silently no-op.)
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyArg(
            method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"),
            index = 0,
            require = 1
    )
    private float aurora$curveSwingArg(float t) {
        return AnimationCurves.applySwing(t);
    }
}