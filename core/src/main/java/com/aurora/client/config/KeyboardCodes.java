package com.aurora.client.config;

import java.util.Arrays;

/** Canonical SDL physical key identifiers on every supported line, including GLFW hosts. */
public final class KeyboardCodes {
    private static final int[] GLFW = new int[512];
    static {
        Arrays.fill(GLFW, -1);
        for (int key = 32; key <= 348; key++) {
            int scan = KeybindMigrator.fromGlfw(key);
            if (scan >= 0 && scan < GLFW.length) GLFW[scan] = key;
        }
    }
    private KeyboardCodes() {}
    public static int toGlfw(int scancode) {
        return scancode >= 0 && scancode < GLFW.length ? GLFW[scancode] : -1;
    }
}
