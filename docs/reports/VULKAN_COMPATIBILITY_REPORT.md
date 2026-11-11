# Phase B — VulkanMod incompatibility

Investigation: 2026-10-19. Starting revision:
`f74ad7c7a6bd15853394c6a18a5affa37efdd50a` (`codex/unified-mapping`).

The owner's prior incompatibility is an established finding. This investigation
reproduced an Aurora-specific native rendering crash and corrected its mechanism.
It does not retroactively identify the cause of the owner's earlier initialization
crash, whose exact configuration and crash artifact were not supplied.

**Result:** the corrected packaged client passes actual singleplayer gameplay
and rendering validation with VulkanMod on the pinned Windows/AMD configuration.
Vulkan, OpenGL and Sodium each pass 66 native checks and 60 capture reviews;
575 automated tests pass. GPU-timestamp Reflex pacing remains unavailable on
Vulkan for the API reason documented below.

## Controlled environment

| Input | Value |
|---|---|
| Minecraft | 1.21.11, official client SHA-1 `ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd` |
| Fabric Loader / API | 0.19.2 / 0.141.4+1.21.11 |
| Game Java | Microsoft OpenJDK 21.0.7+6, Windows x64 |
| VulkanMod | Published 0.6.8+1.21.11, Modrinth version `hWKRTXbQ` |
| VulkanMod SHA-512 | `cbc25c9baee5e3d0e187476ce0b461a5b278341140df9764f4032d1186d8eab8607680480ce09f63887772a7547626d57b11b6a8a90bd5f53de0196bbfc573bb` |
| GPU / driver | AMD Radeon RX 9070 XT / 32.0.31041.1004, driver date 2026-08-17 UTC |
| OS / CPU | Windows 11 build 26100.9711 / i7-14700F |
| Regression renderer | Sodium 0.8.14+mc1.21.11, independently from VulkanMod |
| Shared game settings | 2048 MiB heap; 60 FPS; VSync off; render/simulation distance 8/5; 1280×720; GUI scale 2 |

All runs use new, marked directories below `build/vulkan-investigation/`.
The live `run/`, installed instance configurations and original saved worlds are
not test targets. The baseline Creative world was created and saved in
`control-02/game/saves/New World`; acceptance runs copy that identical saved input.
Driver, Minecraft and Java were held constant. No driver or OS settings were changed.

The initial development pair (`control-01`, `aurora-01`) failed identically with
`NoClassDefFoundError: org/lwjgl/vulkan/VkApplicationInfo`. Loom's local development
remap strips nested libraries, so the fixture was incomplete. Supplying the exact
LWJGL jars from the published VulkanMod artifact fixed **both** launches' setup.
That shared initialization failure is not an Aurora defect or the explanation
for the incompatibility. Normal production launches load the published nested
libraries directly and do not need this development workaround.

## Established failure mechanism

The complete control (`control-02`: Fabric API + VulkanMod, without Aurora)
entered an actual world, rendered it, saved an F2 screenshot, saved the world and
exited normally. The corresponding Aurora run (`aurora-02`) initialized Vulkan
successfully, loaded atlases and then crashed when Aurora's title screen rendered.

The native crash report identifies this chain on the render thread:

```text
AuroraTitleScreen.render
  ButtonWidget.renderGlassPass → GlassSurface.paint
  BlurPanelRenderer.renderUnifiedPanel → renderPanelInternal
  ensureReady → createProgram → compile
  org.lwjgl.opengl.GL20.glCreateShader
  org.lwjgl.opengl.GL20C.glCreateShader
  EXCEPTION_ACCESS_VIOLATION in lwjgl_opengl.dll + 0xe1b7
  read address 0x0000000000000e38
```

The same failure was reproduced with normal Fabric loading in
`production-original-01`, using a production-remapped reconstruction of the
unchanged classes/resources frozen at investigation start. This is a snapshot of
this checkout's original implementation, not a claim about the owner's previously
tested release jar. There is no Loom development mode, dependency-check override,
Sodium or other performance mod in this reproduction.

Targeted isolation (`isolate-original-01`) disabled only the original custom
title screen in a disposable config/profile. That unchanged original build then
entered Vulkan gameplay, rendered HUDs, colored glint, hitboxes and a block overlay,
and executed movement and an attack. Opening the HUD editor's first glass button
reproduced the same native GL20 shader fault. This independently localizes the
fatal path to glass rendering rather than Vulkan initialization, all Aurora world
rendering, or the title-screen mixin alone. Disabling a title screen is not the
delivered fix: the corrected build keeps it and the HUD editor working.

VulkanMod's Window mixin requests `GLFW_CLIENT_API = GLFW_NO_API` and replaces
`RenderSystem.initRenderer` with a `VkGpuDevice`. There is no current OpenGL context
or LWJGL OpenGL dispatch table. Aurora called native GL20 shader creation before
checking the backend. The fault occurs in native code, so the Java `catch` in
`ensureReady` cannot provide recovery.

Simply catching another exception or skipping shader compilation would not fix
the pipeline. It subsequently creates raw GL FBOs and VAOs, attaches texture IDs,
blits the main target, sets GL viewport/sampler/pixel-store state, performs raw GL
draws and calls `glReadPixels`. None of those assumptions describes a Vulkan
command buffer or texture. VulkanMod intercepts selected GL compatibility methods;
it does not turn this entire raw GL20/FBO/readback pipeline into Vulkan rendering.
Its `VkGpuTexture` even subclasses `GlTexture`, so checking a **texture's** class
would incorrectly admit the old GL path. The correction checks the active device.

Two other entry points were audited:

* `ReflexScheduler.beginFrame` called `GL.getCapabilities()` on the Vulkan thread.
  The original log independently demonstrates `IllegalStateException: No
  GLCapabilities instance set`. Its existing guard disabled pacing, so this was a
  recoverable capability failure, not the fatal shader crash. The corrected path
  detects the device before any GL capability/timestamp call and reports the
  unavailable optimization once at INFO.
* `BlurTestScreen` had a separate raw GL synthetic-backdrop compiler/FBO path.
  Its Solid/Stripes modes now use GPU render passes too. All remaining production
  OpenGL dispatch is contained in the device-gated glass and Reflex paths. The
  canvas warning's hardware description now uses `GpuDevice.getRenderer()`; the
  original `GlStateManager._getString` path is not independently established as a
  crash, because VulkanMod also intercepts that wrapper.

## Correction

`RenderBackend.isOpenGl()` identifies `GlDevice`, without invoking GL as a probe.
The original OpenGL/Sodium glass implementation stays selected for that device.
Other devices use `PortableBlurRenderer`:

1. Sample a padded main-target region into a half-resolution GPU attachment.
2. Sample that into a quarter-resolution attachment, preserving the two-stage
   downsample that prevents the historical shimmer regression.
3. Run the same horizontal and vertical nine-tap Gaussian.
4. Composite the same rounded coverage, raised/depressed lighting, rim retirement
   and optional 135% saturation / 92% brightness filter.
5. Submit the distinct result texture through the standard deferred GUI blit.

All GPU work uses Blaze3D's `GpuDevice`, command encoder, render passes, samplers,
textures and uniform buffers. There is no raw GL texture ID, native GL shader
compile, framebuffer binding or synchronous CPU readback in the Vulkan path.
The selected backend owns GPU synchronization and physical resource retirement.

The existing 64-output priority policy, screenshot suppression, backdrop validity,
theme opacity and pre-dim layering rules are retained. Outputs stay unique for
concurrent deferred GUI draws. Each submitted pass uses immutable uniforms; a
later panel cannot overwrite a buffer still referenced by a Vulkan command
buffer. Replaced outputs/buffers retire after the GUI frame that referenced them.
Reset releases the portable pool and allows reconstruction.

The first implementation carried a bottom-left framebuffer convention into the
Vulkan path. Pixel comparison caught mirrored stripe phase despite a successful
launch. VulkanMod's negative-height viewport is now accounted for explicitly:
shader interpolation and attachment/sampler coordinates remain top-down at every
stage. The retained failed images score **−0.912** correlation with the external
stripe reference; corrected images score **+0.912**, with contrast reduced to
**0.714×** the unblurred reference. Solid input stays unchanged within one channel
value, and raised/depressed lighting reverses in the correct direction. These are
actual rendered pixels, not a CPU-only shader approximation.

## Validation and evidence

The final distributable under test is `build/libs/aurora-3.0.0.jar`, SHA-256:
`511f36b74c48a328ab5bb356e547818bc42a1e63b2b3acda1b50cf01abeb5ea9`.
The Vulkan/OpenGL/Sodium fixtures use identical copies of that jar. The test mod
and Vulkan dependency are opt-in tooling; ordinary jar inspection finds no
`vulkanprobe`, `mappingprobe` or research classes.

The final native matrix is recorded under `final-vulkan-02`, `final-opengl-02`
and `final-sodium-02`; its completed results are summarized in
`docs/vulkan/acceptance-summary.json`. Each root retains its launch input hashes,
process ID/exit code, complete logs, runtime assertions, pixel review and captures.
Checked-in selected evidence lives in `docs/vulkan/`.

| Normal-loader fixture | Checks | Captures | In-world render frames | Exit |
|---|---:|---:|---:|---:|
| VulkanMod + corrected Aurora | 66/66 | 60 | 6,628 | 0 |
| OpenGL + corrected Aurora | 66/66 | 60 | 6,649 | 0 |
| Sodium + corrected Aurora | 66/66 | 60 | 6,648 | 0 |

In-world render counts include settings screens over a loaded world; they are
not counts of unpaused simulation ticks or a performance benchmark.

The driver runs a real integrated server and client, not mocks. It tests:

* World entry, actual movement and an attack/swing through Minecraft's game mode.
* Fabric entity/block-outline callbacks, colored block fill/outline, hitboxes,
  waypoint beam/labels, minimap uploads, HUDs and both colored-glint paths.
* All 38 registered feature/settings detail screens, custom crosshair canvas,
  hardware query, HUD editor, maps, waypoints, profiles and color picker.
* Solid/stripe material controls, raised/depressed lighting and 100-panel
  pressure against the 64-output priority policy.
* Resource reload, zero portable textures after reset, pool reconstruction,
  bounded portable texture residency, window resize/GUI scale 3 and restoration,
  Nether transition and return to the Overworld, then normal save/shutdown.

Pixel review rejects black/empty captures, mirrored phase, ineffective blur,
wrong lighting direction, failed assertions, relevant renderer errors and abnormal
exit. Capture occurs after rendering and before present. Earlier frame-head
captures (`gameplay-02`) were invalid because VulkanMod had already cleared the
target; their black images were rejected as evidence. The first resize assertion
also incorrectly expected GUI scale 3 at 960×540, below Minecraft's minimum
logical height; the final fixture uses a valid 1152×768 window. Neither test
fixture mistake was disguised as a renderer failure or accepted as a pass.

`gradlew build` passes all **575** existing tests. Native opt-in regression
instructions are in `tools/vulkan/README.md`.

## Limits and remaining questions

* Vulkan itself supports GPU timestamps, but Minecraft's device API and the
  integration used here do not expose the OpenGL timestamp operations consumed by
  Aurora's automatic Reflex estimator. This optimization remains unavailable on
  Vulkan, with an explicit capability message. No synthetic timing or alternative
  latency estimate is substituted. Ordinary frame caps and manual PARK remain
  available; the OpenGL/Sodium estimator is preserved. This is an API integration
  limitation, not evidence that Vulkan cannot measure GPU time.
* These results cover this Windows/AMD host, Minecraft 1.21.11 and the pinned
  VulkanMod build. They do not establish Linux/macOS/NVIDIA/Intel acceptance,
  arbitrary shader/resource packs, every feature option combination, multiplayer
  PvP timing, or a performance/latency improvement. Better Hitreg's timing core was
  not modified.
* A separate paired-window activation failure was investigated, rather than
  attributed to glass: `candidate-01` faulted in `glfw.dll+0x101ab` during event
  polling. A controlled recheck (`pair-control-01`) reproduced the identical
  native offset **without Aurora installed** while a Windows computer-use
  `activate_window` request targeted the occluded Vulkan-only window beside a
  second Minecraft window. Its Java path was vanilla `RenderSystem.limitDisplayFPS`
  into a GLFW event wait. The corrected Aurora client alongside it remained
  alive and then exited normally. See `docs/vulkan/aurora-free-glfw-reproduction.json`
  and the native excerpt. This establishes that Aurora is not necessary for that
  fault; it does not distinguish a GLFW, OS/driver, VulkanMod or automation-helper
  defect. Paired automated window activation remains an unresolved external
  condition. These failed runs are not counted as acceptance evidence, and this
  report does not claim that the Aurora patch repairs that independent fault.
* Offline test profiles cause expected account/Realms authentication errors;
  VulkanMod also logs invalid uppercase `terrain_earlyZ` resource paths. These
  occur independently of Aurora. No account credentials, authentication changes
  or upstream files are patched to silence them. VulkanMod's own Fabulous/
  improved-transparency limitations are not changed by this patch.

Upstream references: [published VulkanMod version](https://modrinth.com/mod/vulkanmod/version/hWKRTXbQ),
[VulkanMod source](https://github.com/xCollateral/VulkanMod). The source checkout
inspected was dev commit `087ef24e58e5522dec5ccb44f279768786bfb6da`;
release behavior is established by the pinned jar and runtime/crash artifacts,
not assumed from a moving development branch.
