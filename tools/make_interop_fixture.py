"""Rebuild the interop fixture, using the DESKTOP's own encoder.

`app/src/test/resources/desktop_code.txt` is the one thing in this
repository that must not be made here: it is a share code produced by the
Windows app's Python, and `InteropTest` proves this app can read it. A
fixture this project generated itself would only prove it agrees with
itself.

The note inside it is invented, on purpose. The fixture's worth comes
from WHO encoded it, not from whose writing is inside -- and a share code
is not encryption, so anyone who can read this repository can read the
note in it. It used to hold a real note from a real notebook.

    py tools/make_interop_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_interop_fixture.py

Reads the desktop's Python and writes only this repository's fixture. If
the note below changes, the assertions in `InteropTest` change with it --
the script prints what they should say.
"""
import io
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True      # no __pycache__ left in the desktop's tree

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "desktop_code.txt")

# Written for the test. The root and multiplication signs, the Romanian
# diacritics and the emoji are the point of the body: they are the
# characters that break first when an encoding is wrong anywhere along the
# trip, and the emoji is the one the desktop writes as four raw bytes.
# Every field a code carries has a value of its own, none a default, so the
# test can hold each one -- and the note goes through the desktop's own
# normalize_note first, as a note the desktop app shares does.
NOTE = {
    "uid": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
    "title": "Sums for the test",
    "body": "2 \u00d7 3 = 6\n\u221a16 = 4\n\u03c0 \u2248 3,14\n"
            "Diacritics: \u0219tefan, ma\u0219in\u0103, \u021bara\n"
            "Steps \U0001f463 counted\n\u2022 a bullet",
    "category": "Work",
    "comments": "a comment, so there is one of those to carry too",
    "checklist": [
        {"text": "first row", "done": True},
        {"text": "second row", "done": False},
        {"text": "third row", "done": True},
        {"text": "fourth row", "done": False},
        {"text": "fifth row", "done": False},
        {"text": "sixth row", "done": True},
        {"text": "seventh row", "done": False},
        {"text": "eighth row", "done": False},
    ],
    "formatting": [
        {"start": 0, "end": 9, "style": "bold"},
        {"start": 10, "end": 16, "style": "italic"},
        {"start": 17, "end": 26, "style": "underline"},
    ],
    "color": "#4cc9f0",
    "date": "2026-11-02",
    "recurrence": {"freq": "weekly", "interval": 2, "weekdays": [0]},
    "paragraphs": [{"line": 5, "kind": "bullet"}],
}


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
        from noteorganizer.core.model import normalize_note
        from noteorganizer.core.sharing import (SHAREABLE_FIELDS, encode_share_code,
                                                decode_share_code)
    except ImportError as problem:
        print("cannot import the desktop encoder: %s" % problem)
        return 2

    def kept(made, meant):
        """What NOTE set is there as set; a dict may gain the desktop's defaults."""
        if isinstance(meant, dict):
            return isinstance(made, dict) and all(kept(made.get(k), v) for k, v in meant.items())
        return made == meant

    shaped = normalize_note(dict(NOTE))
    lost = sorted(f for f in SHAREABLE_FIELDS if not kept(shaped.get(f), NOTE.get(f)))
    if lost:
        # The test holds each field to NOTE: one the desktop reshapes would
        # be held to a value it never sends.
        print("normalize_note changed %s: write NOTE as the desktop keeps it" % lost)
        return 1
    code = encode_share_code([shaped])

    # Decoded by the same Python that made it, before anything is written:
    # a fixture nobody has read back is a guess.
    back = decode_share_code(code)
    note = (back["notes"] if isinstance(back, dict) else back)[0]
    print("code produced by the desktop's own Python: %d characters, "
          "every field as NOTE says: %s"
          % (len(code), all(kept(note.get(f), NOTE[f]) for f in SHAREABLE_FIELDS)))

    io.open(FIXTURE, "w", encoding="utf-8", newline="\n").write(code)
    print()
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
