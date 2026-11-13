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
reported line defect has been fixed. Pointer-hover, keyboard-focus and
mid-animation states were not exhaustively swept. No rendering workaround was
introduced without a reproduction. The exact affected screen/backend remains
useful follow-up information.

Native window captures are under `build/ui-control-check/`, including
`alerts-before.png`, `alerts-square-before.png`, `toggle-radius1-on-before.png`,
`settings-display-before.png`, `create-round-before.png`,
`vulkan-on-round.png`, `vulkan-off-square.png`, `vulkan-create-small.png`,
`scoreboard-grid.png` and `scoreboard-list.png`. Captures used the desktop API,
so F2's temporary glass suppression did not affect them.

The opt-in parity probe gained `profiles`, `profile-create` and
`profile-name <name>` commands to repeat the inspection in a marked disposable
fixture. This helper is excluded from production JARs.

## Verification and limits

- `./gradlew test build -Paurora.minecraft=26.3` passed.
- `python tools/parity.py` completed successful builds/tests for 26.1.2, 26.2
  and 26.3. PowerShell reported a NativeCommandError for Gradle's stderr warnings;
  each Gradle invocation finished with BUILD SUCCESSFUL, with no failed tests.
- Native OpenGL and Vulkan boots and the screenshots above completed.
- No control layout, input semantics, config schema or defaults changed.
- Performance was not benchmarked; the new symbol uses the existing icon path.
- No existing files were deleted. Merge and release remain with the owner.
