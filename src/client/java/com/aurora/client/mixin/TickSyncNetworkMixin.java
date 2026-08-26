package com.aurora.client.mixin;

import com.aurora.client.feature.impl.TickSyncFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTickingStatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TickSyncNetworkMixin {
    @Inject(method = "handleMoveEntity", at = @At("HEAD"))
    private void aurora$tickSyncMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (TickSyncFeature.INSTANCE != null) {
            TickSyncFeature.INSTANCE.onEntityPacket();
        }
    }

    @Inject(method = "handleTickingState", at = @At("HEAD"))
    private void aurora$tickSyncTickingState(ClientboundTickingStatePacket packet, CallbackInfo ci) {
        if (TickSyncFeature.INSTANCE != null) {
            TickSyncFeature.INSTANCE.serverTPS = packet.tickRate();
            TickSyncFeature.INSTANCE.clientTPS = Math.min(20, packet.tickRate());
        }
    }
}
