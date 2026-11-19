# Aurora mark and app icon rework — 2026-11-19

Owner request: a larger mark and app icon, rounder at the points so small sizes
keep their shape, with gem-like shading similar to Nova Studio's icon.

## What changed

The owner's ribbon is kept: its curves, its teal upper band and violet lower band
with their gradients, the fold and the edge light. (A first, faceted redesign was
rejected; it broke the bands into a checkerboard and lost the flowing shape.)

- **Tips.** The ribbon never gets thinner than 34 units. The original points are
  kept wherever the ribbon is wider than about 120 units; below that, the edges
  ease apart to the minimum and close in round caps. Small icons keep both ends.
- **Shading.** Smooth bevel gradients over each band, no facets: each band is lit
  along its upper side and shaded along its lower side.
- **Size.** The mark fills 78% of the app-icon tile's width (it filled about 60%),
  and `aurora.svg`'s viewport is now fitted to the mark.
- **Source.** `tools/gen_aurora_brand.py` generates `branding/aurora.svg`,
  `branding/aurora-icon.svg` (the dark Spxctra tile with the mark), the 1024 px
  `icon.png` and, through `tools/prepare_brand.py`, `textures/ui/aurora_mark.png`.
  `prepare_brand.py` now writes to the shared adapter, where the brand assets
  live (it resolved to the 26.3 adapter after the multi-version split).

| Before | After |
| --- | --- |
| ![Before: mark, icon, Nova Studio](before.png) | ![After: icon; original and new mark; 16/24/32/48/64 px icons](after.png) |

[Title screen, 26.3, 1600x900](title-after.png).

The Aurora Launcher and website carry their own copies of the previous mark and
icon (the 2026-11-02 files were identical to the launcher's); they are unchanged here.
