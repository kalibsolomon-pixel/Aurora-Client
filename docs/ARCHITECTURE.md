# Aurora Client architecture

The dependency direction is version adapter -> core -> ports. Ports have no library
dependencies. Core has no Minecraft, Fabric, LWJGL, Mixin or Log4j on its compile
classpath. See [ADR 0003](adr/0003-core-ports-adapter-layering.md).

| Location | Responsibility |
|---|---|
| `ports/src/main/java` | `DrawSurface`, `TextMetrics`, `ClockPort`, `InputPort`, `WorldPort` |
| `core/src/main/java` | Config/profile persistence and migration; feature/fair-play catalog; theme/layout/shape coverage; health and diagnostics policies; frame pacing; movement cadence; key edges; world identity |
| `adapters/shared/src/client` | Common Minecraft-facing feature registration, screens, settings, widgets and stable hooks; shared defaults, fonts and textures |
| `adapters/minecraft-26.1`, `minecraft-26.2`, `minecraft-26.3` | Version-owned rendering, mixins, input and Fabric bindings; native manifests and shaders |
| `src/test/java` | Behavior and source-contract tests across all three layers |
| `adapters/minecraft-*/src/test/java` | Native shader and narration fixtures used by the common test suite |

The root Gradle project builds the adapter selected by `-Paurora.minecraft=<version>`
(default `minecraft_version` in `gradle.properties`) and
packages core and ports classes into one runtime JAR. Players install only that
JAR and its declared Fabric dependencies. Each Minecraft version has its own
artifact. [ADR 0008](adr/0008-full-feature-parity-across-supported-versions.md)
requires every supported line to build from this same source tree. Pins and
adapter names live in `gradle/minecraft-versions.json`; outputs are isolated in
`build/minecraft-<version>/`. `python tools/parity.py` builds/tests every line
sequentially, reusing compiled core/ports outputs. Native classpaths never mix.
No source preprocessor or universal runtime JAR is involved.

Shared Minecraft-facing code is still adapter code, compiled against each native
classpath. It cannot move into the Minecraft-free core. Duplicate paths between
shared and selected sources/resources fail the build; there is no override order.

## Native bindings

`NativePorts.install()` runs first in the client initializer. It binds the native
SDL clock on 26.3 or GLFW clock on older lines, key polling and current-world identity. The clock must use
the same epoch as vanilla's frame deadlines and must not change during play.
Input and world queries run on the client thread. No Minecraft object crosses
these interfaces or is retained by core.

`NativeUi` owns screen access/transition and main-target lookup, whose locations
changed between Minecraft lines. `NativeKeyboard` translates host key values at
the controls/rebinding seam. Config and profiles store the same SDL scancode
identifiers on every line; the existing per-config/per-profile migrator upgrades
older GLFW data. Older hosts translate back only for native polling and vanilla
controls. UI input comparisons use that host's `InputConstants`.

`RenderUtil` owns native transforms and fill accounting. It sends physical-pixel
rectangles to core's `ShapeRasterizer` through `DrawSurface`. Both live GUI and
static-layer-cache destinations use the same coverage math. Live rounded
rectangles, outlines, squircles and circles instead go to each adapter's
`NativeShapeRenderer`, which draws one quad through an `aurora:pipeline/ui_shape`
GUI pipeline (ADR 0006). Layer captures, triangles and out-of-range cases stay on
the CPU path, as does the whole session if the shader fails to compile;
`-Daurora.shapes.cpu=true` forces it. Text layout measures through `TextMetrics`
using the active native font.

## Feature and failure contracts

`FeatureCatalog` is the single feature/mixin/fair-play inventory. Each adapter's
`aurora.mixins.json` selects its applicable bindings from that inventory; the
health audit counts those hooks rather than treating another line's hooks as
missing. Unknown or duplicate manifest bindings fail validation. Adapter hooks
read current config fields; health enforcement holds broken features off for the
session without overwriting saved values. Mixins remain fail-soft and accessors
are guarded. See [FAIR_PLAY](FAIR_PLAY.md) and [ADR 0004](adr/0004-fail-soft-mixins-and-single-feature-registry.md).

Persistence uses ordered atomic writes. Profiles reset absent scoped fields to
defaults and run migrations independently. A profile also stores its icon, hotkey
and the game's own key bindings; the core reads and applies those through the
`ControlsBridge` the adapter installs after boot (`VanillaControlsBridge`), so
starting the game never rewrites vanilla bindings. The global
`profilesOwnControls` switch decides whether switching applies key bindings at
all. Profile icons come from the bundled Material Symbols font: `IconCatalog`
(core) names every glyph and `MaterialIconRenderer` keeps an LRU of rasters
whose evictions are freed at the next frame start.

Every search field matches through the core `search/SearchMatcher` (normalized
words, prefix and substring hits, bounded Damerau-Levenshtein typos within the
owner's tolerance); `search/SearchIndex` ranks weighted fields (a feature's
name, its settings' names, its description) and memoizes per query. Text
options resolve in `AuroraFontRenderer`: family, weight and italic pick the
provider (`<family>_<weight>[_italic][_ui]`, built by `tools/build_fonts.py`),
underline and italic flags ride on the style, and the letter case is mapped per
codepoint in the same helpers the font mixin uses for drawing and measuring, so
measured width always equals drawn width. HUD text draws through
`hud/HudTextPainter` for the shadow option. Aurora's TrueType providers are
rasterized at the screen's pixel size (oversample = GUI scale,
`TrueTypeProviderDefinitionMixin`), because Minecraft samples font atlases
with nearest filtering; `AuroraFontQuality` re-loads only those providers and
swaps them into their font sets at the next frame boundary when the GUI scale
changes. Every timed callback contributes to
the unchanged [overhead budget](OVERHEAD_BUDGET.md).

## Working on the code

Use `python tools/parity.py` for all supported lines, or
`./gradlew test build -Paurora.minecraft=26.2` for one. Source-contract tests use `SourceTree`, and Python
tools use `tools/source_tree.py`; neither should assume the old `src/client`
location. `AURORA_MINECRAFT` selects a line for Python source checks. Shared
behavior belongs in `adapters/shared`; genuinely native differences belong in
each version adapter. New policy belongs in
core when it can be expressed without native objects. Add a port only when a
real caller needs it, and test behavior through that interface.

The earlier UI investigations and acceptance history are preserved in
[the architecture archive](history/ARCHITECTURE-before-adapters-2026-11-11.md).
