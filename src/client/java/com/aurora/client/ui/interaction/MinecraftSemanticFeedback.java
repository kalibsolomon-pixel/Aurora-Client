package com.aurora.client.ui.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;

/** Minecraft adapter for the semantic feedback vocabulary. */
public final class MinecraftSemanticFeedback implements SemanticFeedback {
    public static final MinecraftSemanticFeedback INSTANCE = new MinecraftSemanticFeedback();

    private MinecraftSemanticFeedback() {
    }

    @Override
    public void play(SemanticSound sound) {
        if (sound != SemanticSound.ACTIVATION) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return; // headless (unit tests) — no sound manager exists
        AbstractWidget.playButtonClickSound(client.getSoundManager());
    }
}
