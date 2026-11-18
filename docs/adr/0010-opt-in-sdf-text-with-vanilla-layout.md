---
status: accepted
date: 2026-11-17
---

# SDF text is an opt-in glyph raster and pipeline variant; vanilla layout and bitmap fallback are retained

Minecraft's exact-size bitmap is the baseline at native scale; nearest sampling shows pixels under a larger pose.
Aurora's Smooth Scaling (SDF) toggle is off by default until the owner accepts side-by-side comparisons.
It applies to bundled custom faces wherever Text Renderer selects them in the GUI: Aurora windows only in
Aurora UI Only, and also HUD and vanilla GUI text in All Text. The fixed Aurora wordmark can use the same path.

## Decision

- Keep the raster definitions as the only source for sizes, weights, bearings, advances, and fallback order.
  Runtime aliases share a 64 px SDF strike per font file across roles. Minecraft still lays out, decorates,
  batches, transforms and clips the text. Do not build a separate renderer or generate parallel font JSONs.
- Generate distance fields only for requested glyphs, reuse bounded R8 pages, and use the selected backend's
  GUI pipeline. Do not evict textures while deferred draws can reference them. Warm cold glyphs over frames;
  use the exact bitmap until a glyph is ready, or permanently if it cannot fit or has no distance field.
- Preserve Minecraft fallback fonts, sprites and Material Symbols. Leave world-space lighting, see-through
  text, OIT and outline render types on the bitmap path; a world-text variant would need separate validation.
- If compilation, font initialization or upload fails, use raster text for the rest of the session.
  Keep all SDF hooks fail-soft, and stand them down for ModernUI and Caxton.

See [phase 1](../reports/2026-11-17-sdf-spike.md) for geometry, overhead and the native acceptance checklist.
