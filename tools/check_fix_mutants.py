"""Every change to the app's code names its mutant, or why it has none.

A fix comes with a test that fails when the fix is broken on purpose, and
that mutant is what proves the test bites. The rule was asked of the agent
in words; this gate holds it for every commit from START on: a commit that
changes anything under app/src/main carries, on a line of its own,

    Mutant: AS591             (one id or more, each in tools/mutants.json)
    No mutant: <why>          (words only, a move, a write no test reaches)

Commits before START, merges, and commits that change only tests, tools or
documents are not read for it. A shallow clone is refused: its cut commit
has no parents, so it reads as changing every file, and a merge there is no
merge to --no-merges (a pull request's merge commit failed so).
The desktop's repository holds the same rule with a gate of the same name.

    py tools/check_fix_mutants.py
"""
import json
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
START = "2026-11-20"
APP = re.compile(r"^app/src/main/")
NAMED = re.compile(r"^Mutant: ([A-Z]{1,5}\d+(?:, *[A-Z]{1,5}\d+)*) *$", re.M)
NONE = re.compile(r"^No mutant: .{8,}$", re.M)


def problem(message, known):
    """What is wrong with a commit message that changes the app's code, or None."""
    named = NAMED.findall(message)
    if named:
        ids = [i.strip() for line in named for i in line.split(",")]
        unknown = [i for i in ids if i not in known]
        return "names a mutant the catalogue lacks: " + ", ".join(unknown) if unknown else None
    if NONE.search(message):
        return None
    return 'says neither "Mutant: <id>" nor "No mutant: <why>"'


def self_test(known):
    """The rule asked of messages it was written for, before any history is read."""
    some = sorted(known)[0]
    cases = [("fix\n\nMutant: " + some + "\n", None), ("fix\n\nNo mutant: the words only changed\n", None),
             ("fix\n\nnothing said\n", "neither"), ("fix\n\nMutant: ZZ99999\n", "lacks"),
             ("fix\n\nNo mutant:\n", "neither"), ("fix\n\nthe mutant: " + some + " in a sentence\n", "neither")]
    for message, want in cases:
        got = problem(message, known)
        if (want is None) != (got is None) or (want and want not in got):
            sys.exit("check_fix_mutants: its own rule answers %r wrongly (%r)" % (message, got))


def git(*args):
    return subprocess.run(["git"] + list(args), cwd=ROOT, capture_output=True, text=True, encoding="utf-8",
                          check=True, timeout=120).stdout


def main():
    known = {m["id"] for m in json.load(open(os.path.join(ROOT, "tools", "mutants.json"), encoding="utf-8"))}
    if not known:
        sys.exit("check_fix_mutants: the catalogue holds no mutant; a gate with nothing to read must not pass")
    self_test(known)
    if git("rev-parse", "--is-shallow-repository").strip() == "true":
        sys.exit("check_fix_mutants: the history is cut short (a shallow clone), so the commit at the cut "
                 "reads as changing every file; check out all of it (actions/checkout: fetch-depth: 0)")
    commits = git("log", "--no-merges", "--since=" + START + "T00:00:00", "--format=%H", "HEAD").split()
    read, bad = 0, []
    for sha in commits:
        files = git("show", "--name-only", "--format=", sha).split("\n")
        if not any(APP.match(f) for f in files if f):
            continue
        read += 1
        why = problem(git("show", "-s", "--format=%B", sha), known)
        if why:
            bad.append("%s %s: %s" % (sha[:8], git("show", "-s", "--format=%s", sha).strip(), why))
    for line in bad:
        print(line)
    if bad:
        print("%d commit(s) change the app's code and name no mutant, nor why there is none." % len(bad))
        sys.exit(1)
    print("Every change to the app's code since %s names its mutant or why it has none: %d commit(s) read, "
          "%d of them the app's." % (START, len(commits), read))


main()
