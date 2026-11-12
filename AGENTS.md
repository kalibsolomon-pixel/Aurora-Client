# Aurora Client: contributor and agent guide

Aurora (`com.aurora.client`, mod id `aurora`) is a client-side Fabric mod for Minecraft 26.1.2, 26.2 and 26.3: 38 quality-of-life, PvP and performance features (listed in `docs/FAIR_PLAY.md`) inside a themed GUI with a glass material. MIT licensed; code, fonts and libraries from other projects are listed in `THIRD_PARTY_NOTICES.md`.

This guide is short on purpose. The full engineering history it replaced, including every investigation and the reasoning behind each rule below, is in [docs/history/AGENTS-2026-11-11.md](docs/history/AGENTS-2026-11-11.md). Older code comments and reports that cite "AGENTS.md §N" mean a section of that file.

## Direction

- Decisions are ADRs in [docs/adr/](docs/adr/). Read the relevant one before proposing multi-version builds, an own UI renderer, vanilla rendering optimizations, telemetry or changes to feature defaults.
- The order of work is [docs/ROADMAP.md](docs/ROADMAP.md); where it stands is [docs/STATUS.md](docs/STATUS.md).
- The 26.3 port shipped as 3.3.0. Steps 5 and 5.5 are complete; all supported adapters build from one tree. Step 6, shader-drawn shapes, is implemented on `feat/shader-drawn-shapes` and awaits review.

## Build, test, run

- Java 25 target, Gradle wrapper, Fabric Loom. Shared versions are in `gradle.properties`; supported lines and Fabric API pins are in `gradle/minecraft-versions.json`.
- `python tools/parity.py` tests/builds every supported line from one tree, reusing core/ports. `./gradlew test build -Paurora.minecraft=26.2` selects one line (default 26.3), writing `build/minecraft-<minecraft>/libs/aurora-<version>-mc<minecraft>.jar`. Quote the `-P...` argument in PowerShell. `withSourcesJar()` stays disabled: a sources jar in `mods/` crashes Mixin at launch.
- `./gradlew runClient` starts a dev client in `run/`. `run/` is the owner's live dev config and is not tracked; back up `run/config/aurora.json`, `run/config/profiles/` and `run/options.txt` before a test boot and restore them after. `--args='--quickPlaySingleplayer "New World"'` joins the saved world.
- For ports, prefer a disposable copy of the world/config: `./gradlew runClient -Paurora.runDir=build/<fixture>/game`. This Loom property selects the actual game directory; overriding the task's working directory can be superseded by Loom. On Windows, use a Gradle init script to pass quick-play arguments containing spaces (the matrix runner does this). Test native backends with `--graphicsBackend OPENGL` / `VULKAN` in JavaExec's arguments.
- On the owner's workstation, keep test instances on the second, portrait monitor.
- Useful launch options (pass with `JAVA_TOOL_OPTIONS`): `-Daurora.diagnostics.log=true` writes the diagnostics report to the log after joining a world, `-Daurora.diagnostics.runs=N` repeats it, `-Daurora.mixin.disable=A,B` switches mixins off.
- Checkout is LF on every platform (`.gitattributes`); source-contract tests match multi-line snippets with `\n`.
- Scripts regenerate checked-in files: `tools/gen_fair_play_doc.py` (FAIR_PLAY.md), `tools/gen_third_party_notices.py` (the notices and the font license). Tests fail when a generated file is stale. The icon font is the full Material Symbols Rounded set (a static instance, ligatures dropped) with its `.codepoints` name list; `core/.../screen/IconCatalog` reads the names.

## Layout

| Where | What |
|---|---|
| `ports/src/main/java` | Native-free draw, text, clock, input and world interfaces |
| `core/src/main/java` | Gradle module with nothing from Minecraft, Fabric, LWJGL, Mixin or Log4j on its classpath (ADR 0003): config and profiles, the feature catalog, health records, the theme engine, diagnostics, launcher bridge, Modrinth records, pure layout and map math |
| `adapters/shared/src/client` | Common Minecraft-facing behavior and assets, including the feature registry, most UI, and default export |
| `adapters/minecraft-26.1`, `minecraft-26.2`, `minecraft-26.3` | Version-specific renderers, mixins, native bindings, manifests and shaders; 26.1 pins 26.1.2 |
| `src/test/java`, `adapters/minecraft-*/src/test/java` | Common behavior/source contracts plus native shader and narration fixtures |
| `docs/` | Living references; `docs/reports/` dated reports and audits; `docs/releases/` release notes and evidence; `docs/history/` the old guide |

Package names span core, ports, shared adapter and selected native adapter, so search all of them. Duplicate shared/native source or resource paths fail the build. `tools/core_candidates.py` lists client classes that could move to the core.

## Rules

### Config and features

- `AuroraConfig`'s public fields are the config schema. A feature's on/off state is a boolean field there, read fresh each tick, never a flag on a feature object.
- The effective default is the field initializer overlaid by `assets/aurora/defaults/3.2.0.json`. Change both, and read the resource before assuming a default.
- A new field that changes behaviour for existing installs needs a migrator (pattern: `ThemeMigrator`, `PlayerHealthMigrator`), run on `aurora.json` and on every profile applied. Profiles snapshot every profile-scoped field and reset absent ones to defaults.
- Per-feature reset is `AuroraConfig.resetByPrefix`; field names must share the feature's camelCase prefix or list their prefixes explicitly.
- Saves go through `AtomicJsonWriter` (async, atomic, ordered). Never block the tick thread on disk.
- Keyboard bindings store SDL scancode identifiers on every line. `NativeKeyboard`/`NativePorts` translate on GLFW hosts. `KeybindMigrator` converts legacy GLFW values independently for config and imported profiles; keep its profile-scoped format marker. The core stores literals and never imports SDL.
- Binding an Aurora key by editing `aurora.json` alone does not stick: the vanilla binding in `options.txt` must change too.
- `feature/FeatureCatalog` is the one list of features and of the mixins each needs. Adding a feature: a catalog entry, a `FeatureRegistry` entry and a mixin binding for each new mixin. `FeatureCatalogTest` checks they agree.

### Mixins (ADR 0004)

- `aurora.mixins.json` is fail-soft: a mixin that cannot apply turns its feature off for the session and says so; the game still starts.
- Anything a mixin does must be gated by a config field listed as its gate, or be harmless alone, because a mixin can partly apply.
- A mixin-added interface may be absent: use `x instanceof TheAccessor a`, never a bare cast. Static accessors need a `MixinHealth.failed("Name")` check first.

### Fair play (ADR 0007)

- Every feature has a fair-play class in the catalog. Anything that shows something about other players, or a view vanilla does not allow, is an information advantage and ships off. Regenerate `docs/FAIR_PLAY.md`.
- No custom packets, plugin channels, tick-rate changes or new outbound sends. `FeatureCatalogTest` scans for them.
- Better Hitreg's timing core (`hitreg/`, `mixin/hitreg/`) is Jass's code kept verbatim. Read `hitreg/BetterHitreg.java` before touching it.

### Performance (ADR 0005)

- Register every HUD, world-render and tick callback through `diagnostics/Timed` so its time counts toward the overhead budget.
- The budget: Aurora CPU per frame at most 0.25 ms mean and 0.40 ms p95, no glass and at most 100 UI fills per frame, in a world with factory defaults. `tools/overhead_check.py` is a release step. See [docs/OVERHEAD_BUDGET.md](docs/OVERHEAD_BUDGET.md).
- Before calling a UI or feature change complete, check whether it could scale badly: per-frame allocations, widget-cache reconciliation, anything O(n²) on a list that can grow. Reconcile against a `Set`, fetch lists once per frame, memoize text fitting.
- Count shape-fill volume, not only complexity: 1.21.11 records each `GuiGraphics.fill` against everything recorded before it, so tens of thousands of fills per frame cost frame rate. Static per-row shapes belong in a template raster (`UiLayerCache.blitAt`). Measure with `RenderUtil.fillsSubmitted()`.

### Glass and rendering

The full conventions and their history are §6 of the old guide; violating them has caused real bugs.

1. Background Opacity exists only as the alpha of the `WINDOW_FILL` token. Never add a second opacity factor.
2. Windows and containers are depressed; interactive controls are raised.
3. Neutral glass tints with `WINDOW_FILL`; accent-stained glass only for selected or primary elements. Content on a stained surface uses `ON_ACCENT`.
4. Toggles, sliders, non-search text fields and colour-picker surfaces are always opaque, never glass.
5. With no world loaded, glass declines and the caller draws its flat fallback, unless the screen declared a menu backdrop this frame (`GlassSurface.renderMenuPanorama`).
6. Every glass surface paints in the screen's glass pass, before `GlassSurface.overlayDim`; content paints after. The guard reports violations: read the report's stack, don't count reports.
7. Each panel samples only its own sub-rect of the blur chain.
8. Screenshots (F2) suppress glass for 400 ms. Glass uses Blaze3D on both OpenGL and native Vulkan; the backend owns readback and native state.
9. Focus and hover are carried by caret, text colour, focus ring or scale, never by changing the tint.
10. Never destroy a texture mid-frame: `GuiGraphics` blits are deferred, so queue evictions and free them in `beginFrame()`.
11. Drain the GL error queue before polling `glGetError`.
12. Repeated glass names its `Priority` (`ROW`, `DETAIL`); unnamed is `CONTROL`.

### UI text and layout

- **Overflow review is mandatory (owner instruction, 2026-10-06).** Fitted text stays centred where it was centred. Wrap only between whole words; never split a word or change alignment to make it fit. Measure with the active font and the real column. If one word cannot fit, use a bounded ellipsis. Check long labels at small and large viewports and both grid layouts.
- Theme new screens through `ThemedScreen`, `ui/component` widgets and theme tokens; production appearance is Dark only.

### Assets and verification

- An asset is unreferenced only if its namespace:path string appears in no source set, including paths assembled from constants. Deleting a texture is a visual regression no compile check catches: boot the game and look.
- A visual fix verified at one theme setting has verified nothing about the others. Sweep at least the live value and the factory default of any token the fix depends on (opacity, mode).
- Changes to defaults, mixins or rendering need a dev-client boot, not only tests. Say which settings and screens a verification covered.

## Workflow

- Work on a branch and open a PR. The owner handles merges unless explicitly delegated; keep release verification and immutable-release safeguards. Stack dependent branches and say so in the PR.
- When a change alters how something works, update the doc that describes it in the same change: the ADR, `docs/ROADMAP.md`, `docs/STATUS.md`, or the relevant reference in `docs/`.
- Reference documents: [ARCHITECTURE](docs/ARCHITECTURE.md), [DESIGN_LANGUAGE](docs/DESIGN_LANGUAGE.md), [RELEASE_PROCESS](docs/RELEASE_PROCESS.md), [FAIR_PLAY](docs/FAIR_PLAY.md), [OVERHEAD_BUDGET](docs/OVERHEAD_BUDGET.md), [MOD_MATRIX](docs/MOD_MATRIX.md), [LAUNCHER_ACTIVITY_PROTOCOL](docs/LAUNCHER_ACTIVITY_PROTOCOL.md), [HITREG_INTEGRATION_CATALOG](docs/HITREG_INTEGRATION_CATALOG.md).
