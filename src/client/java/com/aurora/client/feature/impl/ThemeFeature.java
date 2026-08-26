package com.aurora.client.feature.impl;

import com.aurora.client.feature.Feature;
import com.aurora.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;

public class ThemeFeature implements Feature {
    public static final String ID = "theme";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Theme";
    }

    @Override
    public void onRegister() {
        ThemeManager.reload();
    }

    @Override
    public void onTick(Minecraft client) {
        // Keeping colors in sync dynamically: dirty-checks the source
        // definition and only re-resolves when the theme actually changed.
        ThemeManager.sync();
    }
}
