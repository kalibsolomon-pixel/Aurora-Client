"""Boot the dev client once per mod set and keep each log (ADR 0004 tested matrix).

Usage: python -I tools/matrix/run.py <pins json> <jars dir> <out dir> [--window-script X.ps1] [set ...]

For each set: copies its jars into run/mods, boots into the saved world
("New World") with three diagnostics reports, closes the window normally
after the third, saves run/logs/latest.log (and a crash report if one was
written), then empties run/mods. Back up run/config first: the boot uses it.
--window-script runs a PowerShell script once the window exists (for example,
to move it to another monitor). Windows only: closing uses CloseMainWindow.
"""
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
args = sys.argv[1:]
window_script = None
if "--window-script" in args:
    i = args.index("--window-script")
    window_script = args[i + 1]
    del args[i:i + 2]
PINS = json.loads(Path(args[0]).read_text())
JARS, OUT = Path(args[1]), Path(args[2])
only = args[3:] or list(PINS["sets"])
MODS, LOG = REPO / "run" / "mods", REPO / "run" / "logs" / "latest.log"
OUT.mkdir(parents=True, exist_ok=True)
MODS.mkdir(parents=True, exist_ok=True)
manifest = json.loads((JARS / "manifest.json").read_text())["mods"]


def ps(*a):
    return subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", *a],
                          capture_output=True, text=True).stdout.strip()


def close_game():
    ps("-Command", "(Get-Process java,javaw -ErrorAction SilentlyContinue | "
                   "? MainWindowTitle -like 'Minecraft*').CloseMainWindow()")


if any(MODS.glob("*.jar")):
    sys.exit(f"{MODS} is not empty; move its jars aside first")
for name in only:
    for slug in PINS["sets"][name]:
        shutil.copy2(JARS / manifest[slug]["file"], MODS)
    print(f"== {name}: {sorted(p.name for p in MODS.glob('*.jar'))}", flush=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS="-Daurora.diagnostics.log=true -Daurora.diagnostics.runs=3")
    gradle = subprocess.Popen(["cmd", "/c", str(REPO / "gradlew.bat"), "--no-daemon", "runClient", "--console=plain",
                               '--args=--quickPlaySingleplayer "New World"'],
                              cwd=REPO, env=env, stdout=open(OUT / f"{name}-gradle.log", "w"), stderr=subprocess.STDOUT)
    started, window_done, outcome = time.time(), window_script is None, "timed out"
    while time.time() - started < 480:
        if gradle.poll() is not None:
            outcome = f"game exited on its own (gradle exit {gradle.returncode})"
            break
        if not window_done and "moved" in ps("-File", window_script):
            window_done = True
        try:
            text = LOG.read_text(encoding="utf-8", errors="replace") if os.path.getmtime(LOG) > started else ""
        except OSError:
            text = ""
        if "[diagnostics] run 3 of 3" in text:
            time.sleep(1)
            close_game()
            outcome = "three reports logged; closed normally"
            break
        time.sleep(1)
    try:
        gradle.wait(timeout=120)
    except subprocess.TimeoutExpired:
        close_game()
        gradle.wait(timeout=60)
    time.sleep(2)
    if LOG.exists() and os.path.getmtime(LOG) > started:
        shutil.copy2(LOG, OUT / f"{name}-latest.log")
    else:
        outcome += "; no new latest.log"
    crashes = REPO / "run" / "crash-reports"
    newest = max(crashes.glob("*.txt"), key=os.path.getmtime, default=None) if crashes.exists() else None
    if newest and os.path.getmtime(newest) > started:
        shutil.copy2(newest, OUT / f"{name}-crash.txt")
        outcome += f"; crash report {newest.name}"
    print(f"  {outcome}", flush=True)
    for f in MODS.glob("*.jar"):
        f.unlink()
print("run/mods emptied", flush=True)
