# Toggle, profile and Scoreboard icon review

The user needs readable, stable settings controls and a recognizable Scoreboard
feature card. This pass inspected existing controls and added the missing icon;
it did not change toggle or profile behavior.

## Scoreboard symbol

The card now uses Google's Material Symbols Rounded `scoreboard` (U+EBD0), a
score display with two numeric columns. The official
[24 px SVG](https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/scoreboard/materialsymbolsrounded/scoreboard_24px.svg)
was inspected before selection. Aurora already bundles this same symbol in its
full icon font, so the card uses the existing MaterialIconRenderer and atlas.
There is no additional texture, SVG parser or dependency. Google's existing
Apache-2.0 notice covers the bundled font.

The icon is centered in grid cards and at the existing leading position in list
rows. It inherits the existing enabled, disabled and hover colors. No new
decorative surfaces, borders or motion were added.

## Native inspection

Minecraft 26.3 was booted with a disposable copy of HudTest, first on OpenGL,
then on native Vulkan (AMD driver 26.10.1). Test windows stayed on the portrait
secondary monitor. The owner's run and launcher configurations were untouched.

The following visual checks did **not reproduce** a line through a toggle or a
defect in the New profile control:

| Surface | Captured conditions |
| --- | --- |
| Alerts toggle | ON and OFF; round radius 10 / opacity 33%, square radius 0 / opacity 19%; both backends |
| Alerts toggle | Intermediate radius 1; GUI scales 2 and 3; OpenGL |
| Display settings | Frame Pacer ON and disabled Low CPU OFF; OpenGL, round / 33% |
| New profile card | Resting plus and label; square and round; OpenGL |
| Inline profile editor | Empty name / disabled Create, entered name / enabled Create; square and round; both backends |
| Small viewport | 854 x 480; round profile card, name field and Create button; both backends; OpenGL Alerts OFF |
| Scoreboard card | New icon at 1280 x 960 in grid and list; Vulkan, square / 19% |

These are visual observations of the current branch, not evidence that the
reported line defect has been fixed. No rendering workaround was introduced
without a reproduction.

After the owner confirmed Vulkan, a further native Vulkan pass captured all
21 supported corner-radius values (0 through 20) with Alerts OFF, Alerts ON and
the resting New profile icon at each value. All 63 captures looked clean;
`vulkan-radius-sweep.png` collects their enlarged crops. Actual pointer clicks
also exercised the focused toggle thumb and opening the inline profile editor;
hover and focused captures showed no line. Transition frames and keyboard
traversal were not exhaustively verified. The owner then confirmed that the
current previews look fine.

A separate disposable compatibility boot with the installed Sodium 0.9.2,
Iris 1.11.7 and ImmediatelyFast 1.17.1 failed before a screen could render:
Iris's SamplerLimits initializer called OpenGL without an active OpenGL context
under native Vulkan. This does not establish a cause for the toggle report.
Removing Iris from the disposable fixture allowed the Sodium/ImmediatelyFast
Vulkan boot; focused toggle and profile-editor captures were also clean
(`vulkan-sodium-toggle.png`, `vulkan-sodium-profile.png`). The owner's mods and
configuration were not changed.

Native window captures are under `build/ui-control-check/`, including
`alerts-before.png`, `alerts-square-before.png`, `toggle-radius1-on-before.png`,
`settings-display-before.png`, `create-round-before.png`,
`vulkan-on-round.png`, `vulkan-off-square.png`, `vulkan-create-small.png`,
`scoreboard-grid.png` and `scoreboard-list.png`. Captures used the desktop API,
so F2's temporary glass suppression did not affect them.

The opt-in parity probe gained `profiles`, `profile-create` and
`profile-name <name>` commands to repeat the inspection in a marked disposable
fixture, plus virtual `hover <x> <y>` and `click <x> <y>` input in GUI coordinates.
Clicks use the selected adapter's native left-button constant. This helper is
excluded from production JARs.

## Verification and limits

- `./gradlew test build -Paurora.minecraft=26.3` passed.
- `python tools/parity.py` completed successful builds/tests for 26.1.2, 26.2
  and 26.3. PowerShell reported a NativeCommandError for Gradle's stderr warnings;
  each Gradle invocation finished with BUILD SUCCESSFUL, with no failed tests.
- Native OpenGL and Vulkan boots and the screenshots above completed.
- No control layout, input semantics, config schema or defaults changed.
- Performance was not benchmarked; the new symbol uses the existing icon path.
- No existing files were deleted. Merge and release remain with the owner.
