"""Send one acceptance command and require its matching acknowledgement."""
import argparse
import time
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('control', type=Path)
parser.add_argument('command')
parser.add_argument('--timeout', type=float, default=15)
args = parser.parse_args()
if not (args.control / 'DISPOSABLE.txt').is_file():
    raise SystemExit('Disposable probe marker required')
command = args.control / 'command.txt'
if command.exists():
    raise SystemExit('Previous command still pending')
result = args.control / 'result.txt'
if result.exists():
    result.unlink()
temporary = args.control / 'command.pending'
temporary.write_text(args.command + '\n')
temporary.replace(command)
deadline = time.monotonic() + args.timeout
while time.monotonic() < deadline:
    if result.exists():
        text = result.read_text()
        if text == args.command + '\nOK\n':
            print(text.strip())
            break
        if text.startswith('FAILED:'):
            raise SystemExit(text)
    time.sleep(.05)
else:
    raise SystemExit('Timed out waiting for ' + args.command)
