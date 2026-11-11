"""Parity release invariants: complete artifact sets and source-bound GPU gates."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import parity_acceptance as acceptance
from tools import verify_release_artifact as release

ROOT = Path(__file__).resolve().parents[1]


def artifact(mc):
    return dict(schemaVersion=release.SCHEMA_VERSION, version='3.4.0', channel='stable', tag='v3.4.0',
                sourceSha='a' * 40, minecraftVersion=mc, fileName=f'aurora-3.4.0-mc{mc}.jar', sizeBytes=10,
                sha256='b' * 64)


def bundle():
    items = [artifact(mc) for mc in ('26.1.2', '26.2', '26.3')]
    return dict(schemaVersion=3, version='3.4.0', channel='stable', tag='v3.4.0', sourceSha='a' * 40,
                minecraftVersions=[m['minecraftVersion'] for m in items], artifacts=items)


class ParityTest(unittest.TestCase):
    def test_every_declared_adapter_has_native_manifest_and_feature_registry(self):
        catalog = (ROOT / 'core/src/main/java/com/aurora/client/feature/FeatureCatalog.java').read_text(encoding='utf-8')
        import re
        bindings = set(re.findall(r'\bmixin\("([^"]+)"', catalog))
        bindings.remove('hitreg.')
        hitreg = re.search(r'for \(String hitreg : List.of\((.*?)\)\)', catalog, re.S)[1]
        bindings.update('hitreg.' + name for name in re.findall(r'"([^"]+)"', hitreg))
        covered = set()
        registries = []
        for mc, line in acceptance.versions(ROOT).items():
            native = ROOT / 'adapters' / line['adapter'] / 'src/client'
            manifest = json.loads((native / 'resources/aurora.mixins.json').read_text())
            self.assertEqual(len(manifest['client']), len(set(manifest['client'])))
            for mixin in manifest['client']:
                self.assertIn(mixin, bindings)
                covered.add(mixin)
                relative = Path('com/aurora/client/mixin') / (mixin.replace('.', '/') + '.java')
                self.assertTrue((native / 'java' / relative).is_file() or (ROOT / 'adapters/shared/src/client/java' / relative).is_file())
            metadata = json.loads((native / 'resources/fabric.mod.json').read_text())
            self.assertEqual('~' + mc, metadata['depends']['minecraft'])
            registry = native / 'java/com/aurora/client/screen/FeatureRegistry.java'
            if not registry.exists(): registry = ROOT / 'adapters/shared/src/client/java/com/aurora/client/screen/FeatureRegistry.java'
            registries.append(registry.resolve())
        self.assertEqual(bindings, covered, 'Every native binding must ship on a supported line')
        self.assertTrue(all(r == registries[0] for r in registries))
        self.assertEqual(ROOT / 'adapters/shared/src/client/java/com/aurora/client/screen/FeatureRegistry.java', registries[0])

    def test_full_bundle_is_valid_and_historical_single_artifact_still_loads(self):
        self.assertEqual(3, len(release.artifact_entries(release.validate_metadata(bundle()))))
        self.assertEqual([artifact('26.3')], release.artifact_entries(release.validate_metadata(artifact('26.3'))))

    def test_missing_duplicate_and_mixed_source_artifacts_fail_closed(self):
        for change in ('missing', 'subset', 'duplicate', 'source', 'version'):
            value = bundle()
            if change == 'missing': value['artifacts'].pop()
            elif change == 'subset':
                value['artifacts'].pop()
                value['minecraftVersions'].pop()
            elif change == 'duplicate': value['artifacts'][1] = value['artifacts'][0]
            elif change == 'source': value['artifacts'][0]['sourceSha'] = 'c' * 40
            else: value['artifacts'][0]['version'] = '3.5.0'
            with self.subTest(change=change), self.assertRaises(release.ReleaseError):
                release.validate_metadata(value)

    def test_draft_asset_omission_and_extra_asset_fail_before_publication(self):
        value = bundle()
        for names in ([a['fileName'] for a in value['artifacts'][:-1]], [a['fileName'] for a in value['artifacts']] + ['unexpected.jar']):
            with self.assertRaises(release.ReleaseError):
                release.validate_release_state(dict(assets=[dict(name=n) for n in names]), value, draft=True)

    def test_all_draft_assets_are_individually_verified(self):
        value = bundle()
        state = dict(tag_name='v3.4.0', target_commitish='a'*40, draft=True, immutable=False, prerelease=False,
                     assets=[dict(name=a['fileName'], size=a['sizeBytes'], state='uploaded', digest='sha256:'+a['sha256']) for a in value['artifacts']])
        release.validate_release_state(state, value, draft=True)
        state['assets'][2]['digest'] = 'sha256:' + 'c'*64
        with self.assertRaises(release.ReleaseError): release.validate_release_state(state, value, draft=True)

    def test_handoff_rejects_an_extra_or_missing_jar(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaises(release.ReleaseError):
                release.verify_candidates(bundle(), Path(temporary), '3.4.0', 'stable', 'a'*40)

    def test_budget_and_warmups_are_unchanged(self):
        good = dict(mean=.25, p95=.40, fills=100, glass=0, verdict='within')
        runs = [dict(mean=5, p95=9, fills=0, glass=0, verdict='OVER')] * 2 + [good]*4
        acceptance.validate_runs(runs)
        for field, value in [('mean', .251), ('p95', .401), ('fills', 101), ('glass', 1), ('verdict', 'not judged'),
                             ('mean', float('nan')), ('p95', float('inf')), ('fills', -1)]:
            bad = copy.deepcopy(runs)
            bad[3][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError): acceptance.validate_runs(bad)
        with self.assertRaises(ValueError): acceptance.validate_runs(runs[:-1])

    def test_fingerprint_order_is_the_same_on_every_host(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in ('build.gradle', 'settings.gradle', 'gradle.properties', 'core/build.gradle', 'ports/build.gradle',
                         'gradle/minecraft-versions.json', 'tools/parity_acceptance.py', 'tools/parity.py'):
                (root / name).parent.mkdir(parents=True, exist_ok=True)
                (root / name).write_text(name)
            for directory in ('core/src/main', 'ports/src/main', 'tools/matrix'):
                (root / directory).mkdir(parents=True, exist_ok=True)
            (root / 'adapters/x').mkdir(parents=True)
            # Case-sensitive order puts "Zed" first; the Windows order the evidence used puts "a_b" first.
            (root / 'adapters/x/Zed.java').write_text('z')
            (root / 'adapters/x/a_b.java').write_text('a')
            fed = []

            class Recorder:
                def update(self, data):
                    fed.append(data)

                def hexdigest(self):
                    return ''

            real = hashlib.sha256
            with patch.object(acceptance.hashlib, 'sha256', side_effect=lambda *a: Recorder() if not a else real(*a)):
                acceptance.fingerprint(root)
            names = [d.rstrip(b'\0').decode() for d in fed if d.endswith(b'\0')]
            self.assertLess(names.index('adapters/x/a_b.java'), names.index('adapters/x/Zed.java'))

    def test_acceptance_rejects_stale_sources_and_missing_rows(self):
        with self.assertRaises(ValueError): acceptance.verify(ROOT, dict(sourceInputsSha256='old', rows=[]))
        with patch.object(acceptance, 'fingerprint', return_value='same'):
            with self.assertRaises(ValueError): acceptance.verify(ROOT, dict(sourceInputsSha256='same', rows=[]))


if __name__ == '__main__':
    unittest.main()
