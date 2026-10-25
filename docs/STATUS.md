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
the list on a wide screen; since Phase 3, custom order arranged by hand,
one day of a series moved, files attached, duplicates found, a table's
caption and column widths, Undo and Redo, and copy and paste that keep
the formatting. It wears the desktop's eight themes under one Light |
Dark switch, and moves notes to and from the desktop by share
code or by the desktop's own backup `.zip`; a note is shared as a PDF, a
web page, its words or a share code through the phone's own share sheet.
Nothing is ever overwritten without asking. It does not sync.

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
2. **Phase 2** — the calendar with its day list, repeating notes, the
   full format bar, templates, find in a note, the calculator, export,
   statistics, and a layout for wide screens.
3. **Phase 3** (built on Phase 2's) — custom order arranged
   by hand, moving one day of a series and settling days left off its
   schedule, adding attachments, the duplicates finder, a table's caption
   and column widths, undo and redo, and pasting with the formatting.
   [What the three cover](#what-works-today).

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
   A table is never drawn on the card: every line a person reads as one
   is left out, by the desktop's `preview_text`, and "▦ 1 table" says it
   is there ([why](ISSUES_LOG.md#a-table-read-by-a-stricter-rule-than-a-persons)).
   A card is given a glimpse of its note, not the whole of it, so the
   list scrolls smoothly however long the notes
   ([measured](ISSUES_LOG.md#two-lines-of-text-that-cost-a-whole-note)).
6. A greeting with the person's name, as the desktop's header has.
7. Search by the desktop's own rule — title, body and comments, a
   table's plumbing stripped — with diacritics folded
   ([the one difference](#where-it-differs-from-the-desktop-on-purpose)).
   A new search shows its results from the top, as on the desktop.
8. **The desktop's whole Sort and Show**, remembered under its own setting
   keys: updated, title, category, Calendar: soonest and oldest, and
   Custom order; and Show by category, 🔁 Repeating, 📅 Dated or 📎 With
   files. Pinned notes float to the top. **Custom order is arranged by
   hand**: a ⠿ handle on each card, dragged — the list scrolling by itself
   at its edges — within the card's own block (pinned or not, its
   category when grouped), written as the desktop numbers it.
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
    series, a day edited on its own, a day moved here, its tables (`▦×1`)
    — and "+ Add note"
    for a note that starts that day. A day held down adds a note, paints
    the day or marks it (three markers at most), with an Undo for what is
    cleared.
13. **The History**, in the month's place as the desktop's toggle swaps
    them: search, the year filters, dated notes, and each series with its
    days edited on their own. **The day's reminder** (2026-10-23): the
    desktop's bar above the notes once a day when the day has notes, a tap
    opening it here; and, turned on in Settings → Reminders, a
    notification at an hour chosen, with the app closed, saying how many
    and never their words.

### Repeating notes

14. **The Repeat sheet**, in a note's details: how often, every how many,
    which weekdays or days of the month, what a short month does, when it
    ends — chosen, never typed — with the rule in the desktop's words and
    the next three days it gives. The desktop's recurrence engine, ported
    whole and checked against it.
15. **One day of a series on its own**, the desktop's day mode: opened from
    the calendar, saved to that day alone, "Whole series" and "Reset this
    day" a tap away; a day deleted from its series comes back with Undo.
    **"📅 Move this day…"** holds it on another date — a weekly meeting
    moved one week — with its Undo; and **days whose own edits fell off
    the schedule** when the repeat changed are shown on the series and
    settled one question at a time: a note of their own, the Trash, or
    left as they are.

### Writing and editing

16. **One screen for reading and writing.** A note opens ready to edit
    and saves itself 0.8 seconds after the last change, when the app
    goes to the background, and on Back — "Auto-saved" says so, and a
    save that fails says why. Turning the phone, its dark mode coming on
    or its letters growing leave the note open where it was. **↶ Undo and
    ↷ Redo** (Ctrl+Z, Ctrl+Y on a keyboard) take back the note's words and
    their styling, a burst of typing at a time, as the desktop's do.
17. Formatting over a selection — bold, italic, underline, strike, text
    colour and highlight from the desktop's palettes, **size and face**
    from its lists — drawn as it is typed, and moved to follow the text
    through every edit
    ([the trap](DATA_FORMAT.md#editing-plain-text-moves-formatting-onto-the-wrong-words)).
    The desktop's Windows faces are drawn as the kind they are: serif,
    fixed-width or plain. The size box says the size of what is selected,
    or "Default" for a mix, as the desktop's does. With nothing selected,
    **B, I, U and S style what is typed next**, until pressed again, as
    the desktop's pending style; letters glued after a styled word wear
    its styles up to a space, and letters typed before one stay plain, as
    on the desktop; a colour tapped again stays, and ✕ clears it.
    **Copy and paste keep it**: a copy carries HTML
    beside the words, so they arrive formatted elsewhere; a paste from a
    browser, a word processor or a spreadsheet brings what the desktop's
    brings — styles, real colours, lists, alignment, tables — and the
    app's own copy brings back its sizes and faces too. "Paste as plain
    text" (Ctrl+Shift+V) brings the words alone.
18. **Lists, indent and alignment** on the cursor's line or every line
    selected, with the desktop's keystrokes: Enter continues a list and
    leaves it from an empty item, Backspace climbs a level, and every
    numbered run counts 1, 2, 3 again.
19. **Tables** drawn as grids — merged cells, checkbox cells, `<br>`
    breaks, each cell's own alignment and highlight, sideways scrolling.
    A new one inserted from a size grid, its first cell open to type. A
    cell is written in place, in the table, and a bar over the keyboard,
    shaped on Samsung Notes', changes the rest: rows and columns added
    or removed, merge and split, highlight, alignment, a checkbox. A
    merge keeps both cells' words and their styles, as the desktop's
    does, and a merge down is drawn as one cell; every edit carries each
    cell's styles with it
    ([why](ISSUES_LOG.md#ports-read-side-by-side-with-the-desktop-they-came-from)).
    The chosen cell's right and bottom edges are dragged for its column's
    width and its row's height. Its
    More: the table's place on the page; a column wider, narrower,
    fitted to its words — one column or all of them — hidden and shown
    again; a numbered caption under the table and a line above it; and
    delete the table, which asks. Deleting a row or a column does what
    the desktop does at the edges — the header row deleted, the next is
    the header; a column of two emptied, not removed
    ([why](ISSUES_LOG.md#two-deletes-the-desktop-had-already-settled)). A
    column is at least eight characters wide, as on the desktop, and its
    width is measured from its letters, so a cell holds its words at any
    of the phone's text sizes
    ([why](ISSUES_LOG.md#widths-in-one-unit-letters-in-another)).
20. Checklists: add, tick, rename, remove, and **drag to reorder** by the
    ⠿ handle. A checklist, comments and files are added from the note's
    ⋮ (since 2026-10-23), no longer from links under every note.
21. Comments, the desktop's separate box under the note.
22. Attached files listed and opened in whatever app on the phone opens
    that kind of file, through Android's own file sharing — never by
    handing out a path. **Files attached** from the phone's own picker,
    stored as the desktop stores them, and taken off again after a
    question — the stored file kept while another note still uses it.
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
    the desktop's page byte for byte, the two kinds shown as cards saying
    what each gives. **Share**, the mark at the top of a note: the note
    as a PDF, a web page, its words or a share code, chosen on one sheet
    and handed to the phone's own share sheet — WhatsApp, a mail app,
    Teams, Drive, and the people those apps put first. The PDF is the
    export's page printed on the phone, A4 with paper margins, each page
    broken in a gap between lines — its pages are pictures, so its words
    cannot be selected. The words are the title, the body, the
    checklist's boxes as ☐ and ☑ and the comments; a note with a table is
    not offered as words, which cannot draw one, and the sheet says to
    send it as a PDF. The app sends nothing itself, reads no contacts and
    remembers nothing; the app picked does the sending. The note's own
    files are not sent, as an export does not carry them, and the sheet
    says so. It replaced "Send by email" and the 🔗 beside it: one door
    out instead of two
    ([why a PDF](ISSUES_LOG.md#a-note-mailed-as-its-code-and-a-pdf-of-one-page-four-times),
    [a long note's PDF](ISSUES_LOG.md#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been)).
28. **Templates**: start a note from one, save a note as one, and edit
    them in Settings → Templates — the desktop's `templates.json`, so a
    template made on either device starts notes on both. A template's
    body shows its tables drawn, in place, as a note does.

### Organising

29. Categories: add, rename, recolour, delete. A rename reaches every
    note, every repeating day filed under it, every rescue copy, the list's
    Show and its folded groups. A delete asks what happens to its notes —
    move them to "General" or send them to the Trash — and "General"
    itself can be neither renamed nor deleted. **Settings → Duplicates**
    finds notes the desktop's own key calls the same, ticks all but the
    first of each group, and sends the ticked ones to the Trash after a
    question, with Undo — a file one of them carried handed, as a copy,
    to the note kept.

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
    An update from the store changes neither: the window is the person's,
    and a note waits its full window whatever is released meanwhile. When
    the window cannot be read, nothing is emptied until it is chosen
    again ([why](ISSUES_LOG.md#a-trash-emptied-by-a-window-nobody-chose)).

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
    under a **Light | Dark** switch that is the app's mode: moved, the app
    wears that mode's last theme at once, and the page shows that mode's
    four. "Follow the phone" is gone (the owner's ask, 2026-10-20); a
    notebook that followed keeps the theme it was wearing.
36. **Wide screens.** From a medium window — a phone on its side, a small
    tablet — the bar becomes a rail down the left edge; from an expanded
    one, a note opens beside the notes or the calendar, which stay in
    reach, its card outlined. What the list does to the note beside it —
    a pin, a category — the note shows at once and keeps. « in the note's
    top bar puts the list (or the calendar) aside for a wider note, »
    brings it back; the choice holds until the app is closed. Walked on a
    small phone and on tablets of 7, 10 and 14 inches (2026-10-24).
37. Settings: your name, appearance, categories, templates,
    **Reminders**, backup, the
    Trash and its window, **Statistics** — the desktop's snapshot of the
    notebook — and About: the version, the welcome note, the **privacy
    policy**, and — once the app is on Google Play — **Feedback**, a
    review there.
38. The desktop's icon, drawn by the desktop's own code.

### Underneath

39. 706 JVM unit tests in 31 suites, mutation-tested; six ports held to
    fixtures the desktop's own Python wrote; four gates against drift
    (the note's shape and the palettes against the desktop's Python, the
    rules layer free of Android, the repository in English) and a fifth
    on the built release APK (every file its code looks up by name, found
    where the shrunk code will look); and an emulator walk of 333
    checks that reads the files the app wrote.
40. **A release build**, the form the app is tested in from now on:
    shrunk by R8 (2.0 MB, from the debug build's 14.5), not debuggable,
    no permission beyond one Android adds for its own receivers, its one
    native library 16 KB-aligned as Play requires, and an app bundle
    (`bundleRelease`) beside the APK. Signed for now with this machine's
    debug key, so it installs over a debug build and keeps the notebook
    — Play refuses that key, which is the first item
    [before an upload](#before-the-first-upload-to-google-play).

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

**Light | Dark remembers a theme for each mode.** The desktop's quick
toggle goes between the last dark and the last light theme of a session;
the phone keeps the two under keys of its own (`phone_theme_light`,
`phone_theme_dark`), which the desktop ignores. Up to 0.9.0 the theme
could also follow the phone's dark mode; 0.9.1 replaced it with the one
switch, at the owner's ask.

**The Trash's automatic emptying spares files still in use.** When a
note leaves the Trash for good, its attached files are deleted — except
any file a living note, or another note still in the Trash, also uses.
The desktop's automatic emptying at startup did not, until its 0.95.391
([the finding](ISSUES_LOG.md#the-desktops-automatic-trash-purge-can-delete-a-file-a-living-note-uses));
the two now agree.

**The Trash's automatic emptying waits for a window it can read.** When
the settings cannot be read, or a hand-edited window is not a whole
number from 1 to 365, nothing is emptied, and the lost window is
remembered until one is chosen again. The desktop emptied by the default
30 days then, and read a hand-edited 0 as one day, until its 0.95.391
([the finding](ISSUES_LOG.md#the-desktops-startup-purge-goes-by-a-window-it-could-not-read));
the two now agree.

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

Phase 3 added three, for the same reason —
[desktop findings](ISSUES_LOG.md#two-desktop-findings-from-porting-phase-3):

* **A day comes home only to an empty date.** Undo of a move, or "Reset
  this day", is refused when another day of the series has been moved
  onto the date it would return to; the desktop brings it back on top,
  and the other day is drawn nowhere.
* **Taking a file off a note keeps the stored file while another note
  uses it**, as the phone's Trash already does.
* **A pasted page's second table keeps its styles on their words.** The
  desktop counts that table's padding in text the first table has
  already changed, and its styles land beside their words.

The audit of 2026-10-19 added one more, and the phone's own
simplifications of the table:

* **Deleting a column a merge passes through keeps the merge**, one
  column narrower; the desktop dissolves it.
* **A hidden column is not drawn at all**, where the desktop draws a
  sliver to right-click; More's "Show hidden columns" brings them all
  back, and the last column in sight is never hidden.
* **Box turns a box into an empty cell at once**; the desktop opens it
  empty and keeps the box until something is typed. Undo brings it back.
* **No selection of several cells**: no copying, pasting or clearing a
  block of cells, and a merge joins a cell with one neighbour at a time.

## Not built yet

What is left after Phase 3, as a list, so nothing is pretended:

* Styling inside a table's cells is not drawn (it is kept, and carried
  with the cells through every edit).
* **Tab-separated text pasted as a table.** The desktop turns a
  spreadsheet's plain-text copy into a table; on the phone a spreadsheet
  is pasted from the HTML it also offers, and plain tab-separated text
  goes in as text.
* A highlight from the format bar is written over line ends, where the
  desktop writes one span per line
  ([open](ISSUES_LOG.md#on-the-phone-a-highlight-from-the-format-bar-covers-a-lines-end)).
* A code can be typed, pasted or shared in, but not scanned.

Built since the list was first written — in Phase 2: the calendar,
repeating notes and one day of a series, the whole format bar,
templates, the remaining sorts and filters with grouping, find, the
calculator, export, statistics, and wide screens, where the + button no
longer covers the last card. In Phase 3: Custom order arranged by hand,
one day moved and days off the schedule settled, attachments added and
taken off, the duplicates finder, a table's caption, column widths and a
line above it, undo and redo, and copy and paste with the formatting.

## Open questions

### Android's own backup of the app's data — decided

`allowBackup` is on, so Android copies the app's data — the notes — to
the person's Google account when they have device backup turned on, and
puts it back when the app is installed again. **Decided 2026-10-20:
it stays on, and the app says so** on Settings → Backup, beside the
backup `.zip`, which is the copy a person keeps and moves. The rules
files name the choice instead of carrying the project template's
comments. Two limits worth knowing: an app's copy there is at most
25 MB, past which Android stops backing it up, attached files and all;
and the desktop cannot read it, so it is no bridge between the two —
the `.zip` through Google Drive is.

### Before the first upload to Google Play

Checked already, on the built release APK: the target SDK (37), not
debuggable, no internet permission, 16 KB alignment, an app bundle, and
no `<queries>` entry at all — the phone's share sheet lists the apps that
can take a note, so the app never looks at what is installed. Still to
do, each a decision or an account step for the owner:

* **An upload key**, made once and kept outside the repository, with
  Play App Signing; the release build's signing moves to it. A phone
  holding a debug-signed build has to uninstall it to take the store's,
  so its notebook goes to a backup `.zip` first.
* **The privacy policy: published** (2026-10-21). One page for both
  apps, on Google Sites (`Links.PRIVACY`), its text the desktop
  repository's `docs/PRIVACY.md`, copied there by hand; in the app at
  Settings → About → Privacy policy, which Play asks for as well. Its
  address goes into the Console's App content → Privacy policy. The owner
  made the Google Site on 2026-10-21, and every release checks it still
  matches the file (the desktop repository's
  RELEASE_WINDOWS, "The privacy page, at every release").
* **Feedback: a review on Google Play** (the owner's choice,
  2026-10-21). `Links.STORE` stays null until the app is published; set
  to its Play page, About shows a Feedback section whose Write a review
  opens it.
* **The Data safety answers: decided (2026-10-20) — no data collected,
  no data shared.** The Console's first question, "Does your app collect
  or share any of the required user data types?", is answered **No**,
  and the rest of the form follows from it. Why that is true:
  * The notebook stays in the app's private storage. A note, a code, a
    backup or an attached file leaves only when the person sends it,
    through Android's own share sheet, file picker or a one-file grant —
    a transfer the person starts, to a place the person picks.
  * Feedback is a review on Google Play: Google's, under its own policy.
    The app only opens its Play page, and the policy's page, sending
    nothing with either. (Up to 0.9.1, Send feedback opened the site's
    form with the app's version, its theme and the notebook's size in the
    address; 0.10.0 took it out, and the form is gone.)
  * Android's own backup (`allowBackup`, on by the owner's choice) is
    the system's, not the app's code, end to end encrypted with the
    phone's lock, kept in the person's own Google account. Google's
    Data safety help does not name it either way; reading "collect" as
    what the app's code sends, it is not collected, and the privacy
    policy says plainly that it happens. This is the one judgement in
    the answer.
  * The system's WebView, which prints a note to PDF, may send Google
    usage statistics when the person has allowed them for the phone; the
    app opts its WebView out in the manifest (`MetricsOptOut`, since
    0.10.0), so nothing goes from it either.
* **A closed test before production.** Play asks a personal developer
  account made after November 2023 to run one with at least 12 testers
  for 14 days in a row before the app can go to production — the
  Console's current wording is the one to follow.
* The store listing: the 512 px icon, a feature graphic, phone
  screenshots (`tools/emulator/take_screenshots.py` makes those), the
  content rating questionnaire and the target audience. Contact: the
  project's address, noteorganizer.app@gmail.com (required); the website
  field stays empty — the project's site is kept apart from the apps'
  users (the owner's rule, 2026-10-21), and this repository must never
  name it.

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
| [The whole app, on a phone](#an-emulator-walk-that-reads-the-files) | 333 checks on the built app ([the last full run](#an-emulator-walk-that-reads-the-files)), upright and on its side, at larger letters and on tablet screens, reading the file the app wrote and the screen it drew |
| [The ports since Phase 2](#the-phase-2-ports-answer-as-the-desktop-answers) | Six fixtures written by the desktop's own Python — repeats, sums, exported pages, templates, renumbered lists, pasted HTML — every answer matched, the 20 pasted fragments where the phone parts from the desktop on purpose excepted and kept beside |
| [Backups](#a-backup-made-on-the-phone-read-by-the-desktops-own-functions) | A backup made through the phone's file picker, read by the desktop's own import functions |
| [Search](#search-agrees-with-the-desktop) | Four queries typed on the phone and counted, against the desktop's `_query_matches_note` over the same file |
| [Writing a note](#the-write-was-judged-by-the-desktop-not-by-itself) | Captured on the emulator, pulled back, and checked in the desktop's Python |
| [Identity and share codes](#the-identity-loop-walked-on-the-emulator-in-both-directions) | Walked both ways on the built app, with the desktop's Python at the other end |
| [The icon](#the-icon-on-the-phone-not-in-a-preview) | Installed and looked at on the emulator, not judged in a preview |
| [The rules](#the-unit-tests-and-their-mutants) | 706 JVM unit tests, each mutant watched failing |
| [The release build](#the-release-build-over-a-debug-one) | Read from the built APK; walked on the emulator; installed over a debug build holding a notebook, which it left byte for byte as it was |

### An emulator walk that reads the files

`py tools/emulator/walk.py <folder>` drives the built app the way a
person would — every tap aimed by the words on the screen, found fresh —
and after each step reads the notebook the app wrote, through `run-as`.
41 parts, 333 checks. The last full run (2026-10-24, evening), with a
note's whole text replaced by a new line and its words kept for Restore
previous, began on an emulator just started cold: it waited 31 seconds,
then passed all 333. The run before it that day, with the part for
screens of every size and the walk's own files taken out of Downloads
again, waited 31 seconds and passed all 332. The two before that
lost six checks to Android's picker refusing a 33rd file of one name
([the story](ISSUES_LOG.md#thirty-two-copies-and-a-save-that-did-nothing)),
and then one to the calendar's row of the day read once, a moment before
it was drawn; that check, and three more read once after the calendar
opened, now wait for what they look for. The run before them
(2026-10-23, late), with a
markdown table pasted into a note and a template added, was begun on an
emulator just started and alone on the computer: it waited 66 seconds,
then passed all 313. Earlier that evening, with an
empty last line put back to the left under a centred one added, it
waited 69 seconds, then passed all 309. That afternoon, with the centred empty last
line and Your name's Saved, all 308. That morning, with the day's reminder and the note's
⋮ added, all 305, after a wait of 72 seconds. The
two before that, on emulators just started too, lost four checks and then
two, every one a check whose margin was the machine's speed and none the
app — Undo tapped after its offer had gone, the theme's fade between two
captures, the calculator looked for before the keyboard was down, the
cursor's line looked for before the note had scrolled back to it; each
check was changed, not run again until it passed
([the story](ISSUES_LOG.md#a-check-that-held-by-one-frame)). Earlier that
day, with the checks for what the owner's phone found that evening — the
screen following the cursor in the text and the comments, the newest
checklist row, the keyboard rising on its side, the list put aside,
Settings' list and the month keeping their places — all 291; before them,
on the first of those fixes, all 278. On 2026-10-22, with the two parts for what the owner's phone and
an agent playing an ordinary user found, the checklist's box above the
keyboard and the note in sight on its side, all 273, after a wait of 106
seconds; the two before it, with fewer of those checks, all 271 and all
269. A run before them, made while the
computer also built the app and ran the desktop's suite, failed a
hundred checks, the emulator's own home screen hung under the load ("Pixel
Launcher isn't responding") — the walk wants the machine to itself. The
first run on the walk's own fixes was begun the moment an emulator had
started: the walk waited 93 seconds for it to settle, then passed all
259. The one before it, the same but for
`keyboard_down`, lost two checks in the sheet part to a Back pressed off
a table cell ([the story](ISSUES_LOG.md#a-check-that-held-by-one-frame)).
Before them, after a new search began to show its results from the top,
all 259; with deletes across a numbered list fixed, all 258. On 0.10.0
(2026-10-21) all 258 passed on an emulator given a minute and a half to
settle after it started. A run on the same build less the WebView's
opt-out lost one: a search typed after two blind flings up the list, which
stopped anywhere from Card 14 to Card 16, so the results opened with
"Card 15" above the screen — the setup now scrolls until the top card is
seen, and the part passed six times running. The run before that, with
257, after About gained the
privacy policy, was the first on an emulator started a moment before: its first
typing never reached the editor, so the first part failed in a chain —
six of its checks — and the dissolve's shades jumped from light to dark
between two frames. The first three parts, walked again on the same
emulator once warm, passed every check, and the other 33 had passed in
the full run. The walk now waits for a just-started emulator to settle,
and watches the dissolve slowed fivefold
([why](ISSUES_LOG.md#a-check-that-held-by-one-frame)). The run before, after Appearance became one
Light | Dark switch whose change dissolves, passed all 256 on its first try; the one before that,
after the list learned to keep its place, all 253. Before them,
the run after the desktop's old way was
brought over, passed all 250 on its first try. The one before it, after
the sheets were fixed, passed all 238 it then had; the parts written
after it were walked on their own — once a photograph taken on the
file's word, before the first start after an install had drawn the list,
learned to wait for the screen. The run after Share passed 229 and failed two:
a brand-new notebook read three seconds after the first start following
an install, on an emulator short of memory, before the app had written
it. Those reads now wait for the file, as every read of the autosave
does. The run before that, after the table's audit, passed all 228 it
then had on its first try. One earlier failed three — two at larger letters, where a cell's
style built anew had lost the theme's line height, and one Redo read a
moment too soon, which every read of the autosave now waits out
(`settle`). The first run of Phase 3 had failed five steps — all the walk's own, in
older parts the new screens had changed under them
([the story](ISSUES_LOG.md#old-steps-walking-through-new-screens)).
Phase 1's four:

* **A brand-new notebook**: the welcome note, written and remembered; a
  note typed, saved by Back, and born with the desktop's whole shape;
  pinning from a selection; Move to Trash and its Undo; Light | Dark
  moved — no "Follow the phone" on the page, the change dissolving, each
  mode's last theme kept — and a theme picked, under the desktop's own
  key; the name, and the greeting that uses it.
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
  app, a note shared out through Android's share sheet, the privacy
  policy opening a browser rather than the app, and About with no
  Feedback section before the app is on Google Play.
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
* **Size and face**, **lists**, **a new table** and **the table's bar**, each
  read back in the shape the desktop writes.
* **A wide window**: a selection and an open find bar surviving the
  phone turning; the rail; a note beside the list, its words in sight
  above the keyboard and no + over the search box while typing; a pin from the list
  kept by the editor's next save; another note opened from its title;
  the list put aside and back, a find bar open across both, and kept aside
  from note to note; the note beside the calendar, and closed for
  Settings; Settings' list and the calendar's month back in their places
  after a page and the History; the pane beside the calendar; two new notes in
  a row; a code shared in over an open note, upright, and the update
  kept; and a medium window, the note in the list's place.
* **Screens of every size** (2026-10-24, for tablets the owner has none
  of to try): the emulator's display set to a small phone and to tablets
  of 7, 10 and 14 inches, upright. The bar along a small phone's bottom,
  a rail from the 7-inch, a note in the list's place up to the 10-inch
  and beside it on the 14-inch; on each, + on the screen and the
  calendar's week from Monday to Sunday in sight. A rail moved to 700dp
  and the panes to 900dp each failed it.

Phase 3 added a part for each of its features, the same way:

* **Moving a day**: a date the series already has refused; a day moved
  with what was typed on it, the editor following it; the move's Undo
  putting it back, its own edit with it. (A day refused its way home
  while another stands there is held by `DaysTest`.)
* **Days off the schedule**: a series with two edits left off it, the
  banner, the first made a note on its date, the second sent to the
  Trash, the summary said, the banner gone.
* **Arranging**: Custom order chosen, cards dragged by the handle, a
  pinned note kept above the rest, and a thirty-note list scrolled by a
  card held at its edge — each order read back as the desktop numbers it.
* **Attaching**: a file added through the picker, kept under a stamped
  name with its bytes in the folder, then taken off after its question
  and its stored copy deleted.
* **Duplicates**: two groups found by the desktop's own key, the one
  with a file saying it will be merged, a copy sent to the Trash after a
  question with its file handed to the note kept, the other group left
  as it was.
* **A table's extras**: a caption numbered and placed with its table, a
  column wider in the separator's dashes, a hidden column as its `~` and
  back, a line above the table, and Undo taking those back.
* **Undo and Redo**: two bursts of typing taken back one at a time and
  brought back, read from the file the autosave wrote.
* **Copy and paste**: a note's words copied with the keyboard's Ctrl+A
  and Ctrl+C, pasted into another with their size, bold and list; the
  app restarted and the same clipboard pasted as the HTML it is, the size
  left behind; Ctrl+Shift+V plain; a cut pasted back whole. The selection
  menu is photographed, with "Paste as plain text" on it, not pressed
  ([why](ISSUES_LOG.md#a-menu-the-phones-own-tree-leaves-out)).

The first test on a real phone added eight parts and grew seven:

* **Previews**: a table with a row left open and one typed past its last
  cell — the card shows words only and "▦ 1 table", a table-only note is
  "(untitled)", and its calendar row says `▦×1`.
* **A table at larger letters**: drawn with the phone's letters at 115%
  and 130%, every cell one line tall, one letter or six.
* **Sharing a note**: a note with a table offers no words, and Text does
  nothing; PDF, Web page, Text and Share code each bring the phone's
  share sheet to the front; the PDF handed over is read back — one A4
  page, not blank, for a short note, and for a long one several, each its
  own and none blank — and the page holds its table; and asked for twice
  while a 40-page PDF is made, one share sheet opens over the whole PDF
  and a message shows. Which message, the screen cannot say: the order
  is held by `OutgoingTest`
  ([why](ISSUES_LOG.md#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been)).
  It replaced the part for "Send by email".
* **A sheet over three buttons**: the emulator put on three-button
  navigation and the app dark on a light phone, as the owner's is —
  Share's sheet and the table's More open whole, their last line above
  the buttons; the buttons' bar dark and the clock light, read from the
  screen's pixels
  ([why](ISSUES_LOG.md#a-sheet-dressed-for-the-phone-not-the-app)).
* **A copy made right after styling**: words made bold and 14 from the
  format bar, copied at once and pasted elsewhere with both; then made
  italic and cut, pasted with the italic, gone from where they were
  ([why](ISSUES_LOG.md#a-copy-of-the-selection-as-it-was-when-selected)).
* **Typing next to styles**: letters glued after a highlighted word
  highlighted, letters typed before a bold word plain, B pressed with
  nothing selected styling what follows until pressed again — read back
  from the file — and a colour tapped twice staying
  ([why](ISSUES_LOG.md#letters-typed-beside-a-style-read-from-the-text-alone)).
* **The calculator's answer in a cell**: a cell being written in, the
  calculator opened from the menu, Insert — the answer in the cell, the
  table no longer.
* **A table pasted into the comments**: a row copied and pasted there,
  left out, and the line at the foot saying so.
* **A tap past a line's end** (2026-10-22, the owner's phone): past an
  item's end and a letter, past a plain line's end, past an item's end and
  Enter — each landing on the line tapped.
* **The editor's corners** (2026-10-22, found by an agent playing an
  ordinary user): a tap at the end of the last item over the empty line a
  list leaves, a tap on a marker, a letter after Undo, Enter in the title,
  the checklist's ✓, and in a long note its box and Add above the keyboard.
  Since 2026-10-23, from the owner's phone: three rows added with the ✓,
  the newest in sight; Enter after Enter at the foot of what shows, in the
  text and in the comments, the line being written in sight; on its side,
  the keyboard rising over a long note and the cursor's line still there.
  The checklist, the comments and files reached through ⋮, the links under
  the note gone.
* **The day's reminder** (2026-10-23): the bar on a day with a note, its
  day written under the desktop's key, a tap opening the day, none on a
  second start; the notification turned on through Android's question,
  its alarm set, fired, saying the count and not the note's words, a tap
  on it opening the day, and turned off with its alarm gone.
* Grown: Appearance's Dark | Light switch (light themes on a light phone,
  the dark ones a tap away, a dark pick saved as dark mode's); the size
  box saying "Default" then "20"; Autofit all columns, tapped from the
  first column, fitting the second; a template's body with its words and
  cells and no pipe row; the series saying its edits reach every day; a
  moved day saying where it came from; one imported note opened from the
  line that says it came.

The photographs it takes are looked at, not only saved: the walk is
where the Settings switch that did not redraw, Back closing the app from
Settings, an Undo that vanished before a thumb could reach it, a merged
cell drawn short, an editor that kept the last note's scroll, a handle
that selected its card and a card that kept its place when held at the
list's edge were found ([the stories](ISSUES_LOG.md)).

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
| Pasting (Phase 3) | `html_to_note` on Python's own `html.parser`, `note_to_html` with tables bordered for the clipboard, `reflow_wrapped`, `strip_list_markers`, `forget_widths` | 567 fragments read, 80 of them the app's own copies with their HTML, 117 plain texts, 86 markdown tables |

Pasting is the one port where the phone answers differently on
purpose, in 20 of the 567 fragments: a style inside a pasted page's
second table, which the desktop puts beside its words
([the finding](ISSUES_LOG.md#two-desktop-findings-from-porting-phase-3)).
Those answers are made by the same two-line change to the desktop's own
function, run in the fixture tool's process, and the desktop's answer is
kept beside each one; a test fails if the two ever differ anywhere else.

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

### The release build, over a debug one

A release build cannot be read through `run-as`, so it was judged by
what it is and by what it shows. Read from the APK: the certificate is
the debug build's (`apksigner`), it is not debuggable and asks for no
permission beyond one Android adds itself (`aapt2`), its native library
is 16 KB-aligned in all four ABIs (`zipalign -P 16`, and the ELF's own
segments), and every file its code looks up is where the renamed code
will look (`tools/check_release_lookups.py`). On the emulator: a new
notebook's welcome note, a paste of text holding an `&` and a `<` after
a restart, the editor's menus, the calendar and every Settings page,
with the crash log empty. Then the trip a test phone makes: a debug
build holding a notebook copied in, the release build installed over
it, the same list, every note opened — and the debug build put back,
the notebook's files had the same checksums as before.

### The unit tests, and their mutants

706 JVM unit tests in 31 suites pin the rules ported from Python, and
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

Phase 3 ran 94 against the unit tests: 90 killed, and four equivalent —
a `trimEnd` a stored name had already had done, which was removed with
it; a table row's length check its two-cell check already makes, as in
the Python; a copy's line filter that both readers after it repeat; and
the space of a join, which nothing reads after a piece one character
long. Eleven survivors on the way were weak checks, strengthened until
they died — six of them cases the paste fixture had not held, among
them [a Kelvin sign written as a K](ISSUES_LOG.md#an-escape-written-as-the-letter-it-stood-for).
Four more were built into the app: an editor that never records a step
for Undo, and the copy, the paste and the kept copy behind the note's
clipboard. Each was caught by the walk.

The first test on a real phone, the table's audit, Share and the
desktop's old way ran 102 against the unit tests — the card's table
rule, Autofit all, a copy's selection brought up to date, the table's
merge, widths, rows and styles, the audit's graves, what Share sends and
its one share at a time, the Trash's window, typing beside styles and
the old way's other rules. 99 were killed, one, a deleted row's orphan
marker, only after a test was written for it; three were equivalent,
each removing a check another line already made, and each redundant
check went with its mutant. Seven more, against the email's addresses
and its words, went with the code they mutated when Share replaced it.

Twenty-nine were built into the app. Twenty-seven were caught by the
walk, among them a PDF painting every page on one picture, a long note
printed as its first page, a note with a table offered as words, a PDF
handed over as the page's code, a sheet opened half-way or drawing the
phone's bars light over the dark app, the Trash page's backup opening a
file to import — that one only once the check asked for a Save button —
and B pressed with nothing selected doing nothing. One, the entities file looked up
relative to a class the release build renames, passed the walk — a
paste with its formatting and one without look the same on a screen —
and is caught by `tools/check_release_lookups.py` on the built APK. One
survived the walk: a second Share started over the first, whose failing
message the walk's look at the screen cannot tell from the refusal's;
the rule moved to the rules side, where the unit tests kill it. Four
more, against the email's hand-off to a mail app, went with that code,
among them the one that had survived, Gmail named rather than its
writing window
([the story](ISSUES_LOG.md#mail-apps-only-said-a-way-android-17-no-longer-accepts)).
The size box's and the template body's fixes had no mutant run, nor did
a PDF's page break moved to the middle of a gap — looked at, page by
page — nor the PDF's file written off the main thread, which was
measured ([the story](ISSUES_LOG.md#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been)).

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
