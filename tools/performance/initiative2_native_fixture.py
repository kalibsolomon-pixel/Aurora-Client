"""Prepare only a NEW disposable native fixture; preserve initial copied state."""
import json
from pathlib import Path
import shutil
import sys

sys.path.insert(0, 'C:/Dev/aurora-launcher/tools/benchmark')
from initiative2_startup import prepare


def main():
    root = Path('C:/Users/kalib/AppData/Local/Temp/aurora-p0-2-i')
    source = Path('C:/Users/kalib/AppData/Local/Temp/ai-1019-d6878d4f/managed')
    binary = Path('C:/Users/kalib/AppData/Local/Temp/aurora-p0-2-ii/final.exe')
    prepare(root, source, binary)
    shutil.copy2(root / 'reference.exe', root / 'final.exe')
    registry_path = root / 'managed/launcher/instances.json'
    registry = json.loads(registry_path.read_text())
    assert len(registry['instances']) == 1
    record = registry['instances'][0]
    instance = root / 'managed/instances' / record['id']
    template = Path('C:/Users/kalib/AppData/Local/Temp/aii-native-template/game')
    shutil.copy2(instance / 'options.txt', root / 'initial-copied-options.txt')
    shutil.copy2(template / 'options.txt', instance / 'options.txt')
    for name in ['mods', 'config', 'saves']:
        old, archive = instance / name, root / ('initial-copied-' + name)
        assert old.resolve().is_relative_to(root.resolve())
        assert archive.resolve().is_relative_to(root.resolve()) and not archive.exists()
        if old.exists(): old.rename(archive)
        shutil.copytree(template / name, old)
    api = list((root / 'initial-copied-mods').glob('fabric-api-*.jar'))
    assert len(api) == 1
    shutil.copy2(api[0], instance / 'mods' / api[0].name)
    shutil.copy2(Path('C:/Dev/Aurora-Client/build/libs/aurora-3.0.0.jar'), instance / 'mods/aurora-initiative-ii-local.jar')
    shutil.copy2(Path('C:/Users/kalib/AppData/Local/Temp/aii-input/tooling/aurora-research.jar'), instance / 'mods/aurora-research.jar')
    output = root / 'game-1'
    output.mkdir()
    (output / 'DISPOSABLE-RESEARCH.txt').write_text('Native disposable workload; retain\n')
    config = record['configuration']
    config['memoryMib'] = 2048
    # Real game metadata currently rejects the native resolution placeholders.
    # Keep the saved 1280x720 options override; do not bypass launch validation.
    config['window'] = None
    config['additionalJvmArguments'] = ' '.join(['-Daurora.research.output=' + output.as_posix(),
        '-Daurora.research.kind=stress', '-Daurora.research.seconds=630', '-Daurora.research.profile=false'])
    registry_path.write_text(json.dumps(registry, indent=2) + '\n')
    (root / 'integration-private.json').write_text(json.dumps(dict(instance=str(instance), output=str(output)), indent=2))
    print(json.dumps(dict(root=str(root), mods=len(list((instance / 'mods').glob('*.jar'))))))


if __name__ == '__main__': main()
