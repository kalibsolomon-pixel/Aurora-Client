package com.aurora.client.module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final ModuleManager INSTANCE = new ModuleManager();
    public static ModuleManager getInstance() {
        return INSTANCE;
    }

    private final List<Module> modules = new ArrayList<>();

    private ModuleManager() {
        modules.add(new Module("zoom", "Zoom", "Optical-style zoom bound to a key. Hold it to narrow your field of view and magnify whatever you're looking at."));
        modules.add(new Module("full_bright", "Full Bright", "Overrides Minecraft's gamma so dark areas become fully visible. Caves, the underwater, and night-time terrain light up."));
        modules.add(new Module("no_fog", "No Fog", "Pushes back the distance fog that normally hides far terrain. You see further across oceans and open landscapes."));
        modules.add(new Module("pack_tweaks", "Pack Tweaks", "A bundle of small quality-of-life visual tweaks — totem particle size, shield rendering, and water/lava clarity."));
        modules.add(new Module("player_health", "Entity Health", "Floats a numeric health value above mobs and players so you can read exactly how much damage is left to deal."));
        modules.add(new Module("free_look", "FreeLook", "Hold a key to swivel the camera around your character without changing the direction you're actually moving or facing."));
        modules.add(new Module("saturation_bar", "Saturation Bar", "Overlays your hidden saturation level on top of the food bar."));
        modules.add(new Module("block_overlay", "Block Overlay", "Replaces the thin vanilla block-selection box with a customizable outline and/or colored fill."));
        modules.add(new Module("toggle_sprint_sneak", "Toggle Sprint/Sneak", "Turns sprint and sneak into press-once toggles instead of hold-to-keep keys."));
        modules.add(new Module("alerts", "Alerts", "On-screen popup warnings for low armor durability, low hunger, and potion effects about to expire. All alerts share the same popup and sound system."));
        modules.add(new Module("crosshair", "Crosshair", "Replaces the vanilla crosshair with a fully customizable one."));
        modules.add(new Module("hitbox", "Hitbox", "Draws entity hitboxes with your own color, line width, and optional eye-level and look-direction lines."));
        modules.add(new Module("better_hitreg", "Better Hitreg", "Client-side hit registration feedback for PvP — plays your hit sound and hurt animation the instant you swing instead of waiting for the server, tracks ghosted and misplaced hits, and adds reach rings, target hitboxes and a practice arena. Original project by Jass."));
        modules.add(new Module("hit_color", "Hit Color", "Recolors the red flash that overlays an entity when it takes damage."));
        modules.add(new Module("info_module", "Info HUD", "A configurable corner readout of useful at-a-glance info — FPS, coordinates, time of day, facing, biome, dimension, light level, playtime and more."));
        modules.add(new Module("cps", "CPS", "Shows your current clicks-per-second as a HUD counter."));
        modules.add(new Module("armor_hud", "Armor HUD", "Displays your four equipped armor pieces and their remaining durability on the HUD."));
        modules.add(new Module("reach_display", "Reach Display", "Shows the exact distance to the last entity you attacked."));
        modules.add(new Module("potion_hud", "Potion HUD", "A cleaner replacement for the default status-effect icons, listing your active potion effects with a readable countdown timer."));
        modules.add(new Module("ping", "Ping", "Surfaces connection latency as real numbers — as a HUD readout, as a value in the tab list, and optionally under player nametags."));
        modules.add(new Module("totem_pop", "Totem Pop Counter", "Counts how many Totems of Undying you've popped, and optionally tracks nearby players' pops too."));
        modules.add(new Module("stats", "Stats Overlay", "A combat readout — kills, deaths, K/D and session time, plus Better Hitreg's fight statistics: fights, fight time and last-fight accuracy."));
        modules.add(new Module("waypoints", "Waypoints", "Place persistent world markers rendered as beacon beams and/or block highlights."));
        modules.add(new Module("container_preview", "Container Preview", "Hover over a shulker box (or your ender chest) in any inventory to see its full contents in a grid tooltip."));
        modules.add(new Module("item_physics", "Item Physics", "Gives dropped items more natural physics — they lie flat where they land instead of hovering and spinning."));
        modules.add(new Module("particles", "Particles", "Per-particle control over every vanilla particle type — hide ones you find distracting and resize the rest."));
        modules.add(new Module("item_scale", "Item Scale", "Customize the scale, rotation, and screen translation of individual item models."));
        modules.add(new Module("held_item_seams", "Held Item Seam Fix", "Hides the hairline seams that can appear between the faces of your first-person held items at certain camera angles."));
        modules.add(new Module("animations", "Animations", "Swing & view-bob curves, classic 1.8-style damage camera tilt, retro backwards-walking & sneak poses, idle held-item sway, and smooth entity rotation."));
        modules.add(new Module("hotbar_bounce", "Hotbar Bounce", "Pops a small bounce/pulse on a hotbar slot the moment an item lands in it or a stack grows."));
        modules.add(new Module("keystrokes", "Keystrokes", "Displays your WASD, mouse, jump, and sneak keys as a HUD overlay that lights up in real time as you press them."));
        modules.add(new Module("minimap", "Minimap", "A top-down map of the world around you, shown as a HUD overlay. Renders terrain, waypoints, and nearby entities as colored dots."));
        modules.add(new Module("world_map", "World Map", "A full-screen, pannable world map of your explored terrain."));
        modules.add(new Module("resourcepack_browser", "Resourcepack Browser", "Browse and install community resource packs directly from Modrinth. Search, preview, and one-click install straight into your resourcepacks folder."));
        modules.add(new Module("reflex", "Minecraft Reflex", "Uses the Nvidia Reflex principle to reduce rendering latency — locks frame pacing by estimating CPU and GPU time so input is sampled as late as possible."));
        modules.add(new Module("miscellaneous", "Miscellaneous", "A temporary home for smaller settings without a dedicated screen of their own yet — smooth camera, frame pacing, latency, tick sync, input handling, server-list dragging, and compliance."));
    }

    public List<Module> getModules() {
        return modules;
    }
}

