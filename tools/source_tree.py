"""Resolve selected native sources plus the shared adapter sources."""
import json
import os
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def adapter_root():
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
    versions = json.loads((ROOT / 'gradle/minecraft-versions.json').read_text())
    minecraft = os.environ.get('AURORA_MINECRAFT', properties['minecraft_version'])
    return ROOT / "adapters" / versions[minecraft]['adapter']
def client_path(relative):
    native = adapter_root() / "src/client" / relative
    return native if native.exists() else ROOT / 'adapters/shared/src/client' / relative
