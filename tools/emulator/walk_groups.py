"""The whole walk in groups of parts, each on an emulator started cold and
stopped after it -- the way the release cut's walk passed, 628 of 628, on
9 November 2026 (qa/WALK_RUNS.md), when one long cold start had lost the
emulator itself twice and the quick-boot snapshot had a hung System UI.

    py -I tools/emulator/walk_groups.py <folder> [--avd noteorg_test] [--size 10]

Every part main() names is walked once, in main()'s order, read from
walk.py itself, so a new part joins without a list kept here. A group whose
emulator got stuck is walked once more on a fresh one, and says so. Each
group writes into <folder>/gN, its own summary.json there (walk.py's), and
the run's into <folder>/summary.json. Exit 0 when every check passed.

Like the emulator it starts, this takes no turn on the machine (CLAUDE.md):
each walk.py takes it as it starts. Run nothing heavy beside it.
"""
import argparse
import inspect
import json
import os
import re
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from emulator_only import ADB, AdbTimeout, run  # noqa: E402 -- adb held to a time limit

SDK = os.path.dirname(os.path.dirname(ADB))
EMULATOR = os.path.join(SDK, "emulator", "emulator.exe" if os.name == "nt" else "emulator")
SERIAL = "emulator-5554"
QEMU = "qemu-system-x86_64-headless.exe"
# The walk's own words when the emulator, not the app, failed it.
STUCK = ("The emulator is stuck", "did not finish starting", "adb did not answer")
GROUP_SECONDS = 2 * 60 * 60


def walk_parts():
    """main()'s parts in its order, read as walk.py's own check reads them."""
    import walk
    return re.findall(r"^    (\w+)\(folder\)$", inspect.getsource(walk.main), re.M)


def adb_quiet(*args, timeout=30):
    try:
        return run([ADB, "-s", SERIAL] + list(args), timeout=timeout,
                   capture_output=True, text=True, encoding="utf-8", errors="replace").stdout
    except (AdbTimeout, OSError):
        return ""


def boot(avd, log):
    """A cold emulator, without a window and saving no snapshot; False when
    it did not report itself started within five minutes."""
    with open(log, "w", encoding="utf-8") as out:
        subprocess.Popen([EMULATOR, "-avd", avd, "-no-snapshot-load", "-no-snapshot-save",
                          "-no-window", "-gpu", "swiftshader_indirect"],
                         stdout=out, stderr=subprocess.STDOUT)
    deadline = time.time() + 300
    while time.time() < deadline:
        if adb_quiet("shell", "getprop", "sys.boot_completed").strip() == "1":
            adb_quiet("logcat", "-G", "64M")
            return True
        time.sleep(3)
    return False


def stop():
    """The emulator stopped, and its process ended if it did not go."""
    adb_quiet("emu", "kill", timeout=60)
    deadline = time.time() + 60
    while time.time() < deadline:
        try:
            listed = run([ADB, "devices"], timeout=20, capture_output=True, text=True).stdout
        except (AdbTimeout, OSError):
            listed = ""
        if SERIAL not in listed:
            break
        time.sleep(2)
    if os.name == "nt":
        found = subprocess.run(["tasklist", "/FI", "IMAGENAME eq %s" % QEMU],
                               capture_output=True, text=True, timeout=30).stdout
        if QEMU.lower() in found.lower():
            subprocess.run(["taskkill", "/IM", QEMU, "/F"], capture_output=True, timeout=30)
            print("   the emulator did not stop; its process was ended")


def walk_group(name, parts, folder, avd):
    """One group, walked on a fresh emulator, once more if it got stuck.
    Returns the walk's exit code, or 4 when both tries got stuck."""
    target = os.path.join(folder, name)
    os.makedirs(target, exist_ok=True)
    for attempt in (1, 2):
        if not boot(avd, os.path.join(target, "emulator-%d.log" % attempt)):
            print("   %s, try %d: the emulator did not start" % (name, attempt))
            stop()
            continue
        out_path = os.path.join(target, "walk.out")
        with open(out_path, "w", encoding="utf-8") as out:
            try:
                code = subprocess.run([sys.executable, "-I", "-u", os.path.join(HERE, "walk.py"),
                                       target] + parts, stdout=out, stderr=subprocess.STDOUT,
                                      timeout=GROUP_SECONDS).returncode
            except subprocess.TimeoutExpired:
                code = 3
                out.write("\nThe emulator is stuck: the group ran past %d s\n" % GROUP_SECONDS)
        stop()
        with open(out_path, encoding="utf-8", errors="replace") as fh:
            said = fh.read()
        if any(word in said for word in STUCK):
            os.replace(out_path, out_path + ".try%d" % attempt)
            print("   %s, try %d: the emulator got stuck after %d passed checks"
                  % (name, attempt, said.count("\nPASS ")))
            continue
        print("   %s: %d passed, %d failed" % (name, said.count("\nPASS "), said.count("\nFAIL ")))
        return code
    return 4


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("folder")
    parser.add_argument("--avd", default="noteorg_test")
    parser.add_argument("--size", type=int, default=10)
    args = parser.parse_args()
    if not os.path.isfile(EMULATOR):
        print("the emulator is not at %s" % EMULATOR)
        return 2
    parts = walk_parts()
    groups = [parts[i:i + args.size] for i in range(0, len(parts), args.size)]
    os.makedirs(args.folder, exist_ok=True)
    print("%d parts in %d groups, each on a cold emulator" % (len(parts), len(groups)))
    worst, combined = 0, {"groups": []}
    for number, group in enumerate(groups, start=1):
        name = "g%d" % number
        print("-- %s: %s .. %s" % (name, group[0], group[-1]))
        worst = max(worst, walk_group(name, group, args.folder, args.avd))
        summary = os.path.join(args.folder, name, "summary.json")
        if os.path.isfile(summary):
            with open(summary, encoding="utf-8") as fh:
                combined["groups"].append(dict(json.load(fh), group=name))
    combined["passed"] = sum(g.get("passed", 0) for g in combined["groups"])
    combined["failed"] = sum(g.get("failed", 0) for g in combined["groups"])
    combined["parts_walked"] = sum(len(g.get("parts", [])) for g in combined["groups"])
    combined["parts_named"] = len(parts)
    with open(os.path.join(args.folder, "summary.json"), "w", encoding="utf-8") as fh:
        json.dump(combined, fh, indent=1, ensure_ascii=False)
    print("\nall groups: %d passed, %d failed, %d of %d parts walked"
          % (combined["passed"], combined["failed"], combined["parts_walked"], len(parts)))
    return worst if worst else (1 if combined["failed"] else 0)


if __name__ == "__main__":
    sys.exit(main())
