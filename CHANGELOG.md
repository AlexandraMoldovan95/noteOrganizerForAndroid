# Changelog

What changed in the Android app, newest first. The desktop app keeps its
own changelog in its own repository; this one covers only what runs on
Android.

## How the numbers work

`versionName` is `0.MINOR.PATCH`, and the first number stays `0` until
the app is handed to somebody as a real release rather than installed by
hand.

- **MINOR** moves when the app can do something it could not do before:
  read a note, write one, edit a table, recognise a note it has already
  met. The numbered list in [docs/STATUS.md](docs/STATUS.md) is the same
  list told as status instead of as history.
- **PATCH** moves for fixes and for changes that add no new capability.
- **`versionCode`** is the number of released version sections in this
  file. Android requires it to increase and never repeat; tying it to the
  sections means it cannot quietly drift away from the name beside it.

A version is cut when an APK is built to actually be used — the same
moment the desktop app cuts one for an `.exe` rebuild. Documentation-only
changes do not move the number, because they do not call for a build.

**Everything below `0.5.0` is reconstructed.** The work happened on the
dates given and in that order — the git history, the test suites and
`docs/STATUS.md` all agree — but the phone ran debug builds throughout,
so no numbered release existed at the time. It is written down so the
trail is not lost, not to claim releases that were never cut.

---

## [Unreleased] — Phase 3

The rest of what the desktop does with a note, each kept to what a
phone's screen holds: arranging the list by hand, moving one day of a
series and settling days its schedule left behind, attaching files,
finding duplicates, a table's caption and column widths, undo and redo,
and copy and paste that keep the formatting. Built on Phase 2 and to be
numbered after it. What is still left is in
[docs/STATUS.md](docs/STATUS.md#not-built-yet).

### Added

- **Custom order arranged by hand**: a ⠿ handle on each card, the list
  scrolling by itself at its edges, a note moving within its own block —
  pinned or not, its category when grouped — and every note numbered as
  the desktop numbers them, with no "updated" stamp.
- **"📅 Move this day…"**: one day of a series held on another date,
  with its Undo, in the desktop's words when a date is taken.
- **Days off the schedule**: when a repeat changes and days with their
  own edits fall off it, the series says so, and each is settled with
  one question — a note of its own, the Trash, or left as it is.
- **Attach files** from the phone's picker, stored as the desktop
  stores them, and take one off after a question.
- **Settings → Duplicates**: the desktop's finder and its clean-up, with
  Undo, a file a copy carried handed to the note kept.
- **A table's caption, its columns' widths and a line above it**, from
  the cell's menu: Add caption below…, Wider, Narrower, Autofit, Hide
  column, Unhide all columns, Add a line above the table.
- **Undo and Redo** in the editor, a burst of typing at a time, from ↶ ↷
  and from a keyboard's Ctrl+Z and Ctrl+Y.
- **Copy and paste with the formatting**: a paste reads HTML as the
  desktop's paste does; a copy puts HTML beside the words; the app's own
  copy brings back sizes and faces; "Paste as plain text" and
  Ctrl+Shift+V paste the words alone. A paste the caps cut says so.

### Changed

- A day of a series, its move undone or its date reset, comes home only
  to a date no other day of the series is on; the desktop brings it home
  on top of the other.
- A pasted page's second table keeps its styles on their words, where
  the desktop's padding moves them beside them.

### Testing

- 572 JVM unit tests in 24 suites, from 516 in 23. 94 mutants against
  them: 90 killed, 4 equivalent. Four more built into the app and caught
  by the walk on the emulator.
- **A sixth port held to a fixture the desktop's own Python wrote**:
  pasting — 567 HTML fragments through the desktop's `html_to_note` and
  Python's own parser, the HTML 80 of the app's own copies were written
  as, and 117 plain texts rejoined and unmarked; the 20 fragments where
  the phone parts from the desktop on purpose are kept with the
  desktop's answer beside them.
- `tools/emulator/walk.py`: 177 checks in 25 parts, from 127 in 17 —
  a part for each Phase 3 feature, the keyboard's copy and paste keys
  where the selection menu cannot be pressed. The first full run from
  start to end failed five steps, all the walk's own, in older parts the
  new screens had changed under them; fixed, the second passed all 177.

## [Unreleased] — Phase 2

The desktop's remaining everyday features, each kept to what a phone's
screen holds: the calendar and repeating notes, the whole format bar,
templates, find, the calculator, export, statistics, and a layout for
wide screens. Built on Phase 1 and to be numbered after it. What is left
for Phase 3 is in [docs/STATUS.md](docs/STATUS.md#not-built-yet).

### Added

- **The calendar**, a tab of its own: the month, Monday first, a dot for
  each colour of note on a day, painted days and their markers, today
  and the open day marked, a swipe for the month; the open day's notes
  under it with "+ Add note"; a day held down to add, paint or mark,
  with Undo for what is cleared; and the History in the month's place.
- **Repeating notes**: the Repeat sheet in a note's details, which
  chooses rather than types, saying the rule in the desktop's words and
  showing its next three days. The desktop's recurrence engine, ported
  whole.
- **One day of a series on its own**, the desktop's day mode: written to
  that day alone, "Whole series" and "Reset this day" a tap away, a day
  deleted from its series with Undo.
- **The desktop's whole Sort and Show**: Calendar: soonest and oldest,
  Custom order as arranged on the desktop, the Repeating, Dated and With
  files filters — and the list grouped by category, its groups folding.
- **Settings → Statistics**, the desktop's snapshot of the notebook.
- **The calculator**, docked under the note: the desktop's keypad, More
  functions, a history, and Insert into note.
- **Find in note**: the count, back and next, case, prose and table cells
  together, every match painted.
- **Export as file**, `.html` or `.txt`, through Android's "save as" —
  the desktop's page byte for byte.
- **Templates**: start a note from one, save a note as one, and edit them
  in Settings → Templates, in the desktop's `templates.json`.
- **Size and face** on the format bar, and the desktop's faces drawn as
  the kind they are — serif, fixed-width or plain.
- **Lists, indent and alignment**, with the desktop's Enter and Backspace
  in a list and its renumbering.
- **A new table** from a size grid, its first cell open to type.
- **The cell menu**: a cell's alignment and highlight, merge and split,
  rows and columns, the table's place on the page, and delete the table.
- **Wide screens**: a rail down the left edge from 600 dp, and from 840
  dp the note beside the notes or the calendar, its card outlined.

### Changed

- The bar at the bottom has three places: Notes, Calendar, Settings.
  Back from the Calendar goes to the month from the History, then to
  Notes.
- Every Undo — a note to the Trash, a day removed, a day's colour or
  markers cleared — is one eight-second offer.
- A note's details show its repeat, and open the Repeat sheet.

### Fixed

- Every font size set on the desktop was read as empty and written back
  without a value, so the desktop dropped it; and typing into a list
  item or an aligned line dropped that line's entry.
- A change made to the open note from outside the editor — most of all
  a code shared in over it and applied as an update — was put back by
  the editor's next save.
- A cell's own alignment was not drawn, and a merged cell was drawn
  short of the columns it covers.
- A table edit that changed nothing — the last row asked to go —
  re-padded the whole table.

### Testing

- 516 JVM unit tests in 23 suites, from 317 in 13. 190 mutants against
  them: 187 killed, 3 equivalent. Three more built into the app and
  caught by the walk on the emulator.
- **Five ports held to fixtures the desktop's own Python wrote**: the
  recurrence engine (300 notes expanded, 400 dates read), the calculator
  (661 sums, 378 shown values), the export (400 notes, 794 files byte for
  byte), templates (302) and renumbering (300). The new sorts were
  checked against the desktop's `_sort_notes`, and all 400 export notes
  round-trip through a save on the phone.
- `tools/emulator/walk.py`: 127 checks in 17 parts, from
  33 in four — a part for each feature, and one turned on its side for
  wide screens. Its `screen_says` reads what a node says as well as what
  it shows, opening a note waits for the editor, and a title is checked
  by the box it shows, not by being in the tree.
- Two Phase 2 commit messages carry counts that were wrong:
  `e29e9a1` (24 mutants tried, 22 killed, 2 equivalent — not 26, 23 and
  3) and `314e89a` (`DaysTest` has 16 cases, not 17).

## [Unreleased] — Phase 1

Phase 1: from a viewer for the desktop's file to a notes app of its own,
ready for a store listing. What it covers, and what is left for Phases 2
and 3, is in [docs/STATUS.md](docs/STATUS.md).

### Added

- **A notebook of its own**, in the app's private storage. A notebook an
  earlier build left in the shared folder is copied in once — never
  moved — and a brand-new one opens on a welcome note written for the
  phone. A file that will not parse is kept aside as
  `notes_data_corrupt_<stamp>.json`, as the desktop keeps one, instead of
  stopping every write.
- **One screen for reading and writing**, saving itself 0.8 seconds after
  the last change, on Back and when the app leaves the screen.
- Checklist rows dragged into a new order; comments; attached files
  opened in the phone's own apps; note details with Android's calendar,
  the colour swatches, the pin, and the word count with reading time.
- **"↩ Restore previous"**: the desktop's `_prev` rescue copy, restorable
  on the phone — the text, and everything an update replaced.
- Long press to select: pin, move to a category, duplicate, share as one
  code, or move several notes to the Trash, with Undo for eight seconds.
- Categories managed in Settings: add, recolour, rename everywhere —
  repeating days and rescue copies included — and delete with a question
  about the notes filed under it.
- **The Trash as a page**: restore several, delete for good, empty, and
  the window (7 to 90 days). It empties itself at start, sparing any file
  a note still uses.
- **The desktop's backup `.zip`**, both ways, through Android's file
  picker: export the whole notebook, import one with the desktop's guards
  and its three questions. Nothing is removed by an import, and a note
  already here is only replaced when the person chooses to update it.
- Share codes arrive from any app through Android's share sheet.
- **The desktop's eight themes** and **"Follow the phone"**, on by
  default, under keys of the phone's own so the desktop's theme is never
  changed by it. Settings also holds the name the list greets with, and
  About: the version, the welcome note, and Send feedback.

### Changed

- **The interface is English**, in the desktop's own words wherever the
  desktop already has words for the same thing. The rules layer's
  messages too.
- The screens are rebuilt on Material 3, coloured from the desktop's
  palettes, with a bottom bar for Notes and Settings; the 1,850-line
  `MainActivity.kt` became twelve drawing files beside fourteen rules
  files ([the map](docs/ARCHITECTURE.md#the-map)).
- An update arriving by code or backup keeps the whole `_prev`, not only
  the text, and an update that would change nothing leaves the note
  untouched. A category the device does not have files the note under
  "General", as `normalize_note` does.
- The startup categories, the highlight palette and the category
  colours are the desktop's, and a deleted category no longer comes back.
- A share code whose version is not a number is refused as damaged, as
  the desktop refuses it.

### Fixed

- The first edit on the phone of a note with a centred or indented line
  turned that line into a bullet and dropped every alignment and indent
  in the note: paragraph entries were rebuilt from two fields instead of
  copied whole.
- Turning the phone, its dark mode coming on, or larger letters closed
  the note being written and went back to the list.
- The card printed a date as the file stores it (`2026-10-18`), the editor
  as the desktop shows it (`18.10.2026`); both now show the desktop's.
- A new note was missing the desktop's `moved` field, which the
  note-shape gate caught after the desktop's `0.92.374`.

### Testing

- 317 JVM unit tests in thirteen suites, from 221 in six; every new rule
  mutation-tested, about seventy mutants in all.
- `tools/check_palettes.py`: the themes and colours compared with the
  desktop's Python, in CI beside the note-shape gate.
- `tools/check_test_results.py` refuses results older than the code — it
  once printed "221 passed" over a build that had not compiled.
- `tools/check_layering.py` fails on a Kotlin file it has not been told
  about, rather than leaving it unchecked.
- `tools/check_english.py` reads the app too, now that it speaks English;
  four files may carry diacritics, for the search that folds them, but no
  Romanian words.
- `tools/emulator/walk.py`: 33 checks in four parts on the built app,
  reading the file after every step — and failing when a step never ran,
  after four checks were found that passed over exactly that. A backup
  made through the phone's picker was read by the desktop's own import
  functions.
- The documentation screenshots retaken in English, six of them, the
  notebook written straight into the app's storage.
- The test plan grows from 35 cases to 57.

### Earlier, before Phase 1

The entries below were collected under [Unreleased] before Phase 1
began, and ship with it.

#### Testing

- Four documentation screenshots, taken on the emulator by
  `tools/emulator/take_screenshots.py`. The script seeds a notebook
  written for the camera — documentation is the one place somebody's real
  writing must never end up — and every shot names the words that must be
  on screen before the shutter opens. The first run showed why: a tap
  that missed the search box left the plain list on screen, and the
  picture was saved and filed as "search" anyway.

- Two more gates, and CI to run all three on every push. The rules layer
  is checked for Android imports (`tools/check_layering.py`), and the
  unit-test job no longer trusts Gradle's exit code alone: it reads the
  result XML afterwards (`tools/check_test_results.py`), because a task
  that never ran prints exactly what a task with no failures prints.
- The interop fixture stops carrying a real note. It held a share code
  made from somebody's actual notebook; a share code is not encryption,
  and this repository is on GitHub now. `tools/make_interop_fixture.py`
  rebuilds it from an invented note, using the desktop's own encoder.

#### Removed

- Android Studio's example test, which checked that 2 + 2 is 4 and was
  counted in "222 unit tests". 221 then, all of them this project's.

#### Changed

- The launcher icon's provenance, the emulator tools and the screenshots
  are described where a reader meets them, rather than being folder
  contents nobody can account for.
- It runs on **Android**, not on phones. The title said "Note Organizer,
  on a phone", which is a claim about the platform that the platform does
  not make — Android is on tablets and foldables too, and the minimum
  here is an API level, not a form factor. Where the word stood in for
  the platform it now says Android; where it means the thing somebody is
  holding, it stays.
- CI runs once per change rather than twice, skips what cannot break a
  test, cancels runs a newer push has made pointless, and carries a
  timeout on every job — the default being six hours.

---

## [0.5.0] — 2026-09-14

### Added

- **The app wears the desktop's icon.** `tools/make_launcher_icon.py`
  imports the desktop app's own `tools/make_icon.py` and asks it for the
  drawing at Android's sizes — one drawing, two programs, rather than a
  second icon that merely rhymes with the first. Ships as
  `mipmap-*/ic_launcher*.webp` at five densities plus the two adaptive
  layers and their `mipmap-anydpi-v26` XML.

### Fixed

- The mark was drawn at the full 72/108 safe square and came back from
  the phone with its frame cut off at every corner: a circle mask keeps
  only the circle *inscribed* in that square, so a square mark has to fit
  inside it — 72/108 ÷ √2, about 47%. The preview that had approved the
  first attempt was composing the layers **without** the system's crop,
  which flattered the icon by exactly the amount that mattered.

### Removed

- Android Studio's template icon — the green robot — and the two vector
  drawables it arrived with.

### Testing

- `qa/NoteOrganizer_Android_TestPlan.xlsx`: 35 cases across nine areas,
  in the phone's own repository rather than the desktop's, each marked
  with who runs it — a unit test, an emulator tool, or a person with a
  phone in their hand.

---

## [0.4.0] — 2026-09-13

### Added

- **A note keeps one identity wherever it goes.** The desktop gained a
  `uid` beside its `id` (its version `0.73.159`), share codes carry it,
  and a note written here is born with one. A code holding a note this
  device already has is recognised as *that note* rather than guessed at
  from its words, and the phone asks — in the desktop's own wording —
  whether to update the copy here or keep both. Nothing is written until
  the question is answered.
- Share codes in both directions: a note becomes a pasteable code, and a
  pasted code becomes a note.

### Changed

- One import route instead of two. `importShareCode` — the way a code
  became notes before identity existed — had stayed behind when the
  screen moved to inspect-then-apply, reachable only by its own six
  checks. It was not merely dead: it carried a **second copy** of the
  new-note defaults, so a field added to the note shape would have rotted
  there in silence while its tests went on passing.

### Testing

- `tools/check_note_shape.py`: a gate that reads the desktop's Python
  **as the specification** and fails when the two programs stop agreeing
  about what a note is. Its own four kinds of drift were tried against it
  and all four turned it red — a gate nobody has seen failing is
  decoration.
- The identity loop walked on the emulator in both directions, on the
  built app rather than in a test, over a copy of a real 77-note
  notebook: the desktop's edit recognised here and applied in place; the
  phone's edit decoded back **in the desktop's own Python**; and the
  "keep both" answer adding a note with an identity of its own, leaving
  all 77 earlier ones untouched.

---

## [0.3.0] — 2026-09-13

### Added

- Editing an existing note's title and body, with the formatting spans
  and the bullet markers moved to follow the edit.
- Applying formatting over a selection — bold, italic, underline,
  strike, text colour and highlight — shown live while writing.
- Tables drawn as grids, with merged cells, checkbox cells, `<br>` line
  breaks, separator-driven alignment and sideways scrolling; and editing
  one as a grid: tap a cell to change it, add or remove rows and columns,
  turn a cell into a checkbox.
- Category, colour, date and pin on a note; checklist rows added,
  renamed, removed and ticked.
- Sorting (the desktop's five orders, pinned notes floating on top) and
  filtering by category.
- Delete to the Trash and restore from it. Nothing is destroyed: a
  deleted note moves to `trash.json` with a `deleted_at` stamp and the
  screen shows how long it has left. **The phone never purges** — only
  the desktop can also delete a purged note's attachments.

### Fixed

- A new note saved with none of its formatting. The toolbar worked and
  the screen drew bold text, but `formatting` had been added as a
  parameter to `updateNote` and not to `createNote`. Nothing looked
  wrong; the written file said otherwise.
- An edit reached the screen before it reached the file, because the
  copy handed to the screen and the copy being written were the same
  object. Found by a mutant that should have died and did not.

---

## [0.2.0] — 2026-09-12

### Added

- **Capture: a new note is written to the notebook.** The only code here
  that writes, and it carries the desktop's guards — append to the array
  that was READ rather than rebuilding the file from a partial model,
  refuse to overwrite a file that exists but would not parse, mend
  unpaired surrogates, write to a sibling `.tmp`, `sync()` it, then
  rename over the real file, and return the reason on failure instead of
  looking like it saved.

### Testing

- The write was judged by the desktop, not by itself: the result was
  pulled back off the emulator and checked in Python — all 77 existing
  notes identical field for field, the new note passing the desktop's
  `normalize_note` **unchanged**, every id unique.

---

## [0.1.0] — 2026-09-12

### Added

- A real 77-note `notes_data.json` reads without loss: Romanian
  diacritics, emoji, CJK, a 107,000-character note. No new dependency —
  `org.json` ships with Android.
- The note list draws the desktop's card: colour stripe, headline,
  preview, and the tags line with its date, comments, attachment, table,
  checklist and recurrence badges.
- Tapping a card opens the note with its formatting drawn.
- Search, by the desktop's own rule: title, body and comments, with a
  table's plumbing stripped so the words in its cells are found and its
  separator rows are not.

### Changed

- The headline falls through to the note's first spoken line when a note
  has no title, set lighter than a real title so a named note still reads
  as named. 48 of those 77 notes have no title and 15 more have one of
  three characters or fewer: a list of titles would have been a list of
  blanks.
- Search folds diacritics — `si` finds `și`, and the cedilla spellings
  older Windows keyboards produce match the comma-below ones. This is the
  one deliberate difference from the desktop so far, and it is a one-way
  widening: the folded search finds everything the strict one finds and
  more. Romanian diacritics get dropped constantly when typing on a
  phone, and a notebook written half with them is unsearchable under a
  strict rule.

### Testing

- Search verified against the specification rather than against itself:
  the desktop's `_query_matches_note` was run over the same file in
  Python, and the phone was made to type each query and report its count.
  Four queries, four exact agreements — 8, 1, 6 and 0 of 77.
