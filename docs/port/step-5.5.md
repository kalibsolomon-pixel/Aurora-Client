# Step 5.5: one-source feature parity

Validated 2026-11-11 on `refactor/parity-adapters`, Aurora 3.4.0 candidate.
Implements [ADR 0008](../adr/0008-full-feature-parity-across-supported-versions.md).

## Ownership and builds

Minecraft 26.1.2, 26.2 and 26.3 now build from this tree. Core and ports remain
Minecraft-free. The shared adapter contains 188 Java implementations, including
the one 38-feature registry, screens, controls, HUD policies and stable hooks,
plus common defaults, fonts and textures. Native adapters contain 63, 61 and 62
Java files respectively: native renderers, signatures, bindings and shaders.
Shared Minecraft-facing code compiles against each selected native classpath.

`gradle/minecraft-versions.json` owns the supported set, adapter paths and Fabric
API pins. The root Loom facade selects `-Paurora.minecraft=<version>`, defaulting
to 26.3. `python tools/parity.py` tests/builds every line sequentially, reusing
core/ports compilation. Each output is isolated under
`build/minecraft-<version>/libs/aurora-3.4.0-mc<version>.jar`. Duplicate paths
between shared and native sources/resources fail the build. No source
preprocessor, runtime version detection or universal JAR is introduced.

Config and profiles use the same canonical SDL key identifiers on every line.
The existing migrator upgrades legacy GLFW config and imported profiles
independently; native adapters translate only at the input/controls boundary.
The inverse map is constant-time. Extra-key HUD labels use that same boundary.
The catalog selects applicable hooks from each adapter's manifest, so another
line's hooks cannot incorrectly disable a healthy feature.

The older adapters now initialize glass at `renderFrame`, including loading
and disconnect renders outside `runTick`. Native acceptance found the stale
hook through the glass guard's title-screen stack trace; the corrected menu
transitions were rerun on OpenGL, VulkanMod and native Vulkan.

## Automated verification

`python tools/parity.py` passes for all three versions, including ownership,
dependency-layering, generated fair-play/notices and native shader checks.
26.1.2 and 26.2 each discover 660 Java tests: 659 pass, one opt-in stress test
is skipped. 26.3 discovers 662: 661 pass, one skipped. All 42 Python tests pass,
including full-set artifact rejection, per-asset draft validation, stale/missing
GPU evidence and unchanged warm-up/budget rules. GLFW key round-trips cover the
legacy key inventory. Workflow YAML and embedded Python parse successfully.

All 50 original 26.3 resource paths remain present. All 15 binary fonts/textures
match the original bytes. Shared vendored hitreg files are exact moves; the
older native timing/render files match their published checkpoint sources.
Inherited whitespace in those verbatim files is retained.

## Native compatibility and overhead

Windows 11, Java 25.0.4.1, AMD Radeon RX 9070 XT, Fabric Loader 0.20.1. Fabric API
pins are 0.155.3+26.1.2, 0.161.0+26.2 and 0.166.0+26.3. The pinned matrix mods
are SHA-512 verified against their downloaded manifests. Each row boots a
disposable New World copy at 854x480, GUI scale 2, render distance 32, 120 fps
cap, saved VSync on, and factory Aurora config/profiles. Windows stay on the
secondary portrait monitor. The owner's live `run/` is untouched. No acceptance
helper, concurrent build or other GPU test runs during judged measurements.

The required fourteen rows each produce six reports. Exactly the first two are
warm-ups; every remaining run must meet 0.25 ms mean / 0.40 ms p95, at most 100
UI fills and zero glass captures. All applicable native mixins and features
must be healthy, Aurora warnings/errors absent and the exit normal.

| Line | Backend | Set | CPU mean, runs 3–6 | CPU p95, runs 3–6 | Gate |
|---|---|---|---|---|---|
| 26.1.2 | OpenGL | baseline | 0.126–0.138 ms | 0.160–0.198 ms | Pass |
| 26.1.2 | OpenGL | A-performance-stack | 0.107–0.126 ms | 0.150–0.196 ms | Pass |
| 26.1.2 | OpenGL | B-sodium-iris | 0.105–0.117 ms | 0.140–0.187 ms | Pass |
| 26.1.2 | VulkanMod | C-vulkanmod | 0.115–0.128 ms | 0.159–0.194 ms | Pass |
| 26.2 | OpenGL | baseline | 0.128–0.143 ms | 0.162–0.211 ms | Pass |
| 26.2 | OpenGL | A-performance-stack | 0.123–0.139 ms | 0.155–0.195 ms | Pass |
| 26.2 | OpenGL | B-sodium-iris | 0.128–0.146 ms | 0.164–0.212 ms | Pass |
| 26.2 | Vulkan | baseline | 0.126–0.153 ms | 0.156–0.215 ms | Pass |
| 26.2 | Vulkan | A-performance-stack | 0.131–0.174 ms | 0.170–0.210 ms | Pass |
| 26.3 | OpenGL | baseline | 0.125–0.138 ms | 0.159–0.196 ms | Pass |
| 26.3 | OpenGL | A-performance-stack | 0.119–0.138 ms | 0.162–0.206 ms | Pass |
| 26.3 | OpenGL | B-sodium-iris | 0.126–0.139 ms | 0.157–0.197 ms | Pass |
| 26.3 | Vulkan | baseline | 0.139–0.151 ms | 0.189–0.227 ms | Pass |
| 26.3 | Vulkan | A-performance-stack | 0.128–0.143 ms | 0.169–0.206 ms | Pass |

[Per-run results, source-input digest and log hashes](../releases/evidence/3.4.0-parity.json)
are checked by `tools/parity_acceptance.py verify`. Each runner result records
its inputs before boot; recording rejects stale runs. The release workflow
requires that same evidence to match its requested runtime/build/pin inputs.
Any failing supported line blocks the entire release. Each raw log also passes
`tools/overhead_check.py`; logs remain under ignored `build/step5.5/acceptance/`.

A preliminary 26.2 Vulkan performance-stack report exceeded p95 at 0.551 ms,
followed by 0.188 ms. The first complete matrix attempt passed thirteen rows;
26.2 OpenGL's performance stack exceeded p95 at 0.672 and 0.418 ms in judged
runs three and four. Both [failed attempts and their log hashes](../releases/evidence/3.4.0-preliminary-overhead.json)
are retained separately and are not accepted evidence.

A diagnostic 1 ms JFR recording of the latter row did not reproduce the failure:
its four judged windows measured 0.188–0.203 ms mean and 0.268–0.312 ms p95.
Samples show native geometry submission and HUD text work; they do not establish
the cause of the earlier spike. The profiled recording is excluded from
acceptance. Two measured changes followed, with unchanged thresholds, warm-ups
and defaults: fixed HUD slots keep their language-aware text components between
frames (shared `HudTextCache`), and the staged world-overlay GPU pools recycle
once per frame at the frame boundary instead of after each overlay draw, inside
Aurora's timing bracket. The complete matrix above was then run from that exact
source and passes all fourteen rows. These results establish acceptance for the
documented fixture, not a claim that timing variability has been eliminated.

## UI and input acceptance

The settings screen's job remains finding a feature and editing its controls.
This change adds no controls, decorative treatments or layout policy. Native
screens keep the existing theme tokens, centered labels and whole-word fitting.
Per-frame work uses the existing caches and bounded collections; the key map
adds no per-poll allocation. Shape shaders remain step 6.

Inspected native screenshots on all six version/backend combinations: OpenGL
and VulkanMod on 26.1.2, OpenGL and native Vulkan on 26.2/26.3. Coverage includes
854x480 and 1280x900, grid/list, factory Dark ROUND/33% and live SQUARE/19%,
Animations and Theme details, focused long search text, title and no-world menu
settings. Long Container Preview labels remain centered in both layouts at both
sizes; Toggle Sprint/Sneak wraps between words. Flat controls, glass, icons and
window edges render correctly. Final UI logs contain no Aurora warnings/errors
or glass-layering guard violations.

The opt-in [native probe](../../tools/parity/README.md) exercises a real
KeybindSetting capture of F7, checks vanilla/config sync and native C Reset
defaults, then verifies F7 after restart on every line. Extra-key labels are
checked in the native renderer and HUD. The helper and screenshots are excluded
from production JARs and performance fixtures. Screenshots/logs are retained
locally in `build/step5.5/visual/` and `overflow/`; the
[visual/input evidence index](../releases/evidence/3.4.0-visual.json) records their hashes.
These are native-handler checks, not a new OS physical-input or exhaustive
accessibility audit.

Representative native captures: [26.1.2 VulkanMod](evidence/step-5.5-26.1.2.png),
[26.2 Vulkan](evidence/step-5.5-26.2.png), [26.3 Vulkan](evidence/step-5.5-26.3.png).

## Release boundary and limits

CI builds every supported line. Publication builds the complete set from one
exact commit, verifies each artifact through handoff and draft redownload,
then anonymously verifies every public JAR. Missing, extra, duplicate,
wrong-line or mixed-source assets fail closed. Historical schema 2 metadata and
published checkpoint identities remain intact. Completing step 5.5 merges the
source and release infrastructure; it does not publish 3.4.0 or alter old tags.

This acceptance covers the specified hardware and singleplayer fixture. Iris
is OpenGL-only and no shader pack is loaded. Multiplayer, a packaged launcher
boot, low-end hardware, Vulkan shader packs and every feature/mod/screen state
remain outside this acceptance. Step 6 is next in roadmap order.
