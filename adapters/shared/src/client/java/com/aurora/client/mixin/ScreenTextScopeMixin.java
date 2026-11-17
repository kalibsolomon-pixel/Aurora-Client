package com.aurora.client.mixin;

import com.aurora.client.ui.util.AuroraFontRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the Aurora window text scope while an Aurora window renders its own
 * content, so "Aurora UI Only" styles exactly that window's text — not every
 * text in a frame with a window open, and not the HUD (owner ruling
 * 2026-11-17).
 *
 * <p>{@code extractRenderStateWithTooltipAndSubtitles} is the one
 * {@code public final} entry point every supported line calls once per frame
 * on the open screen (26.1.2: {@code GameRenderer}; 26.2/26.3: {@code Gui}),
 * and subclasses cannot override it, so the scope wraps the whole of the
 * window's own drawing — widgets, dropdown overlays, tooltips — and nothing
 * else in the frame. Vanilla screens take the same path and simply do not
 * open the scope.
 *
 * <p>Fail-soft (ADR 0004): all this mixin does is maintain a counter that is
 * read only while {@code textRendererMode == AURORA_ONLY}, so it is harmless
 * alone and gates on no switch. If it cannot apply, AuroraFontRenderer falls
 * back to the coarser open-screen check, and FeatureHealth reports it on
 * Text &amp; Fonts through the catalog binding.
 *
 * <p>Exception safety: RETURN is not reached when the extraction throws, so
 * {@code AuroraFontRenderer.beginTextFrame()} (called from
 * {@code GlassSurface.beginFrame()}) resets any scope left open at the frame
 * boundary.
 */
@Mixin(Screen.class)
public abstract class ScreenTextScopeMixin {

    /** Whether this screen's current extraction opened the scope. The method is not re-entrant on one screen instance. */
    @Unique private boolean aurora$scoped;

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"), require = 0)
    private void aurora$enterTextScope(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                       CallbackInfo ci) {
        aurora$scoped = AuroraFontRenderer.enterWindowScope((Screen) (Object) this);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("RETURN"), require = 0)
    private void aurora$exitTextScope(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        if (aurora$scoped) {
            aurora$scoped = false;
            AuroraFontRenderer.exitWindowScope();
        }
    }
}
