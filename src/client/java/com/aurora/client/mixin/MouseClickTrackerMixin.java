package com.aurora.client.mixin;

import com.aurora.client.feature.impl.ClickTrackerFeature;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records every MouseHandler-button DOWN edge directly from the GLFW callback,
 * regardless of frame/tick timing or whether vanilla consumed the press.
 *
 * <p>1.21.11: {@code onMouseButton(long, int, int, int)} was replaced by
 * {@code onButton(long, MouseButtonInfo, int)} where the button id is wrapped
 * in a record.
 */
@Mixin(MouseHandler.class)
public abstract class MouseClickTrackerMixin {

    @Inject(method = "onButton", at = @At("HEAD"), require = 1)
    private void aurora$trackClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) return;
        int button = info.button();
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) ClickTrackerFeature.LEFT.click();
        else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) ClickTrackerFeature.RIGHT.click();
    }
}