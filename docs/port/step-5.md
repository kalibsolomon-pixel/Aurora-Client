# Step 5: core, ports and Minecraft 26.3 adapter

Validated 2026-11-11 on `refactor/version-adapter`, Aurora 3.3.0.

## Ownership and behavior

`ports` defines five JDK-only interfaces used by core: physical-pixel draw
coverage, text metrics, frame-deadline clock, physical key state and world
identity. Core contains their native-free policies. All Minecraft/Fabric code,
mixins, glass shaders and client resources belong to `adapters/minecraft-26.3`.
The root Loom project remains the build facade for one active adapter. Its JAR
bundles both pure modules. Older checkpoint branches build separate artifacts.

The shape math still feeds Minecraft's GUI pipeline and existing raster caches.
No UI controls or decorative treatments were added. The settings screen's job
remains finding a feature and opening its controls. Shape coverage, text
alignment, input edges, pacing and world scope retain their existing contracts.

Native entity sampling now reuses bounded position records. The pure movement
cadence calculation uses a fixed 32-sample window and updates its median once
per frame only when samples changed. Frame easing, eligibility, teleport/gap
filters and world resets are retained. A full-frustum culling experiment did
not demonstrate a reliable gain and is excluded from the candidate.

The scripted diagnostics trigger now waits until a world has no screen open,
so loading screens cannot consume the prescribed warm-up runs. Budget limits
and the two-warm-up rule are unchanged. Pure-module dependency checks run in
the owning Gradle project, avoiding cross-project resolution during parallel
execution.

## Tests and native acceptance

- Java: 661 discovered, 660 passed, one opt-in stress test skipped.
- Python release/default tooling: 34 passed.
- `gradlew test build` passes, including module dependency checks and GPU shader
  contracts. Generated notices and fair-play docs are current.
- Five applicable matrix boots produced six reports each, applied all 73
  mixins, reported all features working, logged no Aurora warnings/errors and
  closed normally. No temporary probe was loaded for these measurements.

Windows 11, Java 25, RX 9070 XT, Fabric Loader 0.20.1, Fabric API 0.166.0+26.3;
disposable copy of New World, 854x480, GUI scale 2, 120 fps cap, render distance
32, factory Aurora feature settings. The fixture retains a user zoom binding;
it changes no measured workload. Test windows were on the secondary portrait
monitor. Pins are in `tools/matrix/pins-26.3.json` and downloads are SHA-512
verified against Modrinth. VSync is enabled in saved options; the native backend
and configured pacing policy determine presentation.

All values below describe runs 3-6, after exactly two warm-ups. Each judged
run has zero UI fills and glass captures. Every row passes `overhead_check.py`.

| Backend | Set | CPU mean (ms) | CPU p95 (ms) |
|---|---|---|---|
| OPENGL | A-performance-stack | 0.131-0.148 | 0.166-0.212 |
| OPENGL | B-sodium-iris | 0.144-0.150 | 0.187-0.218 |
| OPENGL | baseline | 0.141-0.154 | 0.175-0.219 |
| VULKAN | A-performance-stack | 0.144-0.163 | 0.188-0.234 |
| VULKAN | baseline | 0.157-0.170 | 0.196-0.223 |

[Per-run results and original log hashes](../releases/evidence/3.3.0-overhead.json)
retain the warm-up results too. Full local logs are under ignored
`build/step5/cadence-opengl/` and `cadence-vulkan/`. Earlier failed port gates
remain in [MOD_MATRIX](../MOD_MATRIX.md) as historical evidence.

Iris is an OpenGL-only shader mod, so it is intentionally excluded from Vulkan
rows. Beryl would need its own compatible pinned build and shader-pack evidence;
no Vulkan shader-pack support is claimed here.

## Visual acceptance

Inspected grid and list at 854x480 and 1280x900 on both native backends. Covered
Dark, factory ROUND/33% and live SQUARE/19% appearance, plus no-world title and
menu settings after disconnect. Rounded/square coverage, centered labels,
icons, borders, glass and flat controls render as expected. No glass-layering
guard violations were logged. No assets were removed: the source/resource
moves retain the complete inventory. Shader-drawn shapes remain step 6.

After screenshots are retained locally as `build/port-26.3/step5-*.png`; the
initial port's comparison images are alongside them. The temporary probe and
all screenshots are excluded from the runtime artifact. Keyboard/menu edge and
native clock contracts have behavior tests; the initial port's physical input
and binding checks are documented in [26.3.md](26.3.md). This extraction does
not claim a new exhaustive accessibility audit or testing every feature state.

## Release boundary and limits

This candidate is locally accepted; integration and immutable publication are
separate steps. Multiplayer, shader packs, a production launcher, low-end
hardware and every mod/screen combination remain unverified. The owner
explicitly authorized GitHub integration and release work. Checkpoint 26.1.2
and 26.2 artifacts come from their own historical source plus packaging and
diagnostics-trigger fixes; they do not include this adapter extraction.
