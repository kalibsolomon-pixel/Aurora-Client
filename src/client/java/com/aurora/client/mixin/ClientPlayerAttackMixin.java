package com.aurora.client.mixin;

import com.aurora.client.util.AttackedPlayerTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Records the UUID of any player the local user attacks. Used by the
 * player health indicator feature's "only attacked players" mode.
 *
 * <p>Hooks {@code Minecraft.startAttack()} at HEAD. The attack-target
 * entity isn't a parameter â€” it's resolved from {@code hitResult}.
 * If that's an entity hit and the entity is living, we record the UUID.
 *
 * <p>We record before vanilla's cancel/range/cooldown logic â€” clicking on
 * a player is enough intent to be in the "interested" set.
 */
@Mixin(Minecraft.class)
public abstract class ClientPlayerAttackMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), require = 0)
    private void aurora$recordAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        HitResult hr = client.hitResult;
        if (hr instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            if (target instanceof LivingEntity le) {
                AttackedPlayerTracker.recordAttack(le.getUUID());
            }
        }
    }
}
