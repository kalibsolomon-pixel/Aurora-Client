# Keystrokes key styles

2026-11-17. Owner request: "options for 3d shaded keys (shaped like cherry style keycaps) along
with the option to use the UI corner radius for 3d shaded keys, and flat color/translucent keys."

## What changed

- `keystrokesKeyStyle`: **Translucent** (default; runs the unchanged legacy renderer, so existing
  installs look the same and need no migrator), **Flat** (opaque key colour, no shell or rim) and
  **3D Keycap** (Cherry profile).
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

## In-client acceptance (parent)

Minecraft 26.3, OpenGL, GUI scale 3, disposable copy of the owner's config and world. Captured
Translucent and 3D Keycap, keys up and held (W, left mouse), the spacebar and mouse row, Theme
Corner Radius on with SQUARE and ROUND: [sheet](evidence/keystrokes/key-styles.png).

Eight-second in-world samples with no screen open (`benchmark`), owner's full HUD config:

| Style | Aurora CPU mean | Aurora CPU p95 | Fills per frame |
|---|---:|---:|---:|
| Translucent | 0.2017 ms | 0.444 ms | 0 |
| 3D Keycap | 0.2017 ms | 0.517 ms | 0 |

The keycap style adds no measurable mean cost. Both p95 values exceed the 0.40 ms release budget
because this is the owner's full HUD configuration, not the factory-default overhead check
(`tools/overhead_check.py`), which was not run. Not covered: 26.1.2 and 26.2 clients, Vulkan,
fractional HUD scales.
