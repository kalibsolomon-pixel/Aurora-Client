package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.WaterFogEnvironment;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stretches water fog distance, mirroring the lava-clarity pattern.
 * Multiplies all four fog distance components by the configured factor.
 *
 * <p>Performance: free in shallow / contained water bodies (lakes, rivers,
 * pools). Costs FPS in deep ocean biomes proportional to the multiplier
 * since each block of clearer view unlocks more contiguous water chunks.
 */
@Mixin(WaterFogEnvironment.class)
public abstract class UnderwaterClarityMixin {

    @Inject(method = "setupFog", at = @At("TAIL"), require = 1)
    private void aurora$multiplyWaterFog(FogData data, Camera camera,
                                         ClientLevel world, float viewDistance,
                                         DeltaTracker tickCounter, CallbackInfo ci) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.underwaterClarityEnabled) return;
        float mult = (float) Math.max(1.0, cfg.underwaterFogMultiplier);
        data.environmentalStart  *= mult;
        data.environmentalEnd    *= mult;
        data.renderDistanceStart *= mult;
        data.renderDistanceEnd   *= mult;
    }
}