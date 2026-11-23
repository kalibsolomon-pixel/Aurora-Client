package com.aurora.client.screen;

import com.aurora.client.compat.EphemeraOwnership;
import com.aurora.client.config.AuroraConfig;
import com.aurora.client.screen.setting.*;

import java.util.ArrayList;
import java.util.List;

/**
 * The Settings tab's pages: which entries each sub-tab shows, in order, and the
 * rows that belong to a page rather than to a feature (split out of
 * {@link FeatureRegistry}, 2026-11-23).
 *
 * <p>The tab shows one page at a time, chosen from the row of sub-tabs at its
 * top. A page is a list of entries; each entry is a header (with the feature's
 * own switch for Theme and Custom Title) over its rows. Most entries are
 * Settings-tab catalog features (their health note replaces the summary line
 * while a mixin is down); the rest are {@link FeatureMetadata#pageGroup groups}
 * of rows owned by Interface or Performance, or by no feature at all. Six pages
 * since 2026-11-23: Appearance, World &amp; HUD, Performance, Audio &amp; Keys,
 * Privacy, Tools.
 */
public final class SettingsPages {

    /**
     * One page of the Settings tab, chosen from the sub-tabs at its top: a
     * label and the entries it shows, in order. An entry is a Settings-tab
     * catalog feature or a {@link FeatureMetadata#pageGroup} of rows that
     * another feature owns.
     */
    public record Page(String label, List<FeatureMetadata> entries) {}

    /** Rows that belong to a feature the registry builds, shown on a page as a group. */
    record FeatureRows(FeatureSetting uiFpsLimit, FeatureSetting debugEntryCache, FeatureSetting minimapRefreshCap,
                       FeatureSetting unfocusedLimit, FeatureSetting unfocusedFps) {}

    private static final List<Page> PAGES = new ArrayList<>();
    private static boolean ephemeraSettingsAttached = false;

    private SettingsPages() {}

    /** The pages in sub-tab order; adds Ephemera's engine settings to Performance once Ephemera reports them. */
    static List<Page> pages() {
        if (!ephemeraSettingsAttached && com.aurora.client.compat.EphemeraBridge.hasSettings()) {
            attachEphemeraSettings();
        }
        return PAGES;
    }

    /** Builds the pages once, after the registry has registered every Settings-tab feature. */
    static void build(FeatureRows shared) {
        AuroraConfig cfg = AuroraConfig.get();
        AuroraConfig defaults = AuroraConfig.defaults();

        FeatureSetting reducedMotion = new BooleanSetting("Reduced Motion",
                () -> cfg.reducedMotion, value -> { cfg.reducedMotion = value; AuroraConfig.save(); })
                .resetDefault("reducedMotion", () -> defaults.reducedMotion)
                .description("Skip overlay fades and make keyboard focus scrolling instant.");
        PAGES.add(new Page("Appearance", List.of(
                entry("theme").pageSummary("The accent, window tint and glass of every Aurora screen."),
                entry("text_fonts").pageSummary("The typeface and style of Aurora's text, and where it applies."),
                group("interface_motion", "Motion", reducedMotion).pageSummary("Animation and motion preferences for Aurora's menus."),
                entry("custom_title").pageSummary("Aurora's own title screen in place of the vanilla one."))));

        List<FeatureMetadata> worldEntries = new ArrayList<>();
        if (EphemeraOwnership.ownsWorldVisuals()) {
            worldEntries.add(group("world_visuals", "World Visuals", FeatureRegistry.mappingAction("Open Ephemera Settings",
                    "Adjust Gradient Dithering and Smooth Fog in Ephemera's Visuals tab.",
                    com.aurora.client.compat.EphemeraBridge::openConfigScreen)));
        } else {
            worldEntries.add(entry("gradient_dither").pageSummary("Removes color banding from skies, fog and dark scenes."));
            worldEntries.add(entry("smooth_fog").pageSummary("Softens harsh linear distance fog edges across the horizon."));
        }
        worldEntries.add(entry("tab_list").pageSummary("Clean up and restyle the multiplayer player list."));
        worldEntries.add(entry("nametags").pageSummary("Third-person self nametag and floating nametag styling."));
        PAGES.add(new Page("World & HUD", List.copyOf(worldEntries)));

        PAGES.add(new Page("Performance", List.of(
                entry("frame_pacing").pageSummary("Frame cap, even frame pacing and low-latency rendering."),
                entry("decoupled_input").pageSummary("Applies mouse movement at the very start of each frame."),
                group("menu_frame_rate", "Menu Frame Rate", shared.uiFpsLimit()).pageSummary("The framerate of Aurora's own screens."),
                group("debug_screen", "Debug Screen", shared.debugEntryCache()).pageSummary("Makes the F3 screen cheaper to draw."),
                group("maps", "Maps", shared.minimapRefreshCap()).pageSummary("Limits how often the minimap redraws its terrain."),
                group("background", "Background", shared.unfocusedLimit(), shared.unfocusedFps())
                        .pageSummary("Slows drawing while another window has focus."))));

        FeatureSetting hudEditorKey = new KeybindSetting("HUD Editor Key",
                () -> cfg.hudEditorKey, v -> cfg.hudEditorKey = v).resetDefault(() -> defaults.hudEditorKey).keyId("key.aurora.hud_editor")
                .description("Opens the HUD editor in game, where you move and resize HUD modules; its Aurora Settings button leads here. Also listed in Minecraft's Controls. Press ESC or Backspace while rebinding to clear it.");
        PAGES.add(new Page("Audio & Keys", List.of(
                entry("sound_volumes").pageSummary("Lower or mute individual Minecraft sounds (0–100% attenuation only)."),
                group("aurora_keys", "Aurora Keys", hudEditorKey).pageSummary("Keys that open Aurora's own screens."))));

        PAGES.add(new Page("Privacy", List.of(
                entry("streamer_mode").pageSummary("Masks your username, skin, coordinates, server address, and optionally opponent nametags on your screen."))));

        FeatureSetting openScreenshots = FeatureRegistry.mappingAction("Open Screenshot Manager",
                "Browse, search, rename, delete, copy to clipboard, and crop Minecraft screenshots in-game.",
                screen -> com.aurora.client.adapter.NativeUi.setScreen(net.minecraft.client.Minecraft.getInstance(),
                        new ScreenshotManagerScreen(screen)));
        FeatureSetting resourcepackFolders = new BooleanSetting("Resource Pack Folders",
                () -> cfg.resourcepackFoldersEnabled, v -> { cfg.resourcepackFoldersEnabled = v; AuroraConfig.save(); })
                .resetDefault("resourcepackFoldersEnabled", () -> defaults.resourcepackFoldersEnabled)
                .description("Discovers resource packs inside subfolders of resourcepacks/ and opens Aurora's folder-aware manager in Options → Resource Packs.");
        FeatureSetting openPackManager = ButtonSetting.semantic("Open Pack Manager…",
                () -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    if (mc != null) {
                        com.aurora.client.adapter.NativeUi.setScreen(mc,
                                ResourcePackManagerScreen.open(com.aurora.client.adapter.NativeUi.screen(mc)));
                    }
                })
                .description("Opens Aurora's folder-aware resource pack manager with subfolder navigation, search, and load-order controls.");
        FeatureSetting searchInstant = new BooleanSetting("Instant Search",
                () -> cfg.searchInstant, v -> { cfg.searchInstant = v; AuroraConfig.save(); }).resetDefault("searchInstant", () -> defaults.searchInstant)
                .description("Focuses the search bar when the Aurora menu opens or you return to the Features tab, so you can type straight away.");
        FeatureSetting searchTolerance = new SegmentedSetting<>("Search Typo Tolerance", SearchTolerance.class,
                () -> SearchTolerance.of(cfg.searchTypoTolerance), v -> { cfg.searchTypoTolerance = v.ordinal(); AuroraConfig.save(); },
                SearchTolerance::label).resetDefault(() -> SearchTolerance.of(defaults.searchTypoTolerance))
                .description("How many typos a search word can have and still find a match. Exact only finds exact matches; short words always need to be exact.");
        FeatureSetting copyDiagnostics = ButtonSetting.semantic("Copy Diagnostics", "Copy",
                com.aurora.client.diagnostics.DiagnosticsReport::runAndCopy)
                .description("Closes this menu, measures frame times for 5 seconds, then copies a text report to your clipboard: your mod list, any Aurora feature that could not start and why, the renderer, recent Aurora errors and the frame-time result. Nothing is sent anywhere; paste it where you are asking for help. It contains no account, world or server names.");
        PAGES.add(new Page("Tools", List.of(
                group("screenshots", "Screenshots", openScreenshots).pageSummary("Browse, search, rename, copy, crop and delete screenshots in-game."),
                group("resource_packs", "Resource Packs", resourcepackFolders, openPackManager)
                        .pageSummary("Organize resource packs into nested folders with search and load-order controls."),
                entry("server_list").pageSummary("Reorder servers in the Multiplayer list by dragging."),
                group("search", "Search", searchInstant, searchTolerance).pageSummary("How the search bar finds features and settings."),
                group("support", "Support", copyDiagnostics).pageSummary("Collects a diagnostics report to attach to bug reports."))));
    }

    /** A registered Settings-tab feature, by id. */
    private static FeatureMetadata entry(String id) {
        return FeatureRegistry.settings().stream().filter(meta -> meta.id.equals(id)).findFirst().orElseThrow();
    }

    /**
     * A page entry that groups rows owned by other features under one
     * header. It has no switch of its own and no detail screen; its id is
     * namespaced so it can never collide with a catalog feature id.
     */
    private static FeatureMetadata group(String id, String name, FeatureSetting... rows) {
        return new FeatureMetadata("settings_page." + id, name, "", () -> true, v -> {}, List.of(rows))
                .noSettingsToggle(true)
                .pageGroup(true);
    }

    private static void attachEphemeraSettings() {
        ephemeraSettingsAttached = true;
        List<FeatureSetting> rows = new ArrayList<>();
        rows.add(ButtonSetting.semantic("Open Ephemera Settings", () ->
                com.aurora.client.compat.EphemeraBridge.openConfigScreen(
                        com.aurora.client.adapter.NativeUi.screen(net.minecraft.client.Minecraft.getInstance())))
                .description(com.aurora.client.compat.EphemeraBridge::statusSummary));
        String currentCategory = null;
        for (com.aurora.client.compat.EphemeraBridge.OptionInfo opt : com.aurora.client.compat.EphemeraBridge.options()) {
            String cat = opt.category();
            if (cat != null && !cat.isEmpty() && !cat.equals(currentCategory)) {
                rows.add(new SectionHeaderSetting(cat));
                currentCategory = cat;
            }
            String key = opt.key();
            String desc = opt.requiresRestart()
                    ? opt.description() + " (Requires restart.)"
                    : opt.description();
            rows.add(new BooleanSetting(opt.label(),
                    () -> com.aurora.client.compat.EphemeraBridge.getBoolean(key),
                    v -> com.aurora.client.compat.EphemeraBridge.setBoolean(key, v))
                    .resetDefault(opt::defaultValue)
                    .description(desc));
        }
        FeatureMetadata ephemeraGroup = group("ephemera_engine", "Ephemera Engine",
                rows.toArray(FeatureSetting[]::new))
                .pageSummary("Hardware-accelerated terrain, culling, and visuals provided by Ephemera.");
        for (int i = 0; i < PAGES.size(); i++) {
            Page page = PAGES.get(i);
            if ("Performance".equals(page.label())) {
                List<FeatureMetadata> entries = new ArrayList<>(page.entries());
                entries.add(ephemeraGroup);
                PAGES.set(i, new Page(page.label(), List.copyOf(entries)));
                return;
            }
        }
    }

    /** Search Typo Tolerance's segments; the ordinal is the stored value (0–3). */
    private enum SearchTolerance {
        EXACT("Exact"), ONE("1"), TWO("2"), THREE("3");

        private final String label;

        SearchTolerance(String label) { this.label = label; }

        String label() { return label; }

        static SearchTolerance of(int value) {
            return values()[Math.max(0, Math.min(values().length - 1, value))];
        }
    }
}
