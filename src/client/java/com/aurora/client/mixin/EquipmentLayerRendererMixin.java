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
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Makes vanilla's hurt overlay flash visibly affect armor.
 *
 * <p>Vanilla calls {@code RenderTypes.armorCutoutNoCull(texture)} for each
 * armor layer. That render type does not visibly blend the
 * {@link OverlayTexture} the way the body's render type does, so the body
 * flashes red but armor stays unchanged. The fix (mirrors Animatium and
 * Lunar's HitColor): swap the render type to
 * {@link RenderTypes#entityCutoutNoCullZOffset(Identifier)} -- which is the
 * standard entity render type and correctly samples the overlay texture --
 * and pass through the entity's hurt overlay UV so the swapped pipeline
 * reuses the same red row our {@link MixinOverlayTexture} writes for the
 * body. End result: armor and body flash with the same configured color
 * and strength, automatically.
 *
 * <p>Both hooks are gated on {@code hitColorEnabled && hitColorTintArmor};
 * when off, every modifier returns its argument unchanged so vanilla
 * armor rendering is bit-for-bit identical.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class EquipmentLayerRendererMixin {

    private static final String INNER = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";
    private static final String SUBMIT_MODEL = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";
    private static final String ARMOR_CUTOUT_NO_CULL = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;";

    /**
     * Replace {@code armorCutoutNoCull} with {@code entityCutoutNoCullZOffset}
     * so the armor model is drawn through the same overlay-aware pipeline
     * used for the entity body. The Z-offset variant prevents z-fighting
     * with the body underneath.
     */
    @WrapOperation(method = INNER, at = @At(value = "INVOKE", target = ARMOR_CUTOUT_NO_CULL), require = 0)
    private RenderType aurora$swapArmorRenderType(Identifier texture, Operation<RenderType> original) {
        AuroraConfig cfg = AuroraConfig.get();
        if (cfg.hitColorEnabled && cfg.hitColorTintArmor) {
            return RenderTypes.entityCutoutNoCullZOffset(texture);
        }
        return original.call(texture);
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
               at = @At(value = "INVOKE", target = SUBMIT_MODEL),
               index = 5,
               require = 0)
    private int aurora$applyHurtOverlay(int original,
                                        @Local(argsOnly = true) Object renderState) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!(cfg.hitColorEnabled && cfg.hitColorTintArmor)) return original;
        if (!(renderState instanceof LivingEntityRenderState living)) return original;
        return OverlayTexture.pack(OverlayTexture.u(0.0F),
                                   OverlayTexture.v(living.hasRedOverlay));
    }
}
