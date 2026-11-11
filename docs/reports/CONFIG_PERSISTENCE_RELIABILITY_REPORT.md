# Configuration Persistence Reliability — Report

Date: 2026-10-19 · Branch: `codex/gui-premium-design` · Windows 11, Java 21.0.7, RX 9070 XT.

## A. Root cause

**Field symptom** (fixture logs, baseline and candidate jars alike):

```
[Aurora-ConfigSave/ERROR]: Failed to save Aurora config
java.nio.file.AccessDeniedException: …\config\aurora.json.tmp -> …\config\aurora.json
  at sun.nio.fs.WindowsFileCopy.move(WindowsFileCopy.java:328)
  at java.nio.file.Files.move(Files.java:1431)
  at com.aurora.client.config.AuroraConfig.writeJsonAtomic(AuroraConfig.java:1277)
  at …AuroraConfig.lambda$save$0 … ThreadPoolExecutor … (thread Aurora-ConfigSave)
```

The failing step is the **replace** (`MoveFileExW` with `REPLACE_EXISTING|ATOMIC_MOVE`)
of the destination `aurora.json`, on the single save-executor thread — not the temp write,
not cleanup, not a permission problem.

**Mechanism, established outside Minecraft** (`tools/persistence/ReplaceProbe.java`):

| Experiment | Result |
|---|---|
| Hold an open handle on the **destination** (Java's FILE_SHARE_READ\|WRITE\|DELETE — the most permissive sharing) and replace | **Exactly the field exception**, every time; destination left unchanged |
| Hold an open handle on the **temp** file and replace | Succeeds |
| One thread, Aurora's exact write+replace sequence, 3,000× back-to-back, nothing else in the JVM touching the file | **19 / 3,000** fail with the field exception |
| Same, 5 ms between saves | 0 / 600 |
| Same, while a Restart Manager poller (which briefly opens the file) runs | ~1,860 / 3,000 fail — an outside opener reproduces it at will |

So: **Windows refuses to replace a file that any process has open at that instant, and
reports ERROR_ACCESS_DENIED.** On this machine a background process briefly opens
freshly written files (so the failure clusters in bursts of rapid saves). Restart Manager
could not name it (consistent with a kernel-level scan filter); Defender real-time
protection and Windows Search are both active (read-only check). **The external opener is
not conclusively attributed** — category B (external interference) triggering a category A
weakness:

**Aurora defects that turned the interference into data loss / disorder** (by inspection,
confirmed by tests and native A/B):

1. On replace failure the temp file was deleted and the save **dropped** (logged only).
2. `AuroraConfig.saveBlocking()` and `ProfileManager.saveCurrentBlocking()` wrote inline on
   the shutdown thread to the **same fixed `.tmp` path** the async executor uses; an older
   queued async snapshot could also land **after** the final save.
3. Profiles were written asynchronously but read back from disk: `create()` → `switchTo()`
   **silently failed** (file not there yet), a switch could apply a **stale** profile, and a
   delete could be **resurrected** by a still-queued save.
4. Found by relaunch acceptance: an Aurora-UI keybind change was never saved to
   `options.txt`, so on the next launch vanilla's stale binding was synced back over
   `aurora.json` — **rebinds were lost on restart**.

## B. Reproduction

- Standalone: `java tools/persistence/ReplaceProbe.java <build/... dir> held|heldtmp|loop N delayMs`.
- Unit (deterministic, real FS): `AtomicJsonWriterTest.legacySequenceFailsWithTheFieldExceptionOnWindows`.
- A/B stress (opt-in, `AURORA_PERSISTENCE_STRESS=1`): legacy sequence vs new writer with a
  thread that keeps briefly opening the destination — **legacy dropped 90 / 93 / 101 / 116
  of 2,000 saves** over four runs; the writer had **0** failures every time.
- Native: `python tools/gui/run.py <name> --plan persist --phase 1|2|3 [--reuse]` (§F); the
  pre-fix build logged **3 AccessDenied save failures** in phase 1 (rapid saves).

## C. Correction

`config/AtomicJsonWriter` — one shared, ordered persistence primitive, used by
`AuroraConfig` and `ProfileManager`:

- **One daemon worker per owner**; per-destination **latest-wins** coalescing → strict
  ordering, no stale snapshot can land after a newer one.
- **Unique temp names** (`aurora.json.<n>.tmp`), written via `FileChannel` + `force(true)`,
  then atomic replace — the destination is never truncated or partially written.
- **Bounded retry only for the demonstrated transient condition** (AccessDenied / sharing
  violation on replace): 1,2,4…125 ms (~250 ms) per burst, then up to 4 rounds 1 s apart.
  Other I/O errors are not retried within a burst. Content is **kept pending, never
  dropped**; final failure is an ERROR with the previous file intact; the next save retries.
- **`flush(timeout)`**: bounded wait (3 s) used by `saveBlocking()`/`saveCurrentBlocking()`
  on shutdown, through the same worker (no second writer, no temp collision); returns and
  logs whether the data is confirmed on disk.
- **Read-your-writes** (`latest`, `latestAll`) and **ordered deletes** (`delete`):
  profile reads, name scans, delete and rename see queued state.
- Stale temp hygiene at load (legacy `*.json.tmp` and numbered temps only).
- `AuroraKeybinds.tick`: when a binding is pushed from Aurora config to vanilla, vanilla
  options are saved (what vanilla's Controls screen does).

No permission changes, no admin requirement, no security-software changes, no
format/migration changes. `WorldMapStorage`/`ModrinthApi` use the same move pattern but
were **not** changed (no observed failures; evidence-scoped) — see §H.

## D. Data integrity evidence

`AtomicJsonWriterTest` (deterministic, latch-synchronized; no sleeps):
persistent lock keeps the previous file intact and reports failure; a partial/interrupted
temp write never reaches the destination; 50 rapid submits during an in-flight write
coalesce to `v1, v50` in order; a delete queued behind an in-flight save is not undone;
queued/in-flight content is visible before the disk; no temp accumulation across 3
destinations × 20 saves; bounded flush reports a stuck write. Native: all values correct
after two relaunches; no `.tmp` left in `config/` in any phase.

## E. Automated tests

New: `AtomicJsonWriterTest` (14 tests, incl. real Windows open-handle test and the legacy
baseline), `FramePacingPersistenceTest.createThenImmediateSwitch…` (profile ordering),
opt-in `PersistenceStressComparison`. Full suite: **591 run, 0 failed, 1 skipped (opt-in
stress)**.

## F. Native acceptance (real Minecraft, disposable fixtures, normal quit between phases)

Phase 1 changes, through the APIs the UI uses: feature toggle (Full Bright), keybind (zoom
→ X via the Aurora setting path), theme accent, color picker **Apply** (real button click)
and **Cancel** (edited, then cancelled), profile create + switch + profile-scoped value,
HUD module move, **Reset Positions**, waypoint with group, 301 rapid saves. Phase 2
verifies after relaunch, then switches profile/zoom/accent; phase 3 verifies again.

| Check | Pre-fix build | Fixed build |
|---|---|---|
| AccessDenied / dropped saves in logs | **3** | **0** |
| Feature toggle, accent, picker Apply, Cancel not persisted, waypoint, rapid-save final value | pass | pass |
| Keybind survives relaunch (config + options.txt) | **fail** | pass |
| Create → switch profile, active profile after relaunch | **fail** | pass |
| Other profile's data intact after switching away (phase 3) | **fail** | pass |
| Reset Positions restores own defaults | fail (pre-GUI-pass jar) | pass |
| Leftover temp files | none | none |

Renderer regression (primary GUI workflow, fixed build): OpenGL and VulkanMod — all
checks pass, no persistence or renderer errors. Sodium not re-run (no rendering or
lifecycle code changed; it passed the identical workflow in the GUI pass).

## G. Performance

Calling-thread (tick) cost of the hand-off: p50 0.6 µs, p99 23 µs, max 0.6 ms (2,000
submits under interference); GSON serialization unchanged. Rapid saves now coalesce,
so fewer disk writes. Worst blocking flush under continuous interference: ~245 ms.
Shutdown waits at most 3 s per owner and reports if unconfirmed. GUI frame-time P50
16.7 ms on both backends (60 fps cap).

## H. Remaining limitations

- The background opener is not named (Restart Manager cannot see it); behavior verified
  only on this Windows machine. Linux not tested (rename semantics differ; tests pass
  there by design).
- If an outside process holds `aurora.json` open for more than ~4 s, a save still fails —
  reported, previous file intact, retried on the next save.
- No native run happened to hit an in-game transient retry with the fixed build (exposure
  dropped with coalescing); absorption is proven by the real-handle unit test and stress.
- `WorldMapStorage` and `ModrinthApi` keep their own move code (same theoretical exposure;
  no observed failure) — candidates to migrate to `AtomicJsonWriter`'s retry policy.

## I. Repository state

Start: `5a2136c` on `codex/gui-premium-design`, with the pre-existing uncommitted Vulkan
work (AGENTS.md, ARCHITECTURE.md, PixelCanvasSetting, BlurPanelRenderer, BlurTestScreen,
ReflexScheduler, untracked Vulkan files/reports/tools) — **not staged, not modified**
(AGENTS.md note committed from HEAD's content via the index). Original `run/` untouched;
all runs used `build/gui-audit/*` and `build/persistence-probe/*`. Nothing pushed.
