package com.aurora.client.mixin.pulsar;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.PhenomenaClient;

import dev.phenomena.combat.tracking.ClientCapture;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only observations at the TAIL of vanilla handlers, after the main-thread dispatch guard. No
 * packet is cancelled, delayed or modified. Every handler here is low frequency except sound, which
 * returns after two identity comparisons unless a shield sound arrives during an engagement.
 */
@Mixin(ClientPacketListener.class)
public abstract class PacketListenerMixin {
  @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
  private void aurora$pulsar$velocity(ClientboundSetEntityMotionPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.velocity(p);
  }

  @Inject(method = "handleSystemChat", at = @At("TAIL"))
  private void aurora$pulsar$system(ClientboundSystemChatPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.systemMessage(p.content().getString(), p.overlay());
  }

  @Inject(method = "handleDamageEvent", at = @At("TAIL"))
  private void aurora$pulsar$damage(ClientboundDamageEventPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.damage(p);
  }

  @Inject(method = "handleSoundEvent", at = @At("TAIL"))
  private void aurora$pulsar$sound(ClientboundSoundPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.sound(p);
  }

  @Inject(method = "handleAnimate", at = @At("TAIL"))
  private void aurora$pulsar$animate(ClientboundAnimatePacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.animate(p);
  }

  @Inject(method = "handlePlayerCombatKill", at = @At("TAIL"))
  private void aurora$pulsar$combatKill(ClientboundPlayerCombatKillPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.combatKill(p);
  }

  @Inject(method = "setSubtitleText", at = @At("TAIL"))
  private void aurora$pulsar$subtitle(ClientboundSetSubtitleTextPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.subtitle(p.text().getString());
  }

  @Inject(method = "setTitleText", at = @At("TAIL"))
  private void aurora$pulsar$title(ClientboundSetTitleTextPacket p, CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.title(p.text().getString());
  }
}
