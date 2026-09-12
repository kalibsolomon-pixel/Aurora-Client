package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the tiny gaps/seams between adjacent cube faces on first-person
 * held items by scaling the held-item model matrix by a tiny factor (default
 * 1.001). This causes adjacent face vertices to overlap by a fraction of a
 * pixel, hiding the texture-bleed lines that appear at certain camera angles.
 *
 * <p>Visually invisible at 1.001 (sub-pixel inflation) but eliminates
 * mipmap-induced seams. At 1.005 you may notice items look very slightly
 * larger but seams are aggressively hidden.
 *
 * <p>Targets renderArmWithItem at HEAD: pushes a scale onto the matrix
 * stack so all subsequent positioning math operates on the scaled space.
 * The matching pop is at RETURN (every return, not just the last one) —
 * the method early-returns when the player is scoping, and a TAIL-only
 * pop would leak the pushed pose on every scoping frame.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemSeamFixMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 1)
    private void aurora$pushSeamScale(
            net.minecraft.client.player.AbstractClientPlayer player,
            float tickProgress, float pitch, net.minecraft.world.InteractionHand hand,
            float swingProgress, net.minecraft.world.item.ItemStack item,
            float equipProgress, PoseStack matrices,
            net.minecraft.client.renderer.SubmitNodeCollector vertexConsumers,
            int light,
            CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.fixHeldItemSeams) return;
        float s = (float) Math.max(1.0, Math.min(1.05, cfg.heldItemInflation));
        if (s == 1.0f) return;

        matrices.pushPose();
        matrices.scale(s, s, s);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 1)
    private void aurora$popSeamScale(
            net.minecraft.client.player.AbstractClientPlayer player,
            float tickProgress, float pitch, net.minecraft.world.InteractionHand hand,
            float swingProgress, net.minecraft.world.item.ItemStack item,
            float equipProgress, PoseStack matrices,
            net.minecraft.client.renderer.SubmitNodeCollector vertexConsumers,
            int light,
            CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.fixHeldItemSeams) return;
        float s = (float) Math.max(1.0, Math.min(1.05, cfg.heldItemInflation));
        if (s == 1.0f) return;

        matrices.popPose();
    }
}