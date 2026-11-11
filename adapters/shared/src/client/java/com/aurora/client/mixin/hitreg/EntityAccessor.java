package com.aurora.client.mixin.hitreg;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
@Mixin(LivingEntity.class)
public interface EntityAccessor {
    @Invoker("getHurtSound")
    SoundEvent getHurtSound(DamageSource source);
}