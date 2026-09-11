package com.aurora.client.screen.setting;

import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart search + filterable list container for the Particles module.
 *
 * <p>Mirrors the UX of {@link ItemScaleSetting}: a search {@link EditBox}
 * at the top that filters the list of {@link ParticleRowSetting} rows below
 * in real time. Unlike the item-scale variant, particles are a fixed set
 * (every vanilla particle type) so the search bar <b>filters</b> the
 * existing list rather than adding new entries.
 *
 * <p>Matching is case-insensitive against both the registry id
 * (e.g. {@code "minecraft:flame"}) and the humanized display name
 * (e.g. {@code "Flame"}). An empty query shows all particles.
 *
 * <p>The row list is built once at construction from the particle registry
 * (passed in by {@code FeatureRegistry}) and reused for the lifetime of
 * this setting — filtering only changes which rows are rendered, not the
 * backing list.
 */
public class ParticleConfigSetting extends FeatureSetting {

    private static final int SEARCH_H = 30;
    private static final int ROW_GAP = 4;

    private final EditBox searchField;
    private final List<ParticleRowSetting> allRows;
    private String lastQuery = null;
    private List<ParticleRowSetting> filtered = new ArrayList<>();

    public ParticleConfigSetting(List<ParticleRowSetting> rows) {
        super("Particle Search");
        this.allRows = rows;
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal("Search Particles..."));
        this.searchField.setHint(Component.literal("Search particles... (e.g. flame)"));
        // Disable the vanilla border — we draw our own hi-res AA rounded
        // outline on top (see render()) so the vanilla hairline border
        // doesn't double-stroke behind it.
        this.searchField.setBordered(false);
        this.searchField.setTextColor(AuroraTheme.IOS_LABEL);
        // No responder needed — we re-read the query each render/click frame
        // so the filtered list always matches the live text. Keeping a
        // responder would just duplicate work on every keystroke.
        recomputeFiltered();
    }

    /**
     * Each time the detail screen (re)opens, focus the search field and
     * claim global key focus so the player can start typing immediately
     * without an extra click. This pairs with {@link #onDetailScreenClose()},
     * which wipes the previous query so reopening always starts clean.
     */
    @Override
    public void onDetailScreenOpen() {
        searchField.setValue("");
        searchField.setFocused(true);
        searchField.moveCursorToEnd(false);
        requestFocus();
        // Force the filtered list to rebuild against the now-empty query,
        // otherwise recomputeFiltered() would short-circuit on lastQuery
        // (still holding the pre-close value) and the list wouldn't match
        // the cleared text field on the first open frame.
        lastQuery = null;
        recomputeFiltered();
    }

    /**
     * Wipe the query and drop focus when the detail screen closes. Without
     * this, the long-lived {@link EditBox} instance keeps its text across
     * open/close cycles — so the next open would show the player's previous
     * search instead of an empty field. We also clear {@link #lastQuery}
     * so the next {@link #recomputeFiltered()} actually rebuilds the list.
     */
    @Override
    public void onDetailScreenClose() {
        searchField.setValue("");
        searchField.setFocused(false);
        searchField.moveCursorToEnd(false);
        releaseFocus();
        lastQuery = null;
        filtered.clear();
    }

    /** True if the humanized label or registry id contains the query token. */
    private static boolean matches(ParticleRowSetting row, String clean) {
        if (clean.isEmpty()) return true;
        if (row.label.toLowerCase().contains(clean)) return true;
        // Also match the raw registry id (e.g. "minecraft:flame") so a
        // query like "dripping_water" still hits even though the label
        // is humanized to "Dripping Water".
        return row.particleId().toLowerCase().contains(clean);
    }

    private void recomputeFiltered() {
        String q = searchField.getValue();
        String clean = q == null ? "" : q.trim().toLowerCase();
        if (clean.equals(lastQuery)) return;
        lastQuery = clean;
        filtered.clear();
        for (ParticleRowSetting row : allRows) {
            if (matches(row, clean)) filtered.add(row);
        }
    }

    @Override
    public int baseHeight() {
        recomputeFiltered();
        int h = SEARCH_H;
        for (ParticleRowSetting row : filtered) {
            h += row.height() + ROW_GAP;
        }
        if (!filtered.isEmpty()) h -= ROW_GAP; // no trailing gap
        return h;
    }

    /** No description on this container — height equals base height. */
    @Override
    public int height() { return baseHeight(); }

    /**
     * Pre-dim surface (§6 convention 6): drives the search field's own glass
     * pass (EditBoxMixin carries the frame-stamp scheme), positioned from
     * the row geometry the screen passes — the same rect render computes.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!com.aurora.client.ui.component.GlassSurface.passOpen()) return; // legacy frame order
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(width - 24);
        ((com.aurora.client.ui.component.GlassEditBox) searchField).aurora$renderGlassPass(ctx);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        recomputeFiltered();

        // 1. Search bar — the themed background, centered text, and
        // outline are all drawn by EditBoxMixin (which fully replaces
        // the vanilla EditBox render on FeatureDetailScreen), so we
        // just position the field and let it render itself.
        int searchW = width - 24;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(searchW);
        searchField.render(ctx, mouseX, mouseY, 0f);

        // 2. Filtered particle rows
        int currentY = y + SEARCH_H;
        for (ParticleRowSetting row : filtered) {
            int h = row.height();
            if (currentY + h > 0 && currentY < y + baseHeight()) {
                row.render(ctx, x, currentY, width, mouseX, mouseY);
            }
            currentY += h + ROW_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        recomputeFiltered();

        // Search bar hit-test
        if (mouseY >= rowY + 5 && mouseY < rowY + 25) {
            int searchW = rowWidth - 24;
            if (mouseX >= rowX + 12 && mouseX < rowX + 12 + searchW) {
                searchField.setFocused(true);
                requestFocus();
                return true;
            }
            searchField.setFocused(false);
        }

        // Row hit-tests
        int currentY = rowY + SEARCH_H;
        for (ParticleRowSetting row : filtered) {
            int h = row.height();
            if (mouseY >= currentY && mouseY < currentY + h) {
                if (row.mouseClicked(mouseX, mouseY, button, rowX, currentY, rowWidth)) {
                    return true;
                }
            }
            currentY += h + ROW_GAP;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY,
                                int rowX, int rowY, int rowWidth) {
        // Forward to ALL rows — the inner settings (slider / color pad) use
        // their own `dragging` flag to decide whether to handle the event,
        // so no Y-bounds check is needed. The screen passes rowY=0 here
        // (it only tracks the outermost setting), so bounds checks would
        // be wrong anyway. Mirrors ItemScaleSetting's approach.
        for (ParticleRowSetting row : filtered) {
            if (row.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX, rowY, rowWidth)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (ParticleRowSetting row : allRows) {
            if (row.mouseReleased(mouseX, mouseY, button)) return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPress(net.minecraft.client.input.KeyEvent _kev) {
        if (searchField != null && searchField.isFocused()) {
            if (searchField.keyPressed(_kev)) {
                return true;
            }
        }
        // Delegate to active focused embedded setting (slider / color pad)
        FeatureSetting f = FeatureSetting.getFocused();
        if (f != null && f != this) {
            return f.onKeyPress(_kev);
        }
        return false;
    }

    @Override
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (searchField != null && searchField.isFocused()) {
            if (searchField.charTyped(_ev)) {
                return true;
            }
        }
        return false;
    }
}