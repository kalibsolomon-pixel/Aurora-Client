package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Optionally hides player face avatars in the tab list on Minecraft 26.3,
 * reclaiming their 9px column width cleanly.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabHeadsMixin {

    @ModifyExpressionValue(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z")
    )
    private boolean aurora$maybeHideHeads(boolean original) {
        return original && !AuroraConfig.get().tabListHideHeads;
    }
}
