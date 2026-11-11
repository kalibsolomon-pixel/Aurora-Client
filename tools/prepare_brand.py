"""Rasterize the supplied Aurora SVG for Minecraft's native PNG texture pipeline.

Install build-time dependencies with: python -m pip install resvg-py==0.5.0 Pillow
The original SVG stays bundled; no runtime SVG library or rendering pass is needed.
"""
from io import BytesIO
from pathlib import Path

from PIL import Image
import resvg_py

ROOT = Path(__file__).resolve().parents[1] / "src/client/resources/assets/aurora"


def main():
    # Fit the original 640x512 viewport inside the existing square logo band,
    # preserving its aspect ratio and transparency rather than stretching it.
    raster = Image.open(BytesIO(resvg_py.svg_to_bytes(
        svg_path=str(ROOT / "branding/aurora.svg"), width=256))).convert("RGBA")
    canvas = Image.new("RGBA", (256, 256))
    canvas.alpha_composite(raster, ((256 - raster.width) // 2, (256 - raster.height) // 2))
    canvas.save(ROOT / "textures/ui/aurora_mark.png")


if __name__ == "__main__":
    main()
