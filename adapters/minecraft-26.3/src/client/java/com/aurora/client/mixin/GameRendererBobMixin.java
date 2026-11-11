package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AnimationCurves;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * View-bob animation tweaks:
 * <ul>
 *   <li>Curves the tickProgress fed into view bob so the camera bob has an
 *       eased footstep cadence rather than linear (subtle smoothing).</li>
 *   <li>Optionally scales the bob amplitude via a gentle Z-translate that
 *       modulates the perceived bob depth.</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererBobMixin {

    @ModifyVariable(
            method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("HEAD"),
            argsOnly = true,
            require = 0
    )
    private float aurora$curveBobTickDelta(float t) {
        return AnimationCurves.applyBob(t);
    }

    /**
     * Scale the apparent view-bob amplitude. A multiplier of 0.0 cancels
     * the bob entirely; values above 1.0 push the view subtly closer during
     * the bob peak for a punchier stride. Kept intentionally mild to avoid
     * motion sickness or clipping into geometry.
     */
    @Inject(
            method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("TAIL"),
            require = 0
    )
    private void aurora$scaledBob(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.smoothAnimationsEnabled) return;
        double s = cfg.viewBobAmplitude;
        if (s == 1.0 || s <= 0.0) return;

        // Translate along the view Z to modulate the perceived bob depth.
        // (1 - s) < 0 amplifies (moves camera forward into the bob),
        // (1 - s) > 0 dampens (pulls camera back, flattening the bob).
        float push = (float) (0.06 * (1.0 - s));
        matrices.translate(0.0f, 0.0f, push);
    }
}
