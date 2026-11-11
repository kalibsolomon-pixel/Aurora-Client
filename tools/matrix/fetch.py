"""Download the pinned tier-1 mods from Modrinth and verify each file's SHA-512 (ADR 0004).

Usage: python -I tools/matrix/fetch.py tools/matrix/pins-<mc>.json <dest dir>

Only builds that Modrinth lists for the pinned Minecraft version and Fabric are
considered: some mods reuse one version number across Minecraft versions.
Writes <dest>/manifest.json with the file names and hashes. Keep <dest> outside
the repository; the jars are third-party code.
"""
import hashlib
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

PINS = json.loads(Path(sys.argv[1]).read_text())
DEST = Path(sys.argv[2])
DEST.mkdir(parents=True, exist_ok=True)
UA = {"User-Agent": "aurora-client-mod-matrix (github.com/kalibsolomon-pixel/Aurora-Client)"}
QUERY = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": json.dumps([PINS["minecraft"]])})


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()


manifest = {"minecraft": PINS["minecraft"], "mods": {}}
for slug, wanted in PINS["mods"].items():
    versions = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?{QUERY}"))
    match = [v for v in versions if v["version_number"] == wanted]
    if not match:
        sys.exit(f"{slug}: no {wanted} build for Fabric {PINS['minecraft']}")
    f = ([f for f in match[0]["files"] if f["primary"]] or match[0]["files"])[0]
    data = get(f["url"])
    digest = hashlib.sha512(data).hexdigest()
    if digest != f["hashes"]["sha512"]:
        sys.exit(f"{slug}: SHA-512 mismatch")
    (DEST / f["filename"]).write_bytes(data)
    manifest["mods"][slug] = {"version": wanted, "file": f["filename"], "sha512": digest}
    print(f"{slug:16} {wanted:32} sha512 ok")
(DEST / "manifest.json").write_text(json.dumps(manifest, indent=2))
