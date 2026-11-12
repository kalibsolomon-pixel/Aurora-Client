package com.aurora.client.mixin;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hide Reload Overlay (ADR 0009): a resource reload after startup (F3+T, a
 * pack change, a language change) keeps drawing the current screen instead
 * of fading to the Mojang overlay, and the overlay leaves as soon as the
 * reload finishes instead of after its two-second fade.
 *
 * <p>Only overlays created with {@code fadeIn} are touched; the first
 * reload at startup has none and still shows its progress. The overlay stays
 * installed while the reload runs, so input and pausing behave as in vanilla.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private boolean fadeIn;
    @Shadow private long fadeOutStart;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void aurora$drawScreenInstead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!fadeIn || !AuroraConfig.get().perfHideReloadOverlay) return;
        Screen screen = NativeUi.screen(minecraft);
        if (screen != null) screen.extractRenderStateWithTooltipAndSubtitles(graphics, mouseX, mouseY, a);
        // tick() sets fadeOutStart once the reload is done and the screen re-initialized.
        if (fadeOutStart > -1L) NativeUi.setOverlay(minecraft, null);
        ci.cancel();
    }

    /** Vanilla holds a fading-in overlay for a full second even when the reload finished sooner. */
    @Inject(method = "isReadyToFadeOut", at = @At("HEAD"), cancellable = true)
    private void aurora$readyAtOnce(CallbackInfoReturnable<Boolean> cir) {
        if (fadeIn && AuroraConfig.get().perfHideReloadOverlay) cir.setReturnValue(true);
    }
}
