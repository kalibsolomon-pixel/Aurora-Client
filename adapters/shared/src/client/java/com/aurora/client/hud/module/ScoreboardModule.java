package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Customizable replacement for the vanilla scoreboard sidebar, drawn
 * through the universal HUD module layer (position, scale, drag/resize,
 * lock and enable all come from {@link HudModule}).
 *
 * <p>Data pipeline: the per-adapter {@code ScoreboardSidebarMixin} resolves
 * the sidebar objective exactly as vanilla does (team-colored slot falling
 * back to {@code DisplaySlot.SIDEBAR}) in the same frame it would draw it,
 * hands it here via {@link #aurora$noteObjective}, and cancels vanilla's
 * draw. This module re-enters the cached objective through the module
 * manager's render path, so every sidebar is drawn exactly once and its
 * time counts toward the overhead budget like any other HUD module.
 *
 * <p>Row data is rebuilt at most once per tick (game-time + objective
 * identity stamp) into preallocated arrays — no per-frame allocations, no
 * streams in the render path, matching vanilla's ordering (score
 * descending, then owner case-insensitive) and its 15-row cap.
 *
 * <p>Background: VANILLA reproduces vanilla's two translucent stripes and
 * metrics; SOLID/NONE route through {@link HudBackgrounds}. Size follows
 * the live data (vanilla sidebar geometry), scaled by
 * {@code scoreboardScale}; position is the universal layout
 * (factory default MIDDLE_RIGHT, the closest static approximation of
 * vanilla's bottom-pinned-at-center placement).
 */
public final class ScoreboardModule extends HudModule {
    public static final String ID = "scoreboard";
    /** Same cap as vanilla's sidebar. */
    private static final int MAX_ROWS = 15;
    /** Vanilla's separator between a row name and its numeric score. */
    private static final String COLUMN_SPACER = ": ";

    /** Objective resolved by the adapter mixin for the current frame. */
    private Objective currentObjective;
    private Objective renderedObjective;
    private long renderStamp = Long.MIN_VALUE;

    /**
     * Per-adapter F1 (hide HUD) check: 26.1 flags it on
     * {@code Options.hideGui}, 26.2/26.3 on {@code Hud.isHidden()}. Each
     * adapter's {@code AuroraClient} wires this once at startup; the default
     * reports never hidden.
     */
    public static volatile java.util.function.BooleanSupplier vanillaHudHidden = () -> false;

    // Preallocated scratch: rebuilt per tick, read per frame.
    private final Component[] names = new Component[MAX_ROWS];
    private final Component[] scores = new Component[MAX_ROWS];
    private final int[] scoreWidths = new int[MAX_ROWS];
    private int rows;
    private Component title;
    private int titleWidth;
    private int nameColumnWidth;   // widest "name(: score)" cell, plus 4px padding
    private int spacerWidth;

    /** Placeholders for the HUD editor when no sidebar objective is live. */
    private static final Component[] SAMPLE_NAMES = {
            Component.literal("Red Team"), Component.literal("Blue Team"), Component.literal("Spectators")
    };
    private static final int[] SAMPLE_SCORES = { 12, 7, 2 };

    public ScoreboardModule() {
        super(ID);
        resetLayoutToDefaults();
    }

    /** Per-adapter sidebar mixin: hand over the frame's sidebar objective (may be null). */
    public void aurora$noteObjective(Objective objective) {
        this.currentObjective = objective;
    }

    @Override public boolean isConfigEnabled() { return AuroraConfig.get().scoreboardEnabled; }

    @Override public String featureRegistryId() { return ID; }

    @Override protected AuroraConfig.HudBackground backgroundMode() {
        AuroraConfig.HudBackground mode = AuroraConfig.get().scoreboardBackground;
        return mode == AuroraConfig.HudBackground.VANILLA ? AuroraConfig.HudBackground.NONE : mode;
    }

    @Override protected int backgroundColor() { return AuroraConfig.get().scoreboardBgColor; }

    /** Extra content scale on top of the universal layout scale. */
    private static float contentScale() {
        double raw = AuroraConfig.get().scoreboardScale;
        return (float) Math.min(Math.max(raw, HudModule.MIN_SCALE), HudModule.MAX_SCALE);
    }

    @Override public int getWidth() {
        return Math.round((dataWidth() + 4) * contentScale());
    }

    @Override public int getHeight() {
        return Math.round((dataHeight()) * contentScale());
    }

    /** Intrinsic content width (box without the 2px side paddings) at scale 1. */
    private int dataWidth() {
        if (rows == 0) return sampleWidth();
        return Math.max(titleWidth, nameColumnWidth);
    }

    private int dataHeight() {
        return 10 + Math.max(rows, 0) * 9;
    }

    private int sampleWidth() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.font.width("Scoreboard");
        for (int i = 0; i < SAMPLE_NAMES.length; i++) {
            int scoreWidth = mc.font.width(Integer.toString(SAMPLE_SCORES[i]));
            width = Math.max(width, mc.font.width(SAMPLE_NAMES[i]) + (scoreWidth > 0 ? mc.font.width(COLUMN_SPACER) + scoreWidth : 0));
        }
        return width;
    }

    @Override public void resetLayoutToDefaults() {
        if (restoreReleaseLayout()) return;
        // Closest static approximation of vanilla's sidebar: right edge,
        // slightly above screen centre (vanilla pins the box's bottom at
        // centre + height/3, which no static offset reproduces for all
        // row counts).
        anchor = HudAnchor.MIDDLE_RIGHT; offsetX = -2; offsetY = -6;
        scale = 1; enabled = true; locked = false;
    }

    @Override protected void renderContent(GuiGraphicsExtractor g, Minecraft mc, int x, int y) {
        if (mc.level == null || mc.player == null) return;
        if (vanillaHudHidden.getAsBoolean()) return; // F1 hides vanilla's sidebar too
        AuroraConfig cfg = AuroraConfig.get();
        float s = contentScale();

        boolean editor = mc.screen instanceof com.aurora.client.screen.HudEditorScreen;
        boolean vanillaBackground = cfg.scoreboardBackground == AuroraConfig.HudBackground.VANILLA;

        if (editor && currentObjective == null && rows == 0) {
            aurora$drawVanillaStripes(g, mc, x, y, vanillaBackground);
            aurora$drawSampleContent(g, mc, x, y, s);
            return;
        }

        aurora$rebuildIfNeeded(mc);
        if (rows == 0) return; // nothing live: vanilla draws nothing either

        aurora$drawVanillaStripes(g, mc, x, y, vanillaBackground);
        aurora$drawContent(g, mc, x, y, s);
    }

    /** Vanilla's two translucent stripes, drawn in module space so they exactly cover the box. */
    private void aurora$drawVanillaStripes(GuiGraphicsExtractor g, Minecraft mc, int x, int y,
                                           boolean vanillaBackground) {
        if (!vanillaBackground) return;
        int w = getWidth();
        int h = getHeight();
        int headerH = Math.round(9 * contentScale());
        int headerBg = mc.options.getBackgroundColor(0.4F);
        int bodyBg = mc.options.getBackgroundColor(0.3F);
        g.fill(x, y, x + w, y + headerH, headerBg);
        g.fill(x, y + headerH, x + w, y + h, bodyBg);
    }

    private void aurora$drawContent(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        int contentWidth = dataWidth() + 4;
        g.text(mc.font, title, (contentWidth - titleWidth) / 2, 0, -1, false);
        for (int i = 0; i < rows; i++) {
            int rowY = 10 + i * 9;
            g.text(mc.font, names[i], 2, rowY, -1, false);
            if (scoreWidths[i] > 0) {
                g.text(mc.font, scores[i], contentWidth - 2 - scoreWidths[i], rowY, -1, false);
            }
        }
        g.pose().popMatrix();
    }

    private void aurora$drawSampleContent(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        Component sampleTitle = Component.literal("Scoreboard");
        int contentWidth = sampleWidth() + 4;
        g.text(mc.font, sampleTitle, (contentWidth - mc.font.width(sampleTitle)) / 2, 0, -1, false);
        for (int i = 0; i < SAMPLE_NAMES.length; i++) {
            int rowY = 10 + i * 9;
            g.text(mc.font, SAMPLE_NAMES[i], 2, rowY, -1, false);
            String score = Integer.toString(SAMPLE_SCORES[i]);
            g.text(mc.font, score, contentWidth - 2 - mc.font.width(score), rowY, -1, false);
        }
        g.pose().popMatrix();
    }

    /**
     * Rebuilds the row cache at most once per tick (or when the sidebar
     * objective changed). Equivalent work to vanilla's per-tick sidebar
     * extraction: one list, one sort, capped at 15 entries.
     */
    private void aurora$rebuildIfNeeded(Minecraft mc) {
        long stamp = mc.level.getGameTime();
        if (stamp == renderStamp && renderedObjective == currentObjective) return;
        renderStamp = stamp;
        renderedObjective = currentObjective;

        if (currentObjective == null) { rows = 0; title = null; titleWidth = 0; nameColumnWidth = 0; return; }

        Scoreboard scoreboard = currentObjective.getScoreboard();
        NumberFormat format = currentObjective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);

        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(currentObjective));
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).isHidden()) entries.remove(i);
        }
        // Vanilla's SCORE_DISPLAY_ORDER: score descending, then owner
        // case-insensitive. Small list (<= 15 drawn), plain sort.
        entries.sort(Comparator.comparing(PlayerScoreEntry::value).reversed()
                .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER));

        this.spacerWidth = mc.font.width(COLUMN_SPACER);
        this.title = currentObjective.getDisplayName();
        this.titleWidth = mc.font.width(this.title);
        int count = Math.min(entries.size(), MAX_ROWS);
        int biggest = this.titleWidth;
        for (int i = 0; i < count; i++) {
            PlayerScoreEntry entry = entries.get(i);
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            Component score = entry.formatValue(format);
            int scoreWidth = mc.font.width(score);
            names[i] = name;
            scores[i] = score;
            scoreWidths[i] = scoreWidth;
            biggest = Math.max(biggest, mc.font.width(name) + (scoreWidth > 0 ? spacerWidth + scoreWidth : 0));
        }
        this.rows = count;
        this.nameColumnWidth = biggest;
    }
}
