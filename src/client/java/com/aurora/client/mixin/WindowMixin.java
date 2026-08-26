package com.aurora.client.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Historically attempted to scale {@code Window#getWidth/getHeight} down to
 * the FSR render ratio, on the assumption Minecraft would then render the
 * level at that lower resolution. In practice nothing in Minecraft's render
 * path consumes those values for the level framebuffer size (they only drive
 * GUI/HUD layout and input scaling), so this mixin produced no visible effect
 * and could distort the GUI.
 *
 * <p>It has been intentionally reduced to a no-op. Real low-resolution
 * capture for FSR 1 is performed by {@code RenderTargetMixin} redirecting
 * {@code RenderTarget.bindWrite} into Aurora's low-res FBO during
 * {@code GameRenderer#renderLevel}.
 *
 * <p>The empty class is kept so the mixin config entry remains valid; feel
 * free to remove both once {@code aurora.mixins.json} is updated.
 */
@Mixin(Window.class)
public class WindowMixin {
    // intentionally empty — see class Javadoc.
}