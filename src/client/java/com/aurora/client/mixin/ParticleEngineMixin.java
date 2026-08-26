package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ParticleControlFeature;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Per-particle visibility and scale gate for Aurora's "particles" feature.
 *
 * <p>The vanilla {@link ParticleEngine#createParticle} body is roughly:
 * <pre>{@code
 *   Particle p = makeParticle(opts, ...);
 *   if (p != null) add(p);   // <-- enqueued for render BEFORE return
 *   return p;
 * }</pre>
 * Because {@code add(p)} is called before the method returns, injecting
 * at {@code RETURN} and forcing the return value to {@code null} is
 * <b>too late</b> — the particle is already in the engine's add-queue
 * and will render. Visibility therefore has to be gated at {@code HEAD}
 * so {@code makeParticle} and {@code add} are skipped entirely.
 *
 * <p>Scaling, on the other hand, is fine at {@code RETURN}: the particle
 * is in the queue but has not yet been rendered, and the queued object
 * is the same reference we mutate here.
 *
 * <p>Method name only — there is exactly one {@code createParticle} on
 * {@link ParticleEngine} so descriptor pinning isn't needed and avoids
 * any annotation-processor descriptor-remapping concerns.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void aurora$gate(ParticleOptions options,
                             double x, double y, double z,
                             double dx, double dy, double dz,
                             CallbackInfoReturnable<Particle> cir) {
        Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        if (key == null) return;
        if (!ParticleControlFeature.isVisible(key.toString())) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void aurora$scale(ParticleOptions options,
                              double x, double y, double z,
                              double dx, double dy, double dz,
                              CallbackInfoReturnable<Particle> cir) {
        Particle p = cir.getReturnValue();
        if (p == null) return;
        Identifier key = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        if (key == null) return;
        String id = key.toString();
        float scale = ParticleControlFeature.getScale(id);
        int color = ParticleControlFeature.getColor(id);
        if (scale == 1.0f && color == 0) return;
        // Only SingleQuadParticle subclasses expose quadSize/rCol/gCol/bCol
        // via the accessor. Other Particle types (e.g. explosion/shockwave
        // particles like class_8979 from mace smash attacks) don't, and
        // casting them would throw ClassCastException and disconnect the
        // player.
        if (!(p instanceof ParticleAccessor acc)) return;
        if (scale != 1.0f) {
            acc.aurora$setScale(acc.aurora$getScale() * scale);
        }
        if (color != 0) {
            // Alpha → tint strength (0.0–1.0). RGB channels are the target
            // color we blend toward. We keep a fraction of the original
            // color so textured particles still show variation rather than
            // flattening to a solid slab of tint.
            float strength = ((color >>> 24) & 0xFF) / 255.0f;
            if (strength > 0f) {
                float tr = ((color >>> 16) & 0xFF) / 255.0f;
                float tg = ((color >>> 8) & 0xFF) / 255.0f;
                float tb = (color & 0xFF) / 255.0f;
                acc.aurora$setRed(lerp(acc.aurora$getRed(), tr, strength));
                acc.aurora$setGreen(lerp(acc.aurora$getGreen(), tg, strength));
                acc.aurora$setBlue(lerp(acc.aurora$getBlue(), tb, strength));
            }
        }
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
