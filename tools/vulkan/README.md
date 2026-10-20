# Native renderer compatibility fixture

These opt-in tools launch real Minecraft clients in new directories below
`build/vulkan-investigation/`. They never launch against the live `run/` directory.
The gameplay driver modifies only its marked, copied world and configuration.
It is not included in the Aurora distributable. No account credentials are used;
normal offline-profile authentication errors are expected. No multiplayer service
is contacted for gameplay.

Prerequisites: Windows, Java 21, the project's normal Gradle/JDK environment,
Python 3.12+ with Pillow, and cached official Minecraft 1.21.11 assets/client at
`%APPDATA%/.minecraft`. Set `AURORA_COMPAT_JAVA` to the Java 21 executable if the
author's bundled Launcher runtime is not installed. Sodium regression currently
uses the retained `aii-s1` input; substitute a verified Sodium 0.8.14+mc1.21.11
artifact in `production.py` on another host. Driver installation is outside this
fixture.

From the repository root:

```powershell
python tools/vulkan/setup.py
.\gradlew.bat -I tools/vulkan/runtime.init.gradle writeVulkanLaunch vulkanProbeRemapJar build --console=plain
python tools/vulkan/production.py control-02 --renderer vulkan
```

On a fresh evidence directory, create a Creative world named **New World** in
that control client. Enter gameplay, take F2, save and quit normally. Each later
fixture copies this saved input. Never overwrite or reuse an existing output name.

```powershell
python tools/vulkan/production.py candidate-vulkan --aurora --probe
python tools/vulkan/review.py build/vulkan-investigation/candidate-vulkan
python tools/vulkan/production.py candidate-opengl --aurora --probe --renderer opengl
python tools/vulkan/review.py build/vulkan-investigation/candidate-opengl
python tools/vulkan/production.py candidate-sodium --aurora --probe --renderer sodium
python tools/vulkan/review.py build/vulkan-investigation/candidate-sodium
```

`production.py` uses the real remapped mod jar, Mojang's client and normal Fabric
Loader, without Loom development mode or dependency-check overrides. It pins the
Minecraft/Fabric inputs, verifies Minecraft library SHA-1s and records SHA-256s of
runtime jars. The original VulkanMod jar loads its own published nested libraries.

`reproduce.py` additionally supports named-namespace development isolation, with
or without Aurora. It freezes Aurora classes/resources and explicitly supplies
the exact LWJGL nested libraries stripped by Loom's development remap. `--quick`
skips the detail-screen traversal for targeted iterations. The full probe visits
all registered detail screens, moves/attacks in a real world, exercises world/HUD
render hooks, maps, glint toggles, custom crosshair, material controls, pool pressure,
reload/reset, resize/GUI scale and dimension transitions, then exits normally.
Interactions are through Minecraft APIs; they are not physical-input benchmarks.

Capture runs after rendering and before present. Capturing at frame head under
VulkanMod observes the freshly cleared target and produces invalid black evidence.
`review.py` rejects black captures, mirrored stripe phase, ineffective blur,
incorrect lighting direction, failed runtime assertions, relevant renderer errors,
and abnormal exits. Pixel coordinates are for this fixed 1280x720/GUI-scale-2
fixture; resize captures are separate. It intentionally rejects the first,
vertically mirrored implementation retained in `gameplay-03`.

Reports and complete logs stay in each fixture directory. `input-manifest.json`
records immutable inputs; `launch-private.json` is local launch configuration.
Do not publish launch files from credentialed sessions. The checked-in Phase B
report identifies the retained acceptance evidence and its limits.
