"""Rebuild the unknown-fields fixture, using the DESKTOP's own unknown_fields.

`app/src/test/resources/unknown_cases.json` holds a few hundred notes carrying
fields neither app knows -- a newer version's, or another program's -- each with
what the desktop's `unknown_fields` keeps of them. `UnknownFieldsTest` asks the
phone's `Normalize.unknownFields` the same and fails on any answer that differs:
the fields kept, their values, which are left out at each bound (how many, how
long, how deep), and the order they are chosen in, by their names as UTF-16
(an emoji's name sorts before one in U+E000-U+FFFF there, after it by code point).

The notes are drawn from a fixed seed, so the file only changes when this
script or the desktop's rule does. The owner's decision D2, 2026-11-13.

    py tools/make_unknown_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_unknown_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import io
import json
import os
import random
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources", "unknown_cases.json")
SEED = 20261113
CASES = 300

NAMES = ["mood", "weather", "x", "", "Z", "a b", "émoji", "\U0001F600face", "private", "￯end",
         "_mark", "_prev", "deleted_at", "tags", "title", "uid", "order", "id", "pinned", "v2_field"]
SCALARS = ["", "calm", "😀😀", "", 0, 7, -3, 2 ** 70, 1.5, -0.25, True, False, None]


def nest(depth, core="core"):
    value = core
    for _ in range(depth):
        value = [value]
    return value


def nest_object(depth, core="core"):
    value = core
    for _ in range(depth):
        value = {"k": value}
    return value


def value(rng, depth=1):
    pick = rng.random()
    if pick < 0.45 or depth > 3:
        return rng.choice(SCALARS)
    if pick < 0.6:
        # Short: the allowance is met by the edge cases below, which keeps the
        # file small (long strings drawn at random made it 7.5 MB).
        return "w" * rng.choice([1, 100, 1000])
    if pick < 0.8:
        return [value(rng, depth + 1) for _ in range(rng.randrange(0, 4))]
    return {rng.choice(NAMES[:9]): value(rng, depth + 1) for _ in range(rng.randrange(0, 4))}


def note(rng):
    out = {"id": rng.randrange(1, 9), "title": "T"}
    for _ in range(rng.randrange(0, 6)):
        out[rng.choice(NAMES)] = value(rng)
    if rng.random() < 0.15:
        for i in range(rng.randrange(30, 45)):
            out["f%02d" % i] = i
    if rng.random() < 0.1:
        out["deep"] = nest(rng.choice([19, 20, 21, 22]))
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
        from noteorganizer.core.model import unknown_fields
    except ImportError as problem:
        print("cannot import the desktop's unknown_fields: %s" % problem)
        return 2

    rng = random.Random(SEED)
    raws = [note(rng) for _ in range(CASES)]
    # The edges by name: the cap with names whose UTF-16 and code point orders
    # differ, the allowance met exactly and passed by one, depth 20 and 21.
    raws.append(dict({"id": 1}, **{("\U0001F600%02d" % i): i for i in range(20)},
                     **{("%02d" % i): i for i in range(20)}))
    raws.append({"id": 1, "a": "x" * (65536 - 2), "b": "y"})
    raws.append({"id": 1, "a": "x" * (65536 - 1), "b": "y"})
    raws.append({"id": 1, "a": "😀" * 20000, "b": "x" * 25530, "c": "y"})
    raws.append({"id": 1, "deep": nest(19), "deeper": nest(20), "deepest": nest(21)})
    raws.append({"id": 1, "odeep": nest_object(20), "odeeper": nest_object(21)})
    raws.append({"id": 1, "obj": {"k": {"k": {"k": "v"}}}, "list": [[], {}, [None]]})
    cases = [{"raw": raw, "kept": unknown_fields(raw)} for raw in raws]

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "cases": cases}, f, ensure_ascii=False, indent=0)
    print("%d notes" % len(cases))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
