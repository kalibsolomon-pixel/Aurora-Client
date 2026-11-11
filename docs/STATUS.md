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

The Java suite discovers 661 tests (660 pass, one opt-in stress test is skipped).
All 34 Python release/default tests pass. The build checks native dependency
exclusion and includes core and ports in the single runtime artifact.

## Distribution

Separate Minecraft-specific JARs have independent source commits and release checks:

| Minecraft | Aurora | Maintenance after publication |
|---|---|---|
| 26.1.2 | [3.2.2](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.2) | Archived forward-port checkpoint |
| 26.2 | [3.2.3](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.3) | Frozen to crash and security fixes |
| 26.3 | [3.3.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.3.0) | All feature work; includes step 5 |

There was never a universal-JAR decision. ADR 0002 originally published only
the newest checkpoint; the owner's 2026-11-11 revision also publishes 26.1/26.2
artifacts. Artifact availability is separate from ongoing feature maintenance.
Filenames identify both Aurora and Minecraft versions. Release tags remain
`v<aurora-version>`; every artifact has its own exact source and digest.

All three versions are published. Each immutable JAR passed anonymous
redownload and digest/identity checks. [Published artifact identities and
acceptance](releases/PUBLISHED_2026-11-11.md) record the exact source and SHA-256.
Every publication uses the protected release workflow and a verified draft.
The Latest label selects 26.3; PR #14 applies that choice at publication too.

## Acceptance and contributor readiness

See [step 5 evidence](port/step-5.md) and [the compatibility matrix](MOD_MATRIX.md).
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

Step 6 (shader-drawn shapes) and step 7 (Apollo restrictions, then friends/status,
possible NeoForge and limited optimizations) remain future work. No new gameplay
features are included in this restructure. Apollo licensing/detection needs
verification before implementation. Better Hitreg author-permission provenance
and HitColor's missing repository LICENSE remain documented follow-ups; existing
notices follow the published upstream licenses and are not new permission claims.

## Next work

Step 6 is the next implementation task: benchmark shader-drawn shapes against
the existing raster caches before choosing the path. Broader release validation
should add a packaged-client launcher boot, multiplayer and another GPU or
lower-end machine. Vulkan shader-mod support requires a separate pinned build
and shader-pack checks. A broken-feature indicator on grid cards remains a
small UI proposal, not part of the completed restructure.
