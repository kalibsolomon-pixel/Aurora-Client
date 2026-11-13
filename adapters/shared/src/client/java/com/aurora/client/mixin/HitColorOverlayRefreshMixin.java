package com.aurora.client.mixin;

import com.aurora.client.util.HitColorOverlayRows;
import com.aurora.client.util.OverlayReloadListener;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two hit-color hooks riding vanilla's per-entity overlay-coordinate hook
 * {@code LivingEntityRenderer.getOverlayCoords(state, whiteProgress)}:
 *
 * <ol>
 *   <li><b>Per-frame row refresh</b> — the tick-level overlay reload runs at
 *       20 Hz, so the time-driven modes (PULSE, RAINBOW) would visibly step.
 *       This fires once per rendered living entity each frame, which is
 *       always before the first overlay sample of the frame; the listener
 *       itself no-ops for the other modes.</li>
 *
 *   <li><b>Gradient row selection</b> — vanilla returns
 *       {@code pack(u(white), v(hasRedOverlay))}, i.e. the hurt flash always
 *       samples row {@code RED_OVERLAY_V}. For the health-gradient mode the
 *       v coordinate is remapped to one of the eight gradient rows
 *       (0 = 0% health .. 7 = 100%). The armor and trim tint hooks perform
 *       the same remap, so equipment flashes in step with the body.</li>
 * </ol>
 *
 * <p>Gated entirely by {@code hitColorEnabled}; the gradient remap
 * additionally by {@code hitColorHealthGradient} (an information advantage,
 * ADR 0007, so it ships off). Both fall back to vanilla behavior
 * bit-for-bit when inactive, and neither is needed for the base flat mode,
 * so a failed application here leaves Hit Color working.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class HitColorOverlayRefreshMixin {

    @Inject(method = "getOverlayCoords", at = @At("HEAD"))
    private static void aurora$refreshTimeDrivenRows(LivingEntityRenderState state,
                                                     float whiteOverlayProgress,
                                                     CallbackInfoReturnable<Integer> cir) {
        OverlayReloadListener.callFrameRefresh(Util.getMillis());
    }

    @ModifyArg(method = "getOverlayCoords",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/OverlayTexture;pack(II)I"),
            index = 1)
    private static int aurora$gradientOverlayRow(int vanillaRow,
                                                 LivingEntityRenderState state,
                                                 float whiteOverlayProgress) {
        return HitColorOverlayRows.hurtRow(state, vanillaRow);
    }
}
