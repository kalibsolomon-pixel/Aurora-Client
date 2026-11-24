"""Record and verify per-line GPU acceptance, bound to the runtime source inputs.

record consumes fresh matrix logs/results under <logs>/<minecraft>/<backend>/.
verify is the release gate: every required row must be healthy, clean and within
the unchanged budget, and any production/build/pin edit invalidates the evidence.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def versions(root):
    return json.loads((root / 'gradle/minecraft-versions.json').read_text(encoding='utf-8'))


def fingerprint(root):
    paths = [root / p for p in ('build.gradle', 'settings.gradle', 'gradle.properties',
                               'core/build.gradle', 'ports/build.gradle', 'gradle/minecraft-versions.json',
                               'tools/parity_acceptance.py', 'tools/parity.py')]
    for directory in ('core/src/main', 'ports/src/main', 'adapters', 'tools/matrix'):
        paths += [p for p in (root / directory).rglob('*') if p.is_file()
                  and '/src/test/' not in p.as_posix() and '__pycache__' not in p.parts]
    digest = hashlib.sha256()
    # Path ordering is case-insensitive on Windows and case-sensitive on Linux, so a digest
    # recorded on one host never verified on the other. Fix the order to the Windows one
    # the recorded evidence used: lower-cased path components.
    for p in sorted(set(paths), key=lambda p: p.relative_to(root).as_posix().lower().split('/')):
        digest.update(p.relative_to(root).as_posix().encode() + b'\0')
        # Git's LF checkout is the identity on both Windows and Linux.
        data = p.read_bytes()
        if p.suffix in ('.java', '.json', '.gradle', '.properties', '.py', '.txt', '.vsh', '.fsh', '.mcmeta'):
            data = data.replace(b'\r\n', b'\n')
        digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()


def required_rows(root):
    for mc, version in versions(root).items():
        pins = json.loads((root / f'tools/matrix/pins-{mc}.json').read_text(encoding='utf-8'))
        for backend in version['backends']:
            for name in pins['sets']:
                allowed = pins.get('set_backends', {}).get(name)
                if allowed and backend not in allowed:
                    continue
                yield mc, backend, name


def measurements(text):
    result = []
    for block in re.split(r'\[diagnostics\] run ', text)[1:]:
        cpu = re.search(r'Aurora CPU per frame: ([\d.]+) ms mean, [\d.]+ ms median, ([\d.]+) ms p95', block)
        fills = re.search(r'fills per frame: ([\d.]+)', block)
        glass = re.search(r'glass captures per frame: ([\d.]+)', block)
        verdict = re.search(r'- overhead budget: (.*)', block)
        if not all((cpu, fills, glass, verdict)):
            raise ValueError('Incomplete diagnostics report')
        result.append(dict(mean=float(cpu[1]), p95=float(cpu[2]), fills=float(fills[1]),
                           glass=float(glass[1]), verdict=verdict[1].strip()))
    return result


def validate_runs(runs):
    if len(runs) != 6:
        raise ValueError('Exactly six runs required; two warm-ups and four judged runs')
    for run in runs[2:]:
        if any(not isinstance(run[k], (int, float)) or not math.isfinite(run[k]) or run[k] < 0
               for k in ('mean', 'p95', 'fills', 'glass')):
            raise ValueError('Invalid overhead measurement')
        if (run['mean'] > .25 or run['p95'] > .40 or run['fills'] > 100 or run['glass'] != 0
                or not run['verdict'].startswith('within')):
            raise ValueError('Overhead acceptance failed')


def verify(root, evidence):
    if evidence.get('sourceInputsSha256') != fingerprint(root):
        raise ValueError('GPU evidence does not match the current runtime/build/pin inputs')
    expected = set(required_rows(root))
    actual = [(r['minecraft'], r['backend'], r['set']) for r in evidence['rows']]
    if len(actual) != len(expected) or set(actual) != expected:
        raise ValueError('GPU evidence must cover exactly every supported line/backend/set')
    for row in evidence['rows']:
        if row.get('exitCode') != 0 or row.get('healthy') is not True or row.get('clean') is not True:
            raise ValueError('GPU compatibility acceptance failed')
        validate_runs(row['runs'])


def has_vulkanmod_in_log(text):
    return bool(
        re.search(r'(?im)^\s*-\s*vulkanmod(?:\s|$)', text)
        or re.search(r'\bVulkanMod\b', text)
    )


def record(root, logs):
    expected_fp = fingerprint(root)
    rows = []
    for mc, backend, name in required_rows(root):
        directory = logs / mc / backend
        log = directory / (name + '-latest.log')
        log_bytes = log.read_bytes()
        log_sha256 = hashlib.sha256(log_bytes).hexdigest()
        text = log_bytes.decode('utf-8', errors='replace')
        result = json.loads((directory / (name + '-result.json')).read_text(encoding='utf-8'))
        if (result.get('sourceInputsSha256') != expected_fp
                or result.get('outcome') != '6 reports logged; closed normally'):
            raise ValueError(f'Stale or incomplete matrix run: {mc}/{backend}/{name}')
        if result.get('minecraft') != mc:
            raise ValueError(f'Mismatched Minecraft version in result: {mc}/{backend}/{name} (got {result.get("minecraft")})')
        if result.get('set') != name:
            raise ValueError(f'Mismatched matrix set in result: {mc}/{backend}/{name} (got {result.get("set")})')
        result_backend = (result.get('requestedBackend') or result.get('backend') or '').strip().upper()
        if result_backend != backend:
            raise ValueError(f'Mismatched backend in result: {mc}/{backend}/{name} (got {result_backend or None})')
        if result.get('logFile') != log.name or result.get('logSha256') != log_sha256:
            raise ValueError(f'Result does not match log file: {mc}/{backend}/{name}')

        manifest = json.loads((root / f'adapters/{versions(root)[mc]["adapter"]}/src/client/resources/aurora.mixins.json').read_text())
        count = len(manifest['client'])
        reported = re.search(r'^Renderer: ([^|\n]+)', text, re.M)
        if not reported:
            raise ValueError(f'Missing renderer line in log: {mc}/{backend}/{name}')
        observed_log = reported[1].strip().upper()
        observed_result = (result.get('observedRenderer') or '').strip().upper()
        if observed_result != observed_log:
            raise ValueError(f'Mismatched observed renderer between result and log: {mc}/{backend}/{name}')
        vulkanmod_in_log = has_vulkanmod_in_log(text)
        if bool(result.get('vulkanModLoaded')) != vulkanmod_in_log:
            raise ValueError(f'Mismatched VulkanMod presence between result and log: {mc}/{backend}/{name}')

        if backend == 'OPENGL':
            backend_ok = observed_log == 'OPENGL' and not vulkanmod_in_log
        elif backend == 'VULKAN':
            backend_ok = observed_log == 'VULKAN' and not vulkanmod_in_log
        elif backend == 'VULKANMOD':
            backend_ok = observed_log in ('VULKAN', 'VULKANMOD', 'NOT OPENGL (VULKAN OR OTHER)') and vulkanmod_in_log
        else:
            raise ValueError(f'Unsupported backend {backend}: {mc}/{backend}/{name}')
        if not backend_ok:
            raise ValueError(f'Wrong actual renderer: {mc}/{backend}/{name} (observed {observed_log}, vulkanMod={vulkanmod_in_log})')

        healthy = re.search(rf'All {count} Aurora mixins applied(?:\.| or stood down)', text) is not None and 'Features not fully working' not in text
        clean = not re.search(r'^\[[^\n]+\] \[[^\n]+/(?:ERROR|WARN)\] \((?:Aurora|aurora[^)]*)\)', text, re.M)
        rows.append(dict(minecraft=mc, backend=backend, set=name, exitCode=result['exitCode'],
                         healthy=healthy, clean=clean, mixins=count, logSha256=log_sha256,
                         runs=measurements(text)))
    evidence = dict(schemaVersion=1, sourceInputsSha256=expected_fp, rows=rows)
    verify(root, evidence)
    return evidence


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['record', 'verify'])
    parser.add_argument('--source-root', type=Path, default=ROOT)
    parser.add_argument('--evidence', type=Path, required=True)
    parser.add_argument('--logs', type=Path)
    args = parser.parse_args()
    if args.command == 'record':
        evidence = record(args.source_root, args.logs)
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2) + '\n', encoding='utf-8')
    else:
        verify(args.source_root, json.loads(args.evidence.read_text(encoding='utf-8')))
    print('All supported lines pass matrix health and unchanged overhead gates for these source inputs.')


if __name__ == '__main__':
    main()
