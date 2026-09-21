package com.aurora.client.mixin;

import com.aurora.client.util.HoverAnim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Phase C-6: the AbstractButtonMixin painter migration's source contracts —
 * the ownership boundary (vanilla keeps activation/narration/sound; the
 * mixin paints and observes only), the canonical hover vocabulary with no
 * focus aliasing, shared-painter reuse instead of a second implementation,
 * the radius token, and per-widget animation lifecycle. The mixin's render
 * path needs GL; runtime boots carry it (DevPilot {@code c6buttonmixin}).
 * One behavioral case exercises the shared press timeline headlessly.
 */
class AbstractButtonMixinConformanceTest {

    private static final String MIXIN =
            "src/client/java/com/aurora/client/mixin/AbstractButtonMixin.java";
    private static final String BUTTON =
            "src/client/java/com/aurora/client/ui/component/Button.java";

    private static String src(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (java.io.IOException e) {
            return fail(path + " not readable: " + e);
        }
    }

    // ---- Ownership boundary ----

    @Test
    void gateIsUnchangedAndFailsClosed() {
        String s = src(MIXIN);
        assertTrue(s.contains("if (!AuroraConfig.get().customTitleScreen) return false;"),
                "the customTitleScreen gate is preserved verbatim");
        assertTrue(s.contains("JoinMultiplayerScreen || mc.screen instanceof SelectWorldScreen"),
                "the two-screen gate is preserved");
        assertTrue(s.contains("getClass() == net.minecraft.client.gui.components.Button.Plain.class"),
                "exact Button.Plain match (ImageButton & future subclasses stay vanilla)");
        // The painter cancels ONLY renderWidget — never an input handler.
        assertEquals(1, count(s, "cancellable = true"),
                "exactly one cancellable inject (renderWidget)");
        assertTrue(s.contains("method = \"renderWidget\""));
        // The two observation hooks are HEAD-only, and the single
        // cancellable=true already pins that neither of them cancels.
        assertTrue(s.contains("method = \"onClick\", at = @At(\"HEAD\")"));
        assertTrue(s.contains("method = \"keyPressed\", at = @At(\"HEAD\")"));
    }

    @Test
    void vanillaOwnsInteractionAndTheMixinPlaysNoSound() {
        String s = src(MIXIN);
        // No semantic wrapper, no activation duplication, no narration path.
        assertFalse(s.contains("SemanticActionControl"), "no semantic wrapper on vanilla buttons");
        assertFalse(s.contains("onPress("), "the mixin never calls vanilla's activation");
        assertFalse(s.contains("updateWidgetNarration"), "narration stays vanilla");
        assertFalse(s.contains("Narration"), "narration stays vanilla");
        // Identifier-level pins (the words "sound"/"play" appear in the
        // ownership javadoc — the ban is on CODE paths).
        assertFalse(s.contains("playButtonClickSound"), "no direct vanilla click sound");
        assertFalse(s.contains("SoundEvents"), "no sound events");
        assertFalse(s.contains("SoundManager"), "no sound manager use");
        assertFalse(s.contains(".play("), "no playback calls");
        // No second enabled supplier: vanilla's active is authoritative.
        assertTrue(s.contains("self.active && self.isHovered()"),
                "the hover target consults vanilla's active flag, not a second supplier");
        assertTrue(s.contains("self.isFocused(), self.active"),
                "the painter consumes vanilla's focus/active state verbatim");
    }

    // ---- Canonical hover, no focus alias ----

    @Test
    void hoverIsCanonicalSymmetricAndPointerOnly() {
        String s = src(MIXIN);
        assertTrue(s.contains("HoverAnim.symmetric(140L)"),
                "the §8.3 canonical symmetric 140 ms vocabulary");
        assertEquals(1, count(s, "HoverAnim.symmetric"),
                "one long-lived animator — the field initializer, nothing per frame");
        assertFalse(s.contains("isHovered() || self.isFocused()"),
                "the banned focus→hover alias is gone");
        assertFalse(s.contains("isHoveredOrFocused"), "vanilla's aliasing helper is not used");
        assertFalse(s.contains("hoverStartMs"), "the hand-rolled millis math is gone");
        assertFalse(s.contains("easeOutCubic"), "no local hover easing curve");
    }

    @Test
    void focusIsAnIndependentChannel() {
        String s = src(MIXIN);
        // Focus feeds ONLY the painter's hairline argument — never the hover
        // animator's target.
        assertTrue(s.contains("hoverT, Button.pressScaleAt(aurora$pressStartMs),"),
                "hoverT and focus are separate painter inputs");
        assertTrue(s.contains("aurora$hover.update(self.active && self.isHovered())"),
                "the animator's ONLY input is pointer-in-bounds ∧ active");
    }

    // ---- Shared painter reuse ----

    @Test
    void mixinConsumesTheSharedPainterAndTimeline() {
        String s = src(MIXIN);
        assertTrue(s.contains("Button.paintFlatSecondary("),
                "the visual is the shared painter's, not a mixin-local copy");
        assertTrue(s.contains("Button.pressScaleAt(aurora$pressStartMs)"),
                "the press timeline is the shared implementation");
        assertFalse(s.contains("lerpFloat"), "no duplicated press math");
        assertFalse(s.contains("drawRoundedRectAA"), "no mixin-local surface drawing");
        assertFalse(s.contains("drawString"), "no mixin-local text drawing");
        assertFalse(s.contains("cachedLabel"), "the label-width cache went with the old painter");
    }

    @Test
    void buttonKeepsASingleSourceForTheFlatSecondaryLook() throws Exception {
        String s = src(BUTTON);
        assertTrue(s.contains("static int secondaryFlatFill(float hoverT)"));
        assertTrue(s.contains("static int secondaryFlatBorder(float hoverT)"));
        // renderOverlay's secondary branch and the resting template both
        // consume the helpers — no parallel expression survives.
        assertTrue(s.contains("bg = secondaryFlatFill(hoverT);"));
        assertTrue(s.contains("border = secondaryFlatBorder(hoverT);"));
        assertTrue(s.contains("secondaryFlatFill(0f)"));
        // The raw ramp expression appears exactly ONCE in the whole file —
        // inside the helper's own body (the helper signature strip in the
        // old form of this pin missed the body).
        assertEquals(1, count(s, "color(ThemeToken.SURFACE),"),
                "the raw SURFACE ramp exists only inside the helper");
        assertTrue(s.contains("public static float pressScaleAt(long pressDownStartMs)"),
                "the shared press timeline");
        assertTrue(s.contains("return pressScaleAt(pressDownStartMs);"),
                "Button's own scale path delegates to it");
    }

    @Test
    void sharedPainterIsPureVisualsAndOwnsNoSemantics() throws Exception {
        Class<?> button = Class.forName("com.aurora.client.ui.component.Button");
        Method paint = button.getDeclaredMethod("paintFlatSecondary",
                net.minecraft.client.gui.GuiGraphics.class,
                net.minecraft.client.gui.Font.class,
                net.minecraft.network.chat.Component.class,
                int.class, int.class, int.class, int.class,
                float.class, float.class, boolean.class, boolean.class);
        assertTrue(Modifier.isStatic(paint.getModifiers()));
        assertTrue(Modifier.isPublic(paint.getModifiers()));
        Method scale = button.getDeclaredMethod("pressScaleAt", long.class);
        assertTrue(Modifier.isStatic(scale.getModifiers()));
        // The painter's source contains no activation/sound vocabulary.
        String body = src(BUTTON);
        int start = body.indexOf("public static void paintFlatSecondary");
        int end = body.indexOf("private float currentScale()", start);
        String painter = body.substring(start, end);
        assertFalse(painter.contains("onPress"));
        assertFalse(painter.contains("play"));
        assertFalse(painter.contains("mouseClicked"));
    }

    // ---- Radius token ----

    @Test
    void radiusResolvesThroughTheToken() {
        String b = src(BUTTON);
        int start = b.indexOf("public static void paintFlatSecondary");
        String painter = b.substring(start, b.indexOf("private float currentScale()", start));
        assertTrue(painter.contains("roundness().radiusSmall()"),
                "the painter resolves the C-7 radius token");
        assertEquals(1, count(painter, "float radius ="),
                "one radius derivation, and it is the token line above");
    }

    // ---- Lifecycle: per-widget state, no static maps ----

    @Test
    void animationStateIsPerWidgetInstanceFields() {
        String s = src(MIXIN);
        assertTrue(s.contains("@Unique private final HoverAnim aurora$hover"),
                "one animator per widget, final, constructed once");
        Field[] fields = AbstractButtonMixin.class.getDeclaredFields();
        for (Field f : fields) {
            assertFalse(java.lang.reflect.Modifier.isStatic(f.getModifiers()),
                    "no static state in the mixin: " + f.getName());
        }
        assertFalse(s.contains("new HashMap"), "no static map");
        assertFalse(s.contains("Map<"), "no cache map at all");
    }

    @Test
    void noPerFrameConstruction() {
        String s = src(MIXIN);
        // The only construction is the field initializer (runs once per
        // widget in <init>); the render/observation bodies allocate nothing.
        assertFalse(s.contains("System.currentTimeMillis") && count(s, "System.currentTimeMillis") > 2,
                "currentTimeMillis appears only in the two observation arms");
        assertTrue(count(s, "new ") == 0, "no allocations anywhere in the mixin body");
    }

    // ---- Behavioral: the shared press timeline (headless) ----

    @Test
    void sharedPressTimelineMatchesTheCanonicalVocabulary() {
        // Expired/inactive stamps render at rest scale.
        assertEquals(1.0f, com.aurora.client.ui.component.Button.pressScaleAt(-1L), 1e-6f);
        assertEquals(1.0f, com.aurora.client.ui.component.Button.pressScaleAt(0L), 1e-6f);
        // A stamp in the far past is fully recovered.
        assertEquals(1.0f, com.aurora.client.ui.component.Button.pressScaleAt(
                System.currentTimeMillis() - 10_000L), 1e-6f);
        // A stamp 20 ms old is part-way into the 90 ms descent: strictly
        // between rest and the 0.96 floor; 60 ms old is deeper still.
        long start = System.currentTimeMillis();
        float early = com.aurora.client.ui.component.Button.pressScaleAt(start - 20L);
        assertTrue(early > 0.96f && early < 1.0f, "early=" + early);
        float mid = com.aurora.client.ui.component.Button.pressScaleAt(start - 60L);
        assertTrue(mid < early && mid >= 0.959f,
                "monotonic descent toward the 0.96 floor (early=" + early + " mid=" + mid + ")");
        float floor = com.aurora.client.ui.component.Button.pressScaleAt(start - 200L);
        // 200 ms is inside the 180 ms recovery window: already past the floor.
        assertTrue(floor >= 0.96f, "recovery stays at/above the floor (floor=" + floor + ")");
    }

    // ---- HoverAnim vocabulary sanity (the symmetric contract the mixin relies on) ----

    @Test
    void theCanonicalAnimatorNeverSnapsFromRest() {
        HoverAnim a = HoverAnim.symmetric(140L);
        // A fresh leg starts at zero progress — no backdated snap-in.
        float first = a.update(true);
        assertTrue(first < 0.5f, "first sample must be mid-flight from rest, got " + first);
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
