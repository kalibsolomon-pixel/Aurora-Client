"""Guard the portable default export and the packaged 3.2.0 identity contract."""

from source_tree import client_path
import json
import re
import unittest
from pathlib import Path
from prepare_310_defaults import EXACT, PREFIXES

ROOT = Path(__file__).resolve().parents[1]


class ReleaseDefaultsTest(unittest.TestCase):
    def test_only_allowlisted_portable_fields_are_bundled(self):
        data = json.loads(client_path('resources/assets/aurora/defaults/3.2.0.json').read_text())
        for key in data:
            self.assertTrue(key in EXACT or key.startswith(PREFIXES), key)
        self.assertNotIn('stats', data['moduleLayouts'])
        self.assertEqual(12, len(data['moduleLayouts']))
        self.assertFalse(data['minimapEnabled'])
        self.assertFalse(data['infoEnabled'])
        # Inspect all nested strings, including registry-key maps, for personal/path data.
        def strings(value):
            if isinstance(value, str):
                yield value
            elif isinstance(value, dict):
                for key, child in value.items():
                    yield key
                    yield from strings(child)
            elif isinstance(value, list):
                for child in value:
                    yield from strings(child)
        for value in strings(data):
            self.assertNotRegex(value, r'(?i)([a-z]:\\|/home/|/users/|https?://|token|secret|password|uuid)')
        for key in ('particleVisibility', 'particleScale', 'particleColor', 'itemScales'):
            for registry_id in data[key]:
                self.assertRegex(registry_id, r'^minecraft:[a-z0-9_]+$')

    def test_release_version_and_resource_wiring(self):
        properties = (ROOT / 'gradle.properties').read_text()
        self.assertRegex(properties, r'(?m)^mod_version=\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$')
        source = (ROOT / 'core/src/main/java/com/aurora/client/config/ReleaseDefaults.java').read_text()
        self.assertIn('/assets/aurora/defaults/3.2.0.json', source)
        metadata = json.loads(client_path('resources/fabric.mod.json').read_text())
        self.assertEqual('${version}', metadata['version'])
        minecraft = re.search(r'(?m)^minecraft_version=(.+)$', properties).group(1)
        self.assertEqual('~' + minecraft, metadata['depends']['minecraft'])


if __name__ == '__main__':
    unittest.main()
