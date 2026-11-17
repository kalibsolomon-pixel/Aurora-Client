# Curvature bake cost and template retention

Base: `ui/depth-tune`, `4b2b00db59b6237875fe46f9e748fe5a7962b605`.
Scope: preserve captured face/shadow pixels while reducing cold bakes and reusing
templates across screen changes. No game was launched; the parent owns in-client
timings, screenshots and live/factory verification.

The bake now uses direct ABGR buffers, one ramp per row, cached source-colour HSL
terms and repeated colour/delta results. A conservative interior cross bypasses
SDF and normal/specular math. Straight rims reuse row/column samples; corners
retain the original equations and floating-point order. Shadow work skips opaque
pixels and bounds distance calculations to the offset face plus softness; axis
terms are computed separately. An inexpensive outer scan still clears hidden RGB
on fully transparent pixels, matching the old whole-image shadow pass exactly.

`UiLayerCacheLightingTest` compares native images against a frozen test-only copy
of the old math and raster loops. It covers scale 2/3, rounded and square boxes,
fractional circular knobs, clipped geometry and clamped radii, all five profiles,
eight role ramps, six source colours, AA and nonuniform translucent pixels, before
and after shadow. All **1,920 cases / 90,052,800 pixel comparisons** are bit exact
on each supported line: maximum channel difference **0**. A further deterministic
sampling test covers very narrow faces and fractional bevel boundaries.

The benchmark alternates old/new paths within the same warmed JVM: 15 paired
warmups, then 21 paired samples, medians below. It includes synthetic captured AA
face creation, lighting and shadow for an 80×80 GUI-pixel tile at scale 3, radius
10, ON ramp +11→+3, elevation 2 / softness 4 / alpha 20%. NativeImage allocation/texture upload,
deferred rendering, screen extraction and GPU time are outside these measurements.

| Minecraft | Full bake before → after (ms) | Lighting before → after (ms) | Shadow before → after (ms) |
|---|---:|---:|---:|
| 26.1.2 | 5.269 → 1.354 | 3.104 → 0.536 | 1.656 → 0.306 |
| 26.2 | 5.259 → 1.262 | 3.083 → 0.501 | 1.647 → 0.246 |
| 26.3 | 5.255 → 1.261 | 3.080 → 0.500 | 1.654 → 0.258 |

On 26.3 this is 76.0% less full-bake CPU time (4.17× faster). The first plateau/
colour/direct-buffer implementation measured 1.846 ms; reusing straight rim samples
then reduced the lighting phase from 1.073 ms to approximately 0.500 ms. These are
offline microbenchmarks, not a claim about screen reopening or frame rate.

`MaterialTemplates` keeps a primitive LRU across screen removal and resize while a
screen is open; returning to gameplay releases it (parent follow-up, 2026-11-17).
Identity includes exact fractional geometry, recipe/state, shadow policy, colour
endpoints and the theme/scale version. Full identity checks turn hash collisions
into safe misses. A colour change creates another raster rather than modifying
one referenced by deferred blits. Theme generation, scale and roundness invalidate
the pool at the frame boundary. Existing removal hooks no longer clear it.

Retention is capped at **256 entries / 16 MiB RGBA CPU image backing** and hence
at most **16 MiB corresponding texture pixel backing** (32 MiB combined, excluding
driver/object overhead). Evictions and oversized entries retire at the next frame
boundary. Their current-frame transient backing is additional; the cap describes
retained backing, not process memory or a hard peak VRAM limit. Theme invalidation
runs before the retirement drain, so it frees old templates at that same boundary.
Headless pool tests cover reuse, LRU ordering, byte/count limits, fractional and
colour identity, deliberate hash collisions, explicit invalidation, oversized
entries and delayed disposal.

Validation:

- `python tools/parity.py`: all three builds green. 26.1.2: 1,227 tests;
  26.2: 1,227; 26.3: 1,229. Each has zero failures/errors and one existing optional
  persistence stress test skipped (1,226 / 1,226 / 1,228 executed successfully).
- `python -m unittest discover -s tools`: 43 passed.
- `git diff --check`: clean. Sources and documentation use LF.

No dimensions, layout, tokens, ramps, shadow recipes, focus or input semantics
change. The parent should measure both the session's first bake and repeated
screen opens, and check retained backing through the existing UI cache probe.
