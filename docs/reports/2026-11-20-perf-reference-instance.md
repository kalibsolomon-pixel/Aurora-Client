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

## Second pass (owner: "any gain is a good one")

The owner approved the hitbox change and asked for every UI gain. GUI Layering, first measured and dropped (below), came back with the Left-to-Right Text change, whose gain was larger. Measurements alternate a gate on and off inside one boot unless noted.

**Hitboxes at render distance.** Each entity now passes vanilla's `Entity.shouldRender(camera)` before the frustum test, as vanilla does before drawing it, so a box no longer appears for a model the game does not draw. Spectator over a coast (separate boots, runs 3 to 6):

| | Before | After |
|---|---|---|
| Aurora CPU per frame, mean | 0.051–0.054 ms | 0.033–0.034 ms |
| `HitboxRenderer.render`, render-thread samples | 20.4% | 11.5% |
| Average fps | 2,055–2,224 | 2,154–2,338 |

**GUI Layering and Left-to-Right Text.** Both are exact.
- **GUI Layering (`perfGuiLayering`).** Layer lists index their elements' bounds as flat ints with a union box. The element sort uses a written-out comparator with the same keys, without boxing.
- **Left-to-Right Text (`perfLeftToRightText`).** In a left-to-right language, text with no right-to-left letters, Arabic digits or direction controls skips ICU's bidirectional pass, and its pieces are built directly.
- **Tests.** Random layers, every comparator pair, the stable sort, and 5,000 random styled strings (visit index, style and code point) all match vanilla.

| | Off | On |
|---|---|---|
| `GuiRenderState.sortElements` | 1.77–1.79% | 0.67–0.90% |
| `FormattedBidiReorder.reorder` | 0.86–1.57% | 0.31–0.45% |
| Aurora Settings screen extraction, mean | 0.047–0.052 ms | 0.038–0.039 ms |

Render-thread allocation on the HUD fell from 180 to 163 MB/s at about 2,600 fps.

**HUD and screen trims (exact).**
- **Scoreboard.** With no sidebar it returns before sizing its ten-line editor sample, which it did twice a frame (1.1% of render-thread samples).
- **Health HUD.** Measures its readout once per frame.
- **`MixinHealth.failed`.** Skips the map lookup while nothing has failed.
- **SDF text health.** The answer is cached until mixin health changes. Together with the line above, 0.9% on the feature detail screen.
- **Custom crosshairs.** Rows merge into rectangles: the owner's crosshair is 3 fills instead of 9. A 160 px crop around the crosshair is pixel-identical.

Fresh in-world profile of the owner's config after the second pass, compared with the first A/B window:

| | Before | After |
|---|---|---|
| Aurora HUD renderer | 7.7% | 2.8% |
| `Font.width` | 2.1% | 0.3% |
| `Gui.extractRenderState` | 10.7% | 7.4% |
| `GuiRenderer.render` | 7.5% | 4.5% |

**Not changed.**
- **Blaze3D's OpenGL dispatch.** `trySetup`, `bindVertexArray` and `createRenderPass` cost 6–8% of render-thread samples on the HUD, on uniform and sampler hash maps per draw.
- **Particle Core.** It allocates about 22 MB/s boxing doubles at 2,600 fps.
- **The owner's launcher settings.** The instance runs with a 2 GB heap and default G1; its JVM crashed with a native out-of-memory error on 2026-11-13. The launcher's own JVM policy is evidence-gated (`aurora-launcher/docs/performance-p1`), so this report only records it.

## Measured and dropped (first pass)

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

- **Hitboxes beyond render distance.** Resolved in the second pass (above): the owner approved limiting hitboxes to entities the game draws.
- **GPU-side items.** The deferred list in the [world-render report](2026-11-12-perf-world-render.md) still needs GPU-bound evidence. This machine runs the owner's instance at about 2,000 fps.
