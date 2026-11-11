"""Build every supported line sequentially, reusing core/ports task outputs.

Usage: python tools/parity.py [--minecraft VERSION]
The default builds and tests the complete parity set. No publication is performed.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    versions = json.loads((ROOT / 'gradle/minecraft-versions.json').read_text())
    parser.add_argument('--minecraft', choices=list(versions))
    args = parser.parse_args()
    wrapper = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
    for minecraft in [args.minecraft] if args.minecraft else versions:
        print(f'Building full parity candidate: Minecraft {minecraft}', flush=True)
        subprocess.run([wrapper, 'build', f'-Paurora.minecraft={minecraft}', '--console=plain'], cwd=ROOT, check=True)


if __name__ == '__main__':
    main()
