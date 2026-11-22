package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the local player's tab-list and UI head skin with the default skin
 * while Streamer Mode's Hide Own Skin option is active.
 */
@Mixin(PlayerInfo.class)
public abstract class StreamerPlayerInfoSkinMixin {

    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void aurora$hideOwnPlayerInfoSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (!StreamerMode.hideOwnSkin()) return;
        GameProfile profile = getProfile();
        if (profile != null && StreamerMode.isLocalPlayer(profile.id())) {
            cir.setReturnValue(DefaultPlayerSkin.getDefaultSkin());
        }
    }
}
