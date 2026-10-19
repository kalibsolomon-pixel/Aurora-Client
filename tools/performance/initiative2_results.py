"""Assemble sanitized aggregates from this campaign's retained local captures."""
import hashlib
import json
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEMP = Path.home() / 'AppData/Local/Temp'


def read(path):
    return json.loads(path.read_text())


def startup(folder):
    process, trace = read(folder / 'process.json'), read(folder / 'native-trace.json')
    assert process['exitCode'] == 0 and not process['timeout'] and trace['dropped'] == 0
    clock = trace['clock']
    origin = (clock['qpc_before'] + clock['qpc_after']) / 2 * 1e9 / clock['frequency']
    after = process['afterSpawnQpcNs']
    event_names = {100:'frontendInitialize', 118:'initialStateComplete', 122:'accountsComplete',
                   123:'initialHomePaint', 125:'restoredHomeUsable', 106:'firstReady', 124:'readyRaf'}
    checkpoints = {}
    for code, name in event_names.items():
        events = [r for r in trace['records'] if r['event'] == code]
        checkpoints[name] = (origin + min(r['us'] for r in events) * 1000 - after) / 1e6 if events else None
    spans = []
    begins = {r['id']:r for r in trace['records'] if r['edge'] == 1}
    for end in trace['records']:
        if end['edge'] != 2: continue
        begin = begins[end['id']]
        spans.append(dict(event=begin['event'], id=begin['id'], parent=begin['parent'],
                          startMs=(origin + begin['us'] * 1000 - after) / 1e6,
                          durationMs=(end['us'] - begin['us']) / 1000))
    return dict(checkpointsMsAfterSpawnReturn=checkpoints,
                spawnCallBracketMs=(after - process['beforeSpawnQpcNs']) / 1e6,
                firstOwnedWindowMs=(process['firstVisibleWindow']['observedQpcNs'] - after) / 1e6,
                qpcOriginBracketNs=(clock['qpc_after'] - clock['qpc_before']) * 1e9 / clock['frequency'],
                runtimeRequests=sum(r['event'] == 101 for r in trace['records']),
                readinessRequests=sum(r['event'] == 103 for r in trace['records']),
                spans=spans)


def native_game(root, capture, game):
    trace = read(root / capture / 'native-trace.json')
    clock = trace['clock']
    native_origin = (clock['qpc_before'] + clock['qpc_after']) / 2 * 1e9 / clock['frequency']
    records = trace['records']
    begin = next(r for r in records if r['event'] == 31 and r['edge'] == 1)
    end = next(r for r in records if r['id'] == begin['id'] and r['edge'] == 2)
    request = max((r for r in records if r['event'] == 108 and r['us'] < begin['us']), key=lambda r:r['us'])
    process = read(root / game / 'process.json')
    result = dict(exitCode=process['exitCode'], faultAtMenu=process.get('inducedFaultAtMenu', False),
                  spawnBracketWidthMs=(end['us'] - begin['us']) / 1000,
                  playRequestReceiptToSpawnMsBounds=[(begin['us'] - request['us']) / 1000,
                                                     (end['us'] - request['us']) / 1000],
                  firstWindowObservationAfterSpawnMs=(process['firstVisibleWindow']['observedQpcNs'] - native_origin - end['us'] * 1000) / 1e6,
                  windowObservationLate=game == 'game-1')
    if (root / game / 'run.json').exists():
        run = read(root / game / 'run.json'); c = run['clock']
        origin = c['qpcTick'] * 1e9 / c['qpcFrequency'] - (c['nanoBefore'] + c['nanoAfter']) / 2
        result['checkpointsMsAfterSpawnBracket'] = {e['event']:
            [(origin + e['ns'] - native_origin - end['us'] * 1000) / 1e6,
             (origin + e['ns'] - native_origin - begin['us'] * 1000) / 1e6]
            for e in run['events'] if e['event'] in ['minecraft-constructor-start',
                'minecraft-constructor-end', 'main-menu-renderable', 'world-open-request',
                'first-interactive-frame', 'aurora-initialize-start', 'aurora-initialize-end']}
        result['childQpcBracketNs'] = c['nanoAfter'] - c['nanoBefore']
        result['framebuffer'] = [c['framebufferWidth'], c['framebufferHeight']]
    return result


def main():
    matrix, artifacts = [], []
    roots = []
    for i, variant in enumerate(['reference', 'candidate', 'candidate', 'reference', 'reference', 'candidate'], 1):
        root = TEMP / f'aii-s{i}'; roots.append(root)
        matrix.append(dict(run=i, variant=variant, review=read(root / 'review.json'),
                           jfr=read(root / 'jfr-attribution-final.json')))
    native = TEMP / 'aurora-p0-2-i'
    roots += [TEMP / 'aii-base-05', TEMP / 'aii-input', native / 'game-1', native / 'game-2', native / 'game-3']
    for root in roots:
        for name in ['frames.csv', 'run.json', 'process.json', 'process.csv', 'profile.jfr',
                     'presentmon.csv', 'input-events.csv', 'swap-calls.csv', 'input-manifest.json',
                     'mod-provenance.json', 'presentation-capture.json', 'native-game-latest.log']:
            path = root / name
            if path.is_file():
                with path.open('rb') as stream: digest = hashlib.file_digest(stream, 'sha256').hexdigest()
                artifacts.append(dict(root=root.name, file=name, bytes=path.stat().st_size, sha256=digest))
    startup_root = TEMP / 'aurora-p0-2-ii'
    binaries = []
    for label,path in [('client', REPO / 'build/libs/aurora-3.0.0.jar'),
            ('launcher-reference', startup_root / 'reference.exe'),
            ('launcher-final-research', startup_root / 'final.exe'),
            ('launcher-ordinary', Path('C:/Dev/aurora-launcher/.git/performance-initiative-ii/production.exe'))]:
        with path.open('rb') as stream: digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        binaries.append(dict(label=label, sha256=digest, bytes=path.stat().st_size))
    result = dict(date='2026-10-19', binaries=binaries, matrix=matrix,
        micro=read(REPO / '.git/performance-initiative-ii/micro-valid-results.json'),
        lightweight=read(TEMP / 'aii-base-05/review.json'),
        sustained=read(native / 'game-1/review.json'),
        sustainedMinutes=read(native / 'game-1/sustained-minutes.json'),
        combinedFamily=read(native / 'game-1/combined-family/capture.json'),
        latency=read(TEMP / 'aii-input/latency-review.json'),
        modProvenance=[{k:r[k] for k in ['project','version','versionId','file','url','expectedSha512','sha256']}
                       for r in read(TEMP / 'aii-s1/mod-provenance.json')],
        startup={name:startup(startup_root / name) for name in ['b1','b2','b3','c1','c2','c3']},
        nativeStartup={name:startup(native / name) for name in ['n2','n3','n4']},
        nativeGames={game:native_game(native, capture, game) for capture,game in [('n2','game-1'),('n3','game-2'),('n4','game-3')]},
        artifacts=artifacts)
    # Process identities are useful privately, but not needed in shared aggregates.
    family = result['combinedFamily']
    for key in ['rootPid', 'identities', 'membersStart', 'membersEnd']: family.pop(key, None)
    family['samplesPerProcess'] = sorted(family['samplesPerProcess'].values())
    result['latency']['inputPopulation'].pop('pid', None)
    output = REPO / 'docs/performance-initiative-ii/results.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(output=str(output), matrixRuns=len(matrix), hashedArtifacts=len(artifacts))))


if __name__ == '__main__': main()
