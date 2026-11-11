# Tested mod matrix

ADR 0004 promises a tested set of popular mods. This file records what was booted with Aurora and what happened. Rerun it for every Minecraft version Aurora is ported to, before release.

## How to run it

1. Back up `run/config/aurora.json`, `run/config/profiles/` and `run/options.txt`.
2. `python -I tools/matrix/fetch.py tools/matrix/pins-<mc>.json <dir outside the repo>`. Downloads the pinned Fabric builds from Modrinth and checks each SHA-512 against the hash Modrinth publishes.
3. `python -I tools/matrix/run.py tools/matrix/pins-<mc>.json <that dir> <out dir>`. Boots once per set into the saved world with three diagnostics reports, then closes normally. `run/mods` is emptied afterwards.
4. `python -I tools/matrix/summarize.py <out dir>`, then restore the backup.

A set passes when every Aurora mixin applies, no feature is reported as not working, no Aurora warning or error is logged, and the game closes normally. Runs 2 and 3 are the warm figures; run 1 is warm-up.

## Minecraft 1.21.11, 2026-11-11

Dev client, Aurora 3.2.0 with the core module, Fabric Loader 0.19.2, Fabric API 0.141.4. Windows 11, AMD Radeon RX 9070 XT, the owner's dev config, 120 fps cap. Mods are loaded from `run/mods`, where Fabric Loader remaps them for the development environment; a production launcher was not used.

| Set | Mods | Renderer | Result | Aurora CPU per frame, runs 2 and 3 (mean) |
|---|---|---|---|---|
| A | Sodium 0.8.14, Lithium 0.21.4, ImmediatelyFast 1.14.3, EntityCulling 1.11.3, Mod Menu 17.0.1, Text Placeholder API 2.8.2 | OpenGL | Pass | 0.198, 0.172 ms |
| B | Sodium 0.8.14, Iris 1.10.8 (no shader pack) | OpenGL | Pass | 0.220, 0.186 ms |
| C | VulkanMod 0.6.8 | Vulkan | Pass | 0.200, 0.189 ms |

In every set all 72 Aurora mixins applied, every feature reported as working, and no Aurora warning or error was logged. Sodium logged its own `AMD_GAME_OPTIMIZATION_BROKEN` workaround and Iris a dev-only refmap warning; neither concerns Aurora. Glass captures per frame were 0 in-world in every set.

Pinned versions and file hashes: `tools/matrix/pins-1.21.11.json`; `fetch.py` refuses a file whose SHA-512 differs from Modrinth's.

Not covered: shader packs under Iris, multiplayer, a production launcher, and opening Aurora's screens with these mods (the reports measure in-world only).

## Minecraft 26.1.2, 2026-11-11 (port branch)

Dev client from `port/26.1`, Fabric Loader 0.20.1, Fabric API 0.155.3+26.1.2, a 26.1 copy of the owner's world and config; same machine and method as above. Pins: `tools/matrix/pins-26.1.2.json`.

| Set | Mods | Renderer | Result | Aurora CPU per frame, run 3 (mean) |
|---|---|---|---|---|
| A | Sodium 0.9.2, Lithium 0.24.7, ImmediatelyFast 1.15.3, EntityCulling 1.11.3, Mod Menu 18.0.2, Text Placeholder API 3.0.0 | OpenGL | Pass | 0.195 ms |
| B | Sodium 0.9.2, Iris 1.11.4 (no shader pack) | OpenGL | Pass | 0.195 ms |
| C | VulkanMod 0.6.8 | Vulkan | Pass | 0.212 ms |

All 73 mixins applied in every set, every feature reported as working, no Aurora warning or error was logged, and every boot closed normally. Run 2 of each set was slightly over budget (0.243 to 0.264 ms mean): with these mods the warm-up takes a little longer than on 1.21.11. Not checked: Aurora's glass under VulkanMod by eye (Minecraft's screenshot grab returns a black image under VulkanMod in the dev client; the glass ran one panel per frame without errors).

