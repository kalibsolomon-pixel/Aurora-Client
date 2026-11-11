# Aurora Client 3.2.0

Aurora 3.2 is a full interface simplification: one calm visual language, one compact
window for every Aurora screen, smaller text, and sharper color controls — with no
changes to gameplay behavior or your saved settings.

## A calmer, simpler interface

- Selection now means one thing: a soft wash with a single accent edge. Glow halos
  around selected and primary elements are gone.
- Dropdowns, keybind chips and action buttons share one consistent control face;
  the old mix of outlined and near-invisible treatments is gone.
- Windows blur once. Controls inside windows no longer blur individually — manager
  screens drop from up to ~20 blur captures per frame to one, so inventory-weighted
  GUI rendering work falls measurably (no overall FPS claim is made).
- Manager rows lose their repeated outlines; Delete quiets to a red label and only
  turns solid red on hover or press.
- Section captions and setting choices read as normal text ("Side", "Legacy 1.8")
  instead of ALL-CAPS raw identifiers.
- Keyboard focus is now visible even on already-selected elements.

## One compact window

- Every Aurora screen — main grid, all feature details, both managers, the waypoint
  editor, color picker, mapping data and the pack browser — shares one window size
  that shrinks to fit smaller screens.
- UI text is smaller (8 px), rows and controls tighter; far more settings fit without
  scrolling.
- Long feature pages organize into collapsible section categories; they open with
  only the first expanded, and collapsed sections pack tightly.
- The main grid shows exactly a 3×3 set of feature cards.

## Sharper color controls

- Every color swatch is a small square; color pickers render per device pixel, so
  gradients blend smoothly instead of showing blocky cells.
- Theme Accent and Background Color are single swatches that open the picker; the
  preset grids are gone (the factory default remains OnePlus Red).
- Background Opacity is linear and authoritative: maximum is a solid window, minimum
  is clear glass with no frost.

## Corrections and removals

- The shield status overlay is a true translucent second pass: it follows its color
  picker at real opacity, fully covers the shield texture at maximum, and submits
  nothing at zero.
- Dropdown menus on the Settings tab now open and activate when clicked.
- Info HUD options are grouped into collapsible categories (Performance, Position,
  World, Playtime).
- Navigation rows wear a compact arrow icon; genuine actions keep meaningful labels.
- Pack-browser cards derive from the window size: no overlap, no cards outside the
  frame.
- Removed by owner design: the crosshair preview rows, Block Overlay's "See-Through
  Outline" and Hitbox's "See-Through Wireframe" options (both are now always
  see-through), the Animations "Swing Style" selector (a saved choice still applies),
  and the Text & Fonts master toggle (the two dropdowns carry everything).
- "Attack Indicator" is renamed "Vanilla Attack Indicator".

## Compatibility and your data

- Client-side Fabric mod for **Minecraft 1.21.11**. Requires **Java 21 or newer**,
  **Fabric Loader 0.16.0 or newer**, and **Fabric API**. Built with Loader 0.19.2
  and Fabric API 0.141.4+1.21.11. Install `aurora-3.2.0.jar` in your instance's
  `mods` folder.
- Existing configurations, profiles, keybinds, waypoints and map data are preserved.
  The two removed see-through options simply disappear; every other setting carries
  over unchanged, and new installations still receive the reviewed 3.1.0 defaults.
