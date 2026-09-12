package com.aurora.client.ui.util;

import com.aurora.client.config.AuroraConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class AuroraFontRenderer {

    private static AuroraFontRenderer instance = new AuroraFontRenderer();

    public static AuroraFontRenderer getInstance() {
        return instance;
    }

    private static net.minecraft.network.chat.Style cachedStyle = null;
    private static AuroraConfig.FontType cachedFontType = null;
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
        if (active == cachedFontType && cachedStyle != null) {
            return cachedStyle;
        }
        String path = null;
        switch (active) {
            case INTER -> path = "inter";
            case INTER_ITALIC -> path = "inter_italic";
            case JETBRAINS_MONO -> path = "jetbrains_mono";
            case ROBOTO -> path = "roboto";
            case POPPINS -> path = "poppins";
            case OSWALD -> path = "oswald";
            case NUNITO -> path = "nunito";
            case QUICKSAND -> path = "quicksand";
            case FIRA_SANS -> path = "fira_sans";
        }
        if (path != null) {
            net.minecraft.network.chat.FontDescription fontDesc = new net.minecraft.network.chat.FontDescription.Resource(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("aurora", path)
            );
            cachedStyle = net.minecraft.network.chat.Style.EMPTY.withFont(fontDesc);
            cachedFontType = active;
            return cachedStyle;
        }
        return null;
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
        if (mc == null || mc.screen == null) return false;
        // Any screen implemented by Aurora Client lives somewhere under
        // com.aurora.client. (screens, ui, hud, etc.). Matching the package
        // root instead of a single sub-package keeps this robust as new
        // screens/components are added anywhere in the mod.
        return mc.screen.getClass().getName().startsWith("com.aurora.client.");
    }

    /**
     * Static wrapper to draw strings. Automatically applies our dynamically configured native custom fonts.
     */
    public static float drawString(GuiGraphics graphics, Font font, String text, float x, float y, int color, boolean dropShadow) {
        net.minecraft.network.chat.Style style = getActiveStyle();
        
        if (style != null) {
            Component comp = Component.literal(text).withStyle(style);
            graphics.drawString(font, comp.getVisualOrderText(), Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(comp);
        } else {
            graphics.drawString(font, text, Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(text);
        }
    }

    /**
     * Static wrapper to draw FormattedCharSequence. Automatically applies our dynamically configured native custom fonts.
     */
    public static float drawString(GuiGraphics graphics, Font font, FormattedCharSequence text, float x, float y, int color, boolean dropShadow) {
        net.minecraft.network.chat.Style style = getActiveStyle();

        if (style != null) {
            FormattedCharSequence styled = withCustomFont(text, style);
            graphics.drawString(font, styled, Math.round(x), Math.round(y), color, dropShadow);
            return x + font.width(styled);
        } else {
            graphics.drawString(font, text, Math.round(x), Math.round(y), color, dropShadow);
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
        net.minecraft.network.chat.FontDescription font = style.getFont();
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
                    lastSwapped = charStyle.getFont() == DEFAULT_FONT
                            ? charStyle.withFont(font)
                            : charStyle;
                }
                return visitor.accept(index, lastSwapped, codePoint);
            }
        });
    }

    // ============================================================
    //  Centered text — always non-shadowed
    //
    //  Aurora's custom UI never draws text shadows (mod-wide style rule).
    //  GuiGraphics.drawCenteredString has no non-shadow overload (it always
    //  routes through the dropShadow=true path), so centered text goes
    //  through these helpers, which center manually and pass false.
    // ============================================================

    /** Centered, non-shadowed string. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphics graphics, Font font, String text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** Centered, non-shadowed styled sequence. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphics graphics, Font font, FormattedCharSequence text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /** Centered, non-shadowed component. {@code cx} is the center x. */
    public static void drawCentered(GuiGraphics graphics, Font font, Component text, int cx, int y, int color) {
        drawCentered(graphics, font, text.getVisualOrderText(), cx, y, color);
    }

    /**
     * Ellipsis-truncates {@code s} to {@code maxW} pixels: drops trailing
     * characters (never below {@code minLen}) until {@code s + "…"} fits,
     * then appends the ellipsis. Returns {@code s} unchanged if it already
     * fits. The canonical form of the trim loop the manager screens, the
     * pack browser and the keybind pill previously each carried; callers
     * with per-frame text wrap their own memo cache around it (the loop's
     * {@code font.width()} per removed character is the expensive part).
     * Not to be confused with the {@code plainSubstrByWidth + "..."}
     * three-dot idiom some rows use — that spelling stays theirs.
     */
    public static String ellipsize(Font font, String s, int maxW, int minLen) {
        if (font.width(s) <= maxW) return s;
        String ell = "…";
        while (s.length() > minLen && font.width(s + ell) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ell;
    }

    // Keep compatibility wrappers
    public float draw(GuiGraphics graphics, String text, float x, float y, int color, boolean dropShadow) {
        return drawString(graphics, Minecraft.getInstance().font, text, x, y, color, dropShadow);
    }

    public void close() {
        // No-op for compatibility
    }
}