package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.ReachTrackerFeature;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * "Reach: 3.12b" â€” fades smoothly over the last {@value #FADE_WINDOW_MS}ms
 * of its {@value #TOTAL_MS}ms display window using a smoothstep curve,
 * then hides. No hard cutoff.
 *
 * <p>Reach value only changes on attack (driven by ReachTrackerFeature),
 * so the formatted string is cached with a 100 ms TTL. This eliminates a
 * String.format("%.2fb") call from every frame while the module is visible.
 */
public class ReachModule extends HudModule {
    public static final String ID = "reach";
    private static final long TOTAL_MS       = 5_000L;
    private static final long FADE_WINDOW_MS = 1_500L; // last 1.5s fades

    private final CachedValue<String> textCache = new CachedValue<>(100L, this::buildText);

    public ReachModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 80;
    }

    private String buildText() {
        return String.format("Reach: %.2fb", ReachTrackerFeature.lastReach);
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().reachBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().reachBgColor; }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 80;
        return client.font.width(textCache.get());
    }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 10;
        return client.font.lineHeight;
    }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().reachEnabled;
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        if (!AuroraConfig.get().reachEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        if (ReachTrackerFeature.lastReachTime == 0L) return;

        long age = System.currentTimeMillis() - ReachTrackerFeature.lastReachTime;
        if (age >= TOTAL_MS) return;

        int baseColor = com.aurora.client.theme.HudText.color(AuroraConfig.get().hudColor);
        int baseAlpha = (baseColor >>> 24) & 0xFF;
        float fade = computeFade(age);
        int alpha = Math.round(baseAlpha * fade);

        // Skip drawing if effectively invisible â€” prevents sub-pixel flicker
        // at the tail of the fade where alpha rounds to 0 or 1.
        if (alpha <= 1) return;

        int color = (alpha << 24) | (baseColor & 0x00FFFFFF);
        ctx.drawString(tr, textCache.get(), x, y, color, false);
    }

    /**
     * @return 1.0 while fresh, smoothly easing to 0.0 across the final
     *         {@link #FADE_WINDOW_MS} of the display window.
     */
    private static float computeFade(long age) {
        long fadeStart = TOTAL_MS - FADE_WINDOW_MS;
        if (age <= fadeStart) return 1f;
        float t = (age - fadeStart) / (float) FADE_WINDOW_MS; // 0..1
        // Smoothstep: 3tÂ² - 2tÂ³  â€” gentler entry and exit, no banding.
        float smooth = t * t * (3 - 2 * t);
        return 1f - smooth;
    }
    @Override public String featureRegistryId() { return "reach_display"; }
}