"""Offline research review. Reject incomplete captures; metrics remain software-only."""
import argparse
from collections import Counter
import csv
import json
import math
from pathlib import Path
import statistics


def percentile(values, fraction):
    values = sorted(values)
    position = (len(values) - 1) * fraction
    lo, hi = math.floor(position), math.ceil(position)
    return values[lo] + (values[hi] - values[lo]) * (position - lo)


def metrics(values):
    assert values and all(math.isfinite(v) and v > 0 for v in values)
    return dict(count=len(values), minMs=min(values), medianMs=statistics.median(values),
                p95Ms=percentile(values, .95), p99Ms=percentile(values, .99),
                maxMs=max(values), intervalsOver50Ms=sum(v > 50 for v in values),
                intervalsOver100Ms=sum(v > 100 for v in values))


def review(root):
    run = json.loads((root / 'run.json').read_text())
    process = json.loads((root / 'process.json').read_text())
    assert process['exitCode'] == 0 and not process.get('timeout'), 'Abnormal/incomplete child'
    assert not run.get('failedSetupCommands'), 'Scene setup commands failed'
    assert (root / 'profile.jfr').stat().st_size > 0
    rows = [{k: int(v) for k, v in row.items()} for row in csv.DictReader((root / 'frames.csv').open())]
    assert len(rows) == run['frames'] < 180_000
    assert all(b['head_ns'] > a['head_ns'] for a, b in zip(rows, rows[1:])), 'Unordered frames'
    assert all(r['end_ns'] > r['head_ns'] for r in rows)
    events = {r['event']: r for r in run['events']}
    for name in ['scene-setup-complete', 'first-interactive-frame', 'measurement-complete', 'world-disconnected']:
        assert name in events, 'Missing lifecycle checkpoint: ' + name
    result = dict(kind=run['kind'], frames=run['frames'], phases=[], startup={},
                  limitations=['Frame head and pre-updateDisplay intervals are software observations',
                               'JFR and sampling observer cost is not subtracted',
                               'No physical presentation or input-to-photon result'])
    for phase in sorted(set(r['phase'] for r in rows) - {0}):
        selected = [r for r in rows if r['phase'] == phase]
        assert len(selected) > 10
        intervals = [(b['head_ns'] - a['head_ns']) / 1e6 for a, b in zip(selected, selected[1:])]
        seconds = (selected[-1]['head_ns'] - selected[0]['head_ns']) / 1e9
        assert seconds >= 59, 'Short phase'
        frames = metrics(intervals)
        frames['maxIntervalsOver50MsInOneSecond'] = max(Counter(
            int((b['head_ns'] - selected[0]['head_ns']) / 1e9)
            for a, b in zip(selected, selected[1:]) if (b['head_ns'] - a['head_ns']) > 50e6
        ).values(), default=0)
        swap_times = [r['swap_ns'] for r in selected if r['swap_ns'] > 0]
        # The frozen workload marker precedes Window.updateDisplay; it does
        # not bracket glfwSwapBuffers. Actual swap calls use InputTiming.
        frames['preUpdateDisplayIntervals'] = metrics([(b - a) / 1e6 for a, b in zip(swap_times, swap_times[1:])])
        frames['fps'] = (len(selected) - 1) / seconds
        bad = sum((r['state'] & 3) != 3 or (phase != 3 and r['state'] & 12) != 0 for r in selected)
        sample = [s for s in run['samples'] if s['phase'] == phase]
        frames.update(phase=phase, seconds=seconds, invalidStateFrames=bad,
                      invalidStateFraction=bad / len(selected),
                      heapUsedMiBMin=min(s['heapUsed'] for s in sample) / 1024**2,
                      heapUsedMiBMax=max(s['heapUsed'] for s in sample) / 1024**2,
                      heapCommittedMiBMax=max(s['heapCommitted'] for s in sample) / 1024**2,
                      entitiesMin=min(s['entities'] for s in sample),
                      entitiesMax=max(s['entities'] for s in sample),
                      positionFirst=[sample[0][k] for k in ['x', 'y', 'z']],
                      positionLast=[sample[-1][k] for k in ['x', 'y', 'z']],
                      serverAverageTickMsMax=max(s.get('serverAverageTickNs', 0) for s in sample) / 1e6)
        if phase in (1, 4) and run['kind'] != 'input':
            assert len({tuple(s[k] for k in ['x', 'y', 'z', 'yaw', 'pitch']) for s in sample}) == 1, 'Stationary camera changed'
        # Focus loss remains visible and makes a run diagnostic, never silently filtered.
        result['phases'].append(frames)
    result['validState'] = all(p['invalidStateFraction'] <= .01 for p in result['phases'])
    if run.get('clock'):
        clock = run['clock']
        qpc_ns = clock['qpcTick'] * 1e9 / clock['qpcFrequency']
        origin_qpc_ns = qpc_ns - (clock['nanoBefore'] + clock['nanoAfter']) / 2
        if 'afterSpawnQpcNs' in process:
            for event in run['events']:
                result['startup'][event['event']] = (origin_qpc_ns + event['ns'] - process['afterSpawnQpcNs']) / 1e6
        else:
            result['limitations'].append('Native child startup is correlated separately to the retained JavaSpawn bracket')
        result['clockBracketNs'] = clock['nanoAfter'] - clock['nanoBefore']
        result['framebuffer'] = [clock['framebufferWidth'], clock['framebufferHeight']]
    resources = [{k: int(v) if v.isdigit() else v for k, v in r.items()}
                 for r in csv.DictReader((root / 'process.csv').open())]
    first, last = resources[0], resources[-1]
    seconds = (last['monotonic_ns'] - first['monotonic_ns']) / 1e9
    result['wholeProcessContext'] = dict(seconds=seconds,
        cpuPercentOneCore=(last['cpu_time_ns'] - first['cpu_time_ns']) / 1e9 / seconds * 100,
        peakRssMiB=max(r['rss_bytes'] for r in resources) / 1024**2)
    if run.get('clock'):
        for phase in result['phases']:
            selected = [r for r in rows if r['phase'] == phase['phase']]
            start, finish = origin_qpc_ns + selected[0]['head_ns'], origin_qpc_ns + selected[-1]['head_ns']
            inside = [r for r in resources if start <= r['monotonic_ns'] <= finish]
            assert len(inside) >= 100, 'Insufficient independent phase resource samples'
            first, last = inside[0], inside[-1]
            wall = (last['monotonic_ns'] - first['monotonic_ns']) / 1e9
            phase['processContext'] = dict(samples=len(inside), seconds=wall,
                cpuPercentOneCore=(last['cpu_time_ns'] - first['cpu_time_ns']) / 1e9 / wall * 100,
                rssMiBMin=min(r['rss_bytes'] for r in inside) / 1024**2,
                rssMiBMax=max(r['rss_bytes'] for r in inside) / 1024**2,
                availableMiBMin=min(r['system_available_bytes'] for r in inside) / 1024**2)
    (root / 'review.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('roots', nargs='+', type=Path)
    args = parser.parse_args()
    results = []
    for root in args.roots:
        try:
            results.append(dict(root=str(root), result=review(root)))
        except (AssertionError, OSError, ValueError, KeyError) as failure:
            results.append(dict(root=str(root), invalid=str(failure)))
    print(json.dumps(results, indent=2))
    raise SystemExit(2 if any('invalid' in r for r in results) else 0)
