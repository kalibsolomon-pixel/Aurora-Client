package com.aurora.client.ui.render.blur;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeDefinition;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeMode;
import com.aurora.client.theme.ThemeRoundness;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

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

    // Theme state captured on open so [C]/[N]/[O][P] mutations can be restored.
    private ThemeRoundness originalRoundness;
    private ThemeMode originalMode;
    private double originalOpacity;

    private long lastFpsLogMs;

    // ---- Synthetic backdrop target (test harness) ----
    // Batched GuiGraphics fills only reach the framebuffer when the GUI
    // batch flushes — AFTER renderPanel's immediate capture — so the
    // SOLID/STRIPES controls are rendered into this dedicated FBO each
    // frame and handed to the renderer as a CaptureSource. The batched
    // fills are still drawn for the eye; this FBO is what the blur samples.
    private int backdropFbo;
    private int backdropTex;
    private int backdropW = -1, backdropH = -1;
    private int backdropProgram = -1;
    private int backdropVao = -1;
    private int uBackdropMode = -1;
    private int uBackdropSize = -1;
    private int uBackdropScale = -1;

    public BlurTestScreen() {
        super(Component.literal("Blur Panel Test"));
    }

    @Override
    protected void init() {
        AuroraConfig cfg = AuroraConfig.get();
        ThemeRoundness r = cfg.themeOrDefault().roundness;
        ThemeMode m = cfg.themeOrDefault().mode;
        originalRoundness = r != null ? r : ThemeRoundness.ROUND;
        originalMode = m != null ? m : ThemeMode.DARK;
        originalOpacity = ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity);
        com.aurora.client.AuroraClient.LOGGER.info(
                "[BlurTest] opened (lighting={} depressed={} backdrop={} radius={} corner={} theme={} opacity={})",
                lightingOn, depressedMode, mode, (int) blurRadius,
                originalRoundness.displayName(), originalMode,
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
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
                    try { cfg.themeOrDefault().roundness = ThemeRoundness.valueOf(v); themeChanged = true; } catch (IllegalArgumentException ignored) {}
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
                default -> { }
            }
        }
        if (themeChanged) ThemeManager.reload();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        pollStateFile();

        // 1. Static backdrop (negative/positive controls). The batched fills
        //    are what the EYE sees around the panel; the same pattern is
        //    rendered into the synthetic FBO below, which is what the blur
        //    actually samples (see the backdrop-target note above).
        //    WORLD uses neither — the blur samples the live framebuffer.
        BlurPanelRenderer.CaptureSource captureSource = null;
        if (mode != Backdrop.WORLD) {
            drawBackdrop(g);
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
        g.drawString(this.font, status, tx, ty, body, false);
        g.drawString(this.font, "Backdrop: " + mode.label
                        + " | Corner: " + ThemeManager.current().roundness().displayName()
                        + " | Theme: " + ThemeManager.current().mode()
                        + " | Op: " + Math.round(ThemeManager.current().backgroundOpacity() * 100) + "%",
                tx, ty + 12, dim, false);
        g.drawString(this.font, "[B]ackdrop  [L]ighting  [M] raised/depressed  [C]orner  [N] theme  [+/-] radius  [O/P] opacity",
                tx, ty + 26, dim, false);
        g.drawString(this.font, "Light: fixed top, ~14 deg right. Opacity = WINDOW_FILL alpha (single application point).",
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

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent kev) {
        int keyCode = kev.key();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_B) {
            mode = switch (mode) {
                case SOLID -> Backdrop.STRIPES;
                case STRIPES -> Backdrop.WORLD;
                case WORLD -> Backdrop.SOLID;
            };
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] B: backdrop={}", mode);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD) {
            blurRadius = Math.min(64f, blurRadius + 4f);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT) {
            blurRadius = Math.max(0f, blurRadius - 4f);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_L) {
            lightingOn = !lightingOn;
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] L: lighting={}", lightingOn);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_M) {
            depressedMode = !depressedMode;
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] M: depressed={}", depressedMode);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
            // Cycle corner style IN MEMORY (restored on close, never saved).
            ThemeRoundness[] vals = ThemeRoundness.values();
            ThemeRoundness cur = AuroraConfig.get().themeOrDefault().roundness;
            ThemeRoundness next = vals[((cur != null ? cur : ThemeRoundness.ROUND).ordinal() + 1) % vals.length];
            AuroraConfig.get().themeOrDefault().roundness = next;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] C: corner={}", next.displayName());
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_N) {
            // Flip Dark/Light IN MEMORY (restored on close, never saved).
            ThemeMode cur = AuroraConfig.get().themeOrDefault().mode;
            AuroraConfig.get().themeOrDefault().mode = (cur == ThemeMode.LIGHT) ? ThemeMode.DARK : ThemeMode.LIGHT;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] N: theme={}",
                    AuroraConfig.get().themeOrDefault().mode);
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_O
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_P) {
            // Live opacity knob: adjusts the theme's backgroundOpacity IN
            // MEMORY (restored on close). This is the same single value
            // ThemeResolver stamps into WINDOW_FILL's alpha — the ONE
            // documented opacity application point — so no second
            // application point is created by the knob.
            double step = (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_P) ? 0.05 : -0.05;
            AuroraConfig cfg = AuroraConfig.get();
            double next = Math.max(0.05, Math.min(1.0,
                    ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity) + step));
            cfg.themeOrDefault().backgroundOpacity = next;
            ThemeManager.reload();
            com.aurora.client.AuroraClient.LOGGER.info("[BlurTest] {}: opacity={}",
                    (char) keyCode, String.format(java.util.Locale.ROOT, "%.2f", next));
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_S) {
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
                            mc.getMainRenderTarget(), 1,
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

    @Override
    public void removed() {
        // Restore the theme values this screen may have cycled in memory —
        // [C]/[N] deliberately never persist, so the user's saved theme is
        // exactly what it was before the harness opened.
        AuroraConfig cfg = AuroraConfig.get();
        boolean changed = false;
        ThemeRoundness r = cfg.themeOrDefault().roundness;
        if (r != originalRoundness) { cfg.themeOrDefault().roundness = originalRoundness; changed = true; }
        ThemeMode m = cfg.themeOrDefault().mode;
        if (m != originalMode) { cfg.themeOrDefault().mode = originalMode; changed = true; }
        double o = ThemeDefinition.normalizedOpacity(cfg.themeOrDefault().backgroundOpacity);
        if (o != originalOpacity) { cfg.themeOrDefault().backgroundOpacity = originalOpacity; changed = true; }
        if (changed) ThemeManager.reload();
        // Release the synthetic backdrop target (the panel renderer owns
        // its own objects).
        if (backdropFbo != 0) { GL30.glDeleteFramebuffers(backdropFbo); backdropFbo = 0; }
        if (backdropTex != 0) { GL11.glDeleteTextures(backdropTex); backdropTex = 0; }
        if (backdropProgram > 0) { GL20.glDeleteProgram(backdropProgram); backdropProgram = -1; }
        if (backdropVao > 0) { GL30.glDeleteVertexArrays(backdropVao); backdropVao = -1; }
        backdropW = -1;
        backdropH = -1;
        super.removed();
    }

    /**
     * Renders this frame's SOLID/STRIPES pattern into the dedicated
     * synthetic backdrop FBO and returns it as a {@code CaptureSource}.
     * Test-harness machinery only — mirrors the colors of the batched
     * {@link #drawBackdrop} fills so the eye and the blur sample agree.
     */
    private BlurPanelRenderer.CaptureSource renderSyntheticBackdrop() {
        try {
            // HISTORY: this pass originally restored only program/FBO/VAO and
            // leaked glDisable(GL_BLEND) into the rest of the frame. The GUI
            // batch flushes at frame end under whatever state remains, so the
            // LAST queued fill simply replaced pixels — the panel tint painted
            // fully opaque no matter how low Background Opacity was set
            // (looked like "the opacity setting is broken"; really a state
            // leak). SavedGlState now brackets the pass exactly like the
            // renderer's own GL work.
            BlurPanelRenderer.SavedGlState gl = BlurPanelRenderer.SavedGlState.save();
            try {
                ensureSyntheticBackdrop();
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, backdropFbo);
                GL11.glViewport(0, 0, backdropW, backdropH);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL33.glBindSampler(0, 0);
                GL20.glUseProgram(backdropProgram);
                GL20.glUniform1i(uBackdropMode, mode == Backdrop.SOLID ? 0 : 1);
                GL20.glUniform2f(uBackdropSize, backdropW, backdropH);
                GL20.glUniform1f(uBackdropScale,
                        (float) Minecraft.getInstance().getWindow().getGuiScale());
                GL30.glBindVertexArray(backdropVao);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            } finally {
                gl.restore();
            }
            return new BlurPanelRenderer.CaptureSource(backdropFbo, backdropW, backdropH);
        } catch (Throwable t) {
            com.aurora.client.AuroraClient.LOGGER.error("[BlurTest] synthetic backdrop failed", t);
            return null;
        }
    }

    private void ensureSyntheticBackdrop() {
        // Allocated at the MAIN TARGET's device resolution (not GUI size):
        // the capture's source-rect mapping then reduces 1:1 with zero
        // Math.round quantization, and the stripe edges generated below
        // land on exact device pixels exactly like the batched fills the
        // eye compares against. (HISTORY: a GUI-resolution target forced
        // the capture rect through device->GUI rounding — up to half a GUI
        // pixel of extra shift — on top of the half-band phase offset fixed
        // in the STRIPES shader below.)
        Minecraft mc = Minecraft.getInstance();
        int w = Math.max(4, mc.getMainRenderTarget().width);
        int h = Math.max(4, mc.getMainRenderTarget().height);
        if (w == backdropW && h == backdropH && backdropFbo != 0) return;
        if (backdropFbo != 0) GL30.glDeleteFramebuffers(backdropFbo);
        if (backdropTex != 0) GL11.glDeleteTextures(backdropTex);
        backdropTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdropTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        backdropFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, backdropFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, backdropTex, 0);
        backdropW = w;
        backdropH = h;
        if (backdropProgram < 0) {
            String vs = """
                    #version 150
                    out vec2 vUv;
                    void main() {
                        vec2 pos = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                        vUv = pos;
                        gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);
                    }
                    """;
            String fs = """
                    #version 150
                    in vec2 vUv;
                    out vec4 fragColor;
                    uniform int uMode;
                    uniform vec2 uSize;   // device px (main target size)
                    uniform float uScale; // GUI scale factor
                    void main() {
                        if (uMode == 0) {
                            // SOLID — same flat gray as the batched fill (0xFF85878C).
                            fragColor = vec4(0.522, 0.529, 0.549, 1.0);
                        } else {
                            // STRIPES — a PHASE-EXACT, device-resolution duplicate
                            // of the batched fills in drawBackdrop: 8-GUI-px
                            // horizontal bands starting LIGHT at the top row,
                            // 2-GUI-px accents at every 64 GUI px. This FBO is what
                            // the panel SAMPLES; the batched fills are what the EYE
                            // compares against — any phase/position difference
                            // between the two reads as the backdrop being
                            // compressed/shifted behind the panel (most visible at
                            // low blur radii, where the pattern stays legible).
                            // HISTORY: the original bands ran on
                            // fract(vUv.y * h / 8) — a pattern offset HALF A BAND
                            // from drawBackdrop's (vUv.y = 0 is the FBO's BOTTOM
                            // row, and the viewport-edge phase lands mid-band), and
                            // the accents were 1 px wide one column early, so
                            // inside/outside alignment could never read correct at
                            // any radius.
                            // Pixel-center math: top-down row center
                            // rc = uSize.y * (1 - vUv.y); light band iff
                            // floor(rc / (8*uScale)) is even, i.e.
                            // fract(rc / (16*uScale)) < 0.5. Column center
                            // cc = uSize.x * vUv.x; accent iff
                            // fract(cc / (64*uScale)) < 2/64.
                            float rc = uSize.y * (1.0 - vUv.y);
                            float band = 1.0 - step(0.5, fract(rc / (16.0 * uScale)));
                            vec3 col = mix(vec3(0.161, 0.173, 0.200),
                                           vec3(0.800, 0.812, 0.839), band);
                            float cc = uSize.x * vUv.x;
                            float accent = 1.0 - step(2.0 / 64.0, fract(cc / (64.0 * uScale)));
                            fragColor = vec4(mix(col, vec3(0.549, 0.290, 0.314), accent), 1.0);
                        }
                    }
                    """;
            int v = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(v, vs);
            GL20.glCompileShader(v);
            int f = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(f, fs);
            GL20.glCompileShader(f);
            int p = GL20.glCreateProgram();
            GL20.glAttachShader(p, v);
            GL20.glAttachShader(p, f);
            GL20.glLinkProgram(p);
            if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("synthetic backdrop link failed: "
                        + GL20.glGetProgramInfoLog(p, 4096));
            }
            GL20.glDeleteShader(v);
            GL20.glDeleteShader(f);
            backdropProgram = p;
            uBackdropMode = GL20.glGetUniformLocation(p, "uMode");
            uBackdropSize = GL20.glGetUniformLocation(p, "uSize");
            uBackdropScale = GL20.glGetUniformLocation(p, "uScale");
            backdropVao = GL30.glGenVertexArrays();
        }
    }

    /**
     * Static backdrop fills — the simplest possible thing that still makes
     * blur visually obvious. SOLID is the negative control (a correct blur
     * leaves it unchanged; lighting deltas are pure lighting); STRIPES is
     * the positive control: 8-GUI-px alternating bands plus thin vertical
     * accents, guaranteed non-animating, so at a large radius the bands
     * must visibly smear into flat gray.
     *
     * <p>These fills are also the <b>phase reference</b> for the synthetic
     * FBO's STRIPES mode: the shader in {@link #ensureSyntheticBackdrop()}
     * must stay pixel-exact with what is drawn here (same band phase, same
     * accent columns), or the pattern the panel samples will never line up
     * with the pattern the eye sees outside it.
     */
    private void drawBackdrop(GuiGraphics g) {
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
