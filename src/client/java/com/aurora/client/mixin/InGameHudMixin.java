package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla crosshair when Aurora's custom crosshair is enabled,
 * so the two don't stack on top of each other.
 */
@Mixin(Gui.class)
public class InGameHudMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void aurora$hideVanillaCrosshair(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (AuroraConfig.get().crosshairEnabled) {
            ci.cancel();
        }
    }
}
