# Text-field editing audit and undo/redo

Source audit, 2026-11-16. Runtime acceptance is assigned to the parent; no game was
started for this change. Rendering, layout, colours and glass are unchanged.

## Baseline audit

`V` below means vanilla already implements **Ctrl+A**, **Ctrl+Left/Right** word
jump, **Ctrl+Backspace/Delete** word deletion, **Shift-selection** (including
Ctrl+Shift word selection), **Home/End**, and **copy/cut/paste**. This was checked
against the Loom-cached Minecraft `EditBox` sources/bytecode for 26.1.2, 26.2 and
26.3, under `C:/Users/kalib/.gradle/caches/fabric-loom/<version>/` (the
`minecraft-client-only.jar` and cached decompiled sources in `decompile/v1.zip`).
Vanilla word navigation splits on ASCII spaces, and normal movement uses Unicode
code points, not grapheme clusters. No navigation replacement was added.

`C` means focus has a blinking caret in the themed renderer; unified material
primarily relies on that caret, with additional rings/outlines in other material
paths. **Selection exists functionally but its highlight is not painted by
Aurora's existing rendering takeover.** All rows below inherit this limitation.

| Field | Implementation | Existing editing | Undo/redo before → after | Validation/feedback today | Enter / Escape today | Focus |
|---|---|---|---|---|---|---|
| Modules grid search (`AuroraScreen`) | EditBox | V | None → shared history | 64-character cap, silent truncation | No submit / clears query, then closes | C |
| Feature detail | Hosts the search/numeric settings below; no separate field | V via hosted fields | None → shared history | Per hosted setting | Per hosted setting / screen close after field releases focus | C |
| `SearchListSetting` | EditBox | V | None → shared history | Default 32-character cap; no validation message | No submit / unfocuses first, next Escape closes screen | C |
| `ItemScaleSetting` search | EditBox | V | None → shared history | Default 32-character cap; no validation message | No submit / unfocuses first | C |
| `EffectExpiryListSetting` search | EditBox | V | None → shared history | Default 32-character cap; no validation message | No submit / unfocuses first | C |
| Resource-pack browser search (all adapters) | EditBox | V | None → shared history | 80-character cap; no validation message | No submit / closes detail modal first if open, otherwise unfocuses field before screen close | C |
| Profile Manager search | EditBox | V | None → shared history | 48-character cap | No submit / clears non-empty query, then closes | C |
| Profile Manager create/inline rename | EditBox (rename inherited from ManagerListScreen) | V | None → shared history | 48-character cap; empty cancels; duplicate/invalid name produces a failure toast | Commit / cancel editor | C |
| Profile Options name | EditBox | V | None → shared history | 48-character cap; empty/taken/invalid resets silently to original name | Commit and unfocus / closes **and commits** rename | C |
| Waypoint editor: name, coordinates, dimension, group, description, ARGB | Six EditBoxes | V | None → shared history | Description 256, others 128 characters; Save validates coordinates, dimension and 8-digit hex and shows a heading error; blank name becomes Waypoint | Save / discard draft | C |
| Waypoint Manager search | EditBox | V | None → shared history | Default 32-character cap; no validation message | No submit / closes screen | C |
| ManagerListScreen inline rename (including waypoints) | EditBox | V | None → shared history | 48-character cap; trimmed empty cancels; subclass decides other validation | Commit / cancel editor | C |
| ColorPickerScreen hex | EditBox | V | None → shared history | Nine characters; accepts six/eight hex digits with optional #; invalid/incomplete value silently leaves colour unchanged | No submit (Apply button commits) / discards | C |
| IconPickerScreen search | EditBox | V | None → shared history | 48-character cap; no validation message | Applies selected icon if any / closes | C |
| SliderSetting numeric entry (opt-in) | EditBox | V for keyboard; pointer click only focuses, does not reposition caret | None → shared history | Six characters; strips invalid input, permits leading sign and one decimal for doubles; commit snaps/clamps; invalid/empty reverts silently | Commit / revert numeric edit | C |
| Pulsar colour and sound fields | **KTextField extends EditBox**, not an independent editor | V inherited | None → same inherited history | Colour: six characters, hex insertion-fragment/setValue filter, updates at six digits; no error message. Sound ID: 128 characters, unknown IDs silent | No submit; responders update values / closes dashboard | C |

## Implementation

`core/.../ui/text/EditHistory` stores immutable text/caret/anchor snapshots in a
bounded undo/redo history (100 transitions combined). Typed word characters join
one step until punctuation/whitespace, caret/selection/focus changes, clipboard
actions, deletion, or a pause of at least 1,000 ms. Paste, cut, deletion and
selection replacement are individual steps. New edits discard the redo branch.

The existing shared `EditBoxMixin` wraps keyboard/character input and records the
accepted value after synchronous responders. `setValue` outside a user edit clears
history, including same-value resets; responder sanitization during input belongs
to that edit. Undo/redo use `setValue` so searches and colour previews update, then
restore the caret/anchor. If validation or a changed length cap rejects restoration,
history is discarded. No new mixin, accessor or catalog binding is required.

Only the existing `customTitleScreen` + themed-screen takeover enables shortcuts:
Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y, using vanilla's platform editing modifier, including
its macOS Command behavior. Non-Aurora fields keep vanilla input. NativeKeyboard
matches GLFW keys on 26.1.2/26.2 and logical SDL shortcut keycodes on 26.3.
Pulsar's ThemedScreen and inherited EditBox input give KTextField parity without a
second key handler or history. No history code runs in the render path; allocation
and history work occur on input only. Frame-time performance was not measured.

ModernUI-MC's `MixinEditBox` was read as a behavior reference only; this implementation
uses Aurora's own event transactions and snapshot history, with no copied code or
new dependencies.

## Automated verification

| Command / line | Passed | Failed/errors | Skipped |
|---|---:|---:|---:|
| Targeted `gradlew test -Paurora.minecraft=26.3 --tests com.aurora.client.ui.text.*` | 30 | 0 | 0 |
| `python tools/parity.py`: 26.1.2 build + tests | 1,145 | 0 | 1 |
| `python tools/parity.py`: 26.2 build + tests | 1,145 | 0 | 1 |
| `python tools/parity.py`: 26.3 build + tests | 1,147 | 0 | 1 |
| `python -m unittest discover -s tools` | 43 | 0 | 0 |

The skipped Java test is the existing opt-in stress test. The 30 new tests comprise
27 history tests and three native shortcut tests, exercised on every parity line.
Layering, adapter ownership and FeatureCatalog checks passed. `git diff --check`
passed; changed source files use LF. These checks do not constitute a mixin boot.

## Parent-owned dev-client acceptance

Repeat on **26.1.2, 26.2 and 26.3**, on the workstation's second portrait monitor.
Use a disposable Loom `-Paurora.runDir=build/<fixture>/game`; if using the owner's
`run/` instead, back up and restore aurora.json, profiles and options.txt as AGENTS
requires. Use test profiles/waypoints rather than modifying personal records.

1. With `customTitleScreen=true`, open Modules search. Type `alpha beta` quickly.
   Ctrl+Z removes `beta`, then the space, then `alpha`; Ctrl+Shift+Z restores each
   step. Undo again and use Ctrl+Y. Pause over one second between two letters and
   confirm they undo separately. Use punctuation and confirm a separate boundary.
2. Type `alpha`, move Left then Right, type `x`: undo removes only `x`. Repeat with
   Home/End, a pointer caret move and focus away/back. Check Shift+Left,
   Ctrl+Shift+Left and Ctrl+A selection by copying/replacing text (the existing
   renderer does not show its selection highlight). Undo a replacement/cut and
   verify the selected range and direction are restored via Shift/arrows/copy.
3. Check Ctrl+Left/Right and Ctrl+Backspace/Delete on `alpha beta`. Undo each
   deletion. Paste `one two`, undo it in one step, redo, then undo and type a new
   character: redo must do nothing. At empty history, undo/redo must be consumed
   without screen commands. Unfocused/disabled/read-only fields must not edit.
4. Repeat a short type/undo/redo/paste sequence in SearchListSetting, Item Scale
   search, Effect Expiry search, and numeric entry on the frame-cap slider. Search
   results must follow history. In the slider, insert invalid characters, then
   undo accepted edits; sanitization must not clear prior history. Enter clamps
   and commits; Escape reverts the numeric edit. Repeat hosted settings in both
   AuroraScreen and FeatureDetailScreen.
5. Exercise Profile Manager search, new profile, inline rename and Profile Options
   name; test 48-character truncation and a duplicate name. Undo must stay within
   the current field. Escape on search clears its query and history; Escape in an
   inline editor cancels, while Escape in Profile Options commits as before.
6. In Waypoint Manager, test search and inline rename, then all six fields in the
   waypoint editor. Undo coordinate/ARGB edits, confirm Enter still validates and
   Save still works; Escape discards. Check invalid coordinates/dimension/hex and
   the existing error heading, plus the description/name length caps.
7. In Color Picker, replace hex with `#FF336699`, undo/redo and verify the preview;
   invalid `#GGGGGG` must retain the last valid colour. Change a colour slider/pad
   (programmatic hex update), then Ctrl+Z must not recover the previous hex history.
   Apply still commits, Escape still discards. In Icon Picker, test search history
   and Enter selection. In resource packs, test history, filtering and first-Escape
   unfocus (also confirm a detail modal closes before the search field).
8. In Pulsar Settings, test colour hex and sound identifier KTextFields, including
   selection replacement, paste/cut, undo/redo and six-character/128-character
   caps. Existing responders must update the colour/sound value after restoration.
9. Also test Aurora-owned vanilla world-selection and multiplayer searches. In a
   non-Aurora vanilla field (e.g. Edit Server), Ctrl+Z/Y must retain vanilla behavior.
   Turn `customTitleScreen=false` and confirm Aurora's undo interception is absent.
   Inspect logs/diagnostics for EditBoxMixin failures after each line's boot.
10. On macOS if available, repeat undo/redo with Command. On 26.3, additionally
    test a layout where logical Z/Y differ from physical scancodes; shortcut names
    should follow vanilla's logical editing keys. Keep factory and live theme
    settings for the focus check; there is no visual change to accept here.

No implementation questions remain. Runtime/mixin acceptance and the noted existing
selection/validation inconsistencies remain with the parent. The branch/worktree
and normal per-line build outputs are retained for review and reuse; the parent
owns their eventual lifecycle after integration.
