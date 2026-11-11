"""Summarize each matrix log: mods loaded, mixin health, Aurora warnings, and the three reports.

Usage: python -I tools/matrix/summarize.py <out dir>
"""
import re
import sys
from pathlib import Path

for log in sorted(Path(sys.argv[1]).glob("*-latest.log")):
    text = log.read_text(encoding="utf-8", errors="replace")
    print(f"\n### {log.name}")
    m = re.search(r"Loading \d+ mods:[\s\S]*?(?=\n\[)", text)
    if m:
        mods = re.findall(r"^\s+- (\S+ \S+)", m.group(0), re.M)
        print("mods:", ", ".join(x for x in mods if not x.startswith(("fabric-", "java ", "mixinextras", "minecraft"))))
    for line in re.findall(r".*(?:Aurora mixins applied|mixins are not working|was never applied|could not be applied).*", text)[:6]:
        print("  " + line.strip()[:200])
    errors = re.findall(r"^\[[^\]]+\] \[[^\]]+/(?:ERROR|WARN)\] \((?:Aurora|aurora[^)]*)\).*", text, re.M)
    print(f"  Aurora WARN/ERROR lines: {len(errors)}")
    for e in errors[:8]:
        print("   " + e[:220])
    mixin_err = re.findall(r".*(?:Mixin apply failed|InvalidInjectionException|MixinApplyError|crash).*", text, re.I)
    for e in mixin_err[:5]:
        print("  !" + e.strip()[:220])
    for run in re.split(r"\[diagnostics\] run ", text)[1:]:
        head = run.split("\n", 1)[0]
        renderer = re.search(r"Renderer: (.*)", run)
        fps = re.search(r"- \d+ frames, ([\d.]+) fps average", run)
        cpu = re.search(r"Aurora CPU per frame: (.*)", run)
        glass = re.search(r"glass captures per frame: ([\d.]+)", run)
        verdict = re.search(r"- overhead budget: (.*)", run)
        print(f"  run {head}: fps {fps.group(1) if fps else '?'}; CPU {cpu.group(1) if cpu else '?'}; "
              f"glass {glass.group(1) if glass else '?'}; {verdict.group(1)[:60] if verdict else ''}")
        if head.startswith("1 "):
            print("    renderer:", renderer.group(1) if renderer else "?")
            notw = re.search(r"(Features not fully working[\s\S]*?)\n\n", run)
            print("   ", (notw.group(1).replace("\n", " | ") if notw else "all features working")[:300])
            mods = re.search(r"Mods \(\d+\)\n([\s\S]*?)\n\n", run)
            if mods:
                print("    report mods:", mods.group(1).replace("\n", ";"))
