package com.aurora.client.hud.module;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudAnchor;
import com.aurora.client.hud.HudTextPainter;
import com.aurora.client.media.MediaCoverTexture;
import com.aurora.client.media.MediaTrackState;
import com.aurora.client.media.SystemMediaBridge;
import com.aurora.client.theme.HudText;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Now Playing HUD module (Windows-first via GSMTC).
 *
 * <p>Shows playback state icon, track title, optional artist line, and optional
 * progress bar. When idle and not in the HUD Editor, renders nothing (0 fills).
 * In the HUD Editor, displays {@link MediaTrackState#SAMPLE} so users can position
 * and size the overlay even when no player is open.
 */
public class NowPlayingModule extends HudModule {
    public static final String ID = "now_playing";

    private static final int ICON_COL = 18;
    private static final int PAD_X = 4;
    /** Cover art: inset from the module's edge, and the gap to the text column. */
    private static final int ART_PAD = 2;
    private static final int ART_GAP = 5;
    private static final String ICON_PLAY = "\uE037";
    private static final String ICON_PAUSE = "\uE034";
    private static final String ICON_MUSIC = "\uE405";

    private String cachedTitleIn;
    private String cachedTitleOut;
    private String cachedSubIn;
    private String cachedSubOut;
    private int cachedTextMax = -1;
    private Font cachedFont;
    private int cachedStyleKey = Integer.MIN_VALUE;

    public NowPlayingModule() {
        super(ID);
        this.anchor = HudAnchor.TOP_CENTER;
        this.offsetX = 0;
        this.offsetY = 6;
    }

    @Override public String featureRegistryId() { return "media_controls"; }
    @Override protected AuroraConfig.HudBackground backgroundMode() { return AuroraConfig.get().mediaHudBgMode; }
    @Override protected int backgroundColor() { return AuroraConfig.get().mediaHudBgColor; }

    @Override
    public boolean isConfigEnabled() {
        AuroraConfig cfg = AuroraConfig.get();
        return cfg.mediaControlsEnabled && cfg.mediaNowPlayingHudEnabled;
    }

    private MediaTrackState activeOrSample(boolean editing) {
        MediaTrackState live = SystemMediaBridge.currentTrack();
        if (live.active()) return live;
        if (editing) return MediaTrackState.SAMPLE;
        return MediaTrackState.EMPTY;
    }

    @Override
    public int getWidth() {
        AuroraConfig cfg = AuroraConfig.get();
        return Math.max(120, Math.min(280, cfg.mediaHudMaxWidth));
    }

    @Override
    public int getHeight() {
        AuroraConfig cfg = AuroraConfig.get();
        int h = 11; // Title line
        if (cfg.mediaHudShowArtist) h += 10;
        if (cfg.mediaHudShowProgress) h += 6;
        return Math.max(18, h + 2);
    }

    @Override
    protected float backgroundAlpha() {
        AuroraConfig cfg = AuroraConfig.get();
        Minecraft client = Minecraft.getInstance();
        boolean editing = client != null && com.aurora.client.adapter.NativeUi.screen(client) instanceof com.aurora.client.screen.HudEditorScreen;
        if (!editing && !SystemMediaBridge.currentTrack().active() && cfg.mediaHudHideWhenIdle) {
            return 0f;
        }
        return 1f;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, int screenW, int screenH) {
        if (!enabled || !isConfigEnabled()) return;
        boolean editing = client != null && com.aurora.client.adapter.NativeUi.screen(client) instanceof com.aurora.client.screen.HudEditorScreen;
        if (!editing && !SystemMediaBridge.currentTrack().active() && AuroraConfig.get().mediaHudHideWhenIdle) {
            return;
        }
        super.render(ctx, client, screenW, screenH);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        boolean editing = client != null && com.aurora.client.adapter.NativeUi.screen(client) instanceof com.aurora.client.screen.HudEditorScreen;
        drawTrack(ctx, client, x, y, editing);
    }

    private String fitCached(Font tr, String raw, int maxW, boolean isTitle) {
        int sk = AuroraFontRenderer.styleKey();
        if (tr != cachedFont || maxW != cachedTextMax || sk != cachedStyleKey) {
            cachedFont = tr;
            cachedTextMax = maxW;
            cachedStyleKey = sk;
            cachedTitleIn = null;
            cachedSubIn = null;
        }
        if (isTitle) {
            if (raw.equals(cachedTitleIn)) return cachedTitleOut;
            cachedTitleIn = raw;
            return cachedTitleOut = AuroraFontRenderer.ellipsize(tr, raw, maxW, 0);
        }
        if (raw.equals(cachedSubIn)) return cachedSubOut;
        cachedSubIn = raw;
        return cachedSubOut = AuroraFontRenderer.ellipsize(tr, raw, maxW, 0);
    }

    private void drawTrack(GuiGraphicsExtractor ctx, Minecraft client, int x, int y, boolean editing) {
        AuroraConfig cfg = AuroraConfig.get();
        if (!editing && (!cfg.mediaControlsEnabled || !cfg.mediaNowPlayingHudEnabled)) return;
        Font tr = client.font;
        if (tr == null) return;

        MediaTrackState state = activeOrSample(editing);
        if (!state.active()) {
            if (cfg.mediaHudHideWhenIdle) return;
            // Subtle idle indicator only when user explicitly disabled hideWhenIdle.
            int color = cfg.mediaHudTextColor == 0 ? 0xFFFFFFFF : cfg.mediaHudTextColor;
            MaterialIconRenderer.drawIcon(ctx, tr, ICON_MUSIC, x + 8f, y + getHeight() / 2f, 11f,
                    ThemeManager.withAlpha(color, 185));
            HudTextPainter.draw(ctx, tr, AuroraFontRenderer.ellipsize(tr, "No media playing", getWidth() - ICON_COL - PAD_X, 0),
                    x + ICON_COL, y + (getHeight() - tr.lineHeight) / 2, ThemeManager.withAlpha(color, 185));
            return;
        }

        int w = getWidth();
        int h = getHeight();
        int primaryColor = cfg.mediaHudTextColor == 0 ? 0xFFFFFFFF : cfg.mediaHudTextColor;
        int secondaryColor = ThemeManager.withAlpha(primaryColor, 185);
        int accent = ThemeManager.color(ThemeToken.ACCENT);

        int textX;
        if (cfg.mediaHudShowArt) {
            // Cover square, full height: the cover dims with a pause glyph while paused;
            // with no cover (or the editor's sample) a quiet placeholder keeps the layout.
            int s = h - 2 * ART_PAD;
            int ax = x + ART_PAD, ay = y + ART_PAD;
            float glyphSize = Math.min(13f, s * 0.6f);
            Identifier cover = MediaCoverTexture.forTrack(state);
            if (cover != null) {
                int size = SystemMediaBridge.COVER_SIZE;
                ctx.blit(RenderPipelines.GUI_TEXTURED, cover, ax, ay, 0f, 0f, s, s, size, size, size, size,
                        state.playing() ? 0xFFFFFFFF : 0xFF8C8C8C);
                if (!state.playing()) {
                    MaterialIconRenderer.drawIcon(ctx, tr, ICON_PAUSE, ax + s / 2f, ay + s / 2f, glyphSize, 0xFFFFFFFF);
                }
            } else {
                ctx.fill(ax, ay, ax + s, ay + s, ThemeManager.withAlpha(primaryColor, 36));
                MaterialIconRenderer.drawIcon(ctx, tr, state.playing() ? ICON_MUSIC : ICON_PAUSE,
                        ax + s / 2f, ay + s / 2f, glyphSize, secondaryColor);
            }
            textX = ax + s + ART_GAP;
        } else {
            int textBlockH = cfg.mediaHudShowArtist ? 21 : 11;
            String glyph = state.playing() ? ICON_PLAY : ICON_PAUSE;
            MaterialIconRenderer.drawIcon(ctx, tr, glyph,
                    x + 8f, y + textBlockH / 2f + 1f, 13f, accent);
            textX = x + ICON_COL;
        }
        int textMax = Math.max(24, x + w - PAD_X - textX);

        String rawTitle = state.title().isEmpty() ? "Unknown Track" : state.title();
        HudTextPainter.draw(ctx, tr, fitCached(tr, rawTitle, textMax, true), textX, y + 1, primaryColor);

        int curY = y + 12;
        if (cfg.mediaHudShowArtist) {
            String sub = state.artist().isEmpty() ? (state.playing() ? "Playing" : "Paused") : state.artist();
            HudTextPainter.draw(ctx, tr, fitCached(tr, sub, textMax, false), textX, curY, secondaryColor);
            curY += 10;
        }

        if (cfg.mediaHudShowProgress) {
            long now = System.currentTimeMillis();
            float prog = state.progress(now);
            // Under the text column when the cover takes the left edge.
            int barX = cfg.mediaHudShowArt ? textX : x + 2;
            int barW = Math.max(20, x + w - 2 - barX);
            int barY = Math.min(y + h - 3, curY + 2);
            int trackBg = ThemeManager.withAlpha(primaryColor, 48);
            ctx.fill(barX, barY, barX + barW, barY + 2, trackBg);
            if (prog > 0f) {
                int fillW = Math.max(2, Math.round(barW * prog));
                ctx.fill(barX, barY, barX + fillW, barY + 2, accent);
            }
        }
    }
}
