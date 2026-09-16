package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Row that displays and rebinds a single Aurora keybind. Stores the bound
 * key as a GLFW int (see {@link AuroraKey#UNBOUND}). Click the right-
 * hand pill to enter listen mode, then any next key press is captured.
 *
 * <p>Special keys while listening:
 * <ul>
 *   <li>{@code ESC} — clear the binding (sets to {@link AuroraKey#UNBOUND}).</li>
 *   <li>{@code BACKSPACE} — same as ESC, mirrors vanilla controls UX.</li>
 * </ul>
 *
 * <p><b>Phase B pilot (2026-09-16):</b> {@link #canonicalStates()} opts one
 * production row into the state-complete capture model. The other keybind
 * rows deliberately keep their legacy behavior for pilot isolation.
 * Canonical channels overlap rather than alias one another:
 * <ul>
 *   <li>the bound value is persistent config data;</li>
 *   <li>hover is pointer-only {@link HoverAnim#symmetric(long) symmetric
 *       140 ms} motion;</li>
 *   <li>focus is the semantic child's keyboard-navigation target;</li>
 *   <li>listening is persistent modal ownership of the next accepted key,
 *       carried by the existing stained pill + explicit prompt;</li>
 *   <li>disabled is an authoritative interaction gate. Entering disabled
 *       while listening cancels capture without changing or saving the
 *       binding.</li>
 * </ul>
 * Listening still uses {@link FeatureSetting#requestFocus()} for capture
 * routing, but it is never derived from semantic focus and never drives
 * hover. Focus may therefore exist with or without listening, and listening
 * remains visible after the pointer leaves the trigger.
 *
 * <p>The activation event that enters listening is owned by the semantic
 * child and returns through Minecraft's child dispatch before capture is
 * armed for a later event. The next screen-dispatched key reaches
 * {@link #onKeyPress}; Enter/Space cannot self-bind on the activation event.
 * Pointer events are not bindable in Aurora's raw-GLFW-int vocabulary. While
 * listening, the next pointer press cancels and is consumed by
 * {@link com.aurora.client.screen.FeatureDetailScreen} before any underlying
 * control can activate.
 *
 * <p>Glass rollout: the pill renders as RAISED glass — neutral
 * {@code WINDOW_FILL} tint at rest, accent-STAINED tint while listening
 * (the active state reads through the tint, never through the lighting
 * orientation; the stained alpha follows the Background Opacity slider
 * through {@link ThemeManager#stainedTint()}'s fixed floor — single
 * application point). On decline (menu context, screenshot suppression,
 * failure) the complete flat pill returns unchanged.
 */
public class KeybindSetting extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 90;
    private static final int BTN_H = 18;

    private final IntSupplier getter;
    private final IntConsumer setter;
    private final Runnable saveAction;

    /** Canonical capture owner. Legacy rows keep their shipped local-only state. */
    private static KeybindSetting activeCapture;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * the frame in which {@link #renderGlassPass} painted the pill pre-dim
     * and whether it drew. In that frame {@link #render} paints content
     * only (or the flat pill on decline); otherwise the surface paints in
     * place — the legacy order, pixel-identical.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewPill = false;

    private int lastBtnX, lastBtnY;
    private int lastWidth = 240;
    private boolean listening = false;
    /** Legacy by default; swapped only by canonicalStates() for pilot isolation. */
    private HoverAnim hoverAnim = new HoverAnim(140L);
    private boolean canonical = false;
    private boolean focusedVisual = false;
    private SemanticActionControl interactionControl;

    /** Cached user-facing name; the bound code normally changes only on capture. */
    private int cachedKey = Integer.MIN_VALUE;
    private String cachedKeyName = "Unbound";

    public KeybindSetting(String label, IntSupplier getter, IntConsumer setter) {
        this(label, getter, setter, com.aurora.client.config.AuroraConfig::save);
    }

    /** Package-private save seam for deterministic exactly-once tests. */
    KeybindSetting(String label, IntSupplier getter, IntConsumer setter, Runnable saveAction) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.saveAction = saveAction;
    }

    @Override public KeybindSetting description(String desc) { super.description(desc); return this; }
    @Override public KeybindSetting description(Supplier<String> desc) { super.description(desc); return this; }

    /** Opts this row into the Phase B state/capture pilot. */
    public KeybindSetting canonicalStates() {
        this.canonical = true;
        this.hoverAnim = HoverAnim.symmetric(140L);
        return this;
    }

    public boolean canonical() {
        return canonical;
    }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height()     { return CONTROL_H + descriptionHeight(lastWidth); }

    /**
     * Pre-dim surface (the split's surface half): the pill's raised glass,
     * neutral at rest / accent-stained while listening — the tint state is
     * read here so it can never disagree with the content pass.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        boolean disabled = reconcileDisabledState();
        glassPassFrame = GlassSurface.frame();
        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewPill = (!canonical || !disabled)
                && GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR, listening);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        boolean disabled = reconcileDisabledState();

        // Keybind rows intentionally skip the label-hover description tooltip.
        ctx.drawString(tr, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2,
                canonical && disabled ? AuroraTheme.TEXT_DIM : AuroraTheme.TEXT_PRIMARY, false);

        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        lastBtnX = btnX;
        lastBtnY = btnY;

        boolean hover = Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);
        float hT = hoverAnim.update(hoverTarget(hover, disabled));

        if (canonical && interactionControl != null) {
            interactionControl.setBounds(btnX, btnY, BTN_W, BTN_H);
            interactionControl.updatePointer(mouseX, mouseY);
            focusedVisual = interactionControl.isFocused() && !disabled;
        }

        int fillTint   = canonical && disabled
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22)
                : AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = canonical && disabled
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x33)
                : AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, hT);
        int textColor  = canonical && disabled ? AuroraTheme.TEXT_DIM
                : AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: raised glass replaces the flat fill + outline (the glass
        // rim replaces the border — no double outline). Neutral tint at
        // rest; the ACCENT-STAINED tint while listening is the "next key
        // press will be captured" cue (the same selected-control tint the
        // segmented controls use). On decline the complete flat pill
        // (fill + outline) returns. Hover keeps the text-color cue; the
        // tint stays constant, exactly like every other glass control. If
        // the screen ran this row's glass pass this frame the surface is
        // already on screen UNDER the dim and only its result matters
        // here; otherwise (legacy frame order) it is painted in place now
        // through the shared GlassSurface helper (identical calls).
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        boolean glassOk;
        if (glassPassFrame == GlassSurface.frame()) {
            glassOk = passDrewPill;
        } else if (!canonical || !disabled) {
            glassOk = GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR, listening);
        } else {
            glassOk = false;
        }
        if (!glassOk) {
            RenderUtil.drawSquircle(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL,
                    listening ? AuroraTheme.IOS_BLUE_PRESSED : fillTint);
            RenderUtil.drawSquircleOutline(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f,
                    listening ? AuroraTheme.IOS_BLUE : borderTint);
        }

        if (canonical && focusedVisual) {
            RenderUtil.drawRoundedOutlineAA(ctx, btnX, btnY, BTN_W, BTN_H,
                    AuroraTheme.RADIUS_SMALL, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
        }

        String labelText = listening ? "> press key <" : currentKeyName();
        // Trim long names so they fit in the pill (keep at least 3 chars + ellipsis).
        labelText = AuroraFontRenderer.ellipsize(tr, labelText, BTN_W - 8, 3);
        int labelW = tr.width(labelText);
        int textCol = listening ? ThemeManager.color(ThemeToken.ON_ACCENT) : textColor;
        ctx.drawString(tr, labelText,
                btnX + (BTN_W - labelW) / 2,
                btnY + (BTN_H - tr.lineHeight) / 2 + 1,
                textCol, false);

        renderDescription(ctx, x, y + CONTROL_H, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (canonical && reconcileDisabledState()) return false;
        if (button != 0) return false;
        boolean onBtn = Widget.inBounds(mouseX, mouseY, lastBtnX, lastBtnY, BTN_W, BTN_H);
        if (!onBtn) {
            // Click outside the pill cancels listen mode without committing.
            if (listening) {
                cancelListening();
                return true;
            }
            return false;
        }
        if (canonical) {
            // A semantic host owns entry (one activation sound). The direct
            // path exists for headless/manual hosts and stays silent.
            if (interactionControl != null && interactionControl.isAvailable()) {
                return interactionControl.activateFromPointer(mouseX, mouseY, button);
            }
            if (listening) cancelListening(); else beginListening();
            return true;
        }
        // Legacy toggle path — intentionally untouched for the neighboring row.
        listening = !listening;
        if (listening) requestFocus(); else releaseFocus();
        return true;
    }

    /**
     * Closing the detail screen while listening must not leave this row
     * holding the static focus registry into the next screen — same
     * lifecycle contract as KeyListSetting.
     */
    @Override
    public void onDetailScreenClose() {
        cancelListening();
    }

    @Override
    public void onDetailScreenOpen() {
        if (canonical) cancelListening();
    }

    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (canonical && !available) cancelListening();
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!listening) return false;

        if (canonical && reconcileDisabledState()) {
            // The event reached a capture owner whose gate changed since the
            // last frame. Consume-but-inert: no write/save and no fallthrough
            // into another control.
            return true;
        }

        // ESC / BACKSPACE clears the binding. Match vanilla controls UX.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            setter.accept(AuroraKey.UNBOUND);
        } else {
            setter.accept(keyCode);
        }
        saveAction.run();
        finishListening();
        return true;
    }

    /** Canonical semantic adapter: focus/Enter/Space/narration + enabled gate. */
    @Override
    public SemanticActionControl interactionControl() {
        if (!canonical) return null;
        if (interactionControl == null) {
            interactionControl = new SemanticActionControl(SemanticAction.button(
                    Component.literal(label),
                    () -> {
                        String description = currentDescription();
                        return description != null ? Component.literal(description) : Component.empty();
                    },
                    () -> Component.literal(listening
                            ? "Waiting for key input. Current binding: " + currentKeyName()
                            : "Bound to " + currentKeyName()),
                    () -> !isDisabled(),
                    this::beginListening),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return interactionControl;
    }

    private void beginListening() {
        if (!canonical) {
            listening = true;
            requestFocus();
            return;
        }
        if (isDisabled() || listening) return;
        if (activeCapture != null && activeCapture != this) activeCapture.cancelListening();
        activeCapture = this;
        listening = true;
        requestFocus();
    }

    private void finishListening() {
        listening = false;
        if (activeCapture == this) activeCapture = null;
        releaseFocus();
    }

    private void cancelListening() {
        finishListening();
    }

    /**
     * Cancels the one canonical capture owner and reports whether the
     * pointer event was consumed. Called before detail-screen child routing.
     */
    public static boolean cancelActiveCapture() {
        KeybindSetting owner = activeCapture;
        if (owner == null || !owner.listening) return false;
        owner.cancelListening();
        return true;
    }

    /** Disabled can change asynchronously with respect to capture events. */
    private boolean reconcileDisabledState() {
        boolean disabled = isDisabled();
        if (canonical && disabled && listening) cancelListening();
        return disabled;
    }

    boolean hoverTarget(boolean pointerOver, boolean disabled) {
        if (canonical) return pointerOver && !disabled;
        return pointerOver || listening;
    }

    private String currentKeyName() {
        int key = getter.getAsInt();
        if (key != cachedKey) {
            cachedKey = key;
            cachedKeyName = keyName(key);
        }
        return cachedKeyName;
    }

    /** Friendly name for a GLFW key code. Falls back to the raw int. */
    public static String keyName(int glfwKey) {
        if (glfwKey == AuroraKey.UNBOUND) return "Unbound";
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString();
        } catch (Exception e) {
            return "Key " + glfwKey;
        }
    }

    // Package-private state probes for focused tests and the runtime harness.
    HoverAnim hoverAnimator() { return hoverAnim; }
    boolean listeningState() { return listening; }
    boolean focusedVisualState() { return focusedVisual; }
    boolean reconcileStateForTest() { return reconcileDisabledState(); }
}
