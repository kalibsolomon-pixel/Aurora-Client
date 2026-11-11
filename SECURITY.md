# Security policy

## Reporting

Report a suspected vulnerability privately through
[GitHub private vulnerability reporting](https://github.com/kalibsolomon-pixel/Aurora-Client/security/advisories/new).
Include the affected Aurora and Minecraft versions, a minimal reproduction and
the expected impact. Remove tokens, account details, personal paths and server
addresses from logs. Avoid posting exploit details in public issues.

The newest published Minecraft version receives fixes and features. The previous
maintained version receives crash and security fixes; older checkpoint artifacts
are archived. Acknowledgement and fix timing depend on maintainer availability.

## Scope and trust

Aurora is a local client-side Fabric mod. Its config/profile imports, downloaded
Modrinth metadata and images, launcher activity bridge, and release pipeline are
relevant security surfaces. Local files, server-provided content and remote
metadata are inputs to evaluate, rather than proof of trust. Installing arbitrary
third-party mods already permits code to run inside the game process.

## Properties to preserve

- No automatic telemetry or crash uploads; copied diagnostics omit account,
  world and server names. Review a report before sharing it.
- Import and download paths must not escape their intended storage locations.
- Config/profile saves remain atomic and ordered; an import must not execute code.
- The mod must work without Aurora Launcher and never collect account passwords
  or tokens for optional planned social features.
- Release assets must match their recorded source, identity, size and digest.
  Publication verifies a draft first and never rewrites an immutable release.

## Assessment context

Report reachable violations with their actual input path and impact. Tests and
architectural intentions are evidence to inspect, not proof of safety. There are
no finding-class exclusions or newly accepted security risks in this policy.
This document is guidance, not a claim that the repository has had a security
audit. Third-party dependencies and Minecraft remain part of the runtime.
