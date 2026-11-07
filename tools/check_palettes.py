"""Fail when the phone's colours stop being the desktop's colours.

WHY THIS GATE EXISTS
--------------------
A note's colour, a category's colour and a highlight are stored as hex in
the notebook, and the two programs draw them from the same short lists.
When the lists differ, a colour picked on one device is a colour the
other never offers, and a theme called "Ocean Blue" is two different
blues. It had already happened before this gate existed: the phone
started a notebook with three categories while the desktop started one
with five, and it offered eight highlighter shades the desktop has never
heard of.

So the phone's `Themes.kt` is compared, colour for colour, with the
desktop's `core/themes.py`: the eight themes and every role in them, the
names Settings shows, which themes are dark and which light, the starting
categories, the protected category, and the three swatch lists.

It reads the desktop's source as text and never imports or writes it.

    py tools/check_palettes.py
    py tools/check_palettes.py --require-desktop   # in CI: a skip fails
"""
import ast
import io
import os
import re
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))

THEMES_KT = os.path.join(ANDROID, "app", "src", "main", "java", "ro",
                         "noteorganizer", "app", "Themes.kt")
THEMES_PY = os.path.join(DESKTOP, "noteorganizer", "core", "themes.py")

failures = []


def read(path):
    if not os.path.exists(path):
        return None
    return io.open(path, encoding="utf-8", errors="replace").read()


def without_comments(source):
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//[^\n]*", "", source)


def python_constants(source):
    """Every module-level NAME = <literal> in the desktop's file."""
    out = {}
    for node in ast.parse(source).body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            if isinstance(target, ast.Name):
                try:
                    out[target.id] = ast.literal_eval(node.value)
                except ValueError:
                    pass
    return out


def kotlin_block(source, name):
    """The text inside `val NAME ... = listOf(` / `mapOf(` / `linkedMapOf(`."""
    match = re.search(r"val\s+%s\b[^=]*=\s*(?:linkedMapOf|mapOf|listOf)\(" % re.escape(name),
                      source)
    if not match:
        return None
    start = match.end() - 1
    depth = 0
    for position in range(start, len(source)):
        if source[position] == "(":
            depth += 1
        elif source[position] == ")":
            depth -= 1
            if depth == 0:
                return source[start + 1:position]
    return None


def kotlin_strings(block):
    return re.findall(r'"([^"]*)"', block or "")


def kotlin_pairs(block):
    return re.findall(r'"([^"]+)"\s+to\s+"([^"]*)"', block or "")


def kotlin_const(source, name):
    match = re.search(r'const\s+val\s+%s\s*=\s*"([^"]*)"' % re.escape(name), source)
    return match.group(1) if match else None


def kotlin_themes(source):
    """`"name" to mapOf( "role" to "#hex", ... )` blocks inside THEMES."""
    block = kotlin_block(source, "THEMES")
    if block is None:
        return None
    out = {}
    for match in re.finditer(r'"([a-z_]+)"\s+to\s+mapOf\(', block):
        start = match.end() - 1
        depth = 0
        for position in range(start, len(block)):
            if block[position] == "(":
                depth += 1
            elif block[position] == ")":
                depth -= 1
                if depth == 0:
                    out[match.group(1)] = dict(kotlin_pairs(block[start + 1:position]))
                    break
    return out


def compare(what, theirs, ours):
    if theirs == ours:
        print("PASS %s" % what)
    else:
        failures.append(what)
        print("FAIL %s" % what)
        print("       desktop: %s" % (theirs,))
        print("       phone:   %s" % (ours,))


def main():
    kotlin = read(THEMES_KT)
    if kotlin is None:
        print("cannot find %s" % THEMES_KT)
        return 2
    kotlin = without_comments(kotlin)
    python = read(THEMES_PY)
    if python is None:
        print("SKIP: cannot find the desktop app at %s" % DESKTOP)
        print("      (set NOTEORGANIZER_HOME if it lives somewhere else)")
        if "--require-desktop" in sys.argv:
            print("--require-desktop: a SKIP here is a failure, not a pass.")
            return 1
        return 0

    spec = python_constants(python)

    theirs = spec.get("THEMES")
    ours = kotlin_themes(kotlin)
    if not isinstance(theirs, dict) or ours is None:
        failures.append("the themes can be found in both files")
        print("FAIL the themes can be found in both files")
    else:
        compare("the same eight theme names", sorted(theirs), sorted(ours))
        for name in sorted(theirs):
            if name in ours:
                compare("theme %s, every role and colour" % name,
                        dict(sorted((k, v.lower()) for k, v in theirs[name].items())),
                        dict(sorted((k, v.lower()) for k, v in ours[name].items())))

    compare("which themes are dark", spec.get("DARK_THEMES"),
            kotlin_strings(kotlin_block(kotlin, "DARK")))
    compare("which themes are light", spec.get("LIGHT_THEMES"),
            kotlin_strings(kotlin_block(kotlin, "LIGHT")))
    compare("what Settings calls each theme", spec.get("THEME_LABELS"),
            dict(kotlin_pairs(kotlin_block(kotlin, "LABELS"))))
    compare("the categories a new notebook starts with, in order",
            list((spec.get("DEFAULT_CATEGORY_COLORS") or {}).items()),
            kotlin_pairs(kotlin_block(kotlin, "DEFAULT_CATEGORY_COLORS")))
    compare("the category that can never be deleted",
            spec.get("PROTECTED_CATEGORY"), kotlin_const(kotlin, "PROTECTED_CATEGORY"))
    for name in ("PALETTE_COLORS", "PRESET_NOTE_COLORS", "HIGHLIGHT_COLOR_PALETTE"):
        compare("the swatches in %s, in order" % name, spec.get(name),
                kotlin_strings(kotlin_block(kotlin, name)))
    for name in ("LETTERS_LIGHT", "LETTERS_DARK"):
        compare("the words on a fill: %s" % name, spec.get(name), kotlin_const(kotlin, name))

    print()
    if failures:
        print("THE COLOURS: %d disagreement(s) with the desktop." % len(failures))
        return 1
    print("THE COLOURS: the two programs agree.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
