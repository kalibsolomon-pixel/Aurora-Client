"""C-PERF.2B offline review. Based on retained C-PERF.1B review arithmetic.
Never collects, launches or changes inputs; preserves schema-1/2 populations.
"""
import csv, hashlib, io, json, math, statistics, sys
from collections import Counter
from pathlib import Path
sys.path.insert(0, r'C:\Dev\aurora-launcher\tools\benchmark')
from collector_review import COLUMNS, percentile
from world_scene import level

P = Path(__file__).resolve().parents[2] / 'run/cperf2b-benchmark'
inputs = json.loads((P / 'inputs.json').read_text())

def sha(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()

def frames(path):
    text = path.read_text(encoding='utf-8-sig')
    fields = {}
    for line in text.splitlines():
        if line.startswith('# ') and ': ' in line:
            key, value = line[2:].split(': ', 1)
            fields[key] = value
    assert fields['EndReason'] == 'AUTO_DURATION'
    lines = [line for line in text.splitlines() if line and not line.startswith('#')]
    reader = csv.DictReader(lines)
    assert reader.fieldnames == COLUMNS
    rows = list(reader)
    assert len(rows) == int(fields['FramesLogged']) == int(fields['FramesSummary'])
    intervals = [float(r['frame_ms']) for r in rows]
    elapsed = [int(r['elapsed_ms']) for r in rows]
    assert intervals and all(math.isfinite(v) and v > 0 for v in intervals)
    # Existing reader permits a 100 ms endpoint gap: auto-stop is tick-driven,
    # while the final eligible HUD callback can precede the 60 s tick boundary.
    assert elapsed[0] < 100 and 59900 <= elapsed[-1] < 60200
    assert all(b >= a for a, b in zip(elapsed, elapsed[1:]))
    assert all(abs(elapsed[i] - elapsed[i-1] - intervals[i]) <= 1.001 for i in range(1, len(rows)))
    period = 1000/240
    clusters = Counter(t//1000 for t, v in zip(elapsed, intervals) if v > 2*period)
    rate = len(rows)*1000/math.fsum(intervals)
    assert abs(rate - float(fields['AvgFPS'])) < .06
    gc = [float(r['gc_time_delta_ms']) for r in rows if r['gc_time_delta_ms']]
    return dict(file=str(path.relative_to(P)), sha256=sha(path), rows=len(rows),
                firstElapsedMs=elapsed[0], lastElapsedMs=elapsed[-1], fps=rate,
                medianMs=percentile(intervals,.5), p95Ms=percentile(intervals,.95),
                p99Ms=percentile(intervals,.99), maxMs=max(intervals),
                over125T=sum(v>1.25*period for v in intervals), over2T=sum(v>2*period for v in intervals),
                over50ms=sum(v>50 for v in intervals), maxOver2TInOneSecond=max(clusters.values(), default=0),
                gcTimeDeltaMs=math.fsum(gc), gcCounterSamples=len(gc),
                heapMaxMiB=sorted(set(r['mem_max_mb'] for r in rows)), producer=fields)

def resources(directory):
    meta=json.loads((directory/'capture.json').read_text())
    assert meta['status']=='complete' and meta['samples']==121
    rows=list(csv.DictReader((directory/'process.csv').open()))
    first,last=rows[0],rows[-1]
    delta=lambda key:int(last[key])-int(first[key])
    wall=delta('monotonic_ns')/1e9
    cpu=delta('cpu_time_ns')/1e9
    system=delta('system_busy_ns')/delta('system_total_ns')*100
    return dict(wallSeconds=wall,processCpuSeconds=cpu,processCpuPercentOneCore=cpu/wall*100,
                processCpuPercentMachine=cpu/wall*100/meta['logicalProcessors'],
                systemBusyPercent=system,availableMiBMin=min(int(r['system_available_bytes']) for r in rows)/1024**2,
                rssMiBMin=min(int(r['rss_bytes']) for r in rows)/1024**2,
                rssMiBMax=max(int(r['rss_bytes']) for r in rows)/1024**2,
                startUtc=meta['startUtc'],endUtc=meta['endUtc'])

def diagnostic(path, spin):
    d=json.loads(path.read_text()); c=d['conditions']; t=d['transitions']; n=d['counters']
    assert d['schemaVersion'] in (1,2) and not d['active'] and d['segment']==1
    assert c['owner']=='FRAME_HEAD_GLOBAL' and c['strategy'] in ('PARK','PARK_REPARK') and c['effectiveFps']==240 and c['focused']
    assert c['configuredSpinMicros']==c['effectiveSpinMicros']==spin
    assert c['configuredParkMicros']==2000 and not c['hybridLowCpu']
    assert all(v==0 for v in t.values())
    bounds=d['bucketUpperExclusiveNanos']; distributions={}
    for item in d['distributions']:
        name=item['metric']; v=item['values']; b=v['buckets']
        expected=n['parkCalls'] if name in ['REQUESTED_PARK','ACTUAL_PARK','WAKE_ERROR','FIRST_REQUESTED_PARK','FIRST_ACTUAL_PARK','FIRST_WAKE_ERROR','FINAL_PARK_WAKE_ERROR','REMAINING_AFTER_FIRST_PARK'] else n['invocations']
        assert len(b)==len(bounds)+1 and sum(b)==v['count']==expected
        assert v['minNanos']<=v['meanNanos']<=v['maxNanos']
        lowers=[None]+bounds
        v['countAtLeast25us']=sum(k for lo,k in zip(lowers,b) if lo is not None and lo>=25000)
        v['countAtLeast100us']=sum(k for lo,k in zip(lowers,b) if lo is not None and lo>=100000)
        v['countAtLeast1ms']=sum(k for lo,k in zip(lowers,b) if lo is not None and lo>=1000000)
        v['countAtLeast2ms']=sum(k for lo,k in zip(lowers,b) if lo is not None and lo>=2000000)
        distributions[name]=v
    assert n['earlyParkReturns']+n['lateParkReturns']<=n['parkCalls']
    return dict(file=str(path.relative_to(P)),sha256=sha(path),sessionId=d['sessionId'],
                durationSeconds=d['sessionDurationNanos']/1e9,conditions=c,counters=n,
                transitions=t,distributions=distributions,schemaVersion=d['schemaVersion'],parkPath=d['parkPath'],reparkCounters=d.get('reparkCounters'))

def review_run(order):
    directory=P/f'run-{order}'; expected=inputs['runs'][order-1]
    files=sorted((directory/'game/config/performanceoverlay/benchmarks').glob('*.csv'))
    assert len(files)==2
    snapshots=list((directory/'game/debug/aurora-park').glob('*.json'))
    if (directory/'premeasurement-empty-snapshot.json').exists():
        empty=json.loads((directory/'premeasurement-empty-snapshot.json').read_text())
        assert empty['sessionId']=='none' and empty['counters']['invocations']==0
        snapshots=[p for p in snapshots if json.loads(p.read_text())['sessionId']!='none']
    if (directory/'warmup-snapshot.json').exists():
        warmup=json.loads((directory/'warmup-snapshot.json').read_text())
        snapshots=[p for p in snapshots if json.loads(p.read_text())['sessionId']!=warmup['sessionId']]
    assert len(snapshots)==1
    d=level(directory/'game/saves/Cperf1a/level.dat'); player=d['Player']
    assert player['Pos']==inputs['sourcePose']['position'] and player['Rotation']==inputs['sourcePose']['rotation']
    runtime=(directory/'runtime.log').read_text(encoding='utf-8',errors='replace')
    assert 'BUILD SUCCESSFUL' in runtime and 'Stopping!' in runtime
    assert 'PARK snapshot saved' in runtime and 'PARK snapshot export failed' not in runtime
    config=json.loads((directory/'game/config/aurora.json').read_text())
    invalid=json.loads((directory/'invalid.json').read_text(encoding='utf-8-sig')) if (directory/'invalid.json').exists() else None
    observed_spin=invalid['observedSpinMicros'] if invalid else expected['spinMicros']
    assert config['framePacerSpinThresholdMicros']==observed_spin
    assert config['framePacingStrategy']=='PARK' and config['frameCapFps']==240
    validate_repark(snapshots[0], expected['experimental'])
    result=dict(order=order,spinMicros=expected['spinMicros'],process=json.loads((directory/'process.json').read_text()),
                camera=dict(position=player['Pos'],rotation=player['Rotation'],exactMatch=True),
                savedWorldTime=d.get('Time'),savedDayTime=d.get('DayTime'),
                off=frames(files[0]),on=frames(files[1]),
                offResources=resources(directory/'off-resources'),onResources=resources(directory/'on-resources'),
                park=diagnostic(snapshots[0],observed_spin),valid=invalid is None,invalid=invalid)
    (directory/'review.json').write_text(json.dumps(result,indent=2))
    return result



def validate_repark(path, experimental):
    d=json.loads(path.read_text()); n=d['counters']; r=d.get('reparkCounters'); p=d['parkPath']
    assert d['schemaVersion']==(2 if experimental else 1)
    assert d['conditions']['strategy']==('PARK_REPARK' if experimental else 'PARK')
    assert p['experimentalFlag']==experimental and p['maximumParkCalls']==(2 if experimental else 1)
    assert p['path']==('EXPERIMENTAL_BOUNDED_REPARK' if experimental else 'REFERENCE')
    if not experimental:
        assert r is None and p['retryRequestNanos']==0
        return
    assert p['retryRequestNanos']==1000000
    assert r['totalParkInvocations']==n['invocations']==r['zeroParkInvocations']+r['oneParkInvocations']+r['twoParkInvocations']
    assert r['firstParkCalls']==n['parkCalls']==r['oneParkInvocations']+r['twoParkInvocations']
    assert r['retriesTaken']==r['twoParkInvocations']
    assert r['eligibleRetries']==r['retriesTaken']+r['retriesSkippedInterruption']
    assert r['totalParkCalls']==r['firstParkCalls']+r['retriesTaken']
    assert r['retryLimitExhaustions']<=r['retriesTaken']
    expected=1000000 if r['retriesTaken'] else 0
    assert r['retryRequestMinNanos']==r['retryRequestMaxNanos']==expected


if __name__=='__main__':
    orders=[int(v) for v in sys.argv[1:]] or list(range(1,7))
    results=[review_run(n) for n in orders]
    (P/'review-summary.json').write_text(json.dumps(results,indent=2))
    print(json.dumps([dict(order=r['order'],valid=r['valid'],fps=r['on']['fps'],p95Ms=r['on']['p95Ms'],p99Ms=r['on']['p99Ms'],spinMeanUs=r['park']['distributions']['SPIN_WALL']['meanNanos']/1000,reparkCounters=r['park']['reparkCounters']) for r in results],indent=2))
