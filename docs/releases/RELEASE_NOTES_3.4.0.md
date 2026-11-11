# Aurora Client 3.4.0

The source now builds the same 38-feature client for Minecraft 26.1.2, 26.2
and 26.3. Install the JAR matching your Minecraft version with Fabric Loader
and Fabric API. Java 25 is required.

Feature registration, screens, controls and common assets share one
implementation. Version adapters handle Minecraft's rendering, mixins and
keyboard APIs. Existing GLFW keybindings migrate to the shared config/profile
format; native controls remain synchronized with Aurora's bindings.

Every parity release builds all three JARs from one source commit and requires
each supported line's compatibility and overhead checks to pass. Published
3.2.2, 3.2.3 and 3.3.0 checkpoints retain their original identities.

This restructure adds no gameplay features or new defaults. Shader-drawn
shapes remain roadmap step 6. See [step 5.5 acceptance](../port/step-5.5.md)
for verification coverage and limits. These are candidate notes; merging the
restructure does not publish a release.
