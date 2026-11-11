package com.aurora.client.mixin;

import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the glass captures queued while the GUI was extracted (26.1).
 *
 * <p>26.1 extracts the whole GUI before rendering any of it, so a capture taken
 * during extraction reads the previous frame. By the time {@code GuiRenderer}
 * calls {@code prepare()}, this frame's world (or the title panorama, which it
 * draws just before) is in the main target and no GUI element has been drawn:
 * the same backdrop 1.21.11's captures saw.
 */
@Mixin(GuiRenderer.class)
public abstract class GuiRendererGlassMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void aurora$runGlassCaptures(CallbackInfo ci) {
        BlurPanelRenderer.runPendingCaptures();
    }
}
