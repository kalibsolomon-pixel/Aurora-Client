package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Collapsible row for the per-particle controls list.
 *
 * <p>Header (always visible, {@value #HEADER_H} px tall): particle display
 * name on the left, "On 100%" / "Off" state summary on the right, plus a
 * chevron indicating expand/collapse state. Click anywhere on the header
 * to toggle.
 *
 * <p>Body (only when expanded): an embedded {@link BooleanSetting} for
 * visibility, an embedded {@link DoubleSliderSetting} (0–1.5,
 * percent-formatted) for scale, and an embedded {@link ColorSetting}
 * for the ARGB color overlay tint. Mouse / drag / release events are
 * forwarded to the embedded settings at translated row offsets. Scroll
 * and key events route through the global {@link FeatureSetting#getFocused()}
 * mechanism so the screen sends them directly to the focused embedded
 * slider — no forwarding needed here.
 */
public class ParticleRowSetting extends FeatureSetting {
    private static final int HEADER_H = 30;

    private final String particleId;
    /** Package-private so {@link ParticleConfigSetting} can match against
     *  the raw registry id (e.g. {@code "minecraft:flame"}) in addition to
     *  the humanized display label. */
    String particleId() { return particleId; }
    private final BooleanSetting toggleRow;
    private final DoubleSliderSetting scaleRow;
    private final ColorSetting colorRow;
    private boolean expanded = false;

    public ParticleRowSetting(String particleId, String displayName) {
        super(displayName);
        this.particleId = particleId;
        this.toggleRow = new BooleanSetting("Visible",
                () -> {
                    Boolean v = AuroraConfig.get().particleVisibility.get(particleId);
                    return v == null || v;
                },
                v -> {
                    AuroraConfig.get().particleVisibility.put(particleId, v);
                });
        this.scaleRow = new DoubleSliderSetting("Scale",
                () -> {
                    Float s = AuroraConfig.get().particleScale.get(particleId);
                    return s == null ? 1.0 : s.doubleValue();
                },
                v -> AuroraConfig.get().particleScale.put(particleId, (float) v),
                0.0, 1.5).percent();
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
    }

    @Override
    public int baseHeight() {
        if (!expanded) return HEADER_H;
        // Sum the embedded rows so the container grows when the color
        // picker expands. Each sub-row reports its own live height.
        return HEADER_H + toggleRow.height() + scaleRow.height() + colorRow.height();
    }

    /** No description on these rows — height equals base height. */
    @Override
    public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        int textY = y + (HEADER_H - tr.lineHeight) / 2;

        // Display name on the left.
        ctx.drawString(tr, label, x + 12, textY, AuroraTheme.IOS_LABEL, false);

        // State summary + chevron on the right.
        Boolean v = AuroraConfig.get().particleVisibility.get(particleId);
        boolean on = v == null || v;
        Float s = AuroraConfig.get().particleScale.get(particleId);
        float scale = s == null ? 1.0f : s;
        String summary = on ? Math.round(scale * 100f) + "%" : "Off";
        String chev = expanded ? "v" : ">";
        int chevW = tr.width(chev);
        int sumW = tr.width(summary);
        int rightEdge = x + width - 12;
        ctx.drawString(tr, chev, rightEdge - chevW, textY, AuroraTheme.IOS_SECONDARY_LABEL, false);
        ctx.drawString(tr, summary, rightEdge - chevW - 8 - sumW, textY,
                on ? AuroraTheme.IOS_SECONDARY_LABEL : AuroraTheme.IOS_TERTIARY_LABEL, false);

        // Color swatch indicator between summary and chevron, so the header
        // hints at the configured tint even while collapsed.
        Integer c = AuroraConfig.get().particleColor.get(particleId);
        if (c != null && c != 0) {
            int swatchX = rightEdge - chevW - 8 - sumW - 8 - 10;
            int swatchY = textY + (tr.lineHeight - 8) / 2;
            ctx.fill(swatchX, swatchY, swatchX + 10, swatchY + 8, c);
        }

        if (expanded) {
            int subY = y + HEADER_H;
            toggleRow.render(ctx, x, subY, width, mouseX, mouseY);
            subY += toggleRow.height();
            scaleRow.render(ctx, x, subY, width, mouseX, mouseY);
            subY += scaleRow.height();
            colorRow.render(ctx, x, subY, width, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        // Header zone — toggle expand/collapse on left-click.
        if (mouseY < rowY + HEADER_H) {
            if (button == 0
                    && mouseX >= rowX && mouseX < rowX + rowWidth
                    && mouseY >= rowY) {
                expanded = !expanded;
                FeatureSetting.clearFocus();
                return true;
            }
            return false;
        }
        if (!expanded) return false;

        int subY = rowY + HEADER_H;
        int toggleH = toggleRow.height();
        if (mouseY < subY + toggleH) {
            return toggleRow.mouseClicked(mouseX, mouseY, button, rowX, subY, rowWidth);
        }
        subY += toggleH;
        int scaleH = scaleRow.height();
        if (mouseY < subY + scaleH) {
            return scaleRow.mouseClicked(mouseX, mouseY, button, rowX, subY, rowWidth);
        }
        subY += scaleH;
        return colorRow.mouseClicked(mouseX, mouseY, button, rowX, subY, rowWidth);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY,
                                int rowX, int rowY, int rowWidth) {
        if (!expanded) return false;
        // Forward to both drag-capable children unconditionally — each uses
        // its own `dragging` flag (set on mouse-down) to decide whether to
        // handle the event, so no Y-bounds check is needed. The owning
        // screen passes rowY=0 for nested containers, making absolute
        // bounds checks unreliable here.
        if (scaleRow.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX, rowY + HEADER_H, rowWidth)) {
            return true;
        }
        return colorRow.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX, rowY + HEADER_H, rowWidth);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!expanded) return false;
        if (toggleRow.mouseReleased(mouseX, mouseY, button)) return true;
        if (scaleRow.mouseReleased(mouseX, mouseY, button)) return true;
        return colorRow.mouseReleased(mouseX, mouseY, button);
    }
}