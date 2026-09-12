package com.aurora.client.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invokes {@code Screen.renderPanorama} — protected, so unreachable from
 * {@code GlassSurface.renderMenuPanorama}, the shared no-world backdrop
 * helper that generalizes the title screen's panorama-plus-declaration to
 * any screen (the declaring screen's class could call it on itself, but the
 * shared helper cannot). The draw is an eager CubeMap pass straight into the
 * main render target's color texture — exactly the property the menu-backdrop
 * declaration certifies — so this accessor adds no rendering semantics of
 * its own; it only widens the call.
 */
@Mixin(Screen.class)
public interface ScreenPanoramaAccessor {

    @Invoker("renderPanorama")
    void aurora$invokeRenderPanorama(GuiGraphics graphics, float partialTick);
}
