# Aurora branding update — 2026-11-02

The title screen identifies Aurora above the existing five gameplay/settings actions.
This change replaces its old white mark with the owner's latest teal/violet ribbon,
and supplies the app icon for the mod list in launchers and Mod Menu.

## Sources

The November 1 Downloads files match the native launcher's assets byte for byte:

| Downloads | Native launcher | Bundled resource |
| --- | --- | --- |
| `aurora-refined.svg` | `native/assets/aurora.svg` | `assets/aurora/branding/aurora.svg` |
| `aurora-app-icon.png` | `native/assets/icon.png` | `assets/aurora/icon.png` |

SVG SHA-256: `780039369189022782249d46daf69c0eaf779c7bfe7599c1b683080b5cf9c205`.
Icon SHA-256: `ac05f590b6488609300b461ee8aabac34c45779762c31ba44cd39894f7ed9693`.

Minecraft uses the existing `aurora_mark.png` texture, generated from the original
SVG by `python tools/prepare_brand.py` (build-time resvg-py 0.5.0 and Pillow).
The SVG viewport is fitted proportionally and transparently into the existing
256px square. Its source remains bundled. Fabric metadata declares the original
1024px app icon at `assets/aurora/icon.png`, following the
[Fabric icon specification](https://wiki.fabricmc.net/documentation:fabric_mod_json_spec).

No layout, control, focus/input semantics, theme treatment, effects or runtime
dependencies were added. Existing single-texture drawing remains in use.
No files were deleted. Pre-existing untracked release and benchmark work was preserved.

## Native visual verification

Screenshots use the remapped distributable in disposable directories, normal
Fabric 0.19.2, Minecraft 1.21.11, Mod Menu 17.0.1, and OpenGL on RX 9070 XT.
The original Downloads icon and SVG were checked inside the built JAR, and the
capture probe was confirmed absent from that distributable.

| Surface | Before | After |
| --- | --- | --- |
| Title, 1920x1080, GUI scale 3 | [Before](title-before.png) | [After](title-after.png) |
| Mod Menu list and details | [Before](modmenu-before.png) | [After](modmenu-after.png) |
| Title, 1280x720, GUI scale 2 | — | [After](title-after-1280.png) |

The ribbon retains its shape and colors, is centered above the action stack,
and does not cover labels or controls. Mod Menu displays the app icon in both
the selected row and detail header. Hover screenshots are also available under
`build/gui-audit/branding-after-20261102/game/screenshots/`.
Launcher support was verified at the packaged Fabric metadata/resource level;
no live launcher screenshot was captured. No separate accessibility audit or
performance benchmark was performed for this asset-only change.

Reproduce after building the probe with
`gradlew.bat -I tools/gui/runtime.init.gradle guiProbeRemapJar`:

```powershell
python tools/gui/run.py branding-check --plan branding --sizes 1920x1080@3,1280x720@2 --jar build/libs/aurora-3.2.0.jar --extra-mod build/gui-audit/tooling/modmenu-17.0.1.jar
```

## Checks

- Java compilation and `remapJar`: pass.
- `OwnerMenuGeometryTest`: all six tests pass.
- SVG raster regeneration: byte-identical on repeat.
- Native title and Mod Menu checks: pass; see [report](native-report.json).
- `git diff --check`: pass.
- Full suite: 601 tests, 15 failures, 1 skipped. Every failure also occurs in an
  isolated archive of unchanged HEAD (17 failures there); no new failed tests.
  See [comparison](test-comparison.json). These unrelated UI contract failures
  were preserved rather than changing other features in this branding task.

Updated distributable: `build/libs/aurora-3.2.0.jar`.
SHA-256: `6939c3ad07834d757356a51c8ec75ac02fb7d83667e450cba6ea17f9b2891bd8`.
