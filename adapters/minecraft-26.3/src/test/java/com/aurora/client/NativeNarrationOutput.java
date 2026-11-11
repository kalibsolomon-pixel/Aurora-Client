package com.aurora.client;

/** Native narration test fixture; assertions remain shared. */
public abstract class NativeNarrationOutput implements net.minecraft.client.gui.narration.NarrationElementOutput {

    @Override public net.minecraft.client.gui.narration.NarrationTrigger narrationTrigger() {
        return net.minecraft.client.gui.narration.NarrationTrigger.KEYBOARD;
    }
}
