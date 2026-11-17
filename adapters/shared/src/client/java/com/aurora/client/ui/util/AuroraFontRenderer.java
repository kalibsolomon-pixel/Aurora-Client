package com.aurora.client.ui.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class AuroraFontRenderer {

    private record MetricKey(Font font, net.minecraft.network.chat.Style style) {}
    private static final java.util.Map<MetricKey, TextAlignment.Ink> INK = new java.util.HashMap<>();
    private static net.minecraft.locale.Language metricLanguage;
    private static Font lastMetricFont;
    private static net.minecraft.network.chat.Style lastMetricStyle;
    private static TextAlignment.Ink lastInk;

    /**
     * Measure H/x from the active baked glyphs once per style/resource generation. This uses
     * the actual provider's bearings and raster bounds, so 8px body, 9px regular/heading,
     * 11px title, vanilla fallback and resource-pack fonts all use their own metrics.
     * Effects (underline/shadow) and descenders deliberately do not move the optical axis.
     */
    public static TextAlignment.Ink ink(Font font, net.minecraft.network.chat.Style style) {
        var language = net.minecraft.locale.Language.getInstance();
        if (metricLanguage != language) { INK.clear(); metricLanguage = language; lastInk = null; }
        if (lastInk != null && lastMetricFont == font && lastMetricStyle == style) return lastInk;
        var key = new MetricKey(font, style);
        var measured = INK.get(key);
        if (measured == null) {
            float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
            var sample = Component.literal("Hx").withStyle(style == null
                    ? net.minecraft.network.chat.Style.EMPTY : style.withUnderlined(false));
            font.prepareText(sample.getVisualOrderText(), 0, 0, 0xffffffff, false, false, 0)
                    .visit(new Font.GlyphVisitor() {
                        @Override public void acceptGlyph(net.minecraft.client.gui.font.TextRenderable.Styled glyph) {
                            bounds[0] = Math.min(bounds[0], glyph.top());
                            bounds[1] = Math.max(bounds[1], glyph.bottom());
                        }
                    });
            // Missing/empty providers use vanilla's measured seven-pixel cap box.
            measured = bounds[1] > bounds[0] ? new TextAlignment.Ink(bounds[0], bounds[1]) : new TextAlignment.Ink(0, 7);
            INK.put(key, measured);
        }
        lastMetricFont = font; lastMetricStyle = style; lastInk = measured;
        return measured;
    }

    /** Ink midpoints per active style, keyed by String (equals) or a cached sequence (identity). */
    private static final java.util.Map<net.minecraft.network.chat.Style, java.util.HashMap<Object, Float>> INK_MID = new java.util.HashMap<>();
    private static final java.util.HashMap<Object, Float> INK_MID_VANILLA = new java.util.HashMap<>();
    private static Font inkMidFont;
    private static net.minecraft.locale.Language inkMidLanguage;

    /**
     * Horizontal midpoint of the visible ink of {@code text} (a String or FormattedCharSequence)
     * relative to its draw origin, under the active style: the actual glyph quads' left/right
     * bounds, so side bearings (a leading "I", a trailing "c") no longer shift centred labels
     * the way advance-width centring did. Cached per style and resource generation; the cached
     * path allocates nothing.
     */
    public static float inkMid(Font font, Object text) {
        var language = net.minecraft.locale.Language.getInstance();
        if (inkMidFont != font || inkMidLanguage != language) {
            INK_MID.clear(); INK_MID_VANILLA.clear(); inkMidFont = font; inkMidLanguage = language;
        }
        var style = getActiveStyle();
        var cache = style == null ? INK_MID_VANILLA : INK_MID.computeIfAbsent(style, k -> new java.util.HashMap<>());
        Float cached = cache.get(text);
        if (cached != null) return cached;
        if (cache.size() > 1024) cache.clear();
        FormattedCharSequence sequence = text instanceof FormattedCharSequence seq ? seq
                : FormattedCharSequence.forward(String.valueOf(text), net.minecraft.network.chat.Style.EMPTY);
        if (style != null) sequence = withCustomFont(sequence, style.withUnderlined(false));
        float[] span = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        font.prepareText(sequence, 0, 0, 0xffffffff, false, false, 0).visit(new Font.GlyphVisitor() {
            @Override public void acceptGlyph(net.minecraft.client.gui.font.TextRenderable.Styled glyph) {
                span[0] = Math.min(span[0], glyph.left());
                span[1] = Math.max(span[1], glyph.right());
            }
        });
        float mid = span[1] > span[0] ? (span[0] + span[1]) / 2 : font.width(sequence) / 2f;
        cache.put(text, mid);
        return mid;
    }

    /** Draw x that centres {@code text}'s visible ink on {@code centerX}. */
    public static int inkCenteredX(Font font, Object text, float centerX) {
        return Math.round(centerX - inkMid(font, text));
    }

    /** Minecraft draw origin which centres the active font's visible cap/x-height ink. */
    public static int textY(Font font, float y, float height) {
        return TextAlignment.textY(y, height, ink(font, getActiveStyle()));
    }

    public static int roleTextY(Font font, float y, float height, Role role) {
        return TextAlignment.textY(y, height, ink(font, roleStyle(role)));
    }

    public static int linePitch(Font font) { return font.lineHeight + TextAlignment.DESCRIPTOR_GAP; }
    public static int blockY(Font font, float y, float height, int lines) {
        return TextAlignment.blockY(y, height, lines, ink(font, getActiveStyle()), linePitch(font));
    }

    private static AuroraFontRenderer instance = new AuroraFontRenderer();

    public static AuroraFontRenderer getInstance() {
        return instance;
    }

    private static net.minecraft.network.chat.Style cachedStyle = null;
    /** Everything the cached style depends on, packed (see {@link #styleKey}); -1 = nothing cached. */
    private static int cachedKey = -1;

    /**
     * Aurora's windowed screens (main, feature detail, managers, editors, pickers) draw text
     * with the compact {@code <font>_ui} variant — the same face rasterized at 8 instead of
     * 9 px, so it stays crisp (no pose scaling). The title screen, HUD editor, in-world HUD
     * and vanilla screens keep the regular size. Width measurement goes through the same
     * style, so layout and paint always agree.
     */
    public static boolean compactUi() {
        if (isRenderingAuroraUI) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc != null && com.aurora.client.adapter.NativeUi.screen(mc) instanceof com.aurora.client.ui.component.ThemedScreen themed
                && themed.compactText();
    }
    private static volatile boolean isRenderingAuroraUI = false;

    public static void setRenderingAuroraUI(boolean rendering) {
        isRenderingAuroraUI = rendering;
    }

    public static boolean isRenderingAuroraUI() {
        return isRenderingAuroraUI;
    }

    /**
     * Resolve the active font {@link net.minecraft.network.chat.Style} for the
     * current frame, or {@code null} when no custom font should be applied.
     *
     * <p>Gated by {@link AuroraConfig.TextRendererMode}:
     * <ul>
     *   <li>{@code OFF} — never apply a custom font (vanilla rendering everywhere).</li>
     *   <li>{@code AURORA_ONLY} — apply the custom font only on surfaces owned by
     *       Aurora Client (every Aurora screen — module window, feature detail
     *       screens, color pickers, waypoint manager, HUD editor, title screen,
     *       dropdown overlays — plus the in-world HUD). See {@link #isAuroraSurface()}.</li>
     *   <li>{@code ALL_TEXT} — apply the custom font to <b>every</b> piece of text
     *       rendered in the game, vanilla or Aurora, because {@code MixinFont}
     *       intercepts all {@code Font.drawInBatch} overloads globally.</li>
     * </ul>
     */
    public static net.minecraft.network.chat.Style getActiveStyle() {
        if (com.aurora.client.health.MixinHealth.failed("MixinFont")) return null;
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig.TextRendererMode mode = cfg.textRendererMode;
        if (mode == null) return null;
        switch (mode) {
            case OFF -> {
                // Disabled — vanilla rendering everywhere.
                return null;
            }
            case AURORA_ONLY -> {
                // Only on surfaces owned by Aurora Client.
                if (!isAuroraSurface()) {
                    return null;
                }
            }
            case ALL_TEXT -> {
                // Apply everywhere — no surface gate. MixinFont intercepts
                // every Font.drawInBatch overload globally, so this covers
                // all vanilla AND Aurora text.
            }
        }
        AuroraConfig.FontType active = cfg.activeFont;
        if (active == null || active == AuroraConfig.FontType.SANSSERIF) {
            return null;
        }
        boolean compact = compactUi();
        int key = styleKey(cfg, compact);
        if (key == cachedKey && cachedStyle != null) {
            return cachedStyle;
        }
        String family = familyPath(active);
        if (family == null) return null;
        AuroraConfig.TextWeight weight = cfg.textWeight == null ? AuroraConfig.TextWeight.MEDIUM : cfg.textWeight;
        boolean italic = cfg.textItalic || active == AuroraConfig.FontType.INTER_ITALIC;
        boolean realItalic = italic && hasItalicFile(active);
        // Provider ids follow tools/build_fonts.py: <family>_<weight>[_italic][_ui].
        String path = family + "_" + weight.suffix() + (realItalic ? "_italic" : "") + (compact ? "_ui" : "");
        net.minecraft.network.chat.Style style = net.minecraft.network.chat.Style.EMPTY.withFont(
                new net.minecraft.network.chat.FontDescription.Resource(
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", path)));
        // Families without an italic file slant through Minecraft's own italic.
        if (italic && !realItalic) style = style.withItalic(true);
        if (cfg.textUnderline) style = style.withUnderlined(true);
        cachedStyle = style;
        cachedKey = key;
        return cachedStyle;
    }

    /**
     * Everything the resolved text style depends on, packed into one int:
     * font, weight, italic, underline, case, renderer mode and the compact
     * variant. Label caches fold it into their version keys so a change to
     * any text option re-measures them.
     */
    public static int styleKey() {
        return styleKey(AuroraConfig.get(), compactUi());
    }

    private static int styleKey(AuroraConfig cfg, boolean compact) {
        int key = cfg.activeFont == null ? 0 : cfg.activeFont.ordinal() + 1;
        key = key * 8 + (cfg.textWeight == null ? 1 : cfg.textWeight.ordinal());
        key = key * 4 + (cfg.textCase == null ? 0 : cfg.textCase.ordinal());
        key = key * 4 + (cfg.textRendererMode == null ? 0 : cfg.textRendererMode.ordinal());
        key = key * 2 + (cfg.textItalic ? 1 : 0);
        key = key * 2 + (cfg.textUnderline ? 1 : 0);
        key = key * 16 + AuroraFontQuality.rasterScale(); // a re-raster changes advances
        return key * 2 + (compact ? 1 : 0);
    }

    /** The provider-id stem of a bundled family, or null for the vanilla font. */
    public static String familyPath(AuroraConfig.FontType font) {
        if (font == null) return null;
        return switch (font) {
            case INTER, INTER_ITALIC -> "inter";
            case JETBRAINS_MONO -> "jetbrains_mono";
            case ROBOTO -> "roboto";
            case POPPINS -> "poppins";
            case OSWALD -> "oswald";
            case NUNITO -> "nunito";
            case QUICKSAND -> "quicksand";
            case FIRA_SANS -> "fira_sans";
            case SANSSERIF -> null;
        };
    }

    /** Whether a family ships real italic files (the others slant through Minecraft's italic). */
    public static boolean hasItalicFile(AuroraConfig.FontType font) {
        return font != AuroraConfig.FontType.OSWALD && font != AuroraConfig.FontType.QUICKSAND;
    }

    /** The configured letter case applied to one codepoint. */
    private static int mapCase(int codePoint, AuroraConfig.TextCase textCase) {
        return switch (textCase) {
            case UPPER -> Character.toUpperCase(codePoint);
            case LOWER -> Character.toLowerCase(codePoint);
            default -> codePoint;
        };
    }

    private static AuroraConfig.TextCase activeCase() {
        AuroraConfig.TextCase c = AuroraConfig.get().textCase;
        return c == null ? AuroraConfig.TextCase.NORMAL : c;
    }

    /**
     * {@code text} in the configured letter case, codepoint by codepoint —
     * the same mapping {@link #withCustomFont} applies to sequences, so a
     * string measured here and the sequence drawn from it agree.
     */
    public static String applyCase(String text) {
        AuroraConfig.TextCase textCase = activeCase();
        if (textCase == AuroraConfig.TextCase.NORMAL || text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> sb.appendCodePoint(mapCase(cp, textCase)));
        return sb.toString();
    }

    /**
     * {@code text} in the active font and modifiers but never re-cased: the
     * font is already set, so the font mixin leaves it alone. For labels whose
     * letter case is their meaning (the Case row's Aa / AA / aa).
     */
    public static FormattedCharSequence verbatim(String text) {
        net.minecraft.network.chat.Style style = getActiveStyle();
        return style == null ? Component.literal(text).getVisualOrderText()
                : Component.literal(text).withStyle(style).getVisualOrderText();
    }

    /** A plain string as a component in the active style and case — the one form drawing and measuring share. */
    public static net.minecraft.network.chat.MutableComponent styledLiteral(String text, net.minecraft.network.chat.Style style) {
        return Component.literal(applyCase(text)).withStyle(style);
    }

    /** A component restyled with the active style and case (its own styling kept where the case allows). */
    public static net.minecraft.network.chat.MutableComponent styledCopy(Component component, net.minecraft.network.chat.Style style) {
        if (activeCase() == AuroraConfig.TextCase.NORMAL) return component.copy().withStyle(style);
        return Component.literal(applyCase(component.getString())).withStyle(component.getStyle()).withStyle(style);
    }

    /**
     * Determine whether the current frame is being drawn on a surface owned by
     * Aurora Client. Used by {@link #getActiveStyle()} so {@code AURORA_ONLY}
     * mode applies the configured font across the entire Aurora UI.
     *
     * <p>A surface counts as Aurora-owned when either:
     * <ul>
     *   <li>The {@link #isRenderingAuroraUI} flag is set — used by the HUD
     *       renderers (info, armor, waypoint, alerts, etc.) which don't open
     *       a screen.</li>
     *   <li>The currently open screen belongs to Aurora Client, detected by
     *       its class living anywhere under the {@code com.aurora.client.}
     *       package. This catches <em>every</em> Aurora screen regardless of
     *       sub-package: the module window, feature detail screens, color
     *       pickers, waypoint manager, HUD editor, custom title screen, and
     *       any dropdown overlays they render.</li>
     * </ul>
     */
    private static boolean isAuroraSurface() {
        if (isRenderingAuroraUI) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || com.aurora.client.adapter.NativeUi.screen(mc) == null) return false;
        // Any screen implemented by Aurora Client lives somewhere under
        // com.aurora.client. (screens, ui, hud, etc.). Matching the package
        // root instead of a single sub-package keeps this robust as new
        // screens/components are added anywhere in the mod.
        return com.aurora.client.adapter.NativeUi.screen(mc).getClass().getName().startsWith("com.aurora.client.");
    }

    /**
     * Static wrapper to draw strings. Automatically applies our dynamically configured native custom fonts.
     */
    public static float drawString(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int color, boolean dropShadow) {
        net.minecraft.network.chat.Style style = getActiveStyle();
        
        if (style != null) {
            Component comp = styledLiteral(text, style);
            graphics.text(font, comp.getVisualOrderText(), Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(comp);
        } else {
            graphics.text(font, text, Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(text);
        }
    }

    /**
     * Static wrapper to draw FormattedCharSequence. Automatically applies our dynamically configured native custom fonts.
     */
    public static float drawString(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
        net.minecraft.network.chat.Style style = getActiveStyle();

        if (style != null) {
            FormattedCharSequence styled = withCustomFont(text, style);
            graphics.text(font, styled, Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(styled);
        } else {
            graphics.text(font, text, Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(text);
        }
    }

    /**
     * Helper to extract plain string text from FormattedCharSequence with zero allocation overhead.
     */
    public static String getSequenceString(FormattedCharSequence sequence) {
        StringBuilder sb = new StringBuilder();
        sequence.accept((index, style, codePoint) -> {
            sb.appendCodePoint(codePoint);
            return true;
        });
        return sb.toString();
    }

    /**
     * The font of the empty style — what "this character never specified a
     * font" looks like. Shared by the font-swap logic here and in
     * {@code MixinFont}.
     */
    public static final net.minecraft.network.chat.FontDescription DEFAULT_FONT =
            net.minecraft.network.chat.Style.EMPTY.getFont();

    /**
     * Returns a view of {@code sequence} whose characters carry the custom
     * font from {@code style}, preserving every other style attribute
     * (color, bold, italic, obfuscated, …) exactly as the source text
     * defined it.
     *
     * <p>This is the style-preserving alternative to flattening the sequence
     * through {@link #getSequenceString} and re-wrapping it in a
     * font-only component — that flattening destroyed all per-character
     * styling, which is how command syntax highlighting (vanilla
     * {@code CommandSuggestions} colors the chat input via per-segment
     * {@code Style}s), colored chat messages and any other styled text lost
     * their colors under the custom font.
     *
     * <p>Characters that already specify their own font (e.g. Material
     * Symbols icon glyphs) keep it — overriding those would render the icon
     * codepoints as broken glyphs. Callers that have already skipped
     * sequences carrying any custom font ({@code MixinFont}'s icon guard)
     * never hit that branch; it is defense-in-depth for direct callers.
     */
    public static FormattedCharSequence withCustomFont(FormattedCharSequence sequence, net.minecraft.network.chat.Style style) {
        AuroraConfig.TextCase textCase = activeCase();
        return visitor -> sequence.accept(new net.minecraft.util.FormattedCharSink() {
            // Consecutive characters of one segment share the same Style
            // instance, so a single-slot identity cache keeps the swap at one
            // Style allocation per styled segment instead of per character.
            private net.minecraft.network.chat.Style lastSource = null;
            private net.minecraft.network.chat.Style lastSwapped = null;

            @Override
            public boolean accept(int index, net.minecraft.network.chat.Style charStyle, int codePoint) {
                if (charStyle != lastSource) {
                    lastSource = charStyle;
                    // The active style sets only the font and the Text
                    // options' flags (italic, underline); applyTo keeps the
                    // character's own colour and other attributes.
                    lastSwapped = charStyle.getFont() == DEFAULT_FONT
                            ? style.applyTo(charStyle)
                            : charStyle;
                }
                return visitor.accept(index, lastSwapped,
                        lastSwapped == charStyle ? codePoint : mapCase(codePoint, textCase));
            }
        });
    }

    // ============================================================
    //  Centered text — always non-shadowed
    //
    //  Aurora's custom UI never draws text shadows (mod-wide style rule).
    //  GuiGraphicsExtractor.drawCenteredString has no non-shadow overload (it always
    //  routes through the dropShadow=true path), so centered text goes
    //  through these helpers, which center manually and pass false.
    // ============================================================

    /** Centered, non-shadowed string. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, String text, int cx, int y, int color) {
        graphics.text(font, text, inkCenteredX(font, text, cx), y, color, false);
    }

    /** Centered, non-shadowed styled sequence. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int cx, int y, int color) {
        graphics.text(font, text, inkCenteredX(font, text, cx), y, color, false);
    }

    /** Centered, non-shadowed component. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphicsExtractor graphics, Font font, Component text, int cx, int y, int color) {
        drawCentered(graphics, font, text.getVisualOrderText(), cx, y, color);
    }

    /**
     * The shared ellipsis-fit memo: (text, maxW, minLen, {@link #styleKey()})
     * -> fitted, access-order, capped at 1024 entries. Render-thread only,
     * like every fitted-text cache. Sized above one busy frame's distinct
     * labels (buttons, grid summaries, map markers share it): an LRU smaller
     * than the working set misses on every lookup. {@link com.aurora.client.ui.util.TextFit}
     * owns the fitting itself.
     */
    private static final com.aurora.client.ui.util.TextFit.Memo ELLIPSIS_FIT =
            new com.aurora.client.ui.util.TextFit.Memo(1024);

    /**
     * Ellipsis-truncates {@code s} to {@code maxW} pixels: drops trailing
     * text (never below {@code minLen} chars) until {@code s + "…"} fits,
     * then appends the ellipsis. Truncation lands only on extended grapheme
     * cluster boundaries — combining marks, ZWJ emoji sequences, flag pairs
     * and variation selectors are never split — found by binary search over
     * the boundaries (core {@link com.aurora.client.ui.util.TextFit}). The
     * shared memo above makes repeated per-frame calls with the same text,
     * width and text style free; callers no longer need their own cache.
     * The canonical form of the trim loop the manager screens, the pack
     * browser and the keybind pill previously each carried.
     * Not to be confused with the {@code plainSubstrByWidth + "..."}
     * three-dot idiom some rows use — that spelling stays theirs.
     */
    public static String ellipsize(Font font, String s, int maxW, int minLen) {
        return ELLIPSIS_FIT.fit(s, maxW, minLen, styleKey(), font::width);
    }

    /**
     * Text roles above body text (design language §4.1). Hierarchy is carried by a heavier
     * cut of the active family, never by pose scaling: {@code TITLE} takes its own 11 px raster,
     * {@code HEADING} the regular 9 px one, a step above the compact 8 px body. A sequence that names its own font is left alone by {@code MixinFont}, so the
     * role survives the global font swap.
     */
    public enum Role { TITLE, HEADING }

    private static final net.minecraft.network.chat.Style[] roleStyles = new net.minecraft.network.chat.Style[Role.values().length];
    private static final int[] roleKeys = {-1, -1};

    /** The style of {@code role} for the current frame; vanilla bold when no custom font applies. */
    public static net.minecraft.network.chat.Style roleStyle(Role role) {
        net.minecraft.network.chat.Style base = getActiveStyle();
        if (base == null) return net.minecraft.network.chat.Style.EMPTY.withBold(true);
        AuroraConfig cfg = AuroraConfig.get();
        int key = styleKey(cfg, compactUi());
        if (roleKeys[role.ordinal()] == key) return roleStyles[role.ordinal()];
        AuroraConfig.TextWeight weight = cfg.textWeight == null ? AuroraConfig.TextWeight.MEDIUM : cfg.textWeight;
        AuroraConfig.TextWeight heavier = switch (weight) {
            case REGULAR, MEDIUM -> AuroraConfig.TextWeight.SEMIBOLD;
            case SEMIBOLD, BOLD -> AuroraConfig.TextWeight.BOLD;
        };
        String family = familyPath(cfg.activeFont);
        boolean italic = cfg.textItalic && hasItalicFile(cfg.activeFont);
        // TITLE has its own 11 px raster (tools/build_fonts.py PROVIDER_SIZES); HEADING takes the
        // regular 9 px raster, one step above compact 8 px body text (2026-11-14).
        String size = role == Role.TITLE ? "_title" : "";
        String path = family + "_" + heavier.suffix() + (italic ? "_italic" : "") + size;
        net.minecraft.network.chat.Style style = base.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", path)));
        roleStyles[role.ordinal()] = style;
        roleKeys[role.ordinal()] = key;
        return style;
    }

    /** {@code text} in the configured case and the style of {@code role}: draw and measure this one value. */
    public static Component role(String text, Role role) {
        return styledLiteral(text, roleStyle(role));
    }

    private static final TextFit.Memo ROLE_FIT = new TextFit.Memo(128);

    /** Fit role text using the same font, weight and case which will actually be drawn. */
    public static String ellipsizeRole(Font font, String text, int width, Role role) {
        return ROLE_FIT.fit(text, width, 0, styleKey() * 2 + role.ordinal(),
                value -> font.width(role(value, role)));
    }

    // Keep compatibility wrappers
    public float draw(GuiGraphicsExtractor graphics, String text, float x, float y, int color, boolean dropShadow) {
        return drawString(graphics, Minecraft.getInstance().font, text, x, y, color, dropShadow);
    }

    public void close() {
        // No-op for compatibility
    }
}
