package com.aurora.client.screen.setting;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.search.SearchMatcher;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.RecentSoundLog;
import com.aurora.client.util.SoundVolumes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Searchable per-sound volume mixer (0–100% attenuation only) hosted inside a
 * {@code FeatureDetailScreen}.
 *
 * <p>Avoids instantiating sliders for all 1,600+ vanilla sound events up front:
 * when the search box is empty, shows customized sounds first, then recently
 * played sounds (or curated common sounds on a fresh session). Typing filters
 * across every registered sound event and caps the visible list to 25 rows.
 */
public final class SoundVolumeSetting extends FeatureSetting {
    private static final int SEARCH_H = 30;
    private static final int ROW_GAP = 4;
    private static final int MAX_SEARCH_RESULTS = 25;

    private static final List<String> COMMON_SOUNDS = List.of(
            "minecraft:block.portal.ambient",
            "minecraft:weather.rain",
            "minecraft:entity.lightning_bolt.thunder",
            "minecraft:entity.villager.ambient",
            "minecraft:entity.ender_dragon.growl",
            "minecraft:entity.wither.spawn",
            "minecraft:entity.generic.explode",
            "minecraft:entity.enderman.scream",
            "minecraft:entity.ghast.scream",
            "minecraft:block.anvil.land"
    );

    private record SoundEntry(String id, String label) {}

    private final EditBox searchField;
    private List<SoundEntry> catalog;
    private final Map<String, SliderSetting> sliderCache = new LinkedHashMap<>();
    private final List<FeatureSetting> rows = new ArrayList<>();
    private String lastQueryKey;
    private int lastOverrideCount = -1;
    private int lastRecentCount = -1;

    private List<SemanticActionControl> cachedControls = List.of();
    private boolean controlsDirty = true;

    public SoundVolumeSetting() {
        super("Sound Volume Search");
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal("Sound Volume Search"));
        this.searchField.setHint(Component.literal("Search sounds... (e.g. portal, rain, villager, explosion)"));
        this.searchField.setBordered(false);
        this.searchField.setTextColor(AuroraTheme.IOS_LABEL);
    }

    private List<SoundEntry> catalog() {
        if (catalog != null) return catalog;
        List<SoundEntry> built = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
            String raw = id.toString();
            built.add(new SoundEntry(raw, SoundVolumes.humanizeSoundId(raw)));
        }
        built.sort(Comparator.comparing(SoundEntry::label, String.CASE_INSENSITIVE_ORDER));
        catalog = built;
        return catalog;
    }

    private SliderSetting sliderFor(String soundId) {
        return sliderCache.computeIfAbsent(soundId, id -> {
            String label = SoundVolumes.humanizeSoundId(id);
            SliderSetting slider = SliderSetting.of(
                    label,
                    () -> {
                        Map<String, Float> map = AuroraConfig.get().soundVolumes;
                        Float v = map == null ? null : map.get(id);
                        return v == null ? 1.0 : SoundVolumes.clampScale(v);
                    },
                    v -> {
                        AuroraConfig cfg = AuroraConfig.get();
                        if (cfg.soundVolumes == null) cfg.soundVolumes = new LinkedHashMap<>();
                        float clamped = SoundVolumes.clampScale(v);
                        if (SoundVolumes.isNeutral(clamped)) {
                            cfg.soundVolumes.remove(id);
                        } else {
                            cfg.soundVolumes.put(id, clamped);
                        }
                    },
                    0.0,
                    1.0)
                    .resetDefault(() -> 1.0)
                    .valueFormatter(v -> v <= 0.001 ? "Muted" : Math.round(v * 100.0) + "%");
            slider.description(id + " — 0% (Muted) to 100% (Vanilla). Attenuation only.");
            return slider;
        });
    }

    private void rebuildIfNeeded() {
        AuroraConfig cfg = AuroraConfig.get();
        int overrideCount = cfg.soundVolumes == null ? 0 : cfg.soundVolumes.size();
        List<String> recent = RecentSoundLog.snapshot();
        SearchMatcher.Query query = SearchMatcher.query(searchField.getValue(), SearchMatcher.configuredTolerance());
        String key = query.tolerance() + "|" + query.text();
        if (key.equals(lastQueryKey) && overrideCount == lastOverrideCount && recent.size() == lastRecentCount) {
            return;
        }
        lastQueryKey = key;
        lastOverrideCount = overrideCount;
        lastRecentCount = recent.size();
        rows.clear();

        if (!query.isEmpty()) {
            int added = 0;
            for (SoundEntry entry : catalog()) {
                if (SearchMatcher.matches(query, entry.label(), entry.id())) {
                    rows.add(sliderFor(entry.id()));
                    if (++added >= MAX_SEARCH_RESULTS) break;
                }
            }
            controlsDirty = true;
            return;
        }

        Set<String> shown = new LinkedHashSet<>();
        if (cfg.soundVolumes != null && !cfg.soundVolumes.isEmpty()) {
            rows.add(new SectionHeaderSetting("Customized Sounds"));
            List<String> keys = new ArrayList<>(cfg.soundVolumes.keySet());
            keys.sort(String.CASE_INSENSITIVE_ORDER);
            for (String id : keys) {
                rows.add(sliderFor(id));
                shown.add(id);
            }
        }

        List<String> freshRecent = new ArrayList<>();
        for (String id : recent) {
            if (!shown.contains(id)) freshRecent.add(id);
        }
        if (!freshRecent.isEmpty()) {
            rows.add(new SectionHeaderSetting("Recently Played"));
            for (String id : freshRecent) {
                rows.add(sliderFor(id));
                shown.add(id);
            }
        }

        List<String> suggestions = new ArrayList<>();
        for (String id : COMMON_SOUNDS) {
            if (!shown.contains(id)) suggestions.add(id);
        }
        if (!suggestions.isEmpty()) {
            rows.add(new SectionHeaderSetting("Common Sounds"));
            for (String id : suggestions) {
                rows.add(sliderFor(id));
            }
        }
        controlsDirty = true;
    }

    @Override
    public void onDetailScreenOpen() {
        searchField.setValue("");
        searchField.setFocused(true);
        searchField.moveCursorToEnd(false);
        requestFocus();
        lastQueryKey = null;
        rebuildIfNeeded();
    }

    @Override
    public void onDetailScreenClose() {
        searchField.setValue("");
        searchField.setFocused(false);
        searchField.moveCursorToEnd(false);
        releaseFocus();
        lastQueryKey = null;
        rows.clear();
        controlsDirty = true;
    }

    @Override
    public List<SemanticActionControl> interactionControls() {
        rebuildIfNeeded();
        if (controlsDirty) {
            List<SemanticActionControl> fresh = new ArrayList<>();
            for (FeatureSetting row : rows) {
                fresh.addAll(row.interactionControls());
            }
            cachedControls = fresh;
            controlsDirty = false;
        }
        return cachedControls;
    }

    @Override public int fixedHeaderHeight() { return SEARCH_H; }

    @Override
    public void renderGlassPass(GuiGraphicsExtractor ctx, int x, int y, int width) {
        if (!isHeaderPinned()) renderFixedHeaderGlass(ctx, x, y, width);
    }

    @Override
    public void renderFixedHeaderGlass(GuiGraphicsExtractor ctx, int x, int y, int width) {
        if (!GlassSurface.passOpen()) return;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(width - 24);
        ((GlassEditBox) searchField).aurora$renderGlassPass(ctx);
    }

    private long searchFrame = Long.MIN_VALUE;

    @Override
    public void renderFixedHeader(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(width - 24);
        searchFrame = GlassSurface.frame();
        searchField.extractRenderState(ctx, mouseX, mouseY, 0f);
    }

    @Override
    public int baseHeight() {
        rebuildIfNeeded();
        int h = SEARCH_H;
        for (FeatureSetting row : rows) {
            h += row.height() + ROW_GAP;
        }
        if (!rows.isEmpty()) h -= ROW_GAP;
        return h;
    }

    @Override public int height() { return baseHeight(); }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY) {
        rebuildIfNeeded();
        if (!isHeaderPinned()) renderFixedHeader(ctx, x, y, width, mouseX, mouseY);
        int contentBottom = y + baseHeight();
        int currentY = y + SEARCH_H;
        var band = FeatureSetting.hostBand();
        for (FeatureSetting row : rows) {
            int h = row.height();
            if (currentY + h > band.y && currentY < Math.min(contentBottom, band.yEnd())) {
                row.render(ctx, x, currentY, width, mouseX, mouseY);
            } else {
                for (var control : row.interactionControls()) control.setAvailable(false);
            }
            currentY += h + ROW_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int rowX, int rowY, int rowWidth) {
        rebuildIfNeeded();
        if (mouseY >= searchField.getY() && mouseY < searchField.getY() + searchField.getHeight()) {
            int searchW = rowWidth - 24;
            if (mouseX >= rowX + 12 && mouseX < rowX + 12 + searchW) {
                searchField.setFocused(true);
                requestFocus();
                return true;
            }
            searchField.setFocused(false);
        }
        int currentY = rowY + SEARCH_H;
        for (FeatureSetting row : rows) {
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
                                double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        for (FeatureSetting row : rows) {
            if (row.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX, rowY, rowWidth)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = false;
        for (FeatureSetting row : rows) {
            if (row.mouseReleased(mouseX, mouseY, button)) handled = true;
        }
        if (handled) {
            AuroraConfig.save();
            lastOverrideCount = -1;
        }
        return handled;
    }

    @Override
    public boolean onKeyPress(net.minecraft.client.input.KeyEvent kev) {
        if (searchField != null && searchField.isFocused()) {
            if (kev.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                searchField.setFocused(false);
                releaseFocus();
                return true;
            }
            if (searchField.keyPressed(kev)) return true;
        }
        FeatureSetting f = FeatureSetting.getFocused();
        if (f != null && f != this) {
            return f.onKeyPress(kev);
        }
        return false;
    }

    @Override
    public boolean onCharTyped(net.minecraft.client.input.CharacterEvent ev) {
        if (searchField != null && searchField.isFocused()) {
            if (searchField.charTyped(ev)) return true;
        }
        return false;
    }

    @Override
    public boolean resumeSearch(net.minecraft.client.input.CharacterEvent event) {
        var band = FeatureSetting.hostBand();
        long frame = GlassSurface.frame();
        if (isDisabled() || searchFrame < frame - 1
                || (!isHeaderPinned() && !band.intersects(searchField.getX(), searchField.getY(), searchField.getWidth(), searchField.getHeight()))) {
            return false;
        }
        searchField.setFocused(true);
        requestFocus();
        return searchField.charTyped(event);
    }
}
