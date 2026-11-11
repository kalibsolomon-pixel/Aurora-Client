package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Reader-only feature that resolves the per-shield overlay color used by
 * {@code ShieldSpecialRendererMixin}. State (whose shield is being
 * drawn right now) is propagated via a thread-local set by:
 *
 * <ul>
 *   <li>{@code HeldItemRendererTweaksMixin}     — first-person, local player</li>
 *   <li>{@code PlayerItemInHandLayerMixin}      — third-person & other players</li>
 * </ul>
 *
 * <p>Item-frame / dropped-item shield renders never set the holder, so
 * {@link #resolveOverlayArgb()} returns the AVAILABLE color for them when
 * the master toggle is on.
 *
 * <p>Status logic:
 * <ul>
 *   <li>holder == local player && on shield cooldown → DISABLED
 *       (the shield was disabled by an axe hit — the 100-tick cooldown)</li>
 *   <li>otherwise → AVAILABLE</li>
 * </ul>
 *
 * <p>The overlay is <b>enabled (AVAILABLE) at all times</b> except during the
 * brief window when the shield is rendered unusable by an axe. For the local
 * player, {@code isOnCooldown} reliably detects this window. Other players'
 * cooldowns aren't synced to clients, so we cannot detect their axe-disable
 * state and fall back to AVAILABLE — which is correct: the shield should
 * appear ready unless we know for certain it isn't.
 */
public class ShieldStatusFeature implements Feature {
    public static final String ID = "shield_status";

    /** Sentinel for "no holder context" — distinct from any real entity id. */
    private static final int NO_HOLDER = Integer.MIN_VALUE;

    private static final ThreadLocal<Integer> CURRENT_HOLDER_ID =
            ThreadLocal.withInitial(() -> NO_HOLDER);

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Shield Statuses"; }

    /** Push the entity-id of the shield holder before vanilla shield render. */
    public static void pushHolder(int entityId) { CURRENT_HOLDER_ID.set(entityId); }

    /** Clear the holder context after vanilla shield render returns. */
    public static void popHolder() { CURRENT_HOLDER_ID.set(NO_HOLDER); }

    /**
     * Resolve the ARGB color of the FLAT TRANSLUCENT overlay submitted over
     * the shield model (see {@code ShieldSpecialRendererMixin}). Returns a
     * color whose ALPHA is the true overlay opacity — the picker's alpha
     * slider is the source of truth (owner ruling 2026-10-30): at A=255 the
     * shield's texture is fully covered by the solid color; at A=0 (or when
     * the feature is off) nothing is submitted and the shield renders
     * vanilla. The former cutout-limited multiply tint could never cover the
     * texture — dark texels always read through — which is why the overlay
     * never felt vibrant.
     *
     * <p>The overlay rides a white sprite on the shield atlas through an
     * {@code entityTranslucent} render type, so the vertex alpha blends
     * normally — no cutout reinterpretation.
     */
    public static int resolveOverlayArgb() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.shieldStatusEnabled) return 0;

        int holderId = CURRENT_HOLDER_ID.get();
        Minecraft mc = Minecraft.getInstance();
        Player local = mc.player;

        int picked;
        if (local != null
                && holderId != NO_HOLDER
                && holderId == local.getId()) {
            // Local player: we have authoritative cooldown data. The shield is
            // on cooldown only after an axe hit disabled it (vanilla applies a
            // 100-tick / 5-second cooldown via ShieldItem#disableFor). At all
            // other times — including while actively blocking — the shield is
            // ready, so we show the AVAILABLE color.
            if (local.getCooldowns().isOnCooldown(Items.SHIELD.getDefaultInstance())) {
                picked = cfg.shieldStatusDisabledColor;
            } else {
                picked = cfg.shieldStatusAvailableColor;
            }
        } else {
            // Other players / mobs / item-frames / dropped items:
            // Other entities' cooldowns are not synced to clients, so we can't
            // detect their axe-disabled window. The shield is therefore shown
            // as AVAILABLE — correct, since it should only appear DISABLED
            // when we know for certain it is unusable. Blocking (raising the
            // shield to protect the holder) is the shield doing its job, so it
            // is emphatically an AVAILABLE state.
            picked = cfg.shieldStatusAvailableColor;
        }
        return picked;
    }
}
