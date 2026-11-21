# GUI Flat Faces (2026-11-20)

A follow-up to [GUI Glint Baking](2026-11-20-gui-glint-baking.md). Whenever the GUI item atlas draws a flat (generated) item into a slot — on cold fills, inventory opens, or moving glints — vanilla submits every extruded pixel-edge side face along with the front and back faces. Seen head-on under the atlas's orthographic projection, those side faces project to zero area and account for 94–97% of the quads submitted inside `GuiItemAtlas.drawToSlot`.

## What changed

| Optimization | Gate | UI | Mixins |
|---|---|---|---|
| GUI Flat Faces | `perfGuiFlatFaces` | config only | `BakedQuadEdgeOnMixin` (shared), `GuiItemAtlasFlatFacesMixin` (shared), `ItemFeatureRendererFlatFacesMixin` (26.1, 26.2, 26.3) |

- **Exact zero-area criterion (no epsilon).** A quad has four corners `(p0, p1, p2, p3)` in model space. Pair them either as `(0-1, 2-3)` or `(0-3, 1-2)`; under either diagonal split, both triangles contain a paired edge. For a given pose `M`, `GuiFlatFaces.collapsedAxes(M)` computes a 3-bit mask of model axes whose screen-X and screen-Y weights are *exactly* `0.0F` (`m_k0 == 0.0F && m_k1 == 0.0F`, finite matrix). For a quad, `GuiFlatFaces.zeroAreaUnder(p0, p1, p2, p3)` precomputes an 8-bit lookup table (lazily cached in an `int` field on `BakedQuad` via `BakedQuadEdgeOnMixin`, zero per-frame allocation) whose bit `c` is set iff every axis on which one pairing differs is in `c`.
  - When bit `collapsedAxes(pose)` is set, each paired corner differs only along axes multiplied by exact `0.0F` in screen X and Y, so CPU transformation produces identical `(vx, vy)`.
  - `GuiItemAtlasFlatFacesMixin` activates skipping only inside `GuiItemAtlas.drawToSlot` (which binds an orthographic projection) and only when `GuiFlatFaces.keepsDepthOut(RenderSystem.getModelViewStack())` holds (`m20 == 0.0F && m21 == 0.0F && m23 == 0.0F`, finite), so GPU clip `(x, y, w)` is independent of depth too. Both triangles therefore have two identical screen vertices, zero signed area, and produce zero fragments under OpenGL and Vulkan rasterization rules.
  - Because the check is exact `0.0F` rather than a normal-dot-view angle threshold, any tilt or non-axis display rotation — down to `Float.MIN_VALUE` — keeps every quad, while exact view-axis rotations (`[0, 0, 45]`) and exact quarter/half turns (`[0, 90, 0]`, `[0, 180, 0]`) skip only the faces that collapse exactly to zero area. World, hand, and item-frame rendering never run inside `GuiItemAtlas.drawToSlot` and stay unchanged.
- **Per-version hooks.** `ItemFeatureRendererFlatFacesMixin` wraps `putBakedQuad` in `renderItem` (26.1), `prepareMainSubmit` and `prepareFoilSubmit` (26.2), and `putBakedQuad` + `putBakedQuadWithGlint` in `prepareMainSubmit` (26.3). The vertex builder is still fetched for skipped quads, preserving draw order.
- **Mod ownership and compatibility.** Inspected every mod in the owner's 26.2 and 26.3 loadouts; none owns GUI atlas edge-on quad skipping, so no `.yieldsTo(...)` stand-down is needed:
  - **ImmediatelyFast** (`1.16.5+26.2`, `1.17.1+26.3`): `AnimatedItemAtlas extends GuiItemAtlas` and delegates through `GuiItemAtlas.drawToSlot`, so GUI Flat Faces applies cleanly alongside `batch_animated_item_updates`.
  - **Sodium** (`0.9.2+mc26.2`, `0.9.2+mc26.3`): `ItemModelGeneratorMixin` (`ImprovedItemModelBuilder`) merges flat-item side faces at bake time but still emits ordinary `BakedQuad` side faces (~96.5% of atlas quads remain edge-on); `BufferBuilderMixin.putBakedQuad` runs inside `VertexConsumer.putBakedQuad`, below our `@WrapOperation`. `ExtendedItemFeatureRenderer` handles only FRAPI `ExtendedItemSubmit` meshes.
  - **EMF / ETF** (`3.3.12`, `7.2.7`): do not touch `GuiItemAtlas`, `ItemFeatureRenderer`, or `BakedQuad`.
  - **MoreCulling** (`1.8.2`, `1.9.0`): `ItemStackRenderState_faceCullingMixin` only culls item-frame quads, not GUI items; `BakedQuad_cacheMixin` does not conflict.
  - **Iris** (`1.11.7+mc26.3`) and **Continuity** (`3.0.1+26.3`): Iris only injects context hooks at submit boundaries; Continuity emits ordinary `BakedQuad`s.

## Evidence

### Pixel-identical readback

Every capture ran in a disposable fixture on the secondary portrait monitor (`1300×1100`, GUI scale 4, enclosed static glowstone room, Aurora HUD widgets hidden). The hotbar, armor slots, offhand, and survival inventory held 40+ items: flat tools/food/ingots (enchanted and unenchanted), block items (opaque, translucent, animated magma), special/unusual GUI transforms (trident, spyglass, shield, banner, player/creeper heads, enchanted compass, clock, enchanted book, chest, torch, potion, totem), plus a test resource pack applying non-trivial `display.gui` rotations (`stick` `[0,0,45]`, `bread` `[0,90,0]`, `cooked_beef` `[0,180,0]`, `emerald` `[30,0,0]`).

Each configuration checked four conditions (`masked` Glint Color, `vanilla` glint, `strength0`, and `zero` — drawing *only* the skipped quads vs drawing *no* item quads) across both HUD and inventory (`on`, `off`, `on2` with atlas rebuild between captures):

| Configuration | Mod set | Non-animated item rects differing (`on/off`, `on/on2`, `only/none`) | Mixin health |
|---|---|---|---|
| **26.2 OpenGL** | Owner's 26.2 mods (26 jars, no Spark/SVC/CrashAssistant/Aurora) | **0 px** across all 24 pairs (0 px whole-frame outside animated magma/player preview) | All 112 applied or stood down (7 stood down) |
| **26.3 OpenGL** | Owner's 26.3 mods (34 jars) | **0 px** across all 24 pairs (0 px whole-frame outside animated magma/player preview) | All 113 applied or stood down (9 stood down) |
| **26.3 Vulkan** (`--graphicsBackend VULKAN`) | Owner's 26.3 mods (34 jars) | **0 px** across all 24 pairs (0 px whole-frame outside animated magma/player preview) | All 113 applied or stood down (9 stood down) |
| **26.1.2 OpenGL** | Vanilla (no mods) | **0 opaque item px** across all 24 pairs (0 px on all inventory & `vanilla`/`strength0`/`zero` HUD rects; `masked` HUD differed only on translucent slot background from vanilla block-light flicker, with `on/on2` differing more than `on/off`) | All 113 applied |

### Uncontested ABBA measurements

In-boot ABBAABBA (`off, on, on, off, off, on, on, off`) with GUI Glint Baking enabled, uncapped FPS, no other `KnotClient` running, direct `System.nanoTime()` timer around `GuiItemAtlas.drawToSlot`, 8 s `FrameBenchmark`, and 1 ms render-thread JFR windows per run:

| Line & scene | Quads left out | `drawToSlot` µs/draw (off → on) | `drawToSlot` ms/s (off → on) | JFR `drawToSlot` share (off → on) | JFR `putBakedQuad*` (off → on) | Avg FPS (off → on) | Median frame ms (off → on) |
|---|---|---|---|---|---|---|---|
| **26.2 OpenGL** (owner mods), moving glint HUD (`strength 1`, `speed 0.5`) | **96.5%** (545k / 565k/s) | **28.04 → 22.74** (-18.9%; 27.10–29.17 vs 20.87–24.29) | **137.2 → 112.2** (-24.9 ms/s) | **10.47% → 5.82%** (9.85–11.12% vs 4.73–6.77%) | 7.90% → 4.30% | **2,330 → 2,438 (+4.6%)** | **0.370 → 0.361** (-2.4%) |
| **26.2 OpenGL** (owner mods), moving glint Inventory | **94.1%** (868k / 923k/s) | **19.55 → 16.94** (-13.4%; 19.25–20.28 vs 16.60–17.36) | **247.8 → 217.0** (-30.8 ms/s) | **15.65% → 10.43%** (15.26–16.22% vs 9.81–10.79%) | 7.96% → 4.38% | **1,466 → 1,515 (+3.3%)** | **0.593 → 0.570** (-3.9%) |
| **26.2 OpenGL** (owner mods), owner config (`glintStrength 0`) | 0 draws/s | 0.00 → 0.00 | 0.0 → 0.0 | 0.00% → 0.00% | 3.99% → 3.83% | 2,808 → 2,832 (+0.9%, no regression) | 0.329 → 0.325 |
| **26.3 Vulkan** (owner mods), moving glint HUD (`strength 1`, `speed 0.5`) | **96.5%** (271k / 280k/s) | **14.13 → 11.40** (-19.3%; 13.92–14.25 vs 11.10–11.69) | **69.1 → 55.9** (-13.2 ms/s) | **7.93% → 5.04%** (7.26–9.01% vs 4.48–5.36%) | 5.31% → 2.76% | 1,554 → 1,561 (+0.5%, within noise) | 0.617 → 0.616 |
| **26.3 Vulkan** (owner mods), moving glint Inventory | **93.7%** (419k / 447k/s) | **12.29 → 10.01** (-18.6%; 12.25–12.33 vs 9.93–10.08) | **146.4 → 119.1** (-27.3 ms/s) | **14.10% → 8.20%** (12.87–14.97% vs 7.33–8.63%) | 8.18% → 3.57% | 1,120 → 1,105 (-1.3%, within noise) | 0.860 → 0.870 |
| **26.3 Vulkan** (owner mods), owner config (`glintStrength 0`) | 0 draws/s | 0.00 → 0.00 | 0.0 → 0.0 | 0.09% → 0.07% | 3.18% → 3.20% | 1,719 → 1,760 (+2.4%, no regression) | 0.551 → 0.536 |
| **26.1.2 OpenGL** (no mods), moving glint HUD (`strength 1`, `speed 0.5`) | **97.3%** (708k / 728k/s) | **16.77 → 9.12** (-45.6%; 16.60–16.86 vs 8.69–9.70) | **82.2 → 44.9** (-37.3 ms/s) | **11.07% → 4.78%** (10.20–12.40% vs 4.42–5.34%) | 10.68% → 6.13% | **2,491 → 2,554 (+2.5%)** | **0.353 → 0.346** (-1.8%) |

Notes on the numbers:
- On 26.2 and 26.1.2 `moving` HUD, the 10 s counter window slightly outlasted the ~9.2 s benchmark window on two `off` runs (`moving-1-off`, `moving-5-off`) and exited before flushing on 26.1.2 `moving-8-off`, so the 26.1.2 `off` µs/draw and ms/s means average the three logged `off` runs (`16.60, 16.84, 16.86`); that tail overlap makes the reported `off → on` timer delta slightly conservative while JFR and `FrameBenchmark` windows had zero overlap.
- 26.3 fuses the glint into one item pass, so it submits half as many quads per draw as 26.2 (~280k/s vs ~565k/s), yet still cuts `drawToSlot` CPU time by ~19% (13–27 ms/s) and JFR `drawToSlot` share by 2.9–5.9 percentage points.
- Unlike GUI Glint Baking, whose moving-glint saving disappears below 1,000 FPS, GUI Flat Faces cuts 13–46% of every `drawToSlot` call at any frame rate (including inventory opens, hotbar scrolling, and lower-FPS play with visible glint).

## Recommendation

**Ship (`perfGuiFlatFaces = true`).** It satisfies all four ADR 0009 gates: exact zero-area math verified pixel-identical on every line and backend, zero mod conflicts, config-gated and fail-soft, and a clean, non-overlapping 13–46% reduction in `GuiItemAtlas.drawToSlot` CPU time (13–37 ms/s saved; +2.5% to +4.6% FPS on OpenGL moving-glint scenes) with no regression at `glintStrength 0` or on 26.3 Vulkan.

## Tests

`GuiFlatFacesTest` covers:
- head-on flat item side faces skipped and front kept;
- block items keeping every face;
- exact view-axis rotations (`[0, 0, 45]`), quarter turns (`[0, 90, 0]`), and exact vs floating-point-imperfect half turns (`[0, 180, 0]` vs `rotateY(PI)`);
- arbitrarily small tilts (down to `Float.MIN_VALUE`) and diagonal rotations keeping every sliver face;
- `-0.0F` weights, zero scales, and non-finite poses/corners;
- 4,000 randomized axis-aligned box/transform checks asserting every skip has paired screen corners and zero area;
- `keepsDepthOut` model-view checks and `beginAtlasDraw`/`endAtlasDraw` scoping.
