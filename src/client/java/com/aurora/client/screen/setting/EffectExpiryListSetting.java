package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Alerts feature's Per-Effect Alerts section — the
 * {@link ItemScaleSetting} search-then-add pattern applied to potion
 * effects, replacing the first cut's render-every-effect-with-a-toggle
 * list: a search field with a "+" add button suggests one matching
 * effect (sprite + "Add: <name>"), adding it to the config's curated
 * inclusion list ({@code effectExpiryIncludedEffects}); only effects on
 * that list alert AND render rows, each with ItemScale's trash control.
 *
 * <p>Match tiers are ItemScale's: exact registry id or path, then path
 * contains, then localized display name contains. Added rows display in
 * alphabetical order (ItemScale sorts its map keys the same way); the
 * row objects are cached per effect id like ItemScale's per-item
 * sliders. Interaction shape, glass handling (search field + "+" via the
 * frame-stamp scheme), and the close-time reset all mirror ItemScale.
 */
public class EffectExpiryListSetting extends FeatureSetting {

    private static final int ICON_SIZE = 16;

    private final EditBox searchField;
    /** Registry id of the effect the current query resolves to, or null. */
    private Identifier foundEffect = null;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * frame stamp + result for the "+" add button — the exact fields
     * ItemScaleSetting keeps for its own add button. In the stamped frame
     * {@link #render} paints content only (flat pill on decline);
     * otherwise the surface paints in place — legacy, pixel-identical.
     * The search field carries its own stamp inside {@code EditBoxMixin}.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewPlus = false;

    /** Row objects cached per effect id (ItemScale's per-item slider caches). */
    private final Map<String, EffectRowSetting> rowCache = new HashMap<>();

    public EffectExpiryListSetting() {
        super("Per-Effect Alert List");
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal("Search Effects..."));
        this.searchField.setHint(Component.literal("Search effects... (e.g. strength)"));
        // Disable the vanilla border — EditBoxMixin draws the themed AA
        // outline (same reason as every other search field).
        this.searchField.setBordered(false);
        this.searchField.setTextColor(AuroraTheme.IOS_LABEL);
        this.searchField.setResponder(s -> this.foundEffect = findMatchingEffect(s));
    }

    /** ItemScale's three-tier match, over the potion-effect registry. */
    private Identifier findMatchingEffect(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String clean = query.trim().toLowerCase();

        // Exact ID match or exact path match
        for (MobEffect eff : BuiltInRegistries.MOB_EFFECT) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(eff);
            if (id != null && (id.toString().equals(clean) || id.getPath().equals(clean))) {
                return id;
            }
        }
        // Contains in ID
        for (MobEffect eff : BuiltInRegistries.MOB_EFFECT) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(eff);
            if (id != null && id.getPath().contains(clean)) {
                return id;
            }
        }
        // Contains in display name
        for (MobEffect eff : BuiltInRegistries.MOB_EFFECT) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(eff);
            if (id != null && displayNameFor(eff, id).toLowerCase().contains(clean)) {
                return id;
            }
        }
        return null;
    }

    /** Localized effect name, humanized-path fallback when untranslated. */
    private static String displayNameFor(MobEffect eff, Identifier id) {
        String name = null;
        try {
            name = Component.translatable(eff.getDescriptionId()).getString();
        } catch (Throwable ignored) {
        }
        if (name == null || name.isEmpty() || name.startsWith("effect.")) {
            name = humanizeEffectPath(id.getPath());
        }
        return name;
    }

    private static String humanizeEffectPath(String path) {
        StringBuilder sb = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '_') {
                sb.append(' ');
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private EffectRowSetting rowFor(String effectId) {
        return rowCache.computeIfAbsent(effectId, id -> {
            Identifier loc = Identifier.parse(id);
            MobEffect eff = BuiltInRegistries.MOB_EFFECT.getValue(loc);
            String name = eff != null ? displayNameFor(eff, loc) : humanizeEffectPath(loc.getPath());
            return new EffectRowSetting(id, loc, name);
        });
    }

    @Override
    public int baseHeight() {
        // Search band (38px, ItemScale's) + suggestion line + added rows.
        int h = 38;
        if (foundEffect != null) h += 12;
        h += AuroraConfig.get().effectExpiryIncludedEffects.size() * EffectRowSetting.STRIDE;
        return h;
    }

    /**
     * Pre-dim surface (the split's surface half): the "+" add button's
     * raised glass plus the search field's own, positioned here because
     * the pass runs before {@link #render} — a scroll frame would
     * otherwise drive the field at a one-frame-stale rect. ItemScale's
     * hook verbatim.
     */
    @Override
    public void renderGlassPass(GuiGraphics ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return; // legacy frame order — render paints in place
        glassPassFrame = GlassSurface.frame();
        int searchW = width - 56;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(searchW);
        ((GlassEditBox) searchField).aurora$renderGlassPass(ctx);
        int plusX = x + width - 36;
        int plusY = y + 5;
        float plusR = Math.min(20 / 2f, ThemeManager.current().roundness().radiusSmall());
        passDrewPlus = GlassSurface.control(ctx, plusX, plusY, 24, 20, plusR);
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        AuroraConfig cfg = AuroraConfig.get();

        // 1. Search bar — themed background/outline drawn by EditBoxMixin.
        int searchW = width - 56;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(searchW);
        searchField.render(ctx, mouseX, mouseY, 0f);

        // "+" add button — raised glass (flat pill on decline); ItemScale's
        // in-place/stamped split verbatim.
        int plusX = x + width - 36;
        int plusY = y + 5;
        boolean plusHover = Widget.inBounds(mouseX, mouseY, plusX, plusY, 24, 20);
        int plusBg = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND),
                plusHover ? 0x66 : 0x2E);
        int plusBorder = plusHover ? AuroraTheme.BORDER_ON_HOVER : AuroraTheme.BORDER_OFF;
        float plusR = Math.min(20 / 2f, ThemeManager.current().roundness().radiusSmall());
        boolean plusGlass;
        if (glassPassFrame == GlassSurface.frame()) {
            plusGlass = passDrewPlus;
        } else {
            plusGlass = GlassSurface.control(ctx, plusX, plusY, 24, 20, plusR);
        }
        if (!plusGlass) {
            AuroraShapes.panel(ctx, plusX, plusY, 24, 20, plusBg, AuroraTheme.RADIUS_SMALL);
            AuroraShapes.outline(ctx, plusX, plusY, 24, 20, plusBorder, AuroraTheme.RADIUS_SMALL);
        }
        com.aurora.client.ui.util.AuroraFontRenderer.drawCentered(ctx, tr, "+", plusX + 12, plusY + 6,
                plusHover ? 0xFFFFFFFF : AuroraTheme.TEXT_SECONDARY);

        // "Suggested effect" preview — its real status-effect sprite
        // beside the text (ItemScale's "Add: <name>" line).
        int currentY = y + 32;
        if (foundEffect != null) {
            MobEffect eff = BuiltInRegistries.MOB_EFFECT.getValue(foundEffect);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED,
                    Identifier.fromNamespaceAndPath(foundEffect.getNamespace(),
                            "mob_effect/" + foundEffect.getPath()),
                    x + 12, currentY - 2, ICON_SIZE, ICON_SIZE);
            ctx.drawString(tr, "Add: " + displayNameFor(eff, foundEffect), x + 12 + ICON_SIZE + 4, currentY,
                    ThemeManager.color(ThemeToken.SEMANTIC_SUCCESS), false);
            currentY += 12;
        }

        // 2. Added-effects list — alphabetical (ItemScale sorts its ids the
        //    same way). Only added effects render: the perf point of the
        //    search-then-add model.
        List<String> included = new ArrayList<>(cfg.effectExpiryIncludedEffects);
        included.sort(Comparator.comparing(id -> rowFor(id).label.toLowerCase()));

        for (String effectId : included) {
            rowFor(effectId).render(ctx, x, currentY, width, mouseX, mouseY);
            currentY += EffectRowSetting.STRIDE;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        // Forward click to search bar
        if (mouseY >= rowY + 5 && mouseY < rowY + 25) {
            int searchW = rowWidth - 56;
            if (mouseX >= rowX + 12 && mouseX < rowX + 12 + searchW) {
                searchField.setFocused(true);
                requestFocus();
                return true;
            } else {
                searchField.setFocused(false);
            }

            // Click on "+" button
            int plusX = rowX + rowWidth - 36;
            if (mouseX >= plusX && mouseX < plusX + 24) {
                // Nothing to add (no matching effect) — don't consume.
                if (foundEffect == null) return false;
                String idStr = foundEffect.toString();
                AuroraConfig cfg = AuroraConfig.get();
                if (!cfg.effectExpiryIncludedEffects.contains(idStr)) {
                    cfg.effectExpiryIncludedEffects.add(idStr);
                    searchField.setValue("");
                    // Persist immediately — matches ItemScale's add.
                    AuroraConfig.save();
                }
                return true;
            }
        }

        // Click on added-effects list — trash removes (ItemScale's
        // division: the container owns the backing list).
        int currentY = rowY + 32;
        if (foundEffect != null) {
            currentY += 12;
        }

        AuroraConfig cfg = AuroraConfig.get();
        List<String> included = new ArrayList<>(cfg.effectExpiryIncludedEffects);
        included.sort(Comparator.comparing(id -> rowFor(id).label.toLowerCase()));

        for (String effectId : included) {
            if (mouseY >= currentY && mouseY < currentY + EffectRowSetting.ROW_H) {
                if (rowFor(effectId).trashHit(mouseX, mouseY, rowX, currentY, rowWidth)) {
                    cfg.effectExpiryIncludedEffects.remove(effectId);
                    rowCache.remove(effectId);
                    AuroraConfig.save();
                    return true;
                }
            }
            currentY += EffectRowSetting.STRIDE;
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

    /**
     * Reset the search state on screen close so reopening starts clean and
     * this row cannot hold the static focus registry across screens —
     * ItemScale's lifecycle contract (and the previous container's).
     */
    @Override
    public void onDetailScreenClose() {
        searchField.setValue("");
        searchField.setFocused(false);
        searchField.moveCursorToEnd(false);
        releaseFocus();
        foundEffect = null;
    }
}
