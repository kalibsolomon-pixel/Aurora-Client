"""Paths for the single active version adapter in this checkout."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def adapter_root():
    properties = dict(line.split("=", 1) for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
    return ROOT / "adapters" / ("minecraft-" + properties["minecraft_version"])
def client_path(relative):
    return adapter_root() / "src/client" / relative
