# SDF text with ImmediatelyFast (2026-11-20)

3.6.0 stood SDF text down for ImmediatelyFast, because SDF text came out garbled alongside it ([ADR 0010](../adr/0010-opt-in-sdf-text-with-vanilla-layout.md)). Both of the owner's instances run ImmediatelyFast, so they got the bitmap fallback. This pass found the cause, fixed it and removed the stand-down. It also removed a second stand-down, deferred font preparation for ModernFix, which turned out to be a false positive.

## Root cause

ImmediatelyFast's `font_atlas_resizing` option (`MixinFontTexture`, on by default, `font_atlas_size` 1024) applies `@ModifyConstant(method = "*")` to every `256` and `256.0F` in vanilla's `FontTexture`. That covers the texture size, the packing tree and the texture coordinates of vanilla glyphs. The same code is in 1.16.5 (26.2) and 1.17.1 (26.3). Each page records the flag when it is constructed.

Aurora's SDF pages are vanilla `FontTexture`s (`NativeSdfText.createPage`), so they also became 1024 × 1024, and glyphs were packed anywhere up to 1024. `SdfInstance` still divided cell positions by `SdfAtlas.SIZE` (256). Every SDF quad therefore sampled a region four times too far out, clamped at 1: mostly empty texels plus fragments of other glyphs. In All Text, vanilla screens draw with Aurora's fonts, which is why the 3.6.0 matrix reported vanilla text as garbled.

ImmediatelyFast's other options do not touch GUI text: its batching, translucency-sorting and text-lookup mixins act on world rendering, and `batch_animated_item_updates` acts on GUI items.

**Experiment** (26.2 OpenGL, owner's mods, stand-down lifted, code before the fix, factory settings):

| ImmediatelyFast | Result |
|---|---|
| All options as the owner has them | Aurora Settings, chat, F3, tooltips and HUD text garbled at GUI scales 2 and 4 |
| The same with only `font_atlas_resizing: false` | All of it correct |

![Before and after, 26.2 OpenGL](evidence/sdf-immediatelyfast/before-after-26.2-opengl.png)

## Fix

- `NativeSdfText.insert` (all three adapters) records the page's texture size (`getTexture().getWidth(0)`, `getHeight(0)`) in each `SdfAtlas.Cell`. `SdfInstance` computes texture coordinates against that size. Nothing reads ImmediatelyFast's config, so a page of any size works, including one built after a reload changes ImmediatelyFast's runtime flag.
- `SdfAtlas.SIZE` is now `MAX_CELL`. It is still vanilla's 256 and is used only as the largest cell `SdfStrike` generates.
- A cell larger than an empty page (an ImmediatelyFast atlas size below 256) keeps that glyph on its exact bitmap. Before, this threw an exception and turned SDF text off for the session.
- The `immediatelyfast` stand-down is removed from the three SDF text mixins. They still yield to Modern UI and Caxton.

## ModernFix

ModernFix 5.27.20's font patches are `compress_unihex_font`. They wrap the unihex provider definition so that the provider loads lazily, and they compact its glyph storage. Its other client mixins that relate to reloading only swap the reload executor (`dedicated_reload_executor`). Nothing in the jar references `FontManager` or `FontSet`, which are the classes Aurora's deferral patches. The `modernfix` stand-down on `FontManagerDeferredMixin` and `FontSetDeferredMixin` is removed.

All seven boots after the fix logged `fonts pending=146` before first use (deferral active) and `font-check faces=170 widthChecksum=4559170219011580468`. That covers four 26.3 boots with ModernFix, one 26.3 boot without it and the 26.2 boots, which have no ModernFix. The 26.3 font specimen with ModernFix shows fallback CJK, Greek and Cyrillic, bold italic, underline and strikethrough correctly.

## Verification

- **Fixtures.** Disposable dev clients with the owner's instance mods: 26.2 `0cb60dca…`, 26.3 `bdbb2f26…`.
  - Left out: Spark, Simple Voice Chat, Crash Assistant, the installed Aurora jar, and Fabric API (Loom provides it).
  - The owner's mod configs were used, including `immediatelyfast.json` (`font_atlas_resizing` true, size 1024).
- **Settings.** Aurora at factory settings (All Text). Then Aurora UI Only with Inter at scale 2.
- **Scene.** A fresh singleplayer world in survival, peaceful, at noon. Client area 1440 × 2560 on the portrait monitor. GUI scales 2 and 4.

| Line and backend | ImmediatelyFast | Other differences |
|---|---|---|
| 26.2 OpenGL | 1.16.5 / removed | — |
| 26.3 OpenGL | 1.17.1 / removed | Iris 1.11.7 with no pack |
| 26.3 native Vulkan | 1.17.1 / removed | Iris left out (it aborts on native Vulkan, see [MOD_MATRIX](../MOD_MATRIX.md)) |

Scenes in every boot, at scales 2 and 4:
- HUD: FPS, keystrokes, potion HUD, hotbar and armor, chat lines, and a sign's text in the world.
- Chat screen.
- F3.
- Tooltip of an enchanted diamond sword in the survival inventory.
- Server list with two unreachable localhost entries.
- Aurora Settings, Features grid.

Aurora UI Only, scale 2: chat, F3, tooltip, server list and Settings. Then the font specimen.

**Result.** Each scene with ImmediatelyFast matches the same scene without it, on every line, backend and scale. Vanilla text keeps the vanilla font under Aurora UI Only. Mixin health was the expected result in every boot:
- 26.2: "All 109 Aurora mixins applied", or "applied or stood down (4 stood down)" with ImmediatelyFast.
- 26.3: the same with 110 mixins.

The four stand-downs are GUI Item Baking, which still yields to ImmediatelyFast's animated item atlas.

![26.3 Vulkan with and without ImmediatelyFast](evidence/sdf-immediatelyfast/with-without-26.3-vulkan.png)

Not covered: 26.1.2 with ImmediatelyFast 1.15.3, which has the same `FontTexture` constants and the same fix but was not booted; multiplayer; a production launcher.

## Separate finding: SDF can stand down early under RRLS (26.3)

One 26.3 boot logged `Couldn't find source for FRAGMENT shader (aurora:core/gui_text_sdf)` and used raster text for that session. That boot had ImmediatelyFast and RRLS but no ModernFix. The fault is unrelated to ImmediatelyFast or ModernFix.

1. Until the Shader Loader reload applies, `RenderSystem.getCompiledPipelineNullable` compiles pipelines through the fallback cache. `GameRenderer.preloadUiShader` built that cache from the vanilla-only startup resources.
2. RRLS lets screens draw during the reload. The first SDF draw can therefore compile Aurora's pipeline before Aurora's shader is visible.
3. `NativeSdfText.pipelineAvailable` treats the resulting null as a permanent failure.

This happened in 1 of the 9 boots in this pass, all of which ran RRLS. A fix would postpone that decision until the shader manager has applied once, for example with a hook on `ShaderManager.apply` / `RenderSystem.setCurrentPipelineCache` on 26.3 and the equivalent on the other lines. That is left for a separate change.

## Tests

- `MixinStandDownTest`:
  - SDF text yields only to Modern UI and Caxton, not to ImmediatelyFast, ModernFix or Sodium.
  - Font deferral no longer yields to ModernFix.
- `SdfAtlasPageSizeSourceContractTest` (new): cells carry their page's texture size, and the texture coordinates divide by it.
- `SdfTextMathTest`: texel centres on a 1024 page.
- The parity probe gains `chat-open`, `multiplayer`, `inventory` and `hover-slot` for these scenes.
