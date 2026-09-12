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
 * Smart search + filterable list container — the shared mechanism behind
 * the Particles per-particle list ({@link ParticleConfigSetting}) and the
 * Alerts per-effect list ({@code EffectExpiryListSetting}).
 *
 * <p>A search {@link EditBox} at the top filters the rows below in real
 * time. The rows are a fixed, registry-backed set built once at
 * {@code FeatureRegistry} init and passed in by the caller, so the search
 * bar <b>filters</b> the existing list rather than adding entries (the
 * add-on-typing variant is {@link ItemScaleSetting}'s own thing).
 *
 * <p>Subclasses define only {@link #matches(Object, String)} — how one row
 * matches the cleaned query (both shipped lists match the humanized label
 * OR the raw registry id, so {@code "dripping_water"} finds "Dripping
 * Water"). An empty query shows all rows.
 *
 * <p>Everything else is type-agnostic and lives here: the themed borderless
 * search field (rendered by {@code EditBoxMixin} on detail screens, glass
 * surface driven pre-dim via {@link #renderGlassPass}), the memoized
 * filtered-list recompute (only on query change — never per frame), the
 * search-band hit-test, and event forwarding to the visible rows.
 */
public abstract class SearchListSetting<T extends FeatureSetting> extends FeatureSetting {

    protected static final int SEARCH_H = 30;
    protected static final int ROW_GAP = 4;

    protected final EditBox searchField;
    protected final List<T> allRows;
    protected String lastQuery = null;
    protected List<T> filtered = new ArrayList<>();

    protected SearchListSetting(String label, List<T> rows, String searchHint) {
        super(label);
        this.allRows = rows;
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal(label));
        this.searchField.setHint(Component.literal(searchHint));
        // Disable the vanilla border — we draw our own hi-res AA rounded
        // outline on top (EditBoxMixin's themed rendering) so the vanilla
        // hairline border doesn't double-stroke behind it.
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

    /** True if this row matches the cleaned (lowercased, trimmed) query. */
    protected abstract boolean matches(T row, String clean);

    private void recomputeFiltered() {
        String q = searchField.getValue();
        String clean = q == null ? "" : q.trim().toLowerCase();
        if (clean.equals(lastQuery)) return;
        lastQuery = clean;
        filtered.clear();
        for (T row : allRows) {
            if (matches(row, clean)) filtered.add(row);
        }
    }

    @Override
    public int baseHeight() {
        recomputeFiltered();
        int h = SEARCH_H;
        for (T row : filtered) {
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

        // 2. Filtered rows. The container's bottom is computed ONCE —
        // calling baseHeight() inside the loop would re-sum every row's
        // height per iteration (the O(rows²) shape §10 warns about; rows
        // are memoized so it was cheap-but-quadratic even at Particles'
        // ~100 rows).
        int contentBottom = y + baseHeight();
        int currentY = y + SEARCH_H;
        for (T row : filtered) {
            int h = row.height();
            if (currentY + h > 0 && currentY < contentBottom) {
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
        for (T row : filtered) {
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
        // Forward to ALL rows — inner settings that drag (sliders / color
        // pads) use their own `dragging` flag to decide whether to handle
        // the event, so no Y-bounds check is needed. The screen passes
        // rowY=0 here (it only tracks the outermost setting), so bounds
        // checks would be wrong anyway. Mirrors ItemScaleSetting's approach.
        for (T row : filtered) {
            if (row.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX, rowY, rowWidth)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (T row : allRows) {
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
