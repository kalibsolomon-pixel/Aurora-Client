package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudGrowth;
import com.aurora.client.hud.WrapGrid;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Status effect (potion) HUD module. Stacked rows of icon, name, level
 * (Roman numeral), and a smart countdown.
 *
 * <p>With the VANILLA background each icon sits in vanilla's own status-effect
 * container ({@code hud/effect_background}, the ambient one for beacon effects),
 * in one of two layouts: Standard keeps the list with names and timers beside
 * the icons; Condensed is a row of containers, as vanilla's HUD draws them, with
 * the time remaining over each one's bottom-left corner ("4m", then "59s").
 *
 * <p>The module grows from the screen edge toward the centre
 * ({@link HudGrowth#TOWARD_CENTER}): the first effect stays nearest the
 * border, and after {@link AuroraConfig#potionMaxPerLine} effects a line
 * wraps into a new one, also nearer the centre (2026-11-22).
 *
 * <p>Width and height are driven by the active effects' name/timer Component.
 * They're cached with a 100ms TTL so getWidth/getHeight stay cheap even
 * when called many times per frame.
 *
 * <p>Localized effect names are cached by (translationKey, amplifier) so
 * the {@code Component.translatable} lookup runs once per unique effect rather
 * than every dimension recompute. Cleared on language change is unnecessary
 * â€” Minecraft restarts on language change in 1.21.x.
 */
public class PotionModule extends HudModule {
    public static final String ID = "potion";

    private static final int ICON       = 18;
    /** Vanilla's status-effect container, and the step between containers in a vanilla row. */
    private static final int CONTAINER  = 24;
    private static final int CONTAINER_STEP = 25;
    private static final Identifier CONTAINER_SPRITE = Identifier.withDefaultNamespace("hud/effect_background");
    private static final Identifier CONTAINER_AMBIENT_SPRITE = Identifier.withDefaultNamespace("hud/effect_background_ambient");
    private static final int ICON_GAP   = 5;
    /** Per-row height. Icon is 18 px tall; two text lines (name + timer)
     *  fit comfortably inside that same band, matching Inventory HUD+. */
    private static final int ROW_H      = 22;
    /** Vertical gap between adjacent rows. */
    private static final int ROW_GAP    = 2;
    private static final int MIN_NAME_W = 60;
    /** Standard layout: gap between wrapped columns. */
    private static final int COLUMN_GAP = 4;

    private final CachedValue<int[]> dimsCache = new CachedValue<>(100L, this::computeDims);

    /** Cache of formatted "Effect Name [Level]" strings keyed by MobEffect identity + amplifier (0..15). */
    private static final Map<MobEffect, String[]> FAST_NAME_CACHE = new java.util.IdentityHashMap<>();
    private static final Map<String, String> NAME_CACHE = new HashMap<>();
    /** Cache of effect sprite Identifiers keyed by MobEffect identity; null sentinel = no registry key. */
    private static final Identifier NO_SPRITE = Identifier.fromNamespaceAndPath("aurora", "__none");
    private static final Map<MobEffect, Identifier> SPRITE_CACHE = new java.util.IdentityHashMap<>();
    private static final String[] CONDENSED_SECS = new String[60];
    private static final String[] CONDENSED_MINS = new String[60];

    /** How long the background takes to fade out after the last effect expires. */
    private static final long FADE_MS = 300L;
    /**
     * Wall-clock millis of the most recent frame on which at least one
     * active effect was present. Drives the post-expiry fade so the
     * panel glides out smoothly instead of popping.
     */
    private long lastEffectsPresentMs = 0L;
    /**
     * Dimensions captured on the last frame with active effects. Held
     * during the fade so the panel keeps the size it had at the moment
     * effects expired, rather than collapsing to the empty-state size
     * and visibly snapping smaller mid-fade.
     */
    private int lastNonEmptyW = 0;
    private int lastNonEmptyH = 0;
    private int lastNonEmptyColW = 0;

    public PotionModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_RIGHT;
        this.offsetX = -4;
        this.offsetY = 4;
    }

    private Collection<MobEffectInstance> effects() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return java.util.List.of();
        return mc.player.getActiveEffects();
    }

    private static boolean vanillaSlots() { return AuroraConfig.get().potionBgMode == AuroraConfig.HudBackground.VANILLA; }
    private static boolean condensed() {
        return vanillaSlots() && AuroraConfig.get().potionVanillaLayout == AuroraConfig.PotionVanillaLayout.CONDENSED;
    }
    private static int iconWidth() { return vanillaSlots() ? CONTAINER : ICON; }
    private static int rowHeight() { return vanillaSlots() ? CONTAINER : ROW_H; }
    private static int rowStride() { return vanillaSlots() ? CONTAINER_STEP : ROW_H + ROW_GAP; }
    @Override protected AuroraConfig.HudBackground backgroundMode() {
        return vanillaSlots() ? AuroraConfig.HudBackground.NONE : AuroraConfig.get().potionBgMode;
    }
    @Override protected int backgroundColor() { return AuroraConfig.get().potionBgColor; }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().potionHudEnabled;
    }

    /**
     * Effects per line: the setting, cut to what fits on the screen along the
     * line (a row when Condensed, a column otherwise) at the module's scale.
     */
    private int perLine() {
        int setting = Math.max(1, Math.min(12, AuroraConfig.get().potionMaxPerLine));
        float s = Math.max(MIN_SCALE, scale);
        if (condensed()) {
            if (lastScreenW <= 0) return setting;
            int fit = (int) ((lastScreenW / s + (CONTAINER_STEP - CONTAINER)) / CONTAINER_STEP);
            return Math.max(1, Math.min(setting, fit));
        }
        if (lastScreenH <= 0) return setting;
        int fit = (int) ((lastScreenH / s - rowHeight()) / rowStride()) + 1;
        return Math.max(1, Math.min(setting, fit));
    }

    private WrapGrid grid(int count) { return WrapGrid.of(count, perLine()); }

    /** Returns [width, height, column width]. Computed at most every 100ms. */
    private int[] computeDims() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return new int[] { 120, ROW_H, 120 };
        Font tr = client.font;
        int maxTextW = MIN_NAME_W;
        Collection<MobEffectInstance> active = effects();
        WrapGrid grid = grid(active.size());
        if (condensed()) {
            // A row of containers per line; wrapped lines stack as further rows.
            return new int[] { grid.along() * CONTAINER_STEP - (CONTAINER_STEP - CONTAINER),
                    grid.lines() * CONTAINER_STEP - (CONTAINER_STEP - CONTAINER), CONTAINER };
        }
        for (MobEffectInstance e : active) {
            // Per row, the text column has to fit either the effect
            // name + level on the top line or the timer on the bottom
            // line — take the widest of the two so neither clips.
            maxTextW = Math.max(maxTextW, tr.width(nameWithLevel(e)));
            maxTextW = Math.max(maxTextW, tr.width(timeText(e)));
        }
        // A column of rows per line; wrapped lines sit side by side, one shared column width.
        int colW = iconWidth() + ICON_GAP + maxTextW + 4;
        int width = grid.lines() * colW + (grid.lines() - 1) * COLUMN_GAP;
        int height = (grid.along() - 1) * rowStride() + rowHeight();
        return new int[] { width, height, colW };
    }

    @Override protected HudGrowth growthX() { return HudGrowth.TOWARD_CENTER; }
    @Override protected HudGrowth growthY() { return HudGrowth.TOWARD_CENTER; }

    /** One column (Standard) or one container (Condensed): the anchor places the first effect. */
    @Override protected int referenceWidth() {
        if (condensed()) return CONTAINER;
        if (effectsFadeT() > 0f && lastNonEmptyColW > 0) return lastNonEmptyColW;
        return dimsCache.get()[2];
    }

    @Override protected int referenceHeight() { return rowHeight(); }

    /**
     * Fade fraction in [0, 1]: 1 while at least one effect is active,
     * decays linearly to 0 over {@link #FADE_MS} after the last expires.
     * Also updates the snapshot dimensions on every active frame so the
     * fade-out keeps the panel at its last "real" size.
     *
     * <p>Cached at millisecond resolution: {@code getWidth()},
     * {@code getHeight()}, and {@code backgroundAlpha()} all call this
     * within a single render frame (≤ 1 ms apart at any realistic FPS),
     * so the {@code System.currentTimeMillis()} equality check returns
     * the cached value for the 2nd and 3rd calls instead of re-querying
     * the player's effect collection three times per frame.
     */
    private long cachedFadeTMs = Long.MIN_VALUE;
    private float cachedFadeT;

    private float effectsFadeT() {
        long now = System.currentTimeMillis();
        if (now == cachedFadeTMs) return cachedFadeT;
        cachedFadeTMs = now;
        cachedFadeT = computeEffectsFadeT(now);
        return cachedFadeT;
    }

    private float computeEffectsFadeT(long now) {
        Collection<MobEffectInstance> active = effects();
        if (!active.isEmpty()) {
            lastEffectsPresentMs = now;
            int[] d = dimsCache.get();
            lastNonEmptyW = d[0];
            lastNonEmptyH = d[1];
            lastNonEmptyColW = d[2];
            return 1.0f;
        }
        if (lastEffectsPresentMs == 0L) return 0.0f;
        long dt = now - lastEffectsPresentMs;
        if (dt >= FADE_MS) return 0.0f;
        return 1.0f - dt / (float) FADE_MS;
    }

    @Override protected float backgroundAlpha() { return effectsFadeT(); }

    @Override public boolean showsNothing() { return effectsFadeT() <= 0f; }

    @Override public int getWidth() {
        // Hold the last non-empty width while fading out so the panel
        // doesn't visibly shrink mid-fade. Once fully faded we revert
        // to the live computed size.
        if (effectsFadeT() > 0f && lastNonEmptyW > 0) return lastNonEmptyW;
        return dimsCache.get()[0];
    }
    @Override public int getHeight() {
        if (effectsFadeT() > 0f && lastNonEmptyH > 0) return lastNonEmptyH;
        return dimsCache.get()[1];
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.potionHudEnabled) return;
        if (client.player == null) return;

        Collection<MobEffectInstance> active = effects();
        if (active.isEmpty()) return;

        Font tr = client.font;
        WrapGrid grid = grid(active.size());
        // Right of / below the centre, the first effect and line sit at the far (outer) edge.
        boolean right = sideX() > 0;
        boolean below = sideY() > 0;
        int i = 0;

        if (condensed()) {
            // Vanilla rows: containers, each with its time in the bottom-left corner.
            for (MobEffectInstance e : active) {
                int cx = x + grid.alongSlot(i, right) * CONTAINER_STEP;
                int cy = y + grid.lineSlot(i, below) * CONTAINER_STEP;
                drawContainer(ctx, e, cx, cy);
                drawEffectIcon(ctx, e, cx + (CONTAINER - ICON) / 2, cy + (CONTAINER - ICON) / 2);
                com.aurora.client.hud.HudTextPainter.draw(ctx, tr, condensedTime(e), cx + 2,
                        cy + CONTAINER - tr.lineHeight, textColor(0xFFFFFFFF));
                i++;
            }
            return;
        }

        int colW = referenceWidth();
        for (MobEffectInstance e : active) {
            int rowY = y + grid.alongSlot(i, below) * rowStride();
            int colX = x + grid.lineSlot(i, right) * (colW + COLUMN_GAP);

            // Real vanilla 18×18 status-effect sprite, vertically centered
            // inside the row band.
            if (vanillaSlots()) drawContainer(ctx, e, colX, rowY);
            drawEffectIcon(ctx, e, colX + (iconWidth() - ICON) / 2, rowY + (rowHeight() - ICON) / 2);

            // Two-line text column (Inventory HUD+ style):
            //   line 1 — "Effect Name II"           in white
            //   line 2 — two-tone timer:
            //              minutes (if any) in green,
            //              ":SS" or bare "SS" in white,
            //              or "∞" in white for effects ≥ 1 hour / truly infinite.
            int textX = colX + iconWidth() + ICON_GAP;
            int line1Y = rowY + (rowHeight() - (tr.lineHeight * 2 + 1)) / 2;
            int line2Y = line1Y + tr.lineHeight + 1;
            com.aurora.client.hud.HudTextPainter.draw(ctx,tr, nameWithLevel(e), textX, line1Y, textColor(0xFFFFFFFF));
            drawTimer(ctx, tr, e, textX, line2Y);

            i++;
        }
    }

    /**
     * Renders the per-effect timer on row 2 in the new XX:XX style:
     * <ul>
     *   <li>≥ 1 hour remaining (or truly infinite) → {@code ∞} in white.</li>
     *   <li>≥ 1 minute remaining → {@code MM:SS} with the {@code MM} half
     *       drawn green and the {@code :SS} half drawn white.</li>
     *   <li>&lt; 1 minute remaining → bare {@code SS} in white (we drop
     *       the minute pair once it would just read {@code 00:SS} — the
     *       "largest interval left" rule from the spec).</li>
     * </ul>
     */
    private static int textColor(int fallback) {
        Integer override = AuroraConfig.get().potionHudTextColor;
        return override == null ? fallback : override;
    }

    private static void drawTimer(GuiGraphicsExtractor ctx, Font tr, MobEffectInstance e,
                                  int x, int y) {
        String mins = minutesString(e);
        String secs = secondsString(e);
        int cx = x;
        if (mins != null) {
            // Green minutes — the HUD's shared "plentiful time" status color.
            com.aurora.client.hud.HudTextPainter.draw(ctx,tr, mins, cx, y, textColor(com.aurora.client.theme.HudStatus.ON));
            cx += tr.width(mins);
        }
        com.aurora.client.hud.HudTextPainter.draw(ctx,tr, secs, cx, y, textColor(0xFFFFFFFF));     // seconds / infinity — white
    }

    /** Vanilla's status-effect container; beacon (ambient) effects get the ambient one, as in vanilla. */
    private static void drawContainer(GuiGraphicsExtractor ctx, MobEffectInstance e, int x, int y) {
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, e.isAmbient() ? CONTAINER_AMBIENT_SPRITE : CONTAINER_SPRITE,
                x, y, CONTAINER, CONTAINER);
    }

    /**
     * Condensed time: whole minutes ("4m") while a minute or more remains, then
     * seconds counting down ("59s"); "\u221E" for an infinite effect.
     */
    static String condensedTime(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "\u221E";
        return condensedTime((e.getDuration() + 19) / 20);
    }

    static String condensedTime(int seconds) {
        if (seconds >= 60) {
            int m = seconds / 60;
            if (m < CONDENSED_MINS.length) {
                String cached = CONDENSED_MINS[m];
                if (cached == null) CONDENSED_MINS[m] = cached = m + "m";
                return cached;
            }
            return m + "m";
        }
        if (seconds >= 0 && seconds < CONDENSED_SECS.length) {
            String cached = CONDENSED_SECS[seconds];
            if (cached == null) CONDENSED_SECS[seconds] = cached = seconds + "s";
            return cached;
        }
        return seconds + "s";
    }

    /**
     * Renders the vanilla 18×18 status-effect sprite registered at
     * {@code <namespace>:mob_effect/<id>} in the GUI sprite atlas — the
     * same one the inventory effect list uses. Falls back to a flat
     * tinted square only if the effect somehow has no registry key
     * (e.g. a custom effect that never registered a texture).
     */
    private static void drawEffectIcon(GuiGraphicsExtractor ctx, MobEffectInstance e, int x, int y) {
        Holder<MobEffect> holder = e.getEffect();
        MobEffect effect = holder.value();
        Identifier sprite = SPRITE_CACHE.get(effect);
        if (sprite == null) {
            Identifier loc = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
            sprite = loc != null
                    ? Identifier.fromNamespaceAndPath(loc.getNamespace(), "mob_effect/" + loc.getPath())
                    : NO_SPRITE;
            SPRITE_CACHE.put(effect, sprite);
        }
        if (sprite != NO_SPRITE) {
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON, ICON);
            return;
        }
        int color = 0xFF000000 | effect.getColor();
        ctx.fill(x, y, x + ICON, y + ICON, color);
    }

    /**
     * Localized name + Roman-numeral level, cached by (MobEffect identity, amp)
     * so per-frame HUD draws avoid allocating {@code key + "@" + amp} strings.
     */
    private static String nameWithLevel(MobEffectInstance e) {
        MobEffect effect = e.getEffect().value();
        int amp = e.getAmplifier();
        if (amp >= 0 && amp < 16) {
            String[] byAmp = FAST_NAME_CACHE.get(effect);
            if (byAmp == null) {
                byAmp = new String[16];
                FAST_NAME_CACHE.put(effect, byAmp);
            }
            String cached = byAmp[amp];
            if (cached != null) return cached;
            String base = Component.translatable(effect.getDescriptionId()).getString();
            String result = (amp <= 0) ? base : base + " " + roman(amp + 1);
            byAmp[amp] = result;
            return result;
        }
        String key = effect.getDescriptionId();
        String cacheKey = key + "@" + amp;
        String cached = NAME_CACHE.get(cacheKey);
        if (cached != null) return cached;

        String base = Component.translatable(key).getString();
        String result = (amp <= 0) ? base : base + " " + roman(amp + 1);
        NAME_CACHE.put(cacheKey, result);
        return result;
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(n);
        };
    }

    /**
     * Combined timer string used for width measurement only. Mirrors what
     * {@link #drawTimer} actually paints, glued end-to-end, so the column
     * width in {@link #computeDims} always fits the rendered timer.
     */
    private static String timeText(MobEffectInstance e) {
        String m = minutesString(e);
        String s = secondsString(e);
        return (m != null ? m : "") + s;
    }

    /**
     * Two-digit minute string, or {@code null} when there is no minute
     * component to draw (either &lt; 60 s remaining, or ≥ 1 h / infinite —
     * the infinity glyph takes over and the minutes column is suppressed).
     */
    private static String minutesString(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return null;
        int seconds = (e.getDuration() + 19) / 20;
        if (seconds >= 3600) return null; // ≥ 1h → infinity, no minutes column
        int m = seconds / 60;
        if (m <= 0) return null;
        return twoDigits(m, false);
    }

    /**
     * The white-painted right side of the timer:
     * <ul>
     *   <li>{@code "∞"} for infinite or ≥ 1 h remaining,</li>
     *   <li>{@code ":SS"} when there is a minute prefix,</li>
     *   <li>{@code "SS"} (no colon) when the effect is in its final
     *       sub-minute count-down.</li>
     * </ul>
     */
    private static String secondsString(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "\u221E";
        int seconds = (e.getDuration() + 19) / 20;
        if (seconds >= 3600) return "\u221E";
        int m = seconds / 60;
        int s = seconds % 60;
        if (m > 0) return twoDigits(s, true);
        return twoDigits(s, false);
    }

    /** {@code String.format("%02d")} (or {@code ":%02d"}) per value 0-59, formatted once: the HUD asks every frame. */
    private static final String[] TWO_DIGITS = new String[60], COLON_TWO_DIGITS = new String[60];

    private static String twoDigits(int value, boolean colon) {
        String[] memo = colon ? COLON_TWO_DIGITS : TWO_DIGITS;
        String text = memo[value];
        if (text == null) memo[value] = text = String.format(colon ? ":%02d" : "%02d", value);
        return text;
    }
    @Override public String featureRegistryId() { return "potion_hud"; }
}
