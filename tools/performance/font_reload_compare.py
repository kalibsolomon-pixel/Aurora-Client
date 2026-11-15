"""Same-session A/B for Aurora font preparation in a disposable parity client."""
import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('fixture', type=Path)
args = parser.parse_args()
root = args.fixture.resolve()
control = root / 'control'
log = root / 'game/logs/latest.log'
sender = Path(__file__).resolve().parents[1] / 'parity/command.py'


def command(text):
    subprocess.run([sys.executable, str(sender), str(control), text], check=True,
                   stdout=subprocess.DEVNULL)


def wait_for(pattern, offset, timeout=30):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        text = log.read_text(encoding='utf-8', errors='replace')[offset:]
        match = re.search(pattern, text)
        if match:
            return match
        time.sleep(.05)
    raise RuntimeError('Timed out: ' + pattern)


command('pause')
rows = []
for enabled in [False, True, True, False, False, True]:
    command('cfg perfDeferAuroraFonts ' + str(enabled).lower())
    offset = len(log.read_text(encoding='utf-8', errors='replace'))
    command('reload')
    elapsed = int(wait_for(r'reload overlay cleared after (\d+) ms', offset)[1])
    command('font-state')
    pending = int(wait_for(r'fonts pending=(\d+)', offset)[1])
    command('font-first-use aurora:inter_medium_ui')
    active_use = wait_for(r'font-first-use aurora:inter_medium_ui width=(\d+) us=(\d+)', offset)
    command('font-first-use aurora:roboto_semibold_ui')
    first_use = wait_for(r'font-first-use aurora:roboto_semibold_ui width=(\d+) us=(\d+)', offset)
    command('font-check')
    check = wait_for(r'font-check faces=(\d+) widthChecksum=(-?\d+)', offset)
    row = dict(enabled=enabled, reloadMs=elapsed, pendingBeforeLookup=pending,
               activeUseMicros=int(active_use[2]),
               firstUseMicros=int(first_use[2]), firstUseWidth=int(first_use[1]),
               faces=int(check[1]), widthChecksum=int(check[2]))
    rows.append(row)
    print(json.dumps(row), flush=True)
if len({(row['faces'], row['widthChecksum']) for row in rows}) != 1:
    raise AssertionError('Font metrics changed')
command('cfg perfDeferAuroraFonts true')
(root / 'font-reload-comparison.json').write_text(json.dumps(rows, indent=2) + '\n')
