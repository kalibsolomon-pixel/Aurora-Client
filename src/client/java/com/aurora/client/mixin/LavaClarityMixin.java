package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stretches lava fog distance, mirroring the underwater-clarity pattern.
 * Multiplies all four fog distance components by the configured factor so
 * the player can see further when submerged in lava.
 *
 * <p>1.21.11: {@code applyStartEndModifier(FogData,Entity,BlockPos,...)} was
 * collapsed into {@code setupFog(FogData,Camera,ClientLevel,float,DeltaTracker)}.
 */
@Mixin(LavaFogEnvironment.class)
public abstract class LavaClarityMixin {

    @Inject(method = "setupFog", at = @At("TAIL"), require = 1)
    private void aurora$multiplyLavaFog(FogData data, Camera camera,
                                        ClientLevel world, float viewDistance,
                                        DeltaTracker tickCounter, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.lavaClarityEnabled) return;
        float mult = (float) Math.max(1.0, cfg.lavaFogMultiplier);
        data.environmentalStart  *= mult;
        data.environmentalEnd    *= mult;
        data.renderDistanceStart *= mult;
        data.renderDistanceEnd   *= mult;
    }
}