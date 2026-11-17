package com.aurora.client.ui.text;

import com.aurora.client.adapter.NativeKeyboard;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise each selected native adapter against its actual Minecraft KeyEvent implementation. */
class EditShortcutTest {
    private static boolean sdl() {
        return System.getProperty("aurora.adapterRoot").endsWith("minecraft-26.3");
    }

    @Test void recognizesUndoAndRedoInTheNativeKeyRepresentation() {
        var z = new KeyEvent(InputConstants.KEY_Z, sdl() ? 'z' : 0, 0);
        var y = new KeyEvent(InputConstants.KEY_Y, sdl() ? 'y' : 0, 0);
        assertTrue(NativeKeyboard.isUndoKey(z));
        assertFalse(NativeKeyboard.isRedoKey(z));
        assertTrue(NativeKeyboard.isRedoKey(y));
        assertFalse(NativeKeyboard.isUndoKey(y));
    }

    @Test void sdlUsesLogicalShortcutRatherThanPhysicalScancode() {
        var swapped = new KeyEvent(InputConstants.KEY_Y, 'z', 0);
        assertEquals(sdl(), NativeKeyboard.isUndoKey(swapped));
        assertEquals(!sdl(), NativeKeyboard.isRedoKey(swapped));
    }

    @Test void unrelatedKeysAreNotEditingHistoryShortcuts() {
        var a = new KeyEvent(InputConstants.KEY_A, sdl() ? 'a' : 0, 0);
        assertFalse(NativeKeyboard.isUndoKey(a));
        assertFalse(NativeKeyboard.isRedoKey(a));
    }
}
