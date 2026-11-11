# Published Minecraft-specific artifacts, 2026-11-11

Each runtime JAR was built and tested by the protected release workflow at its
exact source commit, verified as a draft, published immutably, and anonymously
redownloaded for identity, size and SHA-256 checks. Only Minecraft 26.3 is Latest.
Players install one matching JAR with Java 25, Fabric Loader and Fabric API;
core (and ports on 26.3) are already included.

| Minecraft | Aurora release | Maintenance | Exact source |
|---|---|---|---|
| 26.1.2 | [3.2.2](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.2) | Archived checkpoint | [268d600](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/268d600bf70579cc63670d561aba35a930ec3e19) |
| 26.2 | [3.2.3](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.2.3) | Crash/security fixes only | [a57de0f](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/a57de0f54fd90c2cec651a92653cf35a888e214d) |
| 26.3 | [3.3.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.3.0) | All feature work; includes step 5 | [3562d09](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/3562d0907aba7178be9262afae314c845d92149a) |

## Artifact identity

The maintenance descriptions above record the checkpoint policy at publication.
[ADR 0008](../adr/0008-full-feature-parity-across-supported-versions.md) supersedes
that policy for future releases: all three lines receive full feature parity
from the common source tree. These published binaries remain immutable.

- [aurora-3.2.2-mc26.1.2.jar](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/download/v3.2.2/aurora-3.2.2-mc26.1.2.jar), 3,284,354 bytes. [Verification metadata](evidence/3.2.2-artifact.json); SHA-256 `53b654d2f237b0c42843183ac6e1e1ce6e93f59ec3ef2a1d974cab079b9296db`.
- [aurora-3.2.3-mc26.2.jar](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/download/v3.2.3/aurora-3.2.3-mc26.2.jar), 3,267,031 bytes. [Verification metadata](evidence/3.2.3-artifact.json); SHA-256 `4fa7ddb76d35cdea724c566902858f45c4a228c63c0d4468ce91ff2774eb5941`.
- [aurora-3.3.0-mc26.3.jar](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/download/v3.3.0/aurora-3.3.0-mc26.3.jar), 3,287,057 bytes. [Verification metadata](evidence/3.3.0-artifact.json); SHA-256 `b23f1919d432bc3ff3cc375ae06c5fbec3433da7cf03b4cec798133135887aee`.

## Acceptance and limits

26.3: 660 Java tests passed, one skipped; 34 Python tests passed. Five applicable
six-report matrix boots passed on OpenGL and native Vulkan, with all 73 mixins.
The grid/list and title/menu glass were inspected on both backends at small and
large viewports and both relevant theme settings. See [step 5 acceptance](../port/step-5.md).

26.1.2: 651 Java tests passed, one skipped; 34 Python tests passed. A new six-run
baseline gate passed with all 73 mixins. Its source branch records
[acceptance](https://github.com/kalibsolomon-pixel/Aurora-Client/blob/release/mc26.1.2/docs/releases/ACCEPTANCE_3.2.2.md)
and per-run evidence. The earlier pinned port matrix still covers unchanged
runtime hooks.

26.2: 648 Java tests passed, one skipped; 34 Python tests passed. New six-run
baseline gates passed on OpenGL and Vulkan with all 72 mixins. Its source branch
records [acceptance](https://github.com/kalibsolomon-pixel/Aurora-Client/blob/release/mc26.2/docs/releases/ACCEPTANCE_3.2.3.md)
and per-run evidence. Two interrupted Vulkan attempts were invalidated by a
pause screen; the final unmodified run passed. Earlier pinned stack and
Iris/OpenGL checks still cover unchanged runtime hooks.

All judged gates use the unchanged two-warm-up rule and budget. Iris is an
OpenGL check; Vulkan shader mods need their own pins and shader-pack acceptance.
Multiplayer, a production launcher, low-end hardware and every feature/mod/screen
combination remain unverified. All 46 live configuration/options files match the
pre-port backup byte-for-byte. Test saves were disposable copies.

The workflow applies Latest selection at publication (PR #14), because GitHub
did not preserve that flag from draft creation. Release tags, source targets and
immutable assets were not rewritten. Launcher-manifest rollout is separate from
these GitHub downloads.

To independently recheck a public JAR:

```sh
python tools/verify_release_artifact.py public \
  --repo kalibsolomon-pixel/Aurora-Client \
  --metadata docs/releases/evidence/3.3.0-artifact.json \
  --download-dir build/public-release-verification
```

## 3.4.0 parity release

[Aurora Client 3.4.0](https://github.com/kalibsolomon-pixel/Aurora-Client/releases/tag/v3.4.0)
is the first release with all three supported lines built from one tree (ADR 0008)
and is Latest. Exact source: [7b51088](https://github.com/kalibsolomon-pixel/Aurora-Client/commit/7b510887c67a3884bd1ef300c7ac344d4a027505).
Published through the protected workflow (run 43064127581) as a verified draft
with that exact target, then anonymously redownloaded and verified.

- aurora-3.4.0-mc26.1.2.jar, 3,306,957 bytes; SHA-256 `7df204d3c59892d10fd16a3a382d2ace27383aaed0682fbb0688a3d707b37100`.
- aurora-3.4.0-mc26.2.jar, 3,285,147 bytes; SHA-256 `7dce642c6fdadc8788eed24db1e9a73bb82d6ecffe7ebaab71fee3727ec81b9e`.
- aurora-3.4.0-mc26.3.jar, 3,292,796 bytes; SHA-256 `854ee8a7ff9adc4810a9f8f6df68b7f1338ea380a68ff8b9b008c44e32df5fe6`.

Acceptance is [step 5.5](../port/step-5.5.md) and its
[per-run evidence](evidence/3.4.0-parity.json). The first dispatch (run 43062815017)
stopped at the evidence gate before any GitHub write: the fingerprint's file order
differed between Windows and Linux. The verifier was fixed (PR #19) without editing
the evidence, and the same pinned source was dispatched again.
