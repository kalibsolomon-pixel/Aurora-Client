# Aurora Client: plan, roadmap and current status

Written 2026-11-11. This is the handover note for the direction work that started on 2026-11-10. The decisions themselves are in [docs/adr/](adr) and the order of work is in [ROADMAP.md](ROADMAP.md); this file says what was agreed, what has been built, and what comes next.

## What we agreed

Aurora Client is a client-side Fabric mod with combat and quality-of-life features. Thirty-nine decisions came out of the review. They are recorded as seven ADRs:

| ADR | Decision |
|---|---|
| [0001](adr/0001-single-open-mod-that-runs-anywhere.md) | One open-source MIT mod that runs in any launcher. No telemetry, no paid cosmetics. Social is limited to friends and online status, no chat. |
| [0002](adr/0002-version-and-loader-policy.md) | Forward-only. The newest Minecraft version gets all feature work; one previous version is frozen to crash and security fixes. Fabric only for now. Ports step through 26.1, 26.2, then the newest. |
| [0003](adr/0003-core-ports-adapter-layering.md) | Three layers by dependency direction: a Minecraft-free core, small ports, and a per-version adapter that owns every mixin. Ports are extracted only after the first forward port shows where the breaks are. |
| [0004](adr/0004-fail-soft-mixins-and-single-feature-registry.md) | A mixin that cannot apply turns its feature off and reports it; the game still starts. One feature registry. A tested mod matrix. A copy-diagnostics report. |
| [0005](adr/0005-performance-scope-and-overhead-budget.md) | Performance means a numeric budget on Aurora's own per-frame cost, plus pacing and latency. No competing with Sodium. Vanilla optimizations only under three rules. |
| [0006](adr/0006-shader-drawn-shapes-no-own-ui-renderer.md) | UI shapes drawn by a shader through Minecraft's pipeline. No own UI renderer. |
| [0007](adr/0007-fair-play-classification-and-apollo-scope.md) | Every feature is classified for fair play; advantage features ship off. Apollo support is limited to honouring server restrictions. |

Standing rule: **no new features until the port to the newest Minecraft release ships.** Only fixes and roadmap steps 1 to 3.

## Roadmap

| Step | What | State |
|---|---|---|
| 1 | Fail-soft mixins, one feature catalog, `breaks` list, copy-diagnostics report | Built, PR #2 |
| 2 | Fair-play classification, default-off switches | Built, PR #3 |
| 3 | Core Gradle module for the Minecraft-free packages, measure and set the overhead budget, third-party notices, repo cleanup | Built: core module (PR #4), overhead budget (PR #6), notices (PR #7), cleanup (PR #8) |
| 4 | Port 26.1, then 26.2, then 26.3; release 26.3; freeze 1.21.11 | 26.1 checkpoint built (PR #10); 26.2 built (PR #11); 26.3 builds and boots on `port/26.3`, stacked on #11; owner review and release remain |
| 5 | Extract the ports; move mixins, the glass renderer and Fabric API calls into the version adapter | Not started |
| 6 | Shader-drawn shapes behind `RenderUtil` | Not started |
| 7 | Later, in order: Apollo restriction support, friends and status, NeoForge if asked, small vanilla optimizations | Not started |

## Current status

The pull requests below are open and stacked; the 26.3 port on `port/26.3` is stacked on #11. **None is merged: merging is blocked for the assistant, so the owner has to merge them, in this order.**

| PR | Branch | Contents | CI |
|---|---|---|---|
| [#1](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/1) | `chore/branding-and-direction` | Branding, the ADRs and roadmap, and a fix for a real race in profile listing | Passing |
| [#2](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/2) | `feat/fail-soft-foundation` | Roadmap step 1, and LF line endings on checkout | Passing |
| [#3](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/3) | `feat/fair-play-defaults` | Roadmap step 2, and this file | Passing |
| [#4](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/4) | `feat/core-module` | Roadmap step 3, the core module (stacked on #3) | See the PR |
| [#5](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/5) | `feat/fair-play-decisions` | The owner's three fair-play decisions (stacked on #4) | See the PR |
| [#6](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/6) | `feat/overhead-budget` | Roadmap step 3, the overhead budget (stacked on #5) | See the PR |
| [#7](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/7) | `feat/third-party-notices` | Roadmap step 3, third-party notices (stacked on #6) | See the PR |
| [#8](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/8) | `chore/repo-cleanup` | Roadmap step 3, repo cleanup (stacked on #7) | See the PR |
| [#9](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/9) | `chore/pre-port-checks` | Step 1's on-screen check and the tested mod matrix (stacked on #8) | See the PR |
| [#10](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/10) | `port/26.1` | Roadmap step 4: the 26.1 checkpoint (stacked on #9) | Passing |
| [#11](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/11) | `port/26.2` | Roadmap step 4: the 26.2 checkpoint (stacked on #10) | See the PR |
| 26.3 port (draft) | `port/26.3` | Roadmap step 4: SDL and RenderPearl port, stacked on #11; OpenGL overhead blocks release | See the PR |

This file was committed with PR #3 and is updated in PR #4.

### Step 1, in PR #2

- `aurora.mixins.json` is `required: false`. A config plugin and error handler record every Aurora mixin failure; other mods' errors are left alone. An audit at client start settles the rest.
- `feature/FeatureCatalog` is the one list of features and of the mixins each needs. The grid cards and the settings registry take their names from it.
- A broken feature's switches are held off for the session only. Saved config and profile values are untouched.
- Display Settings Ã¢â€ â€™ Support Ã¢â€ â€™ Copy Diagnostics: a five-second frame-time benchmark, then a text report on the clipboard. Nothing is sent anywhere.
- Launch options: `-Daurora.mixin.disable=Name,...` and `-Daurora.diagnostics.log=true`.
- Verified by 627 tests and three real boots into the dev world: unmodified (all 72 mixins applied); ten mixins disabled plus one injection point broken on purpose (game ran, saved settings unchanged); one mixin disabled with the report logged.

### Step 2, in PR #3

- Every feature has a fair-play class and a one-sentence note, published as [FAIR_PLAY.md](FAIR_PLAY.md). The file is generated by `tools/gen_fair_play_doc.py`; a test fails if it differs from the catalog.
- FreeLook and other players' totem pops on nametags now ship off. Minimap player dots and Entity Health were already off. Existing installs keep what they had.
- A test scans every source file for custom packets, plugin channels, tick-rate changes and outbound sends.
- Verified by 634 tests. Nothing in step 2 was checked in a running game.

### Found along the way

- ADR 0007 said Better Hitreg "sends no packets". Two optional, unbound keys do send when pressed (Switch Hand, and the practice scoreboard's Send). The ADR and the document are corrected.
- Other players' totem pops were on nametags by default even with the Totem Pop feature off.
- The real default of a setting is its field initializer overlaid by `assets/aurora/defaults/3.2.0.json`. Better Hitreg ships **off** there; Full Bright and No Fog shipped **on** until PR #5.

### Step 3 so far, in PR #4

- New Gradle module `core/` with nothing from Minecraft, Fabric, LWJGL, Mixin or Log4j on its classpath. Its classes go into the same mod jar.
- 75 classes moved there, each keeping its package: the config and profiles, the feature catalog, mixin and feature health, the theme engine, the launcher bridge, the Modrinth records and pure layout and map math.
- Cut on the way: the config directory is now set by the client at start (`config/ConfigDirectory`), the core logs through SLF4J, and two health constants moved into `MixinHealth`. The default keys are GLFW codes written as literals, checked by a test.
- Source-contract tests now read both source roots. The fair-play packet scan had been walking only the client folder and would have missed the core.
- Verified by the test suite and one dev-client boot into the saved world.

### The owner's decisions, in PR #5

Answered 2026-11-11: yes to all three.

- Full Bright and No Fog now ship off. ADR 0007 is amended and `FAIR_PLAY.md` regenerated. Existing installs keep what they had.
- Entity Health has a new **Show Players** switch, off by default; with it off only mobs get a label. A config or profile saved before the switch existed has no value for it, so it takes the saved Entity Health switch: whoever had Entity Health on keeps seeing players.
- The `breaks` id `betterhitreg` is confirmed; nothing changed.
- Also fixed: three scripts still pointed at the client paths of classes PR #4 moved (`tools/gen_fair_play_doc.py`, `tools/test_release_defaults.py`, `subset_script.py`).
- Verified by the test suite and a boot on the real dev config: the migration ran once for the config and once for the profile, and Full Bright and No Fog stayed on there. Not checked in game: a player label, since singleplayer has no other players.

### Step 3, the overhead budget, in PR #6

- Aurora now measures its own CPU time per frame: every HUD, world-render and tick callback it registers is timed (`diagnostics/Timed`, `diagnostics/AuroraCost`), and waits are not counted. Glass captures are counted too.
- The diagnostics report shows both, with the fills, and says whether the run was within the budget.
- Baseline on this machine with factory defaults: 0.15 to 0.18 ms mean, 0.18 to 0.24 ms p95, no glass, no fills. The owner's config with more features on: about 0.23 ms mean.
- A profiler run found the timers cover about 79% of Aurora's per-frame work; the rest is small mixin hooks. The hitbox renderer is the largest single cost.
- Budget, at about 1.5 times the baseline: 0.25 ms mean, 0.40 ms p95, 0 glass, 100 fills. `tools/overhead_check.py` is now a release step; the final build passed it.
- Every test boot's game window now opens on the second monitor.

### Step 3, third-party notices, in PR #7

- `THIRD_PARTY_NOTICES.md` at the repo root and in the jar: Better Hitreg (Apache 2.0), Freelook++ (MIT), HitColor (MIT), AppleSkin (Unlicense), eight fonts under the SIL Open Font License, Material Symbols (Apache 2.0) and the TwelveMonkeys image libraries (BSD 3-Clause), each with its full license text copied from upstream.
- The font folder's `license.txt` named a font Aurora no longer ships; it now lists the real fonts.
- Found: the TwelveMonkeys jars inside the mod carry no license file, so the jar had no BSD notice until now. HitColor's repository has no LICENSE file; only its Modrinth page says MIT.
- A test fails if a font, a bundled library or a credit in the source is missing from the notices.

### Step 3, repo cleanup, in PR #8

- `AGENTS.md` is now a short contributor and agent guide: direction, build and run, layout, and every standing rule in one or two lines. The 2,700-line history it replaced is kept verbatim in `docs/history/AGENTS-2026-11-11.md`; older "AGENTS.md Ã‚Â§N" citations point there.
- The 40 reports at the repo root moved under `docs/`: five living references to `docs/`, release notes to `docs/releases/`, the rest to `docs/reports/`. Relative links were repaired; the publish workflow now reads release notes from `docs/releases/`.
- The repo root now holds only the build, `README.md`, `AGENTS.md`, `LICENSE` and `THIRD_PARTY_NOTICES.md`.

### Port target

26.3 is the release the port targets (owner, 2026-11-11). ADR 0002 and the roadmap now name it.

### Pre-port checks, in PR #9

- **On screen.** The detail-screen header shows "Not working in this session: zooming the view" for a broken feature, and the Copy Diagnostics run puts the full report on the clipboard (checked through a temporary in-game probe, not by hand). The grid card shows nothing: its note only reaches screen-reader narration, because cards have no hover tooltip.
- **Mod matrix, 1.21.11.** Three boots: Sodium, Lithium, ImmediatelyFast, EntityCulling and Mod Menu; Sodium and Iris; VulkanMod. All pass: every mixin applied, every feature working, no Aurora warnings, normal close. [MOD_MATRIX.md](MOD_MATRIX.md); tooling in `tools/matrix/`.

### Step 4, the 26.1 checkpoint, in PR #10

- Builds against Minecraft 26.1.2 with the unobfuscated Loom, Java 25. All 652 tests pass, all 73 mixins apply, the overhead budget passes, and the 26.1 mod matrix passes.
- Found and fixed on the way: a fail-soft hole (a stale injection point crashed the game at launch instead of turning a feature off); two optional mixins that failed silently; and glass that captured the previous frame, because 26.1 extracts the GUI before rendering it (title cards black, in-world windows dark). Glass now matches 1.21.11.
- Details and what is still unchecked by eye: `docs/port/26.1.md`.

### Step 4, the 26.2 checkpoint

- Builds against Minecraft 26.2, Fabric API 0.161.0, Loom 1.17.21 and Gradle 9.5.1, still Java 25. Final build: 648 tests pass, one skipped; all 72 mixins apply. Both native baselines pass the overhead budget.
- Glass and GPU timing use Minecraft's device API on OpenGL and native Vulkan. World overlays use staged native geometry; block outlines draw after the terrain instead of being overwritten by it. Obsolete glint-ordering hooks are removed.
- Native Vulkan and the Sodium performance stack pass. Sodium + Iris passes on OpenGL; forcing Vulkan with the pinned Iris build aborts in Iris's OpenGL initialization. Full evidence and limits: [port/26.2.md](port/26.2.md), [MOD_MATRIX.md](MOD_MATRIX.md).

## Open decisions for the owner

1. **Should a broken feature show on its grid card?** Today only its detail screen says so. A small marker or a second line on the card would make it visible from the grid; cards are deliberately minimal, so this is a design call.

## Not yet verified

- Apollo's license terms and how servers detect Lunar Client.
- Whether Better Hitreg's Apache-2.0 listing on Modrinth matches the terms its author gave. Its repository's LICENSE is stock Apache 2.0.
- HitColor's license: its repository has no LICENSE file; only Modrinth says MIT.

## Next steps

1. Merge PR #1 through the 26.2 checkpoint in stack order.
2. Answer the open decision above.
3. Step 3 is built:
   - ~~Move the Minecraft-free packages into a core Gradle module.~~ Done in PR #4. `module/` stayed in the client: `Module` reads the settings registry.
   - ~~Measure Aurora's per-frame cost and set the overhead budget as a number.~~ Done in PR #6.
   - ~~Add a third-party notices file.~~ Done in PR #7.
   - ~~Replace the history in `AGENTS.md` and move the root-level reports under `docs/`.~~ Done in PR #8.
4. Step 4, the port: 26.1 is built (PR #10); 26.2 is built on `port/26.2`; next is 26.3 (SDL replaces GLFW: keybind migration). Rerun the mod matrix for each.

### Minecraft 26.3 port

Built on `port/26.3`, stacked on PR #11 (`port/26.2`). SDL keyboard and mouse input, saved binding migration, RenderPearl GPU APIs, native glass, combined glint/OIT and hurt armor are adapted. Both native backends boot with all 73 mixins. See [port/26.3.md](port/26.3.md) for validation and remaining limits. OpenGL baseline/stack overhead still exceeds the release budget, so the 26.3 PR is a draft and publication is blocked. Native Vulkan baseline/stack and OpenGL Sodium + Iris pass their budget checks. Iris fails on forced Vulkan even with Aurora disabled. This branch does not publish a release or lift the feature freeze.
