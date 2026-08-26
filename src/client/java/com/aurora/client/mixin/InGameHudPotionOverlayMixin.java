package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels vanilla's status effect overlay rendering when Aurora's potion
 * HUD module is enabled. Aurora draws its own per-effect rows so vanilla's
 * top-right effect strip would otherwise overlap.
 */
@Mixin(Gui.class)
public abstract class InGameHudPotionOverlayMixin {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 1)
    private void aurora$cancelVanillaPotionOverlay(GuiGraphics ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (AuroraConfig.get().potionHudEnabled) {
            ci.cancel();
        }
    }
}