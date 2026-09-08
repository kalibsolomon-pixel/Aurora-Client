package com.aurora.client.theme;

/**
 * The HUD layer's fixed-hue <b>status palette</b> — the single source of every
 * red/green/amber the in-game HUD paints (latency tiers, toggle state text,
 * potion timers, minimap entity dots, alert severities).
 *
 * <p><b>Why this exists instead of reads of the {@code SEMANTIC_ERROR /
 * SUCCESS / WARNING} tokens:</b> those tokens are the right source for
 * <i>menu</i> status colors, but wiring the HUD to them verbatim would not be
 * the zero-visual-change deduplication R6 Part 1 required, for three reasons,
 * all verified against the palette code at rollout time (2026-09-08):
 * <ol>
 *   <li><b>The values differ.</b> The HUD's legacy hexes (the neon-green
 *       {@code 0xFF55FF55} family) were never duplicates of the semantic
 *       token values (factory {@code SEMANTIC_SUCCESS} is {@code 0xFF30D158};
 *       the derived dark-mode value is {@code hsl(133°,0.63,0.50)}). Routing
 *       the HUD through the tokens is a <i>visible restyle</i>, which is
 *       Part-2 territory (pilot, screenshots, sign-off) — not Part 1's
 *       "zero visual risk" contract.</li>
 *   <li><b>The semantic tokens are UI-mode-locked</b> (darker variants in
 *       light mode, for contrast on light UI surfaces). The HUD floats over
 *       the <i>world</i> in both modes and needs its bright values to stay
 *       legible over bright terrain. The theme engine already treats HUD
 *       color as mode-independent — see {@code PaletteEngine}'s
 *       {@link ThemeToken#HUD_BACKDROP_TOP} derivation — this class extends
 *       that precedent to status colors.</li>
 *   <li><b>The latency scale has five quality tiers</b>; the three semantic
 *       tokens cannot express it without collapsing two tiers and losing
 *       user-visible information.</li>
 * </ol>
 *
 * <p>What this class <i>does</i> achieve is the actual deduplication goal:
 * every status hex that used to be independently copied across the HUD layer
 * now reads from here, so a future value unification with the semantic tokens
 * (an explicit, screenshot-gated decision) is a change to this file alone.
 *
 * <p>HUD modules stay never-glass (AGENTS §6); this is purely a color source.
 */
public final class HudStatus {

    private HudStatus() {}

    // ---- State indication ----

    /** Green "engaged / healthy": toggle-ON rows, plentiful-time timers, best latency tier. */
    public static final int ON = 0xFF55FF55;

    /** Gray "disengaged": toggle-OFF rows, unknown-latency text. */
    public static final int OFF = 0xFFAAAAAA;

    // ---- Latency quality ladder (best → worst) ----

    /** &lt; 80 ms — same green as {@link #ON}. */
    public static final int LATENCY_GOOD  = ON;
    /** 80–149 ms — chartreuse. */
    public static final int LATENCY_FAIR  = 0xFFCCFF55;
    /** 150–249 ms — yellow. */
    public static final int LATENCY_LAGGY = 0xFFFFFF55;
    /** 250–399 ms — orange. */
    public static final int LATENCY_SLOW  = 0xFFFFAA33;
    /** ≥ 400 ms — red, same value as {@link #ALERT_URGENT}. */
    public static final int LATENCY_AWFUL = 0xFFFF5555;

    /** Latency tier color — the one algorithm shared by the ping HUD, tab list, and nametags. */
    public static int latencyColor(int ms) {
        if (ms < 80)  return LATENCY_GOOD;
        if (ms < 150) return LATENCY_FAIR;
        if (ms < 250) return LATENCY_LAGGY;
        if (ms < 400) return LATENCY_SLOW;
        return LATENCY_AWFUL;
    }

    // ---- Alert / toast severities ----

    /** Urgent alert (low durability) — red. */
    public static final int ALERT_URGENT  = 0xFFFF5555;
    /** Caution alert / toast (low hunger, compliance active) — amber. */
    public static final int ALERT_CAUTION = 0xFFFFAA00;
    /** "All features restored" compliance toast — green (same value as {@link #DOT_PASSIVE}). */
    public static final int RESTORED = 0xFF30D158;

    // ---- Minimap entity category dots ----

    /** Hostile (Monster) entity dot — red. */
    public static final int DOT_HOSTILE = 0xFFFF453A;
    /** Passive (Animal) entity dot — green. */
    public static final int DOT_PASSIVE = 0xFF30D158;
    /** Other living entity dot (neither Monster nor Animal) — neutral gray. */
    public static final int DOT_NEUTRAL = 0xFF8E8E93;
}
