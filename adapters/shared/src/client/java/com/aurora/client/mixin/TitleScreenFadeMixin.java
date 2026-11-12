package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hide Title Fade (ADR 0009): the vanilla title screen shows its buttons,
 * logo and splash at once instead of fading them in over two seconds. Every
 * other constructor delegates to this one. Aurora's own title screen has no
 * fade, so this only matters with Custom Title off.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenFadeMixin {
    @Shadow private boolean fading;

    @Inject(method = "<init>(ZLnet/minecraft/client/gui/components/LogoRenderer;)V", at = @At("RETURN"))
    private void aurora$noFade(boolean fading, @Nullable LogoRenderer logoRenderer, CallbackInfo ci) {
        if (AuroraConfig.get().perfHideTitleFade) this.fading = false;
    }
}
