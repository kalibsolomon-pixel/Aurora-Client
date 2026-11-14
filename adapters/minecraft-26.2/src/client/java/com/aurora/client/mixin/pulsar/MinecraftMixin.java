package com.aurora.client.mixin.pulsar;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.PhenomenaClient;

import dev.phenomena.combat.tracking.ClientCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  @Inject(
      method = "startAttack",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
  private void aurora$pulsar$empty(CallbackInfoReturnable<Boolean> ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.emptySwing();
  }
}
