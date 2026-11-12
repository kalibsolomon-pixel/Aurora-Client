# Step 8, PR 1: performance foundation and loading (2026-11-12)

Branch `feat/perf-foundation`. [ADR 0009](../adr/0009-client-optimizations-aurora-owns.md) and the `performance` Settings feature, the stand-down mechanism, and four loading optimizations.

## What changed

| Optimization | Gate | UI | Lines | Mixins |
|---|---|---|---|---|
| Hide Reload Overlay | `perfHideReloadOverlay` | Settings → Performance | all | `LoadingOverlayMixin` (shared) |
| Hide Title Fade | `perfHideTitleFade` | Settings → Performance | all | `TitleScreenFadeMixin` (shared) |
| Skip Level Loading Delay | `perfSkipLevelLoadDelay` | config only | all | `LevelLoadTrackerMixin` (shared) |
| No Profile Wait Before Title | `perfNoProfileWait` | config only | all | `MinecraftProfileAccessor` (shared), `InitialScreensProfileMixin` (per line) |

- **Stand-down.** A binding may declare `yieldsTo(mods)` or `requires(mod)`. `AuroraMixinPlugin` skips the mixin, and `MixinHealth.standDowns()` records why. The startup line becomes "All N Aurora mixins applied or stood down (k stood down)", which `parity_acceptance.py` accepts. No mixin in this PR uses it yet.
- **Profile wait.** Vanilla joins the profile lookup in `addInitialScreens` (in `Minecraft` on 26.1, in `Gui` on 26.2 and 26.3). Aurora's title screen also joined it in `init`. Both now use what is ready. The name- and skin-ban notices are shown when the lookup arrives and no world is loaded. The title screen rebuilds with the real skin.
- **Level loading delay.** Vanilla's only non-zero delay is the 500 ms for a new singleplayer world (`Minecraft.doWorldLoad`). Multiplayer already uses 0, so the earlier `ServerboundPlayerLoadedPacket` reaches only the integrated server.

## Evidence

Disposable fixtures (`build/perf-boot/<run>/game`, copied from `build/step5.5/game-<mc>`) with factory settings and the parity probe. Windows 11, RX 9070 XT.

| Run | Renderer | Mixins | Probe `perf-loading` | Reload: overlay up until control returns |
|---|---|---|---|---|
| 26.3 | OpenGL | All 84 applied | `closeDelayMs=0 titleFading=false` | 2801 ms, screen visible throughout |
| 26.3, `LoadingOverlayMixin` disabled | OpenGL | 83 of 84 (by option) | — | 4890 ms, Mojang overlay |
| 26.2 | OpenGL | All 83 applied | same | 2726 ms |
| 26.1.2 | OpenGL | All 84 applied | same | 2805 ms |
| 26.2 | Vulkan | All 83 applied | same | 2836 ms |
| 26.3 | Vulkan | All 84 applied | same | 2870 ms |

- No Aurora warning or error was logged in any run. The exception lines in the logs are the dev account's usual auth and Realms failures.
- Screenshots of the Performance page and of a reload in progress were checked for 26.3 on both backends and for the vanilla comparison.
- The probe also checks that the profile accessor is present.
- `python tools/parity.py` passes on all three lines.

**Profile wait:** this dev session's lookup failed quickly (HTTP 429), so no startup gain was measured here. The change removes a blocking join and gains only when the session server is slow.

**Not checked by eye:** the vanilla title fade (asserted on the live class only) and the ban notices (this account has none).

## Dropped: Parallel Block State Caches

`BlockStateBase.initCache` runs inside `Blocks.<clinit>`. A JFR boot on 26.3 (1 ms sampling) measured about 305 ms in `Blocks.<clinit>`, of which about 191 ms was in `initCache`, out of a startup of more than ten seconds. Parallelizing it would save at most about 150 ms. Against that:

- worker threads that touch any `Blocks.*` constant during the class's own initialization deadlock;
- races in block shape memoization would corrupt collision shapes, which is gameplay-visible.

Under ADR 0009 it is not shipped.
