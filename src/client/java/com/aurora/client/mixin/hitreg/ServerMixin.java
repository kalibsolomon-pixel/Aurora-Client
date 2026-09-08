package com.aurora.client.mixin.hitreg;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import com.aurora.client.hitreg.util.PacketProcessor;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPacketListener.class)
public class ServerMixin {
    @Inject(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At("HEAD"), cancellable = true)
    private void handleDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        //this will run once on the network thread & once on the main thread, unless a server like minemenclub bundles it
        if (!PacketProcessor.processDamage(packet)) ci.cancel();
    }

    @Inject(method = "handleAnimate(Lnet/minecraft/network/protocol/game/ClientboundAnimatePacket;)V", at = @At("HEAD"), cancellable = true)
    private void handleAnimate(ClientboundAnimatePacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (!PacketProcessor.processAnimation(packet)) ci.cancel();
    }

    @Inject(method = "handleSoundEvent(Lnet/minecraft/network/protocol/game/ClientboundSoundPacket;)V", at = @At("HEAD"), cancellable = true)
    private void handleSoundEvent(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (!PacketProcessor.processSound(packet)) ci.cancel();
    }
}