# Aurora Client — Unified Mapping System Implementation Report

2026-10-19 · branch `codex/unified-mapping` · local work only

**Status: core implementation and bounded acceptance complete; ready for owner review.**
The owner resumed the interrupted session. Foreground performance comparison,
loopback multiplayer and observed-terrain eviction/persistence checks now pass.
Nothing was pushed, merged, published, or installed into an original instance.

## A. Executive summary

Minimap and world map now share captured, persistent terrain through the existing
region pipeline. Archive browsing no longer replaces live capture. Waypoints have
stable IDs, shared navigation, visibility/groups/descriptions/styles, a common
transactional editor and a searchable, sortable manager.

The changes preserve the primitive RegionCache improvement, capture sampler,
WMR format compatibility, HUD placement and Aurora components. They add bounds
and lifecycle protections rather than replace working terrain storage/rendering
infrastructure wholesale. Frame Pacer, Reflex, JVM/Launcher policy, Hitreg timing
and the glass renderer were not changed.

565 automated tests and the final Java 21 build pass. A native single-player
workflow passed 19 checks; a separate process relaunch passed three persistence
checks. Multiplayer passed seven checks and extended cache pressure passed six,
for 35 native assertions overall. Forty observed pixels were independently checked
against durable region files. Actual screenshots were inspected. A foreground
performance pair found no material regression within its bounded scope; it does
not establish an FPS gain. Multi-hour and broad hardware acceptance remain outside
this evidence.

## B. Architecture

`WorldMapClient` owns a live context and an optional archive context. Each context
owns its region/overview caches, storage and async mailbox. Minecraft world access,
NativeImage changes and GPU operations stay on the client thread; a bounded serial
worker handles disk operations. Requested-tile identity checks reject results for
evicted/recreated tiles; separate mailboxes isolate old worlds and dimensions.

`MapIdentity` uses the authoritative SP save directory or original MP server
address, hashed with SHA-256. Dimension directory names are also hashed. Legacy
WorldScope/playtime identities are unchanged. Display-name changes preserve SP
mapping identity; moving/renaming the save directory creates a different identity.
Unavailable identities do not write into a shared fallback namespace.

Dirty tiles remain resident until their matching revision is saved successfully.
Rapid returns reuse pending contexts. Persistent IO failures back off and retain
bounded contexts instead of silently dropping dirty data. Shutdown permits a
bounded ten-second drain and logs unresolved failures. A versioned dimension
index preserves visited modded dimension IDs across sessions.

`MapTransform`, `MapViewport` and `WaypointQuery` provide coordinate/heading math,
viewport motion and bounded marker selection. Presentations consume this shared
state; they do not maintain competing waypoint lists.

## C. Minimap

Circular/square shape, size, zoom, HUD placement/scale, heading-up/north-up,
player direction, compass, coordinates, optional biome, terrain alpha, waypoints
and entity categories are integrated with existing settings.

Terrain and border share one masked cached texture. Rendering does not query
world columns. Motion refreshes are capped at 20 Hz and unchanged stationary
terrain does not repaint/upload. Entity searches are throttled, skip disabled
categories, and render at most 128 markers. Waypoints use shared visibility and
navigation priority. Minecraft yaw-zero/south behavior is corrected.

Native circular and square views, heading-up orientation, biome text, marker
crowding and post-relaunch terrain were inspected. See the
[relaunch screenshot](docs/mapping/screenshots/mapping-relaunch.png).

## D. World map

Implemented direct panning, eased cursor-anchored wheel zoom, keyboard zoom,
player recentering, dimension browsing, coordinate navigation, waypoint picking,
right-click creation, hover details and links to mapping settings/management.
Returning to the player restores the live dimension.

Visible regions render as clipped textured rectangles. Far zoom uses the retained
64×64 overview layer; full-resolution cache pressure can also select overview
LOD. Large GUI viewports constrain minimum zoom to bounded residency rather than
crop explored viewport edges. Central tiles request data first. Resident overview
generation remains available while disk requests are saturated; missing regions
reuse immutable zeros and avoid empty overview GPU uploads.

[World map](docs/mapping/screenshots/mapping-world-map.png) and
[far overview](docs/mapping/screenshots/mapping-world-map-overview.png) are actual
1280×720 captures. Unexplored areas remain distinct from observed terrain.
An additional [larger sparse map](docs/mapping/screenshots/mapping-extended-large-map.png)
contains forty visited region centers: 41 partially explored region files and
1,540 stamped chunks. This exceeds the eight-region cache used for that run and
exercises eviction, disk-backed LOD and navigation across a 4,096×2,560-block grid.
It is genuinely client-observed terrain, not synthetic filled map data.

## E. Waypoints

Records retain the existing config persistence infrastructure and gain stable
UUID, visible flag, group, description and marker style. Names remain editable
labels rather than identity. Death-waypoint replacement/count behavior remains;
cross-world/dimension death state resets and missing pre-death positions no longer
remove previous markers.

The manager supports search across metadata, four sorts, dimension/group filters,
group visibility, coordinates/distance, copy, rename, full edit and delete.
Filtered rows are cached; instantiated row controls are bounded to the viewport.
The common two-page editor stages name/coordinates/dimension/group/description/
ARGB/style/visibility changes; Cancel does not mutate records. Saving a new marker
selects it for navigation so dense marker budgets cannot immediately hide it.

Nearest-first selection is bounded and gives navigation priority in every view.
In-world geometry has a separate default 16-marker limit. Map/HUD names avoid
overlap and screen-edge clipping. Waypoint overlays do not draw behind screens.
Beams fade near the camera, fixing the observed full-screen wall when standing
inside an opaque double-sided beam.

Native stress used 10,002 waypoints. The
[1600×1000 manager at GUI scale 4](docs/mapping/screenshots/mapping-manager-scale4.png)
and [editor at GUI scale 3](docs/mapping/screenshots/mapping-editor-scale3.png)
were visually inspected. The native M key, right-click form, typing and Save were
also exercised through desktop input.

## F. Configuration

Existing feature cards and config/profile architecture remain. Shared capture
runs while either terrain presentation needs it. Disabling both releases live
contexts after pending saves. Existing world-map capture/cache settings remain;
new controls include biome text, map waypoint names, marker budgets and range.

The former minimap cache-choice field remains deserializable but is deprecated:
the shared engine is now authoritative. Appearance Reset preserves waypoint
records/navigation. Other profile scoping remains unchanged. Mapping action keys
consume queued vanilla presses; unrelated held-key features retain raw polling.

Legacy import is explicit because old sanitized display-name folders cannot prove
their original owner. World Map settings → Legacy Data Import explains that
ambiguity. Terrain import saves current changes, copies only missing regions or
unexplored destination chunks, and preserves originals. Waypoint imports copy
records with deterministic migration IDs, making repeated imports idempotent.

## G. Performance

One sequential foreground reference/candidate pair completed normally, with
zero invalid-focus frames. Frozen pre-change `fdc1ba5` classes and final production
classes used identical copied input manifests and the identical observer jar
(SHA-256 `7e2bf0a544aec34f075ed3b0016f85b1765c4551c30d64df465c70ad554b9ac1`).
Java 21, G1/2048 MiB, PARK/500, automatic Reflex, Sodium, 1280×720 and the
fixture's 120 FPS gameplay/30 FPS menu limits were unchanged. Each phase lasted
60 seconds after a 30-second scene warmup; no builds, second client or dedicated
server ran during measurement.

| Phase | P95 ms ref → final | P99 ms ref → final | CPU % of one core ref → final | Peak RSS MiB ref → final |
| --- | --- | --- | --- | --- |
| Stationary minimap | 8.344 → 8.402 | 8.975 → 9.197 | 109.62 → 108.17 | 1988.79 → 1995.88 |
| Traversal, 20 × 64 blocks | 8.360 → 8.372 | 9.062 → 8.935 | 203.41 → 193.78 | 2324.84 → 2345.02 |
| Alternating settings/gameplay | 33.334 → 33.334 | 33.347 → 33.355 | 35.51 → 33.70 | 2336.52 → 2362.78 |

Gameplay averaged 119.36/119.60 FPS in the reference and 119.54/119.67 in the
candidate. Neither gameplay phase had an interval above 50 ms. The alternating
UI phase had one such interval in the reference and zero in the candidate. Its
33.33 ms tail reflects the retained menu limit. Final memory was modestly higher
(up to 26.26 MiB / 1.12% in these phases). Small mixed tail differences and lower
observed CPU in one pair do **not** establish a repeatable FPS/CPU improvement or
exclude rare regressions. No material regression appeared in this bounded pair.

Whole-recording JFR allocation weights were 273.85 → 268.65 MiB/s; maximum GC
pause was 30.09 → 29.32 ms. These are statistical samples including startup/setup/
save, not exact phase allocations. Old minimap ring/entity paths no longer appear
among the candidate's leading Aurora sample callers. Primitive region-key Long
allocation weight remained zero in both. Raw and aggregate evidence is retained
in `aurora-mapping-reference-04`, `aurora-mapping-final-03` and
[performance-results.json](docs/mapping/performance-results.json).

Structural bounds: 1–8 captures/tick; four new disk requests/tick; eight in-flight
loads/context; 64 queued worker tasks; two large texture operations/tick; bounded
LRUs; 64 nearest markers and 16 geometry markers by default; reusable label-layout
buffers. The native extended run separately exercises an eight-region cache.
Texture counts estimate payload residency, not driver allocation/VRAM telemetry.

Earlier stale-classpath, failed-scene and unfocused runs remain excluded.
`reference-03` had 100% unfocused measured frames. `final-02` overlapped a later
build after desktop input stopped and is excluded regardless of its focus state.
Those raw artifacts were retained rather than pooled into the accepted pair.

## H. Data integrity

WMR v1 and legacy experimental v2 surfaces are readable. Tests verify exact
pixel/stamp round-trips, truncated/unknown payload rejection, preserved malformed
originals, atomic replacement and copy-only/idempotent migration. Corrupt regions
become read-only rather than being silently overwritten. New region images start
transparent instead of containing uninitialized memory.

Native checks passed archive/live separation, portal return, disabling/re-enabling,
resource reload, disconnect/reconnect and process relaunch persistence. Identity
hash tests distinguish former sanitizer collisions. A separate loopback vanilla
server passed address identity, SP/MP waypoint isolation and reconnect persistence.
The server saved every dimension and stopped normally. New map-created waypoint
navigation selection was verified against the final editor implementation.

Extended traversal visited forty region centers with an eight-region cache. All
forty observed ABGR pixels and corresponding chunk stamps matched their durable
GZIP files after normal exit. Forty-one region payloads decoded successfully
(43,327,693 bytes total; 172,203 compressed bytes), with 1,540 captured chunks.
Native peaks before reconnect were eight full-resolution tiles, eight in-flight
loads, 390 overview entries, two full-resolution textures and 42 overview textures.
Those texture payloads represent at most 2 MiB full-resolution plus 672 KiB overview
GPU data in this run, excluding minimap, driver overhead and other Minecraft assets.
The workflow completed in 219.01 seconds, including far-map browsing and reconnect.

Public authenticated servers, modded portals and permanent disk-failure recovery
have not received native acceptance. A failed filesystem can still leave unsaved changes at shutdown;
that failure is logged rather than represented as success.

## I. Validation

| Check | Result |
| --- | --- |
| Final automated suite | 565 tests, zero failures |
| Final Java 21 `test build --rerun-tasks` | Passed, 35 seconds |
| Native SP API workflow | 19/19 checks, normal exit, 270.45 seconds |
| Separate process relaunch | 3/3 checks, normal exit, 140.61 seconds |
| Native desktop interactions | M, right-click editor, typing, Save observed |
| GUI/resolution evidence | Scale 3 at 1280×720; scale 4 at 1600×1000 |
| Rendering environment | Minecraft 1.21.11, Fabric, Java 21, Sodium 0.8.14, AMD OpenGL |
| Production jar inspection | No research/mapping-probe classes |
| Foreground matched performance | Both normal exits; zero invalid-focus frames; results in §G |
| Loopback multiplayer | 7/7 checks; normal client and corrected-server shutdown |
| Extended native traversal/cache pressure | 6/6 checks; 40 visited centers; normal exit, 219.01 seconds |
| Independent durable-region verification | 40/40 exact observed pixels and stamps; 41 valid region files |
| Final normal Java 21 build after probes | Passed; ordinary tasks up to date, no probe classes in jar |

Checks and screenshot hashes are in
[validation-results.json](docs/mapping/validation-results.json). Resumption details
and excluded captures are in [PROGRESS.md](docs/mapping/PROGRESS.md).

## J. Rejected approaches

- Replacing the working capture/region/storage stack with a new mapping library.
- Keeping duplicate live minimap terrain analysis on the render path.
- Browsing dimensions by replacing the active capture context.
- Automatically assigning ambiguous old folders to new world identities.
- Unbounded IO/result queues, GPU residency or waypoint render loops.
- Claiming FPS gains from code inspection, microbenchmarks or unfocused captures.

## K. Workarounds and limitations

The earlier physical-Escape stop was respected, then the owner explicitly resumed.
The corrected final foreground pair, multiplayer workflow and extended observed-
terrain run are complete. Initial server-01 was excluded before client testing
because its flat-world generator lacked layers and its stdin pipe was closed;
only that exact disposable process was terminated. Server-02 used explicit layers,
accepted the client/reconnect, saved all dimensions and stopped normally.

Multi-hour multiplayer, multi-gigabyte archives, additional GPUs, public server
authentication, modded-dimension transitions and automated death/respawn runtime
regression checks remain unverified. The 41-region test is a sparse, partially
explored dataset; it is not a claim of exhaustive archive/soak coverage. Fast
teleports during native generation produced integrated-server catch-up warnings;
that workload is functional cache-pressure evidence, not a smooth-traversal FPS
claim. The matched performance workload is reported separately.

Advanced region-cache capacity applies when creating a context; reconnect after
changing that setting. Headless tests and bounded architecture do not replace
long-duration and broader hardware testing. Minimap movement rasterization is
capped at 20 Hz; it does not claim continuously transformed terrain. Map-created Y defaults to player height
and is editable, since retained color regions do not store height data.

Save-folder moves and server-address aliases create separate identities; the
client cannot infer remote server resets or aliases without authoritative data.
Legacy imports require the owner to identify the correct original world.

## L. Repository state

- Starting commit: `fdc1ba5`, branch `codex/performance-initiative-ii`.
- Working branch: `codex/unified-mapping`.
- Local checkpoints: `702b8de` baseline/architecture; `555e404` shared mapping
  implementation; `e63d27f` marker/viewport/input refinement and resumable tools;
  `dfe1280` preserved the interrupted report and native evidence; `d082494` records
  completed foreground/MP/extended acceptance and reproducible evidence tooling.
- Implementation ending commit: `e63d27f`; this report/evidence is committed
  separately, with acceptance ending at `d082494`. Resolve the completed reporting
  head with `git rev-parse HEAD` (also supplied in the completion message).
- Starting status/inventory are preserved under `docs/mapping/`. The complete
  [change inventory](docs/mapping/final-changes.txt) lists affected paths.
- Pre-existing untracked `CPERF1B_PARK_BENCHMARK.md` and
  `CPERF2_PARK_WAKE_INVESTIGATION.md` remain untracked and unchanged by this task.
- Original `run/` data and existing performance evidence were not execution
  targets. No protected pacing/Reflex/Launcher/Hitreg/glass source changes or
  unexpected file deletions are present. Raw captures stay in disposable Temp roots.
- No push, merge, remote history rewrite or release publication.

## M. Release readiness

**Ready for owner review and eventual integration, within the documented tested
scope.** The unified engine, presentations, waypoint workflow, migration safeguards,
565-test suite, native SP/relaunch/MP checks, larger observed-map cache-pressure
checks and foreground comparison are complete. No unresolved core defect is known
from this validation. Broader hardware, public-server and multi-hour coverage remain
normal follow-up validation; no whole-game FPS gain is claimed.

All validated changes are committed locally on `codex/unified-mapping`. The normal
production artifact is `build/libs/aurora-3.0.0.jar` (SHA-256
`1f40895e9c90d7e49a271dc68a0439c8add98529666eacd790dd9ce744e9095f`).
No release was published.
