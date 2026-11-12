package com.aurora.client.ui.render.blur;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeDefinition;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeMode;
import com.aurora.client.theme.ThemeRoundness;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Blur + lighting verification screen — one panel, three backdrops, a
 * radius knob, and the four toggles needed to exercise the lighting
 * phase: on/off, raised/depressed, corner style, and theme mode.
 * Deliberately nothing beyond that: no probes, no shader dumps, no
 * benchmarks. A one-line fps log every 5 s is the only instrumentation
 * (it doubles as the frame-cost check for the lighting math).
 *
 * <p>Verification protocol: confirm on the STATIC backdrops first (SOLID
 * is the negative control; STRIPES is the positive control), with plain
 * before/after captures (lighting off vs on) for both raised and
 * depressed, then across corner styles ([C] cycles the theme roundness)
 * and both theme modes ([N] flips Dark/Light), then finally the live
 * world.
 *
 * <p>[C], [N], and the [O]/[P] opacity knob mutate the theme IN MEMORY
 * ONLY (never persisted); {@link #removed()} restores whatever was active
 * when the screen opened.
 *
 * <p>Reached only via the default-unbound "Blur Panel Test" keybind,
 * in-world with no other screen open.
 *
 * <p><b>Known caveat:</b> pressing F2 (vanilla screenshot) while this
 * screen renders crashes the game — see the KNOWN ISSUE note in
 * {@link BlurPanelRenderer}. Verification therefore uses ordinary window
 * captures, not in-game screenshots.
 */
public class BlurTestScreen extends Screen {

    private enum Backdrop {
        SOLID("Solid color (negative control)"),
        STRIPES("Static stripes (positive control)"),
        WORLD("Live world");
        final String label;
        Backdrop(String label) { this.label = label; }
    }

    private static final float PANEL_W = 240f;
    private static final float PANEL_H = 150f;

    // Static so the last test state survives closing/reopening the screen.
    private static Backdrop mode = Backdrop.STRIPES;
    private static float blurRadius = BlurPanelRenderer.DEFAULT_BLUR_RADIUS_PX;
    private static boolean lightingOn = true;
    private static boolean depressedMode = false;
    // Harness-overridable lighting strengths (default 0.45 / 0.10).
    private static float edgeStrength = 0.45f;
    private static float gradStrength = 0.10f;

    // ---- R10: output-pool over-subscription stress (harness only) ----
    // stress=N (state file) / [G] renders N extra small panels in a
    // deliberately ADVERSARIAL render order — the first quarter DETAIL,
    // then ROW, then CONTROL, and WINDOW LAST — so render order and
    // priority disagree and the two admission policies are told apart at
    // a glance: first-come keeps the early DETAIL tiles and drops the late
    // WINDOW tiles; the priority reservation does the reverse. Each tile
    // is labelled with its tier letter; a declined tile draws its flat
    // fallback with a red outline and an "x". One log line per second
    // reports drew/asked per tier. Doubles as the per-panel cost curve
    // (fps line + GlassStats at N = 8 … 96).
    private static int stressN = 0;
    // Cost isolation knobs (state tokens stressglass=/stressfill=): with
    // glass off the tiles are drawn flat only (harness overhead); with fill
    // off only the glass blit is drawn (pipeline + blit cost alone).
    private static boolean stressGlass = true;
    private static boolean stressFill = true;
    private long lastStressLogMs;
    private static final int STRESS_COLS = 12;
    private static final float STRESS_W = 56f, STRESS_H = 14f;

    // Theme state captured on open so [C]/[N]/[O][P] mutations can be restored.
    private int originalCornerRadius;
    private ThemeMode originalMode;
    private double originalOpacity;

    private long lastFpsLogMs;

    // ---- Synthetic backdrop target (test harness) ----
    // Batched GuiGraphicsExtractor fills only reach the framebuffer when the GUI
    // batch flushes — AFTER renderPanel's immediate capture — so the
    // SOLID/STRIPES controls are rendered into this dedicated FBO each
    // frame and handed to the renderer as a CaptureSource. The batched
    // fills are still drawn for the eye; this FBO is what the blur samples.
    private final PortableBlurRenderer.SyntheticBackdrop portableBackdrop = new PortableBlurRenderer.SyntheticBackdrop();

    // ---- Controlled-motion experiment (2026-09-09 shimmer investigation) ----
    // slide=<float device px/frame> scrolls the synthetic STRIPES pattern
    // vertically by a known sub-pixel amount per rendered frame, so the REAL
    // pipeline can be driven with a KNOWN input signal and its output
    // compared against an analytically computed ideal (area-average +
    // Gaussian + bilinear upscale of the same pattern) — a noise measurement
    // with no backdrop-content variance and no vanilla post-chain dither in
    // the loop. slideAccum is the live phase in device px, advanced once per
    // rendered frame; the DevPilot sampler logs it per capture so the offline
    // reference can reproduce each sample's exact pattern phase.
    // slideamp=<0..0.12> adds a deterministic integer-hash per-pixel detail
    // term to the pattern — aliasing-RICH content (energy well above the
    // quarter-res chain's Nyquist), still bit-reproducible offline because
    // the hash is plain uint32 wraparound arithmetic mirrored in the
    // analyzer.
    private static volatile float slidePxPerFrame = 0f;
    private static volatile float slideAccum = 0f;
    private static volatile float slideDetailAmp = 0f;

    /** Live stripe-slide phase in device px (0 when not sliding). Harness read. */
    public static float slideAccum() { return slideAccum; }

    public BlurTestScreen() {
        super(Component.literal("Blur Panel Test"));
    }

    /** A {@code corner} argument: a pixel radius, or a retired Corner Style name. */
    private static int cornerArg(String v) {
        try {
            return ThemeRoundness.clamp(Integer.parseInt(v.trim()));
        } catch (NumberFormatException e) {
            return ThemeRoundness.legacyRadius(v);
        }
    }

    @Override
    protected void init() {
        AuroraConfig cfg = AuroraConfig.get();
        ThemeMode m = cfg.themeOrDefault().mode;
        originalCornerRadius = cfg.themeOrDefault().cornerRadius;
        originalMode = m != null ? m : ThemeMode.DARK;
        originalOpacity = ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity);
        com.aurora.client.AuroraClient.LOGGER.info(
                "[BlurTest] opened (lighting={} depressed={} backdrop={} radius={} corner={} theme={} opacity={})",
                lightingOn, depressedMode, mode, (int) blurRadius,
                originalCornerRadius + " px", originalMode,
                String.format(java.util.Locale.ROOT, "%.2f", originalOpacity));
    }

    /**
     * Intentionally empty. Vanilla's {@code renderBackground} sandwiches a
     * full-screen blur + dark gradient BETWEEN the world and any screen —
     * the glass panel would then sample that already-darkened backdrop and
     * read as a solid dark card no matter how low its own tint is (the
     * "nearly solid black" world mode). This harness supplies its own blur
     * via the panel pipeline, so the vanilla background is suppressed and
     * the glass samples the true, undimmed world. Real Aurora screens keep
     * the vanilla behavior — this is test-harness only.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        // no-op by design (see javadoc)
    }

    // ---- Harness state channel ----
    // Posted keyboard events proved unreliable against MC 1.21.11's new
    // input pipeline (dropped, duplicated, reordered). While this screen
    // is open it polls a small state file each frame and applies any NEW
    // content, echoing it to the log — the verification driver writes a
    // state line, waits for the echo, then captures. Keys still work for
    // manual use.
    private String polledState = "";

    private void pollStateFile() {
        try {
            java.nio.file.Path p = java.nio.file.Path.of("blurtest_state.txt");
            if (!java.nio.file.Files.exists(p)) return;
            String s = java.nio.file.Files.readString(p).trim();
            if (s.isEmpty() || s.equals(polledState)) return;
            polledState = s;
            applyState(s);
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] state: {}", s);
        } catch (Throwable ignored) {
            // Best-effort harness channel — never crash the screen on it.
        }
    }

    /** Apply {@code k=v} tokens: backdrop, lighting, depressed, radius, edge, grad, corner, theme, opacity. */
    private void applyState(String s) {
        AuroraConfig cfg = AuroraConfig.get();
        boolean themeChanged = false;
        for (String tok : s.split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            String k = tok.substring(0, eq);
            String v = tok.substring(eq + 1);
            switch (k) {
                case "backdrop" -> {
                    try { mode = Backdrop.valueOf(v); } catch (IllegalArgumentException ignored) {}
                }
                case "lighting" -> lightingOn = v.equalsIgnoreCase("on");
                case "depressed" -> depressedMode = v.equalsIgnoreCase("on");
                case "radius" -> {
                    try { blurRadius = Math.max(0f, Math.min(64f, Float.parseFloat(v))); } catch (NumberFormatException ignored) {}
                }
                case "edge" -> {
                    try { edgeStrength = Math.max(0f, Math.min(1f, Float.parseFloat(v))); } catch (NumberFormatException ignored) {}
                }
                case "grad" -> {
                    try { gradStrength = Math.max(0f, Math.min(1f, Float.parseFloat(v))); } catch (NumberFormatException ignored) {}
                }
                case "corner" -> {
                    try { cfg.themeOrDefault().cornerRadius = cornerArg(v); themeChanged = true; } catch (IllegalArgumentException ignored) {}
                }
                case "theme" -> {
                    try { cfg.themeOrDefault().mode = ThemeMode.valueOf(v); themeChanged = true; } catch (IllegalArgumentException ignored) {}
                }
                case "opacity" -> {
                    try {
                        cfg.themeOrDefault().backgroundOpacity = ThemeDefinition.normalizedOpacity(Double.parseDouble(v));
                        themeChanged = true;
                    } catch (NumberFormatException ignored) {}
                }
                // Crash-canary command: drives the REAL vanilla grab path
                // (the identical call F2 makes) while this screen renders.
                case "shot" -> issueVanillaGrab();
                // No-op token: the harness appends ping=N to every line and
                // waits for this screen's "[BlurTest] state:" echo, proving
                // the line was consumed (and the screen is on top).
                case "ping" -> { }
                // R10 stress: number of extra priority-tagged panels (0 = off).
                case "stress" -> {
                    try { stressN = Math.max(0, Math.min(200, Integer.parseInt(v))); } catch (NumberFormatException ignored) {}
                }
                case "stressglass" -> stressGlass = v.equalsIgnoreCase("on");
                case "stressfill" -> stressFill = v.equalsIgnoreCase("on");
                // Controlled-motion experiment (see slidePxPerFrame): known
                // sub-pixel stripe scroll through the real pipeline.
                case "slide" -> {
                    try {
                        slidePxPerFrame = Math.max(0f, Math.min(8f, Float.parseFloat(v)));
                        slideAccum = 0f;
                    } catch (NumberFormatException ignored) { }
                }
                case "slideamp" -> {
                    try { slideDetailAmp = Math.max(0f, Math.min(0.12f, Float.parseFloat(v))); } catch (NumberFormatException ignored) { }
                }
                default -> { }
            }
        }
        if (themeChanged) ThemeManager.reload();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        pollStateFile();

        // 1. Static backdrop (negative/positive controls). The batched fills
        //    are what the EYE sees around the panel; the same pattern is
        //    rendered into the synthetic FBO below, which is what the blur
        //    actually samples (see the backdrop-target note above).
        //    WORLD uses neither — the blur samples the live framebuffer.
        BlurPanelRenderer.CaptureSource captureSource = null;
        if (mode != Backdrop.WORLD) {
            drawBackdrop(g);
            if (slidePxPerFrame != 0f) slideAccum += slidePxPerFrame; // once per rendered frame
            captureSource = renderSyntheticBackdrop();
        }

        // 2. The blur panel itself, with the lighting impression when on.
        //    RAISED/DEPRESSED only inverts the two lighting terms.
        float radius = ThemeManager.current().roundness().radius();
        float px = (this.width - PANEL_W) / 2f;
        float py = (this.height - PANEL_H) / 2f - 20f;
        BlurPanelRenderer.Lighting lighting = lightingOn
                ? new BlurPanelRenderer.Lighting(depressedMode, edgeStrength, gradStrength)
                : null;
        boolean ok = BlurPanelRenderer.renderPanel(g, px, py, PANEL_W, PANEL_H, radius, blurRadius,
                lighting, captureSource);

        // 2b. R10 stress tiles — AFTER the main panel (which is a CONTROL
        //     claim, first-come), so the tiles are the over-subscribing part.
        if (stressN > 0) renderStress(g, captureSource);

        // 3. THE TINT — and the single place Background Opacity is applied.
        //    WINDOW_FILL's alpha already carries the Background Opacity
        //    slider (resolved once in ThemeResolver); drawing the normal
        //    translucent fill over the blur is the whole tint model. There
        //    is intentionally NO second opacity factor anywhere.
        RenderUtil.drawRoundedRectAA(g, px, py, PANEL_W, PANEL_H, radius,
                ThemeManager.color(ThemeToken.WINDOW_FILL));
        RenderUtil.drawRoundedOutlineAA(g, px, py, PANEL_W, PANEL_H, radius, 1.0f,
                ThemeManager.color(ThemeToken.WINDOW_OUTLINE));

        // 4. Status + hints (plain text; the renderer's own outcome string,
        //    never just the setting). Four lines, all in the panel's upper
        //    band so the panel body stays a readable test surface.
        int body = 0xFFE3E6EA;
        int dim = 0xFF9AA0A6;
        int tx = (int) (px + 12);
        int ty = (int) (py + 14);
        String light = lightingOn ? ("ON " + (depressedMode ? "DEPRESSED" : "RAISED")) : "OFF";
        String status = ok ? "Panel: BLURRED (r " + (int) blurRadius + ") | Lighting: " + light
                           : "Panel: FALLBACK - " + BlurPanelRenderer.lastOutcome();
        g.text(this.font, status, tx, ty, body, false);
        g.text(this.font, "Backdrop: " + mode.label
                        + " | Corner: " + ThemeManager.current().roundness()
                        + " | Theme: " + ThemeManager.current().mode()
                        + " | Op: " + Math.round(ThemeManager.current().backgroundOpacity() * 100) + "%",
                tx, ty + 12, dim, false);
        g.text(this.font, "[B]ackdrop  [L]ighting  [M] raised/depressed  [C]orner  [N] theme  [+/-] radius  [O/P] opacity",
                tx, ty + 26, dim, false);
        g.text(this.font, "Light: fixed top, ~14 deg right. Opacity = WINDOW_FILL alpha (single application point).",
                tx, ty + 38, dim, false);

        // 5. One fps line every 5 s — the frame-cost check (and soak monitor).
        long now = System.currentTimeMillis();
        if (now - lastFpsLogMs >= 5000) {
            lastFpsLogMs = now;
            int fps = Minecraft.getInstance().getFps();
            com.aurora.client.AuroraClient.LOGGER.info(
                    "[BlurTest] fps={} ({} ms/frame) lighting={} depressed={} backdrop={}",
                    fps, String.format(java.util.Locale.ROOT, "%.1f", 1000.0 / Math.max(1, fps)),
                    lightingOn, depressedMode, mode);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent kev) {
        int keyCode = kev.key();
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_B) {
            mode = switch (mode) {
                case SOLID -> Backdrop.STRIPES;
                case STRIPES -> Backdrop.WORLD;
                case WORLD -> Backdrop.SOLID;
            };
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] B: backdrop={}", mode);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_EQUALS || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ADD) {
            blurRadius = Math.min(64f, blurRadius + 4f);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_MINUS || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
            blurRadius = Math.max(0f, blurRadius - 4f);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_L) {
            lightingOn = !lightingOn;
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] L: lighting={}", lightingOn);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_M) {
            depressedMode = !depressedMode;
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] M: depressed={}", depressedMode);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_C) {
            // Cycle corner style IN MEMORY (restored on close, never saved).
            // Steps through the radii the retired Corner Style choices stood for.
            int cur = AuroraConfig.get().themeOrDefault().cornerRadius;
            int next = cur > ThemeRoundness.SLIGHTLY_ROUND.radius() ? ThemeRoundness.SLIGHTLY_ROUND.radius()
                    : cur > 0 ? 0 : ThemeRoundness.DEFAULT_RADIUS;
            AuroraConfig.get().themeOrDefault().cornerRadius = next;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] C: corner={} px", next);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_N) {
            // Flip Dark/Light IN MEMORY (restored on close, never saved).
            ThemeMode cur = AuroraConfig.get().themeOrDefault().mode;
            AuroraConfig.get().themeOrDefault().mode = (cur == ThemeMode.LIGHT) ? ThemeMode.DARK : ThemeMode.LIGHT;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] N: theme={}",
                    AuroraConfig.get().themeOrDefault().mode);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_O
                || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_P) {
            // Live opacity knob: adjusts the theme's backgroundOpacity IN
            // MEMORY (restored on close). This is the same single value
            // ThemeResolver stamps into WINDOW_FILL's alpha — the ONE
            // documented opacity application point — so no second
            // application point is created by the knob.
            double step = (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_P) ? 0.05 : -0.05;
            AuroraConfig cfg = AuroraConfig.get();
            double next = Math.max(0.05, Math.min(1.0,
                    ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity) + step));
            cfg.themeOrDefault().backgroundOpacity = next;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] {}: opacity={}",
                    (char) keyCode, String.format(java.util.Locale.ROOT, "%.2f", next));
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_G) {
            // R10 stress: cycle the extra panel count through the pool sizes
            // of interest (0 = off).
            int[] steps = {0, 24, 32, 48, 64, 96, 120};
            int idx = 0;
            for (int i = 0; i < steps.length; i++) if (steps[i] == stressN) idx = i;
            stressN = steps[(idx + 1) % steps.length];
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] G: stress={}", stressN);
            return true;
        }
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_S) {
            // Crash CANARY, deliberately re-enabled now that the screenshot
            // interlock exists (see BlurPanelRenderer's screenshot-interlock
            // note): [S] drives the real vanilla grab — the identical path
            // F2 takes — while this screen renders. Expected: suppression
            // engages (status shows FALLBACK for ~400 ms), the screenshot
            // lands in run/screenshots, and the JVM survives. If this ever
            // corrupts the heap again, the interlock regressed.
            issueVanillaGrab();
            return true;
        }
        return super.keyPressed(kev);
    }

    /**
     * Drives the real vanilla screenshot path — byte-for-byte what F2 does —
     * in the F2-equivalent CONTEXT too: deferred via {@code execute} to the
     * render thread's next task drain (start of the next runTick, outside
     * GuiRenderState recording), because the state token that triggers this
     * is consumed mid-render inside Screen.render. Used by the [S] canary
     * key and the harness's {@code shot=} token; posting a real F2 keypress
     * is unreliable on MC 1.21.11.
     */
    private void issueVanillaGrab() {
        try {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                try {
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory, null,
                            com.aurora.client.adapter.NativeUi.mainTarget(mc), 1,
                            c -> com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] grab: {}", c.getString()));
                    com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] grab issued (deferred, F2-equivalent context)");
                } catch (Throwable t) {
                    com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] grab failed: {}", String.valueOf(t));
                }
            });
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] grab queued; glass suppression engages at grab head");
        } catch (Throwable t) {
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] grab queue failed: {}", String.valueOf(t));
        }
    }

    /**
     * R10 stress tiles (see {@link #stressN}): {@code stressN} small raised
     * panels in a 12-column grid at the top-left, tier assigned by position
     * in RENDER order — first quarter DETAIL, then ROW, CONTROL, WINDOW last.
     */
    private void renderStress(GuiGraphicsExtractor g, BlurPanelRenderer.CaptureSource captureSource) {
        BlurPanelRenderer.Priority[] order = BlurPanelRenderer.Priority.values();
        int[] asked = new int[order.length];
        int[] drew = new int[order.length];
        float r = ThemeManager.current().roundness().radiusSmall();
        int tint = ThemeManager.color(ThemeToken.WINDOW_FILL);
        for (int i = 0; i < stressN; i++) {
            int tier = order.length - 1 - (int) ((long) i * order.length / stressN);
            BlurPanelRenderer.Priority p = order[tier];
            float x = 16 + (i % STRESS_COLS) * (STRESS_W + 4);
            float y = 16 + (i / STRESS_COLS) * (STRESS_H + 4);
            asked[tier]++;
            boolean drewGlass = stressGlass && BlurPanelRenderer.renderPanel(g, x, y, STRESS_W, STRESS_H, r,
                    blurRadius, BlurPanelRenderer.Lighting.raised(), p, captureSource);
            if (drewGlass) drew[tier]++;
            if (!stressFill) continue;
            RenderUtil.drawRoundedRectAA(g, x, y, STRESS_W, STRESS_H, r, drewGlass ? tint : 0xE0202020);
            RenderUtil.drawRoundedOutlineAA(g, x, y, STRESS_W, STRESS_H, r, 1.0f,
                    drewGlass ? 0xFF40D080 : 0xFFE05050);
            g.text(this.font, p.name().charAt(0) + (drewGlass ? "" : " x"),
                    (int) x + 3, (int) y + 3, drewGlass ? 0xFFFFFFFF : 0xFFFF8080, false);
        }
        long now = System.currentTimeMillis();
        if (now - lastStressLogMs >= 1000) {
            lastStressLogMs = now;
            int fps = Minecraft.getInstance().getFps();
            com.aurora.client.AuroraClient.LOGGER.info(
                    "[BlurTest] stress N={} glass={} fill={} drew/asked W={}/{} C={}/{} R={}/{} D={}/{} fps={} ({} ms/frame)",
                    stressN, stressGlass, stressFill,
                    drew[0], asked[0], drew[1], asked[1], drew[2], asked[2], drew[3], asked[3],
                    fps, String.format(java.util.Locale.ROOT, "%.1f", 1000.0 / Math.max(1, fps)));
        }
    }

    @Override
    public void removed() {
        stressN = 0;
        slidePxPerFrame = 0f;
        slideAccum = 0f;
        slideDetailAmp = 0f;
        // Restore the theme values this screen may have cycled in memory —
        // [C]/[N] deliberately never persist, so the user's saved theme is
        // exactly what it was before the harness opened.
        AuroraConfig cfg = AuroraConfig.get();
        boolean changed = false;
        if (cfg.themeOrDefault().cornerRadius != originalCornerRadius) {
            cfg.themeOrDefault().cornerRadius = originalCornerRadius;
            changed = true;
        }
        ThemeMode m = cfg.themeOrDefault().mode;
        if (m != originalMode) { cfg.themeOrDefault().mode = originalMode; changed = true; }
        double o = ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity);
        if (o != originalOpacity) { cfg.themeOrDefault().backgroundOpacity = originalOpacity; changed = true; }
        if (changed) ThemeManager.reload();
        // Release the synthetic backdrop target (the panel renderer owns
        // its own objects).
        portableBackdrop.close();
        super.removed();
    }

    /**
     * Renders this frame's SOLID/STRIPES pattern into the dedicated
     * synthetic backdrop FBO and returns it as a {@code CaptureSource}.
     * Test-harness machinery only — mirrors the colors of the batched
     * {@link #drawBackdrop} fills so the eye and the blur sample agree.
     */
    private BlurPanelRenderer.CaptureSource renderSyntheticBackdrop() {
        var main = com.aurora.client.adapter.NativeUi.mainTarget(Minecraft.getInstance());
        return portableBackdrop.render(main.width, main.height,
                (float) Minecraft.getInstance().getWindow().getGuiScale(),
                mode == Backdrop.STRIPES, slideAccum, slideDetailAmp);
    }

    private void drawBackdrop(GuiGraphicsExtractor g) {
        if (mode == Backdrop.SOLID) {
            g.fill(0, 0, this.width, this.height, 0xFF85878C);
            return;
        }
        int band = 8; // GUI px — reads as 16 device px at scale 2
        boolean light = true;
        for (int y = 0; y < this.height; y += band) {
            g.fill(0, y, this.width, Math.min(this.height, y + band),
                    light ? 0xFFCCCFD6 : 0xFF292B33);
            light = !light;
        }
        // Thin static vertical accents so horizontal smear is also visible.
        for (int x = 0; x < this.width; x += 64) {
            g.fill(x, 0, Math.min(this.width, x + 2), this.height, 0xFF8C4A50);
        }
    }

}
