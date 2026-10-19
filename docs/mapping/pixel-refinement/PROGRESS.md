# Per-pixel mapping refinement

Start: 6841436, codex/unified-mapping. Original untracked notes and their hashes
are preserved in starting-status.txt / starting-notes-sha256.txt. No original
run/ data is an execution target. New marked Temp fixtures only.

Inspected RenderUtil physical coverage/RectSink, UiLayerCache capture/masking,
MaterialIconRenderer/NativeIconGeometry, shared materials, ColorSwatch, fonts,
HUD transforms and WorldLineRenderer before changing mapping presentations.

Implementation: reuse cached native AA layers for symbols/label plates, native
minimap shape clipping with logical-resolution terrain sampling and bulk row
expansion, continuously turning AA player arrows, native chevron glyphs, existing
shared UI controls plus marker previews, shared pixel-width 3D block outlines.
The terrain engine, identity, persistence and world-map tile/LOD pipeline stay.

An exact-integer left span was omitted by RenderUtil's existing fillAARow;
regression tests cover that correction, fractional spans, alpha and bulk ABGR
expansion. Initial full test/build passes (570 tests); native visual/scale checks
and performance acceptance are in progress. Cache limits include 128 marker
layers / 16 MiB, a 2048-square minimap output ceiling, no unchanged stationary
uploads, 20 Hz motion updates, and frame-safe texture retirement during HUD resize.

Reference runtime was frozen before changes in Temp/aurora-mapping-pixel-reference-01.
The rendering probe exercises 1080p/1440p/4K, GUI scales 2/3/4/6 and fractional HUD
scale, map/manager/editor/HUD, resource reload and continuous camera rotation.
Raw screenshots and worlds remain private in their fixture roots.

Final: Java 21 test/build passes with 575 tests, zero failures/errors/skips.
Clean final native fixtures candidate-09 and functional-04 passed 15 rendering
and 22 functional assertions, normal exit, and zero render-thread/texture-close
warnings. Both frozen runtime manifests match the current compiled build.
The stricter log check exposed Netty-thread GPU cleanup (also present in the
reference); disconnect now schedules minimap/marker release on the client executor.
Native 1:1 crops and 24-frame layout overviews are derived from candidate-09.
Screenshot hashes, crop coordinates, runtime/observer hashes and production jar
hash are recorded in the adjacent JSON files. Raw launches/saves stay private.

Matched foreground gameplay reference-01 / candidate-04 passed with identical
inputs/observer, no invalid focus frames and no candidate intervals above 50 ms.
Only the subsequent disconnect scheduling change differs, outside measured phases.
4K rotation repeats retained roughly 120 FPS with small mixed tails; the report
records repeat variation and bounded memory costs without claiming an FPS gain.
The uncached arrow prototype was refined. CPU summaries use identical calibrated
intervals and interpolation; early inconsistent progress estimates are superseded.

Final details: MAPPING_PIXEL_RENDERING_REPORT.md. No original run/ data, protected
render/pacing/Hitreg/launcher paths, or pre-existing benchmark notes changed.
No deletions, git clean, push, merge, publishing or user-instance installation.
