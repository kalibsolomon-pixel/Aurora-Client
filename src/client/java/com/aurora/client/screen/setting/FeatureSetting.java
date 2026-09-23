package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Abstract base for one row in a FeatureDetailScreen or settings tab.
 *
 * <p>Rows can optionally carry a description shown as a tooltip that fades
 * in after dwelling on the row's label text. Descriptions can be static
 * (set once via {@link #description(String)}) or
 * dynamic (set via {@link #description(Supplier)} for cases like enum
 * settings where the description changes per selected value).
 *
 * <p><b>Active focus:</b> sliders claim global scroll/key focus by setting
 * themselves as the {@link #activeFocused} setting on click. The owning
 * screen routes {@code mouseScrolled} and {@code keyPressed} events to the
 * focused setting first; if none is set or the focused setting doesn't
 * consume the event, screen-level handling resumes. Clicks on non-slider
 * elements clear focus via {@link #clearFocusIfNotMe}.
 */
public abstract class FeatureSetting {
    public final String label;

    private String staticDescription;
    private Supplier<String> dynamicDescription;

    private java.util.function.BooleanSupplier disableCondition = () -> false;

    /** The setting currently claiming scroll/key focus, or null. */
    private static volatile FeatureSetting activeFocused = null;

    // ===== Host clip band (C-8) =====
    //
    // Settings that float interactive geometry past their own row
    // (EnumSetting's option popup) resolve its placement against the
    // OWNING HOST's effective row clip — the same band truth the host's
    // scissor and hit-tests use (C-1/C-8). Hosts publish it once per frame
    // before their row walks (FeatureDetailScreen: its fade-boundary
    // scissor; AuroraScreen: its contentViewport) and clear it on removal;
    // the default is the full screen, so a host that never publishes (or a
    // headless test) gets plain screen-bounds placement.
    private static volatile com.aurora.client.ui.util.ClipBand hostBand = null;

    /** Publishes this frame's host row-clip band (hosts call before row walks). */
    public static void setHostBand(com.aurora.client.ui.util.ClipBand band) {
        hostBand = band;
    }

    /** Clears the published band (hosts call from {@code removed()}). */
    public static void clearHostBand() {
        hostBand = null;
    }

    /**
     * The owning host's effective row clip for this frame, or the full
     * screen when no host published one. Never null.
     */
    public static com.aurora.client.ui.util.ClipBand hostBand() {
        com.aurora.client.ui.util.ClipBand band = hostBand;
        if (band != null) return band;
        var win = Minecraft.getInstance() != null ? Minecraft.getInstance().getWindow() : null;
        int w = win != null ? win.getGuiScaledWidth() : 240;
        int h = win != null ? win.getGuiScaledHeight() : 240;
        return new com.aurora.client.ui.util.ClipBand(0, 0, w, h);
    }

    // ===== Exclusive capture ownership =====
    //
    // Capture-style settings (Keybind, KeyList) own the NEXT keyboard event
    // while listening. Exactly one such owner may exist process-wide — the
    // slot below is that invariant, structural rather than per-family, so a
    // KeyList and a Keybind can never both claim the next input even if a
    // future screen hosts both. Claiming cancels the previous owner through
    // its {@link #onCancelCapture} hook; release paths are idempotent.

    /** The setting currently owning key capture, or null. */
    private static FeatureSetting captureOwner;

    /**
     * Claims exclusive capture ownership, cancelling any previous owner.
     * Returns false (and claims nothing) when this setting is disabled —
     * the authoritative gate lives here so no call site can forget it.
     */
    protected boolean claimCaptureOwnership() {
        if (isDisabled()) return false;
        if (captureOwner != null && captureOwner != this) {
            FeatureSetting previous = captureOwner;
            captureOwner = null;
            previous.onCancelCapture();
        }
        captureOwner = this;
        return true;
    }

    /** Releases capture ownership if held; always invokes the cancel hook. */
    protected void releaseCaptureOwnership() {
        boolean held = captureOwner == this;
        if (held) captureOwner = null;
        onCancelCapture();
    }

    /** Whether this setting currently owns key capture. */
    protected boolean ownsCapture() {
        return captureOwner == this;
    }

    /** Family hook: clear listening visuals/state. Default no-op. */
    protected void onCancelCapture() {
    }

    /**
     * Cancels the one capture owner (whatever family) and reports whether
     * a pointer event was consumed. Called before screen child routing.
     */
    public static boolean cancelActiveCapture() {
        FeatureSetting owner = captureOwner;
        if (owner == null) return false;
        owner.releaseCaptureOwnership();
        return true;
    }

    protected FeatureSetting(String label) {
        this.label = label;
    }

    public FeatureSetting description(String desc) {
        this.staticDescription = desc;
        this.dynamicDescription = null;
        return this;
    }

    public FeatureSetting description(Supplier<String> desc) {
        this.dynamicDescription = desc;
        this.staticDescription = null;
        return this;
    }

    public FeatureSetting disabled(java.util.function.BooleanSupplier condition) {
        this.disableCondition = condition;
        return this;
    }

    public boolean isDisabled() {
        return disableCondition.getAsBoolean();
    }

    public String currentDescription() {
        if (dynamicDescription != null) return dynamicDescription.get();
        return staticDescription;
    }

    public int height() {
        return baseHeight() + descriptionHeight(240);
    }

    public abstract int baseHeight();

    public abstract void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY);

    /**
     * Cacheable static geometry layer. Rendered into the owning screen's
     * static-layer cache (or straight to the screen, when no cache is
     * active) — must contain only pixels that are stable across frames:
     * no hover state, no animation, no per-frame value. Default is empty:
     * settings that haven't declared a cacheable shape layer render
     * entirely in {@link #renderOverlay} instead.
     */
    public void renderShapes(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
    }

    /**
     * Live layer drawn above the cached shapes every frame: text, hover
     * feedback, animated values. Defaults to the full {@link #render}, so
     * settings without a shape split keep their exact current behavior.
     */
    public void renderOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        render(ctx, x, y, width, mouseX, mouseY);
    }

    /**
     * Per-frame live glass pass, called by the owning screen AFTER the
     * static-cache capture pass and BEFORE the overlay dim + cached blit —
     * so cached content (text, thumbs, knobs) stacks above the glass. The
     * row's current geometry is passed in (the screen computes it fresh
     * from its scroll state, so a pass on a scrolling frame paints at the
     * same position the content pass will use — remembered-from-last-frame
     * rects would lag one frame behind the eased scroll).
     *
     * <p>Split discipline (the {@code Button}/{@code EditBoxMixin} scheme,
     * AGENTS.md §6 convention 6): a widget that paints glass implements
     * this to paint its SURFACE while a structural glass pass is open
     * ({@code GlassSurface.passOpen()} — the widget itself checks, because
     * screens like {@code FeatureDetailScreen} call this hook on the
     * legacy frame order too, where the surface must keep painting in
     * place inside {@link #render}), stamps the frame
     * ({@code GlassSurface.frame()}) and remembers whether the glass drew;
     * {@link #render} then paints content only — or the flat fallback when
     * the glass declined, or the full in-place surface when the stamp says
     * no pass ran this frame (the exact legacy look, pixel for pixel).
     * Default no-op.
     */
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
    }

    /** Optional screen-lifecycle adapter for custom-painted semantic controls. */
    public com.aurora.client.ui.interaction.SemanticActionControl interactionControl() {
        return null;
    }

    /**
     * All custom-painted semantic controls of this setting. Default: the
     * single {@link #interactionControl()} when non-null, else empty.
     * Settings exposing several distinct actions (PixelCanvas's
     * Clear/Default/Apply) override this; the owning screen registers,
     * availability-sweeps, and refocuses the whole list. Lazy single
     * controls keep flowing through the default without change.
     */
    public java.util.List<com.aurora.client.ui.interaction.SemanticActionControl> interactionControls() {
        com.aurora.client.ui.interaction.SemanticActionControl c = interactionControl();
        return c == null ? java.util.List.of() : java.util.List.of(c);
    }

    /**
     * Notifies a setting whether its semantic control is currently inside
     * the owning screen's effective interaction viewport. Most settings do
     * not hold modal input state and need no callback. Capture-style
     * controls override this so a scrolled-out control cannot keep hidden
     * ownership of subsequent input.
     */
    public void onInteractionAvailabilityChanged(boolean available) {
    }

    /**
     * Stable fingerprint of this setting's cacheable shape layer. The owning
     * screen's static-layer cache folds it into its version, so a change
     * re-renders only this row's cached shapes. Default {@code 0} — override
     * for rows whose cached geometry depends on state beyond the theme
     * generation stamp and content height (e.g. a toggle's on/off state).
     */
    public int shapeFingerprint() {
        return 0;
    }

    public abstract boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth);

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean onScroll(double vertical) {
        return false;
    }

    /**
     * Optional hook for routed key events. Called by the owning screen for
     * the focused setting (only). Return true if consumed.
     *
     * <p>Default: no-op. Slider subclasses override (arrow keys ➔ ±step).
     */
    public boolean onKeyPress(int keyCode, int modifiers) {
        return false;
    }

    public boolean onKeyPress(net.minecraft.client.input.KeyEvent _kev) {
        return onKeyPress(_kev.key(), _kev.modifiers());
    }

    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent _ev) {
        return false;
    }

    // ===== Detail-screen lifecycle hooks =====
    //
    // Optional hooks fired by FeatureDetailScreen so stateful settings
    // (e.g. a search field) can reset/focus themselves when the screen
    // that owns them opens or closes. Defaults are no-ops so existing
    // subclasses are unaffected.

    /**
     * Called once by {@link com.aurora.client.screen.FeatureDetailScreen#init()}
     * for every setting on the screen, each time the screen opens (and on
     * resize). Override to claim initial focus, pre-fill values, etc.
     */
    public void onDetailScreenOpen() {
    }

    /**
     * Called once by {@link com.aurora.client.screen.FeatureDetailScreen#onClose()}
     * for every setting on the screen, right before the screen is torn down.
     * Override to clear transient state (e.g. a search query) so it doesn't
     * leak into the next open — settings are long-lived singletons shared
     * across open/close cycles.
     */
    public void onDetailScreenClose() {
    }

    // ===== Focus management =====

    /** Claim scroll/key focus for this setting. */
    protected void requestFocus() {
        activeFocused = this;
    }

    /** Release focus if held by this setting. */
    protected void releaseFocus() {
        if (activeFocused == this) activeFocused = null;
    }

    /** Returns the currently focused setting, or null. */
    public static FeatureSetting getFocused() {
        return activeFocused;
    }

    /**
     * Drop focus regardless of which setting holds it. Owning screens call
     * this on clicks that aren't on a focusable setting (e.g. tab strip,
     * Done button, empty list area).
     */
    public static void clearFocus() {
        activeFocused = null;
    }

    // ===== Description rendering =====
    //
    // Inline descriptions are gone: each setting's description surfaces
    // as a tooltip that fades in after dwelling on the setting's label
    // text (see the label-hover tooltip section below). These two hooks
    // are retained as no-ops so existing subclass call sites keep
    // compiling and reserve zero vertical space.

    protected int descriptionHeight(int width) {
        return 0;
    }

    protected void renderDescription(GuiGraphics ctx, int x, int y, int width) {
        // no-op — see class note above.
    }

    // ===== Design-language §4 live-value subtitle (opt-in) =====

    /**
     * Design language §4 row subtitle: an optional small
     * {@code ON_BACKGROUND_SECONDARY} line beneath the label, for live
     * values/state the control itself does not show. Opt-in by design —
     * the spec's exclusion clause governs: never add one to a widget that
     * already surfaces its state at a glance (sliders/enums/keybinds all
     * do), which is why the mechanism lives here unused by most widgets.
     * First and so far only user: Better Hitreg's three alert toggles.
     */
    private Supplier<String> valueLine = null;

    /**
     * Fluent setter for the §4 live-value subtitle. The supplier is
     * evaluated at render time (once per frame while the row is visible
     * and enabled) — keep it cheap: a small string build is fine, no
     * lookups or per-frame recomputation of the underlying figure.
     */
    public FeatureSetting valueLine(Supplier<String> supplier) {
        this.valueLine = supplier;
        return this;
    }

    /** The configured §4 subtitle supplier, or null when the row has none. */
    protected Supplier<String> valueLine() {
        return valueLine;
    }

    /** Height the §4 subtitle contributes inside the row (0 when absent). */
    protected int valueLineHeight(net.minecraft.client.gui.Font tr) {
        return valueLine != null ? tr.lineHeight + 3 : 0;
    }

    /**
     * Draws the §4 subtitle at {@code (x, y)} when one is configured and
     * the row isn't disabled; hidden entirely on disabled rows, matching
     * the label's dim treatment. Subclasses call this after their label
     * block and fold {@link #valueLineHeight} into their row height.
     */
    protected void drawValueLine(GuiGraphics ctx, int x, int y, boolean disabled) {
        if (valueLine == null || disabled) return;
        String v = valueLine.get();
        if (v != null && !v.isEmpty()) {
            ctx.drawString(Minecraft.getInstance().font, v, x, y,
                    com.aurora.client.util.AuroraTheme.TEXT_SECONDARY, false);
        }
    }

    // ===== Label-hover description tooltip =====
    //
    // There is no separate help badge anymore: the setting's label text
    // itself is the hover target. Dwelling on the text for
    // {@link #HOVER_DWELL_MS} fades the description tooltip in; moving
    // the cursor away fades it back out. The tooltip box's corner
    // chamfer follows the theme's roundness token, like every other
    // panel.

    /** Continuous hover over a label before its tooltip fades in. */
    private static final long HOVER_DWELL_MS = 1500L;
    /** Fade-in / fade-out duration of the description tooltip. */
    private static final long TOOLTIP_FADE_MS = 200L;
    /** Max rendered width of the tooltip text before it wraps. */
    private static final int TOOLTIP_MAX_W = 220;

    /** Per-label dwell-start timestamps, keyed by a caller-supplied id. */
    private static final Map<String, Long> HOVER_DWELL = new HashMap<>();

    /** Shared 0→1 fade for the tooltip; advanced exactly once per frame
     *  in {@link #drawPendingTooltip}. Symmetric (§8.3 vocabulary at the
     *  tooltip's own 200 ms — supplemental-info motion, not the 140 ms
     *  control-hover duration): the fade-in animates from rest once the
     *  dwell completes; it does not snap in. */
    private static final HoverAnim TOOLTIP_FADE = HoverAnim.symmetric(TOOLTIP_FADE_MS);

    /**
     * Tooltip state for the current frame. Label renderers set
     * {@code tooltipClaimed} when a dwell completes under the cursor;
     * {@link #drawPendingTooltip} consumes the claim and resets it, so
     * owning screens need no begin-frame hook. Content and anchor stay
     * frozen (and keep drawing, fading) once the cursor leaves so the
     * box drifts away instead of snapping.
     */
    private static boolean tooltipClaimed = false;
    private static String tooltipContent = null;
    private static int tooltipX = 0;
    private static int tooltipY = 0;

    /** Stable per-instance key for {@link #HOVER_DWELL}. */
    protected String tooltipKey() {
        return "s" + Integer.toHexString(System.identityHashCode(this));
    }

    /**
     * Hit-tests one label-text region for the description tooltip dwell.
     * Every label renderer calls this each frame; dwell timing and claim
     * bookkeeping are centralized here so callers stay one-liners.
     * Leaving the region (or losing the description) resets the dwell.
     * The description arrives as a supplier and is only evaluated once the
     * label is actually hovered — dynamic descriptions (live stats,
     * per-value enum text) must not be built every frame for every row.
     */
    protected static void trackLabelHover(String key, Supplier<String> desc, int x, int y,
                                          int textW, int lineH,
                                          int mouseX, int mouseY, boolean disabled) {
        boolean hovered = !disabled
                && mouseX >= x && mouseX < x + Math.max(1, textW)
                && mouseY >= y - 1 && mouseY < y + lineH + 1;
        if (!hovered) {
            HOVER_DWELL.remove(key);
            return;
        }
        String text = desc.get();
        if (text == null || text.isEmpty()) {
            HOVER_DWELL.remove(key);
            return;
        }
        long now = System.currentTimeMillis();
        long start = HOVER_DWELL.computeIfAbsent(key, k -> now);
        if (now - start >= HOVER_DWELL_MS) {
            tooltipClaimed = true;
            tooltipContent = text;
            tooltipX = mouseX;
            tooltipY = mouseY;
        }
    }

    /**
     * Draws {@code text} at {@code (x, y)} in {@code color} and binds the
     * text bounds to this setting's description tooltip: dwelling on the
     * label itself for {@link #HOVER_DWELL_MS} fades the description in,
     * moving the cursor away fades it back out.
     */
    protected void renderLabelWithTooltip(GuiGraphics ctx, String text, int x, int y,
                                          int color, int mouseX, int mouseY) {
        renderLabelWithTooltip(ctx, text, x, y, color, mouseX, mouseY, false);
    }

    protected void renderLabelWithTooltip(GuiGraphics ctx, String text, int x, int y,
                                          int color, int mouseX, int mouseY, boolean disabled) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        int finalColor = disabled ? ThemeManager.semanticContrast().disabledText() : color;
        ctx.drawString(tr, text, x, y, finalColor, false);
        trackLabelHover(tooltipKey(), this::currentDescription, x, y,
                tr.width(text), tr.lineHeight, mouseX, mouseY, disabled);
    }

    /**
     * Advances the tooltip fade and draws it, clamped inside the screen.
     * Owning screens call this once, after all rows and overlays; the
     * call also consumes the per-frame claim so the next frame starts
     * fresh. Every layer — shadow, box, outline, text — is alpha-scaled
     * by the fade value, and the box's corner chamfer conforms to the
     * Theme module's roundness option.
     */
    public static void drawPendingTooltip(GuiGraphics ctx, int screenW, int screenH) {
        float fade = TOOLTIP_FADE.update(tooltipClaimed);
        tooltipClaimed = false;
        if (tooltipContent == null) return;
        if (fade <= 0.001f) {
            tooltipContent = null; // fully faded out — drop the content
            return;
        }
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        List<FormattedCharSequence> lines =
                tr.split(Component.literal(tooltipContent), TOOLTIP_MAX_W);
        int textW = 0;
        for (var l : lines) textW = Math.max(textW, tr.width(l));
        int boxW = textW + 12;
        int boxH = lines.size() * (tr.lineHeight + 1) + 7;
        int bx = tooltipX + 12;
        int by = tooltipY + 12;
        if (bx + boxW > screenW - 4) bx = screenW - 4 - boxW;
        if (by + boxH > screenH - 4) by = screenH - 4 - boxH;
        if (bx < 4) bx = 4;
        if (by < 4) by = 4;
        // Corner chamfer conforms to the Theme module's roundness option.
        int radius = ThemeManager.current().roundness().radius();
        AuroraShapes.dropShadow(ctx, bx, by, boxW, boxH, radius, fade);
        AuroraShapes.panel(ctx, bx, by, boxW, boxH,
                fadeColor(ThemeManager.semanticContrast().tooltipBackground(), fade), radius);
        AuroraShapes.outline(ctx, bx, by, boxW, boxH,
                fadeColor(AuroraTheme.WINDOW_OUTLINE, fade), radius);
        // ON_OVERLAY is the tooltip-text role every other tooltip in the
        // codebase reads (Profile/Waypoint rows, the pack-browser toast).
        int textColor = fadeColor(ThemeManager.semanticContrast().tooltipForeground(), fade);
        int ty = by + 4;
        for (var l : lines) {
            ctx.drawString(tr, l, bx + 6, ty, textColor, false);
            ty += tr.lineHeight + 1;
        }
    }

    /** Scales the alpha byte of {@code argb} by {@code t} (0..1). */
    private static int fadeColor(int argb, float t) {
        return com.aurora.client.util.AuroraAnim.scaleAlpha(argb, t);
    }
}
