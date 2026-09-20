package com.aurora.client.screen.setting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.component.Widget;
import com.aurora.client.util.AuroraAnim;
import com.aurora.client.ui.util.ItemSpriteRenderer;
import com.aurora.client.ui.util.RenderUtil;

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
 *
 * <p>Glass rollout: the "+" add button renders as RAISED glass with the
 * neutral {@code WINDOW_FILL} tint (flat pill on decline), matching the
 * keybind pills and every other interactive control.
 *
 * <p>Icon rendering: each item tab renders its real item icon through
 * {@link ItemSpriteRenderer} — the crisp flat-sprite path (resolved GUI
 * sprite blitted pixel-aligned from the block atlas), falling back to the
 * 3D {@link GuiGraphics#renderItem} for tinted/multi-layer models. The
 * expand/collapse chevrons use the Material Symbols glyphs already in the
 * font subset (the {@code EnumSetting} dropdown convention), not ASCII
 * stand-ins.
 *
 * <p>Design language §4: collapsed item rows hide all seven configured
 * values behind the expand chevron — exactly the "state not visible at a
 * glance from the control itself" case — so each collapsed row carries a
 * small {@code ON_BACKGROUND_SECONDARY} subtitle beneath the label
 * surfacing the headline figure ("Scale 1.40×"). Expanded rows drop the
 * subtitle: the scale slider's own readout then shows the value at a
 * glance, and the spec's exclusion clause governs.
 */
public class ItemScaleSetting extends FeatureSetting {

    /** Material Symbols chevrons — already in the font subset (EnumSetting). */
    private static final String CHEV_UP = "\uE5CE";    // expand_less
    private static final String CHEV_DOWN = "\uE5CF";  // expand_more

    private static final int ICON_SIZE = 16;

    /**
     * Collapsed item-row header height and stride. Grew from 26/30 when the
     * §4 live-value subtitle ("Scale 1.40×") was added beneath each item
     * label — the label block is 9 (label) + 2 (gap) + 9 (subtitle) = 20px,
     * so 38 keeps the same breathing room the 26px single-line row had.
     */
    private static final int ITEM_ROW_H = 38;
    private static final int ITEM_ROW_STRIDE = 42;

    private final EditBox searchField;
    private Item foundItem = null;

    /**
     * Glass-pass bookkeeping (the {@code Button} scheme, §6 convention 6):
     * frame stamp + result for the "+" add button. In the stamped frame
     * {@link #render} paints content only (flat button on decline);
     * otherwise the surface paints in place — legacy, pixel-identical.
     * The search field carries its own stamp inside {@code EditBoxMixin}
     * and is driven from {@link #renderGlassPass} below.
     */
    private long glassPassFrame = -1L;
    private boolean passDrewPlus = false;

    private final Map<String, Boolean> expandedStates = new HashMap<>();

    // Keep active SliderSetting instances per item and per field to handle drag events cleanly
    private final Map<String, List<SliderSetting>> itemSliders = new HashMap<>();

    // ---- Phase C-4 rollout: the search-then-add action ----
    //
    // The "+" pill as the canonical icon action (the C-4A EffectExpiry add
    // pilot's exact shape — the two search-then-add twins share one
    // contract): Material add glyph, pointer-only symmetric-140 hover
    // driving BOTH the pill's flat fallback and the glyph, focus hairline,
    // Tab/Enter/Space, exactly one activation click. The enabled gate IS
    // the can-add state (a match exists and is not already customized) —
    // the authoritative disabled contract: no hover target, no activation,
    // glyph painted muted. (A duplicate suggestion used to consume its
    // click silently; it now reads — honestly — as disabled, and the click
    // falls through unconsumed, which nothing else can reach.)
    private final com.aurora.client.ui.component.IconAction addAction =
            new com.aurora.client.ui.component.IconAction(
                    com.aurora.client.screen.FeatureIcons.get("_action_add"),
                    com.aurora.client.ui.interaction.SemanticAction.button(
                            Component.literal("Add item"),
                            () -> Component.literal(foundItem == null
                                    ? "Search for an item to add."
                                    : "Adds the matched item to the customized item list."),
                            null,
                            this::canAddFoundItem,
                            this::addFoundItem),
                    () -> AuroraTheme.TEXT_SECONDARY,
                    () -> 0xFFFFFFFF);

    private boolean canAddFoundItem() {
        if (foundItem == null) return false;
        Identifier id = BuiltInRegistries.ITEM.getKey(foundItem);
        return id != null && !AuroraConfig.get().itemScales.containsKey(id.toString());
    }

    /** The ONE add path — the action's behavior and the legacy fallback both land here. */
    private void addFoundItem() {
        if (foundItem == null) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(foundItem);
        if (id == null) return;
        String idStr = id.toString();
        AuroraConfig cfg = AuroraConfig.get();
        if (!cfg.itemScales.containsKey(idStr)) {
            cfg.itemScales.put(idStr, new AuroraConfig.ItemScaleData());
            expandedStates.put(idStr, true);
            searchField.setValue("");
            // Persist immediately — matches how sibling widgets save at
            // commit; relying on a later save loses the change on a crash.
            AuroraConfig.save();
            rebuildRowControlList();
        }
    }

    // ---- Phase C-4 rollout: the per-row disclosure actions ----
    //
    // The expand/collapse chevrons as STATEFUL disclosure icon actions (the
    // C-4A classification): one long-lived IconAction per item id whose
    // glyph SUPPLIER flips expand_less/expand_more with the row's state —
    // the instance (and with it the semantic control and keyboard focus)
    // survives the toggle. The pointer target is the row header body — the
    // exact zone the legacy body click toggled — so there is ONE toggle
    // path for pointer and keyboard, not a chevron action beside a legacy
    // body mutation. Narration carries the row label ("Toggle <name>") and
    // the Expanded/Collapsed state; the glyph is presentation only.
    private final Map<String, com.aurora.client.ui.component.IconAction> rowDiscloseActions = new LinkedHashMap<>();

    private com.aurora.client.ui.component.IconAction rowDiscloseAction(String idStr) {
        return rowDiscloseActions.computeIfAbsent(idStr, id -> {
            String label = humanizeItemId(id);
            return new com.aurora.client.ui.component.IconAction(
                    () -> expandedStates.getOrDefault(id, false) ? CHEV_UP : CHEV_DOWN,
                    com.aurora.client.ui.interaction.SemanticAction.button(
                            Component.literal("Toggle " + label),
                            () -> Component.literal("Shows or hides the scale sliders for " + label + "."),
                            () -> Component.literal(
                                    expandedStates.getOrDefault(id, false) ? "Expanded" : "Collapsed"),
                            () -> true,
                            () -> toggleExpanded(id)),
                    () -> AuroraTheme.IOS_SECONDARY_LABEL,
                    () -> AuroraTheme.IOS_LABEL);
        });
    }

    /** The ONE disclosure toggle path — action behavior and legacy fallback converge. */
    private void toggleExpanded(String idStr) {
        expandedStates.put(idStr, !expandedStates.getOrDefault(idStr, false));
        FeatureSetting.clearFocus();
    }

    // ---- Phase C-4 pilot A: the per-row remove action ----
    //
    // One long-lived IconAction per customized item (identity-keyed by item
    // id, created lazily by the render walk, pruned by the removal itself) —
    // the C-4 icon-action primitive supplies the canonical contract
    // (Material close glyph, pointer-only symmetric-140 hover, focus
    // hairline, Tab/Enter/Space, exactly one activation click). The domain
    // behavior is unchanged verbatim; the accessible label is textual
    // ("Remove item" + the row's name in the description), never the glyph.
    private final Map<String, com.aurora.client.ui.component.IconAction> rowRemoveActions = new LinkedHashMap<>();
    /**
     * The host-visible control list, rebuilt on mutation only (never per
     * frame). The rebuild swaps in a FRESH instance — the detail screen's
     * dynamic-set diff is identity-based, so clearing the same list in
     * place would hide the mutation and leave a removed row's control
     * registered (found live by the C-4 pilot boots).
     */
    private List<com.aurora.client.ui.interaction.SemanticActionControl> rowControlList = new ArrayList<>();

    private com.aurora.client.ui.component.IconAction rowRemoveAction(String idStr) {
        return rowRemoveActions.computeIfAbsent(idStr, id -> {
            String label = humanizeItemId(id);
            return new com.aurora.client.ui.component.IconAction(
                    com.aurora.client.screen.FeatureIcons.get("_action_close"),
                    com.aurora.client.ui.interaction.SemanticAction.button(
                            Component.literal("Remove item"),
                            () -> Component.literal("Removes " + label + " from the customized item list."),
                            null,
                            () -> true,
                            () -> removeItem(id)),
                    () -> AuroraTheme.IOS_TERTIARY_LABEL,
                    () -> ThemeManager.color(ThemeToken.SEMANTIC_ERROR));
        });
    }

    /** The ONE removal path — the action's behavior and the legacy fallback both land here. */
    private void removeItem(String idStr) {
        AuroraConfig cfg = AuroraConfig.get();
        cfg.itemScales.remove(idStr);
        itemSliders.remove(idStr);
        expandedStates.remove(idStr);
        // The row's controls are stale the moment its row dies: prune them
        // here so the host's diff unregisters them next frame (no semantic
        // child outlives its row).
        rowRemoveActions.remove(idStr);
        rowDiscloseActions.remove(idStr);
        rebuildRowControlList();
        AuroraConfig.save();
    }

    /** Re-syncs the host-visible control list to the sorted visual row order (mutation-time only). */
    private void rebuildRowControlList() {
        List<com.aurora.client.ui.interaction.SemanticActionControl> fresh = new ArrayList<>();
        List<String> sortedIds = new ArrayList<>(AuroraConfig.get().itemScales.keySet());
        Collections.sort(sortedIds);
        // The search-then-add pill hosts first (it sits above the row list);
        // then reading order per row: the disclosure (chevron side), then
        // the remove action (trailing edge).
        fresh.add(addAction.interactionControl());
        for (String id : sortedIds) {
            fresh.add(rowDiscloseAction(id).interactionControl());
            fresh.add(rowRemoveAction(id).interactionControl());
        }
        rowControlList = fresh; // fresh instance — the host diff sees the swap
    }

    /** C-4: the add control + two controls per row (disclosure + remove), in visual order. */
    @Override
    public List<com.aurora.client.ui.interaction.SemanticActionControl> interactionControls() {
        // Mutation paths rebuild the list themselves; this count check is the
        // safety net for externally-changed configs (a stale control must
        // never outlive its row).
        if (rowControlList.size() != AuroraConfig.get().itemScales.size() * 2 + 1) {
            rebuildRowControlList();
        }
        return rowControlList;
    }

    public ItemScaleSetting() {
        super("Item Scale Configurator");
        Font font = Minecraft.getInstance().font;
        this.searchField = new EditBox(font, 0, 0, 150, 18, Component.literal("Search Items..."));
        this.searchField.setHint(Component.literal("Search items... (e.g. sword)"));
        // Disable the vanilla border — we draw our own hi-res AA rounded
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
            h += ITEM_ROW_STRIDE; // Row header height
            if (expandedStates.getOrDefault(id, false)) {
                h += 7 * 36; // 7 sliders, each is 36px tall
            }
        }
        return h;
    }

    /**
     * Pre-dim surface (the split's surface half): the "+" add button's
     * raised glass, plus the search field's own pre-dim surface (driven
     * through {@link GlassEditBox} — {@code EditBoxMixin} carries the same
     * frame-stamp scheme). The field's geometry is set HERE as well because
     * the pass runs before {@link #render} positions it; a scroll frame
     * would otherwise drive the field's surface at a one-frame-stale rect.
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

        // 1. Search bar — the themed background, centered text, and
        // outline are all drawn by EditBoxMixin (which fully replaces
        // the vanilla EditBox render on FeatureDetailScreen), so we
        // just position the field and let it render itself.
        int searchW = width - 56;
        searchField.setX(x + 12);
        searchField.setY(y + 6);
        searchField.setWidth(searchW);
        searchField.render(ctx, mouseX, mouseY, 0f);

        // Draw "+" add button — raised glass (flat pill on decline). If the
        // screen ran this row's glass pass this frame the surface is already
        // on screen UNDER the dim and only its result matters here;
        // otherwise (legacy frame order) it is painted in place now through
        // the shared GlassSurface helper (identical calls). C-4 rollout: the
        // canonical hover (one animator) drives the flat fallback AND the
        // glyph — the whole action eases (was an immediate boolean).
        int plusX = x + width - 36;
        int plusY = y + 5;
        addAction.syncChannels(plusX, plusY, 24, 20, mouseX, mouseY);
        float plusT = addAction.hoverT();
        int plusBg = ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND),
                (int) (0x2E + (0x66 - 0x2E) * plusT));
        int plusBorder = AuroraAnim.lerpArgb(
                AuroraTheme.BORDER_OFF, AuroraTheme.BORDER_ON_HOVER, plusT);
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
        addAction.paintGlyph(ctx, tr, plusX, plusY, 24, 20);

        // Draw "suggested item" preview if found — with its real icon
        // (crisp flat-sprite path) beside the text.
        int currentY = y + 32;
        if (foundItem != null) {
            String name = foundItem.getName(foundItem.getDefaultInstance()).getString();
            ItemSpriteRenderer.renderIcon(ctx, foundItem.getDefaultInstance(), x + 12, currentY - 2);
            ctx.drawString(tr, "Add: " + name, x + 12 + ICON_SIZE + 4, currentY,
                    ThemeManager.color(ThemeToken.SEMANTIC_SUCCESS), false);
            currentY += 12;
        }

        // 2. Draw Customized Items List
        List<String> sortedIds = new ArrayList<>(cfg.itemScales.keySet());
        Collections.sort(sortedIds);

        for (String idStr : sortedIds) {
            boolean expanded = expandedStates.getOrDefault(idStr, false);
            int rowY = currentY;

            // Header Background Panel — the hover band matches so there is
            // no hover-without-click strip at the row's bottom edge.
            boolean rowHover = Widget.inBounds(mouseX, mouseY, x + 10, rowY, width - 20, ITEM_ROW_H);
            AuroraShapes.panel(ctx, x + 10, rowY, width - 20, ITEM_ROW_H,
                    ThemeManager.withAlpha(ThemeManager.color(ThemeToken.ON_BACKGROUND), 0x1A),
                    AuroraTheme.RADIUS_SMALL);
            if (rowHover) {
                AuroraShapes.outline(ctx, x + 10, rowY, width - 20, ITEM_ROW_H, AuroraTheme.BORDER_OFF, AuroraTheme.RADIUS_SMALL);
            }

            // Real item icon (crisp flat-sprite path, 3D fallback), then
            // the humanized label beside it.
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(idStr));
            int textPadX = x + 20;
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = item.getDefaultInstance();
                ItemSpriteRenderer.renderIcon(ctx, stack, x + 14, rowY + (ITEM_ROW_H - ICON_SIZE) / 2);
                textPadX = x + 20 + ICON_SIZE + 4;
            }
            String labelStr = humanizeItemId(idStr);
            // §4 subtitle block: the label sits at the top of a 20px block
            // (label + gap + subtitle) centered in the row, mirroring
            // FeatureSetting.drawValueLine's beneath-label placement.
            int textTop = rowY + (ITEM_ROW_H - 20) / 2;
            ctx.drawString(tr, labelStr, textPadX, textTop, AuroraTheme.IOS_LABEL, false);

            // §4 live-value subtitle: collapsed rows hide all seven
            // configured values behind the expand chevron, so the headline
            // figure — the scale this feature is named for — surfaces under
            // the label (same idiom/color as FeatureSetting.drawValueLine,
            // drawn in-widget because these rows aren't FeatureSettings).
            // Only on COLLAPSED rows: expanded, the scale slider's own
            // readout shows it at a glance and the exclusion clause governs.
            // One map lookup + string build per row per frame — the same
            // cost class as the hitreg alert suppliers.
            if (!expanded) {
                AuroraConfig.ItemScaleData d = cfg.itemScales.get(idStr);
                if (d != null) {
                    ctx.drawString(tr, String.format(java.util.Locale.ROOT, "Scale %.2f\u00d7", d.scale),
                            textPadX, textTop + tr.lineHeight + 2, AuroraTheme.TEXT_SECONDARY, false);
                }
            }

            // chevron and trash icon
            int rightX = x + width - 24;
            // Remove action (C-4 pilot A): the Material close glyph through
            // the icon-action primitive — canonical 140 ms hover (tertiary →
            // error red, animated — was immediate), focus hairline, and the
            // control sync, in the same 16×16 zone the click walk tests.
            rowRemoveAction(idStr).paint(ctx, tr, rightX - 16, rowY + (ITEM_ROW_H - 16) / 2, 16, 16,
                    mouseX, mouseY);

            // Disclosure action (C-4 rollout): the expand/collapse chevron as
            // a stateful disclosure icon action. Same Material glyph pair the
            // static path drew (the EnumSetting convention); the pointer
            // target is the row header body (the legacy toggle zone), the
            // hover animates the glyph secondary→label, and the focus
            // hairline paints on the chevron box — where keyboard focus for
            // this row visually lives.
            com.aurora.client.ui.component.IconAction disclose = rowDiscloseAction(idStr);
            disclose.syncChannels(x + 10, rowY, width - 50, ITEM_ROW_H, mouseX, mouseY);
            disclose.paintGlyph(ctx, tr, rightX - 38, rowY + (ITEM_ROW_H - 16) / 2, 16, 16);

            currentY += ITEM_ROW_STRIDE;

            if (expanded) {
                // Get or create sliders for this item
                List<SliderSetting> sliders = getOrCreateSliders(idStr);
                for (SliderSetting s : sliders) {
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

    private List<SliderSetting> getOrCreateSliders(String idStr) {
        return itemSliders.computeIfAbsent(idStr, id -> {
            List<SliderSetting> list = new ArrayList<>();
            // Getter and setters referencing config directly
            list.add(SliderSetting.of("Scale",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.scale : 1.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.scale = (float) v;
                    }, 0.1, 2.0));

            list.add(SliderSetting.of("Translation X",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationX : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationX = (float) v;
                    }, -2.0, 2.0));

            list.add(SliderSetting.of("Translation Y",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationY : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationY = (float) v;
                    }, -2.0, 2.0));

            list.add(SliderSetting.of("Translation Z",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.translationZ : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.translationZ = (float) v;
                    }, -2.0, 2.0));

            list.add(SliderSetting.of("Pitch",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.pitch : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.pitch = (float) v;
                    }, -180.0, 180.0));

            list.add(SliderSetting.of("Yaw",
                    () -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        return d != null ? d.yaw : 0.0;
                    },
                    v -> {
                        AuroraConfig.ItemScaleData d = AuroraConfig.get().itemScales.get(id);
                        if (d != null) d.yaw = (float) v;
                    }, -180.0, 180.0));

            list.add(SliderSetting.of("Roll",
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

            // Click on "+" button (C-4 rollout): routes through the semantic
            // action (exactly-once + the one click); the disabled gate IS
            // the can-add state, so a no-match or already-listed click falls
            // through to the legacy fallback and stays unconsumed exactly
            // as a no-match click always did.
            int plusX = rowX + rowWidth - 36;
            if (mouseX >= plusX && mouseX < plusX + 24) {
                if (addAction.clicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (!canAddFoundItem()) return false;
                addFoundItem();
                return true;
            }
            return true;
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

            if (mouseY >= rowHeaderY && mouseY < rowHeaderY + ITEM_ROW_H) {
                int rightX = rowX + rowWidth - 24;
                // Remove click (C-4 pilot A): routes through the semantic
                // action — exactly-once activation + the one click — with the
                // legacy direct path beside it for the pre-first-ask window
                // (the EnumSetting discipline). Both land in removeItem.
                if (mouseX >= rightX - 16 && mouseX < rightX) {
                    com.aurora.client.ui.component.IconAction remove = rowRemoveActions.get(idStr);
                    if (remove != null && remove.clicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    removeItem(idStr);
                    return true;
                }

                // Expand / Collapse click (C-4 rollout): routes through the
                // stateful disclosure action — exactly-once toggle + the one
                // click — with the shared toggle path beside it for the
                // pre-first-ask window (the EnumSetting discipline).
                if (mouseX >= rowX + 10 && mouseX < rightX - 16) {
                    com.aurora.client.ui.component.IconAction disclose = rowDiscloseActions.get(idStr);
                    if (disclose != null && disclose.clicked(mouseX, mouseY, button)) {
                        return true;
                    }
                    toggleExpanded(idStr);
                    return true;
                }
            }

            currentY += ITEM_ROW_STRIDE;

            if (expanded) {
                List<SliderSetting> sliders = getOrCreateSliders(idStr);
                for (SliderSetting s : sliders) {
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
            currentY += ITEM_ROW_STRIDE;

            if (expanded) {
                List<SliderSetting> sliders = getOrCreateSliders(idStr);
                for (SliderSetting s : sliders) {
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
        for (List<SliderSetting> sliders : itemSliders.values()) {
            for (SliderSetting s : sliders) {
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

    /**
     * Reset the search state on screen close so reopening starts clean and
     * this row cannot hold the static focus registry across screens — same
     * lifecycle contract as ParticleConfigSetting.
     */
    @Override
    public void onDetailScreenClose() {
        searchField.setValue("");
        searchField.setFocused(false);
        searchField.moveCursorToEnd(false);
        releaseFocus();
        foundItem = null;
    }
}
