# Step 8, PR 5: world rendering, measured (2026-11-12)

Branch `feat/perf-world-render`, stacked on `feat/perf-maps` ([ADR 0009](../adr/0009-client-optimizations-aurora-owns.md)). Every item was measured before shipping. Neither of the two CPU-side items cleared the bar, so neither ships.

## Larger Section Copy Cache (dropped)

In 26.3, `LevelExtractor` creates a fresh `RenderRegionCache` every frame. A cache that outlives the frame would hold block-state copies across frames, so every block change would have to invalidate it or meshes would be built from stale neighbours.

The probe teleported the player 1500 blocks to force a burst of chunk loading. Over the next 12 s, `RenderRegionCache.createRegion` took 0.1% of render-thread samples and `SectionCopy.<init>` 0.1%. There is nothing for a larger cache to save. Sodium replaces this path entirely.

## Particle Section Culling (dropped)

Vanilla culls particles against the frustum only. The idea was to also skip particles in sections the renderer's visibility pass did not reach, for vanilla and through Sodium's `isBoxVisible`.

Stress scene on 26.3 OpenGL: the probe adds 400 flame particles per tick within 24 blocks, half above the player and half 16–40 blocks underground. `Particle.extract` takes 6–8% of render-thread samples.

| Variant | `Particle.extract` | Culling cost | FPS |
|---|---|---|---|
| Off | 5.9–7.9% | — | about 550–660 |
| Visible-section set only | 2.7–3.3% | 1.0–3.7% | higher, **but wrong** |
| Visible set, all-air sections counted as visible (correct) | 5.7–6.1% | 2.2–2.3% | about 470–530 |
| Sodium `isBoxVisible` per particle (matrix set A) | 11–12% | 13–15% | about 870 (vs about 1080 off) |

- **Why the first variant was wrong.** The visible-section list leaves out all-air sections, which have nothing to draw. Screenshots showed most open-air particles next to the player missing.
- **Why the correct variant doesn't pay.** Once only sections with blocks can be culled, the saving is about the size of the lookup cost.
- **Sodium.** Its visibility query is a tree walk per particle, and it reports most underground particles as visible anyway.

The probe gained `particles <n>` and `teleport <blocks>` for these measurements.

## Deferred: GPU-side items (owner decision, 2026-11-12)

Deferred until testers with GPU-bound hardware can supply evidence:

- Deferred Sky
- Entity Outline Scissor
- Nearest Plane Quad Order (a Sodium mesh patch)
- Sky In Main Pass, Early Swapchain Acquire and Background Vertex Copy (native Vulkan, 26.2 and 26.3 only)
- Occlusion Culling

Each would save GPU time, not CPU time. The development machine (RX 9070 XT) keeps Minecraft CPU-bound at 500–1000 fps, so frame time there cannot show a gain. Each also risks visual or pacing regressions: sky and fog ordering, outline post passes, render-pass state, and Reflex's present timing. They need GPU timestamps on GPU-bound hardware before a prototype is worth building. The roadmap lists them under later work.
