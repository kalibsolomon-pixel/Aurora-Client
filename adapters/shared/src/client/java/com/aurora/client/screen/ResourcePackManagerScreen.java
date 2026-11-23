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
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.RenderUtil;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraTheme;
import com.aurora.client.util.SmoothScroll;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
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
    }

    @Override
    protected void init() {
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
        int y = listTop - (int) Math.round(leftScroll.current());

        // ".." Up row
        if (!currentView.searching() && !currentFolder.isEmpty()) {
            if (my >= y && my <= y + FOLDER_ROW_H) {
                enterFolder(PackFolderTree.parentFolder(currentFolder));
                return true;
            }
            y += FOLDER_ROW_H + ROW_GAP;
        }

        // Subfolder rows
        for (PackFolderTree.FolderEntry folder : currentView.folders()) {
            if (my >= y && my <= y + FOLDER_ROW_H) {
                enterFolder(folder.path());
                return true;
            }
            y += FOLDER_ROW_H + ROW_GAP;
        }

        // Pack rows
        for (PackFolderTree.PackItem item : currentView.packs()) {
            if (my >= y && my <= y + ROW_H) {
                if (item.originalIndex() >= 0 && item.originalIndex() < unselectedEntries.size()) {
                    PackSelectionModel.Entry entry = unselectedEntries.get(item.originalIndex());
                    if (entry.canSelect()) {
                        entry.select();
                        rebuildLists();
                    }
                }
                return true;
            }
            y += ROW_H + ROW_GAP;
        }
        return false;
    }

    private boolean handleRightClick(double mx, double my) {
        int y = listTop - (int) Math.round(rightScroll.current());
        int cardW = rightW - SCROLLBAR_W - 4;
        for (PackSelectionModel.Entry entry : selectedEntries) {
            if (my >= y && my <= y + ROW_H) {
                int btnY = y + (ROW_H - ACTION_BTN) / 2;
                int removeX = rightX + cardW - 6 - ACTION_BTN;
                int downX = removeX - 3 - ACTION_BTN;
                int upX = downX - 3 - ACTION_BTN;

                if (entry.canUnselect() && inBox(mx, my, removeX, btnY, ACTION_BTN, ACTION_BTN)) {
                    entry.unselect();
                    rebuildLists();
                    return true;
                }
                if (entry.canMoveDown() && inBox(mx, my, downX, btnY, ACTION_BTN, ACTION_BTN)) {
                    entry.moveDown();
                    rebuildLists();
                    return true;
                }
                if (entry.canMoveUp() && inBox(mx, my, upX, btnY, ACTION_BTN, ACTION_BTN)) {
                    entry.moveUp();
                    rebuildLists();
                    return true;
                }
                return true;
            }
            y += ROW_H + ROW_GAP;
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
            enterFolder(PackFolderTree.parentFolder(currentFolder));
            return true;
        }
        return super.keyPressed(event);
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
    }

    private void renderLeftHeading(GuiGraphicsExtractor ctx, int mouseX, int mouseY,
                                   int onBg, int secondary, int accent) {
        crumbHits.clear();
        if (currentView == null) return;
        int textY = AuroraFontRenderer.textY(this.font, crumbY, crumbH);
        int headingRight = leftX + leftW - SCROLLBAR_W - 4;
        int packs = currentView.packs().size();
        if (currentView.searching()) {
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
            boolean hovered = !isLast && inBox(mouseX, mouseY, x, crumbY, w, crumbH);
            if (hovered) {
                RenderUtil.drawRoundedRectAA(ctx, x, crumbY, w, crumbH, AuroraTheme.RADIUS_SMALL,
                        ThemeManager.withAlpha(accent, 42));
            }
            ctx.text(this.font, label, x + 5, textY, isLast ? onBg : (hovered ? accent : secondary), false);
            if (!isLast) crumbHits.add(new CrumbHit(x, crumbY, w, crumbH, crumb.path()));
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

        ctx.enableScissor(leftX, listTop, leftX + leftW, listTop + listH);
        int y = listTop - (int) Math.round(leftScroll.current());

        if (currentView != null) {
            // ".." Up row
            if (!currentView.searching() && !currentFolder.isEmpty()) {
                if (y + FOLDER_ROW_H >= listTop && y <= listTop + listH) {
                    boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, FOLDER_ROW_H)
                            && mouseY >= listTop && mouseY <= listTop + listH;
                    drawFolderRow(ctx, leftX, y, cardW, ICON_BACK, "..", "Up a folder", hover, onBg, secondary, accent, false);
                }
                y += FOLDER_ROW_H + ROW_GAP;
            }

            // Child folder rows
            for (PackFolderTree.FolderEntry folder : currentView.folders()) {
                if (y + FOLDER_ROW_H >= listTop && y <= listTop + listH) {
                    boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, FOLDER_ROW_H)
                            && mouseY >= listTop && mouseY <= listTop + listH;
                    String countLabel = folder.packCount() == 0
                            ? "Empty"
                            : folder.packCount() + (folder.packCount() == 1 ? " pack" : " packs");
                    drawFolderRow(ctx, leftX, y, cardW, ICON_FOLDER, folder.name(), countLabel, hover, onBg, secondary, accent, true);
                }
                y += FOLDER_ROW_H + ROW_GAP;
            }

            // Pack rows
            for (PackFolderTree.PackItem item : currentView.packs()) {
                if (y + ROW_H >= listTop && y <= listTop + listH) {
                    boolean hover = inBox(mouseX, mouseY, leftX, y, cardW, ROW_H)
                            && mouseY >= listTop && mouseY <= listTop + listH;
                    PackSelectionModel.Entry entry = item.originalIndex() >= 0 && item.originalIndex() < unselectedEntries.size()
                            ? unselectedEntries.get(item.originalIndex()) : null;
                    drawAvailablePackRow(ctx, leftX, y, cardW, item, entry, hover, onBg, secondary, accent);
                }
                y += ROW_H + ROW_GAP;
            }

            if (currentView.folders().isEmpty() && currentView.packs().isEmpty()) {
                String emptyMsg = currentView.searching()
                        ? "No matching resource packs."
                        : "This folder has no unselected packs.";
                String fitted = AuroraFontRenderer.ellipsize(this.font, emptyMsg, cardW - 12, 0);
                ctx.text(this.font, fitted, leftX + 6, y + 10, secondary, false);
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

        ctx.enableScissor(rightX, listTop, rightX + rightW, listTop + listH);
        int y = listTop - (int) Math.round(rightScroll.current());

        for (PackSelectionModel.Entry entry : selectedEntries) {
            if (y + ROW_H >= listTop && y <= listTop + listH) {
                boolean hover = inBox(mouseX, mouseY, rightX, y, cardW, ROW_H)
                        && mouseY >= listTop && mouseY <= listTop + listH;
                drawSelectedPackRow(ctx, rightX, y, cardW, entry, hover, mouseX, mouseY, onBg, secondary, accent);
            }
            y += ROW_H + ROW_GAP;
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
                               boolean hover, int onBg, int secondary, int accent, boolean showChevron) {
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, FOLDER_ROW_H, r, false, hover ? 1f : 0f);
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
                                      boolean hover, int onBg, int secondary, int accent) {
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, ROW_H, r, false, hover ? 1f : 0f);

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
            int btnFill = hover ? ThemeManager.withAlpha(accent, 75) : ThemeManager.withAlpha(onBg, 22);
            RenderUtil.drawRoundedRectAA(ctx, addX, addY, ACTION_BTN, ACTION_BTN, AuroraTheme.RADIUS_SMALL, btnFill);
            MaterialIconRenderer.drawIcon(ctx, this.font, ICON_ADD,
                    addX + ACTION_BTN / 2f, addY + ACTION_BTN / 2f, 12f, hover ? accent : onBg);
        }
    }

    private void drawSelectedPackRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                                     PackSelectionModel.Entry entry, boolean hover,
                                     int mouseX, int mouseY, int onBg, int secondary, int accent) {
        float r = AuroraTheme.RADIUS_SMALL;
        MaterialSurface.selection(ctx, x, y, w, ROW_H, r, false, hover ? 0.8f : 0f);

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
                drawMiniAction(ctx, upX, btnY, ICON_UP,
                        inBox(mouseX, mouseY, upX, btnY, ACTION_BTN, ACTION_BTN), onBg, accent);
            }
            if (entry.canMoveDown()) {
                drawMiniAction(ctx, downX, btnY, ICON_DOWN,
                        inBox(mouseX, mouseY, downX, btnY, ACTION_BTN, ACTION_BTN), onBg, accent);
            }
            if (entry.canUnselect()) {
                int danger = ThemeManager.color(ThemeToken.SEMANTIC_ERROR);
                drawMiniAction(ctx, removeX, btnY, ICON_CLOSE,
                        inBox(mouseX, mouseY, removeX, btnY, ACTION_BTN, ACTION_BTN), onBg, danger);
            }
        } else {
            ctx.text(this.font, "Required", x + w - 46, AuroraFontRenderer.textY(this.font, y, ROW_H), secondary, false);
        }
    }

    private void drawMiniAction(GuiGraphicsExtractor ctx, int x, int y, String glyph,
                                boolean hovered, int onBg, int activeColor) {
        int bg = hovered ? ThemeManager.withAlpha(activeColor, 65) : ThemeManager.withAlpha(onBg, 18);
        RenderUtil.drawRoundedRectAA(ctx, x, y, ACTION_BTN, ACTION_BTN, AuroraTheme.RADIUS_SMALL, bg);
        MaterialIconRenderer.drawIcon(ctx, this.font, glyph,
                x + ACTION_BTN / 2f, y + ACTION_BTN / 2f, 11f, hovered ? activeColor : onBg);
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
