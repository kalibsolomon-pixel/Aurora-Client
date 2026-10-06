# Aurora Client 3.0.0

Aurora 3.0 brings a unified visual identity inspired by Aurora Launcher, a more compact interface, and updated HUD customization.

## Interface and materials

- Unified dark surfaces, accent highlights, glass backdrops and floating menus across Aurora screens.
- Compact module cards and feature settings, with clearer control spacing and whole-word text wrapping.
- Aurora branding above the streamlined main-menu buttons.
- Refined hover, focus, scrolling and interaction animations, with shared keyboard navigation and feedback.
- Parent-owned backdrop capture in the module browser and feature details reduces repeated glass work.

## HUD and customization

- Independent text colors for Info, CPS, Armor, Reach, Potion, Ping, Totem Pops, Keystrokes and Toggle Sprint/Sneak.
- Resource-pack-aware vanilla slot backgrounds for HUD panels, armor and potion effects.
- Updated Keystrokes presentation, including mouse-button CPS and a labeled space key.
- Improved crosshair presets and previews, contextual custom canvases, and an optional attack cooldown indicator with its own size control.
- Optional Alternative Glint for moving shadows and highlights, adjustable intensity, and corrected item glint draw ordering.
- Optional hurt tinting for armor trims when armor tinting is enabled.

## Organization and reliability

- Hotbar Bounce now lives under Animations; Miscellaneous is now Display Settings.
- Stats Overlay and hunger alerts have been removed. Better Hitreg retains its lifetime fight counters.
- Effect-expiry alerts use vanilla effect icons; sound previews work from menus.
- Resource-pack browsing opens directly, with bounded WebP, PNG and JPEG icon decoding and retry handling.
- Automatic measured latency pacing replaces the Reflex settings tile, with bounded waits and session fail-off handling.

## Compatibility

Client-side Fabric mod for Minecraft **1.21.11**. Requires **Java 21 or newer**, **Fabric Loader 0.16.0 or newer**, and **Fabric API**. Built and smoke-tested with Loader 0.19.2 and Fabric API 0.141.4+1.21.11.

Install `aurora-3.0.0.jar` in your Fabric instance's `mods` folder. Aurora Launcher availability is controlled separately through its production release manifest.
