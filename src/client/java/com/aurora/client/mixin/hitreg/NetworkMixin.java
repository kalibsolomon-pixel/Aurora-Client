package com.aurora.client.mixin.hitreg;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.aurora.client.hitreg.settings.Toggle;
import com.aurora.client.hitreg.util.DontAnimate;

import static com.aurora.client.hitreg.Hitreg.*;

@Mixin(ClientPacketListener.class)
public abstract class NetworkMixin {
    @ModifyArg(method = "handleDamageEvent(Lnet/minecraft/network/protocol/game/ClientboundDamageEventPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;handleDamageEvent(Lnet/minecraft/world/damagesource/DamageSource;)V"))
    private DamageSource handleDamageEvent(DamageSource damageSource) {
        if (Toggle.HIDE_ANIMATIONS.toggled()) return new DontAnimate(damageSource);
        else if (damageSource != null && damageSource.getEntity() != null && client.player != null && client.player.getId() == damageSource.getEntity().getId() && lastHitHandled && withinFight && System.currentTimeMillis() - lastAttack <= 1000) return new DontAnimate(damageSource);
        return damageSource;
    }
}