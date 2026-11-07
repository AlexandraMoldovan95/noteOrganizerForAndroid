"""Each fix broken on purpose, to watch the check that guards it fail.

    py tools/mutate.py AS610 AS611          the mutants named
    py tools/mutate.py --all                every mutant the unit tests judge
    py tools/mutate.py --apk OUT AS610      a walk mutant's APK, built to OUT

tools/mutants.json holds one entry per mutant: the file, the text it
replaces (`old`, which must be found exactly once) and what it puts there
(`new`), why the change is wrong (`why`), and what must fail (`tests`):
unit test classes, comma-separated, or `walk:<part>` for a part of the
emulator walk. `expect` is "killed" unless the entry says otherwise.

The repository is only read. A commit (HEAD unless --commit names one) is
unpacked into a folder of its own in the system's temp folder, each
mutation applied there as bytes and put back before the next. A unit
mutant's verdict comes from the test XML, never from Gradle's exit code
alone: one that does not compile, or a run with no results, is NO RUN,
not a survivor. A walk mutant is built, not walked: the emulator walk
needs a device, so the APK is written where --apk says and the part to
walk is named.

It builds, so it takes the machine's turn first, as the walk does
(tools/desktop_guest.py): a second heavy job waits for this one. A Gradle
run past half an hour is ended with everything it started, and reads as
NO RUN.

Exit 0 when every mutant run met its expectation.
"""
import argparse
import glob
import io
import json
import os
import shutil
import signal
import subprocess
import sys
import tarfile
import tempfile
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOGUE = os.path.join(ROOT, "tools", "mutants.json")
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ROOT), "NoteOrganizer"))
CRLF, LF = bytes([13, 10]), bytes([10])
# Gradle's own heap is 2 GB (gradle.properties); the ceiling leaves it room.
PER_PROCESS_MB, ALL_TOGETHER_MB = 4096, 6144
# One mutant's build and tests take one to three minutes on this machine.
GRADLE_LIMIT_S = 1800


def unpack(commit):
    work = tempfile.mkdtemp(prefix="noteorg-mutants-")
    tar = subprocess.run(["git", "-C", ROOT, "archive", "--format=tar", commit],
                         capture_output=True, check=True).stdout
    with tarfile.open(fileobj=io.BytesIO(tar)) as t:
        t.extractall(work, filter="data")
    props = os.path.join(ROOT, "local.properties")     # where the SDK is
    if os.path.exists(props):
        shutil.copy2(props, work)
    return work


def gradle(work, *tasks):
    """A Gradle run in the copy; one that passes GRADLE_LIMIT_S is ended and
    reads as no results -- a NO RUN -- rather than holding the machine's turn
    for good, as emulator_only.py limits every adb call."""
    wrapper = os.path.join(work, "gradlew.bat" if os.name == "nt" else "gradlew")
    args = (["cmd", "/c", wrapper] if os.name == "nt" else [wrapper])
    args += ["-p", work, "--console=plain", "-q", "--no-daemon"] + list(tasks)
    return limited(args, GRADLE_LIMIT_S)


def limited(args, limit):
    """*args* run to its end or for *limit* seconds, whichever is first. Ended,
    it is ended whole: the wrapper's own end left the java it started building
    beside the next mutant's."""
    proc = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
                            encoding="utf-8", errors="replace", start_new_session=os.name != "nt")
    try:
        out, err = proc.communicate(timeout=limit)
        return subprocess.CompletedProcess(args, proc.returncode, out, err)
    except subprocess.TimeoutExpired:
        if os.name == "nt":
            subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
        else:
            os.killpg(proc.pid, signal.SIGKILL)
        proc.communicate()
        return subprocess.CompletedProcess(args, 124, "", "ended after %d s" % limit)


def mutated(work, m):
    """The mutated file's original bytes, after writing the mutation; None
    when its text is not found exactly once."""
    path = os.path.join(work, m["file"])
    data = open(path, "rb").read()
    old, new = m["old"].encode("utf-8"), m["new"].encode("utf-8")
    # An unpacked copy has this machine's line endings; the catalogue's text
    # is written with "\n".
    if CRLF in data and CRLF not in old:
        old, new = old.replace(LF, CRLF), new.replace(LF, CRLF)
    if data.count(old) != 1:
        return None
    open(path, "wb").write(data.replace(old, new))
    return data


def run_unit(work, m):
    results = os.path.join(work, "app", "build", "test-results", "testDebugUnitTest")
    shutil.rmtree(results, ignore_errors=True)
    tasks = ["testDebugUnitTest"]
    for name in m["tests"].split(","):
        tasks += ["--tests", "ro.noteorganizer.app." + name.strip()]
    run = gradle(work, *tasks)
    tests = failed = 0
    names = []
    for xml in glob.glob(os.path.join(results, "*.xml")):
        root = ET.parse(xml).getroot()
        tests += int(root.get("tests"))
        for case in root.iter("testcase"):
            if case.find("failure") is not None or case.find("error") is not None:
                failed += 1
                names.append(case.get("name"))
    if tests == 0:
        return "NO RUN", "no test results -- " + (run.stdout + run.stderr)[-400:]
    return ("KILLED" if failed else "SURVIVED"), "%d of %d failed %s" % (failed, tests, names[:3])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--commit", default="HEAD")
    ap.add_argument("--apk")
    a = ap.parse_args()
    catalogue = json.load(io.open(CATALOGUE, encoding="utf-8"))
    by_id = {m["id"]: m for m in catalogue}
    if a.all:
        wanted = [m for m in catalogue if not m["tests"].startswith("walk:")]
    else:
        unknown = [i for i in a.ids if i not in by_id]
        if unknown or not a.ids:
            sys.exit("Name mutants from tools/mutants.json, or --all: %s" % (unknown or "none named"))
        wanted = [by_id[i] for i in a.ids]
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import desktop_guest
    desktop_guest.settle(DESKTOP, PER_PROCESS_MB, ALL_TOGETHER_MB)
    work = unpack(a.commit)
    wrong = 0
    try:
        for m in wanted:
            data = mutated(work, m)
            if data is None:
                print("NO RUN %s: its text is not found exactly once at %s" % (m["id"], a.commit), flush=True)
                wrong += 1
                continue
            path = os.path.join(work, m["file"])
            try:
                if m["tests"].startswith("walk:"):
                    if not a.apk:
                        print("%s is judged by the walk's part %s: build it with --apk" % (m["id"], m["tests"][5:]))
                        wrong += 1
                        continue
                    built = gradle(work, "assembleDebug")
                    apk = os.path.join(work, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
                    if not os.path.exists(apk):
                        print("NO RUN %s: the APK did not build -- %s" % (m["id"], built.stderr[-400:]))
                        wrong += 1
                        continue
                    shutil.copy2(apk, a.apk)
                    print("BUILT %s -> %s; walk its part: py tools/emulator/walk.py <folder> %s"
                          % (m["id"], a.apk, m["tests"][5:]))
                    continue
                verdict, detail = run_unit(work, m)
            finally:
                open(path, "wb").write(data)
            expect = m.get("expect", "killed").upper()
            met = verdict == expect or (expect != "KILLED" and verdict == "SURVIVED")
            wrong += not met
            print("%s %s%s: %s -- %s" % (verdict, m["id"], "" if met else " (expected %s)" % expect,
                                         m["why"], detail), flush=True)
    finally:
        shutil.rmtree(work, ignore_errors=True)
    print("%d of %d as expected." % (len(wanted) - wrong, len(wanted)))
    return 1 if wrong else 0


if __name__ == "__main__":
    sys.exit(main())
