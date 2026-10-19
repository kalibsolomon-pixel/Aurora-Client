"""Quality failures stay failures; no real processes or worlds are used here."""
import json
from pathlib import Path
import tempfile
import unittest
from initiative2_review import metrics, percentile, review


class QualityTests(unittest.TestCase):
    def test_percentiles_and_long_interval_counts(self):
        self.assertEqual(percentile([3, 1, 2], .5), 2)
        result = metrics([1, 2, 3, 51, 101])
        self.assertEqual(result['intervalsOver50Ms'], 2)
        self.assertEqual(result['intervalsOver100Ms'], 1)

    def test_invalid_intervals_are_rejected(self):
        for values in [[], [0], [-1], [float('inf')], [float('nan')]]:
            with self.assertRaises(AssertionError): metrics(values)

    def test_failed_child_never_becomes_a_valid_capture(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'run.json').write_text('{}')
            (root / 'process.json').write_text(json.dumps(dict(exitCode=1)))
            with self.assertRaisesRegex(AssertionError, 'Abnormal'):
                review(root)

    def test_failed_scene_is_rejected_even_after_normal_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / 'run.json').write_text(json.dumps(dict(failedSetupCommands=['fill fixture footprint'])))
            (root / 'process.json').write_text(json.dumps(dict(exitCode=0)))
            with self.assertRaisesRegex(AssertionError, 'Scene setup'):
                review(root)


if __name__ == '__main__': unittest.main()
