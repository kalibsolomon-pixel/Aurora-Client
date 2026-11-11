# Aurora 3.2.3 / Minecraft 26.2 acceptance

The checkpoint runtime is unchanged apart from waiting for a world with no
screen before scripted diagnostics. Packaging adds Minecraft-qualified filenames
and verified release tooling. Project contact URLs point to the real repository.
The subsequent adapter extraction is part of 26.3, not this checkpoint.

Java: 649 discovered, 648 passed, one opt-in stress test skipped. Python: 34
passed. `gradlew test build` passes. Final baseline boots on OpenGL and native
Vulkan produced six reports each, applied all 72 mixins, reported all features
working, logged zero Aurora warnings/errors and closed normally. After two
warm-ups, all four judged runs on both backends pass the unchanged overhead gate.
OpenGL: mean 0.215-0.220 ms, p95 0.296-0.302 ms. Vulkan: mean 0.170-0.188 ms,
p95 0.203-0.241 ms. Judged runs have zero UI fills and glass captures.

Windows 11, Java 25, RX 9070 XT, Loader 0.20.1, API 0.161.0+26.2, 854x480 /
GUI scale 2, 120 fps cap, 32-chunk render distance, factory Aurora settings in
a copied 26.2 world. All windows stayed on the secondary portrait monitor.
The live save/settings were untouched. Two earlier Vulkan attempts opened a
pause screen and are excluded as invalid, not as budget passes. A temporary
read-only probe confirmed the fixture path and pause-on-focus-loss setting;
it was removed before the final accepted run and is not packaged.

The earlier performance-stack matrix on OpenGL/Vulkan, Sodium + Iris on OpenGL,
and targeted visual evidence in [the port report](../port/26.2.md) still describe
the same runtime hooks. Iris is an OpenGL shader mod, so forced-Vulkan Iris
failures are excluded from required acceptance. Vulkan shader mods such as Beryl
need separate compatible pins and shader-pack evidence; none is claimed here.
Multiplayer, a production launcher, low-end hardware and every feature/screen
combination retain the documented limits. This line is frozen to crash and
security fixes after publication.
