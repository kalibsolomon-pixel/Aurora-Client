package com.aurora.client.mixin;

import com.aurora.client.hud.SaturationOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders Aurora's saturation overlay at the end of vanilla's renderFood
 * so it appears on top of the hunger icons, perfectly aligned.
 *
 * <p>Approach borrowed from AppleSkin (Unlicense).
 */
@Mixin(Gui.class)
public class InGameHudFoodMixin {

    @Inject(method = "renderFood", at = @At("RETURN"), require = 0)
    private void aurora$renderSaturation(
            GuiGraphics context,
            Player player,
            int top,
            int right,
            CallbackInfo ci
    ) {
        SaturationOverlay.render(context, player, top, right);
    }
}