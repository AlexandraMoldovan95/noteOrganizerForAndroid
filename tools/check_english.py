"""Fail when Romanian reaches anything a reader of this repository sees.

WHY THIS GATE EXISTS
--------------------
The work here is discussed in Romanian and written down in English, and
the line between the two is easy to cross without noticing. It was
crossed: the qa stories named their checks in Romanian, four screenshots
carried Romanian filenames, and every CI run printed those names into a
log anybody can open.

Nothing about that was wrong while it lived on one machine. It stops
being a private note the moment it is pushed.

THE BOUNDARY HERE
-----------------
Until 2026-10-15 this app spoke Romanian to its users, and everything
under `app/` was exempt for that reason. It speaks English now, like the
desktop, so the exemption went with it: the app, its tests and its
strings are read like everything else.

What is left are exceptions by file, each with its reason recorded
below. Most are documents that quote what the app USED to draw, or that
explain a behaviour which cannot be explained without showing the
spelling. A few files may carry diacritics but still no Romanian words:
search folds "notiță" into "notita", and the code and tests that prove it
have to write both.

    py tools/check_english.py
    py tools/check_english.py --list     # every hit, not just the first few

Exit code 0 when clean, 1 when something slipped through.

WHAT IT READS
-------------
Every text file git tracks, minus binaries and the exceptions below. It
asks git rather than walking the disk, so an untracked scratch file
cannot fail the build and a tracked one cannot hide from it.
"""
import io
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

# Words that are Romanian and are not also English. Short ones like "a",
# "la" or "un" are left out on purpose: they appear inside English prose
# and in code as identifiers, and a gate that cries wolf gets switched
# off. These are enough -- they carry ordinary Romanian sentences.
WORDS = [
    "acest", "aceasta", "acum", "adauga", "arata", "cand", "cauta",
    "daca", "deci", "dupa", "este", "fara", "fiindca", "fisier",
    "fisierul", "fisiere", "inainte", "inapoi", "notita", "notite",
    "pentru", "poate", "ramane", "sterge", "sters", "stearsa", "sunt",
    "toate", "trebuie", "verifica", "vreau",
    # Added 2026-10-15, when the app's own exemption went and these were
    # found sitting in a CI message, a tool's output and test fixtures
    # that the list above had walked straight past.
    "aici", "asteptat", "codul", "cumparaturi", "editata", "facut",
    "lapte", "lipseste", "nimic", "paine", "salvarea", "tastat",
    "valoarea", "veche", "vechi",
]
ROMANIAN = re.compile(r"\b(%s)\b" % "|".join(WORDS), re.I)

# Words checked only against FILENAMES. They are too short, or too close
# to something a program legitimately says, to search for inside code --
# `cos` is the cosine the icon is drawn with -- but in a filename there
# is nothing else they could be.
FILENAME_ONLY = ["lista", "setari", "categorii", "cos", "cosul", "tema",
                 "deschisa", "poza", "poze", "ecran", "ecrane", "nota",
                 "tabel", "cautare", "cautare", "caiet"]
IN_FILENAME = re.compile(r"\b(%s)\b" % "|".join(WORDS + FILENAME_ONLY), re.I)

# Romanian diacritics. Any of them, anywhere, in a repository whose app
# speaks English.
DIACRITICS = re.compile(r"[ăâîșțĂÂÎȘȚ]")

# Extensions worth reading. Everything else is either binary or not prose.
TEXT = (".py", ".md", ".txt", ".yml", ".yaml", ".json", ".kt", ".kts",
        ".xml", ".cfg", ".toml", ".ini", ".bat", ".sh", ".properties")

# Exceptions, each with the reason it is one. An exception nobody can
# read is not a reviewed exception -- the same rule the note-shape gate
# keeps.
ALLOWED = {
    "tools/check_english.py":
        "this gate: the wordlist it searches for is necessarily Romanian",
    "docs/ISSUES_LOG.md":
        "quotes the search placeholder the app drew when it spoke Romanian, "
        "in the story about a tap that could not find it",
}

# Files where the LETTERS are allowed but the words are still read. Search
# folds diacritics ("notiță" finds "notita"), and the code that does it,
# and the tests that prove it, cannot do so without writing both.
MARKS_ONLY = {
    "app/src/main/java/ro/noteorganizer/app/Notebook.kt":
        "the doc comment on the search fold shows what it folds",
    "app/src/test/java/ro/noteorganizer/app/NotebookTest.kt":
        "the tests that search with and without diacritics",
    "app/src/test/java/ro/noteorganizer/app/FindingTest.kt":
        "the tests that Find in note matches both Romanian spellings of a letter",
    "app/src/test/java/ro/noteorganizer/app/SharingTest.kt":
        "a share code must carry diacritics through untouched",
    "app/src/test/java/ro/noteorganizer/app/InteropTest.kt":
        "the desktop's own code carries diacritics, and they must arrive",
    "CHANGELOG.md":
        "explains that search folds diacritics, which cannot be explained "
        "without printing both spellings",
    "docs/STATUS.md":
        "the same explanation, where the app's differences are listed",
}


def tracked():
    """Every path git tracks here -- or a refusal.

    Asking git is what makes an untracked scratch file harmless and a
    tracked one impossible to hide. It also means that OUTSIDE a git
    checkout this gate has nothing to read, and a gate with nothing to
    read must not print a pass: "0 files, all clean" is the shape of
    every check that has quietly stopped working.
    """
    out = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True,
                         text=True, encoding="utf-8", errors="replace")
    names = [line.strip() for line in out.stdout.splitlines() if line.strip()]
    if out.returncode != 0 or not names:
        print("Cannot list the tracked files (is %s a git checkout?)." % ROOT)
        print("This gate reads what git tracks; with nothing to read it")
        print("refuses rather than reporting everything clean.")
        sys.exit(2)
    return names


def tracked_files():
    for name in tracked():
        if os.path.splitext(name)[1].lower() in TEXT:
            yield name


def hits_in(relative, marks_allowed=False):
    path = os.path.join(ROOT, relative)
    try:
        text = io.open(path, encoding="utf-8", errors="replace").read()
    except OSError as exc:
        # A tracked file not read is not a clean one.
        return [(0, "(unread)", "the file could not be read: %s" % exc)]
    found = []
    for number, line in enumerate(text.splitlines(), start=1):
        word = ROMANIAN.search(line)
        mark = None if marks_allowed else DIACRITICS.search(line)
        if word or mark:
            found.append((number, (word or mark).group(0), line.strip()))
    return found


def main():
    show_all = "--list" in sys.argv
    leaks = []
    named = []

    # A listed file that no longer exists is a reason nobody is checking.
    names = set(tracked())
    gone = sorted(set(MARKS_ONLY) - names)
    if gone:
        print("MARKS_ONLY names file(s) git does not track: %s" % ", ".join(gone))
        print("If they moved, move the list too; if they went, drop the line.")
        return 1

    for relative in tracked_files():
        if relative in ALLOWED:
            continue
        for number, word, line in hits_in(relative, relative in MARKS_ONLY):
            leaks.append((relative, number, word, line))

    # Filenames are read too: a picture called 04-setari-cos.png says it
    # in the file listing, before anybody opens anything.
    for name in tracked():
        stem = os.path.basename(name).rsplit(".", 1)[0].replace("-", " ")
        if name not in ALLOWED and IN_FILENAME.search(stem):
            named.append(name)

    if not leaks and not named:
        print("English throughout: %d tracked text file(s) read, plus every "
              "tracked filename." % sum(1 for _ in tracked_files()))
        return 0

    if named:
        print("Romanian in FILENAMES (%d):" % len(named))
        for name in named:
            print("    %s" % name)
        print()

    if leaks:
        print("Romanian in CONTENT (%d line(s) in %d file(s)):"
              % (len(leaks), len({l[0] for l in leaks})))
        shown = leaks if show_all else leaks[:12]
        for relative, number, word, line in shown:
            print("    %s:%d  [%s]  %s" % (relative, number, word, line[:70]))
        if len(shown) < len(leaks):
            print("    ... and %d more (--list for all)"
                  % (len(leaks) - len(shown)))
        print()

    print("This repository is English, the app included. Translate it, or")
    print("add it to ALLOWED (or MARKS_ONLY, for letters without words) in")
    print("this gate WITH the reason it belongs there.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
