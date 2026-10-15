# Where this stands

What works today, what is deliberately not built, what was verified and
how, and the questions still open. Kept honest on purpose: the "not
built" list is as much a part of this document as the "done" one, and a
claim that has drifted out of date is a bug in the documentation.

**In short:** the app reads a real notebook without loss, searches it by
the desktop's own rule, writes new notes and edits existing ones —
formatting, tables, checklists, category, colour, date and pin — keeps
deleted notes in the Trash, and trades single notes with the desktop
through share codes that carry each note's identity. It is not a full
editor, and it does not sync.

## Contents

- [What it is for](#what-it-is-for)
- [What works today](#what-works-today)
- [The one deliberate difference from the desktop](#the-one-deliberate-difference-from-the-desktop)
- [Not built, and not pretended](#not-built-and-not-pretended)
- [Open questions](#open-questions)
- [How each part was verified](#how-each-part-was-verified)

## What it is for

This was the first open question — *what belongs on a phone* — and it is
no longer open.

**Decided 2026-09-12: read, search, and capture quickly.** Not a full
editor. A fifteen-button formatting bar, tables with a cell editor, a
calendar with a day panel and a templates manager are mouse gestures; a
phone is good at *"I had a thought, I'm writing it down now."* Reading
and searching are free of risk because they write nothing; capture is the
step that first writes to a file and is therefore deliberately last.

## What works today

Grouped by what the app does; when each item arrived is in
[CHANGELOG.md](../CHANGELOG.md), and how the main parts were verified is
[at the end](#how-each-part-was-verified). Reading and finding write
nothing. Writing, the Trash and sharing do, and every write carries the
guards in [Writing the file](DATA_FORMAT.md#writing-the-file).

### Reading

1. A real 77-note `notes_data.json` reads without loss: Romanian
   diacritics, emoji, CJK, a 107,000-character note. No new dependency —
   `org.json` ships with Android.
2. The list draws the desktop's card: colour stripe, headline, preview,
   and the tags line with its date, comments, attachment, table,
   checklist and recurrence badges. A note with no title is headed by its
   first spoken line, set lighter than a real title so a named note still
   reads as named
   ([why](ISSUES_LOG.md#a-list-of-titles-would-have-been-a-list-of-blanks)).
3. Tapping a card opens the note with its formatting drawn.
4. Tables drawn as grids, with merged cells, checkbox cells, `<br>` line
   breaks, separator-driven alignment and sideways scrolling.

### Finding

5. Search, by the desktop's own rule: title, body and comments, with a
   table's plumbing stripped so the words in its cells are found and its
   separator rows are not. It also folds diacritics — the
   [one deliberate difference](#the-one-deliberate-difference-from-the-desktop).
6. Sorting (the desktop's five orders, pinned notes floating on top) and
   filtering by category.

### Writing

7. Capture: a new note is written to the notebook, carrying the desktop's
   guards ([the list](DATA_FORMAT.md#a-partial-reader-must-not-become-a-total-writer)).
8. Editing an existing note's title and body. The formatting and the
   bullet markers are moved to follow the edit
   ([the trap](DATA_FORMAT.md#editing-plain-text-moves-formatting-onto-the-wrong-words)).
9. Applying formatting: bold, italic, underline, strike, text colour and
   highlight over a selection, shown live while writing.
10. Category, colour, date and pin on a note; checklist rows added,
    renamed, removed and ticked.
11. Editing a table as a grid: tap a cell to change it, add or remove rows
    and columns, turn a cell into a checkbox.

### The Trash

12. Delete to the Trash, and restore from it. Nothing is destroyed: a
    deleted note moves to `trash.json` with a `deleted_at` stamp and the
    screen shows how long it has left. **The phone never purges**
    ([why](DATA_FORMAT.md#a-delete-touches-two-files-so-the-order-of-the-writes-is-the-safety)).

### Sharing with the desktop

13. Share codes, both directions: a note becomes a pasteable code, and a
    pasted code becomes a note.
14. **A note keeps one identity wherever it goes.** The desktop gained a
    `uid` beside its `id` (its version `0.73.159`), share codes carry it,
    and a note written here is born with one. So a code holding a note
    this device already has is recognised as *that note* rather than
    guessed at from its words — and the phone asks, in the desktop's own
    wording, whether to update the copy here or keep both. Nothing is
    written until the question is answered. See
    [What a shared note is allowed to change](DATA_FORMAT.md#what-a-shared-note-is-allowed-to-change).
15. **One import route, not two.** A pasted code is inspected, asked
    about, then applied — by that route alone. The older `importShareCode`
    route, which carried a second copy of the new-note defaults, was
    removed on 2026-09-13
    ([the story](ISSUES_LOG.md#a-dead-route-carrying-a-second-copy-of-the-rules)).
    Importing the same code twice still makes two notes when the code
    carries no identity — which is most codes from before — and is
    recognised when it carries one.

### The app itself

16. **The app wears the desktop's icon.** Drawn by
    `tools/make_launcher_icon.py`, which imports the desktop's own
    `tools/make_icon.py` rather than copying it — one drawing, two
    programs. Android Studio's template robot and its two vector
    drawables are gone.
17. 221 JVM unit tests, mutation-tested — the rules ported from Python are
    pinned, and each mutant was watched failing before being reverted.

## The one deliberate difference from the desktop

So far there is one. Search folds diacritics: `si` finds `și`, `masina`
finds `mașină`, and the cedilla spellings older Windows keyboards produce
(`ş`, `ţ`) match the correct comma-below ones. The desktop compares the
letters as typed.

This is a one-way widening — the folded search finds everything the
strict one finds and more — and the reason is the language: Romanian
diacritics get dropped constantly when typing on a phone, and a notebook
written half with them is unsearchable under a strict rule. **It is worth
porting back to the desktop** rather than leaving the two disagreeing;
that is an [open finding](ISSUES_LOG.md#still-open).

Every other rule here matches the Python deliberately, including where the
Python is arguably worse, because a difference is a surprise, not a
feature.

## Not built, and not pretended

### Editing

* Comments and attachments are read-only.
* Checklist rows cannot be reordered.
* Font size and family can be read but not applied.
* A table's column alignment and its merges can be read and kept, but not
  changed from the phone.
* A recurrence rule can be read but neither set nor changed — which is
  why clearing a date is refused rather than offered with a warning:
  there would be no way back
  ([the trap](DATA_FORMAT.md#clearing-a-date-deletes-a-recurrence-rule)).
* The desktop's `_prev` rescue copy is written, but nothing on the phone
  can restore from it yet, so it is insurance for the desktop to honour.

### Drawing

* Styling inside a table's cells is not drawn.
* A vertical merge is parsed but drawn as an empty cell rather than a
  joined one, which no real note has yet noticed because none uses one.
* The recurrence badge is one word rather than the desktop's full
  sentence.
* One screen size and one theme have been seen.

### Underneath

* The file is read and written on the UI thread, which is fine for half a
  megabyte and will not be for a synchronising app
  ([where this will hurt next](ARCHITECTURE.md#where-this-will-hurt-next)).
* Attachments are untested, because a real notebook has none.
* A code can be typed or pasted but not scanned. QR is discussed and
  undecided, and it would cost a scanning library plus the camera
  permission this app has so far needed none of.
* Sync — see [How the two would share data](#how-the-two-would-share-data-if-at-all).

Three claims that used to sit in this list were removed on 2026-09-13
because the list had drifted: tables *can* now be edited as a grid,
sorting and category filtering *do* exist, and the search *does* fold
diacritics — each contradicted by the numbered list above it in this same
file. A stale "not built" line is worse than no line: it sends the next
reader to build something twice.

## Open questions

### How the two would share data, if at all

The desktop app already has share codes (Base64 of zlib of JSON — a
copy-paste format, **not encryption**) and a `.zip` backup export.
Neither is a sync mechanism.

**Sync is still not solved, and share codes still do not solve it.** They
move a note you choose, when you choose. Two devices editing the same
notebook still diverge, and nothing here reconciles that — there is no
background exchange, no clock deciding who wins, and no way to tell that
a note changed somewhere else without being handed its code.

What *has* changed is the part that made even a manual exchange
impossible. Identity was the blocker, not the network: `id` is `max + 1`,
a note's place in one file, so two devices both invent 88 and a note
arriving from the other one could only be guessed at by its words — a
guess that is wrong exactly when the note was edited, which is the only
reason anyone sends one back. The desktop now mints a `uid` for every
note at its own door (0.73.159), codes carry it, and both sides can
therefore ask the honest question instead of quietly copying. What an
arriving note may replace, and what it must never touch, belongs to the
format: see
[What a shared note is allowed to change](DATA_FORMAT.md#what-a-shared-note-is-allowed-to-change).

### Whether this becomes a product at all

It is currently an afternoon's exploration and is allowed to stay one.

### Findings waiting elsewhere

Two findings are open in the [issues log](ISSUES_LOG.md#still-open): a
docstring in the DESKTOP app that disagrees with its own code about
`trash_retention_days` — to be fixed there, not here — and the diacritic
folding above, worth porting back.

## How each part was verified

A suite proves the code does what it says. Where it could, the work here
was judged by something else: the desktop's own Python, the written file,
or the phone itself.

| Part | How it was verified |
|---|---|
| [Search](#search-agrees-with-the-desktop) | Four queries typed on the phone and counted, against the desktop's `_query_matches_note` over the same file |
| [Writing a note](#the-write-was-judged-by-the-desktop-not-by-itself) | Captured on the emulator, pulled back, and checked in the desktop's Python |
| [Identity and share codes](#the-identity-loop-walked-on-the-emulator-in-both-directions) | Walked both ways on the built app, with the desktop's Python at the other end |
| [The icon](#the-icon-on-the-phone-not-in-a-preview) | Installed and looked at on the emulator, not judged in a preview |
| [The rules](#the-unit-tests-and-their-mutants) | 221 JVM unit tests, each mutant watched failing |

### Search agrees with the desktop

Verified against the specification rather than against itself — the
desktop's `_query_matches_note` was run over the same file in Python and
the phone was made to type each query and report its count. Four queries,
four exact agreements (8, 1, 6, 0 of 77). A test suite proves the code
does what it says; this proves it does what the desktop does.

### The write was judged by the desktop, not by itself

After capturing a note on the emulator over a copy of a real 77-note
file, the result was pulled back and checked in Python: all 77 existing
notes identical field for field, the new note passing `normalize_note`
**unchanged**, every id unique. A suite proves the code does what it
says. This proves the two programs agree about what a note is.

Reading the written file, not the screen, is also how
[a new note saved without its formatting](ISSUES_LOG.md#a-new-note-saved-without-its-formatting)
was found: the toolbar worked, the screen drew bold, and the file held
none of it. The test that would have caught it exists now.

### The identity loop, walked on the emulator in both directions

Walked on the built app rather than in a test, over a copy of the real
77-note notebook, normalised through the desktop's own `normalize_note`
so every note had a `uid`:

* A note edited **on the desktop** became a 634-character code, was typed
  into the phone, and was recognised — the phone asked its question
  instead of copying. Answering "Actualizeaza" changed note 12 in place:
  same `id`, same `uid`, new title, `_prev` written with the old one,
  `created` untouched, the other 76 notes identical byte for byte, and
  the result passing `normalize_note` unchanged.
* The same note was then edited **on the phone**, shared from there, and
  its code decoded by the desktop's own Python: same `uid`, no `id`
  carried, `split_by_identity` answering "1 known, 0 new", and the
  desktop's `apply_shared_update` updating its copy in place with the
  three formatting spans and the eight checklist rows intact.
* Answering **"Adauga ca notita noua"** instead added an 78th note with
  `id` 87 and a `uid` of its own, leaving all 77 earlier notes untouched
  and every identity in the file distinct.

### The icon, on the phone, not in a preview

Verified on the emulator, not in a preview: the first attempt drew the
mark at the full safe-zone size and the phone cropped its frame away at
the corners, because a circle mask keeps only the circle inscribed in
that square ([the story](ISSUES_LOG.md#the-preview-that-flattered-the-icon)).

### The unit tests, and their mutants

221 JVM unit tests pin the rules ported from Python, and each mutant was
watched failing before being reverted. The identity work's own round:
eleven mutants, four surviving the first pass. Three were weak checks,
one was the code
([the candidate array](DATA_FORMAT.md#a-copy-of-the-array-is-not-a-copy-of-the-notes));
after both were fixed, all eleven died and all eleven reached the tests.
When the old import route was removed, its checks moved onto the route
the screen actually uses, and the four that matter were re-proved with
mutants.

Two lessons from running them, each with its full story in the issues
log:

* **A silent mutant is not a passing one.** It has happened three times
  now, with three different causes, and in every case Gradle printed no
  test results at all. Always check that the mutant reached the tests
  before drawing any conclusion about them
  ([the story](ISSUES_LOG.md#a-silent-mutant-is-not-a-passing-one)).
* **A mutant runner must put the source back even when it crashes.** The
  restore belongs in a `finally`, not at the end of the happy path
  ([the story](ISSUES_LOG.md#a-mutant-runner-that-left-the-code-broken)).
