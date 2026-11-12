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
        if (aurora$dispatch(key, true, false)) ci.cancel();
    }

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private static void aurora$pressWinners(InputConstants.Key key, boolean state, CallbackInfo ci) {
        // A release reaches every mapping on the key (vanilla), so a combo
        // still lets go when its modifier came up first.
        if (state && aurora$dispatch(key, false, true)) ci.cancel();
    }

    /** A screen closing re-reads held keys: a combo whose modifiers are not held is not down. */
    @Inject(method = "setAll", at = @At("TAIL"))
    private static void aurora$recheckHeld(CallbackInfo ci) {
        if (!KeyCombo.Registry.hasCombos()) return;
        int held = AuroraKey.heldModifiers();
        for (KeyMapping mapping : ALL.values()) {
            if (!mapping.isDown()) continue;
            int binding = VanillaKeyCombos.binding(mapping);
            if (binding >= 0 && !KeyCombo.resolves(binding, held)) mapping.setDown(false);
        }
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
