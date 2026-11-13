"""Actual-renderer alias regression: translating a four-pixel pattern must average out.

Run with BlurTestScreen open in a marked parity fixture. Both copies are settled
and screen-captured, without F2 (which intentionally suppresses glass).
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from PIL import Image, ImageChops, ImageStat

parser = argparse.ArgumentParser()
parser.add_argument('fixture', type=Path)
parser.add_argument('window')
parser.add_argument('output', type=Path)
parser.add_argument('--radius', type=int, default=9)
parser.add_argument('--box', default='300,490,960,620')
parser.add_argument('--cli', default='C:/Users/kalib/AppData/Local/Programs/node-v24.21.0/node_modules/agent-desktop/bin/agent-desktop-win32-x64.exe')
args = parser.parse_args()
root = args.fixture.resolve()
if not (root / 'control/DISPOSABLE.txt').is_file():
    raise SystemExit('Disposable fixture required')
args.output.mkdir(parents=True, exist_ok=False)
sender = Path(__file__).resolve().parents[1] / 'parity/command.py'
log = root / 'game/logs/latest.log'
subprocess.run([sys.executable, str(sender), str(root / 'control'), 'blur'], check=True)
captures = []
for phase in [0, 1, 2, 3, 0]:
    state = f'backdrop=STRIPES radius={args.radius} slide=0 slideamp=0 patternscale=0.25 phase={phase} lighting=off opacity=0.19 ping={len(captures)}'
    offset = len(log.read_text(encoding='utf-8', errors='replace'))
    (root / 'game/blurtest_state.txt').write_text(state + '\n')
    deadline = time.monotonic() + 10
    while state not in log.read_text(encoding='utf-8', errors='replace')[offset:]:
        if time.monotonic() > deadline: raise RuntimeError('Blur state not rendered')
        time.sleep(.02)
    # The state echo occurs during GUI extraction; allow its deferred GPU pass
    # and native presentation to settle, then capture the stationary output.
    time.sleep(.25)
    path = args.output / f'{len(captures)}-phase-{phase}.png'
    raw = subprocess.run([args.cli, 'screenshot', '--window-id', args.window, str(path)], capture_output=True, text=True, check=True)
    if not json.loads(raw.stdout)['ok']: raise RuntimeError(raw.stdout)
    captures.append(path)

# 1280x960 client, GUI scale 3: body of the panel below the status text.
box = tuple(map(int, args.box.split(',')))
images = [Image.open(path).convert('RGB').crop(box) for path in captures]
diffs = [ImageChops.difference(images[i-1], images[i]) for i in range(1, len(images))]
report = dict(radius=args.radius, box=box,
              phaseChanges=[dict(mean=sum(ImageStat.Stat(d).mean)/3,
                                 maximum=max(c[1] for c in d.getextrema())) for d in diffs])
report['pass'] = all(row['maximum'] <= 2 for row in report['phaseChanges'])
(args.output / 'result.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
raise SystemExit(0 if report['pass'] else 1)
