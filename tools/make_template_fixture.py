"""Rebuild the template fixture, using the DESKTOP's own normalize_template.

`app/src/test/resources/template_cases.json` holds a few hundred templates
as a hand-edited or old `templates.json` might hold them -- missing
fields, wrong types, junk checklist items, styles valid and not -- each
with what the desktop's `normalize_template` makes of it. `TemplatesTest`
asks the phone's port the same and fails on any answer that differs.

The templates are drawn from a fixed seed, so the file only changes when
this script or the desktop's rule does.

    py tools/make_template_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_template_fixture.py

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
                       "template_cases.json")
SEED = 20261015
CASES = 300

TEXTS = ["", "Weekly plan", "  padded  ", "😀 party", "line one\nline two", "• item\n1. first",
         "| a | b |\n| --- | --- |\n| 1 | 2 |", 3, None, True, ["x"]]
STYLES = ["bold", "italic", "underline", "highlight", "text_color", "font_size", "font_family", "odd"]


def span(rng):
    out = {"start": rng.choice([0, 1, 3, -1, 2.0]), "end": rng.choice([2, 5, 9, 1, 40]),
           "style": rng.choice(STYLES)}
    if out["style"] in ("highlight", "text_color"):
        out["value"] = rng.choice(["#fff3b0", "#ABCDEF", "red", None])
    elif out["style"] == "font_size":
        out["value"] = rng.choice([12, 14, 13, "12", True])
    elif out["style"] == "font_family":
        out["value"] = rng.choice(["Arial", "Georgia", "Comic Sans", 7])
    return out


def template(rng):
    out = {}
    for key in ("name", "title", "body", "comments", "category"):
        if rng.random() < 0.8:
            out[key] = rng.choice(TEXTS)
    if rng.random() < 0.6:
        out["formatting"] = rng.choice([[span(rng) for _ in range(rng.randrange(0, 5))], "junk", None])
    if rng.random() < 0.5:
        out["paragraphs"] = rng.choice([
            [{"line": rng.choice([0, 1, 2, 5, True, -1]), "kind": rng.choice(["bullet", "number", "x", None]),
              "indent": rng.choice([0, 2, 11, -3, True]), "align": rng.choice(["center", "left", "up"])}
             for _ in range(rng.randrange(0, 4))], {}, None])
    if rng.random() < 0.6:
        out["checklist"] = rng.choice([
            [rng.choice([{"text": "milk", "done": True}, {"text": 5, "done": 1}, {"done": ""},
                         {"text": "bread"}, "junk", None, {"text": "eggs", "done": [1]}])
             for _ in range(rng.randrange(0, 6))],
            "junk", {"text": "x"}, None])
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
        from noteorganizer.core.storage import normalize_template
    except ImportError as problem:
        print("cannot import the desktop's normalize_template: %s" % problem)
        return 2

    rng = random.Random(SEED)
    cases = []
    for _ in range(CASES):
        raw = template(rng)
        cases.append({"raw": raw, "normalized": normalize_template(raw)})
    # One the checklist cap reaches, and one that is not a template at all.
    big = {"name": "big", "checklist": [{"text": str(i)} for i in range(1005)]}
    cases.append({"raw": big, "normalized": normalize_template(big)})
    cases.append({"raw": "not a template", "normalized": normalize_template("not a template")})

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "cases": cases}, f, ensure_ascii=False, indent=0)
    print("%d templates" % len(cases))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
