# Step 8, PR 3: textures (2026-11-12)

Branch `feat/perf-atlas`, stacked on `feat/perf-cpu` ([ADR 0009](../adr/0009-client-optimizations-aurora-owns.md)).

## Free Sprite Mipmaps (shipped)

| Gate | UI | Mixins (shared) |
|---|---|---|
| `perfFreeSpriteMipmaps` | config only | `SpriteContentsMipsAccessor`, `TextureAtlasFreeMipsMixin` |

- **What it frees.** Every sprite keeps CPU copies of its generated mip levels, but only `TextureAtlas.upload` reads them: the first frame is written to the atlas, and animated sprites copy all their frames into GPU textures in `createAnimationState`, also inside `upload`. This holds on all three lines. After `upload` returns, levels above 0 are closed and dropped.
- **What stays.** Level 0 is the original image, read by `isTransparent`/`computeTransparency`, and is kept.
- **Why it is safe across reloads.** Each atlas loads its own `SpriteContents` on every reload (`SpriteLoader.loadAndStitch`; `MissingTextureAtlasSprite.create()` makes a new one each call), so nothing freed is uploaded again.

Evidence (probe `sprite-mips`, which sums the CPU bytes held above level 0 over every loaded atlas):

| Run | Sprites | Held above level 0 |
|---|---|---|
| 26.3 vanilla textures, mixin disabled | 3282 | 832 KiB |
| 26.3, mixin applied | 3282 | 0 KiB (before and after an F3+T-style reload) |
| 26.2 OpenGL and Vulkan, 26.1.2 OpenGL, 26.3 Vulkan | 3773 / 3600 / 3282 | 0 KiB |

- The saving scales with texture area: vanilla's 16×16 textures free 832 KiB, a 64× pack about 13 MiB, and a 512× pack several hundred MiB.
- Animated textures still animate: two inventory screenshots 1.5 s apart show the same 27 of 36 sprite-animated slots changing on every line and backend.
- Terrain mipmaps look normal on OpenGL and Vulkan after a reload.
- With matrix set A on 26.3 (Sodium 0.9.2, Lithium, ImmediatelyFast, EntityCulling), mips were freed and animations ran (27 of 36 slots, GUI Item Baking disabled for the isolation run).
- Every boot applied all of its mixins (93 on 26.3 and 26.1.2, 92 on 26.2) with no Aurora warning or error.

## Animated Sprite Order (measured and dropped)

The idea was to order each atlas's animated sprites so interpolated ones draw together and each pass binds each pipeline once. JFR recordings on 26.3 put all of `TextureAtlas.cycleAnimationFrames` at 0.1% of render-thread samples, both in the normal scene and in the 2048-chest stress scene. Reordering can at most save part of that, so it is not shipped.
