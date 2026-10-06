package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared unified Button painting on Aurora's two customized vanilla selection screens.
 * Vanilla retains activation, focus traversal, narration and click sounds. Input
 * hooks only observe accepted presses and arm the shared animation; only painting
 * is cancelled. One painter lives with each vanilla widget and reuses its label.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    /** Shared CSS motion, one long-lived painter per vanilla widget. */
    @Unique private Button aurora$painter;

    /**
     * Press-observation state: the start stamp feeds the shared timeline
     * (rendering only), and the counter is the deterministic oracle that
     * exactly one observation fires per accepted activation (the vanilla
     * sound's own funnel — the mixin itself plays zero sounds).
     */
    @Unique private long aurora$pressStartMs = -1L;
    @Unique private int aurora$pressCount = 0;

    @Unique
    private boolean aurora$shouldApplyStyle() {
        if (!AuroraConfig.get().customTitleScreen) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return false;
        if (!(mc.screen instanceof JoinMultiplayerScreen || mc.screen instanceof SelectWorldScreen)) return false;

        // Only the standard vanilla button — exactly what Button.builder()
        // constructs. On 1.21.11 that is the inner subclass Button.Plain
        // (Builder.build() news Button.Plain; Button itself is abstract), so
        // the former getClass() == Button.class gate never matched anything
        // and this theming — and the press-squash, gated by the same check —
        // was inert for its entire life (found + fixed 2026-09-12). Matching
        // Button.Plain exactly keeps ImageButton, LockIconButton and any
        // other Button subclass (present or future) out: they draw their own
        // sprites/geometry and must not take this painter, and a future
        // vanilla button fails closed (stays vanilla) rather than being
        // silently restyled. A getSuperclass() == Button.class check was
        // rejected for exactly that reason — ImageButton extends Button
        // directly and would match it.
        // Button here is Aurora's shared component; the vanilla class is
        // qualified at the gate so both can coexist in this file.
        return ((Object) this).getClass() == net.minecraft.client.gui.components.Button.Plain.class;
    }

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void aurora$renderCustomWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (aurora$shouldApplyStyle()) {
            AbstractButton self = (AbstractButton) (Object) this;
            // Pointer-only hover target, gated by vanilla's authoritative
            // active flag — disabled buttons have no hover target (the
            // shared Button's contract; an in-flight hover eases back to
            // rest on the enable→disable transition).
            // The whole visual — press-scale pose, token radius, colors,
            // focus hairline, centered label — is the shared painter's.
            if (aurora$painter == null) aurora$painter = new Button(self.getMessage(), () -> {});
            aurora$painter.label(self.getMessage()).focused(self.isFocused()).disabled(!self.active);
            aurora$painter.renderOverlay(ctx,self.getX(),self.getY(),self.getWidth(),self.getHeight(),mouseX,mouseY);
            ci.cancel();
        }
    }

    /**
     * Press observation, pointer path (mouseClicked → playDownSound +
     * onClick in the vanilla bytecode). HEAD, never cancellable: vanilla's
     * own click sound and onPress proceed untouched.
     */
    @Inject(method = "onClick", at = @At("HEAD"))
    private void aurora$observePointerPress(MouseButtonEvent ev, boolean dbl, CallbackInfo ci) {
        if (aurora$shouldApplyStyle()) {
            aurora$pressStartMs = System.currentTimeMillis();
            aurora$pressCount++;
            if (aurora$painter != null) aurora$painter.triggerPressAnimation();
        }
    }

    /**
     * Press observation, keyboard path (keyPressed's selection branch →
     * playDownSound + onPress; onClick is NOT called on that path). The
     * gate mirrors vanilla's own selection check so a non-selection key or
     * an inactive button never arms the animation. keyPressed returns
     * boolean, so the observation hook carries a CallbackInfoReturnable —
     * still never set to a value (vanilla's routing decides).
     */
    @Inject(method = "keyPressed", at = @At("HEAD"))
    private void aurora$observeKeyboardPress(KeyEvent ev,
                                             org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> ci) {
        if (aurora$shouldApplyStyle() && ev.isSelection()
                && ((AbstractButton) (Object) this).isActive()) {
            aurora$pressStartMs = System.currentTimeMillis();
            aurora$pressCount++;
            if (aurora$painter != null) aurora$painter.triggerPressAnimation();
        }
    }
}
