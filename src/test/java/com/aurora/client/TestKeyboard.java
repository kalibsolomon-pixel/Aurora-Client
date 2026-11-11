package com.aurora.client;

import com.aurora.client.config.KeybindMigrator;

/** Distinguish a native input event from the portable persisted key in assertions. */
public final class TestKeyboard {
    private TestKeyboard() {}
    public static int stored(int nativeKey) {
        return SourceTree.ADAPTER.endsWith("26.3") ? nativeKey : KeybindMigrator.fromGlfw(nativeKey);
    }
    public static int nativeKey(int storedKey) {
        return SourceTree.ADAPTER.endsWith("26.3") ? storedKey : com.aurora.client.config.KeyboardCodes.toGlfw(storedKey);
    }
}
