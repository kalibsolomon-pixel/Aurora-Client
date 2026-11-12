"""One-time, explicit portable-settings allowlist for the 3.1.0 reference export.

Reads an isolated copy only. Never reads or modifies a live instance.
"""

from source_tree import client_path
import json
from pathlib import Path

PREFIXES = tuple("""
zoom fullBright smoothCamera smoothAnimations swing viewBob idleSway animationCurve
hitColor glintColor info armor effectExpiry toolDurability freeLook saturationBar
tabPing toggleSprint toggleSneak blockOverlay potion totem shield itemScale
mainHand offHand playerHealth noFog fogDistance underwater lava
cps reach sprintHud waypointDisplay waypointBeam waypointLabel waypointBlock
waypointShow deathWaypoint crosshair hitbox particle containerPreview itemPhysics
keystrokes minimap worldMapShow resourcepackBrowser
""".split())
EXACT = set("""
keybindVersion customTitleScreen moduleGridView textRendererMode activeFont textWeight textItalic
textUnderline textCase textHudShadow searchInstant searchTypoTolerance themeEnabled theme
showFps showCoords showTime hudColor pingHudEnabled nametagPingEnabled nametagTotemPopsEnabled
hudEditorKey waypointDropKey waypointManagerKey waypointMaxVisible
waypointWorldMarkerLimit waypointMaxDistance waypointsEnabled worldMapEnabled
worldMapKey damageTiltEnabled damageTiltStrength smoothFovEnabled smoothFovSpeed
hotbarBounceEnabled serverListDragReorder moduleLayouts hitregEnabled
""".split())

def export(source: Path, target: Path):
    raw = json.loads(source.read_text(encoding="utf-8"))
    selected = {k: v for k, v in raw.items() if k in EXACT or k.startswith(PREFIXES)}
    # Migration history and legacy exclusion state are not visual preferences.
    selected.pop("effectExpiryExcludedEffects", None)
    selected.pop("minimapUseWorldMapCache", None)
    selected["moduleLayouts"].pop("stats", None)  # removed HUD, not a shipped module
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(selected, indent=2) + "\n", encoding="utf-8")
    return selected

if __name__ == "__main__":
    # The 3.2.0 resource carries the reviewed 3.1.0 export verbatim; only the
    # packaged resource name follows the release version.
    export(Path("build/release-3.1.0/reference/aurora.json"),
           client_path("resources/assets/aurora/defaults/3.2.0.json"))
