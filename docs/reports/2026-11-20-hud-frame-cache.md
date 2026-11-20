# HUD frame-cache prototype (2026-11-20)

A delegated agent built a disposable 26.2 prototype and this source inventory on 2026-11-20. The prototype was not kept: no code from it is in Aurora. It wrapped two contiguous extraction groups, rendered each group into a transparent RGBA8 TextureTarget, and composited the result every frame with nearest filtering. The prototype was gated by temporary fields in the probe only. No production config, mixin, feature-catalog or manifest entry remains.

## Decision

Stop before shipping. The OpenGL A/B run passed the useful threshold in the owner's 26.2 mod set, but the prototype does not yet meet the required acceptance matrix. Native Vulkan did not complete in this disposable boot, and 26.1.2/26.3 have not had an in-boot refresh and pixel-diff run. The measured images show the compositing path can stay within the requested 1/255 channel tolerance for the exercised group, but this is insufficient evidence for all pipelines and backends.

## Source inventory

The client-only sources were read from the canonical Loom cache for 26.1.2, 26.2 and 26.3. Each line defines the same relevant states.

| Pipeline | Blend state | Cache decision |
|---|---|---|
| GUI, GUI_TEXTURED, GUI_TEXT, grayscale GUI text | SRC_ALPHA, ONE_MINUS_SRC_ALPHA, alpha ONE, ONE_MINUS_SRC_ALPHA | Cacheable only in a contiguous below/above-world-independent group |
| GUI_TEXTURED_PREMULTIPLIED_ALPHA | ONE, ONE_MINUS_SRC_ALPHA, alpha ONE, ONE_MINUS_SRC_ALPHA | Cacheable with premultiplied accumulation |
| GUI_INVERT, CROSSHAIR | color ONE_MINUS_DST_COLOR, ONE_MINUS_SRC_COLOR | Live. It depends on the world/HUD destination |
| VIGNETTE | color ZERO, ONE_MINUS_SRC_COLOR | Live. It modulates the world |
| block/fire screen effects and camera overlays | textured translucent or additive variants | Live. They blend with or cover the scene |
| GUI_NAUSEA_OVERLAY | additive | Live unless a separate scene-composited pass is retained |
| opaque GUI textures | no blend | Cacheable when they are in the selected contiguous group |
| Mojang logo and special overlay states | additive or overlay variants | Live for menu/panorama ordering |
| picture-in-picture render states | their own renderer and texture/depth work | Live |
| Aurora glass/blur | reads the main target and records a capture | Live |

The Fabric HUD registry and Aurora's HudRenderer run inside vanilla Hud.extractRenderState. The vanilla order is camera overlays, crosshair, hotbar/decorations, effects and bosses, then messages, title, chat, tab and subtitles. Aurora's registry layers are appended by the HUD renderer. The cache therefore needs split groups around live overlays and must never cache the crosshair, vignette, scene overlays, glass, PiP or Iris post-composite work.

## Blend math

For ordinary straight-alpha source-over, direct drawing computes

C' = Cs * As + Cd * (1 - As) and A' = As + Ad * (1 - As).

The cache stores premultiplied color P = C * A and alpha A, accumulating

P' = Ps + Pd * (1 - As) and A' = As + Ad * (1 - As).

The composite over the world uses ONE, ONE_MINUS_SRC_ALPHA for color and ONE, ONE_MINUS_SRC_ALPHA for alpha. This reproduces the direct source-over sequence, subject to RGBA8 rounding. Straight-alpha compositing of a transparent cache would multiply alpha twice. Inverted blending cannot be represented by a transparent cached source because its result uses the destination color. The prototype's refresh-frame image pair reached max absolute differences of 1 in RGB and 0 in alpha for the exercised HUD group. A black-to-blue fallback pair from the first stale fixture was discarded and is not evidence.

## Freshness contract for a production version

A refresh is immediate on window or GUI-scale resize, screen open or close, F1, health/hunger/armor/hotbar-slot or item changes, chat insertion, titles, damage, and Aurora HUD-editor edits. A timer refreshes unchanged cacheable state at the configured cap, with a default candidate of 30 Hz. Live scene-dependent layers update every frame. A refresh is also required when Fabric HUD elements or a mod reports a changed layer identity. The capped path would otherwise visibly lag a changed item, title or damage flash.

## Measurements

Owner 26.2 instance, Sodium and ImmediatelyFast, 1440x2560 framebuffer, GUI scale 4, OpenGL, uncapped probe benchmark. ABBA sequence after warm-up:

| State | Median frame | P95 | Aurora CPU mean |
|---|---:|---:|---:|
| off | 0.3839 ms | 0.6211 ms | 0.02730 ms |
| on | 0.3537 ms | 0.5720 ms | 0.02119 ms |
| on | 0.3747 ms | 0.6015 ms | 0.02385 ms |
| off | 0.3757 ms | 0.6242 ms | 0.02850 ms |
| off | 0.3664 ms | 0.6130 ms | 0.02724 ms |
| on | 0.3487 ms | 0.5606 ms | 0.02285 ms |

The paired on/off medians are about 5.8% lower on the first pair and 4.8% lower on the second. Aurora CPU is about 20% lower in the same samples. This is a prototype benchmark, not release evidence. No trustworthy JFR share was retained because the first JFR file was zero bytes after the disposable boot was closed.

The native Vulkan boot did not reach a completed benchmark or screenshot pair. No 26.1.2 or 26.3 in-boot result was claimed.

## Compatibility review

ImmediatelyFast's MixinGuiRenderer owns animated item atlas preparation, so a production cache must leave animated and oversized items live or coordinate through its accessor. Iris wraps GUI rendering and suppresses vignette under shader packs. The cache must be inserted below Iris's GUI wrapper and leave Iris-owned post-composite work live. Sodium has no HUD extraction owner in the inspected mod set. Fabric's HUD element registry and Simple Voice Chat HUD icons remain live unless their layer is explicitly declared cache-safe. Aurora glass/blur and any scene-reading feature remain live. Every production mixin would need the perfHudFrameCache gate, fail-soft MixinHealth behavior, matching catalog and aurora.mixins.json order on all three adapters, plus yieldsTo only for an actual owner.

The remaining risks are backend-specific render-target orientation and synchronization, layer-order changes from other mods, animated textures, title/chat timing, and cache memory at high framebuffer sizes. These require a new prototype pass before any production field or settings row is added.

