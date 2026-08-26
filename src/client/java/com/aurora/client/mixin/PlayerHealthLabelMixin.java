package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AttackedPlayerTracker;
import com.aurora.client.util.AuroraHealthSnapshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a player's current HP above their head.
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

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("TAIL"),
            require = 0)
    private void aurora$drawHealth(LivingEntityRenderState state,
                                   PoseStack matrices,
                                   SubmitNodeCollector collector,
                                   CameraRenderState camera,
                                   CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.playerHealthIndicators) return;

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

        Component label;
        if (cfg.playerHealthDisplayMode == AuroraConfig.HealthDisplayMode.HEARTS) {
            label = buildHeartsLabel(snap.health, snap.maxHealth, snap.absorption);
        } else {
            // Heart in red, number in white.
            label = Component.literal("\u2764 ")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)))
                    .copy()
                    .append(Component.literal(formatHp(hp))
                            .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        }

        // Position above the head. Use the entity's nameTagAttachment when
        // available (already includes vanilla's headroom); fall back to a
        // bounding-box-derived offset for entities that don't have one.
        // When a nametag IS being drawn here, push our label slightly higher
        // so they don't overlap.
        Vec3 base = state.nameTagAttachment != null
                ? state.nameTagAttachment
                : new Vec3(0.0, state.boundingBoxHeight + 0.5, 0.0);
        Vec3 offset = state.nameTag != null ? base.add(0.0, 0.30, 0.0) : base;

        // submitNameTag handles billboarding + distance fading like vanilla
        // nametag rendering. Calling shape mirrors EntityRenderer.submitNameTag.
        collector.submitNameTag(
                matrices,
                offset,
                0,
                label,
                !state.isDiscrete,
                color,
                state.distanceToCameraSq,
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

    /**
     * Builds a Component that renders health as a row of heart-shaped
     * icons, mimicking the vanilla HUD health bar. Each heart represents
     * 2 HP (one "row" of hearts in vanilla terms).
     *
     * <p>The visual encoding mirrors vanilla so the bar is instantly
     * readable to anyone who plays the game. Each heart is a single
     * {@code ❤} glyph (U+2764) whose color communicates its state:
     * <ul>
     *   <li><b>Full</b> — bright red ({@code 0xFF5555}), or gold
     *       ({@code 0xFFAA00}) for absorption hearts.</li>
     *   <li><b>Half</b> — a pronounced dark red ({@code 0xFFCC3333}).
     *       This is deliberately saturated so it stays clearly visible
     *       at small sizes / distance.</li>
     *   <li><b>Empty</b> — not drawn at all. Only hearts that carry at
     *       least some HP are rendered, so the row reads purely as how
     *       much health the entity has left rather than also showing
     *       empty containers.</li>
 * </ul>
     *
     * <p>Vanilla caps the visible bar at 10 hearts (20 HP) per row and
     * wraps to a second row for higher max-health/absorption totals. For
     * an above-head indicator that quickly becomes unreadable, so we cap
     * the row at {@code MAX_HEARTS} (10) hearts and, when the entity has
     * more than that, append the remaining HP as a short "+N" suffix.
     * This keeps the most common case (mobs & players near 20 HP) an
     * exact 1:1 match with the HUD while still degrading gracefully for
     * tanky bosses (Wither, Warden, etc.) without flooding the screen.
     */
    private static Component buildHeartsLabel(float health, float maxHealth, float absorption) {
        // Vanilla heart math: each heart = 2 HP. We round so half-hearts
        // land on a 0.5 boundary (1 HP = half heart).
        float totalHealth = health + absorption;
        float maxNormal = Math.max(2.0f, maxHealth);
        int normalHearts = Math.round(maxNormal / 2.0f);
        // Absorption hearts sit beyond the normal max.
        int absorptionHearts = Math.max(0, Math.round(absorption / 2.0f));

        // Cap how many hearts we actually render. Beyond MAX_HEARTS the
        // row gets too wide to read above a head, so we summarize the
        // overflow as a "+N" number instead.
        int MAX_HEARTS = 10;
        int heartsToRender = Math.min(MAX_HEARTS, normalHearts + absorptionHearts);

        // How much HP each rendered heart represents. If the entity has
        // more max health than we can show, we scale each icon so the bar
        // still spans 0..full proportionally (e.g. a 60-HP Wither shows
        // 10 hearts where each icon = 6 HP).
        float hpPerRenderedHeart;
        if (normalHearts + absorptionHearts <= MAX_HEARTS) {
            hpPerRenderedHeart = 2.0f;
        } else {
            hpPerRenderedHeart = (maxNormal + absorption) / heartsToRender;
        }

        // Palette — matches vanilla heart colors. The half-heart tones are
        // deliberately saturated so a half heart reads as "damaged red"
        // and stays clearly visible at small sizes / distance. Empty hearts
        // are not drawn (skipped in the loop below), so they have no color.
        final int RED_FULL = 0xFFFF5555;   // normal full
        final int RED_HALF = 0xFFCC3333;   // normal half — pronounced dark red
        final int GOLD_FULL = 0xFFFFAA00;  // absorption full
        final int GOLD_HALF = 0xFFCC7A00;  // absorption half

        MutableComponent hearts = Component.empty();
        float remaining = totalHealth;
        for (int i = 0; i < heartsToRender; i++) {
            float fill = remaining;            // HP available for this heart
            // Absorption hearts (gold) only get distinct coloring when
            // we're not in "scaled down" mode — otherwise the gold tint
            // loses its "beyond max health" meaning.
            boolean isAbsorption =
                    i >= normalHearts && (normalHearts + absorptionHearts) <= MAX_HEARTS;

            // Don't render empty hearts at all — only show hearts that carry
            // at least half their HP. The row then reads purely as "health
            // remaining" rather than also marking empty containers.
            if (fill < hpPerRenderedHeart / 2.0f) {
                remaining -= hpPerRenderedHeart;
                continue;
            }

            int heartColor = (isAbsorption)
                    ? (fill >= hpPerRenderedHeart ? GOLD_FULL : GOLD_HALF)
                    : (fill >= hpPerRenderedHeart ? RED_FULL : RED_HALF);
            hearts.append(Component.literal("\u2764")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(heartColor))));
            remaining -= hpPerRenderedHeart;
        }

        // If the entity has more total HP than we could display, append a
        // compact "+N" so the player still gets a sense of remaining bulk.
        float displayedHp = heartsToRender * hpPerRenderedHeart;
        float overflow = totalHealth - displayedHp;
        if (overflow > 0.5f) {
            hearts.append(Component.literal("+" + formatHp(overflow))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFAAAAAA))));
        }

        return hearts;
    }
}
