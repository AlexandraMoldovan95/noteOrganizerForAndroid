# How the Android app is put together

The shape of the code, and the handful of decisions that explain why it
is that shape rather than another. Read the first section if you only
want to know where things are; the rest is the reasoning, and it is where
the surprises live.

- [The short version](#the-short-version)
- [The map](#the-map)
- [Decision: a note has two representations, on purpose](#decision-a-note-has-two-representations-on-purpose)
- [Decision: write to a copy, then to disk, then to the screen](#decision-write-to-a-copy-then-to-disk-then-to-the-screen)
- [Decision: the rules layer knows nothing about Android](#decision-the-rules-layer-knows-nothing-about-android)
- [Decision: one state object, no ViewModel, no injection](#decision-one-state-object-no-viewmodel-no-injection)
- [Decision: one layout at every width](#decision-one-layout-at-every-width)
- [Decision: the notebook is the app's own](#decision-the-notebook-is-the-apps-own)
- [Decision: `org.json`, and the trap that comes with it](#decision-orgjson-and-the-trap-that-comes-with-it)
- [Decision: a paste is recognised under the field, not taken from the menu](#decision-a-paste-is-recognised-under-the-field-not-taken-from-the-menu)
- [The desktop is the specification](#the-desktop-is-the-specification)
- [Where this will hurt next](#where-this-will-hurt-next)

## The short version

One Activity, no fragments, no navigation library: the whole app is
Compose, and which screen you are looking at is a value in one state
object, `AppState`. Below the screens sit twenty-six files of plain
Kotlin that know the rules — what a note is, how a table is written
down, when a series repeats, what a sum comes to, what a backup may
contain, which theme is which, what a pasted page becomes — and nothing
about Android. That split is why 675 tests run on a laptop in seconds,
with no emulator anywhere.

There is no database, no network, no account and no background service.
The app reads and writes the same JSON files the desktop app reads and
writes, in its own private folder on the device. The only doors out are
Android's own: the file picker for a backup, the share sheet for a note
or a code, a temporary grant for an attached file, and the browser for
the privacy policy — and, once the app is published, its Google Play page,
where feedback is a review. Both addresses live in `Links.kt`; the app
sends nothing with either.

Words follow the desktop's: a **popup** is anchored to what opened it (a
menu, a bottom sheet), a **dialog** holds the screen until it is
answered (the import's questions, "Delete for good?"). The Trash, which
the desktop keeps in Settings → Recently deleted, is Settings → Trash
here.

## The map

About 20,750 lines of Kotlin. The rules are 11,700 of them, in files that
import nothing from Android; the drawing is 9,000, in files that may.

**The rules** — ported from the desktop's Python, tested on the JVM:

| File | Lines | What lives there | The desktop's |
|---|---|---|---|
| `Notebook.kt` | 1,940 | The `Note` model, loading, every write to a note, search, sorting, share-code inspection, `_prev` and its restore | `model.py`, `storage.py`, parts of the UI |
| `Pasting.kt` | 1,580 | HTML in, through the desktop's reader and the part of Python's parser it reaches; a selection out as HTML; where a paste lands; a PDF's column rejoined | `richtext.py`'s `html_to_note`, `reflow_wrapped`; `html.parser`, `html.unescape`; the editor's paste |
| `Recurrence.kt` | 830 | Dates read and shown, the repeat rule, a series' days in a range, moved and edited days, the History | `recurrence.py` |
| `Organizing.kt` | 770 | Duplicate, pin, move, categories, the Trash for good, restore; Custom order placed and moved; files attached and taken off; duplicates found and cleaned up | `storage.py`, the settings page, the list |
| `Exporting.kt` | 700 | A note as the desktop's web page or plain text, through its own gatekeepers for styles and lines | `richtext.py`'s `note_to_html`, `html_document` |
| `Tables.kt` | 950 | The table grammar: pipe rows to a grid and back, `TableEdit` with the table bar's edits and where each cell came from, a merge's and a row height's writes, a column's width and hidden mark, each cell's place in a body | `tables.py`, the editor's `_grid_*` |
| `Importing.kt` | 580 | One planner and one apply for codes and backups: the three questions, what joins without asking | `import_data`, `split_by_identity` |
| `Days.kt` | 490 | The calendar's month and days, painted days and markers, one day of a series saved, deleted, reset, moved — and days off the schedule settled | the calendar view, day mode |
| `Calculator.kt` | 420 | The calculator's parser, its display, its keys, and where an answer goes in a note | `calculator.py` |
| `Paragraphs.kt` | 460 | List markers, indent and alignment; Enter and Backspace in a list; renumbering | `richtext.py`, the editor |
| `Themes.kt` | 350 | The eight themes, the palettes, and the contrast rules that pick legible words | `themes.py` |
| `Normalize.kt` | 350 | The parts of `normalize_note` a writer needs, `_dup_key`, `content_signature` | `model.py` |
| `Backup.kt` | 270 | The `.zip`: export, the guards, unpacking only known names | `_safe_extract_zip` and friends |
| `Styling.kt` | 360 | Format spans over a selection, and the desktop's sizes and faces | `richtext.py` |
| `Settings.kt` | 300 | `settings.json`: the desktop's keys, the phone's theme keys, categories | `storage.py` |
| `Templates.kt` | 200 | `templates.json`: normalising, reading, the two saving rules, a note from a template | `normalize_template`, the templates page |
| `Editing.kt` | 300 | A body cut into prose and tables for the editor, a new table, a caption, Undo's history, word count, reading time | the editor |
| `Sharing.kt` | 220 | Share codes: Base64 of zlib of JSON, and the limits that make a pasted one safe | `sharing.py` |
| `Finding.kt` | 110 | Find in note: matches in prose and cells, the count, stepping | the find bar |
| `Welcome.kt` | 110 | The welcome note a new notebook opens on | `welcome.py` |
| `Stats.kt` | 95 | The Statistics page's figures | the statistics page |
| `Files.kt` | 95 | The atomic write, and keeping a broken file aside | `storage.py` |
| `Startup.kt` | 110 | What happens before the first screen: copy-in, welcome, purge, the first-use day | the entry file's start |
| `Outgoing.kt` | 90 | A note shared: whether it has a table, so its words are not offered; its words for a message; the file's name; one share made at a time | the export's `.txt` and file name |
| `PyText.kt` | 55 | Python's idea of a space, for the ports that strip and split as it does | `str.isspace` |
| `Links.kt` | 20 | The pages the app opens in the browser: the privacy policy, and its Google Play page once the app is there | `core/diagnostics.py`'s addresses |

**The drawing** — turns rules into pixels, checked on the emulator:

| File | Lines | What lives there |
|---|---|---|
| `NoteEditor.kt` | 2,820 | The one screen for reading and writing a note: autosave, Undo and Redo, the format bar and its pending styles, copy and paste through the note's clipboard, checklist, comments, files, details, restore, a day's move and the questions for days off the schedule, the series' banner, a cell written in place and the table's bar and More |
| `SettingsScreens.kt` | 830 | Settings and its nine pages, Duplicates among them |
| `CalendarScreen.kt` | 570 | The month, the day's notes, a day's menu, the History |
| `NotesScreen.kt` | 550 | The list: greeting, search, sort and show, groups, selection, the handle that arranges Custom order, the + button |
| `AppState.kt` | 590 | The app's state, and every action a screen can take on the notebook |
| `MainActivity.kt` | 510 | The Activity: the notebook's folder, the doors to the rest of the phone, the bar or the rail, the panes, Back |
| `TemplateScreens.kt` | 480 | Choosing and saving a template, Settings → Templates, the template editor |
| `ImportFlow.kt` | 290 | The code dialog and the import's questions, in the desktop's words |
| `CalculatorPanel.kt` | 250 | The calculator docked under the note |
| `RepeatSheet.kt` | 250 | The Repeat sheet |
| `TableView.kt` | 420 | The grid, drawn: a merge down as one cell, the chosen cell holding the field it is written in and the handles on its edges |
| `Components.kt` | 250 | The note card, swatches, the shared dialog, and `AppSheet`, which every bottom sheet is: the app's light or dark for the phone's bars, opened whole |
| `FindBar.kt` | 180 | The find bar, and matches painted |
| `PdfPrint.kt` | 280 | The export's page printed to an A4 PDF by the system's WebView: drawn page by page on the main thread, the file written off it |
| `ShareFlow.kt` | 180 | The Share sheet and its cards, the share mark, and the hand-off to the phone's share sheet |
| `ExportFlow.kt` | 155 | Export as file through Android's "save as" |
| `RichText.kt` | 140 | A note plus its spans, turned into an `AnnotatedString` |
| `ui/theme/` | 280 | A palette as Compose colours, Material 3 mapped onto it, and `ThemeVeil`, the picture a change of theme dissolves under |

The tests mirror the rules, a suite for each file of them; the suites
and their counts are listed in [qa/README](../qa/README.md#the-unit-tests).
Four hold drawing-side classes: `AppStateTest`, the order of the saves
around the open note, which no emulator tap can time
([why](ISSUES_LOG.md#taps-aimed-through-the-keyboard)), and
`EditorCopyTest`, `EditorTableTest` and `EditorTypingTest`, the editor's
model.

## Decision: a note has two representations, on purpose

This is the one that explains the most code.

`Notebook.load()` returns a `Load` holding **both** a `List<Note>` — the
model the screen draws — and `raw: JSONArray`, the array exactly as it
was parsed from the file.

`Note` deliberately reads only what the screen needs. It does not carry
every field of `exceptions` or `overrides`, and it cannot carry a field
that a future desktop version adds and this app has never heard of.

So a write **never rebuilds the file from `Note`**. Serialising that
model back would silently delete the parts it never read — from every
note in the file, on the first save, invisibly. Writes work on `raw`:
they append to the array that was read, or replace one entry inside it,
and leave every other byte alone. The same rule holds one level down,
inside a note: a paragraph entry renumbered by an edit is copied whole,
not rebuilt from the two fields this app expected it to have
([the story](ISSUES_LOG.md#a-centred-line-that-came-back-as-a-bullet)).

The rule this comes from is the desktop's, and it is written out, with
the guards every write carries, in
[DATA_FORMAT.md](DATA_FORMAT.md#a-partial-reader-must-not-become-a-total-writer):
*a partial reader must not become a total writer.*

## Decision: write to a copy, then to disk, then to the screen

Every write in the rules layer follows the same three steps:

1. build a **candidate** — a copy of the array, with the change applied;
2. write the candidate to a sibling `.tmp`, `sync()` it, rename it over
   the real file (`Files.writeAtomic`);
3. only now update the in-memory array the screen is reading, and let
   `AppState` refresh the screen from it.

If step 2 fails, the screen never showed a change the file does not have,
and the failure comes back as a `SaveResult.Failed` carrying a reason to
show — not as a save that looked like it worked.

Two traps live here, and both have drawn blood:

- **Copying a `JSONArray` copies references.** The candidate holds the
  same note objects as the live array, so editing a note "in the copy"
  edits what is on the screen. A note being changed must be rebuilt from
  its own text first. See the [issues log](ISSUES_LOG.md#an-update-that-reached-the-screen-before-it-reached-the-file).
- **The file may exist and not parse.** Its bytes are copied aside first
  and the app starts from an empty array, as the desktop does. Only when
  even the copy cannot be made is `raw` null — and then every write
  refuses, rather than replacing a file nobody has kept.

## Decision: the rules layer knows nothing about Android

The twenty-six files in the first table contain **zero** `import android`
and **zero** `import androidx`. This is not a coincidence to be preserved
by good intentions; it is the same rule the desktop keeps between its
`core/` layer and tkinter, and it buys two things:

- the tests for those files run on the JVM, with no device and no
  emulator — which is why there are 675 of them and why running them
  costs seconds;
- the rules can be read, and compared against the Python they were ported
  from, without Compose in the way.

The rule is checked rather than remembered:

    py tools/check_layering.py

It lists both sides by name. A new `.kt` file that is on neither list
fails the gate, because left to default, a rules file nobody listed
would be free to import Android with the gate printing "clean" over it.

The leak it guards against would be quiet: reach for
`android.util.Base64` because it is first in the autocomplete and
everything still compiles, still runs, and can no longer be tested
without a device. The same habit puts a display rule in the right place:
[a date shown one way](ISSUES_LOG.md#the-same-date-written-two-ways)
lives in the rules, where every screen can ask for it.

## Decision: one state object, no ViewModel, no injection

`AppState` holds what the app is showing — the tab, the settings page,
the open note, the loaded notebook, the message waiting to be said — and
every action a screen can take on the notebook. Screens read it and call
it; it calls the rules layer and refreshes itself from what was written.
There is no ViewModel, no DI container, no repository interface and no
abstraction over the file.

At this size those layers would be ceremony: every one of them exists to
decouple things that are not currently coupled. One class does what they
would, and is short enough to read in one sitting.

Two things it does on purpose:

- **The notebook is held with `neverEqualPolicy()`.** A setting is saved
  inside the object that was read, so the refreshed notebook compares
  equal to the old one and Compose would skip the redraw. Every refresh
  is a redraw ([the story](ISSUES_LOG.md#a-setting-saved-and-a-switch-that-did-not-move)).
- **The editor saves through hooks the Activity calls.** A note is saved
  0.8 seconds after the last change, on Back, and in `onStop` — because
  most notes on a phone are left by Home or by switching apps, not by
  Back.
- **Every change to a note from outside the editor goes through one
  door**, `outside()`. The editor holds its own copy of every field and
  writes them all back when it saves, so when the open note is among the
  notes a change touches, the door saves what the editor holds first and
  has the editor read the note back after. A pin from the list beside
  the note, a code shared in over it and applied as an update — each was
  once [put back by the editor's next save](ISSUES_LOG.md#an-update-the-editor-put-back).
  A new action that writes notes belongs behind that door.

## Decision: one layout at every width

Material's window widths, measured on the window itself rather than on
the device, so a split screen counts too: under 600 dp, the bar at the
bottom and one screen at a time; from 600, a rail down the left edge; from
840, the note beside the notes or the calendar. Settings has no note to
sit beside, so it never shares the screen.

The three are **one layout**, with the rail, the panes and the bar each
there or not — never three layouts chosen between. Compose keeps a
screen's state by its place in the tree, so a layout per width would
put the editor somewhere else when the phone turns and build a new one:
its find bar shut, its cursor lost, and words not yet saved left in the
old one. Kept in its place, the editor is only moved. The walk turns the
phone with a find bar open and a selection made, and both were shown
lost by a mutant that rebuilt the editor
([the story](ISSUES_LOG.md#what-a-phone-resets-for-free)).

The same story is why each note opened gets **an editor of its own**,
counted by `AppState.opening`. On a phone the editor closes between
notes and starts each one fresh; beside the list it stays, and kept the
last note's scroll until it was told not to.

Inside the wide layout the window's edges are kept once, around the
panes, so the editor's own paddings find nothing left to add; on a phone
each screen keeps its own, as before.

## Decision: the notebook is the app's own

Until Phase 1 the app read a notebook from its external files folder,
where `adb push` could put a copy of the desktop's. Now it lives in
`filesDir/notebook/`: private, no permission needed, gone with the app —
which is why Settings → Backup says it is the way to keep a copy.

`Startup.prepare` runs before the first screen:

1. A notebook found in the older folder is **copied** in, once, the first
   time there is none here — never moved.
2. A notebook that did not exist until this start gets the welcome note,
   once.
3. The Trash is emptied of what has outlived its window, sparing files a
   note still uses.

It returns one line worth saying at the top of the list, or nothing.

The doors out are all the system's, so the app needs no permission at
all: the file picker (`CreateDocument` / `OpenDocument`) for a backup,
the share sheet for a note or a code, an intent filter for text shared in,
the browser, opened on the privacy policy's page, and a `FileProvider` that grants one attached file, or the PDF or page
being shared, to the app that opens it, for as long as it is open. The
share sheet lists the apps itself, so the app declares no `<queries>`
and never looks at what is installed. A backup is written and read on
`Dispatchers.IO`, and so is a shared PDF's file — forty pages written on
the main thread held it 1.2 s
([the story](ISSUES_LOG.md#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been));
everything else is small enough for the main thread
([for now](#where-this-will-hurt-next)).

## Decision: `org.json`, and the trap that comes with it

The notebook is parsed with `org.json`, which **ships inside Android** —
so the whole app has no JSON dependency at all.

The trap: the SDK jar that JVM unit tests compile against contains only
*stubs* of those classes, and every one of them throws "not mocked" at
runtime. Hence the single line in `app/build.gradle.kts`:

    testImplementation("org.json:json:20240303")

Test scope only. The app itself keeps using the platform's own — and the
two are **not the same library**. On the computer, a decimal arrives as a
`BigDecimal` rather than a `Double`, and a `JSONObject` forgets the order
of its keys. A rule that depends on either is written so it holds for
both: whole numbers checked as whole numbers, whatever class they arrive
as; and an order that matters kept in an ordered map, tested there
([the story](ISSUES_LOG.md#the-tests-json-library-is-not-the-phones)).

## Decision: a paste is recognised under the field, not taken from the menu

Pasting with the formatting needs the clipboard's HTML at the moment of
a paste, and a copy needs to put HTML beside the words. Compose's text
field does both itself, through a selection menu it draws (Cut, Copy,
Paste, Select all, then whatever the phone's other apps add — Translate,
Read aloud) and through a keyboard's Ctrl+V.

The obvious route was to take Paste out of that menu and add one of our
own. It works, and it costs: an added item goes **after** everything the
field and the phone put there, so Paste would sit behind Translate and
Read aloud, often in the overflow — and a keyboard's Ctrl+V would still
paste plain, by a road the menu never sees.

So the editor's fields get **the note's own clipboard** instead
(`NoteClipboard` in `NoteEditor.kt`), handed down under each field in
place of the phone's:

- **A copy or a cut** writes through it. When the words written are the
  selection the editor noted, HTML made from that selection goes beside
  them, and the copy is kept for a paste back into a note. A cut takes
  the words out before the clipboard is written, which is why the
  selection is noted as it is made, with the text it was made in.
- **A paste** reads through it. The field then puts the clipboard's text
  in place of the selection; when exactly that arrives within two
  seconds of the read, the editor knows it for the paste and makes it
  itself, from the HTML — through `Pasting`, where everything about what
  a paste becomes lives, tested on the JVM. Anything else is typing.

The menu's own items keep their place and their words, and Ctrl+V comes
the same way. What is added — "Paste as plain text", only when the
clipboard has formatting — is the secondary action, so its place at the
end is the right one. A paste from the keyboard's own clipboard chip
does not read the clipboard through the field, and goes in plain, as
typing does.

The cost is that the menu cannot be walked: it is a floating bar the
phone's UI tree leaves out, so the walk presses the keys that call the
same code, and the menu itself is photographed
([the story](ISSUES_LOG.md#a-menu-the-phones-own-tree-leaves-out)).

## The desktop is the specification

Where a rule here answers *"what should this do?"*, the answer comes from
reading the desktop app's Python — including where the Python is arguably
the worse choice, because a difference between the two programs is a
surprise rather than a feature. The deliberate exceptions, each with its
reason, are in
[STATUS.md](STATUS.md#where-it-differs-from-the-desktop-on-purpose).

That agreement is not left to memory. Two gates read the desktop's source
**as the specification**, and never write to it:

- `tools/check_note_shape.py` compares what a note is: its fields, the
  two places here that build a new one, what a share code carries, and
  what an arriving note — from a code or from a backup — may replace.
- `tools/check_palettes.py` compares the eight themes colour for colour,
  the category and note colours, the highlight palette, and the colours
  the contrast rules choose for words on a fill.

Where a rule can be run on its own, it is held to the Python's answers
rather than to a reading of them: a tool in `tools/` runs the desktop's
own function over hundreds of generated cases and writes what it
answered into a fixture under `app/src/test/resources/`, and a unit test
asks the phone the same questions. Repeats, the calculator, the export,
templates and renumbering are held that way
([the five](STATUS.md#the-phase-2-ports-answer-as-the-desktop-answers)),
and since Phase 3 pasting too — the reader, Python's parser under it and
the HTML a copy writes. Where the desktop's lines live inside a window
method that cannot be imported, the tool copies them and fingerprints
the original, and stops when the original changes. Where the phone
deliberately answers differently, the tool makes the phone's answers by
the smallest change to the desktop's own function, run in its own
process — the paste fixture's second-table rule is two lines — and stops
when the line it changes is no longer there. Reading the spec by hand
instead was [wrong twice](ISSUES_LOG.md#the-answer-written-by-hand-wrong-twice).

Both gates run in CI, in [gates.yml](../.github/workflows/gates.yml), kept apart
from the unit tests in [tests.yml](../.github/workflows/tests.yml) so a
change to a gate does not drag a five-minute build along with it. There
they fetch the desktop app and are run with `--require-desktop`, because
a check that quietly could not run reads exactly like a check that
passed. Reading a private repository from another one needs a token:
[qa/README.md](../qa/README.md#what-runs-on-every-push) says which, and
where it goes.

## Where this will hurt next

Named here so nobody has to discover them by surprise:

- **Notes are written on the main thread.** Fine for the notebooks seen
  so far — the largest real one is half a megabyte — and not fine for one
  ten times that on a slow phone. A backup already moved off it, and a
  shared PDF's file. Its pages are still drawn there, as a WebView must
  be — 0.35 s for the forty a PDF holds at most, on the emulator.
- **Where you were does not survive the process being killed.** The note
  is saved before that can happen (`onStop`), but the app reopens on the
  list rather than in the note.
- **`Notebook.kt` is 1,940 lines, `NoteEditor.kt` 2,820.** The first is
  the note model and every write to a note; the seam to split on is the
  day a group of writes needs state the others do not — not before, and
  not by line count. The second holds the editor's model, its clipboard
  and its sheets; the model itself, now with Undo and the paste, is the
  likeliest to leave first, then the table's bar and the format bar.
- **Undo lives in the open note.** It takes back the words and their
  styling while the note is open, as the desktop's does; it is gone when
  the note is closed, and the actions taken from outside the editor —
  the Trash, a moved day — have their own eight-second Undo instead.

Retired from this list in Phase 2: **one column everywhere** — the rail
and the panes are [above](#decision-one-layout-at-every-width). In
Phase 3: **no undo**.
