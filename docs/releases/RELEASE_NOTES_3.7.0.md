# Aurora Client 3.7.0

One JAR per Minecraft version: 26.1.2, 26.2 and 26.3, built from the same
source commit. Install the one matching your Minecraft version with Fabric
Loader and Fabric API. Java 25 is required.

## New

- **Pulsar PvP overhaul & v7 offline HTML reports:** deep breakdown for 1.9+
  Sword / NethPot / SMP and Shield / Axe combat — exchange openers & combo
  conversion, hit trade ratio, W-tap / sprint-reset cadence (overall and in
  combos), attack-charge discipline (`>= 90%` vs `< 90%`), shield-disable
  punish conversion & delay, axe-to-sword swap speed, health & absorption
  timeline with worst 3 s burst highlighting, clickable key turning points, and
  strictly measurement-backed takeaways. Settings are reorganized from 9 chips
  into 6 groups, and offline HTML reports are rebranded to **Aurora Pulsar**
  (format v7) with interactive SVG health and timeline lanes.
- **Gradient Dithering** (Settings → Display, on by default, cosmetic): adds
  an 8×8 ordered Bayer dither of under half an 8-bit step to skies, sunrise
  glow, End sky, clouds and terrain (vanilla and Sodium) to eliminate color
  banding without altering GUI/HUD pixels. Stands down for VulkanMod and active
  Iris shader packs.
- **Smooth Fog** (Settings → Display, off by default, cosmetic): replaces
  Minecraft's linear distance fog ramp with a C1-continuous cubic Hermite curve
  over the exact same `[fogStart, fogEnd]` bounds, composing with Sodium and
  Gradient Dithering in one pass.
- **Background Frame Limit** (Settings → Performance, off by default): caps
  frame rate when unfocused; yields automatically to Dynamic FPS.

## Performance

- **Entity & Block-Entity Occlusion Culling** (yields to EntityCulling):
  conservatively skips extracting entities and block entities hidden behind
  settled opaque terrain, with direct chunk-section reads, cross-frame
  `terrainStamp` block-solidity caching, exact sweep-decision caching, and
  packet/tick-driven leash tracking.
- **GUI & rendering fast paths:** GUI Glint Baking (redraws glint-only GUI
  items only when the glint moves, yielding to ImmediatelyFast), GUI Flat Faces
  (skips exact zero-area edge-on quads), GUI Layering (yields to Polytone and
  Gnetum), Left-to-Right Text fast path (yields to Modern UI and Caxton), and
  26.2 OpenGL dispatch caching (`perfGlDispatch`).
- **World, HUD, font & UI baking efficiency pass:**
  - `HitboxRenderer` and `BlockOverlayRenderer` defer GPU buffer allocation
    until a shape actually draws, eliminate per-entity `AABB`, `Vec3`, and list
    allocations, and unroll SDF box corner transforms.
  - `HudModule` computes layout width/height once per frame instead of twice;
    `InfoModule`, `ArmorModule`, `PotionModule`, and `CrosshairRenderer` cache
    per-frame text measurements, equipment slots, effect sprites/names, and
    crosshair geometry without per-frame heap churn.
  - `MixinFont`, `AuroraFontRenderer`, `SdfInstance`, and `UiLayerCache` cut
    per-call allocations, use binary search for width truncation, hoist SDF UV
    math per glyph, and bulk-fill opaque cached UI rects.
  - World map region I/O uses block transfers, primitive capture queues, and
    reused north-up rows.
- **Overhead budget:** across all 14 native GPU acceptance rows (26.1.2, 26.2,
  26.3 on OpenGL, native Vulkan, and VulkanMod), judged runs measured
  **0.034–0.089 ms** mean and **<= 0.167 ms** p95 Aurora CPU per frame (down
  from up to 0.222 ms mean / 0.290 ms p95 in 3.6.0), with zero fills and zero
  glass captures.

## Changed

- **UI layout & corners:** fixed square corners throughout the UI, flush
  feature grid alignment across shrunk viewports, and polished layout spacing
  and visual hierarchy.
- **Pulsar capture & export:** cached weapon/world/player lookups per tick,
  replaced nested incoming-hit scans with amortized O(1) sweeps, added O(1)
  history ID lookups, and streamed report SHA-256 digests without re-reading
  written files.

## Fixes

- **Sodium 0.9.2 on Minecraft 26.2 native Vulkan:** preserved the `"sodium"`
  substring in Aurora's Sodium twin pipeline namespaces (`aurora_sodium`) so
  Sodium's `VulkanPipelineMixin` attaches its push-constant range and never
  crashes `vkCreateGraphicsPipelines`.
- **SDF text & shader compatibility:** SDF text now runs alongside
  ImmediatelyFast, font deferral runs alongside ModernFix, and SDF/shape
  pipelines wait for the Shader Loader's first apply under RRLS.
- **Hitboxes, crosshair & Item Physics:** hitboxes follow main-world visible
  entities after vanilla/Sodium/EntityCulling/Aurora occlusion, crosshair
  target indicators reuse vanilla's block-clipped `crosshairPickEntity`, and
  Item Physics uses frame-rate-independent landing easing.
