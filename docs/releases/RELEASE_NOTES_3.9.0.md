# Aurora Client 3.9.0

One JAR per Minecraft version: **26.1.2**, **26.2**, and **26.3**, built from the same
source commit. Install the one matching your Minecraft version with Fabric Loader
and Fabric API. Java 25 is required.

## New

- **Album art on the Now Playing HUD:** The track's cover art now appears beside the
  title and artist, as your media player publishes it (Spotify, browsers and most
  Windows players do). While paused, the cover dims and shows a pause symbol. A track
  with no cover gets a quiet placeholder, so the HUD keeps its shape. Turn it off with
  **Show Album Art**.
- **Potion HUD wraps:** The Potion HUD now grows from the screen edge toward the
  centre. After **Effects per Line** effects (new setting, default 6) it starts a new
  row when Condensed, or a new column otherwise, so a long list never runs off the
  screen.
- **Combo keybinds don't trigger their modifier:** When a modifier is part of a combo
  (Ctrl in Ctrl + Right Arrow), pressing it no longer fires what is bound to that
  modifier on its own, such as Sprint on Ctrl. The modifier's own binding fires when
  you release it without completing a combo. Hold bindings, such as hold-to-sneak,
  still take a modifier held for 200 ms. Toggles fire only on release, so reaching
  for a combo can't flip them. Minecraft's own shortcuts (F2, drop, inventory) also
  respect combos now. With no combos bound, keys behave exactly as before.

## Fixes & Changes

- **Media Controls and Now Playing work on Windows:** Both were broken on every
  Windows install. The background helper could not find the media player under
  Windows PowerShell 5.1, and its media-key code never compiled. It now finds the
  player and sends play/pause, next and previous straight to it, which works
  whichever window has focus. The media keys remain as a fallback. The helper can no
  longer stall the game, and it logs problems.
- **Per-sound volumes apply to every sound:** Sound Volume Mixer settings previously
  affected only looping sounds. One-shot sounds, such as explosions, anvils, mob
  sounds and XP orbs, now play at their set volume, and a muted sound no longer plays
  at all.
- **Health HUD shows one number:** Absorption now adds to the same number (28.00
  after a golden apple) instead of showing as a separate gold value.
- **Armor HUD slots stay square:** A long durability number is drawn smaller instead
  of stretching the strip. The HUD now grows away from the screen centre, so beside
  the hotbar extra pieces extend outward. A full armour set stays where you placed it.
- **Cleaner SDF text:** Fixed faint specks that could appear under text in Aurora's
  screens and HUD.
- **Pack Tweaks:**
  - The particle slider is now **Totem Particle Size %**.
  - The redundant **Hide 1st Person Fire (Lava Clarity)** switch is removed. If it was
    hiding the fire for you, **Hide Fire Overlay** is turned on instead, so nothing
    changes. Saved profiles carrying the old field load without warnings.
- **No crash in Item Scale before joining a world:** Opening **Item Scale**, the HUD
  Editor or Shield Status from the title screen no longer crashes with "Components
  not bound yet" (PR #114).

## Verification

- Full 14-row parity GPU acceptance across Minecraft **26.1.2**, **26.2**, and
  **26.3** (OpenGL, VulkanMod, and native Vulkan), 56 judged runs:
  **0.034–0.114 ms** mean and **<= 0.263 ms** p95 Aurora CPU per frame (budget:
  0.25 ms mean / 0.40 ms p95), **0.0** UI fills, **0.0** glass captures, all native
  mixins healthy, and zero Aurora warnings or errors across every row
  ([evidence](evidence/3.9.0-parity.json)).
- Per-sound volumes owner-tested in game. The Now Playing HUD read a live Spotify
  session, including its album art, in a 26.3 dev boot.
