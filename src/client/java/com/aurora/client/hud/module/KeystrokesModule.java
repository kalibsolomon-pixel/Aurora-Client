package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ClickTrackerFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.CachedValue;
import com.aurora.client.util.RoundedRect;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Keystrokes overlay — a single cohesive panel of rounded key cells that
 * light up (animated accent blend + subtle scale-up) as inputs are pressed.
 *
 * <p>Layout, top to bottom (every row optional):
 * <pre>
 *        [W]
 *   [A] [S] [D]          movement (follows vanilla movement binds)
 *   [L]  [M]  [R]        mouse buttons, arranged like a mouse
 *      L: 4  R: 2        rolling CPS (1-second window)
 *   [  space  ]          jump
 *   [SNEAK] [SPRINT]     sneak / sprint binds
 *   [Q] [E] [F] ...      user-configured extra keys, rows of three
 * </pre>
 *
 * <p>Input detection reuses the parts of this mod that already work:
 * movement/mouse/space/sneak via polled vanilla {@code KeyMapping.isDown()}
 * (rebind-aware, doesn't consume presses), CPS via {@link ClickTrackerFeature}'s
 * event-based trackers, extra keys via raw GLFW state through {@link AuroraKey}.
 *
 * <p>Rendering budget: no per-frame allocations. Labels are cached (refreshed
 * every 2 s to pick up rebinding), the CPS string is rebuilt at most 10x/s via
 * {@link CachedValue}, and the press animation is one {@code float} per slot
 * updated by a frame-rate-independent lerp.
 */
public class KeystrokesModule extends HudModule {
    public static final String ID = "keystrokes";

    // ---- Intrinsic geometry (scale = 1) ----
    /** Square key cell edge. */
    private static final int CELL = 22;
    /** Gap between keys and between rows. */
    private static final int GAP = 2;
    /** Cluster width: three key columns. */
    private static final int WIDTH = CELL * 3 + GAP * 2;
    /**
     * Keycap corner radius — REPRESENTATIONAL HUD geometry, Square-mode-exempt
     * by ruling (C-7): the cells draw physical keyboard keycaps (a drawn
     * keycap reads as a key BECAUSE of its slightly rounded corners), in the
     * HUD floating-content exception domain (DESIGN_LANGUAGE §16). Normal
     * rectangular chrome must not copy this pattern — tokenize instead.
     */
    private static final int KEY_RADIUS = 3;
    private static final int SPACE_H = 10;
    private static final int CPS_H = 11;
    private static final int EXTRA_COLS = 3;
    /** Rendered extra-key slot cap. */
    public static final int MAX_EXTRA_KEYS = 12;

    // ---- Slot indices into press[] / labels[] ----
    private static final int SLOT_W = 0, SLOT_A = 1, SLOT_S = 2, SLOT_D = 3;
    private static final int SLOT_LMB = 4, SLOT_MMB = 5, SLOT_RMB = 6;
    private static final int SLOT_SPACE = 7, SLOT_SNEAK = 8, SLOT_SPRINT = 9;
    private static final int SLOT_EXTRA0 = 10;
    private static final int SLOTS = SLOT_EXTRA0 + MAX_EXTRA_KEYS;

    // ---- Palette ----
    /** Idle key fill: ~18% white glass over the backing panel. */
    private static final int KEY_IDLE_BG = 0x2EFFFFFF;

    // ---- Press animation speeds (lerp factors, per second) ----
    private static final float PRESS_SPEED = 26f;
    private static final float RELEASE_SPEED = 12f;

    private final float[] press = new float[SLOTS];
    private int visibleMask;
    private long lastFrameNanos;

    // ---- Label cache (bind names change only when keys are rebound) ----
    private static final long LABEL_REFRESH_MS = 2000L;
    private final String[] labels = new String[SLOTS];
    private final int[] labelWidths = new int[SLOTS];
    private long labelStamp;

    /** CPS text — integer values, so a 100 ms rebuild cap is imperceptible. */
    private final CachedValue<String> cpsText = new CachedValue<>(100L, this::buildCpsText);

    public KeystrokesModule() {
        super(ID);
        this.anchor = HudAnchor.BOTTOM_LEFT;
        this.offsetX = 4;
        this.offsetY = 80;
    }

    @Override
    public void resetLayoutToDefaults() {
        this.anchor = HudAnchor.BOTTOM_LEFT;
        this.offsetX = 4;
        this.offsetY = 80;
        this.scale = 1.0f;
        this.enabled = true;
        this.locked = false;
    }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.keystrokesEnabled) return false;
        return cfg.keystrokesShowMovement || cfg.keystrokesShowMouse || cfg.keystrokesShowCps
                || cfg.keystrokesShowSpacebar || cfg.keystrokesShowExtra || extraCount(cfg) > 0;
    }

    @Override
    protected AuroraConfig.HudBackground backgroundMode() {
        return AuroraConfig.get().keystrokesBgMode;
    }

    @Override
    protected int backgroundColor() {
        return AuroraConfig.get().keystrokesBgColor;
    }

    /**
     * Backing-panel opacity only. Key fills and labels are unaffected so the
     * overlay stays legible even with the panel set to 0%.
     */
    @Override
    protected float backgroundAlpha() {
        double o = AuroraConfig.get().keystrokesBgOpacity;
        return (float) Math.max(0.0, Math.min(1.0, o));
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        AuroraConfig cfg = AuroraConfig.get();
        int h = 0;
        int rows = 0;
        if (cfg.keystrokesShowMovement) { h += CELL * 2; rows += 2; }
        if (cfg.keystrokesShowMouse)    { h += CELL;     rows += 1; }
        if (cfg.keystrokesShowCps)      { h += CPS_H;    rows += 1; }
        if (cfg.keystrokesShowSpacebar) { h += SPACE_H;  rows += 1; }
        if (cfg.keystrokesShowExtra)    { h += CELL;     rows += 1; }
        int ex = extraCount(cfg);
        if (ex > 0) {
            int r = (ex + EXTRA_COLS - 1) / EXTRA_COLS;
            h += r * CELL;
            rows += r;
        }
        if (rows == 0) return CELL; // keep the AABB non-degenerate
        return h + (rows - 1) * GAP;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        Font font = client.font;
        Options opts = client.options;
        if (font == null || opts == null) return;

        boolean showMid = cfg.keystrokesShowMouse && cfg.keystrokesShowMiddleMouse;
        List<Integer> extra = cfg.keystrokesExtraKeys;
        int extraCount = extra == null ? 0 : Math.min(extra.size(), MAX_EXTRA_KEYS);

        ensureLabels(font, opts, cfg, extraCount);

        // Frame delta (clamped) for a frame-rate-independent press lerp.
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0f
                : clampf((now - lastFrameNanos) / 1_000_000_000f, 0f, 0.25f);
        lastFrameNanos = now;

        // Slots that just became visible (a row toggled on) snap to their
        // live input state instead of easing in from a stale value.
        int mask = visibilityMask(cfg, extraCount);
        int newly = mask & ~visibleMask;
        visibleMask = mask;
        if (newly != 0) {
            for (int i = 0; i < SLOTS; i++) {
                if ((newly & (1 << i)) != 0) {
                    press[i] = slotDown(opts, extra, extraCount, i) ? 1f : 0f;
                }
            }
        }

        int accent = accentColor(cfg);
        int labelColor = labelColor(cfg);
        int cy = y;

        // ---- Movement (W centered over A S D) ----
        if (cfg.keystrokesShowMovement) {
            advance(SLOT_W, down(opts.keyUp), dt);
            drawKey(ctx, font, x + CELL + GAP, cy, CELL, CELL, SLOT_W, accent, labelColor);
            cy += CELL + GAP;

            advance(SLOT_A, down(opts.keyLeft), dt);
            drawKey(ctx, font, x, cy, CELL, CELL, SLOT_A, accent, labelColor);
            advance(SLOT_S, down(opts.keyDown), dt);
            drawKey(ctx, font, x + CELL + GAP, cy, CELL, CELL, SLOT_S, accent, labelColor);
            advance(SLOT_D, down(opts.keyRight), dt);
            drawKey(ctx, font, x + (CELL + GAP) * 2, cy, CELL, CELL, SLOT_D, accent, labelColor);
            cy += CELL + GAP;
        }

        // ---- Mouse buttons (L / M / R, arranged like a mouse) ----
        if (cfg.keystrokesShowMouse) {
            advance(SLOT_LMB, down(opts.keyAttack), dt);
            advance(SLOT_RMB, down(opts.keyUse), dt);
            if (showMid) {
                advance(SLOT_MMB, down(opts.keyPickItem), dt);
                int mW = 12; // 27 + 2 + 12 + 2 + 27 = WIDTH
                int side = WIDTH / 2 - mW / 2 - GAP;
                drawKey(ctx, font, x, cy, side, CELL, SLOT_LMB, accent, labelColor);
                drawKey(ctx, font, x + WIDTH / 2 - mW / 2, cy, mW, CELL, SLOT_MMB, accent, labelColor);
                drawKey(ctx, font, x + WIDTH / 2 + mW / 2 + GAP, cy, side, CELL, SLOT_RMB, accent, labelColor);
            } else {
                int side = (WIDTH - GAP) / 2;
                drawKey(ctx, font, x, cy, side, CELL, SLOT_LMB, accent, labelColor);
                drawKey(ctx, font, x + side + GAP, cy, side, CELL, SLOT_RMB, accent, labelColor);
            }
            cy += CELL + GAP;
        }

        // ---- CPS readout (event-based rolling 1-second counters) ----
        if (cfg.keystrokesShowCps) {
            String text = cpsText.get();
            int tw = font.width(text);
            ctx.drawString(font, text, x + (WIDTH - tw) / 2,
                    cy + (CPS_H - font.lineHeight) / 2, labelColor, false);
            cy += CPS_H + GAP;
        }

        // ---- Spacebar ----
        if (cfg.keystrokesShowSpacebar) {
            advance(SLOT_SPACE, down(opts.keyJump), dt);
            drawKey(ctx, font, x, cy, WIDTH, SPACE_H, SLOT_SPACE, accent, labelColor);
            cy += SPACE_H + GAP;
        }

        // ---- Sneak / Sprint ----
        if (cfg.keystrokesShowExtra) {
            advance(SLOT_SNEAK, down(opts.keyShift), dt);
            advance(SLOT_SPRINT, down(opts.keySprint), dt);
            int side = (WIDTH - GAP) / 2;
            drawKey(ctx, font, x, cy, side, CELL, SLOT_SNEAK, accent, labelColor);
            drawKey(ctx, font, x + side + GAP, cy, side, CELL, SLOT_SPRINT, accent, labelColor);
            cy += CELL + GAP;
        }

        // ---- Extra keys (auto-arranged rows of three) ----
        int exRows = (extraCount + EXTRA_COLS - 1) / EXTRA_COLS;
        for (int r = 0; r < exRows; r++) {
            for (int c = 0; c < EXTRA_COLS; c++) {
                int i = r * EXTRA_COLS + c;
                if (i >= extraCount) break;
                Integer boxed = extra.get(i);
                advance(SLOT_EXTRA0 + i, boxed != null && AuroraKey.isDown(boxed), dt);
                drawKey(ctx, font, x + c * (CELL + GAP), cy, CELL, CELL,
                        SLOT_EXTRA0 + i, accent, labelColor);
            }
            cy += CELL + GAP;
        }
    }

    /**
     * Draws one rounded key cell. The press value drives a background blend
     * from idle glass to the accent color plus a subtle 1px scale-up once
     * mostly pressed; the label is always centered.
     */
    private void drawKey(GuiGraphics ctx, Font font, int x, int y, int w, int h,
                         int slot, int accent, int labelColor) {
        float t = press[slot];
        int grow = t >= 0.5f ? 1 : 0;
        RoundedRect.fill(ctx, x - grow, y - grow, x + w + grow, y + h + grow,
                KEY_RADIUS, blend(KEY_IDLE_BG, accent, t));

        String label = labels[slot];
        if (label == null || label.isEmpty()) return;
        int lw = labelWidths[slot];
        if (lw <= 0) return;
        if (lw <= w - 4) {
            ctx.drawString(font, label, x + (w - lw) / 2,
                    y + (h - font.lineHeight) / 2, labelColor, false);
        } else {
            // Long bind names shrink to fit instead of overflowing the cell.
            float s = (w - 4f) / lw;
            int tx = x + Math.max(2, Math.round((w - lw * s) / 2f));
            int ty = y + (h - font.lineHeight) / 2;
            ctx.pose().pushMatrix();
            ctx.pose().translate(tx, ty);
            ctx.pose().scale(s, s);
            ctx.drawString(font, label, 0, 0, labelColor, false);
            ctx.pose().popMatrix();
        }
    }

    /** Frame-rate-independent lerp toward the pressed/released target. */
    private void advance(int slot, boolean isDown, float dt) {
        float target = isDown ? 1f : 0f;
        float cur = press[slot];
        if (cur == target) return;
        float speed = target > cur ? PRESS_SPEED : RELEASE_SPEED;
        float next = cur + (target - cur) * Math.min(1f, dt * speed);
        press[slot] = Math.abs(target - next) < 0.004f ? target : next;
    }

    private static boolean down(KeyMapping km) {
        return km != null && km.isDown();
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Per-channel ARGB lerp. */
    private static int blend(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int a1 = (from >>> 24) & 0xFF, r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF, b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF, r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF, b2 = to & 0xFF;
        int a = a1 + Math.round((a2 - a1) * t);
        int r = r1 + Math.round((r2 - r1) * t);
        int g = g1 + Math.round((g2 - g1) * t);
        int b = b1 + Math.round((b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int visibilityMask(AuroraConfig cfg, int extraCount) {
        int mask = 0;
        if (cfg.keystrokesShowMovement) mask |= 0x0F; // W A S D
        if (cfg.keystrokesShowMouse) {
            mask |= (1 << SLOT_LMB) | (1 << SLOT_RMB);
            if (cfg.keystrokesShowMiddleMouse) mask |= 1 << SLOT_MMB;
        }
        if (cfg.keystrokesShowSpacebar) mask |= 1 << SLOT_SPACE;
        if (cfg.keystrokesShowExtra) mask |= (1 << SLOT_SNEAK) | (1 << SLOT_SPRINT);
        for (int i = 0; i < extraCount; i++) mask |= 1 << (SLOT_EXTRA0 + i);
        return mask;
    }

    /** Live input state for one slot — used to seed newly-visible slots. */
    private static boolean slotDown(Options opts, List<Integer> extra, int extraCount, int slot) {
        return switch (slot) {
            case SLOT_W -> down(opts.keyUp);
            case SLOT_A -> down(opts.keyLeft);
            case SLOT_S -> down(opts.keyDown);
            case SLOT_D -> down(opts.keyRight);
            case SLOT_LMB -> down(opts.keyAttack);
            case SLOT_MMB -> down(opts.keyPickItem);
            case SLOT_RMB -> down(opts.keyUse);
            case SLOT_SPACE -> down(opts.keyJump);
            case SLOT_SNEAK -> down(opts.keyShift);
            case SLOT_SPRINT -> down(opts.keySprint);
            default -> {
                int i = slot - SLOT_EXTRA0;
                if (i < 0 || i >= extraCount || extra == null) yield false;
                Integer boxed = extra.get(i);
                yield boxed != null && AuroraKey.isDown(boxed);
            }
        };
    }

    /**
     * The pressed-key accent. An explicit {@code keystrokesAccentColor}
     * (non-zero) wins; the default {@code 0} follows the theme accent, so
     * the highlight matches whatever accent the user picked (R6 Part 2 —
     * this used to be a hardcoded azure no theme setting could reach).
     */
    private static int accentColor(AuroraConfig cfg) {
        int c = cfg.keystrokesAccentColor;
        return c == 0 ? com.aurora.client.theme.ThemeManager.color(
                com.aurora.client.theme.ThemeToken.ACCENT) : c;
    }

    /**
     * Key labels follow the mod-wide HUD text color — the same sentinel
     * contract as every other hudColor reader ({@link
     * com.aurora.client.theme.HudText}): 0 follows the theme accent, an
     * explicit color wins. Before the sentinel this guard fell back to white,
     * which was dead code in practice (hudColor's old default was white).
     */
    private static int labelColor(AuroraConfig cfg) {
        return com.aurora.client.theme.HudText.color(cfg.hudColor);
    }

    private static int extraCount(AuroraConfig cfg) {
        List<Integer> list = cfg.keystrokesExtraKeys;
        return list == null ? 0 : Math.min(list.size(), MAX_EXTRA_KEYS);
    }

    // ---- Label cache -------------------------------------------------

    private void ensureLabels(Font font, Options opts, AuroraConfig cfg, int extraCount) {
        long now = System.currentTimeMillis();
        if (labelStamp != 0L && now - labelStamp < LABEL_REFRESH_MS) return;
        labelStamp = now;

        putLabel(font, SLOT_W, bindLabel(opts.keyUp, "W"));
        putLabel(font, SLOT_A, bindLabel(opts.keyLeft, "A"));
        putLabel(font, SLOT_S, bindLabel(opts.keyDown, "S"));
        putLabel(font, SLOT_D, bindLabel(opts.keyRight, "D"));
        putLabel(font, SLOT_LMB, "L");
        putLabel(font, SLOT_MMB, "M");
        putLabel(font, SLOT_RMB, "R");
        putLabel(font, SLOT_SPACE, "");
        putLabel(font, SLOT_SNEAK, bindLabel(opts.keyShift, "SHIFT"));
        putLabel(font, SLOT_SPRINT, bindLabel(opts.keySprint, "CTRL"));

        List<Integer> extra = cfg.keystrokesExtraKeys;
        for (int i = 0; i < MAX_EXTRA_KEYS; i++) {
            String s = "";
            if (i < extraCount && extra != null) {
                Integer boxed = extra.get(i);
                int code = boxed == null ? AuroraKey.UNBOUND : boxed;
                if (code != AuroraKey.UNBOUND) s = rawKeyName(code);
            }
            putLabel(font, SLOT_EXTRA0 + i, s);
        }
    }

    private void putLabel(Font font, int slot, String text) {
        labels[slot] = text;
        labelWidths[slot] = text.isEmpty() ? 0 : font.width(text);
    }

    /** Display name of a vanilla bind, trimmed to keep key cells compact. */
    private static String bindLabel(KeyMapping km, String fallback) {
        try {
            if (km != null) {
                InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(km);
                if (key != null && !key.equals(InputConstants.UNKNOWN)) {
                    String s = key.getDisplayName().getString();
                    if (s != null) {
                        s = s.trim();
                        if (!s.isEmpty()) return s.length() > 8 ? s.substring(0, 8) : s;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to the static fallback label.
        }
        return fallback;
    }

    private static String rawKeyName(int glfwKey) {
        try {
            String s = InputConstants.Type.KEYSYM.getOrCreate(glfwKey).getDisplayName().getString();
            if (s != null && !s.isEmpty()) return s.length() > 8 ? s.substring(0, 8) : s;
        } catch (Exception ignored) {
        }
        return "?" + glfwKey;
    }

    private String buildCpsText() {
        return "L: " + ClickTrackerFeature.LEFT.get() + "   R: " + ClickTrackerFeature.RIGHT.get();
    }
}