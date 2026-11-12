package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.FeatureDetailScreen;
import com.aurora.client.screen.FeatureMetadata;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.HoverAnim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One particle in the Particles list, drawn as a card (owner redesign
 * 2026-11-12): the particle's name over a one-line summary of what is
 * changed, an On/Off label with the visibility switch, and a chevron. The
 * switch shows or hides the particle in place; anywhere else on the card
 * opens the particle's own detail screen with its Visible, Scale and Color
 * Overlay rows and a Reset that clears only this particle.
 *
 * <p>Two semantic controls per card, in visual order: the card's navigation
 * action (Enter/Space open the detail screen) and the switch (Enter/Space
 * toggle it). Each owns its own pointer zone; the switch zone wins.
 */
public class ParticleRowSetting extends FeatureSetting {
    /** Card height and the inset that aligns cards with the search field above them. */
    private static final int CARD_H = 34, INSET = 12;
    /** Right-edge slot for the chevron, and the gap before the switch. */
    private static final int CHEV_SLOT = 18, GAP = 6;
    /** Material expand_more, rotated a quarter turn to point right. */
    private static final String CHEVRON = com.aurora.client.screen.FeatureIcons.get("_dropdown_expand_more");
    private static final long HOVER_MS = 140L;

    private final String particleId;
    /** Package-private so {@link ParticleConfigSetting} can match against
     *  the raw registry id (e.g. {@code "minecraft:flame"}) in addition to
     *  the humanized display label. */
    String particleId() { return particleId; }

    private final BooleanSetting visibleRow;
    private final SliderSetting scaleRow;
    private final ColorSetting colorRow;
    private final ToggleSwitch toggle;
    private final SemanticActionControl openControl, toggleControl;
    private final HoverAnim hover = HoverAnim.symmetric(HOVER_MS);
    private FeatureMetadata detail;

    /** Memoized summary line, rebuilt only when the particle's settings or the available width change. */
    private long summaryKey = Long.MIN_VALUE;
    private String summary = "";
    private String fittedTitle = "", fittedSummary = "";
    private int fittedWidth = -1;
    private long fittedFont = Long.MIN_VALUE;

    public ParticleRowSetting(String particleId, String displayName) {
        super(displayName);
        this.particleId = particleId;
        this.visibleRow = new BooleanSetting("Visible", this::visible, this::setVisible);
        visibleRow.description("Shows or hides the " + displayName + " particle.");
        this.scaleRow = SliderSetting.of("Scale",
                () -> {
                    Float s = AuroraConfig.get().particleScale.get(particleId);
                    return s == null ? 1.0 : s.doubleValue();
                },
                v -> AuroraConfig.get().particleScale.put(particleId, (float) v),
                0.0, 1.5).percent();
        scaleRow.description("The " + displayName + " particle's size. 100% is vanilla.");
        ColorSetting color = new ColorSetting("Color Overlay",
                () -> {
                    Integer c = AuroraConfig.get().particleColor.get(particleId);
                    return c == null ? 0 : c;
                },
                v -> {
                    // Treat alpha==0 (fully transparent) as "no tint" and
                    // remove the entry so the config stays clean and the
                    // mixin's fast-path (`color == 0`) skips the particle.
                    if ((v >>> 24) == 0) {
                        AuroraConfig.get().particleColor.remove(particleId);
                    } else {
                        AuroraConfig.get().particleColor.put(particleId, v);
                    }
                });
        color.description("Tints this particle with the chosen color. The alpha channel controls tint strength — lower alpha gives a subtle wash, full alpha recolors it completely. Set alpha to 0 to clear the tint and use vanilla colors.");
        this.colorRow = color;

        this.toggle = new ToggleSwitch(this::visible, this::setVisible);
        this.openControl = new SemanticActionControl(SemanticAction.button(
                Component.literal(displayName),
                () -> Component.literal("Opens the " + displayName + " particle's settings."),
                () -> Component.literal(summaryText()),
                () -> true,
                this::openDetail),
                MinecraftSemanticFeedback.INSTANCE,
                null, // no press animation — the screen transition is the feedback
                SemanticActionControl.PointerRouting.MANUAL);
        this.toggleControl = new SemanticActionControl(SemanticAction.button(
                Component.literal(displayName + " visible"),
                () -> Component.literal("Shows or hides the " + displayName + " particle."),
                () -> Component.literal(visible() ? "On" : "Off"),
                () -> true,
                () -> {
                    toggle.toggle();
                    AuroraConfig.save();
                }),
                MinecraftSemanticFeedback.INSTANCE,
                null,
                SemanticActionControl.PointerRouting.MANUAL);
    }

    private boolean visible() {
        Boolean v = AuroraConfig.get().particleVisibility.get(particleId);
        return v == null || v;
    }

    private void setVisible(boolean v) {
        AuroraConfig.get().particleVisibility.put(particleId, v);
    }

    /** The particle's own detail screen: Visible, Scale and Color Overlay, reset as one. */
    private FeatureMetadata detail() {
        if (detail == null) {
            List<FeatureSetting> rows = new ArrayList<>(List.of(visibleRow, scaleRow, colorRow));
            detail = new FeatureMetadata("particle." + particleId, label,
                    "Visibility, size and tint of the " + label + " particle.",
                    () -> true, v -> {}, rows)
                    .subtitle("Particles")
                    .resetWith(() -> {
                        AuroraConfig cfg = AuroraConfig.get();
                        cfg.particleVisibility.remove(particleId);
                        cfg.particleScale.remove(particleId);
                        cfg.particleColor.remove(particleId);
                    });
        }
        return detail;
    }

    private void openDetail() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        FeatureSetting.clearFocus();
        com.aurora.client.adapter.NativeUi.setScreen(mc,
                new FeatureDetailScreen(com.aurora.client.adapter.NativeUi.screen(mc), detail()));
    }

    /** "Hidden", or what differs from vanilla ("Size 75% · Tinted"), or "Vanilla look". */
    private String summaryText() {
        AuroraConfig cfg = AuroraConfig.get();
        boolean on = visible();
        Float s = cfg.particleScale.get(particleId);
        int pct = Math.round((s == null ? 1f : s) * 100f);
        Integer c = cfg.particleColor.get(particleId);
        int tint = c == null ? 0 : c;
        long key = (on ? 1L : 0L) | ((long) (pct & 0xFFF) << 1) | ((long) tint << 13);
        if (key != summaryKey) {
            summaryKey = key;
            if (!on) summary = "Hidden";
            else if (pct == 100 && tint == 0) summary = "Vanilla look";
            else if (tint == 0) summary = "Size " + pct + "%";
            else if (pct == 100) summary = "Tinted";
            else summary = "Size " + pct + "% · Tinted";
            fittedWidth = -1;
        }
        return summary;
    }

    @Override public int baseHeight() { return CARD_H; }
    @Override public int height() { return CARD_H; }

    @Override
    public List<SemanticActionControl> interactionControls() {
        return List.of(openControl, toggleControl);
    }

    @Override
    public void onInteractionAvailabilityChanged(boolean available) {
        if (!available) toggle.focusedVisual(false);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        float cx = x + INSET, cw = width - 2f * INSET, right = cx + cw;
        float radius = ThemeManager.current().roundness().radiusSmall();

        // Right cluster, right to left: chevron, switch, On/Off label.
        float tw = UiDensity.TOGGLE_W, th = UiDensity.TOGGLE_H;
        float tx = right - CHEV_SLOT - tw, ty = y + (CARD_H - th) / 2f;
        String state = visible() ? "On" : "Off";
        int stateX = Math.round(tx - GAP - tr.width(state));
        int zoneX = stateX - 4, zoneR = Math.round(tx + tw + 4);

        boolean overCard = mouseX >= cx && mouseX < right && mouseY >= y && mouseY < y + CARD_H;
        float hoverT = hover.update(overCard);
        MaterialSurface.selection(ctx, cx, y, cw, CARD_H, radius, false, hoverT);
        // Disjoint zones: the open action owns the card left of the switch
        // zone (the chevron still opens through mouseClicked), so a click on
        // the switch never hands focus to the card.
        openControl.setBounds((int) cx, y, Math.max(1, zoneX - (int) cx), CARD_H);
        openControl.updatePointer(mouseX, mouseY);
        toggleControl.setBounds(zoneX, y, zoneR - zoneX, CARD_H);
        toggleControl.updatePointer(mouseX, mouseY);
        if (openControl.isFocused()) {
            RenderUtil.drawRoundedOutlineAA(ctx, cx, y, cw, CARD_H, radius, 1.0f,
                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.ACCENT), 0x99));
        }

        // Title over summary, each fitted once per width/font/state change.
        int textX = Math.round(cx) + 10, textW = Math.max(1, zoneX - 8 - textX);
        String line = summaryText();
        long font = ThemeManager.generation();
        if (fittedWidth != textW || fittedFont != font) {
            fittedWidth = textW;
            fittedFont = font;
            fittedTitle = AuroraFontRenderer.ellipsize(tr, label, textW, 1);
            fittedSummary = AuroraFontRenderer.ellipsize(tr, line, textW, 1);
        }
        ctx.text(tr, fittedTitle, textX, y + 7, MaterialSurface.color(ThemeToken.ON_BACKGROUND), false);
        ctx.text(tr, fittedSummary, textX, y + 19, MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED), false);

        ctx.text(tr, state, stateX, Math.round(y + (CARD_H - tr.lineHeight) / 2f + 1),
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), false);
        toggle.layout(tx, ty, tw, th);
        toggle.renderShapes(ctx, tx, ty, tw, th);
        toggle.renderOverlay(ctx, tx, ty, tw, th, mouseX, mouseY);
        toggle.focusedVisual(toggleControl.isFocused());

        // Chevron: expand_more turned a quarter counter-clockwise to point right.
        int chev = com.aurora.client.util.AuroraAnim.lerpArgb(
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_MUTED),
                MaterialSurface.color(ThemeToken.ON_BACKGROUND_SECONDARY), hoverT);
        float chevX = right - CHEV_SLOT / 2f, chevY = y + CARD_H / 2f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(chevX, chevY);
        ctx.pose().rotate((float) (-Math.PI / 2));
        MaterialIconRenderer.drawIcon(ctx, tr, CHEVRON, 0, 0, 12, chev);
        ctx.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        float cx = rowX + INSET, right = rowX + rowWidth - INSET;
        if (mouseX < cx || mouseX >= right || mouseY < rowY || mouseY >= rowY + CARD_H) return false;
        // The switch zone (its label through the switch) toggles; the rest of the card opens.
        if (toggleControl.getWidth() > 0 && mouseX >= toggleControl.getX()
                && mouseX < toggleControl.getX() + toggleControl.getWidth()) {
            if (!toggleControl.activateFromPointer(mouseX, mouseY, button)) {
                toggle.toggle();
                AuroraConfig.save();
            }
            return true;
        }
        if (!openControl.activateFromPointer(mouseX, mouseY, button)) openDetail();
        return true;
    }
}
