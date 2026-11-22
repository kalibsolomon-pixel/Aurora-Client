package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pack Tweaks custom sky colors, presets, celestial toggles, star brightness,
 * and void dark-disc suppression on Minecraft 26.3.
 */
@Mixin(SkyRenderer.class)
public abstract class CustomSkyMixin {
    @Unique private final Vector3f aurora$skyScratch = new Vector3f();
    @Unique private final Vector4f aurora$sunsetScratch = new Vector4f();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aurora$applyCustomSky(ClientLevel level, float partialTick, Camera camera,
                                       SkyRenderState state, CallbackInfo ci) {
        if (state == null) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.customSkyMode != AuroraConfig.CustomSkyMode.VANILLA) {
            int vanillaRgb = state.skyColor == null ? 0
                    : (((int) (state.skyColor.x() * 255f) & 0xFF) << 16)
                    | (((int) (state.skyColor.y() * 255f) & 0xFF) << 8)
                    | ((int) (state.skyColor.z() * 255f) & 0xFF);
            int rgb = PackTweaksMath.skyRgb(cfg.customSkyMode, cfg.customSkyColor, vanillaRgb);
            aurora$skyScratch.set(
                    ((rgb >> 16) & 0xFF) / 255.0f,
                    ((rgb >> 8) & 0xFF) / 255.0f,
                    (rgb & 0xFF) / 255.0f);
            state.skyColor = aurora$skyScratch;

            int sunrise = PackTweaksMath.sunriseSunsetArgb(cfg.customSkyMode, 0);
            if (sunrise == 0) {
                aurora$sunsetScratch.set(0f, 0f, 0f, 0f);
            } else {
                aurora$sunsetScratch.set(
                        ((sunrise >> 16) & 0xFF) / 255.0f,
                        ((sunrise >> 8) & 0xFF) / 255.0f,
                        (sunrise & 0xFF) / 255.0f,
                        ((sunrise >>> 24) & 0xFF) / 255.0f);
            }
            state.sunriseAndSunsetColor = aurora$sunsetScratch;
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
    private void aurora$maybeHideSun(RenderPass pass, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowSun) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeHideMoon(RenderPass pass, MoonPhase phase, float alpha, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowMoon) ci.cancel();
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void aurora$maybeHideStars(RenderPass pass, float brightness, PoseStack poseStack, CallbackInfo ci) {
        if (!AuroraConfig.get().customSkyShowStars) ci.cancel();
    }
}
