package com.aurora.client.theme;

import com.aurora.client.config.AuroraConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner of the theme lifecycle: resolve once, cache, re-resolve only when
 * the theme actually changes.
 *
 * <p>Call sites:
 * <ul>
 *   <li>{@link #reload()} — config load, profile apply, and any setting
 *       edit that touches the theme (the color pickers).</li>
 *   <li>{@link #sync()} — once per client tick (from
 *       {@code ThemeFeature}); a cheap 6-field dirty-check that replaces
 *       the legacy system's unconditional per-tick recompute, while still
 *       catching profile switches that bypass the setters.</li>
 *   <li>{@link #color}/{@link #current} — read the cached theme. A volatile
 *       array read; safe from any thread, zero allocation.</li>
 * </ul>
 *
 * <p>Reload also republishes the values onto the legacy {@code AuroraTheme}
 * statics via {@link ResolvedTheme#project()} (the Stage 1 facade — see
 * that class for details).
 */
public final class ThemeManager {

    private static final Logger LOG = LoggerFactory.getLogger("Aurora");

    /**
     * The live resolved theme. Initialized to the factory palette so any
     * read before the first reload sees sane values (identical to the old
     * system's pre-{@code updateColors()} state).
     */
    private static volatile ResolvedTheme current = ThemeResolver.factoryPalette();

    /**
     * Monotonic reload stamp. Bumped once per {@link #reload()}; renderers
     * that cache theme-derived pixels key their caches off this so any
     * theme change — through whichever path — invalidates them exactly once
     * (no under- or over-invalidation).
     */
    private static final java.util.concurrent.atomic.AtomicLong GENERATION =
            new java.util.concurrent.atomic.AtomicLong();

    private ThemeManager() {}

    /** Monotonic reload counter; changes exactly once per theme change. */
    public static long generation() {
        return GENERATION.get();
    }

    /** The currently resolved theme. */
    public static ResolvedTheme current() {
        return current;
    }

    /** The resolved color for a token (ARGB) — cached array access. */
    public static int color(ThemeToken token) {
        return current.color(token);
    }

    /**
     * A surface token with the panel's opacity-driven alpha. Control and card
     * backgrounds use this so they inherit the same dark/translucent character
     * as the main window panel (and track the Background Opacity slider), in
     * both Dark and Light mode.
     */
    public static int surfaceColor(ThemeToken surface) {
        int panel = current.color(ThemeToken.WINDOW_FILL);
        return (panel & 0xFF000000) | (current.color(surface) & 0x00FFFFFF);
    }

    /**
     * Tint for the <b>accent-stained glass</b> control family (Theme screen
     * pilot: selected segmented options, primary buttons, the toggle's ON
     * track, the accent chip, slider fills). Single derivation point —
     * accent RGB at the stained alpha.
     *
     * <p><b>Alpha policy:</b> the neutral glass tint is {@code WINDOW_FILL}
     * whose alpha IS the Background Opacity slider (the rebuild's single
     * opacity application point — see {@code BlurPanelRenderer}'s class
     * javadoc). Stained elements cannot use that raw alpha directly: at the
     * default 8% opacity an accent tint is visually indistinguishable from
     * neutral glass, defeating the entire "distinguish selected by tint"
     * purpose. The stained alpha therefore follows the SAME slider through a
     * fixed visibility floor: {@code max(opacity, 0.55)}. This is a style
     * constant, not a second opacity control — the slider still drives the
     * value everywhere above the floor, and no caller may set stained
     * translucency independently.
     */
    public static int stainedTint() {
        int windowAlpha = (current.color(ThemeToken.WINDOW_FILL) >>> 24) & 0xFF;
        int alpha = Math.max(windowAlpha, STAINED_MIN_ALPHA);
        return (alpha << 24) | (current.color(ThemeToken.ACCENT) & 0x00FFFFFF);
    }

    /**
     * Fixed visibility floor for stained tints — see {@link #stainedTint()}.
     * Aliased from {@link ContrastDerivations#STAINED_VISIBILITY_FLOOR_ALPHA}
     * (Phase D-1) so the floor has exactly one definition.
     */
    private static final int STAINED_MIN_ALPHA = ContrastDerivations.STAINED_VISIBILITY_FLOOR_ALPHA;

    /**
     * Re-stamp an ARGB color's alpha channel (clamped 0..255). Used to derive
     * the subtler screen-dim strengths from the themed
     * {@link ThemeToken#OVERLAY_DIM} RGB, so even alpha-scaled overlays stay
     * token-driven instead of reintroducing hardcoded near-black hexes.
     */
    public static int withAlpha(int argb, int alpha) {
        int a = alpha < 0 ? 0 : (Math.min(alpha, 255));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Re-read the config, resolve the theme, cache it, and republish the
     * facade projection. Cheap (one small array build + ~50 field writes),
     * but call it only when the theme is known/suspected to have changed;
     * prefer {@link #sync()} on tick paths.
     */
    public static void reload() {
        AuroraConfig cfg = AuroraConfig.get();
        ThemeDefinition def = cfg.themeOrDefault();
        boolean enabled = cfg.themeEnabled;

        ResolvedTheme resolved = ThemeResolver.resolve(def, enabled);
        resolved.project();
        current = resolved;
        GENERATION.incrementAndGet();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Theme reloaded: mode={} accentsEnabled={} accent=#{}",
                    resolved.mode(), enabled,
                    String.format("%08X", resolved.accent()));
        }
    }

    /**
     * Dirty-check the live config against the last-applied theme and
     * {@link #reload()} only if something changed. Designed to be called
     * every client tick; the happy path is a handful of comparisons. All
     * comparisons use the same normalization {@link #reload()} stores, so a
     * direct reload is never double-fired by this check, and profile
     * switches that bypass the setters are still caught.
     */
    public static void sync() {
        AuroraConfig cfg = AuroraConfig.get();
        ThemeDefinition def = cfg.themeOrDefault();
        ResolvedTheme last = current;
        boolean enabledChanged = cfg.themeEnabled != last.accentsEnabled();
        boolean accentChanged = def.accent != last.accent();
        ThemeMode mode = def.mode != null ? def.mode : ThemeMode.DARK;
        boolean modeChanged = mode != last.mode();
        ThemeRoundness roundness = def.roundness != null ? def.roundness : ThemeRoundness.ROUND;
        boolean roundnessChanged = roundness != last.roundness();
        double opacity = ThemeDefinition.normalizedOpacity(def.backgroundOpacity);
        boolean opacityChanged = Double.compare(opacity, last.backgroundOpacity()) != 0;
        GlassStyle glassStyle = def.glassStyle != null ? def.glassStyle : GlassStyle.FROSTED;
        boolean glassStyleChanged = glassStyle != last.glassStyle();
        if (enabledChanged || accentChanged || modeChanged
                || roundnessChanged || opacityChanged || glassStyleChanged) {
            reload();
        }
    }
}
