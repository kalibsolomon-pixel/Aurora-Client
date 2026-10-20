package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ClickTrackerFeature;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.util.AuroraKey;
import com.aurora.client.util.CachedValue;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * Keystrokes overlay — a single cohesive panel of rounded keycaps. Each cap
 * sits {@link #DEPTH} px proud of a darker base (the visible bottom edge) and
 * travels down onto it while pressed, picking up a soft accent tint and edge.
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
 * {@link CachedValue}, key shapes are cached device-resolution templates (one
 * blit each), and the press animation is one interruptible ease-out per slot.
 */
public class KeystrokesModule extends HudModule {
    public static final String ID = "keystrokes";

    // ---- Intrinsic geometry (scale = 1) ----
    /** Square key cell edge. */
    private static final int CELL = 22;
    /** Gap between keys and between rows. */
    private static final int GAP = 3;
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
    private static final int SPACE_H = 16;
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
    private static int idleBackground() {
        return com.aurora.client.theme.ThemeManager.withAlpha(com.aurora.client.theme.ThemeManager.color(
                com.aurora.client.theme.ThemeToken.SURFACE),0xEE);
    }

    /** Keycap travel: height of the visible bottom edge, consumed while pressed. */
    static final int DEPTH = 2;

    // ---- Press motion (premium-design press/recovery vocabulary) ----
    /** Press lands fast so the cap tracks the finger; release settles a little slower. */
    static final long PRESS_NS = 70_000_000L, RELEASE_NS = 140_000_000L;

    private final float[] press = new float[SLOTS];
    private final float[] pressFrom = new float[SLOTS];
    private final boolean[] pressTarget = new boolean[SLOTS];
    private final long[] pressStart = new long[SLOTS], pressLeg = new long[SLOTS];
    private int visibleMask;
    
    // ---- Label cache (bind names change only when keys are rebound) ----
    private static final long LABEL_REFRESH_MS = 2000L;
    private final String[] labels = new String[SLOTS];
    private final int[] labelWidths = new int[SLOTS];
    private long labelStamp;

    /** CPS text — integer values, so a 100 ms rebuild cap is imperceptible. */
    private final CachedValue<String> cpsText = new CachedValue<>(100L, this::buildCpsText);

    private final CachedValue<String[]> mouseCps = new CachedValue<>(100L,
            () -> new String[]{ClickTrackerFeature.LEFT.get() + " CPS", ClickTrackerFeature.RIGHT.get() + " CPS"});

    private static int mouseHeight(AuroraConfig cfg) { return CELL + (cfg.keystrokesShowCps ? 10 : 0); }

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
        if (cfg.keystrokesShowMouse)    { h += mouseHeight(cfg); rows += 1; }
        if (cfg.keystrokesShowCps && !cfg.keystrokesShowMouse)      { h += CPS_H;    rows += 1; }
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

        // One clock per frame: every slot's ease-out is a pure function of it.
        long now = System.nanoTime();

        // Slots that just became visible (a row toggled on) snap to their
        // live input state instead of easing in from a stale value.
        int mask = visibilityMask(cfg, extraCount);
        int newly = mask & ~visibleMask;
        visibleMask = mask;
        if (newly != 0) {
            for (int i = 0; i < SLOTS; i++) {
                if ((newly & (1 << i)) != 0) {
                    boolean down = slotDown(opts, extra, extraCount, i);
                    press[i] = pressFrom[i] = down ? 1f : 0f;
                    pressTarget[i] = down; pressLeg[i] = 0L;
                }
            }
        }

        int accent = accentColor(cfg);
        int labelColor = labelColor(cfg);
        int cy = y;

        // ---- Movement (W centered over A S D) ----
        if (cfg.keystrokesShowMovement) {
            advance(SLOT_W, down(opts.keyUp), now);
            drawKey(ctx, font, x + CELL + GAP, cy, CELL, CELL, SLOT_W, accent, labelColor);
            cy += CELL + GAP;

            advance(SLOT_A, down(opts.keyLeft), now);
            drawKey(ctx, font, x, cy, CELL, CELL, SLOT_A, accent, labelColor);
            advance(SLOT_S, down(opts.keyDown), now);
            drawKey(ctx, font, x + CELL + GAP, cy, CELL, CELL, SLOT_S, accent, labelColor);
            advance(SLOT_D, down(opts.keyRight), now);
            drawKey(ctx, font, x + (CELL + GAP) * 2, cy, CELL, CELL, SLOT_D, accent, labelColor);
            cy += CELL + GAP;
        }

        // ---- Mouse buttons (L / M / R, arranged like a mouse) ----
        if (cfg.keystrokesShowMouse) {
            advance(SLOT_LMB, down(opts.keyAttack), now);
            advance(SLOT_RMB, down(opts.keyUse), now);
            int mouseH = mouseHeight(cfg);
            if (showMid) {
                advance(SLOT_MMB, down(opts.keyPickItem), now);
                drawKey(ctx, font, x, cy, CELL, mouseH, SLOT_LMB, accent, labelColor);
                drawKey(ctx, font, x + CELL + GAP, cy, CELL, mouseH, SLOT_MMB, accent, labelColor);
                drawKey(ctx, font, x + (CELL + GAP) * 2, cy, CELL, mouseH, SLOT_RMB, accent, labelColor);
            } else {
                int side = (WIDTH - GAP) / 2;
                drawKey(ctx, font, x, cy, side, mouseH, SLOT_LMB, accent, labelColor);
                drawKey(ctx, font, x + side + GAP, cy, WIDTH - side - GAP, mouseH, SLOT_RMB, accent, labelColor);
            }
            cy += mouseH + GAP;
        }

        // ---- CPS readout (event-based rolling 1-second counters) ----
        if (cfg.keystrokesShowCps && !cfg.keystrokesShowMouse) {
            blitTemplate(ctx, TPL_PLATE, x, cy, WIDTH, CPS_H, accent, 1f);
            String text = cpsText.get();
            int tw = font.width(text);
            ctx.drawString(font, text, x + (WIDTH - tw) / 2,
                    cy + (CPS_H - font.lineHeight) / 2, labelColor, false);
            cy += CPS_H + GAP;
        }

        // ---- Spacebar ----
        if (cfg.keystrokesShowSpacebar) {
            advance(SLOT_SPACE, down(opts.keyJump), now);
            drawKey(ctx, font, x, cy, WIDTH, SPACE_H, SLOT_SPACE, accent, labelColor);
            cy += SPACE_H + GAP;
        }

        // ---- Sneak / Sprint ----
        if (cfg.keystrokesShowExtra) {
            advance(SLOT_SNEAK, down(opts.keyShift), now);
            advance(SLOT_SPRINT, down(opts.keySprint), now);
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
                advance(SLOT_EXTRA0 + i, boxed != null && AuroraKey.isDown(boxed), now);
                drawKey(ctx, font, x + c * (CELL + GAP), cy, CELL, CELL,
                        SLOT_EXTRA0 + i, accent, labelColor);
            }
            cy += CELL + GAP;
        }
    }

    // ---- Keycap templates (per-pixel rendering, 2026-10-19) ----
    //
    // Caps, bases and the CPS plate are anti-aliased at the device resolution
    // (RenderUtil's coverage math, the Aurora UI's rounded corners) instead of
    // the logical-pixel chamfers that read as blocky Minecraft UI. Drawing
    // that live would submit hundreds of strip fills per key every frame, so
    // each (shape, size) is rasterized once into a texture and blitted on the
    // device grid; colour/theme/density changes re-raster in place. The press
    // animation stays transform + opacity: the cap slides by a device-snapped
    // offset and the pressed cap fades in over the idle one.
    private static final int TPL_BASE = 0, TPL_IDLE = 1, TPL_PRESSED = 2, TPL_PLATE = 3;
    /** AA margin around a template (logical px). */
    private static final int TPL_PAD = 1;
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<com.aurora.client.ui.util.UiLayerCache> templates =
            new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    private com.aurora.client.ui.util.UiLayerCache template(GuiGraphics ctx, int kind, int w, int h, int accent) {
        long key = ((long) kind << 40) | ((long) w << 20) | h;
        var cache = templates.get(key);
        if (cache == null) { cache = new com.aurora.client.ui.util.UiLayerCache(); templates.put(key, cache); }
        float density = com.aurora.client.worldmap.MapMarkers.density(ctx);
        int idle = idleBackground();
        int outline = ThemeManager.color(ThemeToken.TILE_OUTLINE);
        long version = ThemeManager.generation() * 1_000_003L ^ Float.floatToIntBits(density) * 7919L
                ^ (long) accent * 31L ^ (long) idle * 131L ^ (long) outline * 524287L;
        if (!cache.isCurrent(version)) {
            float gui = (float) Minecraft.getInstance().getWindow().getGuiScale();
            float f = density / gui; // logical units that land on device pixels at this density
            int pw = Math.max(1, Math.round((w + TPL_PAD * 2) * density));
            int ph = Math.max(1, Math.round((h + TPL_PAD * 2) * density));
            cache.ensureSize(pw, ph);
            cache.clear();
            var previous = com.aurora.client.ui.util.RenderUtil.beginCapture(cache.sink());
            try {
                float x = TPL_PAD * f, y = TPL_PAD * f, cw = w * f, chh = h * f, r = KEY_RADIUS * f;
                switch (kind) {
                    case TPL_BASE -> com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x, y, cw, chh, r,
                            blend(idle, idle & 0xFF000000, 0.55f));
                    case TPL_IDLE -> {
                        com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x, y, cw, chh, r, idle);
                        // Hairline top light: the lit face of the resting cap.
                        com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x + r, y + f * .5f, cw - 2 * r, f * .75f, f * .375f, // capsule-ended hairline
                                ThemeManager.withAlpha(0xFFFFFFFF, 0x26));
                        com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx, x, y, cw, chh, r, f, outline);
                    }
                    case TPL_PRESSED -> {
                        com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x, y, cw, chh, r, blend(idle, accent, 0.30f));
                        com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx, x, y, cw, chh, r, f, blend(outline, accent, 0.85f));
                    }
                    default -> com.aurora.client.ui.util.RenderUtil.drawRoundedRectAA(ctx, x, y, cw, chh, r, idle);
                }
            } finally {
                com.aurora.client.ui.util.RenderUtil.endCapture(previous);
            }
            cache.commit(version);
        }
        return cache;
    }

    /** One template blit on the device grid, {@code alpha} 0..1 as a texture-alpha fade. */
    private void blitTemplate(GuiGraphics ctx, int kind, float x, float y, int w, int h, int accent, float alpha) {
        if (alpha <= 0.004f) return;
        var cache = template(ctx, kind, w, h, accent);
        float density = com.aurora.client.worldmap.MapMarkers.density(ctx);
        int px = com.aurora.client.worldmap.MapMarkers.pixelX(ctx, x - TPL_PAD, y - TPL_PAD);
        int py = com.aurora.client.worldmap.MapMarkers.pixelY(ctx, x - TPL_PAD, y - TPL_PAD);
        int size = Math.max(1, Math.round((w + TPL_PAD * 2) * density)), sizeH = Math.max(1, Math.round((h + TPL_PAD * 2) * density));
        com.aurora.client.worldmap.MapMarkers.blitPixels(ctx, cache, px, py, size, sizeH,
                (Math.round(Math.min(1f, alpha) * 255) << 24) | 0xFFFFFF);
    }

    /**
     * Draws one keycap. The cap ({@code h - DEPTH} tall) rests on a darker base
     * whose lower {@link #DEPTH} px read as the key's bottom edge; pressing
     * slides the cap (and its label) down onto the base by a device-snapped
     * offset while the accent-tinted pressed cap fades in over the idle one.
     */
    private void drawKey(GuiGraphics ctx, Font font, int x, int y, int w, int h,
                         int slot, int accent, int labelColor) {
        float t = press[slot];
        int capH = h - DEPTH;
        float density = com.aurora.client.worldmap.MapMarkers.density(ctx);
        // Whole device pixels only, so edges and text stay crisp mid-press.
        float offset = Math.round(t * DEPTH * density) / density;
        blitTemplate(ctx, TPL_BASE, x, y + DEPTH, w, capH, accent, 1f);
        blitTemplate(ctx, TPL_IDLE, x, y + offset, w, capH, accent, 1f);
        blitTemplate(ctx, TPL_PRESSED, x, y + offset, w, capH, accent, t);
        ctx.pose().pushMatrix();
        ctx.pose().translate(0f, offset);
        drawKeyLabel(ctx, font, x, y, w, capH, slot, labelColor);
        ctx.pose().popMatrix();
    }

    private void drawKeyLabel(GuiGraphics ctx, Font font, int x, int y, int w, int h,
                              int slot, int labelColor) {
        boolean mouseWithCps = h > CELL - DEPTH && slot >= SLOT_LMB && slot <= SLOT_RMB;
        int labelY = mouseWithCps ? y + 4 : y + (h - font.lineHeight) / 2 + 1;
        if (mouseWithCps && slot != SLOT_MMB) {
            String count = mouseCps.get()[slot == SLOT_LMB ? 0 : 1];
            float scale = Math.min(0.75f, (w - 6f) / font.width(count));
            ctx.pose().pushMatrix();
            ctx.pose().translate(x + (w - font.width(count) * scale) / 2f, y + h - 10);
            ctx.pose().scale(scale, scale);
            ctx.drawString(font, count, 0, 0, labelColor, false);
            ctx.pose().popMatrix();
        }

        String label = labels[slot];
        if (label == null || label.isEmpty()) return;
        int lw = labelWidths[slot];
        if (lw <= 0) return;
        if (lw <= w - 4) {
            ctx.drawString(font, label, x + (w - lw) / 2,
                    labelY, labelColor, false);
        } else {
            // Long bind names shrink to fit instead of overflowing the cell.
            float s = (w - 4f) / lw;
            int tx = x + Math.max(2, Math.round((w - lw * s) / 2f));
            int ty = labelY;
            ctx.pose().pushMatrix();
            ctx.pose().translate(tx, ty);
            ctx.pose().scale(s, s);
            ctx.drawString(font, label, 0, 0, labelColor, false);
            ctx.pose().popMatrix();
        }
    }

    /**
     * Interruptible ease-out toward the pressed/released state. A new target
     * starts from the currently visible value, and the leg is shortened in
     * proportion to the remaining distance, so rapid taps never snap or restart.
     */
    private void advance(int slot, boolean isDown, long now) {
        if (isDown != pressTarget[slot]) {
            pressTarget[slot] = isDown;
            pressFrom[slot] = press[slot];
            pressStart[slot] = now;
            float distance = Math.abs((isDown ? 1f : 0f) - press[slot]);
            pressLeg[slot] = Math.max(1L, Math.round((isDown ? PRESS_NS : RELEASE_NS) * (double) distance));
        }
        press[slot] = pressValue(pressFrom[slot], isDown, now - pressStart[slot], pressLeg[slot]);
    }

    /** Pure press curve: ease-out cubic from {@code from} to the target over {@code leg} ns. */
    static float pressValue(float from, boolean down, long elapsed, long leg) {
        float target = down ? 1f : 0f;
        if (leg <= 0L || elapsed >= leg) return target;
        float raw = Math.max(0f, elapsed / (float) leg);
        float eased = 1f - (1f - raw) * (1f - raw) * (1f - raw);
        return from + (target - from) * eased;
    }

    private static boolean down(KeyMapping km) {
        return km != null && km.isDown();
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

    /** Per-feature key label override, inheriting legacy HUD color until customized. */
    private static int labelColor(AuroraConfig cfg) {
        return cfg.keystrokesTextColor == null ? ThemeManager.color(ThemeToken.ON_SURFACE) : cfg.keystrokesTextColor;
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
        putLabel(font, SLOT_LMB, "LMB");
        putLabel(font, SLOT_MMB, "MMB");
        putLabel(font, SLOT_RMB, "RMB");
        putLabel(font, SLOT_SPACE, bindLabel(opts.keyJump, "SPACE"));
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
                        s = shortKeyName(s);
                        if (!s.isEmpty()) return s;
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
            if (s != null && !(s = shortKeyName(s)).isEmpty()) return s;
        } catch (Exception ignored) {
        }
        return "?" + glfwKey;
    }

    /**
     * Keycap legend for a vanilla key name: side prefixes dropped and common
     * modifiers abbreviated ("Left Shift" → "Shift", "Right Control" → "Ctrl"),
     * so caps read like a keyboard instead of a truncated "Left Shi".
     */
    static String shortKeyName(String name) {
        String s = name.trim();
        if (s.startsWith("Left ")) s = s.substring(5);
        else if (s.startsWith("Right ")) s = s.substring(6);
        s = switch (s) {
            case "Control" -> "Ctrl";
            case "Escape" -> "Esc";
            case "Backspace" -> "Bksp";
            case "Caps Lock" -> "Caps";
            case "Page Up" -> "PgUp";
            case "Page Down" -> "PgDn";
            case "Delete" -> "Del";
            case "Insert" -> "Ins";
            default -> s;
        };
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private String buildCpsText() {
        return "L: " + ClickTrackerFeature.LEFT.get() + "   R: " + ClickTrackerFeature.RIGHT.get();
    }
}