"""Read-only SHA-256 preservation audit against the Initiative II baseline."""
import argparse
import hashlib
import json
from pathlib import Path


def audit(root):
    folder = root / '.git/performance-initiative-ii'
    baseline = json.loads((folder / 'baseline.json').read_text())
    original_untracked = set(baseline['untracked']) - {''}
    missing, changed, protected_changed = [], [], []
    for record in baseline['files']:
        path = root / record['path']
        if not path.is_file():
            missing.append(record['path']); continue
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        if digest != record['sha256']:
            changed.append(record['path'])
            if record['path'] in original_untracked or record['path'].startswith('run/'):
                protected_changed.append(record['path'])
    result = dict(files=len(baseline['files']), missing=missing, changed=changed,
                  protectedChanged=protected_changed)
    (folder / 'preservation-audit.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    assert not missing and not protected_changed, 'Pre-existing evidence changed'


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('roots', nargs='+', type=Path)
    for root in parser.parse_args().roots: audit(root.resolve())
