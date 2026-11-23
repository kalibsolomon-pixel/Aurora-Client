package com.aurora.client.mixin;

import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.KeyCombo;
import com.aurora.client.util.KeyComboHolder;
import com.aurora.client.util.VanillaKeyCombos;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

/**
 * Ctrl / Shift / Alt combos on Minecraft's key bindings (owner request
 * 2026-11-12; {@link KeyCombo}). Each mapping gains a modifier set; a key
 * press reaches only the mappings that win on that key — the most specific
 * whose modifiers are all held — so P and Ctrl + P are separate bindings and
 * a plain key keeps working while an unrelated modifier is held.
 *
 * <p>Harmless alone: with no modifiers anywhere ({@link KeyCombo.Registry#hasCombos})
 * every injection returns without changing anything, so a partly applied
 * mixin can only make a combo behave like its plain key.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingComboMixin implements KeyComboHolder {
    @Shadow @Final private static Map<InputConstants.Key, List<KeyMapping>> MAP;
    @Shadow @Final private static Map<String, KeyMapping> ALL;
    @Shadow private int clickCount;

    @Unique private int aurora$modifiers;

    @Override public int aurora$modifiers() { return aurora$modifiers; }
    @Override public void aurora$setModifiers(int modifiers) { aurora$modifiers = modifiers & KeyCombo.MODIFIER_MASK; }

    /** A new key starts plain: vanilla rebinds and resets clear the modifiers; Aurora sets them after. */
    @Inject(method = "setKey", at = @At("HEAD"))
    private void aurora$plainOnRebind(InputConstants.Key key, CallbackInfo ci) {
        aurora$modifiers = 0;
    }

    /** P and Ctrl + P are different bindings, not a conflict. */
    @Inject(method = "same", at = @At("RETURN"), cancellable = true)
    private void aurora$sameCombo(KeyMapping that, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && VanillaKeyCombos.modifiers(that) != aurora$modifiers) cir.setReturnValue(false);
    }

    /** The default binding has no modifiers. */
    @Inject(method = "isDefault", at = @At("RETURN"), cancellable = true)
    private void aurora$defaultIsPlain(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && aurora$modifiers != 0) cir.setReturnValue(false);
    }

    /** "Ctrl + P"; "Ctrl + …" on the Controls row still waiting for its second key. */
    @Inject(method = "getTranslatedKeyMessage", at = @At("RETURN"), cancellable = true)
    private void aurora$comboName(CallbackInfoReturnable<Component> cir) {
        if ((Object) this == VanillaKeyCombos.capturing && VanillaKeyCombos.CAPTURE.pending() != 0) {
            cir.setReturnValue(Component.literal(KeyCombo.prefix(VanillaKeyCombos.CAPTURE.pending()) + "…"));
        } else if (aurora$modifiers != 0) {
            cir.setReturnValue(Component.literal(KeyCombo.prefix(aurora$modifiers)).append(cir.getReturnValue()));
        }
    }

    @Inject(method = "click", at = @At("HEAD"), cancellable = true)
    private static void aurora$clickWinners(InputConstants.Key key, CallbackInfo ci) {
        // A modifier that may be starting a combo clicks nothing until it is let go.
        if (KeyCombo.Deferral.waiting(VanillaKeyCombos.storedKey(key))) {
            ci.cancel();
            return;
        }
        if (aurora$dispatch(key, true, false)) ci.cancel();
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void aurora$pressWinners(InputConstants.Key key, boolean state, CallbackInfo ci) {
        if (!state) {
            // A release reaches every mapping on the key (vanilla), so a combo
            // still lets go when its modifier came up first. A waiting modifier
            // let go with no combo taps instead.
            if (aurora$tap(key)) ci.cancel();
            return;
        }
        int stored = VanillaKeyCombos.storedKey(key);
        if (KeyCombo.Deferral.press(stored, System.currentTimeMillis())) {
            ci.cancel();
            return;
        }
        if (stored >= 0 && KeyCombo.Registry.hasCombos()) {
            KeyCombo.Deferral.consume(KeyCombo.comboBits(stored, AuroraKey.heldModifiers()));
        }
        if (aurora$dispatch(key, false, true)) ci.cancel();
    }

    /** A screen closing re-reads held keys: a combo whose modifiers are not held is not down. */
    @Inject(method = "setAll", at = @At("TAIL"))
    private static void aurora$recheckHeld(CallbackInfo ci) {
        if (!KeyCombo.Registry.hasCombos()) return;
        // A modifier held across the screen started nothing: it taps nothing when let go.
        KeyCombo.Deferral.cancelHeld();
        int held = AuroraKey.heldModifiers();
        for (KeyMapping mapping : ALL.values()) {
            if (!mapping.isDown()) continue;
            int binding = VanillaKeyCombos.binding(mapping);
            if (binding < 0) continue;
            int key = KeyCombo.key(binding);
            boolean waiting = KeyCombo.Deferral.defers(key) && !KeyCombo.Deferral.held(key);
            if (waiting || !KeyCombo.resolves(binding, held)) mapping.setDown(false);
        }
    }

    /**
     * Screens and Minecraft's own shortcuts (screenshot, drop, inventory) match key
     * events directly, which ignores modifiers: with an F2 combo, plain F2 still
     * took a screenshot. A match now also needs the binding to win.
     */
    @Inject(method = "matches(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("RETURN"), cancellable = true)
    private void aurora$matchCombo(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && !VanillaKeyCombos.matchesWithCombos((KeyMapping) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    /** Gives a waiting modifier's winners the press they skipped, if it taps. */
    @Unique
    private static boolean aurora$tap(InputConstants.Key key) {
        int stored = VanillaKeyCombos.storedKey(key);
        if (stored < 0) return false;
        boolean inGame = com.aurora.client.adapter.NativeUi.screen(net.minecraft.client.Minecraft.getInstance()) == null;
        if (!KeyCombo.Deferral.release(stored, inGame)) return false;
        List<KeyMapping> mappings = MAP.get(key);
        if (mappings == null) return true;
        int held = AuroraKey.heldModifiers();
        for (KeyMapping mapping : mappings) {
            if (!KeyCombo.resolves(KeyCombo.of(stored, VanillaKeyCombos.modifiers(mapping)), held)) continue;
            VanillaKeyCombos.holdForOneTick(mapping);
            ((KeyMappingComboMixin) (Object) mapping).clickCount++;
        }
        return true;
    }

    /**
     * Applies vanilla's per-mapping operation to the winners on {@code key}
     * only. Returns false — vanilla runs untouched — when there are no combos
     * or the key is not a keyboard key.
     */
    @Unique
    private static boolean aurora$dispatch(InputConstants.Key key, boolean click, boolean down) {
        if (!KeyCombo.Registry.hasCombos()) return false;
        int stored = VanillaKeyCombos.storedKey(key);
        if (stored < 0) return false;
        List<KeyMapping> mappings = MAP.get(key);
        if (mappings == null || mappings.isEmpty()) return true;
        int held = AuroraKey.heldModifiers();
        for (KeyMapping mapping : mappings) {
            if (!KeyCombo.resolves(KeyCombo.of(stored, VanillaKeyCombos.modifiers(mapping)), held)) continue;
            if (click) ((KeyMappingComboMixin) (Object) mapping).clickCount++;
            else mapping.setDown(down);
        }
        return true;
    }
}
