# C-PERF.3 acceptance record — 2026-10-17

## Repository baseline and preservation

- Target root `C:\Dev\Aurora-Client`; origin `https://github.com/kalibsolomon-pixel/Aurora-Client.git`.
- Starting branch `codex/unified-theme-pilot`, complete starting HEAD `5bdf3aa30bc7bfa9a226170a9c54ca5ec3fd8e1d` (matches the historical audit).
- Starting status empty; 422 tracked files; no untracked files. Saved the tracked set, individual SHA-256s, history/status and critical build/document inventory under `%TEMP%/aurora-cperf3-baseline-20261017.json`. Read root AGENTS.md and ARCHITECTURE.md in full and inspected existing tests/history/performance notes. No separate C-PERF.1 report is checked into this Client tree; the supplied audit was reconfirmed against code and the mapped jar.
- Stayed on the existing branch. No reset, rebase, switch, cleanup, deletions, dependency updates, Launcher edits, production profile/fixture changes or external writes. Two independently reviewed local commits; no push/merge/tag/publication/release.
- Runtime games/configs/profiles/harnesses live only under `%TEMP%/aurora-cperf3-sg__c0lj/`. The existing development world was copied, never opened in place; all 131 source files and original development configuration hashes matched after testing. Sodium was copied read-only from an existing installed jar into the disposable third game's mods directory; the original artifact hash was recorded and rechecked. Existing instances were not launched or modified.

## Confirmed defects and Unit A

Both defects reproduced in current source. Adaptive used `options.framerateLimit()` and cancelled the effective native wait. GUI pacing unconditionally replaced the incoming effective target. The Low CPU modifier only fed HYBRID, increasing its planned active window to at least 4000 microseconds, against the stated longer-parking purpose; PARK/SPIN ignored it. The unused third HYBRID argument did not shorten that window.

[CPERF3_NATIVE_THROTTLE.md](CPERF3_NATIVE_THROTTLE.md) records the mapped native policy and Unit A in detail. `FramePacing.resolve` uses the effective tracker result, with explicit applicability/sentinels: native 260+ inactive, global 0 inactive, GUI 1000 Unlimited; the historical GUI-only skip at native Unlimited remains. Positive global caps still work at Unlimited, including above 260. Applicable positive native/global/GUI constraints select the tightest target. Configured native values are not substituted for tracker policy (which deliberately returns 60 for outside-world menus).

A single shared deadline owns head or post-swap pacing; arming/hitches retain native fallback, owner/target changes discard stale deadlines, and tighter native targets appearing during swap cannot be cancelled by a looser head wait. Ordinary custom pacing, native fallback and fixed HYBRID global/GUI fallback remain available. Hook placement/order, input application and all PARK wait mathematics/defaults are unchanged. A missing post-swap interception is now a visible startup error (`require=1`).

Unit A commit: `cd3f50de5eb362de877ce6804438c9e92da277a9`. Completed and validated before Unit B began.

## Unit B — Low CPU contract and correction

The initial `50c2aed` implementation, `95c833d` HYBRID loop consolidation and existing dispatch establish HYBRID as the only affected strategy; the old description promised longer parking but did not establish an implemented all-strategy modifier. Preserve that compatibility rather than inventing new PARK/SPIN behavior.

HYBRID Low CPU now uses the existing configured Spin Threshold, bounded by its normal HYBRID window. At factory values, Low CPU off plans a 2000 microsecond active window and on plans 500 (previously at least 4000). This is a policy correction, not measured CPU savings. No new hardcoded tuning threshold or strategy switch is introduced. With Low CPU off, HYBRID's existing normalization/algorithm is unchanged. PARK/SPIN method bodies are pinned to their audited hashes and remain identical; fixed fallback still uses a 2000 microsecond HYBRID window. The unused HYBRID parameter was removed from its internal callers.

The UI says **Low CPU (Hybrid only)**, disables the control outside HYBRID without clearing its saved value, and describes exactly which thresholds each strategy uses. HYBRID's strategy description reflects the modifier. Existing field names, enum identifiers, boolean/default values and valid custom thresholds remain unchanged; no schema migration. Configuration/profile regression tests exercise real atomic writes, reloads, full profile persistence and application for each strategy with the modifier on/off. Old partial profiles retain defaults; the existing removed-YIELD/null fallback remains. Negative/zero threshold normalization remains as before.

## Commands and results

Commands ran from the Client root, in order:

1. `.\gradlew.bat test --console=plain` — baseline passed, 443 tests.
2. Unit A `.\gradlew.bat test --console=plain`, `.\gradlew.bat build --console=plain`, then `.\gradlew.bat test build --console=plain` — final Unit A 474 tests, zero failures/errors/skips. 31 new limit/ownership/transition/startup regressions.
3. `.\gradlew.bat -I C:\Users\kalib\AppData\Local\Temp\aurora-cperf3-sg__c0lj\smoke.init.gradle runClient --console=plain` — corrected Unit A Java 21 menu/minimize/restore smoke passed. Initial attempts exposed a temporary Loom absolute-path issue and a new singleton initialization-order bug; both were corrected, a singleton regression added, and the successful boot rerun before committing A.
4. Unit B `.\gradlew.bat test --console=plain` and `.\gradlew.bat test build --console=plain` — 489 tests, zero failures/errors/skips. 15 new HYBRID/manual-value/control/configuration/profile cases; the full supported threshold grid also runs inside the policy test.
5. `.\gradlew.bat -I C:\Users\kalib\AppData\Local\Temp\aurora-cperf3-sg__c0lj\smoke-b.init.gradle runClient --console=plain` — 21-phase disposable gameplay matrix passed; normal client/server shutdown. The temporary harness's first compile used a wrong label accessor; corrected before the game boot, with no production change.
6. `.\gradlew.bat -I C:\Users\kalib\AppData\Local\Temp\aurora-cperf3-sg__c0lj\smoke-c.init.gradle runClient --console=plain` — same gameplay matrix with Sodium plus VSync handoff checks passed, normal shutdown, Reflex session-disabled=false.
7. Final `.\gradlew.bat test build --console=plain` — 489 tests, zero failures/errors/skips; production build passed after the final description update.

Build host JDK 25 compiles with release 21. All three Minecraft smokes used Microsoft Java 21.0.7, Minecraft 1.21.11, Fabric Loader 0.19.2, Fabric API 0.141.4+1.21.11 and AMD RX 9070 XT OpenGL. Sodium compatibility pass used `0.8.14+mc1.21.11`, without Sodium Extra. No production dependency was changed. Existing deprecation/native-access/Loom development warnings and offline Realms rejection were observed; no unrelated test failure or runtime rendering error.

## Bounded runtime evidence and limits

Each gameplay phase ran for 40 client ticks, with a short settled HUD-render count/time observation. These are cap sanity checks, not comparable performance measurements. Scene/load/focus/pause conditions varied; do not compare uncapped rows or infer CPU, power, latency or frame-time quality from them. Minecraft quantized a requested native 144 option to 140; the tracker result was used faithfully. AFK states were exercised by setting the real tracker's input timestamp to just past its mapped thresholds, rather than waiting ten minutes. Recovery uses the real input-received method. Iconification/restoration used the development window's GLFW handle.

| Phase | Scenario | OpenGL FPS | Sodium/OpenGL FPS |
|---|---|---:|---:|
| 0 | Native Unlimited, global Off | 122.58 | 175.80 |
| 1 | Native Unlimited, global 60 | 60.05 | 59.99 |
| 2 | Native 30, global 60 | 30.01 | 30.00 |
| 3 | Native 140, global 30 | 30.01 | 29.99 |
| 4 | Adaptive, native 120 | 119.55 | 119.99 |
| 5 | Adaptive, native Unlimited | 803.29 | 634.46 |
| 6 | Adaptive disabled, native 120 | 120.00 | 120.03 |
| 7 | Aurora GUI 60, native 30 | 29.98 | 30.00 |
| 8 | Aurora GUI 60, native 120 | 60.01 | 59.99 |
| 9 | Minimized with adaptive | 10.00 | 10.00 |
| 10 | Window restored with adaptive | 120.00 | 120.00 |
| 11 | Short AFK with adaptive | 30.00 | 30.01 |
| 12 | Long AFK with adaptive | 10.00 | 10.00 |
| 13 | Input/AFK recovery | 119.97 | 120.01 |
| 14 | HYBRID, Low CPU off | 60.00 | 60.00 |
| 15 | HYBRID, Low CPU on | 59.98 | 60.01 |
| 16 | PARK, Low CPU on | 60.00 | 59.99 |
| 17 | SPIN, Low CPU on | 59.99 | 59.99 |
| 18 | VANILLA strategy, global 30 at Unlimited | 30.01 | 30.01 |
| 19 | Frame Pacer disabled, global 30 at Unlimited | 30.00 | 30.01 |
| 20 | Display Settings with HYBRID Low CPU on | 60.00 | 60.02 |

The additional Sodium checks confirmed Window's VSync field restored true when Disable VSync was off, then false while forcing was on, with the native option still true. The actual production Low CPU widget's disabled gate matched PARK/HYBRID/SPIN and retained 500/2000 values. No new Reflex failure latch or exceptions. The original mouse application block and tick-rate constant were not modified.

Unverified: subjective mouse/input feel, multiplayer, a complete fullscreen/VSync matrix, all drivers/platforms, sustained GPU-bound Reflex queue behavior, UI readability at every viewport, actual elapsed AFK inactivity, and high-resolution frame-time/CPU/power outcomes. Existing Reflex unit tests pass. No new spikes or input-latency conclusions are claimed from this coarse smoke. These limits motivate diagnostics, not speculative tuning or another correctness implementation.

## File inventory and next phase

Modified existing files across the two units:

- `AGENTS.md` — correction records/links.
- `src/client/java/com/aurora/client/config/AuroraConfig.java` — accurate comments only; serialized fields/defaults unchanged.
- `src/client/java/com/aurora/client/mixin/MinecraftClientRenderMixin.java` — effective native target/shared ownership; HYBRID call signature.
- `src/client/java/com/aurora/client/mixin/RenderSystemMixin.java` — post-swap precedence/shared ownership; HYBRID call signature.
- `src/client/java/com/aurora/client/screen/FeatureRegistry.java` — Low CPU scope/gate/descriptions.
- `src/client/java/com/aurora/client/util/FramePacer.java` — HYBRID Low CPU correction and unused-argument removal; PARK/SPIN unchanged.

Added:

- `src/client/java/com/aurora/client/util/FramePacing.java`.
- `src/test/java/com/aurora/client/util/FramePacingTest.java`.
- `src/test/java/com/aurora/client/util/FramePacerLowCpuTest.java`.
- `src/test/java/com/aurora/client/config/profile/FramePacingPersistenceTest.java`.
- `CPERF3_NATIVE_THROTTLE.md` and this record.

Deleted: none. Original tracked inventory and unrelated file hashes checked before each local commit; generated game/runtime/build data not staged. Final commit SHAs and clean working-tree status are reported at delivery (this record lives inside Unit B's commit).

**Recommendation: Proceed to C-PERF.1A — Minimal Timing Diagnostics.** The two source-established correctness defects are corrected, deterministic coverage passes, and native/GUI/global/strategy transitions execute correctly with OpenGL and Sodium. Fine timing evidence should precede a controlled PARK benchmark. No PARK optimization, CPU detection, adaptive tuning, new thread, tick-rate/Reflex/GPU ordering change, Vulkan work or subsequent experiment is authorized by this recommendation. Owner review is next.
