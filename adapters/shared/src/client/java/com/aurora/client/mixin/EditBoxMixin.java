package com.aurora.client.mixin;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.text.EditHistory;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fully replaces the vanilla {@link EditBox} rendering on every screen
 * that contains a search bar (world-selection, multiplayer, main menu,
 * module detail) with Aurora's themed search bar, with the typed text
 * (or placeholder hint) vertically centered.
 *
 * <p>The injection fires at {@code HEAD} and {@code cancel()}s the
 * vanilla {@code renderWidget} so the default dark box + hairline
 * border never show through. This also fixes the text sitting too high —
 * the vanilla render positions text at a fixed offset that doesn't
 * account for our taller themed background; rendering the text ourselves
 * lets us vertically center it precisely against the theme box.
 *
 * <p>{@code displayPos} (the horizontal scroll offset of the visible text)
 * and {@code cursorPos} (the caret index) support the renderer;
 * {@code highlightPos} preserves selection direction in editing history.
 * The input hooks reuse the same screen/config takeover as the renderer.
 * The blinking caret uses {@link System#currentTimeMillis()} instead of
 * shadowing the vanilla {@code frame} counter, avoiding a fragile
 * private-field dependency.
 *
 * <p><b>Depth pass (2026-11-17):</b> on a unified screen EVERY field —
 * search included (owner report 2026-11-17: search bars were still flat
 * translucent glass while every other field was depressed) — renders as
 * the opaque inset well ({@code MaterialSurface.inset}); the bevel
 * defines the edge, so no resting outline draws. The trailing magnifier,
 * the text window, caret, selection and the Aurora text-window hooks are
 * unchanged. On a non-unified screen the field keeps its RAISED glass
 * look (or the flat themed fill + outline on decline) exactly as before.
 * This is the ONE canonical search-bar implementation — Particles, Item
 * Scale, ResourcePacks and the Modules grid all render through it.
 */
@Mixin(EditBox.class)
public abstract class EditBoxMixin implements GlassEditBox {

    /** Horizontal scroll offset of the first visible character. */
    @Shadow private int displayPos;
    /** Caret index within the value string. */
    @Shadow private int cursorPos;
    /** The other end of the selection; equal to cursorPos when collapsed. */
    @Shadow private int highlightPos;
    /** Placeholder hint text shown when the field is empty. */
    @Shadow private Component hint;

    /**
     * Glass-pass bookkeeping (same scheme as {@code Button}): the frame in
     * which {@link #aurora$renderGlassPass} painted this field's surface and
     * whether the glass drew. In that frame {@code renderWidget} paints
     * content only; otherwise it paints the surface in place (legacy order).
     */
    @Unique private long aurora$glassPassFrame = -1L;
    @Unique private boolean aurora$glassPassDrew = false;

    /**
     * Take over rendering on vanilla screens that got Aurora search bars
     * via mixins, and on every Aurora screen marked with ThemedScreen — so
     * ALL of the mod's text fields (search bars, hex fields, inline rename
     * rows, the map's waypoint prompt) share this one canonical themed
     * text-field implementation.
     */
    @Unique
    private static boolean aurora$themedScreen(Minecraft mc) {
        if (!AuroraConfig.get().customTitleScreen) return false;
        if (mc == null || com.aurora.client.adapter.NativeUi.screen(mc) == null) return false;
        return com.aurora.client.adapter.NativeUi.screen(mc) instanceof JoinMultiplayerScreen
                || com.aurora.client.adapter.NativeUi.screen(mc) instanceof SelectWorldScreen
                || com.aurora.client.adapter.NativeUi.screen(mc) instanceof com.aurora.client.ui.component.ThemedScreen;
    }

    /** Vanilla's drawn-text origin, rewritten below so clicks map to Aurora's text position. */
    @Shadow private int textX;

    /** Aurora's text inset from the field's left edge (vanilla's bordered inset is 4). */
    @Unique private static final int TEXT_LEFT = 8;

    /**
     * Search fields carry a trailing magnifier (2026-11-14) so an empty field reads as search,
     * not a bare input; the text window stops short of the glyph.
     */
    @Unique
    private boolean aurora$search() {
        return MaterialSurface.unified() && hint != null && hint.getString().startsWith("Search");
    }

    /** Right edge of Aurora's text window, relative to the field's left edge. */
    @Unique
    private int aurora$textRight() {
        return ((EditBox) (Object) this).getWidth() - 4 - (aurora$search() ? 14 : 0);
    }

    /**
     * Vanilla scrolls ({@code scrollTo}) and maps clicks to characters through its own text
     * window: origin {@code x + 4}, width {@code getInnerWidth()}. Aurora draws from
     * {@code x + 8} and stops before the search glyph, so on Aurora fields both follow
     * Aurora's window; otherwise a long value scrolled its last characters under the clip
     * and clicks landed half a glyph off (2026-11-16).
     */
    @Inject(method = "getInnerWidth", at = @At("RETURN"), cancellable = true)
    private void aurora$innerWidth(CallbackInfoReturnable<Integer> cir) {
        if (aurora$themedScreen(Minecraft.getInstance())) cir.setReturnValue(Math.max(1, aurora$textRight() - TEXT_LEFT));
    }

    @Inject(method = "updateTextPosition", at = @At("TAIL"))
    private void aurora$textOrigin(CallbackInfo ci) {
        if (aurora$themedScreen(Minecraft.getInstance())) textX = ((EditBox) (Object) this).getX() + TEXT_LEFT;
    }

    // Allocated only on input, never from the render path. KTextField inherits these hooks.
    @Unique private EditHistory aurora$history;
    @Unique private EditHistory.State aurora$beforeEdit;
    @Unique private EditHistory.Kind aurora$editKind;
    @Unique private boolean aurora$restoring;

    @Unique
    private EditHistory.State aurora$textState() {
        return new EditHistory.State(((EditBox) (Object) this).getValue(), cursorPos, highlightPos);
    }

    @Unique
    private boolean aurora$canEdit() {
        EditBox self = (EditBox) (Object) this;
        return aurora$themedScreen(Minecraft.getInstance()) && self.isVisible() && self.canConsumeInput();
    }

    @Unique
    private void aurora$beginEdit(EditHistory.Kind kind) {
        if (aurora$history == null) aurora$history = new EditHistory();
        aurora$beforeEdit = aurora$textState();
        aurora$editKind = kind;
    }

    @Unique
    private void aurora$finishEdit() {
        if (aurora$beforeEdit == null) return;
        aurora$history.record(aurora$beforeEdit, aurora$textState(), aurora$editKind,
                System.nanoTime() / 1_000_000L);
        aurora$beforeEdit = null;
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void aurora$historyKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!aurora$canEdit()) {
            if (aurora$history != null) aurora$history.clear();
            aurora$beforeEdit = null;
            return;
        }
        boolean undo = com.aurora.client.adapter.NativeKeyboard.isUndoKey(event);
        boolean redo = com.aurora.client.adapter.NativeKeyboard.isRedoKey(event) && !event.hasShiftDown();
        if (event.hasControlDownWithQuirk() && !event.hasAltDown() && (undo || redo)) {
            if (aurora$history != null) {
                aurora$restore(redo || event.hasShiftDown() ? aurora$history.redo() : aurora$history.undo());
            }
            // Consume even empty history: the screen must not treat this as a command.
            cir.setReturnValue(true);
            return;
        }
        // A key transaction captures deletion, cut and paste as individual steps. Vanilla still
        // owns the operation, including platform modifiers, filtering and max-length handling.
        if (event.isPaste() || event.isCut()
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_DELETE) {
            aurora$beginEdit(EditHistory.Kind.ATOMIC);
        } else if (event.isCopy() && aurora$history != null) {
            aurora$history.breakRun();
        }
    }

    @Inject(method = "keyPressed", at = @At("RETURN"))
    private void aurora$historyKeyDone(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        aurora$finishEdit();
    }

    @Inject(method = "charTyped", at = @At("HEAD"))
    private void aurora$historyCharacter(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (aurora$canEdit()) aurora$beginEdit(EditHistory.Kind.TYPING);
    }

    @Inject(method = "charTyped", at = @At("RETURN"))
    private void aurora$historyCharacterDone(CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        aurora$finishEdit();
    }

    @Unique
    private void aurora$restore(EditHistory.State state) {
        if (state == null) return;
        EditBox self = (EditBox) (Object) this;
        aurora$restoring = true;
        try {
            self.setValue(state.text()); // Keep responders (search, colour, etc.) in sync.
            if (self.getValue().equals(state.text())) {
                self.moveCursorTo(state.cursor(), false);
                self.setHighlightPos(state.anchor());
            } else {
                // A changed validator or max length rejected the old state; discard stale history.
                aurora$history.clear();
            }
        } finally {
            aurora$restoring = false;
        }
    }

    @Inject(method = "setValue", at = @At("HEAD"))
    private void aurora$programmaticValue(String value, CallbackInfo ci) {
        // Responder normalization during an input transaction belongs to that same edit.
        if (aurora$history != null && aurora$beforeEdit == null && !aurora$restoring) aurora$history.clear();
    }

    @Inject(method = {"setCursorPosition", "setHighlightPos"}, at = @At("HEAD"))
    private void aurora$historyCaret(int position, CallbackInfo ci) {
        if (aurora$history != null && aurora$beforeEdit == null) aurora$history.breakRun();
    }

    @Inject(method = "setFocused", at = @At("HEAD"))
    private void aurora$historyFocus(boolean focused, CallbackInfo ci) {
        if (aurora$history != null && focused != ((EditBox) (Object) this).isFocused()) aurora$history.breakRun();
    }

    /**
     * Pre-dim surface: on non-unified screens the field is a RAISED glass
     * control with the neutral {@code WINDOW_FILL} tint (see
     * {@link GlassEditBox}). Unified screens never take glass — the inset
     * material face paints in the content pass — so this declines there.
     * Painted at the field's current bounds — the screen positions it
     * before calling.
     */
    @Override
    public void aurora$renderGlassPass(GuiGraphicsExtractor ctx) {
        EditBox self = (EditBox) (Object) this;
        aurora$glassPassFrame = GlassSurface.frame();
        // GlassSurface.control declines on unified screens for every field;
        // search fields take the same inset recipe as all other fields
        // (owner report 2026-11-17).
        aurora$glassPassDrew = self.visible && aurora$themedScreen(Minecraft.getInstance())
                && GlassSurface.control(ctx, self.getX(), self.getY(), self.getWidth(), self.getHeight(),
                        AuroraTheme.RADIUS_SMALL);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void aurora$renderCustomStyle(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!aurora$themedScreen(mc)) return;

        EditBox self = (EditBox) (Object) this;
        Font font = mc.font;
        float x = self.getX();
        float y = self.getY();
        float w = self.getWidth();
        float h = self.getHeight();
        boolean focused = self.isFocused();
        String value = self.getValue();

        // --- 1. Background (fully replaces vanilla box) ---
        // Unified screens: EVERY field — search included (owner report
        // 2026-11-17) — is the opaque inset well; the bevel defines the
        // edge, so no resting outline draws over it and the focus cue
        // stays the caret alone. Non-unified screens keep the raised glass
        // contract (or the flat themed fill + outline on decline) exactly
        // as before glass.
        int fillCol = ThemeManager.surfaceColor(focused ? ThemeToken.SURFACE_VARIANT : ThemeToken.SURFACE);
        int borderCol = focused ? AuroraTheme.BORDER_ON_HOVER : AuroraTheme.BORDER_OFF;

        if(MaterialSurface.unified()) {
            fillCol=MaterialSurface.color(ThemeToken.SURFACE_INSET);
            borderCol=MaterialSurface.color(ThemeToken.BORDER_HOVER);
        }
        float radius = AuroraTheme.RADIUS_SMALL;
        boolean opaqueInset = MaterialSurface.unified();
        boolean glassOk = !opaqueInset && (aurora$glassPassFrame == GlassSurface.frame()
                ? aurora$glassPassDrew
                : GlassSurface.control(ctx, x, y, w, h, radius));
        if(opaqueInset) {
            MaterialSurface.inset(ctx,x,y,w,h,radius,!self.isActive());
        } else if (glassOk) {
            // Focus ring on glass — a translucent hairline that brightens
            // the rim without stacking a second surface.
            if (focused && !MaterialSurface.unified()) {
                RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, radius, 1.0f,
                        ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
            }
        } else {
            RenderUtil.drawRoundedRectAA(ctx, x, y, w, h, radius, fillCol);
        }

        // --- 2. Text / hint rendering, vertically centered ---
        int textCol = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int innerX = (int) x + TEXT_LEFT;
        int innerRight = (int) x + aurora$textRight();
        if (aurora$search()) {
            com.aurora.client.ui.util.MaterialIconRenderer.drawIcon(ctx, font, com.aurora.client.screen.IconCatalog.glyph("search"),
                    x + w - 10, y + h / 2f, 10, MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY));
        }
        int textY = (int) (y + (h - font.lineHeight) / 2) + 1; // +1 nudges past baseline offset

        ctx.enableScissor(innerX, (int) y, innerRight, (int) (y + h));
        if (value.isEmpty()) {
            // Placeholder hint text
            if (hint != null) {
                // Secondary text (2026-11-14): quieter than typed primary text, but the muted tone
                // was hard to read on the dark inset field.
                // Plain string: vanilla styles the hint component itself, and a per-character colour
                // overrode this token, leaving the hint a dim fixed grey.
                // Between secondary and muted, so an empty field never reads as holding a query.
                ctx.text(font, hint.getString(), innerX, textY, com.aurora.client.util.AuroraAnim.lerpArgb(
                        MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY),
                        MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), .5f), false);
            }
        } else {
            // Render the value text, scrolled by displayPos so long
            // queries stay visible as the user types.
            int safeDisp = Math.min(displayPos, value.length());
            String visible = value.substring(safeDisp);
            // Selection behind the text, measured over the same visible window as the caret.
            // Shift-selection, Ctrl+A and double-click selected invisibly before (2026-11-16).
            int selStart = Math.max(safeDisp, Math.min(Math.min(cursorPos, highlightPos), value.length()));
            int selEnd = Math.max(safeDisp, Math.min(Math.max(cursorPos, highlightPos), value.length()));
            if (focused && selEnd > selStart) {
                int selX1 = innerX + font.width(value.substring(safeDisp, selStart));
                int selX2 = Math.min(innerRight, innerX + font.width(value.substring(safeDisp, selEnd)));
                // The accent at 40%: the soft row-selection fill vanished on the factory near-black field.
                ctx.fill(selX1, textY - 1, selX2, textY + font.lineHeight,
                        ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x66));
            }
            // From innerX, the origin the caret and selection measure from; this subtracted the
            // scroll index as pixels, so scrolled text drifted left of its caret.
            ctx.text(font, visible, innerX, textY, textCol, false);
        }
        ctx.disableScissor();

        // --- 3. Blinking caret when focused ---
        // Use wall-clock time for the blink cycle (~530ms on, ~530ms off)
        // instead of shadowing the vanilla `frame` counter.
        if (focused && (System.currentTimeMillis() / 530L) % 2L == 0L) {
            // Caret X from the ACTUAL cursor position (mid-string included),
            // measured over the visible window: width of the text between
            // the scroll offset and the cursor.
            int safeDisp = Math.min(displayPos, value.length());
            int safeCursor = Math.max(safeDisp, Math.min(cursorPos, value.length()));
            // One pixel of air before the next glyph (2026-11-14): flush against it, the caret
            // read as part of the placeholder's first letter.
            int caretX = innerX + font.width(value.substring(safeDisp, safeCursor)) - 2;
            int caretY1 = textY - 1;
            int caretY2 = textY + font.lineHeight;
            // Clamp caret inside the box
            caretX = Math.min(caretX, innerRight - 1);
            ctx.fill(caretX, caretY1, caretX + 1, caretY2, textCol);
        }

        // --- 4. Flat-path outline on top (after text) ---
        // The inset bevel defines the unified field's edge; the outline is
        // the legacy flat fallback's chrome only.
        if (!glassOk && !opaqueInset) {
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, h, radius, 1.0f, borderCol);
        }

        // The caret indicates editing; active fields retain the neutral resting edge.
        // Cancel the vanilla render so its background/border never appear.
        ci.cancel();
    }
}
