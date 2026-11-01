"""Prove the tests actually ran, and say how many.

WHY THIS EXISTS
---------------
`gradlew testDebugUnitTest` prints nothing useful when it fails, and --
worse -- prints nothing at all when the tests never ran. Three times in
this project a mutant "survived" while no test had executed: the suite
had not compiled, or the task had been skipped, and "no failures" looked
exactly like "no tests".

The XML in app/build/test-results/ is the only proof. This reads it.

    python tools/check_test_results.py

Fails when there are no result files, when the total is zero, when the
total falls below the floor below, or when anything failed or errored.

And when the results are OLDER than the code. A build that fails to
compile leaves the last run's XML exactly where it was, so this gate once
printed "221 tests passed" under a Gradle run that had just failed --
the silent-mutant failure again, one level up. A result file older than
the newest source file is from some other version of the code, and is
refused as such. A source file is any file the tests are built or fed
from: a fixture the tests replay (days_cases.json) counts as much as the
Kotlin (the final audit's twenty-second round).

And when a test class ran fewer tests than its file holds, or did not run
at all: each *Test.kt file's @Test count must be the count its result
file reports. The floor alone let 26 of 31 classes vanish one at a time
with the gate green (the final audit's twenty-fifth round).

The floor is not a target to grow; it is a tripwire. If a whole suite
stops being discovered, the count quietly halves and every run stays
green. When tests are deliberately removed, lower it in the same commit.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
RESULTS = os.path.join(ANDROID, "app", "build", "test-results",
                       "testDebugUnitTest", "*.xml")

# Near the count the suite holds (903 at the final audit's fourteenth round):
# at 200, twelve of 31 test classes -- 612 tests -- could go undiscovered and
# the gate still passed.
FLOOR = 880

# The only tests that may skip, and why: each Assumes what the clock alone
# decides. Any other skip fails the gate -- counted as a pass, a test that
# stopped running kept CI green (the final audit's twelfth round).
MAY_SKIP = {
    ("ro.noteorganizer.app.EditorTypingTest",
     "an Undo of letters typed in two places carries the calculator's place back through each"),
}

SOURCES = os.path.join(ANDROID, "app", "src")
TESTS = os.path.join(SOURCES, "test", "java", "ro", "noteorganizer", "app")


def newest_source():
    """The newest file the tests could have been built or fed from."""
    newest, name = 0.0, None
    for root, _dirs, files in os.walk(SOURCES):
        for file in files:
            path = os.path.join(root, file)
            stamp = os.path.getmtime(path)
            if stamp > newest:
                newest, name = stamp, path
    return newest, name


def tests_written():
    """{class name: the @Test count its file holds}, for every *Test.kt."""
    written = {}
    for path in glob.glob(os.path.join(TESTS, "*Test.kt")):
        with open(path, encoding="utf-8") as source:
            count = sum(1 for line in source if line.strip().startswith("@Test"))
        written["ro.noteorganizer.app." + os.path.basename(path)[:-3]] = count
    return written


def main():
    # An optional directory, so this gate can be pointed at a fabricated
    # set of results and watched failing without touching a real build.
    where = os.path.join(sys.argv[1], "*.xml") if len(sys.argv) > 1 else RESULTS
    files = sorted(glob.glob(where))
    if not files:
        print("NO RESULTS in %s" % where)
        print("The tests did not run. A task that does not run is not a")
        print("task that passed.")
        return 1

    if len(sys.argv) <= 1:
        newest, name = newest_source()
        stale = [path for path in files if os.path.getmtime(path) < newest]
        if stale:
            print("STALE RESULTS: %d of %d result file(s) predate %s."
                  % (len(stale), len(files), os.path.relpath(name, ANDROID)))
            for path in stale[:5]:
                print("    %s" % os.path.basename(path))
            print("They are from an earlier run, not from this code: the")
            print("build failed, or that suite no longer exists. Clear")
            print("app/build/test-results and run the tests again.")
            return 1

    total = failures = errors = skipped = 0
    unexpected = []
    ran = {}
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as problem:
            print("Broken XML: %s (%s)" % (os.path.basename(path), problem))
            return 1
        ran[suite.get("name")] = int(suite.get("tests", 0))
        total += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            if case.find("skipped") is not None and (case.get("classname"), case.get("name")) not in MAY_SKIP:
                unexpected.append("%s > %s" % (case.get("classname"), case.get("name")))
        print("  %-40s %s tests" % (os.path.basename(path)[:40],
                                    suite.get("tests", "?")))

    print()
    print("%d tests, %d failed, %d errors, %d skipped, in %d files"
          % (total, failures, errors, skipped, len(files)))

    if failures or errors:
        print("FAILED: read the XML above.")
        return 1
    if unexpected or skipped > len(unexpected) + len(MAY_SKIP):
        print("SKIPPED: a test that does not run is not a test that passed.")
        for name in unexpected[:10]:
            print("    %s" % name)
        return 1
    if total - skipped < FLOOR:
        print("TOO FEW: %d run, under the floor of %d." % (total - skipped, FLOOR))
        print("Either a suite stopped being discovered without saying so,")
        print("or the floor belongs in the same commit that removes tests.")
        return 1
    if len(sys.argv) <= 1:
        short = ["%s: %d written, %s run" % (name.rsplit(".", 1)[-1], count, ran.get(name, "none"))
                 for name, count in sorted(tests_written().items()) if ran.get(name) != count]
        if short:
            print("NOT EVERY TEST RAN: a class's result differs from its file.")
            for line in short[:10]:
                print("    %s" % line)
            print("A class undiscovered, or tests in it that never ran: run")
            print("the whole suite, not a part of it, before this gate.")
            return 1

    print("The tests ran, and they passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
