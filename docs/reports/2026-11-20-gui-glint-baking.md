# GUI Glint Baking (2026-11-20)

A follow-up to the [reference-instance pass](2026-11-20-perf-reference-instance.md), whose largest remaining cost in a PvP loadout was the GUI item atlas: vanilla redraws every glinting item into the atlas every frame.

## What changed

| Optimization | Gate | UI | Mixins (all shared) |
|---|---|---|---|
| GUI Glint Baking | `perfGuiGlintBaking` | config only | `ItemStackRenderStateGlintMixin`, `CuboidItemModelGlintMixin`, `GuiItemAtlasGlintMixin`, `OversizedItemGlintMixin`, `TextureTransformAccessor` |

- **Why it is exact.** A standard glint changes between frames only through its texture transform. The transform is built from `Util.getMillis() * glintSpeed * 8` as a whole number, so it moves at most once a millisecond. Its other inputs are the glint strength, the glint texture and the glint render type (Glint Color). Above 1,000 frames a second, consecutive frames often draw the same pixels.
- **How it works.** The glint's `setAnimated()` call in `CuboidItemModelWrapper.update` marks the item instead of animating it. The atlas keeps a glint-only item's slot like a static item's. `GuiItemAtlasGlintMixin` redraws the slot when the transform (computed by the glint's own supplier), the strength, the Glint Color mode or the glint texture has changed since it was drawn.
  - A drawing is kept only if the transform was the same before and after it.
  - Like vanilla, an item is drawn at most once a frame.
- **Strength 0.** The glint pass changes no pixel: vanilla's glint adds black, Glint Color's masked pass discards, and 26.3's fused item shader adds zero. So a glint-only item at strength 0 is drawn once and kept.
- **Stays as before.** Oversized GUI items and items that are also animated for another reason are redrawn every frame:
  - animated sprites;
  - the special foil of compass-like items;
  - another mod's `setAnimated()`, such as Continuity's emissive items.

  GUI Item Baking now counts a marked glint as every-frame.
- **ImmediatelyFast.** Its `batch_animated_item_updates` takes only items that are still animated. Glint-only items leave that set, so this needs no stand-down: in both owner instances it applies where GUI Item Baking stands down.

## Evidence

The scene for every run:
- Disposable fixtures. 26.2 is the owner's mod set and config, OpenGL. 26.3 is the owner's mod set, native Vulkan, Iris with no pack. 26.1.2 has no mods, OpenGL.
- PvP hotbar and armor HUD: enchanted sword, bow, axe and pickaxe, enchanted golden apple, four enchanted armor pieces. GUI scale 4, uncapped.
- The gate is switched in memory with the parity probe inside one boot.
- Shares are render-thread JFR samples at 1 ms.

The owner's 26.2 config (glint strength 0, Glint Color black masked):

| | `GuiItemAtlas.drawToSlot` | Average fps | Median frame |
|---|---|---|---|
| off | 17.7%, 21.0% | 1,498 / 1,429 / 1,604 | 0.571–0.625 ms |
| on | 0% | 2,200 / 2,284 / 2,066 | 0.409–0.455 ms |

With the glint moving, so only the transform test applies:

| Line | `drawToSlot`, off → on | Average fps, off / on |
|---|---|---|
| 26.2 OpenGL, owner's mods (a build before the strength-0 rule; the owner's strength 0, so the same redraws as a visible glint) | 22.1% → 13.9% | noisy (another build shared the CPU) |
| 26.1.2 OpenGL, no mods, strength 1, speed 0.5 | 20.5% → 13.8% | 2,021 / 2,119 → 2,298 / 2,249 |
| 26.3 Vulkan, owner's mods, strength 1, speed 0.5 | 14.1% → 10.3% | 1,234 / 1,349 → 1,165 / 1,267 (within noise) |

The gain scales with frame rate. Below 1,000 fps nearly every frame has a new millisecond, so the cost is unchanged.

**Pixels.**
- *Static glint* (speed 0, strength 1): the HUD region captured with the gate on and off is byte-identical. Checked on 26.1.2, 26.2 (vanilla glint and Glint Color's masked glint) and 26.3 Vulkan.
- *Moving glint* (speed 0.5): consecutive captures with the gate on change in every glint slot by as much as with it off.
- *Animated items:* sea lantern, magma, prismarine, warped stem and an enchanted magma block keep animating on all three lines. On 26.2 and 26.3 that is with ImmediatelyFast's animated atlas in use; on 26.1.2 it is with GUI Item Baking active.

The mixin health report reads "All 110 Aurora mixins applied" on all three lines (26.2 and 26.3 with their usual stand-downs).

`GlintBakeTrackerTest` covers:
- the redraw decision;
- a drawing across a clock step;
- once-a-frame look-ups;
- a strength change forgetting for two frames;
- a texture reload;
- an unknown transform;
- pruning.
