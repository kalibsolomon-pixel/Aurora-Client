# C-PERF.3 Unit A — Native throttle precedence

## Baseline and source evidence

- Root: `C:\Dev\Aurora-Client`; origin: `https://github.com/kalibsolomon-pixel/Aurora-Client.git`.
- Branch: `codex/unified-theme-pilot`; starting HEAD: `5bdf3aa30bc7bfa9a226170a9c54ca5ec3fd8e1d`.
- Initial `git status --short` empty; 422 tracked files; no untracked developer files.
- Read AGENTS.md and ARCHITECTURE.md. Recorded tracked paths and SHA-256 baseline outside the repository. No branch switch/reset, cleanup, Launcher changes, profile/fixture changes, or push.
- Both C-PERF.1 paths still existed: frame head read the configured option while adaptive cancelled the native wait; GUI interception replaced the incoming effective native target outright.
- Verified the locally mapped 1.21.11 jar with `javap -p -c`: `FramerateLimitTracker.getThrottleReason/getFramerateLimit` and `Minecraft.runTick`. Precedence: iconified -> 10; inactivity setting AFK and >600000ms -> 10; AFK and >60000ms -> min(configured,30); outside a level with screen/overlay -> 60; otherwise configured. Losing focus alone is not a native FPS restriction. `runTick` only calls `limitDisplayFPS` when the effective value is <260. These are native policy values, not new Aurora detection logic.

## Corrected ownership

`FramePacing.resolve` composes only applicable positive limits: effective native (<260), positive global cap, and GUI cap on Aurora screens when the native limiter is applicable. Global 0/negative is inactive; native 260+ is Unlimited; GUI 1000 is its Unlimited choice. Zero/unavailable native limits do not become finite caps. Existing GUI applicability at native Unlimited remains unchanged (the historical GUI-only skip), while a positive global cap still works there, including values above 260.

A positive global cap owns the frame-head wait, incorporating tighter native/GUI limits rather than running independent full-period waits. Without it, adaptive owns a finite native target only with its existing prerequisites and outside Aurora screens. Otherwise the ordinary custom/GUI pacer owns post-swap; Frame Pacer off/VANILLA returns native ownership, except for the existing fixed HYBRID global/GUI fallback.

The two hooks share one deadline. An owner or FPS change clears the old deadline, including Unlimited/disabled intervals where there is no post-swap call. The prior deadline + period and late-by-half-period reanchor calculation is preserved. Arming/reanchoring leaves native pacing available. After swap/event pumping, cancellation requires an actual frame-head wait at an equal or tighter target, or an actual post-swap wait. A newly tighter native target falls through to Minecraft and is picked up at frame head next time. GUI classification and hook locations/order are unchanged. The interception now requires its verified target rather than silently skipping it.

No change to FramePacer's PARK/HYBRID/SPIN implementations, thresholds, Reflex, VSync, input, tick rate, dependencies or serialized settings. This supersedes the historical independent-anchor composition guarantee in AGENTS.md's global-cap entry.

## Verification

- Baseline `.\gradlew.bat test --console=plain`: passed (443 tests).
- Unit A `.\gradlew.bat test --console=plain`: passed; `.\gradlew.bat build --console=plain`: passed.
- Final Unit A `.\gradlew.bat test build --console=plain`: 474 tests, zero failures/errors/skips; 31 new decision/ownership/transition/hook tests. Includes the shared instance startup regression caught by the first smoke boot and fixed before commit.
- Java build host: JDK 25, Java compilation release 21. Disposable runtime: Microsoft OpenJDK 21.0.7, Minecraft 1.21.11, Fabric Loader 0.19.2, Fabric API 0.141.4+1.21.11, AMD OpenGL. `.\gradlew.bat -I <temporary smoke.init.gradle> runClient --console=plain` passed after correcting the temporary Loom runDir to a relative path and the shared-instance initialization order.
- A temporary separate smoke mod/source set rendered the Aurora title with global 30, adaptive enabled/disabled GUI exemption, iconified native=10, restored native=60, then `Minecraft.stop()` exited normally. Default PARK/500/2000 confirmed at runtime. Artifacts are outside the tracked tree: `%TEMP%/aurora-cperf3-sg__c0lj/` (init script, harness, logs, disposable game/config). No production harness or background thread added.
- This smoke checks execution/targets, not achieved FPS or CPU consumption. Gameplay, actual AFK timeouts, Sodium, input feel/frame-time quality and full VSync/Reflex interaction remain for bounded follow-up validation; deterministic tests cover the target/owner matrix.
- Existing warnings: Loom modifier-remap notice, deprecated Java APIs/native access, development refmap/main-source-set warnings, and unauthenticated development Realms rejection. No unrelated test failures.
