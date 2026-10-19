"""Explicit local research baseline; never edits existing files or launches apps."""
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess


def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args]).decode().strip()


def preserve(root):
    destination = root / '.git/performance-initiative-ii'
    destination.mkdir(exist_ok=False)
    tracked = git(root, 'ls-files', '-z').split('\0')
    untracked = git(root, 'ls-files', '--others', '--exclude-standard', '-z').split('\0')
    paths = {root / name for name in tracked + untracked if name}
    # Existing worlds, configs, logs and benchmark captures stay immutable.
    run = root / 'run'
    if run.exists():
        paths.update(p for p in run.rglob('*') if p.is_file())
    rows = []
    for path in sorted(paths):
        with path.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        rows.append(dict(path=path.relative_to(root).as_posix(), bytes=path.stat().st_size,
                         sha256=digest))
    result = dict(createdUtc=dt.datetime.now(dt.timezone.utc).isoformat(),
                  branch=git(root, 'branch', '--show-current'), head=git(root, 'rev-parse', 'HEAD'),
                  status=git(root, 'status', '--short'), remotes=git(root, 'remote', '-v'),
                  history=git(root, 'log', '-6', '--oneline'), tracked=tracked,
                  untracked=untracked, files=rows)
    (destination / 'baseline.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(dict(repo=str(root), files=len(rows), bytes=sum(r['bytes'] for r in rows))))


if __name__ == '__main__':
    for repository in [Path('C:/Dev/Aurora-Client'), Path('C:/Dev/aurora-launcher')]:
        preserve(repository)
