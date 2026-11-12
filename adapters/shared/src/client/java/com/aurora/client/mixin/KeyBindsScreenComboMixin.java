package com.aurora.client.mixin;

import com.aurora.client.util.KeyCombo;
import com.aurora.client.util.VanillaKeyCombos;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Combos in Minecraft's Controls screen (owner request 2026-11-12;
 * {@link KeyCombo}): while a row waits for a key, holding Ctrl, Shift or Alt
 * shows "Ctrl + …" and waits for a second key; releasing the modifier alone
 * binds it by itself. A plain key, Esc and mouse buttons take vanilla's own
 * path unchanged.
 */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenComboMixin extends OptionsSubScreen {
    @Shadow public KeyMapping selectedKey;
    @Shadow public long lastKeySelection;
    @Shadow private KeyBindsList keyBindsList;

    protected KeyBindsScreenComboMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void aurora$comboPress(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (selectedKey == null) return;
        if (VanillaKeyCombos.capturing != selectedKey) {
            VanillaKeyCombos.capturing = selectedKey;
            VanillaKeyCombos.CAPTURE.reset();
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            aurora$endCapture(); // vanilla clears; its setKey drops the modifiers
            return;
        }
        Integer done = VanillaKeyCombos.CAPTURE.press(com.aurora.client.adapter.NativeKeyboard.toStored(event.key()), false);
        if (done == null) {
            keyBindsList.resetMappingAndUpdateButtons(); // "Ctrl + …"
            cir.setReturnValue(true);
            return;
        }
        int modifiers = KeyCombo.modifiers(done);
        aurora$endCapture();
        if (modifiers == 0) return; // a plain key: vanilla's own path
        aurora$bind(event, modifiers);
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void aurora$mouseEndsCapture(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (selectedKey != null) aurora$endCapture(); // vanilla binds the button, plain
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // A modifier released before a second key binds the modifier itself.
        if (selectedKey != null && VanillaKeyCombos.capturing == selectedKey) {
            Integer done = VanillaKeyCombos.CAPTURE.release(com.aurora.client.adapter.NativeKeyboard.toStored(event.key()));
            if (done != null) {
                aurora$endCapture();
                aurora$bind(event, KeyCombo.modifiers(done));
                return true;
            }
        }
        return super.keyReleased(event);
    }

    @Override
    public void removed() {
        aurora$endCapture();
        super.removed();
        VanillaKeyCombos.store(); // modifiers persist beside options.txt, which keeps the plain key
    }

    /** Vanilla's finish path, with the modifiers set after its {@code setKey} (which clears them). */
    @Unique
    private void aurora$bind(KeyEvent event, int modifiers) {
        selectedKey.setKey(InputConstants.getKey(event));
        VanillaKeyCombos.setModifiers(selectedKey, modifiers);
        selectedKey = null;
        lastKeySelection = Util.getMillis();
        keyBindsList.resetMappingAndUpdateButtons();
    }

    @Unique
    private static void aurora$endCapture() {
        VanillaKeyCombos.capturing = null;
        VanillaKeyCombos.CAPTURE.reset();
    }
}
