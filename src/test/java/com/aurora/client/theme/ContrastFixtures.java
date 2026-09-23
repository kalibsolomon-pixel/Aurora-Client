package com.aurora.client.theme;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

/**
 * Phase D-1's tracked stress-test infrastructure (DESIGN_LANGUAGE §3.6;
 * the D-0 accent stress set promoted out of the untracked harness).
 *
 * <p><b>Provenance.</b> D-0's exact fixture hexes lived in the untracked
 * DevPilot harness and were not preserved; this catalog was RECONSTRUCTED
 * at D-1 by searching the production color math for hexes that reproduce
 * D-0's recorded ratios. Thirteen of the fourteen reproduce D-0's numbers
 * to within ±0.01 (most to ±0.005 — see {@code D0CharacterizationTest} for
 * the pinned table). The one structural divergence: D-0 recorded
 * saturated-blue as 4.33 opaque AND 3.46 hover-worst, but an exhaustive
 * scan (hue 225–255 at every saturation/lightness, then the full blue RGB
 * cube) proves no blue satisfies both simultaneously — the two recorded
 * numbers are mutually exclusive on a single color. This reconstruction
 * pins the OPAQUE pairing (4.33, the D0-01 headline) and accepts 3.38 for
 * the hover family, documenting the ~0.08 gap instead of hiding it.
 *
 * <p>Two extra pure-hue accents ({@code PURE_GREEN}, {@code PURE_MAGENTA})
 * ride along because the 2026-09-14 audit independently measured them;
 * they widen the property-test grid without pretending to be D-0 pins.
 *
 * <p>Do not scatter raw literals across tests — add or change fixtures
 * here, with a provenance note.
 */
public final class ContrastFixtures {

    private ContrastFixtures() {}

    /** The D-0 14-accent stress catalog (semantic name → opaque accent ARGB). */
    public static final Map<String, Integer> ACCENTS =
            Collections.unmodifiableMap(buildAccents());

    /** Frequently-pinned members, as constants. */
    public static final int DEFAULT_RED = 0xFFEB0029;
    public static final int NEAR_WHITE = 0xFFF5F5F5;
    public static final int NEAR_BLACK = 0xFF2B2B2B;
    public static final int SATURATED_RED = 0xFFFF0000;
    public static final int SATURATED_BLUE = 0xFF5E5EFF;

    private static LinkedHashMap<String, Integer> buildAccents() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("DEFAULT_RED", 0xFFEB0029);       // factory default; D-0: 4.30 (exact 4.3025)
        m.put("BLACK", 0xFF000000);             // D-0: 19.64 (exact 19.6387)
        m.put("NEAR_BLACK", 0xFF2B2B2B);        // D-0: 13.24 (exact 13.2412)
        m.put("WHITE", 0xFFFFFFFF);             // D-0: 18.59 (exact 18.5944)
        m.put("NEAR_WHITE", 0xFFF5F5F5);        // D-0: 17.06 (exact 17.0555)
        m.put("MID_GRAY", 0xFF808080);          // D-0: 4.71 (exact 4.7081)
        m.put("LIGHT_MID_GRAY", 0xFFA8A8A8);    // D-0: 7.82 (exact 7.8198)
        m.put("LOW_SAT_MIDTONE", 0xFFB99F9F);   // D-0: 7.55 (exact 7.5465); low-saturation, midtone
        m.put("SATURATED_RED", 0xFFFF0000);     // D-0: 4.65 (4.6504); pressed-worst 2.67 (2.6683)
        m.put("SATURATED_GREEN", 0xFF4ACA0B);   // D-0: 8.45 (exact 8.4494)
        m.put("SATURATED_BLUE", 0xFF5E5EFF);    // D-0: 4.33 (4.3385); see the class javadoc
        m.put("SATURATED_YELLOW", 0xFFE6E672);  // D-0: 13.76 (exact 13.7579)
        m.put("CYAN", 0xFF00D5D5);              // D-0: 9.96 (exact 9.9634)
        m.put("MAGENTA", 0xFFEF06EF);           // D-0: 5.25 (exact 5.2486)
        // audit-overlap extras (2026-09-14 audit measured pure hues)
        m.put("PURE_GREEN", 0xFF00FF00);
        m.put("PURE_MAGENTA", 0xFFFF00FF);
        return m;
    }

    /**
     * The D-0 backdrop fixture set — deterministic backdrops a translucent
     * layer may be composited over in tests. Reconstructed like the accents:
     * NEAR_BLACK and NEAR_WHITE reproduce D-0's stained-worst (1.95) and
     * low-opacity-window (1.21) measurements; the remaining four are
     * representative reconstruction (no D-0 number pins them). Test
     * infrastructure only — none of these may become production tokens.
     */
    public static final int NEAR_BLACK_BACKDROP = 0xFF080808;
    public static final int DARK_MIDTONE_BACKDROP = 0xFF3A3A3A;
    public static final int LIGHT_MIDTONE_BACKDROP = 0xFFB4B4B4;
    public static final int NEAR_WHITE_BACKDROP = 0xFFFAFAFA;
    public static final int DEEP_GREEN_BACKDROP = 0xFF1B5E20;
    public static final int SUNSET_ORANGE_BACKDROP = 0xFFC77B3B;

    public static final Map<String, Integer> BACKDROP_FIXTURES = buildBackdrops();
    public static final int[] BACKDROPS = BACKDROP_FIXTURES.values().stream()
            .mapToInt(Integer::intValue).toArray();

    private static Map<String, Integer> buildBackdrops() {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        m.put("NEAR_BLACK", NEAR_BLACK_BACKDROP);
        m.put("DARK_MIDTONE", DARK_MIDTONE_BACKDROP);
        m.put("LIGHT_MIDTONE", LIGHT_MIDTONE_BACKDROP);
        m.put("NEAR_WHITE", NEAR_WHITE_BACKDROP);
        m.put("DEEP_GREEN", DEEP_GREEN_BACKDROP);
        m.put("SUNSET_ORANGE", SUNSET_ORANGE_BACKDROP);
        return Collections.unmodifiableMap(m);
    }

    /** The D-0 opacity matrix. */
    public static final double[] OPACITIES = {0.10, 0.35, 0.65, 1.0};

    /**
     * Epsilon policy (documented once, used everywhere):
     * <ul>
     *   <li>Exact comparisons (identity, symmetry, bitwise pins) use no
     *       epsilon.</li>
     *   <li>Ratio pins reconstructed to D-0 use ±0.01–±0.02 — the shift one
     *       8-bit channel step (1/255) causes in a ratio near that value,
     *       plus float HSL quantization on the reconstructed hexes.</li>
     *   <li>Threshold conformance checks use plain {@code >=} with NO
     *       epsilon — a value is conformant or it is not; borderline cases
     *       must not be waved through.</li>
     * </ul>
     */
    public static final double PIN_TOLERANCE = 0.01;
    public static final double LOOSE_PIN_TOLERANCE = 0.02;
}
