# Changelog

What changed in the phone app, newest first. The desktop app keeps its
own changelog in its own repository; this one covers only what runs on a
phone.

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

## [Unreleased]

### Testing

- Four documentation screenshots, taken on the emulator by
  `tools/emulator/take_screenshots.py`. The script seeds a notebook
  written for the camera — documentation is the one place somebody's real
  writing must never end up — and every shot names the words that must be
  on screen before the shutter opens. The first run showed why: a tap
  that missed the search box left the plain list on screen, and the
  picture was saved and filed as "search" anyway.

### Changed

- The launcher icon's provenance, the emulator tools and the screenshots
  are described where a reader meets them, rather than being folder
  contents nobody can account for.

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
