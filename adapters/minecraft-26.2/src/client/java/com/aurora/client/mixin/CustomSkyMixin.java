package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pack Tweaks custom sky colors, presets, celestial toggles, star brightness,
 * and void dark-disc suppression on Minecraft 26.2.
 */
@Mixin(SkyRenderer.class)
public abstract class CustomSkyMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aurora$applyCustomSky(ClientLevel level, float partialTick, Camera camera,
                                       SkyRenderState state, CallbackInfo ci) {
        if (state == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.customSkyMode != AuroraConfig.CustomSkyMode.VANILLA) {
            int rgb = PackTweaksMath.skyRgb(cfg.customSkyMode, cfg.customSkyColor, state.skyColor);
            state.skyColor = (state.skyColor & 0xFF000000) | rgb;
            state.sunriseAndSunsetColor = PackTweaksMath.sunriseSunsetArgb(cfg.customSkyMode, state.sunriseAndSunsetColor);
        }
        if (!cfg.customSkyShowStars || Math.abs(cfg.customSkyStarBrightness - 1.0) > 1e-4
                || cfg.customSkyMode == AuroraConfig.CustomSkyMode.AURORA_NIGHT) {
            state.starBrightness = PackTweaksMath.starBrightness(
                    state.starBrightness, cfg.customSkyMode, cfg.customSkyStarBrightness, cfg.customSkyShowStars);
        }
        if (cfg.customSkyHideVoid) {
            state.shouldRenderDarkDisc = false;
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeHideSun(float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowSun) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeHideMoon(MoonPhase phase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowMoon) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeHideStars(float brightness, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowStars) ci.cancel();
    }
}
