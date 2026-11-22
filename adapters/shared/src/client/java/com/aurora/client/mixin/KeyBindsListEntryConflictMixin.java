package com.aurora.client.mixin;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.util.KeybindConflictLookup;
import com.aurora.client.util.KeybindConflicts;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Surfaces keybind conflicts in vanilla's Controls screen that vanilla's own
 * {@link KeyBindsList.KeyEntry#refreshEntry()} misses: profile hotkeys (which
 * are not vanilla {@link KeyMapping}s) and default-vs-default collisions
 * involving an Aurora binding.
 *
 * <p>Harmless alone (ADR 0004): if this mixin cannot apply, vanilla Controls
 * keeps its standard conflict warnings unchanged.
 */
@Mixin(KeyBindsList.KeyEntry.class)
public abstract class KeyBindsListEntryConflictMixin {
    @Shadow @Final private KeyMapping key;
    @Shadow @Final private Button changeButton;
    @Shadow private boolean hasCollision;

    @Inject(method = "refreshEntry", at = @At("TAIL"))
    private void aurora$appendExtraConflicts(CallbackInfo ci) {
        List<String> extra = KeybindConflictLookup.extraVanillaControlsConflicts(this.key);
        if (extra.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        List<String> vanillaLabels = new ArrayList<>();
        if (mc != null && mc.options != null && mc.options.keyMappings != null && !this.key.isUnbound()) {
            for (KeyMapping other : mc.options.keyMappings) {
                if (other != this.key && this.key.same(other) && (!other.isDefault() || !this.key.isDefault())) {
                    vanillaLabels.add(Component.translatable(other.getName()).getString());
                }
            }
        }
        List<String> all = KeybindConflicts.mergeLabels(vanillaLabels, extra);
        if (all.isEmpty()) return;

        boolean wasColliding = this.hasCollision;
        this.hasCollision = true;

        MutableComponent joined = Component.empty();
        for (int i = 0; i < all.size(); i++) {
            if (i > 0) joined.append(", ");
            joined.append(Component.literal(all.get(i)));
        }
        this.changeButton.setTooltip(Tooltip.create(
                Component.translatable("controls.keybinds.duplicateKeybinds", joined)));

        if (!wasColliding) {
            boolean selected = mc != null
                    && NativeUi.screen(mc) instanceof KeyBindsScreen screen
                    && screen.selectedKey == this.key;
            Component msg = this.key.getTranslatedKeyMessage();
            msg = Component.literal("[ ").append(msg.copy().withStyle(ChatFormatting.WHITE)).append(" ]")
                    .withStyle(ChatFormatting.YELLOW);
            if (selected) {
                msg = Component.literal("> ")
                        .append(msg.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <")
                        .withStyle(ChatFormatting.YELLOW);
            }
            this.changeButton.setMessage(msg);
        }
    }
}
