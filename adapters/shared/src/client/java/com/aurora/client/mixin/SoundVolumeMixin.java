package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.RecentSoundLog;
import com.aurora.client.util.SoundVolumes;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Per-sound volume attenuation (0–100% only, never amplifies above vanilla)
 * across Minecraft 26.1.2, 26.2, and 26.3. Also records recently played sound
 * ids for quick access in the Sound Volume Mixer.
 *
 * <p>{@code SoundEngine.play} computes a new sound's starting volume through
 * {@code calculateVolume(float, SoundSource)}; the {@code SoundInstance}
 * overload only runs for ticking (looping/moving) sounds and category slider
 * changes. Both paths are attenuated, otherwise one-shot sounds would always
 * start at full vanilla volume.
 */
@Mixin(SoundEngine.class)
public abstract class SoundVolumeMixin {

    @ModifyExpressionValue(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F")
    )
    private float aurora$attenuateNewSound(float volume, @Local(argsOnly = true) SoundInstance sound) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.soundVolumesEnabled || sound == null) return volume;
        String key = aurora$key(sound);
        if (key == null) return volume;
        RecentSoundLog.record(key);
        return aurora$attenuate(cfg, key, volume);
    }

    @Inject(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void aurora$attenuateTickingSound(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.soundVolumesEnabled || sound == null) return;
        String key = aurora$key(sound);
        if (key == null) return;
        float volume = cir.getReturnValueF();
        float attenuated = aurora$attenuate(cfg, key, volume);
        if (attenuated != volume) cir.setReturnValue(attenuated);
    }

    private static String aurora$key(SoundInstance sound) {
        Identifier id = sound.getIdentifier();
        return id == null ? null : id.toString();
    }

    private static float aurora$attenuate(AuroraConfig cfg, String key, float volume) {
        Map<String, Float> overrides = cfg.soundVolumes;
        if (overrides == null || overrides.isEmpty()) return volume;
        Float scale = overrides.get(key);
        return scale == null ? volume : SoundVolumes.attenuate(volume, scale);
    }
}
