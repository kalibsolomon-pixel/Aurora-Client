/*
 * Ported from harimasa/HitColor (MIT licensed).
 * https://github.com/harimasa/HitColor
 */
package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.OverlayReloadListener;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
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
     * Last applied (color, enabled) Ã¢â‚¬â€ sentinel values guarantee the first call
     * always applies. Storing on the mixin (per-instance) is fine because
     * vanilla constructs OverlayTexture once and keeps it for the session.
     */
    @Unique private int aurora$lastAppliedColor   = Integer.MIN_VALUE;
    @Unique private int aurora$lastAppliedEnabled = -1; // -1 = unknown, 0 = false, 1 = true

    /**
     * The exact pixel vanilla writes into the top 8 rows of the overlay atlas
     * (0xB2FF0000 ARGB — red at alpha 178, OverlayTexture's constructor).
     * Restores the vanilla red flash bit-for-bit when hit color is off.
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
     * Rewrites the top-half rows of the 16x16 overlay atlas with the
     * configured hit-color pixel, and uploads the texture to the GPU.
     *
     * <p>Was previously: 16 GPU uploads per call, 256 setColor calls per call,
     * called every world tick (20 Hz) Ã¢â€ â€™ 320 uploads/sec regardless of whether
     * Hit Color was even enabled. Now: short-circuits when nothing changed,
     * uploads exactly once when it does, and writes only the rows that need
     * writing.
     */
    @Unique
    public void reloadOverlay() {
        AuroraConfig cfg = AuroraConfig.get();
        int wantColor   = cfg.hitColor;
        int wantEnabled = cfg.hitColorEnabled ? 1 : 0;

        if (wantColor == aurora$lastAppliedColor && wantEnabled == aurora$lastAppliedEnabled) {
            return; // no change since last apply Ã¢â‚¬â€ skip GPU work entirely
        }

        NativeImage nativeImage = this.texture.getPixels();
        if (nativeImage == null) return;

        int packed = (wantEnabled == 1) ? getColorInt(wantColor) : AURORA_DISABLED_PIXEL;

        // Original behavior: only top 8 rows are rewritten (the hit-flash strip).
        for (int y = 0; y < 8; ++y) {
            for (int x = 0; x < 16; ++x) {
                nativeImage.setPixel(x, y, packed);
            }
        }
        texture.upload(); // single upload, was 16

        aurora$lastAppliedColor   = wantColor;
        aurora$lastAppliedEnabled = wantEnabled;
    }
}