package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.TooltipPresenter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
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

    private java.util.function.BooleanSupplier visibility = () -> true;
    public FeatureSetting visibleWhen(java.util.function.BooleanSupplier condition) {
        visibility = condition;
        return this;
    }
    /** The collapsible caption this row belongs to (null when ungrouped). */
    private SectionHeaderSetting section;
    final void section(SectionHeaderSetting header) { this.section = header; }
    /** Visible when its own condition holds and its section (if any) is expanded. */
    public final boolean isVisible() { return visibility.getAsBoolean() && (section == null || section.expanded()); }

    /** Detail hosts measure simple rows before height, paint and input walks; custom editors retain their own geometry. */
    protected com.aurora.client.ui.util.SettingRowLayout compactRow;
    private int compactWidth = -1;
    private long compactFontKey = Long.MIN_VALUE;
    private int compactSubtitleH = -1;
    /** The track length its page gave this slider row ({@link #pageTrackWidth}); 0 = its own. */
    private int pageTrackW;

    /**
     * One slider track length per page (owner decision 2026-11-14): the short track only when
     * every slider on the page has few positions, so values and track starts share one column.
     */
    public static int pageTrackWidth(Iterable<FeatureSetting> settings) {
        int w = com.aurora.client.ui.util.SettingRowLayout.SHORT_TRACK_W;
        for (FeatureSetting s : settings)
            if (s instanceof SliderSetting ss) w = Math.max(w, com.aurora.client.ui.util.SettingRowLayout.trackWidth(ss.steps()));
        return w;
    }

    /** Hosts set the page's track length before measuring; a change re-measures the row. */
    public final void pageTrack(int width) {
        if (width != pageTrackW) { pageTrackW = width; compactRow = null; }
    }

    public static long compactMeasurementKey() {
        return ThemeManager.generation() * 65537L
                + com.aurora.client.ui.util.AuroraFontRenderer.styleKey() * 31L;
    }

    public final void measureCompact(int width) {
        measureCompactChildren(width);
        if (!(this instanceof BooleanSetting || this instanceof SliderSetting || this instanceof EnumSetting<?>
                || this instanceof KeybindSetting || this instanceof ColorSetting || this instanceof ButtonSetting
                || this instanceof SectionHeaderSetting)) return;
        Font font = Minecraft.getInstance().font;
        long key = compactMeasurementKey();
        int subH = (this instanceof BooleanSetting || this instanceof KeybindSetting) ? valueLineHeight(font) : 0;
        if (compactRow != null && compactWidth == width && compactFontKey == key && compactSubtitleH == subH) return;
        var kind = this instanceof BooleanSetting ? com.aurora.client.ui.util.SettingRowLayout.Kind.BOOLEAN
                : this instanceof SliderSetting ? com.aurora.client.ui.util.SettingRowLayout.Kind.SLIDER
                : this instanceof EnumSetting<?> ? com.aurora.client.ui.util.SettingRowLayout.Kind.ENUM
                : this instanceof KeybindSetting ? com.aurora.client.ui.util.SettingRowLayout.Kind.KEYBIND
                : this instanceof ColorSetting ? com.aurora.client.ui.util.SettingRowLayout.Kind.COLOR
                : this instanceof ButtonSetting bs ? bs.compactKind()
                : com.aurora.client.ui.util.SettingRowLayout.Kind.SECTION;
        int trackW = pageTrackW > 0 ? pageTrackW
                : this instanceof SliderSetting ss ? com.aurora.client.ui.util.SettingRowLayout.trackWidth(ss.steps())
                : com.aurora.client.ui.util.SettingRowLayout.TRACK_W;
        compactRow = com.aurora.client.ui.util.SettingRowLayout.measure(kind, label, width, font.lineHeight, font::width, trackW);
        if (subH > 0) compactRow = compactRow.withSubtitle(subH, font.lineHeight);
        compactWidth = width;
        compactFontKey = key;
        compactSubtitleH = subH;
    }
    public final void clearCompactMeasurement() { compactRow = null; compactWidth = -1; compactSubtitleH = -1; clearCompactChildren(); }
    protected void measureCompactChildren(int width) {}
    protected void clearCompactChildren() {}
    protected final int controlX(int x, int width, int controlWidth) {
        return compactRow == null ? x + width - controlWidth - 14 : x + compactRow.control().x();
    }
    protected final int controlY(int y, int rowHeight, int controlHeight) {
        return compactRow == null ? y + (rowHeight - controlHeight) / 2 : y + compactRow.control().y();
    }
    protected final boolean compactControlContains(double mouseX, double mouseY, int x, int y) {
        return compactRow != null && compactRow.controlHit().contains(mouseX - x, mouseY - y);
    }
    protected final void syncCompactControl(int x, int y) {
        if (compactRow == null) return;
        var control = interactionControl();
        if (control == null) return;
        if (this instanceof BooleanSetting) control.setBounds(x, y, compactWidth, compactRow.height());
        else {
            var hit = compactRow.controlHit();
            control.setBounds(x + hit.x(), y + hit.y(), hit.width(), hit.height());
        }
    }
    protected final void renderCompactLabel(GuiGraphicsExtractor ctx, int x, int y, int mouseX, int mouseY, int color) {
        Font font = Minecraft.getInstance().font;
        // A disabled row says so in its label too, not only in a dimmed control beside a
        // full-strength label (UI polish 2026-11-14).
        if (isDisabled()) color = com.aurora.client.util.AuroraAnim.lerpArgb(
                com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.ON_BACKGROUND_MUTED),
                com.aurora.client.ui.component.MaterialSurface.color(com.aurora.client.theme.ThemeToken.ON_BACKGROUND_SECONDARY), .5f);
        var box = compactRow.label();
        if (isModified()) {
            renderResetLabel(ctx, label, x + box.x(), y + box.y(), box.width(),
                    box.height() - valueLineHeight(font), color, mouseX, mouseY, isDisabled());
            drawValueLine(ctx, x + box.x(), AuroraFontRenderer.blockY(font, y + box.y(), box.height(), 2) + AuroraFontRenderer.linePitch(font), isDisabled());
            return;
        }
        int textY = AuroraFontRenderer.blockY(font, y + box.y(), box.height(), compactRow.lines().size() + (valueLineHeight(font) > 0 ? 1 : 0));
        int textTop = textY;
        int textX = x + box.x();
        boolean labelHovered = false;
        for (String line : compactRow.lines()) {
            ctx.text(font, line, textX, textY, color, false);
            if (!labelHovered && !isDisabled() && hoveredLabelPixel(textX, textY, font, line, mouseX, mouseY)) labelHovered = true;
            textY += AuroraFontRenderer.linePitch(font);
        }
        if (labelHovered) TooltipPresenter.offer(tooltipKey(), currentDescription(), textX, textTop - 1,
                box.width(), textY - textTop + 2);
        drawValueLine(ctx, x + box.x(), textY, isDisabled());
    }

    private static boolean hoveredLabelPixel(int x, int y, Font font, String text, int mouseX, int mouseY) {
        return hostBand().contains(mouseX, mouseY) && TooltipPresenter.hit(x, y - 1, font.width(text), font.lineHeight + 2, mouseX, mouseY);
    }


    private java.util.function.BooleanSupplier differsFromDefault;
    private Runnable resetValue;
    private Supplier<String> resetDisplay;
    private com.aurora.client.ui.component.IconAction resetAction;
    private String resetTooltip;
    private Supplier<String> resetTooltipSupplier;
    private List<com.aurora.client.ui.interaction.SemanticActionControl> resetControls;
    private String resetFitText;
    private int resetFitWidth = -1, resetFitStyle = -1;
    private long resetFitGeneration = -1;
    private String resetFitted;
    private com.aurora.client.ui.util.SettingRowLayout.Reset resetLayout;
    private int resetH, resetFittedWidth;
    private final Supplier<String> rowDescription = this::currentDescription;

    protected final void installReset(java.util.function.BooleanSupplier differs, Runnable apply, Supplier<String> display) {
        differsFromDefault = differs;
        resetValue = apply;
        resetDisplay = display;
    }

    public final boolean hasResetDefault() { return differsFromDefault != null; }
    public final boolean isModified() { return hasResetDefault() && differsFromDefault.getAsBoolean(); }

    /** Hidden resets leave traversal and narration together, without rebuilding the host's children. */
    public final boolean controlVisible(com.aurora.client.ui.interaction.SemanticActionControl control) {
        return resetAction == null || control != resetAction.interactionControl() || isModified();
    }

    /** Explicit audit marker for a simple-looking row that edits non-config domain data. */
    public FeatureSetting noReset(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Reset exemption needs a reason");
        return this;
    }

    /** Formatter options are fluent too, so materialize the text after construction, on host registration. */
    private void ensureResetAction() {
        if (resetAction != null || !hasResetDefault()) return;
        resetTooltip = "Reset " + label + " to default (" + resetDisplay.get() + ")";
        resetTooltipSupplier = () -> resetTooltip;
        resetAction = new com.aurora.client.ui.component.IconAction("\uf053",
                com.aurora.client.ui.interaction.SemanticAction.button(Component.literal(resetTooltip),
                        null, null, () -> !isDisabled() && isModified(), () -> {
                            resetValue.run();
                            com.aurora.client.config.AuroraConfig.save();
                        }),
                () -> MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                () -> MaterialSurface.color(ThemeToken.ON_BACKGROUND)).visualEm(12);
    }

    /** The host uses this union so compound row families retain their original peer sets. */
    public final List<com.aurora.client.ui.interaction.SemanticActionControl> controlsWithReset() {
        var original = interactionControls();
        if (!hasResetDefault()) return original;
        ensureResetAction();
        boolean same = resetControls != null && resetControls.size() == original.size() + 1;
        if (same) for (int i = 0; i < original.size(); i++)
            if (resetControls.get(i) != original.get(i)) { same = false; break; }
        if (!same) {
            var all = new java.util.ArrayList<>(original);
            all.add(resetAction.interactionControl());
            resetControls = List.copyOf(all);
        }
        return resetControls;
    }

    protected final boolean resetMouseClicked(double mouseX, double mouseY, int button) {
        return isModified() && resetAction != null && resetAction.clicked(mouseX, mouseY, button);
    }

    /** A reset's selection key precedes stale slider/editor focus; active key capture still owns input. */
    public final boolean resetKeyPressed(Object focused, net.minecraft.client.input.KeyEvent event) {
        return !hasActiveCapture() && event.isSelection() && resetAction != null
                && focused == resetAction.interactionControl() && resetAction.interactionControl().keyPressed(event);
    }

    /** One cached fit/layout per row, shared by normal and compact label painters. */
    private void renderResetLabel(GuiGraphicsExtractor ctx, String text, int x, int y, int width, int height,
                                  int color, int mouseX, int mouseY, boolean disabled) {
        ensureResetAction();
        Font font = Minecraft.getInstance().font;
        int style = com.aurora.client.ui.util.AuroraFontRenderer.styleKey();
        long generation = ThemeManager.generation();
        if (!text.equals(resetFitText) || width != resetFitWidth || style != resetFitStyle || generation != resetFitGeneration) {
            resetFitText = text; resetFitWidth = width; resetFitStyle = style; resetFitGeneration = generation;
            resetFitted = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, text,
                    Math.max(0, width - com.aurora.client.ui.util.SettingRowLayout.RESET_W
                            - com.aurora.client.ui.util.SettingRowLayout.RESET_GAP), 0);
            resetFittedWidth = font.width(resetFitted);
            resetLayout = null;
        }
        if (resetLayout == null || resetH != height) {
            resetH = height;
            resetLayout = com.aurora.client.ui.util.SettingRowLayout.reset(0, 0, width, height, resetFittedWidth);
        }
        int ty = AuroraFontRenderer.textY(font, y, height);
        ctx.text(font, resetFitted, x, ty, color, false);
        trackLabelHover(tooltipKey(), rowDescription, x, ty, resetFittedWidth, font.lineHeight, mouseX, mouseY, disabled);
        var hit = resetLayout.action();
        resetAction.paint(ctx, font, x + hit.x(), y + hit.y(), hit.width(), hit.height(), mouseX, mouseY);
        trackLabelHover(resetTooltip, resetTooltipSupplier, x + hit.x(), y + hit.y(), hit.width(), hit.height(), mouseX, mouseY, disabled);
    }

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

    public static boolean hasActiveCapture() { return captureOwner != null; }

    /** Typing may resume a visible search, without taking keys from an editor/capture. */
    public boolean resumeSearch(net.minecraft.client.input.CharacterEvent event) { return false; }

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

    private boolean headerPinned;
    public int fixedHeaderHeight() { return 0; }
    public final void pinHeader(boolean pinned) { headerPinned = pinned; }
    public final boolean isHeaderPinned() { return headerPinned; }
    public void renderFixedHeader(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {}
    public void renderFixedHeaderGlass(GuiGraphicsExtractor ctx, int x, int y, int width) {}
    public java.util.List<com.aurora.client.ui.interaction.SemanticActionControl> fixedHeaderControls() { return java.util.List.of(); }

    public abstract void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY);

    /**
     * Cacheable static geometry layer. Rendered into the owning screen's
     * static-layer cache (or straight to the screen, when no cache is
     * active) — must contain only pixels that are stable across frames:
     * no hover state, no animation, no per-frame value. Default is empty:
     * settings that haven't declared a cacheable shape layer render
     * entirely in {@link #renderOverlay} instead.
     */
    public void renderShapes(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
    }

    /**
     * Live layer drawn above the cached shapes every frame: text, hover
     * feedback, animated values. Defaults to the full {@link #render}, so
     * settings without a shape split keep their exact current behavior.
     */
    public void renderOverlay(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
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
    public void renderGlassPass(GuiGraphicsExtractor ctx, int x, int y, int width) {
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
    private com.aurora.client.ui.interaction.SemanticActionControl cachedSingleControl;
    private List<com.aurora.client.ui.interaction.SemanticActionControl> singleControls = List.of();

    public java.util.List<com.aurora.client.ui.interaction.SemanticActionControl> interactionControls() {
        com.aurora.client.ui.interaction.SemanticActionControl c = interactionControl();
        if (c != cachedSingleControl) {
            cachedSingleControl = c;
            singleControls = c == null ? List.of() : List.of(c);
        }
        return singleControls;
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

    /** A key came up while this setting owns the capture slot (a combo's modifier released alone). Default: no-op. */
    public boolean onKeyRelease(int keyCode) {
        return false;
    }

    /** Hosts forward {@code keyReleased} here: it reaches the one capture owner, if any. */
    public static boolean routeKeyRelease(net.minecraft.client.input.KeyEvent _kev) {
        return captureOwner != null && captureOwner.onKeyRelease(_kev.key());
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
        if (activeFocused != this && (activeFocused instanceof EnumSetting<?> || activeFocused instanceof ColorSetting)) {
            activeFocused.onDetailScreenClose();
        }
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

    public boolean dismissPopup() { return false; }

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

    protected void renderDescription(GuiGraphicsExtractor ctx, int x, int y, int width) {
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
        return valueLine != null ? AuroraFontRenderer.linePitch(tr) : 0;
    }

    /**
     * Draws the §4 subtitle at {@code (x, y)} when one is configured and
     * the row isn't disabled; hidden entirely on disabled rows, matching
     * the label's dim treatment. Subclasses call this after their label
     * block and fold {@link #valueLineHeight} into their row height.
     */
    protected void drawValueLine(GuiGraphicsExtractor ctx, int x, int y, boolean disabled) {
        if (valueLine == null || disabled) return;
        String v = valueLine.get();
        if (v != null && !v.isEmpty()) {
            ctx.text(Minecraft.getInstance().font, v, x, y,
                    com.aurora.client.util.AuroraTheme.TEXT_SECONDARY, false);
        }
    }

    protected void drawValueLineCentered(GuiGraphicsExtractor ctx, int x, int y, int width, boolean disabled) {
        if (valueLine == null || disabled) return;
        String value = valueLine.get();
        if (value == null || value.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        String fitted = com.aurora.client.ui.util.AuroraFontRenderer.ellipsize(font, value, width, 0);
        ctx.text(font, fitted, x + (width - font.width(fitted)) / 2, y,
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
    }
    protected Object tooltipKey() { return this; }

    protected static void trackLabelHover(Object key, Supplier<String> desc, int x, int y,
                                          int textW, int lineH, int mouseX, int mouseY, boolean disabled) {
        if (!disabled && (hostBand == null || hostBand.contains(mouseX, mouseY))
                && TooltipPresenter.hit(x, y - 1, textW, lineH + 2, mouseX, mouseY)) {
            TooltipPresenter.offer(key, desc.get(), x, y - 1, textW, lineH + 2);
        }
    }

    public static void hoverTooltip(Object key, String text, int x, int y, int w, int h, int mouseX, int mouseY) {
        if (TooltipPresenter.hit(x, y, w, h, mouseX, mouseY)) TooltipPresenter.offer(key, text, x, y, w, h);
    }
    /**
     * Draws {@code text} at {@code (x, y)} in {@code color} and binds the
     * text bounds to this setting's description tooltip: dwelling on the
     * label itself for {@link TooltipPresenter#DWELL_MS} fades the description in,
     * moving the cursor away fades it back out.
     */
    protected void renderLabelWithTooltip(GuiGraphicsExtractor ctx, String text, int x, int y,
                                          int color, int mouseX, int mouseY) {
        renderLabelWithTooltip(ctx, text, x, y, color, mouseX, mouseY, false);
    }

    protected void renderLabelWithTooltip(GuiGraphicsExtractor ctx, String text, int x, int y,
                                          int color, int mouseX, int mouseY, boolean disabled) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;
        if(MaterialSurface.unified() && color==com.aurora.client.util.AuroraTheme.IOS_LABEL)
            color=MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int finalColor = disabled ? (MaterialSurface.unified() ? MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED)
                : com.aurora.client.util.AuroraTheme.TEXT_DIM) : color;
        ctx.text(tr, text, x, y, finalColor, false);
        trackLabelHover(tooltipKey(), this::currentDescription, x, y,
                tr.width(text), tr.lineHeight, mouseX, mouseY, disabled);
    }

    private String wrappedText;
    private int wrappedWidth = -1;
    private long wrappedGeneration = -1;
    private List<String> wrappedLabel = java.util.List.of();
    private int wrappedStyleKey = -1;

    /** Two measured lines within the label column; cache layout until text/width/font changes. */
    protected void renderWrappedLabel(GuiGraphicsExtractor ctx, String text, int x, int y, int width,
                                      int color, int mouseX, int mouseY, boolean disabled) {
        Font font = Minecraft.getInstance().font;
        if (font == null || width <= 0) return;
        if (isModified()) {
            renderResetLabel(ctx, text, x, y - 2, width, font.lineHeight + 4,
                    disabled ? MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED) : color, mouseX, mouseY, disabled);
            return;
        }
        long generation = ThemeManager.generation();
        int styleKey = com.aurora.client.ui.util.AuroraFontRenderer.styleKey();
        if (!text.equals(wrappedText) || width != wrappedWidth || generation != wrappedGeneration || styleKey != wrappedStyleKey) {
            wrappedText = text; wrappedWidth = width; wrappedGeneration = generation;
            wrappedStyleKey = styleKey;
            wrappedLabel = com.aurora.client.ui.util.WordWrap.lines(text, width, 2, font::width);
        }
        int count = Math.min(2, wrappedLabel.size());
        int top = y - (count - 1) * (AuroraFontRenderer.linePitch(font)) / 2;
        int finalColor = disabled ? MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED) : color;
        for (int i = 0; i < count; i++)
            ctx.text(font, wrappedLabel.get(i), x, top + i * (AuroraFontRenderer.linePitch(font)), finalColor, false);
        trackLabelHover(tooltipKey(), this::currentDescription, x, top, width,
                count * (AuroraFontRenderer.linePitch(font)), mouseX, mouseY, disabled);
    }

    public static void drawPendingTooltip(GuiGraphicsExtractor ctx, int left, int top, int width, int height) {
        TooltipPresenter.draw(ctx, left, top, width, height);
    }
}
