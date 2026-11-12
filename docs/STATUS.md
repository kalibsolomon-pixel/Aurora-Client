# Aurora Client status

Updated 2026-11-11. See [ROADMAP](ROADMAP.md) for the order of work,
[architecture](ARCHITECTURE.md) for ownership, and [ADRs](adr/) for decisions.

## Structure and integration

Roadmap steps 1–3 and the 26.1.2, 26.2 and 26.3 ports are merged through PRs
#1–#12. The owner authorized autonomous GitHub integration and publication.

Step 5 is merged in [PR #13](https://github.com/kalibsolomon-pixel/Aurora-Client/pull/13): core → ports, with all
Minecraft/Fabric code and resources under `adapters/minecraft-26.3/`. Five ports
serve real core consumers: draw coverage, text measurement, frame clock, key
state and world identity. Every mixin and the entire glass renderer belong to
the adapter. Root Gradle commands and the native UI pipeline are preserved.

Step 5.5 adds the 26.1 and 26.2 adapters to this tree, with 188 shared
Minecraft-facing implementations and common assets. Version-specific rendering,
mixins and input stay in native adapters. One shared registry owns all 39
features. `python tools/parity.py` builds/tests all three lines; each output is
isolated under `build/minecraft-<version>/libs/`.

The Java suite discovers 660 tests on 26.1.2/26.2 and 662 on 26.3 (one opt-in
stress test skipped per line). All 42 Python release/default/parity tests pass.
The build checks native dependency exclusion and shared/native source ownership,
and includes core and ports in each runtime artifact.

## Distribution

The published checkpoints have independent source commits and release checks:

| Minecraft | Aurora | Maintenance after publication |
|---|---|---|
| 26.1.2 | [3.2.2](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.2) | Full parity from the next release (ADR 0008) |
| 26.2 | [3.2.3](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.3) | Full parity from the next release (ADR 0008) |
| 26.3 | [3.3.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.3.0) | All feature work; includes step 5 |

There was never a universal-JAR decision, and ADR 0008 keeps it that way. The
owner's 2026-11-11 revision goes further than the earlier checkpoint publication:
from the next release onward, artifact availability and feature maintenance are
joined — every line from 26.1 onward builds from one source tree and receives all
feature work. Published JARs stay immutable; parity applies to future releases.
Filenames identify both Aurora and Minecraft versions. Release tags remain
`v<aurora-version>`; every artifact has its own exact source and digest.

All three versions are published. Each immutable JAR passed anonymous
redownload and digest/identity checks. [Published artifact identities and
acceptance](releases/PUBLISHED_2026-11-11.md) record the exact source and SHA-256.
Every publication uses the protected release workflow and a verified draft.
The Latest label selects 26.3; PR #14 applies that choice at publication too.

[3.4.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.4.0)
is published and Latest: all three lines built from one commit,
[7b51088](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/7b510887c67a3884bd1ef300c7ac344d4a027505).
Publication required all fourteen native matrix rows and the unchanged overhead
gate, bound to runtime/build/pin inputs, then verified every JAR through handoff,
draft and anonymous public redownload. [Artifact identities](releases/PUBLISHED_2026-11-11.md#340-parity-release).

## Acceptance and contributor readiness

See [step 5.5 evidence](port/step-5.5.md), [step 5 evidence](port/step-5.md)
and [the compatibility matrix](MOD_MATRIX.md).
Iris is tested only on OpenGL. Native Vulkan has its own baseline/performance
rows. Beryl is a possible separate shader-mod check; no Fabric 26.3 version was
returned by its Modrinth version query, so it is not advertised as tested.

The automatic benchmark now waits for the world with no screen open before
starting. It keeps two warm-up runs, four judged runs and the unchanged 0.25 ms
mean / 0.40 ms p95 budget. Earlier failed measurements remain in the history;
passing results must name the current candidate and fixture.

Contributor guidance, issue forms, a PR template, an updated architecture guide
and a security reporting policy are included. GitHub private vulnerability
reporting is enabled. Older engineering reports and superseded status documents
are retained in `docs/history/`.

## Remaining direction

Step 6 (shader-drawn shapes) is merged, PR #21: live rounded shapes draw
through a native GUI pipeline by default, with a
CPU fallback. Evidence and its limits are in the [step 6 report](reports/2026-11-11-step-6-baseline.md).
Caches are retained until measurement supports removing one. Step 7 (Apollo restrictions, then friends/status,
possible NeoForge and limited optimizations) remain future work. No new gameplay
features are included in this restructure. Apollo licensing/detection needs
verification before implementation. Better Hitreg author-permission provenance
and HitColor's missing repository LICENSE remain documented follow-ups; existing
notices follow the published upstream licenses and are not new permission claims.

## Step 8: client optimizations (in progress)

[ADR 0009](adr/0009-client-optimizations-aurora-owns.md) supersedes ADR 0005's scope rule: measured
optimizations ship on by default under the `performance` feature, stand down for mods that
own their area, and only the ones with a visible trade-off get a row on Settings → Performance.
PR 1 (`feat/perf-foundation`) adds the stand-down mechanism and four loading optimizations;
[evidence](reports/2026-11-12-perf-loading.md). Later stacked PRs cover per-frame CPU work,
textures, Aurora's maps, world rendering with Sodium patches, and measured Vulkan and
occlusion prototypes.

## Next work

Step 5.5 (ADR 0008) is complete; step 6 (shader-drawn shapes) is merged, PR #21.
The earlier 26.3 OpenGL overhead failure was resolved before 3.3.0 publication;
the failed historical measurements remain documented. Broader release validation
should add a packaged-client launcher boot, multiplayer and another GPU or
lower-end machine. Vulkan shader-mod support requires a separate pinned build
and shader-pack checks. A broken-feature indicator on grid cards remains a
small UI proposal, not part of the completed restructure.
