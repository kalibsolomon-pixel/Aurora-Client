package com.aurora.client.screen;

import com.aurora.client.adapter.NativeUi;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.resourcepack.PackFolderTree;
import com.aurora.client.resourcepack.PackIconLoader;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.ui.component.ScrollbarChrome;
import com.aurora.client.ui.component.ThemedScreen;
import com.aurora.client.ui.component.Toast;
import com.aurora.client.ui.interaction.FocusReveal;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticControlGroup;
import com.aurora.client.ui.interaction.SemanticControlHost;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.OverlayLayout;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.SmoothScroll;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackRepository;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aurora's folder-aware Resource Pack Manager.
 *
 * <p>Supports nested organizing subfolders inside {@code resourcepacks/},
 * clickable folder breadcrumbs, global recursive search across all subfolders,
 * pack icons loaded asynchronously, and full selection + load-order management
 * backed by Minecraft's {@link PackSelectionModel}.
 *
 * <p>Not a window (owner, 2026-11-23): it keeps vanilla's pack screen layout (header, two
 * 200 px lists 30 px apart, 33 px footer, vanilla background, separators and list texture) and
 * replaces the elements inside it with Aurora's: search, breadcrumbs, pack rows and buttons.
 */
public class ResourcePackManagerScreen extends Screen implements ThemedScreen {

    private static final AtomicBoolean VANILLA_BYPASS = new AtomicBoolean(false);

    public static void allowVanillaNext() { VANILLA_BYPASS.set(true); }
    public static boolean consumeVanillaBypass() { return VANILLA_BYPASS.getAndSet(false); }

    /** Vanilla's pack row height; folders are a step shorter. */
    private static final int ROW_H = 36;
    private static final int FOLDER_ROW_H = 28;
    private static final int ROW_GAP = 4;
    private static final int ICON_SIZE = 28;
    /** Vanilla's pack screen geometry: list width, the gap between the lists, the footer band. */
    private static final int LIST_W = 200;
    private static final int LIST_GAP = 30;
    private static final int FOOTER_H = 33;
    private static final int SEARCH_W = 200;
    private static final int SEARCH_H = 15;
    private static final int BUTTON_GAP = 8;
    private static final Identifier MENU_LIST_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/menu_list_background.png");
    private static final Identifier INWORLD_MENU_LIST_BACKGROUND =
            Identifier.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    private static final int ACTION_BTN = 18;
    private static final int SCROLLBAR_W = 4;

    private static final String ICON_FOLDER = "\uE2C7";
    private static final String ICON_UP = "\uE5D8";
    private static final String ICON_DOWN = "\uE5DB";
    private static final String ICON_BACK = "\uE5C4";
    private static final String ICON_ADD = "\uE145";
    private static final String ICON_CLOSE = "\uE5CD";
    private static final String ICON_CHEVRON = "\uE5CC";
    private static final String ICON_PACK = "\uE1A1";
    private static final String ICON_SEARCH = "\uE8B6";

    private static final String[] SEARCH_HINTS = {
            "Search resource packs across all folders", "Search all folders", "Search"};

    private final PackSelectionModel model;
    private final Path packDir;
    private final Screen returnParent;
    private final Toast toast = new Toast();

    private final SmoothScroll leftScroll = new SmoothScroll(55.0).boundedMotion();
    private final SmoothScroll rightScroll = new SmoothScroll(55.0).boundedMotion();

    private EditBox searchField;
    private String searchQuery = "";
    private String currentFolder = "";
    private Set<String> diskFolders = Set.of();

    private List<PackSelectionModel.Entry> unselectedEntries = List.of();
    private List<PackSelectionModel.Entry> selectedEntries = List.of();
    private Map<String, Pack> packById = Map.of();
    private PackFolderTree.FolderView currentView;

    // Layout geometry
    private int titleY, headerH, footerTop;
    private int searchX, searchY, searchW, searchH;
    private int crumbY, crumbH;
    private int leftX, leftW, rightX, rightW;
    private int listTop, listH;

    // Clickable breadcrumb hitboxes computed on render
    private final List<CrumbHit> crumbHits = new ArrayList<>();
    private record CrumbHit(int x, int y, int w, int h, String folderPath) {}

    // Semantic controls for keyboard navigation, focus reveal, and narration
    private final SemanticControlHost semanticHost = new SemanticControlHost(
            this::addWidget,
            this::removeWidget,
            this::getFocused,
            this::setFocused);
    private final List<SemanticActionControl> crumbControls = new ArrayList<>();
    private SemanticActionControl upFolderControl;
    private final List<SemanticActionControl> folderControls = new ArrayList<>();
    private final List<SemanticActionControl> availablePackControls = new ArrayList<>();
    private final List<SelectedRowControls> selectedRowControls = new ArrayList<>();
    private final Map<SemanticActionControl, Integer> leftControlTop = new HashMap<>();
    private final Map<SemanticActionControl, Integer> leftControlHeight = new HashMap<>();
    private final Map<SemanticActionControl, Integer> rightControlTop = new HashMap<>();
    private final Map<SemanticActionControl, Integer> rightControlHeight = new HashMap<>();
    private final Map<String, SemanticActionControl> focusKeys = new HashMap<>();
    private String pendingFocusKey;

    private record SelectedRowControls(String packId, SemanticActionControl up,
                                       SemanticActionControl down, SemanticActionControl remove) {
        SemanticActionControl firstFocusable() {
            if (up != null && up.action().enabled()) return up;
            if (down != null && down.action().enabled()) return down;
            if (remove != null && remove.action().enabled()) return remove;
            return null;
        }
    }

    /** Opens the manager directly from an Aurora screen (reloading packs first). */
    public static ResourcePackManagerScreen open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        PackSelectionModel model = new PackSelectionModel(
                entry -> {},
                pack -> null,
                repo,
                r -> {
                    mc.options.updateResourcePacks(r);
                    NativeUi.setScreen(mc, parent);
                });
        return new ResourcePackManagerScreen(model, mc.getResourcePackDirectory(), parent);
    }

    public ResourcePackManagerScreen(PackSelectionModel model, Path packDir, Screen returnParent) {
        super(Component.literal("Resource Packs"));
        this.model = model;
        this.packDir = packDir;
        this.returnParent = returnParent;
        rescanDiskAndModel(false);
    }

    private void rescanDiskAndModel(boolean reloadRepo) {
        if (reloadRepo) {
            model.findNewPacks();
            PackIconLoader.invalidateAll();
        }
        diskFolders = PackFolderTree.discoverOrganizingFolderPaths(packDir);
        rebuildLists();
    }

    private void rebuildLists() {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Pack> byId = new HashMap<>();
        if (mc != null && mc.getResourcePackRepository() != null) {
            for (Pack p : mc.getResourcePackRepository().getAvailablePacks()) {
                byId.put(p.getId(), p);
            }
        }
        packById = byId;

        unselectedEntries = model.getUnselected().toList();
        selectedEntries = model.getSelected().toList();

        List<PackFolderTree.PackItem> items = new ArrayList<>(unselectedEntries.size());
        for (int i = 0; i < unselectedEntries.size(); i++) {
            PackSelectionModel.Entry e = unselectedEntries.get(i);
            String id = e.getId();
            items.add(new PackFolderTree.PackItem(
                    id,
                    e.getTitle().getString(),
                    e.getDescription().getString(),
                    PackFolderTree.folderOfPackId(id),
                    i));
        }

        // If currentFolder vanished on disk and has no packs, step back to root cleanly.
        if (!currentFolder.isEmpty() && !diskFolders.contains(currentFolder)) {
            boolean hasAny = items.stream().anyMatch(p ->
                    p.folderPath().equals(currentFolder) || p.folderPath().startsWith(currentFolder + "/"));
            if (!hasAny) currentFolder = "";
        }

        currentView = PackFolderTree.buildView(items, diskFolders, currentFolder, searchQuery);
        rebuildSemanticControls();
    }

    private void rebuildSemanticControls() {
        GuiEventListener prevFocused = this.getFocused();
        boolean preserveSearchFocus = (searchField != null && prevFocused == searchField);
        String restoreKey = pendingFocusKey;
        pendingFocusKey = null;
        if (restoreKey == null && prevFocused instanceof SemanticActionControl prevCtrl) {
            restoreKey = keyOfControl(prevCtrl);
        }

        semanticHost.clear();
        crumbControls.clear();
        upFolderControl = null;
        folderControls.clear();
        availablePackControls.clear();
        selectedRowControls.clear();
        leftControlTop.clear();
        leftControlHeight.clear();
        rightControlTop.clear();
        rightControlHeight.clear();
        focusKeys.clear();

        if (currentView == null) return;

        // 1. Breadcrumbs (horizontal roving group)
        SemanticControlGroup crumbGroup = SemanticControlGroup.horizontal();
        List<PackFolderTree.Breadcrumb> crumbs = currentView.breadcrumbs();
        for (int i = 0; i < crumbs.size(); i++) {
            PackFolderTree.Breadcrumb crumb = crumbs.get(i);
            boolean isCurrent = (i == crumbs.size() - 1) && !currentView.searching();
            String path = crumb.path();
            SemanticActionControl ctrl = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Folder: " + crumb.label()),
                    () -> Component.literal("Navigates to this resource pack folder."),
                    () -> isCurrent ? Component.literal("Current folder") : Component.empty(),
                    () -> !isCurrent,
                    () -> {
                        if (!searchQuery.isEmpty() && searchField != null) {
                            searchQuery = "";
                            searchField.setValue("");
                        }
                        pendingFocusKey = "left:first";
                        enterFolder(path);
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            crumbGroup.attach(ctrl);
            semanticHost.register(ctrl);
            crumbControls.add(ctrl);
            focusKeys.put("crumb:" + path, ctrl);
        }

        // 2. Left pane (vertical roving group: ".." + folders + available packs)
        SemanticControlGroup leftGroup = SemanticControlGroup.vertical();
        int leftOffset = 0;
        if (!currentView.searching() && !currentFolder.isEmpty()) {
            String parentPath = PackFolderTree.parentFolder(currentFolder);
            SemanticActionControl upCtrl = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Parent folder"),
                    () -> Component.literal("Moves up one resource pack folder."),
                    () -> Component.literal("Up a folder"),
                    () -> true,
                    () -> {
                        pendingFocusKey = "left:first";
                        enterFolder(parentPath);
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            leftGroup.attach(upCtrl);
            semanticHost.register(upCtrl);
            upFolderControl = upCtrl;
            leftControlTop.put(upCtrl, leftOffset);
            leftControlHeight.put(upCtrl, FOLDER_ROW_H);
            focusKeys.put("left:up", upCtrl);
            focusKeys.putIfAbsent("left:first", upCtrl);
            leftOffset += FOLDER_ROW_H + ROW_GAP;
        }

        for (int i = 0; i < currentView.folders().size(); i++) {
            PackFolderTree.FolderEntry folder = currentView.folders().get(i);
            String countLabel = folder.packCount() == 0
                    ? "Empty"
                    : folder.packCount() + (folder.packCount() == 1 ? " pack" : " packs");
            SemanticActionControl ctrl = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Folder: " + folder.name()),
                    () -> Component.literal("Opens this resource pack folder."),
                    () -> Component.literal(countLabel),
                    () -> true,
                    () -> {
                        pendingFocusKey = "left:first";
                        enterFolder(folder.path());
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            leftGroup.attach(ctrl);
            semanticHost.register(ctrl);
            folderControls.add(ctrl);
            leftControlTop.put(ctrl, leftOffset);
            leftControlHeight.put(ctrl, FOLDER_ROW_H);
            focusKeys.put("left:folder:" + folder.path(), ctrl);
            focusKeys.putIfAbsent("left:first", ctrl);
            leftOffset += FOLDER_ROW_H + ROW_GAP;
        }

        for (int i = 0; i < currentView.packs().size(); i++) {
            final int packIdx = i;
            PackFolderTree.PackItem item = currentView.packs().get(i);
            PackSelectionModel.Entry entry = item.originalIndex() >= 0 && item.originalIndex() < unselectedEntries.size()
                    ? unselectedEntries.get(item.originalIndex()) : null;
            boolean selectable = entry != null && entry.canSelect();
            String sub = buildSubtitle(item.folderPath(), item.description(),
                    entry != null ? entry.getCompatibility() : PackCompatibility.COMPATIBLE,
                    currentView.searching());
            SemanticActionControl ctrl = new SemanticActionControl(SemanticAction.button(
                    Component.literal(item.title()),
                    () -> Component.literal("Select resource pack"),
                    () -> Component.literal(sub),
                    () -> selectable,
                    () -> selectAvailablePack(packIdx, true)),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            leftGroup.attach(ctrl);
            semanticHost.register(ctrl);
            availablePackControls.add(ctrl);
            leftControlTop.put(ctrl, leftOffset);
            leftControlHeight.put(ctrl, ROW_H);
            focusKeys.put("left:pack:" + item.id(), ctrl);
            focusKeys.put("left:pack-idx:" + packIdx, ctrl);
            focusKeys.putIfAbsent("left:first", ctrl);
            leftOffset += ROW_H + ROW_GAP;
        }

        // 3. Right pane (selected packs: Move Up, Move Down, Remove)
        SemanticControlGroup rightGroup = SemanticControlGroup.vertical();
        int rightOffset = 0;
        for (int i = 0; i < selectedEntries.size(); i++) {
            final int rowIdx = i;
            PackSelectionModel.Entry entry = selectedEntries.get(i);
            String packId = entry.getId();
            String title = entry.getTitle().getString();
            String folder = PackFolderTree.folderOfPackId(packId);
            String sub = buildSubtitle(folder, entry.getDescription().getString(),
                    entry.getCompatibility(), !folder.isEmpty());

            SemanticActionControl upBtn = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Move " + title + " up"),
                    () -> Component.literal("Moves this selected resource pack higher in load priority."),
                    () -> Component.literal(sub),
                    entry::canMoveUp,
                    () -> reorderSelectedPack(rowIdx, true)),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            SemanticActionControl downBtn = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Move " + title + " down"),
                    () -> Component.literal("Moves this selected resource pack lower in load priority."),
                    () -> Component.literal(sub),
                    entry::canMoveDown,
                    () -> reorderSelectedPack(rowIdx, false)),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            SemanticActionControl removeBtn = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Remove " + title),
                    () -> Component.literal("Removes this resource pack from the selected list."),
                    () -> Component.literal(sub),
                    entry::canUnselect,
                    () -> unselectSelectedPack(rowIdx)),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);

            rightGroup.attach(upBtn);
            rightGroup.attach(downBtn);
            rightGroup.attach(removeBtn);
            semanticHost.register(upBtn);
            semanticHost.register(downBtn);
            semanticHost.register(removeBtn);

            SelectedRowControls rowCtrls = new SelectedRowControls(packId, upBtn, downBtn, removeBtn);
            selectedRowControls.add(rowCtrls);

            rightControlTop.put(upBtn, rightOffset);
            rightControlHeight.put(upBtn, ROW_H);
            rightControlTop.put(downBtn, rightOffset);
            rightControlHeight.put(downBtn, ROW_H);
            rightControlTop.put(removeBtn, rightOffset);
            rightControlHeight.put(removeBtn, ROW_H);

            focusKeys.put("right:" + packId + ":up", upBtn);
            focusKeys.put("right:" + packId + ":down", downBtn);
            focusKeys.put("right:" + packId + ":remove", removeBtn);
            SemanticActionControl rowDefault = rowCtrls.firstFocusable();
            if (rowDefault != null) {
                focusKeys.put("right:idx:" + rowIdx, rowDefault);
                focusKeys.putIfAbsent("right:first", rowDefault);
            }
            rightOffset += ROW_H + ROW_GAP;
        }

        syncScrollFocusTargets();

        if (preserveSearchFocus) {
            this.setFocused(searchField);
        } else if (restoreKey != null) {
            SemanticActionControl restored = resolveRestoreTarget(restoreKey);
            if (restored != null) {
                this.setFocused(restored);
                revealFocusedControl(restored);
            }
        }
    }

    private String keyOfControl(SemanticActionControl control) {
        for (Map.Entry<String, SemanticActionControl> e : focusKeys.entrySet()) {
            if (e.getValue() == control && !e.getKey().endsWith(":first")) {
                return e.getKey();
            }
        }
        return null;
    }

    private SemanticActionControl resolveRestoreTarget(String key) {
        SemanticActionControl direct = focusKeys.get(key);
        if (direct != null && direct.canReceiveFocus()) return direct;
        if (key.startsWith("right:")) {
            int lastColon = key.lastIndexOf(':');
            if (lastColon > 6) {
                String packId = key.substring(6, lastColon);
                for (SelectedRowControls row : selectedRowControls) {
                    if (row.packId().equals(packId)) {
                        SemanticActionControl fallback = row.firstFocusable();
                        if (fallback != null && fallback.canReceiveFocus()) return fallback;
                    }
                }
            }
            if (key.startsWith("right:idx:")) {
                try {
                    int idx = Integer.parseInt(key.substring("right:idx:".length()));
                    for (int i = Math.min(idx, selectedRowControls.size() - 1); i >= 0; i--) {
                        SemanticActionControl fallback = selectedRowControls.get(i).firstFocusable();
                        if (fallback != null && fallback.canReceiveFocus()) return fallback;
                    }
                } catch (NumberFormatException ignored) {}
            }
            SemanticActionControl firstRight = focusKeys.get("right:first");
            if (firstRight != null && firstRight.canReceiveFocus()) return firstRight;
        }
        if (key.startsWith("left:pack-idx:")) {
            try {
                int idx = Integer.parseInt(key.substring("left:pack-idx:".length()));
                for (int i = Math.min(idx, availablePackControls.size() - 1); i >= 0; i--) {
                    SemanticActionControl c = availablePackControls.get(i);
                    if (c.canReceiveFocus()) return c;
                }
            } catch (NumberFormatException ignored) {}
        }
        SemanticActionControl firstLeft = focusKeys.get("left:first");
        return firstLeft != null && firstLeft.canReceiveFocus() ? firstLeft : null;
    }

    private void syncScrollFocusTargets() {
        for (SemanticActionControl ctrl : leftControlTop.keySet()) {
            ctrl.setScrollFocusTarget(true);
        }
        for (SemanticActionControl ctrl : rightControlTop.keySet()) {
            ctrl.setScrollFocusTarget(true);
        }
    }

    private void selectAvailablePack(int packIdx, boolean keepFocusInLeft) {
        if (currentView == null || packIdx < 0 || packIdx >= currentView.packs().size()) return;
        PackFolderTree.PackItem item = currentView.packs().get(packIdx);
        if (item.originalIndex() < 0 || item.originalIndex() >= unselectedEntries.size()) return;
        PackSelectionModel.Entry entry = unselectedEntries.get(item.originalIndex());
        if (!entry.canSelect()) return;
        if (keepFocusInLeft) {
            pendingFocusKey = "left:pack-idx:" + packIdx;
        }
        entry.select();
        rebuildLists();
    }

    private void reorderSelectedPack(int rowIdx, boolean moveUp) {
        if (rowIdx < 0 || rowIdx >= selectedEntries.size()) return;
        PackSelectionModel.Entry entry = selectedEntries.get(rowIdx);
        if (moveUp) {
            if (!entry.canMoveUp()) return;
            pendingFocusKey = "right:" + entry.getId() + ":up";
            entry.moveUp();
        } else {
            if (!entry.canMoveDown()) return;
            pendingFocusKey = "right:" + entry.getId() + ":down";
            entry.moveDown();
        }
        rebuildLists();
    }

    private void unselectSelectedPack(int rowIdx) {
        if (rowIdx < 0 || rowIdx >= selectedEntries.size()) return;
        PackSelectionModel.Entry entry = selectedEntries.get(rowIdx);
        if (!entry.canUnselect()) return;
        pendingFocusKey = "right:idx:" + rowIdx;
        entry.unselect();
        rebuildLists();
    }

    private void revealFocusedControl(GuiEventListener focused) {
        if (!(focused instanceof SemanticActionControl control) || listH <= 0) return;
        Integer lTop = leftControlTop.get(control);
        if (lTop != null) {
            int h = leftControlHeight.getOrDefault(control, ROW_H);
            int maxScroll = Math.max(0, leftContentHeight() - listH);
            double top = listTop + lTop;
            double target = OverlayLayout.scrollToReveal(leftScroll.target(), maxScroll,
                    top, top + h, listTop, listTop + listH, FocusReveal.MARGIN);
            if (AuroraConfig.get().reducedMotion) leftScroll.set(target);
            else if (target != leftScroll.target()) leftScroll.retarget(target, maxScroll);
            return;
        }
        Integer rTop = rightControlTop.get(control);
        if (rTop != null) {
            int h = rightControlHeight.getOrDefault(control, ROW_H);
            int maxScroll = Math.max(0, rightContentHeight() - listH);
            double top = listTop + rTop;
            double target = OverlayLayout.scrollToReveal(rightScroll.target(), maxScroll,
                    top, top + h, listTop, listTop + listH, FocusReveal.MARGIN);
            if (AuroraConfig.get().reducedMotion) rightScroll.set(target);
            else if (target != rightScroll.target()) rightScroll.retarget(target, maxScroll);
        }
    }

    @Override
    protected void init() {
        semanticHost.beginRebuild();
        int centerX = this.width / 2;
        int listW = Math.max(1, Math.min(LIST_W, (this.width - LIST_GAP - 2 * 8) / 2));
        leftW = listW;
        rightW = listW;
        leftX = centerX - LIST_GAP / 2 - listW;
        rightX = centerX + LIST_GAP / 2;

        // Header: the title, then the search field under it, both centred.
        titleY = 6;
        searchW = Math.min(SEARCH_W, this.width - 16);
        searchH = SEARCH_H;
        searchX = centerX - searchW / 2;
        searchY = titleY + this.font.lineHeight + 5;
        headerH = searchY + searchH + 5;

        searchField = new EditBox(this.font, searchX, searchY, searchW, searchH, Component.literal("Search"));
        searchField.setMaxLength(120);
        searchField.setHint(Component.literal(searchHint(searchW - 20)));
        searchField.setValue(searchQuery);
        searchField.setResponder(q -> {
            searchQuery = q;
            leftScroll.set(0);
            rebuildLists();
        });
        this.addRenderableWidget(searchField);

        // Footer: one centred row of buttons. Labels shorten, then the extras drop out
        // (Modrinth, Refresh, Vanilla) before anything can run off the screen.
        footerTop = this.height - FOOTER_H;
        layoutFooter(this.height - FOOTER_H + (FOOTER_H - UiDensity.HEADER_BUTTON_H) / 2 + 1);

        // Each list: its heading (breadcrumbs on the left, "Selected" on the right), then its rows.
        crumbY = headerH + 4;
        crumbH = 18;
        listTop = crumbY + crumbH + 2;
        listH = Math.max(0, footerTop - 4 - listTop);
        semanticHost.finishRebuild();
        syncScrollFocusTargets();
    }

    private record FooterButton(String label, String shortLabel, String tooltip, Runnable action, boolean optional) {}

    private void layoutFooter(int y) {
        List<FooterButton> buttons = new ArrayList<>(5);
        if (AuroraConfig.get().resourcepackBrowserEnabled) {
            buttons.add(new FooterButton("Modrinth", "Modrinth", "Browse and install resource packs from Modrinth.",
                    () -> NativeUi.setScreen(this.minecraft, new ResourcePackBrowserScreen(this)), true));
        }
        buttons.add(new FooterButton("Open folder", "Folder", "Opens the current resource packs folder in your file manager.",
                this::openCurrentFolder, false));
        buttons.add(new FooterButton("Refresh", "Sync", "Re-scans resourcepacks and all subfolders.", () -> {
            rescanDiskAndModel(true);
            toast.flash("Rescanned resource packs", 1800L);
        }, true));
        buttons.add(new FooterButton("Vanilla", "Vanilla", "Switches to Minecraft's default resource pack screen.",
                this::openVanillaScreen, true));
        buttons.add(new FooterButton("Done", "Done", "Applies selected resource packs and closes.", this::commitAndClose, false));

        int room = this.width - 16;
        boolean compact = footerWidth(buttons, false) > room;
        while (footerWidth(buttons, compact) > room && buttons.stream().anyMatch(FooterButton::optional)) {
            for (int i = 0; i < buttons.size(); i++) {
                if (buttons.get(i).optional()) {
                    buttons.remove(i);
                    break;
                }
            }
        }
        int x = (this.width - footerWidth(buttons, compact)) / 2;
        for (FooterButton b : buttons) {
            String label = compact ? b.shortLabel() : b.label();
            int w = buttonWidth(label);
            ButtonWidget widget = ButtonWidget.semantic(x, y, w, UiDensity.HEADER_BUTTON_H,
                    Component.literal(label), b.tooltip(), b.action());
            if (b.label().equals("Done")) widget.primary(true);
            this.addRenderableWidget(widget);
            x += w + BUTTON_GAP;
        }
    }

    /** Label plus the button's inner pad and border inset: the label never ellipsizes. */
    private int buttonWidth(String label) {
        return Math.max(46, this.font.width(label) + 22);
    }

    private int footerWidth(List<FooterButton> buttons, boolean compact) {
        int total = 0;
        for (FooterButton b : buttons) total += buttonWidth(compact ? b.shortLabel() : b.label());
        return total + BUTTON_GAP * Math.max(0, buttons.size() - 1);
    }

    /** The longest search hint that fits {@code maxW}; the field's text inset and icon take the rest. */
    private String searchHint(int maxW) {
        for (String hint : SEARCH_HINTS) {
            if (this.font.width(hint) <= maxW) return hint;
        }
        return SEARCH_HINTS[SEARCH_HINTS.length - 1];
    }

    private void openCurrentFolder() {
        Path target = PackFolderTree.resolveSubfolder(packDir, currentFolder);
        if (target != null) {
            NativeUi.openPath(target);
            toast.flash("Opened " + (currentFolder.isEmpty() ? "resourcepacks" : currentFolder), 1800L);
        }
    }

    private void openVanillaScreen() {
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        allowVanillaNext();
        Screen back = returnParent != null ? returnParent : NativeUi.optionsScreen(mc, null);
        NativeUi.setScreen(mc, new PackSelectionScreen(
                mc.getResourcePackRepository(),
                repo -> {
                    mc.options.updateResourcePacks(repo);
                    NativeUi.setScreen(mc, back);
                },
                packDir,
                Component.translatable("resourcePack.title")));
    }

    private void commitAndClose() {
        model.commit();
        // If model.commit() didn't change the screen (defensive guard), return explicitly.
        if (this.minecraft != null && NativeUi.screen(this.minecraft) == this) {
            NativeUi.setScreen(this.minecraft, returnParent);
        }
    }

    @Override
    public void onClose() {
        commitAndClose();
    }

    private void enterFolder(String path) {
        currentFolder = PackFolderTree.normalizeFolder(path);
        leftScroll.set(0);
        rebuildLists();
    }

    private int leftContentHeight() {
        if (currentView == null) return 0;
        int h = 0;
        if (!currentView.searching() && !currentFolder.isEmpty()) {
            h += FOLDER_ROW_H + ROW_GAP;
        }
        h += currentView.folders().size() * (FOLDER_ROW_H + ROW_GAP);
        h += currentView.packs().size() * (ROW_H + ROW_GAP);
        return Math.max(0, h - ROW_GAP);
    }

    private int rightContentHeight() {
        if (selectedEntries.isEmpty()) return 0;
        return selectedEntries.size() * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseY >= listTop && mouseY <= listTop + listH) {
            if (mouseX >= leftX && mouseX <= leftX + leftW) {
                leftScroll.wheel(vertical, 28.0, Math.max(0, leftContentHeight() - listH));
                return true;
            }
            if (mouseX >= rightX && mouseX <= rightX + rightW) {
                rightScroll.wheel(vertical, 28.0, Math.max(0, rightContentHeight() - listH));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            // 1. Breadcrumb clicks
            for (CrumbHit hit : crumbHits) {
                if (mx >= hit.x && mx <= hit.x + hit.w && my >= hit.y && my <= hit.y + hit.h) {
                    if (!searchQuery.isEmpty() && searchField != null) {
                        searchQuery = "";
                        searchField.setValue("");
                    }
                    enterFolder(hit.folderPath);
                    return true;
                }
            }

            // 2. Left pane (Available folders & packs)
            if (mx >= leftX && mx <= leftX + leftW - SCROLLBAR_W - 2 && my >= listTop && my <= listTop + listH) {
                if (handleLeftClick(mx, my)) return true;
            }

            // 3. Right pane (Selected packs: up / down / remove)
            if (mx >= rightX && mx <= rightX + rightW - SCROLLBAR_W - 2 && my >= listTop && my <= listTop + listH) {
                if (handleRightClick(mx, my)) return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleLeftClick(double mx, double my) {
        if (currentView == null) return false;
        int scrollOff = (int) Math.round(leftScroll.current());
        int sectionOffset = 0;

        // ".." Up row
        if (!currentView.searching() && !currentFolder.isEmpty()) {
            int y = listTop + sectionOffset - scrollOff;
            if (my >= y && my <= y + FOLDER_ROW_H) {
                enterFolder(PackFolderTree.parentFolder(currentFolder));
                return true;
            }
            sectionOffset += FOLDER_ROW_H + ROW_GAP;
        }

        // Subfolder rows (O(visible))
        int folderCount = currentView.folders().size();
        PackFolderTree.VisibleRange folderRange = PackFolderTree.visibleRange(
                folderCount, sectionOffset, FOLDER_ROW_H, ROW_GAP, scrollOff, listH);
        for (int i = folderRange.first(); i < folderRange.lastExclusive(); i++) {
            int y = listTop + sectionOffset + i * (FOLDER_ROW_H + ROW_GAP) - scrollOff;
            if (my >= y && my <= y + FOLDER_ROW_H) {
                enterFolder(currentView.folders().get(i).path());
                return true;
            }
        }
        sectionOffset += folderCount * (FOLDER_ROW_H + ROW_GAP);

        // Pack rows (O(visible))
        int packCount = currentView.packs().size();
        PackFolderTree.VisibleRange packRange = PackFolderTree.visibleRange(
                packCount, sectionOffset, ROW_H, ROW_GAP, scrollOff, listH);
        for (int i = packRange.first(); i < packRange.lastExclusive(); i++) {
            int y = listTop + sectionOffset + i * (ROW_H + ROW_GAP) - scrollOff;
            if (my >= y && my <= y + ROW_H) {
                selectAvailablePack(i, false);
                return true;
            }
        }
        return false;
    }

    private boolean handleRightClick(double mx, double my) {
        int scrollOff = (int) Math.round(rightScroll.current());
        int cardW = rightW - SCROLLBAR_W - 4;
        PackFolderTree.VisibleRange visible = PackFolderTree.visibleRange(
                selectedEntries.size(), 0, ROW_H, ROW_GAP, scrollOff, listH);
        for (int i = visible.first(); i < visible.lastExclusive(); i++) {
            int y = listTop + i * (ROW_H + ROW_GAP) - scrollOff;
            if (my >= y && my <= y + ROW_H) {
                PackSelectionModel.Entry entry = selectedEntries.get(i);
                int btnY = y + (ROW_H - ACTION_BTN) / 2;
                int removeX = rightX + cardW - 6 - ACTION_BTN;
                int downX = removeX - 3 - ACTION_BTN;
                int upX = downX - 3 - ACTION_BTN;

                if (entry.canUnselect() && inBox(mx, my, removeX, btnY, ACTION_BTN, ACTION_BTN)) {
                    unselectSelectedPack(i);
                    return true;
                }
                if (entry.canMoveDown() && inBox(mx, my, downX, btnY, ACTION_BTN, ACTION_BTN)) {
                    reorderSelectedPack(i, false);
                    return true;
                }
                if (entry.canMoveUp() && inBox(mx, my, upX, btnY, ACTION_BTN, ACTION_BTN)) {
                    reorderSelectedPack(i, true);
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private static boolean inBox(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_ESCAPE) {
            if (searchField != null && !searchQuery.isEmpty()) {
                searchQuery = "";
                searchField.setValue("");
                searchField.setFocused(false);
                rebuildLists();
                return true;
            }
            if (!currentFolder.isEmpty()) {
                enterFolder(PackFolderTree.parentFolder(currentFolder));
                return true;
            }
            commitAndClose();
            return true;
        }
        if (key == InputConstants.KEY_BACKSPACE && (searchField == null || !searchField.isFocused())
                && !currentFolder.isEmpty()) {
            pendingFocusKey = "left:first";
            enterFolder(PackFolderTree.parentFolder(currentFolder));
            return true;
        }
        // Pressing Down from the search box jumps straight into the first matching pack/folder row.
        if (key == InputConstants.KEY_DOWN && searchField != null && searchField.isFocused()) {
            SemanticActionControl firstLeft = focusKeys.get("left:first");
            if (firstLeft != null && firstLeft.canReceiveFocus()) {
                searchField.setFocused(false);
                this.setFocused(firstLeft);
                revealFocusedControl(firstLeft);
                return true;
            }
        }

        GuiEventListener before = this.getFocused();
        // Horizontal roving between Move Up / Move Down / Remove within a selected pack row.
        if ((key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)
                && before instanceof SemanticActionControl currentCtrl
                && roveSelectedRowHorizontal(currentCtrl, key == InputConstants.KEY_RIGHT ? 1 : -1)) {
            revealFocusedControl(this.getFocused());
            return true;
        }

        boolean handled = SemanticControlGroup.rove(before, event, this::setFocused)
                || super.keyPressed(event);
        if (FocusReveal.navigation(event) && before != this.getFocused()) {
            revealFocusedControl(this.getFocused());
        }
        return handled;
    }

    private boolean roveSelectedRowHorizontal(SemanticActionControl current, int delta) {
        for (SelectedRowControls row : selectedRowControls) {
            SemanticActionControl[] actions = {row.up(), row.down(), row.remove()};
            int idx = -1;
            for (int i = 0; i < actions.length; i++) {
                if (actions[i] == current) { idx = i; break; }
            }
            if (idx < 0) continue;
            for (int step = 1; step < actions.length; step++) {
                SemanticActionControl candidate = actions[Math.floorMod(idx + step * delta, actions.length)];
                if (candidate != null && candidate.canReceiveFocus()) {
                    this.setFocused(candidate);
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        // Vanilla's list texture behind both lists, and its separators under the header and above the footer.
        boolean inWorld = this.minecraft != null && this.minecraft.level != null;
        Identifier list = inWorld ? INWORLD_MENU_LIST_BACKGROUND : MENU_LIST_BACKGROUND;
        int bandH = footerTop - headerH;
        if (bandH > 0) {
            Screen.extractMenuBackgroundTexture(g, list, 0, headerH, 0f, 0f, this.width, bandH);
        }
        g.blit(RenderPipelines.GUI_TEXTURED, inWorld ? Screen.INWORLD_HEADER_SEPARATOR : Screen.HEADER_SEPARATOR,
                0, headerH - 2, 0f, 0f, this.width, 2, 32, 2);
        g.blit(RenderPipelines.GUI_TEXTURED, inWorld ? Screen.INWORLD_FOOTER_SEPARATOR : Screen.FOOTER_SEPARATOR,
                0, footerTop, 0f, 0f, this.width, 2, 32, 2);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        PackIconLoader.drainReady();
        leftScroll.advance(Math.max(0, leftContentHeight() - listH));
        rightScroll.advance(Math.max(0, rightContentHeight() - listH));
        semanticHost.beginAvailabilitySweep();
        syncScrollFocusTargets();

        int onBg = MaterialSurface.color(ThemeToken.ON_BACKGROUND);
        int secondary = ThemeManager.color(ThemeToken.ON_BACKGROUND_SECONDARY);
        int accent = MaterialSurface.color(ThemeToken.ACCENT);

        // Title, centred like vanilla's.
        String headerTitle = AuroraFontRenderer.ellipsize(this.font, "Resource Packs", this.width - 16, 0);
        ctx.text(this.font, headerTitle, (this.width - this.font.width(headerTitle)) / 2, titleY, 0xFFFFFFFF, true);

        // Left heading: clickable breadcrumbs with the folder's counts; right heading: the selection.
        renderLeftHeading(ctx, mouseX, mouseY, onBg, secondary, accent);
        int headingY = AuroraFontRenderer.textY(this.font, crumbY, crumbH);
        String rightHeading = "Selected (" + selectedEntries.size() + ")";
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, rightHeading, rightW, 0),
                rightX, headingY, secondary, false);

        // Left & Right lists
        renderLeftPane(ctx, mouseX, mouseY, onBg, secondary, accent);
        renderRightPane(ctx, mouseX, mouseY, onBg, secondary, accent);

        // Widgets (buttons & search EditBox)
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Subtle search icon inside right edge of search field
        if (searchField != null) {
            MaterialIconRenderer.drawIcon(ctx, this.font, ICON_SEARCH,
                    searchX + searchW - 11f, searchY + searchH / 2f, 11f, secondary);
        }

        // Toast notifications
        toast.render(ctx, this.font, this.width, this.height, Math.round(AuroraTheme.RADIUS_SMALL));
        semanticHost.finishAvailabilitySweep();
    }

    private void renderLeftHeading(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                   int onBg, int secondary, int accent) {
        crumbHits.clear();
        if (currentView == null) return;
        int textY = AuroraFontRenderer.textY(this.font, crumbY, crumbH);
        int headingRight = leftX + leftW - SCROLLBAR_W - 4;
        int packs = currentView.packs().size();
        if (currentView.searching()) {
            // Search spans every folder; Escape clears it. The crumb controls sit out this frame.
            String heading = "Matches in all folders (" + packs + ")";
            ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, heading, headingRight - leftX, 0),
                    leftX, textY, secondary, false);
            return;
        }

        // The counts sit right-aligned and give way to the breadcrumbs when the column is narrow.
        int folders = currentView.folders().size();
        String counts = (folders > 0 ? folders + (folders == 1 ? " folder \u00b7 " : " folders \u00b7 ") : "")
                + packs + (packs == 1 ? " pack" : " packs");
        int countsW = this.font.width(counts);
        int maxRight = headingRight;
        if (headingRight - countsW - 8 - leftX >= 90) {
            ctx.text(this.font, counts, headingRight - countsW, textY, secondary, false);
            maxRight = headingRight - countsW - 8;
        }

        int x = leftX;
        List<PackFolderTree.Breadcrumb> crumbs = currentView.breadcrumbs();
        for (int i = 0; i < crumbs.size(); i++) {
            PackFolderTree.Breadcrumb crumb = crumbs.get(i);
            boolean isLast = i == crumbs.size() - 1;
            int remaining = maxRight - x - 10;
            if (remaining <= 20) break;
            String label = AuroraFontRenderer.ellipsize(this.font, crumb.label(), Math.min(remaining, 140), 0);
            int w = this.font.width(label) + 10;
            SemanticActionControl ctrl = i < crumbControls.size() ? crumbControls.get(i) : null;
            if (ctrl != null) {
                ctrl.setBounds(x, crumbY, w, crumbH);
                ctrl.setAvailable(true);
                ctrl.updatePointer(mouseX, mouseY);
            }
            boolean focused = ctrl != null && ctrl.isFocused();
            boolean hovered = inBox(mouseX, mouseY, x, crumbY, w, crumbH);
            if ((hovered || focused) && !isLast) {
                RenderUtil.drawRoundedRectAA(ctx, x, crumbY, w, crumbH, AuroraTheme.RADIUS_SMALL,
                        ThemeManager.withAlpha(accent, 42));
            }
            if (focused) {
                RenderUtil.drawRoundedOutlineAA(ctx, x, crumbY, w, crumbH, AuroraTheme.RADIUS_SMALL, 1f, accent);
            }
            ctx.text(this.font, label, x + 5, textY, isLast ? onBg : (hovered || focused ? accent : secondary), false);
            crumbHits.add(new CrumbHit(x, crumbY, w, crumbH, crumb.path()));
            x += w;
            if (!isLast) {
                MaterialIconRenderer.drawIcon(ctx, this.font, ICON_CHEVRON,
                        x + 5f, crumbY + crumbH / 2f, 10f, secondary);
                x += 10;
            }
        }
    }

    private void renderLeftPane(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                int onBg, int secondary, int accent) {
        int cardW = leftW - SCROLLBAR_W - 4;
        if (cardW <= 0 || listH <= 0) return; // no room: an empty scissor would crash the frame
        int maxScroll = Math.max(0, leftContentHeight() - listH);
        int scrollOff = (int) Math.round(leftScroll.current());

        ctx.enableScissor(leftX, listTop, leftX + leftW, listTop + listH);

        if (currentView != null) {
            int sectionOffset = 0;

            // ".." Up row
            if (!currentView.searching() && !currentFolder.isEmpty()) {
                int y = listTop + sectionOffset - scrollOff;
                boolean visible = y + FOLDER_ROW_H >= listTop && y <= listTop + listH;
                if (upFolderControl != null) {
                    upFolderControl.setBounds(leftX, y, cardW, FOLDER_ROW_H);
                    upFolderControl.setAvailable(visible);
                    upFolderControl.updatePointer(mouseX, mouseY);
                }
                if (visible) {
                    boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, FOLDER_ROW_H)
                            && mouseY >= listTop && mouseY <= listTop + listH;
                    boolean focused = upFolderControl != null && upFolderControl.isFocused();
                    drawFolderRow(ctx, leftX, y, cardW, ICON_BACK, "..", "Up a folder", hover, focused, onBg, secondary, accent, false);
                }
                sectionOffset += FOLDER_ROW_H + ROW_GAP;
            }

            // Child folder rows (viewport-culled to visible indices only)
            int folderCount = currentView.folders().size();
            PackFolderTree.VisibleRange folderRange = PackFolderTree.visibleRange(
                    folderCount, sectionOffset, FOLDER_ROW_H, ROW_GAP, scrollOff, listH);
            for (int i = folderRange.first(); i < folderRange.lastExclusive(); i++) {
                int y = listTop + sectionOffset + i * (FOLDER_ROW_H + ROW_GAP) - scrollOff;
                PackFolderTree.FolderEntry folder = currentView.folders().get(i);
                SemanticActionControl ctrl = i < folderControls.size() ? folderControls.get(i) : null;
                if (ctrl != null) {
                    ctrl.setBounds(leftX, y, cardW, FOLDER_ROW_H);
                    ctrl.setAvailable(true);
                    ctrl.updatePointer(mouseX, mouseY);
                }
                boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, FOLDER_ROW_H)
                        && mouseY >= listTop && mouseY <= listTop + listH;
                boolean focused = ctrl != null && ctrl.isFocused();
                String countLabel = folder.packCount() == 0
                        ? "Empty"
                        : folder.packCount() + (folder.packCount() == 1 ? " pack" : " packs");
                drawFolderRow(ctx, leftX, y, cardW, ICON_FOLDER, folder.name(), countLabel, hover, focused, onBg, secondary, accent, true);
            }
            sectionOffset += folderCount * (FOLDER_ROW_H + ROW_GAP);

            // Pack rows (viewport-culled to visible indices only)
            int packCount = currentView.packs().size();
            PackFolderTree.VisibleRange packRange = PackFolderTree.visibleRange(
                    packCount, sectionOffset, ROW_H, ROW_GAP, scrollOff, listH);
            for (int i = packRange.first(); i < packRange.lastExclusive(); i++) {
                int y = listTop + sectionOffset + i * (ROW_H + ROW_GAP) - scrollOff;
                PackFolderTree.PackItem item = currentView.packs().get(i);
                SemanticActionControl ctrl = i < availablePackControls.size() ? availablePackControls.get(i) : null;
                if (ctrl != null) {
                    ctrl.setBounds(leftX, y, cardW, ROW_H);
                    ctrl.setAvailable(true);
                    ctrl.updatePointer(mouseX, mouseY);
                }
                boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, ROW_H)
                        && mouseY >= listTop && mouseY <= listTop + listH;
                boolean focused = ctrl != null && ctrl.isFocused();
                PackSelectionModel.Entry entry = item.originalIndex() >= 0 && item.originalIndex() < unselectedEntries.size()
                        ? unselectedEntries.get(item.originalIndex()) : null;
                drawAvailablePackRow(ctx, leftX, y, cardW, item, entry, hover, focused, onBg, secondary, accent);
            }

            if (currentView.folders().isEmpty() && currentView.packs().isEmpty()) {
                String emptyMsg = currentView.searching()
                        ? "No matching resource packs."
                        : "This folder has no unselected packs.";
                String fitted = AuroraFontRenderer.ellipsize(this.font, emptyMsg, cardW - 12, 0);
                // Under the ".." row when there is one, not on it.
                ctx.text(this.font, fitted, leftX + 6, listTop + sectionOffset - scrollOff + 10, secondary, false);
            }
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int sbX = leftX + leftW - SCROLLBAR_W;
            ScrollbarChrome.track(ctx, sbX, listTop, listH, ThemeToken.ON_OVERLAY);
            int guiScale = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : 2;
            ScrollbarChrome.draw(ctx, sbX,
                    ScrollbarChrome.thumb(leftScroll, listTop, listH, maxScroll, 24.0, guiScale),
                    inBox(mouseX, mouseY, sbX - 4, listTop, SCROLLBAR_W + 8, listH),
                    ThemeToken.ON_OVERLAY);
        }
    }

    private void renderRightPane(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                 int onBg, int secondary, int accent) {
        int cardW = rightW - SCROLLBAR_W - 4;
        if (cardW <= 0 || listH <= 0) return;
        int maxScroll = Math.max(0, rightContentHeight() - listH);
        int scrollOff = (int) Math.round(rightScroll.current());

        ctx.enableScissor(rightX, listTop, rightX + rightW, listTop + listH);
        PackFolderTree.VisibleRange visible = PackFolderTree.visibleRange(
                selectedEntries.size(), 0, ROW_H, ROW_GAP, scrollOff, listH);

        for (int i = visible.first(); i < visible.lastExclusive(); i++) {
            int y = listTop + i * (ROW_H + ROW_GAP) - scrollOff;
            PackSelectionModel.Entry entry = selectedEntries.get(i);
            SelectedRowControls rowCtrls = i < selectedRowControls.size() ? selectedRowControls.get(i) : null;
            boolean hover = inBox(mouseX, mouseY, rightX, y, cardW, ROW_H)
                    && mouseY >= listTop && mouseY <= listTop + listH;
            drawSelectedPackRow(ctx, rightX, y, cardW, entry, rowCtrls, hover, mouseX, mouseY, onBg, secondary, accent);
        }
        ctx.disableScissor();

        if (maxScroll > 0) {
            int sbX = rightX + rightW - SCROLLBAR_W;
            ScrollbarChrome.track(ctx, sbX, listTop, listH, ThemeToken.ON_OVERLAY);
            int guiScale = this.minecraft != null ? this.minecraft.getWindow().getGuiScale() : 2;
            ScrollbarChrome.draw(ctx, sbX,
                    ScrollbarChrome.thumb(rightScroll, listTop, listH, maxScroll, 24.0, guiScale),
                    inBox(mouseX, mouseY, sbX - 4, listTop, SCROLLBAR_W + 8, listH),
                    ThemeToken.ON_OVERLAY);
        }
    }

    private void drawFolderRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                               String iconGlyph, String name, String countText,
                               boolean hover, boolean focused, int onBg, int secondary, int accent, boolean showChevron) {
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, FOLDER_ROW_H, r, focused, hover ? 1f : 0f);
        if (focused) {
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, FOLDER_ROW_H, r, 1f, accent);
        }
        MaterialIconRenderer.drawIcon(ctx, this.font, iconGlyph,
                x + 15f, y + FOLDER_ROW_H / 2f, 14f, showChevron ? accent : secondary);

        int countW = this.font.width(countText);
        int rightReserve = countW + (showChevron ? 22 : 10);
        int nameX = x + 28;
        int nameMax = Math.max(20, w - (nameX - x) - rightReserve - 6);
        String fittedName = AuroraFontRenderer.ellipsize(this.font, name, nameMax, 0);
        int textY = AuroraFontRenderer.textY(this.font, y, FOLDER_ROW_H);
        ctx.text(this.font, fittedName, nameX, textY, onBg, false);

        int countX = x + w - rightReserve;
        ctx.text(this.font, countText, countX, textY, secondary, false);
        if (showChevron) {
            MaterialIconRenderer.drawIcon(ctx, this.font, ICON_CHEVRON,
                    x + w - 10f, y + FOLDER_ROW_H / 2f, 12f, secondary);
        }
    }

    private void drawAvailablePackRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                                      PackFolderTree.PackItem item, PackSelectionModel.Entry entry,
                                      boolean hover, boolean focused, int onBg, int secondary, int accent) {
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, ROW_H, r, focused, hover ? 1f : 0f);
        if (focused) {
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, w, ROW_H, r, 1f, accent);
        }

        int iconX = x + 6;
        int iconY = y + (ROW_H - ICON_SIZE) / 2;
        drawPackIcon(ctx, item.id(), iconX, iconY, ICON_SIZE, secondary);

        int addX = x + w - 6 - ACTION_BTN;
        int addY = y + (ROW_H - ACTION_BTN) / 2;
        int textX = iconX + ICON_SIZE + 8;
        int textMax = Math.max(24, addX - textX - 6);

        String title = AuroraFontRenderer.ellipsize(this.font, item.title(), textMax, 0);
        ctx.text(this.font, title, textX, y + 7, onBg, false);

        String sub = buildSubtitle(item.folderPath(), item.description(),
                entry != null ? entry.getCompatibility() : PackCompatibility.COMPATIBLE,
                currentView != null && currentView.searching());
        int subColor = entry != null && !entry.getCompatibility().isCompatible()
                ? ThemeManager.color(ThemeToken.SEMANTIC_WARNING) : secondary;
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, sub, textMax, 0),
                textX, y + 20, subColor, false);

        if (entry != null && entry.canSelect()) {
            boolean active = hover || focused;
            int btnFill = active ? ThemeManager.withAlpha(accent, 75) : ThemeManager.withAlpha(onBg, 22);
            RenderUtil.drawRoundedRectAA(ctx, addX, addY, ACTION_BTN, ACTION_BTN, AuroraTheme.RADIUS_SMALL, btnFill);
            MaterialIconRenderer.drawIcon(ctx, this.font, ICON_ADD,
                    addX + ACTION_BTN / 2f, addY + ACTION_BTN / 2f, 12f, active ? accent : onBg);
        }
    }

    private void drawSelectedPackRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                                     PackSelectionModel.Entry entry, SelectedRowControls rowCtrls,
                                     boolean hover, int mouseX, int mouseY, int onBg, int secondary, int accent) {
        boolean rowFocused = rowCtrls != null && ((rowCtrls.up() != null && rowCtrls.up().isFocused())
                || (rowCtrls.down() != null && rowCtrls.down().isFocused())
                || (rowCtrls.remove() != null && rowCtrls.remove().isFocused()));
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, ROW_H, r, rowFocused, hover ? 0.8f : 0f);

        int iconX = x + 6;
        int iconY = y + (ROW_H - ICON_SIZE) / 2;
        drawPackIcon(ctx, entry.getId(), iconX, iconY, ICON_SIZE, secondary);

        int btnY = y + (ROW_H - ACTION_BTN) / 2;
        int removeX = x + w - 6 - ACTION_BTN;
        int downX = removeX - 3 - ACTION_BTN;
        int upX = downX - 3 - ACTION_BTN;
        boolean hasControls = entry.canUnselect() || entry.canMoveUp() || entry.canMoveDown();
        int controlsLeft = hasControls ? upX : (x + w - 48);

        int textX = iconX + ICON_SIZE + 8;
        int textMax = Math.max(24, controlsLeft - textX - 6);

        String title = AuroraFontRenderer.ellipsize(this.font, entry.getTitle().getString(), textMax, 0);
        ctx.text(this.font, title, textX, y + 7, onBg, false);

        String folder = PackFolderTree.folderOfPackId(entry.getId());
        String sub = buildSubtitle(folder, entry.getDescription().getString(), entry.getCompatibility(), !folder.isEmpty());
        int subColor = !entry.getCompatibility().isCompatible()
                ? ThemeManager.color(ThemeToken.SEMANTIC_WARNING) : secondary;
        ctx.text(this.font, AuroraFontRenderer.ellipsize(this.font, sub, textMax, 0),
                textX, y + 20, subColor, false);

        if (hasControls) {
            if (entry.canMoveUp()) {
                SemanticActionControl upCtrl = rowCtrls != null ? rowCtrls.up() : null;
                if (upCtrl != null) {
                    upCtrl.setBounds(upX, btnY, ACTION_BTN, ACTION_BTN);
                    upCtrl.setAvailable(true);
                    upCtrl.updatePointer(mouseX, mouseY);
                }
                drawMiniAction(ctx, upX, btnY, ICON_UP,
                        inBox(mouseX, mouseY, upX, btnY, ACTION_BTN, ACTION_BTN),
                        upCtrl != null && upCtrl.isFocused(), onBg, accent);
            }
            if (entry.canMoveDown()) {
                SemanticActionControl downCtrl = rowCtrls != null ? rowCtrls.down() : null;
                if (downCtrl != null) {
                    downCtrl.setBounds(downX, btnY, ACTION_BTN, ACTION_BTN);
                    downCtrl.setAvailable(true);
                    downCtrl.updatePointer(mouseX, mouseY);
                }
                drawMiniAction(ctx, downX, btnY, ICON_DOWN,
                        inBox(mouseX, mouseY, downX, btnY, ACTION_BTN, ACTION_BTN),
                        downCtrl != null && downCtrl.isFocused(), onBg, accent);
            }
            if (entry.canUnselect()) {
                SemanticActionControl removeCtrl = rowCtrls != null ? rowCtrls.remove() : null;
                if (removeCtrl != null) {
                    removeCtrl.setBounds(removeX, btnY, ACTION_BTN, ACTION_BTN);
                    removeCtrl.setAvailable(true);
                    removeCtrl.updatePointer(mouseX, mouseY);
                }
                int danger = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
                drawMiniAction(ctx, removeX, btnY, ICON_CLOSE,
                        inBox(mouseX, mouseY, removeX, btnY, ACTION_BTN, ACTION_BTN),
                        removeCtrl != null && removeCtrl.isFocused(), onBg, danger);
            }
        } else {
            ctx.text(this.font, "Required", x + w - 46, AuroraFontRenderer.textY(this.font, y, ROW_H), secondary, false);
        }
    }

    private void drawMiniAction(GuiGraphicsExtractor ctx, int x, int y, String glyph,
                                boolean hovered, boolean focused, int onBg, int activeColor) {
        int bg = (hovered || focused) ? ThemeManager.withAlpha(activeColor, 65) : ThemeManager.withAlpha(onBg, 18);
        RenderUtil.drawRoundedRectAA(ctx, x, y, ACTION_BTN, ACTION_BTN, AuroraTheme.RADIUS_SMALL, bg);
        if (focused) {
            RenderUtil.drawRoundedOutlineAA(ctx, x, y, ACTION_BTN, ACTION_BTN, AuroraTheme.RADIUS_SMALL, 1f, activeColor);
        }
        MaterialIconRenderer.drawIcon(ctx, this.font, glyph,
                x + ACTION_BTN / 2f, y + ACTION_BTN / 2f, 11f, (hovered || focused) ? activeColor : onBg);
    }

    private void drawPackIcon(GuiGraphicsExtractor ctx, String packId, int x, int y, int size, int secondary) {
        Pack pack = packById.get(packId);
        PackIconLoader.LoadedIcon icon = PackIconLoader.request(pack);
        if (icon != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, icon.textureId(),
                    x, y, 0f, 0f, size, size, size, size);
        } else {
            RenderUtil.drawRoundedRectAA(ctx, x, y, size, size, AuroraTheme.RADIUS_SMALL,
                    ThemeManager.withAlpha(MaterialSurface.color(ThemeToken.SURFACE_VARIANT), 170));
            MaterialIconRenderer.drawIcon(ctx, this.font, ICON_PACK,
                    x + size / 2f, y + size / 2f, 14f, secondary);
        }
    }

    private static String buildSubtitle(String folder, String description, PackCompatibility compat, boolean showFolder) {
        StringBuilder sb = new StringBuilder();
        if (!compat.isCompatible()) sb.append("Incompatible • ");
        if (showFolder && folder != null && !folder.isEmpty()) {
            sb.append(folder);
            if (description != null && !description.isBlank()) sb.append(" • ");
        }
        if (description != null && !description.isBlank()) {
            sb.append(description.replace('\n', ' ').trim());
        } else if (sb.isEmpty()) {
            sb.append("Resource pack");
        }
        return sb.toString();
    }
}
