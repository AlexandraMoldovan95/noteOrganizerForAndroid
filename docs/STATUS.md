# Where this stands

What works today, what is not built yet, what was verified and how, and
the questions still open. Kept honest on purpose: the "not built" list is
as much a part of this document as the "done" one, and a claim that has
drifted out of date is a bug in the documentation.

**In short:** the app is a notes app of its own now, not a viewer for the
desktop's file. It keeps its notebook in its own private storage, writes,
edits and organises notes the way the desktop does — formatting, lists,
tables, checklists, comments, categories, colours, dates, pins, the
Trash — and since Phase 2 has the desktop's calendar, repeating notes,
templates, find, calculator, export and statistics, on a phone or beside
the list on a wide screen. It wears the desktop's eight themes or follows
the phone's light and dark, and moves notes to and from the desktop by
share code or by the desktop's own backup `.zip`. Nothing is ever
overwritten without asking. It does not sync; custom order, moving one
day, adding attachments and undo are Phase 3.

## Contents

- [What it is for](#what-it-is-for)
- [What works today](#what-works-today)
- [Where it differs from the desktop, on purpose](#where-it-differs-from-the-desktop-on-purpose)
- [Not built yet](#not-built-yet)
- [Open questions](#open-questions)
- [How each part was verified](#how-each-part-was-verified)

## What it is for

**Decided 2026-10-15: a companion app, as complete as a phone allows.**
The desktop's features are brought over in three phases, each one kept
simple enough for a phone's screen rather than copied button for button:

1. **Phase 1 — ready for a store listing**: the notebook, the editor,
   organising, the Trash, settings and themes, backup and share codes.
2. **Phase 2** (this branch, built on Phase 1's) — the calendar with its
   day list, repeating notes, the full format bar, templates, find in a
   note, the calculator, export, statistics, and a layout for wide
   screens. [What the two cover](#what-works-today).
3. **Phase 3** — custom order, moving one day of a series, adding
   attachments, the duplicates finder, undo.

The notebook on the phone is the phone's own. **There is no sync**, by
the same choice the desktop made: no account, no server, no background
exchange. Notes travel when the person sends them — a share code for one
note, a backup `.zip` for the whole notebook — and a note that arrives
where a copy of it already lives is never written over on its own: the
person chooses **"Update this note"** or **"Add as a new note"**, in the
desktop's own words.

The interface is English, as the desktop's is.

This replaces the decision of **2026-09-12** — *read, search, and capture
quickly; not a full editor* — which was right for an exploration and
stopped being right once the question became what a phone version of the
app should be. That earlier phase is what the [changelog](../CHANGELOG.md)
records up to `0.5.0`.

## What works today

Grouped by what the app does; when each item arrived is in the
[changelog](../CHANGELOG.md). Every write carries the guards in
[Writing the file](DATA_FORMAT.md#writing-the-file).

### The notebook on the phone

1. **Kept in the app's own storage**, where no other app can read it. A
   notebook left in the older shared folder by an earlier build is
   copied in once, on the first start that finds none — copied, never
   moved, so nothing is deleted on the way.
2. **A brand-new notebook opens on a welcome note**, once, as the
   desktop's does; one that was copied in or emptied by hand never gets
   one. Settings → About brings it back.
3. **A file that will not parse is kept aside, not refused.** The
   unreadable bytes are copied beside it as
   `notes_data_corrupt_<stamp>.json` and the app opens on an empty list
   and says where the copy is — the desktop's answer, ported. The same
   for `trash.json` and `settings.json`.
4. A real 77-note `notes_data.json` reads without loss: Romanian
   diacritics, emoji, CJK, a 107,000-character note.

### The list

5. The desktop's card: colour stripe, headline, preview, and the tags line
   with its category, date (`17.10.2026`, as the desktop shows it),
   comments, attachments, tables, checklist and repeat badges. A note
   with no title is headed by its first spoken line, set lighter so a
   named note still reads as named
   ([why](ISSUES_LOG.md#a-list-of-titles-would-have-been-a-list-of-blanks)).
6. A greeting with the person's name, as the desktop's header has.
7. Search by the desktop's own rule — title, body and comments, a
   table's plumbing stripped — with diacritics folded
   ([the one difference](#where-it-differs-from-the-desktop-on-purpose)).
8. **The desktop's whole Sort and Show**, remembered under its own setting
   keys: updated, title, category, Calendar: soonest and oldest, and
   Custom order — the arrangement made on the desktop; and Show by
   category, 🔁 Repeating, 📅 Dated or 📎 With files. Pinned notes float to
   the top.
9. **Grouped by category**, from the list's menu: a header per category in
   Settings' order, each folding with a tap, the folds remembered.
10. **Long press selects.** With notes selected: pin, move to a category,
    duplicate, share as a code, move to the Trash. A delete offers
    **Undo** for eight seconds.

### The calendar

11. **The month**, Monday first: a dot for each colour of note on a day,
    the day's own paint and its markers, today, the open day ringed;
    arrows or a sideways swipe for the month.
12. **The open day's notes** under it, with the desktop's marks — a
    series, a day edited on its own, a day moved here — and "+ Add note"
    for a note that starts that day. A day held down adds a note, paints
    the day or marks it (three markers at most), with an Undo for what is
    cleared.
13. **The History**, in the month's place as the desktop's toggle swaps
    them: search, the year filters, dated notes, and each series with its
    days edited on their own.

### Repeating notes

14. **The Repeat sheet**, in a note's details: how often, every how many,
    which weekdays or days of the month, what a short month does, when it
    ends — chosen, never typed — with the rule in the desktop's words and
    the next three days it gives. The desktop's recurrence engine, ported
    whole and checked against it.
15. **One day of a series on its own**, the desktop's day mode: opened from
    the calendar, saved to that day alone, "Whole series" and "Reset this
    day" a tap away; a day deleted from its series comes back with Undo.

### Writing and editing

16. **One screen for reading and writing.** A note opens ready to edit
    and saves itself 0.8 seconds after the last change, when the app
    goes to the background, and on Back — "Auto-saved" says so, and a
    save that fails says why. Turning the phone, its dark mode coming on
    or its letters growing leave the note open where it was.
17. Formatting over a selection — bold, italic, underline, strike, text
    colour and highlight from the desktop's palettes, **size and face**
    from its lists — drawn as it is typed, and moved to follow the text
    through every edit
    ([the trap](DATA_FORMAT.md#editing-plain-text-moves-formatting-onto-the-wrong-words)).
    The desktop's Windows faces are drawn as the kind they are: serif,
    fixed-width or plain.
18. **Lists, indent and alignment** on the cursor's line or every line
    selected, with the desktop's keystrokes: Enter continues a list and
    leaves it from an empty item, Backspace climbs a level, and every
    numbered run counts 1, 2, 3 again.
19. **Tables** drawn as grids — merged cells, checkbox cells, `<br>`
    breaks, each cell's own alignment and highlight, sideways scrolling.
    A new one inserted from a size grid, its first cell open to type. A
    cell's menu: its text, alignment and highlight; merge and split; rows
    and columns added or removed; a checkbox; the table's place on the
    page; and delete the table, which asks.
20. Checklists: add, tick, rename, remove, and **drag to reorder** by the
    ⠿ handle.
21. Comments, the desktop's separate box under the note.
22. Attached files listed and opened in whatever app on the phone opens
    that kind of file, through Android's own file sharing — never by
    handing out a path.
23. Note details: category, date (Android's own calendar), colour, pin,
    repeat, and the word count with the desktop's reading time.
24. **"↩ Restore previous".** When a note carries the desktop's `_prev`
    rescue copy — left by a big deletion or by an update that arrived
    from elsewhere — the note says so and brings it back, its text and
    everything else the update changed.
25. **Find in note**: the count, back and next, case, prose and table
    cells together, every match painted and the current one scrolled into
    sight.
26. **The calculator**, docked under the note: the desktop's keypad and
    More functions, a history, and Insert into note — its answer for
    every sum.
27. **Export as file**, `.html` or `.txt`, through Android's "save as":
    the desktop's page byte for byte.
28. **Templates**: start a note from one, save a note as one, and edit
    them in Settings → Templates — the desktop's `templates.json`, so a
    template made on either device starts notes on both.

### Organising

29. Categories: add, rename, recolour, delete. A rename reaches every
    note, every repeating day filed under it, every rescue copy, the list's
    Show and its folded groups. A delete asks what happens to its notes —
    move them to "General" or send them to the Trash — and "General"
    itself can be neither renamed nor deleted.

### The Trash

30. Delete to the Trash and restore from it, one note or several. A
    restored note gets a fresh id and, if its category is gone, files
    under "General".
31. **The Trash empties itself** of notes older than its window (7 to 90
    days, the desktop's setting) when the app starts, and can be emptied
    or cleared note by note by hand. Shortening the window says first how
    many notes it would remove. Attached files go with their note, except
    a file another note still uses
    ([why that matters](#where-it-differs-from-the-desktop-on-purpose)).

### Moving notes between the phone and the desktop

32. **Backup `.zip`, the desktop's own format.** Export saves one file
    through Android's file picker — notes, settings, attachments,
    templates and the Trash. Import reads one, with the desktop's guards
    (path tricks, decompression bombs, too many files, too little space),
    and adds its notes: nothing is removed, and a note already here is
    only replaced when the person chooses to update it.
33. **Share codes, both ways**, for one note or several. A code arrives by
    pasting it or by sharing text to the app from any other app — over an
    open note too, which then shows what an update changed.
34. **A note keeps one identity wherever it goes** (`uid`). A code or a
    backup holding a note this phone already has is recognised as *that*
    note, and the phone asks the desktop's questions in the desktop's
    words: update the copy here or add a new one; skip copies that hold
    nothing new; skip look-alikes. Nothing is written until they are
    answered, and an update leaves the `_prev` that "↩ Restore previous"
    undoes.

### Looks and settings

35. The desktop's eight themes, its palettes measured equal by a gate,
    and **"Follow the phone"**: dark when the phone is dark, light when it
    is light, in two themes the person picks. On by default, as Android
    apps are.
36. **Wide screens.** From a medium window — a phone on its side, a small
    tablet — the bar becomes a rail down the left edge; from an expanded
    one, a note opens beside the notes or the calendar, which stay in
    reach, its card outlined. What the list does to the note beside it —
    a pin, a category — the note shows at once and keeps.
37. Settings: your name, appearance, categories, templates, backup, the
    Trash and its window, **Statistics** — the desktop's snapshot of the
    notebook — and About: the version, the welcome note, and
    **Send feedback**, which opens the desktop's feedback form in the
    browser and sends nothing from the notes.
38. The desktop's icon, drawn by the desktop's own code.

### Underneath

39. 516 JVM unit tests in 23 suites, mutation-tested; five ports held to
    fixtures the desktop's own Python wrote; four gates against drift
    (the note's shape and the palettes against the desktop's Python, the
    rules layer free of Android, the repository in English); and an
    emulator walk of 127 checks that reads the files the app
    wrote.

## Where it differs from the desktop, on purpose

Every rule here matches the Python deliberately, including where the
Python is arguably worse, because a difference is a surprise, not a
feature. These are the exceptions, each with its reason.

**Search folds diacritics.** `si` finds `și`, `masina` finds `mașină`, and
the cedilla spellings older Windows keyboards produce (`ş`, `ţ`) match
the comma-below ones. The desktop compares the letters as typed. This is
a one-way widening — the folded search finds everything the strict one
finds and more — and the reason is the phone: diacritics get dropped
constantly when typing on one, and a notebook written half with them is
unsearchable under a strict rule. **Worth porting back to the desktop**
([open finding](ISSUES_LOG.md#still-open)).

**The theme can follow the phone.** Android apps are expected to; a
desktop app is not. The choice lives under keys of its own
(`phone_theme_follows_phone`, `phone_theme_light`, `phone_theme_dark`), so
a settings file carried to the desktop in a backup leaves the desktop's
own `theme` exactly as it was.

**The Trash's automatic emptying spares files still in use.** When a
note leaves the Trash for good, its attached files are deleted — except
any file a living note, or another note still in the Trash, also uses.
The desktop's manual "Delete for good" already spares them; its
*automatic* emptying at startup does not, so on a notebook where two
notes share one stored file, the startup purge can delete a file a
living note still shows. That is a [desktop finding](ISSUES_LOG.md#still-open),
not a reason for the phone to copy it.

**The welcome note is the phone's own.** Same title, same idea — a short
tour that is itself a note — but written for a phone: tap, long press,
the share sheet, Settings at the bottom.

Phase 2 added five more, each where the desktop's answer is a side
effect or a fault rather than a rule — all five are
[desktop findings](ISSUES_LOG.md#seven-desktop-findings-from-porting-phase-2):

* **Untitled notes stay at the bottom of "Title Z–A"** as of "Title A–Z".
  The desktop reverses A–Z's whole order, so its untitled notes rise to
  the top of Z–A, against the comment that says they sink.
* **Find steps through a table row by row**, as a table is read; the
  desktop goes column by column, because of how it sorts its matches.
* **A sum nested past about 190 brackets shows Error.** The desktop's
  parser raises `RecursionError` there instead of failing as it fails
  everywhere else.
* **The table size grid draws every column**, so the squares lit are the
  table's shape. The desktop's starts at its second column and lights a
  single one for a two-column table.
* **The export writes a note with a `^` merge under a shorter row.** The
  desktop's renderer raises `IndexError` on it.

## Not built yet

Phase 3, and what is left beyond it, as a list, so nothing is pretended:

* **Arranging notes by hand.** Custom order shows the desktop's
  arrangement; the phone cannot change it yet.
* **Moving one day of a series** to another date. A day moved on the
  desktop is shown where it went, marked, and can be sent back.
* **Adding attachments.** They can be opened, not added.
* The duplicates finder, pasting with formatting, table column widths
  and captions, and **undo and redo** — which is why deleting a table
  asks first.
* Styling inside a table's cells is not drawn, and a vertical merge is
  drawn as an empty cell rather than a joined one.
* A code can be typed, pasted or shared in, but not scanned.

Built since the list above was first written, in Phase 2: the calendar,
repeating notes and one day of a series, the whole format bar,
templates, the remaining sorts and filters with grouping, find, the
calculator, export, statistics, and wide screens — where the + button no
longer covers the last card.

## Open questions

### Android's own backup of the app's data

`allowBackup` is on, so Android copies the app's data — the notes — to
the person's Google account when they have device backup turned on, and
puts it back on a new phone. That is convenient and it is also the notes
leaving the phone. It is a choice for the owner, not a default to inherit
from the project template.

### The welcome note's words

A first draft, written for a phone. It is the first thing a new user
reads, which makes it a judgement a script cannot make — it is marked
NEEDS YOUR EYE in the test plan.

### Sync

Still not built, and still not wanted for now: share codes and backups
move notes when the person chooses, and an arriving copy never wins
without asking. What made even a manual exchange possible was identity,
not the network — `id` is a note's place in one file, so two devices
both invent 88, while a `uid` names the note itself
([what an arriving note may change](DATA_FORMAT.md#what-a-shared-note-is-allowed-to-change)).

## How each part was verified

A suite proves the code does what it says. Where it could, the work here
was judged by something else: the desktop's own Python, the written file,
or the phone itself.

| Part | How it was verified |
|---|---|
| [The whole app, on a phone](#an-emulator-walk-that-reads-the-files) | 127 checks walked on the built app, upright and on its side, reading the file the app wrote and the screen it drew |
| [The Phase 2 ports](#the-phase-2-ports-answer-as-the-desktop-answers) | Five fixtures written by the desktop's own Python — repeats, sums, exported pages, templates, renumbered lists — every answer matched |
| [Backups](#a-backup-made-on-the-phone-read-by-the-desktops-own-functions) | A backup made through the phone's file picker, read by the desktop's own import functions |
| [Search](#search-agrees-with-the-desktop) | Four queries typed on the phone and counted, against the desktop's `_query_matches_note` over the same file |
| [Writing a note](#the-write-was-judged-by-the-desktop-not-by-itself) | Captured on the emulator, pulled back, and checked in the desktop's Python |
| [Identity and share codes](#the-identity-loop-walked-on-the-emulator-in-both-directions) | Walked both ways on the built app, with the desktop's Python at the other end |
| [The icon](#the-icon-on-the-phone-not-in-a-preview) | Installed and looked at on the emulator, not judged in a preview |
| [The rules](#the-unit-tests-and-their-mutants) | 516 JVM unit tests, each mutant watched failing |

### An emulator walk that reads the files

`py tools/emulator/walk.py <folder>` drives the built app the way a
person would — every tap aimed by the words on the screen, found fresh —
and after each step reads the notebook the app wrote, through `run-as`.
17 parts, 127 checks. On the last full run 125 passed and 2 failed —
the walk's own fault, not the app's: text typed at the start of a
field that capitalises came back with a capital
([the story](ISSUES_LOG.md#taps-aimed-through-the-keyboard)). Both
checks were fixed and their parts run again, passing; a full run from
start to end has not been repeated since. Phase 1's four:

* **A brand-new notebook**: the welcome note, written and remembered; a
  note typed, saved by Back, and born with the desktop's whole shape;
  pinning from a selection; Move to Trash and its Undo; "Follow the
  phone" turned off and a theme picked, under the desktop's own key; the
  name, and the greeting that uses it.
* **A notebook copied in**, written for the camera: copied in without a
  welcome note; a table cell edited, the table still a table; a checklist
  row ticked with the desktop's styling still in place; a category chosen
  in the details; "↩ Restore previous" bringing the text back and
  spending the copy; a category renamed in every note and in the list; a
  share code made by the **desktop's own encoder** read and added; a note
  restored from the Trash page; a backup exported through the picker and
  remembered; that same backup imported again — the question seen, and
  skipping it changing nothing.
* **The doors to the rest of the phone**: a code shared in from another
  app, a note shared out through Android's share sheet, and Send
  feedback opening a browser rather than the app.
* **The phone changing under an open note**: its dark mode coming on,
  larger letters, the phone turned on its side — and the note still open
  after each ([why this part exists](ISSUES_LOG.md#turning-the-phone-closed-the-note)).

Phase 2 added a part for each feature, each over a notebook written for
it and read back from the file:

* **The calendar**: a day of a series edited on its own and written to
  that day alone; deleted from the series and brought back by Undo with
  its own text; a day painted under the desktop's key; the History; a
  repeat chosen on the sheet.
* **The list**: Show 📅 Dated, grouping, a folded group — the choices kept
  under the desktop's own keys.
* **Statistics** over a notebook whose figures are known.
* **The calculator**: keys, two inserts, the panel standing aside for the
  keyboard, Error, the history, Back — and each sum on its own line in
  the file.
* **Find in note**: prose and cells counted together, stepping, case, a
  miss, the bar opening again with the last search, the file unchanged.
* **Export**: both formats through Android's "save as", each file read
  back off the phone and compared byte for byte with the desktop's
  export of the same note.
* **Styles through an edit**: a letter typed into a desktop note, its
  size still the number 24 and its bullet still on its line.
* **Templates**: a note saved as one, a taken name asked about, a note
  made from one, a template edited in Settings, a delete that asks.
* **Size and face**, **lists**, **a new table** and **the cell menu**, each
  read back in the shape the desktop writes.
* **A wide window**: a selection and an open find bar surviving the
  phone turning; the rail; a note beside the list; a pin from the list
  kept by the editor's next save; another note opened from its title;
  the note beside the calendar, and closed for Settings; two new notes in
  a row; a code shared in over an open note, upright, and the update
  kept; and a medium window, the note in the list's place.

The photographs it takes are looked at, not only saved: the walk is
where the Settings switch that did not redraw, Back closing the app from
Settings, an Undo that vanished before a thumb could reach it, a merged
cell drawn short, and an editor that kept the last note's scroll were
found ([the stories](ISSUES_LOG.md)).

### The Phase 2 ports answer as the desktop answers

Each rule ported in Phase 2 that can be run on its own was run, in the
desktop's own Python, over cases generated for it — and the phone had to
give every answer back exactly. The tools that write the fixtures read
the desktop's source and never write to it.

| Port | The desktop's own function | Cases |
|---|---|---|
| Repeating notes | its recurrence engine, expanding and reading dates | 300 seeded notes expanded, 400 typed dates read |
| The calculator | its evaluator and its display rounding | 661 sums, 378 shown values |
| Export | `note_to_html`, `html_document` and the page around them | 400 notes, 794 files compared byte for byte |
| Templates | `normalize_template` | 302 templates as a hand-edited or old file might hold them |
| Lists | its renumbering | 300 bodies |

The four sorts new to the phone were checked against the desktop's
`_sort_notes` on the same notes, and the round trip that found
[a desktop note's lost sizes](ISSUES_LOG.md#a-desktop-note-lost-its-sizes-on-the-way-through-the-phone)
sends all 400 export notes through a save on the phone.

### A backup made on the phone, read by the desktop's own functions

A `.zip` exported through the phone's real file picker was pulled off the
emulator and handed to the desktop's own `backup_entries_problem` and
`_safe_extract_zip`, and every note in it to `normalize_note`: accepted,
extracted, and every note passing unchanged. The same backup restored
onto a fresh phone notebook came back whole.

### Search agrees with the desktop

Verified against the specification rather than against itself — the
desktop's `_query_matches_note` was run over the same file in Python and
the phone was made to type each query and report its count. Four queries,
four exact agreements (8, 1, 6, 0 of 77).

### The write was judged by the desktop, not by itself

After capturing a note on the emulator over a copy of a real 77-note
file, the result was pulled back and checked in Python: all 77 existing
notes identical field for field, the new note passing `normalize_note`
**unchanged**, every id unique.

Reading the written file, not the screen, is also how
[a new note saved without its formatting](ISSUES_LOG.md#a-new-note-saved-without-its-formatting)
was found: the toolbar worked, the screen drew bold, and the file held
none of it.

### The identity loop, walked on the emulator in both directions

Walked on the built app (then in Romanian) over a copy of the real
77-note notebook, normalised through the desktop's own `normalize_note`
so every note had a `uid`:

* A note edited **on the desktop** became a 634-character code, was typed
  into the phone, and was recognised. Answering "Update" changed note 12
  in place: same `id`, same `uid`, new title, `_prev` written with the
  old one, `created` untouched, the other 76 notes identical byte for
  byte, and the result passing `normalize_note` unchanged.
* The same note was then edited **on the phone**, shared from there, and
  its code decoded by the desktop's own Python: same `uid`, no `id`
  carried, `split_by_identity` answering "1 known, 0 new", and the
  desktop's `apply_shared_update` updating its copy in place with the
  three formatting spans and the eight checklist rows intact.
* Answering "Add as a new note" instead added a 78th note with `id` 87
  and a `uid` of its own, leaving all 77 earlier notes untouched.

### The icon, on the phone, not in a preview

The first attempt drew the mark at the full safe-zone size and the phone
cropped its frame away at the corners, because a circle mask keeps only
the circle inscribed in that square
([the story](ISSUES_LOG.md#the-preview-that-flattered-the-icon)).

### The unit tests, and their mutants

516 JVM unit tests in 23 suites pin the rules ported from Python, and
each mutant was watched failing before being reverted. Phase 1's rounds —
the themes, the settings, the import and backup, organising, the
editor's segments, startup and the card's date — ran about seventy
mutants; every survivor was either a weak check, strengthened until it
died, or an equivalent mutant, removed with the redundant code it
exposed.

Phase 2 ran 190 against the unit tests: 187 killed, and three equivalent
— two in the recurrence engine (a check the next line repeats, as in the
Python; a `break` where the sorted weekdays make `continue` the same) and
a blank table padded as centred, whose cells are all padding. Three more
were built into the app itself and walked on the emulator, where the
rule lives in the drawing: each was caught.

Two commit messages carry counts that were wrong when written and are
corrected here, the history left as it is: the recurrence engine's
(`e29e9a1`) says 26 tried, 23 killed, 3 equivalent — it was 24, 22 and
2; the calendar days' (`314e89a`) says `DaysTest` has 17 cases — it has
16.

Two lessons from running them, each with its full story in the issues
log:

* **A silent mutant is not a passing one.** In every case Gradle printed
  no test results at all. Check that the mutant reached the tests before
  drawing any conclusion about them
  ([the story](ISSUES_LOG.md#a-silent-mutant-is-not-a-passing-one)).
* **A mutant runner must put the source back even when it crashes** — or
  better, work on a copy, as the one used now does
  ([the story](ISSUES_LOG.md#a-mutant-runner-that-left-the-code-broken)).
