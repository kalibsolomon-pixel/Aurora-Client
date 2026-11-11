package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses the vanilla crosshair when Aurora's custom crosshair is enabled,
 * so the two don't stack on top of each other.
 */
@Mixin(Hud.class)
public class InGameHudMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void aurora$hideVanillaCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (AuroraConfig.get().crosshairEnabled) {
            ci.cancel();
        }
    }
}
