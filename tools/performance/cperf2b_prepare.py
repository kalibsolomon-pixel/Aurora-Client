"""Prepare fresh C-PERF.2B copies; never launch or alter source evidence."""
import datetime
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PHASE = ROOT / 'run/cperf2b-benchmark'
SOURCE = ROOT / 'run/cperf1av-validation/game'
JARS = ROOT / 'run/cperf1b-benchmark/run-1/game/mods'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inventory(root):
    return [dict(path=p.relative_to(root).as_posix(), bytes=p.stat().st_size,
                 sha256=digest(p)) for p in sorted(root.rglob('*')) if p.is_file()]


def main():
    assert not PHASE.exists(), 'Refuse to reuse or overwrite a prior experiment'
    previous = json.loads((ROOT / 'run/cperf1b-benchmark/inputs.json').read_text())
    world = SOURCE / 'saves/Cperf1a'
    world_files = inventory(world)
    identity = hashlib.sha256(json.dumps(world_files, separators=(',', ':')).encode()).hexdigest()
    assert identity == previous['worldIdentity']
    assert inventory(JARS) == previous['runs'][0]['mods']
    settings = dict(smoothFramePacer=True, framePacingStrategy='PARK',
                    framePacerSpinThresholdMicros=500, framePacerParkThresholdMicros=2000,
                    framePacerLowCpuMode=False, frameCapFps=240, lowLatencyRender=True,
                    disableVSync=True, adaptiveRenderSleeping=False)
    options = (SOURCE / 'options.txt').read_text().splitlines()
    overrides = {'maxFps': '260', 'enableVsync': 'false', 'inactivityFpsLimit': '"minimized"'}
    options = '\n'.join(line.split(':', 1)[0] + ':' + overrides[line.split(':', 1)[0]]
                        if line.split(':', 1)[0] in overrides else line for line in options) + '\n'
    PHASE.mkdir()
    rows = []
    for ordinal, experimental in enumerate([False, True, True, False, False, True], 1):
        target = PHASE / f'run-{ordinal}/game'
        target.mkdir(parents=True)
        shutil.copytree(SOURCE / 'config', target / 'config')
        shutil.copytree(world, target / 'saves/Cperf1a')
        shutil.copytree(JARS, target / 'mods')
        assert inventory(target / 'saves/Cperf1a') == world_files
        cfg_path = target / 'config/aurora.json'
        cfg = json.loads(cfg_path.read_text())
        cfg.update(settings)
        cfg_path.write_text(json.dumps(cfg, indent=2))
        for profile in (target / 'config/profiles').glob('*.json'):
            data = json.loads(profile.read_text())
            data['fields'].update(settings)
            profile.write_text(json.dumps(data, indent=2))
        (target / 'config/performanceoverlay.json').write_text(json.dumps(
            dict(enabled=False, autoBenchmarkDurationSec=60), indent=2))
        (target / 'options.txt').write_text(options)
        rows.append(dict(order=ordinal, experimental=experimental, spinMicros=500,
                         game=str(target), worldIdentity=identity, mods=inventory(target / 'mods'),
                         config=inventory(target / 'config'), optionsSha256=digest(target / 'options.txt')))
    result = dict(preparedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
                  worldIdentity=identity, sourceWorld=str(world), sourceWorldInventory=world_files,
                  sourcePose=previous['sourcePose'], runs=rows)
    (PHASE / 'inputs.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(dict(worldIdentity=identity, runs=len(rows), evidence=str(PHASE))))


if __name__ == '__main__':
    main()
