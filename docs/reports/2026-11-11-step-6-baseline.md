# Step 6: preliminary waypoint baseline

The step 6 reference case is the waypoint manager with 40 rows. A disposable
26.3 dev client on the development workstation was measured using the opt-in
probe in `tools/parity` at commit `0ca3c51`, before shader changes. This is one
diagnostic run, not a release or cross-device performance result.

- Backend and device: OpenGL, AMD Radeon RX 9070 XT, no additional mod set.
- Window: 854 x 480 framebuffer, GUI scale 2. Factory configuration: Dark,
  Round, background opacity 0.33; GUI frame limit 60.
- Forty seeded waypoints, with the manager open and current production cache
  behavior. The viewport showed about five rows at once.
- After 120 warm-up frames, 600 measured frames averaged 18.166 ms elapsed in
  `WaypointManagerScreen.extractRenderState`, p95 18.717 ms. The same scope
  submitted 6,766 `RenderUtil` fills per frame.

This interval includes work and any waits inside extraction. It excludes
Minecraft's deferred GUI processing, total GPU execution and frame pacing; it
is not a CPU-only measurement. The fixture includes glass, text, layout and
other screen work, so the entire result cannot be attributed to shapes. The
row-template path is bypassed when `MaterialSurface.unified()` is active, so
the result must not be described as a measurement of every cache variant.
The screenshot and raw fixture remain under `build/step6-baseline/` locally.

## Experimental shader comparison

An opt-in prototype (`-Daurora.shapes.shader=true`) was subsequently run with
the same world and settings on 26.3 OpenGL. One run measured 0.104 ms mean,
0.118 ms p95, and zero CPU rasterizer fills per frame in the same interval. A
second boot measured 0.118 ms mean, 0.185 ms p95, zero fills and 33 shader quads
per frame. The original fill counter does not count shader quads. These runs
exclude deferred rendering and GPU cost; they do not establish an end-to-end
speedup.

An earlier pair, before the scaled-bounds change, used the same disposable
26.3 OpenGL game directory, window, theme, 40 waypoints and GUI cap, with a
fresh boot per path. Each run warmed up 120 manager frames and sampled 600:

| Path | Extraction mean / p95 | Frame interval mean / p95 | Fill / quad submissions per frame |
|---|---|---|---|
| CPU | 17.771 / 18.706 ms | 21.506 / 23.167 ms | 6,766 / 0 |
| Shader | 0.120 / 0.157 ms | 16.667 / 17.970 ms | 0 / 33 |

Frame intervals span successive manager extractions, including the 60 FPS GUI
cap and any waits; they do not isolate GPU time. This single pair is diagnostic
for this fixture and hardware, not a broad frame-rate or GPU result. The world
continues to advance between boots.

A controlled pair after the scaled-bounds change used separately copied clean
fixtures, the same original world and options, and exactly 40 freshly seeded
waypoints per run. Minecraft's AFK frame cap was temporarily disabled during
each sample; the fixture verified that the prior option was restored:

| Path | Extraction mean / p95 | Frame interval mean / p95 | Fill / quad submissions per frame |
|---|---|---|---|
| CPU | 20.191 / 26.250 ms | 25.172 / 34.364 ms | 6,766 / 0 |
| Shader | 0.199 / 0.380 ms | 16.692 / 19.646 ms | 0 / 33 |

The 60 FPS GUI cap remained enabled. The world and loading state can differ
between boots, and one pair does not characterize variability or isolate GPU
time. A separate repeated shader run reached Minecraft's 30 FPS AFK cap and
its frame intervals are excluded from the comparison above.

With the experimental flag enabled, a separate 26.3 OpenGL boot with factory
defaults and no screen open passed `tools/overhead_check.py`: after two warm-up
runs, the four judged runs measured 0.141 to 0.158 ms CPU mean and 0.183 to
0.229 ms p95, with zero fills and glass captures. This is one matrix row on the
development workstation, not the complete release gate. Baseline rows on
26.1.2 OpenGL/VulkanMod, 26.2 OpenGL/Vulkan and 26.3 Vulkan also passed before
the scaled-bounds change; they must be rerun against the final candidate.

In-world 40-waypoint screens were additionally inspected at 854 x 480 / scale 2
on 26.1.2 OpenGL and VulkanMod, and 26.2 OpenGL and native Vulkan, using the
Round/33% theme. The 26.1.2 OpenGL waypoint screen was also inspected at
Square/19%; the feature grid was inspected on 26.1.2 VulkanMod and 26.2 Vulkan.

The default Round/33% waypoint screen was also inspected on native Vulkan; the
Square/19% screen was inspected on OpenGL. The Aurora feature screen's grid and
list layouts were inspected on 26.3 OpenGL at 854 x 480 and 1280 x 720, in the
Round/33% theme. A separate OpenGL fixture compares
fractional-position rounded fills, circles, outlines, squircles, clipping and a
rotated and scaled pose against the CPU path. It also asserts that a clipped
2,600-device-pixel-wide shape submits one quad. The earlier shape cases were
inspected without a world on 26.1.2 OpenGL, 26.1.2 VulkanMod 0.6.8 and 26.2 native Vulkan. The
VulkanMod log contained `Element Normal not found in elements [Position, Color,
UV0, UV2]` during startup both with the prototype enabled and disabled in the
same fixture; this is not a clean compatibility result. The cases were also
inspected on 26.3 OpenGL with Sodium 0.9.2 and Iris 1.11.7, both without a
shader pack and in a world with Complementary Unbound r5.9.3 active. Iris
logged missing overrides for the existing hit-color armor and item-cutout
pipelines in both shader-enabled and shader-disabled runs of that same fixture;
this is not a clean shader-pack compatibility result. Shader edges are smoother than
the CPU's pixel-row approximation; the paths are not pixel-identical.
Screenshots remain under `build/step6-shader-test/`, `build/step6-261-opengl/`,
`build/step6-262-vulkan/` and `build/step6-263-iris/` locally. The scaled and
wide cases were inspected on 26.3 OpenGL after the latest geometry change;
the other backend captures preceded it.

The shader remains disabled by default. Triangle rasterization, cases outside
its parameter range and layer captures still use the CPU path. Runtime testing
of further shader-mod combinations, real-screen and appearance cases,
frame-level/GPU measurements and the release overhead gate
remain outstanding. Retain caches until individual removal is supported by
measurement.
