---
status: accepted
date: 2026-11-17
amended: 2026-11-19
---

# SDF text is a glyph raster and pipeline variant; vanilla layout and bitmap fallback are retained

Minecraft's exact-size bitmap is the baseline at native scale; nearest sampling shows pixels under a larger pose.
Aurora shipped SDF text behind an off-by-default Smooth Scaling (SDF) toggle until the owner accepted it.
On 2026-11-19 the owner made SDF the only rendering path for text, hitboxes and block outlines and the
toggles were removed (see Amendment). It applies to bundled custom faces wherever Text Renderer selects them in the GUI: Aurora windows only in
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

## Amendment, 2026-11-19

- Smooth Scaling (SDF) and the Smooth Lines (SDF) options for Hitboxes and Block Overlay are gone, with their
  config fields (`textSdf`, `hitboxSdfLines`, `blockOverlaySdfLines`). Saved values are ignored.
- SDF is used whenever it is available. The bitmap text path and vanilla line pipeline remain only as the
  session fallback described above: a failed or stood-down SDF hook, a failed shader or atlas, ModernUI or
  Caxton installed. They are not a user choice.

See [phase 1](../reports/2026-11-17-sdf-spike.md) for geometry, overhead and the native acceptance checklist.
