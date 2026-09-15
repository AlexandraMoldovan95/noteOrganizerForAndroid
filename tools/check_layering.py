"""Fail when Android leaks into the four files that must not know about it.

WHY THIS GATE EXISTS
--------------------
`Notebook.kt`, `Sharing.kt`, `Tables.kt` and `Styling.kt` hold the rules
this app was ported from the desktop's Python: what a note is, how a
table is written down, what a share code carries. They contain no
`import android` and no `import androidx`, and that is not an accident to
be preserved by good intentions -- it is what makes 221 unit tests run on
a laptop in seconds instead of on an emulator in minutes.

The leak is easy and quiet. Reach for `android.util.Base64` because it is
the first Base64 in the autocomplete, or for a Compose `Color` because a
category has one, and the file still compiles, the app still runs, and
the tests for it can no longer run without a device. Nobody notices until
the suite that used to take six seconds needs an emulator.

So the rule is checked rather than remembered. The same shape as the
desktop's rule that `core/` never imports tkinter.

    py tools/check_layering.py
    py tools/check_layering.py --show      # print what each file imports

Exit code 0 when the layer is clean, 1 when something leaked.
"""
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
SOURCE = os.path.join(ANDROID, "app", "src", "main", "java", "ro",
                      "noteorganizer", "app")

# The rules layer: ported from Python, tested without a device.
RULES = ["Notebook.kt", "Sharing.kt", "Tables.kt", "Styling.kt"]

# The deliberate exceptions, named here so the list is a decision rather
# than an omission: these two exist to turn rules into pixels.
DRAWS = ["RichText.kt", "TableView.kt", "MainActivity.kt"]

FORBIDDEN = re.compile(r"^\s*import\s+(android|androidx)\b", re.M)
IMPORT = re.compile(r"^\s*import\s+(\S+)", re.M)


def main():
    show = "--show" in sys.argv
    missing = []
    leaked = []

    for name in RULES:
        path = os.path.join(SOURCE, name)
        if not os.path.isfile(path):
            missing.append(name)
            continue
        text = io.open(path, encoding="utf-8", errors="replace").read()
        for match in FORBIDDEN.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            # The whole line, not just the matched prefix: "import android"
            # tells nobody which piece of Android arrived.
            whole = text[match.start():].split("\n", 1)[0].strip()
            leaked.append((name, line, whole))
        if show:
            print("%s:" % name)
            for imported in IMPORT.findall(text):
                print("    %s" % imported)

    if missing:
        # A renamed or moved file is not a pass. The gate would go quiet
        # about exactly the code it exists to watch.
        print("NOT FOUND: %s" % ", ".join(missing))
        print("If those files moved, move the RULES list in this gate too.")
        return 1

    if leaked:
        print("Android has reached the rules layer:")
        for name, line, text in leaked:
            print("    %s:%d  %s" % (name, line, text))
        print()
        print("Those four files are tested on the JVM, with no emulator.")
        print("One import like this moves their tests onto a device.")
        print("If something from Android is genuinely needed, move that")
        print("piece into RichText.kt / TableView.kt / MainActivity.kt,")
        print("which are allowed to know about it.")
        return 1

    print("The rules layer is clean: %s" % ", ".join(RULES))
    print("(the drawing may know about Android: %s)" % ", ".join(DRAWS))
    return 0


if __name__ == "__main__":
    sys.exit(main())
