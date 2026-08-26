package com.aurora.client.mixin;

import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screenshot interlock for the glass panel pipeline. Every vanilla
 * screenshot entry point ({@code Screenshot.grab} — the F2 path — and
 * {@code Screenshot.takeScreenshot}) stamps a suppression window on
 * {@link BlurPanelRenderer#noteScreenshotGrab()} at HEAD, before any of
 * vanilla's grab machinery runs ({@code CommandEncoder.copyTextureToBuffer}
 * plus async buffer reads), so every glass {@code renderPanel} call in the
 * affected window declines and callers draw their opaque fallback instead.
 *
 * <p>Handler takes only the CallbackInfo so the same name-only
 * {@code method = {"grab", "takeScreenshot"}} matcher applies to every
 * overload regardless of parameters; all targets are static, hence the
 * static handler.
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotMixin {

    @Inject(method = {"grab", "takeScreenshot"}, at = @At("HEAD"))
    private static void aurora$suppressGlassDuringScreenshot(CallbackInfo ci) {
        BlurPanelRenderer.noteScreenshotGrab();
    }
}
