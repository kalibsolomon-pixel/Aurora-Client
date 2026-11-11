"""Boot the dev client once per mod set and keep each log (ADR 0004 tested matrix).

Usage: python -I tools/matrix/run.py <pins json> <jars dir> <out dir> [--window-script X.ps1] [set ...]

For each set: copies its jars into the selected game directory's mods folder, boots into the saved world
("New World") with three diagnostics reports, closes the window normally
after the third, saves logs/latest.log (and a crash report if one was
written), then removes the staged jars. Use a disposable copy of the world and config.
--run-dir selects a disposable game directory (defaults to run/).
--graphics-backend OPENGL|VULKAN selects Minecraft 26.2's native backend.
--runs N logs N reports (default 3; use 6 for the overhead release check).
--window-script runs a PowerShell script once the window exists (for example,
to move it to another monitor). Windows only: closing uses CloseMainWindow.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO))
from tools.parity_acceptance import fingerprint

args = sys.argv[1:]
window_script = None
graphics_backend = None
runs = 3
if "--runs" in args:
    i = args.index("--runs")
    runs = int(args[i + 1])
    if runs < 3:
        sys.exit("--runs must be at least 3")
    del args[i:i + 2]
if "--graphics-backend" in args:
    i = args.index("--graphics-backend")
    graphics_backend = args[i + 1].upper()
    if graphics_backend not in {"OPENGL", "VULKAN"}:
        sys.exit("--graphics-backend must be OPENGL or VULKAN")
    del args[i:i + 2]
run_dir = REPO / "run"
if "--run-dir" in args:
    i = args.index("--run-dir")
    run_dir = Path(args[i + 1]).resolve()
    del args[i:i + 2]
if "--window-script" in args:
    i = args.index("--window-script")
    window_script = args[i + 1]
    del args[i:i + 2]
PINS = json.loads(Path(args[0]).read_text())
versions = json.loads((REPO / 'gradle/minecraft-versions.json').read_text())
if PINS['minecraft'] not in versions:
    sys.exit('Pins must name a supported Minecraft line')
JARS, OUT = Path(args[1]), Path(args[2])
requested = args[3:]
only = requested or list(PINS["sets"])
for name in list(only):
    allowed = PINS.get("set_backends", {}).get(name)
    if graphics_backend and allowed and graphics_backend not in allowed:
        if requested:
            sys.exit(f"{name} supports {allowed}, not {graphics_backend}")
        print(f"Skipping {name} on {graphics_backend}: supports {allowed}", flush=True)
        only.remove(name)
MODS, LOG = run_dir / "mods", run_dir / "logs" / "latest.log"
OUT = OUT.resolve()
OUT.mkdir(parents=True, exist_ok=True)
MODS.mkdir(parents=True, exist_ok=True)
manifest = json.loads((JARS / "manifest.json").read_text())["mods"]
import hashlib
for slug in {slug for name in only for slug in PINS['sets'][name]}:
    item = manifest[slug]
    if item['version'] != PINS['mods'][slug] or hashlib.sha512((JARS / item['file']).read_bytes()).hexdigest() != item['sha512']:
        sys.exit(f'{slug}: staged mod differs from the pinned version or hash')


def ps(*a):
    return subprocess.run(["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", *a],
                          capture_output=True, text=True).stdout.strip()


def game_windows():
    return {int(pid) for pid in ps('-Command', "Get-Process java,javaw -ErrorAction SilentlyContinue | "
                                 "Where-Object MainWindowTitle -like 'Minecraft*' | Select-Object -ExpandProperty Id").split()}


def close_game():
    for pid in game_windows() - existing_windows:
        ps('-Command', f'(Get-Process -Id {pid} -ErrorAction SilentlyContinue).CloseMainWindow()')


if any(MODS.glob("*.jar")):
    sys.exit(f"{MODS} is not empty; move its jars aside first")
# JavaExec arguments avoid cmd.exe splitting a world name with spaces.
init = OUT / "matrix-launch.gradle"
launch_args = "args '--quickPlaySingleplayer', 'New World'"
if graphics_backend:
    launch_args += f"; args '--graphicsBackend', '{graphics_backend}'"
init.write_text("gradle.projectsEvaluated { rootProject.tasks.named('runClient') { " + launch_args + " } }\n")
failed = False
for name in only:
    source_inputs = fingerprint(REPO)
    existing_windows = game_windows()
    staged = []
    for slug in PINS["sets"][name]:
        staged.append(Path(shutil.copy2(JARS / manifest[slug]["file"], MODS)))
    print(f"== {name}: {sorted(p.name for p in MODS.glob('*.jar'))}", flush=True)
    env = dict(os.environ, JAVA_TOOL_OPTIONS=f"-Daurora.diagnostics.log=true -Daurora.diagnostics.runs={runs}")
    gradle = subprocess.Popen(["cmd", "/c", str(REPO / "gradlew.bat"), "--no-daemon", "runClient", f"-Paurora.minecraft={PINS['minecraft']}", f"-Paurora.runDir={run_dir}", "--console=plain", "-I", str(init)],
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
        if f"[diagnostics] run {runs} of {runs}" in text:
            time.sleep(1)
            close_game()
            outcome = f"{runs} reports logged; closed normally"
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
    crashes = run_dir / "crash-reports"
    newest = max(crashes.glob("*.txt"), key=os.path.getmtime, default=None) if crashes.exists() else None
    if newest and os.path.getmtime(newest) > started:
        shutil.copy2(newest, OUT / f"{name}-crash.txt")
        outcome += f"; crash report {newest.name}"
    print(f"  {outcome}", flush=True)
    (OUT / f'{name}-result.json').write_text(json.dumps(dict(minecraft=PINS['minecraft'], backend=graphics_backend,
                                                          outcome=outcome, exitCode=gradle.returncode,
                                                          sourceInputsSha256=source_inputs), indent=2))
    failed |= outcome != f"{runs} reports logged; closed normally" or gradle.returncode != 0
    if graphics_backend:
        renderer = re.search(r"^Renderer: ([^|\n]+)", text, re.M)
        if not renderer or renderer.group(1).strip().upper() != graphics_backend:
            print(f"  requested {graphics_backend}, actual {renderer.group(1).strip() if renderer else 'unknown'}", flush=True)
            failed = True
    for f in staged:
        f.unlink()
print(f"{MODS}: staged jars removed", flush=True)
sys.exit(1 if failed else 0)
