package com.aurora.client.adapter;

import com.mojang.blaze3d.platform.InputConstants;

/** Native keyboard values at the host; SDL physical identifiers in config/profiles. */
public final class NativeKeyboard {
    private NativeKeyboard() {}
    /** Match the key representation used by vanilla's editing shortcuts. */
    public static boolean isUndoKey(net.minecraft.client.input.KeyEvent event) { return event.key() == InputConstants.KEY_Z; }
    public static boolean isRedoKey(net.minecraft.client.input.KeyEvent event) { return event.key() == InputConstants.KEY_Y; }
    public static int toStored(int key) { return com.aurora.client.config.KeybindMigrator.fromGlfw(key); }
    public static InputConstants.Type type() { return InputConstants.Type.KEYSYM; }
    public static InputConstants.Key key(int key) {
        return key < 0 ? InputConstants.UNKNOWN : type().getOrCreate(com.aurora.client.config.KeyboardCodes.toGlfw(key));
    }
}
