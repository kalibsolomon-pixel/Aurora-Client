"""Diagnostic-only PresentMon capture, exact research child and unique owned trace.
No elevation, service, timer, privilege, driver or global-session control.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
sys.path.insert(0, 'C:/Dev/aurora-launcher/tools/benchmark')
from etw_query import query
from process_capture import Reader


def capture(root):
    assert (root / 'DISPOSABLE-RESEARCH.txt').is_file()
    executable = Path('C:/Users/kalib/AppData/Local/Temp/aurora-p3a1-benchmark-tools-72f579a9-6511-4a18-be35-f14e6962298c/PresentMon-2.3.1-x64.exe')
    assert hashlib.sha256(executable.read_bytes()).hexdigest() == '364e5d98d4d134bd54dd25c22ed2ca2f4883f8bc3ed6502bee0c151e3436d30c'
    deadline = time.monotonic() + 150
    while not (root / 'process.json').exists():
        assert time.monotonic() < deadline
        time.sleep(.1)
    identity = json.loads((root / 'process.json').read_text())
    reader = Reader(identity['pid'])
    assert reader.creation_id == identity['creationId']
    try:
        log = root / 'game/logs/latest.log'
        while not log.exists() or 'Checkpoint scene-setup-complete' not in log.read_text(errors='replace'):
            assert time.monotonic() < deadline, 'Scene preparation timed out'
            reader.sample()
            time.sleep(.25)
        time.sleep(10)
        session = 'Aurora-P3A3-II-' + str(identity['pid']) + '-' + identity['creationId'][-6:]
        target = root / 'presentmon.csv'
        assert not target.exists()
        args = [str(executable), '--process_id', str(identity['pid']), '--output_file', str(target),
                '--qpc_time', '--timed', '100', '--terminate_after_timed', '--terminate_on_proc_exit',
                '--session_name', session, '--set_circular_buffer_size', '8192', '--no_console_stats']
        with (root / 'presentmon.stdout.log').open('xb') as out, (root / 'presentmon.stderr.log').open('xb') as err:
            before = time.perf_counter_ns()
            child = subprocess.Popen(args, stdout=out, stderr=err, creationflags=subprocess.CREATE_NO_WINDOW)
            observations = []
            started = time.monotonic()
            scheduled = [1, 23, 79]
            while child.poll() is None:
                elapsed = time.monotonic() - started
                if scheduled and elapsed >= scheduled[0]:
                    observations.append(dict(afterSeconds=scheduled.pop(0), **query(session)))
                if elapsed > 115:
                    child.terminate()  # Only this exact owned collector handle.
                    break
                time.sleep(.1)
            child.wait(timeout=10)
        result = dict(pid=identity['pid'], creationId=identity['creationId'], session=session,
                      beforeCollectorQpcNs=before, afterCollectorQpcNs=time.perf_counter_ns(),
                      collectorExitCode=child.returncode, lossSnapshots=observations,
                      claim='OS submission/display estimates; never physical latency',
                      observerOverhead='Uncalibrated; separate diagnostic cohort')
        (root / 'presentation-capture.json').write_text(json.dumps(result, indent=2))
        print(json.dumps(result), flush=True)
    finally: reader.close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('root', type=Path)
    capture(parser.parse_args().root.resolve())
