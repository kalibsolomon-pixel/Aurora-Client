package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scales totem-of-undying particles by the user's configured percentage.
 *
 * <p>Hooks the {@link TotemParticle} constructor TAIL and multiplies the
 * inherited (package-private) {@code Particle.scale} field by the config
 * factor. Field access goes through {@link ParticleAccessor}, an accessor
 * mixin on {@link net.minecraft.client.particle.Particle} that exposes
 * {@code scale} as a public getter+setter pair via {@code @Accessor}.
 *
 * <p>The targeted constructor is the 7-arg variant
 * ({@code (ClientLevel, DDDDDD, SpriteProvider)V}) â€” the one the particle
 * factory uses. There's a 5-arg variant with explicit scale, but it's
 * not what's invoked in normal play.
 *
 * <p>No effect when the totem-tweaks feature is off (factor = 1.0).
 */
@Mixin(TotemParticle.class)
public abstract class TotemParticleMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/client/particle/SpriteSet;)V",
            at = @At("TAIL"), require = 0)
    private void aurora$scaleParticle(CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.totemTweaksEnabled) return;
        float factor = Math.max(25, Math.min(100, cfg.totemParticleScale)) / 100.0f;

        ParticleAccessor self = (ParticleAccessor) (Object) this;
        self.aurora$setScale(self.aurora$getScale() * factor);
    }
}