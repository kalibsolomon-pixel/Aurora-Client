#!/usr/bin/env python3
"""Builds Aurora's bundled text fonts: every family at Regular, Medium,
SemiBold and Bold, plus italics where the family has them (2026-11-12).

Sources are Google Fonts' own files (github.com/google/fonts, SIL OFL 1.1).
Variable families are instanced at each weight with fontTools; static
families use the upstream file for that weight. Every output keeps only the
character ranges below and drops the layout and hinting tables: Minecraft's
TTF provider draws one glyph at a time (no shaping) at size x oversample
pixels, so neither affects what it renders.

Quicksand is the exception: its license reserves the name "Quicksand", and
an instanced or subset copy is a modified font that could not keep it, so
Quicksand ships Google's file unmodified (its default weight) and all four of
its weight providers point at it.

Writes <stem>-<weight>[-italic].ttf and the provider JSONs
<family>_<weight>[_italic][_ui].json into the font folder, and deletes the
pre-2026-11-12 single-weight files.

Usage (downloads are untrusted input; keep them in their own folder):
    python -I tools/build_fonts.py --download build/fonts/download
    python -I tools/build_fonts.py --build build/fonts/download
"""
import argparse
import json
import pathlib
import sys
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
FONT_DIR = ROOT / "adapters/shared/src/client/resources/assets/aurora/font"
RAW = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

WEIGHTS = {"regular": 400, "medium": 500, "semibold": 600, "bold": 700}
STATIC_NAMES = {"regular": "Regular", "medium": "Medium", "semibold": "SemiBold", "bold": "Bold"}

# provider-id stem, file stem, google/fonts folder, upright source, italic source.
# A source is either a variable file name or "static:<Prefix>" for per-weight files.
FAMILIES = [
    ("inter", "inter", "inter", "Inter[opsz,wght].ttf", "Inter-Italic[opsz,wght].ttf"),
    ("jetbrains_mono", "jetbrains-mono", "jetbrainsmono", "JetBrainsMono[wght].ttf", "JetBrainsMono-Italic[wght].ttf"),
    ("roboto", "roboto", "roboto", "Roboto[wdth,wght].ttf", "Roboto-Italic[wdth,wght].ttf"),
    ("poppins", "poppins", "poppins", "static:Poppins", "static:Poppins"),
    ("oswald", "oswald", "oswald", "Oswald[wght].ttf", None),
    ("nunito", "nunito", "nunito", "Nunito[wght].ttf", "Nunito-Italic[wght].ttf"),
    ("quicksand", "quicksand", "quicksand", "Quicksand[wght].ttf", None),
    ("fira_sans", "fira-sans", "firasans", "static:FiraSans", "static:FiraSans"),
]

# Latin, Greek, Cyrillic and the punctuation/symbol blocks the old files covered.
RANGES = [(0x0020, 0x024F), (0x0250, 0x02FF), (0x0300, 0x036F), (0x0370, 0x03FF), (0x0400, 0x052F),
          (0x1E00, 0x1EFF), (0x1F00, 0x1FFF), (0x2000, 0x206F), (0x2070, 0x209F), (0x20A0, 0x20CF),
          (0x2100, 0x218F), (0x2190, 0x21FF), (0x2200, 0x22FF), (0x2300, 0x23FF), (0x2500, 0x25FF),
          (0x2C60, 0x2C7F), (0xA720, 0xA7FF), (0xFB00, 0xFB06)]

# Minecraft provider sizes: in-world/regular text, and the compact windowed-screen variant.
PROVIDER_SIZES = {"": 9.0, "_ui": 8.0}
OVERSAMPLE = 8.0

# Shipped as the unmodified upstream file (Reserved Font Name; see the docstring).
UNMODIFIED = {"quicksand": ("Quicksand[wght].ttf", "quicksand-variable.ttf")}

OLD_FILES = ["inter-frozen-medium.ttf", "inter-frozen-medium-italic.ttf", "fira-sans-medium.ttf",
             "jetbrains-mono-medium.ttf", "poppins-medium.ttf", "roboto-variable.ttf",
             "oswald-variable.ttf", "nunito-variable.ttf"]
OLD_JSON = ["inter", "inter_italic", "jetbrains_mono", "roboto", "poppins", "oswald", "nunito",
            "quicksand", "fira_sans"]


def source_files(family):
    _, _, folder, upright, italic = family
    out = []
    for src, is_italic in ((upright, False), (italic, True)):
        if src is None:
            continue
        if src.startswith("static:"):
            prefix = src[len("static:"):]
            for w in WEIGHTS:
                name = STATIC_NAMES[w]
                if is_italic:
                    name = "Italic" if w == "regular" else name + "Italic"
                out.append(f"{prefix}-{name}.ttf")
        else:
            out.append(src)
    return folder, sorted(set(out)) + ["OFL.txt"]


def download(dest):
    dest.mkdir(parents=True, exist_ok=True)
    for family in FAMILIES:
        folder, files = source_files(family)
        (dest / folder).mkdir(exist_ok=True)
        for name in files:
            target = dest / folder / name
            if target.exists():
                continue
            url = RAW + folder + "/" + urllib.parse.quote(name)
            print("GET", url)
            with urllib.request.urlopen(url, timeout=60) as r:
                target.write_bytes(r.read())


def unicodes():
    out = []
    for lo, hi in RANGES:
        out.extend(range(lo, hi + 1))
    return out


def build(src_dir):
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer
    from fontTools import subset

    for f in OLD_FILES:
        (FONT_DIR / f).unlink(missing_ok=True)
    for stem in OLD_JSON:
        for suffix in PROVIDER_SIZES:
            (FONT_DIR / f"{stem}{suffix}.json").unlink(missing_ok=True)

    options = subset.Options()
    options.layout_features = []
    options.hinting = False
    options.drop_tables += ["GSUB", "GPOS", "GDEF", "kern", "STAT", "DSIG", "MVAR", "HVAR", "avar", "fvar", "gvar"]
    options.name_IDs = [0, 1, 2, 3, 4, 5, 6, 13, 14]
    options.notdef_outline = True
    keep = unicodes()
    total = 0

    for provider, stem, folder, upright, italic in FAMILIES:
        if provider in UNMODIFIED:
            source, shipped = UNMODIFIED[provider]
            (FONT_DIR / shipped).write_bytes((src_dir / folder / source).read_bytes())
            total += (FONT_DIR / shipped).stat().st_size
            for weight in WEIGHTS:
                for suffix, size in PROVIDER_SIZES.items():
                    write_provider(FONT_DIR / f"{provider}_{weight}{suffix}.json", shipped, size)
            print(f"{shipped}: unmodified")
            continue
        for src, is_italic in ((upright, False), (italic, True)):
            if src is None:
                continue
            for weight, wght in WEIGHTS.items():
                if src.startswith("static:"):
                    name = STATIC_NAMES[weight]
                    if is_italic:
                        name = "Italic" if weight == "regular" else name + "Italic"
                    font = TTFont(src_dir / folder / f"{src[len('static:'):]}-{name}.ttf")
                else:
                    font = TTFont(src_dir / folder / src)
                    axes = {a.axisTag: a.defaultValue for a in font["fvar"].axes}
                    axes["wght"] = wght
                    font = instancer.instantiateVariableFont(font, axes, updateFontNames=False)
                sub = subset.Subsetter(options)
                sub.populate(unicodes=keep)
                sub.subset(font)
                out_name = f"{stem}-{weight}{'-italic' if is_italic else ''}.ttf"
                font.save(FONT_DIR / out_name)
                total += (FONT_DIR / out_name).stat().st_size
                provider_id = f"{provider}_{weight}{'_italic' if is_italic else ''}"
                for suffix, size in PROVIDER_SIZES.items():
                    write_provider(FONT_DIR / f"{provider_id}{suffix}.json", out_name, size)
                print(f"{out_name}: {(FONT_DIR / out_name).stat().st_size // 1024} KiB")
    print(f"total {total / 1024 / 1024:.2f} MiB")


def write_provider(path, ttf, size):
    data = {"providers": [
        {"type": "ttf", "file": f"aurora:{ttf}", "shift": [0, 0], "size": size, "oversample": OVERSAMPLE},
        {"type": "reference", "id": "minecraft:default"},
    ]}
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--download", type=pathlib.Path)
    parser.add_argument("--build", type=pathlib.Path)
    args = parser.parse_args()
    if args.download:
        download(args.download)
    if args.build:
        build(args.build)
    if not args.download and not args.build:
        parser.print_help()
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
