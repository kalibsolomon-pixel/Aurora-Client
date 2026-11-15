package com.aurora.client.mixin.pulsar;

import com.aurora.client.config.AuroraConfig;
import dev.phenomena.PhenomenaClient;

import dev.phenomena.combat.tracking.ClientCapture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  @Inject(method = "jumpFromGround", at = @At("TAIL"))
  private void aurora$pulsar$jump(CallbackInfo ci) {
    if (!AuroraConfig.get().pulsarEnabled || !PhenomenaClient.isInitialized()) return;
    ClientCapture.jumped((LivingEntity) (Object) this);
  }
}
