"""Compare a fixed native-window region across successive desktop captures.

Run against BlurTestScreen's stationary STRIPES backdrop. The test samples the
panel body below its labels; it never calls Minecraft's glass-suppressing F2.
"""
import argparse
import json
import shutil
import subprocess
import time
from pathlib import Path

from PIL import Image, ImageChops, ImageStat


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('window')
    parser.add_argument('output', type=Path)
    parser.add_argument('--frames', type=int, default=16)
    parser.add_argument('--box', default='220,240,640,365')
    parser.add_argument('--cli', default=shutil.which('agent-desktop'))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    box = tuple(map(int, args.box.split(',')))
    executable = Path(args.cli)
    if executable.suffix.lower() in ('.cmd', '.ps1'):
        executable = executable.parent / 'node_modules/agent-desktop/bin/agent-desktop-win32-x64.exe'
    previous = None
    differences = []
    started = time.monotonic()
    for index in range(args.frames):
        path = args.output / f'{index:03}.png'
        result = subprocess.run([str(executable), 'screenshot', '--window-id', args.window, str(path)],
                                capture_output=True, text=True, check=True)
        envelope = json.loads(result.stdout)
        if not envelope['ok']:
            raise RuntimeError(envelope)
        current = Image.open(path).convert('RGB').crop(box)
        if previous is not None:
            delta = ImageChops.difference(previous, current)
            differences.append({'mean': sum(ImageStat.Stat(delta).mean) / 3,
                                'max': max(channel[1] for channel in delta.getextrema())})
        previous = current
    report = {'box': box, 'frames': args.frames, 'seconds': time.monotonic() - started,
              'differences': differences, 'stable': all(d['max'] == 0 for d in differences)}
    (args.output / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    raise SystemExit(0 if report['stable'] else 1)


if __name__ == '__main__':
    main()
