package com.aurora.client.hud.module;

import com.aurora.client.AuroraClient;
import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.HudTextPainter;
import com.aurora.client.hud.VanillaHudLayout;
import com.aurora.client.screen.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Draggable, scalable, hideable HUD-editor representation of one vanilla HUD
 * element ({@link VanillaHudLayout.Element}).
 *
 * <p>During gameplay this module draws nothing of its own — vanilla's own
 * renderers draw the real boss bar, action bar, titles, subtitles, toasts and
 * advancement pop-ups using this module's anchor, offset, scale and enabled
 * state (via {@code VanillaBossBarMixin}, {@code VanillaHudOverlayMixin} and
 * {@code VanillaToastMixin}). While {@link HudEditorScreen} is open, this
 * module renders a faithful vanilla-styled sample preview inside its AABB so
 * the user can position, resize or hide it interactively.
 */
public final class VanillaHudModule extends HudModule {
    public static final String FEATURE_ID = "vanilla_hud";

    private static final Identifier BOSS_BAR_BG = Identifier.withDefaultNamespace("boss_bar/purple_background");
    private static final Identifier BOSS_BAR_FILL = Identifier.withDefaultNamespace("boss_bar/purple_progress");
    private static final Identifier TOAST_SYSTEM = Identifier.withDefaultNamespace("toast/system");
    private static final Identifier TOAST_ADVANCEMENT = Identifier.withDefaultNamespace("toast/advancement");

    private static final Map<VanillaHudLayout.Element, VanillaHudModule> BY_ELEMENT =
            new EnumMap<>(VanillaHudLayout.Element.class);

    private final VanillaHudLayout.Element element;

    public VanillaHudModule(VanillaHudLayout.Element element) {
        super(element.id);
        this.element = element;
        resetLayoutToDefaults();
    }

    /** Registers all six vanilla HUD control modules with the shared manager. */
    public static void registerAll(HudModuleManager manager) {
        BY_ELEMENT.clear();
        for (VanillaHudLayout.Element element : VanillaHudLayout.Element.values()) {
            VanillaHudModule module = new VanillaHudModule(element);
            BY_ELEMENT.put(element, module);
            manager.register(module);
        }
    }

    /** Returns the registered module for {@code element}, or {@code null} before startup. */
    public static VanillaHudModule of(VanillaHudLayout.Element element) {
        VanillaHudModule cached = BY_ELEMENT.get(element);
        if (cached != null) return cached;
        HudModuleManager mgr = AuroraClient.modules();
        if (mgr != null && mgr.get(element.id) instanceof VanillaHudModule found) {
            BY_ELEMENT.put(element, found);
            return found;
        }
        return null;
    }

    /** True when the element's per-element switch in AuroraConfig is on. */
    public static boolean configShow(VanillaHudLayout.Element element) {
        AuroraConfig cfg = AuroraConfig.get();
        return switch (element) {
            case BOSS_BAR -> cfg.vanillaHudShowBossBar;
            case ACTION_BAR -> cfg.vanillaHudShowActionBar;
            case TITLE -> cfg.vanillaHudShowTitles;
            case SUBTITLE -> cfg.vanillaHudShowSubtitles;
            case TOASTS -> cfg.vanillaHudShowToasts;
            case ADVANCEMENTS -> cfg.vanillaHudShowAdvancements;
        };
    }

    /**
     * True when {@code element} should be drawn in game while Vanilla HUD
     * control is active: its config switch is on and the user has not hidden
     * it via right-click in the HUD editor.
     */
    public static boolean isVisible(VanillaHudLayout.Element element) {
        if (!configShow(element)) return false;
        VanillaHudModule module = of(element);
        return module == null || module.enabled;
    }

    public VanillaHudLayout.Element element() { return element; }

    @Override public String featureRegistryId() { return FEATURE_ID; }
    @Override public String displayName() { return element.displayName; }
    @Override public int getWidth() { return element.width; }
    @Override public int getHeight() { return element.height; }

    @Override
    public boolean isConfigEnabled() {
        return AuroraConfig.get().vanillaHudEnabled && configShow(element);
    }

    @Override
    public void resetLayoutToDefaults() {
        if (restoreReleaseLayout()) return;
        this.anchor = element.defaultAnchor;
        this.offsetX = element.defaultOffsetX;
        this.offsetY = element.defaultOffsetY;
        this.scale = 1.0f;
        this.enabled = true;
        this.locked = false;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, Minecraft client, int screenW, int screenH) {
        // Gameplay rendering is performed by vanilla's own overlays (transformed/hidden
        // by our mixins). Only paint preview placeholders while the HUD editor is open.
        if (client == null || !(NativeUi.screen(client) instanceof HudEditorScreen)) return;
        super.render(ctx, client, screenW, screenH);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor ctx, Minecraft client, int x, int y) {
        Font font = client.font;
        if (font == null) return;
        switch (element) {
            case BOSS_BAR -> renderBossBarPreview(ctx, font, x, y);
            case ACTION_BAR -> renderActionBarPreview(ctx, font, x, y);
            case TITLE -> renderScaledTextPreview(ctx, font, "Title", x, y, element.width, element.height, 4.0f);
            case SUBTITLE -> renderScaledTextPreview(ctx, font, "Subtitle", x, y, element.width, element.height, 2.0f);
            case TOASTS -> renderToastPreview(ctx, font, x, y, TOAST_SYSTEM, "Toast Notification", "System message", 0xFFFFFF00);
            case ADVANCEMENTS -> renderToastPreview(ctx, font, x, y, TOAST_ADVANCEMENT, "Advancement Made!", "Getting an Upgrade", 0xFFFFFF00);
        }
    }

    private static void renderBossBarPreview(GuiGraphicsExtractor ctx, Font font, int x, int y) {
        String title = "Ender Dragon";
        int titleW = font.width(title);
        HudTextPainter.draw(ctx, font, title, x + (182 - titleW) / 2, y, 0xFFFFFFFF);
        int barY = y + 9;
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, BOSS_BAR_BG, 182, 5, 0, 0, x, barY, 182, 5);
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, BOSS_BAR_FILL, 182, 5, 0, 0, x, barY, 136, 5);
    }

    private static void renderActionBarPreview(GuiGraphicsExtractor ctx, Font font, int x, int y) {
        String text = "Action Bar Message";
        int textW = font.width(text);
        int tx = x + (140 - textW) / 2;
        int ty = y + (12 - font.lineHeight) / 2;
        ctx.fill(tx - 3, ty - 2, tx + textW + 3, ty + font.lineHeight + 1, 0x66000000);
        HudTextPainter.draw(ctx, font, text, tx, ty, 0xFFFFFFFF);
    }

    private static void renderScaledTextPreview(GuiGraphicsExtractor ctx, Font font, String text,
                                                int x, int y, int w, int h, float textScale) {
        int textW = font.width(text);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + w * 0.5f, y + h * 0.5f);
        ctx.pose().scale(textScale, textScale);
        HudTextPainter.draw(ctx, font, text, -textW / 2, -4, 0xFFFFFFFF);
        ctx.pose().popMatrix();
    }

    private static void renderToastPreview(GuiGraphicsExtractor ctx, Font font, int x, int y,
                                           Identifier sprite, String heading, String body, int headingColor) {
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 160, 32);
        boolean advancement = sprite == TOAST_ADVANCEMENT;
        int textX = advancement ? x + 30 : x + 18;
        HudTextPainter.draw(ctx, font, heading, textX, y + 7, headingColor);
        HudTextPainter.draw(ctx, font, body, textX, advancement ? y + 18 : y + 19, 0xFFFFFFFF);
    }
}
