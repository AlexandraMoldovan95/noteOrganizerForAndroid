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
This app speaks Romanian to its users, and that is the product rather
than a leak: "Sterge notita" is a button, not a note somebody forgot to
translate. So the line this gate draws is not "no Romanian" -- it is
"no Romanian in anything written for a DEVELOPER".

Everything under `app/` is exempt, with its reason recorded below: the
strings the app draws, and the tests that assert those exact words back.
A handful of documents are exempt too, each because it quotes the app or
explains a behaviour it cannot explain without showing the spelling.

Everything else -- the tools, the workflows, the READMEs, CLAUDE.md --
is read, and must be English. That is where a CI log comes from.

(The desktop repository has the opposite shape: its interface is English,
so nothing there is exempt at all.)

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
    "CHANGELOG.md":
        "explains that search folds diacritics, which cannot be explained "
        "without printing both spellings",
    "docs/STATUS.md":
        "the same diacritics explanation, and it quotes the app's own "
        "buttons ('Adauga ca notita noua') where the wording is the point",
    "docs/ISSUES_LOG.md":
        "quotes the search placeholder the app draws, in the story about a "
        "tap that could not find it",
    "tools/emulator/take_screenshots.py":
        "the seeded notebook, and the words each shot is checked against, "
        "ARE the app's own screen -- an English word would match no button",
    "tools/make_interop_fixture.py":
        "the fixture's note is content inside a Romanian-speaking app",
}

# Whole trees, with the reason. Coarser than a file on purpose:
# everything under here is either what the app says to its users or a
# test asserting those exact words back.
ALLOWED_PREFIXES = {
    "app/":
        "the app speaks Romanian to its users -- the product, not a leak -- "
        "and its tests assert those strings literally",
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


def exempt_tree(relative):
    """True when a path sits under an allowed tree, with a reason."""
    return any(relative.startswith(prefix) for prefix in ALLOWED_PREFIXES)


def tracked_files():
    for name in tracked():
        if os.path.splitext(name)[1].lower() in TEXT:
            yield name


def hits_in(relative):
    path = os.path.join(ROOT, relative)
    try:
        text = io.open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return []
    found = []
    for number, line in enumerate(text.splitlines(), start=1):
        word = ROMANIAN.search(line)
        mark = DIACRITICS.search(line)
        if word or mark:
            found.append((number, (word or mark).group(0), line.strip()))
    return found


def main():
    show_all = "--list" in sys.argv
    leaks = []
    named = []

    for relative in tracked_files():
        if relative in ALLOWED or exempt_tree(relative):
            continue
        for number, word, line in hits_in(relative):
            leaks.append((relative, number, word, line))

    # Filenames are read too: a picture called 04-setari-cos.png says it
    # in the file listing, before anybody opens anything.
    for name in tracked():
        stem = os.path.basename(name).rsplit(".", 1)[0].replace("-", " ")
        if (name not in ALLOWED and not exempt_tree(name)
                and IN_FILENAME.search(stem)):
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

    print("The app may speak Romanian; this does not. Anything written for")
    print("a developer -- tools, workflows, documents, filenames -- is read")
    print("by people who do not. Translate it, or add it to ALLOWED in this")
    print("gate WITH the reason it belongs there.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
