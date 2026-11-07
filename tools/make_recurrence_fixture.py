"""Rebuild the recurrence fixture, using the DESKTOP's own engine.

`app/src/test/resources/recurrence_cases.json` holds a few hundred
repeating notes, each with a range to expand and the days the desktop's
`expand_occurrences` gives -- plus typed dates and what its `read_date`
makes of them, and rules with what its `describe_recurrence` says.
`RecurrenceTest` asks the phone's port the same questions and fails on
any answer that differs.

The hand-written suite checks the cases somebody thought of; this checks
the ones nobody did. The notes are drawn from a fixed seed, so the file
only changes when this script or the desktop's engine does -- and a
change in the desktop's engine is exactly what it exists to catch.

    py tools/make_recurrence_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_recurrence_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import io
import json
import os
import random
import sys
from datetime import date, timedelta

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "recurrence_cases.json")
SEED = 20261015
CASES = 300


def day(rng, start=date(2024, 1, 1), span=1200):
    return start + timedelta(days=rng.randrange(span))


def rule(rng):
    freq = rng.choice(["daily", "weekly", "monthly", "yearly", "", "daily", "weekly", "monthly"])
    out = {"freq": freq}
    if rng.random() < 0.6:
        out["interval"] = rng.choice([1, 1, 2, 3, 5, 7])
    if freq == "weekly" and rng.random() < 0.7:
        out["weekdays"] = rng.sample(range(7), rng.randrange(1, 4))
    if freq == "monthly" and rng.random() < 0.7:
        out["month_days"] = rng.sample([1, 5, 15, 28, 29, 30, 31], rng.randrange(1, 4))
    if rng.random() < 0.3:
        out["month_day_policy"] = rng.choice(["clamp", "skip"])
    ending = rng.random()
    if ending < 0.25:
        # Small counts as often as large ones: a series that has run out
        # before the window is where a count is easiest to get wrong.
        out["count"] = rng.choice([rng.randrange(1, 6), rng.randrange(1, 40)])
    elif ending < 0.45:
        out["end"] = day(rng, date(2025, 1, 1), 900).isoformat()
    return out


def main():
    if not os.path.isdir(DESKTOP):
        print("cannot find the desktop app at %s" % DESKTOP)
        print("(set NOTEORGANIZER_HOME if it lives somewhere else)")
        return 2
    sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)      # the desktop's memory ceiling, before its code runs here
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.recurrence import (
            expand_occurrences, read_date, describe_recurrence, off_schedule_days)
    except ImportError as problem:
        print("cannot import the desktop engine: %s" % problem)
        return 2

    rng = random.Random(SEED)
    cases = []
    for _ in range(CASES):
        anchor = day(rng)
        note = {"id": 1, "date": anchor.isoformat(), "recurrence": rule(rng)}
        if rng.random() < 0.3:
            note["exceptions"] = [day(rng, anchor, 200).isoformat() for _ in range(rng.randrange(1, 4))]
        if rng.random() < 0.25:
            orig = day(rng, anchor, 200).isoformat()
            note["moved"] = {orig: day(rng, anchor, 220).isoformat()}
        if rng.random() < 0.25:
            note["overrides"] = {day(rng, anchor - timedelta(days=30), 260).isoformat(): {"title": "x"}
                                 for _ in range(rng.randrange(1, 3))}
        # Mostly near the first day; one window in four years away from it.
        start = day(rng, anchor - timedelta(days=60), rng.choice([500, 500, 500, 3000]))
        end = start + timedelta(days=rng.choice([0, 6, 30, 92, 400]))
        limit = rng.choice([None, None, None, 1, 3])
        cases.append({
            "note": note, "start": start.isoformat(), "end": end.isoformat(), "limit": limit,
            "days": expand_occurrences(note, start, end, limit=limit),
            "said": describe_recurrence(note),
            "off": off_schedule_days(note),
        })

    # Typed dates: every way the field's form invites, and some it does not.
    parts = ["17", "1", "31", "10", "2", "13", "2026", "26", "0999", "oct", "Oct.", "octombrie",
             "mai", "mar", "Saturday", "Sat", "the", "of", "17th", "x", "12", "29", "2028"]
    seps = [".", "/", "-", " ", ", ", ". "]
    typed = []
    for _ in range(400):
        words = [rng.choice(parts) for _ in range(rng.randrange(1, 5))]
        text = rng.choice(seps).join(words)
        settled = rng.random() < 0.5
        got = read_date(text, settled=settled, today=date(2026, 10, 3))
        typed.append({"text": text, "settled": settled, "date": got.isoformat() if got else None})

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "expand": cases, "read": typed}, f, ensure_ascii=False, indent=0)
    found = sum(1 for c in cases if c["days"])
    print("%d repeating notes (%d with days in range), %d typed dates" % (len(cases), found, len(typed)))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
