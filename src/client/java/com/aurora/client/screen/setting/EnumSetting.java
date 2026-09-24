package com.aurora.client.screen.setting;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Phase B ROLLOUT (2026-09-16): the pilot's canonical state treatment is
 * this component's only behavior — every production enum row (all 26
 * construct here; there is no mock/dev consumer) runs it, and the pilot's
 * opt-in mechanism is retired with the flag, the Button/ToggleSwitch end
 * state (Slider keeps its flag only because the ThemePreview mock
 * constructs it directly — Enum has no such consumer). The trigger models
 * OVERLAPPING state channels, not one exclusive enum:
 * <ul>
 *   <li><b>selected value</b> — persistent data (the config-backed getter);
 *       never conflated with hover or expanded.</li>
 *   <li><b>hover</b> — {@link HoverAnim#symmetric}(140), the pointer's
 *       relationship with the trigger pill ONLY. The pre-pilot code pinned
 *       the hover target at 1 while expanded ({@code hover || expanded});
 *       that conflation is gone: with the popup open and the pointer down
 *       in the option list, the pill eases back to its REST surface while
 *       still visibly owning the popup (below). Settled-hover endpoints
 *       are unchanged; only the pin and the path animate.</li>
 *   <li><b>expanded</b> — popup ownership, persistent until dismissal or
 *       selection. Distinct treatment: the EXISTING chevron indicator (it
 *       already flips down-arrow/up-arrow) takes the accent color for the
 *       whole time the popup is open — the design language §11.7 expanded
 *       state, expressed through the indicator the component already has
 *       (no new icon), readable over glass and flat alike (content glyph,
 *       never a tint change). Independent of hover: expanded + not hovered
 *       reads as rest surface + accent chevron + the open popup.</li>
 *   <li><b>focused</b> — vanilla child focus on this row's
 *       {@link SemanticActionControl}, mirrored into the paint as the
 *       Button-family focus hairline (1 px accent outline on the pill
 *       rect) — visible without hover, distinct from the expanded
 *       treatment (hairline around the pill vs. accent chevron inside
 *       it). <b>Host-dependent:</b> the control materializes only when
 *       the host asks for it ({@link #interactionControl()} —
 *       {@code FeatureDetailScreen} and, since Phase C-2,
 *       {@code AuroraScreen}'s inline Settings tab both do). Keyboard
 *       dismissal also routes through the setting focus registry while the
 *       popup is expanded.</li>
 *   <li><b>disabled</b> — unchanged: inset fill, dim text, no open, no
 *       selection, keyboard rejected, disabled narration exposed.</li>
 * </ul>
 *
 * <p>Input semantics (the narrow Phase A adapter): trigger activation —
 * pointer or Enter/Space — routes through the semantic action where one
 * exists (exactly-once vanilla UI click on open/close, §11.7; the
 * pointer/open/close path was silent before the pilot — an intended
 * change). Escape while expanded collapses the popup WITHOUT closing the
 * screen (the pre-pilot path let Escape fall through to vanilla, closing
 * the whole screen — a correctness defect once keyboard activation
 * exists); Up/Down while expanded scroll the option list (the popup's
 * existing wheel behavior, mapped 1:1 — full keyboard VALUE selection
 * stays a dedicated semantic-control pass). Option selection and
 * outside-click dismissal keep their exact pre-pilot semantics (silent,
 * consumed).
 *
 * <p>Popup rows are deliberately NOT migrated: they are a selection-list
 * family (persistent accent text = committed value, immediate wash =
 * transient hover), classified for later Phase B review. Cache note: the
 * enum renders FULLY LIVE (no shape layer, constant fingerprint); expanded
 * state changes the row height, which is already an owning-screen cache
 * version input, so no fingerprint changes are needed.
 */
public class EnumSetting<E extends Enum<E>> extends FeatureSetting {
    private static final int CONTROL_H = 28;
    private static final int BTN_W = 100; // slightly wider to accommodate arrow indicators
    private static final int BTN_H = 18;
    private static final int OPT_H = 16;  // height of individual dropdown option

    private static final net.minecraft.network.chat.Style SYMBOL_STYLE = net.minecraft.network.chat.Style.EMPTY
            .withFont(new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", "material_symbols")
            ));

    private final Supplier<E> getter;
    private final Consumer<E> setter;
    private final E[] values;
    private Function<E, String> labelFn = null;

    /**
     * Glass rollout: the closed dropdown button renders as NEUTRAL raised
     * glass by default — the button family's secondary look, the same
     * contract as {@code Button#glassBackground} (the glass rim replaces
     * the outline; the tint is {@code WINDOW_FILL}, whose alpha carries the
     * theme's Background Opacity). Pass {@code false} to force the flat
     * look. The expanded option popup is also raised
     * glass (a floating panel above other content) with the same neutral
     * tint — see the expanded block in {@link #render}.
     */
    private boolean glassButton = true;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * the frame in which {@link #renderGlassPass} painted this row's trigger
     * pill pre-dim, and whether it drew. In that frame {@link #render}
     * paints content only (or the flat pill if the glass declined);
     * otherwise it paints the surface in place — the legacy order screens
     * not on the structural pass still use, pixel-identical. The expanded
     * popup is deliberately NOT part of the split: it floats above the
     * dimmed screen by design (§9's named above-the-dim case) and keeps
     * painting in place in {@link #render} on every screen.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewButton = false;

    private int lastBtnX, lastBtnY;
    private int lastWidth = 240;

    private String cachedName;
    private int cachedNameW;

    private net.minecraft.util.FormattedCharSequence cachedUpArrow;
    private net.minecraft.util.FormattedCharSequence cachedDownArrow;
    private int cachedUpArrowWidth = -1;
    private int cachedDownArrowWidth = -1;

    private boolean expanded = false;
    private int scrollOffset = 0; // scroll offset for listing max 5 elements

    /** §8.3 canonical hover — symmetric 140 ms, pointer-only target. */
    private final HoverAnim hoverAnim = HoverAnim.symmetric(140L);
    /** Mirrored from the semantic control's vanilla focus each frame. */
    private boolean focusedVisual = false;
    /** Lazy — created when a host asks {@link #interactionControl()} for it. */
    private SemanticActionControl interactionControl;

    public EnumSetting(String label, Class<E> enumClass, Supplier<E> getter, Consumer<E> setter) {
        super(label);
        this.getter = getter;
        this.setter = setter;
        this.values = enumClass.getEnumConstants();
    }

    @Override public EnumSetting<E> description(String desc) { super.description(desc); return this; }
    @Override public EnumSetting<E> description(Supplier<String> desc) { super.description(desc); return this; }

    public EnumSetting<E> valueDescriptions(Function<E, String> fn) {
        super.description(() -> {
            E v = getter.get();
            return v == null ? null : fn.apply(v);
        });
        return this;
    }

    /** Custom option label for the button + dropdown (default: raw enum name). */
    public EnumSetting<E> labels(Function<E, String> fn) {
        this.labelFn = fn;
        this.cachedName = null;
        return this;
    }

    /** Glass — see {@link #glassButton}. Default on; {@code false} forces flat. */
    public EnumSetting<E> glassButton(boolean g) {
        this.glassButton = g;
        return this;
    }

    /**
     * The trigger's hover-anim target: the POINTER only. The pre-rollout
     * code pinned this while expanded ({@code hover || expanded}) — the
     * conflation the pilot removed and the rollout makes permanent. Kept as
     * a method because the invariant is testable headless: expanded must
     * never force the target.
     */
    boolean hoverTarget(boolean pointerOverTrigger, boolean disabled) {
        return pointerOverTrigger && !disabled;
    }

    /** Trigger activation: toggle the popup + keep the focus bookkeeping. */
    private void toggleExpanded() {
        expanded = !expanded;
        if (expanded) {
            requestFocus();
        } else {
            releaseFocus();
        }
    }

    private String displayName(E v) {
        return labelFn != null ? labelFn.apply(v) : v.name();
    }

    /** Truncate long values with an ellipsis (matches the mod's plainSubstrByWidth convention). */
    private static String fit(Font tr, String text, int maxW) {
        if (tr.width(text) <= maxW) return text;
        return tr.plainSubstrByWidth(text, maxW - tr.width("...")) + "...";
    }

    @Override public int baseHeight() {
        int h = CONTROL_H;
        if (expanded) {
            h += Math.min(5, values.length) * OPT_H + 6;
        }
        return h;
    }
    @Override public int height() { return baseHeight() + descriptionHeight(lastWidth); }

    /**
     * Pre-dim surface (the split's surface half — see the glass-pass
     * bookkeeping field). Only the trigger pill: the popup stays a
     * render-painted above-the-dim surface. Geometry derives from the row
     * params the screen passes (the same formula {@link #render} uses), so
     * a pass on a scrolling frame lands exactly where the content will.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        glassPassFrame = GlassSurface.frame();
        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewButton = glassButton && !isDisabled()
                && GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        lastWidth = width;
        Font tr = Minecraft.getInstance().font;
        boolean disabled = isDisabled();
        renderLabelWithTooltip(ctx, label, x + 12, y + (CONTROL_H - tr.lineHeight) / 2,
                AuroraTheme.TEXT_PRIMARY, mouseX, mouseY, disabled);

        // Cache arrow glyphs first (their width reserves space for the label).
        if (cachedUpArrow == null) {
            Component upComp = Component.literal("\uE5CE").withStyle(SYMBOL_STYLE);
            cachedUpArrow = upComp.getVisualOrderText();
            cachedUpArrowWidth = tr.width(upComp);
            Component downComp = Component.literal("\uE5CF").withStyle(SYMBOL_STYLE);
            cachedDownArrow = downComp.getVisualOrderText();
            cachedDownArrowWidth = tr.width(downComp);
        }

        E current = getter.get();
        String raw = current == null ? "—" : displayName(current);
        int arrowReserve = (expanded ? cachedUpArrowWidth : cachedDownArrowWidth) + 8;
        String name = fit(tr, raw, BTN_W - arrowReserve);
        // Value equality, not reference equality — fit() builds a fresh
        // trimmed string every frame, so != never hit and the width was
        // re-measured per frame.
        if (!name.equals(cachedName)) {
            cachedNameW = tr.width(name);
            cachedName = name;
        }

        int btnX = x + width - BTN_W - 14;
        int btnY = y + (CONTROL_H - BTN_H) / 2;
        lastBtnX = btnX;
        lastBtnY = btnY;

        boolean hover = !disabled && Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);
        // Canonical mode drives the animator from the POINTER only; legacy
        // keeps the shipped pin-at-1-while-expanded target.
        float hT = hoverAnim.update(hoverTarget(hover, disabled));

        // Mirror the semantic control's geometry, pointer, and vanilla focus
        // into this row every frame (the BooleanSetting sync discipline) — a
        // focused or hovered-for-narration pill always corresponds to real
        // widget state. A host that never asks keeps it null: canonical
        // visuals without semantic capabilities it has not adopted.
        if (interactionControl != null) {
            interactionControl.setBounds(btnX, btnY, BTN_W, BTN_H);
            interactionControl.updatePointer(mouseX, mouseY);
            focusedVisual = interactionControl.isFocused();
        }

        int fillTint   = disabled ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x22)
                                  : AuroraAnim.lerpArgb(AuroraTheme.PANEL_OFF, AuroraTheme.PANEL_OFF_HOVER, hT);
        int borderTint = disabled ? ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x33)
                                  : AuroraAnim.lerpArgb(AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_OFF_HOVER, hT);
        int textColor  = disabled ? AuroraTheme.TEXT_DIM : AuroraAnim.lerpArgb(AuroraTheme.TEXT_SECONDARY, AuroraTheme.TEXT_PRIMARY, hT);

        // Glass: raised glass + neutral tint replace the fill + outline —
        // the glass rim replaces the border, no double outline. Disabled
        // rows keep the flat look. On decline (menu context, screenshot
        // suppression, failure) the complete flat button returns — the same
        // fallback contract every glass integration uses. Hover keeps the
        // text-color cue; the tint stays constant, exactly like every other
        // glass control. If the screen ran this row's glass pass this frame
        // the surface is already on screen UNDER the dim and only its
        // result matters here; otherwise (legacy frame order) it is painted
        // in place now — through the shared GlassSurface helper, whose
        // no-pass path makes the identical calls the raw idiom made.
        boolean glassOk;
        if (glassPassFrame == GlassSurface.frame()) {
            glassOk = passDrewButton;
        } else if (glassButton && !disabled) {
            float glassR = Math.min(BTN_H / 2f, ThemeManager.current().roundness().radiusSmall());
            glassOk = GlassSurface.control(ctx, btnX, btnY, BTN_W, BTN_H, glassR);
        } else {
            glassOk = false;
        }
        if (!glassOk) {
            RenderUtil.drawSquircle(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, fillTint);
            RenderUtil.drawSquircleOutline(ctx, btnX, btnY, BTN_W, BTN_H, AuroraTheme.RADIUS_SMALL, 1.0f, borderTint);
        }

        // Focus treatment: the Button-family hairline (1 px accent outline
        // on the pill rect) — the geometry-following provisional focus
        // family. Drawn in the content pass over glass and flat alike;
        // hue/geometry-distinct from the expanded chevron treatment below.
        if (focusedVisual) {
            RenderUtil.drawRoundedOutlineAA(ctx, btnX, btnY, BTN_W, BTN_H,
                    AuroraTheme.RADIUS_SMALL, 1.0f,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ACCENT), 0x99));
        }

        // Draw the option name centered within the space left of the arrow.
        int textX = btnX + (BTN_W - arrowReserve - cachedNameW) / 2;
        int textY = btnY + (BTN_H - tr.lineHeight) / 2 + 1;
        ctx.drawString(tr, name, textX, textY, textColor, false);

        // Draw the Material Symbols arrow icon on the right. Rendered at the
        // font's natural em size, but through MaterialIconRenderer so the
        // glyph is rasterized at the device's true pixel grid (the shared
        // atlas is point-sampled and washes chevron strokes out at GUI
        // scales where 11 GUI units ≠ a texel multiple). Slot metrics are
        // unchanged, so layout and hit area are identical.
        int arrowW = expanded ? cachedUpArrowWidth : cachedDownArrowWidth;
        int arrowX = btnX + BTN_W - arrowW - 4; // 4px margin from right edge
        int arrowY = btnY + (BTN_H - tr.lineHeight) / 2 + 1; // centered vertically exactly with text
        // Expanded treatment: the existing chevron indicator (it already
        // flips direction) takes the accent color for the whole time the
        // popup is open — persistent, independent of hover, readable on
        // glass and flat alike, the same accent role the popup's selected
        // row reads.
        int arrowColor = expanded ? AuroraTheme.TEXT_ACCENT : textColor;
        MaterialIconRenderer.drawIcon(ctx, tr, expanded ? "\uE5CE" : "\uE5CF",
                arrowX + arrowW / 2f, arrowY + tr.lineHeight / 2f,
                MaterialIconRenderer.NATURAL_EM_GUI, arrowColor);

        if (expanded) {
            int visibleCount = Math.min(5, values.length);
            int dropdownH = visibleCount * OPT_H + 4;
            // C-8: placement resolves against the host band — the popup
            // flips UP when opening down would clip its option rows at the
            // band's bottom edge. One truth for paint and input
            // (dropdownY); every consumer below derives from it.
            int dropdownY = dropdownY(btnY, dropdownH);

            // Glass: the expanded option list is a floating panel above other
            // content — RAISED glass with the neutral WINDOW_FILL tint (never
            // stained: no individual list row is a selected/primary element;
            // the current value reads through its accent text color, hover
            // through the plain SURFACE_VARIANT wash — no per-row glass). The
            // popup is an ABOVE-THE-DIM surface by design (§9's named case:
            // it must float above the dimmed screen), so it paints here in
            // the content pass through GlassSurface.aboveDimControl — the
            // identical idiom, in place, exempt from the glass-pass ordering
            // — on migrated and legacy screens alike. On decline the flat
            // panel returns — the same fallback contract every glass
            // integration uses.
            float popR = Math.min(dropdownH / 2f, ThemeManager.current().roundness().radiusSmall());
            boolean popGlass = glassButton && GlassSurface.aboveDimControl(ctx, btnX, dropdownY, BTN_W, dropdownH, popR);
            if (popGlass) {
                RenderUtil.drawRoundedRectAA(ctx, btnX, dropdownY, BTN_W, dropdownH, popR,
                        ThemeManager.color(ThemeToken.WINDOW_FILL));
                BlurPanelRenderer.drawRimFinish(ctx, btnX, dropdownY, BTN_W, dropdownH, popR);
            } else {
                RenderUtil.drawSquircle(ctx, btnX, dropdownY, BTN_W, dropdownH, AuroraTheme.RADIUS_SMALL, ThemeManager.surfaceColor(ThemeToken.SURFACE));
                RenderUtil.drawSquircleOutline(ctx, btnX, dropdownY, BTN_W, dropdownH, AuroraTheme.RADIUS_SMALL, 1.0f, borderTint);
            }

            for (int vIdx = 0; vIdx < visibleCount; vIdx++) {
                int actualIdx = scrollOffset + vIdx;
                if (actualIdx >= 0 && actualIdx < values.length) {
                    E val = values[actualIdx];
                    String optName = fit(tr, displayName(val), BTN_W - 10);
                    int optY = dropdownY + 2 + vIdx * OPT_H;

                    boolean optHover = Widget.inBounds(mouseX, mouseY, btnX, optY, BTN_W, OPT_H);

                    if (optHover) {
                        ctx.fill(btnX + 2, optY, btnX + BTN_W - 2, optY + OPT_H, ThemeManager.surfaceColor(ThemeToken.SURFACE_VARIANT));
                    }

                    int optTextX = btnX + (BTN_W - tr.width(optName)) / 2;
                    int optTextY = optY + (OPT_H - tr.lineHeight) / 2 + 1;
                    int optColor = optHover ? AuroraTheme.TEXT_PRIMARY : AuroraTheme.TEXT_SECONDARY;

                    if (val == current) {
                        optColor = AuroraTheme.TEXT_ACCENT;
                    }

                    ctx.drawString(tr, optName, optTextX, optTextY, optColor, false);
                }
            }

            // Draw scrollbar if there are more than 5 options
            if (values.length > 5) {
                int scrollbarX = btnX + BTN_W - 4;
                int scrollbarY = dropdownY + 2;
                int scrollbarH = dropdownH - 4;
                int thumbH = (int) (scrollbarH * (5.0 / values.length));
                int thumbY = scrollbarY + (int) ((scrollbarH - thumbH) * ((double) scrollOffset / (values.length - 5)));
                ctx.fill(scrollbarX, thumbY, scrollbarX + 2, thumbY + thumbH,
                        ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x66));
            }
        }

        renderDescription(ctx, x, y + baseHeight(), width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (isDisabled()) return false;
        if (button != 0) return false;

        int btnX = rowX + rowWidth - BTN_W - 14;
        int btnY = rowY + (CONTROL_H - BTN_H) / 2;

        boolean clickedButton = Widget.inBounds(mouseX, mouseY, btnX, btnY, BTN_W, BTN_H);

        if (clickedButton) {
            // Semantic host (the detail screen marks the control available
            // and runs the lifecycle): the semantic action owns activation —
            // exactly-once behavior + the §11.7 activation click on open and
            // close, pointer and keyboard alike. Hosts that never ask keep
            // the direct silent path; AuroraScreen has asked since C-2.
            if (interactionControl != null && interactionControl.isAvailable()) {
                return interactionControl.activateFromPointer(mouseX, mouseY, button);
            }
            toggleExpanded();
            return true;
        }

        if (expanded) {
            int visibleCount = Math.min(5, values.length);
            int dropdownH = visibleCount * OPT_H + 4;
            // C-8: placement resolves against the host band — the popup
            // flips UP when opening down would clip its option rows at the
            // band's bottom edge. One truth for paint and input
            // (dropdownY); every consumer below derives from it.
            int dropdownY = dropdownY(btnY, dropdownH);

            boolean clickedDropdown = Widget.inBounds(mouseX, mouseY, btnX, dropdownY, BTN_W, dropdownH);

            if (clickedDropdown) {
                int clickedIndex = (int) ((mouseY - (dropdownY + 2)) / OPT_H);
                if (clickedIndex >= 0 && clickedIndex < visibleCount) {
                    int actualIdx = scrollOffset + clickedIndex;
                    if (actualIdx >= 0 && actualIdx < values.length) {
                        setter.accept(values[actualIdx]);
                        com.aurora.client.config.AuroraConfig.save();
                    }
                }
                expanded = false;
                releaseFocus();
                return true;
            } else {
                // Outside click collapses the popup AND consumes the click —
                // letting it fall through would act on whatever renders
                // underneath the now-dismissed dropdown.
                expanded = false;
                releaseFocus();
                return true;
            }
        }

        return false;
    }

    /**
     * Reset popup state on screen close so the next visit starts collapsed
     * and this row cannot hold the static focus registry across screens —
     * same lifecycle contract as KeyListSetting/ParticleConfigSetting.
     */
    @Override
    public void onDetailScreenClose() {
        expanded = false;
        scrollOffset = 0;
        releaseFocus();
    }

    /**
     * The resolved popup top for the current frame (C-8) — the ONE
     * placement truth the render, the option hover, the click walk and the
     * wheel all derive from. Deterministic, geometry-only:
     * <ol>
     *   <li>preferred: below the trigger ({@code btnY + BTN_H + 2}) when
     *       the whole popup fits before the host band's bottom edge;</li>
     *   <li>otherwise above ({@code btnY - 2 - dropdownH}) when the whole
     *       popup fits below the band's top edge;</li>
     *   <li>otherwise the side with more usable space, clamped into the
     *       band (the popup's own scroll handles oversized lists; the
     *       scissor clips whatever a tiny band cannot show).</li>
     * </ol>
     * The band is the OWNING host's row clip ({@link FeatureSetting#hostBand()}
     * — FeatureDetailScreen's fade boundary / AuroraScreen's content
     * viewport), not the raw screen: a popup that "fits the screen" but
     * crosses the host's scissor is clipped pixels, exactly the lost-option
     * rows this resolves. Placement only — popup semantics, scrolling,
     * selection, keyboard and sound behavior are unchanged.
     */
    int dropdownY(int btnY, int dropdownH) {
        com.aurora.client.ui.util.ClipBand band = FeatureSetting.hostBand();
        int below = btnY + BTN_H + 2;
        if (below + dropdownH <= band.yEnd()) return below;
        int above = btnY - 2 - dropdownH;
        if (above >= band.y) return above;
        int spaceBelow = band.yEnd() - below;
        int spaceAbove = btnY - 2 - band.y;
        // Neither side fits: the side with more usable space, clamped into
        // the band (both directions — a degenerate tiny band still yields a
        // deterministic, in-band top).
        int belowClamped = Math.max(band.y, Math.min(band.yEnd() - dropdownH, below));
        int aboveClamped = Math.max(band.y, above);
        return spaceBelow >= spaceAbove ? belowClamped : aboveClamped;
    }

    /**
     * The row's semantic control (focus traversal, Enter/Space activation,
     * the activation click, narration with the current value and
     * expanded/collapsed state). Lazy by design: it materializes only when
     * a host asks for it — {@code FeatureDetailScreen} and, since Phase C-2,
     * {@code AuroraScreen}'s inline Settings tab both do at initialization.
     */
    @Override
    public SemanticActionControl interactionControl() {
        if (interactionControl == null) {
            interactionControl = new SemanticActionControl(SemanticAction.button(
                    Component.literal(label),
                    () -> {
                        String description = currentDescription();
                        return description != null
                                ? Component.literal(description)
                                : Component.empty();
                    },
                    () -> {
                        E v = getter.get();
                        return Component.literal((v == null ? "—" : displayName(v))
                                + (expanded ? ", expanded" : ", collapsed"));
                    },
                    () -> !isDisabled(),
                    this::toggleExpanded),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
        }
        return interactionControl;
    }

    /**
     * A popup is an interaction surface owned by its trigger. If the host
     * removes that trigger from the active viewport/tab, collapse the popup
     * and release the setting-level keyboard owner immediately; an invisible
     * popup must not retain Escape/arrow/wheel ownership.
     */
    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (!available && expanded) {
            expanded = false;
            scrollOffset = 0;
            releaseFocus();
        }
    }

    /**
     * Canonical keyboard adapter — the NARROW set the pilot's keyboard path
     * requires, nothing more (full keyboard value selection is a dedicated
     * semantic-control pass):
     * <ul>
     *   <li><b>Escape while expanded</b> collapses the popup and consumes —
     *       without this, Escape falls through to vanilla and closes the
     *       whole screen, a correctness defect once keyboard activation can
     *       open the popup. Collapsed Escape is not consumed (screen close
     *       stays vanilla).</li>
     *   <li><b>Up/Down while expanded</b> scroll the option list ±1 row —
     *       the popup's existing wheel behavior mapped 1:1; consumed only
     *       when the list actually scrolls (&gt;5 options).</li>
     * </ul>
     * Enter/Space stay on the vanilla-child path (the semantic control's
     * selection-key handling) so activation fires exactly once.
     */
    @Override
    public boolean onKeyPress(int keyCode, int modifiers) {
        if (!expanded) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            expanded = false;
            releaseFocus();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            return onScroll(-1);
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            return onScroll(1);
        }
        return false;
    }

    @Override
    public boolean onScroll(double vertical) {
        if (!expanded) return false;
        int maxVisible = 5;
        if (values.length <= maxVisible) return false;

        scrollOffset -= (int) Math.signum(vertical);
        if (scrollOffset < 0) scrollOffset = 0;
        int maxScroll = values.length - maxVisible;
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        return true;
    }

    // Package-private test visibility (Phase B pilot).
    HoverAnim hoverAnimator() { return hoverAnim; }
    boolean expandedState() { return expanded; }
    boolean focusedVisualState() { return focusedVisual; }
}
