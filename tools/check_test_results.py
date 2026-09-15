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

FLOOR = 200


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

    total = failures = errors = skipped = 0
    for path in files:
        try:
            suite = ET.parse(path).getroot()
        except ET.ParseError as problem:
            print("Broken XML: %s (%s)" % (os.path.basename(path), problem))
            return 1
        total += int(suite.get("tests", 0))
        failures += int(suite.get("failures", 0))
        errors += int(suite.get("errors", 0))
        skipped += int(suite.get("skipped", 0))
        print("  %-40s %s tests" % (os.path.basename(path)[:40],
                                    suite.get("tests", "?")))

    print()
    print("%d tests, %d failed, %d errors, %d skipped, in %d files"
          % (total, failures, errors, skipped, len(files)))

    if failures or errors:
        print("FAILED: read the XML above.")
        return 1
    if total < FLOOR:
        print("TOO FEW: %d, under the floor of %d." % (total, FLOOR))
        print("Either a suite stopped being discovered without saying so,")
        print("or the floor belongs in the same commit that removes tests.")
        return 1

    print("The tests ran, and they passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
