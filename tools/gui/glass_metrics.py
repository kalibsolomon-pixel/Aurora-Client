"""Per-screen glass-capture metrics from a `--plan glass` fixture run.

Attributes each [GlassStats] line (requires --jvm-arg -Daurora.glassStats=true) to the
screen whose `glass-begin`/`glass-end` markers surround it, frame-weighted.

    python tools/gui/glass_metrics.py <fixture-name> [<fixture-name> ...]
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / 'build/gui-audit'
STATS = re.compile(r'\[GlassStats\] frames=(\d+) panels/frame avg=([\d.]+) max=(\d+) \| ms/frame total=([\d.]+) max=([\d.]+)'
                   r'.*declines=(\d+) poolExhausted=(\d+)')
MARK = re.compile(r'\[gui-audit\] glass-(begin|end) (\S+)')


def metrics(name):
    screen, out = None, {}
    for line in (ROOT / name / 'stdout.log').read_text(errors='replace').splitlines():
        m = MARK.search(line)
        if m:
            screen = m.group(2) if m.group(1) == 'begin' else None
            if screen:
                out.setdefault(screen, dict(frames=0, panels=0.0, ms=0.0, max=0, declines=0))
            continue
        m = STATS.search(line)
        if m and screen:
            f = int(m.group(1)); s = out[screen]
            s['frames'] += f
            s['panels'] += float(m.group(2)) * f
            s['ms'] += float(m.group(4)) * f
            s['max'] = max(s['max'], int(m.group(3)))
            s['declines'] += int(m.group(6))
    return out


if __name__ == '__main__':
    runs = {n: metrics(n) for n in sys.argv[1:]}
    screens = list(dict.fromkeys(s for r in runs.values() for s in r))
    print('screen'.ljust(18) + ''.join(f'{n[:24]:>28}' for n in runs))
    for s in screens:
        cells = []
        for r in runs.values():
            v = r.get(s)
            if not v or not v['frames']:
                cells.append('no glass frames'.rjust(28))
            else:
                cells.append(f"{v['panels'] / v['frames']:5.1f} pnl {v['ms'] / v['frames']:6.2f} ms max{v['max']:3d}".rjust(28))
        print(s.ljust(18) + ''.join(cells))
