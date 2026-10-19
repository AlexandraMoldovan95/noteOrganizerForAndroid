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
  - [Settings the two apps share](#settings-the-two-apps-share)
- [A note](#a-note)
  - [Where `uid` comes from, and who may mint one](#where-uid-comes-from-and-who-may-mint-one)
  - [Recurrence](#recurrence)
  - [Clearing a date deletes a recurrence rule](#clearing-a-date-deletes-a-recurrence-rule)
  - [The ceilings, and why they exist](#the-ceilings-and-why-they-exist)
- [Formatting](#formatting)
  - [A list item is told by its marker, not by its entry](#a-list-item-is-told-by-its-marker-not-by-its-entry)
  - [What a span is](#what-a-span-is)
  - [Offsets are code points, and Kotlin counts UTF-16](#offsets-are-code-points-and-kotlin-counts-utf-16)
  - [Styles are algebra, not a flag](#styles-are-algebra-not-a-flag)
  - [Editing plain text moves formatting onto the wrong words](#editing-plain-text-moves-formatting-onto-the-wrong-words)
  - [A highlight must blend toward the page](#a-highlight-must-blend-toward-the-page)
  - [A format bar has two enemies on a phone, and both are the system's](#a-format-bar-has-two-enemies-on-a-phone-and-both-are-the-systems)
  - [What a paste may bring](#what-a-paste-may-bring)
- [Tables](#tables)
  - [Measure the real notebook before deciding what to support](#measure-the-real-notebook-before-deciding-what-to-support)
  - [Editing one cell must not rewrite the whole table](#editing-one-cell-must-not-rewrite-the-whole-table)
  - [The drawn grid and the file do not number rows the same way](#the-drawn-grid-and-the-file-do-not-number-rows-the-same-way)
  - [Where a table keeps what is not words](#where-a-table-keeps-what-is-not-words)
- [Templates](#templates)
- [Share codes](#share-codes)
  - [What a code carries](#what-a-code-carries)
  - [Interop is the test](#interop-is-the-test)
  - [Why a code carries the note's identity](#why-a-code-carries-the-notes-identity)
  - [Build the code from the stored note, never from what is on screen](#build-the-code-from-the-stored-note-never-from-what-is-on-screen)
  - [The code field on a phone](#the-code-field-on-a-phone)
  - [What a shared note is allowed to change](#what-a-shared-note-is-allowed-to-change)
- [Backups](#backups)
  - [What a backup holds](#what-a-backup-holds)
  - [The guards on the way in](#the-guards-on-the-way-in)
  - [The questions, and what joins without asking](#the-questions-and-what-joins-without-asking)
- [Writing the file](#writing-the-file)
  - [A partial reader must not become a total writer](#a-partial-reader-must-not-become-a-total-writer)
  - [A delete touches two files, so the ORDER of the writes is the safety](#a-delete-touches-two-files-so-the-order-of-the-writes-is-the-safety)
  - [A copy of the array is not a copy of the notes](#a-copy-of-the-array-is-not-a-copy-of-the-notes)

## The data contract

Plain JSON, no database. When the desktop app runs as its packaged
`.exe`, its files are in `%APPDATA%\NoteOrganizer\`; this app keeps its
own notebook in its private storage, in `files/notebook/`, where no other
app can read it. The files are the same files, byte for byte in shape;
a notebook moves between the two as a [backup](#backups), and single
notes as [share codes](#share-codes).

| File | Holds |
|---|---|
| `notes_data.json` | the notes, as a JSON array |
| `settings.json` | name, theme, sort, filter, categories, the Trash's window, and more ([below](#settings-the-two-apps-share)) |
| `trash.json` | deleted notes awaiting purge — the Trash |
| `templates.json` | saved templates |
| `attachments/` | files attached to notes |

A file that exists and will not parse is **kept aside, not refused**: its
bytes are copied beside it as `<name>_corrupt_<yyyyMMdd_HHmmss>.json`
(once per distinct content), and the app opens without it and says where
the copy is — the desktop's answer, so that a broken file costs a
message, not a phone that cannot take a single note.

### Settings the two apps share

`settings.json` is read with the desktop's own keys — `username`,
`theme`, `note_sort`, `note_filter`, `categories`, `trash_retention_days`,
`welcome_offered`, `welcome_note_uid`, `last_export_at` — so a backup
carries the person's choices both ways. A key the phone does not use is
kept, never dropped.

Phase 2 reads and writes five more, and two old ones with every value
the desktop gives them, each as the desktop keeps it:

| Key | Holds |
|---|---|
| `note_sort`, `note_filter` | not new, but now every value the desktop stores: the sort's own words (`"Calendar: soonest"`, `"Custom order"`), and Show as a category's name or a type's label (`"🔁 Repeating"`, `"📅 Dated"`, `"📎 With files"`). A filter naming a category since deleted reads as All. |
| `group_by_category` | `true` when the list is grouped |
| `collapsed_groups` | the folded groups' category names |
| `day_colors` | `{"YYYY-MM-DD": "#rrggbb"}` — a painted day; only real colours are read |
| `day_emoji` | `{"YYYY-MM-DD": [str, ...]}` — a day's markers, three at most; the older one-marker string is read too |
| `first_launch_date` | the day this install was first used, stamped once and never again |

A rename of a category carries its name along in `note_filter` and
`collapsed_groups`; a delete drops its folded group and returns Show to
All — and the categories, the folds and the filter are written in one
write, so no file ever holds one without the others.

The phone adds three keys of its own, for **"Follow the phone"**:
`phone_theme_follows_phone` (on unless turned off), `phone_theme_light`
and `phone_theme_dark`. They are separate on purpose: the desktop reads
`theme` and ignores these, so following the phone's dark mode can never
change the theme the desktop opens in. With following off, the phone
writes `theme`, as the desktop does.

The categories are an ordered map of name to colour, and the order is the
order Settings shows. Categories found on notes but missing from the map
are adopted with a colour, as the desktop's `load_categories` does.

## A note

Every note passes through one choke point in the desktop app
(`normalize_note`), which coerces anything malformed into these defaults.
Field for field, as of the desktop's `0.95.382` — and
`tools/check_note_shape.py` fails the day this list and the Python's
stop agreeing:

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
  "moved": {},              // {"YYYY-MM-DD": "YYYY-MM-DD"}: a day of a series
                            //   held on another date (desktop 0.92.374)
  "pinned": false,          // bool — pinned notes float to the top
  "checklist": [],          // [{"text": str, "done": bool}, ...]
  "paragraphs": []          // [{"line": int, "kind": "bullet"|"number",
                            //   "indent": int, "align": str}, ...] -- an entry
                            //   may have NO kind: a centred line is one
}
```

Two more keys travel beside these and are kept whole, though they are not
defaults: `_prev`, the one-step rescue copy
([what writes it, and what restores it](#what-a-shared-note-is-allowed-to-change)),
and `order`, the desktop's custom order.

Since Phase 3 the phone writes `order` too, as the desktop's arranging
does: every note numbered by its place — the hidden ones too, so a
search or a filter never loses a note its place — in one write, and with
**no `updated` stamp**, because arranging a list is not editing a note.
Choosing Custom order before anything has been placed numbers the notes
as the list was showing them, so the choice changes nothing on screen.

**An attached file** is kept in `attachments/` under the moment it was
added, to the microsecond, and its own name — the desktop's
`add_attachment` — made a name Windows will take, because a backup
carries it there. Taking a file off a note deletes the stored file only
when no other note, and no note in the Trash, still names it.

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

Plus three maps on the note itself: `exceptions` (dates deleted
individually), `overrides` (date → partial note, for "edit only this
day") and `moved` (a day of the series held on another date).

**An override may only replace these fields:** `title`, `body`,
`comments`, `category`, `color`. Everything else belongs to the series.
Getting this wrong produces a note that looks edited and is not.

Since Phase 2 the phone writes all three, as the desktop's day mode
writes them:

- **A day saved on its own** keeps only those five fields, and only
  plain text — a day's body carries no styles. A day that says exactly
  what the series says keeps no override at all, and follows the series
  again.
- **A day deleted** from its series goes into `exceptions` — a moved day
  by the date its rule gives it, not the date it was moved to — and its
  Undo finds the note by `uid`, not by `id`, and brings back the day's own
  edit and its move with it.
- **A repeat turned off** when some day holds its own text puts the whole
  note — the rule, the deleted days, the edits — into `_prev` first, so
  "↩ Restore previous" brings the series back whole.

Phase 3 writes the other two the day mode has:

- **A day moved** is written in `moved` against the date its rule gives
  it — `{"2026-10-20": "2026-10-22"}` — and its own edit, if it has one,
  moves with it in `overrides`. Moved back home, the entry goes. A day
  may move only to a date the series has no day on, and comes home only
  to one ([the desktop's own date rule does not ask](STATUS.md#where-it-differs-from-the-desktop-on-purpose)).
- **A day off the schedule** — an override on a date the rule no longer
  gives, after the repeat changed — is settled as the desktop settles
  it: made a note of its own on that date (its title, text, comments,
  category and colour; the series' formatting only where the day's text
  is the series' own; the lines' entries; none of the checklist or the
  files, which were never the day's), or that note sent to the Trash.
  Either way the override goes, and so does a move that brought the day
  there.

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

**A size is a number.** `font_size`'s value is an `int` in the file —
`{"style": "font_size", "value": 24}` — and the desktop's
`normalize_formatting` drops a size written as text. The phone once read
only text there, so every desktop size came in empty and went back out
without a value ([the story](ISSUES_LOG.md#a-desktop-note-lost-its-sizes-on-the-way-through-the-phone)).
It reads and writes numbers now, and only numbers: a size in quotes,
which the desktop refuses, is not revived here either.

### A list item is told by its marker, not by its entry

The desktop draws a list from the **text**: a line that begins with a
bullet glyph (`• `, `◦ `, `▪ ` by depth) or a number (`1. `) is a list
item. What `paragraphs` stores is worked out from the body when the note
is saved — the kind read off the marker, and the indent and alignment
kept for the line — so a body and its entries cannot disagree for long.
The phone does the same: the editor keeps each line's indent and
alignment in step with its text, and the save writes the entries.

What counts as an entry is the desktop's `normalize_paragraphs`: `line`
a whole number within the text — `true` and `false` count as 1 and 0,
as Python counts them, and `1.0` does not count — and the **first**
usable entry for a line wins. Every numbered run is renumbered 1, 2, 3
after each change: a counter per depth, a deeper list starting again
under each item, a bullet interrupting its depth and those below, one
blank line not ending a list and two ending it. That rule was checked
against the desktop's own renumbering over 300 bodies.

Typing into a line keeps its entry. Only a change in the number of
lines — a break added or taken away — may drop the entries inside the
edited stretch, for fear of moving one onto a stranger's line.

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

### What a paste may bring

Exactly what the desktop's paste brings, because it is the desktop's
reader (`html_to_note`) ported, and what it reads passes the same two
gatekeepers a saved note does: bold, italic, underline, strikethrough;
a text colour only when it is really a colour (a page's greys are its
theme, not anyone's choice); a highlight only when it is pale enough to
read black text through; lists as markers in the text; alignment; tables
as pipe rows, re-padded. Sizes and faces are left behind — a web page
uses neither of the desktop's lists, and matching would mean claiming a
note is set in something it never was.

Laid into the note, a pasted highlight goes **line by line, never over a
line's end**, as the desktop lays every highlight. The app's own copy is
the exception to what is left behind: pasted back into a note, it brings
its sizes and faces, kept from the moment it was copied.

The caps are the desktop's: 100,000 characters and 2,000 styled pieces,
past which the paste says what it left out.

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

### Where a table keeps what is not words

A table's edits — Phase 2's cell menu, the table's bar since — write
four things, each where the desktop keeps it, none of them in a field of
its own:

| What | Where it lives |
|---|---|
| A cell's alignment | its **padding**: spaces after the words for left, before for right, both for centre. A cell's own padding comes before the column's, which the separator's colons give. |
| A cell's highlight | a `highlight` span laid **pipe to pipe**, over the whole cell and its padding, so whatever is later typed in the cell lands inside it. A span over part of a cell is a style on its words, not a fill. |
| A merge | the marker as the cell's whole text: `<` joins the cell to its left, `^` to the one above. Splitting empties every cell the merge took and leaves the first its words. |
| The table's place on the page | the alignment of **its lines** in `paragraphs` — a centred table is every one of its lines centred |

Phase 3 adds three more, all in the text:

| What | Where it lives |
|---|---|
| A column's width | the **dashes** of its separator cell, counted with its colons. Three or fewer is markdown's way of writing any separator at all and sets no width; a hand-widened column is always wider. Narrowed below its words, a column is drawn clipped, an ellipsis at the cut; its words stay whole in the file. |
| A hidden column | `~` as its separator cell |
| A caption | two lines under the table, `Table N: …` and `Source: …`, placed as the table is; N comes after every caption the note already has, the older `Tab. N:` ones included |

The separator stays under the first row, whatever is inserted above it
or deleted from it: moved to the third line it is no longer read as a
separator, and its dashes turn into a row of text, and left on the first
line it makes no table at all. So the header row deleted, the next row
is the header. A table keeps two columns, as the desktop's does: a
column of two deleted is emptied, since one column reads as prose. And
a merge marker whose partner a deletion took is emptied, as
`_dissolve_orphan_markers` empties it
([the story](ISSUES_LOG.md#two-deletes-the-desktop-had-already-settled)).
And **an edit that changes nothing writes
nothing** — a marker asked for on the separator, the last row asked to
go — or the whole table comes back re-padded, every style in it at risk
([the story](ISSUES_LOG.md#a-table-edit-nobody-asked-for)).

## Templates

`templates.json` is a list of templates, each shaped as the desktop's
`normalize_template` leaves it:

```jsonc
{
  "name": "Weekly plan",   // str, the template's own name; "Untitled template" if empty
  "title": "",             // what a note made from it is called
  "body": "",              // its text, tables and list markers included
  "formatting": [],        // spans, kept only as normalize_formatting keeps them
  "paragraphs": [],        // line entries, as normalize_paragraphs keeps them
  "comments": "",
  "category": "General",   // "General" when empty; a note made from it files
                           //   under General when the category is gone
  "checklist": []          // [{"text": str, "done": bool}, ...], ticks kept
}
```

Two saving rules, both the desktop's: **Save as template** from a note
puts the template last and drops any other with its name — after asking;
the **editor in Settings** keeps an edited template in its place. A file
nobody can read, or a list of junk, is copied aside before anything
writes over it, as a broken `notes_data.json` is. Templates do not go to
the Trash: a delete is for good, and says so.

## Share codes

Share codes are the transfer mechanism between the two programs, and
interop is the test. What the user sees of them is in the
[README](../README.md#what-it-does); this is what they contain and
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
rescue — and both apps restore from it with **"↩ Restore previous"**. So
an update accepted by mistake is one button away from being undone, on
either device. `_prev` holds the text fields and, after an update, every
other field the update replaced; restoring puts all of them back and
spends the copy. An update that would change nothing does not touch the
note at all, so it cannot spend a rescue copy on nothing.

Answering "add" instead strips the `uid` from the copy before it is
written. A note the user asked to keep SEPARATE must not wear the
identity of the one it came from — otherwise the next code turns two
notes into one, which is the collision the field exists to prevent.

The update itself is written the way every write is, with one trap of its
own: [a copy of the array is not a copy of the
notes](#a-copy-of-the-array-is-not-a-copy-of-the-notes).

## Backups

A backup is the whole notebook in one `.zip`, the same file the desktop's
Settings → Backup writes and reads, so a backup made on either device
restores on the other. It is how a notebook moves; a share code is how a
note does.

### What a backup holds

`notes_data.json`, `settings.json`, `templates.json`, `trash.json`, and
every attached file under `attachments/`. An export is built in a scratch
folder first and checked by the import's own rules before it is handed
to the file picker: a backup this app wrote must be one it agrees to read
back.

### The guards on the way in

A backup arrives from anywhere, so reading one is guarded as the
desktop's `_safe_extract_zip` guards it, with the desktop's numbers —
these are what a hostile archive could do, not a size cap:

| Guard | Value |
|---|---|
| `MAX_BACKUP_FILES` | 100,000 entries |
| Decompression bomb | any entry, or the backup as a whole, expanding to over 50 MB **and** to more than 100 times its packed size |
| Disk margin | 200 MB must stay free after unpacking — the attached files counted twice, since on a phone the unpacking and the notebook share one disk |

And one step stricter than the desktop: **only the names a backup is made
of are unpacked at all** — the four data files by their exact names, and
attached files only as `attachments/<bare name>`. `../x`, an absolute
path and a nested folder are refused before a byte is written. The sizes
the guards read are the archive's own claim, so an entry that unpacks to
more than it declared is stopped at what it declared.

### The questions, and what joins without asking

A backup's notes go through the same planner a share code does, and the
desktop's three questions are asked before anything is written:

1. Notes this device already has, by `uid`, that arrive **changed**:
   update them, or add them as new notes. The question says when the
   arriving copy is *older* than the one here.
2. Notes that arrive **exactly as they are** here: skip them, or add
   copies.
3. New notes that **look identical** to one here (the desktop's
   `_dup_key`: title, text, comments, category, checklist and repeat
   rule): skip them, or import them anyway.

Then the rest joins without replacing anything: the backup's categories
(a name already here keeps its own colour), its painted calendar days, a
template only when none here has its name, and its Trash — an entry
already in this Trash is skipped, so re-importing your own backup changes
nothing. A file another note here already uses is given a copy of its
own, so deleting one note can never take another's file with it. The
writes go in the desktop's order: settings, templates, the Trash, and
the notes last.

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

**The Trash empties itself when the app starts**, of every note older
than `trash_retention_days` (1 to 365, default 30; anything that is not
a whole number is the default). A note whose `deleted_at` cannot be read
is never removed: better kept too long than gone too soon. Until
2026-10-15 the phone never purged, because it read a folder it shared
with the desktop and the desktop owned the purge; with a notebook of its
own, the reason went and so did the rule.

When a note leaves the Trash for good, its attached files go with it —
**except any file a note still uses**, living or still in the Trash, and
any stored name that tries to point outside the attachments folder. The
desktop's automatic purge does not spare them yet
([the finding](ISSUES_LOG.md#the-desktops-automatic-trash-purge-can-delete-a-file-a-living-note-uses)).

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
