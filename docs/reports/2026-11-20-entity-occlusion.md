# Entity and Block-Entity Occlusion Culling (2026-11-20)

Revisits the entity and block-entity occlusion item originally deferred in [ADR 0009](../adr/0009-client-optimizations-aurora-owns.md). Instead of an asynchronous raytracer or heuristic depth pyramid, Aurora uses a synchronous, allocation-free, mathematically conservative opaque-layer sweep on the render thread during level extraction.

## What changed

| Optimization | Gate | UI | Mixins | Stands down for |
|---|---|---|---|---|
| Entity & Block-Entity Occlusion | `perfEntityOcclusion` (default `true`, machine-scoped) | config only | `OcclusionFrameMixin` (native per line), `OcclusionBodyMixin`, `OcclusionChestMixin`, `OcclusionDirtyMixin` (shared) | `entityculling` |

### Why the test is conservative

- **Sufficient opaque-layer condition (`OcclusionSweep`).** For an axis-aligned eye cube `[eye ± 0.25]` (absorbing view bobbing and rounding) and an expanded render box `[box + motion + overhang/shadow]`, the sweep searches up to 16 integer separating planes per axis strictly between `eyeFar + 0.5` (well past the 0.05 near clip plane at any FOV/aspect) and `boxNear - 1e-3`. Every ray from any eye point to any box point crosses the plane inside the exact `[uMin, uMax] × [vMin, vMax]` footprint (grown by `1e-3` so boundary crossings test both cells). A box is hidden **only** if every cell in that footprint (at most 36 cells per layer) is a full opaque cube (`BlockState.isSolidRender() && getRenderShape() == RenderShape.MODEL`) in a built, faded-in, settled section. A miss or budget overflow (`4,096` tests or `65,536` probes per frame) simply returns `false` and draws the entity as vanilla.
- **No stale terrain (`SectionStability` + `OcclusionDirtyMixin` + `OcclusionTerrain`).** Every client terrain edit (`sendBlockUpdated`, `setBlocksDirty`, `setSectionDirtyWithNeighbors`, `setSectionRangeDirty`) and chunk load (`ClientChunkEvents.CHUNK_LOAD`) marks affected sections or chunk columns unsettled for `chunkSectionFadeInTime + 1000 ms`. On table overflow during mass edits, the entire level resets to unsettled rather than dropping a dirty mark. Every cell also checks `LevelRenderer.isSectionCompiledAndVisible` (with fade duration on 26.3; Sodium implements both `hasRenderedAllSections` and `isSectionCompiledAndVisible` through `SodiumWorldRenderer`). While the chunk builder is busy (`!hasRenderedAllSections()`), occlusion pauses until it has been idle for a full settle window.
- **Camera inside a block / spectator noclip / third person.** `EntityOcclusion.begin` reads the live `Camera.position()` (correct in first and third person) and checks every block overlapped by `[eye ± 0.25]`. If any such block has `canOcclude() == true`, occlusion disables for the entire frame.
- **Render-thread only, zero per-frame allocations.** All level reads run on the render thread (`mc.isSameThread()`) inside `LevelRenderer.extractLevel` (26.1.2) or `LevelExtractor.extract` (26.2 / 26.3), using pre-allocated direct-mapped cell/section caches (`stamp`-versioned), `MutableBlockPos`, and `IdentityTable` (index-walked without iterators).
- **Shader packs.** When Iris is loaded and `IrisApi.getInstance().isShaderPackInUse()` is true (or cannot be queried), occlusion disables so shadow-map passes are never altered.

### Never-cull list

Entities never skipped:
- The camera entity and anything sharing its root vehicle stack;
- Players and `Avatar`s (keeps vanilla player nametags and Aurora's `nametagPingEnabled` / `nametagTotemPopsEnabled`);
- Entities with custom names, `shouldShowName()`, or a scoreboard `Team` (`LivingEntityRenderer.shouldShowName` renders team labels through walls);
- Glowing entities (`Minecraft.shouldEntityAppearGlowing`);
- Leashed entities (`Leashable.isLeashed()` / `getLeashData() != null`), leash fence knots, fishing hooks, and both ends of every active leash or fishing line (tracked via `ClientEntityEvents` in `LINKERS`/`ENDS`);
- Oversized or beam/off-box renderers: `EnderDragon`, `EndCrystal`, `WitherBoss`, `Giant`, `Guardian` (and `ElderGuardian`), `Shulker`, `Display`, `LightningBolt`, `AbstractMinecart`, and any entity whose bounding box exceeds `3.0` wide or `4.0` tall;
- All `LivingEntity` instances while Aurora's `playerHealthIndicators` is enabled (draws health labels through walls). `HitboxRenderer` follows the visible-entity list after culling (matching `F3+B`); `HitColor` tints only the entity's depth-tested model.

Block entities: allowlisted in per-version `OcclusionTerrain.contained` to renderers that stay within `[pos - 1, pos + 2]` (`CHEST`, `TRAPPED_CHEST`, `ENDER_CHEST`, `SHULKER_BOX`, `BED` on 26.1.2, `SIGN`, `HANGING_SIGN`, `SKULL`, `DECORATED_POT`, `CAMPFIRE`, `LECTERN`, `ENCHANTING_TABLE`, `BELL`, `BRUSHABLE_BLOCK`, `SHELF`). Beacons, banners, conduits, end gateways, end portals, spawners, pistons, structure/jigsaw blocks and all modded block entities are never culled.

### Mod compatibility and stand-down

- **Entity Culling (`entityculling`):** owns entity and block-entity occlusion culling; all four Aurora occlusion mixins declare `.yieldsTo("entityculling")`. Verified live on 26.3 native Vulkan with `entityculling 1.11.3`: all four report `stood down: handled by entityculling` with zero warnings (`All 114 Aurora mixins applied or stood down (13 stood down)`).
- **Sodium:** `SodiumWorldRenderer` implements `hasRenderedAllSections()` and `isSectionCompiledAndVisible(...)`, calls `BlockEntityRenderDispatcher.tryExtractRenderState` (where `OcclusionChestMixin` hooks), and performs section-level entity culling in `EntityRenderer.shouldRender` which composes cleanly with `OcclusionBodyMixin` on `EntityRenderDispatcher.shouldRender`.
- **MoreCulling, ImmediatelyFast, EMF:** bytecode inspection of the pinned jars confirmed none of the three owns `EntityRenderDispatcher.shouldRender` or `BlockEntityRenderDispatcher.tryExtractRenderState` occlusion; all three ran together with `perfEntityOcclusion = true` in the 26.2 OpenGL and 26.3 Vulkan suites.

## Visual verification (zero pixel difference)

All captures ran on the workstation's second (portrait) monitor at `800 × 600` (`GUI scale 2`, `tick freeze`, `doMobSpawning`/`doDaylightCycle` off, NoAI silent mobs, weather clear, noon, chat hidden, cleared gear/effects). Every scene first verified a **same-gate control capture** (gate off vs gate off) had `0` differing pixels, then verified **gate on vs gate off** had `0` differing pixels across the full `800 × 600` frame (480,000 pixels).

Configurations exercised:
- **26.1.2 OpenGL:** Aurora + Fabric API + parity probe (no third-party mods).
- **26.2 OpenGL:** owner's 26.2 mod stack (Sodium 0.9.2, ImmediatelyFast 1.16.5, MoreCulling 1.8.2, BadOptimizations 2.4.1, EMF 3.3.12, ETF 7.2.7, FerriteCore, Krypton, etc.; excluding Spark, Simple Voice Chat, Crash Assistant, and installed Aurora/Fabric API jars).
- **26.3 native Vulkan (`--graphicsBackend VULKAN`):** owner's 26.3 mod stack (Sodium 0.9.2, C2ME, Lithium, ModernFix, VMP, ScalableLux, ImmediatelyFast 1.17.1, MoreCulling 1.9.0, EMF 3.3.12, ETF 7.2.7, Continuity, BadOptimizations, Sodium Extra; excluding Iris which aborts native Vulkan init per `MOD_MATRIX.md`, `entityculling` for the active test, plus Spark, Simple Voice Chat, Crash Assistant, and installed Aurora/Fabric API jars).

| Scene | Expected culling | 26.1.2 OpenGL | 26.2 OpenGL (owner mods) | 26.3 Vulkan (owner mods) |
|---|---|---|---|---|
| `wall` | Culled (`> 0`) | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) |
| `wall-edge` | Hidden culled, peeking kept | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) |
| `nametag` | Label visible through wall | 0 ctrl / 0 diff (named=1) | 0 ctrl / 0 diff (named=1) | 0 ctrl / 0 diff (named=1) |
| `glowing` | Outline visible through wall | 0 ctrl / 0 diff (glowing=1) | 0 ctrl / 0 diff (glowing=1) | 0 ctrl / 0 diff (glowing=1) |
| `glass` | Zero culls | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) |
| `leaves` | Zero culls | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) |
| `door` | Zero culls | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) |
| `leash` | Holder + leashed kept | 0 ctrl / 0 diff (leashed=1) | 0 ctrl / 0 diff (leashed=1) | 0 ctrl / 0 diff (leashed=1) |
| `third-person` | Hidden culled in F5 back | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) |
| `spectator-in-wall` | Disabled (0 active frames) | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) | 0 ctrl / 0 diff (0 culls) |
| `dense` (320 entities + 49 chests) | ~209 bodies + 49 chests/frame | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) | 0 ctrl / 0 diff (culled) |

All 33 visual checks (11 scenes × 3 configurations) recorded exact `[0, 0, 0]` pixel differences.

## Measurements (uncontested in-boot ABBA)

Dense scene: 160 NoAI silent pigs, 160 NoGravity dropped stone items, and 49 chests placed behind a `16 × 11` stone wall (`z = 5`), `renderDistance = 6`, VSync off, uncapped (`maxFps = 260` uncapped in benchmark window). Each boot ran **A1 (off) → B1 (on) → B2 (on) → A2 (off)** in-memory via the parity probe with per-window 1 ms JFR (`jcmd JFR.start/stop`) while no other `KnotClient` process was running.

### Frame times and Aurora overhead

| Line & configuration | Run | Gate | Avg FPS | Median (ms) | p95 (ms) | Aurora mean (ms) | Culled (bodies / chests / frames) |
|---|---|---|---:|---:|---:|---:|---|
| 26.1.2 OpenGL (no mods) | A1 | off | 882.1 | 1.058 | 1.404 | 0.0184 | 0 / 0 / 0 |
| | B1 | on | 3,130.7 | 0.296 | 0.541 | 0.0134 | 4,915,889 / 1,152,529 / 23,521 |
| | B2 | on | 3,046.3 | 0.296 | 0.643 | 0.0148 | 4,904,394 / 1,149,834 / 23,466 |
| | A2 | off | 874.5 | 1.077 | 1.502 | 0.0158 | 0 / 0 / 0 |
| 26.2 OpenGL (owner mods) | A1 | off | 1,597.2 | 0.575 | 0.825 | 0.0182 | 0 / 0 / 0 |
| | B1 | on | 3,879.6 | 0.220 | 0.463 | 0.0154 | 5,253,633 / 1,231,713 / 25,137 |
| | B2 | on | 3,729.2 | 0.229 | 0.487 | 0.0161 | 5,304,211 / 1,243,571 / 25,379 |
| | A2 | off | 1,574.2 | 0.589 | 0.835 | 0.0189 | 0 / 0 / 0 |
| 26.3 Vulkan (owner mods) | A1 | off | 988.7 | 0.984 | 1.223 | 0.0253 | 0 / 0 / 0 |
| | B1 | on | 1,581.9 | 0.620 | 0.781 | 0.0240 | 3,416,732 / 801,052 / 16,348 |
| | B2 | on | 1,614.4 | 0.605 | 0.777 | 0.0238 | 3,470,445 / 813,645 / 16,605 |
| | A2 | off | 1,030.2 | 0.930 | 1.181 | 0.0259 | 0 / 0 / 0 |

### JFR Render-thread (`tid=3`) execution shares

| Line | Run | Gate | Total samples | Extract share | Submit share | Occlusion share |
|---|---|---|---:|---:|---:|---:|
| 26.1.2 OpenGL | A1 / A2 | off | 4,171 / 4,216 | 20.12% / 15.73% | 5.71% / 5.00% | 0.74% / 0.26% |
| | B1 / B2 | on | 1,818 / 1,957 | 56.27% / 56.26% | 0.00% / 0.00% | 44.77% / 43.18% |
| 26.2 OpenGL | A1 / A2 | off | 4,236 / 4,222 | 23.44% / 22.38% | 15.20% / 15.66% | 0.07% / 0.05% |
| | B1 / B2 | on | 1,825 / 1,870 | 52.88% / 53.80% | 0.05% / 0.11% | 38.74% / 37.01% |
| 26.3 Vulkan | A1 / A2 | off | 2,650 / 2,904 | 24.26% / 23.69% | 15.09% / 16.15% | 0.23% / 0.17% |
| | B1 / B2 | on | 1,693 / 1,661 | 50.21% / 50.33% | 0.06% / 0.00% | 29.24% / 29.74% |

Notes on JFR interpretation: `FrameBenchmark` finishes at 16,384 frames (taking less wall time when FPS triples), and `extract` includes `EntityRenderDispatcher.shouldRender` / `BlockEntityRenderDispatcher.tryExtractRenderState` where `EntityOcclusion` executes. With the gate on, entity and block-entity submission (`submitEntities` + `submitBlockEntities`) drops from **5.0–16.2%** down to **0.00–0.11%**, and total frame time drops by **72% (26.1.2)**, **61% (26.2 OpenGL + Sodium)**, and **36% (26.3 native Vulkan + Sodium)**.

## Recommendation

**Ship on by default (`perfEntityOcclusion = true`).** All four ADR 0009 criteria are met: substantial measured gain across all three supported lines and both OpenGL and native Vulkan backends, clean startup stand-down for `entityculling`, zero pixel difference across 33 deterministic visual checks, and a machine-scoped fail-soft `perf*` gate with Aurora overhead staying at `0.013–0.026 ms` mean (well below the `0.25 ms` budget).

## Tests

- `EntityOcclusionCoreTest` covers `OcclusionSweep` (all 6 axis/sign directions, negative coordinates and negative-mirror layer mapping, single-cell holes, wall-edge peeking, `NEAR` and `EDGE` separation bounds, eye-radius footprint expansion, NaN/Inf/huge-coord/inverted-box rejection, `maxCells` and probe counters), `SectionStability` (section and column expiry, global reset, expired-entry compaction, live-overflow fail-closed reset), and `IdentityTable` (reference identity vs value equality, backward-shift deletion, growth, and index traversal).
- `MixinStandDownTest.entityCullingOwnsEveryOcclusionHook` verifies all four occlusion mixins gate on `perfEntityOcclusion`, yield to `entityculling`, and do not yield to `sodium`, `moreculling`, `immediatelyfast`, or `entity_model_features`.
