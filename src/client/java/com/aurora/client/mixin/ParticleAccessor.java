package com.aurora.client.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin exposing the package-private {@code quadSize} and
 * {@code rCol}/{@code gCol}/{@code bCol} fields on
 * {@link SingleQuadParticle} for read+write.
 *
 * <p>Used by {@link TotemParticleMixin} to scale particles after
 * construction, and by {@link ParticleEngineMixin} to apply the
 * per-particle color overlay tint from the Particles module.
 */
@Mixin(SingleQuadParticle.class)
public interface ParticleAccessor {

    @Accessor("quadSize")
    float aurora$getScale();

    @Accessor("quadSize")
    void aurora$setScale(float value);

    @Accessor("rCol")
    float aurora$getRed();

    @Accessor("rCol")
    void aurora$setRed(float value);

    @Accessor("gCol")
    float aurora$getGreen();

    @Accessor("gCol")
    void aurora$setGreen(float value);

    @Accessor("bCol")
    float aurora$getBlue();

    @Accessor("bCol")
    void aurora$setBlue(float value);
}
