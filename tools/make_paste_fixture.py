"""Rebuild the paste fixture, using the DESKTOP's own reader.

`app/src/test/resources/paste_cases.json` holds several hundred HTML
fragments -- pages, word processors, spreadsheets, the app's own copies,
and markup broken every way Python's `html.parser` has an answer for --
each with what the desktop's `html_to_note` (core/richtext.py) makes of
it: the text, the styles, the lines' properties, and whether the caps cut
anything. `PastingTest` asks the phone's port the same and fails on any
difference.

The reader is imported and run, on the Python this script runs on -- the
one the desktop's .exe is built with, since `html.parser` and
`html.unescape` are part of what is being matched. The fragments are
drawn from a fixed seed, so the file only changes when this script, the
desktop's reader or Python's parser does.

    py tools/make_paste_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_paste_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import hashlib
import io
import json
import os
import random
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources", "paste_cases.json")
SEED = 20261016
RANDOM_CASES = 340
ROUND_TRIPS = 80
# A text longer than this is kept as its length and fingerprint, not in full.
LONG = 3000

TAGS = ["b", "strong", "i", "em", "u", "ins", "s", "strike", "del", "span", "span", "span",
        "p", "div", "br", "h1", "h3", "blockquote", "pre", "section", "article",
        "ul", "ol", "li", "li", "table", "tr", "td", "th", "a", "font", "sup", "img", "hr",
        "tbody", "code", "B", "LI", "Td", "script", "style", "textarea", "title", "xmp"]
STYLES = [
    "font-weight: bold", "font-weight:700", "font-weight: 600", "font-weight:normal",
    "font-weight: 400", "font-weight:bolder", "font-weight: 6_00", "font-weight:\u0666\u0660\u0660",
    "font-weight: 700 !important", "font-weight:inf", "font-weight: nan", "font-weight: 1e3",
    "font-weight: 599.9", "font-weight:+600", "font-weight: 6 00", "font-weight:600px",
    "font-style: italic", "font-style:oblique 10deg", "font-style: normal", "font-style: ITALIC",
    "font: italic bold 12px/30px Georgia", "font: 700 1em Arial", "font: 12px Arial",
    "font: oblique 600 1em/2 x", "font: bold/2 x", "font:", "font: normal",
    "color: #d32f2f", "color:#abc", "color: rgb(211, 47, 47)", "color: rgba(0,128,255,0.5)",
    "color:red", "color: #777777", "color: #D32F2F !important", "color: rgb(\u0662\u0661\u0661, 47, 47)",
    "color: rgb(300,0,0)", "color: rgb( 10 , 200 , 30 )", "color: rgb(10,200)", "color:#12345",
    "background: #fff3b0", "background-color: #ffff00", "background-color:#222244",
    "background: yellow", "background-color: rgb(255, 240, 160)", "background:#ccffcc",
    "background-color: #808080", "background: #fff3b0 !important",
    "text-align: center", "text-align:right", "text-align: left", "text-align: CENTER",
    "text-align: justify", "text-align : right ",
    "", ";;", "foo", "COLOR: #00AA00", " Font-Weight : BOLD ", "color", "font-size: 30px",
]
WORDS = ["milk", "Bread", "the", "a b", "  spaced  ", "\n", "line\nbreak", "\t", " \n  ", "\r\n",
         "&amp;", "&lt;b&gt;", "&nbsp;", "&copy", "&notit;", "&#128;", "&#x1F600;", "\U0001F600",
         "caf\u00e9", "\u0663", "|", "a | b", "\u2611", "\u2610", "  ", "&", "& ", "x<y", "&#0;",
         "&#xD800;", "&#1114112;", "&#xFFFE;", "&ampfoo", "\u00a0", "\u2028", "&#65", "&#x;",
         "&#;", "&Amp;", "&amp", "&ltfoo", "&CounterClockwiseContourIntegral;", "&#13;", "\x85",
         "Table 1: caption", "1. not a list", "| a | b |", "\u2022 dot", "<br>", "\u00ad", "\u200b"]
ATTR_NAMES = ["class", "id", "href", "title", "data-x", "STYLE", "dir"]
ATTR_VALUES = ["x", "a&amp;b", "a&ampb", "&quot;q&quot;", "&#39;", "&#x41;", "&amp=", "&#65=",
               "&notit;", "", "two words", "&lt;tag&gt;", "1 > 0", "&#99999999999;"]
SPANS = ["1", "2", "3", " 2 ", "0", "-1", "40", "abc", "2.5", "\u0662", "1_0", "+3", "", None]


def text(rng):
    return "".join(rng.choice(WORDS) for _ in range(rng.randrange(0, 4)))


def attrs(rng, tag):
    out = []
    if rng.random() < 0.6:
        style = ";".join(rng.choice(STYLES) for _ in range(rng.randrange(1, 4)))
        out.append(("style", style))
    if tag.lower() in ("td", "th"):
        for name in ("colspan", "rowspan"):
            if rng.random() < 0.35:
                out.append((name, rng.choice(SPANS)))
    for _ in range(rng.choice([0, 0, 0, 1, 2])):
        out.append((rng.choice(ATTR_NAMES), rng.choice(ATTR_VALUES)))
    parts = []
    for name, value in out:
        if value is None:
            parts.append(" " + name)
            continue
        quote = rng.choice(['"', '"', "'", ""])
        if quote == "" and (not value or any(c in value for c in " >'\"=")):
            quote = '"'
        if quote and quote in value:
            quote = "'" if quote == '"' else '"'
        spaces = rng.choice(["", "", " "])
        parts.append(" %s%s=%s%s%s%s" % (name, spaces, spaces, quote, value, quote))
    return "".join(parts)


def element(rng, depth):
    if depth > 3 or rng.random() < 0.3:
        return text(rng)
    tag = rng.choice(TAGS)
    opening = "<%s%s%s>" % (tag, attrs(rng, tag), rng.choice(["", "", "", "/", " /"]))
    inner = "".join(element(rng, depth + 1) for _ in range(rng.randrange(0, 3)))
    closing = "" if rng.random() < 0.15 else "</%s>" % rng.choice([tag, tag, tag, tag.upper(), "span", "p"])
    return opening + inner + closing


def table(rng):
    """A table as spreadsheets and pages write them, merges and all."""
    rows = []
    for _ in range(rng.randrange(1, 5)):
        cells = []
        for _ in range(rng.randrange(1, 5)):
            tag = rng.choice(["td", "td", "th"])
            inner = rng.choice(["", "1", "Milk", "<p>in a p</p>", "a<br>b", "\u2611 done", "\u2610",
                                "<b>bold</b> cell", "<div>x</div><div>y</div>", "  pad  ", "&lt;", "^",
                                "\U0001F600", "| pipe"])
            cells.append("<%s%s>%s</%s>" % (tag, attrs(rng, tag), inner, tag))
        rows.append("<tr>" + "".join(cells) + ("</tr>" if rng.random() < 0.9 else ""))
    body = "".join(rows)
    if rng.random() < 0.5:
        body = "<tbody>" + body + "</tbody>"
    return "<table%s>%s</table>" % (attrs(rng, "table"), body)


def fragment(rng):
    pieces = []
    for _ in range(rng.randrange(1, 6)):
        roll = rng.random()
        if roll < 0.15:
            pieces.append(table(rng))
        elif roll < 0.3:
            kind = rng.choice(["ul", "ol"])
            items = "".join("<li%s>%s</li>" % (attrs(rng, "li"), element(rng, 2)) for _ in range(rng.randrange(1, 4)))
            pieces.append("<%s>%s</%s>" % (kind, items, kind))
        else:
            pieces.append(element(rng, 0))
    return "".join(pieces)


# Written by hand: each one a rule of the reader or the parser, by name.
EDGES = [
    "", "plain text", "  leading and trailing  ", "line one\nline two\n\nline four",
    "a  \n  b", "<p>para one</p><p>para two</p>", "text\n<p>late block</p>",
    "<b>bold</b> <i>italic</i> <u>under</u> <s>strike</s> <del>del</del> <ins>ins</ins> <strike>x</strike>",
    "<strong><em>both</em></strong>", "<b>a<i>b</b>c</i>", "<b>unclosed", "</b>stray end",
    '<b style="font-weight:normal"><p><span style="font-weight:700">Docs bold</span> plain</p></b>',
    '<i style="font-style:normal">not italic</i>', '<b style="font-weight:normal"><b>inner</b></b>',
    '<span style="color:#d32f2f;font-weight:bold">red bold</span> after',
    '<span style="color:red"><span>inner</span>outer</span>',
    '<p style="color:#1565c0">blue para</p>next',
    '<span style="background-color:#fff3b0">pale</span><span style="background:#222244">dark</span>',
    '<span style="color:#777">grey</span><span style="color:#888888">grey2</span>',
    '<ul><li>one</li><li>two<ul><li>deep</li></ul></li></ul><ol><li>first</li><li>second</li></ol>',
    "<li>no list</li>", "<ul>" * 10 + "<li>very deep</li>" + "</ul>" * 10,
    '<ol><li style="text-align:center">centred item</li></ol>',
    '<p style="text-align:center">centre</p><p style="text-align:right">right</p><div style="text-align:left">left</div>',
    "<table><tr><th>H1</th><th>H2</th></tr><tr><td>a</td><td>b</td></tr></table>",
    '<table><tr><td colspan="2">wide</td></tr><tr><td>a</td><td>b</td></tr></table>',
    '<table><tr><td rowspan="2">tall</td><td>a</td></tr><tr><td>b</td></tr></table>',
    '<table><tr><td rowspan="3" colspan="2">big</td><td>x</td></tr><tr><td>y</td></tr><tr><td>z</td></tr></table>',
    '<table><tr><td>a</td><td rowspan="2">r</td></tr><tr></tr></table>',
    '<table><tr><td style="text-align:right">1</td><td style="text-align:center">two</td></tr></table>',
    "<table><tr><td>\u2611</td><td>\u2610 todo</td></tr></table>\u2611 outside",
    "<table><tr><td>a<br>b</td><td><p>p1</p><p>p2</p></td></tr></table>",
    "<td>cell outside a table</td>", "<tr><td>row outside</td></tr>",
    "<table><tr><td>outer<table><tr><td>inner</td></tr></table></td></tr></table>",
    "<table><tr><td>a</td><td>b</td></tr></table><table><tr><td><b>second</b></td><td>tab</td></tr></table>",
    "<table><tr><td><b>x</b></td><td>longer text here</td></tr><tr><td>y</td><td><i>z</i></td></tr></table>"
    "<p>between</p><table><tr><td>m</td><td><b>bold in table two</b></td></tr></table>",
    "<table><tr><td>aaaaaaaaaaaaaaaaaaaa</td><td>b</td></tr></table><p>x</p>"
    "<table><tr><td>m</td><td><b>bold</b></td></tr></table><p><i>after both</i></p>",
    "<p>| a | b |</p><p>| c | d |</p>", "| x | y |\n| --- | --- |\n| 1 | 2 |",
    "<script>var x = '<b>no</b>';</script>after", "<style>p{color:red}</style><p>styled</p>",
    "<textarea>&amp; kept</textarea>", "<title>T &amp; t</title>body", "<xmp><b>raw</b></xmp>",
    "<SCRIPT>a</script >b", "<script>never closed", "<plaintext><b>all raw</b> &amp;",
    "<!-- comment -->visible", "<!-->abrupt", "<!--->abrupt2", "<!-- a --!> b", "<!-- unterminated",
    "<!DOCTYPE html><p>doc</p>", "<!doctype html", "<![CDATA[x<y]]>after", "<![CDATA[open",
    "<?xml version='1.0'?>pi", "<?unterminated", "<!bogus>after", "</>gone", "</ b>bogus end",
    "a < b", "1 <3 2", "trailing <", "trailing </", "<b", '<a href="x>y">link</a>',
    "<a href='unclosed>text", '<a b="c"d>joined</a>', "<a b=c\x0bd>vt</a>", "<p/>x", "<br/>x<br />y",
    "<b/>not bold", "<a/b>slash</a>", "<a b //c>s</a>", "<a b= 'unclosed>t", "<a b='x' = 'y'>z</a>",
    '<span style="color:#f00" style="color:#0f0">dup</span>', '<td colspan>v</td>',
    "<P>upper</P><DIV>DIV</DIV>", "<dİv>dotted</dİv>\nnewline", "<bloc\N{KELVIN SIGN}quote>kelvin</bloc\N{KELVIN SIGN}quote>\nx",
    "<\N{LATIN SMALL LETTER DOTLESS I}>dotless</\N{LATIN SMALL LETTER DOTLESS I}>\nnot a block",
    '<span style="font:bold&nbsp=italic 1em x">nbsp before =</span>',
    '<span style="font:bold&nbsp;italic 1em x">nbsp with ;</span>',
    '<span style="font-weight:600_">u1</span>', '<span style="font-weight:6__00">u2</span>',
    '<span style="font-weight:_600">u3</span>', '<span style="font-weight:6_0_0">u4</span>',
    '<table><tr><td colspan="2_">u5</td><td colspan="_2">u6</td><td colspan="1__0">u7</td>'
    '<td colspan="1_0">u8</td></tr><tr><td>a</td></tr></table>',
    "<script>a</scriptx>b</script>c", "<style>x</style2>y</style>z", "<script>a</script",
    "| a | b |\n| ------------ | --- |\n| 1 | 2 |",
    "<p>| a | b |</p><p>| :----------: | ---: |</p><p>| <b>1</b> | 2 |</p>",
    "<\u017fection>long s</\u017fection>\nx", "<p2>not a block</p2>\nkept", "< / P >\nspaces",
    "<pre>pre  text\n  kept?</pre>", "x\n<br>\ny", "&amp;&lt;&gt;&quot;&#39;&apos;",
    "&#x41;&#65;&#x1F600;&#128512;&#0;&#13;&#x80;&#xD800;&#x110000;&#1;&#xFFFF;",
    "&notin; &notit; &not &amp &ampx &CounterClockwiseContourIntegral; &#x &#; &# ",
    "&" + "a" * 40 + ";", "&#" + "1" * 4301 + ";", "&#" + "0" * 4300 + "65;",
    "&#x" + "f" * 5000 + ";", '<span style="color: rgb(' + "1" * 4301 + ',0,0)">big</span>',
    '<span style="color: rgb(' + "1" * 4301 + ',0)">big2</span>',
    '<td colspan="' + "1" * 4301 + '">c</td>', '<a title="&#' + "1" * 4301 + ';">t</a>',
    "\U0001F600<b>after emoji</b>\U0001F600<i>x</i>", "<b>\U0001F600</b>\u0301<i>e</i>",
    "\n\n<b>leading newlines</b>", "<p></p><p></p><b>after empty paras</b><p></p>",
    "<br><br>text", "a<br>   b", "  <b>  spaced  </b>  ", "<p>   </p><p>x</p>",
    "<span>a</span>\n<span>b</span>", "word\u00a0word", "x\u2028y", "x\x85y",
    '<p style="text-align:center">\u00a0</p>', "<ul><li></li><li>after empty</li></ul>",
    '<div style="font-weight:bold"><div>nested bold block</div></div>',
    '<li style="color:#c62828">red li</li>', '<td style="font-weight:700">bold td</td>',
    '<br style="font-weight:bold">x', '<img style="color:#f00">y', '<hr style="font-weight:bold">z',
    '<span style="font-weight:bold;font-weight:normal">last wins</span>',
    '<span style="font:italic 700 1em x;font-style:normal">longhand wins</span>',
    '<span style="FONT-WEIGHT: BOLD">upper css</span>', '<span style="font-weight:BOLD">upper value</span>',
    '<span style="color:#ABCDEF">upper hex</span>', '<span style="color:#abcd">four</span>',
    "<sup>sup</sup><sub>sub</sub><code>code</code><font color=red>font</font>",
]


def long_cases():
    """The caps: a page past 100,000 characters, and past 2,000 styles.
    Kept as pieces and how many times each repeats, so the fixture stays
    small; the test puts them together again."""
    return [
        [["<p>", 1], ["word ", 21000], ["</p><p>tail that is cut</p>", 1]],
        [["x", 99999], ["<b>yz</b>", 1]],
        [["x", 100000], ["   ", 1]],
        [["<p>", 1], ["<b>x</b> ", 2100], ["</p>", 1]],
        [['<span style="color:#d32f2f">7</span>', 2003]],
        [["<ul>", 1], ["<li>item</li>", 600], ["</ul>", 1]],
        [["<table>", 1], ["<tr><td>1</td><td>row</td></tr>", 400], ["</table>", 1]],
    ]


# The one place the phone does not answer as the desktop does. The
# desktop's `tables.align` counts a second table's offsets in the text the
# first table's padding has already changed, while `shift_spans` judges
# every offset against the text as it came -- so a style inside a pasted
# page's second table lands on the spaces beside its words. The phone
# counts them in the text as it came (ISSUES_LOG, desktop findings). Its
# answers are made here by that same two-line change to the desktop's own
# function, run in this process only; the desktop's file is not touched.
ALIGN_FAULT = "pos = sum(len(line) + 1 for line in lines[:first])"
ALIGN_FIX = "pos = sum(len(line) + 1 for line in as_came[:first])"
ALIGN_LINES = 'lines = (text or "").split("\\n")'


def corrected_align():
    """`tables.align` with offsets counted in the text as it came -- or a
    string saying why the desktop's function no longer has the line."""
    import inspect
    import textwrap
    from noteorganizer.core import tables
    source = textwrap.dedent(inspect.getsource(tables.align))
    if source.count(ALIGN_FAULT) != 1 or source.count(ALIGN_LINES) != 1:
        return ("the desktop's tables.align has changed: re-read it, bring "
                "Pasting.align into line, then update ALIGN_* in this script")
    source = source.replace(ALIGN_FAULT, ALIGN_FIX)
    source = source.replace(ALIGN_LINES, ALIGN_LINES + "\n    as_came = list(lines)")
    space = dict(vars(tables))
    exec(compile(source, "<corrected tables.align>", "exec"), space)  # noqa: S102 -- the desktop's own code
    return space["align"]


def desktop():
    if not os.path.isdir(DESKTOP):
        return ("cannot find the desktop app at %s "
                "(set NOTEORGANIZER_HOME if it lives somewhere else)" % DESKTOP)
    if HERE not in sys.path:
        sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)      # the desktop's memory ceiling, before its code runs here
    if DESKTOP not in sys.path:
        sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.richtext import html_to_note, note_to_html, reflow_wrapped
        from noteorganizer.core.formatting import strip_list_markers
    except ImportError as problem:
        return "cannot import the desktop reader: %s" % problem
    return html_to_note, note_to_html, reflow_wrapped, strip_list_markers


PROSE = ("the quick brown fox jumps over a lazy dog while seven wizards box and "
         "quietly judge every phrase that typesetting keeps more-or-less intact. "
         "Café owners said “yes”; others didn’t. A long-wordwithoutbreaks "
         "\U0001F600 emoji sits here, then (brackets) and [squares] end it!").split(" ")


def wrapped(rng, width):
    """One paragraph broken at a column, the way a PDF's text comes out."""
    words = [rng.choice(PROSE) for _ in range(rng.randrange(8, 60))]
    lines, line = [], ""
    for word in words:
        if line and len(line) + 1 + len(word) > width:
            lines.append(line)
            line = word
        else:
            line = (line + " " + word) if line else word
    if line:
        lines.append(line)
    if rng.random() < 0.3 and len(lines) > 1:
        # A word hyphenated across the break.
        lines[0] = lines[0] + " type-"
        lines[1] = "setting " + lines[1]
    if rng.random() < 0.2:
        lines[0] = "    " + lines[0]
    return lines


def plain_texts(rng):
    """Plain text as other programs offer it: PDF columns, lists, tables,
    headings, and blank lines that are not quite blank."""
    out = ["", "one line", "a\nb\nc", "• item\n◦ deeper\n1. first\n- dash\n* star",
           "  • indented bullet\n\t2. tabbed number", "| a | b |\n| c | d |",
           # A paragraph's last line short of the column -- between half and
           # seven tenths of it -- and ending on no full stop: short enough
           # to end the paragraph.
           "The committee met on Tuesday morning to discuss the budget\n"
           "proposals that had been submitted by every department over\n"
           "the previous quarter and agreed on it\n"
           "Second paragraph starts here with enough words to fill the\n"
           "line completely and then continues onto the following line\n"
           "until it finally ends with a full stop."]
    for _ in range(110):
        blocks = []
        for _ in range(rng.randrange(1, 4)):
            roll = rng.random()
            if roll < 0.55:
                blocks.append("\n".join(wrapped(rng, rng.choice([40, 50, 60, 72, 90]))))
            elif roll < 0.7:
                blocks.append("\n".join(rng.choice(["• ", "1. ", "- ", "12. "]) + rng.choice(PROSE)
                                        for _ in range(rng.randrange(1, 5))))
            elif roll < 0.8:
                blocks.append("| x | y |\n| --- | --- |\n| 1 | 2 |")
            elif roll < 0.9:
                blocks.append(rng.choice(["Heading", "Chapter 2:", "short line.", "  "]))
            else:
                blocks.append("\n".join(rng.choice(PROSE) for _ in range(rng.randrange(3, 8))))
        out.append(rng.choice(["\n\n", "\n \t\n", "\n"]).join(blocks) + rng.choice(["", "\n", "  "]))
    return out


SEPARATOR_CELLS = ["---", ":---", "---:", ":---:", "------", ":-:", "--:", ":--", "~", "-",
                   "----------:", ":------:", "----"]


def separator_texts(rng):
    """Markdown tables as other programs write them, for `forget_widths`:
    every spelling of a separator cell, with prose and an emoji around the
    tables so the offsets are counted in code points."""
    out = ["", "no table", "| a | b |\n| --- | ---: |\n| x | y |",
           "| a | b |\n| c | d |\n| ---- | ---- |",
           "a ---- b\n|---|---:|",
           "\U0001F600 first\n| \U0001F600 | b |\n|:----|----:|\n| x | y |\n\n| p | q |\n|------|~|"]
    for _ in range(80):
        blocks = []
        for _ in range(rng.randrange(1, 4)):
            if rng.random() < 0.3:
                blocks.append(" ".join(rng.choice(PROSE) for _ in range(rng.randrange(1, 6))))
                continue
            cols = rng.randrange(2, 5)
            gap = rng.choice([" | ", "|", "  |  "])
            edge = gap[:gap.index("|")]
            rows = ["|" + edge + gap.join(rng.choice(PROSE) for _ in range(cols)) + edge + "|"]
            sep = "|" + edge + gap.join(rng.choice(SEPARATOR_CELLS) for _ in range(cols)) + edge + "|"
            rows.insert(rng.choice([1, 1, 1, 0]), sep)
            rows += ["| " + " | ".join(rng.choice(PROSE) for _ in range(cols)) + " |"
                     for _ in range(rng.randrange(0, 3))]
            blocks.append("\n".join(rows))
        out.append(rng.choice(["\n", "\n\n"]).join(blocks))
    return out


def round_trip_sources(rng):
    """Notes the app itself copies out: the export fixture's notes, as HTML."""
    path = os.path.join(ANDROID, "app", "src", "test", "resources", "export_cases.json")
    with io.open(path, encoding="utf-8") as f:
        notes = [c["note"] for c in json.load(f)["cases"]]
    return rng.sample(notes, ROUND_TRIPS)


def answer(html_to_note, html):
    report = {}
    try:
        text, spans, paragraphs = html_to_note(html, report=report)
    except Exception as problem:  # noqa: BLE001 -- recorded, not hidden
        return {"raises": type(problem).__name__}
    # Compact rows rather than objects: [start, end, style(, value)] and
    # [line, kind, indent, align], so a few thousand of them stay readable.
    out = {"formatting": [[s["start"], s["end"], s["style"]] + ([s["value"]] if "value" in s else [])
                          for s in spans],
           "paragraphs": [[p["line"], p.get("kind"), p.get("indent", 0), p.get("align")] for p in paragraphs],
           "text_cut": bool(report.get("text_cut")), "styles_cut": bool(report.get("styles_cut"))}
    if len(text) > LONG:
        out["text_length"] = len(text)
        out["text_sha256"] = hashlib.sha256(text.encode("utf-8", "surrogatepass")).hexdigest()
    else:
        out["text"] = text
    return out


def main():
    found = desktop()
    if isinstance(found, str):
        print(found)
        return 2
    html_to_note, note_to_html, reflow_wrapped, strip_list_markers = found
    fixed = corrected_align()
    if isinstance(fixed, str):
        print(fixed)
        return 2
    from noteorganizer.core import tables

    def expect(html):
        """The phone's answer: the desktop's, with the one fault fixed --
        and the desktop's own beside it wherever the two differ."""
        desktop_says = answer(html_to_note, html)
        original = tables.align
        tables.align = fixed
        try:
            phone = answer(html_to_note, html)
        finally:
            tables.align = original
        if phone != desktop_says:
            phone["desktop_formatting"] = desktop_says.get("formatting")
        return phone

    rng = random.Random(SEED)
    sources = [("edge", h) for h in EDGES] + [("random", fragment(rng)) for _ in range(RANDOM_CASES)]
    copies = []
    for note in round_trip_sources(rng):
        inline = rng.random() < 0.5
        try:
            html = note_to_html(note.get("body", ""), note.get("formatting"), note.get("paragraphs"),
                                inline_tables=inline)
        except Exception:  # noqa: BLE001 -- a note the export cannot write is no paste either
            continue
        # The note goes with it, so the phone's copy is checked as well as its paste.
        copies.append({"kind": "own copy", "html": html, "inline": inline,
                       "note": {k: note.get(k) for k in ("body", "formatting", "paragraphs")}})
    cases = []
    for kind, html in sources:
        case = {"kind": kind, "html": html}
        case.update(expect(html))
        cases.append(case)
    for case in copies:
        case.update(expect(case["html"]))
        cases.append(case)
    plain = []
    for text in plain_texts(rng):
        plain.append({"kind": "plain", "plain": text, "reflowed": reflow_wrapped(text),
                      "unmarked": strip_list_markers(text)})
    big = []
    for parts in long_cases():
        case = {"kind": "caps", "html_parts": parts}
        case.update(expect("".join(piece * times for piece, times in parts)))
        big.append(case)
    if not hasattr(tables, "forget_widths"):
        print("the desktop has no tables.forget_widths: bring it up to date first")
        return 2
    # A generator of its own, so the sections above keep their cases.
    forgotten = []
    for text in separator_texts(random.Random(SEED + 1)):
        out, shifts = tables.forget_widths(text)
        forgotten.append({"text": text, "forgotten": out, "shifts": [list(s) for s in shifts]})
    # One case to a line, so a change to the reader shows as the lines it moved.
    with io.open(FIXTURE, "w", encoding="ascii", newline="\n") as f:
        f.write('{"seed": %d, "python": "%s", "cases": [\n' % (SEED, sys.version.split()[0]))
        f.write(",\n".join(json.dumps(c, ensure_ascii=True, separators=(",", ":")) for c in cases + big))
        f.write('\n], "plain": [\n')
        f.write(",\n".join(json.dumps(p, ensure_ascii=True, separators=(",", ":")) for p in plain))
        f.write('\n], "forgotten": [\n')
        f.write(",\n".join(json.dumps(p, ensure_ascii=True, separators=(",", ":")) for p in forgotten))
        f.write("\n]}\n")
    raised = sum("raises" in c for c in cases + big)
    empty = sum(c.get("text") == "" for c in cases + big)
    cut = sum(c.get("text_cut", False) or c.get("styles_cut", False) for c in cases + big)
    print("%d fragments: %d read as nothing, %d cut by the caps, %d raised"
          % (len(cases) + len(big), empty, cut, raised))
    print("%d plain texts, %d of them rejoined as a column"
          % (len(plain), sum(p["reflowed"] != p["plain"] for p in plain)))
    print("%d where the desktop's second table puts its styles beside their words"
          % sum("desktop_formatting" in c for c in cases + big))
    print("%d texts with markdown tables, %d of them with a width forgotten"
          % (len(forgotten), sum(p["shifts"] != [] for p in forgotten)))
    print("written: %s" % os.path.relpath(FIXTURE, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
