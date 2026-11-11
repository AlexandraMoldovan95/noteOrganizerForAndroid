"""walk_groups.py's own decisions, proved without an emulator: boot, adb and
the walk are all faked, so this starts nothing and touches no device.

    py -I tools/emulator/walk_groups_selftest.py

Run it after any change to walk_groups.py, and before a full walk. Three
review rounds in a row (12 November 2026) found the runner able to report a
pass for parts it never walked, or to stop an emulator it had not started;
each case they found is a scenario here, and the version before each fix
failed it. Exit 0 when every scenario holds.
"""
import json
import os
import shutil
import sys
import tempfile
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walk_groups as g  # noqa: E402

AVD = "noteorg_test"
results = []
serials = []


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
    time.sleep = lambda s: None
    g.subprocess.Popen = lambda *a, **k: proc
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
    note(used == want_proc and killed == want_kill and not str(got).startswith("crash"),
         what, "used %s, emu kill sent %s (%s)" % (used, killed, got))


def scenario(what, parts, behaviour, devices=lambda n: "List of devices attached\n\n",
             want_exit=None, want=lambda s: True, size=2):
    """main() over [parts], each walk answering [behaviour](group, try) with
    (its lines, its summary dict / "cut" / None, its exit code)."""
    real = (g.walk_parts, g.listed, g.boot, g.stop, g.EMULATOR, g.subprocess.run, sys.argv)
    folder = tempfile.mkdtemp(prefix="wg-selftest-")
    with open(os.path.join(folder, "summary.json"), "w", encoding="utf-8") as fh:
        json.dump({"passed": 999, "failed": 0}, fh)        # an earlier run's
    walks = {"n": 0, "tries": {}}

    def fake_walk(cmd, stdout=None, stderr=None, timeout=None, env=None):
        serials.append((env or {}).get("ANDROID_SERIAL"))
        target = cmd[4]
        group = os.path.basename(target)
        walks["n"] += 1
        attempt = walks["tries"][group] = walks["tries"].get(group, 0) + 1
        lines, summary, code = behaviour(group, attempt)
        stdout.write("\n".join(lines) + "\n")
        if summary == "cut":
            with open(os.path.join(target, "summary.json"), "w", encoding="utf-8") as fh:
                fh.write('{"passed": 3, "fai')
        elif summary is not None:
            with open(os.path.join(target, "summary.json"), "w", encoding="utf-8") as fh:
                json.dump(summary, fh)
        return type("R", (), {"returncode": code})()

    g.walk_parts = lambda: list(parts)
    g.listed = lambda: devices(walks["n"])
    g.boot = lambda avd, log: FakeProc()
    g.stop = lambda proc, avd=None: None
    g.EMULATOR = sys.executable                             # "is there"
    g.subprocess.run = fake_walk
    sys.argv = ["walk_groups.py", folder, "--size", str(size)]
    out = sys.stdout
    sys.stdout = open(os.devnull, "w", encoding="utf-8")    # the runner's own lines
    try:
        code = g.main()
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
    try:
        exit_ok = want_exit(code) if callable(want_exit) else (want_exit is None or code == want_exit)
        ok = exit_ok and bool(want(combined))
    except Exception:  # noqa: BLE001 -- a summary of another shape is a miss
        ok = False
    note(ok, what, "exit %s" % (code,))


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
    scenario("a stuck try's failures are kept after a clean retry", ["a", "b"],
             lambda grp, a: (["FAIL lost", "The emulator is stuck"], None, 3) if a == 1 else clean(grp, a),
             want_exit=0, want=lambda s: s["groups"][0]["failed_on_stuck_tries"] == ["try 1: lost"])
    scenario("a walk ended by a signal, no summary: not a pass", four,
             lambda grp, a: (["PASS one"], None, -9) if grp == "g2" else clean(grp, a),
             want_exit=lambda c: c not in (0,) and not str(c).startswith("crash"))
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
    sys.exit(main())
