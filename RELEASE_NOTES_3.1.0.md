# Aurora Client 3.1.0

Aurora 3.1 refines the interface, unifies mapping and waypoints, adds a portable
glass renderer for VulkanMod, and improves settings reliability.

## Refined defaults

- New installations receive the owner's selected dark theme, Fira Sans text,
  visual settings, keybinds, and anchored HUD layout.
- Armor, potion effects, and Keystrokes form the default HUD. Optional Info,
  CPS, Ping, Reach, Totem Pops, and Minimap displays start disabled.
- Existing configurations, profiles, keybinds, waypoints, and map data are preserved.
- Reset Positions restores the new layout and scaling while preserving which
  HUD elements you have hidden or locked.
- Corrupt configurations are backed up before recovery can save new settings.

## Interface

- More consistent manager windows, content-sized feature settings, color picker,
  HUD editing, scroll fades, dialogs, and tooltip timing.
- Refined themed buttons, module outlines, keyboard and mouse keycaps, font
  fallback, and server-list dragging and ping presentation.

## Maps and waypoints

- Minimap and World Map share one mapping engine with persistent terrain,
  bounded caches, and safer texture cleanup.
- Shared waypoint editing, grouping, search, filtering, and navigation.
- Sharper map shapes and markers using Aurora's physical-pixel rendering,
  with clearer label placement and fewer overlapping markers.
- Existing region formats remain readable; legacy display-name map data can
  be imported explicitly without deleting the original files.

## Compatibility and reliability

- Glass uses a backend-compatible rendering path with VulkanMod while retaining
  the OpenGL and Sodium paths. Native acceptance covers the recorded Windows/AMD
  configuration with VulkanMod 0.6.8 and Sodium 0.8.14 for Minecraft 1.21.11.
- OpenGL GPU timestamp pacing is skipped safely on Vulkan devices; ordinary
  frame limits remain available.
- Ordered configuration/profile saves, targeted Windows file-replacement retries,
  and shutdown flushing prevent stale saves from replacing newer settings.
- Profile creation and switching see queued saves immediately. Keybind edits
  also persist their vanilla Controls settings.

Client-side Fabric mod for **Minecraft 1.21.11**. Requires **Java 21 or newer**,
**Fabric Loader 0.16.0 or newer**, and **Fabric API**. Built with Loader 0.19.2 and
Fabric API 0.141.4+1.21.11. Install `aurora-3.1.0.jar` in your instance's `mods` folder.

Aurora Launcher is released separately. Launcher availability remains controlled
by its production release manifest.
