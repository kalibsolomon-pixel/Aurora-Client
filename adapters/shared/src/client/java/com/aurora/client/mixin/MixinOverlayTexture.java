/*
 * Ported from harimasa/HitColor (MIT licensed).
 * https://github.com/harimasa/HitColor
 */
package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.HitColorModes;
import com.aurora.client.util.OverlayReloadListener;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class MixinOverlayTexture implements OverlayReloadListener {

    @Shadow
    @Final
    private DynamicTexture texture;

    /**
     * Hash of everything the overlay rows depend on (color, enabled, mode,
     * gradient gate, pulse rate, and the current time for time-driven
     * modes) — sentinel value guarantees the first call always applies.
     * Storing on the mixin (per-instance) is fine because vanilla constructs
     * OverlayTexture once and keeps it for the session.
     */
    @Unique private int aurora$lastSignature = Integer.MIN_VALUE;

    /**
     * The exact pixel vanilla writes into the top 8 rows of the overlay atlas
     * (0xB2FF0000 ARGB — red at alpha 178, OverlayTexture's constructor).
     * Restores the vanilla red flash bit-for-bit when hit color is off or in
     * the vanilla mode.
     */
    @Unique private static final int AURORA_DISABLED_PIXEL = -1291911168;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void modifyHitColor(CallbackInfo ci) {
        this.reloadOverlay();
        OverlayReloadListener.register(this);
    }

    public void aurora$onOverlayReload() {
        this.reloadOverlay();
    }

    public void aurora$onOverlayFrameRefresh(long nowMillis) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.hitColorEnabled) return;
        // Only the time-driven modes need per-frame updates; the tick-level
        // reload catches config changes for the rest.
        AuroraConfig.HitColorMode mode = cfg.hitColorMode;
        if (mode != AuroraConfig.HitColorMode.PULSE && mode != AuroraConfig.HitColorMode.RAINBOW) return;
        this.reloadOverlayAt(cfg, nowMillis);
    }

    @Unique
    private static int getColorInt(int argbColor) {
        // Use Mojang's ARGB utility (same approach as Animatium). Decompose
        // the user's ARGB picker color via the float helpers and recompose
        // with colorFromFloat -- this produces the exact pixel-int format
        // expected by NativeImage.setPixel.
        //
        // CRITICAL: invert alpha. The vanilla entity shader uses
        //     color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
        // i.e. overlayColor.a is the weight of the ORIGINAL color, not the
        // overlay. So storing alpha=255 makes the overlay invisible, and
        // storing alpha=0 makes the overlay fully replace the diffuse.
        // To make the picker behave intuitively (high picker alpha = strong
        // visible tint), we feed (1 - picker_alpha) into the texture.
        float pickerA = ((argbColor >>> 24) & 0xFF) / 255.0f;
        float a = 1.0f - pickerA;
        float r = ((argbColor >>> 16) & 0xFF) / 255.0f;
        float g = ((argbColor >>>  8) & 0xFF) / 255.0f;
        float b = ( argbColor         & 0xFF) / 255.0f;
        return ARGB.colorFromFloat(a, r, g, b);
    }

    /**
     * Tick-level entry: rewrites the flash rows only when their signature
     * changed since the last apply. The per-frame refresh keeps the
     * time-driven modes smooth between ticks.
     */
    @Unique
    public void reloadOverlay() {
        this.reloadOverlayAt(AuroraConfig.get(), Util.getMillis());
    }

    @Unique
    private void reloadOverlayAt(AuroraConfig cfg, long now) {
        int signature = aurora$signature(cfg, now);
        if (signature == aurora$lastSignature) {
            return; // no change since last apply — skip GPU work entirely
        }

        NativeImage nativeImage = this.texture.getPixels();
        if (nativeImage == null) return;

        aurora$applyRows(nativeImage, cfg, now);
        texture.upload(); // single upload for all 8 flash rows

        aurora$lastSignature = signature;
    }

    /**
     * Rewrites the top-half rows (the hit-flash strip) of the 16x16 overlay
     * atlas per the current mode and uploads nothing itself.
     *
     * <pre>
     * VANILLA          — vanilla's own red pixels (feature visually off)
     * FLAT             — the picker color, constant
     * PULSE            — picker color, alpha oscillating over time
     * HEALTH_GRADIENT  — eight rows bucketed by health (only when the
     *                    gradient gate is on; otherwise the flat color)
     * RAINBOW          — hue cycling over time, picker alpha
     * </pre>
     */
    @Unique
    private void aurora$applyRows(NativeImage image, AuroraConfig cfg, long now) {
        int color = cfg.hitColor;
        if (!cfg.hitColorEnabled || cfg.hitColorMode == AuroraConfig.HitColorMode.VANILLA) {
            aurora$fillRows(image, AURORA_DISABLED_PIXEL);
            return;
        }
        switch (cfg.hitColorMode) {
            case FLAT -> aurora$fillRows(image, getColorInt(HitColorModes.flat(color)));
            case PULSE -> aurora$fillRows(image, getColorInt(HitColorModes.pulse(color, now, cfg.hitColorPulseRate)));
            case RAINBOW -> aurora$fillRows(image, getColorInt(HitColorModes.rainbow(now, color)));
            case HEALTH_GRADIENT -> {
                if (cfg.hitColorHealthGradient) {
                    for (int y = 0; y < 8; ++y) {
                        int packed = getColorInt(HitColorModes.gradientRowColor(y, color));
                        for (int x = 0; x < 16; ++x) image.setPixel(x, y, packed);
                    }
                } else {
                    aurora$fillRows(image, getColorInt(HitColorModes.flat(color)));
                }
            }
            default -> aurora$fillRows(image, getColorInt(HitColorModes.flat(color)));
        }
    }

    @Unique
    private static void aurora$fillRows(NativeImage image, int packed) {
        for (int y = 0; y < 8; ++y) {
            for (int x = 0; x < 16; ++x) {
                image.setPixel(x, y, packed);
            }
        }
    }

    /**
     * Cheap change detector: folds every config input the rows depend on,
     * plus the palette step for the time-driven modes. Allocation-free.
     */
    @Unique
    private static int aurora$signature(AuroraConfig cfg, long now) {
        int s = 31 * (31 * cfg.hitColor + (cfg.hitColorEnabled ? 1 : 0));
        s = 31 * (s + cfg.hitColorMode.ordinal()) + (cfg.hitColorHealthGradient ? 1 : 0);
        if (cfg.hitColorMode == AuroraConfig.HitColorMode.PULSE) {
            s = 31 * s + (int) Math.round(
                    Math.min(Math.max(cfg.hitColorPulseRate, HitColorModes.PULSE_RATE_MIN), HitColorModes.PULSE_RATE_MAX) * 100.0);
        }
        if (cfg.hitColorMode == AuroraConfig.HitColorMode.PULSE || cfg.hitColorMode == AuroraConfig.HitColorMode.RAINBOW) {
            s = 31 * s + HitColorModes.animationStep(
                    cfg.hitColorMode == AuroraConfig.HitColorMode.PULSE, now, cfg.hitColorPulseRate);
        }
        return s;
    }
}
