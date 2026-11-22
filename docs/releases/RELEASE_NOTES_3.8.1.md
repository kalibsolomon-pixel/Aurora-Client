# Aurora Client 3.8.1

One JAR per Minecraft version: **26.1.2**, **26.2**, and **26.3**, built from the same
source commit. Install the one matching your Minecraft version with Fabric Loader
and Fabric API. Java 25 is required.

## Ephemera Performance Ownership

- **Ephemera-first engine ownership:** When paired with Ephemera builds that
  advertise `ephemera:engine_ownership` (and `ephemera:world_visual_ownership` on
  supported lines), Aurora defers overlapping GUI item-atlas scheduling, redundant
  GPU timing/Reflex queries, and supported world shader hooks to Ephemera so both
  mods run without redundant per-frame work. Aurora retains the Armor HUD, glint
  styling, SDF text, flat-face culling, HUD modules, and overlays.
- **Cooperative frame pacing:** When Ephemera's frame pacer is active, Aurora
  forwards its effective Global Frame Cap, UI FPS Limit, and VSync preference to
  Ephemera instead of racing it, and restores Aurora's own pacer controls and
  fallback pacing automatically if Ephemera's pacer is disabled. Fully backward
  compatible with earlier Ephemera 1.1.0 builds via capability detection.

## Fixes & Changes

- **Removed Smooth Camera:** Removed the Smooth Camera feature, settings rows, and
  camera smoothing branch (Free Look remains intact). Existing configs and saved
  profiles carrying `smoothCamera` / `smoothCameraStrength` load cleanly and skip
  the retired fields quietly without warnings.
- **Per-feature Reset fixes:** Fixed Reset buttons across 11 features whose prefix
  lists left fields unreset (`no_fog`, `toggle_sprint_sneak`, `info_module`,
  `armor_hud`, `reach_display`, `potion_hud`, `totem_pop`, `waypoints`,
  `particles`, `interface`, and `animations`).
- **Restored missing/dead settings:** Wired `resourcepackBrowserEnabled` to gate
  the Modrinth browser button, card action, and screen; surfaced **Background
  Frame Limit** / **Background FPS** on **Settings → Performance**.
- **UI input & small-scale layout fixes:**
  - Pressing Escape on a Settings-tab popup (colour picker, dropdown, keybind, or
    text field) now dismisses only the popup instead of closing Aurora's screen.
  - Fixed mouse-input layout refreshes in `AuroraScreen`, `FeatureDetailScreen`,
    and `ManagerListScreen` to run inside Aurora's text scope so hitboxes stay
    aligned with drawn controls when the text renderer is set to Aurora UI Only.
  - Compacted the Waypoint Editor layout so it fits cleanly at 240 px GUI height
    (Scale 3 at 1080×720 and Scale 2 at 854×480).
  - Measured and shortened Resource Pack Manager header buttons below 390 px width
    and ellipsized the title so controls never overlap on narrow viewports.

## Verification

- Full 14-row parity GPU acceptance across Minecraft **26.1.2**, **26.2**, and
  **26.3** (OpenGL, VulkanMod, and native Vulkan), 56 judged runs:
  **0.031–0.128 ms** mean and **<= 0.229 ms** p95 Aurora CPU per frame (budget:
  0.25 ms mean / 0.40 ms p95), **0.0** UI fills, **0.0** glass captures, all native
  mixins healthy, and zero Aurora warnings or errors across every row.
