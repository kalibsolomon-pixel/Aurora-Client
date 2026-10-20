"""Pin and verify the published VulkanMod input. Does not change installed mods."""
import hashlib
import json
import urllib.request
from reproduce import ROOT

ROOT.mkdir(parents=True, exist_ok=True)
metadata = json.load(urllib.request.urlopen('https://api.modrinth.com/v2/version/hWKRTXbQ'))
assert metadata['version_number'] == '0.6.8+1.21.11'
assert '1.21.11' in metadata['game_versions']
artifact = next(file for file in metadata['files'] if file['primary'])
path = ROOT / artifact['filename']
if not path.exists():
    urllib.request.urlretrieve(artifact['url'],path)
assert hashlib.sha512(path.read_bytes()).hexdigest() == artifact['hashes']['sha512']
(ROOT / 'vulkanmod-version.json').write_text(json.dumps(metadata,indent=2))
print(path)
