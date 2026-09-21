package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Button;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

/**
 * Phase C-6: Aurora painting/motion ONLY on the vanilla button family of the
 * customized selection screens — vanilla {@code AbstractButton}/{@code Button}
 * keeps every interaction semantic (pointer/Enter/Space activation, disabled
 * authority, focus traversal, narration, and the activation click sound).
 *
 * <p><b>Ownership boundary (the C-6 rule):</b> this mixin cancels only
 * {@code renderWidget} (pixels) and never intercepts an input event. The two
 * @Inject HEAD hooks on {@code onClick}/{@code keyPressed} are pure
 * OBSERVATIONS — they arm the press animation and never cancel, consume,
 * call {@code onPress}, play a sound, or alter active/focused state. The
 * vanilla activation funnel (verified in the 1.21.11 bytecode:
 * {@code mouseClicked → playDownSound + onClick} and
 * {@code keyPressed → playDownSound + onPress}) fires exactly once per
 * accepted activation on each input path, so arming at both observation
 * sites — which are disjoint by input path — yields exactly one arm per
 * activation, keyboard included (the old onClick-only observation never
 * animated a keyboard press).
 *
 * <p><b>Shared visuals (no second implementation):</b> the surface, colors,
 * radius, focus hairline, label centering, and the press-scale timeline all
 * come from the canonical shared Button — {@link Button#paintFlatSecondary}
 * (a pure visual painter) and {@link Button#pressScaleAt} (the shared
 * 90 ms/180 ms timeline). Hover is the §8.3 canonical
 * {@link HoverAnim#symmetric(long) symmetric 140 ms} — pointer-only
 * ({@code active && isHovered()}, never {@code isHovered() || isFocused()};
 * disabled has no hover target, exactly like the shared Button), so keyboard
 * focus is carried by the Button-family hairline alone. Vanilla
 * {@code AbstractWidget.render} sets {@code isHovered} without consulting
 * {@code active} (bytecode), so the explicit {@code active} gate is
 * required for the canonical disabled behavior.
 *
 * <p><b>Lifecycle:</b> all animation state is per-widget @Unique instance
 * fields — a button's hover/press state dies with the widget on screen
 * close/reinit; there is no static map and nothing leaks across screens.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    /** §8.3 canonical hover — one long-lived animator per widget (C-6). */
    @Unique private final HoverAnim aurora$hover = HoverAnim.symmetric(140L);

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
            Font tr = Minecraft.getInstance().font;
            // Pointer-only hover target, gated by vanilla's authoritative
            // active flag — disabled buttons have no hover target (the
            // shared Button's contract; an in-flight hover eases back to
            // rest on the enable→disable transition).
            float hoverT = aurora$hover.update(self.active && self.isHovered());
            // The whole visual — press-scale pose, token radius, colors,
            // focus hairline, centered label — is the shared painter's.
            Button.paintFlatSecondary(ctx, tr, self.getMessage(),
                    self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                    hoverT, Button.pressScaleAt(aurora$pressStartMs),
                    self.isFocused(), self.active);
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
        }
    }
}
