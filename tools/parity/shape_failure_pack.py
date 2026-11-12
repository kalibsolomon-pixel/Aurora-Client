"""Create a broken shape-shader resource pack in a marked disposable game dir.

Enable the generated pack in that fixture and launch with
`-Daurora.shapes.expectFallback=true` to exercise the shape-cases probe.
Never install this pack in a real game instance.
"""
import argparse
import json
from pathlib import Path


FORMATS = {"26.1.2": [84, 0], "26.2": [88, 0], "26.3": [97, 1]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft", choices=FORMATS)
    parser.add_argument("game_dir", type=Path)
    args = parser.parse_args()
    game = args.game_dir.resolve()
    if not (game / "SHAPE-BENCHMARK-DISPOSABLE.txt").is_file():
        parser.error("game directory must contain SHAPE-BENCHMARK-DISPOSABLE.txt")
    pack = game / "resourcepacks" / "AuroraShapeFailure"
    if pack.exists():
        parser.error(f"refusing to overwrite {pack}")
    shaders = pack / "assets" / "aurora" / "shaders" / "core"
    shaders.mkdir(parents=True)
    format_version = FORMATS[args.minecraft]
    metadata = {"pack": {"description": "Deliberately broken Aurora shape shader",
                         "min_format": format_version, "max_format": format_version}}
    (pack / "pack.mcmeta").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    (shaders / "ui_shape.fsh").write_text("#version 330\nthis_is_deliberately_invalid\n", encoding="utf-8")
    print(f"Created {pack}; enable file/AuroraShapeFailure in the disposable fixture.")


if __name__ == "__main__":
    main()
