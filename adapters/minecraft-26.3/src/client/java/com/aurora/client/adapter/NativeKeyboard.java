package com.aurora.client.adapter;

import com.mojang.blaze3d.platform.InputConstants;

/** Native keyboard values at the host; SDL physical identifiers in config/profiles. */
public final class NativeKeyboard {
    private NativeKeyboard() {}
    public static int toStored(int key) { return key; }
    public static InputConstants.Type type() { return InputConstants.Type.KEYBOARD; }
    public static InputConstants.Key key(int key) {
        return key < 0 ? InputConstants.UNKNOWN : type().getOrCreate(key);
    }
}
