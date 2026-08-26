package com.aurora.client.feature.impl;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Reader-only feature that resolves the per-shield tint color used by
 * {@code ShieldSpecialRendererMixin}. State (whose shield is being
 * drawn right now) is propagated via a thread-local set by:
 *
 * <ul>
 *   <li>{@code HeldItemRendererTweaksMixin}     — first-person, local player</li>
 *   <li>{@code PlayerItemInHandLayerMixin}      — third-person & other players</li>
 * </ul>
 *
 * <p>Item-frame / dropped-item shield renders never set the holder, so
 * {@link #resolveTintArgb()} returns the AVAILABLE color for them when
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

    /** Vanilla "no tint" color that {@code submitModelPart} expects. */
    public static final int NO_TINT = 0xFFFFFFFF; // == -1

    private static final ThreadLocal<Integer> CURRENT_HOLDER_ID =
            ThreadLocal.withInitial(() -> NO_HOLDER);

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Shield Statuses"; }
    @Override public boolean enabledByDefault() { return false; }

    /** Push the entity-id of the shield holder before vanilla shield render. */
    public static void pushHolder(int entityId) { CURRENT_HOLDER_ID.set(entityId); }

    /** Clear the holder context after vanilla shield render returns. */
    public static void popHolder() { CURRENT_HOLDER_ID.set(NO_HOLDER); }

    /**
     * Resolve the ARGB tint to multiply onto the next shield model
     * submit. Returns {@link #NO_TINT} when the feature is off so the
     * mixin can short-circuit to vanilla behavior.
     *
     * <p>The shield's render type uses cutout blending (binary alpha
     * test), so the alpha channel of a vertex color is <i>not</i>
     * blended — it would just punch through or pass. To make the user's
     * opacity slider actually feel like opacity, we reinterpret the
     * picker's alpha as <b>tint strength</b>: at A=0 we return white
     * (no tint, vanilla shield), at A=255 we return the full chosen
     * RGB (max tint), and in between we lerp the RGB toward white. The
     * output alpha byte is always {@code 0xFF} so the cutout test
     * always passes.
     */
    public static int resolveTintArgb() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.shieldStatusEnabled) return NO_TINT;

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
        return remapOpacityToTintStrength(picked);
    }

    /**
     * Convert an ARGB picker color into a cutout-safe tint int that
     * encodes the picker's alpha as tint strength against white.
     *
     * <p>For each channel: {@code out = lerp(255, channel, a/255)}.
     * Equivalent to {@code out = 255 - (255 - channel) * a / 255},
     * which keeps the math in int arithmetic.
     */
    private static int remapOpacityToTintStrength(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0)   return NO_TINT;            // fully transparent ⇒ no tint
        if (a == 255) return 0xFF000000 | (argb & 0x00FFFFFF); // full strength

        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b =  argb         & 0xFF;
        int rOut = 255 - ((255 - r) * a) / 255;
        int gOut = 255 - ((255 - g) * a) / 255;
        int bOut = 255 - ((255 - b) * a) / 255;
        return 0xFF000000 | (rOut << 16) | (gOut << 8) | bOut;
    }
}