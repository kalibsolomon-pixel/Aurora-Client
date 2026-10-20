"""Validate the actual distributable with normal Fabric, outside Loom's dev mode."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request

from reproduce import JAVA, REPO, ROOT, launch


def fetch(url, path, sha1=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        urllib.request.urlretrieve(url, path)
    if sha1:
        assert hashlib.sha1(path.read_bytes()).hexdigest() == sha1, path
    return path


def allowed(library):
    result = not library.get('rules')
    for rule in library.get('rules', []):
        os_rule = rule.get('os', {})
        if os_rule.get('name', 'windows') != 'windows':
            continue
        if os_rule.get('arch', 'x86_64') not in ('x86_64','amd64'):
            continue
        if rule.get('features'):
            continue
        result = rule['action'] == 'allow'
    return result


def prepare(name, renderer, probe, aurora):
    root = ROOT / name
    root.mkdir(exist_ok=False)
    game = root / 'game'
    (game / 'mods').mkdir(parents=True)
    (root / 'DISPOSABLE-RESEARCH.txt').write_text('Packaged-artifact renderer acceptance fixture.\n')
    (game / 'options.txt').write_text('onboardAccessibility:false\nmaxFps:60\nenableVsync:false\nrenderDistance:8\nsimulationDistance:5\nguiScale:2\nfullscreen:false\n')
    world = ROOT / 'control-02/game/saves/New World'
    if world.exists():
        shutil.copytree(world, game / 'saves/New World')
    else:
        assert not probe, 'First create and save New World in the isolated control-02 fixture'
    minecraft = Path.home() / 'AppData/Roaming/.minecraft'
    version = json.loads((minecraft / 'versions/1.21.11/1.21.11.json').read_text())
    profile = json.load(urllib.request.urlopen('https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.19.2/profile/json'))
    (root / 'fabric-profile.json').write_text(json.dumps(profile,indent=2))
    entries = []
    for library in version['libraries']:
        if not allowed(library):
            continue
        artifact = library.get('downloads', {}).get('artifact')
        if artifact:
            existing = minecraft / 'libraries' / artifact['path']
            path = fetch(artifact['url'], existing if existing.exists() else ROOT / 'production-libraries' / artifact['path'], artifact['sha1'])
            entries.append(str(path))
    for library in profile['libraries']:
        group, artifact, version_number = library['name'].split(':')
        relative = '/'.join([group.replace('.','/'), artifact, version_number, f'{artifact}-{version_number}.jar'])
        path = fetch(library.get('url','https://libraries.minecraft.net/') + relative, ROOT / 'production-libraries' / relative)
        entries.append(str(path))
    client = minecraft / 'versions/1.21.11/1.21.11.jar'
    assert hashlib.sha1(client.read_bytes()).hexdigest() == version['downloads']['client']['sha1']
    entries.append(str(client))
    cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
    api = next((cache / 'net.fabricmc.fabric-api/fabric-api/0.141.4+1.21.11').rglob('fabric-api-0.141.4+1.21.11.jar'))
    shutil.copy2(api, game / 'mods' / api.name)
    if aurora:
        properties = dict(line.split('=', 1) for line in (REPO / 'gradle.properties').read_text().splitlines()
                          if '=' in line and not line.lstrip().startswith('#'))
        jar = REPO / 'build/libs' / ('aurora-' + properties['mod_version'].strip() + '.jar')
        if not jar.is_file():
            jars = [p for p in (REPO / 'build/libs').glob('*.jar') if not any(s in p.name for s in ('sources','dev'))]
            assert len(jars) == 1, jars
            jar = jars[0]
        shutil.copy2(jar, game / 'mods' / jar.name)
    if renderer == 'vulkan':
        jar = ROOT / 'VulkanMod-0.6.8+1.21.11.jar'
        shutil.copy2(jar, game / 'mods' / jar.name)
    elif renderer == 'sodium':
        jar = Path(os.environ['TEMP']) / 'aii-s1/game/mods/sodium-fabric-0.8.14+mc1.21.11.jar'
        shutil.copy2(jar, game / 'mods' / jar.name)
    if probe:
        assert aurora
        shutil.copy2(ROOT / 'tooling/aurora-vulkan-probe-production.jar',game / 'mods/aurora-vulkan-probe.jar')
    jvm = ['-Xmx2048m', '-Dfile.encoding=UTF-8']
    if probe:
        jvm += ['-Daurora.vulkan.output=' + str(root)]
    spec = dict(executable=str(JAVA), workingDirectory=str(game), classpath=';'.join(entries),
                jvmArgs=jvm, mainClass=profile['mainClass'], args=[
                    '--username','VulkanResearch','--version','1.21.11','--gameDir',str(game),
                    '--assetsDir',str(minecraft / 'assets'),'--assetIndex',version['assetIndex']['id'],
                    '--accessToken','0','--userType','legacy','--versionType','release',
                    '--width','1280','--height','720'])
    (root / 'launch-private.json').write_text(json.dumps(spec,indent=2))
    paths = [Path(p) for p in entries] + list((game / 'mods').glob('*.jar'))
    (root / 'input-manifest.json').write_text(json.dumps([dict(path=str(p),sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths],indent=2))
    return root


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('name')
    parser.add_argument('--renderer',choices=['vulkan','opengl','sodium'],default='vulkan')
    parser.add_argument('--probe',action='store_true')
    parser.add_argument('--aurora',action='store_true')
    options = parser.parse_args()
    raise SystemExit(launch(prepare(options.name,options.renderer,options.probe,options.aurora)))
