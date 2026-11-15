package com.aurora.client.mixin.hitreg;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.aurora.client.hitreg.Hitreg;
import com.aurora.client.hitreg.util.DontAnimate;
import com.aurora.client.hitreg.util.MultiVersion;
import com.aurora.client.hitreg.util.OnlyAnimate;

/**
 * From BetterHitreg by Jass (modrinth.com/mod/betterhitreg), integrated into
 * Aurora with the author's permission. Logic is upstream's, moved verbatim —
 * see {@link com.aurora.client.hitreg.BetterHitreg} for the integration notes.
 */
@Mixin(LivingEntity.class)
public abstract class DamageMixin {
    @Shadow @Nullable private DamageSource lastDamageSource;

    @Shadow private long lastDamageStamp;

    @Shadow @Final public WalkAnimationState walkAnimation;

    @Shadow public int hurtDuration;

    @Shadow public int hurtTime;

    @Shadow protected abstract void playHurtSound(DamageSource damageSource);

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
    private void handleDamageEvent(DamageSource damageSource, CallbackInfo ci) {
        if (damageSource instanceof DontAnimate) {
            LivingEntity entity = (LivingEntity) (Object) this;
            entity.invulnerableTime = 20;
            lastDamageSource = damageSource;

            lastDamageStamp = MultiVersion.getLevelTime(entity);

            //if it's you play the sound since it's client sided
            if (Hitreg.client.player != null && Hitreg.client.player.getId() == entity.getId()) playHurtSound(damageSource);

            ci.cancel();
        }

        if (damageSource instanceof OnlyAnimate) {
            walkAnimation.setSpeed(1.5F);
            hurtDuration = 10;
            hurtTime = hurtDuration;
            ci.cancel();
        }
    }
}