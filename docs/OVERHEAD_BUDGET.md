# Aurora overhead budget

Set 2026-11-11 under [ADR 0005](adr/0005-performance-scope-and-overhead-budget.md). A release that exceeds this budget on the development machine does not ship.

## The budget

Measured in a world with no screen open, with factory default settings.

| Measure | Budget | Baseline (factory defaults) |
|---|---|---|
| Aurora CPU per frame, mean | 0.25 ms | 0.15 to 0.18 ms |
| Aurora CPU per frame, p95 | 0.40 ms | 0.18 to 0.24 ms |
| Glass captures per frame | 0 | 0 |
| Aurora UI fills per frame | 100 | 0 |

The budget is about 1.5 times the baseline. At 120 fps, 0.25 ms is 3% of a frame; at 240 fps it is 6%. The values live in `diagnostics/OverheadBudget`, and every diagnostics report says whether its run was within the budget.

## What is measured

**Aurora CPU per frame** is the time the render thread spends inside Aurora's own entry points: its HUD, world-render and client-tick callbacks, each wrapped by `diagnostics/Timed` and added up by `diagnostics/AuroraCost`. Time spent waiting does not count, so frame pacing and Reflex waits are excluded. The benchmark records the total once per frame.

Not counted:

- **Mixin hooks inside vanilla methods.** A timer per hook would cost more than most hooks do.
- **Better Hitreg.** Its classes are kept verbatim, and it ships off.

A profiler run on 2026-11-11 measured how much this leaves out. JFR sampled the render thread every 1 ms in a 47-second boot with factory defaults. Leaving out startup code, the timers covered about 79% of Aurora's per-frame work, so the true cost is about 0.19 ms per frame. The work the timers count:

- **Hitbox renderer:** 57%. Hitboxes are on by default.
- **HUD renderer:** 35%.
- **Ticks:** the rest.

The untimed remainder is mostly the mouse-delta, font-width, entity-smoothing and font-decoration hooks.

**Glass captures** are glass panels claimed from the renderer's output pool. **UI fills** are `RenderUtil` fills; MC 1.21.11's GUI recording makes their cost grow faster than their count (see section 10 of [the old guide](history/AGENTS-2026-11-11.md)).

## Baseline runs

Development machine: AMD Radeon RX 9070 XT, OpenGL, Windows 11, 28 CPU threads, Java 25. Window 854x480, GUI scale 2. Game settings: fps limit 120, vsync on, render distance 32. Each boot ran six 5-second benchmarks back to back in the saved singleplayer world.

| Boot | Runs 3 to 6, CPU mean | Runs 3 to 6, CPU p95 | Runs 1 and 2, CPU mean |
|---|---|---|---|
| Factory defaults | 0.148 to 0.179 ms | 0.176 to 0.236 ms | 0.309, 0.186 ms |
| Factory defaults, under JFR | 0.145 to 0.18 ms | up to 0.21 ms | higher while loading |
| The owner's dev config, more features on | 0.215 to 0.242 ms | 0.331 to 0.389 ms | 0.479, 0.295 ms |

Runs 1 and 2 are warm-up: chunks still load and the JIT has not compiled the hot paths yet. Their figures are recorded here, but the release check does not judge them.

Every run had no fills and no glass captures. The owner's config stays under the budget too, but the budget is defined for factory defaults.

## Release check

1. Back up `run/config/aurora.json`, `run/config/profiles/` and `run/options.txt`, then remove the first two so the boot starts from factory defaults.
2. Boot into the saved world:

   ```
   JAVA_TOOL_OPTIONS="-Daurora.diagnostics.log=true -Daurora.diagnostics.runs=6" \
     ./gradlew runClient --args='--quickPlaySingleplayer "New World"'
   ```

   Leave the game alone, with no screen open, until `[diagnostics] run 6 of 6` is in the log.
3. `python tools/overhead_check.py run/logs/latest.log`. It drops runs 1 and 2 and exits non-zero if any later run is over budget or was not measured in a world.
4. Restore the backup.

## Limits

- This machine cannot stand in for low-end hardware. Testers' reports show the same figures, and those decide how the defaults are set for weak machines (ADR 0005), not this release check.
- Only singleplayer was measured. On a busy server, more entities mean more work for the hitbox and entity-health paths.

## Minecraft 26.3 validation

The unchanged gate passes for the native Vulkan baseline and performance stack, and for OpenGL Sodium + Iris. The unprofiled OpenGL baseline and performance stack still exceed it after two warm-ups. This blocks publication of the 26.3 port. A separate JFR diagnostic boot had lower steady-state costs, but does not override the failing gate. See [MOD_MATRIX.md](MOD_MATRIX.md) for the measured ranges and test conditions. Neither the budget nor the warm-up rule was relaxed.
