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

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "desktop_code.txt")

# Written for the test. The root and multiplication signs and the
# Romanian diacritics are the point of the body: they are the characters
# that break first when an encoding is wrong anywhere along the trip.
NOTE = {
    "title": "Sums for the test",
    "body": "2 \u00d7 3 = 6\n\u221a16 = 4\n\u03c0 \u2248 3,14\n"
            "Diacritics: \u0219tefan, ma\u0219in\u0103, \u021bara",
    "category": "General",
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
    "color": "",
    "date": "",
    "recurrence": None,
    "paragraphs": {},
}


def main():
    if not os.path.isdir(DESKTOP):
        print("cannot find the desktop app at %s" % DESKTOP)
        print("(set NOTEORGANIZER_HOME if it lives somewhere else)")
        return 2
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.sharing import (encode_share_code,
                                                decode_share_code)
    except ImportError as problem:
        print("cannot import the desktop encoder: %s" % problem)
        return 2

    code = encode_share_code([NOTE])

    # Decoded by the same Python that made it, before anything is written:
    # a fixture nobody has read back is a guess.
    back = decode_share_code(code)
    note = (back["notes"] if isinstance(back, dict) else back)[0]

    print("code produced by the desktop's own Python: %d characters"
          % len(code))
    print()
    print("What InteropTest must say about it:")
    print('    assertEquals("%s", note.getString("title"))' % note["title"])
    print('    assertEquals(%d, note.getJSONArray("formatting").length())'
          % len(note.get("formatting") or []))
    print('    assertEquals(%d, note.getJSONArray("checklist").length())'
          % len(note.get("checklist") or []))
    print('    assertEquals("%s", note.getString("comments"))'
          % note.get("comments", ""))
    print('    assertTrue(note.getString("body").contains("\u221a"))  -> %s'
          % ("\u221a" in note.get("body", "")))

    io.open(FIXTURE, "w", encoding="utf-8", newline="\n").write(code)
    print()
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
