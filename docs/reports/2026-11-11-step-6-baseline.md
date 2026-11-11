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

The probe also builds for 26.1.2 and 26.2, but this run does not validate their
runtime behavior. Compare the same fixture and settings after implementation,
measure deferred/frame-level effects independently, and retain caches until
their individual removal is supported by measurement. No shader has been
implemented or validated by this preliminary baseline.
