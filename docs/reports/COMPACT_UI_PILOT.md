# Compact Aurora UI pilot — 2026-10-06

Status: historical UX-0–3 layout record. The owner correction pass supersedes
its card proportions, branding placement, Configure arrows and Pack-only row
measurement scope; see OWNER_UI_CORRECTIONS.md. **User visual acceptance remains open.**
The user's subsequent correction is authoritative: setting labels and section
captions retain the original left alignment and 12px text inset. This is a compact
geometry pass within the approved unified theme, not a new design language.

## Starting state and protection

Workspace: `C:\Dev\Aurora-Client`; branch `codex/unified-theme-pilot`;
starting HEAD `eade456efa3092965fc98a439fdde7831b2d37cb`.
Initial status: 221 tracked `M` entries, but an empty substantive tracked diff,
plus the existing untracked `tools/__pycache__/` directory. Git's LF/CRLF warnings
were recorded separately from the actual diff. The four existing Python cache
files were hashed before edits. Their bytes remain protected. No clean, push,
unrelated normalization or deletion is part of this task. Edits retain each
touched file's original newline convention. Baseline and original-byte backups
are outside the repository in the task's temporary baseline directory.

## Implemented geometry

| Surface | Before | Pilot |
|---|---|---|
| Main frame | 360×240 | preferred 344×224, bounded to viewport minus 16 |
| Header | scattered geometry | 32px header plus 8px content inset |
| Sidebar | 64×22 peers | 72px band; 56×24 peers at 28px pitch |
| Grid | 80×80, 4px gaps, fixed three columns | 80×52, 6px gaps, 1–3 measured columns |
| Full cards at preferred size | six | nine |
| List | 250×48, 6px gaps | 252×32, 4px gaps |
| Full list rows at preferred size | three | five |
| Main painted content | 254×194 with separate 180px scroll tuning | 252×176; one cached ClipBand |
| Simple settings | generally 28px | 24px, longer labels ≥36px |
| Slider | 36px stacked | 28px inline when eligible; ≥36px stacked otherwise |
| Detail | 320px column, screen-relative toolbar/body | Pack only: stationary ≤344×300, viewport margin 12 |

`UiDensity` owns frame, viewport, card and row metrics. At frame widths below
320px, main navigation becomes horizontal and its content starts lower. Wordmark
omits below 300px frame width. Layout/search remain in the header. No font or
pose scaling is added. UI geometry and frame origins use whole GUI pixels.
Mechanical toggle, slider and thumb shapes retain their existing rendering.

## Shared measurement and adapters

`SettingRowLayout` owns immutable label lines, height, visual control rect,
pointer target, value rect and inline/stacked choice. Active-font measurement runs
before height, clip, scroll or pointer walks. `FeatureSetting` caches the result
by width, active font, renderer and theme generation. Closing the detail clears
the opt-in measurement, preserving every other detail's legacy path.

- Boolean: 24px simple row, unchanged 28×15 switch and whole-row activation.
- Slider: distinct label/value/track columns; existing Slider snap, clamp, drag,
  key modifiers, readout formatting and opt-in numeric editing remain the owners.
- Enum: 24px simple row or wrapped fallback; unchanged trigger visual, 24px-high
  pointer target. Expanded popup floats after all normal rows, reserves no row
  height, resolves/ flips against the body band and gets pointer priority.
- Keybind: measured left-aligned label and existing capture/cancel behavior.
- Color: measured collapsed swatch row; existing expanded picker body retained.
- Button: measured label and unchanged action/sound ownership, 24px-high target.
- Section: compact uppercase, left-aligned caption; no new interactive semantics.

Only Pack Tweaks opts these adapters in. Complex editors are not migrated.
Descriptions remain zero-height help with existing 1500ms dwell / 200ms fade.
F1 help, section IDs/jumps and detail search are deferred; this nine-control pilot
does not justify additional navigation infrastructure.

Inline slider eligibility requires a single-line label in its measured column
and at least 120px of useful track. At 344px the actual allocation retains a
108px label column at x=12, a separate 48px value slot and a **144px track**.
The track differs from the analysis's 152px reference because the unchanged
7px knob and AA edge need clearance inside the clip. Narrower layouts or longer
labels use a nearly full-width track below the label/value line. Shared slider
value math is unchanged; the geometry only changes widget placement.

## Branding and Configure

Source: `C:\Users\kalib\Downloads\Aurora-logo.png`, 3162×3162 RGBA.
SHA-256: `faba1af24b964cfd085505c63e9e43d7abedebd70d70794e841a3251d0fa5a19`.
Alpha crop `(332,231)-(2830,2769)`, aspect preserved and transparently padded,
then deterministic Lanczos reduction to 256×256. Runtime asset:
`assets/aurora/textures/ui/aurora_mark.png` (32,499 bytes; 256 KiB RGBA texture).
The original stays outside the repository. `AuroraBrand` uses the normal GUI
textured pipeline: white/untinted mark at 28px in main and 24px in Pack detail,
without extra glow or blur. The title screen is outside this pilot's rollout.

Eligible module cards gain a separate 24×24 Configure target with a 16px bundled
chevron. The initial unbundled gear was caught and replaced during live checking.
Each action is stable across frames and registered through the existing
IconAction/SemanticControlHost family: textual Configure narration, Tab/Enter/
Space, existing focus treatment and one activation sound. Its callback opens
detail, independently of the module toggle. Right-click configuration remains.

## Pack Tweaks behavior boundary

Final visual sections: **PARTICLES / PRESENTATION**, **SHIELD**,
**ENVIRONMENT / CLARITY**. The four former headings become three, without
adding or removing any of the nine existing actionable controls. Getter/setter
bindings, fields, bundle OR/set-all semantics, reset prefixes, profiles and all
render hooks remain unchanged. Generic Enabled labels intentionally remain.
Missing subfeature enable rows, master-gate reorganization, the fire/overlay
label issue, particle overlap and existing fog performance descriptions remain
deferred to a separately authorized behavioral pass.

## Verification and practical limits

Deterministic geometry checks cover preferred capacity, empty/trailing-gap
extents, 640×360 / 320×240 / 240×180 / 800×600 viewport bounds, wrapping,
non-overlapping slider columns, fallback threshold, pointer edges, the original
12px text inset across adapters and no enum-popup height reservation. Existing
semantic, capture, sound, slider math and material tests remain in the full suite;
source pins were adjusted only for changed dimensions and Configure inventory.

Final full suite: **408 tests, 0 failures, 0 errors, 0 skipped**; `gradlew.bat test build`
completed successfully, including check/remap/assemble. No tests were excluded.

Live `gradlew.bat runClient` reached Minecraft 1.21.11 / Fabric Loader 0.19.2,
Aurora ready, loaded the main screen and opened Pack Tweaks. Checks covered
the supplied mark, nine grid cards, five list rows, module search, explicit
Configure after filtering, original toggle behavior, slider drag/release and
max-end knob clearance, enum selection and upward popup placement, scrolling,
stationary header and restored left-aligned labels. The dev profile's original
shield style, toggle and particle size were restored after interaction checks.
Config/options backups were restored byte-for-byte after client shutdown; the
runClient Gradle invocation ended with BUILD SUCCESSFUL.

No shader, missing-texture or GL clip error was observed in the exercised screens.
Expected development warnings include the dev auth/Realms JWT warning, absent
main-source-set classpath entries and Java 25/LWJGL native-access/deprecation
warnings. Automated desktop arrow presses did not visibly change the slider;
the existing deterministic slider keyboard/snap/modifier tests pass. Confirm
keyboard interaction manually during visual review rather than treating that
automation attempt as runtime keyboard acceptance.

Performance facts: the mark's RGBA allocation is 256 KiB versus about 38.1 MiB
for the source; wrapped labels and measured rows cache by font/width, card-bound
arrays are reused, and the main viewport is cached at resize. New Configure
controls are init-owned, not constructed per frame. No frame-time speedup or
unchanged total GPU cost is claimed; a smaller viewport can display more cards,
so representative gameplay performance remains a user review item.

## Deferred and visual-review gate

UX-4 behavioral reorganization, UX-5 low-risk additions, UX-6 advanced additions
and broader UX-7 rollout remain deferred. Optional 480px detail width, F1 help
and section navigation are also deferred.

Review at your normal GUI scale and accent/roundness: mark clarity, left label
alignment, compact spacing, long labels, Configure versus toggle, popup layering,
keyboard focus/help expectations, scroll/thumb reach and glass continuity.
Runtime correctness checks are not visual acceptance. Stop at this pilot.

## Local implementation commits

- `ac791dd` Define compact Aurora geometry and measurement
- `0dbc9a9` Add opt-in measured compact setting rows
- `1cbc1de` Pilot compact Aurora main screen and supplied branding
- `5fd4a05` Pilot compact Pack Tweaks detail layout

The documentation commit records this pilot and visual-review gate. Nothing was pushed.
