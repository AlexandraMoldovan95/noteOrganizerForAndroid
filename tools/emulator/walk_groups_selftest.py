"""walk_groups.py's own decisions, proved without an emulator: boot, adb and
the walk are all faked, so this starts nothing and touches no device.

    py -I tools/emulator/walk_groups_selftest.py              the scenarios
    py -I tools/emulator/walk_groups_selftest.py --mutants    and its mutants

Run it after any change to walk_groups.py, and before a full walk. Four
review rounds in a row (12 November 2026) found the runner able to report a
pass for parts it never walked, or to stop an emulator it had not started;
each case they found is a scenario here, and the version before each fix
failed it. Exit 0 when every scenario holds. With --mutants, each of
MUTANTS breaks one promise in a fresh copy (the source is only read, as
bytes), and the scenarios must fail on every one.
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time

# (file, the code as it is, the code broken, what that breaks)
MUTANTS = [
    ("walk_groups.py", b"            stop(proc, avd)   # however this ends", b"            pass  # however this ends",
     "no stop in walk_group's finally"),
    ("walk_groups.py", b"return FAILED_ON_A_STUCK_TRY if stuck_fails else 0", b"return 0",
     "a check failed on a stuck try passes"),
    ("walk_groups.py", b"(code if 0 <= code <= 255 else 5)", b"(code if code <= 255 else 5)",
     "a negative exit code passes"),
    ("walk_groups.py", b"summary[\"unwalked\"] = [p for p in group if p not in started]",
     b"summary[\"unwalked\"] = []", "the parts after one that died are not named"),
    ("walk_groups.py", b"\"-avd\", avd, \"-port\", PORT, ", b"\"-avd\", avd, ", "no port of its own"),
    ("walk_groups.py", b"        stop(proc, avd)            # a Ctrl-C while it starts",
     b"        pass            # a Ctrl-C while it starts", "a Ctrl-C while it starts leaves it running"),
    ("walk_groups.py", b"    if ours(proc, avd):\n        adb_quiet(\"emu\", \"kill\"",
     b"    if True:\n        adb_quiet(\"emu\", \"kill\"", "stop() kills whatever is at the serial"),
    ("walk_groups.py", b"env=dict(os.environ, ANDROID_SERIAL=SERIAL)", b"env=None", "the walk is not told the serial"),
    ("walk_groups.py", b"if code == NOT_OURS or code in REFUSED:", b"if code == NOT_OURS:",
     "a refusal walks on"),
    ("walk_groups.py", b"    if not parts:\n        print(\"no parts",
     b"    if False:\n        print(\"no parts", "no parts read is walked as a pass"),
]


def mutants():
    here = os.path.dirname(os.path.abspath(__file__))
    survived = 0
    for name, old, new, what in MUTANTS:
        work = tempfile.mkdtemp(prefix="wg-mutant-")
        try:
            for f in os.listdir(here):
                if f.endswith(".py"):
                    shutil.copy2(os.path.join(here, f), work)
            path = os.path.join(work, name)
            with open(path, "rb") as fh:
                data = fh.read().replace(b"\r\n", b"\n")      # the copy's, whatever git wrote
            if data.count(old) != 1:
                print("NOT FOUND %s" % what)
                survived += 1
                continue
            with open(path, "wb") as fh:
                fh.write(data.replace(old, new))
            run = subprocess.run([sys.executable, "-I", os.path.join(work, os.path.basename(__file__))],
                                 capture_output=True, text=True, timeout=300)
            killed = run.returncode != 0
            survived += not killed
            print("%s %s" % ("KILLED   " if killed else "SURVIVED ", what))
        finally:
            shutil.rmtree(work, ignore_errors=True)
    print("%d of %d mutants killed" % (len(MUTANTS) - survived, len(MUTANTS)))
    return 1 if survived else 0

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walk_groups as g  # noqa: E402

AVD = "noteorg_test"
results = []
serials = []
boots, stops = [], []      # the fake emulators walk_group started, and stopped


def note(ok, what, got):
    results.append((bool(ok), what, got))


class FakeProc:
    """An emulator's process: alive for [lives] polls, or for ever."""

    def __init__(self, lives=None):
        self.lives, self.pid = lives, 0

    def poll(self):
        if self.lives is None:
            return None
        self.lives -= 1
        return None if self.lives > 0 else 1

    def wait(self, timeout=None):
        return 0


def ownership(what, name, lives, interrupt=False, want_proc=False, want_kill=False):
    """boot() then stop() against a device at the serial naming [name]."""
    real = (time.sleep, g.subprocess.Popen, g.adb_quiet, g.subprocess.run)
    sent = []

    def quiet(*args, timeout=30):
        sent.append(args)
        if args[:2] == ("shell", "getprop"):
            if interrupt:
                raise KeyboardInterrupt
            return "1\n"
        if args[:3] == ("emu", "avd", "name"):
            return name + "\r\nOK\r\n"
        return ""
    proc = FakeProc(lives)
    folder = tempfile.mkdtemp(prefix="wg-selftest-")
    started = []
    time.sleep = lambda s: None
    g.subprocess.Popen = lambda args, **k: started.append(list(args)) or proc
    g.adb_quiet = quiet
    g.subprocess.run = lambda *a, **k: type("R", (), {"returncode": 0, "stdout": ""})()
    try:
        try:
            got = g.boot(AVD, os.path.join(folder, "emulator.log"))
        except KeyboardInterrupt:
            got = "interrupted"
        if got not in (None, "interrupted"):
            g.stop(got, AVD)
    except Exception as exc:  # noqa: BLE001 -- a crash is this scenario's answer
        got = "crash: %s: %s" % (type(exc).__name__, exc)
    finally:
        time.sleep, g.subprocess.Popen, g.adb_quiet, g.subprocess.run = real
        shutil.rmtree(folder, ignore_errors=True)
    used = got is proc
    killed = ("emu", "kill") in sent
    # Started as promised: on its own port, without a window or a snapshot.
    args = started[0] if started else []
    shaped = all(a in args for a in ("-port", getattr(g, "PORT", "?"), "-no-window",
                                     "-no-snapshot-load", "-no-snapshot-save", AVD))
    note(used == want_proc and killed == want_kill and shaped and not str(got).startswith("crash"),
         what, "used %s, emu kill sent %s, started %s (%s)" % (used, killed, args[1:], got))


def scenario(what, parts, behaviour, devices=lambda n: "List of devices attached\n\n",
             want_exit=None, want=lambda s: True, size=2, starts=True):
    """main() over [parts], each walk answering [behaviour](group, try) with
    (its lines, its summary dict / "cut" / None, its exit code), or raising.
    A summary's parts are the group's own, in order, as many as it lists.
    The emulators are fakes that walk_group itself stops ([starts] False:
    none ever starts)."""
    real = (g.walk_parts, g.listed, g.boot, g.stop, g.EMULATOR, g.subprocess.run, sys.argv)
    folder = tempfile.mkdtemp(prefix="wg-selftest-")
    with open(os.path.join(folder, "summary.json"), "w", encoding="utf-8") as fh:
        json.dump({"passed": 999, "failed": 0}, fh)        # an earlier run's
    walks = {"n": 0, "tries": {}}
    del boots[:], stops[:]

    def fake_walk(cmd, stdout=None, stderr=None, timeout=None, env=None):
        serials.append((env or {}).get("ANDROID_SERIAL"))
        target, names = cmd[4], cmd[5:]
        group = os.path.basename(target)
        walks["n"] += 1
        attempt = walks["tries"][group] = walks["tries"].get(group, 0) + 1
        lines, summary, code = behaviour(group, attempt)
        stdout.write("\n".join(lines) + "\n")
        if summary == "cut":
            with open(os.path.join(target, "summary.json"), "w", encoding="utf-8") as fh:
                fh.write('{"passed": 3, "fai')
        elif summary is not None:
            summary = dict(summary, parts=[{"part": n} for n in names[:len(summary["parts"])]])
            with open(os.path.join(target, "summary.json"), "w", encoding="utf-8") as fh:
                json.dump(summary, fh)
        return type("R", (), {"returncode": code})()

    def fake_boot(avd, log):
        if not starts:
            return None
        boots.append(FakeProc())
        return boots[-1]

    g.walk_parts = lambda: list(parts)
    g.listed = lambda: devices(walks["n"])
    g.boot = fake_boot
    g.stop = lambda proc, avd=None: stops.append(proc)
    g.EMULATOR = sys.executable                             # "is there"
    g.subprocess.run = fake_walk
    sys.argv = ["walk_groups.py", folder, "--size", str(size)]
    out = sys.stdout
    sys.stdout = open(os.devnull, "w", encoding="utf-8")    # the runner's own lines
    try:
        code = g.main()
    except KeyboardInterrupt:
        code = "interrupted"
    except Exception as exc:  # noqa: BLE001 -- a crash is this scenario's answer
        code = "crash: %s: %s" % (type(exc).__name__, exc)
    finally:
        sys.stdout.close()
        sys.stdout = out
        (g.walk_parts, g.listed, g.boot, g.stop, g.EMULATOR, g.subprocess.run, sys.argv) = real
    path = os.path.join(folder, "summary.json")
    combined = None
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            combined = json.load(fh)
    shutil.rmtree(folder, ignore_errors=True)
    # In every scenario, however it ended: each emulator started was stopped.
    left = [b for b in boots if not any(s is b for s in stops)]
    try:
        exit_ok = want_exit(code) if callable(want_exit) else (want_exit is None or code == want_exit)
        ok = exit_ok and bool(want(combined)) and not left
    except Exception:  # noqa: BLE001 -- a summary of another shape is a miss
        ok = False
    note(ok, what, "exit %s, %d of %d emulators left running" % (code, len(left), len(boots)))


def clean(group, attempt):
    return ["PASS one", "PASS two"], {"passed": 2, "failed": 0, "parts": [{"part": "x"}, {"part": "y"}]}, 0


def main():
    ownership("its own emulator: used, then stopped by emu kill", AVD, None, want_proc=True, want_kill=True)
    ownership("another AVD's emulator at the serial: not used, not stopped", "someone_else", 6)
    ownership("its launcher ended at once (the port taken): the one there is not stopped", AVD, 1)
    ownership("a Ctrl-C while it starts: its emulator is stopped", AVD, None, interrupt=True, want_kill=True)

    four = ["a", "b", "c", "d"]
    scenario("every group clean: a pass", four, clean, want_exit=0,
             want=lambda s: s["passed"] == 4 and s["parts_walked"] == 4)
    scenario("no parts read from main(): refused, and no earlier summary left", [], clean,
             want_exit=2, want=lambda s: s is None)
    scenario("a group stuck twice: exit 4, and named unwalked in the summary", four,
             lambda grp, a: (["PASS one", "AdbTimeout: adb did not answer within 120 s"],
                             {"passed": 1, "failed": 0, "parts": [{"part": "x"}]}, 1)
             if grp == "g2" else clean(grp, a),
             want_exit=4, want=lambda s: any(x["group"] == "g2" and x["tries"] == 2 and x.get("unwalked")
                                             for x in s["groups"]))
    scenario("stuck once, clean on the retry: a pass that says it took two tries", ["a", "b"],
             lambda grp, a: (["the emulator is stuck"], None, 3) if a == 1 else clean(grp, a),
             want_exit=0, want=lambda s: s["groups"][0]["tries"] == 2)
    scenario("a check failed on a stuck try, the retry clean: kept, and not a pass (exit 7)", ["a", "b"],
             lambda grp, a: (["FAIL lost", "The emulator is stuck"], None, 3) if a == 1 else clean(grp, a),
             want_exit=7, want=lambda s: s["groups"][0]["failed_on_stuck_tries"] == ["try 1: lost"])
    scenario("a walk ended by a signal, no summary: not a pass", four,
             lambda grp, a: (["PASS one"], None, -9) if grp == "g2" else clean(grp, a),
             want_exit=lambda c: c not in (0,) and not str(c).startswith("crash"))
    scenario("a walk ended by a signal after a whole summary: exit 5, not 0", ["a", "b"],
             lambda grp, a: (clean(grp, a)[0], clean(grp, a)[1], -9), want_exit=5)

    def interrupted(grp, a):
        raise KeyboardInterrupt
    scenario("a Ctrl-C during a group's walk: its emulator is stopped, the Ctrl-C goes on", ["a", "b"],
             interrupted, want_exit="interrupted", want=lambda s: len(boots) == 1)

    def past_its_time(grp, a):
        if a == 1:
            raise g.subprocess.TimeoutExpired("walk.py", g.GROUP_SECONDS)
        return clean(grp, a)
    scenario("a group past its time: said stuck, walked once more, both emulators stopped", ["a", "b"],
             past_its_time, want_exit=0, want=lambda s: s["groups"][0]["tries"] == 2 and len(boots) == 2)
    scenario("the emulator never starts, twice: exit 4, the group named unwalked", ["a", "b"], clean,
             starts=False, want_exit=4, want=lambda s: s["groups"][0]["unwalked"] == ["a", "b"])
    scenario("a part died mid-group: the parts after it are named unwalked", ["a", "b", "c"],
             lambda grp, a: (["PASS one", "FAIL b ended early: RuntimeError"],
                             {"passed": 1, "failed": 1, "parts": [{"part": "x"}, {"part": "y"}]}, 1),
             size=3, want_exit=1, want=lambda s: s["groups"][0]["unwalked"] == ["c"])
    scenario("an exit code past 255: a failure, never wrapped to 0", ["a", "b"],
             lambda grp, a: (["PASS one"], {"passed": 2, "failed": 0, "parts": [{"part": "x"}, {"part": "y"}]},
                             256),
             want_exit=5)
    scenario("a summary cut short: the run goes on, and is not a pass", four,
             lambda grp, a: (["PASS one"], "cut", 0) if grp == "g1" else clean(grp, a),
             want_exit=lambda c: c not in (0,) and not str(c).startswith("crash"),
             want=lambda s: s is not None)
    scenario("an emulator not its own appears before group 2: the run stops", four, clean,
             devices=lambda n: "emulator-5556\tdevice\n" if n >= 1 else "List of devices attached\n",
             want_exit=6, want=lambda s: len(s["groups"]) == 2 and s["parts_walked"] == 2)
    scenario("adb not answering at the start is not 'no emulator'", ["a", "b"], clean,
             devices=lambda n: None, want_exit=2, want=lambda s: s is None)
    scenario("a check quoting the stuck phrase is not a stuck emulator", ["a", "b"],
             lambda grp, a: (["FAIL x  -- the screen said adb did not answer / more"],
                             {"passed": 0, "failed": 1, "parts": [{"part": "x"}, {"part": "y"}]}, 1),
             want_exit=1, want=lambda s: s["groups"][0]["tries"] == 1)
    scenario("a group that walked fewer parts than it named: not a pass", ["a", "b"],
             lambda grp, a: (["PASS one"], {"passed": 1, "failed": 0, "parts": [{"part": "x"}]}, 0),
             want_exit=1)
    scenario("the walk refused the turn: the run stops, the rest named", four,
             lambda grp, a: (["The walk did not get the machine's turn (exit 75)"], None, 75),
             want_exit=75, want=lambda s: len(s["groups"]) == 1 and s["not_reached"] == ["c", "d"])
    del serials[:]
    scenario("the walk is told this run's serial, not the first one listed", ["a", "b"], clean,
             want_exit=0, want=lambda s: serials == [g.SERIAL])

    for ok, what, got in results:
        print("%s %s%s" % ("ok  " if ok else "BAD ", what, "" if ok else "  -- " + got))
    bad = sum(1 for ok, _, _ in results if not ok)
    print("%d of %d scenarios hold" % (len(results) - bad, len(results)))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(mutants() if "--mutants" in sys.argv[1:] else main())
