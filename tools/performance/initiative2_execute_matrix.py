"""Execute only already frozen marked fixtures, sequentially; stop on invalid runs."""
import argparse
import json
from pathlib import Path
import subprocess
from initiative2_run import run
from initiative2_review import review
from initiative2_prepare import REPO


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('orders', nargs='+', type=int)
    args = parser.parse_args()
    inputs = json.loads((REPO / '.git/performance-initiative-ii/matrix-private.json').read_text())
    for order in args.orders:
        row = inputs[order - 1]
        root = Path(row['root'])
        print(json.dumps(dict(startOrder=order, variant=row['variant'])), flush=True)
        assert not (root / 'process.json').exists(), 'Never overwrite a prior capture'
        assert run(root, 480) == 0
        result = review(root)
        assert result['validState'], 'Focus/pause/screen validity failure; preserve and stop'
        java = json.loads((root / 'launch-private.json').read_text())['executable']
        with (root / 'jfr-attribution.json').open('xb') as output:
            subprocess.run([java, '--source', '21', str(REPO / 'tools/performance/JfrAttribution.java'),
                            str(root / 'profile.jfr')], stdout=output, check=True, timeout=30)
        print(json.dumps(dict(completedOrder=order, variant=row['variant'],
                              phases=result['phases'])), flush=True)
