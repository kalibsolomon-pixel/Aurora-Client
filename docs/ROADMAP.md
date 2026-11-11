# Aurora Client roadmap

Agreed 2026-11-10. The decisions behind this order are in [docs/adr/](adr/):

| ADR | Decision |
|---|---|
| [0001](adr/0001-single-open-mod-that-runs-anywhere.md) | One open-source mod that runs in any launcher; no telemetry, no cosmetics, limited social |
| [0002](adr/0002-version-and-loader-policy.md) | Forward-only versions, newest plus one frozen; Fabric-only releases |
| [0003](adr/0003-core-ports-adapter-layering.md) | Core, ports, version adapter |
| [0004](adr/0004-fail-soft-mixins-and-single-feature-registry.md) | Fail-soft mixins, one feature registry, tested matrix, diagnostics report |
| [0005](adr/0005-performance-scope-and-overhead-budget.md) | Overhead budget; no competing engine; rule for vanilla optimizations |
| [0006](adr/0006-shader-drawn-shapes-no-own-ui-renderer.md) | Shader-drawn shapes through Minecraft's pipeline; no own UI renderer |
| [0007](adr/0007-fair-play-classification-and-apollo-scope.md) | Fair-play classification, default-off list, Apollo scope |

## Order of work

Steps 1 to 3 happen on 1.21.11 and each makes the port cheaper or safer. Nothing after step 4 is designed before the port shows where the breaks are.

1. **Fail-soft foundation (1.21.11).** Single feature registry that records each feature's mixins. Fail-soft mixins with a per-feature health flag. A `breaks` list in `fabric.mod.json`. The copy-diagnostics report with its built-in benchmark. *Built 2026-11-11 on `feat/fail-soft-foundation`; see "Step 1 status" below.*
2. **Fair play (1.21.11).** Classify every feature. Make minimap player dots, player entity health, other players' totem pops and FreeLook default-off. Document Better Hitreg as local feedback only.
3. **Groundwork (1.21.11).** Move the already Minecraft-free packages into a core Gradle module. Measure the overhead baseline and set the budget. Add third-party notices. Clean up the repo for contributors.
4. **Port.** 26.1, then 26.2, then the newest release, each made to build and boot. Release the newest. Freeze 1.21.11 to crash and security fixes.
5. **Extract the layers.** Define the ports from what actually broke. Move every mixin, the glass renderer and the Fabric API calls into the version adapter.
6. **Shader-drawn shapes.** Behind `RenderUtil`, measured against the existing caches.
7. **Later, in this order.** Apollo restriction support, once its license and detection behaviour are checked. Friends and online status. NeoForge if asked for. Small vanilla optimizations under the ADR 0005 rule as they turn up.

## Step 1 status

Built and verified by automated tests and three dev-client boots (unmodified; ten mixins disabled plus one broken injection point; one mixin disabled with the report logged). Still open before step 1 counts as done:

- Look at the feature card tooltip, the detail-screen header and the Copy Diagnostics button on screen. They were not checked visually.
- Boot the tier-1 matrix (Sodium, Iris, VulkanMod, ImmediatelyFast, EntityCulling, Lithium, Mod Menu) with `-Daurora.diagnostics.log=true` and read each report.
- The `breaks` list holds only the standalone Better Hitreg mod. Add a mod when a boot shows it cannot work with Aurora, not before.
- `FeatureManager` still lists the runtime tick services by hand. It is not a list of features, so it was left alone.

## Standing rules until the port ships

- No new features. Only fixes and the work in steps 1 to 3.

## Housekeeping in step 3

- **Third-party notices.** One file covering Better Hitreg, Freelook++, AppleSkin, HitColor and the bundled fonts, with the Apache license text included. Modrinth lists Better Hitreg as Apache-2.0; the repo currently carries only the MIT `LICENSE`.
- **Repo cleanup.** A short contributor and agent guide in place of the history in `AGENTS.md`. The reports at the repo root move under `docs/`.

## Goals

- Release within two weeks of Sodium supporting a new Minecraft version (loose goal, once step 5 is done).
- A numeric per-frame overhead budget that blocks releases. The value is set in step 3.

## Still to verify

- Whether 26.3 is the newest Minecraft release. Sodium listings showed builds for 26.1, 26.2 and 26.3 on 2026-11-10; the newest was not confirmed.
- Apollo's license terms, and whether registering its plugin channel is how servers identify Lunar Client.
- Whether Better Hitreg's Apache-2.0 listing matches the terms its author gave for the integration.

## Costs accepted knowingly

- Stepping through every intermediate version adds build-and-boot passes on versions that are never released.
- Friends and status is a backend to operate, even without chat.
