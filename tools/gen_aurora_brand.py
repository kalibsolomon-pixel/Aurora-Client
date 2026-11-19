"""Generate Aurora's mark and app icon: a faceted, round-ended ribbon (2026-11-19).

Writes, under adapters/shared/src/client/resources/assets/aurora:
  branding/aurora.svg          the mark alone, on a viewport fitted to it
  branding/aurora-icon.svg     the app icon: the dark Spxctra tile with the mark
  icon.png                     the 1024 px mod icon, rasterized from aurora-icon.svg
  textures/ui/aurora_mark.png  the title-screen mark (prepare_brand.py)

The ribbon keeps the original logo's two edge curves (the rising crest, the
trough and the lagging lower edge). Where the ribbon narrows toward each end it
is cut off and closed with a round cap instead of a needle point, so the mark
keeps a readable shape at 16-32 px. The shading is gem-like, after Nova
Studio's icon: flat triangular facets cut between the edges and a ridge, the
upper half in Aurora's teal band and the lower half in its violet band, the
facets alternating between catching and losing the light.

Run from the repo root: python tools/gen_aurora_brand.py   (needs resvg-py and Pillow)
"""
import math
from io import BytesIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "adapters/shared/src/client/resources/assets/aurora"

# ---- Shape: the original edges, as cubic Bezier segments (640x512 space) ----
UPPER_EDGE = [((32, 452), (132, 235), (216, 123), (311, 115)),
              ((311, 115), (406, 109), (452, 226), (497, 231)),
              ((497, 231), (539, 236), (573, 167), (604, 72))]
LOWER_EDGE = [((32, 452), (127, 377), (198, 300), (269, 302)),
              ((269, 302), (330, 303), (358, 374), (420, 399)),
              ((420, 399), (516, 438), (582, 288), (604, 72))]
THICKEN = 1.16       # push the lower edge away from the upper one: a bolder ribbon at small sizes
END_WIDTH = 46.0     # the ribbon is cut where it narrows to this, and capped round
RIDGE = 0.5          # ridge position across the width, 0 = lower edge, 1 = upper edge
FACETS = 8           # facet columns along the length
SAMPLES = 400

UPPER = ["#22c4cf", "#27e3d2", "#65dce8", "#a3c1ff"]   # teal band, start -> end
LOWER = ["#2d4ed1", "#4c56e0", "#5d5ce8", "#b17bfa"]   # violet band, start -> end
LIGHT, DARK = (0xd8, 0xff, 0xff), (0x12, 0x30, 0x8a)


def bezier(segments, count):
    pts = []
    for s, (p0, p1, p2, p3) in enumerate(segments):
        for k in range(count):
            t = k / count
            u = 1 - t
            pts.append(tuple(u ** 3 * p0[j] + 3 * u * u * t * p1[j] + 3 * u * t * t * p2[j] + t ** 3 * p3[j]
                             for j in range(2)))
    pts.append(segments[-1][3])
    return pts


def resample(poly, count):
    """count evenly spaced points by arc length."""
    d = [0.0]
    for a, b in zip(poly, poly[1:]):
        d.append(d[-1] + math.dist(a, b))
    out, j = [], 0
    for i in range(count):
        target = d[-1] * i / (count - 1)
        while j < len(d) - 2 and d[j + 1] < target:
            j += 1
        seg = (d[j + 1] - d[j]) or 1.0
        u = (target - d[j]) / seg
        a, b = poly[j], poly[j + 1]
        out.append((a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u))
    return out


def lerp(a, b, u):
    return (a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u)


def mix(c1, c2, u):
    return tuple(round(c1[i] + (c2[i] - c1[i]) * u) for i in range(3))


def hexrgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def ramp(stops, t):
    stops = [hexrgb(s) for s in stops]
    x = max(0.0, min(1.0, t)) * (len(stops) - 1)
    i = min(int(x), len(stops) - 2)
    return mix(stops[i], stops[i + 1], x - i)


def css(c):
    return "#%02x%02x%02x" % c


def cap(a, b, outward):
    """Half circle from a to b (the ribbon's two edges), bulging toward outward."""
    c = lerp(a, b, 0.5)
    r = math.dist(a, b) / 2
    a0 = math.atan2(a[1] - c[1], a[0] - c[0])
    # choose the sweep direction whose midpoint lies on the outward side
    mid_ccw = (c[0] + r * math.cos(a0 + math.pi / 2), c[1] + r * math.sin(a0 + math.pi / 2))
    sign = 1 if (mid_ccw[0] - c[0]) * outward[0] + (mid_ccw[1] - c[1]) * outward[1] > 0 else -1
    return [(c[0] + r * math.cos(a0 + sign * math.pi * k / 14), c[1] + r * math.sin(a0 + sign * math.pi * k / 14))
            for k in range(1, 14)]


def build():
    top = resample(bezier(UPPER_EDGE, 120), SAMPLES)
    bottom = resample(bezier(LOWER_EDGE, 120), SAMPLES)
    bottom = [lerp(t, b, THICKEN) for t, b in zip(top, bottom)]
    widths = [math.dist(t, b) for t, b in zip(top, bottom)]
    first = next(i for i, w in enumerate(widths) if w >= END_WIDTH)
    last = len(widths) - 1 - next(i for i, w in enumerate(reversed(widths)) if w >= END_WIDTH)
    top, bottom = top[first:last + 1], bottom[first:last + 1]
    n = len(top) - 1
    ridge = [lerp(b, t, RIDGE) for t, b in zip(top, bottom)]

    def direction(i, j):
        a, b = ridge[i], ridge[j]
        ln = math.dist(a, b) or 1.0
        return ((b[0] - a[0]) / ln, (b[1] - a[1]) / ln)

    start_cap = cap(bottom[0], top[0], direction(4, 0))     # bottom[0] -> top[0]
    end_cap = cap(bottom[n], top[n], direction(n - 4, n))   # bottom[n] -> top[n]
    silhouette = bottom + end_cap + list(reversed(top)) + list(reversed(start_cap))

    # Facet columns by arc length along the ridge.
    cols = [round(n * f / FACETS) for f in range(FACETS + 1)]
    facets = []
    for f in range(FACETS):
        i0, i1 = cols[f], cols[f + 1]
        im = (i0 + i1) // 2
        t = (f + 0.5) / FACETS
        up, low = ramp(UPPER, t), ramp(LOWER, t)
        even = f % 2 == 0
        # Two triangles per half, split corner to corner, for a cut-stone look.
        upper_a = top[i0:im + 1] + [ridge[i0]]
        upper_b = top[im:i1 + 1] + list(reversed(ridge[i0:i1 + 1]))
        lower_a = ridge[i0:i1 + 1] + [bottom[im]]
        lower_b = bottom[i0:im + 1] + [ridge[i0]]
        lower_c = bottom[im:i1 + 1] + [ridge[i1]]
        facets.append((upper_a, css(mix(up, LIGHT, 0.44 if even else 0.22))))
        facets.append((upper_b, css(mix(up, LIGHT, 0.10 if even else 0.36))))
        facets.append((lower_a, css(mix(low, DARK, 0.02 if even else 0.18))))
        facets.append((lower_b, css(mix(low, DARK, 0.22 if even else 0.34))))
        facets.append((lower_c, css(mix(low, DARK, 0.36 if even else 0.08))))
    # The caps continue their neighbouring halves.
    half = len(start_cap) // 2
    facets.append(([ridge[0]] + start_cap[half:] + [top[0]], css(mix(ramp(UPPER, 0), LIGHT, 0.44))))
    facets.append(([bottom[0]] + start_cap[:half + 1] + [ridge[0]], css(mix(ramp(LOWER, 0), DARK, 0.22))))
    facets.append(([ridge[n]] + end_cap[half:] + [top[n]], css(mix(ramp(UPPER, 1), LIGHT, 0.36))))
    facets.append(([bottom[n]] + end_cap[:half + 1] + [ridge[n]], css(mix(ramp(LOWER, 1), DARK, 0.08))))
    return silhouette, facets


def path(points):
    return "M" + " L".join("%.1f %.1f" % p for p in points) + "Z"


def bounds(points):
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def mark_group(silhouette, facets):
    # Facets are clipped to the silhouette so the caps and edges stay one clean outline;
    # each facet is stroked in its own colour so antialiasing leaves no seams between them.
    out = ['<clipPath id="ribbon"><path d="%s"/></clipPath>' % path(silhouette),
           '<g clip-path="url(#ribbon)">']
    for pts, color in facets:
        out.append('<path d="%s" fill="%s" stroke="%s" stroke-width="1.5" stroke-linejoin="round"/>'
                   % (path(pts), color, color))
    out.append("</g>")
    return "\n".join(out)


def tile_path():
    """The Spxctra app-icon squircle (superellipse, n = 5) on the 1024 canvas, 40 px margin."""
    pts = []
    for k in range(256):
        a = 2 * math.pi * k / 256
        c, s = math.cos(a), math.sin(a)
        pts.append((512 + 472 * math.copysign(abs(c) ** 0.4, c), 512 + 472 * math.copysign(abs(s) ** 0.4, s)))
    return path(pts)


def main():
    silhouette, facets = build()
    group = mark_group(silhouette, facets)
    x0, y0, x1, y1 = bounds(silhouette)
    pad = 8
    mark = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="%.1f %.1f %.1f %.1f" role="img" '
            'aria-label="Aurora faceted teal and violet ribbon logo">\n%s\n</svg>\n'
            % (x0 - pad, y0 - pad, x1 - x0 + 2 * pad, y1 - y0 + 2 * pad, group))
    (ROOT / "branding/aurora.svg").write_text(mark, encoding="utf-8", newline="\n")

    # App icon: the mark fills 78% of the tile's width, centred optically (a touch above centre).
    scale = 0.78 * 944 / (x1 - x0)
    tx = 512 - (x0 + x1) / 2 * scale
    ty = 500 - (y0 + y1) / 2 * scale
    icon = ('<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024" role="img" '
            'aria-label="Aurora app icon">\n<defs><linearGradient id="surface" x1="0" y1="0" x2="0" y2="1">'
            '<stop stop-color="#25262e"/><stop offset="1" stop-color="#060609"/></linearGradient></defs>\n'
            '<path d="%s" fill="url(#surface)"/>\n<g transform="translate(%.1f %.1f) scale(%.4f)">\n%s\n</g>\n</svg>\n'
            % (tile_path(), tx, ty, scale, group))
    (ROOT / "branding/aurora-icon.svg").write_text(icon, encoding="utf-8", newline="\n")

    from PIL import Image
    import resvg_py
    png = Image.open(BytesIO(resvg_py.svg_to_bytes(svg_path=str(ROOT / "branding/aurora-icon.svg"), width=1024)))
    png.convert("RGBA").save(ROOT / "icon.png")

    import prepare_brand
    prepare_brand.main()


if __name__ == "__main__":
    main()
