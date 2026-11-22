package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the local player's in-world skin with the neutral default Steve
 * skin while Streamer Mode's Hide Own Skin option is active.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class StreamerSkinMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void aurora$hideOwnSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!StreamerMode.hideOwnSkin()) return;
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && self.getUUID().equals(mc.player.getUUID())) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultSkin());
        }
    }
}
