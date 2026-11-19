"""Generate Aurora's mark and app icon from the owner's ribbon (2026-11-19).

Writes, under adapters/shared/src/client/resources/assets/aurora:
  branding/aurora.svg          the mark alone, on a viewport fitted to it
  branding/aurora-icon.svg     the app icon: the dark Spxctra tile with the mark
  icon.png                     the 1024 px mod icon, rasterized from aurora-icon.svg
  textures/ui/aurora_mark.png  the title-screen mark (prepare_brand.py)

The shape and colors are the owner's 2026-11-02 ribbon: a teal upper band and a
violet lower band with their original gradients, fold and edge light. Two things
change (owner, 2026-11-19):
  - the needle points are cut where the ribbon narrows to END_WIDTH and closed
    with round caps, so small icons keep both ends instead of losing them;
  - each band carries smooth bevel shading over its gradient: lit along its upper
    side, shaded along its lower side, a polished-gem feel with no hard facets.

Run from the repo root: python tools/gen_aurora_brand.py   (needs resvg-py and Pillow)
"""
import math
from io import BytesIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "adapters/shared/src/client/resources/assets/aurora"

# ---- The original ribbon, as cubic Bezier segments (640x512 space), tip to tip ----
TOP_EDGE = [((32, 452), (132, 235), (216, 123), (311, 115)),
            ((311, 115), (406, 109), (452, 226), (497, 231)),
            ((497, 231), (539, 236), (573, 167), (604, 72))]
BOTTOM_EDGE = [((32, 452), (127, 377), (198, 300), (269, 302)),
               ((269, 302), (330, 303), (358, 374), (420, 399)),
               ((420, 399), (516, 438), (582, 288), (604, 72))]
BAND_SPLIT = [((32, 452), (125, 326), (207, 202), (294, 200)),       # lower edge of the teal band
              ((294, 200), (361, 199), (383, 283), (449, 311)),
              ((449, 311), (524, 342), (574, 266), (604, 72))]
FOLD = [((32, 452), (137, 345), (214, 247), (291, 255)),             # the dark fold inside the violet band
        ((291, 255), (355, 262), (380, 332), (442, 364)),
        ((442, 364), (508, 397), (560, 334), (604, 72))]
GLOSS = [((32, 452), (129, 303), (214, 145), (311, 157)),            # the lower edge of the top-edge light
         ((311, 157), (384, 165), (417, 251), (475, 262)),
         ((475, 262), (517, 269), (553, 211), (604, 72))]

END_WIDTH = 34.0     # the ribbon never gets thinner than this; its ends are capped round
SOFTNESS = 3.0       # how gently the minimum width blends into the original taper
BLEND = 2.5          # the original points return fully once the ribbon is END_WIDTH * (1 + BLEND) wide
SAMPLES = 420

DEFS = """<linearGradient id="upper" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#138aab"/><stop offset=".32" stop-color="#27e3d2"/><stop offset=".65" stop-color="#65dce8"/><stop offset="1" stop-color="#a3c1ff"/></linearGradient>
<linearGradient id="lower" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#2d4ed1"/><stop offset=".48" stop-color="#5d5ce8"/><stop offset="1" stop-color="#b17bfa"/></linearGradient>
<linearGradient id="edge" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#d8ffff" stop-opacity=".8"/><stop offset="1" stop-color="#a1d9ff" stop-opacity="0"/></linearGradient>
<linearGradient id="lit" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#ffffff" stop-opacity=".9"/><stop offset="1" stop-color="#ffffff" stop-opacity="0"/></linearGradient>
<linearGradient id="shade" x1="0" y1="1" x2="0" y2="0"><stop stop-color="#0b1660" stop-opacity=".9"/><stop offset="1" stop-color="#0b1660" stop-opacity="0"/></linearGradient>"""


def bezier(segments, per=120):
    pts = []
    for p0, p1, p2, p3 in segments:
        for k in range(per):
            t = k / per
            u = 1 - t
            pts.append(tuple(u ** 3 * p0[j] + 3 * u * u * t * p1[j] + 3 * u * t * t * p2[j] + t ** 3 * p3[j]
                             for j in range(2)))
    pts.append(segments[-1][3])
    return pts


def resample(poly, count=SAMPLES):
    d = [0.0]
    for a, b in zip(poly, poly[1:]):
        d.append(d[-1] + math.dist(a, b))
    out, j = [], 0
    for i in range(count):
        target = d[-1] * i / (count - 1)
        while j < len(d) - 2 and d[j + 1] < target:
            j += 1
        u = (target - d[j]) / ((d[j + 1] - d[j]) or 1.0)
        a, b = poly[j], poly[j + 1]
        out.append((a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u))
    return out


def lerp(a, b, u):
    return (a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u)


def cap(a, b, outward, steps=16):
    """Half circle from a to b (bottom edge to top edge), bulging toward outward; excludes a and b."""
    c = lerp(a, b, 0.5)
    r = math.dist(a, b) / 2
    a0 = math.atan2(a[1] - c[1], a[0] - c[0])
    probe = (math.cos(a0 + math.pi / 2), math.sin(a0 + math.pi / 2))
    sign = 1 if probe[0] * outward[0] + probe[1] * outward[1] > 0 else -1
    return [(c[0] + r * math.cos(a0 + sign * math.pi * k / steps), c[1] + r * math.sin(a0 + sign * math.pi * k / steps))
            for k in range(1, steps)]


def blunt(lines):
    """Widen the ribbon where it is thinner than END_WIDTH, keeping its length and every
    line's place across the width, so only the tips change. lines[0] is the top edge,
    lines[1] the bottom edge; the rest sit between them."""
    top, bottom = lines[0], lines[1]
    count = len(top)
    mid = [lerp(b, t, 0.5) for t, b in zip(top, bottom)]
    normals = []
    for i in range(count):
        a, b = mid[max(i - 1, 0)], mid[min(i + 1, count - 1)]
        ln = math.dist(a, b) or 1.0
        nx, ny = (b[1] - a[1]) / ln, -(b[0] - a[0]) / ln
        # point the normal toward the top edge
        probe = next((j for j in range(count) if math.dist(top[j], bottom[j]) > 2), i)
        if (top[probe][0] - bottom[probe][0]) * nx + (top[probe][1] - bottom[probe][1]) * ny < 0:
            nx, ny = -nx, -ny
        normals.append((nx, ny))
    # Each line's fraction across the width, carried to the tips from where it is defined.
    fractions = []
    for line in lines:
        frac = [None] * count
        for i in range(count):
            w = math.dist(top[i], bottom[i])
            if w > 6:
                frac[i] = math.dist(line[i], bottom[i]) / w
        last = next(f for f in frac if f is not None)
        for i in range(count):
            last = frac[i] = frac[i] if frac[i] is not None else last
        for i in reversed(range(count)):
            if math.dist(top[i], bottom[i]) > 6:
                break
        fractions.append(frac)
    out = [[] for _ in lines]
    for i in range(count):
        w = math.dist(top[i], bottom[i])
        wide = (w ** SOFTNESS + END_WIDTH ** SOFTNESS) ** (1 / SOFTNESS)
        nx, ny = normals[i]
        # Only the thin ends change: where the ribbon is already wide the original points stay.
        g = max(0.0, min(1.0, (w - END_WIDTH) / (BLEND * END_WIDTH)))
        g = g * g * (3 - 2 * g)
        for k in range(len(lines)):
            c = -wide / 2 + fractions[k][i] * wide
            widened = (mid[i][0] + nx * c, mid[i][1] + ny * c)
            out[k].append(lerp(widened, lines[k][i], g))
    return out


def path(points):
    return "M" + " L".join("%.1f %.1f" % p for p in points) + "Z"


def build():
    top, bottom = resample(bezier(TOP_EDGE)), resample(bezier(BOTTOM_EDGE))
    split, fold, gloss = resample(bezier(BAND_SPLIT)), resample(bezier(FOLD)), resample(bezier(GLOSS))
    top, bottom, split, fold, gloss = blunt([top, bottom, split, fold, gloss])
    n = len(top) - 1
    mid = [lerp(b, t, 0.5) for t, b in zip(top, bottom)]

    def outward(i, j):
        a, b = mid[i], mid[j]
        ln = math.dist(a, b) or 1.0
        return ((b[0] - a[0]) / ln, (b[1] - a[1]) / ln)

    start = cap(bottom[0], top[0], outward(6, 0))
    end = cap(bottom[n], top[n], outward(n - 6, n))
    silhouette = bottom + end + list(reversed(top)) + list(reversed(start))

    def cap_share(points, b, t, s):
        """The part of a cap (bottom -> top) beside the top edge, for a band reaching s of the width down."""
        share = max(0.0, min(1.0, math.dist(s, t) / (math.dist(b, t) or 1.0)))
        keep = int(round(len(points) * share))
        return points[len(points) - keep:] if keep else []

    # The teal band: from the top edge down to the band split, its caps continuing the share it has at each cut.
    upper_band = (list(top)
                  + list(reversed(cap_share(end, bottom[n], top[n], split[n])))
                  + list(reversed(split))
                  + cap_share(start, bottom[0], top[0], split[0]))
    fold_band = list(fold) + list(reversed(bottom))
    gloss_band = list(top) + list(reversed(gloss))

    def strip(edge_a, edge_b, f0, f1):
        """The part of a band between fractions f0 and f1 of its width (0 = edge_a)."""
        a = [lerp(x, y, f0) for x, y in zip(edge_a, edge_b)]
        b = [lerp(x, y, f1) for x, y in zip(edge_a, edge_b)]
        return a + list(reversed(b))

    # Smooth bevel shading, no hard facets: each band is lit along its upper side and
    # falls into shadow along its lower side, so the ribbon reads as a polished, cut surface.
    upper_light = strip(top, split, 0.0, 0.55)
    upper_shade = strip(split, top, 0.0, 0.45)
    lower_light = strip(split, bottom, 0.0, 0.50)
    lower_shade = strip(bottom, split, 0.0, 0.50)
    return silhouette, upper_band, fold_band, gloss_band, upper_light, upper_shade, lower_light, lower_shade


def mark_group(parts, uid=""):
    silhouette, upper_band, fold_band, gloss_band, upper_light, upper_shade, lower_light, lower_shade = parts
    s = path(silhouette)
    return "\n".join([
        '<defs>%s' % DEFS.replace('id="', 'id="%s' % uid),
        '<clipPath id="%ssil"><path d="%s"/></clipPath>' % (uid, s),
        '<clipPath id="%sband"><path d="%s"/></clipPath></defs>' % (uid, path(upper_band)),
        '<g clip-path="url(#%ssil)">' % uid,
        '<path d="%s" fill="url(#%slower)"/>' % (s, uid),
        '<path d="%s" fill="#172c8e" opacity=".18"/>' % path(fold_band),
        '<path d="%s" fill="url(#%slit)" opacity=".30"/>' % (path(lower_light), uid),
        '<path d="%s" fill="url(#%sshade)" opacity=".34"/>' % (path(lower_shade), uid),
        '<path d="%s" fill="url(#%supper)"/>' % (path(upper_band), uid),
        '<g clip-path="url(#%sband)">' % uid,
        '<path d="%s" fill="url(#%slit)" opacity=".34"/>' % (path(upper_light), uid),
        '<path d="%s" fill="url(#%sshade)" opacity=".22"/>' % (path(upper_shade), uid),
        "</g>",
        '<path d="%s" fill="url(#%sedge)" opacity=".28"/>' % (path(gloss_band), uid),
        "</g>",
    ])


def bounds(points):
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def tile_path():
    """The Spxctra app-icon squircle (superellipse, n = 5) on the 1024 canvas, 40 px margin."""
    pts = []
    for k in range(256):
        a = 2 * math.pi * k / 256
        c, s = math.cos(a), math.sin(a)
        pts.append((512 + 472 * math.copysign(abs(c) ** 0.4, c), 512 + 472 * math.copysign(abs(s) ** 0.4, s)))
    return path(pts)


def main():
    parts = build()
    x0, y0, x1, y1 = bounds(parts[0])
    pad = 8
    mark = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%.1f %.1f %.1f %.1f" role="img" '
            'aria-label="Aurora teal and violet ribbon logo">\n%s\n</svg>\n'
            % (x0 - pad, y0 - pad, x1 - x0 + 2 * pad, y1 - y0 + 2 * pad, mark_group(parts)))
    (ROOT / "branding/aurora.svg").write_text(mark, encoding="utf-8", newline="\n")

    # App icon: the mark fills 78% of the tile's width, centred optically (a touch above centre).
    scale = 0.78 * 944 / (x1 - x0)
    tx = 512 - (x0 + x1) / 2 * scale
    ty = 500 - (y0 + y1) / 2 * scale
    icon = ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024" role="img" '
            'aria-label="Aurora app icon">\n<defs><linearGradient id="surface" x1="0" y1="0" x2="0" y2="1">'
            '<stop stop-color="#25262e"/><stop offset="1" stop-color="#060609"/></linearGradient></defs>\n'
            '<path d="%s" fill="url(#surface)"/>\n<g transform="translate(%.1f %.1f) scale(%.4f)">\n%s\n</g>\n</svg>\n'
            % (tile_path(), tx, ty, scale, mark_group(parts)))
    (ROOT / "branding/aurora-icon.svg").write_text(icon, encoding="utf-8", newline="\n")

    from PIL import Image
    import resvg_py
    png = Image.open(BytesIO(resvg_py.svg_to_bytes(svg_path=str(ROOT / "branding/aurora-icon.svg"), width=1024)))
    png.convert("RGBA").save(ROOT / "icon.png")

    import prepare_brand
    prepare_brand.main()


if __name__ == "__main__":
    main()
