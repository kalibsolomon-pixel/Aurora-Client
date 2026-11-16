# Aurora Client 3.5.1

One JAR per Minecraft version: 26.1.2, 26.2 and 26.3, built from the same
source commit. Install the one matching your Minecraft version with Fabric
Loader and Fabric API. Java 25 is required.

## New

- **Pulsar** (off by default): Phenomena's combat analytics and recording as an
  Aurora feature, with a six-tab dashboard (Overview, Offense, Defense, Fight
  Flow, History, Settings). Open it from its feature details, with K, or with
  `/pulsar`. It describes other players, so it ships off as an information
  advantage. See `docs/PULSAR.md`.
- **Scoreboard HUD** (off by default): replaces the vanilla sidebar with a
  movable, resizable module with Vanilla, Solid or no background, colour and
  scale. The vanilla sidebar returns under F3.
- **Armor HUD** shows only occupied slots, with optional main and off hand. Its
  Vanilla background is one continuous hotbar strip, horizontal or vertical.
  Vanilla backgrounds are offered only on Armor HUD and Potion HUD; other
  modules fold Vanilla to Solid.
- **Alerts** are two HUD modules, Armor Alert and Effect Alert, instead of a
  popup card. A durability warning follows its item, so wear and moving it
  never re-fire; it re-arms once that item is mended or replaced.
- **Hit Color** Flash Style: Vanilla, Flat (default), Pulse, Health gradient
  (ships off) and Rainbow.

## Changed

- **Every Aurora window keeps one size.** Settings pages and feature detail
  windows no longer shrink to fit their content; long pages scroll.
- **Better Hitreg** now keeps only Custom Hitreg: Aurora's own hit feedback
  after Hitreg Delay, with Safe Regs Only and Ignore Shield Holders. Tracking,
  alert delays, audio, render, practice arena, colours, the HUD score and its
  six keybinds are removed.
- **Removed:** Hide Reload Overlay and Hide Title Fade (resource reloads and the
  vanilla title screen behave as vanilla again), and entity movement smoothing
  in Animations.
- Unused Aurora font families load on demand; the selected family still loads
  at startup, so font reloads are faster.

## Fixes

- Theme sliders (Background Opacity, Blur Radius, Corner Radius) can be dragged
  on the track itself; their click area sat below it.
- Collapsing a section on the Settings tab hides its rows, and dropdowns below
  it keep working.
- Crosshair previews are centred in their wells.
- The "+" buttons in Item Scale and Effect Expiry respond across their whole
  face, and Pulsar dropdowns no longer pick a hidden option from their bottom
  edge.
- Moving detail behind blurred panels no longer flickers at some blur radii.
