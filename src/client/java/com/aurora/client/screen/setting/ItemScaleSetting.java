package com.aurora.client.screen.setting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.util.AuroraFontRenderer;

import com.aurora.client.config.AuroraConfig;
import com.aurora.client.util.AuroraShapes;
import com.aurora.client.util.AuroraTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Smart search and custom item scaling configuration UI row.
 * Extends FeatureSetting to act as a single setting container inside FeatureDetailScreen.
 */
public class ItemScaleSetting extends FeatureSetting {

    private final EditBox searchField;
    private Item foundItem = null;
    private final Map<String, Boolean> expandedStates = new HashMap<>();

    // Keep active DoubleSliderSetting instances per item and per field to handle drag events cleanly
    private final Map<String, List<DoubleSliderSetting>> itemSliders = new HashMap<>();

    public ItemScaleSetting() {
        super("Item Scale Configurator");
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal("Search Items..."));
        this.searchField.setHint(Component.literal("Search items... (e.g. sword)"));
        // Disable the vanilla border â€” we draw our own hi-res AA rounded
        // outline on top (see render()) so the vanilla hairline border
        // doesn't double-stroke behind it.
        this.searchField.setBordered(false);
        this.searchField.setTextColor(AuroraTheme.IOS_LABEL);
        this.searchField.setResponder(s -> {
            this.foundItem = findMatchingItem(s);
        });
    }

    private Item findMatchingItem(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String clean = query.trim().toLowerCase();

        // Exact ID match or exact path match
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                String idStr = id.toString();
                String pathStr = id.getPath();
                if (idStr.equals(clean) || pathStr.equals(clean)) {
                    return item;
                }
            }
        }

        // Contains in ID
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && id.getPath().contains(clean)) {
                return item;
            }
        }

        // Contains in display name
        for (Item item : BuiltInRegistries.ITEM) {
            String name = item.getName(item.getDefaultInstance()).getString().toLowerCase();
            if (name.contains(clean)) {
                return item;
            }
        }

        return null;
    }

    @Override
    public int baseHeight() {
        // Search bar (32px) + lists of items
        int h = 38;
        AuroraConfig cfg = AuroraConfig.get();
        for (String id : cfg.itemScales.keySet()) {
            h += 30; // Row header height
            if (expandedStates.getOrDefault(id, false)) {
                h += 7 * 36; // 7 sliders, each is 36px tall
            }
        }
        return h;
    }

    @Override
    public void render(GuiGraphics ctx, int x, int y, int width, int mouseX, int mouseY) {
        Font tr = Minecraft.getInstance().font;
        AuroraConfig cfg = AuroraConfig.get();

        // 1. Search bar â€” the themed background, centered text, and
        // outline are all drawn by EditBoxMixin (which fully replaces
        // the vanilla EditBox render on FeatureDetailScreen), so we
        // just position the field and let it render itself.
        int searchW = width - 56;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(searchW);
        searchField.render(ctx, mouseX, mouseY, 0f);

        // Draw "+" add button
        int plusX = x + width - 36;
        int plusY = y + 5;
        boolean plusHover = mouseX >= plusX && mouseX < plusX + 24 && mouseY >= plusY && mouseY < plusY + 20;
        int plusBg = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND),
                plusHover ? 0x66 : 0x2E);
        int plusBorder = plusHover ? AuroraTheme.BORDER_ON_HOVER : AuroraTheme.BORDER_OFF;
        AuroraShapes.panel(ctx, plusX, plusY, 24, 20, plusBg, AuroraTheme.RADIUS_SMALL);
        AuroraShapes.outline(ctx, plusX, plusY, 24, 20, plusBorder, AuroraTheme.RADIUS_SMALL);
        AuroraFontRenderer.drawCentered(ctx, tr, "+", plusX + 12, plusY + 6,
                plusHover ? 0xFFFFFFFF : AuroraTheme.TEXT_SECONDARY);

        // Draw "suggested item" preview if found
        int currentY = y + 32;
        if (foundItem != null) {
            String name = foundItem.getName(foundItem.getDefaultInstance()).getString();
            ctx.drawString(tr, "Add: " + name, x + 12, currentY,
                    ThemeManager.color(ThemeToken.SEMANTIC_SUCCESS), false);
            currentY += 12;
        }

        // 2. Draw Customized Items List
        List<String> sortedIds = new ArrayList<>(cfg.itemScales.keySet());
        Collections.sort(sortedIds);

        for (String idStr : sortedIds) {
            boolean expanded = expandedStates.getOrDefault(idStr, false);
            int rowY = currentY;

            // Header Background Panel
            boolean rowHover = mouseX >= x + 10 && mouseX < x + width - 10 && mouseY >= rowY && mouseY < rowY + 28;
            AuroraShapes.panel(ctx, x + 10, rowY, width - 20, 26,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x1A),
                    AuroraTheme.RADIUS_SMALL);
            if (rowHover) {
                AuroraShapes.outline(ctx, x + 10, rowY, width - 20, 26, AuroraTheme.BORDER_OFF, AuroraTheme.RADIUS_SMALL);
            }

            // Humanize item id for label
            String labelStr = humanizeItemId(idStr);
            ctx.drawString(tr, labelStr, x + 20, rowY + 9, AuroraTheme.IOS_LABEL, false);

            // chevron and trash icon
            String chev = expanded ? "v" : ">";
            int rightX = x + width - 24;
            // Draw Delete Button (Red cross/Trash)
            boolean trashHover = mouseX >= rightX - 16 && mouseX < rightX && mouseY >= rowY + 5 && mouseY < rowY + 21;
            ctx.drawString(tr, "x", rightX - 12, rowY + 8,
                    trashHover ? ThemeManager.color(ThemeToken.SEMANTIC_ERROR)
                                : AuroraTheme.IOS_TERTIARY_LABEL, false);

            // Draw Chevron
            ctx.drawString(tr, chev, rightX - 30, rowY + 8, AuroraTheme.IOS_SECONDARY_LABEL, false);

            currentY += 30;

            if (expanded) {
                // Get or create sliders for this item
                List<DoubleSliderSetting> sliders = getOrCreateSliders(idStr);
                for (DoubleSliderSetting s : sliders) {
                    s.render(ctx, x + 8, currentY, width - 16, mouseX, mouseY);
                    currentY += s.height();
                }
            }
        }
    }

    private String humanizeItemId(String idStr) {
        if (idStr == null) return "Unknown";
        int colon = idStr.indexOf(':');
        String path = colon >= 0 ? idStr.substring(colon + 1) : idStr;
        StringBuilder sb = new StringBuilder();
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

    private List<DoubleSliderSetting> getOrCreateSliders(String idStr) {
        return itemSliders.computeIfAbsent(idStr, id -> {
            List<DoubleSliderSetting> list = new ArrayList<>();
            // Getter and setters referencing config directly
            list.add(new DoubleSliderSetting("Scale",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.scale : 1.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.scale = (float) v;
                    }, 0.1, 2.0));

            list.add(new DoubleSliderSetting("Translation X",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationX : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationX = (float) v;
                    }, -2.0, 2.0));

            list.add(new DoubleSliderSetting("Translation Y",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationY : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationY = (float) v;
                    }, -2.0, 2.0));

            list.add(new DoubleSliderSetting("Translation Z",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationZ : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationZ = (float) v;
                    }, -2.0, 2.0));

            list.add(new DoubleSliderSetting("Pitch",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.pitch : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.pitch = (float) v;
                    }, -180.0, 180.0));

            list.add(new DoubleSliderSetting("Yaw",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.yaw : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.yaw = (float) v;
                    }, -180.0, 180.0));

            list.add(new DoubleSliderSetting("Roll",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.roll : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.roll = (float) v;
                    }, -180.0, 180.0));

            return list;
        });
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
                if (foundItem != null) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(foundItem);
                    if (id != null) {
                        String idStr = id.toString();
                        AuroraConfig cfg = AuroraConfig.get();
                        if (!cfg.itemScales.containsKey(idStr)) {
                            cfg.itemScales.put(idStr, new AuroraConfig.ItemScaleData());
                            expandedStates.put(idStr, true);
                            searchField.setValue("");
                        }
                    }
                }
                return true;
            }
        }

        // Click on customized items list
        int currentY = rowY + 32;
        if (foundItem != null) {
            currentY += 12;
        }

        AuroraConfig cfg = AuroraConfig.get();
        List<String> sortedIds = new ArrayList<>(cfg.itemScales.keySet());
        Collections.sort(sortedIds);

        for (String idStr : sortedIds) {
            boolean expanded = expandedStates.getOrDefault(idStr, false);
            int rowHeaderY = currentY;

            if (mouseY >= rowHeaderY && mouseY < rowHeaderY + 26) {
                int rightX = rowX + rowWidth - 24;
                // Delete Click
                if (mouseX >= rightX - 16 && mouseX < rightX) {
                    cfg.itemScales.remove(idStr);
                    itemSliders.remove(idStr);
                    expandedStates.remove(idStr);
                    return true;
                }

                // Expand / Collapse click
                if (mouseX >= rowX + 10 && mouseX < rightX - 16) {
                    expandedStates.put(idStr, !expanded);
                    FeatureSetting.clearFocus();
                    return true;
                }
            }

            currentY += 30;

            if (expanded) {
                List<DoubleSliderSetting> sliders = getOrCreateSliders(idStr);
                for (DoubleSliderSetting s : sliders) {
                    int h = s.height();
                    if (mouseY >= currentY && mouseY < currentY + h) {
                        if (s.mouseClicked(mouseX, mouseY, button, rowX + 8, currentY, rowWidth - 16)) {
                            return true;
                        }
                    }
                    currentY += h;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, int rowX, int rowY, int rowWidth) {
        int currentY = rowY + 32;
        if (foundItem != null) {
            currentY += 12;
        }

        AuroraConfig cfg = AuroraConfig.get();
        List<String> sortedIds = new ArrayList<>(cfg.itemScales.keySet());
        Collections.sort(sortedIds);

        for (String idStr : sortedIds) {
            boolean expanded = expandedStates.getOrDefault(idStr, false);
            currentY += 30;

            if (expanded) {
                List<DoubleSliderSetting> sliders = getOrCreateSliders(idStr);
                for (DoubleSliderSetting s : sliders) {
                    int h = s.height();
                    if (s.mouseDragged(mouseX, mouseY, button, deltaX, deltaY, rowX + 8, currentY, rowWidth - 16)) {
                        return true;
                    }
                    currentY += h;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AuroraConfig cfg = AuroraConfig.get();
        for (List<DoubleSliderSetting> sliders : itemSliders.values()) {
            for (DoubleSliderSetting s : sliders) {
                if (s.mouseReleased(mouseX, mouseY, button)) {
                    return true;
                }
            }
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

        // Delegate to active slider if focused
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
