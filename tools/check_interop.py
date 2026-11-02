"""Fail when the desktop cannot read a share code this app makes.

InteropTest proves this app reads a code the desktop's Python made. The
other half -- the desktop reading a code made here -- was promised by the
test's own words and DATA_FORMAT, and nothing ran it (the final audit's
third wave). This is that half: it takes the code InteropTest writes out,
`app/build/interop/phone_code.txt`, and reads it as the desktop's import
does -- `decode_share_code`, then `normalize_note`, which drops what the
desktop does not keep -- and compares every field the phone meant to send
(`phone_note.json`, written beside it by the same test: a note through the
phone's own `normalizeNote`, carrying every field a code does).

Run it after the unit tests, as check_test_results.py is:

    gradlew.bat testDebugUnitTest
    py tools/check_interop.py

It reads the desktop's Python and never writes there. Exit 2 when there
is nothing fresh to read (the tests not run since the code changed, or the
desktop not found): a cross-program check that could not run must not
read as one that passed.
"""
import glob
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
# Its import of the desktop's code writes no __pycache__ there, as the
# fixture makers' imports do not.
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
OUT = os.path.join(ANDROID, "app", "build", "interop")
CODE = os.path.join(OUT, "phone_code.txt")
NOTE = os.path.join(OUT, "phone_note.json")
# What makes the code: newer than the hand-off means the tests have not run
# since. Every file of the app's code, not the four first named: the note is
# shaped through Recurrence, Exporting, Templates and more besides.
SOURCES = sorted(glob.glob(os.path.join(ANDROID, "app", "src", "main", "java", "**", "*.kt"),
                           recursive=True)) + \
          [os.path.join(ANDROID, "app", "src", "test", "java", "ro", "noteorganizer", "app",
                        "InteropTest.kt")]


def main():
    if not os.path.isfile(CODE) or not os.path.isfile(NOTE):
        print("Nothing to read: run the unit tests first (InteropTest writes %s)"
              % os.path.relpath(OUT, ANDROID))
        return 2
    written = min(os.path.getmtime(CODE), os.path.getmtime(NOTE))
    stale = [os.path.basename(s) for s in SOURCES if os.path.getmtime(s) > written]
    if stale:
        print("Nothing fresh to read: %s changed after the tests last ran" % ", ".join(stale))
        return 2
    if not os.path.isdir(DESKTOP):
        print("cannot find the desktop app at %s (set NOTEORGANIZER_HOME)" % DESKTOP)
        return 2
    sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)      # the desktop's memory ceiling, before its code runs here
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.model import normalize_note
        from noteorganizer.core.sharing import SHAREABLE_FIELDS, decode_share_code
    except ImportError as problem:
        print("cannot import the desktop's decoder: %s" % problem)
        return 2

    code = io.open(CODE, encoding="utf-8").read().strip()
    sent = json.load(io.open(NOTE, encoding="utf-8"))
    missing = sorted(set(SHAREABLE_FIELDS) - set(sent))
    if missing:
        print("FAIL the note sent leaves out fields a code carries: %s" % missing)
        return 1
    notes = decode_share_code(code)
    if not isinstance(notes, list) or len(notes) != 1:
        print("FAIL the desktop's decode_share_code did not give one note back: %r" % (notes,))
        return 1
    read = normalize_note(dict(notes[0]))
    wrong = [field for field, value in sent.items() if read.get(field) != value]
    for field in wrong:
        print("FAIL %s: sent %r, the desktop keeps %r" % (field, sent[field], read.get(field)))
    if wrong:
        print("The desktop reads a code made here differently in %d field(s)." % len(wrong))
        return 1
    print("The desktop reads a code made here as it was meant: all %d fields a code "
          "carries, decoded and normalized as its import does, came back as sent." % len(sent))
    return 0


if __name__ == "__main__":
    sys.exit(main())
