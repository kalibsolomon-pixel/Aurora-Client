"""Before/after native-pixel crops for the Spxctra simplification report (never resampled).

    python tools/gui/spx_evidence.py <before-fixture> <after-fixture>

Writes docs/spxctra/{before,after}-<name>.png from 1920x1080@3 captures.
"""
import sys
from pathlib import Path
from PIL import Image

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / 'docs/spxctra'
P = '1920x1080-s3-'
CROPS = {
    'main-grid': ('main-grid', (440, 200, 1480, 880)),
    'main-settings': ('main-settings', (440, 200, 1480, 880)),
    'detail-world-map': ('detail-world_map', (440, 88, 1480, 995)),
    'detail-pack-tweaks': ('detail-pack_tweaks', (440, 88, 1480, 995)),
    'detail-crosshair': ('detail-crosshair', (440, 88, 1480, 995)),
    'waypoints': ('waypoints-dense', (245, 35, 1675, 1045)),
    'profiles': ('profiles', (270, 175, 1650, 905)),
    'world-map-toolbar': ('world-map', (0, 0, 1920, 110)),
    'pack-browser': ('pack-browser-loaded', (430, 160, 1900, 800)),
    'waypoint-edit': ('waypoint-edit-existing', (440, 295, 1480, 785)),
}

if __name__ == '__main__':
    before, after = sys.argv[1:3]
    OUT.mkdir(parents=True, exist_ok=True)
    for label, (shot, box) in CROPS.items():
        for tag, fixture in (('before', before), ('after', after)):
            src = REPO / 'build/gui-audit' / fixture / 'game/screenshots' / f'{P}{shot}.png'
            if src.is_file():
                Image.open(src).crop(box).save(OUT / f'{tag}-{label}.png', optimize=True)
            else:
                print('missing', src)
    print('wrote', len(list(OUT.glob('*.png'))), 'crops to', OUT)
