package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GlintRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only Alternative Glint uses the mask pipeline; additive and disabled paths keep vanilla types. */
@Mixin(RenderTypes.class)
public abstract class GlintRenderTypesMixin {
    @Inject(method = "glint", at = @At("HEAD"), cancellable = true)
    private static void aurora$item(CallbackInfoReturnable<RenderType> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.glintColorEnabled && cfg.glintColorAlternative) cir.setReturnValue(GlintRenderTypes.item());
    }
    @Inject(method = "glintTranslucent", at = @At("HEAD"), cancellable = true)
    private static void aurora$translucent(CallbackInfoReturnable<RenderType> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.glintColorEnabled && cfg.glintColorAlternative) cir.setReturnValue(GlintRenderTypes.translucent());
    }
    @Inject(method = "entityGlint", at = @At("HEAD"), cancellable = true)
    private static void aurora$entity(CallbackInfoReturnable<RenderType> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.glintColorEnabled && cfg.glintColorAlternative) cir.setReturnValue(GlintRenderTypes.entity());
    }
    @Inject(method = "armorEntityGlint", at = @At("HEAD"), cancellable = true)
    private static void aurora$armor(CallbackInfoReturnable<RenderType> cir) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.glintColorEnabled && cfg.glintColorAlternative && cfg.glintColorTintArmor)
            cir.setReturnValue(GlintRenderTypes.armor());
    }
}
