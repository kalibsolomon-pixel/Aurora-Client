"""Native GUI-audit captures with the real remapped distributable (disposable dirs only).

Reuses the Vulkan fixture's pinned production launch (normal Fabric, Mojang client).
Never touches the live run/ directory or the user's instances.
"""
import argparse
import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'vulkan'))
import production  # noqa: E402
from reproduce import REPO, ROOT as VULKAN_ROOT, launch  # noqa: E402

ROOT = REPO / 'build/gui-audit'


def prepare(name, renderer, plan, sizes, jar):
    ROOT.mkdir(parents=True, exist_ok=True)
    production.ROOT = ROOT  # outputs live under build/gui-audit
    # Reuse the Vulkan fixture's immutable saved world + VulkanMod input.
    for link in ('control-02', 'VulkanMod-0.6.8+1.21.11.jar', 'production-libraries'):
        target = ROOT / link
        if not target.exists():
            src = VULKAN_ROOT / link
            (shutil.copytree if src.is_dir() else shutil.copy2)(src, target)
    root = production.prepare(name, renderer, False, True)
    if jar:
        mods = root / 'game/mods'
        for old in mods.glob('aurora-*.jar'):
            old.unlink()
        shutil.copy2(jar, mods / Path(jar).name)
    shutil.copy2(ROOT / 'tooling/aurora-gui-probe-production.jar', root / 'game/mods/aurora-gui-probe.jar')
    (root / 'DISPOSABLE-RESEARCH.txt').write_text('GUI audit capture fixture.\n')
    spec = json.loads((root / 'launch-private.json').read_text())
    spec['jvmArgs'] += ['-Daurora.gui.output=' + str(root), '-Daurora.gui.plan=' + plan, '-Daurora.gui.sizes=' + sizes]
    spec['args'] = [a if a != 'VulkanResearch' else 'GuiAudit' for a in spec['args']]
    (root / 'launch-private.json').write_text(json.dumps(spec, indent=2))
    return root


def relaunch(root, phase):
    """Launch (again) in the same disposable game dir with per-phase logs."""
    import subprocess
    assert (root / 'DISPOSABLE-RESEARCH.txt').is_file()
    spec = json.loads((root / 'launch-private.json').read_text())
    jvm = [a for a in spec['jvmArgs'] if not a.startswith('-Daurora.gui.phase=')] + [f'-Daurora.gui.phase={phase}']
    with (root / f'stdout-phase{phase}.log').open('wb') as out, (root / f'stderr-phase{phase}.log').open('wb') as err:
        code = subprocess.run([spec['executable'], *jvm, '-cp', spec['classpath'], spec['mainClass'], *spec['args']],
                              cwd=spec['workingDirectory'], stdout=out, stderr=err, creationflags=subprocess.CREATE_NO_WINDOW).returncode
    (root / f'exit-phase{phase}.json').write_text(json.dumps(dict(exitCode=code)))
    print('phase', phase, 'exitCode', code, flush=True)
    return code


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('name')
    parser.add_argument('--renderer', choices=['vulkan', 'opengl', 'sodium'], default='opengl')
    parser.add_argument('--plan', choices=['full', 'scale', 'persist'], default='full')
    parser.add_argument('--phase', type=int, default=1, help='persist plan phase')
    parser.add_argument('--reuse', action='store_true', help='relaunch an existing fixture (same game dir)')
    parser.add_argument('--sizes', default='1920x1080@3')
    parser.add_argument('--jar', help='explicit Aurora jar (defaults to build/libs)')
    o = parser.parse_args()
    if o.reuse:
        raise SystemExit(relaunch(ROOT / o.name, o.phase))
    root = prepare(o.name, o.renderer, o.plan, o.sizes, o.jar)
    if o.plan == 'persist':
        raise SystemExit(relaunch(root, o.phase))
    raise SystemExit(launch(root))
