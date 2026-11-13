package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.aurora.client.util.HitColorOverlayRows;
import com.aurora.client.util.HitColorTrimRenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Enables Aurora's hurt overlay on native armor and paletted trim materials.
 * The replacement pipelines retain vanilla per-face lighting, glint and decal depth.
 * All changes are gated by the corresponding hit-color switches.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

    private static final String INNER = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";
    private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V";
    private static final String ARMOR_CUTOUT_NO_CULL = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;";

    /** Enable the overlay sampler without replacing vanilla's armor lighting. */
    @WrapOperation(method = INNER, at = @At(value = "INVOKE", target = ARMOR_CUTOUT_NO_CULL), require = 0)
    private RenderType aurora$swapArmorRenderType(Identifier texture, Operation<RenderType> original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.hitColorEnabled && cfg.hitColorTintArmor) {
            return HitColorTrimRenderTypes.armor(texture, false);
        }
        return original.call(texture);
    }

    @WrapOperation(method = INNER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCullGlint(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType aurora$swapGlintArmorRenderType(Identifier texture, Operation<RenderType> original) {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.hitColorEnabled && cfg.hitColorTintArmor
                ? HitColorTrimRenderTypes.armor(texture, true) : original.call(texture);
    }

    /**
     * Force the overlay UV passed to {@code submitModel} to encode the
     * entity's hurt state. With the render type swapped above, this UV
     * makes the overlay sampler produce the configured hit color on top
     * of the armor texture during the hurt animation. {@code @Local} pulls
     * the entity render state out of {@code renderLayers}'s {@code Object}
     * parameter without needing a thread-local bridge.
     */
    @ModifyArg(method = INNER,
               at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 0),
               index = 5,
               require = 0)
    private int aurora$applyHurtOverlay(int original,
                                        @Local(argsOnly = true) Object renderState) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!(cfg.hitColorEnabled && cfg.hitColorTintArmor)) return original;
        if (!(renderState instanceof LivingEntityRenderState living)) return original;
        return OverlayTexture.pack(OverlayTexture.u(0.0F),
                                   HitColorOverlayRows.hurtRow(living, OverlayTexture.v(living.hasRedOverlay)));
    }

    /** Trims retain vanilla's paletted texture and UV mapping. */
    @WrapOperation(method = INNER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private RenderType aurora$swapTrimRenderType(Identifier texture, boolean decal, Operation<RenderType> original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!(cfg.hitColorEnabled && cfg.hitColorTintArmor && cfg.hitColorTintArmorTrims))
            return original.call(texture, decal);
        return HitColorTrimRenderTypes.trim(texture, decal);
    }

    /** Second submission is the trim; the separate trimmed-armor glint stays untouched. */
    @ModifyArg(method = INNER,
            at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 1),
            index = 5)
    private int aurora$applyTrimHurtOverlay(int original, @Local(argsOnly = true) Object renderState) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!(cfg.hitColorEnabled && cfg.hitColorTintArmor && cfg.hitColorTintArmorTrims)) return original;
        if (!(renderState instanceof LivingEntityRenderState living)) return original;
        return OverlayTexture.pack(OverlayTexture.u(0.0F), HitColorOverlayRows.hurtRow(living, OverlayTexture.v(living.hasRedOverlay)));
    }

}
