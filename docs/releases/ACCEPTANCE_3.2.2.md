# Aurora 3.2.2 / Minecraft 26.1.2 acceptance

The checkpoint runtime is unchanged apart from waiting for a world with no
screen before starting scripted diagnostics. Packaging adds the explicit
Minecraft filename and verified release tooling; project contact URLs now point
to the repository. The development build supports a disposable run directory,
including the relative path required by this older Loom on Windows.

Java: 652 discovered, 651 passed, one opt-in stress test skipped. Python: all 34
release/default tests passed. `gradlew test build` passed. A fresh six-report
baseline boot applied all 73 mixins, reported all features working, logged no
Aurora warnings/errors and closed normally. Runs 3-6 pass the unchanged overhead
check: mean 0.153-0.229 ms, p95 0.187-0.362 ms, zero UI fills/glass captures.

Windows 11, Java 25, RX 9070 XT, Loader 0.20.1, Fabric API 0.155.3+26.1.2,
854x480 / GUI scale 2, 120 fps cap, 32-chunk render distance, factory Aurora
settings. A copied world was upgraded using a temporary helper; that helper was
removed before measurement and is not packaged. All instances stayed on the
secondary portrait monitor. Live settings and the live save were not modified.

The earlier pinned performance/Sodium-Iris/VulkanMod matrix and visual evidence
in [the port report](../port/26.1.md) still describe the same runtime hooks.
VulkanMod glass appearance, held-key controls, multiplayer, shader packs and
every HUD state retain the documented validation limits. This is an archived
checkpoint artifact; the subsequent step 5 extraction ships on 26.3.
