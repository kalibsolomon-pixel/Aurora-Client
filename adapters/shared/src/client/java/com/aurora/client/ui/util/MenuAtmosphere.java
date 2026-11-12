package com.aurora.client.ui.util;

import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.util.AuroraAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * The title screen's background (owner request 2026-11-12: "a dark simple
 * gradient with random light atmospheric animations"): a vertical gradient
 * from the theme's background, a faint accent glow at the top, and soft
 * light motes that drift upward and fade in and out at random, with two very
 * faint haze bands crossing slowly.
 *
 * <p>Cost: the gradient is one cached raster (re-made only on a resize or a
 * theme change) blitted once per frame; each mote and band is one blit of a
 * single cached glow texture. Mote state lives in arrays allocated once, so a
 * frame allocates nothing. Motion follows frame time, and freezes into a
 * still scatter when Minecraft's Screen Effects accessibility option is 0.
 */
public final class MenuAtmosphere {
    static final int MOTES = 18, HAZES = 2;
    private static final int GLOW_SIZE = 128;
    private static final float MAX_MOTE_ALPHA = 0.22f, MAX_HAZE_ALPHA = 0.05f;

    private final UiLayerCache gradient = new UiLayerCache();
    private final UiLayerCache glow = new UiLayerCache();
    private final Random random = new Random();
    // One slot per mote, then the haze bands: position, drift, size, peak alpha, age and lifetime (seconds).
    private final float[] x = new float[MOTES + HAZES], y = new float[MOTES + HAZES];
    private final float[] vx = new float[MOTES + HAZES], vy = new float[MOTES + HAZES];
    private final float[] w = new float[MOTES + HAZES], h = new float[MOTES + HAZES];
    private final float[] peak = new float[MOTES + HAZES], sway = new float[MOTES + HAZES];
    private final float[] age = new float[MOTES + HAZES], life = new float[MOTES + HAZES];
    private int width = -1, height = -1;
    private long lastNs;

    /** Paints the whole background for a {@code width × height} screen. */
    public void draw(GuiGraphicsExtractor g, int width, int height) {
        if (width != this.width || height != this.height) seed(width, height);
        drawGradient(g, width, height);
        if (!glow.isCurrent(1)) rasterGlow();
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0 : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        if (reducedMotion()) dt = 0;
        int accent = ThemeManager.color(ThemeToken.ACCENT);
        int moteRgb = AuroraAnim.lerpArgb(accent, 0xFFFFFFFF, 0.4f) & 0xFFFFFF;
        int hazeRgb = AuroraAnim.lerpArgb(accent, 0xFFFFFFFF, 0.25f) & 0xFFFFFF;
        for (int i = 0; i < MOTES + HAZES; i++) {
            boolean haze = i >= MOTES;
            age[i] += dt;
            if (age[i] >= life[i]) spawn(i, false);
            float drift = (float) Math.sin((age[i] / life[i]) * Math.PI * 2 + sway[i] * 7) * sway[i];
            float px = x[i] + vx[i] * age[i] + drift, py = y[i] + vy[i] * age[i];
            if (haze) px = wrap(px, w[i], width);
            // Fade in and out over the lifetime: zero at birth and death, the peak in the middle.
            float fade = (float) Math.sin(Math.PI * Math.min(1f, age[i] / life[i]));
            int alpha = Math.round(255 * peak[i] * fade * fade);
            if (alpha <= 0) continue;
            g.pose().pushMatrix();
            g.pose().translate(px - w[i] / 2f, py - h[i] / 2f);
            glow.blitAt(g, 0, 0, Math.round(w[i]), Math.round(h[i]), (alpha << 24) | (haze ? hazeRgb : moteRgb));
            g.pose().popMatrix();
        }
    }

    /** Releases both textures (screen close). */
    public void dispose() {
        gradient.dispose();
        glow.dispose();
        width = height = -1;
        lastNs = 0;
    }

    private static boolean reducedMotion() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.options != null && mc.options.screenEffectScale().get() <= 0.0;
    }

    private static float wrap(float px, float bandWidth, int screenWidth) {
        float span = screenWidth + bandWidth;
        float shifted = (px + bandWidth / 2f) % span;
        if (shifted < 0) shifted += span;
        return shifted - bandWidth / 2f;
    }

    private void seed(int width, int height) {
        this.width = width;
        this.height = height;
        for (int i = 0; i < MOTES + HAZES; i++) spawn(i, true);
    }

    /** A fresh mote or band at a random place; {@code initial} also starts it at a random age so they never pulse together. */
    private void spawn(int i, boolean initial) {
        boolean haze = i >= MOTES;
        if (haze) {
            w[i] = width * (0.9f + random.nextFloat() * 0.6f);
            h[i] = 50 + random.nextFloat() * 50;
            x[i] = random.nextFloat() * width;
            y[i] = height * (0.25f + random.nextFloat() * 0.55f);
            vx[i] = (random.nextBoolean() ? 1 : -1) * (3 + random.nextFloat() * 4);
            vy[i] = 0;
            sway[i] = 0;
            peak[i] = MAX_HAZE_ALPHA * (0.6f + random.nextFloat() * 0.4f);
            life[i] = 20 + random.nextFloat() * 14;
        } else {
            float size = 14 + random.nextFloat() * 52;
            w[i] = h[i] = size;
            x[i] = random.nextFloat() * width;
            y[i] = height * (0.15f + random.nextFloat() * 0.9f);
            vx[i] = (random.nextFloat() - 0.5f) * 3;
            vy[i] = -(3 + random.nextFloat() * 7);
            sway[i] = 2 + random.nextFloat() * 6;
            // Small motes may be brighter; large ones stay a whisper.
            peak[i] = MAX_MOTE_ALPHA * (0.35f + random.nextFloat() * 0.65f) * (size < 30 ? 1f : 0.6f);
            life[i] = 6 + random.nextFloat() * 6;
        }
        age[i] = initial ? random.nextFloat() * life[i] : 0;
    }

    private void drawGradient(GuiGraphicsExtractor g, int width, int height) {
        int scale = Minecraft.getInstance().getWindow() == null ? 1 : Math.max(1, (int) Minecraft.getInstance().getWindow().getGuiScale());
        int texel = Math.max(1, (scale + 1) / 2); // a smooth gradient needs no more than half the device resolution
        int base = 0xFF000000 | MaterialSurface.color(ThemeToken.BACKGROUND);
        int accent = ThemeManager.color(ThemeToken.ACCENT);
        long version = ThemeManager.generation() * 31 + ((long) width << 32 | (long) height << 8 | texel) * 17 + base * 7L + accent;
        if (!gradient.isCurrent(version)) {
            int tw = width * texel, th = height * texel;
            gradient.ensureSize(tw, th);
            int top = AuroraAnim.lerpArgb(base, 0xFFFFFFFF, 0.04f);
            int bottom = AuroraAnim.lerpArgb(base, 0xFF000000, 0.55f);
            float cx = tw / 2f, cy = -th * 0.25f, radius = Math.max(tw, th) * 0.85f;
            gradient.writeArgb((px, py) -> {
                float t = py / (float) Math.max(1, th - 1);
                int c = AuroraAnim.lerpArgb(top, bottom, t * t * (3 - 2 * t));
                float dx = (px - cx) / radius, dy = (py - cy) / radius;
                float glowT = Math.max(0f, 1f - (dx * dx + dy * dy));
                c = AuroraAnim.lerpArgb(c, accent | 0xFF000000, 0.10f * glowT * glowT);
                // ±1 level of ordered noise hides 8-bit banding on a dark ramp.
                int n = ((px * 7 + py * 13 + (px * py & 7)) & 3) - 1;
                return dither(c, n);
            });
            gradient.commit(version);
        }
        gradient.blitAt(g, 0, 0, width, height);
    }

    private static int dither(int c, int n) {
        int r = Math.max(0, Math.min(255, (c >> 16 & 0xFF) + n));
        int gr = Math.max(0, Math.min(255, (c >> 8 & 0xFF) + n));
        int b = Math.max(0, Math.min(255, (c & 0xFF) + n));
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    private void rasterGlow() {
        glow.ensureSize(GLOW_SIZE, GLOW_SIZE);
        float c = (GLOW_SIZE - 1) / 2f;
        glow.writeArgb((px, py) -> {
            float dx = (px - c) / c, dy = (py - c) / c;
            float d2 = dx * dx + dy * dy;
            if (d2 >= 1f) return 0;
            // A gaussian, eased to exactly zero at the rim so the square never shows.
            float a = (float) Math.exp(-d2 * 4.0) * (1f - d2);
            return Math.round(255 * a) << 24 | 0xFFFFFF;
        });
        glow.commit(1);
    }
}
