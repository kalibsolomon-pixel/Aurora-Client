# Aurora Client — Unified Mapping System Implementation Report

2026-10-19 · branch `codex/unified-mapping` · local work only

**Status: implementation available for review; final acceptance is incomplete.**
The owner stopped Computer Use with physical Escape during performance foreground
selection. Desktop input stopped immediately. Foreground performance comparison
and loopback multiplayer acceptance remain pending. Nothing was pushed, merged,
published, or installed into an original instance.

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
checks. Actual screenshots were inspected. This does **not** constitute completed
performance, multiplayer, multi-hour or broad hardware acceptance.

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

**No accepted foreground before/after comparison or FPS gain is claimed.**

Initial fixtures retained a historical reference classpath; those runs are
excluded. A later reference reported no-op fill setup failures and is excluded.
The corrected performance probe resets its disposable scene before positive
setup assertions and uses identical observer binaries, copied inputs, Java 21,
G1/2048 MiB, PARK/500, bounded automatic Reflex, Sodium and 1280×720.

`aurora-mapping-reference-03` completed, but every measured frame lacked focus,
activating native background throttling at approximately 10 FPS/100 ms. It is
excluded from foreground conclusions. The final candidate was already running
when desktop input was interrupted; it has a bounded automatic stop. Its raw
results require review and are excluded from a comparison here.

Structural bounds verified in code/tests: primitive region lookups retained;
1–8 captures/tick; four new disk requests/tick; eight in-flight loads/context;
64 queued worker tasks; two large texture operations/tick; bounded LRU caches;
64 nearest markers by default; 16 world geometry markers by default; reusable
label-layout buffers. These are implementation bounds, not measured FPS or GPU
memory improvements. GPU residency and P95/P99 regression acceptance remain open.

## H. Data integrity

WMR v1 and legacy experimental v2 surfaces are readable. Tests verify exact
pixel/stamp round-trips, truncated/unknown payload rejection, preserved malformed
originals, atomic replacement and copy-only/idempotent migration. Corrupt regions
become read-only rather than being silently overwritten. New region images start
transparent instead of containing uninitialized memory.

Native checks passed archive/live separation, portal return, disabling/re-enabling,
resource reload, disconnect/reconnect and process relaunch persistence. Identity
hash tests distinguish former sanitizer collisions. Actual hosted multiplayer,
modded portals and permanent disk-failure recovery have not received native
acceptance. A failed filesystem can still leave unsaved changes at shutdown;
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
| Foreground performance / loopback MP | Pending |

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

Desktop validation stopped under the computer-use interruption rule after physical
Escape. No further desktop input was issued. Loopback client/server fixtures are
prepared but were not run. Foreground comparison must be repeated with verified
focus before performance acceptance.

Long-duration multiplayer, huge physical explored archives, additional GPUs,
modded-dimension transitions and automated death/respawn runtime regression checks
remain unverified. Headless tests and bounded architecture do not replace those
runtime scenarios. Minimap movement rasterization is capped at 20 Hz; it does not
claim continuously transformed terrain. Map-created Y defaults to player height
and is editable, since retained color regions do not store height data.

Save-folder moves and server-address aliases create separate identities; the
client cannot infer remote server resets or aliases without authoritative data.
Legacy imports require the owner to identify the correct original world.

## L. Repository state

- Starting commit: `fdc1ba5`, branch `codex/performance-initiative-ii`.
- Working branch: `codex/unified-mapping`.
- Local checkpoints: `702b8de` baseline/architecture; `555e404` shared mapping
  implementation; `e63d27f` marker/viewport/input refinement and resumable tools.
- Implementation ending commit: `e63d27f`; this report/evidence is committed
  separately. Resolve the reporting head with `git rev-parse HEAD`.
- Starting status/inventory are preserved under `docs/mapping/`.
- Pre-existing untracked `CPERF1B_PARK_BENCHMARK.md` and
  `CPERF2_PARK_WAKE_INVESTIGATION.md` remain untracked and unchanged by this task.
- Original `run/` data and existing performance evidence were not execution
  targets. No protected pacing/Reflex/Launcher/Hitreg/glass source changes or
  unexpected file deletions are present. Raw captures stay in disposable Temp roots.
- No push, merge, remote history rewrite or release publication.

## M. Release readiness

The implementation and available evidence are ready for owner/code review.
**The initiative does not yet satisfy its complete acceptance definition.**
Finish foreground performance comparison and loopback multiplayer validation,
review resulting limits/regressions, and repeat any affected runtime checks before
eventual integration or release. No release was published.
