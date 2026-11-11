"""Check every mixin's target names against a Minecraft version, before booting it.

Usage: python tools/port/check_mixin_targets.py <mixin dir> "<client jar>;<common jar>"

Lists each method a mixin targets that its target class does not declare (any
overload), each @At INVOKE/FIELD target whose owner lacks the member, and each
@Invoker/@Accessor name that is gone. Unlike the boot report, this also covers
optional (require = 0) injectors, which fail silently at runtime. Names only:
descriptors and injection points still need a boot (MixinHealth reports them).
The jars are Loom's, under .gradle/loom-cache/minecraftMaven/net/minecraft/.
"""
import functools
import os
import re
import subprocess
import sys

MIXINS, CP = sys.argv[1], sys.argv[2]


@functools.lru_cache(maxsize=None)
def members(cls):
    out = subprocess.run(["javap", "-p", "-cp", CP, cls], capture_output=True, text=True).stdout
    if not out:
        return None
    names = set(re.findall(r"\s([\w$]+)\(", out))
    fields = set(re.findall(r"\s([\w$]+);\s*$", out, re.M))
    return names, fields


def resolve(simple, src):
    m = re.search(r"^import\s+([\w.]+\." + re.escape(simple) + r");", src, re.M)
    return m.group(1) if m else None


problems = 0
for dirpath, _, files in os.walk(MIXINS):
    for f in sorted(files):
        if not f.endswith(".java"):
            continue
        src = open(os.path.join(dirpath, f), encoding="utf-8").read()
        code = re.sub(r"/\*.*?\*/|//[^\n]*", "", src, flags=re.S)
        mixin = re.search(r"@Mixin\(\s*(?:value\s*=\s*)?\{?\s*([\w.]+)\.class", code)
        targets_str = re.search(r'@Mixin\(\s*targets\s*=\s*"([^"]+)"', code)
        if mixin:
            cls = resolve(mixin.group(1).split(".")[0], src) or mixin.group(1)
            if "." in mixin.group(1) and resolve(mixin.group(1).split(".")[0], src):
                cls = resolve(mixin.group(1).split(".")[0], src) + "$" + ".".join(mixin.group(1).split(".")[1:]).replace(".", "$")
        elif targets_str:
            cls = targets_str.group(1).replace("/", ".")
        else:
            continue
        info = members(cls)
        name = f[:-5]
        if info is None:
            print(f"{name}: target class {cls} not found")
            problems += 1
            continue
        methods, fields = info
        for m in re.finditer(r'\bmethod\s*=\s*(\{[^}]*\}|"[^"]*"|\w+)', code):
            raw = m.group(1)
            if raw.startswith("{") or raw.startswith('"'):
                values = re.findall(r'"([^"]+)"', raw)
            else:  # a constant
                const = re.search(r"\b" + raw + r'\s*=\s*"([^"]+)"', code)
                values = [const.group(1)] if const else []
            for v in values:
                mname = re.split(r"[(]", v.split(";")[-1] if v.startswith("L") and ";" in v else v)[0]
                if mname in ("<init>", "<clinit>"):
                    continue
                if mname not in methods:
                    print(f"{name}: {cls.split('.')[-1]} has no method '{mname}'")
                    problems += 1
        for t in re.finditer(r'\btarget\s*=\s*"L([\w/$]+);(\w+)', code):
            owner, member = t.group(1).replace("/", "."), t.group(2)
            oi = members(owner)
            if oi is None:
                print(f"{name}: INVOKE owner {owner} not found")
                problems += 1
            elif member not in oi[0] and member not in oi[1]:
                print(f"{name}: {owner.split('.')[-1]} has no member '{member}' (an @At target)")
                problems += 1
        for inv in re.finditer(r'@(Invoker|Accessor)\("(\w+)"\)', code):
            if inv.group(2) not in methods and inv.group(2) not in fields:
                print(f"{name}: {cls.split('.')[-1]} has no '{inv.group(2)}' for @{inv.group(1)}")
                problems += 1
print(problems, "problems")
