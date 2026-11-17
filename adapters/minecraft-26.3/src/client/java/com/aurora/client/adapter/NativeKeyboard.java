package com.aurora.client.adapter;

import com.mojang.blaze3d.platform.InputConstants;

/** Native keyboard values at the host; SDL physical identifiers in config/profiles. */
public final class NativeKeyboard {
    private NativeKeyboard() {}
    /** SDL editing shortcuts use logical keycodes, not InputConstants' physical scancodes. */
    public static boolean isUndoKey(net.minecraft.client.input.KeyEvent event) { return event.shortcutKey() == 'z'; }
    public static boolean isRedoKey(net.minecraft.client.input.KeyEvent event) { return event.shortcutKey() == 'y'; }
    public static int toStored(int key) { return key; }
    public static InputConstants.Type type() { return InputConstants.Type.KEYBOARD; }
    public static InputConstants.Key key(int key) {
        return key < 0 ? InputConstants.UNKNOWN : type().getOrCreate(key);
    }
}
