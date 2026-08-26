"""Generate the World Map module icon (folded map + location pin) at 1536x1536.

Follows the exact convention of the other module_icons/*.png:
  - 1536x1536 transparent canvas
  - pure-white line art (no fill), antialiased via PIL
  - final icon downscaled + centered via the standard pipeline

Run:  python generate_world_map_icon.py
"""
import os
from PIL import Image, ImageDraw

CANVAS = 1536
LINE_W = 50  # base line width, matches sibling icons

def create_icon_canvas():
    return Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))

def save_image(img, name):
    out_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)))
    os.makedirs(out_dir, exist_ok=True)
    # Same 0.66 scale + center rule used by the sibling generator.
    factor = 0.90  # world map looks better slightly larger
    w, h = img.size
    sw = int(w * factor)
    sh = int(h * factor)
    scaled = img.resize((sw, sh), Image.Resampling.LANCZOS)
    final = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    final.paste(scaled, ((w - sw) // 2, (h - sh) // 2), scaled)
    path = os.path.join(out_dir, f"{name}.png")
    final.save(path, "PNG")
    print(f"Generated {path}")

def make_world_map():
    img = create_icon_canvas()
    draw = ImageDraw.Draw(img)
    WHITE = (255, 255, 255, 255)

    # --- Folded map body: a rounded rectangle with two horizontal fold creases ---
    # Body occupies roughly the lower 2/3 of the canvas.
    left, top, right, bottom = 330, 480, 1206, 1286
    draw.rounded_rectangle([left, top, right, bottom], radius=60,
                           outline=WHITE, width=LINE_W)

    # Fold creases (horizontal lines across the map body).
    crease1_y = top + (bottom - top) // 3
    crease2_y = top + 2 * (bottom - top) // 3
    draw.line([(left, crease1_y), (right, crease1_y)], fill=WHITE, width=34)
    draw.line([(left, crease2_y), (right, crease2_y)], fill=WHITE, width=34)

    # A couple of "terrain" contour lines inside the top fold for visual interest.
    cy = top + (crease1_y - top) // 2
    draw.arc([left + 140, cy - 70, left + 460, cy + 70], start=0, end=180,
             fill=WHITE, width=28)
    draw.arc([left + 560, cy - 90, left + 880, cy + 50], start=200, end=360,
             fill=WHITE, width=28)

    # --- Location pin (teardrop) sitting on the map, upper-right ---
    pin_cx, pin_cy = 940, 360
    pin_r = 150
    # Pin head: filled circle
    draw.ellipse([pin_cx - pin_r, pin_cy - pin_r,
                  pin_cx + pin_r, pin_cy + pin_r],
                 outline=WHITE, width=LINE_W)
    # Pin point: triangle down to the map
    draw.polygon([
        (pin_cx - pin_r + 30, pin_cy + pin_r - 20),
        (pin_cx + pin_r - 30, pin_cy + pin_r - 20),
        (pin_cx, pin_cy + pin_r + 180),
    ], outline=WHITE, width=LINE_W)
    # Inner dot
    draw.ellipse([pin_cx - 45, pin_cy - 45, pin_cx + 45, pin_cy + 45],
                 fill=WHITE)

    save_image(img, "world_map")

if __name__ == "__main__":
    make_world_map()
    print("World Map icon generated.")