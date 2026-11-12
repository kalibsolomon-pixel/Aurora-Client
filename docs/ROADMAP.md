# Aurora Client roadmap

Agreed 2026-11-10; revised by the owner on 2026-11-11. Decisions are in [ADRs](adr/)
and current evidence is summarized in [STATUS](STATUS.md).

| Step | Work | State |
|---|---|---|
| 1 | Fail-soft mixins, one feature catalog, diagnostics and a proven breaks list | Merged, PR #2; UI/clipboard and mod-matrix evidence recorded |
| 2 | Fair-play classes and advantage defaults off | Merged, PR #3 and #5; generated FAIR_PLAY.md |
| 3 | Minecraft-free core, numeric overhead budget, notices, contributor cleanup | Merged, PR #4 and #6–#9 |
| 4 | Port sequentially through 26.1.2, 26.2 and 26.3; publish individual artifacts | Complete; all ports merged (#10–#12) and published |
| 5 | Extract ports and make the version adapter own all mixins, glass and Fabric calls | Complete and released on 26.3, PR #13; acceptance evidence recorded |
| 5.5 | One-source parity restructure: 26.1 and 26.2 adapters under the PR #13 layout, per-line release builds (ADR 0008) | Complete; [acceptance](port/step-5.5.md) |
| 6 | Shader-drawn shapes behind RenderUtil, measured against current caches | Merged, PR #21; [evidence](reports/2026-11-11-step-6-baseline.md) |
| 7 | Apollo restrictions, friends/status, NeoForge if requested | Deferred in that order |
| 8 | Client optimizations Aurora owns: loading, CPU, textures and maps ([ADR 0009](adr/0009-client-optimizations-aurora-owns.md)) | Stacked PRs #31–#35; GPU-side items deferred (below) |

The owner explicitly authorized step 5 before publication. The newest port
shipped as 3.3.0, satisfying the feature-freeze condition. Work continues in
roadmap order. The shape extraction in step 5 keeps
the existing coverage math and Minecraft renderer; it does not implement step 6.

## Version and release policy

Every Minecraft line from 26.1 onward receives all feature work in parity, from
one source tree holding one adapter per line; each release builds one
version-pinned JAR per line and the window only grows as new versions are
ported. 1.21.11 stays immutable and outside the window, and there is still no
universal JAR. The reversal of the frozen-line rule, its prerequisites and its
costs are recorded in [ADR 0008](adr/0008-full-feature-parity-across-supported-versions.md);
the Fabric-only loader policy remains [ADR 0002](adr/0002-version-and-loader-policy.md).

The target is a release within two weeks of Sodium supporting a new Minecraft
version once the extracted layers are established; with a growing parity window
the cadence is judged per release. Every release must pass the
[overhead budget](OVERHEAD_BUDGET.md), applicable [compatibility matrix](MOD_MATRIX.md)
and [immutable-release procedure](RELEASE_PROCESS.md) for every line it ships.

## Later work and unresolved evidence

Apollo support only honors server restrictions and requires licensing/detection
research first. Social is friends and online status without chat or telemetry;
it remains optional. NeoForge requires demand. Client optimizations follow ADR 0009 (step 8). No
competing renderer is planned. Step 8's GPU-side items wait for GPU-bound tester
evidence: deferred sky, entity outline scissor, Sodium nearest-plane quad order,
the three native-Vulkan items (sky in main pass, early swapchain acquire,
background vertex copy) and occlusion culling. The development machine is
CPU-bound and cannot show their gains; see the
[world-render report](reports/2026-11-12-perf-world-render.md).

Better Hitreg's author-permission provenance and HitColor's missing repository
LICENSE remain upstream follow-ups. The current notices reproduce the licenses
published by those projects. Preserve the vendored hitreg timing core.

The earlier detailed roadmap/status notes are preserved in [history](history/).
