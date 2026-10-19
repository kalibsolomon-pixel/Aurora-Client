"""Create new disposable mapping fixtures and freeze the reference runtime.

Never runs against the repository's live run directory. Existing fixture and
performance evidence is read-only. launch-private.json is deliberately untracked.
"""
import hashlib
import json
from pathlib import Path
import shutil
import sys

REPO = Path(__file__).resolve().parents[1]


def prepare(root, reference=False):
    assert not root.exists(), root
    source = Path.home() / 'AppData/Local/Temp/aii-s1'
    root.mkdir(parents=True)
    (root / 'DISPOSABLE-RESEARCH.txt').write_text('Mapping acceptance fixture; retain evidence.\n')
    game = root / 'game'
    for folder in ('config', 'saves', 'mods'):
        shutil.copytree(source / 'game' / folder, game / folder)
    shutil.copy2(source / 'game/options.txt', game / 'options.txt')
    cfg = json.loads((game / 'config/aurora.json').read_text())
    cfg.update(minimapEnabled=True, worldMapEnabled=True, waypointsEnabled=True)
    (game / 'config/aurora.json').write_text(json.dumps(cfg, indent=2))
    for path in (game / 'config/profiles').glob('*.json'):
        data = json.loads(path.read_text())
        data['fields'].update({k: cfg[k] for k in ('minimapEnabled', 'worldMapEnabled', 'waypointsEnabled')})
        path.write_text(json.dumps(data, indent=2))
    spec = json.loads((source / 'launch-private.json').read_text())
    spec['workingDirectory'] = str(game)
    spec['jvmArgs'] = [('-Daurora.research.output=' + str(root)) if s.startswith('-Daurora.research.output=') else s for s in spec['jvmArgs']]
    launch = (REPO / '.gradle/loom-cache/launch.cfg').read_text()
    if reference:
        for part in ('build/classes/java/client', 'build/resources/client'):
            shutil.copytree(REPO / part, root / part)
            spec['classpath'] = spec['classpath'].replace(str(REPO / part), str(root / part))
            launch = launch.replace(str(REPO / part), str(root / part))
    config = root / 'frozen-launch.cfg'
    config.write_text(launch)
    spec['jvmArgs'] = ['-Dfabric.dli.config=' + str(config) if s.startswith('-Dfabric.dli.config=') else s for s in spec['jvmArgs']]
    (root / 'launch-private.json').write_text(json.dumps(spec, indent=2))
    inventory = []
    for path in game.rglob('*'):
        if path.is_file():
            inventory.append(dict(path=path.relative_to(game).as_posix(), bytes=path.stat().st_size,
                                  sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    (root / 'input-manifest.json').write_text(json.dumps(inventory, indent=2))
    print(root)


if __name__ == '__main__':
    prepare(Path(sys.argv[1]).resolve(), '--reference' in sys.argv[2:])
