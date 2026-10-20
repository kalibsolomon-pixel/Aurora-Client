"""Isolated Vulkan/OpenGL reproduction; never uses the live run/ directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

REPO = Path(__file__).resolve().parents[2]
ROOT = REPO / 'build/vulkan-investigation'
JAVA = Path(os.environ.get('AURORA_COMPAT_JAVA', str(Path(os.environ['LOCALAPPDATA']) / 'com.aurora.launcher/runtimes/java-runtime-delta/windows-x64-cb4394a27089d19f65d5baa6cf0482c27c3c7865/bin/java.exe')))


def prepare(name, aurora, probe=False, renderer='vulkan', quick=False):
    root = ROOT / name
    root.mkdir(exist_ok=False)
    game = root / 'game'
    game.mkdir()
    (root / 'DISPOSABLE-RESEARCH.txt').write_text('Isolated Vulkan compatibility fixture.\n')
    if probe:
        assert aurora
        shutil.copytree(ROOT / 'control-02/game/saves/New World', game / 'saves/New World')
    (game / 'options.txt').write_text('onboardAccessibility:false\nmaxFps:60\nenableVsync:false\nrenderDistance:8\nsimulationDistance:5\nguiScale:2\nfullscreen:false\n')
    spec = json.loads((ROOT / 'launch-template.json').read_text())
    entries = spec['classpath'].split(';')
    if renderer != 'vulkan':
        entries = [e for e in entries if 'VulkanMod-' not in e]
    if renderer == 'sodium':
        mods = game / 'mods'
        mods.mkdir()
        source = Path(os.environ['TEMP']) / 'aii-s1/game/mods/sodium-fabric-0.8.14+mc1.21.11.jar'
        shutil.copy2(source, mods / source.name)
    if probe:
        probe_path = root / 'aurora-vulkan-probe.jar'
        shutil.copy2(ROOT / 'tooling/aurora-vulkan-probe.jar', probe_path)
        entries.append(str(probe_path))
    # Loom's local development remap strips nested libraries. Use the exact
    # published LWJGL jars for both sides, including their Windows natives.
    libraries = ROOT / 'libraries'
    libraries.mkdir(exist_ok=True)
    with zipfile.ZipFile(ROOT / 'VulkanMod-0.6.8+1.21.11.jar') as archive:
        for entry in archive.namelist():
            if entry.startswith('META-INF/jars/lwjgl-') and entry.endswith('.jar') and ('natives-' not in entry or 'natives-windows' in entry):
                target = libraries / Path(entry).name
                if not target.exists():
                    target.write_bytes(archive.read(entry))
                entries.append(str(target))
    if not aurora:
        entries = [e for e in entries if str(REPO / 'build/classes') not in e and str(REPO / 'build/resources') not in e]
    else:
        for part in ('build/classes/java/client', 'build/resources/client'):
            shutil.copytree(REPO / part, root / part)
            entries = [e.replace(str(REPO / part), str(root / part)) for e in entries]
    config = (REPO / '.gradle/loom-cache/launch.cfg').read_text()
    if not aurora:
        lines = []
        for line in config.splitlines():
            if line.strip().startswith('fabric.classPathGroups='):
                key, groups = line.split('=', 1)
                groups = [g for g in groups.split(';;') if str(REPO / 'build/classes') not in g]
                line = key + '=' + ';;'.join(groups)
            lines.append(line)
        config = '\n'.join(lines)
    else:
        for part in ('build/classes/java/client', 'build/resources/client'):
            config = config.replace(str(REPO / part), str(root / part))
    (root / 'launch.cfg').write_text(config)
    args = [a for a in spec['jvmArgs'] if not a.startswith(('@', '-Dfabric.dli.config=', '-Dfabric.skipDependencyCheck=', '-Dfabric.modVersion.minecraft='))]
    args += ['-Xmx2048m', '-Dfabric.dli.config=' + str(root / 'launch.cfg')]
    if probe:
        args += ['-Daurora.vulkan.output=' + str(root)]
        if quick:
            args += ['-Daurora.vulkan.quick=true']
    spec.update(executable=str(JAVA), jvmArgs=args, classpath=';'.join(entries), workingDirectory=str(game))
    spec['args'] += ['--username', 'VulkanResearch', '--width', '1280', '--height', '720']
    (root / 'launch-private.json').write_text(json.dumps(spec, indent=2))
    manifest = [dict(path=e, sha256=hashlib.sha256(Path(e).read_bytes()).hexdigest()) for e in entries if Path(e).is_file()]
    (root / 'input-manifest.json').write_text(json.dumps(manifest, indent=2))
    return root


def launch(root):
    spec = json.loads((root / 'launch-private.json').read_text())
    version = subprocess.run([spec['executable'], '-version'],capture_output=True,text=True,check=True)
    assert 'version "21.' in version.stderr, 'Select Java 21 with AURORA_COMPAT_JAVA'
    with (root / 'stdout.log').open('xb') as out, (root / 'stderr.log').open('xb') as err:
        child = subprocess.Popen([spec['executable'], *spec['jvmArgs'], '-cp', spec['classpath'], spec['mainClass'], *spec['args']], cwd=spec['workingDirectory'], stdout=out, stderr=err, creationflags=subprocess.CREATE_NO_WINDOW)
    (root / 'process.json').write_text(json.dumps(dict(pid=child.pid)))
    print(json.dumps(dict(root=str(root), pid=child.pid)), flush=True)
    code = child.wait()
    (root / 'exit.json').write_text(json.dumps(dict(exitCode=code)))
    print('exitCode', code, flush=True)
    return code


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('name')
    parser.add_argument('--aurora', action='store_true')
    parser.add_argument('--probe', action='store_true')
    parser.add_argument('--renderer', choices=['vulkan','opengl','sodium'], default='vulkan')
    parser.add_argument('--quick', action='store_true')
    options = parser.parse_args()
    raise SystemExit(launch(prepare(options.name, options.aurora, options.probe, options.renderer, options.quick)))
