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
