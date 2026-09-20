package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Collapsible row for the per-particle controls list.
 *
 * <p>Header (always visible, {@value #HEADER_H} px tall): particle display
 * name on the left, "On 100%" / "Off" state summary on the right, plus a
 * Material disclosure chevron indicating expand/collapse state. Click
 * anywhere on the header to toggle.
 *
 * <p>Phase C-4 rollout: the disclosure is a stateful disclosure icon
 * action — the whole header is the action's pointer target (exactly the
 * zone that always toggled), the chevron is the Material
 * {@code expand_more}/{@code expand_less} pair flipping with the state
 * (the ASCII "v"/">" stand-ins are gone), hover animates the glyph, and
 * Tab/Enter/Space toggle through the same semantic action exactly once.
 * Narration carries the row label ("Toggle <particle>") plus the
 * Expanded/Collapsed state; the glyph is presentation only.
 *
 * <p>Body (only when expanded): an embedded {@link BooleanSetting} for
 * visibility, an embedded {@link SliderSetting} (0–1.5,
 * percent-formatted) for scale, and an embedded {@link ColorSetting}
 * for the ARGB color overlay tint. Mouse / drag / release events are
 * forwarded to the embedded settings at translated row offsets. Scroll
 * and key events route through the global {@link FeatureSetting#getFocused()}
 * mechanism so the screen sends them directly to the focused embedded
 * slider — no forwarding needed here.
 */
public class ParticleRowSetting extends FeatureSetting {
    private static final int HEADER_H = 30;
    /** Right-edge slot the disclosure glyph occupies (incl. breathing room). */
    private static final int CHEV_SLOT = 16;
    /** Material Symbols chevrons — the EnumSetting/ItemScale disclosure pair. */
    private static final String CHEV_UP = "\uE5CE";    // expand_less
    private static final String CHEV_DOWN = "\uE5CF";  // expand_more

    private final String particleId;
    /** Package-private so {@link ParticleConfigSetting} can match against
     *  the raw registry id (e.g. {@code "minecraft:flame"}) in addition to
     *  the humanized display label. */
    String particleId() { return particleId; }
    private final BooleanSetting toggleRow;
    private final SliderSetting scaleRow;
    private final ColorSetting colorRow;
    private boolean expanded = false;

    /**
     * The stateful disclosure action — one long-lived instance per row
     * (rows are fixed-set and long-lived); the glyph supplier flips with
     * {@link #expanded} while the instance — and with it the semantic
     * control and keyboard focus — survives the toggle.
     */
    private final com.aurora.client.ui.component.IconAction disclosure;

    private void toggleDisclosure() {
        expanded = !expanded;
        FeatureSetting.clearFocus();
    }

    public ParticleRowSetting(String particleId, String displayName) {
        super(displayName);
        this.particleId = particleId;
        this.disclosure = new com.aurora.client.ui.component.IconAction(
                () -> expanded ? CHEV_UP : CHEV_DOWN,
                com.aurora.client.ui.interaction.SemanticAction.button(
                        net.minecraft.network.chat.Component.literal("Toggle " + displayName),
                        () -> net.minecraft.network.chat.Component.literal(
                                "Shows or hides the visibility, scale, and color controls for the "
                                        + displayName + " particle."),
                        () -> net.minecraft.network.chat.Component.literal(
                                expanded ? "Expanded" : "Collapsed"),
                        () -> true,
                        this::toggleDisclosure),
                () -> AuroraTheme.IOS_SECONDARY_LABEL,
                () -> AuroraTheme.IOS_LABEL);
        this.toggleRow = new BooleanSetting("Visible",
                () -> {
                    Boolean v = AuroraConfig.get().particleVisibility.get(particleId);
                    return v == null || v;
                },
                v -> {
                    AuroraConfig.get().particleVisibility.put(particleId, v);
                });
        this.scaleRow = SliderSetting.of("Scale",
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

    /** The disclosure's semantic control (the row's one action). */
    @Override
    public SemanticActionControl interactionControl() {
        return disclosure.interactionControl();
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        int textY = y + (HEADER_H - tr.lineHeight) / 2;

        // Display name on the left.
        ctx.drawString(tr, label, x + 12, textY, AuroraTheme.IOS_LABEL, false);

        // State summary on the right, before the disclosure slot.
        Boolean v = AuroraConfig.get().particleVisibility.get(particleId);
        boolean on = v == null || v;
        Float s = AuroraConfig.get().particleScale.get(particleId);
        float scale = s == null ? 1.0f : s;
        String summary = on ? Math.round(scale * 100f) + "%" : "Off";
        int sumW = tr.width(summary);
        int rightEdge = x + width - 12;
        ctx.drawString(tr, summary, rightEdge - CHEV_SLOT - sumW, textY,
                on ? AuroraTheme.IOS_SECONDARY_LABEL : AuroraTheme.IOS_TERTIARY_LABEL, false);

        // Color swatch indicator between summary and disclosure, so the
        // header hints at the configured tint even while collapsed.
        Integer c = AuroraConfig.get().particleColor.get(particleId);
        if (c != null && c != 0) {
            int swatchX = rightEdge - CHEV_SLOT - sumW - 8 - 10;
            int swatchY = textY + (tr.lineHeight - 8) / 2;
            ctx.fill(swatchX, swatchY, swatchX + 10, swatchY + 8, c);
        }

        // Disclosure action (C-4 rollout): the header is the pointer target;
        // the Material chevron paints in the right-edge slot, hover-animated.
        disclosure.syncChannels(x, y, width, HEADER_H, mouseX, mouseY);
        disclosure.paintGlyph(ctx, tr, rightEdge - CHEV_SLOT, y + (HEADER_H - CHEV_SLOT) / 2,
                CHEV_SLOT, CHEV_SLOT);

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
        // Header zone — toggle expand/collapse on left-click, through the
        // disclosure action (exactly-once + the one click) when hosted; the
        // shared toggle path beside it covers the pre-first-ask window.
        if (mouseY < rowY + HEADER_H) {
            if (button == 0
                    && mouseX >= rowX && mouseX < rowX + rowWidth
                    && mouseY >= rowY) {
                if (disclosure.clicked(mouseX, mouseY, button)) {
                    return true;
                }
                toggleDisclosure();
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