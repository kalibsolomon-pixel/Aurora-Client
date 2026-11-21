package com.aurora.client.hud;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.hud.module.VanillaHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.Profiler;

/**
 * Shared render/transform helper invoked by the Vanilla HUD control mixins
 * ({@code VanillaBossBarMixin}, {@code VanillaHudOverlayMixin},
 * {@code VanillaToastMixin}) across Minecraft 26.1.2, 26.2 and 26.3.
 */
public final class VanillaHudOverlayHelper {
    private VanillaHudOverlayHelper() {}

    /**
     * Pushes a pose transform for {@code BossHealthOverlay.extractRenderState}
     * when the boss bar was moved or scaled. Returns true iff a matrix was pushed
     * and the caller must pop it on exit.
     */
    public static boolean beginBossBar(GuiGraphicsExtractor graphics) {
        VanillaHudModule module = VanillaHudModule.of(VanillaHudLayout.Element.BOSS_BAR);
        if (module == null) return false;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float vanillaX = screenW * 0.5f - 91.0f;
        float vanillaY = 3.0f;
        VanillaHudLayout.Transform t = VanillaHudLayout.topLeft(
                module.anchor, module.offsetX, module.offsetY, module.scale,
                module.getWidth(), module.getHeight(), screenW, screenH);
        if (t.isIdentity(vanillaX, vanillaY)) return false;
        graphics.pose().pushMatrix();
        graphics.pose().translate(t.x(), t.y());
        graphics.pose().scale(t.scale(), t.scale());
        graphics.pose().translate(-vanillaX, -vanillaY);
        return true;
    }

    /**
     * Pushes a pose transform for {@code extractOverlayMessage} (action bar)
     * when moved or scaled. Returns true iff a matrix was pushed and must be popped.
     */
    public static boolean beginActionBar(GuiGraphicsExtractor graphics) {
        VanillaHudModule module = VanillaHudModule.of(VanillaHudLayout.Element.ACTION_BAR);
        if (module == null) return false;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        float vanillaX = screenW * 0.5f;
        float vanillaY = screenH - 68.0f;
        VanillaHudLayout.Transform t = VanillaHudLayout.center(
                module.anchor, module.offsetX, module.offsetY, module.scale,
                module.getWidth(), module.getHeight(), screenW, screenH);
        if (t.isIdentity(vanillaX, vanillaY)) return false;
        graphics.pose().pushMatrix();
        graphics.pose().translate(t.x(), t.y());
        graphics.pose().scale(t.scale(), t.scale());
        graphics.pose().translate(-vanillaX, -vanillaY);
        return true;
    }

    /**
     * Handles {@code extractTitle} when {@link AuroraConfig#vanillaHudEnabled}
     * is on. Returns true if Aurora handled (or hid) title &amp; subtitle rendering
     * and vanilla's method should be cancelled; returns false when both elements
     * are visible at their exact vanilla placement and scale.
     */
    public static boolean handleTitleAndSubtitle(GuiGraphicsExtractor graphics, Font font,
                                                 Component title, Component subtitle,
                                                 int titleTime, int fadeInTime, int stayTime, int fadeOutTime,
                                                 float partialTick) {
        if (!AuroraConfig.get().vanillaHudEnabled) return false;
        if (title == null || titleTime <= 0) return false;

        boolean showTitle = VanillaHudModule.isVisible(VanillaHudLayout.Element.TITLE);
        boolean showSubtitle = subtitle != null && VanillaHudModule.isVisible(VanillaHudLayout.Element.SUBTITLE);
        if (!showTitle && !showSubtitle) return true;

        VanillaHudModule titleMod = VanillaHudModule.of(VanillaHudLayout.Element.TITLE);
        VanillaHudModule subMod = VanillaHudModule.of(VanillaHudLayout.Element.SUBTITLE);
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        VanillaHudLayout.Transform titleT = titleMod != null
                ? VanillaHudLayout.center(titleMod.anchor, titleMod.offsetX, titleMod.offsetY, titleMod.scale,
                        titleMod.getWidth(), titleMod.getHeight(), screenW, screenH)
                : new VanillaHudLayout.Transform(screenW * 0.5f, screenH * 0.5f - 22.0f, 1.0f);
        VanillaHudLayout.Transform subT = subMod != null
                ? VanillaHudLayout.center(subMod.anchor, subMod.offsetX, subMod.offsetY, subMod.scale,
                        subMod.getWidth(), subMod.getHeight(), screenW, screenH)
                : new VanillaHudLayout.Transform(screenW * 0.5f, screenH * 0.5f + 19.0f, 1.0f);

        boolean titleVanilla = showTitle && titleT.isIdentity(screenW * 0.5f, screenH * 0.5f - 22.0f);
        boolean subVanilla = (subtitle == null || (showSubtitle && subT.isIdentity(screenW * 0.5f, screenH * 0.5f + 19.0f)));
        if (titleVanilla && subVanilla) return false;

        Profiler.get().push("titleAndSubtitle");
        int alpha = VanillaHudLayout.titleAlpha(titleTime, fadeInTime, stayTime, fadeOutTime, partialTick);
        if (alpha > 0) {
            graphics.nextStratum();
            int textColor = ARGB.white(alpha);
            if (showTitle) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(titleT.x(), titleT.y());
                float s = 4.0F * titleT.scale();
                graphics.pose().scale(s, s);
                graphics.pose().translate(0.0F, -4.5F);
                int titleWidth = font.width(title);
                graphics.textWithBackdrop(font, title, -titleWidth / 2, 0, titleWidth, textColor);
                graphics.pose().popMatrix();
            }
            if (showSubtitle) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(subT.x(), subT.y());
                float s = 2.0F * subT.scale();
                graphics.pose().scale(s, s);
                graphics.pose().translate(0.0F, -4.5F);
                int subWidth = font.width(subtitle);
                graphics.textWithBackdrop(font, subtitle, -subWidth / 2, 0, subWidth, textColor);
                graphics.pose().popMatrix();
            }
        }
        Profiler.get().pop();
        return true;
    }

    /**
     * Handles {@code ToastManager.ToastInstance.extractRenderState} when
     * {@link AuroraConfig#vanillaHudEnabled} is on. Returns true if Aurora
     * handled (or hid) the toast and vanilla's draw should be cancelled.
     */
    public static boolean handleToast(GuiGraphicsExtractor graphics, int screenWidth,
                                      Toast toast, int firstSlotIndex,
                                      float visiblePortion, long fullyVisibleFor) {
        if (!AuroraConfig.get().vanillaHudEnabled) return false;
        VanillaHudLayout.Element element = (toast instanceof AdvancementToast)
                ? VanillaHudLayout.Element.ADVANCEMENTS
                : VanillaHudLayout.Element.TOASTS;
        if (!VanillaHudModule.isVisible(element)) return true;

        VanillaHudModule module = VanillaHudModule.of(element);
        if (module == null) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return false;

        int screenH = graphics.guiHeight();
        VanillaHudLayout.Transform t = VanillaHudLayout.toast(
                module.anchor, module.offsetX, module.offsetY, module.scale,
                screenWidth, screenH, toast.width(), toast.height(), firstSlotIndex, visiblePortion);
        graphics.pose().pushMatrix();
        graphics.pose().translate(t.x(), t.y());
        if (Math.abs(t.scale() - 1.0f) >= 0.001f) {
            graphics.pose().scale(t.scale(), t.scale());
        }
        toast.extractRenderState(graphics, mc.font, fullyVisibleFor);
        graphics.pose().popMatrix();
        return true;
    }
}
