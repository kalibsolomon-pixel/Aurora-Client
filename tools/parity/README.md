# Native acceptance probe

This optional Fabric helper drives Aurora's existing screens and key-capture
paths inside a disposable dev client. It is excluded from production sources,
dependencies and JARs. It adds no renderer or feature behavior.

Build for the selected native classpath:

```sh
./gradlew parityProbeJar -Paurora.minecraft=26.2 -I tools/parity/runtime.init.gradle
```

Copy `build/minecraft-26.2/acceptance-tooling/parity-probe.jar` into a disposable
fixture's `mods/`. Create an absolute command directory with `DISPOSABLE.txt`.
Pass `-Daurora.parity.probe=<directory>` to the game JVM and select the fixture
with `-Paurora.runDir=<fixture>`. Never install this helper in the owner's live
instance or include it during overhead measurements.

Write one command to `command.txt`, then wait for matching `result.txt` ending
in `OK`. A `FAILED` result must be investigated; commands are consumed once.
Use native desktop screenshots to inspect the resulting screen.

| Command | Action |
|---|---|
| `world` | Wait for a loaded world, then close the current screen |
| `settings` | Open Aurora settings |
| `resize 854 480` | Set the native window size |
| `layout grid` / `layout list` | Open the selected feature layout |
| `theme ROUND 0.33` / `theme SQUARE 0.19` | Apply factory/live appearance |
| `font INTER AURORA_ONLY` / `font SANSSERIF OFF` | Change the live HUD font policy |
| `detail animations` | Open a feature's native detail screen by ID |
| `search <text>` | Focus and fill the native search field |
| `escape` | Send Escape through the active screen's key handler |
| `capture` | Capture F7 through a real KeybindSetting and save |
| `bindings` | Assert config/vanilla sync and the native C reset default |
| `persisted` | Assert the F7 capture survived restart |
| `keycaps` | Assert the native extra-key label is F7 and show it in the HUD |
| `menu` | Disconnect to Aurora's title screen |
| `stop` | Shut down normally |

Check both layouts at small and large sizes, factory ROUND/33% and live
SQUARE/19%, details, focused search, and menu settings without a world. Repeat
on OpenGL and native Vulkan (VulkanMod on 26.1.2). Run `capture`, then `bindings`
on a later tick; restart the same fixture and run `persisted` and `bindings`.
This verifies native handlers and persistence; it does not synthesize OS keys
or replace a physical-input/accessibility audit.
