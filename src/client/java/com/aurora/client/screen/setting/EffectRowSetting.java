package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.ToggleSwitch;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * One row in the per-effect expiry-alert list (Alerts → Per-Effect
 * Alerts): the effect's vanilla status-effect sprite, its localized
 * display name, and a shared {@link ToggleSwitch} that opts the effect
 * in or out of the effect-expiry alert.
 *
 * <p>The toggle reads the config's exclusion set — on (default) means the
 * effect alerts, off means it is excluded — so the factory state (empty
 * set) matches the pre-per-effect behavior where every effect alerted.
 *
 * <p>Design language: no §4 subtitle (the toggle position IS the state,
 * per the exclusion clause) and no description tooltip (the row is
 * self-explanatory). Row chrome follows the {@link BooleanSetting}
 * geometry — same 28px minimum height, same trailing-switch placement —
 * with the label indented past the 18×18 icon the way ItemScale rows
 * indent past their real item icons.
 */
public class EffectRowSetting extends FeatureSetting {

    private static final int MIN_ROW_H = 28;
    private static final int ICON = 18;
    /** Icon slot + gap before the label, from the list's left margin. */
    private static final int LABEL_PAD = 12 + ICON + 8;

    private final String effectId;
    private final Identifier spriteLoc;
    /** Package-private so the container can match raw registry ids in search. */
    String effectId() { return effectId; }

    /** Shared themed switch — owns the pill/thumb drawing + slide animation. */
    private final ToggleSwitch toggle;

    public EffectRowSetting(String effectId, Identifier effectLoc, String displayName) {
        super(displayName);
        this.effectId = effectId;
        this.spriteLoc = Identifier.fromNamespaceAndPath(
                effectLoc.getNamespace(), "mob_effect/" + effectLoc.getPath());
        this.toggle = new ToggleSwitch(
                () -> !AuroraConfig.get().effectExpiryExcludedEffects.contains(effectId),
                v -> {
                    var set = AuroraConfig.get().effectExpiryExcludedEffects;
                    if (v) set.remove(effectId);
                    else set.add(effectId);
                });
    }

    @Override public int baseHeight() { return MIN_ROW_H; }
    @Override public int height() { return MIN_ROW_H; }

    @Override
    public int shapeFingerprint() {
        return toggle.shapeFingerprint();
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        if (tr == null) return;

        // Effect sprite — the same 18×18 status-effect icon the vanilla
        // inventory effect list draws (PotionModule's icon path).
        ctx.blitSprite(RenderPipelines.GUI_TEXTURED, spriteLoc, x + 12, y + (MIN_ROW_H - ICON) / 2, ICON, ICON);

        // Display name, indented past the icon (ItemScale row geometry).
        int textY = y + (MIN_ROW_H - tr.lineHeight) / 2;
        ctx.drawString(tr, label, x + LABEL_PAD, textY, AuroraTheme.IOS_LABEL, false);

        // Trailing toggle — identical placement to BooleanSetting.
        float switchX = x + width - 28 - 14;
        float switchY = y + (MIN_ROW_H - 15) / 2.0f;
        toggle.layout(switchX, switchY, 28, 15);
        toggle.renderShapes(ctx, switchX, switchY, 28, 15);
        toggle.renderOverlay(ctx, switchX, switchY, 28, 15, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int rowX, int rowY, int rowWidth) {
        if (button != 0) return false;
        if (mouseY < rowY || mouseY > rowY + MIN_ROW_H) return false;
        if (mouseX < rowX || mouseX > rowX + rowWidth) return false;
        // Click anywhere on the row toggles — the same full-row target
        // BooleanSetting gives its label + switch.
        toggle.toggle();
        AuroraConfig.save();
        return true;
    }
}
