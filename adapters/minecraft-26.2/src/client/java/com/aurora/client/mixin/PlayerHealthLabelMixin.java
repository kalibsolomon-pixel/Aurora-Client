package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.EntityHeartBar;
import com.aurora.client.util.AttackedPlayerTracker;
import com.aurora.client.util.AuroraHealthSnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws an entity's current HP above its head: a number, or vanilla's
 * heart bar ({@link EntityHeartBar}) in Hearts mode.
 *
 * <p>Hooks {@code LivingEntityRenderer.render(state, matrices, vertices, light)}
 * at RETURN ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â fires for every player render every frame, regardless of
 * whether vanilla decided to draw a nametag. This is the same injection
 * strategy the popular PlayerHealthIndicators mod uses.
 *
 * <p>The 1.21.5+ render-state architecture means the {@code render} method
 * gets a {@link AvatarRenderState} snapshot, not a live entity.
 * Health and absorption aren't fields on the state, so we look up the
 * live player via {@link AvatarRenderState#id} and read HP off it.
 *
 * <p>Positioning matches PHI's approach: translate up by hitbox height
 * + 0.5 blocks, apply the entity dispatcher's camera-facing rotation
 * quaternion, then draw scaled Component. The translate-up amount nudges
 * higher when a vanilla nametag is also being shown (so we don't
 * overlap it).
 *
 * <p>Skipped: the local player (you), invisible players, and anyone
 * beyond 64 blocks (squared distance &gt; 4096) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â vanilla's nametag
 * distance cap, applied here too so we don't pollute distant scenes.
 *
 * <p>The dispatcher comes from {@code Minecraft.getEntityRenderDispatcher()}
 * rather than a {@code @Shadow}, because the dispatcher field is declared
 * on the {@code EntityRenderer} grandparent class and {@code @Shadow}
 * doesn't follow inheritance for field lookup.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerHealthLabelMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$drawHealth(LivingEntityRenderState state,
                                   PoseStack matrices,
                                   SubmitNodeCollector collector,
                                   CameraRenderState camera,
                                   CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.playerHealthIndicators) return;

        // The player filter is enforced here, not only at snapshot capture:
        // Hit Color's health-gradient mode shares the snapshot store with
        // players captured, and those captures must not add labels.
        if (!cfg.playerHealthShowPlayers && state instanceof AvatarRenderState) return;

        // Distance cap (vanilla nametag distance: 64 blocks -> 4096 squared).
        if (state.distanceToCameraSq > 4096.0) return;
        if (state.isInvisible || state.isInvisibleToPlayer) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;

        // Health data comes from the snapshot captured during render-state
        // extraction (see LivingEntityRendererExtractMixin). Works for any
        // LivingEntity, not just players.
        AuroraHealthSnapshots.Snapshot snap = AuroraHealthSnapshots.get(state);
        if (snap == null) return;

        // Suppress on the local player (avatar render state carries the
        // entity id; the local player would otherwise show their own bar).
        if (state instanceof AvatarRenderState p && p.id == client.player.getId()) return;

        if (cfg.playerHealthOnlyAttacked) {
            if (!AttackedPlayerTracker.wasAttacked(snap.uuid)) return;
        }

        float hp = snap.health + snap.absorption;
        float max = Math.max(1.0f, snap.maxHealth);
        float ratio = Mth.clamp(hp / max, 0.0f, 2.0f);
        int color = healthColor(ratio);

        // Position above the head. Use the entity's nameTagAttachment when
        // available (already includes vanilla's headroom); fall back to a
        // bounding-box-derived offset for entities that don't have one.
        // When a nametag IS being drawn here, push our label slightly higher
        // so they don't overlap.
        Vec3 base = state.nameTagAttachment != null
                ? state.nameTagAttachment
                : new Vec3(0.0, state.boundingBoxHeight + 0.5, 0.0);
        Vec3 offset = state.nameTag != null ? base.add(0.0, 0.30, 0.0) : base;

        // Hearts: vanilla's heart sprites and animation, billboarded like a nametag.
        if (cfg.playerHealthDisplayMode == AuroraConfig.HealthDisplayMode.HEARTS) {
            EntityHeartBar.submit(matrices, collector, camera, offset, !state.isDiscrete, snap);
            return;
        }

        // Heart in red, number in white.
        Component label = Component.literal("\u2764 ")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)))
                .copy()
                .append(Component.literal(formatHp(hp))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));

        // submitNameTag handles billboarding + distance fading like vanilla
        // nametag rendering. Calling shape mirrors EntityRenderer.submitNameTag.
        collector.submitNameTag(
                matrices,
                offset,
                0,
                label,
                !state.isDiscrete,
                color,
                camera);
    }

    /** RedÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢yellowÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢green based on ratio of current/max HP, with a green-cyan tint above 100%. */
    private static int healthColor(float ratio) {
        float r, g, b;
        if (ratio <= 1.0f) {
            if (ratio < 0.5f) {
                float t = ratio * 2.0f;
                r = 1.0f; g = t; b = 0.0f;
            } else {
                float t = (ratio - 0.5f) * 2.0f;
                r = 1.0f - t; g = 1.0f; b = 0.0f;
            }
        } else {
            float t = Mth.clamp((ratio - 1.0f), 0.0f, 1.0f);
            r = t * 0.7f; g = 1.0f; b = t;
        }
        int ri = (int) (r * 255.0f) & 0xFF;
        int gi = (int) (g * 255.0f) & 0xFF;
        int bi = (int) (b * 255.0f) & 0xFF;
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static String formatHp(float hp) {
        if (Math.abs(hp - Math.round(hp)) < 0.05f) {
            return Integer.toString(Math.round(hp));
        }
        return String.format("%.1f", hp);
    }
}
