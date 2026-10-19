"""Bounded streaming JFR review. Sample counts/weights are statistical attribution."""
import argparse
import json
from pathlib import Path
import subprocess


def review(root, jfr):
    raw = root / 'profile.jfr'
    summary = subprocess.check_output([str(jfr), 'summary', str(raw)], timeout=30)
    (root / 'jfr-summary.txt').write_bytes(summary)
    # CLI stack JSON expands small recordings into hundreds of MB. Use the
    # JDK's streaming reader and emit only aggregate attribution instead.
    source = Path(__file__).with_name('JfrAttribution.java')
    java = jfr.with_name('java.exe' if jfr.suffix == '.exe' else 'java')
    export = subprocess.check_output([str(java), '--source', '21', str(source), str(raw)], timeout=30)
    result = json.loads(export)
    (root / 'jfr-review.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('root', type=Path)
    parser.add_argument('--jfr', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(review(args.root, args.jfr), indent=2))
