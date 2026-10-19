"""Verify genuinely observed terrain after the disposable extended client exits."""
import argparse
import gzip
import json
from pathlib import Path
import struct


def verify(root):
    assert (root / 'DISPOSABLE-RESEARCH.txt').is_file()
    process = json.loads((root / 'acceptance-process.json').read_text())
    assert process['exitCode'] == 0
    observed = json.loads((root / 'observed-regions.json').read_text())
    directory = root / 'game/config/aurora-worldmap' / observed['identity'] / observed['dimensionFolder']
    matches, chunks, compressed, payload_bytes = 0, 0, 0, 0
    points = {(p['x'] >> 9, p['z'] >> 9): p for p in observed['points']}
    files = list(directory.glob('r.*.*.wmr'))
    for path in files:
        compressed += path.stat().st_size
        with gzip.open(path, 'rb') as stream:
            payload = stream.read(5 + 1024 * 8 + 512 * 512 * 4 + 1)
        assert len(payload) == 5 + 1024 * 8 + 512 * 512 * 4, path.name
        assert struct.unpack_from('>IB', payload) == (0x41574D52, 1), path.name
        payload_bytes += len(payload)
        chunks += sum(v != 0 for (v,) in struct.iter_unpack('>q', payload[5:8197]))
        parts = path.name.split('.')
        point = points.get((int(parts[1]), int(parts[2])))
        if point:
            offset = 8197 + ((point['z'] & 511) * 512 + (point['x'] & 511)) * 4
            assert struct.unpack_from('>i', payload, offset)[0] == point['abgr'], path.name
            stamp = ((point['z'] & 511) >> 4) * 32 + ((point['x'] & 511) >> 4)
            assert struct.unpack_from('>q', payload, 5 + stamp * 8)[0] != 0, path.name
            matches += 1
    assert matches == len(points) == 40, (matches, len(points))
    result = dict(regions=len(files), stampedChunks=chunks, observedPointsVerified=matches,
                  compressedBytes=compressed, decodedPayloadBytes=payload_bytes,
                  maxResident=observed['maxResident'], maxLoads=observed['maxLoads'],
                  maxOverviews=observed['maxOverviews'], maxRegionTextures=observed['maxRegionTextures'],
                  maxOverviewTextures=observed['maxOverviewTextures'])
    with (root / 'region-integrity.json').open('x') as output:
        json.dump(result, output, indent=2)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    print(json.dumps(verify(parser.parse_args().root.resolve()), indent=2))
