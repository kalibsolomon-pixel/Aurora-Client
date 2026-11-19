"""Generate Aurora's mark and app icon from the owner's ribbon (2026-11-19).

Writes, under adapters/shared/src/client/resources/assets/aurora:
  branding/aurora.svg          the owner's 2026-11-02 ribbon with slightly rounded tips
  branding/aurora-icon.svg     the app icon: the dark Spxctra tile with that mark
  icon.png                     the 1024 px mod icon, rasterized from aurora-icon.svg
  textures/ui/aurora_mark.png  the title-screen mark (prepare_brand.py)

The ribbon is the original, unchanged, except its two needle points: each is
cut where the ribbon is 2 * RADIUS wide and closed with a half circle, so the shape
and colours stay the owner's and each point ends in a round tip (owner, 2026-11-19).

Run from the repo root: python tools/gen_aurora_brand.py   (needs resvg-py and Pillow)
"""
import math
from io import BytesIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "adapters/shared/src/client/resources/assets/aurora"

RADIUS = 9.0  # radius that rounds each point (640x512 units)

SILHOUETTE = ("M32 452 C132 235 216 123 311 115 C406 109 452 226 497 231 C539 236 573 167 604 72 "
              "C582 288 516 438 420 399 C358 374 330 303 269 302 C198 300 127 377 32 452Z")

# The ribbon's two edges, each from the lower-left point to the upper-right point.
TOP_EDGE = [((32, 452), (132, 235), (216, 123), (311, 115)),
            ((311, 115), (406, 109), (452, 226), (497, 231)),
            ((497, 231), (539, 236), (573, 167), (604, 72))]
BOTTOM_EDGE = [((32, 452), (127, 377), (198, 300), (269, 302)),
               ((269, 302), (330, 303), (358, 374), (420, 399)),
               ((420, 399), (516, 438), (582, 288), (604, 72))]


def sample(segments, per=400):
    pts = []
    for p0, p1, p2, p3 in segments:
        for k in range(per):
            t = k / per
            u = 1 - t
            pts.append(tuple(u ** 3 * p0[j] + 3 * u * u * t * p1[j] + 3 * u * t * t * p2[j] + t ** 3 * p3[j]
                             for j in range(2)))
    pts.append(segments[-1][3])
    return pts


def at_arc(points, distance):
    """The point distance along a polyline from its start."""
    run = 0.0
    for a, b in zip(points, points[1:]):
        step = math.dist(a, b)
        if run + step >= distance:
            u = (distance - run) / (step or 1.0)
            return (a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u)
        run += step
    return points[-1]


def round_tip(top, bottom, r):
    """Cut a point where the ribbon is 2r wide and close it with a half circle.

    top and bottom run from the point inward. Returns the circle's centre and the region
    beyond the cut (to remove); the half circle lies inside the original shape, so its
    colours are the original layers'."""
    s = 0.0
    while True:
        s += 0.25
        a, b = at_arc(top, s), at_arc(bottom, s)
        if math.dist(a, b) >= 2 * r:
            break
    centre = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    chord = ((b[0] - a[0]) / (2 * r), (b[1] - a[1]) / (2 * r))
    out = (top[0][0] - centre[0], top[0][1] - centre[1])
    ln = math.hypot(*out)
    out = (out[0] / ln, out[1] / ln)
    w, far = 60.0, 400.0
    quad = [(a[0] - chord[0] * w, a[1] - chord[1] * w), (b[0] + chord[0] * w, b[1] + chord[1] * w)]
    quad += [(quad[1][0] + out[0] * far, quad[1][1] + out[1] * far), (quad[0][0] + out[0] * far, quad[0][1] + out[1] * far)]
    return centre, "M" + " L".join("%.2f %.2f" % q for q in quad) + "Z"


_TOP, _BOTTOM = sample(TOP_EDGE), sample(BOTTOM_EDGE)
CUTS = [round_tip(_TOP, _BOTTOM, RADIUS), round_tip(_TOP[::-1], _BOTTOM[::-1], RADIUS)]
MASK = "".join('<path d="%s" fill="#000"/><circle cx="%.2f" cy="%.2f" r="%.2f" fill="#fff"/>' % (cut, c[0], c[1], RADIUS)
               for c, cut in CUTS)

# The owner's 2026-11-02 ribbon (aurora-refined.svg), unchanged except that its clip is
# now a mask with the two points rounded.
GROUP = """<defs>
<linearGradient id="upper" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#138aab"/><stop offset=".32" stop-color="#27e3d2"/><stop offset=".65" stop-color="#65dce8"/><stop offset="1" stop-color="#a3c1ff"/></linearGradient>
<linearGradient id="lower" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#2d4ed1"/><stop offset=".48" stop-color="#5d5ce8"/><stop offset="1" stop-color="#b17bfa"/></linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#d8ffff" stop-opacity=".8"/><stop offset="1" stop-color="#a1d9ff" stop-opacity="0"/></linearGradient>
<mask id="silhouette" maskUnits="userSpaceOnUse" x="0" y="0" width="640" height="512"><path d="{s}" fill="#fff"/>{mask}</mask></defs>
<g mask="url(#silhouette)">
<path d="{s}" fill="url(#lower)"/>
<path d="M32 452 C136 349 211 286 269 302 C330 303 358 374 420 399 C516 438 582 288 604 72 C560 334 508 397 442 364 C380 332 355 262 291 255 C214 247 137 345 32 452Z" fill="#172c8e" opacity=".18"/>
<path d="M32 452 C132 235 216 123 311 115 C406 109 452 226 497 231 C539 236 573 167 604 72 C574 266 524 342 449 311 C383 283 361 199 294 200 C207 202 125 326 32 452Z" fill="url(#upper)"/>
<path d="M32 452 C132 235 216 123 311 115 C406 109 452 226 497 231 C539 236 573 167 604 72 C553 211 517 269 475 262 C417 251 384 165 311 157 C214 145 129 303 32 452Z" fill="url(#edge)" opacity=".28"/>
</g>""".format(s=SILHOUETTE, mask=MASK)

# Content bounds of the ribbon, including the outline.
X0, Y0, X1, Y1 = 32, 72, 604, 452


def tile_path():
    """The Spxctra app-icon squircle (superellipse, n = 5) on the 1024 canvas, 40 px margin."""
    pts = []
    for k in range(256):
        a = 2 * math.pi * k / 256
        c, s = math.cos(a), math.sin(a)
        pts.append((512 + 472 * math.copysign(abs(c) ** 0.4, c), 512 + 472 * math.copysign(abs(s) ** 0.4, s)))
    return "M" + " L".join("%.1f %.1f" % p for p in pts) + "Z"


def main():
    mark = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 512" role="img" '
            'aria-label="Aurora continuous teal and violet ribbon logo">\n%s\n</svg>\n' % GROUP)
    (ROOT / "branding/aurora.svg").write_text(mark, encoding="utf-8", newline="\n")

    # App icon: the ribbon fills 78% of the tile's width, centred optically (a touch above centre).
    scale = 0.78 * 944 / (X1 - X0)
    tx = 512 - (X0 + X1) / 2 * scale
    ty = 500 - (Y0 + Y1) / 2 * scale
    icon = ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024" role="img" '
            'aria-label="Aurora app icon">\n<defs><linearGradient id="surface" x1="0" y1="0" x2="0" y2="1">'
            '<stop stop-color="#25262e"/><stop offset="1" stop-color="#060609"/></linearGradient></defs>\n'
            '<path d="%s" fill="url(#surface)"/>\n<g transform="translate(%.1f %.1f) scale(%.4f)">\n%s\n</g>\n</svg>\n'
            % (tile_path(), tx, ty, scale, GROUP))
    (ROOT / "branding/aurora-icon.svg").write_text(icon, encoding="utf-8", newline="\n")

    from PIL import Image
    import resvg_py
    png = Image.open(BytesIO(resvg_py.svg_to_bytes(svg_path=str(ROOT / "branding/aurora-icon.svg"), width=1024)))
    png.convert("RGBA").save(ROOT / "icon.png")

    import prepare_brand
    prepare_brand.main()


if __name__ == "__main__":
    main()
