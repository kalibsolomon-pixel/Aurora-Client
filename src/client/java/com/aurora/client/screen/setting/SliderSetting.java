package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.Slider;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One labeled slider row — the single parameterized implementation behind
 * every slider setting (integer or double range, optional percent readout).
 * Track/fill/knob drawing and drag/key interaction live in the shared
 * {@link Slider} control; this class owns the row chrome: the label with
 * its description tooltip, the right-aligned value readout (cached by
 * value), focus claiming, and config persistence at commit points
 * (release / consumed key press).
 *
 * <p>Pixel/layout facts inherited from the widgets this consolidates: the
 * row is 36 px tall, the label sits at {@code (x+12, y+6)}, the value
 * readout is right-aligned 14 px from the row's right edge, and the track
 * spans the row inset 12 px per side ({@link #trackInset()} total; the
 * theme-opacity slider keeps its own historical 26 px inset).
 *
 * <p><b>Editable value</b> ({@link #editableValue()}, opt-in): the value
 * readout becomes a small text field on click, for precise entry on ranges
 * too wide to drag accurately (the global frame cap's 0–2000). Enter or
 * clicking away commits; Escape reverts. A numeric value outside the range
 * clamps (drag parity — the slider could never leave the range either);
 * empty or non-numeric input reverts. The committed value flows through
 * the slider's own snap+clamp+setter path, and persists via the same
 * async {@link AuroraConfig#save()} as every other commit point.
 *
 * <p>{@link ThemeOpacitySetting} extends this row with drag-aware
 * throttled live-apply: it reuses the drawing pieces and the value cache
 * but owns its interaction and splits rendering across the cacheable shape
 * layer and the live overlay.
 */
public class SliderSetting extends FeatureSetting {

    protected static final int CONTROL_H = 36;
    private static final double STEP = 0.01;

    /** Shared themed slider — owns the track/fill/knob drawing + drag/key interaction. */
    protected final Slider slider;

    /** Track geometry from the last layout — hit-testing for subclasses with custom interaction. */
    protected int lastTrackX, lastTrackW;
    protected int lastWidth = 240;

    private final boolean intMode;
    private boolean percent = false;

    // ----- editable value (opt-in; see class doc) -----
    private boolean editable = false;
    private boolean editingValue = false;
    private EditBox valueField;
    /** Readout text transform (e.g. the frame cap's "Off" at 0); null = raw value text. */
    private Function<Double, String> valueFormat = null;
    private int lastReadoutRight, lastReadoutW;

    private long cachedKey = Long.MIN_VALUE;
    private String cachedStr = "";
    private int cachedStrW = 0;

    /**
     * Integer-range slider row. A factory (not an overloaded constructor —
     * {@code IntSupplier}/{@code DoubleSupplier} lambdas are both
     * applicable to int getters and make the overloads ambiguous) so call
     * sites state their range type.
     */
    public static SliderSetting ofInt(String label, IntSupplier getter, IntConsumer setter, int min, int max) {
        Slider slider = new Slider(getter::getAsInt, v -> setter.accept((int) Math.round(v)), min, max, 1);
        return new SliderSetting(label, slider, true);
    }

    /** Double-range slider row (two-decimal readout; see {@link #percent()}). */
    public static SliderSetting of(String label, DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new SliderSetting(label, getter, setter, min, max, false);
    }

    private SliderSetting(String label, Slider slider, boolean intMode) {
        super(label);
        this.intMode = intMode;
        this.slider = slider;
    }

    SliderSetting(String label, DoubleSupplier getter, DoubleConsumer setter, double min, double max, boolean intMode) {
        super(label);
        this.intMode = intMode;
        this.slider = createSlider(getter, setter, min, max);
    }

    /**
     * Builds the renderer's slider for double-range rows. Subclass hook:
     * {@link ThemeOpacitySetting} swaps in a pending-aware display getter.
     * Runs during construction, so overrides must not eagerly read subclass
     * state — capturing it inside the returned slider's lambdas is fine
     * (they evaluate at render time).
     */
    protected Slider createSlider(DoubleSupplier getter, DoubleConsumer setter, double min, double max) {
        return new Slider(getter, setter, min, max, STEP);
    }
    @Override public SliderSetting description(String desc) { super.description(desc); return this; }
    @Override public SliderSetting description(Supplier<String> desc) { super.description(desc); return this; }

    /**
     * Render the value as a percentage (value * 100, rounded, with "%")
     * instead of the raw two-decimal form. Double ranges only; ignored for ints.
     */
    public SliderSetting percent() { this.percent = true; this.cachedKey = Long.MIN_VALUE; return this; }

    /**
     * Opt this row into the editable value readout: clicking the readout
     * swaps it for a small text field (Enter/click-away commits, Escape
     * reverts — see the class doc for the exact commit semantics). For
     * wide ranges where dragging can't hit an exact value quickly.
     */
    public SliderSetting editableValue() { this.editable = true; return this; }

    /**
     * Custom readout text (e.g. "Off" for a 0 sentinel). The editor still
     * prefills and commits raw numbers — the formatter is display-only.
     */
    public SliderSetting valueFormatter(Function<Double, String> format) {
        this.valueFormat = format;
        this.cachedKey = Long.MIN_VALUE;
        return this;
    }

    /**
     * Opt this row into the Phase B canonical slider state channels
     * (symmetric 140 ms hover, drag-grip knob, focus hairline) — see
     * {@link Slider#canonicalStates()}. Pilot-scoped: slider rows stay
     * legacy until deliberately migrated.
     */
    public SliderSetting canonicalStates() {
        slider.canonicalStates();
        return this;
    }

    @Override public int baseHeight() { return CONTROL_H; }
    @Override public int height() { return CONTROL_H + descriptionHeight(lastWidth); }

    /**
     * The cached layer (track) depends on the slider's disabled color —
     * delegate to the slider's fingerprint so a flip invalidates the row
     * cache (the latent staleness the Phase B audit found).
     */
    @Override
    public int shapeFingerprint() {
        return slider.shapeFingerprint();
    }

    // ------------------------------------------------------------------
    //  Row pieces — shared by render() and the shapes/overlay split a
    //  subclass declares for the owning screen's static-layer cache.
    // ------------------------------------------------------------------

    /** Total horizontal inset of the track from the row (both sides combined). */
    protected int trackInset() { return 24; }

    private void layoutTrack(int x, int y, int width) {
        lastWidth = width;
        int trackX = x + 12;
        int trackW = width - trackInset();
        lastTrackX = trackX;
        lastTrackW = trackW;
        slider.layout(trackX, y, trackW, CONTROL_H);
    }

    /** Label + cached value readout (or the edit field while editing). Reads the value through the slider's getter. */
    protected void drawLabelRow(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        double value = slider.value();

        renderLabelWithTooltip(ctx, label, x + 12, y + 6, AuroraTheme.IOS_LABEL, mouseX, mouseY, disabled);

        // A click elsewhere on the screen clears row focus before the clicked
        // row re-claims it — focus loss without a click on THIS row is the
        // click-away commit signal (Done button, another row, the search
        // field…). Checked here rather than in mouseClicked because most
        // clicks never reach this row's handler at all.
        if (editingValue && FeatureSetting.getFocused() != this) {
            commitValueEdit();
        }

        if (editingValue && valueField != null) {
            layoutValueField(x, y, width);
            valueField.setEditable(!disabled);
            valueField.render(ctx, mouseX, mouseY, 0f);
            return;
        }

        long key = valueKey(value);
        if (key != cachedKey) {
            cachedStr = valueText(value);
            cachedStrW = tr.width(cachedStr);
            cachedKey = key;
        }
        lastReadoutRight = x + width - 14;
        lastReadoutW = cachedStrW;

        // Highlight the value in accent when this slider holds focus, so the
        // user can see at a glance which slider scroll/keys go to.
        boolean focused = (!disabled && FeatureSetting.getFocused() == this);
        int valueColor = disabled ? AuroraTheme.TEXT_DIM : (focused ? AuroraTheme.IOS_BLUE : AuroraTheme.IOS_SECONDARY_LABEL);
        ctx.drawString(tr, cachedStr, x + width - cachedStrW - 14, y + 6, valueColor, false);
    }

    /** Cacheable static track layer (depends only on the roundness token). */
    protected void drawTrackShapes(GuiGraphics ctx, int x, int y, int width) {
        layoutTrack(x, y, width);
        slider.disabled(isDisabled());
        slider.renderShapes(ctx, lastTrackX, y, lastTrackW, CONTROL_H);
    }

    /** Live track layer: fill, knob, hover ring — tracks value and hover. */
    protected void drawTrackOverlay(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        layoutTrack(x, y, width);
        slider.disabled(isDisabled());
        // Canonical focus paint: the row owns keyboard focus (its readout
        // already highlights); mirror it onto the track hairline.
        slider.focusedVisual(!isDisabled() && FeatureSetting.getFocused() == this);
        slider.renderOverlay(ctx, lastTrackX, y, lastTrackW, CONTROL_H, mouseX, mouseY);
    }

    private long valueKey(double v) {
        return intMode ? (long) v : Math.round(v * 100.0);
    }

    private String valueText(double v) {
        if (valueFormat != null) return valueFormat.apply(v);
        if (intMode) return Integer.toString((int) v);
        return percent ? Math.round(v * 100.0) + "%" : String.format("%.2f", v);
    }

    // ------------------------------------------------------------------
    //  Editable value (opt-in; see class doc)
    // ------------------------------------------------------------------

    private static final int VALUE_FIELD_W = 46;
    private static final int VALUE_FIELD_H = 16;

    /** Lazily creates the value EditBox (theme rendering comes from EditBoxMixin like every other field). */
    private void ensureValueField() {
        if (valueField != null) return;
        Font font = Minecraft.getInstance().font;
        valueField = new EditBox(font, 0, 0, VALUE_FIELD_W, VALUE_FIELD_H, Component.literal(label));
        valueField.setMaxLength(6);
        valueField.setBordered(false);
        valueField.setTextColor(AuroraTheme.IOS_LABEL);
        // Digits only (plus '.'/'-' for double rows), live-stripped — the
        // same responder idiom as PixelCanvasSetting's W/H fields.
        valueField.setResponder(s -> {
            String allowed = intMode ? "0123456789" : "0123456789.-";
            StringBuilder sb = new StringBuilder(s.length());
            boolean dotSeen = false;
            for (char c : s.toCharArray()) {
                if (Character.isDigit(c) || (c == '-' && sb.length() == 0) || (c == '.' && !intMode && !dotSeen)) {
                    if (c == '.') dotSeen = true;
                    sb.append(c);
                }
            }
            String clean = sb.toString();
            if (!clean.equals(s)) valueField.setValue(clean);
        });
    }

    private void layoutValueField(int x, int y, int width) {
        // Vertically centered on where the readout text sits (y+6, ~9px line)
        // so opening the editor doesn't visibly jump the value's position.
        valueField.setX(x + width - 14 - VALUE_FIELD_W);
        valueField.setY(y + 2);
        valueField.setWidth(VALUE_FIELD_W);
    }

    /** Opens the in-place value editor (the readout's click handler). */
    private void openValueEdit() {
        ensureValueField();
        double v = slider.value();
        valueField.setValue(intMode ? Integer.toString((int) v) : String.valueOf(v));
        valueField.setFocused(true);
        valueField.moveCursorToEnd(false);
        editingValue = true;
        requestFocus();
    }

    /** Commits the field's contents: numeric → clamp through the slider; empty/invalid → revert. Never re-claims row focus — the Enter path does that itself, and on click-away the clicked element owns focus now. */
    private void commitValueEdit() {
        if (!editingValue) return;
        editingValue = false;
        if (valueField != null) valueField.setFocused(false);
        String text = valueField == null ? "" : valueField.getValue().trim();
        if (!text.isEmpty() && !"-".equals(text) && !".".equals(text)) {
            try {
                double parsed = intMode ? Integer.parseInt(text) : Double.parseDouble(text);
                slider.setValue(parsed); // snap + clamp + setter — the exact drag path
                AuroraConfig.save();
            } catch (NumberFormatException ignored) {
                // reverted — stripped characters can't produce this for ints,
                // but a lone "."/"-" guard costs nothing for doubles
            }
        }
    }

    /** Reverts the edit without applying (Escape / screen close). */
    private void cancelValueEdit() {
        editingValue = false;
        if (valueField != null) valueField.setFocused(false);
    }

    /**
     * Pre-dim surface (§6 convention 6): drives the value field's own glass
     * pass (EditBoxMixin carries the frame-stamp scheme), positioned from
     * the row geometry the screen passes — the same rect render computes.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return; // legacy frame order
        if (!editingValue || valueField == null || isDisabled()) return;
        layoutValueField(x, y, width);
        ((com.aurora.client.ui.component.GlassEditBox) valueField).aurora$renderGlassPass(ctx);
    }

    @Override
    public void onDetailScreenClose() {
        cancelValueEdit();
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        drawLabelRow(ctx, x, y, width, mouseX, mouseY);
        drawTrackShapes(ctx, x, y, width);
        drawTrackOverlay(ctx, x, y, width, mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    //  Interaction — delegated to the shared slider; focus is claimed on
    //  grab and the config persists at commit points only.
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        if (editingValue && valueField != null) {
            // Inside the field: keep editing. Elsewhere in the row: commit and
            // let the click do its normal job (e.g. start a slider drag).
            boolean inField = mouseX >= valueField.getX() && mouseX < valueField.getX() + VALUE_FIELD_W
                    && mouseY >= valueField.getY() && mouseY < valueField.getY() + VALUE_FIELD_H;
            if (inField) {
                valueField.setFocused(true);
                requestFocus();
                return true;
            }
            commitValueEdit(); // falls through — the click proceeds
        } else if (editable && button == 0) {
            // Readout rect (padded to a comfortable click target) opens the editor.
            int hitW = Math.max(lastReadoutW, 30) + 6;
            if (lastReadoutRight > 0
                    && mouseX >= lastReadoutRight - hitW && mouseX < lastReadoutRight + 2
                    && mouseY >= rowY + 2 && mouseY < rowY + 16) {
                openValueEdit();
                return true;
            }
        }
        boolean handled = slider.mouseClicked(mouseX, mouseY, button);
        if (handled) requestFocus();
        return handled;
    }

    @Override
    public boolean onKeyPress(net.minecraft.client.input.KeyEvent _kev) {
        if (isDisabled()) return false;
        if (editingValue && valueField != null) {
            if (_kev.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || _kev.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitValueEdit();
                // Keep the row focused so arrow keys adjust the slider right after.
                requestFocus();
                return true;
            }
            if (_kev.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                cancelValueEdit();
                return true;
            }
            return valueField.keyPressed(_kev);
        }
        // Non-editing presses keep the existing routing (the int variant,
        // which subclasses like ThemeOpacitySetting may override).
        return super.onKeyPress(_kev);
    }

    @Override
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (isDisabled()) return false;
        if (editingValue && valueField != null) {
            return valueField.charTyped(_ev);
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (slider.mouseReleased(mouseX, mouseY, button)) {
            AuroraConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean onScroll(double vertical) {
        return false;
    }

    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (isDisabled()) return false;
        if (slider.onKeyPress(keyCode, modifiers)) {
            AuroraConfig.save();
            return true;
        }
        return false;
    }
}
