package com.aurora.client.util;

/**
 * Single source of truth for Aurora's visual theme — colors, radii, spacing.
 *
 * <p><b>iOS Dark redesign:</b> The palette is the iOS dark-mode system
 * palette with {@code systemBlue} (#0A84FF) as the only accent. The old
 * blue/green/yellow aurora colors have been retired; constant names from
 * the prior palette are preserved as aliases mapped to iOS equivalents so
 * existing call sites continue to compile and read correctly. New code
 * should prefer the iOS-prefixed constants for clarity.
 *
 * <p>Reference: Apple HIG, "Color" — System Colors (Dark).
 *
 * <p>All colors are 32-bit ARGB ({@code 0xAARRGGBB}). Translucent values
 * (alpha < 0xFF) compose over Minecraft's premultiplied-alpha pipeline
 * just like before — they're how iOS achieves its layered-frosted look
 * over the system background.
 */
public final class AuroraTheme {

    private AuroraTheme() {}

    // ============================================================
    //  iOS system palette (dark mode)
    // ============================================================

    /** OnePlus Red Accent — used by misc UI affordances (toggles, links). */
    public static int IOS_BLUE              = 0xFFEB0029;
    /**
     * Default accent for an ENABLED module — the color of its outline and
     * icon tint. Per-module overrides live in
     * {@code com.aurora.client.screen.ModuleAccentColors}.
     */
    public static int MODULE_ACCENT_ON      = 0xFFEB0029;
    /** OnePlus Red pressed (~70 % opacity blend with black). */
    public static int IOS_BLUE_PRESSED      = 0xFF9E0016;
    /** OnePlus Red hover (slightly lighter for hover-only feedback). */
    public static int IOS_BLUE_HOVER        = 0xFFFF2447;

    /**
     * Top stop of the OnePlus Red vertical gradient — slightly brighter
     * tint of {@link #IOS_BLUE}. Used for the upper edge of any blue
     * filled element (button background, switch ON track, slider fill,
     * segmented control selected indicator).
     */
    public static int IOS_BLUE_GRAD_TOP     = 0xFFFF3B58;
    /** Bottom stop of the OnePlus Red vertical gradient — canonical OnePlus Red. */
    public static int IOS_BLUE_GRAD_BOT     = 0xFFEB0029;
    /** Hover-state top stop (same gradient, lifted ~6 % luminance). */
    public static int IOS_BLUE_GRAD_TOP_HOVER = 0xFFFF5C74;
    /** Hover-state bottom stop. */
    public static int IOS_BLUE_GRAD_BOT_HOVER = 0xFFFF2447;

    /** {@code systemGray} → {@code systemGray6} in iOS dark. Used for borders, tracks, fills. */
    public static final int IOS_GRAY              = 0xFF8E8E93;
    public static final int IOS_GRAY_2            = 0xFF555555;
    public static final int IOS_GRAY_3            = 0xFF3E3E3E;
    public static final int IOS_GRAY_4            = 0xFF2E2E2E;
    public static final int IOS_GRAY_5            = 0xFF242424;
    public static final int IOS_GRAY_6            = 0xFF121212;

    /** Page background — OxygenOS dark. Black, full alpha. */
    public static int IOS_SYSTEM_BG         = 0xFF000000;
    /** Card / grouped row background — Charcoal card. */
    public static int IOS_SECONDARY_BG      = 0xFF1A1A1A;
    /** Inset / nested background — Hover card. */
    public static int IOS_TERTIARY_BG       = 0xFF262626;

    /** Translucent fill for off-state controls (e.g. UISwitch off track). */
    public static final int IOS_SYSTEM_FILL       = 0x52787880;
    public static final int IOS_SECONDARY_FILL    = 0x3D787880;
    public static final int IOS_TERTIARY_FILL     = 0x2E787880;

    // ---- Material gradient stops for "dark" surfaces ----
    // Every dark fill in the UI (cards, OFF tracks, secondary buttons,
    // slider bases, detail-screen setting rows) renders as a vertical
    // gradient with a slightly-lifted top stop and a deeper bottom stop.
    // This is what gives the surfaces a material, lit-from-above feel
    // instead of looking flat. Translucent variants are used where the
    // surface must blend over the backdrop.

    /** Opaque material dark gradient — top stop (subtly lifted). */
    public static final int MATERIAL_DARK_TOP      = 0xFF2C2C32;
    /** Opaque material dark gradient — bottom stop (deeper). */
    public static final int MATERIAL_DARK_BOT      = 0xFF15151A;

    /** Translucent OFF-track gradient — top stop (lifted gray). */
    public static final int IOS_SYSTEM_FILL_TOP    = 0x60909098;
    /** Translucent OFF-track gradient — bottom stop (deeper gray). */
    public static final int IOS_SYSTEM_FILL_BOT    = 0x42454550;

    /** Secondary-button background gradient — base, top stop. */
    public static final int IOS_TERTIARY_FILL_TOP  = 0x3A909098;
    /** Secondary-button background gradient — base, bottom stop. */
    public static final int IOS_TERTIARY_FILL_BOT  = 0x28454550;
    /** Secondary-button background gradient — hover, top stop. */
    public static final int IOS_SECONDARY_FILL_TOP = 0x50909098;
    /** Secondary-button background gradient — hover, bottom stop. */
    public static final int IOS_SECONDARY_FILL_BOT = 0x38454550;

    /** Separator line — translucent, blends over varied backgrounds. */
    public static final int IOS_SEPARATOR         = 0x99545458;
    /** Opaque separator — for places where translucency reads wrong. */
    public static final int IOS_OPAQUE_SEPARATOR  = 0xFF38383A;

    /** Primary label text — pure white. */
    public static int IOS_LABEL             = 0xFFFFFFFF;
    /** Secondary label — descriptions, captions ({@code rgba(235,235,245,0.6)}). */
    public static int IOS_SECONDARY_LABEL   = 0x99EBEBF5;
    /** Tertiary label — placeholder, inactive ({@code rgba(235,235,245,0.3)}). */
    public static int IOS_TERTIARY_LABEL    = 0x4DEBEBF5;
    /** Quaternary label — barely-there hints. */
    public static int IOS_QUATERNARY_LABEL  = 0x2EEBEBF5;

    /** {@code systemGreen} (dark) — the success accent. */
    public static final int IOS_GREEN             = 0xFF30D158;
    /** Pressed-state systemGreen. */
    public static final int IOS_GREEN_PRESSED     = 0xFF248A3D;
    /** Hover-state systemGreen. */
    public static final int IOS_GREEN_HOVER       = 0xFF4CD964;
    /** Top stop of the iOS green vertical gradient — slightly brighter tint. */
    public static final int IOS_GREEN_GRAD_TOP    = 0xFF5BE079;
    /** Bottom stop of the iOS green vertical gradient — canonical {@code systemGreen}. */
    public static final int IOS_GREEN_GRAD_BOT    = 0xFF30D158;
    /** Hover-state top stop. */
    public static final int IOS_GREEN_GRAD_TOP_HOVER = 0xFF7BE894;
    /** Hover-state bottom stop. */
    public static final int IOS_GREEN_GRAD_BOT_HOVER = 0xFF4CD964;

    // ============================================================
    //  Green-accent toggle
    //
    //  Single source of truth for whether Aurora's green-accent layer
    //  (success-state highlights on enabled feature tiles, section
    //  captions, etc.) is rendered. Set to {@code false} to revert the
    //  whole UI to the pure-blue palette — every consumer routes its
    //  accent through {@link #accentGradTop(boolean)} /
    //  {@link #accentGradBot(boolean)} / {@link #sectionCaption()} so
    //  one flip removes every green pixel from the UI.
    // ============================================================

    /**
     * Master toggle for the optional green-accent layer. Default {@code true}
     * (green visible on enabled feature tiles + section captions). Flip
     * to {@code false} to remove all green from the UI in one change.
     */
    public static boolean GREEN_ACCENTS_ENABLED = false;

    /**
     * Green-tinted section caption color (when accents are on) or the
     * {@code ON_BACKGROUND_MUTED} token projection (when accents are off).
     * Used by {@link com.aurora.client.screen.setting.SectionHeaderSetting}.
     *
     * <p>Design language §2: section captions must read one of the two
     * sanctioned de-emphasis tokens (SECONDARY or MUTED), never a third
     * ad-hoc level. The old tertiary-label static happened to carry the
     * same 0x4D alpha as the MUTED projection in both factory palettes,
     * so this is token correctness, not a visible change.
     */
    public static int sectionCaption() {
        // 60 % alpha green when enabled; ON_BACKGROUND_MUTED when not.
        return GREEN_ACCENTS_ENABLED ? 0x9930D158 : TEXT_DIM;
    }

    /**
     * Top stop for an "accent" gradient — green when accents are on AND
     * {@code preferGreen} is true (caller is requesting the success
     * variant), otherwise the standard iOS blue gradient top.
     */
    public static int accentGradTop(boolean preferGreen, boolean hover) {
        if (GREEN_ACCENTS_ENABLED && preferGreen) {
            return hover ? IOS_GREEN_GRAD_TOP_HOVER : IOS_GREEN_GRAD_TOP;
        }
        return hover ? IOS_BLUE_GRAD_TOP_HOVER : IOS_BLUE_GRAD_TOP;
    }

    /** Bottom stop for an "accent" gradient — green/blue per the same rule as {@link #accentGradTop}. */
    public static int accentGradBot(boolean preferGreen, boolean hover) {
        if (GREEN_ACCENTS_ENABLED && preferGreen) {
            return hover ? IOS_GREEN_GRAD_BOT_HOVER : IOS_GREEN_GRAD_BOT;
        }
        return hover ? IOS_BLUE_GRAD_BOT_HOVER : IOS_BLUE_GRAD_BOT;
    }

    // ============================================================
    //  Legacy alias surface — preserved so existing callers compile.
    //
    //  Each constant below maps to the iOS equivalent that produces the
    //  closest visual intent in the new design. New code should prefer
    //  the IOS_-prefixed constants above.
    // ============================================================

    // ---- Aurora backdrop ----
    // The page background is an almost-black, very dark blue vertical
    // gradient that slowly breathes between a darker and lighter phase.
    // Two endpoints per stop: the breath lerps `*_DARK` → `*_LIGHT` over a
    // slow sine and the rendered gradient interpolates `TOP_*` → `BOT_*`
    // top-to-bottom.
    /** Backdrop top color, darker phase — near-black with a hint of blue. */
    public static int BACKDROP_TOP_DARK  = 0xFF030610;
    /** Backdrop top color, lighter phase — dark systemBlue tint. */
    public static int BACKDROP_TOP_LIGHT = 0xFF0C1A36;
    /** Backdrop bottom color, darker phase — pure black. */
    public static int BACKDROP_BOT_DARK  = 0xFF000000;
    /** Backdrop bottom color, lighter phase — very dark blue. */
    public static int BACKDROP_BOT_LIGHT = 0xFF050912;

    /** @deprecated kept as alias for {@link #BACKDROP_TOP_DARK} — anything still reading this lands on the darker phase. */
    @Deprecated
    public static final int BACKDROP_TOP = BACKDROP_TOP_DARK;
    /** @deprecated kept as alias for a mid-blue value. */
    @Deprecated
    public static final int BACKDROP_MID = 0xFF050A18;
    /** @deprecated kept as alias for {@link #BACKDROP_BOT_DARK}. */
    @Deprecated
    public static final int BACKDROP_BOT = BACKDROP_BOT_DARK;

    /** @deprecated drifting blob retired; left as zero alpha so any stale call is a no-op. */
    @Deprecated
    public static final int BACKDROP_BLOB = 0x00000000;

    /** Aurora ribbon overlays — retired, kept only as zero-alpha so any stale render is invisible. */
    public static final int AURORA_RIBBON_GREEN = 0x00000000;
    public static final int AURORA_RIBBON_BLUE  = 0x00000000;

    // ============================================================
    //  Windowed-content tokens
    //
    //  Aurora's modules / settings screens now draw a single dark
    //  translucent "window" containing the search bar, tabs, and grid,
    //  in the spirit of Lunar Client's mod menu. The window reads as
    //  having heavier blur than the surrounding world (faked via the
    //  alpha/saturation step between the screen-level OVERLAY_DIM token
    //  and {@link #WINDOW_FILL}); tiles inside the window read as having a
    //  further step of blur via {@link #TILE_FILL}'s translucent
    //  overlay on top of the window.
    // ============================================================

    /**
     * Opaque charcoal fill for the main content window. The earlier
     * translucent navy was contributing real GPU cost (every pixel of
     * the window had to alpha-blend over the dimmed world frame) and
     * users found the modules grid hard to scan against the see-through
     * backdrop. Solid {@code #0A0A0A} reads as a deliberate dark sheet
     * — close to black, slightly lifted off pure-black so it doesn't
     * bleed into the world dim layer underneath.
     */
    public static int WINDOW_FILL    = 0xFF0D0D0D;
    /** Translucent white outline around the content window. */
    public static int WINDOW_OUTLINE = 0x66FFFFFF;
    /**
     * Subtle top-edge highlight applied to dark surfaces (window
     * backdrops, dark panels, card insets). Replaces the previous
     * gradient/sheen system: a single thin bright line at the top of
     * each darker shape, simulating a beam of light catching the upper
     * edge — the cheapest possible "material" cue without any AA or
     * shading band.
     *
     * <p>Around 28 % white. Combined with the surface's own dark fill,
     * this reads as a faint silver rim at the top, fading invisibly
     * against lighter surfaces (where it's not needed).
     */
    public static int WINDOW_HIGHLIGHT = 0x48FFFFFF;
    /**
     * Translucent overlay for a feature tile sitting inside the window —
     * lighter than the window fill, so each tile reads as a discrete
     * card without going opaque. The only solid element on a tile is
     * its outline.
     */
    public static int TILE_FILL          = 0x22FFFFFF;
    /** Hover overlay added on top of {@link #TILE_FILL}. */
    public static int TILE_FILL_HOVER    = 0x18FFFFFF;
    /**
     * Solid flat dark-gray ({@code #2A2A2A}) tile surface at full
     * opacity. Both stops are identical so the gradient call collapses
     * to a single color — kept as TOP/BOT pair for compatibility with
     * the existing {@code AuroraShapes.panelGradient} call site.
     */
    public static int TILE_GRAD_TOP      = 0xFF2A2A2A;
    public static int TILE_GRAD_BOT      = 0xFF2A2A2A;
    /** Translucent white outline around a tile, OFF state. */
    public static int TILE_OUTLINE_OFF   = 0x55FFFFFF;
    /** Translucent white outline around a tile, ON state — slightly brighter. */
    public static int TILE_OUTLINE_ON    = 0xAAFFFFFF;

    // Panel tints — flat iOS card surfaces.
    public static int PANEL_OFF        = IOS_SECONDARY_BG;
    public static int PANEL_OFF_HOVER  = IOS_TERTIARY_BG;
    public static int PANEL_ON               = 0xFFEB0029;
    public static int PANEL_ON_HOVER         = 0xFFFF2447;
    public static int PANEL_INSET      = IOS_GRAY_6;

    // Gem gradient — flat in iOS, both stops the same color.
    public static int GEM_OFF_TOP   = IOS_SECONDARY_BG;
    public static int GEM_OFF_BOT   = IOS_SECONDARY_BG;
    public static int GEM_ON_TOP          = 0xFFEB0029;
    public static int GEM_ON_BOT          = 0xFF9E0016;

    // Borders — replaced by hairline separators in iOS, but a subtle
    // opaque separator remains the default and the accent stays blue.
    public static int BORDER_OFF        = IOS_OPAQUE_SEPARATOR;
    public static int BORDER_OFF_HOVER  = IOS_GRAY_3;
    public static int BORDER_ON               = 0xFFEB0029;
    public static int BORDER_ON_HOVER         = 0xFFFF2447;

    /** Primary accent — system blue. Old "aurora cyan" name preserved. */
    public static int ACCENT_BLUE             = 0xFFEB0029;
    /** Secondary accent — also system blue (no green in iOS palette). */
    public static int ACCENT_GREEN            = 0xFFEB0029;
    /** Tertiary accent — also system blue (no yellow in iOS palette). */
    public static int ACCENT_YELLOW           = 0xFFEB0029;

    // Semantic status colors — token-projected (fixed hues, mode-aware
    // lightness; see ThemeToken.SEMANTIC_*). Written exclusively by
    // ResolvedTheme.project(); the initializers mirror the factory palette.
    public static int SEMANTIC_ERROR   = 0xFFFF453A;
    public static int SEMANTIC_SUCCESS = 0xFF30D158;
    public static int SEMANTIC_WARNING = 0xFFFFC107;

    /**
     * Full-screen dim behind modal/list screens — token-projected (see
     * {@link com.aurora.client.theme.ThemeToken#OVERLAY_DIM}). Alpha-scaled
     * variants derive from this RGB via
     * {@link com.aurora.client.theme.ThemeManager#withAlpha}.
     */
    public static int OVERLAY_DIM = 0x990A0B0D;

    /** Text on {@link #OVERLAY_DIM} — light in BOTH modes (token-projected). */
    public static int ON_OVERLAY = 0xFFFFFFFF;

    // Typography
    public static int TEXT_PRIMARY   = IOS_LABEL;
    public static int TEXT_SECONDARY = IOS_SECONDARY_LABEL;
    public static int TEXT_DIM       = IOS_TERTIARY_LABEL;
    public static int TEXT_ACCENT          = 0xFFEB0029;

    /**
     * Text color on {@link #IOS_SYSTEM_BG} / {@link #IOS_SECONDARY_BG}
     * surfaces — contrast-checked against the surface (Stage 2).
     */
    public static int ON_SURFACE           = 0xFFFFFFFF;
    /**
     * Text color on accent-filled controls (buttons, toggles) —
     * contrast-checked against the accent so any chosen accent stays
     * legible (Stage 2).
     */
    public static int ON_ACCENT            = 0xFFFFFFFF;

    // ============================================================
    //  Token projection (Stage 1 of the COSMIC-style theme rework)
    //
    //  The mutable statics above are no longer self-owned: they are a
    //  cached projection of the resolved semantic tokens and are written
    //  exclusively by ResolvedTheme.project() (invoked from
    //  com.aurora.client.theme.ThemeManager whenever the theme actually
    //  changes). The derivation math that used to live in updateColors()
    //  moved verbatim to com.aurora.client.theme.ThemeResolver.
    //
    //  The field initializers scattered above still mirror the factory
    //  palette so any read that races the first reload sees sane values -
    //  identical to the pre-reload state of the old system.
    // ============================================================

    // ============================================================
    //  Sizes
    // ============================================================

    /**
     * Standard chamfer (corner radius) in pixels. Because corners are
     * rendered as a binary-thresholded staircase of straight-line fills
     * (no AA in the redesign), the only knob for "curve smoothness" is
     * the number of stair steps along the arc, which equals this
     * radius. At {@code 72} a panel corner spans 72 distinct stair
     * steps — at GUI scale 3 that's 216 screen pixels of arc, which
     * reads as a continuously curving silhouette indistinguishable
     * from a true rounded rectangle.
     *
     * <p><b>Stage 3:</b> no longer a compile-time constant — this is a
     * projection of the theme's {@link com.aurora.client.theme.ThemeRoundness}
     * token, written exclusively by {@code ResolvedTheme.project()} (see the
     * token-projection note above). The initializer mirrors the
     * {@code ROUND} factory default so any read that races the first
     * reload sees sane values.
     */
    public static int RADIUS = 10;

    /** Larger chamfer for major page panels and sheets (theme-projected — see {@link #RADIUS}). */
    public static int RADIUS_LARGE = 14;

    /** Tight chamfer for compact controls (theme-projected — see {@link #RADIUS}). */
    public static int RADIUS_SMALL = 6;

    /** Slider / toggle thumb radius — iOS UISwitch knob is 27 pt; we scale to the GUI. */
    public static final int KNOB_RADIUS = 8;

    /** Standard padding inside a panel. */
    public static final int PAD = 12;
    /** Tight padding (used in dense rows). */
    public static final int PAD_TIGHT = 6;
    /** Generous padding (page-level sections). */
    public static final int PAD_LOOSE = 16;
}
