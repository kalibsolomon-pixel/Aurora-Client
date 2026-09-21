package com.aurora.client.screen.setting;

import com.aurora.client.ui.util.ClipBand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-8, part 2 — edge geometry: the Escape/unfocus rule on every
 * production text field, exact search-band bounds, the Enum popup's
 * bottom-edge flip-up placement, the ColorPicker's short-window geometry,
 * and the PixelCanvas warning panel's input modality. Source pins where
 * screens are not headless-instantiable; pure placement/geometry math
 * where the component is (EnumSetting).
 */
class PhaseC8EdgeGeometryTest {

    private static String source(String rel) {
        try {
            return Files.readString(Path.of(rel));
        } catch (java.io.IOException e) {
            return fail("not readable from the test working dir: " + e);
        }
    }

    // ---- Escape / unfocus (§15) ----

    @Test
    void everyProductionSearchFieldUnfocusesOnEscapeFirst() {
        // The canonical rule: Escape with a focused text field unfocuses +
        // consumes; only a second press reaches the screen. The four
        // detail-screen/Aurora fields had the fall-through (screen closed
        // on first press).
        String[] fields = {
                "src/client/java/com/aurora/client/screen/setting/SearchListSetting.java",
                "src/client/java/com/aurora/client/screen/setting/ItemScaleSetting.java",
                "src/client/java/com/aurora/client/screen/setting/EffectExpiryListSetting.java",
                "src/client/java/com/aurora/client/screen/setting/PixelCanvasSetting.java",
                "src/client/java/com/aurora/client/screen/AuroraScreen.java",
        };
        for (String f : fields) {
            String s = source(f);
            assertTrue(s.contains("GLFW_KEY_ESCAPE"),
                    Path.of(f).getFileName() + " must handle Escape for its focused field");
            assertTrue(s.contains("setFocused(false)"),
                    Path.of(f).getFileName() + " must UNFOCUS the field (not close the screen)");
        }
        // The capture families' Escape semantics (clear-binding / cancel
        // capture) are their own and must not be swept into this rule.
        String keybind = source("src/client/java/com/aurora/client/screen/setting/KeybindSetting.java");
        assertTrue(keybind.contains("GLFW_KEY_ESCAPE"), "Keybind keeps its clear-to-UNBOUND rule");
    }

    // ---- Search-band exact bounds (§16) ----

    @Test
    void searchHitBandsEqualThePaintedFields() {
        String[] rows = {
                "src/client/java/com/aurora/client/screen/setting/SearchListSetting.java",
                "src/client/java/com/aurora/client/screen/setting/ItemScaleSetting.java",
                "src/client/java/com/aurora/client/screen/setting/EffectExpiryListSetting.java",
        };
        for (String f : rows) {
            String s = source(f);
            assertTrue(s.contains("mouseY >= rowY + 6 && mouseY < rowY + 24"),
                    Path.of(f).getFileName() + " hit band must be the painted field [y+6, y+24)");
            assertFalse(s.contains("mouseY >= rowY + 5 && mouseY < rowY + 25"),
                    Path.of(f).getFileName() + " 2px-taller band must not return");
            assertTrue(s.contains("setY(y + 6)") || s.contains("setY(y + 6);"),
                    Path.of(f).getFileName() + " paints its field at y+6 (the band's truth)");
        }
    }

    // ---- Enum popup placement (§17-19) — headless placement math ----

    private enum TenValues { A, B, C, D, E, F, G, H, I, J }

    private static EnumSetting<TenValues> ten() {
        AtomicReference<TenValues> v = new AtomicReference<>(TenValues.A);
        return new EnumSetting<>("Placement", TenValues.class, v::get, v::set);
    }

    /** dropdownH for a 5-option popup: 5*16 + 4 (OPT_H=16). */
    private static final int DROPDOWN_H = 5 * 16 + 4;

    @AfterEach
    void clearBand() {
        FeatureSetting.clearHostBand();
    }

    @Test
    void popupOpensBelowWhenSpaceAllows() {
        FeatureSetting.setHostBand(new ClipBand(0, 0, 400, 300));
        EnumSetting<TenValues> row = ten();
        int btnY = 100;
        assertEquals(btnY + 18 + 2, row.dropdownY(btnY, DROPDOWN_H),
                "preferred placement is below the trigger");
    }

    @Test
    void popupFlipsUpNearTheBandBottom() {
        // Band bottom at 150: below-space is 150 - (btnY + 20) = 30 — less
        // than the 84px popup; above-space is btnY - 2 = 98 — enough.
        FeatureSetting.setHostBand(new ClipBand(0, 0, 400, 150));
        EnumSetting<TenValues> row = ten();
        int btnY = 100;
        assertEquals(btnY - 2 - DROPDOWN_H, row.dropdownY(btnY, DROPDOWN_H),
                "bottom-edge popup flips up");
        // Just fitting below stays below: band bottom at 206 exactly fits
        // btnY + 20 + 84.
        FeatureSetting.setHostBand(new ClipBand(0, 0, 400, 206));
        assertEquals(btnY + 20, row.dropdownY(btnY, DROPDOWN_H),
                "a popup that exactly fits below stays below (half-open band edge)");
    }

    @Test
    void popupPicksTheRoomierSideAndClampsWhenNeitherFits() {
        // Tiny band [40, 120): neither 84px side fits from btnY=100
        // (below-space 0, above-space 58) — the roomier side (above)
        // clamps into the band.
        FeatureSetting.setHostBand(new ClipBand(0, 40, 400, 80));
        EnumSetting<TenValues> row = ten();
        int y = row.dropdownY(100, DROPDOWN_H);
        assertEquals(40, y, "clamped to the band's top edge on the roomier side");
        // And with the room below instead (trigger near the band's top),
        // below wins and clamps into the band.
        FeatureSetting.setHostBand(new ClipBand(0, 90, 400, 20));
        assertEquals(90, row.dropdownY(91, DROPDOWN_H),
                "below-side clamp lands inside the band");
    }

    @Test
    void renderAndClickShareTheResolvedPlacement() {
        String s = source("src/client/java/com/aurora/client/screen/setting/EnumSetting.java");
        assertEquals(2, countOccurrences(s, "int dropdownY = dropdownY(btnY, dropdownH);"),
                "the render walk AND the click walk must resolve placement through the one helper");
        assertTrue(s.contains("int dropdownY(int btnY, int dropdownH)"),
                "the placement resolver exists");
        assertFalse(s.contains("int dropdownY = btnY + BTN_H + 2;"),
                "the always-down placement must not return");
    }

    @Test
    void hostsPublishTheirBand() {
        String detail = source("src/client/java/com/aurora/client/screen/FeatureDetailScreen.java");
        assertTrue(detail.contains("FeatureSetting.setHostBand("),
                "FeatureDetailScreen publishes its fade-boundary clip");
        assertTrue(detail.contains("FeatureSetting.clearHostBand()"),
                "FeatureDetailScreen clears the band on teardown");
        String aurora = source("src/client/java/com/aurora/client/screen/AuroraScreen.java");
        assertTrue(aurora.contains("FeatureSetting.setHostBand(contentViewport())"),
                "AuroraScreen publishes its C-1 content viewport");
        assertTrue(aurora.contains("FeatureSetting.clearHostBand()"),
                "AuroraScreen clears the band on removal");
    }

    // ---- ColorPicker short-window geometry (§20) — the layout math ----

    @Test
    void colorPickerStackNeverOverlapsAcrossWindowHeights() {
        // Replicates init()'s arithmetic (C-8) and asserts the invariant the
        // fix exists for: whenever the editing surfaces render, the hex
        // field's bottom sits strictly above the pinned action row.
        for (int ch = 120; ch <= 600; ch += 10) {
            int cw = 400;
            int maxPad = Math.min(240, Math.min(cw - 200, ch - 160));
            boolean supportable = maxPad >= 40;
            if (!supportable) continue; // surfaces skipped — nothing to overlap
            int padSize = maxPad;
            int hexBottom = 48 + padSize + 14 + 28 + 10 + 18; // padY + stack
            int btnTop = ch - 32;
            assertTrue(hexBottom <= btnTop,
                    "hex field overlaps buttons at ch=" + ch);
            // Widths stay inside the window too.
            int padX = (cw - padSize - 80) / 2;
            int alphaRight = padX + padSize + 20 + 18 + 12 + 18;
            assertTrue(alphaRight <= cw, "strips exceed window at ch=" + ch);
        }
        // The old 60px floor regime genuinely overlapped — the fix's reason.
        int oldPad = Math.max(60, Math.min(240, Math.min(400 - 200, 190 - 160)));
        assertEquals(60, oldPad, "sanity: the old floor binds at ch=190");
        assertTrue(48 + oldPad + 14 + 28 + 10 + 18 > 190 - 32,
                "sanity: the old layout overlapped at ch=190");
    }

    @Test
    void colorPickerFailsGracefullyBelowTheSupportableMinimum() {
        String s = source("src/client/java/com/aurora/client/screen/ColorPickerScreen.java");
        assertTrue(s.contains("pickerSupportable = maxPad >= MIN_PAD"),
                "supportability is an explicit computed decision");
        assertTrue(s.contains("Window too small for the color picker"),
                "the unsupported configuration renders a message, not overlapping controls");
        assertTrue(s.contains("button == 0 && pickerSupportable"),
                "surface input paths are gated off when unsupported");
        assertFalse(s.contains("Math.max(60,"),
                "the overlap-causing 60px pad floor must not return");
    }

    // ---- PixelCanvas warning modality (§26) ----

    @Test
    void pixelCanvasWarningPanelOwnsItsRegion() {
        String s = source("src/client/java/com/aurora/client/screen/setting/PixelCanvasSetting.java");
        // The panel consumes every click inside its rect; the buttons act
        // only in the decision state (measuring-state clicks are inert).
        String clickBody = methodBody(s, "public boolean mouseClicked");
        int panelBranch = clickBody.indexOf("Warning panel (C-8 modality)");
        assertTrue(panelBranch >= 0, "the warning-panel consumption branch exists");
        String branch = clickBody.substring(panelBranch,
                clickBody.indexOf("// Resolution fields."));
        assertTrue(branch.contains("WARN_PANEL_H)"),
                "the panel's own rect bounds the consumed region");
        assertTrue(branch.contains("warnPending != null && mouseY >= warnBtnY"),
                "buttons act only in the decision state");
        assertTrue(branch.contains("return true; // panel body / measuring-state buttons: inert, consumed"),
                "panel-body and measuring-state clicks are consumed-but-inert");
    }

    // ---- helpers ----

    private static int countOccurrences(String s, String needle) {
        int c = 0, i = 0;
        while ((i = s.indexOf(needle, i)) >= 0) { c++; i += needle.length(); }
        return c;
    }

    private static String methodBody(String src, String signatureFragment) {
        int i = src.indexOf(signatureFragment);
        if (i < 0) return "";
        int open = src.indexOf('{', i);
        int depth = 0;
        for (int j = open; j < src.length(); j++) {
            char c = src.charAt(j);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(i, j + 1);
            }
        }
        return src.substring(i);
    }
}
