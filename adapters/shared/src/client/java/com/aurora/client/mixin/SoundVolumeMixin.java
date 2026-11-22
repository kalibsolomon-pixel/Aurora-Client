package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.RecentSoundLog;
import com.aurora.client.util.SoundVolumes;
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
 */
@Mixin(SoundEngine.class)
public abstract class SoundVolumeMixin {

    @Inject(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"),
            cancellable = true
    )
    private void aurora$attenuateSoundVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound == null) return;
        Identifier id = sound.getIdentifier();
        if (id == null) return;
        String key = id.toString();
        RecentSoundLog.record(key);

        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.soundVolumesEnabled) return;
        Map<String, Float> overrides = cfg.soundVolumes;
        if (overrides == null || overrides.isEmpty()) return;

        Float scale = overrides.get(key);
        if (scale == null) return;
        cir.setReturnValue(SoundVolumes.attenuate(cir.getReturnValueF(), scale));
    }
}
