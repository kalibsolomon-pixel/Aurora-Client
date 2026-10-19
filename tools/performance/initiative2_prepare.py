"""Make a new marked fixture; acquire only pinned Modrinth metadata/hash-verified jars."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import urllib.parse
import urllib.request
import zipfile

REPO = Path(__file__).resolve().parents[2]
VERSIONS = {'lithium': 'Ow7wA0kG', 'ferrite-core': 'Ii0gP3D8',
            'immediatelyfast': '4EwhsTu7', 'entityculling': 'CTabrwB1',
            'terralith': 'vVdNToqE', 'tectonic': '7olSYFxL'}


def get(url):
    assert url.startswith(('https://api.modrinth.com/', 'https://cdn.modrinth.com/'))
    return urllib.request.urlopen(urllib.request.Request(url, headers={
        'User-Agent': 'Aurora-Local-Performance-Research/2'}), timeout=30).read()


def prepare(root, pack=False, pack_source=None):
    pack = pack or pack_source is not None
    root.mkdir(parents=True, exist_ok=False)
    (root / 'DISPOSABLE-RESEARCH.txt').write_text('Initiative II, disposable fixture; retain evidence.\n')
    game = root / 'game'
    source = REPO / 'run/cperf2b-benchmark/run-5/game'
    for name in ['config', 'saves']:
        shutil.copytree(source / name, game / name)
    mods = game / 'mods'
    mods.mkdir()
    # Gradle supplies API; copy only Sodium, not the old overlay/Cloth observers.
    sodium = source / 'mods/sodium-fabric-0.8.14+mc1.21.11.jar'
    shutil.copy2(sodium, mods / sodium.name)
    options = (source / 'options.txt').read_text()
    updates = dict(fullscreen='false', overrideWidth='1280', overrideHeight='720',
                   renderDistance='16' if pack else '8', simulationDistance='8',
                   maxFps='120', enableVsync='false', pauseOnLostFocus='false',
                   onboardAccessibility='false', guiScale='2')
    lines = []
    for line in options.splitlines():
        key = line.partition(':')[0]
        lines.append(key + ':' + updates.pop(key) if key in updates else line)
    lines.extend(k + ':' + v for k, v in updates.items())
    (game / 'options.txt').write_text('\n'.join(lines) + '\n')
    cfg_path = game / 'config/aurora.json'
    cfg = json.loads(cfg_path.read_text())
    cfg.update(frameCapFps=120, framePacingStrategy='PARK', framePacerSpinThresholdMicros=500,
               adaptiveRenderSleeping=False, minimapEnabled=pack, worldMapEnabled=True,
               playerHealthIndicators=pack, hitboxEnabled=pack)
    cfg_path.write_text(json.dumps(cfg, indent=2) + '\n')
    # Prevent copied profile overlay from undoing the fixture's explicit config.
    for profile in (game / 'config/profiles').glob('*.json'):
        data = json.loads(profile.read_text())
        # ProfileManager wraps the field snapshot in name/createdAtMs/fields.
        data['fields'].update({k: cfg[k] for k in ['frameCapFps', 'framePacingStrategy',
                    'framePacerSpinThresholdMicros', 'adaptiveRenderSleeping',
                    'minimapEnabled', 'worldMapEnabled', 'playerHealthIndicators', 'hitboxEnabled']})
        profile.write_text(json.dumps(data, indent=2) + '\n')
    manifest = []
    if pack_source:
        manifest = json.loads((pack_source / 'mod-provenance.json').read_text())
        for record in manifest:
            source_jar = pack_source / 'game/mods' / record['file']
            assert hashlib.sha512(source_jar.read_bytes()).hexdigest() == record['expectedSha512']
            shutil.copy2(source_jar, mods / record['file'])
    elif pack:
        queue = list(VERSIONS.values())
        completed = set()
        while queue:
            version_id = queue.pop(0)
            v = json.loads(get('https://api.modrinth.com/v2/version/' + version_id))
            if v['project_id'] in completed:
                continue
            assert '1.21.11' in v['game_versions'] and 'fabric' in v['loaders']
            completed.add(v['project_id'])
            for dependency in v['dependencies']:
                if dependency['dependency_type'] != 'required' or dependency['project_id'] == 'P7dR8mSH':
                    continue
                if dependency['version_id']:
                    queue.append(dependency['version_id'])
                else:
                    query = urllib.parse.urlencode({'game_versions': '["1.21.11"]', 'loaders': '["fabric"]'})
                    versions = json.loads(get('https://api.modrinth.com/v2/project/' + dependency['project_id'] + '/version?' + query))
                    assert versions
                    queue.append(versions[0]['id'])
            f = next(f for f in v['files'] if f['primary'])
            filename = f['filename']
            assert Path(filename).name == filename and filename.endswith('.jar')
            body = get(f['url'])
            assert len(body) == f['size'] and hashlib.sha512(body).hexdigest() == f['hashes']['sha512']
            path = mods / filename
            path.write_bytes(body)
            with zipfile.ZipFile(path) as archive:
                metadata = json.loads(archive.read('fabric.mod.json'))
            manifest.append(dict(project=v['project_id'], version=v['version_number'], versionId=v['id'],
                                 file=filename, url=f['url'], expectedSha512=f['hashes']['sha512'],
                                 sha256=hashlib.sha256(body).hexdigest(), dependencies=v['dependencies'],
                                 fabricMetadata=metadata))
    (root / 'mod-provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
    inventory = []
    for path in sorted(game.rglob('*')):
        if path.is_file():
            with path.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            inventory.append(dict(path=path.relative_to(game).as_posix(), bytes=path.stat().st_size, sha256=digest))
    (root / 'input-manifest.json').write_text(json.dumps(inventory, indent=2) + '\n')
    print(json.dumps(dict(root=str(root), addedMods=len(manifest))))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('new_root', type=Path)
    parser.add_argument('--pack', action='store_true')
    parser.add_argument('--pack-source', type=Path)
    args = parser.parse_args()
    prepare(args.new_root.resolve(), args.pack, args.pack_source)
