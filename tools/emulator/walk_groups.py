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
PORT = "5554"
SERIAL = "emulator-" + PORT
# The walk's own words when the emulator, not the app, failed it.
STUCK = ("The emulator is stuck", "did not finish starting", "adb did not answer")
GROUP_SECONDS = 2 * 60 * 60
# Exit codes: walk.py refuses with 2 (no such part, the turn refused) or 75
# (the machine busy) before it walks anything -- every next group would be
# refused the same; 6 is this runner's own "an emulator not its own".
REFUSED = (2, 75)
NOT_OURS = 6


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


def listed():
    """adb's list of devices, or None when adb did not answer: an empty list
    and an unanswered question are not the same, and only one means that no
    emulator is running."""
    try:
        out = run([ADB, "devices"], timeout=20, capture_output=True, text=True)
    except (AdbTimeout, OSError):
        return None
    return out.stdout if out.returncode == 0 else None


def ours(proc, avd):
    """Whether the emulator answering at SERIAL is the one this run started:
    its process still running -- started on PORT, it holds that serial or
    ends -- and the device there naming this run's AVD. An emulator of
    someone else's that took the serial first is never used or stopped."""
    if proc is None or proc.poll() is not None:
        return False
    named = adb_quiet("emu", "avd", "name").splitlines()
    return bool(named) and named[0].strip() == avd


def boot(avd, log):
    """A cold emulator, without a window and saving no snapshot: its process,
    or None when it did not report itself started within five minutes, or
    its own process ended -- then whatever answers is not this run's."""
    with open(log, "w", encoding="utf-8") as out:
        proc = subprocess.Popen([EMULATOR, "-avd", avd, "-port", PORT, "-no-snapshot-load",
                                 "-no-snapshot-save", "-no-window", "-gpu", "swiftshader_indirect"],
                                stdout=out, stderr=subprocess.STDOUT,
                                start_new_session=os.name != "nt")
    try:
        deadline = time.time() + 300
        while time.time() < deadline and proc.poll() is None:
            if (adb_quiet("shell", "getprop", "sys.boot_completed").strip() == "1"
                    and ours(proc, avd)):
                adb_quiet("logcat", "-G", "64M")
                return proc
            time.sleep(3)
    except BaseException:
        stop(proc, avd)            # a Ctrl-C while it starts: not left running
        raise
    stop(proc, avd)
    return None


def stop(proc, avd):
    """The emulator this run started stopped, and only its own process tree
    ended if it did not go: never another emulator on the machine."""
    if proc is None:
        return
    if ours(proc, avd):
        adb_quiet("emu", "kill", timeout=60)
        deadline = time.time() + 60
        while time.time() < deadline and SERIAL in (listed() or ""):
            time.sleep(2)
    try:
        proc.wait(timeout=15)     # one still on its way out is not "stuck"
        return
    except subprocess.TimeoutExpired:
        pass
    try:
        if os.name == "nt":
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)],
                           capture_output=True, timeout=30)
        else:
            os.killpg(proc.pid, 9)    # its own session: the launcher and its qemu
        proc.wait(timeout=30)
        print("   the emulator did not stop; its own process was ended")
    except (OSError, subprocess.SubprocessError):
        print("   the emulator did not stop, and its process %d could not be ended:"
              " end it before anything else runs" % proc.pid)


def walk_group(name, parts, folder, avd):
    """One group, walked on a fresh emulator, once more if it got stuck.
    Returns the walk's exit code, the tries it took, and the checks that
    failed on a try that got stuck: 4 when both tries got stuck, NOT_OURS
    when an emulator it did not start is running or adb cannot say, 5 when
    the walk was ended from outside (a signal, a memory ceiling)."""
    target = os.path.join(folder, name)
    os.makedirs(target, exist_ok=True)
    earlier = []
    for attempt in (1, 2):
        # A summary left by an earlier run in this folder is not this one's.
        old = os.path.join(target, "summary.json")
        if os.path.isfile(old):
            os.remove(old)
        devices = listed()
        if devices is None or "emulator-" in devices:
            # Started meanwhile, or adb cannot say: not this run's to use or stop.
            print("   %s: %s; the run stops here" % (name, "adb did not answer" if devices is None
                                                     else "an emulator this run did not start is running"))
            return NOT_OURS, attempt, earlier
        proc = boot(avd, os.path.join(target, "emulator-%d.log" % attempt))
        if proc is None:
            print("   %s, try %d: the emulator did not start" % (name, attempt))
            continue
        out_path = os.path.join(target, "walk.out")
        try:
            with open(out_path, "w", encoding="utf-8") as out:
                try:
                    # The walk talks to this run's emulator, not the first one listed.
                    code = subprocess.run([sys.executable, "-I", "-u", os.path.join(HERE, "walk.py"),
                                           target] + parts, stdout=out, stderr=subprocess.STDOUT,
                                          timeout=GROUP_SECONDS,
                                          env=dict(os.environ, ANDROID_SERIAL=SERIAL)).returncode
                except subprocess.TimeoutExpired:
                    code = 3
                    out.write("\nThe emulator is stuck: the group ran past %d s\n" % GROUP_SECONDS)
        finally:
            stop(proc, avd)   # however this ends: a Ctrl-C left it running
        with open(out_path, encoding="utf-8", errors="replace") as fh:
            lines = fh.read().splitlines()
        good = sum(1 for line in lines if line.startswith("PASS "))
        bad = sum(1 for line in lines if line.startswith("FAIL "))
        # Read off the walk's own lines, never a check's words, which may quote
        # one (walk.py's check() keeps a check on one line); in any case, as
        # walk.py writes the phrase both ways.
        if any(word.lower() in line.lower() for line in lines
               if not line.startswith(("PASS ", "FAIL ")) for word in STUCK):
            for kept in ("walk.out", "summary.json"):
                path = os.path.join(target, kept)
                if os.path.isfile(path):
                    os.replace(path, path + ".try%d" % attempt)
            # Kept and said: a retry that passes does not make them not have been.
            earlier += ["try %d: %s" % (attempt, line[5:]) for line in lines if line.startswith("FAIL ")]
            print("   %s, try %d: the emulator got stuck after %d passed, %d failed checks"
                  % (name, attempt, good, bad))
            continue
        print("   %s: %d passed, %d failed" % (name, good, bad))
        # A code an exit cannot carry as it is (negative: a signal; past 255
        # on Linux, where it wraps and could read 0) is a failure of its own.
        return (code if 0 <= code <= 255 else 5), attempt, earlier
    return 4, 2, earlier


def read_summary(path):
    """A group's summary.json, or None when it is missing or cut short."""
    try:
        with open(path, encoding="utf-8") as fh:
            summary = json.load(fh)
    except (OSError, ValueError):
        return None
    return summary if isinstance(summary, dict) else None


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("folder")
    parser.add_argument("--avd", default="noteorg_test")
    parser.add_argument("--size", type=int, default=10)
    args = parser.parse_args()
    if args.size < 1:
        print("--size must be at least 1")
        return 2
    # The run's summary is written last; one left by an earlier run must not
    # stand as this one's if this one ends early.
    combined_path = os.path.join(args.folder, "summary.json")
    if os.path.isfile(combined_path):
        os.remove(combined_path)
    if not os.path.isfile(EMULATOR):
        print("the emulator is not at %s" % EMULATOR)
        return 2
    devices = listed()
    if devices is None:
        print("adb did not answer, so whether an emulator is running is not known: run this again.")
        return 2
    if "emulator-" in devices:
        # One it did not start is not its to stop between groups.
        print("An emulator is already running: stop it first (adb emu kill), then run this again.")
        return 2
    parts = walk_parts()
    if not parts:
        print("no parts were read from walk.py's main(): nothing would be walked")
        return 2
    groups = [parts[i:i + args.size] for i in range(0, len(parts), args.size)]
    os.makedirs(args.folder, exist_ok=True)
    print("%d parts in %d groups, each on a cold emulator" % (len(parts), len(groups)))
    worst, combined = 0, {"groups": []}
    for number, group in enumerate(groups, start=1):
        name = "g%d" % number
        print("-- %s: %s .. %s" % (name, group[0], group[-1]))
        code, tries, earlier = walk_group(name, group, args.folder, args.avd)
        worst = max(worst, code)
        summary = read_summary(os.path.join(args.folder, name, "summary.json"))
        if summary is None:
            # Said in the run's summary, not only in its exit code.
            summary = {"passed": 0, "failed": 0, "parts": [], "unwalked": list(group)}
            print("   %s: no summary of its own was written" % name)
        combined["groups"].append(dict(summary, group=name, tries=tries, exit=code,
                                       failed_on_stuck_tries=earlier))
        if code == NOT_OURS or code in REFUSED:
            # Every next group would meet the same: named, not walked.
            combined["not_reached"] = [p for later in groups[number:] for p in later]
            if code in REFUSED:
                print("   %s: the walk refused to start (exit %d); the run stops here" % (name, code))
            break
    combined["passed"] = sum(g.get("passed", 0) for g in combined["groups"])
    combined["failed"] = sum(g.get("failed", 0) for g in combined["groups"])
    combined["parts_walked"] = sum(len(g.get("parts", [])) for g in combined["groups"])
    combined["parts_named"] = len(parts)
    with open(combined_path + ".tmp", "w", encoding="utf-8") as fh:
        json.dump(combined, fh, indent=1, ensure_ascii=False)
    os.replace(combined_path + ".tmp", combined_path)
    print("\nall groups: %d passed, %d failed, %d of %d parts walked"
          % (combined["passed"], combined["failed"], combined["parts_walked"], len(parts)))
    stuck_fails = sum(len(g["failed_on_stuck_tries"]) for g in combined["groups"])
    if stuck_fails:
        print("%d checks failed on tries that got stuck and were walked again: each is in its"
              " group's summary (failed_on_stuck_tries) -- read them, a pass on the retry is not"
              " an answer" % stuck_fails)
    if worst:
        return worst
    if combined["parts_walked"] < len(parts):
        print("not every part was walked: the run is not a pass")
        return 1
    return 1 if combined["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
