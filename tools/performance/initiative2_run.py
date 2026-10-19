"""Launch an explicit marked research specification, sample only its exact child.
Windows window visibility is a 20ms observer bound, never scanout/interaction proof.
"""
import argparse
import ctypes
from ctypes import wintypes as w
import csv
import datetime as dt
import json
from pathlib import Path
import subprocess
import sys
import time

sys.path.insert(0, 'C:/Dev/aurora-launcher/tools/benchmark')
from process_capture import Reader


def visible_window(pid):
    user = ctypes.WinDLL('user32', use_last_error=True)
    user.GetWindowThreadProcessId.argtypes = [w.HWND, ctypes.POINTER(w.DWORD)]
    user.IsWindowVisible.argtypes = [w.HWND]
    found = []
    callback_type = ctypes.WINFUNCTYPE(w.BOOL, w.HWND, w.LPARAM)
    @callback_type
    def visit(hwnd, unused):
        owner = w.DWORD()
        user.GetWindowThreadProcessId(hwnd, ctypes.byref(owner))
        if owner.value == pid and user.IsWindowVisible(hwnd):
            found.append(int(hwnd))
        return True
    user.EnumWindows.argtypes = [callback_type, w.LPARAM]
    user.EnumWindows(visit, 0)
    return found


def run(root, timeout):
    assert (root / 'DISPOSABLE-RESEARCH.txt').is_file()
    spec = json.loads((root / 'launch-private.json').read_text())
    assert Path(spec['workingDirectory']).resolve() == root / 'game'
    command = [spec['executable'], *spec['jvmArgs'], '-cp', spec['classpath'], spec['mainClass'], *spec['args']]
    origin = time.perf_counter_ns()
    with (root / 'stdout.log').open('xb') as stdout, (root / 'stderr.log').open('xb') as stderr:
        child = subprocess.Popen(command, cwd=root / 'game', stdout=stdout, stderr=stderr,
                                 creationflags=subprocess.CREATE_NO_WINDOW)
        spawned = time.perf_counter_ns()
        reader = Reader(child.pid)
        identity = dict(pid=child.pid, creationId=reader.creation_id, beforeSpawnQpcNs=origin,
                        afterSpawnQpcNs=spawned, startUtc=dt.datetime.now(dt.timezone.utc).isoformat())
        (root / 'process.json').write_text(json.dumps(identity, indent=2) + '\n')
        print(json.dumps(identity), flush=True)
        samples, window = [], None
        last_sample = 0
        try:
            while child.poll() is None:
                now = time.perf_counter_ns()
                if now - origin > timeout * 1e9:
                    # Only the exact disposable child handle, with retained failed evidence.
                    child.terminate()
                    identity['timeout'] = True
                    break
                if window is None:
                    handles = visible_window(child.pid)
                    if handles:
                        window = dict(qpcNs=now, handles=handles, pollBoundMs=20)
                if now - last_sample >= 500_000_000:
                    try:
                        samples.append(reader.sample())
                    except OSError:
                        break
                    last_sample = now
                time.sleep(.02 if window is None else .1)
            child.wait(timeout=30)
        finally:
            reader.close()
        identity.update(exitCode=child.returncode, endQpcNs=time.perf_counter_ns(), firstVisibleWindow=window)
        (root / 'process.json').write_text(json.dumps(identity, indent=2) + '\n')
        if samples:
            with (root / 'process.csv').open('x', newline='') as stream:
                writer = csv.DictWriter(stream, fieldnames=list(samples[0]))
                writer.writeheader(); writer.writerows(samples)
        print(json.dumps(dict(exitCode=child.returncode, samples=len(samples), window=window)), flush=True)
        return child.returncode


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--timeout', type=int, default=600)
    args = parser.parse_args()
    raise SystemExit(run(args.root.resolve(), args.timeout))
