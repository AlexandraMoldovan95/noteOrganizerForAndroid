"""Rebuild the export fixture, using the DESKTOP's own renderer.

`app/src/test/resources/export_cases.json` holds a few hundred notes --
prose, lists, tables with merges and hidden columns and checkboxes,
styles valid and invalid -- each with the exact file the desktop's
"Export as file" writes for it, as a web page and as plain text, and the
file name it offers. `ExportingTest` asks the phone's port the same and
fails on any byte that differs.

The page itself comes from the desktop's core/richtext.py (note_to_html,
html_document) and core/storage.py (mend_surrogates), imported and run.
The few lines that put a page together around the note -- the checklist,
the comments, the title -- live in a method of the desktop's window
class, which cannot be imported without starting the app, so they are
written out here. To keep that copy honest, the method's source is
fingerprinted: if the desktop's `_note_export_content` or its file-name
line changes, this script stops and says so, rather than going on
producing answers from an old copy.

The notes are drawn from a fixed seed, so the file only changes when this
script or the desktop's renderer does.

    py tools/make_export_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_export_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import hashlib
import io
import json
import os
import random
import re
import sys
from html import escape

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources",
                       "export_cases.json")
SEED = 20261015
CASES = 400

# The desktop method this script mirrors, and the file-name line, as they
# were when the copy below was written. Since the desktop's 1fcc189 the
# name is core/model.py's export_file_stem, imported and run below rather
# than copied; its source and the two patterns it uses are fingerprinted,
# because Exporting.fileName is a port of them and must follow a change.
COMPOSITION_SHA1 = "664dead77a092d71d28c61244c464c3bf3d93abc"
NAME_LINE = "initialfile=export_file_stem(title) + ext,"
STEM_SHA1 = "21a56e17ae3cf4967031e01e9b4d52c657af82cb"

WORDS = ["milk", "Bread", "the", "<b>tag</b>", "a & b", "\"quoted\"", "it's", "😀", "naïve",
         "x", "  spaced  ", "café", "→", "", "1. not a list", "a|b"]
CELLS = ["a", "Milk", "  right", "  centre  ", "", "<", "^", "[ ]", "[x]", "[X]", "line<br>two",
         "😀", "<i>", "& co", "x"]
SEPARATORS = ["| --- | --- |", "| :-: | --: |", "| ~ | --- |", "|---|---|", "| --- | ~ |",
              "| :--- | ---: |", "| --- | --- | --- |"]
LIST_LINES = ["• bullet", "◦ deeper", "▪ deepest", "1. first", "12. twelfth", "٣. arabic",
              "•no space", "- dash", "plain"]
STYLES = ["bold", "italic", "bold_italic", "underline", "strike", "text_color", "highlight",
          "font_size", "font_family", "nonsense", "bold", "italic", "highlight"]
COLOURS = ["#ff0000", "#FFAA00", "#12ab9f", "red", "#12345", "#gggggg", None]
SIZES = [9, 12, 14, 24, 13, "12", 12.0, True]
FAMILIES = ["Arial", "Georgia", "Times New Roman", "Comic Sans", "arial", 3]


def row(rng, width):
    cells = [rng.choice(CELLS) for _ in range(width)]
    pad = rng.choice(["", " ", "  "])
    return pad + "|" + "|".join(" %s " % c if not c.startswith("  ") else c + " " for c in cells) + "|"


def table(rng):
    width = rng.choice([2, 2, 3])
    lines = [row(rng, width)]
    if rng.random() < 0.75:
        lines.append(rng.choice(SEPARATORS))
    for _ in range(rng.randrange(0, 4)):
        # Now and then a row of another width: a table still being typed.
        lines.append(row(rng, width if rng.random() < 0.85 else rng.choice([1, 2, 3, 4])))
    return lines


def body(rng):
    lines = []
    for _ in range(rng.randrange(0, 9)):
        roll = rng.random()
        if roll < 0.2:
            lines.extend(table(rng))
        elif roll < 0.4:
            lines.append(rng.choice(LIST_LINES) + " " + rng.choice(WORDS))
        elif roll < 0.5:
            lines.append("")
        else:
            lines.append(" ".join(rng.choice(WORDS) for _ in range(rng.randrange(1, 6))))
    return "\n".join(lines)


def span(rng, length):
    start = rng.randrange(-2, max(1, length + 3))
    end = start + rng.choice([0, 1, 2, 5, 12, 40, -1])
    out = {"start": start, "end": end, "style": rng.choice(STYLES)}
    if rng.random() < 0.05:
        out["start"] = float(start)
    if out["style"] in ("text_color", "highlight"):
        out["value"] = rng.choice(COLOURS)
    elif out["style"] == "font_size":
        out["value"] = rng.choice(SIZES)
    elif out["style"] == "font_family":
        out["value"] = rng.choice(FAMILIES)
    return out


def paragraph(rng, lines):
    out = {"line": rng.choice([rng.randrange(0, lines + 2), -1, True, "2", 1.0])}
    if rng.random() < 0.7:
        out["kind"] = rng.choice(["bullet", "number", "bullet", "nonsense", None])
    if rng.random() < 0.5:
        out["indent"] = rng.choice([0, 1, 2, 3, 9, -1, True, 1.5])
    if rng.random() < 0.4:
        out["align"] = rng.choice(["center", "right", "left", "justify"])
    return out


def note(rng):
    text = body(rng)
    length = len(text)
    out = {
        "title": rng.choice(["Shopping", "  Trip: day 1/2  ", "", "   ", "a<b>c", "...dots...",
                             "😀 party", "x" * 70, "tab\there", "Untitled"]),
        "body": text,
        "formatting": [span(rng, length) for _ in range(rng.randrange(0, 8))],
        "paragraphs": [paragraph(rng, text.count("\n") + 1) for _ in range(rng.randrange(0, 5))],
    }
    if rng.random() < 0.4:
        out["checklist"] = [{"text": rng.choice(WORDS + ["to do <more>"]), "done": rng.random() < 0.5}
                            for _ in range(rng.randrange(1, 4))]
    if rng.random() < 0.4:
        out["comments"] = rng.choice(["a comment", "  padded  ", "   ", "two\nlines & <x>", "😀"])
    return out


def check_desktop_copy():
    """The fingerprint of the desktop code this script copies."""
    src = io.open(os.path.join(DESKTOP, "note_organizer.py"), encoding="utf-8").read()
    found = re.search(r"\n    def _note_export_content\(self, note, fmt, title\):\n(.*?)\n    def ", src, re.S)
    if not found:
        return "the desktop's _note_export_content was not found"
    if hashlib.sha1(found.group(1).encode("utf-8")).hexdigest() != COMPOSITION_SHA1:
        return "the desktop's _note_export_content has changed"
    if NAME_LINE not in src:
        return "the desktop's file-name line in export_note_as_file has changed"
    model = io.open(os.path.join(DESKTOP, "noteorganizer", "core", "model.py"), encoding="utf-8").read()
    stem = re.search(r"\ndef export_file_stem\(title\):\n(.*?)\n\n\n", model, re.S)
    forbidden = re.search(r"\n(_FORBIDDEN_IN_FILE_NAMES = .*?)\n", model)
    hidden = re.search(r"\n(HIDDEN_IN_NAMES = re.compile\(\n.*?\)\n)", model, re.S)
    if not (stem and forbidden and hidden):
        return "the desktop's export_file_stem, or a pattern it uses, was not found"
    if hashlib.sha1((stem.group(1) + forbidden.group(1) + hidden.group(1)).encode("utf-8")).hexdigest() != STEM_SHA1:
        return "the desktop's export_file_stem has changed"
    return None


def desktop_export():
    """The desktop's export, as a function: (compose(note, fmt, title),
    file_name(title, ext)) -- or a string saying why it cannot be had.
    The emulator walk asks it too, to compare the phone's file with it."""
    if not os.path.isdir(DESKTOP):
        return ("cannot find the desktop app at %s "
                "(set NOTEORGANIZER_HOME if it lives somewhere else)" % DESKTOP)
    changed = check_desktop_copy()
    if changed:
        return (changed + ": re-read it, bring compose() and file_name() below and "
                "Exporting.kt into line, then update the fingerprint at the top of this script")
    if HERE not in sys.path:
        sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)      # the desktop's memory ceiling, before its code runs here
    if DESKTOP not in sys.path:
        sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.richtext import note_to_html, html_document
        from noteorganizer.core.storage import mend_surrogates
        from noteorganizer.core.model import export_file_stem
    except ImportError as problem:
        return "cannot import the desktop renderer: %s" % problem

    def compose(note, fmt, title):
        # _note_export_content, as fingerprinted above.
        body = note.get("body", "")
        comments = (note.get("comments") or "").strip()
        checklist = note.get("checklist") or []
        if fmt == "html":
            fragment = note_to_html(body, note.get("formatting"), note.get("paragraphs"))
            if checklist:
                items = "\n".join(
                    "<div>{} {}</div>".format("☑" if it.get("done") else "☐", escape(it.get("text", "")))
                    for it in checklist)
                fragment += ("<hr>\n<div><i>Checklist</i></div>\n" + items)
            if comments:
                fragment += ("<hr>\n<div><i>Comments</i></div>\n" + note_to_html(comments))
            content = html_document(title, fragment)
        else:
            content = title + "\n\n" + body
            if checklist:
                content += "\n\nChecklist:\n" + "\n".join(
                    "[{}] {}".format("x" if it.get("done") else " ", it.get("text", ""))
                    for it in checklist)
            if comments:
                content += "\n\nComments:\n" + comments
            content += "\n"
        return mend_surrogates(content)

    def file_name(title, ext):
        # export_note_as_file's initialfile, as fingerprinted above.
        return export_file_stem(title) + ext

    return compose, file_name


def main():
    found = desktop_export()
    if isinstance(found, str):
        print(found)
        return 2
    compose, file_name = found
    rng = random.Random(SEED)
    cases, raised = [], 0
    # After the drawn notes, titles the file name must clean, one each: every
    # control character Windows refuses, and what hides or reorders text
    # (export_file_stem, since the desktop's 1fcc189). Added after, so the
    # drawn notes stay as they were.
    named = [{"title": t, "body": "x"} for t in [
        "bell\x07title", "esc\x1b[0m", "Invoice\u202efdp.exe", "del\x7fC1\x85here",
        "\u200e\u200fmarks\u2066\u2069", "line\u2028sep\u2029para\ufeffbom", " _.edges._ ",
        "\x01\x02", "名前" * 40]]
    for i in range(CASES + len(named)):
        n = note(rng) if i < CASES else named[i - CASES]
        title = (n.get("title") or "").strip() or "Untitled note"
        case = {"note": n, "title": title, "name": file_name(title, ".html")}
        for fmt in ("html", "txt"):
            try:
                case[fmt] = compose(n, fmt, title)
            except Exception as problem:  # noqa: BLE001 -- recorded, not hidden
                case[fmt] = None
                case["raises"] = type(problem).__name__
        raised += "raises" in case
        cases.append(case)

    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"seed": SEED, "cases": cases}, f, ensure_ascii=False, indent=0)
    print("%d notes, %d of which the desktop's renderer raised on" % (len(cases), raised))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
