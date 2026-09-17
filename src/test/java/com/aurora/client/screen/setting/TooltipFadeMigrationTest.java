package com.aurora.client.screen.setting;

import com.aurora.client.util.HoverAnim;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B tooltip-fade migration: the sole production tooltip system (the
 * {@link FeatureSetting} label-dwell tooltip) fades with the §8.3
 * symmetric vocabulary at its own 200 ms — supplemental-information motion,
 * distinct from the 140 ms control-hover duration. These tests pin the
 * migration's contract: no snap on fade-in, symmetric settle, continuous
 * mid-flight reversal, the unchanged 1500 ms dwell gate (incl. per-label
 * identity and disabled eligibility). The dwell map and claim flags are
 * seeded through reflection so no test sleeps a real dwell; the shared
 * statics are restored after each test.
 */
class TooltipFadeMigrationTest {

    private static final long DWELL_MS = 1500L;

    private HoverAnim fade;
    private Map<String, Long> dwellMap;
    private Field claimedField;
    private Field contentField;
    private boolean savedClaimed;
    private String savedContent;

    @BeforeEach
    void setUp() throws Exception {
        fade = (HoverAnim) field("TOOLTIP_FADE").get(null);
        dwellMap = (Map<String, Long>) field("HOVER_DWELL").get(null);
        claimedField = field("tooltipClaimed");
        contentField = field("tooltipContent");
        savedClaimed = claimedField.getBoolean(null);
        savedContent = (String) contentField.get(null);
        // Start every test from a fully-settled, fully-faded-out animator
        // so shared static state cannot leak between tests.
        settleToZero();
    }

    @AfterEach
    void tearDown() throws Exception {
        settleToZero();
        claimedField.setBoolean(null, savedClaimed);
        contentField.set(null, savedContent);
        dwellMap.clear();
    }

    @Test
    void fadeInDoesNotSnapFromRest() {
        // The legacy constructor's first update(true) from locked rest
        // returned exactly 1 (the snap-in); symmetric mode must still be
        // mid-flight at zero progress.
        float first = fade.update(true);
        assertTrue(first < 0.5f, "tooltip fade-in must animate from rest, got " + first);
        assertTrue(first >= 0f);
    }

    @Test
    void fadeIsSymmetricAt200ms() throws Exception {
        fade.update(true);
        Thread.sleep(210);
        assertEquals(1f, fade.update(true), 1e-6f, "fade-in settles at exactly 1 within 200 ms");
        fade.update(false);
        Thread.sleep(210);
        assertEquals(0f, fade.update(false), 1e-6f, "fade-out settles at exactly 0 within 200 ms");
    }

    @Test
    void midFlightFadeInReversesContinuously() throws Exception {
        fade.update(true);
        Thread.sleep(80);
        float mid = fade.update(true);
        assertTrue(mid > 0f && mid < 1f, "expected mid-flight fade, got " + mid);
        // Eligibility ends mid-fade-in: the reversal continues from the
        // current progress — no jump to either endpoint.
        float reversed = fade.update(false);
        assertTrue(reversed > 0f, "reversal must not snap to 0, got " + reversed);
        assertTrue(reversed <= mid + 0.05f, "reversal must not brighten, got " + reversed + " after " + mid);
    }

    @Test
    void noClaimBeforeDwellThreshold() throws Exception {
        // Hovered for 1400 ms — 100 ms short of the dwell: no claim, and
        // the fade stays at rest (no tooltip pixels before dwell).
        hoverDweltFor("short", 1400);
        assertFalse(claimed(), "no tooltip claim before 1500 ms dwell");
        assertEquals(0f, fade.update(claimed()), "fade must remain at rest before dwell");
    }

    @Test
    void claimBeginsFadeFromRestAtDwell() throws Exception {
        // Dwell satisfied: the claim fires THIS frame and the fade starts
        // from rest (the fade does not shorten or front-run the dwell —
        // 1400 ms of dwell never produced partial fade above).
        hoverDweltFor("full", DWELL_MS + 100);
        assertTrue(claimed(), "dwell completion must claim the tooltip");
        float first = fade.update(true);
        assertTrue(first < 0.5f, "fade must BEGIN at dwell completion, not arrive settled: " + first);
    }

    @Test
    void disabledLabelsNeverClaim() throws Exception {
        // A dwell clock predates the row becoming disabled; the disabled
        // pass must neither claim nor preserve the stale dwell entry.
        dwellMap.put("disabled", System.currentTimeMillis() - (DWELL_MS + 100));
        FeatureSetting.trackLabelHover("disabled", () -> "desc",
                0, 0, 100, 9, 10, 4, true);
        assertFalse(claimed(), "disabled rows never dwell — no claim, no fade");
        assertFalse(dwellMap.containsKey("disabled"), "disabled pass drops the stale dwell entry");
    }

    @Test
    void leavingTheLabelEndsEligibility() throws Exception {
        hoverDweltFor("leave", DWELL_MS + 100);
        assertTrue(claimed());
        claimedField.setBoolean(null, false); // drawPendingTooltip consumed it
        // Next frame: pointer elsewhere — the label's renderer either
        // reports not-hovered (dwell reset) or doesn't run. No re-claim.
        FeatureSetting.trackLabelHover("leave", () -> "desc",
                0, 0, 100, 9, 500, 500, false);
        assertFalse(claimed(), "leaving the label must not re-claim");
        assertTrue(dwellMap.isEmpty(), "leaving the label resets its dwell timestamp");
    }

    @Test
    void tooltipIdentityRequiresItsOwnDwell() throws Exception {
        // Label A fully dwelt and claimed; the pointer moves to label B.
        // B must dwell its own 1500 ms — A's fade state must not let B's
        // tooltip appear (and A's fade-out proceeds on eligibility alone).
        hoverDweltFor("labelA", DWELL_MS + 100);
        assertTrue(claimed());
        String contentA = (String) contentField.get(null);
        claimedField.setBoolean(null, false); // frame consumed A's claim

        // B hovered for only 200 ms: no claim, content still A (frozen).
        FeatureSetting.trackLabelHover("labelB", () -> "desc B",
                0, 0, 100, 9, 10, 4, false);
        assertFalse(claimed(), "label B must not inherit label A's dwell");
        assertEquals(contentA, contentField.get(null), "content identity is frozen per claim");
        assertTrue(dwellMap.containsKey("labelB"), "B starts its own dwell clock");
    }

    // ===== helpers =====

    /** Simulates a continuous hover of {@code ms} by seeding the dwell map,
     *  then running one live track pass at the label's coordinates. */
    private void hoverDweltFor(String key, long ms) throws Exception {
        long now = System.currentTimeMillis();
        dwellMap.put(key, now - ms);
        FeatureSetting.trackLabelHover(key, () -> "desc",
                0, 0, 100, 9, 10, 4, false);
    }

    private boolean claimed() throws Exception {
        return claimedField.getBoolean(null);
    }

    /** Drives the shared animator to a locked 0 without waiting a full leg
     *  when it is already at rest (the common case between tests). */
    private void settleToZero() throws Exception {
        if (fade.current() == 0f) return;
        fade.update(false);
        long deadline = System.currentTimeMillis() + 400;
        while (fade.current() > 0f && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            fade.update(false);
        }
        assertEquals(0f, fade.current(), "shared fade animator must settle to 0 between tests");
    }

    private static Field field(String name) throws Exception {
        Field f = FeatureSetting.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }
}
