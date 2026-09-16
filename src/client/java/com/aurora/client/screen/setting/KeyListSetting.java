package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.module.KeystrokesModule;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Editor for a list of raw GLFW key codes (the Keystrokes overlay's
 * "Extra Keys"). One row per entry with a "−" remove button, plus an
 * "Add Key" pill that enters listen mode — the next key pressed is
 * appended. ESC and BACKSPACE cancel the capture without changing the
 * list (deliberately NOT the single-Keybind clear-to-UNBOUND rule: there
 * is no one value to clear, and cancel is the honest meaning here);
 * duplicates and the {@link KeystrokesModule#MAX_EXTRA_KEYS} cap close
 * the capture as an accepted no-op without mutating or saving.
 *
 * <p><b>Phase B pilot (2026-09-16): canonical multi-value capture
 * states.</b> This is the only production KeyList, so the canonical
 * behavior is unconditional (family-level isolation — there is no second
 * instance to leave legacy). The state model keeps the channels the
 * legacy compound control conflated apart:
 * <ul>
 *   <li>the list values are persistent DATA (rendered literally, also
 *       while disabled);</li>
 *   <li>hover is pointer-only {@link HoverAnim#symmetric(long) symmetric
 *       140 ms} motion on the ADD pill; entry rows are removable chips —
 *       a scanning family whose immediate remove-button hover is kept
 *       deliberately and documented;</li>
 *   <li>focus is the add action's semantic keyboard target (Tab/Enter/
 *       Space/narration) and never means "capturing";</li>
 *   <li>listening is one persistent capture session owned at the ADD
 *       level (capture purpose is always ADD — no rebinding exists, so
 *       no REPLACE semantics are invented), carried by the stained pill
 *       + "> press key <" prompt, visible after the pointer leaves;</li>
 *   <li>disabled is the authoritative gate: no capture, no add, no
 *       remove, no save, no actionable focus; entering disabled while
 *       listening cancels without mutation.</li>
 * </ul>
 *
 * <p>Capture ownership uses the shared exclusive slot on {@link
 * FeatureSetting} — the same single-owner registry the canonical
 * {@link KeybindSetting} claims, so a KeyList and a Keybind can never
 * both own the next input event. The activation event (Enter/Space or
 * pointer) arms capture only after its dispatch returns; a later
 * screen-routed key is the one captured, so Enter can never add itself.
 * While listening, the next pointer press cancels and is consumed by
 * the owning screen before any child can activate (the Keybind two-step
 * rule). Scrolling the row out of the interactive viewport, replacing
 * or closing the screen cancels without mutation or save.
 *
 * <p>Glass: the "Add Key" pill matches {@link KeybindSetting}'s pill —
 * RAISED glass with the neutral {@code WINDOW_FILL} tint at rest and
 * the accent-STAINED tint while listening; disabled bypasses glass for
 * the established Enum-style muted fill (complete flat pill on decline).
 */
public class KeyListSetting extends FeatureSetting {
    private static final int ROW_H = 18;
    private static final int ADD_H = 20;
    private static final int BTN_SIZE = 14;

    private final Supplier<List<Integer>> getter;
    private final Consumer<List<Integer>> setter;
    private final Runnable saveAction;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * frame stamp + result for the "Add Key" pill. In the stamped frame
     * {@link #render} paints content only (flat pill on decline);
     * otherwise the surface paints in place — legacy, pixel-identical.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewPill = false;

    private boolean listening = false;
    private int lastX, lastY, lastW = 240;
    private final HoverAnim hoverAnim = HoverAnim.symmetric(140L);
    private SemanticActionControl interactionControl;

    /** User-facing key names, memoized by GLFW value (no per-frame formatting). */
    private final Map<Integer, String> nameCache = new HashMap<>();

    public KeyListSetting(String label, Supplier<List<Integer>> getter, Consumer<List<Integer>> setter) {
        this(label, getter, setter, AuroraConfig::save);
    }

    /** Package-private save seam for deterministic exactly-once tests. */
    KeyListSetting(String label, Supplier<List<Integer>> getter, Consumer<List<Integer>> setter, Runnable saveAction) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.saveAction = saveAction;
    }

    @Override public KeyListSetting description(String desc) { super.description(desc); return this; }

    @Override public int baseHeight() {
        return ROW_H + safeList().size() * (ROW_H + 2) + ADD_H + 8;
    }

    @Override public int height() { return baseHeight(); }

    /**
     * Pre-dim surface (the split's surface half): the Add pill's raised
     * glass, neutral at rest / accent-stained while listening. The Y walk
     * mirrors {@link #render}'s item loop (header + one stride per existing
     * entry) — keep the two in lockstep.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        glassPassFrame = GlassSurface.frame();
        int addX = x + 14;
        int addW = width - 28;
        int addY = y + ROW_H + 2 + safeList().size() * (ROW_H + 2) + 4;
        float glassR = Math.min(ADD_H / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewPill = !isDisabled()
                && GlassSurface.control(ctx, addX, addY, addW, ADD_H, glassR, listening);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastX = x;
        lastY = y;
        lastW = width;

        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        boolean disabled = reconcileDisabledState();

        renderLabelWithTooltip(ctx, label, x + 12, y + 4,
                disabled ? AuroraTheme.TEXT_DIM : AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        List<Integer> items = safeList();
        int iy = y + ROW_H + 2;
        int err = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
        for (int i = 0; i < items.size(); i++) {
            // Mode-aware row tint (translucent ON_BACKGROUND, not white).
            ctx.fill(x + 10, iy, x + width - 10, iy + ROW_H,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND),
                            disabled ? 0x14 : 0x22));

            int btnX = x + width - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;
            // Removable-chip family: immediate hover tint (no animator) by
            // design — list scanning, not a state change. Disabled removes
            // the affordance's hover emphasis entirely.
            boolean btnHover = !disabled && Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_SIZE, BTN_SIZE);
            ctx.fill(btnX, btnY, btnX + BTN_SIZE, btnY + BTN_SIZE,
                    ThemeManager.withAlpha(err, btnHover ? 0x66 : 0x33));
            AuroraFontRenderer.drawCentered(ctx, tr, "\u2212", btnX + BTN_SIZE / 2,
                    btnY + (BTN_SIZE - tr.lineHeight) / 2, 0xFFFFFFFF);

            Integer boxed = items.get(i);
            String display = boxed == null ? "?" : cachedName(boxed);
            int maxW = width - BTN_SIZE - 44;
            display = AuroraFontRenderer.ellipsize(tr, display, maxW, 3);
            ctx.drawString(tr, display, x + 16, iy + (ROW_H - tr.lineHeight) / 2,
                    disabled ? AuroraTheme.TEXT_DIM : AuroraTheme.TEXT_SECONDARY, false);
            iy += ROW_H + 2;
        }

        // "Add Key" pill (or "> press key <" while listening).
        int addX = x + 14;
        int addW = width - 28;
        int addY = iy + 4;
        boolean addHover = Widget.inBounds(mouseX, mouseY, addX, addY, addW, ADD_H);
        float hT = hoverAnim.update(addHover && !disabled);

        if (interactionControl != null) {
            interactionControl.setBounds(addX, addY, addW, ADD_H);
            interactionControl.updatePointer(mouseX, mouseY);
        }

        int fillTint   = disabled
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22)
                : AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = disabled
                ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x33)
                : AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_ON_HOVER, hT);
        int textColor  = disabled ? AuroraTheme.TEXT_DIM
                : AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: same contract as KeybindSetting's pill — raised glass,
        // neutral tint at rest, accent-stained tint while listening, and
        // the complete flat pill on decline. If the screen ran this row's
        // glass pass this frame the surface is already on screen UNDER the
        // dim and only its result matters here; otherwise (legacy frame
        // order) it is painted in place now through the shared GlassSurface
        // helper (identical calls).
        float glassR = Math.min(ADD_H / 2f, ThemeManager.current().roundness().radiusSmall());
        boolean glassOk;
        if (glassPassFrame == GlassSurface.frame()) {
            glassOk = passDrewPill;
        } else if (!disabled) {
            glassOk = GlassSurface.control(ctx, addX, addY, addW, ADD_H, glassR, listening);
        } else {
            glassOk = false;
        }
        if (!glassOk) {
            RenderUtil.drawSquircle(ctx, addX, addY, addW, ADD_H, AuroraTheme.RADIUS_SMALL,
                    listening ? AuroraTheme.IOS_BLUE_PRESSED : fillTint);
            RenderUtil.drawSquircleOutline(ctx, addX, addY, addW, ADD_H, AuroraTheme.RADIUS_SMALL, 1.0f,
                    listening ? AuroraTheme.IOS_BLUE : borderTint);
        }

        String addText = listening ? "> press key <"
                : items.size() >= KeystrokesModule.MAX_EXTRA_KEYS ? "List full (12 max)"
                : "+ Add Key";
        int addTextW = tr.width(addText);
        int textCol = listening ? ThemeManager.color(ThemeToken.ON_ACCENT) : textColor;
        ctx.drawString(tr, addText, addX + (addW - addTextW) / 2,
                addY + (ADD_H - tr.lineHeight) / 2 + 1, textCol, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false; // authoritative at action time, not render time
        if (button != 0) return false;

        List<Integer> items = safeList();
        int iy = lastY + ROW_H + 2;
        for (int i = 0; i < items.size(); i++) {
            int btnX = lastX + lastW - BTN_SIZE - 14;
            int btnY = iy + (ROW_H - BTN_SIZE) / 2;
            if (Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_SIZE, BTN_SIZE)) {
                List<Integer> copy = new ArrayList<>(items);
                if (i < copy.size()) {
                    copy.remove(i);
                    setter.accept(copy);
                    saveAction.run();
                }
                return true;
            }
            iy += ROW_H + 2;
        }

        int addX = lastX + 14;
        int addW = lastW - 28;
        int addY = iy + 4;
        if (Widget.inBounds(mouseX, mouseY, addX, addY, addW, ADD_H)) {
            // A semantic host owns entry (one activation sound). The direct
            // path exists for headless/manual hosts and stays silent.
            if (interactionControl != null && interactionControl.isAvailable()) {
                return interactionControl.activateFromPointer(mouseX, mouseY, button);
            }
            if (listening) cancelListening(); else beginListening();
            return true;
        }

        // A click anywhere else cancels listen mode without committing.
        if (listening) {
            cancelListening();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!listening) return false;

        if (reconcileDisabledState()) {
            // The event reached a capture owner whose gate changed since
            // the last frame. Consume-but-inert: the reconcile above tore
            // the session down; no mutation/save and no fallthrough.
            return true;
        }

        // ESC / BACKSPACE cancel without changing the list — the multi-value
        // analogue of the single-Keybind clear rule (there is no one value
        // to clear). Delete and every other keycode are ordinary entries.
        if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_BACKSPACE) {
            List<Integer> copy = new ArrayList<>(safeList());
            if (!copy.contains(keyCode) && copy.size() < KeystrokesModule.MAX_EXTRA_KEYS) {
                copy.add(keyCode);
                setter.accept(copy);
                saveAction.run(); // exactly one save, only on real mutation
            }
            // Duplicate / full-list capture: accepted no-op close. No
            // mutation, therefore no save.
        }
        finishListening();
        return true;
    }

    @Override
    public void onDetailScreenClose() {
        cancelListening();
    }

    @Override
    public void onDetailScreenOpen() {
        cancelListening();
    }

    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (!available) cancelListening();
    }

    /** Semantic adapter for the ADD action: focus/Enter/Space/narration + enabled gate. */
    @Override
    public SemanticActionControl interactionControl() {
        if (interactionControl == null) {
            interactionControl = new SemanticActionControl(SemanticAction.button(
                    Component.literal(label + ": Add Key"),
                    () -> {
                        String description = currentDescription();
                        return description != null ? Component.literal(description) : Component.empty();
                    },
                    () -> Component.literal(listening
                            ? "Waiting for key input. Extra keys: " + namesSummary()
                            : (safeList().isEmpty() ? "No extra keys" : "Extra keys: " + namesSummary())),
                    () -> !isDisabled(),
                    this::beginListening),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return interactionControl;
    }

    private void beginListening() {
        if (isDisabled() || listening) return;
        // Claims the SHARED exclusive slot — a listening Keybind anywhere
        // is cancelled first; there is never more than one capture owner.
        if (!claimCaptureOwnership()) return;
        listening = true;
        requestFocus();
    }

    /** Release clears listening + focus via {@link #onCancelCapture}. */
    private void finishListening() {
        releaseCaptureOwnership();
    }

    private void cancelListening() {
        finishListening();
    }

    @Override
    protected void onCancelCapture() {
        listening = false;
        releaseFocus();
    }

    /** Disabled can change asynchronously with respect to capture events. */
    private boolean reconcileDisabledState() {
        boolean disabled = isDisabled();
        if (disabled && listening) cancelListening();
        return disabled;
    }

    /** Comma-separated user-facing names of the configured entries. */
    private String namesSummary() {
        List<Integer> items = safeList();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(cachedName(items.get(i)));
        }
        return sb.toString();
    }

    private String cachedName(int glfwKey) {
        // Memoized — the ellipsized per-frame render path must not format
        // key names through InputConstants every frame.
        return nameCache.computeIfAbsent(glfwKey, KeybindSetting::keyName);
    }

    private List<Integer> safeList() {
        List<Integer> list = getter.get();
        return list != null ? list : new ArrayList<>();
    }

    // Package-private state probes for focused tests and the runtime harness.
    HoverAnim hoverAnimator() { return hoverAnim; }
    boolean listeningState() { return listening; }
    boolean reconcileForTest() { return reconcileDisabledState(); }
    Map<Integer, String> nameCacheForTest() { return nameCache; }
}
