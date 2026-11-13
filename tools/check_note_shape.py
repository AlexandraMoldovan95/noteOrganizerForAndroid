"""Fail when the two programs stop agreeing about what a note is.

WHY THIS GATE EXISTS
--------------------
This project's whole premise is that the phone and the Windows desktop
app read and write the SAME file. Nothing enforces that. The agreement
lives in two languages, in two repositories, and drifts silently: the
code keeps compiling, every test keeps passing, and the damage only shows
up on a real notebook that has travelled.

It has already happened twice in three days, both found by reading rather
than by any check:

* The desktop gained a `uid` field. This app was blind to it until
  somebody noticed -- and a notebook written by a blind app is a notebook
  whose notes cannot be recognised when they come back.
* The rule "what fields does a new note have" was written out twice
  inside THIS app (`createNote`, and `fillNewNoteDefaults` for an
  imported note -- since replaced by `Normalize.normalizeNote`, the
  choke point every note from outside passes), and a third copy sat in
  `importShareCode` until it was removed. Add a field tomorrow and one
  copy gets it while the others rot, with their tests still passing,
  because the tests test the copy.

The desktop has a gate for the same shape of failure
(`tools/check_one_definition.py`, "one definition per judgement"). This
is its Android counterpart, and it does one thing that one cannot: it
reads the Python as the specification and compares.

WHAT IT CHECKS
--------------
1. The note's field set, as this app writes it, equals the desktop's
   `NOTE_DEFAULTS`.
2. The two places here that build a new note name the same fields as
   each other.
3. The fields a share code carries match the desktop's `SHAREABLE_FIELDS`.
4. The fields an arriving shared note may replace match the desktop's
   `UPDATABLE_FROM_SHARED`, and the ones a note from a backup may replace
   match `UPDATABLE_FROM_BACKUP`.
5. docs/DATA_FORMAT.md's field list (the jsonc block under "A note") is the
   desktop's `NOTE_DEFAULTS`.
6. The fields each app knows (`KNOWN_NOTE_FIELDS`), beside which any other
   is kept as it came, and the three bounds on those, are the same.

It reads the desktop's source. It never writes there -- that is the hard
rule of this project, and a gate is no exception.

    py tools/check_note_shape.py
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

NOTEBOOK_KT = os.path.join(ANDROID, "app", "src", "main", "java", "ro",
                           "noteorganizer", "app", "Notebook.kt")
SHARING_KT = os.path.join(ANDROID, "app", "src", "main", "java", "ro",
                          "noteorganizer", "app", "Sharing.kt")
NORMALIZE_KT = os.path.join(ANDROID, "app", "src", "main", "java", "ro",
                            "noteorganizer", "app", "Normalize.kt")
MODEL_PY = os.path.join(DESKTOP, "noteorganizer", "core", "model.py")
SHARING_PY = os.path.join(DESKTOP, "noteorganizer", "core", "sharing.py")

# Fields the desktop keeps but a phone has no business writing, with the
# reason. An exception nobody can read is not a reviewed exception.
NOT_ON_THE_PHONE = {
    "_prev": "the desktop's one-step rescue copy. This app WRITES it on an "
             "update so the desktop can restore from it, but never creates "
             "one for a new note -- there is nothing to go back to yet.",
}

# Not a default in either program: an id is COMPUTED (max + 1) by whoever
# is adding the note, so it appears where notes are appended and not in
# the lists of what a blank note starts with. Comparing it made the gate
# report three disagreements on its first run, all of them its own.
NOT_A_DEFAULT = {
    "id": "computed as max + 1 where a note is appended, never defaulted",
    "_prev": "carried through when a note from outside has one, never "
             "made for a new note -- see NOT_ON_THE_PHONE",
    "_set_aside": "a series' days whose earlier text waits to become notes "
                  "of their own: carried through when a note has some, as "
                  "the desktop's normalize_note carries them, and never made "
                  "for a new note",
    "order": "a note's place in Custom order: kept when it is a finite "
             "number, never defaulted -- 'not placed yet' is its own "
             "answer, and it is not in the desktop's NOTE_DEFAULTS either",
}

failures = []
notes = []


def fail(what, detail=""):
    failures.append(what)
    print("FAIL %s" % what)
    if detail:
        for line in str(detail).splitlines():
            print("       %s" % line)


def ok(what):
    print("PASS %s" % what)


def read(path):
    if not os.path.exists(path):
        return None
    return io.open(path, encoding="utf-8", errors="replace").read()


def without_comments(source):
    """Kotlin source with its comments removed.

    The gate's first run reported that this app shares a field the
    desktop does not. It shares no such thing: the field name was sitting
    inside a COMMENT explaining why it is never shared, and a regex
    reading quoted words cannot tell the difference between a rule and a
    sentence about the rule.
    """
    source = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    return re.sub(r"//[^\n]*", "", source)


def python_list(source, name):
    """A list-of-strings constant, read from the Python without importing."""
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == name:
                    try:
                        value = ast.literal_eval(node.value)
                    except ValueError:
                        return None
                    return list(value)
    return None


def python_dict_keys(source, name):
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign):
            for target in node.targets:
                if isinstance(target, ast.Name) and target.id == name:
                    try:
                        value = ast.literal_eval(node.value)
                    except ValueError:
                        return None
                    return list(value)
    return None


def kotlin_function(source, name):
    """The body of a Kotlin function, by brace counting from its `{`."""
    match = re.search(r"fun\s+%s\s*\(" % re.escape(name), source)
    if not match:
        return None
    index = source.index("{", match.end())
    depth = 0
    for position in range(index, len(source)):
        if source[position] == "{":
            depth += 1
        elif source[position] == "}":
            depth -= 1
            if depth == 0:
                return source[index:position + 1]
    return None


def fields_written(body, source=None):
    """Every note field a Kotlin body names -- as a put() key, in a
    listOf() of field names it then puts, or in a named list it walks
    (`for (field in TEXT_FIELDS) out.put(field, ...)`), resolved in
    *source*."""
    found = set(re.findall(r'\.put\(\s*"([a-z_]+)"', body))
    puts_a_variable = re.search(r"\.put\(\s*field", body)
    for group in re.findall(r'listOf\(([^)]*)\)', body):
        names = re.findall(r'"([a-z_]+)"', group)
        # A listOf of field names is only that when the body puts them.
        if names and puts_a_variable:
            found.update(names)
    if source is not None and puts_a_variable:
        for name in re.findall(r"for\s*\(\s*field\s+in\s+([A-Z_]+)\s*\)", body):
            found.update(kotlin_list(source, name) or [])
    return found


def kotlin_list(source, name):
    match = re.search(r"val\s+%s\s*(?::[^=]+)?=\s*listOf\(" % re.escape(name), source)
    if not match:
        return None
    start = source.index("(", match.end() - 1)
    depth = 0
    for position in range(start, len(source)):
        if source[position] == "(":
            depth += 1
        elif source[position] == ")":
            depth -= 1
            if depth == 0:
                return re.findall(r'"([^"]+)"', source[start:position])
    return None


notebook = read(NOTEBOOK_KT)
sharing_kt = read(SHARING_KT)
normalize_kt = read(NORMALIZE_KT)
if notebook:
    notebook = without_comments(notebook)
if sharing_kt:
    sharing_kt = without_comments(sharing_kt)
if normalize_kt:
    normalize_kt = without_comments(normalize_kt)
model_py = read(MODEL_PY)
sharing_py = read(SHARING_PY)

if notebook is None or sharing_kt is None or normalize_kt is None:
    print("cannot find the Kotlin sources")
    sys.exit(2)
if model_py is None or sharing_py is None:
    print("SKIP: cannot find the desktop app at %s" % DESKTOP)
    print("      (set NOTEORGANIZER_HOME if it lives somewhere else)")
    print("      The checks between the two programs did not run.")
    if "--require-desktop" in sys.argv:
        # On a person's machine a skip is a courtesy: the desktop app may
        # genuinely not be checked out there. In CI it is the very failure
        # this gate exists to prevent, because a check that quietly does
        # not run reads exactly like a check that passed.
        print()
        print("--require-desktop: a SKIP here is a failure, not a pass.")
        sys.exit(1)
    model_py = sharing_py = None

# ---- 1 & 2: what a new note is made of -------------------------------

create = kotlin_function(notebook, "createNote")
fill = kotlin_function(normalize_kt, "normalizeNote")
if create is None or fill is None:
    fail("I can find the two places that build a new note",
         "Notebook.createNote or Normalize.normalizeNote has been renamed")
else:
    created = fields_written(create, notebook) - set(NOT_A_DEFAULT)
    filled = fields_written(fill, normalize_kt) - set(NOT_A_DEFAULT)
    if created == filled:
        ok("the two places that make a new note name the same fields "
           "(%d)" % len(created))
    else:
        fail("the two places that make a new note name the same fields",
             "only in createNote: %s\nonly in normalizeNote: %s"
             % (sorted(created - filled) or "-", sorted(filled - created) or "-"))

    if model_py is not None:
        desktop_fields = (set(python_dict_keys(model_py, "NOTE_DEFAULTS") or [])
                          - set(NOT_A_DEFAULT))
        phone_fields = created | filled
        missing = desktop_fields - phone_fields - set(NOT_ON_THE_PHONE)
        extra = phone_fields - desktop_fields
        if not missing and not extra:
            ok("a note has the same shape as on the desktop (%d fields)"
               % len(desktop_fields))
        else:
            fail("a note has the same shape as on the desktop",
                 "the desktop has and we do not: %s\n"
                 "we have and the desktop does not: %s"
                 % (sorted(missing) or "-", sorted(extra) or "-"))

# ---- 3: what a share code carries ------------------------------------

if sharing_py is not None:
    theirs = python_list(sharing_py, "SHAREABLE_FIELDS")
    ours = kotlin_list(sharing_kt, "SHAREABLE_FIELDS")
    if ours is None or theirs is None:
        fail("I can find the list of fields a code carries, in both languages")
    elif set(ours) == set(theirs):
        ok("a code carries exactly the fields it carries on the desktop (%d)"
           % len(theirs))
    else:
        fail("a code carries exactly the fields it carries on the desktop",
             "the desktop sends and we do not: %s\n"
             "we send and the desktop does not: %s"
             % (sorted(set(theirs) - set(ours)) or "-",
                sorted(set(ours) - set(theirs)) or "-"))

# ---- 4: what an arriving note may replace ----------------------------
#
# Two lists, both by the desktop's names: what a share code may replace,
# and what a backup -- the person's own notebook -- may replace besides.
# The desktop writes the second as the first plus four fields, so both
# readers below resolve "A + [...]" rather than giving up on it.


def python_list_sum(source, name, seen=None):
    """A list constant, or NAME = OTHER + [...], resolved."""
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and any(
                isinstance(t, ast.Name) and t.id == name for t in node.targets):
            value = node.value
            if isinstance(value, ast.BinOp) and isinstance(value.op, ast.Add):
                left = (python_list_sum(source, value.left.id)
                        if isinstance(value.left, ast.Name) else None)
                try:
                    right = list(ast.literal_eval(value.right))
                except ValueError:
                    right = None
                return None if left is None or right is None else left + right
            try:
                return list(ast.literal_eval(value))
            except ValueError:
                return None
    return None


def kotlin_list_sum(source, name):
    """A Kotlin listOf constant, or NAME = OTHER + listOf(...), resolved."""
    plain = kotlin_list(source, name)
    if plain is not None:
        return plain
    match = re.search(r"val\s+%s\s*=\s*(\w+)\s*\+\s*listOf\(" % re.escape(name), source)
    if not match:
        return None
    left = kotlin_list(source, match.group(1))
    start = source.index("(", match.end() - 1)
    depth = 0
    for position in range(start, len(source)):
        if source[position] == "(":
            depth += 1
        elif source[position] == ")":
            depth -= 1
            if depth == 0:
                right = re.findall(r'"([^"]+)"', source[start:position])
                return None if left is None else left + right
    return None


if model_py is not None:
    for name, what in (("UPDATABLE_FROM_SHARED", "an arriving shared note"),
                       ("UPDATABLE_FROM_BACKUP", "a note from a backup")):
        theirs = python_list_sum(model_py, name)
        ours = kotlin_list_sum(notebook, name)
        if ours is None or theirs is None:
            fail("I can find what %s replaces (%s), in both languages" % (what, name))
        elif set(ours) == set(theirs):
            ok("%s replaces exactly what it replaces on the desktop (%d fields)"
               % (what, len(theirs)))
        else:
            fail("%s replaces exactly what it replaces on the desktop" % what,
                 "the desktop replaces and we do not: %s\n"
                 "we replace and the desktop does not: %s"
                 % (sorted(set(theirs) - set(ours)) or "-",
                    sorted(set(ours) - set(theirs)) or "-"))

# ---- 5: the field list a reader is shown -----------------------------
# docs/DATA_FORMAT.md said this gate held its list to the Python's, and
# nothing read it.

data_format = read(os.path.join(ANDROID, "docs", "DATA_FORMAT.md"))
parts = data_format.split("\n## A note\n", 1) if data_format is not None else []
block = re.search(r"```jsonc\n(.*?)\n```", parts[1], re.S) if len(parts) == 2 else None
if block is None:
    # Quiet here, the gate ended "the two programs agree" having read no list.
    fail("DATA_FORMAT.md has its \"A note\" section with the jsonc field list")
elif model_py is not None:
    documented = set(re.findall(r'^  "(\w+)":', block.group(1), re.M))
    desktop_fields = (set(python_dict_keys(model_py, "NOTE_DEFAULTS") or [])
                      - set(NOT_A_DEFAULT))
    if documented - {"id"} == desktop_fields:
        ok("DATA_FORMAT.md lists the note's fields the desktop has (%d)"
           % len(desktop_fields))
    else:
        fail("DATA_FORMAT.md lists the note's fields the desktop has",
             "the desktop has and the document does not: %s\n"
             "the document has and the desktop does not: %s"
             % (sorted(desktop_fields - documented) or "-",
                sorted(documented - {"id"} - desktop_fields) or "-"))

# ---- 6: the fields a version does not know ----------------------------
# Both apps keep them by one rule (the owner's decision D2, 2026-11-13): the
# same names known, the same bounds. A name one app knows and the other does
# not is shaped away by one and kept as it came by the other.

normalize_kt = read(NORMALIZE_KT)
if model_py is not None and normalize_kt is not None:
    known = re.search(r"KNOWN_NOTE_FIELDS = frozenset\(NOTE_DEFAULTS\) \| \{([^}]*)\}", model_py)
    theirs = ((set(python_dict_keys(model_py, "NOTE_DEFAULTS") or []) | set(re.findall(r'"(\w+)"', known.group(1))))
              if known else None)
    listed = re.search(r"val KNOWN_NOTE_FIELDS = setOf\(([^)]*)\)", without_comments(normalize_kt), re.S)
    ours = set(re.findall(r'"(\w+)"', listed.group(1))) if listed else None
    if theirs is None or ours is None:
        fail("I can find the fields each app knows (KNOWN_NOTE_FIELDS), in both languages")
    elif theirs == ours:
        ok("the two apps know the same fields, and keep any other alike (%d known)" % len(theirs))
    else:
        fail("the two apps know the same fields",
             "the desktop knows and we do not: %s\nwe know and the desktop does not: %s"
             % (sorted(theirs - ours) or "-", sorted(ours - theirs) or "-"))
    for name in ("MAX_UNKNOWN_FIELDS", "MAX_UNKNOWN_UNITS", "MAX_UNKNOWN_DEPTH"):
        py = re.search(r"^%s = (\d+)$" % name, model_py, re.M)
        kt = re.search(r"const val %s = (\d+)" % name, normalize_kt)
        if not py or not kt:
            fail("I can find %s in both languages" % name)
        elif py.group(1) == kt.group(1):
            ok("%s is %s in both" % (name, py.group(1)))
        else:
            fail("%s is the same in both" % name, "desktop %s, phone %s" % (py.group(1), kt.group(1)))

print()
if failures:
    print("THE NOTE'S SHAPE: %d disagreement(s)." % len(failures))
    print("Each one means the two programs no longer say the same thing")
    print("about the same file. Fix it, or add an exception WITH its reason.")
    sys.exit(1)
print("THE NOTE'S SHAPE: the two programs agree.")
