package com.aurora.client.conformance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.aurora.client.conformance.SemanticFamilyManifest.CANONICAL_HOVER_ANCHORS;
import static com.aurora.client.conformance.SemanticFamilyManifest.FAMILIES;
import static com.aurora.client.conformance.SemanticFamilyManifest.NON_FAMILIES;
import static com.aurora.client.conformance.SemanticFamilyManifest.SRC;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The §15.2 conformance harness's manifest audit — the durable, source-level
 * half of Phase C closure. Where a contract can be tested BEHAVIORALLY it
 * lives in the per-family suites (the Phase B/C pilots) and in
 * {@link ButtonLikeConformanceTest}; the checks here are the ones that are
 * inherently architectural (inventory/ownership/completeness), which the
 * closure task sanctions as source-contract tests:
 *
 * <ul>
 *   <li>completeness — every production {@code SemanticActionControl} site
 *       belongs to a manifest family, so a new family cannot ship
 *       unclassified;</li>
 *   <li>the canonical hover vocabulary is pinned where each animated family
 *       declares its animator (the behavioral no-snap/reversal math is
 *       pinned per-family and by {@code TogglePilotTest});</li>
 *   <li>sound ownership — exactly one owner per family, no vanilla sound
 *       call sites in Aurora UI chrome;</li>
 *   <li>the Square-mode radius literal inventory — every remaining literal
 *       radius in the AA/squircle/glass-panel family is one of the fourteen
 *       classified sites (C-7 Wave 1 plus the representational/mechanical
 *       exemptions); {@code SquareModeConformanceTest} pins the non-AA
 *       exemption families (HudBackgrounds, Keystrokes keycaps) and the
 *       token substitutions themselves;</li>
 *   <li>the ClipBand consumer set — ALL of it this time: the C-8 pin matched
 *       {@code new ClipBand(} only, which missed fully-qualified
 *       constructions and the static host-band API readers;</li>
 *   <li>the AuroraScreen hosted inventory, re-derived from live source
 *       (§31: never trust the historical count) and the frozen tooltip
 *       constants.</li>
 * </ul>
 */
class ConformanceManifestTest {

    // ------------------------------------------------------------------
    //  Completeness
    // ------------------------------------------------------------------

    @Test
    void manifestFilesExist() {
        for (SemanticFamilyManifest.Family f : FAMILIES) {
            for (String file : f.files()) {
                assertTrue(Files.exists(Path.of(file)), "manifest family " + f.id() + " file missing: " + file);
            }
        }
        for (SemanticFamilyManifest.NonFamily nf : NON_FAMILIES) {
            String path = nf.file().contains("#") ? nf.file().substring(0, nf.file().indexOf('#')) : nf.file();
            assertTrue(Files.exists(Path.of(path)), "non-family file missing: " + path);
        }
    }

    /**
     * The completeness direction: every production file that constructs a
     * {@code SemanticActionControl} must be claimed by a manifest family —
     * an unclaimed site is an unclassified semantic family (a Phase C
     * closure defect), and a claimed-but-vanished file is manifest rot.
     */
    @Test
    void everyProductionSemanticControlSiteBelongsToAManifestFamily() throws IOException {
        Set<String> claimed = new HashSet<>();
        for (SemanticFamilyManifest.Family f : FAMILIES) claimed.addAll(f.files());

        List<String> unclaimed = new ArrayList<>();
        for (String file : javaFiles()) {
            if (read(file).contains("new SemanticActionControl(") && !claimed.contains(file)) {
                unclaimed.add(file);
            }
        }
        assertTrue(unclaimed.isEmpty(),
                "unclassified semantic-control construction sites: " + unclaimed);
    }

    // ------------------------------------------------------------------
    //  Hover vocabulary (§9)
    // ------------------------------------------------------------------

    /**
     * Every family whose hover is CANONICAL_140 and whose animator is a
     * single-file declaration carries the symmetric construction, and every
     * {@code HOVER_MS} constant it resolves through is exactly 140. The
     * finite immediate/legacy exception set is pinned by
     * {@code ColorSwatchDataOnlyTest.postCleanupLegacyHoverAnimInventory}
     * (the two documented mock/data-only default constructors) and by the
     * per-family sanctioned-scanning source pins (Enum popup rows, KeyList
     * chips).
     */
    @Test
    void canonicalHoverFamiliesDeclareTheSymmetricVocabulary() throws IOException {
        for (var entry : CANONICAL_HOVER_ANCHORS.entrySet()) {
            String src = read(entry.getKey());
            assertTrue(src.contains(entry.getValue()),
                    entry.getKey() + " must declare the canonical animator via " + entry.getValue());
            if (entry.getValue().contains("HOVER_MS")) {
                assertTrue(src.contains("HOVER_MS = 140"),
                        entry.getKey() + " HOVER_MS must stay 140 (§8.3)");
            }
        }
    }

    // ------------------------------------------------------------------
    //  Sound ownership (§14)
    // ------------------------------------------------------------------

    /**
     * Exactly one sound owner per family. The behavior-side play sites
     * (families whose control carries {@code SemanticSound.NONE} and whose
     * BEHAVIOR plays exactly one ACTIVATION on genuine change) are the four
     * recorded files; {@code SemanticAction.button(...)} owns the click for
     * the button-action family by construction; vanilla-backed controls keep
     * vanilla's funnel and carry NONE.
     */
    @Test
    void behaviorSideSoundPlaysAreExactlyTheRecordedSet() throws IOException {
        Set<String> expected = Set.of(
                SRC + "screen/setting/AccentSetting.java",
                SRC + "screen/AuroraScreen.java",
                SRC + "screen/ResourcePackBrowserScreen.java",
                SRC + "ui/component/SegmentedControl.java");
        Set<String> found = new HashSet<>();
        for (String file : javaFiles()) {
            if (read(file).contains(".play(SemanticSound")) {
                found.add(file);
            }
        }
        assertEquals(expected, found,
                "behavior-side ACTIVATION play sites must stay the recorded play-on-change set");
    }

    /** The vanilla adapter carries NONE — vanilla's own click stays the one owner (C-6 boundary). */
    @Test
    void vanillaBackedButtonsCarryNoneAndKeepVanillaOwnership() throws IOException {
        String src = read(SRC + "ui/component/ButtonWidget.java");
        assertTrue(src.contains("SemanticSound.NONE"),
                "ButtonWidget.semantic must carry SemanticSound.NONE (vanilla owns the click)");
    }

    /**
     * No Aurora UI chrome calls vanilla's sound path directly — the only
     * project {@code UI_BUTTON_CLICK} is Better Hitreg's practice-arena
     * behavior (upstream timing core, domain code, not UI chrome), and no
     * Aurora class invokes {@code playDownSound} (mixin javadocs describe
     * VANILLA's funnel, they do not call it).
     */
    @Test
    void noAuroraUiChromePlaysVanillaSoundDirectly() throws IOException {
        for (String file : javaFiles()) {
            String src = read(file);
            boolean hitreg = file.contains("/hitreg/");
            assertTrue(hitreg || !src.contains("SoundEvents.UI_BUTTON_CLICK"),
                    file + " must not call the vanilla UI click directly");
            assertFalse(src.contains(".playDownSound("),
                    file + " must not call playDownSound (vanilla-internal funnel)");
        }
    }

    // ------------------------------------------------------------------
    //  Square-mode radius inventory (§25–§26)
    // ------------------------------------------------------------------

    /** One literal-radius call site, identified by file + the radius source token. */
    private record Site(String file, String radius) {}

    /**
     * Argument-aware inventory of literal radii in the rounded-AA /
     * squircle / glass-panel family. Every one of the (currently fourteen)
     * literal sites is classified; a NEW literal site fails this test until
     * it is either tokenized or added here with a ruling. The non-AA
     * exemption families (HudBackgrounds' sanctioned radius 2, the
     * Keystrokes keycap's representational radius 3, the toggle/slider
     * mechanical shapes) are pinned by {@code SquareModeConformanceTest}.
     *
     * <p>Classifications: the four scrollbar-thumb capsules are MECHANICAL
     * direct-manipulation chrome (C-7 ruling, documented at the sites); the
     * three ColorPicker frames are DATA-DRIVEN color-picking surfaces
     * (§16 exception domain); the layout pair's six icon strokes are
     * REPRESENTATIONAL icon content (C-5 preserved the vector icons
     * verbatim); the sidebar's two 0.5-radius strokes are degenerate
     * hairline geometry (the 1px divider, and the 6×1 list-icon line — no
     * visible corner in any roundness mode).
     */
    @Test
    void radiusLiteralsAreExactlyTheClassifiedInventory() throws IOException {
        List<Site> classified = new ArrayList<>(List.of(
                // MECHANICAL scrollbar-thumb capsules (Square-exempt, C-7).
                new Site(SRC + "screen/AuroraScreen.java", "2"),
                new Site(SRC + "screen/ManagerListScreen.java", "2"),
                new Site(SRC + "screen/ResourcePackBrowserScreen.java", "2"),
                new Site(SRC + "screen/ResourcePackBrowserScreen.java", "2"),
                // DATA-DRIVEN picker frames (§16).
                new Site(SRC + "screen/ColorPickerScreen.java", "6"),
                new Site(SRC + "screen/ColorPickerScreen.java", "6"),
                new Site(SRC + "screen/ColorPickerScreen.java", "6"),
                // REPRESENTATIONAL layout icons (C-5 ruling).
                new Site(SRC + "screen/AuroraScreen.java", "1"),
                new Site(SRC + "screen/AuroraScreen.java", "1"),
                new Site(SRC + "screen/AuroraScreen.java", "1"),
                new Site(SRC + "screen/AuroraScreen.java", "1"),
                new Site(SRC + "screen/AuroraScreen.java", "1"),
                // DEGENERATE hairline strokes (the 1px divider + the icon's
                // 6×1 line — width-1 geometry has no corner to square).
                new Site(SRC + "screen/AuroraScreen.java", "0.5f"),
                new Site(SRC + "screen/AuroraScreen.java", "0.5f")));

        List<Site> seen = new ArrayList<>();
        for (String file : javaFiles()) {
            String src = read(file);
            Pattern call = Pattern.compile(
                    "(drawRoundedRectAA|drawRoundedOutlineAA|drawSquircle|drawSquircleOutline|renderPanel)\\(");
            Matcher m = call.matcher(src);
            while (m.find()) {
                String args = balancedArgs(src, m.end());
                List<String> parts = splitTopLevel(args);
                if (parts.size() > 5) {
                    String radius = parts.get(5).trim();
                    if (radius.matches("\\d+(\\.\\d+)?[fF]?")) {
                        seen.add(new Site(file, radius));
                    }
                }
            }
        }
        assertEquals(new HashSet<>(classified), new HashSet<>(seen),
                "unclassified or missing literal radius sites");
        assertEquals(14, seen.size(), "inventory drift — sites: " + seen);
        assertEquals(classified.size(), seen.size(), "multiplicity drift");
    }

    // ------------------------------------------------------------------
    //  ClipBand consumer set (§18)
    // ------------------------------------------------------------------

    /**
     * The COMPLETE ClipBand consumer set, including the fully-qualified
     * construction in FeatureDetailScreen and the two static host-band API
     * readers that the C-8 pin's {@code new ClipBand(} pattern could not
     * see. Any further consumer must be justified here (the coupling
     * contract: one band truth drives scissor, cull, clicks, hover, and the
     * availability sweep).
     */
    @Test
    void clipBandConsumersAreTheCompleteCoupledSet() throws IOException {
        Set<String> expected = Set.of(
                SRC + "ui/util/ClipBand.java",
                SRC + "screen/AuroraScreen.java",
                SRC + "screen/ManagerListScreen.java",
                SRC + "screen/ResourcePackBrowserScreen.java",
                SRC + "screen/FeatureDetailScreen.java",
                SRC + "screen/setting/EnumSetting.java",
                SRC + "screen/setting/FeatureSetting.java");
        Set<String> found = new HashSet<>();
        for (String file : javaFiles()) {
            if (read(file).contains("ClipBand")) found.add(file);
        }
        assertEquals(expected, found, "ClipBand consumers must be exactly the coupled set");
    }

    // ------------------------------------------------------------------
    //  AuroraScreen hosted inventory (§31) — derived, never trusted
    // ------------------------------------------------------------------

    /**
     * The current AuroraScreen semantic inventory, re-derived from live
     * source: 49 screen-owned chrome controls (2 sidebar tabs + Profiles +
     * 2 layout peers + every ModuleManager tile + 3 detail-capable Settings
     * headers + 4 header toggles) plus 20 component-owned hosted controls
     * (3 enum triggers + 10 Accent peers + one peer per segmented value,
     * sized from the live enums). Tile count comes from ModuleManager's
     * source, not the historical number. The seven registration sites are
     * the init-time hosted-settings loop plus the six identity-stable
     * factories — none in the frame path.
     */
    @Test
    void auroraScreenSemanticInventoryMatchesLiveSource() throws IOException {
        int modules = countOccurrences(read(SRC + "module/ModuleManager.java"), "new Module(");
        int segmentedPeers = com.aurora.client.theme.ThemeMode.values().length
                + com.aurora.client.theme.ThemeRoundness.values().length
                + com.aurora.client.theme.GlassStyle.values().length;
        int hosted = 3 + 10 + segmentedPeers;               // enums + accent + segmented peers
        int screenOwned = 2 + 1 + 2 + modules + 3 + 4;      // tabs, profiles, layout, tiles, nav, toggles
        assertEquals(37, modules, "ModuleManager tile count");
        assertEquals(7, segmentedPeers, "segmented peers (Mode 2 + Corner 3 + Glass 2)");
        assertEquals(20, hosted, "component-owned hosted controls");
        assertEquals(49, screenOwned, "screen-owned chrome controls");
        assertEquals(69, hosted + screenOwned, "AuroraScreen total semantic controls");

        String src = read(SRC + "screen/AuroraScreen.java");
        assertEquals(7, countOccurrences(src, "semanticHost.register("),
                "the init-time hosted loop + six identity-stable factory registration sites");
        // Registration is init/factory-only: every register site must sit
        // after a private control factory (or the init loop), never inside a
        // render walk — the frame path checks availability, never membership.
        int idx = -1;
        while ((idx = src.indexOf("semanticHost.register(", idx + 1)) != -1) {
            int methodStart = Math.max(src.lastIndexOf("private ", idx), src.lastIndexOf("protected void init", idx));
            String enclosing = src.substring(methodStart, idx);
            assertFalse(enclosing.contains("void render"),
                    "semantic registration must never live in the frame path (site at " + idx + ")");
        }
    }

    // ------------------------------------------------------------------
    //  Tooltip constants (§10 — frozen)
    // ------------------------------------------------------------------

    @Test
    void tooltipContractStaysFrozenAt1500DwellAnd200SymmetricFade() throws IOException {
        String src = read(SRC + "screen/setting/FeatureSetting.java");
        assertTrue(src.contains("HOVER_DWELL_MS = 1500"),
                "the 1500 ms dwell is the frozen contract");
        assertTrue(src.contains("HoverAnim.symmetric(TOOLTIP_FADE_MS")
                        && src.contains("TOOLTIP_FADE_MS = 200"),
                "the tooltip fade stays the deliberate symmetric 200 ms supplemental family");
    }

    // ------------------------------------------------------------------
    //  helpers
    // ------------------------------------------------------------------

    private static List<String> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of(SRC))) {
            List<String> out = new ArrayList<>();
            files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().endsWith("DevPilot.java"))
                    .forEach(p -> out.add(p.toString()));
            return out;
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /** Extracts the argument text of a call whose '(' ends at {@code openParenIndex}. */
    private static String balancedArgs(String src, int openParenIndex) {
        int depth = 1, j = openParenIndex;
        while (j < src.length() && depth > 0) {
            char c = src.charAt(j);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            j++;
        }
        return src.substring(openParenIndex, j - 1);
    }

    private static List<String> splitTopLevel(String args) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : args.toCharArray()) {
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }
}
