# Aurora Client 3.1.0 release report

Prepared 2026-10-20. The owner explicitly authorized Client 3.1.0 publication
and confirmed runtime acceptance in this release request. Launcher publication
and its independently controlled production catalog are outside this release.

## A. Starting repository state

- Source: `kalibsolomon-pixel/Aurora-Client`.
- Starting branch: `codex/gui-premium-design`.
- Starting HEAD: `dc84b9be1d2b57a2b8fe4f086a1b8d9696f2f420`.
- Production baseline: `v3.0.0`, source `5bdf3aa30bc7bfa9a226170a9c54ca5ec3fd8e1d`.
- Existing local Vulkan changes were present in AGENTS, ARCHITECTURE,
  PixelCanvasSetting, BlurPanelRenderer, BlurTestScreen and ReflexScheduler,
  plus the portable renderer, shaders, report, evidence and tooling.
- Existing benchmark notes `CPERF1B_PARK_BENCHMARK.md` and
  `CPERF2_PARK_WAKE_INVESTIGATION.md` are preserved outside the release commit.
- Starting status, tracked inventory and binary diff are retained locally in
  `build/release-3.1.0/`. Release work uses `codex/release-3.1.0`.
- Existing mapping and GUI branches are already ancestors of the starting HEAD.
  No unrelated branches were reset and no files were cleaned or discarded.

## B. Integrated changes

- Unified mapping/waypoints: `555e404`, `e63d27f`, `d082494`, `6841436`.
- Physical-pixel map presentation and GPU lifetime: `51cd675`, `041cd48`,
  `f08cf92`, `f74ad7c`.
- Premium GUI and tooltip hand-off: `176adb8`, `31a1c9d`, `5a2136c`.
- Config/profile write ordering, Windows retries and keybind saves: `926b109`.
- Owner UI corrections: `33e2bd5`, `b9296b0`, `dc84b9b`.
- Previously local, validated Vulkan backend changes are included, with original
  evidence in `VULKAN_COMPATIBILITY_REPORT.md` and `docs/vulkan/`.
- Release changes add portable defaults, geometry reset integration, corrupt-file
  backup protection, version 3.1.0, regression tests and release notes.
- Existing opt-in performance experiments remain opt-in; no new pacing,
  movement, packet timing or Better Hitreg core changes were introduced.

## C. Default configuration

Authority: the owner's Launcher instance `044b250279dc48de822f1684a2a3941e`.
Only isolated copies of `aurora.json` and its active `profiles/default.json`
were read for values. They match. All original config files were inventoried
and checksummed without launching against or writing to that directory.

Reference `aurora.json` SHA-256:
`1bfa338e37fd45c35eaa99b1a068ee3bae4d548ee05c8a7bf48d3db09499c8b2`.
The original Aurora config and profile still match their isolated copies.
Four map files differed from the initial live-directory inventory during this
session; no operation in this release writes to that source directory. They
were not copied, restored, migrated or included in the release.

`tools/prepare_310_defaults.py` records an explicit portable-setting allowlist.
The resulting resource is `assets/aurora/defaults/3.1.0.json` (238 fields).
It includes visual/feature preferences, theme, font, keybinds, registry-based
particle/item preferences and the twelve supported anchored HUD layouts.
The removed Stats layout is omitted. Private maps, waypoints/navigation,
telemetry, timestamps, profile names/history, migration flags, caches, external
mod settings and machine-specific performance tuning are excluded.

`ReleaseDefaults` applies the resource after normal field initialization in
each AuroraConfig constructor. Gson then overlays existing serialized values.
Fresh installs receive the resource; existing configs and profiles retain
their fields, and genuinely missing fields receive release defaults. The
ordinary ordered AtomicJsonWriter persistence paths remain in use.

Feature/profile reset snapshots now include these defaults. Reset Positions
restores anchor, offsets and scale from independent resource layout copies;
hidden/locked state and unrelated preferences are preserved. Existing waypoint
appearance reset protections remain intact. Corrupt JSON is backed up before
later autosaves; if backup fails, config writes are blocked for that session.

## D. Visual acceptance

The owner confirmed the completed interface works and requested immediate
publication. The exported theme, feature states, keybinds, coordinates, anchors
and scales match the reference without conversion to absolute screen positions.
Default HUDs are Armor, Potion and Keystrokes; Minimap, Info, CPS, Ping, Reach,
Totem Pops and Toggle Sprint/Sneak start disabled at the feature level.

Automated bounds checks cover conservative default-visible HUD envelopes at
logical 480x270, 640x360, 854x480, 960x540 and 1280x720, covering normal GUI scales
for 1080p/1440p/4K and a smaller window. Effect rows and localized text have dynamic
dimensions; these checks are not a claim of universal containment for arbitrary
effect counts, fonts, window sizes or GUI scales.

Final packaged-3.1.0 native checks and default screenshots are being recorded in
`build/vulkan-investigation/release-310-{opengl,sodium,vulkan}`. Final results
are recorded in the publication addendum below rather than inferred from a build.

## E. Compatibility

Minecraft 1.21.11 (`~1.21.11`), Java >=21, Fabric Loader >=0.16.0 and Fabric API.
Build inputs remain Loader 0.19.2 and API 0.141.4+1.21.11; no dependency upgrades.
Build JDK is 25, Java release target 21. Native fixture uses Java 21.

Existing full native matrix: OpenGL, Sodium 0.8.14 and VulkanMod 0.6.8 each passed
66/66 checks, 60 captures and normal exit on the recorded Windows/AMD host.
Vulkan bypasses unsupported GL timestamp calls while retaining ordinary caps.
See the Vulkan report for pinned dependencies, GPU/driver and upstream limits.

## F. Automated tests

- Gradle test/build: 610 tests, zero failures/errors, one opt-in Windows stress
  comparison skipped. That longer comparison is not required for the regular suite.
- Python release-tool/default checks: 32 passed.
- Added five Java regressions: defaults/privacy/independence, fresh-vs-upgrade
  loading including profile/relaunch preservation, corrupt backup, position reset,
  and default HUD bounds.
- Added two Python checks for the export allowlist, nested strings/registry IDs,
  version metadata and resource wiring.
- Initial new-test failures were incorrect test assumptions (Minimap enabled and
  counted as visible despite the reference disabling it); corrected to the actual
  reference. No product behavior was changed to satisfy those assumptions.
- JAR inspected for embedded 3.1.0 identity, dependencies, exact defaults resource,
  unexpected data paths and diagnostic probes. Only six intended ImageIO support
  jars are nested. `git diff --check` passed.

## G. Native testing

Historical component evidence covers mapping SP/relaunch/loopback multiplayer,
map-cache pressure, Premium GUI and config/profile persistence. The owner's
acceptance supplements those records. Final 3.1.0 fixture outputs, exit status,
fresh-default assertions and image checks are recorded below when complete.
No live Launcher instance, original config or original run directory is a test target.

## H. Release artifact

- Filename: `aurora-3.1.0.jar`.
- Local candidate: 3,208,004 bytes.
- Local candidate SHA-256:
  `37cabf84254632deedec910ffa775448c7bb7871366e3fe512f6ccf62550cea0`.
- Embedded mod ID/version: `aurora` / `3.1.0`; environment `client`.
- Defaults resource is present and byte-for-byte matches the reviewed source.
- CI records the authoritative final artifact checksum; cross-platform archive
  bytes are compared before approving publication.

## I. Publication

Publication must use workflow_dispatch on master, exact source SHA, stable
channel and the aurora-production reviewer gate. The existing draft verification,
sole-asset rule, collision refusal, immutable publication and anonymous download
verification remain intact. Versioned release notes now travel with the verified
candidate and are combined with its exact source/hash metadata by the workflow.

Target identity: `v3.1.0`. No existing tag/release is replaced. Final source,
workflow, release/asset URLs and checksum verification are recorded below.

## J. Launcher integration

No Launcher application release or version change is authorized or performed.
README documents the production manifest as a separate manual publication gate.
The existing `release-manifest/aurora-releases.json` is not updated here, so a
GitHub Client release alone does not advertise 3.1.0 to existing Launcher builds.
The owner can coordinate that catalog update independently.

## K. Remaining limitations

Linux/macOS and other GPU vendors are not natively retested. User runtime
acceptance and recorded component evidence do not establish universal Vulkan
support, latency/FPS gains, arbitrary resource-pack compatibility or public PvP
timing. Multi-resolution default geometry checks do not substitute for a new
physical-display screenshot sweep at every supported size.

## L. Final repository and publication state

See the publication addendum for the exact source commit, final artifact,
workflow result, native result and repository status. Local reference copies,
launch specs, full logs and temporary worlds remain ignored under build/;
no account credentials or personal world data are packaged.
