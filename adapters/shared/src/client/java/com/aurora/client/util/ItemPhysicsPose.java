package com.aurora.client.util;

import com.aurora.client.config.AuroraConfig;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/**
 * Item Physics' pose for a dropped item, shared by every line's
 * {@code ItemEntityRendererSubmitMixin}: the line-specific part is only how
 * the mixin reaches vanilla's translate and spin calls (26.3 spins with
 * {@code rotate(Axis, float)}, earlier lines with {@code mulPose(Quaternionfc)}).
 *
 * <ol>
 *   <li>{@link #translateY}: the {@code translate(0, bob+yOffset, 0)} call's Y
 *       argument. On the ground the item is laid flat, so it is lowered from
 *       vanilla's bobbing height to rest on the surface: the flat pose turns
 *       the model's depth into its height, so its lowest point is the model
 *       box's {@code maxZ} below the origin.</li>
 *   <li>{@link #spin}: the spin rotation.
 *     <ul>
 *       <li>Grounded: an X-axis 90° rotation so the sprite lies face-up.
 *           Vanilla's Y spin is discarded.</li>
 *       <li>Airborne: an X-axis tumble post-multiplied onto vanilla's Y
 *           spin, scaled by motion magnitude and the user's strength
 *           slider. Result is a yaw+pitch end-over-end roll.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>State carries no ground/motion data on its own — it is read from
 * {@link AuroraItemPhysicsSnapshots}, populated each frame by
 * {@code ItemEntityRendererExtractMixin}.
 */
public final class ItemPhysicsPose {
    private ItemPhysicsPose() {}

    /** Gap (in blocks) left under a flat item so it does not z-fight the ground. */
    private static final float FLAT_CLEARANCE = 1.0f / 64.0f;

    public static float translateY(float origY, ItemEntityRenderState state) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.itemPhysicsEnabled || !cfg.itemPhysicsFlatOnGround) return origY;
        AuroraItemPhysicsSnapshots.Snapshot snap = AuroraItemPhysicsSnapshots.get(state);
        if (snap == null || snap.groundT <= 0f) return origY;
        // The flat pose is a +90° turn about X, which maps the model's z to -y:
        // its lowest point is maxZ below the origin, so the origin goes maxZ up.
        // A fixed push-down tuned to one model box sank 26.x items under the
        // ground (2026-11-18). Eased by groundT so the item settles rather
        // than snapping flat in one frame.
        float flatY = (float) state.item.getModelBoundingBox().maxZ + FLAT_CLEARANCE;
        return Mth.lerp(snap.groundT, origY, flatY);
    }

    /** Whether {@link #spin} would change vanilla's spin for this item. */
    public static boolean altersSpin(ItemEntityRenderState state) {
        return AuroraConfig.get().itemPhysicsEnabled && AuroraItemPhysicsSnapshots.get(state) != null;
    }

    public static Quaternionfc spin(Quaternionfc origSpin, ItemEntityRenderState state) {
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
        Quaternionf air = new Quaternionf(origSpin);
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
