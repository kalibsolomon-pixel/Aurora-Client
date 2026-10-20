"""Metrics for the owner-feedback probe plan (tools/gui/run.py --plan owner).

Usage: python tools/gui/owner_metrics.py <fixture-name> [<fixture-name> ...]
Reads build/gui-audit/<name>/game/screenshots and prints:
  * minimap motion: per-frame change of the minimap region across consecutive
    frames while sprinting / turning (0.0 = frozen frame, i.e. visible stepping);
  * glint: held-item region difference, enchanted vs plain sword.
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2] / 'build/gui-audit'
MINIMAP = (1500, 0, 1920, 420)      # TOP_RIGHT default at 1920x1080@3
HELD_ITEM = (1150, 520, 1920, 1080)  # first-person right hand


def mean_diff(a, b, box):
    d = ImageChops.difference(a.crop(box).convert('RGB'), b.crop(box).convert('RGB'))
    return sum(ImageStat.Stat(d).mean) / 3


def burst(shots, name):
    frames = sorted(shots.glob(f'*{name}-*.png'), reverse=True)  # names count down
    images = [Image.open(p) for p in frames]
    return [round(mean_diff(a, b, MINIMAP), 2) for a, b in zip(images, images[1:])]


for fixture in sys.argv[1:]:
    shots = ROOT / fixture / 'game/screenshots'
    print(f'== {fixture}')
    for name in ('minimap-walk', 'minimap-turn'):
        deltas = burst(shots, name)
        frozen = sum(1 for d in deltas if d < 0.05)
        print(f'{name}: {len(deltas)} frame pairs, frozen={frozen}, deltas={deltas}')
    enchanted = next(shots.glob('*glint-alt-enchanted.png'), None)
    plain = next(shots.glob('*glint-alt-plain.png'), None)
    additive = next(shots.glob('*glint-additive-enchanted.png'), None)
    if enchanted and plain:
        e, p = Image.open(enchanted), Image.open(plain)
        print('glint alternative, enchanted vs plain held item:', round(mean_diff(e, p, HELD_ITEM), 2))
        if additive:
            print('glint additive vs plain held item:', round(mean_diff(Image.open(additive), p, HELD_ITEM), 2))
