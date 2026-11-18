# Aurora Client 3.5.0

One JAR per Minecraft version: 26.1.2, 26.2 and 26.3, built from the same
source commit. Install the one matching your Minecraft version with Fabric
Loader and Fabric API. Java 25 is required.

## Look and feel

- **UI polish overhaul.** A graphite window and an Indigo accent for new
  installs (saved colours are kept); title and heading text sizes; feature
  tiles show their state by icon colour, with a hover outline and a settings
  chevron; detail and Settings windows fit their content; inline sliders with
  short tracks; colour values shown as hex beside each swatch; one solid accent
  Done button; 24 px list rows; clearer empty states. The sidebar's first item
  is now **Features**.
- **Shader-drawn shapes.** Rounded panels, outlines and circles are drawn by a
  shader instead of many small fills. If a resource pack breaks the shader,
  Aurora logs one warning and falls back to the old path.
- **Corner Radius and Blur Radius** sliders replace the three corner styles;
  switches, sliders and scrollbars follow the chosen radius.
- **Settings** is split into sub-tab pages. **Particles** shows each particle as
  a card with its own detail screen.
- **Title screen** redesigned: Singleplayer, Multiplayer, Realms, Settings and
  (with Mod Menu) Mods, a skin preview and an account card.

## Features

- **Search** across features and settings, tolerant of small typos, in the menu,
  Profiles, Waypoints, particle lists and the item and effect pickers.
- **Text options:** font weight, italic, underline, case and a HUD text shadow.
- **Key combos:** any Aurora, Minecraft or profile hotkey can be Ctrl, Shift or
  Alt plus a key.
- **Profiles:** icons, hotkeys, and options to rename, duplicate or delete.
- **Smooth Lines (SDF)** for Hitboxes and Block Overlay (off by default): a
  distance-field line shader with anti-aliased edges and round corners.

## Performance

A new **Settings → Performance** page. Measured optimizations ship on and stand
down when a mod that owns the same area is loaded: no reload overlay or title
fade, no level-loading delay, throttled sound-listener updates, a cached F3
screen, merged text decorations, GUI item baking, freed sprite mipmaps and a
minimap refresh cap.

## New defaults (new installs only)

Graphite background (#16171D), Indigo accent (#5C6BC0), armor durability bars
on and HUD text shadow Normal. Existing configs keep their saved values.

Fixes include a stray line under the Keystrokes Space key at some HUD scales.
