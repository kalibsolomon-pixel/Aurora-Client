package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Multiplies the atmospheric (distance) fog start/end by a user-configurable
 * factor. Higher = less fog visible but more rendered chunks contribute to
 * draw cost; vanilla = 1x. We multiply rather than push to infinity so users
 * can pick their own perf vs visibility tradeoff.
 *
 * <p>1.21.11: {@code applyStartEndModifier} renamed to {@code setupFog};
 * {@code Entity}+{@code BlockPos} params collapsed into {@code Camera}.
 */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class NoFogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"), require = 1)
    private void aurora$multiplyFog(FogData data, Camera camera,
                                    ClientLevel world, float viewDistance,
                                    DeltaTracker tickCounter, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.noFogEnabled) return;
        float mult = (float) Math.max(1.0, cfg.fogDistanceMultiplier);
        data.environmentalStart *= mult;
        data.environmentalEnd   *= mult;
        data.renderDistanceStart *= mult;
        data.renderDistanceEnd   *= mult;
        data.skyEnd   *= mult;
        data.cloudEnd *= mult;
    }
}