"""Rasterize the supplied Aurora SVG for Minecraft's native PNG texture pipeline.

Writes square power-of-two tiers (16..512, with 256 at aurora_mark.png) plus
matching .mcmeta files enabling linear filtering and edge clamping, and bleeds
edge RGB into adjacent transparent pixels so bilinear sampling never produces
dark fringes.

Install build-time dependencies with: python -m pip install resvg-py==0.5.0 Pillow
The original SVG stays bundled; no runtime SVG library or rendering pass is needed.
"""

from io import BytesIO
from pathlib import Path
import re

from PIL import Image
import resvg_py

# The brand assets are shared by every line (adapters/shared), not a native adapter.
ROOT = Path(__file__).resolve().parents[1] / "adapters/shared/src/client/resources/assets/aurora"
UI_DIR = ROOT / "textures/ui"
TIERS = (16, 32, 64, 128, 256, 512)
MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def tier_filename(size: int) -> str:
    return "aurora_mark.png" if size == 256 else f"aurora_mark_{size}.png"


def square_svg(svg_text: str) -> str:
    """Centre the 640x512 ribbon inside a 640x640 viewBox for sub-pixel alignment across tiers."""
    updated, count = re.subn(r'viewBox="0 0 640 512"', 'viewBox="0 -64 640 640"', svg_text, count=1)
    if count != 1:
        raise ValueError("Expected viewBox=\"0 0 640 512\" in aurora.svg")
    return updated


def bleed_edge_rgb(image: Image.Image, passes: int = 3) -> Image.Image:
    """Copy alpha-weighted neighbour RGB into transparent pixels while keeping alpha=0.

    Minecraft's GUI_TEXTURED pipeline uses straight-alpha blending; without RGB
    dilation, hardware bilinear filtering blends opaque edge colours with
    transparent black (0, 0, 0, 0) and creates a dark fringe.
    """
    out = image.copy()
    w, h = out.size
    for _ in range(passes):
        src = out.load()
        updates = []
        for y in range(h):
            y0, y1 = max(0, y - 1), min(h - 1, y + 1)
            for x in range(w):
                r, g, b, a = src[x, y]
                if a != 0 or (r | g | b) != 0:
                    continue
                x0, x1 = max(0, x - 1), min(w - 1, x + 1)
                wr = wg = wb = wt = 0
                for ny in range(y0, y1 + 1):
                    for nx in range(x0, x1 + 1):
                        nr, ng, nb, na = src[nx, ny]
                        weight = na if na > 0 else (1 if (nr | ng | nb) != 0 else 0)
                        if weight > 0:
                            wr += nr * weight
                            wg += ng * weight
                            wb += nb * weight
                            wt += weight
                if wt > 0:
                    updates.append((x, y, (round(wr / wt), round(wg / wt), round(wb / wt), 0)))
        if not updates:
            break
        dst = out.load()
        for x, y, rgba in updates:
            dst[x, y] = rgba
    return out


def main():
    svg_text = square_svg((ROOT / "branding/aurora.svg").read_text(encoding="utf-8"))
    for size in TIERS:
        raster = Image.open(BytesIO(resvg_py.svg_to_bytes(
            svg_string=svg_text, width=size, height=size))).convert("RGBA")
        if raster.size != (size, size):
            canvas = Image.new("RGBA", (size, size))
            canvas.alpha_composite(raster, ((size - raster.width) // 2, (size - raster.height) // 2))
            raster = canvas
        raster = bleed_edge_rgb(raster)
        out_path = UI_DIR / tier_filename(size)
        raster.save(out_path, optimize=True)
        out_path.with_name(out_path.name + ".mcmeta").write_text(MCMETA, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
