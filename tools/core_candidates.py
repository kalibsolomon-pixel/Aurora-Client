"""List client classes that could move into the Minecraft-free core module.

A class qualifies when it imports nothing from Minecraft, Mojang, Fabric, LWJGL or
Mixin, and every Aurora class it refers to also qualifies. Same-package references
need no import, so they are found by matching simple class names in the source.

Usage: python tools/core_candidates.py [package ...]
"""

from source_tree import client_path
import os
import re
import sys

HERE = os.path.dirname(__file__)
ROOTS = [str(client_path("java")), os.path.join(HERE, "..", "core", "src", "main", "java"), os.path.join(HERE, "..", "ports", "src", "main", "java")]
ROOTS.append(os.path.join(HERE, '..', 'adapters', 'shared', 'src', 'client', 'java'))
FORBIDDEN = re.compile(
    r"^import\s+(static\s+)?(net\.minecraft|com\.mojang|net\.fabricmc|org\.lwjgl"
    r"|org\.spongepowered|com\.llamalad7|com\.terraformersmc|org\.apache\.logging)\b",
    re.M,
)
# The same packages written out in full, with no import. Log4j is the game's logging
# backend; the core logs through SLF4J only.
QUALIFIED = re.compile(
    r"\b(net\.minecraft|com\.mojang|net\.fabricmc|org\.lwjgl|org\.spongepowered"
    r"|org\.apache\.logging)\.\w"
)
IMPORT = re.compile(r"^import\s+(?:static\s+)?(com\.aurora\.client\.[\w.]+?)(?:\.\*|\.[a-z]\w*)?;", re.M)


def load():
    classes = {}
    for root in ROOTS:
        for dirpath, _, files in os.walk(root):
            for f in files:
                if f.endswith(".java"):
                    path = os.path.join(dirpath, f)
                    rel = os.path.relpath(path, root)[:-5].replace(os.sep, ".")
                    with open(path, encoding="utf-8") as fh:
                        classes[rel] = fh.read()
    return classes


def strip_comments(src):
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    return re.sub(r'"(?:\\.|[^"\\])*"', '""', src)


def deps(name, src, classes):
    pkg = name.rsplit(".", 1)[0]
    out = set()
    for m in IMPORT.finditer(src):
        target = m.group(1)
        while target and target not in classes and "." in target:
            target = target.rsplit(".", 1)[0]  # nested class import
        if target in classes:
            out.add(target)
    code = strip_comments(src)
    for other in classes:
        if other != name and other.rsplit(".", 1)[0] == pkg:
            simple = other.rsplit(".", 1)[1]
            # A nested type of the same name shadows the package class.
            if re.search(r"\b(class|record|enum|interface)\s+" + re.escape(simple) + r"\b", code):
                continue
            if re.search(r"\b" + re.escape(simple) + r"\b", code):
                out.add(other)
    # Fully qualified references with no import.
    for m in re.finditer(r"\bcom\.aurora\.client\.[\w.]+", code):
        target = m.group(0)
        while target and target not in classes and "." in target:
            target = target.rsplit(".", 1)[0]
        if target in classes and target != name:
            out.add(target)
    return out


def main():
    classes = load()
    graph = {n: deps(n, s, classes) for n, s in classes.items()}
    bad = {n for n, s in classes.items() if FORBIDDEN.search(s) or QUALIFIED.search(strip_comments(s))}
    reason = {n: "uses Minecraft/Fabric/LWJGL/Log4j" for n in bad}
    changed = True
    while changed:
        changed = False
        for n, ds in graph.items():
            if n in bad:
                continue
            hit = next((d for d in sorted(ds) if d in bad), None)
            if hit:
                bad.add(n)
                reason[n] = "uses " + hit
                changed = True
    prefixes = ["com.aurora.client." + p for p in sys.argv[1:]] or ["com.aurora.client"]
    for n in sorted(classes):
        if any(n == p or n.startswith(p + ".") for p in prefixes):
            print(("CORE  " if n not in bad else "NO    ") + n + ("" if n not in bad else "  (" + reason[n] + ")"))


if __name__ == "__main__":
    main()
