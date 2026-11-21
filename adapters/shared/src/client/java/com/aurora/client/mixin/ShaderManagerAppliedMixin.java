package com.aurora.client.mixin;

import com.aurora.client.ui.util.ShaderLoad;
import net.minecraft.client.renderer.ShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Says when the Shader Loader has applied, so Aurora's shader users (SDF text, shader-drawn shapes) do not judge a
 * compile that ran against the vanilla-only startup resources. Harmless alone: it only sets a flag. A failed apply
 * throws before the return, so the flag means shaders were loaded.
 */
@Mixin(ShaderManager.class)
public abstract class ShaderManagerAppliedMixin {
    @Inject(method = "apply", at = @At("RETURN"), require = 1)
    private void aurora$shadersApplied(CallbackInfo ci) { ShaderLoad.markApplied(); }
}
