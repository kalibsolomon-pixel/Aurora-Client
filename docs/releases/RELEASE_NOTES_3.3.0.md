# Aurora Client 3.3.0 for Minecraft 26.3

Aurora now builds for Minecraft 26.3 with SDL input and native OpenGL/Vulkan
rendering. Install this JAR only on the declared Minecraft version with Java 25,
Fabric Loader and Fabric API. Separate 26.1.2 and 26.2 artifacts are provided from
their checkpoint sources; no universal JAR is used.

The restructure separates native-free core policies and five small ports from
the version adapter. The adapter owns every mixin, glass renderer and Fabric
hook. Existing settings and imported profiles migrate physical key bindings
independently. Rendering preserves the native pipeline, transparent-item glint,
armor hurt tint and trims.

Fail-soft mixins report broken features and hold them off for the session without
overwriting saved settings. Advantage defaults are off, including Full Bright,
No Fog, FreeLook and player health. Existing installs retain migrated choices.

Contributor setup, issue forms, PR guidance, current architecture and private
security reporting are now documented. Integrated code/font/library licenses
ship in the JAR. No new gameplay features or telemetry are added.

See [step 5 acceptance](../port/step-5.md) and the pinned [mod matrix](../MOD_MATRIX.md)
for measured conditions and limits. Iris checks apply to OpenGL only; Vulkan
shader mods are separate compatibility targets. Shader packs, multiplayer and
every third-party screen combination are not claimed as tested.
