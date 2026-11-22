package com.aurora.client.mixin;

import com.aurora.client.util.StreamerMode;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Masks the local player's name and optionally other players' names in the
 * multiplayer tab list while Streamer Mode is active.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class StreamerTabNameMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void aurora$maskTabName(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        if (!StreamerMode.isActive() || entry == null || entry.getProfile() == null) return;
        UUID id = entry.getProfile().id();
        Component orig = cir.getReturnValue();
        if (StreamerMode.isLocalPlayer(id)) {
            if (!StreamerMode.hideOwnName()) return;
            Component redacted = StreamerMode.redactComponent(orig);
            if (redacted == orig) {
                cir.setReturnValue(Component.literal(StreamerMode.ownAlias())
                        .setStyle(orig != null ? orig.getStyle() : net.minecraft.network.chat.Style.EMPTY));
            } else {
                cir.setReturnValue(redacted);
            }
        } else if (StreamerMode.hideOtherNames()) {
            cir.setReturnValue(StreamerMode.opponentComponent(id));
        }
    }
}
