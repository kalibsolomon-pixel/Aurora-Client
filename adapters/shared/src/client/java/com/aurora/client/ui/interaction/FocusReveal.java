package com.aurora.client.ui.interaction;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.util.OverlayLayout;
import com.aurora.client.util.SmoothScroll;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.input.KeyEvent;

public final class FocusReveal {
    public static final int MARGIN = 8;
    private FocusReveal() {}

    public static boolean navigation(KeyEvent event) {
        return switch (event.key()) {
            case InputConstants.KEY_TAB, InputConstants.KEY_UP, InputConstants.KEY_DOWN,
                    InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT -> true;
            default -> false;
        };
    }

    public static void afterNavigation(GuiEventListener before, GuiEventListener after,
                                       SmoothScroll scroll, double max, int top, int bottom) {
        if (before != after && after instanceof SemanticActionControl control && control.isScrollFocusTarget()) {
            double current = scroll.current();
            double target = OverlayLayout.scrollToReveal(scroll.target(), max,
                    control.getY() + current, control.getBottom() + current, top, bottom, MARGIN);
            if (AuroraConfig.get().reducedMotion) scroll.set(target);
            else if (target != scroll.target()) scroll.retarget(target, max);
        }
    }
}
