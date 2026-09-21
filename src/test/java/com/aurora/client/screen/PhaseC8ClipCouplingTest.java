package com.aurora.client.screen;

import com.aurora.client.ui.util.ClipBand;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase C-8, part 1 — the ClipBand rollout to the remaining justified
 * consumers (the C-1 record's named later adopters): the pack browser's
 * card grid, the manager list viewport (rows + inline editors), the
 * Profile create row, and the pack detail modal's animation geometry.
 *
 * <p>Source-contract pins (screens are not headless-instantiable) plus
 * pure-geometry cases against the same ClipBand math the hosts run. The
 * behavioral proof — old-position-inert / new-position-active after
 * scroll, hidden editors unavailable, animated modal hit rects — is the
 * runtime {@code c8correctness} matrix; these pins keep the contracts
 * from silently regressing.
 */
class PhaseC8ClipCouplingTest {

    private static final Path MANAGER = Path.of(
            "src/client/java/com/aurora/client/screen/ManagerListScreen.java");
    private static final Path PROFILE = Path.of(
            "src/client/java/com/aurora/client/screen/ProfileManagerScreen.java");
    private static final Path WAYPOINT = Path.of(
            "src/client/java/com/aurora/client/screen/WaypointManagerScreen.java");
    private static final Path PACKS = Path.of(
            "src/client/java/com/aurora/client/screen/ResourcePackBrowserScreen.java");

    private static String source(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            return fail("not readable from the test working dir: " + e);
        }
    }

    // ---- Manager viewport: one band truth ----

    @Test
    void managerDerivesOneBandTruthAndCullsThroughIt() {
        String src = source(MANAGER);
        assertTrue(src.contains("protected ClipBand listBand(int listX)"),
                "the manager base must expose the one viewport truth");
        assertTrue(src.contains("rowPaintVisible(ClipBand band, int rowY)"),
                "the render cull must take the band (the pre-C-8 literal cull is gone)");
        assertTrue(src.contains("ClipBand band = listBand(listX);"),
                "the frame path must fetch the band once and hand it to the walks");
    }

    @Test
    void managerOldLiteralCullIsGone() {
        // The pre-C-8 cull admitted rows up to a full rowHeight above the
        // band (harmless only because the scissor clipped them) — the band
        // intersect is the truth now.
        assertFalse(source(MANAGER).contains("rowY + rowHeight() > listTop() - rowHeight()"),
                "the over-wide literal row cull must not return");
    }

    @Test
    void managerEditorPositionIsWrittenEveryFrameBeforeVisibility() {
        String src = source(MANAGER);
        // layoutRenameField: no visibility early-return sits between the
        // rowY computation and the setX/setY writes (a hidden editor must
        // never keep stale hit bounds from its last visible frame).
        String layout = methodBody(src, "protected EditBox layoutRenameField");
        assertNotNull(layout, "layoutRenameField must exist");
        int rowYIdx = layout.indexOf("int rowY =");
        int setXIdx = layout.indexOf("nameField.setX(");
        int returnNullIdx = layout.lastIndexOf("return null;");
        assertTrue(rowYIdx >= 0 && setXIdx > rowYIdx,
                "the field position must be derived and written inside layoutRenameField");
        assertTrue(returnNullIdx < rowYIdx || returnNullIdx < 0,
                "no stale-return path may skip the position writes");
        assertFalse(layout.contains("rowY < listTop() - rowHeight()"),
                "the old hidden-without-reposition early return must not return");
    }

    @Test
    void managerKeyboardAndPointerRoutesAreBandGated() {
        String src = source(MANAGER);
        assertTrue(src.contains("renameEditorVisible())"),
                "keyPressed/charTyped must suspend a scrolled-out editor (renameEditorVisible gate)");
        assertTrue(src.contains("mouseY >= listClipTop() && mouseY < listClipBottom()\n                && editorClickFirst("),
                "editorClickFirst must only see clicks inside the clip band");
        assertTrue(src.contains("renderEditorClipped"),
                "the base must provide the clipped editor painter");
    }

    @Test
    void managerEditorPaintsUnderTheListClip() {
        // The glass-pass hook is wrapped in the tracked scissor by the base,
        // and both subclasses render the editor through renderEditorClipped
        // gated on the field's band visibility.
        String base = source(MANAGER);
        int hookCall = base.indexOf("paintEditorGlassPass(ctx, rows, listX);");
        int enable = base.lastIndexOf("GlassSurface.enableScissor", hookCall);
        int disable = base.indexOf("GlassSurface.disableScissor", hookCall);
        assertTrue(hookCall >= 0 && enable >= 0 && disable > hookCall,
                "the editor glass pass must run inside the tracked list scissor");
        for (Path p : new Path[]{PROFILE, WAYPOINT}) {
            String s = source(p);
            assertTrue(s.contains("renderEditorClipped("),
                    p.getFileName() + " must render its editor through the clipped painter");
            assertTrue(s.contains("EditorVisible"),
                    p.getFileName() + " must gate editor paint/keys on band visibility");
        }
    }

    // ---- Profile create row: availability-aware consumption ----

    @Test
    void profileCreateRowUsesLiveGeometryAndAvailability() {
        String src = source(PROFILE);
        String body = methodBody(src, "protected boolean rowAreaClickFirst");
        assertNotNull(body);
        assertTrue(body.contains("int rowY = listTop() - (int) scroll.current();"),
                "the create-row hit test must use the LIVE leading-row geometry, not control bounds");
        assertTrue(body.contains("createControl.isAvailable()"),
                "activation must be availability-aware (clip-band gate), not bounds-only");
        assertTrue(body.contains("return true;"),
                "an in-rect click stays consumed even when rejected (no fall-through onto row 0)");
        assertFalse(body.contains("control.getX()"),
                "the stale-able control bounds must not drive the hit test");
    }

    // ---- Pack grid: the click walk is the render walk, band-clamped ----

    @Test
    void packGridClickWalkIsCulledAndBandClamped() {
        String src = source(PACKS);
        assertTrue(src.contains("forEachVisibleCard(list, cols, gridLeft, gridBottom,"),
                "the click walk must iterate the SAME culling walk the render uses");
        assertTrue(src.contains("gridBand.contains(mouseX, mouseY)"),
                "every card hit must additionally require the pointer inside the grid band");
        // The old unclamped full-list walk must not return.
        assertFalse(src.contains("int yBase = LIST_TOP + row * (CARD_H + CARD_GAP);"),
                "the pre-C-8 raw-rect full-list walk must not return");
    }

    @Test
    void packModalGeometryHasOneAnimatedTruth() {
        String src = source(PACKS);
        assertTrue(src.contains("private int detailModalY()"),
                "the modal's animated Y must live in one helper");
        // Both the paint and the hit test must derive from it.
        int paintY = src.indexOf("int modalY = detailModalY();", src.indexOf("private void renderDetailModal"));
        int hitY = src.indexOf("int modalY = detailModalY();", src.indexOf("private boolean handleDetailClick"));
        assertTrue(paintY >= 0 && hitY >= 0,
                "renderDetailModal and handleDetailClick must both use detailModalY");
        // And no independent final-position literal remains in the hit path.
        String hitBody = methodBody(src, "private boolean handleDetailClick");
        assertFalse(hitBody.contains("(this.height - modalH) / 2;"),
                "handleDetailClick must not re-derive the un-animated final position");
    }

    // ---- Pure geometry: the coupling math on concrete grid numbers ----

    @Test
    void packCardVisibleIntersectionGeometry() {
        // The same numbers the screen computes: LIST_TOP=56 (44+12), a
        // 230px viewport bottom, cards 96 tall at 106 stride.
        ClipBand band = new ClipBand(150, 54, 500, 230);
        // A card straddling the band's top: raw rect [.., 40..136) — its
        // pixels above y=54 are clipped away and must not be actionable,
        // its visible slab [54, 136) must be.
        assertFalse(band.contains(200, 53), "the clipped-away slab is not actionable");
        assertTrue(band.contains(200, 54), "the first visible pixel is actionable");
        assertTrue(band.intersects(150, 40, 220, 96), "the straddling card is partially visible");
        // A card fully above the band: invisible and inert.
        assertFalse(band.intersects(150, -50, 220, 96), "a fully hidden card is invisible");
        // A card fully below the fold.
        assertFalse(band.intersects(150, 284, 220, 96), "a card past the fold is invisible");
        assertTrue(band.intersects(150, 200, 220, 96), "the last visible card is visible");
        assertTrue(band.intersects(150, 188, 220, 96), "a straddling-the-fold card is still partially visible");
    }

    // ---- helpers ----

    /** Extracts one method body by signature fragment (best-effort brace matching). */
    private static String methodBody(String src, String signatureFragment) {
        int i = src.indexOf(signatureFragment);
        if (i < 0) return null;
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
