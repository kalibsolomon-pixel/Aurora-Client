package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.GuiElementOrder;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Comparator;

/**
 * GUI Layering (ADR 0009): GUI elements sort with {@link GuiElementOrder},
 * the same order as vanilla's comparator without its boxing. A comparator
 * another mod put in its place is left alone.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererElementOrderMixin {
    @Shadow @Final private static Comparator<GuiElementRenderState> ELEMENT_SORT_COMPARATOR;

    @ModifyArg(method = "prepare", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;sortElements(Ljava/util/Comparator;)V"))
    private Comparator<GuiElementRenderState> aurora$elementOrder(Comparator<GuiElementRenderState> order) {
        return order == ELEMENT_SORT_COMPARATOR && AuroraConfig.get().perfGuiLayering ? GuiElementOrder.INSTANCE : order;
    }
}
