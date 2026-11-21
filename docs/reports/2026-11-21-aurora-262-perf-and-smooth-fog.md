# Aurora 26.2 Performance Pass and Smooth Fog

Date: 2026-11-21  
Branch: `perf/aurora-262-pass` (base `a5073d49`)  
Hardware: Windows 11, Intel Core i7-14700F, AMD Radeon RX 9070 XT, Java 25 (Temurin 25.0.4.1).

## Reference instance and profiling baseline

Profiling used a disposable copy of the owner's **Aurora 26.2** reference instance (`25` active gameplay/performance mods: Sodium 0.9.2, ImmediatelyFast 1.16.5, BadOptimizations 2.4.1, MoreCulling 1.8.2, FerriteCore 9.0.0, Particle Core 0.3.3, Krypton 0.3.1, RRLS 5.2.8, EMF 3.3.12, ETF 7.2.7, 3D Skin Layers 1.11.4, Wavey Capes 1.11.3, NotEnoughAnimations 1.12.7, Fast IP Ping 1.0.12, PacketFixer 3.3.6, CWB 5.0.0, Controlling, Searchables, Reese's Sodium Options, Mod Menu, Placeholder API, Cloth Config, Fzzy Config, IAS, Fabric Language Kotlin) at `1280×960`, GUI scale 3, OpenGL, uncapped FPS, on the portrait monitor.

Baseline JFR on `master` (`a5073d49`, 8 s per scene) identified three actionable hotspots alongside the owner's full mod stack:

| Scene | Baseline FPS / median | `EntityOcclusion` share | `GlCommandEncoder` / `VertexArrayCache$Separate` | `DynamicAtlasAllocator` |
|---|---|---|---|---|
| `world-pvp` | 761.4 FPS / 1.140 ms | 9.07% (`opaque` 4.19%, `hideBody` 1.63%, `begin` 1.40%, `Leashable.getLeashHolder` 1.40%) | 3.49% / 1.63% | <0.25% |
| `inventory-pvp` | 840.2 FPS / 1.055 ms | 8.77% (`begin` 2.01%, `getLeashHolder` 2.01%) | 4.76% / 0.75% | 1.75% total |
| `visible-crowd` (60 pigs + 21 chests) | — | 12.89% (`opaque` 5.16%, `Level.getBlockState` 6.02%, `LivingEntity.isCurrentlyGlowing` 1.43%) | 4.01% / 1.43% | 0.57% |

## Shipped changes

### 1. Entity Occlusion fast paths (`perfEntityOcclusion`, all three lines)

Stays under the existing `perfEntityOcclusion` machine gate and yields to `entityculling` (`OcclusionFrameMixin`, `OcclusionBodyMixin`, `OcclusionChestMixin`, `OcclusionDirtyMixin`, `OcclusionLeashMixin`).

- **Axis gap early-out** (`core/.../OcclusionSweep.java`): skips `axis(...)` when an axis gap is smaller than `NEAR` before computing layer bounds and lerps.
- **Direct section reads + cross-frame solidity cache** (`adapters/shared/.../EntityOcclusion.java`): caches settled non-air `LevelChunkSection` references for the current frame (cleared in `end()`, zero chunk retention), short-circuits `hasOnlyAir()` sections, adds a 1-entry inline `(lastSx, lastSy, lastSz)` section hit, and tags the 8,192-entry block solidity cache with `terrainStamp` (incremented only on `blockChanged`, `sectionsChanged`, `CHUNK_LOAD`, `CHUNK_UNLOAD`, level/enable resets, or chunk builder activity).
- **Exact sweep-decision cache** (`EntityOcclusion.java`): direct-mapped primitive tables (`1024` entities, `512` block entities, zero allocations) reuse exact `SWEEP.hidden(...)` results while `terrainStamp`, camera coordinates `(eyeX, eyeY, eyeZ)`, render distance, and swept entity bounds match (refreshed every 250 ms so newly settled sections start culling promptly). Dynamic metadata exclusions (`mayHideSlow`: custom name, team, glowing, leash, fishing hooks) still run live every frame.
- **Tick + packet-invalidated leash ends** (`OcclusionLeashMixin` on `ClientPacketListener#handleEntityLinkPacket` + `EntityOcclusion.java`): separates fishing hooks (typically empty) from `Leashable` entities and rebuilds `LEASH_ENDS` lazily on demand only when the game tick advances, an entity loads/unloads, or a leash packet arrives (falling back to per-frame refresh if `OcclusionLeashMixin` does not apply).

#### Measured impact on Aurora 26.2 (OpenGL, 25 owner mods)

| Scene | `EntityOcclusion` JFR share (`master` → candidate) | `Level.getBlockState` share (`master` → candidate) | Steady-state FPS / median |
|---|---|---|---|
| `world-pvp` | **9.07% → 0.00%** (0/334 samples) | 0.59% | **1,565.8 FPS / 0.5625 ms** |
| `inventory-pvp` | **8.77% → 0.26%** (1/379 samples) | 0.53% | **1,513.7 FPS / 0.5856 ms** |
| `visible-crowd` | **12.89% → 1.03%** (3/292 samples) | **6.02% → 0.00–1.37%** | **1,668.3 FPS / 0.5427 ms** (`perfEntityOcclusion=on` beats `off` `1,620.0 FPS / 0.5472 ms`) |

Occlusion culling verified active (`1,376,007` bodies skipped on 26.2, `1,610,754` bodies + `211,336` chests skipped on 26.1.2) with zero entity/terrain pixel differences (`(0, 0)` on frozen 26.2 world; named and glowing entities preserved).

### 2. Blaze3D OpenGL dispatch caching (`perfGlDispatch`, Minecraft 26.2 only)

Machine-scoped gate `perfGlDispatch = true` (kept out of profiles and `defaults/3.2.0.json`). Yields to `vulkanmod` at startup and checks `!ShaderPackState.irisPackInUse()` at draw time before skipping any GL calls.

Applies on **26.2 only** (`GlTextureMipCacheMixin`, `GlCommandEncoderDispatchMixin`, `FrameBufferCacheMixin`, `VertexArrayCacheSeparateMixin`):
- Caches `GL_TEXTURE_BASE_LEVEL` / `GL_TEXTURE_MAX_LEVEL` per `GlTexture` to skip unchanged `_texParameter` calls.
- Skips repeat `GL33C.glDrawBuffers` calls within the same `GlRenderPass`.
- 4-entry inline primitive `(cId, cMip, dId, dMip) -> fbo` cache for single-color-attachment `FrameBufferCache.getFbo` (invalidated in `destroyFbo`).
- Identity cache (`lastBindings` + `IdentityHashMap`) on `VertexArrayCache$Separate.bindVertexArray` avoiding per-draw `Arrays.asList` allocation and hash lookup while keeping all GL calls vanilla.

#### Warmed ABBA benchmarks on Aurora 26.2 (OpenGL, 25 owner mods)

| Scene | `off1` | `on1` | `on2` | `off2` | Mean OFF → ON | Pixel diff (frozen) |
|---|---|---|---|---|---|---|
| `world-pvp` | 1,196.7 FPS (0.7091 ms) | 1,340.4 FPS (0.6288 ms) | 1,565.8 FPS (0.5625 ms) | 1,506.5 FPS (0.5829 ms) | **0.6460 → 0.5957 ms (-7.8% median)** | `(0, 0)` |
| `inventory-pvp` | 1,440.5 FPS (0.6188 ms) | 1,513.7 FPS (0.5892 ms) | 1,510.5 FPS (0.5856 ms) | 1,467.6 FPS (0.5999 ms) | **0.6094 → 0.5874 ms (-3.6% median, +4.0% FPS)** | `(0, 0)` |

JFR shares on 26.2: `VertexArrayCache$Separate.bindVertexArray` dropped `1.63% → 0.00%` (`world-pvp`), `0.75% → 0.53%` (`inventory-pvp`), `1.43% → 0.34%` (`visible-crowd`); `GlCommandEncoder.trySetup` dropped `3.49% → 1.20%` (`world-pvp`), `4.76% → 2.64%` (`inventory-pvp`), `4.01% → 2.40%` (`visible-crowd`); `FrameBufferCache.getFbo` dropped to `0.00%` across all three scenes.

### 3. Smooth Fog (`smooth_fog`, cosmetic, all three lines)

New profile-scoped Display setting (`smoothFog = false` default, `FairPlay.COSMETIC`, icon `foggy` `\uE818`). Placed under **Settings → Display** directly below Gradient Dithering.

- `core/.../SmoothFog.java` patches `linear_fog_value(...)` in vanilla `minecraft:include/fog.glsl` and Sodium's `sodium:fog.glsl` behind `#ifdef AURORA_SMOOTH_FOG` to apply a cubic Hermite S-curve (`t * t * (3.0 - 2.0 * t)`) over the exact same `[fogStart, fogEnd]` endpoints.
- `SmoothFogPipelineMixin` (`priority = 1050`) runs after `GradientDitherPipelineMixin` and accepts vanilla world pipelines, Sodium terrain pipelines, and `aurora:gradient_dither/*` pipelines, so Smooth Fog composes cleanly with Gradient Dithering in a single pass.
- Yields to `vulkanmod` at startup and checks `!ShaderPackState.irisPackInUse()` at draw time.

Visual & UI verification:
- Verified zero shader compilation warnings/errors across `26.1.2` OpenGL, `26.2` OpenGL (with Sodium + Gradient Dithering), `26.2` native Vulkan, `26.3` OpenGL (with Sodium + Iris + Gradient Dithering), and `26.3` native Vulkan (with Sodium + Gradient Dithering).
- Visually inspected off vs on captures underwater (26.2 OpenGL) and on distant frozen terrain at `renderDistance = 6` (26.2 native Vulkan): near detail retains crispness while mid-to-far fog eases smoothly into the horizon color with identical endpoints.
- Mandatory UI overflow review passed for Settings → Display at `1280×960` scale 3 (factory opacity `0.42` glass and `1.0` opaque) and `854×480` scale 2, plus Search (`Smooth Fog`) in both Grid and List layouts.

## Measured and dropped (ADR 0009)

1. **`DynamicAtlasAllocatorMixin` (`perfGuiAtlasAlloc`)**: Implemented and ABBA-tested on 26.2 OpenGL (`world-pvp`: `1,370/1,431 FPS` off vs `1,440/1,347 FPS` on — flat noise; in `inventory-pvp`, `GuiItemAtlas` sizes tightly so `used + keys.size() > capacity` still hit key hashing). Dropped.
2. **`perfGlDispatch` on Minecraft 26.1.2 and 26.3**:
   - On `26.1.2` OpenGL (`VertexArrayCache` takes a single `VertexFormat`, no `glDrawBuffers` or `FrameBufferCache`): ABBA measured `0.4025 ms` off (`off1=0.3719 ms`, `off2=0.4330 ms`) vs `0.3919 ms` on (`on1=0.3913 ms`, `on2=0.3924 ms`) — inside run-to-run drift with `off1` fastest. Dropped from 26.1 adapter.
   - On `26.3` OpenGL (Mojang's `renderpearl` already moved VAO onto `GlRenderPipeline` and `glDrawBuffers` into `createRenderPass`): ABBA measured `0.4596 ms` off vs `0.4710 ms` on — no gain. Dropped from 26.3 adapter.

## Cross-line verification and overhead budget

| Line / Backend | Mods | Mixins | Aurora WARN/ERROR | Smooth Fog | Notes |
|---|---|---|---|---|---|
| 26.1.2 OpenGL | Vanilla + Fabric API | 126/126 applied | 0 | Active (vanilla + Gradient Dither) | `EntityOcclusion` culled 1.61M bodies + 211k chests |
| 26.2 OpenGL | 25 Aurora 26.2 owner mods | 127/127 (4 stood down for `immediatelyfast`) | 0 | Active (Sodium + Gradient Dither) | Full ABBA + JFR + UI overflow sweep |
| 26.2 Vulkan | Vanilla + Fabric API | 127/127 applied | 0 | Active (vanilla + Gradient Dither) | Static `rd=6` visual capture verified |
| 26.3 OpenGL | Aurora 26.3 stack (Sodium, Iris, EntityCulling, ImmediatelyFast, RRLS, MoreCulling, etc.) | 127/127 (9 stood down: 5 `entityculling`, 4 `immediatelyfast`) | 0 | Active (Sodium + Gradient Dither alongside Iris w/o pack) | Verified stand-downs |
| 26.3 Vulkan | Aurora 26.3 stack minus Iris & RRLS | 127/127 (9 stood down) | 0 | Active (Sodium + Gradient Dither) | Native Vulkan clean |

### Factory-defaults overhead budget (`26.2` OpenGL, `-Daurora.diagnostics.log=true -Daurora.diagnostics.runs=6`)

Validated with `python tools/overhead_check.py`:
- Run 3: **0.028 ms** mean, **0.109 ms** p95, `0` glass, `0` fills
- Run 4: **0.029 ms** mean, **0.103 ms** p95, `0` glass, `0` fills
- Run 5: **0.027 ms** mean, **0.102 ms** p95, `0` glass, `0` fills
- Run 6: **0.028 ms** mean, **0.104 ms** p95, `0` glass, `0` fills

All 4 judged runs are well within the `0.25 ms` mean / `0.40 ms` p95 budget.

## Gaps not covered

- Active Iris shader pack runtime stand-down was verified via shared `ShaderPackState.irisPackInUse()` contract and code path (identical to Gradient Dithering); Aurora 26.3 had no installed shader pack zip on disk during this pass.
- VulkanMod on 26.1.2 was verified via `MixinStandDownTest` unit contract (`yieldsTo("vulkanmod")`), not booted live.
