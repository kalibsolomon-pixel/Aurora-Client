# Individual setting reset implementation

Branch: `ui/setting-reset`, based on `origin/ui/confirm-destructive` at `b4376f9`
(PR #44). Local implementation only: no push, PR, game boot or merge.

## Design and scope

Players can return one changed setting to its factory value without losing their
other choices. Each simple row accepts a typed `resetDefault` supplier, evaluates
it once, and retains the value for a cheap comparison. Reset goes through the
existing setter, then `AuroraConfig.save()` and its ordered asynchronous writer.
Sliders restore the exact default without rounding it to an interactive step.
Theme rows cancel deferred drag state before invoking their existing responder.

`AuroraConfig.defaults()` returns an independent deep copy of the private snapshot.
The registry obtains one copy at construction, including the release JSON overlay,
initializer-only machine settings and nested Pulsar settings. Captured `cfg`
lambdas cannot be retargeted safely; explicit suppliers avoid reflection in the
frame path or a rewrite of every getter/setter API. Boolean rows additionally
name their config field so failed-mixin session gating does not masquerade as a
changed stored preference. Reset forgets that field's remembered preference and
re-enforces the session gate, following whole-feature reset's health policy.

Nullable HUD colour overrides restore `null` inheritance rather than pinning a
resolved colour. Their tooltip uses the factory-resolved colour; inheritance can
subsequently follow the live theme. Defaults do not alias the private snapshot.

The registry contract scans 236 constructor/factory sites: 235 declare a default
and one explicitly declares its domain exemption. The two Boolean helper sites
expand at their callers; there are 247 config-backed simple rows including those
helper calls. Keystrokes **Scale** edits a HUD module layout rather than a plain
config field, and explicitly has no reset. Toggle groups, actions, previews,
lists, Particle rows, Item Scale editors and per-item editors are unchanged.
Repository search found other simple-row constructions only inside those custom
editors and SliderSetting's own factories.

## Interaction and layout

The existing Material Symbols codepoint list contains `restart_alt f053`.
`IconAction` supplies a 12 px glyph in an 18 x 18 px target, a 4 px label gap,
colour-only hover, focus hairline and one semantic activation click. The icon has
no surface or glass pass. Its accessible name and tooltip are identical:
`Reset <label> to default (<displayed default>)`.

The icon follows the fitted label inside the existing label column. Only a
modified label gives up space; default rows have no empty icon slot. Value and
control columns, alignment and row height stay fixed when the icon appears.
Modified labels use cached `AuroraFontRenderer.ellipsize` output. Relative reset
geometry comes from core `SettingRowLayout` and is shared by paint, pointer and
focus bounds; scrolling adds an offset without rebuilding the geometry.

Both hosts register the stable component-owned control union. Tab reaches the
reset; Enter/Space reset once. Hidden/offscreen/collapsed resets leave keyboard
availability and narration. Disabled rows reject reset. Reset selection takes
priority over stale slider/edit-field focus, while active key capture retains
ownership. The reset consumes its pointer press before a Boolean whole-row
toggle can receive it.

Segmented rows retain a stable 44 px height with their track at y+22, leaving a
clear header for the reset target. Their original segment group remains intact.
The common single-control list and reset unions are cached. Default comparison,
label fitting and relative geometry add no recurring allocations on an unchanged
row; there is no per-frame defaults copy or per-frame registry reflection.
Frame-time and fill-volume performance were not measured in a running client.

Single-setting reset is immediate: changing the value again is easy, so there is
no press-again confirmation. PR #44's whole-feature confirmation remains intact.

## Keybind description tooltip

`git log -S"intentionally skip"` on the shared file identifies its move in
`0267cf0`; searching the complete path history locates the comment's introduction
in `50c2aed` (Initial commit of Aurora UI Engine). Neither gives a rationale for
suppressing descriptions. Compact keybind labels already use the common tooltip
path. Normal labels now use the same measured label/description path too; capture
ownership, cancellation and pill interaction are preserved.

## Automated verification

`python tools/parity.py` passes all three lines, including build, tests, adapter
ownership and layering checks. Totals come from the JUnit XML, including skips:

| Minecraft | Tests | Passed | Skipped | Failures/errors |
| --- | ---: | ---: | ---: | ---: |
| 26.1.2 | 1,137 | 1,136 | 1 | 0 |
| 26.2 | 1,137 | 1,136 | 1 | 0 |
| 26.3 | 1,139 | 1,138 | 1 | 0 |

`python -m unittest discover -s tools`: 43 passed. Focused iteration suites also
passed. `git diff --check` passes; edited/new source uses LF.

New tests cover deep-copy isolation, release/machine defaults, once-only default
evaluation, setter responses, immediate persistence, pointer/keyboard activation,
disabled/unavailable/default state, nullable colour inheritance, exact numeric
restoration, enum/segment/readout formatting, failed-mixin preference handling,
registry coverage, the icon/tooltip contract and shared reset geometry. Existing
host and HUD-corner source contracts were updated for the reset-aware interface.

## Overflow measurements

Headless tests use Minecraft's actual `TrueTypeGlyphProvider`/FreeType and the
bundled factory-active Fira Sans Medium TTF. No game window or GPU was started.
They cover 8 px compact and 9 px normal text, GUI scales 2 and 3, viewports
240 x 180, 320 x 240, 640 x 360 and 1280 x 720, both grid choices, both host
columns and Boolean/slider/enum/keybind/colour families. Grid/list choice leaves
these host columns identical.

Measured unfitted label advances (GUI pixels):

| Font size / GUI scale | Disable VSync (causes tearing without VRR) | Underwater Fog Multiplier |
| --- | ---: | ---: |
| 8 / 2 | 161 | 97 |
| 8 / 3 | 159 | 97 |
| 9 / 2 | 177 | 107 |
| 9 / 3 | 180 | 110 |

The slider is the tightest tested column. Budgets exclude the reset's 18 px
target and 4 px gap; other controls and the value keep their original position:

| Viewport | Detail row width / label budget | Inline Settings row width / label budget |
| --- | --- | --- |
| 240 x 180 | 216 / 32 | 190 / 6 |
| 320 x 240 | 296 / 112 | 270 / 86 |
| 640 x 360 | 336 / 152 | 246 / 62 |
| 1280 x 720 | 336 / 152 | 246 / 62 |

At the smallest inline width, the maximum reset target spans x=22..40 and the
value starts at x=46. The label can reduce to a bounded ellipsis while the icon
remains available. Tests assert no overlap, half-open target boundaries and
unchanged value/control geometry. Live screenshots, the owner's active custom
font and native focus-ring appearance remain for the parent's boot.

## Manual acceptance for the parent

1. Boot each supported adapter with a disposable factory fixture using
   `-Paurora.runDir=build/<fixture>/game`, on the portrait monitor. If using the
   owner's `run/`, first back up `run/config/aurora.json`, `run/config/profiles/`
   and `run/options.txt`, and restore them afterward. No game/config was touched
   by this implementation task.
2. On a factory config, expect **no modified reset icons initially**. Open Modules
   from both grid and list layouts. In **Zoom**, change Zoom Level from 4 to 8:
   expect `Reset Zoom Level to default (4.00)`. Hover, click, and repeat with
   Tab + Enter and Tab + Space. Only that row returns to 4; no confirm state.
3. **Settings > General**: turn Instant Search off, then reset to On.
   **Settings > Performance**: turn Debug Entry Cache off, then reset to On
   (initializer default). Verify other settings retain their changes.
4. **Settings > Appearance > Text & Fonts**: change Text Renderer from All Text,
   then reset it. Change Case from Aa, then reset via its separate icon. Segment
   arrow navigation, focus and existing theme responders should still work.
5. **Appearance > Theme**: change Accent Color, Background Opacity, Blur Radius
   and Corner Radius. Defaults are #5C6BC0, 33%, 9 px and 10 px. Reset each with
   pointer and keyboard. The picker closes on reset; deferred drag values must
   not reappear on a later frame. Existing theme/profile persistence still runs.
6. **Settings > Controls > Aurora Keys**: rebind HUD Editor Key, then reset to
   Right Shift. Hover the label to see its description. While key capture is
   active, the next pointer click must cancel capture and be consumed. Test the
   same description behaviour on Zoom's Hold Key.
7. **Pulsar**: turn Automatic capture off, then reset to On (nested config).
   **Alerts**: give Armor Module Text Color a custom colour, then reset; verify
   the nullable override returns to inheritance and follows subsequent HUD/theme
   colour changes. **Keystrokes** Scale and Particle/Item Scale custom editors
   should still have their original controls and no individual reset.
8. On **Miscellaneous > Low Latency**, modify **Disable VSync (causes tearing
   without VRR)**. Also modify **Pack Tweaks > Underwater Fog Multiplier**. Sweep
   the four logical viewports above, GUI scales 2/3, both grid layouts, compact
   and normal fonts, the live font and factory Fira Sans Medium. Confirm bounded
   ellipsis, a visible/clickable reset, fixed value/control columns and no
   subtitle collision. Check hover, Tab focus, Enter/Space, narration, scroll
   clipping, collapsed sections and disabled rows.
9. Sweep live and factory (33%) Background Opacity, and the no-world flat fallback
   versus an in-world backdrop. The reset adds no glass surface. Reopen/restart
   after a reset to verify the saved value. Whole-feature Reset must still arm on
   the first press, confirm on the second, and disarm on Escape.

Open questions: no unresolved implementation decisions. The parent owns the
requested game boot, visual/focus/narration acceptance and performance evidence.
Normal per-line build outputs and dependency/compiler caches are retained for
that review; the parent owns their eventual lifecycle. Task scratch and recorded
`aurora-client-tests-*` log directories are disposable and removed before handoff.

## Changed files

- Core: `AuroraConfig.java`, `SettingRowLayout.java`.
- Shared screens: `FeatureRegistry.java`, `FeatureDetailScreen.java`, `AuroraScreen.java`.
- Shared rows: `FeatureSetting.java`, `BooleanSetting.java`, `SliderSetting.java`,
  `ThemeSliderSetting.java`, `EnumSetting.java`, `SegmentedSetting.java`,
  `ColorSetting.java`, `AccentSetting.java`, `KeybindSetting.java`.
- New tests: `SettingResetTest.java`, `SettingResetHealthTest.java`,
  `SettingResetLayoutTest.java`, `SettingResetFontTest.java`.
- Updated tests: `AuroraScreenSemanticHostingTest.java`,
  `SegmentedSettingPilotTest.java`, `SquareModeConformanceTest.java`.
- Docs: `DESIGN_LANGUAGE.md`, `SETTINGS_TREE.md`, this report.
