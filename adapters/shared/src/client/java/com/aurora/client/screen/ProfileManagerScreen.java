package com.aurora.client.screen;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.ui.component.MaterialSurface;
import com.aurora.client.config.profile.ProfileHotkey;
import com.aurora.client.config.profile.ProfileManager;
import com.aurora.client.screen.setting.FeatureSetting;
import com.aurora.client.screen.setting.KeybindSetting;
import com.aurora.client.theme.ThemeManager;
import com.aurora.client.theme.ThemeToken;
import com.aurora.client.ui.component.Button;
import com.aurora.client.ui.component.ButtonWidget;
import com.aurora.client.ui.component.GlassEditBox;
import com.aurora.client.ui.component.GlassSurface;
import com.aurora.client.ui.interaction.MinecraftSemanticFeedback;
import com.aurora.client.ui.interaction.SemanticAction;
import com.aurora.client.ui.interaction.SemanticActionControl;
import com.aurora.client.ui.interaction.SemanticSound;
import com.aurora.client.ui.render.blur.BlurPanelRenderer;
import com.aurora.client.ui.util.AuroraFontRenderer;
import com.aurora.client.ui.util.MaterialIconRenderer;
import com.aurora.client.ui.util.UiDensity;
import com.aurora.client.util.AuroraKey;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lists configuration profiles as a card grid: a "New profile" card first,
 * then one card per profile with its icon, name and hotkey.
 *
 * <p>Backed by {@link ProfileManager}, on the shared
 * {@link ManagerListScreen} foundation (audit R3): the frame order, scroll +
 * scrollbar, toast, inline rename lifecycle and toolbar row come from the
 * base. This class owns the header (search, Separate controls, Open folder),
 * the card content and actions, and the inline create editor that opens in
 * the "New profile" card.
 *
 * <p>Profiles refresh (owner request 2026-11-12, after a launcher-style
 * reference): a card click switches to that profile and the active card
 * wears the thin selected edge; the hotkey chip captures the key that
 * switches to it in game; the settings glyph opens {@link ProfileOptionsScreen}
 * (icon, rename, hotkey, Duplicate, Delete). The {@code default} profile and
 * the active profile cannot be deleted (the manager enforces this).
 */
public class ProfileManagerScreen extends ManagerListScreen<String> {

    // ---- Card grid (owner request 2026-10-19: tiles, like the launcher's instances) ----
    //
    // Each card: the settings glyph in the top-right corner, a large icon,
    // the name under it, and the hotkey chip on the bottom line, so every
    // card in a row keeps its parts on the same lines. Columns follow the
    // available width (at least one).
    private static final int CARD_H = 84;
    private static final int CARD_MIN_W = 104;
    private static final int CARD_GAP = 8;
    private static final int INSET = 6;
    private static final int BTN_H = 14;
    private static final int ICON_Y = 24;
    private static final float ICON_EM = 24f;
    private static final int NAME_Y = 44;
    /** The settings glyph's hit square, inset from the card's top-right corner. */
    private static final int GEAR = 16;
    /** The bottom action line: the hotkey chip, or the create card's Create button. */
    private static final int LOWER_BTN_Y = CARD_H - INSET - BTN_H;
    private static final String GEAR_GLYPH = IconCatalog.glyph("settings");
    private static final String KEYBOARD_GLYPH = IconCatalog.glyph("keyboard");
    private static final String ADD_GLYPH = IconCatalog.glyph("add");

    /**
     * Pending create: "New profile" turns its card into an editor in place
     * rather than adding a card mid-grid.
     */
    private boolean creatingNew = false;
    private EditBox createField;

    /** The profile whose hotkey chip is waiting for a key, or null. */
    private String capturingHotkey;
    /** Modifiers held so far while capturing: Ctrl, Shift or Alt alone waits for a second key. */
    private final ProfileHotkey.Capture capture = new ProfileHotkey.Capture();

    /**
     * Per-card semantic controls (Phase A rollout), keyed by profile NAME,
     * created on the card's first paint and dropped in
     * {@link #reconcileRowCaches} (removal unregisters the control, clearing
     * focus if it held it): the settings glyph and the hotkey chip.
     */
    private final Map<String, SemanticActionControl> optionsControls = new HashMap<>();
    private final Map<String, SemanticActionControl> hotkeyControls = new HashMap<>();
    /** The create-row's confirm button (recreated per create session). */
    private Button createBtn;

    /**
     * The Create confirm's Phase A semantic control (rollout): long-lived
     * (one per screen), with a DYNAMIC enabled gate — enabled exactly while
     * the create field holds a non-empty name. Keyboard routing stays
     * deliberately with the field's Enter (focusable/keyboard-eligible off
     * on the control): making the button a selection target while its own
     * field is being typed into would let a Space keypress commit the form
     * mid-typing — the field already commits on Enter, which is the form's
     * established keyboard path. Pointer activation routes through the
     * control's gate (clicks on the disabled button no longer consume,
     * animate, or fall through into a create attempt).
     */
    private final SemanticActionControl createControl;

    private boolean createRowGlassDrawn = false;

    /** Per-card hover ease (canonical 140 ms), keyed and reconciled like the controls. */
    private final Map<String, com.aurora.client.util.HoverAnim> cardHover = new HashMap<>();

    // ---- Header (search, Separate controls, Open folder) ----
    private EditBox searchField;
    private ButtonWidget controlsChip;
    private ButtonWidget folderBtn;
    /** Search memo: the filtered list is rebuilt only when the query or the profile list changes. */
    private String memoQuery;
    private List<String> memoSource;
    private List<String> memoRows;
    /** The hotkey chip under the pointer this frame (its tooltip draws in the tail), or null. */
    private String hoveredChip;
    private int hoveredChipX, hoveredChipY;
    /** Hotkey chip label memo, by stored key. */
    private final Map<Integer, String> keyNames = new HashMap<>();

    /** Card action width — shared by the glass and content passes so they never disagree. */
    private int btnW() { return cellWidth() - INSET * 2; }

    public ProfileManagerScreen(Screen parent) {
        super(Component.literal("Profiles"), parent);
        // Constructed directly (not via SemanticAction.button): this form's
        // keyboard commit belongs to the field's Enter, so the button is
        // focusable=false / keyboard-eligible=false — pointer-activated,
        // narratable, traversal-invisible (see the field javadoc).
        this.createControl = new SemanticActionControl(new SemanticAction(
                Component.literal("Create"),
                () -> Component.literal("Creates a new profile with the entered name."),
                null,
                this::createEnabled,
                this::commitCreate,
                SemanticSound.ACTIVATION,
                false, false),
                MinecraftSemanticFeedback.INSTANCE,
                () -> {
                    if (createBtn != null) createBtn.triggerPressAnimation();
                },
                SemanticActionControl.PointerRouting.MANUAL);
        registerSemanticControl(this.createControl);
    }

    /** The create gate: enabled exactly while a non-empty name is entered. */
    private boolean createEnabled() {
        return creatingNew && createField != null && !createField.getValue().trim().isEmpty();
    }

    // ------------------------------------------------------------------
    //  Header
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        String query = searchField != null ? searchField.getValue() : "";
        boolean searchFocused = searchField != null && searchField.isFocused();
        searchField = new EditBox(this.font, 0, 0, 80, UiDensity.HEADER_BUTTON_H, Component.literal("Search profiles"));
        searchField.setMaxLength(48);
        searchField.setHint(Component.literal("Search…"));
        searchField.setValue(query);
        searchField.setFocused(searchFocused);
        this.addRenderableWidget(searchField);

        controlsChip = this.addRenderableWidget(ButtonWidget.semantic(0, 0, 10, UiDensity.HEADER_BUTTON_H,
                Component.literal("Separate controls"),
                "When on, each profile keeps its own Aurora and Minecraft key bindings and switching applies them.",
                this::toggleSeparateControls).glassBackground(true));
        folderBtn = this.addRenderableWidget(ButtonWidget.semantic(0, 0, 10, UiDensity.HEADER_BUTTON_H,
                Component.literal("Open folder"),
                "Opens the folder the profile files are saved in.",
                () -> com.aurora.client.adapter.NativeUi.openPath(ProfileManager.getInstance().profilesDirectory()))
                .glassBackground(true));
        layoutHeader();
    }

    /**
     * Header row, left to right: search (takes what is left, at least
     * {@code SEARCH_MIN}), Separate controls, Open folder, then the base's
     * Done on the right. Narrow windows get the short labels; the long ones
     * never truncate mid-word.
     */
    private void layoutHeader() {
        if (searchField == null) return;
        // pad: Button keeps 20 px around its label (it ellipsizes past w - 20), plus 2 of air.
        final int gap = 6, searchMin = 56, pad = 22;
        int left = windowX() + 8;
        int right = windowX() + windowWidth() - 8 - UiDensity.HEADER_BUTTON_W - gap;
        String chipLabel = "Separate controls", folderLabel = "Open folder";
        int chipW = this.font.width(chipLabel) + pad, folderW = this.font.width(folderLabel) + pad;
        if (right - left - chipW - folderW - 2 * gap < searchMin) {
            chipLabel = "Controls";
            folderLabel = "Folder";
            chipW = this.font.width(chipLabel) + pad;
            folderW = this.font.width(folderLabel) + pad;
        }
        int y = toolbarY();
        folderBtn.setMessage(Component.literal(folderLabel));
        folderBtn.setX(right - folderW);
        folderBtn.setY(y);
        folderBtn.setWidth(folderW);
        controlsChip.setMessage(Component.literal(chipLabel));
        controlsChip.setX(right - folderW - gap - chipW);
        controlsChip.setY(y);
        controlsChip.setWidth(chipW);
        searchField.setX(left);
        searchField.setY(y);
        searchField.setWidth(Math.max(searchMin, right - folderW - chipW - 2 * gap - left));
    }

    private void toggleSeparateControls() {
        AuroraConfig config = AuroraConfig.get();
        config.profilesOwnControls = !config.profilesOwnControls;
        AuroraConfig.save();
        flash(config.profilesOwnControls ? "Profiles keep their own controls" : "Controls are shared by every profile");
    }

    @Override
    protected void paintHeaderGlassPass(GuiGraphicsExtractor ctx) {
        layoutHeader();
        // On is the selected state, so it alone is stained (glass rule 3).
        boolean on = AuroraConfig.get().profilesOwnControls;
        controlsChip.glassStyle(on ? Button.GlassStyle.STAINED : Button.GlassStyle.NEUTRAL).primary(on);
        controlsChip.renderGlassPass(ctx);
        folderBtn.renderGlassPass(ctx);
        if (searchField instanceof GlassEditBox glassField) glassField.aurora$renderGlassPass(ctx);
    }

    // ------------------------------------------------------------------
    //  Foundation hooks
    // ------------------------------------------------------------------

    @Override protected int listWidth() { return Math.max(CARD_MIN_W, windowWidth() - 16); }
    @Override protected int columns() { return Math.max(1, (listWidth() + CARD_GAP) / (CARD_MIN_W + CARD_GAP)); }
    @Override protected int columnGap() { return CARD_GAP; }
    @Override protected int rowGap() { return CARD_GAP; }
    @Override protected int rowHeight() { return CARD_H; }
    @Override protected int nameColumnWidth() { return cellWidth() - INSET * 2; }
    @Override protected int editorFieldX(int cellX) { return cellX + INSET; }
    @Override protected int editorFieldYOffset() { return NAME_Y - 4; }

    private String query() {
        return searchField == null ? "" : searchField.getValue().trim().toLowerCase(Locale.ROOT);
    }

    @Override protected List<String> currentRows() {
        List<String> all = ProfileManager.getInstance().listProfileNames();
        String query = query();
        if (query.isEmpty()) return all;
        if (!query.equals(memoQuery) || !all.equals(memoSource)) {
            List<String> rows = new ArrayList<>();
            for (String name : all) {
                if (name.toLowerCase(Locale.ROOT).contains(query)) rows.add(name);
            }
            memoQuery = query;
            memoSource = all;
            memoRows = rows;
        }
        return memoRows;
    }

    @Override protected Component emptyMessage() {
        // With no search the "New profile" card is the empty state.
        return query().isEmpty() ? Component.empty() : Component.literal("No profiles match \"" + searchField.getValue().trim() + "\"");
    }

    /** The "New profile" card; hidden while a search filters the grid, unless it is being edited. */
    @Override protected int leadingRowCount() {
        return creatingNew || query().isEmpty() ? 1 : 0;
    }

    @Override protected ButtonWidget createToolbarActionBtn() {
        return null; // the header's left side is the search field (see init)
    }

    @Override
    protected void reconcileRowCaches(List<String> profiles) {
        // Row-widget cache hygiene: drop controls for profiles that no longer
        // exist. Reconciled against a SET: retainAll(List) costs O(rows²) per
        // map (a List.contains scan per key) and ran every frame — with a
        // large profile list that alone dominated the frame (~5 ms at 2000
        // rows). (fixed in 0a0caaa; the rule is now standing — AGENTS.md §10)
        // The reconcile covers every profile, not only the filtered ones, so
        // a search does not drop and rebuild the hidden cards' controls.
        List<String> all = ProfileManager.getInstance().listProfileNames();
        Set<String> live = new HashSet<>(all);
        boolean membershipChanged = cardHover.keySet().retainAll(live);
        // The card controls drop with their profile — removeWidget takes the
        // control out of the vanilla child/narratable lists and clears screen
        // focus if the removed control held it, so a deleted profile can
        // never leave a stale focusable region behind (the Waypoint-Copy
        // lifecycle rule).
        membershipChanged |= reconcileSemanticControls(optionsControls, live);
        membershipChanged |= reconcileSemanticControls(hotkeyControls, live);
        if (capturingHotkey != null && !live.contains(capturingHotkey)) stopCapture();
        if (membershipChanged) nameFitCache.clear();
    }

    /** Unregisters and drops the card controls whose profile is no longer live. */
    private boolean reconcileSemanticControls(Map<String, SemanticActionControl> controls,
                                              Set<String> live) {
        boolean removed = false;
        for (var it = controls.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!live.contains(entry.getKey())) {
                unregisterSemanticControl(entry.getValue());
                it.remove();
                removed = true;
            }
        }
        return removed;
    }

    @Override
    protected void onBeginRowGlassPass() {
        createRowGlassDrawn = false;
        hoveredChip = null;
    }

    // ------------------------------------------------------------------
    //  Card passes
    // ------------------------------------------------------------------

    @Override
    protected void paintLeadingRowGlassPass(GuiGraphicsExtractor ctx, int x, int y, int w, int leadIndex) {
        // Same template treatment as the profile cards. Availability follows
        // the same clip-band rule as the row loop so a create card scrolled
        // out of the clip can neither be keyed nor clicked while invisible.
        createControl.setAvailable(creatingNew && y + CARD_H > listClipTop() && y < listClipBottom());
        paintTemplatedRowSurface(ctx, x, y,
                () -> createRowGlassDrawn = renderRowSurface(ctx, x, y, w),
                () -> {
                    if (!creatingNew) return;
                    Button cb = createBtn();
                    int bx = x + INSET, by = y + LOWER_BTN_Y;
                    cb.layout(bx, by, btnW(), BTN_H);
                    createControl.setBounds(bx, by, btnW(), BTN_H);
                    boolean enabled = createEnabled();
                    cb.disabled(!enabled);
                    cb.focused(createControl.isFocused() && enabled);
                    cb.renderGlassPass(ctx, bx, by, btnW(), BTN_H);
                },
                () -> createRowGlassDrawn);
    }

    @Override
    protected void paintLeadingRow(GuiGraphicsExtractor ctx, int x, int y, int w, int leadIndex,
                                   int mouseX, int mouseY) {
        renderCreateRow(ctx, x, y, w, mouseX, mouseY);
    }

    @Override
    protected void paintRowGlassPass(GuiGraphicsExtractor ctx, int x, int y, int w, String name) {
        // Static shapes (rings + card tint) come from the shared row template
        // (see ManagerListScreen); the card panel renders live below. The
        // card's chip and glyph are opaque content, painted after the dim.
        paintTemplatedRowSurface(ctx, x, y,
                () -> rowGlassDrawn.put(name, renderRowSurface(ctx, x, y, w)),
                () -> {},
                () -> rowGlassDrawn.getOrDefault(name, false));
    }

    @Override
    protected void paintRow(GuiGraphicsExtractor ctx, int x, int y, int w, String name, int index,
                            int mouseX, int mouseY) {
        renderRow(ctx, x, y, w, name, index, ProfileManager.getInstance().currentProfile(), mouseX, mouseY);
    }

    private boolean inBand(int y) {
        return y + CARD_H > listClipTop() && y < listClipBottom();
    }

    private boolean pointerIn(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && mouseY >= listClipTop() && mouseY < listClipBottom();
    }

    private void renderRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                            String name, int index, String active,
                            int mouseX, int mouseY) {
        ProfileManager manager = ProfileManager.getInstance();
        boolean isActive = name.equals(active);
        float radius = ThemeManager.current().roundness().radiusSmall();
        boolean pointerIn = pointerIn(mouseX, mouseY, x, y, w, CARD_H);
        float hover = cardHover.computeIfAbsent(name, k -> com.aurora.client.util.HoverAnim.symmetric(140L))
                .update(pointerIn && !isActive);

        // The card surface was painted in the glass pass, under the dim — see
        // paintRowGlassPass. Here: the selection role (the thin selected edge
        // on the active card, a hover lift on the others), or the flat
        // fallback for a card whose glass declined.
        if (MaterialSurface.unified()) {
            MaterialSurface.selection(ctx, x, y, w, CARD_H, radius, isActive, hover);
        } else if (!rowGlassDrawn.getOrDefault(name, false)) {
            if (isActive) MaterialSurface.selection(ctx, x, y, w, CARD_H, radius, true, 0);
            else paintFlatRow(ctx, x, y, w);
        }

        // Settings glyph — opens the profile's options.
        int gearX = x + w - GEAR - 2, gearY = y + 2;
        SemanticActionControl options = optionsControl(name);
        options.setBounds(gearX, gearY, GEAR, GEAR);
        options.setAvailable(inBand(y));
        options.updatePointer(mouseX, mouseY);
        boolean gearHot = pointerIn(mouseX, mouseY, gearX, gearY, GEAR, GEAR) || options.isFocused();
        MaterialIconRenderer.drawIcon(ctx, this.font, GEAR_GLYPH, gearX + GEAR / 2f, gearY + GEAR / 2f, 10f,
                ThemeManager.color(gearHot ? ThemeToken.ON_BACKGROUND : ThemeToken.ON_BACKGROUND_MUTED));
        if (options.isFocused()) {
            com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx, gearX, gearY, GEAR, GEAR, radius, 1f,
                    ThemeManager.color(ThemeToken.ACCENT));
        }

        // The profile's icon.
        MaterialIconRenderer.drawIcon(ctx, this.font, profileGlyph(manager.icon(name)), x + w / 2f, y + ICON_Y, ICON_EM,
                ThemeManager.color(isActive ? ThemeToken.ACCENT : ThemeToken.ON_BACKGROUND));

        // Name (or the inline editor over it).
        if (index != editingIndex) {
            AuroraFontRenderer.drawCentered(ctx, this.font, fitName(name), x + w / 2, y + NAME_Y,
                    ThemeManager.color(ThemeToken.ON_BACKGROUND));
        }

        // Hotkey chip: a keyboard glyph and the key, accent edge while waiting for a key.
        int chipX = x + INSET, chipY = y + LOWER_BTN_Y, chipW = btnW();
        SemanticActionControl hotkey = hotkeyControl(name);
        hotkey.setBounds(chipX, chipY, chipW, BTN_H);
        hotkey.setAvailable(inBand(y));
        hotkey.updatePointer(mouseX, mouseY);
        boolean capturing = name.equals(capturingHotkey);
        boolean chipHot = pointerIn(mouseX, mouseY, chipX, chipY, chipW, BTN_H);
        MaterialSurface.selection(ctx, chipX, chipY, chipW, BTN_H, radius, capturing || hotkey.isFocused(),
                chipHot ? 1f : 0f);
        ProfileHotkey binding = manager.binding(name);
        String label = capturing ? captureLabel(capture.pending()) : binding.bound() ? keyName(binding) : "None";
        int textCol = ThemeManager.color(capturing || binding.bound() ? ThemeToken.ON_BACKGROUND : ThemeToken.ON_BACKGROUND_MUTED);
        String fitted = AuroraFontRenderer.ellipsize(this.font, label, chipW - 20, 0);
        int contentW = 12 + this.font.width(fitted);
        int cx = chipX + (chipW - contentW) / 2;
        MaterialIconRenderer.drawIcon(ctx, this.font, KEYBOARD_GLYPH, cx + 4.5f, chipY + BTN_H / 2f, 9f, textCol);
        ctx.text(this.font, fitted, cx + 12, chipY + (BTN_H - this.font.lineHeight) / 2 + 1, textCol, false);
        if (chipHot) {
            hoveredChip = name;
            hoveredChipX = chipX;
            hoveredChipY = chipY;
        }
    }

    private String keyName(ProfileHotkey binding) {
        return keyNames.computeIfAbsent(binding.key() | binding.modifiers() << 16, k -> hotkeyName(binding));
    }

    /** "Ctrl + P": the modifiers, then the key's display name. */
    static String hotkeyName(ProfileHotkey binding) {
        return binding.bound() ? ProfileHotkey.modifierPrefix(binding.modifiers()) + KeybindSetting.keyName(binding.key()) : "None";
    }

    /** The chip while capturing: "Press a key…", or "Ctrl + …" once a modifier is held. */
    static String captureLabel(int pendingModifiers) {
        return pendingModifiers == 0 ? "Press a key…" : ProfileHotkey.modifierPrefix(pendingModifiers) + "…";
    }

    private void stopCapture() {
        capturingHotkey = null;
        capture.reset();
    }

    /** Applies a finished capture ({@link ProfileHotkey#NONE} clears) and says so. */
    private void commitCapture(String name, ProfileHotkey binding) {
        stopCapture();
        if (ProfileManager.getInstance().setHotkey(name, binding)) {
            flash(binding.bound() ? keyName(binding) + " switches to " + name : "Cleared the hotkey for " + name);
        }
    }

    /** A profile's icon glyph: its Material Symbols name, or the person glyph when unset or unknown. */
    static String profileGlyph(String icon) {
        String glyph = IconCatalog.glyph(icon);
        return glyph.isEmpty() ? IconCatalog.glyph("person") : glyph;
    }

    /** The settings glyph's semantic control (Phase A): opens the profile's options. */
    private SemanticActionControl optionsControl(String name) {
        return optionsControls.computeIfAbsent(name, k -> {
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Options for " + k),
                    () -> Component.literal("Change this profile's icon, name or hotkey, duplicate it or delete it."),
                    () -> Component.empty(),
                    () -> true,
                    () -> openOptions(k)),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            registerSemanticControl(control);
            return control;
        });
    }

    /** The hotkey chip's semantic control (Phase A): starts or cancels capture. */
    private SemanticActionControl hotkeyControl(String name) {
        return hotkeyControls.computeIfAbsent(name, k -> {
            SemanticActionControl control = new SemanticActionControl(SemanticAction.button(
                    Component.literal("Hotkey for " + k),
                    () -> Component.literal("Sets the key that switches to this profile in game."),
                    () -> {
                        return Component.literal(hotkeyName(ProfileManager.getInstance().binding(k)));
                    },
                    () -> true,
                    () -> {
                        commitEditors();
                        boolean again = k.equals(capturingHotkey);
                        stopCapture();
                        if (!again) capturingHotkey = k;
                    }),
                    MinecraftSemanticFeedback.INSTANCE,
                    null,
                    SemanticActionControl.PointerRouting.MANUAL);
            registerSemanticControl(control);
            return control;
        });
    }

    private void openOptions(String name) {
        commitEditors();
        stopCapture();
        if (this.minecraft != null) {
            com.aurora.client.adapter.NativeUi.setScreen(this.minecraft, new ProfileOptionsScreen(this, name));
        }
    }

    /** The create-row's confirm button — neutral raised glass like every other action button here. */
    private Button createBtn() {
        if (createBtn == null) {
            // No-op onPress: the behavior fires exactly once through the
            // createControl's semantic gate (this button only paints).
            createBtn = new Button("Create", () -> {}).glassBackground(true)
                    .priority(BlurPanelRenderer.Priority.DETAIL);
        }
        return createBtn;
    }

    /**
     * Per-frame sync of the create pair: the control's bounds follow the
     * row's live geometry and its availability tracks the clip band; the
     * Button mirrors the dynamic enabled gate (existing disabled treatment —
     * the rollout's one new visual state) and the control's focus.
     */
    private void syncCreateControl(int x, int y, int mouseX, int mouseY) {
        Button cb = createBtn();
        int btnX = x + INSET;
        int btnY = y + LOWER_BTN_Y;
        cb.layout(btnX, btnY, btnW(), BTN_H);
        createControl.setBounds(btnX, btnY, btnW(), BTN_H);
        createControl.setAvailable(y + CARD_H > listClipTop() && y < listClipBottom());
        createControl.updatePointer(mouseX, mouseY);
        boolean enabled = createEnabled();
        cb.disabled(!enabled);
        cb.focused(createControl.isFocused() && enabled);
    }

    /**
     * Positions the inline editor (the create field, else the rename
     * field) over its row for this frame and returns it, or {@code null}
     * when none is open. Shared by the glass pass (which paints the
     * field's surface at that position) and the content pass (which
     * renders it), so the two can never disagree.
     *
     * <p>C-8: like the base's rename contract, the create field's position
     * is written EVERY frame while the create editor is open — even when
     * its leading row has scrolled out of the clip band — so it can never
     * keep stale hit bounds from its last visible frame. Paint and input
     * gate on {@link #editFieldVisible} plus the base's band gates.
     */
    private EditBox layoutEditField(List<String> profiles, int listX) {
        if (creatingNew && createField != null) {
            createField.setX(editorFieldX(cellX(listX, 0)));
            createField.setY(cellY(0) + editorFieldYOffset());
            return createField;
        }
        return layoutRenameField(profiles, listX);
    }

    /**
     * Whether this screen's active inline editor (create or rename) shows
     * at least one pixel in the clip band this frame — the C-8 render-truth
     * gate the paint passes and keyboard routing consult.
     */
    private boolean editFieldVisible(List<String> profiles) {
        if (creatingNew && createField != null) {
            int fieldY = cellY(0) + editorFieldYOffset();
            return fieldY + 16 > listClipTop() && fieldY < listClipBottom();
        }
        return renameEditorVisible();
    }

    private void renderCreateRow(GuiGraphicsExtractor ctx, int x, int y, int w,
                                  int mouseX, int mouseY) {
        // A neutral card like the others; its surface was painted in the
        // glass pass, so only the flat fallback and the hover lift draw here.
        float radius = ThemeManager.current().roundness().radiusSmall();
        boolean hot = !creatingNew && pointerIn(mouseX, mouseY, x, y, w, CARD_H);
        if (MaterialSurface.unified()) {
            MaterialSurface.selection(ctx, x, y, w, CARD_H, radius, false, hot ? 1f : 0f);
        } else if (!createRowGlassDrawn) {
            paintFlatRow(ctx, x, y, w);
        }

        // A plus in a circle (a capsule, so it follows Corner Radius) over
        // the label, or over the open name field. At rest the card has no
        // bottom line, so circle and label centre in it as one group (owner
        // 2026-11-12); while editing they keep the profile cards' lines.
        int ink = ThemeManager.color(hot || creatingNew ? ThemeToken.ON_BACKGROUND : ThemeToken.ON_BACKGROUND_SECONDARY);
        int lift = creatingNew ? 0 : createCardCentreOffset(this.font.lineHeight);
        com.aurora.client.ui.util.RenderUtil.drawRoundedOutlineAA(ctx, x + w / 2f - 11, y + lift + ICON_Y - 11, 22, 22,
                ThemeManager.current().roundness().capsule(22f), 1.2f, ink);
        MaterialIconRenderer.drawIcon(ctx, this.font, ADD_GLYPH, x + w / 2f, y + lift + ICON_Y, 16f, ink);
        if (!creatingNew) {
            AuroraFontRenderer.drawCentered(ctx, this.font, fitName("New profile"), x + w / 2, y + lift + NAME_Y, ink);
            return;
        }

        // "Create" confirm button on the card's bottom line — surface
        // painted in the glass pass; this draws the label.
        syncCreateControl(x, y, mouseX, mouseY);
        Button cb = createBtn();
        cb.render(ctx, x + INSET, y + LOWER_BTN_Y, btnW(), BTN_H, mouseX, mouseY);
    }

    /**
     * How far the resting "New profile" group (the 22px circle at
     * {@link #ICON_Y} and the label at {@link #NAME_Y}) moves down to sit in
     * the card's vertical centre.
     */
    static int createCardCentreOffset(int lineHeight) {
        int top = ICON_Y - 11, bottom = NAME_Y + lineHeight;
        return (CARD_H - (bottom - top)) / 2 - top;
    }

    /**
     * Glass pass for one card surface: the soft glow (the card's drop
     * shadow, so part of the surface) plus one DEPRESSED glass panel. Cards
     * are this screen's containers, so they take the window treatment
     * (depressed lighting, WINDOW_FILL tint) rather than the raised control
     * treatment; selection is never carried here. Returns whether the glass
     * drew (false = the content pass draws the flat card).
     */
    private boolean renderRowSurface(GuiGraphicsExtractor ctx, int x, int y, int w) {
        if (MaterialSurface.unified()) return GlassSurface.card(ctx,x,y,w,CARD_H,
                ThemeManager.current().roundness().radiusSmall());
        paintRowShadow(ctx, x, y, w);
        return GlassSurface.container(ctx, x, y, w, CARD_H,
                ThemeManager.current().roundness().radiusSmall(), BlurPanelRenderer.Priority.ROW);
    }

    // ------------------------------------------------------------------
    //  Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent _ev, boolean _doubleClicked) {
        // Waiting for a hotkey: the next press cancels the capture and is
        // consumed (the Keybind setting's rule).
        if (capturingHotkey != null) {
            stopCapture();
            return true;
        }
        if (searchField != null && _ev.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            boolean inSearch = searchField.isMouseOver(_ev.x(), _ev.y());
            searchField.setFocused(inSearch);
            if (inSearch) {
                commitEditors();
                searchField.mouseClicked(_ev, _doubleClicked);
                return true;
            }
        }
        return super.mouseClicked(_ev, _doubleClicked);
    }

    @Override
    protected boolean editorClickFirst(MouseButtonEvent _ev, boolean _doubleClicked) {
        if (createField != null && createField.mouseClicked(_ev, _doubleClicked)) return true;
        if (nameField != null && nameField.mouseClicked(_ev, _doubleClicked)) return true;
        return false;
    }

    @Override
    protected boolean rowAreaClickFirst(double mouseX, double mouseY) {
        // The "New profile" card, at its LIVE geometry (leading row 0 at the
        // current scroll) and only inside the clip band.
        if (leadingRowCount() == 0) return false;
        int listX = (this.width - listWidth()) / 2;
        int cardX = cellX(listX, 0), cardY = cellY(0);
        if (!creatingNew) {
            if (mouseX >= cardX && mouseX < cardX + cellWidth() && mouseY >= Math.max(cardY, listClipTop())
                    && mouseY < Math.min(cardY + CARD_H, listClipBottom())) {
                beginCreate();
                return true;
            }
            return false;
        }
        // "Create" confirm button in the create card — routed through its
        // semantic control. C-8: the hit test uses the create row's LIVE
        // geometry (leading row 0 at the current scroll), never the
        // control's possibly-stale bounds — the render walk only re-syncs
        // them while the row is visible — and availability is the control's
        // own clip-band gate, so a create row scrolled out of the band can
        // neither create nor fall through onto anything. Clicks within the
        // button's rect are CONSUMED even when the gate rejects them (empty
        // name, scrolled-away row): an unconsumed click would fall through
        // onto whatever lies under it. A rejected click is inert — no
        // animation, no sound, no create, no fall-through.
        int btnX = cardX + INSET;
        int btnY = cellY(0) + LOWER_BTN_Y;
        boolean inButton = mouseX >= btnX && mouseX < btnX + btnW()
                && mouseY >= btnY && mouseY < btnY + BTN_H;
        if (!inButton) return false;
        if (createControl.isAvailable()) {
            createControl.activateFromPointer(mouseX, mouseY, com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        }
        return true;
    }

    @Override
    protected boolean rowClicked(double mouseX, double mouseY, int listX, String name, int index) {
        // The card's controls see the click first, through their semantic
        // controls (enabled gate + exactly-once activation + sound + focus).
        SemanticActionControl options = optionsControls.get(name);
        if (options != null && options.activateFromPointer(mouseX, mouseY, InputConstants.MOUSE_BUTTON_LEFT)) {
            return true; // the options screen opened; nothing here to focus
        }
        SemanticActionControl hotkey = hotkeyControls.get(name);
        if (hotkey != null && hotkey.activateFromPointer(mouseX, mouseY, InputConstants.MOUSE_BUTTON_LEFT)) {
            this.setFocused(hotkey);
            return true;
        }

        // The name line renames; everywhere else on the card switches.
        int nameLeft = editorFieldX(listX);
        int nameTop = cellY(leadingRowCount() + index) + editorFieldYOffset();
        if (mouseX >= nameLeft && mouseX < nameLeft + nameColumnWidth()
                && mouseY >= nameTop && mouseY < nameTop + 16) {
            startEditing(index, name);
            return true;
        }

        // Click on the card body (not on a control) → switch if not active.
        // (The base's band + X-extent test is what gives the body click its
        // horizontal bound.)
        if (!name.equals(ProfileManager.getInstance().currentProfile())) {
            commitEditors();
            if (ProfileManager.getInstance().switchTo(name)) {
                flash("Switched to " + name);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent _kev) {
        int key = _kev.key();
        // Hotkey capture owns the keyboard: Esc or Backspace clears it; Ctrl,
        // Shift or Alt held waits for a second key (owner request 2026-11-12).
        if (capturingHotkey != null) {
            boolean clear = key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE;
            ProfileHotkey done = capture.press(clear ? AuroraKey.UNBOUND
                    : com.aurora.client.adapter.NativeKeyboard.toStored(key), clear);
            if (done != null) commitCapture(capturingHotkey, done);
            return true;
        }
        // C-8: the create editor follows the base rename contract — a field
        // scrolled out of the clip band is SUSPENDED (no keys routed), not
        // cancelled; scrolling back restores typing with focus and value.
        if (createField != null && createField.isFocused() && editFieldVisible(currentRows())) {
            if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                commitCreate();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                cancelCreate();
                return true;
            }
            if (createField.keyPressed(_kev)) return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (key == InputConstants.KEY_ESCAPE && !searchField.getValue().isEmpty()) {
                searchField.setValue("");
                return true;
            }
            if (key != InputConstants.KEY_ESCAPE && searchField.keyPressed(_kev)) {
                scroll.set(0);
                return true;
            }
        }
        // The base handles the rename field's ENTER/ESCAPE/forwarding.
        return super.keyPressed(_kev);
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent _kev) {
        // A modifier released before a second key binds the modifier itself.
        if (capturingHotkey != null) {
            ProfileHotkey done = capture.release(com.aurora.client.adapter.NativeKeyboard.toStored(_kev.key()));
            if (done != null) commitCapture(capturingHotkey, done);
            return true;
        }
        return super.keyReleased(_kev);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent _ev) {
        if (createField != null && createField.isFocused() && editFieldVisible(currentRows())
                && createField.charTyped(_ev)) return true;
        if (searchField != null && searchField.isFocused() && searchField.charTyped(_ev)) {
            scroll.set(0);
            return true;
        }
        return super.charTyped(_ev);
    }

    // ------------------------------------------------------------------
    //  Editor lifecycle (create editor + rename apply)
    // ------------------------------------------------------------------

    private void beginCreate() {
        commitEditors();
        creatingNew = true;
        if (searchField != null) searchField.setFocused(false);
        createField = new EditBox(this.font, 0, 0, nameColumnWidth(), 16, Component.literal("Name"));
        createField.setMaxLength(48);
        createField.setHint(Component.literal("Name…")); // the field's own placeholder, above its fill
        createField.setFocused(true);
        scroll.set(0);
    }

    private void commitCreate() {
        if (createField == null) return;
        String v = createField.getValue().trim();
        cancelCreate();
        if (v.isEmpty()) return;
        if (ProfileManager.getInstance().create(v)) {
            flash("Created " + v);
        } else {
            flash("Could not create (name taken?)");
        }
    }

    private void cancelCreate() {
        creatingNew = false;
        createField = null;
        createBtn = null;
    }

    @Override
    protected void applyRename(String oldName, String newValue) {
        // Renaming to the same name is a no-op (no manager call, no toast).
        if (newValue.equals(oldName)) return;
        if (ProfileManager.getInstance().rename(oldName, newValue)) {
            flash("Renamed → " + newValue);
        } else {
            flash("Could not rename (name taken?)");
        }
    }

    @Override
    protected void commitEditors() {
        if (creatingNew) commitCreate();
        if (editingIndex >= 0) commitEdit();
    }

    /** Toast from the options screen once it returns here (same package). */
    void notice(String text) {
        flash(text);
    }

    // ------------------------------------------------------------------
    //  Frame tails (screen-owned order)
    // ------------------------------------------------------------------

    @Override
    protected void paintEditorGlassPass(GuiGraphicsExtractor ctx, List<String> rows, int listX) {
        // Position ALWAYS (fresh bounds every frame — a hidden editor must
        // never keep a stale rect its last visible frame could leave in the
        // band), then paint only when a pixel is visible. The base scissors
        // this call to the list clip (C-8).
        EditBox field = layoutEditField(rows, listX);
        if (field == null || !editFieldVisible(rows)) return;
        if (field instanceof GlassEditBox glassField) glassField.aurora$renderGlassPass(ctx);
    }

    @Override
    protected void paintTail(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta,
                             List<String> rows, int listX) {
        // Inline editors render on top so the caret draws above row fills,
        // and UNDER THE LIST CLIP (C-8 — renderEditorClipped): a partially
        // visible editor paints only its visible pixels, a fully hidden one
        // nothing (bounds still refreshed above via the glass-pass layout).
        // Their surface was painted in the glass pass; this is content only.
        EditBox editor = layoutEditField(rows, listX);
        if (editor != null && editFieldVisible(rows)) {
            renderEditorClipped(ctx, editor, listX, mouseX, mouseY, delta);
        }

        if (controlsChip != null) {
            FeatureSetting.hoverTooltip("profiles-separate-controls", () -> AuroraConfig.get().profilesOwnControls
                            ? "On: each profile keeps its own key bindings, Aurora's and Minecraft's, and switching applies them."
                            : "Off: key bindings stay the same whichever profile is active.",
                    controlsChip.getX(), controlsChip.getY(), controlsChip.getWidth(), controlsChip.getHeight(),
                    mouseX, mouseY);
        }

        // One tooltip key for every chip: a frame with no chip under the
        // pointer passes an empty rect, which resets the dwell.
        boolean capturing = hoveredChip != null && hoveredChip.equals(capturingHotkey);
        FeatureSetting.hoverTooltip("profile-hotkey", () -> capturing
                        ? "Press the key that switches to this profile in game. Hold Ctrl, Shift or Alt first for a combination. Esc or Backspace clears it."
                        : "Click to set the key that switches to this profile in game.",
                hoveredChip == null ? -1000 : hoveredChipX, hoveredChipY, btnW(), BTN_H, mouseX, mouseY);

        // Toast (above the editor here — this screen's historical order;
        // the Waypoint screen draws it the other way around).
        renderToast(ctx);
        FeatureSetting.drawPendingTooltip(ctx, this.width, this.height);
    }
}
