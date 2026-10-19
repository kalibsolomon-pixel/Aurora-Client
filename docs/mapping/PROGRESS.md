# Unified mapping overhaul — progress

Started 2026-10-19 at `fdc1ba5` on `codex/performance-initiative-ii`.
Working branch: `codex/unified-mapping`. No push, merge or publication.

Protected: existing `run/` data, benchmark evidence, two pre-existing untracked
CPERF notes, Frame Pacer, Reflex, JVM/Launcher policies, glass renderer, Hitreg core.
Starting status and file inventory are alongside this document.

## Audit and architecture

Retain ChunkCapturer, versioned WMR regions, primitive RegionCache LRU,
OverviewCache LOD, HUD placement and shared Aurora widgets. Client thread owns
terrain access/images/textures; a serial worker owns disk operations.

Confirmed defects/limitations:

- Minimap duplicates live column analysis on render, and its cache is optional.
- Archive browsing replaces the live capture context.
- Load queues are cleared on switch but old worker callbacks can repopulate them.
- Region images use uninitialized allocation despite promising transparency.
- Lossy sanitized identities can collide; SP display names do not identify saves.
- IO work/results are unbounded and dirty is cleared before save success.
- Waypoints have no stable ID, visibility/group/description/navigation or draw cap.
- Minimap yaw-zero arrow points north although Minecraft yaw zero is south.

Plan: one live capture context plus one optional read-only archive context, shared
cached terrain for both maps, identity directories hashed from authoritative save
path/server address, conservative recoverable legacy import, generation/lifetime
safe async loads, bounded request/result/upload work, shared waypoint filtering
and selection, Aurora-native editor/manager improvements, targeted tests and
disposable Java 21/Sodium runtime acceptance. No new library is needed.

## Validation log

- Baseline automated suite started before production edits.
- Native baseline/final fixtures will be separate copied worlds/configurations.
- Runtime measurements must distinguish component work from whole-game frame time.
- Native visual/runtime acceptance and performance comparisons remain pending.

## Implementation checkpoint

Shared engine, hashed save/server identity, separate archive contexts, unique
texture lifetimes, bounded disk/load/upload work, pending-save context reuse,
failure backoff, dirty eviction protection and modded-dimension index implemented.
Minimap is now a shared-data presentation with one cached masked terrain/border
blit, bounded motion updates and tick-cached entity/waypoint overlays. Added
waypoint IDs, description/group/style/visibility, bounded shared selection and
navigation; transactional two-page editor; searchable/sortable/dimension/group
filtered manager with viewport-bounded control caches. World map has eased
cursor-anchored zoom, coordinate navigation, LOD, marker picking, settings links.
Legacy import is explicit, copy-only, merges only previously unexplored chunks
and preserves original files; repeated waypoint imports are idempotent. Settings
reset no longer clears stored waypoint data. Old cache-choice field remains
readable for config compatibility; shared data is now authoritative.

562 tests passed at the latest checkpoint. New tests exercise format round-trips,
malformed/legacy payloads, copy-only migration, cache pressure, identity collision
resistance, map transforms and 10,000-waypoint selection. Reference and initial
candidate native stress workloads exited normally. Candidate minimap visually
inspected. Initial candidate precedes final lifecycle fixes and is diagnostic,
not final acceptance. Dedicated native acceptance driver is under validation.
Raw logs/worlds remain under new `Temp/aurora-mapping-*` roots.

### Evidence corrections and refinement

Initial baseline-01/candidate-01 and acceptance-01/02 launch templates retained
the previous performance initiative's reference classpath. Those runs are excluded
from candidate/overhaul evidence; initial visual assessment was premature. Fixture
preparation now normalizes classpaths AND JVM classpath groups and hashes frozen
runtime files. acceptance-03 loaded verified current classes and passed through
resource reload/GUI checks, then its driver re-entered during Minecraft's nested
disconnect rendering. The driver now guards reentrancy; its failed run is retained.

reference-02 exited normally but reported no-op fill setup failures in an already
populated copied scene; it is excluded from performance comparisons. A separate
mapping performance workload now resets only its disposable scene before setup.
Original performance tools/evidence are unchanged. acceptance-04 is exercising
the corrected final workflow, including map-created waypoints, far LOD and panning.

Runtime visual refinement added overlap-free HUD/map labels, a separate default
16-beam budget, no waypoint overlays behind screens, full Delete labels, center-first
tile requests and empty-overview fast resolution. Giant viewports constrain zoom
to bounded residency instead of drawing blank cropped edges. Tests/build pass.

## Preserved at desktop-input interruption

The owner stopped Computer Use with physical Escape during performance foreground
selection. No further desktop input was issued. Do not mark the initiative fully
accepted: foreground matched performance and loopback multiplayer remain pending.

- acceptance-04: 19/19 native checks, normal exit, 270.45 seconds; map creation,
  editing, 10,002 markers, far overview/pan, archive/live isolation, portals,
  toggles, resource reload, GUI scale 3 at 1280x720 and scale 4 at 1600x1000,
  disconnect/reconnect. Selected real GPU screenshots are under `screenshots/`.
- Same game directory, separate final-code process: 3/3 relaunch checks and normal
  exit, 140.61 seconds. Edited waypoint, large collection and terrain survived.
- Native desktop observations: brief M press opened map; right-click opened new
  editor; text entry and Save worked. Queued mapping actions fixed missed taps.
- Near-camera beam fade corrected the observed full-screen wall when standing
  inside an opaque double-sided marker. Labels avoid overlap; UI overlays no
  longer draw waypoint labels/beams behind screens.
- reference-03 completed but **all measured frames lacked focus**, activating
  native 10 FPS throttling. Excluded from foreground performance conclusions.
- final-02 timed candidate was already launched before the interruption; it has
  an automatic bounded stop. Its raw results must be reviewed before use and are
  excluded from comparison here. No foreground focus or FPS gain is claimed.
- 565 tests and normal builds pass. A final Java 21 rerun is recorded separately.

Raw fixture roots: `Temp/aurora-mapping-acceptance-04`, `...reference-03`,
`...final-02`. Earlier failed/stale roots are retained. Final runtime manifests
freeze client classes/resources; probes are opt-in and absent from ordinary jars.
`Temp/aurora-mapping-multiplayer-01` and `...server-01` are prepared, not run.
Use the final frozen runtime override when resuming that MP fixture; copy-only
SP data has already been supplied to verify world isolation.
