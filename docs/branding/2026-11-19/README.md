# Aurora mark and app icon rework — 2026-11-19

Owner request: a larger mark and app icon, rounder at the points so small sizes
keep their shape, with gem-like shading similar to Nova Studio's icon.

## What changed

- **Shape.** The ribbon keeps the original logo's two edge curves (the rising
  crest, the trough and the lagging lower edge), thickened 16%. Instead of
  tapering to needle points, it is cut where it narrows to 46 units and closed
  with round caps, so 16-32 px icons keep a readable shape.
- **Shading.** Flat triangular facets cut between the edges and a central ridge,
  after Nova Studio's starburst: the upper half in Aurora's teal band, the lower
  half in its violet band, alternating between catching and losing the light.
- **Size.** The mark fills 78% of the app-icon tile's width (it filled about 60%),
  and `aurora.svg`'s viewport is now fitted to the mark.
- **Source.** `tools/gen_aurora_brand.py` generates `branding/aurora.svg`,
  `branding/aurora-icon.svg` (the dark Spxctra tile with the mark), the 1024 px
  `icon.png` and, through `tools/prepare_brand.py`, `textures/ui/aurora_mark.png`.
  `prepare_brand.py` now writes to the shared adapter, where the brand assets
  live (it resolved to the 26.3 adapter after the multi-version split).

| Before | After |
| --- | --- |
| ![Before: mark, icon, Nova Studio](before.png) | ![After: icon, title mark and 16/24/32/48/64 px icons](after.png) |

[Title screen, 26.3, 1600x900](title-after.png).

The Aurora Launcher and website carry their own copies of the previous mark and
icon (the 2026-11-02 files were identical to the launcher's); they are unchanged here.
