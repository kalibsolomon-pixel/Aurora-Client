"""Separate diagnostic channels. Never converts software timestamps into photons."""
import bisect
import csv
import json
from pathlib import Path
from initiative2_review import metrics


def review(root):
    process = json.loads((root / 'process.json').read_text())
    capture = json.loads((root / 'presentation-capture.json').read_text())
    population = json.loads((root / 'input-population.json').read_text())
    assert process['exitCode'] == capture['collectorExitCode'] == 0
    assert process['pid'] == capture['pid'] and process['creationId'] == capture['creationId']
    diagnostics = (root / 'presentmon.stderr.log').read_text(errors='replace')
    assert not any(word in diagnostics.lower() for word in ['overflow', 'events lost', 'error:'])
    inputs = [{k: int(v) for k, v in r.items()} for r in csv.DictReader((root / 'input-events.csv').open())]
    assert len(inputs) == population['pairedFrames'] and inputs
    for row in inputs:
        values = list(row.values())
        assert values == sorted(values) and len(set(values)) == 4
    result = dict(inputPopulation=population, softwareStages={}, limitations=[
        'Only last callback consumed in a rendered frame is paired',
        'Three automated drags, small descriptive sample; no population latency claim',
        'Mouse application marker is handleAccumulatedMovement return',
        'No hardware input, display scanout, photon response or physical delivery measurement',
        'Desktop observation and PresentMon overhead are uncalibrated; diagnostic cohort only'])
    stages = [('callbackToMouseReturn', 'arrival_ns', 'application_return_ns'),
              ('mouseReturnToCameraSetup', 'application_return_ns', 'camera_setup_return_ns'),
              ('cameraToGlfwSwapEntry', 'camera_setup_return_ns', 'swap_entry_ns'),
              ('callbackToGlfwSwapEntry', 'arrival_ns', 'swap_entry_ns')]
    for name, first, last in stages:
        result['softwareStages'][name] = metrics([(r[last] - r[first]) / 1e6 for r in inputs])
    calls = [{k: int(v) for k, v in r.items()} for r in csv.DictReader((root / 'swap-calls.csv').open())]
    assert len(calls) == population['swapCalls']
    assert all(b['qpc_tick'] > a['qpc_tick'] for a, b in zip(calls, calls[1:]))
    assert all(c['return_ns'] > c['entry_ns'] for c in calls)
    rows = list(csv.DictReader((root / 'presentmon.csv').open(encoding='utf-8-sig')))
    assert all(r['ProcessID'] == str(process['pid']) for r in rows)
    streams = {(r['SwapChainAddress'], r['PresentRuntime'], r['PresentMode']) for r in rows}
    assert len(streams) == 1
    ticks = [int(r['TimeInQPC']) for r in rows]
    assert all(b > a for a, b in zip(ticks, ticks[1:]))
    frequency = population['qpcFrequency']
    # Predeclared common interior: trim exactly 2 seconds at both boundaries.
    start, end = max(ticks[0], calls[0]['qpc_tick']) + 2 * frequency, min(ticks[-1], calls[-1]['qpc_tick']) - 2 * frequency
    api = [c['qpc_tick'] for c in calls if start <= c['qpc_tick'] <= end]
    present = [tick for tick in ticks if start <= tick <= end]
    assert end - start >= 20 * frequency
    matches, errors = [], []
    for tick in present:
        i = bisect.bisect_left(api, tick)
        choices = [j for j in [i - 1, i] if 0 <= j < len(api)]
        j = min(choices, key=lambda j: abs(tick - api[j]))
        matches.append(j); errors.append((tick - api[j]) / frequency * 1000)
    selected = [r for r in rows if start <= int(r['TimeInQPC']) <= end]
    valid_loss = all(r['statusCode'] == 0 and not any(r[k] for k in ['eventsLost', 'logBuffersLost', 'realTimeBuffersLost']) for r in capture['lossSnapshots'])
    covered = max(r['qpcNs'] for r in capture['lossSnapshots']) >= end / frequency * 1e9
    delays = [float(r['MsUntilDisplayed']) for r in selected if r['MsUntilDisplayed'] != 'NA']
    result['presentation'] = dict(stream=list(streams)[0], interiorSeconds=(end - start) / frequency,
        glfwSwapCalls=len(api), presentRows=len(present), uniqueNearestAssociations=len(set(matches)),
        maximumAbsoluteAssociationMs=max(map(abs, errors)),
        meanSignedAssociationMs=sum(errors) / len(errors),
        submissionIntervals=metrics([(b - a) / frequency * 1000 for a,b in zip(present,present[1:])]),
        independentSwapIntervals=metrics([(b - a) / frequency * 1000 for a,b in zip(api,api[1:])]),
        availableDisplayEstimates=len(delays), missingDisplayEstimates=len(selected)-len(delays),
        displayEstimateDelayMsRange=[min(delays), max(delays)] if delays else None,
        inputToPhotonFieldsAvailable=sum(r['MsAllInputToPhotonLatency'] != 'NA' or r['MsClickToPhotonLatency'] != 'NA' for r in selected),
        zeroObservedSessionLoss=valid_loss, lossQueryCoversInteriorEnd=covered,
        uniqueAssociationWithin2ms=len(set(matches)) == len(api) == len(present) and max(map(abs, errors)) <= 2,
        physicalDisplayCertified=False)
    (root / 'latency-review.json').write_text(json.dumps(result, indent=2))
    return result


if __name__ == '__main__':
    import sys
    print(json.dumps(review(Path(sys.argv[1])), indent=2))
