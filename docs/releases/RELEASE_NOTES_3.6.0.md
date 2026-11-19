# Aurora Client 3.6.0

One JAR per Minecraft version: 26.1.2, 26.2 and 26.3, built from the same
source commit. Install the one matching your Minecraft version with Fabric
Loader and Fabric API. Java 25 is required.

## New

- **Animations** gains hand sway, compact eating and drinking, a classic
  (1.7/1.8) block pose with block-hit swing, options to skip the hand dip after
  using an item or during the attack cooldown, re-equip on slot change only,
  1.8 backwards walking for every player and a 1.8 sneak pose for your own
  model. Classic Damage Tilt now rolls one way, as 1.8 did. Everything is
  visual and every new option ships at vanilla. The detail window is regrouped
  into three categories: Hands & Held Items, Using & Switching Items, and
  Camera & Body.
- **Potion HUD**: the Vanilla background now uses vanilla's own effect
  container, with two layouts. Standard lists names and timers beside the
  icons; Condensed is a vanilla-style row with the time in each container's
  corner (4m, then 59s).
- **HUD colours**: the Health HUD gains background and text colour options and
  the Ping HUD a background; the Scoreboard HUD gains a text colour.
- **Health Indicators** can show vanilla's heart bar above entities, and your
  own health is a separate HUD module.
- **Keystrokes** has two key styles: flat Lunar-style keys and 3D Cherry
  keycaps.
- **Text fields** have undo and redo, a visible selection, and say why a typed
  character was refused. Any setting can be reset to its default on its own.
  Destructive actions ask you to press again to confirm.
- **In-Game Account Switcher**: its multiplayer-screen button is drawn in
  Aurora's style when that mod is installed.

## Changed

- **Smooth rendering is the only path.** Aurora's custom text, hitboxes and
  block outlines are always drawn with distance fields (sharp at any scale,
  anti-aliased lines with round corners); the Smooth Scaling and Smooth Lines
  options are gone. Plain rendering remains only as an automatic fallback.
- **New look**: lit 3D edges on windows, curvature-lit controls, one bevel
  strength (the Window Bevel setting is retired), a bevelled title screen and
  HUD editor, council-reviewed polish across the UI, and raised 3D buttons on
  the Multiplayer and Select World screens. Shared tooltips, dropdowns anchored
  to their rows, and a Reduced Motion option.
- **Logo**: the Aurora ribbon has rounded tips so it survives small sizes, and
  fills more of the app icon.
- **Text scope**: Aurora UI Only styles Aurora windows only; the HUD follows the
  custom font only under All Text.
- The Custom Title setting also controls the Multiplayer and Select World
  button and text-field styling.

## Fixes

- Hitboxes are drawn only for entities in view, as vanilla culls entity
  rendering, which keeps Aurora within its overhead budget with smooth lines.
- The server list's numeric ping no longer sits under the scrollbar.
- Better Hitreg no longer crashes on mobs that override their hurt sound.
- Window text measured during setup uses the same font as when it is drawn.
- Ellipsized text is cut at whole characters (grapheme clusters).
- Three mixin hooks that never applied (Health Indicators, crosshair blending)
  are fixed.
