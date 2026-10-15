# The data format, and the decisions that come with it

Note Organizer keeps a notebook as a few plain JSON files. The Windows
desktop app and this Android app read and write the **same** files — no
conversion step, no server in between — so a note written on one has to
come back whole on the other. This document says what is in those files,
and why the rules for them are what they are.

The Python desktop app is the specification. This file records the
contract as re-implemented in Kotlin, the places where the two languages
disagree about the same text, and the design decisions each of those
forced. `py tools/check_note_shape.py` fails when the two programs stop
agreeing about what a note is
([how it reads the Python](ARCHITECTURE.md#the-desktop-is-the-specification)).

If you only need the shape of a note, read [The data contract](#the-data-contract)
and [A note](#a-note) and stop there. Everything after them is grouped by
subject, and most of it is traps: each one cost something to find, and
each keeps the reason it is there. Read a group before touching anything
that reads or writes what it covers.

## Contents

- [The data contract](#the-data-contract)
- [A note](#a-note)
  - [Where `uid` comes from, and who may mint one](#where-uid-comes-from-and-who-may-mint-one)
  - [Recurrence](#recurrence)
  - [Clearing a date deletes a recurrence rule](#clearing-a-date-deletes-a-recurrence-rule)
  - [The ceilings, and why they exist](#the-ceilings-and-why-they-exist)
- [Formatting](#formatting)
  - [What a span is](#what-a-span-is)
  - [Offsets are code points, and Kotlin counts UTF-16](#offsets-are-code-points-and-kotlin-counts-utf-16)
  - [Styles are algebra, not a flag](#styles-are-algebra-not-a-flag)
  - [Editing plain text moves formatting onto the wrong words](#editing-plain-text-moves-formatting-onto-the-wrong-words)
  - [A highlight must blend toward the page](#a-highlight-must-blend-toward-the-page)
  - [A format bar has two enemies on a phone, and both are the system's](#a-format-bar-has-two-enemies-on-a-phone-and-both-are-the-systems)
- [Tables](#tables)
  - [Measure the real notebook before deciding what to support](#measure-the-real-notebook-before-deciding-what-to-support)
  - [Editing one cell must not rewrite the whole table](#editing-one-cell-must-not-rewrite-the-whole-table)
  - [The drawn grid and the file do not number rows the same way](#the-drawn-grid-and-the-file-do-not-number-rows-the-same-way)
- [Share codes](#share-codes)
  - [What a code carries](#what-a-code-carries)
  - [Interop is the test](#interop-is-the-test)
  - [Why a code carries the note's identity](#why-a-code-carries-the-notes-identity)
  - [Build the code from the stored note, never from what is on screen](#build-the-code-from-the-stored-note-never-from-what-is-on-screen)
  - [The code field on a phone](#the-code-field-on-a-phone)
  - [What a shared note is allowed to change](#what-a-shared-note-is-allowed-to-change)
- [Writing the file](#writing-the-file)
  - [A partial reader must not become a total writer](#a-partial-reader-must-not-become-a-total-writer)
  - [A delete touches two files, so the ORDER of the writes is the safety](#a-delete-touches-two-files-so-the-order-of-the-writes-is-the-safety)
  - [A copy of the array is not a copy of the notes](#a-copy-of-the-array-is-not-a-copy-of-the-notes)

## The data contract

Plain JSON, no database. When the desktop app runs as its packaged
`.exe`, its files are in `%APPDATA%\NoteOrganizer\`; this app keeps its
copy of the notebook in its own folder on the device
([how to put one there](../README.md#building-and-running-it)).

| File | Holds |
|---|---|
| `notes_data.json` | the notes, as a JSON array |
| `settings.json` | theme, sort, filter, last-open note, categories |
| `trash.json` | deleted notes awaiting purge — the Trash |
| `templates.json` | saved templates |
| `attachments/` | files attached to notes |

## A note

Every note passes through one choke point in the desktop app
(`normalize_note`), which coerces anything malformed into these defaults.
Field for field, as of `0.73.165`:

```jsonc
{
  "id": 1,                  // int, unique within the file -- a note's PLACE,
                            //   not its name: two devices both hand a new
                            //   note max+1, so both invent 87
  "uid": "…",               // str, a uuid4 -- the note's name. Minted once,
                            //   never reused, carried by share codes. What
                            //   lets an arriving copy be recognised as a note
                            //   this device already has
  "title": "",              // str
  "body": "",               // str — the note text, newline-separated
  "formatting": [],         // spans over `body`; see Formatting
  "comments": "",           // str — the separate comments box
  "category": "General",    // str — one of the categories in settings.json
  "date": "",               // "" or "YYYY-MM-DD"
  "color": "",              // "" or "#rrggbb"
  "attachments": [],        // [{"filename": str, "stored_as": str}, ...]
  "updated": "",            // "YYYY-MM-DD HH:MM:SS" -- seconds included
  "created": "",            // same shape; backfilled from updated if absent
  "recurrence": {},         // the repeat rule; "" freq = one-off. See below
  "exceptions": [],         // ["YYYY-MM-DD", ...] dates removed from a series
  "overrides": {},          // {"YYYY-MM-DD": {partial note}, ...}
  "pinned": false,          // bool — pinned notes float to the top
  "checklist": [],          // [{"text": str, "done": bool}, ...]
  "paragraphs": []          // [{"line": int, "kind": "bullet"|"number"}, ...]
}
```

### Where `uid` comes from, and who may mint one

The desktop mints them at its own door, for every note, on the first read
that finds one missing — and, from its `0.73.161`, writes them to the file
there and then. That last part matters here: before it, the identities
existed only in the desktop's memory until something saved, so a notebook
copied to a phone could carry a set of identities the desktop itself would
not recognise after a restart.

**This app mints only for notes IT creates.** A note that arrives without
an identity is left exactly as it is. If both programs minted, the same
note would end up with two different names — the collision the field
exists to prevent, manufactured by the fix for it.

### Recurrence

A repeating note is stored **once**. The rule:

```jsonc
{
  "freq": "",                  // "" | "daily" | "weekly" | "monthly" | "yearly"
  "interval": 1,               // every N days/weeks/months/years
  "weekdays": [],              // weekly only: 0=Mon .. 6=Sun
  "month_days": [],            // monthly only: 1..31
  "month_day_policy": "clamp", // "clamp" -> Feb 28/29, "skip" -> no occurrence
  "end": "",                   // "" = never ends, else "YYYY-MM-DD"
  "count": 0                   // 0 = unlimited, else stop after N occurrences
}
```

**A note is recurring when `recurrence.freq` is non-empty — never when
the dict is non-empty.** `normalize_note` fills every note's rule with
the defaults above, so in a real saved file *all* notes carry a full
`recurrence` object and an emptiness test marks the entire notebook as
repeating. The desktop app's own test is one line, in
`noteorganizer/core/recurrence.py`:

```python
return bool((note.get("recurrence") or {}).get("freq"))
```

Measured against a real 77-note file: emptiness said 77 recurring,
`freq` said 0. Copy the Python's test, not the field list's shape.

Plus two maps on the note itself: `exceptions` (dates deleted
individually) and `overrides` (date → partial note, for "edit only this
day").

**An override may only replace these fields:** `title`, `body`,
`comments`, `category`, `color`. Everything else belongs to the series.
Getting this wrong produces a note that looks edited and is not.

### Clearing a date deletes a recurrence rule

`normalize_note` throws away a `recurrence` whose note has no valid date:

```python
if clean["recurrence"]["freq"] and not parse_date(clean["date"]):
    clean["recurrence"] = normalize_recurrence({})
```

So letting someone clear the date on a repeating note looks harmless and
silently deletes the repeat at the desktop's next read. `updateNote`
refuses it and says why. This is the shape to watch for generally: a
field whose emptiness makes `normalize_note` discard a DIFFERENT field.

A related habit, for the same reason: every optional field on a write is
**nullable, meaning "leave it alone"**, so a caller editing only the text
cannot wipe a note's colour by not mentioning it.

And a date is parsed with leniency OFF. Left lenient, `2026-02-30`
quietly becomes the 2nd of March, and the note shows a day nobody chose.

### The ceilings, and why they exist

These are **anti-bomb limits, not performance promises**. A pasted share
code is a few kilobytes and is saved before it is ever drawn, so an
unbounded field could make the app fail to open on every launch
afterwards — which happened, twice, with real fixes behind it.

| Limit | Value |
|---|---|
| `MAX_CHECKLIST_ITEMS` | 1000 |
| `MAX_ATTACHMENTS` | 200 |
| `MAX_FORMATTING_SPANS` | 10000 (whole note) |
| `MAX_SPANS_PER_LINE` | 500 (covering any one line) |
| `MAX_LINE_RANGES` | 20000 (total ranges a note may draw) |
| `MAX_CATEGORIES` / `MAX_CATEGORY_NAME` | 200 / 80 chars |
| `MAX_OCCURRENCES` | 5000 per expansion |
| `MAX_RECURRENCE_INTERVAL` | 1000 |

Anything reading this data should assume the file may be hand-edited or
hostile, and coerce rather than trust — the desktop app does exactly
that, at one choke point, and it is the reason bad data has never
crashed it into an unopenable state twice running.

## Formatting

A note's styling lives in two fields: `formatting` holds character
styles as spans over the body, and `paragraphs` holds the bullet and
number markers by line. The traps below cover how a style is stored,
applied, kept through an edit, and drawn.

### What a span is

A span is `{"start": int, "end": int, "style": str}` over **character
offsets into `body`** — not line/column positions, deliberately, so they
survive being saved, shared and reopened at a different window size.

Styles come in three kinds:

| Kind | Styles |
|---|---|
| on/off, no value | `bold`, `italic`, `bold_italic`, `underline`, `strike` |
| a hex colour in `"value"` | `text_color`, `highlight` |
| a font value in `"value"` | `font_size` (int), `font_family` (str) |

`bold_italic` is one style rather than two overlapping spans because two
Tk tags that both set a font do not compose. That is a Tkinter
constraint; an Android renderer is free to handle it differently, but the
**stored data will contain `bold_italic`** and must be read.

### Offsets are code points, and Kotlin counts UTF-16

Python counts a string in characters, so an emoji is one. Kotlin indexes
in UTF-16 units, so the same emoji is two. **Every span after an emoji
lands one position early per emoji before it** — half a character
emboldened, the last letter missed.

This is not hypothetical: against a real 77-note file, 4 notes would have
drawn their styling in the wrong place, one across 24 spans shifted by 3.
`utf16Offsets` in `Notebook.kt` converts, resolving all the offsets in
one walk of the body rather than counting from the start for each — the
same shape as the desktop's `_char_offsets` fix, which took a 15-second
startup down to 1.5.

The trap runs the other way when a style is applied: a text field reports
its selection in UTF-16 while the file stores code points — so
`codePointOffsets` is the mirror of `utf16Offsets`, and skipping it puts
every style one character late after an emoji.

### Styles are algebra, not a flag

Applying a style where another one already is needs real rules, and the
one the desktop imposes is the surprising one: **bold and italic share a
group**, because their combination is stored as the single style
`bold_italic` ([above](#what-a-span-is)). Bolding italic text must
therefore produce `bold_italic`, not two overlapping spans, or the
desktop draws one and loses the other. `Styling.kt` does this with a
boundary sweep: collect every edge in the group, decide each elementary
interval, merge the runs back up. Interval arithmetic done by hand is
where these bugs live.

A toolbar button reads as "on" only when the style covers the **whole**
selection, or a mixed selection would need two presses to go uniformly
bold.

### Editing plain text moves formatting onto the wrong words

Spans are positions. Delete a word near the top of a note and everything
after it slides while the stored numbers do not: the bold still says
40..55 and now sits on different text. Not lost — **moved**, which is
worse, because nothing looks broken. 41 of a real notebook's 77 notes
carry formatting, so this is the common case, not the corner one.

`remapSpans` locates the edit by what did NOT change — the longest
identical prefix and suffix — then:

* a span wholly before or after the edit keeps its text;
* a span that reaches PAST the edit on both sides is stretched, or fixing
  a typo would strip the styling off the sentence around it;
* a span lying entirely INSIDE the edited stretch is dropped and counted,
  because its text is gone. A first version missed this case and handed
  the user's newly typed words the styling of the words they had just
  deleted — bold on `bb`, replaced with `NOU NOU`, came back as bold
  `NOU NOU`. A test caught it; nothing about it looked wrong.

Bullet and number markers are stored as LINE indices and need the same
treatment one level up. `remapParagraphLines` returns old line → new line
rather than a bare list of survivors: pairing positionally means that
dropping one marker gives every later marker the wrong kind. That bug was
written, then caught by a test, then fixed.

### A highlight must blend toward the page

The stored colour is a palette swatch chosen against a white page. Paint
it solid on a dark one and it becomes a pale wash that buries the text.
The desktop hit this and fixed it by blending the paint 45% toward the
current theme's surface while the **data keeps the pure colour**; its own
comment is blunt: *"the solid palette fill buried the text under it."*
Re-blend per theme, never rewrite the note.

### A format bar has two enemies on a phone, and both are the system's

Selecting a word makes Android raise its **own** floating menu — copy,
share, translate — directly over the selection. A format bar above the
text is covered by it: the first attempt put it there and a tap meant for
bold hit "Translate" and launched a web search. Moving the bar to the
bottom escapes that menu and lands it under the **keyboard** instead,
which an edge-to-edge window does not make room for. `Modifier.imePadding()`
on the screen's root is what lifts it. Both problems are invisible in
source and obvious in one photograph.

## Tables

A table is not a field of its own: it is pipe rows inside `body`, drawn
as a grid. The rules here are about what to support, and how to read and
rewrite those rows without disturbing the text and spans around them.

### Measure the real notebook before deciding what to support

Counted across 77 real notes, before a line of the renderer was written:
59 table blocks, up to 67 rows and 20 columns, and the features used were
checkbox cells **143** times, merge-left **102** times, `<br>` inside a
cell 34 times, separator rows 14 times — and merge-UP and hidden columns
**not once**. Merge-left is a main case, not a corner one. Guessing would
have put the effort in the wrong place.

Three decisions follow from that:

* **Monospaced cells.** The parser returns column widths counted in
  CHARACTERS, the way `column_widths` does, and a fixed-advance font turns
  that straight into a width — no measuring pass, no guessing. A column
  stops growing at 26 characters and wraps, or one cell holding a
  paragraph makes every row that tall.
* **Sideways scrolling, not squeezing.** Twenty columns crushed into a
  phone's width is not a table.
* **A cell spanning several columns is left out of the width
  calculation**, or it would widen a column to fit text that is not in it.

`splitBody` cuts the note into prose runs and tables and hands back
UTF-16 offsets so the caller SLICES the already-styled text. Re-rendering
each run on its own would need the formatting offsets recomputed per
piece — a second copy of a rule, which is how they drift. Note the two
coordinate systems living side by side: formatting spans are code points,
these slices are UTF-16
([why that matters](#offsets-are-code-points-and-kotlin-counts-utf-16)).

Cell text is drawn plain: a cell is not a slice of the body once the row
has been taken apart, so styling inside a table is not shown. The screen
says so, and only when the note has both.

### Editing one cell must not rewrite the whole table

Rebuilding a table re-pads every line. The span remap finds an edit by
what did NOT change, so a fully re-padded table reads as one enormous
changed region and **every style inside it is dropped**. Measured on a
real note: changing one cell lost a colour three rows away.

So a cell edit rewrites only that cell's stretch of its own line and
leaves every other byte alone — which also preserves each cell's
alignment for free, since the padding IS the alignment and untouched
padding cannot be misread. Structural changes (a row or column added or
removed) do rebuild, because they must.

### The drawn grid and the file do not number rows the same way

`parseTable` drops the separator row, so from that row downwards a row
number taken off the screen is one short of the file's. Taken at face
value it edits the row ABOVE the one tapped — which is exactly what
happened, and what the written file showed. `TableBlock.fileRow()` is the
conversion; nothing should index the file with a drawn row without it.

A related trap for the tests, not the app: a one-column table is **not a
table**. A row needs at least two cells (`_MIN_CELLS`), so a test written
with `| --- |` measures nothing at all. One was.

## Share codes

Share codes are the transfer mechanism between the two programs, and
interop is the test. What the user sees of them is in the
[README](../README.md#what-it-does-today); this is what they contain and
what they may do.

### What a code carries

A code is a small JSON payload, zlib-compressed, Base64'd, behind
`NOTE1:`. It carries content and never attachments — not for size, for
safety: an imported note must not be able to smuggle in a `stored_as`
that a later delete would follow out of the attachments folder. Both ends
project through the SAME field list, so the boundary is symmetric.

**It is not encryption.** The screen says so, beside the code.

### Interop is the test

The check that matters is not the round trip — it is that the OTHER
program can read it. `InteropTest` decodes a code produced by the Windows
app's own `encode_share_code`, and writes one out for that Python to
decode in turn. Both were run and both passed, carrying diacritics, an
emoji, formatting spans and a checklist intact. A test that only encodes
and decodes with itself proves nothing about the thing it exists for.

### Why a code carries the note's identity

An import used to always CREATE, because a code carried no id and
matching on the text would have been the merge problem in disguise:
importing twice gave two notes, which is visible and fixable, where
overwriting the wrong one is not. That reasoning was right, and it is
what made the `uid` necessary rather than optional. Since the desktop's
`0.73.159` a code carries the note's identity, so the far end can tell
the difference between "another note that reads the same" and "the note
you already have" — and asks. See
[What a shared note is allowed to change](#what-a-shared-note-is-allowed-to-change).

### Build the code from the stored note, never from what is on screen

`shareCodeFor` reads the note out of the array and encodes that. The
desktop's editor did the opposite — it assembled a code from its editor
snapshot — and so its codes silently went out without the identity,
because identity is not something the screen holds. Fixed there in
`0.73.165`; this app never had the bug, and this line is why.

### The code field on a phone

Two things the code field must do that are not obvious: turn autocorrect
and auto-capitalisation OFF (a "helpfully" capitalised letter makes a
Base64 code merely *damaged*, and the app can then only say so, which is
true and useless), and expect the panel to MOVE as the code is typed —
a scripted tap on the button's old position misses it, which cost three
runs before the screen was actually read.

### What a shared note is allowed to change

An arriving note replaces ten fields of the copy here — `title`, `body`,
`formatting`, `paragraphs`, `comments`, `category`, `date`, `color`,
`recurrence`, `checklist` — and nothing else. Not the `id`, which belongs
to this file. Not the `uid`, which is what made the match. Not `created`,
because an update is not a birth. Not the attachments, because **a code
never carries files**: it holds text, and a note's attachments live
beside the notebook. Replacing them from a code would empty them.

The old version goes into `_prev` first — the desktop's own one-step
rescue, which its editor restores from with "↩ Restore previous". So an
update accepted by mistake on the phone is one button away from being
undone on the machine that has the button. The phone still cannot restore
from `_prev` itself.

Answering "add" instead strips the `uid` from the copy before it is
written. A note the user asked to keep SEPARATE must not wear the
identity of the one it came from — otherwise the next code turns two
notes into one, which is the collision the field exists to prevent.

The update itself is written the way every write is, with one trap of its
own: [a copy of the array is not a copy of the
notes](#a-copy-of-the-array-is-not-a-copy-of-the-notes).

## Writing the file

The rules here are what a program writing this file must keep; how this
app's code is shaped around them is in
[ARCHITECTURE](ARCHITECTURE.md#decision-write-to-a-copy-then-to-disk-then-to-the-screen).

### A partial reader must not become a total writer

This app's `Note` model reads only what the screen needs, so a write
never rebuilds the file from it — that would delete, from every note, the
fields the model never read
([why there are two representations](ARCHITECTURE.md#decision-a-note-has-two-representations-on-purpose)).
Every write carries the desktop's guards:

* work on the array that was READ — append to it, or replace one entry
  inside it — and never rebuild the file from the partial Kotlin model;
* refuse to overwrite a file that exists but would not parse;
* mend unpaired surrogates;
* write to a sibling `.tmp`, `sync()` it, then rename over the real file;
* return the reason on failure instead of looking like it saved.

### A delete touches two files, so the ORDER of the writes is the safety

`notes_data.json` and `trash.json` cannot both be written at once, and a
phone is killed casually. The trash copy goes first: interrupted there,
nothing happened at all; interrupted after it, the note is in **both**
files — a duplicate, which is visible and fixable. The other order leaves
the note in neither, which is the one outcome that cannot be undone.
Restoring mirrors it: the notebook first. If the trash cannot be read at
all, the delete refuses to start rather than proceeding without a
rescue copy.

**The phone never purges.** The countdown is read and shown; running it
out is the desktop's job, because the desktop is also what can delete a
purged note's attachment files. A phone that emptied the Trash without
them would leave orphans on every machine sharing the folder.

A rollback must never edit the shared array in place: `JSONArray.remove`
shifts everything left, so putting a note back at its old index
overwrites whatever slid into that slot — a rollback that deletes a
different note. Every write here builds a candidate array, writes it, and
only then changes the shared one.

**A mutant that proves nothing looks exactly like one that passed.** The
first attempt at the ordering mutant moved the notebook write earlier but
left the early return in place, so the trash was still written and the
test still went green — "the mutant survived" would have been read as
"the test is weak", when in fact the mutant was wrong. The real mutant —
notebook first, return before the trash — kills the test immediately.
When a mutant survives, check that it changed the behaviour you meant
before concluding anything about the test.

### A copy of the array is not a copy of the notes

**The candidate array shares its note objects with the live one.**
`copyOf` copies references, so editing a note found in the candidate
edits what the screen is reading — before the write has had a chance to
fail. Applying a shared update therefore works on a *copy* of the note
and replaces the entry. This was found by a mutant that survived: the
check said the screen matches the disk, and it passed with the code that
keeps them in step deleted, because the sharing was holding it up. A
surviving mutant is a question about the code as much as about the check
([the story](ISSUES_LOG.md#an-update-that-reached-the-screen-before-it-reached-the-file)).
