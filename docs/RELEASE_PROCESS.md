# Aurora Client release process — immutable-release rules

Codified 2026-09-28 after the v2.1.4 publication incident
(`BRIDGE_V2_RELEASE_ACCEPTANCE.md`, "Publication outcome addendum"): a release
was created published-first without an explicit target commit, its recorded
`target_commitish` could not be repaired on an immutable release, and the
delete/recreate "repair" permanently burned the `v2.1.4` tag name. This file is
the authoritative procedure so that class of incident cannot repeat.

## Immutable facts about GitHub releases in this repository

- Published releases are **immutable**: metadata cannot be edited after
  publication, and **deleting a published release does not free its tag name**.
  A tag name used by a published release can never host another release (and,
  once burned, the tag ref itself cannot even be re-created by push).
- Therefore: a publication is a **one-way, one-shot action**. Everything
  verifiable must be verified while it is still a draft.

## Required publication sequence (no step may be skipped or reordered)

All tooling references are `tools/verify_release_artifact.py`.

1. **Candidate verification (local, before any GitHub write).**
   - `inspect` against the exact source tree → candidate metadata JSON
     (version, tag, source SHA, filename, size, SHA-256, requirements).
   - `verify` the metadata against the built JAR directory.
   - `collision` — the intended tag AND release must be unused. A burned tag
     name fails here and is a hard stop, never a retry.
   - **Overhead budget (ADR 0005).** Boot the candidate with factory defaults
     and six diagnostics runs, then `python tools/overhead_check.py`. Over
     budget is a hard stop. Protocol: `docs/OVERHEAD_BUDGET.md`.
2. **Create the release as a DRAFT with an explicit exact target.**
   ```sh
   gh release create v<X.Y.Z> \
     --repo kalibsolomon-pixel/Aurora-Client \
     --draft \
     --target <exact source commit SHA, full 40 hex> \
     --title "Aurora Client <X.Y.Z>" \
     --notes-file <notes> \
     <exact approved aurora-<X.Y.Z>.jar>
   ```
   The `--draft` and `--target <full SHA>` flags are mandatory. Never create a
   published release directly.
3. **Verify the draft** with the `draft` subcommand (it locates the draft via
   the authenticated release listing — drafts are not reachable through
   `releases/tags/<tag>` — and fails closed unless the draft's
   `target_commitish` equals the exact source SHA, the asset's name/size/state
   and digest match the candidate, and the release is still a draft).
4. **Publish as a separate, explicit step.**
   ```sh
   gh release edit v<X.Y.Z> --repo kalibsolomon-pixel/Aurora-Client --draft=false
   ```
5. **Verify the publication read-only** with `public` (published metadata,
   immutability, anonymous download, hash/size re-verification).

## After publication: read-only, no automatic recovery

- Verification after publication is **read-only**. The tooling has no
  delete/recreate/repair path, and none may be improvised.
- If any post-publication check fails (wrong target metadata, wrong digest,
  anything): **STOP for owner review.** Do not delete the release, do not
  replace it, do not modify historical immutable artifacts, and do not touch
  the tag.
- The recovery for a burned publication identity is a new patch version with a
  fresh owner review — exactly what 2.1.5 is for 2.1.4.

## Never

- Never publish without a verified draft stage.
- Never omit `--target <exact SHA>`.
- Never delete or recreate a published release to repair metadata.
- Never reuse or re-push a tag that a published release has used.
- Never force-push tags or rewrite pushed history.

## Separate Minecraft artifacts (2026-11-11)

Every release contains one Minecraft-specific runtime JAR named
`aurora-<version>-mc<minecraft>.jar`. The verifier checks that the filename,
source `minecraft_version` and embedded Fabric requirement agree. It rejects
mixed or ambiguous output directories. Core and ports are included in that JAR.

The initial distribution uses fresh, distinct Aurora versions: 3.2.2 for the
26.1.2 checkpoint, 3.2.3 for 26.2, and 3.3.0 for 26.3 with the layer extraction.
Each release targets its own exact source commit and uses the existing immutable
`v<version>` tag scheme. These are separate branch builds, not simultaneous
multi-version compilation. Leave `make_latest` off for checkpoint releases;
only the newest Minecraft line is marked latest.

Local runtime acceptance evidence, including the overhead check, is required
before dispatch. Hosted CI builds cannot stand in for a GPU boot. The protected
environment, draft verification, exact target and anonymous download checks
remain mandatory even when GitHub work is authorized autonomously.

## Latest release selection

Pass `--latest` explicitly when publishing the verified draft, as well as when
creating it. GitHub did not preserve the draft's `--latest=false` selection for
the 26.1.2 checkpoint; its asset identity remained correct. The workflow now
applies `make_latest` at publication. Only the newest Minecraft line is latest.

The repository's Latest label is a distribution pointer. Selecting an already
verified newest release as Latest changes no tag, source target or runtime
asset. This does not permit rewriting a published release's identity or repairing
failed artifact verification. Those failures still require owner review.
