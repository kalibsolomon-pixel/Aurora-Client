---
status: accepted
date: 2026-11-10
---

# Aurora is one open-source mod that runs in any launcher

Aurora's reason to exist is being a single MIT-licensed Fabric mod with one coherent UI that runs next to Sodium in any launcher. The incumbents (Lunar, Badlion, Feather) are closed launchers with bundled forks, and a pack of separate mods has no coherent UI, so this is the position they cannot copy. Aurora Launcher is the default way to run it, but the mod must behave in a stranger's mod folder and never depend on the launcher.

## Considered options

- **A modpack of per-feature mods sharing a UI library.** Rejected: no performance or structural gain, and it multiplies release and compatibility work.
- **Launcher-only distribution with a pinned mod set.** Rejected: it gives up the "runs anywhere" position.

## Consequences

- No automatic telemetry or crash upload. Feedback is a "copy diagnostics" report the player sends by hand.
- No paid cosmetics.
- Social features are limited to a friends list with online status. No chat. Identity is proven through Mojang's session check, so Aurora never handles a password or token. Social is optional, off until the player signs in, and sharing which server you are on is a separate opt-in. World sharing and joining is a possible later addition and is not designed.
- The mod must be fully functional with social features unused.
