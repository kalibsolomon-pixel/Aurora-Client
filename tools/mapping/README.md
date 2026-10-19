# Mapping validation

All native fixtures are new, marked disposable directories outside `run/`.
Never point these tools at an original instance. Raw logs, worlds, profiles and
JFR recordings contain local identities and stay private; commit only selected
screenshots and aggregate reports.

`mapping_prepare.py ROOT --reference` copies the existing local `aii-s1` fixture,
normalizes its historical reference classpath (including JVM classpath groups),
and freezes current client classes/resources with SHA-256 manifests. Use
`--classes-from ROOT` to freeze the previously preserved pre-change runtime.
This tool deliberately refuses existing destinations. The source fixture path is
local research infrastructure; use an explicit replacement when reproducing
elsewhere. It never downloads mods or alters the source fixture.

`gradlew -I tools/mapping/runtime.init.gradle -PmappingRoot=ROOT mappingProbeJar`
builds an opt-in native integration mod. `mapping_run.py ROOT` runs its API-driven
workflow, captures real GPU screenshots, and checks editor propagation, 10,000+
waypoints, viewport-control bounds, archives, dimensions, toggles, resource reload,
GUI scales and reconnects. The driver guards nested `runTick` calls during save
screens. `--resume` reopens the same saved instance in a new process. A separately
frozen `--runtime-root ROOT` can validate final visual/input fixes against the same
persisted game directory while retaining all previous runtime artifacts.
`--multiplayer` uses only the disposable loopback server at 127.0.0.1:25579.

For native eviction pressure, prepare another new frozen fixture with
`--reference --cache-regions 8` (config and active profiles), build
`mappingProbeJar`, and run `mapping_run.py ROOT --extended`. This mode visits
forty region centers, waiting for actual client terrain capture at each. It
records peak full-resolution/overview residency and in-flight loads, browses the
resulting map at far zoom, and reconnects. It never fabricates terrain pixels.
After normal exit, run `mapping_verify_regions.py ROOT` to verify every observed
pixel and chunk stamp against the durable GZIP files. Keep its raw world and
identity private; the aggregate `region-integrity.json` is suitable for review.

`mappingPerfJar` builds the performance workload. It is a preserved copy of the
previous stress observer, with a disposable scene reset before positive setup
assertions. Replace only the research jar classpath entry with the new jar;
run with `tools/performance/initiative2_run.py`, then review with
`tools/performance/initiative2_review.py`. The reference and candidate use the
identical probe binary, Java 21, G1/2048 MiB, PARK/500, bounded automatic Reflex,
1280x720, VSync off and the same copied input world/mods/settings. Never run two
timed clients or a dedicated test server concurrently. Setup/warmup is excluded.
Preserve incomplete/failed runs and do not pool them with valid comparisons.

The single-pair mapping comparison is bounded diagnostic evidence, not a repeated
whole-game FPS result or physical input-to-photon measurement. Component and cache
bounds are also covered by JUnit tests. No ordinary build includes these probes.
