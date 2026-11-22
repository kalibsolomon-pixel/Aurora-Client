package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.VanillaHudOverlayHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves, scales or hides vanilla toasts and advancement pop-ups from Aurora's
 * HUD editor. Gated by {@code vanillaHudEnabled} (fail-soft: when disabled or
 * unapplied, vanilla draws untouched).
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance")
public abstract class VanillaToastMixin {
    @Shadow @Final private Toast toast;
    @Shadow @Final private int firstSlotIndex;
    @Shadow private float visiblePortion;
    @Shadow private long fullyVisibleFor;
    @Shadow protected boolean hasFinishedRendering;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;I)V",
            at = @At("HEAD"), cancellable = true)
    private void aurora$transformOrHideToast(GuiGraphicsExtractor graphics, int screenWidth, CallbackInfo ci) {
        if (this.hasFinishedRendering) return;
        if (!AuroraConfig.get().vanillaHudEnabled) return;
        if (VanillaHudOverlayHelper.handleToast(graphics, screenWidth, this.toast,
                this.firstSlotIndex, this.visiblePortion, this.fullyVisibleFor)) {
            ci.cancel();
        }
    }
}
