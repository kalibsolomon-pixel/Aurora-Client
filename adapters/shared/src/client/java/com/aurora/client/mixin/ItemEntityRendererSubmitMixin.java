package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraItemPhysicsSnapshots;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Implements the Item Physics feature by tweaking two PoseStack calls
 * inside {@code ItemEntityRenderer.submit}:
 *
 * <ol>
 *   <li>The {@code translate(0, bob+yOffset, 0)} call's Y argument:
 *       when the entity is on the ground we subtract the bob component
 *       so the item sits at its natural ground height instead of
 *       hovering. We reconstruct the bob from {@code ageInTicks} +
 *       {@code bobOffset} (the same formula vanilla uses just above the
 *       translate call), since {@code @ModifyArg} only gives us the
 *       composed Y value.</li>
 *   <li>The {@code mulPose(spinQuat)} call's quaternion argument:
 *     <ul>
 *       <li>Grounded: replace with an X-axis 90° rotation so the sprite
 *           lies face-up. Vanilla's Y spin is discarded.</li>
 *       <li>Airborne: post-multiply an X-axis tumble onto vanilla's Y
 *           spin, scaled by motion magnitude and the user's strength
 *           slider. Result is a yaw+pitch end-over-end roll.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Both targets are unique in {@code submit}, so we rely on the
 * INVOKE descriptor without an ordinal.
 *
 * <p>State carries no ground/motion data on its own — we read it from
 * {@link AuroraItemPhysicsSnapshots}, populated each frame by
 * {@link ItemEntityRendererExtractMixin}.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererSubmitMixin {

    /**
     * Additional downward push (in world blocks) applied at full
     * {@code groundT} on top of the bob removal. Cancels vanilla's
     * +0.0625 ground padding plus the model bounding box's vertical
     * lift so the rotated 2D sprite actually rests on the surface
     * instead of hovering. Scaled by {@code groundT} so the descent
     * is smooth.
     */
    @org.spongepowered.asm.mixin.Unique
    private static final float aurora$GROUND_PUSH_DOWN = 0.0925f;

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
               at = @At(value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
               index = 1,
               require = 0)
    private float aurora$modifyTranslateY(float origY,
                                          @Local(argsOnly = true) ItemEntityRenderState state) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.itemPhysicsEnabled || !cfg.itemPhysicsFlatOnGround) return origY;
        AuroraItemPhysicsSnapshots.Snapshot snap = AuroraItemPhysicsSnapshots.get(state);
        if (snap == null || snap.groundT <= 0f) return origY;
        // Reverse-engineer the bob component (vanilla:
        //   bob = sin((age/10 + bobOffset)) * 0.1f + 0.1f).
        // Both the bob and the additional ground-flush push are scaled
        // by groundT so the item descends smoothly during the contact
        // transition rather than snapping flat in one frame.
        double phase = state.ageInTicks / 10.0 + state.bobOffset;
        float bob = Mth.sin((float) phase) * 0.1f + 0.1f;
        float t = snap.groundT;
        return origY - (bob + aurora$GROUND_PUSH_DOWN) * t;
    }

    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
               at = @At(value = "INVOKE",
                        target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"),
               index = 0,
               require = 0)
    private Quaternionfc aurora$modifySpin(Quaternionfc origSpin,
                                           @Local(argsOnly = true) ItemEntityRenderState state) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.itemPhysicsEnabled) return origSpin;
        AuroraItemPhysicsSnapshots.Snapshot snap = AuroraItemPhysicsSnapshots.get(state);
        if (snap == null) return origSpin;

        // Items that have already come to rest never tumble again. A drop
        // that settled flat and then falls (block broken beneath it, shoved
        // off a ledge) must keep its flat pose instead of snapping back into
        // a spin — that snap is the lerping glitch. Only genuinely fresh
        // inventory drops (which have never been grounded) are allowed to
        // tumble through the airborne path below.
        if (snap.settled) {
            return cfg.itemPhysicsFlatOnGround ? Axis.XP.rotationDegrees(90f) : origSpin;
        }

        // Build the airborne pose first (vanilla Y spin + optional
        // motion-scaled X tumble), then slerp toward the flat pose by
        // groundT. This avoids the one-frame rotation snap that
        // happened when we returned origSpin in air and a hard 90°
        // quaternion on contact.
        Quaternionf air = new Quaternionf((Quaternionf) origSpin);
        double tumbleStrength = cfg.itemPhysicsTumbleStrength;
        if (tumbleStrength > 0.0) {
            // lengthSqr is capped so a stationary entity contributes 0
            // and an item at terminal fall (~0.0625 m²/tick²) contributes
            // ~1. Tumble fades out as we settle so the slerp into flat
            // doesn't fight an active rotation.
            float motion = (float) Math.min(1.0, snap.motionLenSq * 16.0);
            float airWeight = 1.0f - snap.groundT;
            if (motion > 0.0f && airWeight > 0.0f) {
                float tumbleAngle = (state.ageInTicks / 6.0f)
                        * (float) tumbleStrength * motion * airWeight;
                air.mul(Axis.XP.rotation(tumbleAngle));
            }
        }

        if (!cfg.itemPhysicsFlatOnGround || snap.groundT <= 0f) return air;
        if (snap.groundT >= 1f) return Axis.XP.rotationDegrees(90f);

        // Slerp from the (possibly tumbling) airborne quaternion to
        // the flat-face-up pose by the smoothed groundT.
        Quaternionf flat = Axis.XP.rotationDegrees(90f);
        return air.slerp(flat, snap.groundT);
    }
}
