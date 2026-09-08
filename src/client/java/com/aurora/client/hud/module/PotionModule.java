package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int ICON_GAP   = 5;
    /** Per-row height. Icon is 18 px tall; two text lines (name + timer)
     *  fit comfortably inside that same band, matching Inventory HUD+. */
    private static final int ROW_H      = 22;
    /** Vertical gap between adjacent rows. */
    private static final int ROW_GAP    = 2;
    private static final int MIN_NAME_W = 60;

    private final CachedValue<int[]> dimsCache = new CachedValue<>(100L, this::computeDims);

    /**
     * Cache of formatted "Effect Name [Level]" strings.
     * Key = translationKey + "@" + amplifier. Tiny key space (a few dozen
     * effects Ã— 0â€“9 amplifier), so unbounded HashMap is fine.
     */
    private static final Map<String, String> NAME_CACHE = new HashMap<>();

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

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().potionBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().potionBgColor; }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().potionHudEnabled;
    }

    /** Returns [width, height]. Computed at most every 100ms. */
    private int[] computeDims() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return new int[] { 120, ROW_H };
        Font tr = client.font;
        int maxTextW = MIN_NAME_W;
        Collection<MobEffectInstance> active = effects();
        for (MobEffectInstance e : active) {
            // Per row, the text column has to fit either the effect
            // name + level on the top line or the timer on the bottom
            // line — take the widest of the two so neither clips.
            maxTextW = Math.max(maxTextW, tr.width(nameWithLevel(e)));
            maxTextW = Math.max(maxTextW, tr.width(timeText(e)));
        }
        int width = ICON + ICON_GAP + maxTextW + 4;
        int rows = Math.max(1, active.size());
        int height = rows * ROW_H + (rows - 1) * ROW_GAP;
        return new int[] { width, height };
    }

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
            return 1.0f;
        }
        if (lastEffectsPresentMs == 0L) return 0.0f;
        long dt = now - lastEffectsPresentMs;
        if (dt >= FADE_MS) return 0.0f;
        return 1.0f - dt / (float) FADE_MS;
    }

    @Override protected float backgroundAlpha() { return effectsFadeT(); }

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
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.potionHudEnabled) return;
        if (client.player == null) return;

        Collection<MobEffectInstance> active = effects();
        if (active.isEmpty()) return;

        Font tr = client.font;
        int row = 0;

        for (MobEffectInstance e : active) {
            int rowY = y + row * (ROW_H + ROW_GAP);

            // Real vanilla 18×18 status-effect sprite, vertically centered
            // inside the row band.
            drawEffectIcon(ctx, e, x, rowY + (ROW_H - ICON) / 2);

            // Two-line text column (Inventory HUD+ style):
            //   line 1 — "Effect Name II"           in white
            //   line 2 — two-tone timer:
            //              minutes (if any) in green,
            //              ":SS" or bare "SS" in white,
            //              or "∞" in white for effects ≥ 1 hour / truly infinite.
            int textX = x + ICON + ICON_GAP;
            int line1Y = rowY + 2;
            int line2Y = line1Y + tr.lineHeight + 1;
            ctx.drawString(tr, nameWithLevel(e), textX, line1Y, 0xFFFFFFFF, false);
            drawTimer(ctx, tr, e, textX, line2Y);

            row++;
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
    private static void drawTimer(GuiGraphics ctx, Font tr, MobEffectInstance e,
                                  int x, int y) {
        String mins = minutesString(e);
        String secs = secondsString(e);
        int cx = x;
        if (mins != null) {
            // Green minutes — the HUD's shared "plentiful time" status color.
            ctx.drawString(tr, mins, cx, y, com.aurora.client.theme.HudStatus.ON, false);
            cx += tr.width(mins);
        }
        ctx.drawString(tr, secs, cx, y, 0xFFFFFFFF, false);     // seconds / infinity — white
    }

    /**
     * Renders the vanilla 18×18 status-effect sprite registered at
     * {@code <namespace>:mob_effect/<id>} in the GUI sprite atlas — the
     * same one the inventory effect list uses. Falls back to a flat
     * tinted square only if the effect somehow has no registry key
     * (e.g. a custom effect that never registered a texture).
     */
    private static void drawEffectIcon(GuiGraphics ctx, MobEffectInstance e, int x, int y) {
        Holder<MobEffect> holder = e.getEffect();
        Identifier loc = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
        if (loc != null) {
            Identifier sprite = Identifier.fromNamespaceAndPath(
                    loc.getNamespace(), "mob_effect/" + loc.getPath());
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, ICON, ICON);
            return;
        }
        int color = 0xFF000000 | holder.value().getColor();
        ctx.fill(x, y, x + ICON, y + ICON, color);
    }

    /**
     * Localized name + Roman-numeral level, cached by (translationKey, amp).
     * Key cardinality is tiny (active effect types Ã— amplifiers), so the
     * cache never grows beyond a few dozen entries.
     */
    private static String nameWithLevel(MobEffectInstance e) {
        Holder<MobEffect> type = e.getEffect();
        String key = type.value().getDescriptionId();
        int amp = e.getAmplifier();
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
        return String.format("%02d", m);
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
        if (m > 0) return String.format(":%02d", s);
        return String.format("%02d", s);
    }
    @Override public String featureRegistryId() { return "potion_hud"; }
}