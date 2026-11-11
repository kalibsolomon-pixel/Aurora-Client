package com.aurora.client.hud.module;

import com.aurora.client.ui.util.AuroraFontRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import java.util.Arrays;
import java.util.Objects;

/** Fixed HUD slots retain vanilla's language-aware shaped text between frames. */
final class HudTextCache {
    private final String[] values;
    private final Component[] components;
    private Style style;

    HudTextCache(int slots) {
        values = new String[slots];
        components = new Component[slots];
    }

    Component text(int slot, String value) {
        return text(slot, value, AuroraFontRenderer.getActiveStyle());
    }

    Component text(int slot, String value, Style activeStyle) {
        if (!Objects.equals(style, activeStyle)) {
            Arrays.fill(components, null);
            style = activeStyle;
        }
        if (components[slot] == null || !value.equals(values[slot])) {
            values[slot] = value;
            var component = Component.literal(value);
            components[slot] = activeStyle == null ? component : component.withStyle(activeStyle);
        }
        return components[slot];
    }
}
