"""The test plan's numbers, where the documents give them, are the plan's own.

The plan is a workbook, qa/NoteOrganizer_Android_TestPlan.xlsx, whose
Summary counts itself with formulas -- which only a spreadsheet program
works out. Its counts are copied into qa/README.md by hand, as the
desktop's were into three documents, where they drifted. This counts the
rows itself and fails when:

* a case has no ID, or one another case has;
* a case's status is one the Summary does not count, or its area is
  missing from the Summary's list by area, so the case is counted nowhere
  (35 were, until 7 November 2026);
* a Summary formula stops short of the last case;
* qa/README.md gives a number of cases or of areas the plan does not
  have, or names areas other than the plan's.

The sentence it reads must be found: a gate that cannot find what it
checks fails, rather than passing over it. The desktop's
tools/check_test_plan.py holds its own plan the same way.

Run: py tools/check_test_plan.py
"""
import collections
import io
import os
import re
import sys

import openpyxl

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAN = os.path.join(ROOT, "qa", "NoteOrganizer_Android_TestPlan.xlsx")
README = os.path.join(ROOT, "qa", "README.md")
CASES, SUMMARY = "Test Cases", "Summary"
RANGE = re.compile(r"'Test Cases'!\$?([A-Z]+)\$?(\d+):\$?([A-Z]+)\$?(\d+)")
COUNTED = re.compile(r"'Test Cases'!\$?([A-Z]+)\$?\d+:\$?[A-Z]+\$?\d+,\"([^\"*]+)\"")
WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
         "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
         "eighteen", "nineteen", "twenty", "twenty-one", "twenty-two", "twenty-three",
         "twenty-four", "twenty-five"]


def read_plan():
    """(cases, problems): every case as {header: value}, and what is wrong
    with the workbook itself."""
    wb = openpyxl.load_workbook(PLAN)
    problems = []
    rows = list(wb[CASES].iter_rows(values_only=True))
    header = [str(h).strip() if h is not None else "" for h in rows[0]]
    letters = {openpyxl.utils.get_column_letter(i + 1): name for i, name in enumerate(header)}
    cases, last = [], 1
    for n, row in enumerate(rows[1:], start=2):
        if all(v is None or str(v).strip() == "" for v in row):
            continue
        last = n
        cases.append({name: ("" if v is None else str(v).strip()) for name, v in zip(header, row)})

    for c in cases:
        if not c["ID"]:
            problems.append("a case with no ID: %r" % c["Title"][:60])
    for ident, times in collections.Counter(c["ID"] for c in cases).items():
        if ident and times > 1:
            problems.append("case ID %s is used %d times" % (ident, times))

    counted = collections.defaultdict(set)
    for row in wb[SUMMARY].iter_rows():
        for cell in row:
            formula = cell.value if isinstance(cell.value, str) and cell.value.startswith("=") else ""
            for _col, _top, _end_col, end in RANGE.findall(formula):
                if int(end) < last:
                    problems.append("%s!%s counts the cases to row %s; the last case is on row %d"
                                    % (SUMMARY, cell.coordinate, end, last))
            for col, value in COUNTED.findall(formula):
                counted[letters.get(col, col)].add(value)
    for column in ("Status", "Area"):
        if not counted[column]:
            problems.append("the Summary counts no %s at all" % column.lower())
            continue
        stray = collections.Counter(c[column] for c in cases if c[column] not in counted[column])
        for value, times in sorted(stray.items()):
            problems.append("%d case(s) with %s %r, which the Summary does not count"
                            % (times, column.lower(), value))
    return cases, problems


def check_readme(cases):
    """What qa/README.md says of the plan, against the plan."""
    with io.open(README, encoding="utf-8") as fh:
        text = fh.read()
    pattern = (r"`NoteOrganizer_Android_TestPlan\.xlsx` — ([\d,]+) cases on [^,]+,\s+"
               r"across ([\w-]+) areas \(([^)]*)\)")
    m = re.search(pattern, text)
    if not m:
        return ["qa/README.md: the sentence giving the plan's cases and areas is not found "
                "(the gate reads %r)" % pattern]
    problems = []
    total, word, names = int(m.group(1).replace(",", "")), m.group(2), m.group(3)
    areas = {c["Area"] for c in cases}
    if total != len(cases):
        problems.append("qa/README.md says the plan has %d cases; it has %d" % (total, len(cases)))
    if word not in WORDS or WORDS.index(word) != len(areas):
        problems.append("qa/README.md says the cases are across %s areas; they are across %d"
                        % (word, len(areas)))
    listed = re.sub(r"\band since Phase \d+\b", ",", " ".join(names.split()))
    listed = {n.strip() for n in re.split(r",|\band\b", listed) if n.strip()}
    if listed != areas:
        problems.append("qa/README.md names areas the plan does not have (%s), or leaves out some it has (%s)"
                        % (", ".join(sorted(listed - areas)) or "none", ", ".join(sorted(areas - listed)) or "none"))
    return problems


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    cases, problems = read_plan()
    problems += check_readme(cases)
    if problems:
        print("The test plan and what is said of it disagree:")
        for p in problems:
            print("  - " + p)
        return 1
    status = collections.Counter(c["Status"] for c in cases)
    print("The test plan counts itself and is counted right: %d cases, %d passed, %d not yet walked, "
          "in %d areas; qa/README.md agrees." % (len(cases), status["Pass"], status["Not Tested"],
                                                 len({c["Area"] for c in cases})))
    return 0


if __name__ == "__main__":
    sys.exit(main())
