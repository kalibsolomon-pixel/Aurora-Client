"""Release check for Aurora's overhead budget (ADR 0005, docs/OVERHEAD_BUDGET.md).

Reads a game log written with -Daurora.diagnostics.log=true -Daurora.diagnostics.runs=6,
drops the warm-up runs, and fails unless every remaining run is within the budget.

Usage: python tools/overhead_check.py [run/logs/latest.log] [--warmup N]
"""
import re
import sys

args = [a for a in sys.argv[1:] if not a.startswith("--")]
log = args[0] if args else "run/logs/latest.log"
warmup = 2
if "--warmup" in sys.argv:
    warmup = int(sys.argv[sys.argv.index("--warmup") + 1])

text = open(log, encoding="utf-8", errors="replace").read()
runs = re.split(r"\[diagnostics\] run ", text)[1:]
if not runs:
    sys.exit(f"{log}: no diagnostics runs; boot with -Daurora.diagnostics.log=true -Daurora.diagnostics.runs=6")

judged, failures = 0, []
for block in runs[warmup:]:
    name = "run " + block.split("\n", 1)[0]
    cpu = re.search(r"Aurora CPU per frame: ([\d.]+) ms mean, [\d.]+ ms median, ([\d.]+) ms p95", block)
    verdict = re.search(r"- overhead budget: (.*)", block)
    print(f"{name}: CPU mean {cpu.group(1) if cpu else '?'} ms, p95 {cpu.group(2) if cpu else '?'} ms; "
          f"{verdict.group(1) if verdict else 'no verdict'}")
    if not verdict or verdict.group(1).startswith("not judged"):
        failures.append(f"{name} was not measured in a world with no screen open")
    elif verdict.group(1).startswith("OVER"):
        failures.append(f"{name}: {verdict.group(1)}")
    else:
        judged += 1

if judged < 3 and not failures:
    failures.append(f"only {judged} run(s) after the {warmup} warm-up run(s); need at least 3")
if failures:
    print("\nOVER BUDGET or not measurable:\n- " + "\n- ".join(failures))
    sys.exit(1)
print(f"\nWithin budget: {judged} run(s) after {warmup} warm-up run(s).")
