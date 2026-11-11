# Aurora Client roadmap

Agreed 2026-11-10; revised by the owner on 2026-11-11. Decisions are in [ADRs](adr/)
and current evidence is summarized in [STATUS](STATUS.md).

| Step | Work | State |
|---|---|---|
| 1 | Fail-soft mixins, one feature catalog, diagnostics and a proven breaks list | Merged, PR #2; UI/clipboard and mod-matrix evidence recorded |
| 2 | Fair-play classes and advantage defaults off | Merged, PR #3 and #5; generated FAIR_PLAY.md |
| 3 | Minecraft-free core, numeric overhead budget, notices, contributor cleanup | Merged, PR #4 and #6–#9 |
| 4 | Port sequentially through 26.1.2, 26.2 and 26.3; publish individual artifacts | All ports merged (#10–#12); publication being completed |
| 5 | Extract ports and make the version adapter own all mixins, glass and Fabric calls | Implemented on refactor/version-adapter; acceptance and integration being completed |
| 6 | Shader-drawn shapes behind RenderUtil, measured against current caches | Planned |
| 7 | Apollo restrictions, friends/status, NeoForge if requested, limited vanilla optimizations | Deferred in that order |

The owner explicitly authorized step 5 before publication. New gameplay features
remain on hold until the newest port ships. The shape extraction in step 5 keeps
the existing coverage math and Minecraft renderer; it does not implement step 6.

## Version and release policy

Each Minecraft line gets its own JAR. The owner revised the original newest-only
publication rule to include 26.1 and 26.2 checkpoint artifacts. This does not add
a universal JAR or simultaneous multi-version feature builds. Feature work stays
on 26.3; 26.2 becomes the previous frozen line; 26.1.2 is archived. Older releases
remain immutable. See [ADR 0002](adr/0002-version-and-loader-policy.md).

The target is a release within two weeks of Sodium supporting a new Minecraft
version once the extracted layers are established. Every release must pass the
[overhead budget](OVERHEAD_BUDGET.md), applicable [compatibility matrix](MOD_MATRIX.md)
and [immutable-release procedure](RELEASE_PROCESS.md).

## Later work and unresolved evidence

Apollo support only honors server restrictions and requires licensing/detection
research first. Social is friends and online status without chat or telemetry;
it remains optional. NeoForge requires demand. Vanilla optimizations must meet
the three rules in ADR 0005. No competing renderer is planned.

Better Hitreg's author-permission provenance and HitColor's missing repository
LICENSE remain upstream follow-ups. The current notices reproduce the licenses
published by those projects. Preserve the vendored hitreg timing core.

The earlier detailed roadmap/status notes are preserved in [history](history/).
