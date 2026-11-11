# Contributing to Aurora Client

Aurora is a client-side Fabric mod. Start with [AGENTS.md](AGENTS.md),
[the architecture](docs/ARCHITECTURE.md) and [the roadmap](docs/ROADMAP.md).
Feature development targets the newest Minecraft version. Separate older-version
artifacts do not require every change to be backported.

## Set up

Install Java 25, clone the repository and use the checked-in Gradle wrapper:

```sh
./gradlew test build
python -m unittest discover -s tools -p 'test_*.py'
```

On Windows use `gradlew.bat`. No IDE or launcher is required. The runtime artifact
is `build/libs/aurora-<version>-mc<minecraft>.jar`; core and ports are bundled.

Use `./gradlew runClient -Paurora.runDir=build/contributor-game` for a disposable
game directory. Create a throwaway world there. Never upgrade a personal save for
a port test. The default `run/` directory may contain the owner's live settings.

## Choose work

Bug fixes, documentation, tests and port improvements are welcome. The 26.3 port has
shipped; work continues in roadmap order. Discuss feature proposals in an issue
first; decisions are recorded in [ADRs](docs/adr/). Favor small PRs
with one concrete problem and a clear before/after example.

Native Minecraft/Fabric code belongs in the version adapter. Pure policy belongs
in core. Add a port when there is a real consumer, rather than wrapping every
native class. Preserve fail-soft mixins and the fair-play defaults.

## Verify a change

Run relevant tests and the full build before submitting. Changes to defaults,
mixins or rendering also need a dev-client boot. UI work needs screenshots at
small and large viewports, grid/list, Dark, and the live and factory settings of
affected theme tokens. Keep whole words and centered text. Record exactly what
you verified and any gaps; compilation is not visual proof.

Register callbacks through `Timed`. Check per-frame allocation, growing-list
complexity and fill volume. Performance claims need measurements against the
[unchanged budget](docs/OVERHEAD_BUDGET.md). Compatibility evidence must identify
Minecraft, mod versions and graphics backend. Iris is an OpenGL check.

Generated docs and licenses have generators under `tools/`; update their sources
and regenerate them. Keep dependency notices complete. Check LF line endings and
avoid committing personal configs, logs, tokens, caches or test worlds.

## Submit

Use a branch and open a PR describing the trigger, resulting behavior and checks.
Link an issue when relevant and name dependencies if the PR is stacked. Update
the corresponding reference or ADR in the same change.

Report crashes with a redacted log and **Copy Diagnostics**. For vulnerabilities,
follow [SECURITY.md](SECURITY.md).
