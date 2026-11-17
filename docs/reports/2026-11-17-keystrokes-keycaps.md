# Keystrokes key styles

2026-11-17. Owner request: "options for 3d shaded keys (shaped like cherry style keycaps) along
with the option to use the UI corner radius for 3d shaded keys, and flat color/translucent keys."

## What changed

- `keystrokesKeyStyle`: **Flat** (default) and **3D Keycap**. Owner correction (same day): the
  first version kept the legacy Translucent shell-and-rim style; it is removed. Flat is the
  Lunar-style key: the legend on a flat coloured face, no outline, shell or travel, the face
  swapping to the accent while pressed. Saved `TRANSLUCENT` values load as Flat.
- 3D Keycap geometry and lighting live in core `KeycapProfile`: back wall 8%, front wall 18%, side
  walls 12%, a 2 GUI px Cherry radius, a shallow concave dish on the top surface, planar wall
  facets lit by the UI's upper-left light, 1 GUI px press travel and a contact shadow that
  tightens on press. Legends and CPS text centre on the top surface's visible ink.
- The existing **Theme Corner Radius** switch gives keycaps the theme's control radius
  (`radiusSmall`): SQUARE themes get faceted square caps, ROUND themes rounded caps. (The first
  version used the window radius, which turned 20 px keys into circles.)
- `KeycapRenderer` bakes each face once into its own bounded pool (48 entries / 4 MiB), separate
  from the screen templates that are released during gameplay; each key is then one blit.
- Parity probe: `config <field> <json>` and `hold <key> on|off` drive these checks.

## Legend centring (owner correction)

Both styles place legends through one routine: the visible ink (glyph left/right bounds and the
active font's cap/x-height) is centred on the label area (Flat: the face; Keycap: the top
surface) with the origin snapped to the device grid. Each row shares one size from its widest
legend (3 px side clearance on Flat, 1.5 px on a keycap top), rounded to whole device pixels per
font pixel when that costs at most 15%.

Measured at GUI scale 3 against the module's layout (A key anchored, then CELL 22 / GAP 3):
W/A/S/D and LMB/MMB/RMB legends are within 0.33 GUI px of their area centre horizontally and
0.17 vertically, in Flat and 3D Keycap, SQUARE and ROUND. Row sizes are uniform (movement
6.7 px cap height; mouse 4.3 px Flat, 4.0 px Keycap). "Space" reads about 1 px low in this
measurement only because its descender is counted.

## In-client acceptance (parent)

Minecraft 26.3, OpenGL, GUI scale 3, disposable copy of the owner's config and world. Captured
Flat and 3D Keycap, keys up and held (W, left mouse), the spacebar and mouse row, Theme
Corner Radius on with SQUARE and ROUND: [sheet](evidence/keystrokes/key-styles.png).

Eight-second in-world samples with no screen open (`benchmark`), owner's full HUD config:

| Style | Aurora CPU mean | Aurora CPU p95 | Fills per frame |
|---|---:|---:|---:|
| Translucent (since retired) | 0.2017 ms | 0.444 ms | 0 |
| 3D Keycap | 0.2017 ms | 0.517 ms | 0 |

The keycap style adds no measurable mean cost. Both p95 values exceed the 0.40 ms release budget
because this is the owner's full HUD configuration, not the factory-default overhead check
(`tools/overhead_check.py`), which was not run. Not covered: 26.1.2 and 26.2 clients, Vulkan,
fractional HUD scales.
