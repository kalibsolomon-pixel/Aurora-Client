# Aurora Client

Terms used for Aurora's distribution and compatibility promises.

## Language

**Minecraft-specific artifact**:
An Aurora JAR intended for one declared Minecraft release line. Its availability
does not promise ongoing feature work for that Minecraft version.
_Avoid_: universal JAR, modern-version JAR

**Checkpoint release**:
A published artifact from an intermediate forward port, with its own build and
runtime evidence. New feature development continues on the newest release.
_Avoid_: parallel feature release

**Frozen version**:
The previous maintained Minecraft version, eligible only for crash and security
fixes.
_Avoid_: active version

**Compatibility set**:
Pinned mods tested together with Aurora on a specified rendering backend.
_Avoid_: works with every mod

**Overhead budget**:
The release limit on Aurora's own measured per-frame work under the documented
factory-default workload.
_Avoid_: total Minecraft frame time
