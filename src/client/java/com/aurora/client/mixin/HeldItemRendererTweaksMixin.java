package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ShieldStatusFeature;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.math.Axis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.Blaze3D;

/**
 * First-person held-item tweaks: shield style preset, main/off-hand scale.
 *
 * <p><b>Injection timing:</b> we hook just BEFORE vanilla's internal
 * {@code renderItem} call inside {@code renderArmWithItem} (1.21.11; the
 * method was renamed from {@code renderFirstPersonItem} pre-1.21.11). By
 * that point vanilla has applied hand-position, swing, block-raise, and
 * base item-display transforms; our matrix operations stack on top.
 *
 * <p><b>Shield style:</b> applies unconditionally including during the
 * block-raise animation, mirroring how PvP texture packs work. The one
 * exception: when the SIDE style is active and the player is actively
 * blocking, we swap to a Compact-like front-facing render so the shield
 * can do its protective job visually.
 *
 * <p><b>Hand scale:</b> uniformly scales the held item in the configured
 * hand. Range 50..150% per hand. Shields are excluded so that the
 * shield-style preset retains its own scale.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererTweaksMixin {

    /**
     * Reusable scratch array for {@link #idleSwayOffset}, kept on the mixin
     * to avoid a {@code new float[3]} allocation every frame per hand.
     * Rendering is single-threaded so a single shared instance is safe.
     */
    private static final float[] SWAY_SCRATCH = new float[3];

    /**
     * Push the local player as the shield-tint context holder before
     * vanilla draws the first-person held item. Paired with
     * {@link #aurora$popShieldHolder} at RETURN so any shield model
     * submitted during {@code renderArmWithItem} sees the right holder.
     * Runs unconditionally — {@link ShieldStatusFeature#resolveTintArgb()}
     * already short-circuits when the feature is off.
     */
    @Inject(method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("HEAD"))
    private void aurora$pushShieldHolder(AbstractClientPlayer player,
                                         float tickDelta, float pitch, InteractionHand hand,
                                         float swingProgress, ItemStack stack,
                                         float equipProgress, PoseStack matrices,
                                         SubmitNodeCollector vertices,
                                         int light, CallbackInfo ci) {
        if (player != null) ShieldStatusFeature.pushHolder(player.getId());
    }

    @Inject(method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At("RETURN"))
    private void aurora$popShieldHolder(AbstractClientPlayer player,
                                        float tickDelta, float pitch, InteractionHand hand,
                                        float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack matrices,
                                        SubmitNodeCollector vertices,
                                        int light, CallbackInfo ci) {
        ShieldStatusFeature.popHolder();
    }

    @Inject(method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    shift = At.Shift.BEFORE),
            require = 1)
    private void aurora$applyTweaks(AbstractClientPlayer player,
                                    float tickDelta, float pitch, InteractionHand hand,
                                    float swingProgress, ItemStack stack,
                                    float equipProgress, PoseStack matrices,
                                    SubmitNodeCollector vertices,
                                    int light, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean isShield = stack != null && stack.is(Items.SHIELD);

        // Per-item translation, rotation, and scaling transformations.
        // Only do the (allocation-prone) registry lookup + toString when the
        // feature is actually enabled — otherwise we skip straight to the
        // shield/sway logic, avoiding Identifier allocation + map probing
        // on every frame for every player.
        if (cfg.itemScaleEnabled && stack != null && !stack.isEmpty()) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId != null) {
                String idStr = itemId.toString();
                if (cfg.itemScales.containsKey(idStr)) {
                    AuroraConfig.ItemScaleData data = cfg.itemScales.get(idStr);
                    if (data != null) {
                        // Apply translations
                        if (data.translationX != 0.0f || data.translationY != 0.0f || data.translationZ != 0.0f) {
                            matrices.translate(data.translationX, data.translationY, data.translationZ);
                        }
                        // Apply rotations (pitch, yaw, roll)
                        if (data.pitch != 0.0f) {
                            matrices.mulPose(Axis.XP.rotationDegrees(data.pitch));
                        }
                        if (data.yaw != 0.0f) {
                            matrices.mulPose(Axis.YP.rotationDegrees(data.yaw));
                        }
                        if (data.roll != 0.0f) {
                            matrices.mulPose(Axis.ZP.rotationDegrees(data.roll));
                        }
                        // Apply scale
                        if (data.scale != 1.0f) {
                            matrices.scale(data.scale, data.scale, data.scale);
                        }
                    }
                } else {
                    // Apply default hand-specific scales if no specific item configuration exists
                    float defaultScale = (hand == InteractionHand.OFF_HAND) ? cfg.offHandDefaultScale : cfg.mainHandDefaultScale;
                    if (defaultScale != 1.0f) {
                        matrices.scale(defaultScale, defaultScale, defaultScale);
                    }
                }
            }
        }

        // Shield style - only applies when holding a shield.
        if (cfg.shieldTweaksEnabled && isShield) {
            applyShieldStyle(matrices, cfg.shieldStyle, hand, player);
        }

        // 1.8-style first-person swing arc — a steeper "chop" plus a wider
        // outward sweep that peaks mid-swing, layered on top of whatever
        // vanilla's swingArm() already did to the matrix stack. Fires only
        // during an active swing and only when the LEGACY_1_8 style is picked.
        // We host this here (rather than a dedicated @Inject into the private
        // swingArm method) because this injection point is proven reliable:
        // it targets a single public renderItem INVOKE with require=1, and it
        // runs AFTER swingArm has applied its transform.
        if (cfg.smoothAnimationsEnabled
                && cfg.swingStyle == AuroraConfig.SwingStyle.LEGACY_1_8
                && swingProgress > 0f && swingProgress < 1f) {
            HumanoidArm arm = (hand == InteractionHand.MAIN_HAND)
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            applyLegacySwingArc(matrices, swingProgress, arm);
        }

        // Idle sway — a breathing/float when standing still, scaled by
        // strength (up to 10.0 for dramatic drift). Suppressed while actively
        // swinging so it doesn't fight the swing arc. Off-hand gets a tiny
        // phase offset so both hands don't move in lockstep. The movement
        // curve is selected by the user and shapes how the item oscillates.
        if (cfg.smoothAnimationsEnabled && cfg.idleSwayEnabled && swingProgress <= 0f) {
            float t = (float) (Blaze3D.getTime() * 0.6);
            float phase = (hand == InteractionHand.OFF_HAND) ? 1.7f : 0f;
            // Allow up to 10.0 for dramatic drift; clamp to a sane max.
            float s = (float) Math.max(0.0, Math.min(10.0, cfg.idleSwayStrength));
            float[] off = idleSwayOffset(t + phase, s, cfg.idleSwayCurve);
            matrices.translate(off[0], off[1], 0.0f);
            matrices.mulPose(Axis.ZP.rotationDegrees(off[2]));
        }
    }

    /**
     * Compute the (x, y, tilt) idle-sway offset for the given curve. {@code t}
     * is the phase-advanced time; {@code s} is the user strength (0..10).
     *
     * <p>Returns the shared {@link #SWAY_SCRATCH} array
     * {@code {translateX, translateY, tiltDeg}} — it is overwritten on every
     * call, so callers must consume it before invoking this method again.
     * Avoids a {@code new float[3]} allocation every frame per hand.
     *
     * <p>Base amplitudes (at s=1.0) are tuned to a gentle breath. Higher
     * values scale every dimension up; some curves (Circular, Lissajous)
     * read as dramatic figure tracing at high values, others (Double Sine)
     * read as a nervous wobble.
     */
    private static float[] idleSwayOffset(float t, float s, AuroraConfig.IdleSwayCurve curve) {
        float tx, ty, tilt;
        switch (curve) {
            case LISSAJOUS -> {
                // Figure-8 / infinity: x at 1x, y at 2x frequency.
                tx   = (float) Math.sin(t)        * 0.0045f * s;
                ty   = (float) Math.sin(t * 2f)   * 0.0035f * s;
                tilt = (float) Math.sin(t * 0.5f) * 1.5f    * s;
            }
            case SMOOTHSTEP_DRIFT -> {
                // Shape a sine through smoothstep so the drift lingers at the
                // extremes. The raw sine is mapped 0..1 -> smoothstep -> -1..1.
                float raw = (float) Math.sin(t);
                float n = raw * 0.5f + 0.5f; // 0..1
                float shaped = (n * n * (3f - 2f * n)) * 2f - 1f; // back to -1..1
                float rawY = (float) Math.cos(t * 0.8f);
                float nY = rawY * 0.5f + 0.5f;
                float shapedY = (nY * nY * (3f - 2f * nY)) * 2f - 1f;
                tx   = shaped  * 0.0045f * s;
                ty   = shapedY * 0.0035f * s;
                tilt = shaped  * 1.5f    * s;
            }
            case DOUBLE_SINE -> {
                // 2x frequency: quicker, shallower wobble.
                tx   = (float) Math.sin(t * 2f)        * 0.0030f * s;
                ty   = (float) Math.cos(t * 2f * 0.8f) * 0.0025f * s;
                tilt = (float) Math.sin(t * 2f * 0.5f) * 1.2f    * s;
            }
            case PENDULUM -> {
                // Triple-harmonic pendulum: rich, organic sway.
                tx   = (float) (Math.sin(t) + 0.33 * Math.sin(t * 2f) + 0.2 * Math.sin(t * 3f)) * 0.0025f * s;
                ty   = (float) (Math.cos(t * 0.8f) + 0.33 * Math.cos(t * 1.6f))                  * 0.0020f * s;
                tilt = (float) (Math.sin(t * 0.5f) + 0.33 * Math.sin(t))                          * 1.6f    * s;
            }
            case CIRCULAR -> {
                // Clean circular orbit: cos/sin of the same phase.
                tx   = (float) Math.cos(t)        * 0.0040f * s;
                ty   = (float) Math.sin(t)        * 0.0040f * s;
                tilt = (float) Math.sin(t * 0.5f) * 1.5f    * s;
            }
            default -> {
                // Single pure sine — the classic gentle breath. (SINE_BREATH)
                tx   = (float) Math.sin(t)        * 0.0045f * s;
                ty   = (float) Math.cos(t * 0.8f) * 0.0035f * s;
                tilt = (float) Math.sin(t * 0.5f) * 1.5f    * s;
            }
        }
        SWAY_SCRATCH[0] = tx;
        SWAY_SCRATCH[1] = ty;
        SWAY_SCRATCH[2] = tilt;
        return SWAY_SCRATCH;
    }

    private static void applyShieldStyle(PoseStack matrices,
                                         AuroraConfig.ShieldStyle style,
                                         InteractionHand hand,
                                         AbstractClientPlayer player) {
        if (style == null) return;

        // Off-hand on right-handed player -> shield is at screen LEFT, X<0.
        // Sign flips for main-hand placement to keep "outer" actually outer.
        float xSign = (hand == InteractionHand.OFF_HAND) ? 1.0f : -1.0f;

        // Detect active block: the player is using the item in this hand.
        // When blocking with SIDE style, swap to a Compact-ish render
        // (vanilla position, scaled down) so the shield can actually
        // protect the player visually. Other styles keep their look.
        boolean blocking = player != null
                && player.isUsingItem()
                && player.getUsedItemHand() == hand;

        if (style == AuroraConfig.ShieldStyle.SIDE && blocking) {
            // Compact-ish during block: vanilla position, slightly smaller
            // than Lowered (which has no scale).
            matrices.translate(0.04f, 0.04f, 0.0f);
            matrices.scale(0.70f, 0.70f, 0.70f);
            return;
        }

        switch (style) {
            case VANILLA -> {
                // no-op
            }
            case LOWERED -> {
                matrices.translate(0.0f, -0.15f, 0.0f);
            }
            case SIDE -> {
                matrices.translate(xSign * -0.15f, -0.10f, 0.0f);
                matrices.mulPose(Axis.YP.rotationDegrees(xSign * 75.0f));
                matrices.scale(0.80f, 0.80f, 0.80f);
            }
            case COMPACT -> {
                matrices.translate(0.05f, 0.05f, 0.0f);
                matrices.scale(0.55f, 0.55f, 0.55f);
            }
        }
    }

    /**
     * Layer the 1.8-style swing arc on top of whatever vanilla's
     * {@code swingArm} already applied to the matrix stack. We add a steeper
     * forward pitch ("chop") plus a touch of outward yaw sweep, both peaking
     * mid-swing and mirrored per-arm — recreating the punchy old held-item
     * feel without disturbing vanilla's translate/scale contributions.
     *
     * @param swingProgress 0..1, 0 and 1 are excluded by the caller.
     * @param arm           the arm whose hand is being rendered.
     */
    private static void applyLegacySwingArc(PoseStack matrices, float swingProgress, HumanoidArm arm) {
        // A sine bump peaking at t=0.5, scaled to a few degrees. The sign
        // flips per-arm so left/right mirror each other naturally.
        float bump = (float) Math.sin(swingProgress * Math.PI); // 0→1→0
        float sign = (arm == HumanoidArm.RIGHT) ? -1f : 1f;

        // Steeper forward pitch (the classic "chop" arc).
        matrices.mulPose(Axis.XP.rotationDegrees(sign * 18f * bump));
        // A touch of outward yaw sweep for the wider old-school arc.
        matrices.mulPose(Axis.YP.rotationDegrees(sign * 10f * bump));
    }
}