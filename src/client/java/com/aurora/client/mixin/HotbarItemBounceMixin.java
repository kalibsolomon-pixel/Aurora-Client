package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.HotbarBounceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HotbarItemBounceMixin {

    @Inject(method = "renderItemHotbar", at = @At("TAIL"), require = 1)
    private void aurora$drawBounceOverlay(GuiGraphics ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!AuroraConfig.get().hotbarBounceEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        HotbarBounceTracker.tickAndRender(ctx, mc);
    }
}