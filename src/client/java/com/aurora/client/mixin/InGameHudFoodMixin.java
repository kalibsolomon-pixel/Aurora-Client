package com.aurora.client.mixin;

import com.aurora.client.hud.SaturationOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
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
@Mixin(Hud.class)
public class InGameHudFoodMixin {

    @Inject(method = "extractFood", at = @At("RETURN"), require = 0)
    private void aurora$renderSaturation(
            GuiGraphicsExtractor context,
            Player player,
            int top,
            int right,
            CallbackInfo ci
    ) {
        SaturationOverlay.render(context, player, top, right);
    }
}