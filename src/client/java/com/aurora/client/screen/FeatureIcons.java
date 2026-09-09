package com.aurora.client.screen;

import java.util.HashMap;
import java.util.Map;

/**
 * Material Icons Rounded displayed under each feature tile's title text.
 *
 * <p>All icons are pulled from the Material Icons Rounded Font
 * registered under 'aurora:material_symbols'.
 */
public final class FeatureIcons {

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        // ============ MODULES TAB ============
        ICONS.put("theme",                 "\uE40A"); // palette
        ICONS.put("zoom",                  "\uE8FF"); // zoom_in
        ICONS.put("full_bright",           "\uE430"); // wb_sunny (brightness)
        ICONS.put("no_fog",                "\uF15C"); // cloud
        ICONS.put("pack_tweaks",           "\uE87B"); // extension (puzzle)
        ICONS.put("player_health",         "\uE87D"); // favorite (heart)
        ICONS.put("free_look",             "\uE8F4"); // visibility (eye)
        ICONS.put("saturation_bar",        "\uEAAC"); // cookie
        ICONS.put("tab_ping",              "\uE4E3"); // flash_on
        ICONS.put("block_overlay",         "\uF720"); // deployed_code
        ICONS.put("toggle_sprint_sneak",   "\uE566"); // directions_run
        ICONS.put("alerts",                "\uE002"); // warning
        ICONS.put("crosshair",             "\uE55C"); // gps_fixed (target)
        ICONS.put("hitbox",                "\uE3C2"); // crop_free (box outline)
        ICONS.put("hit_color",             "\uE3B8"); // colorize (dropper)
        ICONS.put("better_hitreg",         "\uF889"); // swords — crossed blades, melee PvP; subsetting requires the real full_material.ttf (see subset_script.py)
        ICONS.put("info_module",           "\uE88E"); // info
        ICONS.put("cps",                   "\uE323"); // mouse
        ICONS.put("armor_hud",             "\uF014"); // gpp_maybe (shield / alert)
        ICONS.put("reach_display",         "\uE41C"); // straighten (ruler)
        ICONS.put("potion_hud",            "\uE3F3"); // healing
        ICONS.put("ping",                  "\uEF06"); // android_cell_4_bar (signal)
        ICONS.put("totem_pop",             "\uE800"); // exposure_plus_1 (pop counter)
        ICONS.put("stats",                 "\uE24B"); // assessment (bar chart)
        ICONS.put("waypoints",             "\uE0C8"); // location_on (pin)
        ICONS.put("container_preview",     "\uE1A1"); // inventory_2 (shulker preview)
        ICONS.put("item_physics",          "\uE574"); // category (shapes physics)
        ICONS.put("particles",             "\uE819"); // sunny_snowing (particles)
        ICONS.put("item_scale",            "\uE85B"); // aspect_ratio
        ICONS.put("animations",            "\uE038"); // animation
        ICONS.put("keystrokes",            "\uE312"); // keyboard
        ICONS.put("minimap",               "\uE2C5"); // file_map
        ICONS.put("world_map",             "\uF3CA"); // map_search
        ICONS.put("resourcepack_browser",  "\uE8B0"); // browse_gallery (grid of tiles)
        ICONS.put("reflex",                "\uE4E3"); // flash_on

        // ============ SETTINGS TAB ============
        ICONS.put("custom_title",          "\uE264"); // title
        ICONS.put("hotbar_bounce",         "\uE8D5"); // swap_vert (a Modules-grid card)
        // The eight former Settings-tab tiles merged into "miscellaneous"
        // (2026-09-09) had their own glyphs (videocam, av_timer, flash_on,
        // keyboard, drag_handle, verified_user, accessibility_new); they
        // were removed with the tiles. flash_on/keyboard remain in use by
        // other ids above.
        ICONS.put("miscellaneous",         "\uE574"); // category (three shapes) - shared with item_physics, already in the font subset

        // Dropdown chevrons (EnumSetting) — referenced by codepoint here so
        // subset_script.py includes them in the material-symbols font subset.
        ICONS.put("_dropdown_expand_more", "\uE5CF"); // expand_more (chevron down)
        ICONS.put("_dropdown_expand_less", "\uE5C6"); // expand_less (chevron up)
    }

    private FeatureIcons() {}

    /** Returns the icon glyph for the given feature id, or "" if none. */
    public static String get(String id) {
        String v = ICONS.get(id);
        return v == null ? "" : v;
    }

    /** Returns all unique characters used as icons to pre-rasterize them in the atlas. */
    public static java.util.Set<Character> getIconCodepoints() {
        java.util.Set<Character> chars = new java.util.HashSet<>();
        for (String s : ICONS.values()) {
            if (s != null && !s.isEmpty()) {
                chars.add(s.charAt(0));
            }
        }
        return chars;
    }
}
