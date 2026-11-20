# Performance pass against the owner's reference instance (2026-11-20)

Branch `perf/client-throughput`. The owner asked for performance work with no limit on area (chunks, world generation, sky) and compatibility with the mods in their Aurora Launcher instances. Every item below was measured before it shipped or was dropped ([ADR 0009](../adr/0009-client-optimizations-aurora-owns.md)).

## Where the time goes

Profile setup: a disposable copy of the owner's 26.2 instance (its `config/`, `options.txt` and mods; Spark, Simple Voice Chat, CrashAssistant and the installed Aurora jar left out) on a fresh 26.2 world. GUI scale 4, render distance 16, uncapped, OpenGL on the RX 9070 XT. JFR sampled the render thread every 1 ms.

- About 2,000 fps, a 0.44 ms median frame. Aurora's timers report 0.036 ms per frame.
- Aurora's code was on the stack in about 20% of render-thread samples, including untimed mixin hooks:
  - **Hitboxes:** 7.4%.
  - **HUD:** 6.1%.
  - **Frame-start glass hook:** 4.5%. 3% of that was one font re-raster when the window changed monitors (GUI scale 3 to 4); 1.3% was the per-frame font scale check below.
- Vanilla areas the instance's mods already own:
  - **Entity visibility (13.7%):** Sodium, BadOptimizations, EntityCulling, EMF/ETF and MoreCulling patch it.
  - **Terrain:** Sodium.
  - **World generation:** C2ME on the 26.3 profile; on servers it is not client work.
  - **Sky (2.3%):** draw submission only. Iris and Sodium Extra patch `SkyRenderer` on the 26.3 profile.

  An Aurora patch in these areas would stand down for those mods and do nothing in the owner's setup.
- **GUI render-state bookkeeping:** layer search, sort and traversal, about 10%. No mod in either instance patches it, so it was the one unowned vanilla candidate (below).

## Shipped

| Change | Before | After |
|---|---|---|
| World map region save (real 512×512 region) | 62 ms | 28 ms |
| World map region load | 32 ms | 9 ms |
| Capture queue, 4,096-chunk burst | 0.60 ms, 1.44 MB allocated | 0.21 ms, 0.47 MB |
| North-up minimap repaint, 512 px | 0.31 ms | 0.05 ms (identical pixels) |
| Font scale check per frame (`AuroraFontQuality.anyStale`) | 1.3% of render-thread samples in world, 1.0% in Settings | not sampled; the whole hook is 0.26% |

- **Region files:** the primitive writes and reads are buffered in front of gzip. The file format is unchanged.
- **Capture queue:** one primitive linked set, with the same order, dedup and capacity rules.
- **Font scale check:** re-scans the providers only after one registers, instead of iterating a synchronized weak map of 170 entries every frame. A real scale change still re-rasterizes: verified by moving the window from GUI scale 3 to 4.

## Measured and dropped

**GUI Layering:** a `GuiRenderState` layer list that indexed its elements' bounds as flat ints plus a union box, and a comparator with the same element order as `GuiRenderer`'s, without boxing. Both are exact; the tests compared them with vanilla's scan and stable sort.

The measurements alternated on and off inside one boot:

| Scene | On | Off |
|---|---|---|
| Aurora Settings, screen extraction (mean) | 0.0455, 0.0458 ms | 0.0500, 0.0499, 0.0474 ms |
| Aurora Settings, whole frame (mean interval) | 0.723–0.736 ms | 0.717–0.738 ms |
| In world, HUD, median frame | 0.504–0.531 ms | 0.472–0.547 ms |

The first "on" run is left out of the extraction row; it was still warming up.

A model of the same search on an item panel (slot, item and count per slot):

| Slots | Vanilla | Indexed |
|---|---|---|
| 45 | 0.0058 ms | 0.0056 ms |
| 200 | 0.047 ms | 0.041 ms |
| 500 | 0.300 ms | 0.212 ms |

On a full panel the union box cannot prune anything, so the search stays linear. The gain is a few microseconds on real screens and is not visible in frame time. That does not pay for three mixins in GUI code other mods also hook, so it was dropped.

## Not changed; for the owner

- **Hitboxes beyond render distance.** The hitbox renderer draws every loaded entity inside the view frustum. Vanilla stops drawing entities past their render distance: about 64 blocks for a player at the default Entity Distance. On a server that tracks players farther than that, Aurora still draws boxes for players whose models are not drawn. Limiting hitboxes to rendered entities would cut hitbox cost and close an information gap. It is a visible change, so it waits for a decision.
- **GPU-side items.** The deferred list in the [world-render report](2026-11-12-perf-world-render.md) still needs GPU-bound evidence. This machine runs the owner's instance at about 2,000 fps.
