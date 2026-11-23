package com.aurora.client.mixin;

import com.aurora.client.util.VanillaKeyCombos;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies Aurora's combo modifier resolution to Minecraft 26.3's raw-key
 * {@link KeyMapping#matches(InputConstants.Key)} overload, which
 * {@code Minecraft.handleGlobalKeyPress} uses for fullscreen, screenshot,
 * and friends shortcuts both in-world and while a screen is open.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingGlobalMatchMixin {

    @Inject(
            method = "matches(Lcom/mojang/blaze3d/platform/InputConstants$Key;)Z",
            at = @At("RETURN"),
            cancellable = true
    )
    private void aurora$matchGlobalCombo(InputConstants.Key key, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !VanillaKeyCombos.matchesWithCombos((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
