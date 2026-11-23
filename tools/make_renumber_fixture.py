"""Rebuild the renumbering fixture, using the DESKTOP's own renumber.

`app/src/test/resources/renumber_cases.json` holds a few hundred bodies --
numbered and bulleted items at several depths, blank lines between them,
prose, tables -- with each line's indent, and what the desktop's
`renumber` makes of the body. The paragraphs it is given are the ones the
desktop's editor gives it: the kind read off each line's marker
(`_extract_paragraphs`), the indent from the line. `ParagraphsTest` asks
the phone's port the same and fails on any body that differs.

    py tools/make_renumber_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_renumber_fixture.py

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
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "renumber_cases.json")
SEED = 20261015
CASES = 300

LINES = ["1. one", "2. two", "7. seven", "12. twelve", "٣. three", "1.no space", "• dot", "◦ ring",
         "▪ square", "plain words", "", "", "   ", "| a | b |", "10. ten", "1. "]


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
        from noteorganizer.core.formatting import renumber, parse_list_marker
    except ImportError as problem:
        print("cannot import the desktop's renumber: %s" % problem)
        return 2

    rng = random.Random(SEED)
    cases = []
    for _ in range(CASES):
        lines = [rng.choice(LINES) for _ in range(rng.randrange(1, 12))]
        indents = [rng.choice([0, 0, 0, 1, 1, 2, 3]) for _ in lines]
        paragraphs = []
        for n, (line, indent) in enumerate(zip(lines, indents)):
            kind = parse_list_marker(line)[0]
            if kind or indent:
                entry = {"line": n}
                if kind:
                    entry["kind"] = kind
                if indent:
                    entry["indent"] = indent
                paragraphs.append(entry)
        body = "\n".join(lines)
        renumbered, _shifts = renumber(body, paragraphs)
        cases.append({"body": body, "indents": indents, "renumbered": renumbered})

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "cases": cases}, f, ensure_ascii=False, indent=0)
    changed = sum(1 for c in cases if c["body"] != c["renumbered"])
    print("%d bodies, %d renumbered" % (len(cases), changed))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
