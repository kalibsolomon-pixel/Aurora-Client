# Aurora Client

Terms used for Aurora's distribution and compatibility promises.

## Language

**Minecraft-specific artifact**:
An Aurora JAR intended for one declared Minecraft release line. Supported lines
receive full feature parity; historical checkpoint availability alone does not
make a line supported.
_Avoid_: universal JAR, modern-version JAR

**Checkpoint release**:
A published artifact from an intermediate forward port, with its own build and
runtime evidence. The published artifact stays immutable when later releases
bring that line into full feature parity.
_Avoid_: parallel feature release

**Supported line**:
A Minecraft release line that receives the complete current Aurora feature set
and passes its own compatibility and overhead acceptance.
_Avoid_: frozen version, legacy feature subset

**Parity window**:
The growing set of supported Minecraft lines. Adding a new line does not remove
feature support for an existing line.
_Avoid_: newest-version-only support

**Parity release**:
One Aurora release containing a matching Minecraft-specific artifact for every
supported line, with the same features and shared release identity.
_Avoid_: universal JAR, backport train

**Compatibility set**:
Pinned mods tested together with Aurora on a specified rendering backend.
_Avoid_: works with every mod

**Overhead budget**:
The release limit on Aurora's own measured per-frame work under the documented
factory-default workload.
_Avoid_: total Minecraft frame time
