"""Read-only fixture validation of Anvil block entities and biome palettes.

Small NBT reader for locally generated research saves; not a general importer.
"""
import argparse
import io
import json
from pathlib import Path
import struct
import zlib


def nbt(data):
    stream = io.BytesIO(data)
    def unpack(fmt):
        return struct.unpack('>' + fmt, stream.read(struct.calcsize('>' + fmt)))[0]
    def string():
        return stream.read(unpack('H')).decode('utf-8')
    def payload(tag):
        if tag in range(1, 7): return unpack({1:'b', 2:'h', 3:'i', 4:'q', 5:'f', 6:'d'}[tag])
        if tag == 7: return stream.read(unpack('i'))
        if tag == 8: return string()
        if tag == 9:
            child, count = unpack('B'), unpack('i')
            assert 0 <= count <= 2_000_000
            return [payload(child) for _ in range(count)]
        if tag == 10:
            result = {}
            while (child := unpack('B')) != 0:
                name = string(); result[name] = payload(child)
            return result
        if tag in [11, 12]:
            count = unpack('i'); assert 0 <= count <= 2_000_000
            stream.seek(count * (4 if tag == 11 else 8), 1)
            return None
        raise ValueError('Unknown NBT tag')
    assert unpack('B') == 10
    string()
    return payload(10)


def scene(world):
    hoppers, biomes, chunks = set(), set(), 0
    for file in sorted((world / 'region').glob('*.mca')):
        data = file.read_bytes()
        for index in range(1024):
            location = int.from_bytes(data[index * 4:index * 4 + 4], 'big')
            offset = (location >> 8) * 4096
            if not offset: continue
            length = int.from_bytes(data[offset:offset + 4], 'big')
            compression = data[offset + 4]
            assert compression == 2, 'Expected zlib research chunk'
            root = nbt(zlib.decompress(data[offset + 5:offset + 4 + length]))
            chunks += 1
            for entity in root.get('block_entities', []):
                if entity['id'] == 'minecraft:hopper':
                    hoppers.add(tuple(entity[k] for k in ['x', 'y', 'z']))
            for section in root.get('sections', []):
                biomes.update(section.get('biomes', {}).get('palette', []))
    expected = {(x, 80, z) for x in range(-8, 8) for z in range(20, 36)}
    assert expected <= hoppers, 'Missing declared hopper footprint'
    result = dict(chunks=chunks, fixtureHoppers=len(expected & hoppers),
                  terralithBiomes=sorted(b for b in biomes if b.startswith('terralith:')))
    assert result['terralithBiomes'], 'No new worldgen biome observed'
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('worlds', nargs='+', type=Path)
    for world in parser.parse_args().worlds:
        print(json.dumps(dict(world=str(world), **scene(world))))
