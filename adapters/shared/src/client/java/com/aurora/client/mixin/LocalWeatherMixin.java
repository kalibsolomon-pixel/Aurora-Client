package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.PackTweaksMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side local weather override (Pack Tweaks, off by default).
 * Only affects {@link ClientLevel} rendering — integrated servers and
 * server-side weather mechanics are untouched.
 */
@Mixin(Level.class)
public abstract class LocalWeatherMixin {

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void aurora$overrideRainLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.localWeatherEnabled) return;
        cir.setReturnValue(PackTweaksMath.rainLevel(cfg.localWeatherMode));
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void aurora$overrideThunderLevel(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.localWeatherEnabled) return;
        cir.setReturnValue(PackTweaksMath.thunderLevel(cfg.localWeatherMode));
    }
}
