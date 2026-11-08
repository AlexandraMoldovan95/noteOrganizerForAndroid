"""Every mutant in tools/mutants.json still finds what it mutates.

A mutant whose text is no longer in its file cannot be applied, and
tools/mutate.py reports it NO RUN; a catalogue of those reads like
protection and gives none. This reads every entry and fails when:

* an id is used twice, or a field is missing;
* its file is gone, or its `old` text is not found exactly once there;
* its `tests` name a unit test class with no file in app/src/test, or a
  walk part tools/emulator/walk.py does not call;
* it is expected to live (equivalent, or survives) and its `why` does not
  say so: a mutant left alive must say why it may be.

When a fix reshapes the code a mutant aims at, aim the mutant at the code
as it is now, or remove it along with the check it guarded. The desktop's
tools/check_mutant_targets.py holds its catalogue to the same rule.

Run: py tools/check_mutant_targets.py
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOGUE = os.path.join(ROOT, "tools", "mutants.json")
TESTS = os.path.join(ROOT, "app", "src", "test", "java", "ro", "noteorganizer", "app")
WALK = os.path.join(ROOT, "tools", "emulator", "walk.py")
FIELDS = ("id", "file", "old", "new", "why", "tests")


def main():
    with io.open(CATALOGUE, encoding="utf-8") as fh:
        catalogue = json.load(fh)
    with io.open(WALK, encoding="utf-8") as fh:
        parts = set(re.findall(r"^    (\w+)\(folder\)$", fh.read(), re.M))
    seen, problems = set(), []
    for m in catalogue:
        mid = m.get("id", "?")
        # `new` may be empty: a mutant that deletes the text it finds.
        missing = [f for f in FIELDS if f not in m or (not m[f] and f != "new")]
        if missing:
            problems.append("%s: no %s" % (mid, ", ".join(missing)))
            continue
        if mid in seen:
            problems.append("%s: the id is used twice" % mid)
        seen.add(mid)
        path = os.path.join(ROOT, m["file"])
        if not os.path.isfile(path):
            problems.append("%s: %s is gone" % (mid, m["file"]))
            continue
        with open(path, "rb") as fh:
            text = fh.read().replace(b"\r\n", b"\n")
        found = text.count(m["old"].encode("utf-8"))
        if found != 1:
            problems.append("%s: its text is found %d times in %s" % (mid, found, m["file"]))
        for name in m["tests"].split(","):
            name = name.strip()
            if name.startswith("walk:"):
                if name[5:] not in parts:
                    problems.append("%s: the walk has no part %s" % (mid, name[5:]))
            elif not os.path.isfile(os.path.join(TESTS, name + ".kt")):
                problems.append("%s: no test class %s" % (mid, name))
        # AS533 was marked equivalent with only what it would do, not why.
        expect = m.get("expect", "killed")
        if expect != "killed" and expect not in m["why"]:
            problems.append("%s: expected to live (%s), and its why does not say so, nor why" % (mid, expect))
    for line in problems:
        print("  " + line)
    if problems:
        print("%d problem(s) in the catalogue: aim each mutant at the code as it is now, or "
              "drop it with the check it guarded; one left alive says why." % len(problems))
        return 1
    print("Every one of the %d mutants has its own id and finds the code it mutates." % len(catalogue))
    return 0


if __name__ == "__main__":
    sys.exit(main())
