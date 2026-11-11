# Aurora Client architecture

The dependency direction is version adapter -> core -> ports. Ports have no library
dependencies. Core has no Minecraft, Fabric, LWJGL, Mixin or Log4j on its compile
classpath. See [ADR 0003](adr/0003-core-ports-adapter-layering.md).

| Location | Responsibility |
|---|---|
| `ports/src/main/java` | `DrawSurface`, `TextMetrics`, `ClockPort`, `InputPort`, `WorldPort` |
| `core/src/main/java` | Config/profile persistence and migration; feature/fair-play catalog; theme/layout/shape coverage; health and diagnostics policies; frame pacing; movement cadence; key edges; world identity |
| `adapters/minecraft-26.3/src/client/java` | Minecraft screens, HUD/world rendering, mixins, glass, native input and Fabric lifecycle bindings |
| `adapters/minecraft-26.3/src/client/resources` | Fabric/mixin metadata, defaults, fonts, textures and native-pipeline shaders |
| `src/test/java` | Behavior and source-contract tests across all three layers |

The root Gradle project builds the adapter selected by `minecraft_version` and
packages core and ports classes into one runtime JAR. Players install only that
JAR and its declared Fabric dependencies. Each Minecraft version has its own
artifact; older checkpoints are built from their version branches.

## Native bindings

`NativePorts.install()` runs first in the client initializer. It binds the native
SDL clock, physical key polling and current-world identity. The clock must use
the same epoch as vanilla's frame deadlines and must not change during play.
Input and world queries run on the client thread. No Minecraft object crosses
these interfaces or is retained by core.

`RenderUtil` owns native transforms and fill accounting. It sends physical-pixel
rectangles to core's `ShapeRasterizer` through `DrawSurface`. Both live GUI and
static-layer-cache destinations use the same coverage math. Text layout measures
through `TextMetrics` using the active native font. Shader-drawn shapes remain
roadmap step 6.

## Feature and failure contracts

`FeatureCatalog` is the single feature/mixin/fair-play inventory. Adapter hooks
read current config fields; health enforcement holds broken features off for the
session without overwriting saved values. Mixins remain fail-soft and accessors
are guarded. See [FAIR_PLAY](FAIR_PLAY.md) and [ADR 0004](adr/0004-fail-soft-mixins-and-single-feature-registry.md).

Persistence uses ordered atomic writes. Profiles reset absent scoped fields to
defaults and run migrations independently. Every timed callback contributes to
the unchanged [overhead budget](OVERHEAD_BUDGET.md).

## Working on the code

Use `./gradlew test build`. Source-contract tests use `SourceTree`, and Python
tools use `tools/source_tree.py`; neither should assume the old `src/client`
location. New native hooks belong in the active adapter. New policy belongs in
core when it can be expressed without native objects. Add a port only when a
real caller needs it, and test behavior through that interface.

The earlier UI investigations and acceptance history are preserved in
[the architecture archive](history/ARCHITECTURE-before-adapters-2026-11-11.md).
