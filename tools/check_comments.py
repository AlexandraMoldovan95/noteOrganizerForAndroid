"""Comments say why, in a few lines; the story lives in the issues log.

WHAT IT CHECKS
--------------
Everywhere in the app's Kotlin -- app/src/main and app/src/test -- that
every pointer into the issues log leads somewhere: ``ISSUES_LOG, "..."``
names the start of a story's title in docs/ISSUES_LOG.md, and
``smaller lessons: ...`` names an entry in its "Smaller lessons, moved out
of the code" section; and that no comment names a round of an audit
("round 9", "the lows wave"): it says when a reason was found, never the
reason, and a reader outside the audit cannot use it. A story's title
quoted as a pointer is not counted. The reasons in tools/mutants.json,
and the comments and docstrings of the Python under tools/, are held to
the same rule.

In the files listed in BRIEF, four rules as well:

* a run of comment lines is at most three lines long;
* a KDoc block spends at most six lines on prose -- its @param, @return
  and other tags document an interface and are not counted;
* a file's overview, its first comment when only the package line and
  the imports stand before it, is at most ten lines;
* no comment names a release. The changelog says when; the code says
  why, and "up to 0.12.0" is history the day after.

Three lines hold a reason. What happened, and how it was found, is a
story: it goes to docs/ISSUES_LOG.md, and the code keeps a pointer to it.
The desktop's tools/check_comments.py holds its Python to the same brief.

WHY A LIST
----------
A file joins BRIEF once it has been condensed, and stays condensed from
then on. When the list holds every file, a new one joins it when it is
written.

Run: py tools/check_comments.py
"""

import ast
import io
import json
import os
import re
import sys
import tokenize

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(ROOT, "app", "src")
PACKAGE = "/java/ro/noteorganizer/app/"
ISSUES_LOG = os.path.join(ROOT, "docs", "ISSUES_LOG.md")

# Files whose comments have been condensed: main/ or test/, then the path
# below the app's package.
BRIEF = [
    "main/AppState.kt",
    "main/Backup.kt",
    "main/Calculator.kt",
    "main/CalculatorPanel.kt",
    "main/CalendarScreen.kt",
    "main/Components.kt",
    "main/Days.kt",
    "main/Editing.kt",
    "main/ExportFlow.kt",
    "main/Exporting.kt",
    "main/Files.kt",
    "main/FindBar.kt",
    "main/Finding.kt",
    "main/ImportFlow.kt",
    "main/Importing.kt",
    "main/Links.kt",
    "main/MainActivity.kt",
    "main/Normalize.kt",
    "main/NoteEditor.kt",
    "main/Notebook.kt",
    "main/NotesBackupAgent.kt",
    "main/NotesScreen.kt",
    "main/Organizing.kt",
    "main/Outgoing.kt",
    "main/Paragraphs.kt",
    "main/Pasting.kt",
    "main/PdfPrint.kt",
    "main/PyText.kt",
    "main/Recurrence.kt",
    "main/ReminderAlarm.kt",
    "main/Reminders.kt",
    "main/RepeatSheet.kt",
    "main/RichText.kt",
    "main/Settings.kt",
    "main/SettingsScreens.kt",
    "main/ShareFlow.kt",
    "main/Sharing.kt",
    "main/Startup.kt",
    "main/Stats.kt",
    "main/Styling.kt",
    "main/TableView.kt",
    "main/Tables.kt",
    "main/TemplateScreens.kt",
    "main/Templates.kt",
    "main/Themes.kt",
    "main/Welcome.kt",
    "main/ui/theme/Color.kt",
    "main/ui/theme/Theme.kt",
    "main/ui/theme/ThemeVeil.kt",
    "main/ui/theme/Type.kt",
    "test/AppStateTest.kt",
    "test/BackupTest.kt",
    "test/CalculatorTest.kt",
    "test/DaysTest.kt",
    "test/EditingTest.kt",
    "test/EditorCopyTest.kt",
    "test/EditorTableTest.kt",
    "test/EditorTypingTest.kt",
    "test/ExportingTest.kt",
    "test/FindingTest.kt",
    "test/IdentityTest.kt",
    "test/ImportingTest.kt",
    "test/InteropTest.kt",
    "test/LinksTest.kt",
    "test/ListingTest.kt",
    "test/NotebookTest.kt",
    "test/OrganizingTest.kt",
    "test/OutgoingTest.kt",
    "test/ParagraphsTest.kt",
    "test/PastingTest.kt",
    "test/RecurrenceTest.kt",
    "test/RemindersTest.kt",
    "test/Scratch.kt",
    "test/SettingsTest.kt",
    "test/Shares.kt",
    "test/SharingTest.kt",
    "test/StartupTest.kt",
    "test/StatsTest.kt",
    "test/StylingTest.kt",
    "test/TablesTest.kt",
    "test/TempPlaceTest.kt",
    "test/TemplatesTest.kt",
    "test/ThemesTest.kt",
    "test/Trashed.kt",
]

MAX_COMMENT_LINES = 3
MAX_KDOC_LINES = 6
MAX_OVERVIEW_LINES = 10

# A release as both apps number them before 1.0.0. Dates such as
# 17.10.2026 and library versions such as Compose 1.10.4 do not match.
RELEASE = re.compile(r"\b0\.\d{1,3}\.\d{1,4}\b")

# A round of an audit, as the comments once named them: in figures, in
# words ("round eleven", "the tenth round"), or as the audit itself. Only
# figures were refused at first, and 79 labels in words stayed.
_NUM = (r"(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|"
        r"twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|"
        r"nineteen|twenty(?:-[a-z]+)?)")
_ORD = (r"(?:first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|"
        r"tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|"
        r"seventeenth|eighteenth|nineteenth|twentieth|twenty-[a-z]+)")
ROUND = re.compile(
    r"\brounds?[ -]%s\b|\b(?:%s|%s)(?: and (?:%s|%s))? rounds?\b(?! trip)|\blows wave\b"
    r"|\bfinal audit\b|\baudit's\b|\bwave %s\b"
    % (_NUM, _ORD, _NUM, _ORD, _NUM, _NUM), re.I)

# Where a KDoc's prose ends and its block tags begin.
TAG = re.compile(r"^@(param|return|property|throws|exception|see|sample|"
                 r"receiver|constructor|suppress|since|author)\b")

# The two ways the code points into the issues log.
STORY = re.compile(r'ISSUES_LOG, "([^"]+)"')
LESSON = re.compile(r"smaller lessons: ([^.;)]+)")
LESSONS_HEADING = "## Smaller lessons, moved out of the code"

# What may stand before a file's overview.
PREAMBLE = re.compile(r"^\s*(package|import|@file:)\b")


def kotlin_files():
    """{name as BRIEF writes it: path} for every Kotlin file."""
    files = {}
    for folder, _dirs, names in os.walk(SOURCES):
        for name in sorted(names):
            if name.endswith(".kt"):
                path = os.path.join(folder, name)
                rel = os.path.relpath(path, SOURCES).replace(os.sep, "/")
                files[rel.replace(PACKAGE, "/", 1)] = path
    return files


def issues_log():
    """(story titles, smaller-lesson names) as docs/ISSUES_LOG.md holds
    them; the names lowercased, without their backticks."""
    with io.open(ISSUES_LOG, encoding="utf-8") as fh:
        text = fh.read()
    titles = [line.lstrip("#").strip() for line in text.splitlines()
              if line.startswith("## ") or line.startswith("### ")]
    lessons = []
    parts = text.split(LESSONS_HEADING, 1)
    if len(parts) == 2:
        for line in parts[1].splitlines():
            if line.startswith("## "):
                break
            found = re.match(r"- \*\*(.+?)\*\*", line)
            if found:
                lessons.append(found.group(1).replace("`", "").lower())
    return titles, lessons


def comments(source):
    """(start, end, kind) of every comment in *source*, as offsets: kind is
    'line' for //, 'doc' for /** */ and 'block' for /* */. Strings are
    skipped whole -- templates, raw strings, characters and names in
    backticks included -- so a // inside "https://" is not a comment, and
    block comments nest, as Kotlin's do."""
    n = len(source)
    found = []

    def block_end(i):
        depth = 0
        while i < n:
            if source.startswith("/*", i):
                depth += 1
                i += 2
            elif source.startswith("*/", i):
                depth -= 1
                i += 2
                if depth == 0:
                    return i
            else:
                i += 1
        return n

    def string(i, raw):
        while i < n:
            if raw and source.startswith('"""', i):
                i += 3
                while i < n and source[i] == '"':
                    i += 1
                return i
            if not raw and source[i] == '"':
                return i + 1
            if not raw and source[i] == "\\":
                i += 2
            elif source.startswith("${", i):
                i = code(i + 2, True)
            else:
                i += 1
        return n

    def code(i, template):
        depth = 0
        while i < n:
            if source.startswith("//", i):
                end = source.find("\n", i)
                end = n if end < 0 else end
                found.append((i, end, "line"))
                i = end
            elif source.startswith("/*", i):
                end = block_end(i)
                doc = source.startswith("/**", i) and not source.startswith("/**/", i)
                found.append((i, end, "doc" if doc else "block"))
                i = end
            elif source.startswith('"""', i):
                i = string(i + 3, True)
            elif source[i] == '"':
                i = string(i + 1, False)
            elif source[i] == "`":
                # A name in backticks, as a test's is: its words may hold an
                # apostrophe, which is no character literal.
                end = source.find("`", i + 1)
                i = n if end < 0 else end + 1
            elif source[i] == "'":
                i += 1
                while i < n and source[i] != "'":
                    i += 2 if source[i] == "\\" else 1
                i += 1
            elif template and source[i] == "{":
                depth += 1
                i += 1
            elif template and source[i] == "}":
                if depth == 0:
                    return i + 1
                depth -= 1
                i += 1
            else:
                i += 1
        return i

    code(0, False)
    return found


def prose(block):
    """The lines a /* */ or /** */ comment spends on prose: its delimiters,
    leading stars and empty edges left out, and for a KDoc everything from
    its first block tag on."""
    body = block[3:] if block.startswith("/**") else block[2:]
    body = body[:-2] if body.endswith("*/") else body
    lines = []
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("*"):
            line = line[1:].strip()
        lines.append(line)
    for index, line in enumerate(lines):
        if TAG.match(line):
            lines = lines[:index]
            break
    while lines and not lines[0]:
        lines.pop(0)
    while lines and not lines[-1]:
        lines.pop()
    return lines


def runs(source):
    """(first line, length, text, kind, alone) for each comment: a run of
    consecutive lines that hold nothing but a // comment is one, a comment
    after code is a run of its own, and a /* */ or /** */ block is one,
    counted by its prose."""
    starts = [0]
    for index, char in enumerate(source):
        if char == "\n":
            starts.append(index + 1)

    def line_of(offset):
        low, high = 0, len(starts) - 1
        while low < high:
            middle = (low + high + 1) // 2
            if starts[middle] <= offset:
                low = middle
            else:
                high = middle - 1
        return low + 1

    out = []
    for start, end, kind in comments(source):
        line = line_of(start)
        alone = not source[starts[line - 1]:start].strip()
        if kind == "line":
            words = source[start + 2:end].strip()
            last = out[-1] if out else None
            if (alone and last and last[3] == "line" and last[4]
                    and last[0] + last[1] == line):
                last[1] += 1
                last[2] += " " + words
            else:
                out.append([line, 1, words, kind, alone])
        else:
            lines = prose(source[start:end])
            out.append([line, len(lines), " ".join(lines), kind, alone])
    return [tuple(run) for run in out]


def overview_line(source, found):
    """The first line of the file's overview, or None: its first comment,
    when only the package line, the imports and blank lines stand before
    it."""
    if not found:
        return None
    first, _length, _text, _kind, _alone = found[0]
    before = source.split("\n")[:first - 1]
    if all(not line.strip() or PREAMBLE.match(line) for line in before):
        return first
    return None


def broken_pointers(text, titles, lessons):
    """What *text* points to in the issues log that is not there."""
    flat = " ".join(text.split())
    broken = []
    for found in STORY.finditer(flat):
        wanted = found.group(1)
        if not any(title.startswith(wanted) for title in titles):
            broken.append('no story titled "%s"' % wanted)
    for found in LESSON.finditer(flat):
        wanted = found.group(1).replace("`", "").strip().lower()
        if not any(wanted.startswith(name) for name in lessons):
            broken.append('no smaller lesson named "%s"' % wanted)
    return broken


def check(name, path, titles, lessons):
    with io.open(path, encoding="utf-8") as fh:
        source = fh.read()
    problems = []
    found = runs(source)
    for first, _length, text, _kind, _alone in found:
        for what in broken_pointers(text, titles, lessons):
            problems.append((first, "points to %s in ISSUES_LOG" % what))
        named = ROUND.search(STORY.sub("", " ".join(text.split())))
        if named:
            problems.append((first, "names the audit's %r" % named.group(0)))
    if name not in BRIEF:
        return sorted(problems)
    overview = overview_line(source, found)
    for first, length, text, kind, alone in found:
        release = RELEASE.search(text)
        if release:
            problems.append((first, "names release %s" % release.group(0)))
        if first == overview:
            if length > MAX_OVERVIEW_LINES:
                problems.append((first, "an overview of %d lines (at most %d)"
                                 % (length, MAX_OVERVIEW_LINES)))
        elif kind == "doc":
            if length > MAX_KDOC_LINES:
                problems.append((first, "KDoc prose of %d lines (at most %d)"
                                 % (length, MAX_KDOC_LINES)))
        elif alone and length > MAX_COMMENT_LINES:
            problems.append((first, "%d comment lines in a row (at most %d)"
                             % (length, MAX_COMMENT_LINES)))
    return sorted(problems)


def python_rounds():
    """(file, line, label) for each comment run or docstring in the tools'
    Python that names a round. This file is left out: it quotes the labels
    it refuses."""
    found = []
    for folder, _dirs, names in os.walk(os.path.join(ROOT, "tools")):
        for name in sorted(names):
            path = os.path.join(folder, name)
            rel = os.path.relpath(path, ROOT).replace(os.sep, "/")
            if not name.endswith(".py") or rel == "tools/check_comments.py":
                continue
            with io.open(path, encoding="utf-8") as fh:
                source = fh.read()
            texts, last = [], None
            for tok in tokenize.generate_tokens(io.StringIO(source).readline):
                if tok.type != tokenize.COMMENT:
                    continue
                if texts and last == tok.start[0] - 1 and tok.line.strip().startswith("#"):
                    texts[-1][1] += " " + tok.string.lstrip("#")
                else:
                    texts.append([tok.start[0], tok.string.lstrip("#")])
                last = tok.start[0]
            for node in ast.walk(ast.parse(source)):
                if isinstance(node, (ast.Module, ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)):
                    doc = ast.get_docstring(node)
                    if doc:
                        texts.append([node.body[0].lineno, doc])
            for line, text in texts:
                named = ROUND.search(STORY.sub("", " ".join(text.split())))
                if named:
                    found.append((rel, line, named.group(0)))
    return sorted(found)


def catalogue_rounds():
    """The mutants whose reason names a round: tools/mutants.json is read by
    whoever runs a mutant, outside the audit as much as a comment is."""
    with io.open(os.path.join(ROOT, "tools", "mutants.json"), encoding="utf-8") as fh:
        catalogue = json.load(fh)
    return [(m.get("id", "?"), named.group(0)) for m in catalogue
            for named in [ROUND.search(m.get("why", ""))] if named]


def main():
    files = kotlin_files()
    missing = [name for name in BRIEF if name not in files]
    if missing:
        for name in missing:
            print("BRIEF names a file that does not exist: %s" % name)
        return 1
    titles, lessons = issues_log()
    failures = 0
    for name in sorted(files):
        for line, what in check(name, files[name], titles, lessons):
            print("%s:%d: %s" % (name, line, what))
            failures += 1
    for mid, named in catalogue_rounds():
        print("tools/mutants.json: %s's reason names the audit's %r" % (mid, named))
        failures += 1
    for rel, line, named in python_rounds():
        print("%s:%d: names the audit's %r" % (rel, line, named))
        failures += 1
    if failures:
        print("\n%d problem(s). A comment says why in at most %d lines; the "
              "story goes to docs/ISSUES_LOG.md, with a pointer left here."
              % (failures, MAX_COMMENT_LINES))
        return 1
    print("Comments brief: %d file(s) read, no audit round named, every pointer "
          "into ISSUES_LOG leads somewhere, %d condensed file(s) within their limits; "
          "the tools' Python and the mutants' reasons name no round either."
          % (len(files), len(BRIEF)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
