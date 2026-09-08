package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.feature.impl.StatsTrackerFeature;
import com.aurora.client.util.CachedValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Session combat-stats readout: kills, deaths, K/D, session time, plus the
 * fight rows merged from Better Hitreg (fights this session / lifetime,
 * fight time, last fight's duration and accuracies). Data comes from
 * {@link StatsTrackerFeature} (session) and {@code AuroraConfig}'s
 * {@code fightStats*} fields (lifetime). Each row is individually
 * toggleable; the module hides entirely when no row is enabled.
 *
 * <p>Totem pops are intentionally <em>not</em> shown here — that lives in
 * the dedicated Totem Pop counter module.
 *
 * <p>Mirrors {@link InfoModule}: dynamic strings are rebuilt at most every
 * 100&nbsp;ms via {@link CachedValue} so the per-frame cost is negligible.
 */
public class StatsModule extends HudModule {
    public static final String ID = "stats";

    private final CachedValue<List<String>> linesCache = new CachedValue<>(100L, this::computeLines);

    public StatsModule() {
        super(ID);
        this.anchor = com.aurora.client.hud.HudAnchor.TOP_LEFT;
        this.offsetX = 4;
        this.offsetY = 100;
    }

    private List<String> computeLines() {
        AuroraConfig cfg = AuroraConfig.get();
        StatsTrackerFeature stats = StatsTrackerFeature.get();
        List<String> lines = new ArrayList<>(7);
        if (stats == null) return lines;

        if (cfg.statsShowKills)  lines.add("Kills: " + stats.kills());
        if (cfg.statsShowDeaths) lines.add("Deaths: " + stats.deaths());
        if (cfg.statsShowKd)     lines.add(String.format(Locale.ROOT, "K/D: %.2f", stats.kd()));
        if (cfg.statsShowSession) lines.add("Session: " + formatDuration(stats.sessionMs()));

        // Fight rows (Better Hitreg's fight tracker; see StatsTrackerFeature).
        if (cfg.statsShowFights) {
            lines.add("Fights: " + stats.sessionFights() + " (" + cfg.fightStatsTotalFights + " total)");
        }
        if (cfg.statsShowFightTime) {
            lines.add("Fight Time: " + formatDuration(stats.sessionFightSeconds() * 1000L)
                    + " (" + formatDuration(cfg.fightStatsPlaytimeSeconds * 1000L) + " total)");
        }
        if (cfg.statsShowLastFight) {
            StatsTrackerFeature.LastFight lf = stats.lastFight();
            if (lf == null) {
                lines.add("Last Fight: -");
            } else {
                StringBuilder sb = new StringBuilder("Last Fight: ")
                        .append(formatDuration(lf.durationSeconds() * 1000L));
                int you = lf.yourAccuracyPct();
                int them = lf.theirAccuracyPct();
                if (you >= 0) sb.append(" \u00b7 You ").append(you).append('%');
                if (them >= 0) sb.append(" \u00b7 Them ").append(them).append('%');
                lines.add(sb.toString());
            }
        }

        return lines;
    }

    /**
     * Compact two-tier duration, identical in spirit to the Info HUD's
     * playtime formatter: {@code <1m → "12s"}, {@code <1h → "5m 12s"},
     * {@code <1d → "2h 5m"}, otherwise {@code "3d 2h"}.
     */
    private static String formatDuration(long ms) {
        if (ms < 0L) ms = 0L;
        long s = ms / 1000L;
        long m = s / 60L; s %= 60L;
        long h = m / 60L; m %= 60L;
        long d = h / 24L; h %= 24L;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().statsBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().statsBgColor; }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().statsEnabled;
    }

    @Override
    public int getWidth() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 60;
        Font tr = client.font;
        int max = 0;
        List<String> lines = linesCache.get();
        for (int i = 0; i < lines.size(); i++) {
            int w = tr.width(lines.get(i));
            if (w > max) max = w;
        }
        return Math.max(max, 40);
    }

    @Override
    public int getHeight() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return 10;
        int n = linesCache.get().size();
        return Math.max(1, n) * (client.font.lineHeight + 1);
    }

    @Override
    protected void renderContent(GuiGraphics ctx, Minecraft client, int x, int y) {
        if (!AuroraConfig.get().statsEnabled) return;
        Font tr = client.font;
        if (tr == null) return;
        int color = AuroraConfig.get().hudColor;
        int lh = tr.lineHeight + 1;
        List<String> lines = linesCache.get();
        for (int i = 0; i < lines.size(); i++) {
            ctx.drawString(tr, lines.get(i), x, y + i * lh, color, false);
        }
    }

    @Override public String featureRegistryId() { return "stats"; }
}
