"""Run the marked native acceptance fixture; preserve every process and capture.

The existing stress runner remains the matched reference/candidate performance
driver. This driver is for functional integration and native screenshots only.
"""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import sys
import time
import hashlib

sys.path.insert(0, 'C:/Dev/aurora-launcher/tools/benchmark')
from process_capture import Reader


def run(root, resume, multiplayer=False, runtime_root=None, extended=False, rendering=False, review_seconds=120):
    assert (root / 'DISPOSABLE-RESEARCH.txt').is_file()
    manifest = root / 'runtime-manifest.json'
    if manifest.exists():
        for entry in json.loads(manifest.read_text()):
            assert hashlib.sha256((root / entry['path']).read_bytes()).hexdigest() == entry['sha256']
    spec = json.loads((root / 'launch-private.json').read_text())
    assert 'performance-initiative-ii\\reference-client' not in spec['classpath']
    assert Path(spec['workingDirectory']).resolve() == root / 'game'
    if runtime_root:
        assert (runtime_root / 'runtime-manifest.json').is_file()
        for entry in json.loads((runtime_root / 'runtime-manifest.json').read_text()):
            assert hashlib.sha256((runtime_root / entry['path']).read_bytes()).hexdigest() == entry['sha256']
        for part in ('build/classes/java/client', 'build/resources/client'):
            spec['classpath'] = spec['classpath'].replace(str(root / part), str(runtime_root / part))
            spec['jvmArgs'] = [arg.replace(str(root / part), str(runtime_root / part)) for arg in spec['jvmArgs']]
        spec['jvmArgs'] = ['-Dfabric.dli.config=' + str(runtime_root / 'frozen-launch.cfg')
                           if arg.startswith('-Dfabric.dli.config=') else arg for arg in spec['jvmArgs']]
    probe = (runtime_root or root) / 'tooling/aurora-mapping-acceptance.jar'
    assert probe.is_file()
    entries = spec['classpath'].split(';')
    entries = [str(probe) if 'aurora-research-named.jar' in item else item for item in entries]
    assert str(probe) in entries
    args = [a for a in spec['jvmArgs'] if not a.startswith('-Daurora.research.')]
    args += ['-Daurora.mapping.output=' + str(root), '-Daurora.mapping.reviewSeconds=' + str(review_seconds)]
    if resume:
        args.append('-Daurora.mapping.resume=true')
    if multiplayer:
        args.append('-Daurora.mapping.multiplayer=true')
    if extended:
        assert not multiplayer and not resume
        args.append('-Daurora.mapping.extended=true')
    if rendering:
        assert not multiplayer and not resume and not extended
        args.append('-Daurora.mapping.rendering=true')
    name = 'resume' if resume else 'acceptance'
    samples = []
    with (root / (name + '-stdout.log')).open('xb') as out, (root / (name + '-stderr.log')).open('xb') as err:
        child = subprocess.Popen([spec['executable'], *args, '-cp', ';'.join(entries), spec['mainClass'], *spec['args']],
                                 cwd=root / 'game', stdout=out, stderr=err, creationflags=subprocess.CREATE_NO_WINDOW)
        reader = Reader(child.pid)
        identity = dict(pid=child.pid, creationId=reader.creation_id)
        print(json.dumps(identity), flush=True)
        try:
            while child.poll() is None:
                samples.append(reader.sample())
                time.sleep(1)
        except OSError:
            child.wait(timeout=30)
        finally:
            reader.close()
        identity['exitCode'] = child.wait()
    (root / (name + '-process.json')).write_text(json.dumps(identity, indent=2))
    if samples:
        with (root / (name + '-process.csv')).open('x', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=list(samples[0])); writer.writeheader(); writer.writerows(samples)
    report = json.loads((root / (name + '-report.json')).read_text())
    failures = [r for r in report['checks'] if not r['passed']]
    log = (root / (name + '-stdout.log')).read_text(encoding='utf-8', errors='replace')
    log_checks = dict(wrongRenderThread=log.count('Rendersystem called from wrong thread'),
                      failedTextureClose=log.count('Failed to close texture'))
    (root / (name + '-log-checks.json')).write_text(json.dumps(log_checks, indent=2) + '\n')
    failures += [dict(check=key, count=value) for key, value in log_checks.items() if value]
    print(json.dumps(dict(exitCode=child.returncode, checks=len(report['checks']), failures=failures)), flush=True)
    return int(child.returncode != 0 or bool(failures))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--resume', action='store_true')
    parser.add_argument('--multiplayer', action='store_true')
    parser.add_argument('--runtime-root', type=Path)
    parser.add_argument('--extended', action='store_true')
    parser.add_argument('--rendering', action='store_true')
    parser.add_argument('--review-seconds', type=int, choices=range(0,601), default=120)
    args = parser.parse_args()
    raise SystemExit(run(args.root.resolve(), args.resume, args.multiplayer, args.runtime_root, args.extended, args.rendering, args.review_seconds))
