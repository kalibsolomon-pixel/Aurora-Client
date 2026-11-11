# Aurora Client 3.2.2 for Minecraft 26.1.2

This is a separate Minecraft-specific artifact from the sequential forward port.
Install `aurora-3.2.2-mc26.1.2.jar` with Java 25, Fabric Loader and the matching
Fabric API. Do not combine it with another Aurora JAR or use it on another
Minecraft line. Existing configuration and profiles are preserved.

The release includes the fail-soft feature foundation, fair-play defaults,
Minecraft-free core, overhead diagnostics and third-party notices. The runtime
JAR bundles core; players do not install an additional core library.

Feature work continues on Minecraft 26.3. This checkpoint is archived.
The 26.3 release includes the subsequent adapter extraction. This artifact does
not claim that later work was backported.

The release verifier checks the exact source, embedded Minecraft requirement,
version-qualified filename, size and SHA-256. Publication verifies a draft first.
Runtime acceptance and limitations are recorded in the accompanying port report
and release evidence. Shader packs, multiplayer and every third-party UI
combination are not claimed as tested. Vulkan on this Minecraft line uses the separately tested VulkanMod build.
