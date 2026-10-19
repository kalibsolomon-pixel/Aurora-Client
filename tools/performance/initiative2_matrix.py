"""Freeze six matched, explicitly marked workloads; never run them concurrently."""
import hashlib
import json
from pathlib import Path
import subprocess
from initiative2_prepare import prepare, REPO


def freeze():
    first = Path('C:/Users/kalib/AppData/Local/Temp/aii-s1')
    template = json.loads((first / 'launch-private.json').read_text())
    reference = REPO / '.git/performance-initiative-ii/reference-client'
    candidate_classes = REPO / 'build/classes/java/client'
    changed = []
    for path in sorted(candidate_classes.rglob('*.class')):
        old = reference / 'build/classes/java/client' / path.relative_to(candidate_classes)
        if path.read_bytes() != old.read_bytes(): changed.append(path.relative_to(candidate_classes).as_posix())
    assert set(changed) <= {'com/aurora/client/worldmap/cache/RegionCache.class',
                            'com/aurora/client/worldmap/cache/RegionCache$RegionTile.class'}, changed
    # Recompiling the outer source shifts inner-class debug line numbers.
    inner = 'com.aurora.client.worldmap.cache.RegionCache$RegionTile'
    before = subprocess.check_output(['javap', '-p', '-c', '-classpath', str(reference / 'build/classes/java/client'), inner])
    after = subprocess.check_output(['javap', '-p', '-c', '-classpath', str(candidate_classes), inner])
    assert before == after, 'Unexpected inner-class bytecode change'
    order = ['reference', 'candidate', 'candidate', 'reference', 'reference', 'candidate']
    rows = []
    for i, variant in enumerate(order, 1):
        root = first.parent / f'aii-s{i}'
        if i != 1: prepare(root, pack_source=first.parent / 'aii-pack-screen')
        assert not (root / 'frames.csv').exists()
        spec = json.loads(json.dumps(template))
        spec['workingDirectory'] = str(root / 'game')
        spec['jvmArgs'] = [('-Daurora.research.output=' + str(root)) if arg.startswith('-Daurora.research.output=') else arg for arg in spec['jvmArgs']]
        launch = (REPO / '.gradle/loom-cache/launch.cfg').read_text()
        if variant == 'reference':
            for part in ['build/classes/java/client', 'build/resources/client']:
                a, b = str(REPO / part), str(reference / part)
                spec['classpath'] = spec['classpath'].replace(a, b)
                launch = launch.replace(a, b)
        config = root / 'frozen-launch.cfg'
        config.write_text(launch)
        spec['jvmArgs'] = ['-Dfabric.dli.config=' + str(config) if arg.startswith('-Dfabric.dli.config=') else arg for arg in spec['jvmArgs']]
        (root / 'launch-private.json').write_text(json.dumps(spec, indent=2))
        rows.append(dict(order=i, variant=variant, root=str(root),
                         launchSha256=hashlib.sha256(json.dumps(spec).encode()).hexdigest()))
    (REPO / '.git/performance-initiative-ii/matrix-private.json').write_text(json.dumps(rows, indent=2))
    print(json.dumps(dict(order=order, changedClasses=changed)))


def micro():
    template = json.loads(Path('C:/Users/kalib/AppData/Local/Temp/aii-s1/launch-private.json').read_text())
    candidate = str(REPO / 'build/classes/java/client')
    reference = str(REPO / '.git/performance-initiative-ii/reference-client/build/classes/java/client')
    results = []
    for i, variant in enumerate(['reference', 'candidate', 'candidate', 'reference', 'reference', 'candidate'], 1):
        directory = REPO / f'.git/performance-initiative-ii/micro-valid-{i}'
        directory.mkdir(exist_ok=False)
        classpath = template['classpath'].replace(reference, candidate)
        if variant == 'reference': classpath = classpath.replace(candidate, reference)
        assert (reference in classpath) == (variant == 'reference')
        result = subprocess.run([template['executable'], '-Xms256m', '-Xmx256m',
            '-Dlog4j.configurationFile=' + str(REPO / 'src/test/resources/aurora-test-log4j2.xml'),
            '-Daurora.testLogDirectory=' + str(directory / 'logs'),
            '-cp', classpath, 'research.RegionCacheBenchmark'], cwd=directory,
            capture_output=True, timeout=30)
        (directory / 'stdout.log').write_bytes(result.stdout)
        (directory / 'stderr.log').write_bytes(result.stderr)
        assert result.returncode == 0, result.stderr.decode(errors='replace')[-1500:]
        line = next(l for l in result.stdout.decode().splitlines() if l.startswith('{"calls"'))
        results.append(dict(order=i, variant=variant, **json.loads(line)))
    (REPO / '.git/performance-initiative-ii/micro-valid-results.json').write_text(json.dumps(results, indent=2))
    print(json.dumps(results, indent=2))


if __name__ == '__main__':
    import sys
    if sys.argv[1:] == ['micro']: micro()
    else: freeze()
