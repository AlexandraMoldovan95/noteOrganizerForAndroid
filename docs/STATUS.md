# Where this stands

What works today, what is deliberately not built, what was verified and
how, and the questions still open. Kept honest on purpose: the "not
built" list is as much a part of this document as the "done" one, and a
claim that has drifted out of date is a bug in the documentation.

## Open questions, deliberately not decided

* ~~**What belongs on a phone.**~~ **Decided 2026-09-12: read, search, and
  capture quickly.** Not a full editor. A fifteen-button formatting bar,
  tables with a cell editor, a calendar with a day panel and a templates
  manager are mouse gestures; a phone is good at *"I had a thought, I'm
  writing it down now."* Reading and searching are free of risk because
  they write nothing; capture is the step that first writes to a file and
  is therefore deliberately last.
* **How the two would share data**, if at all. The desktop app already
  has share codes (Base64 of zlib of JSON — a copy-paste format, **not
  encryption**) and a `.zip` backup export. Neither is a sync mechanism.
* **Whether this becomes a product at all.** It is currently an
  afternoon's exploration and is allowed to stay one.

## Where the work stands

**Done (2026-09-12 and 13).** Items 1–4 read only. From 5 on the app
writes, and every write carries the guards described under "The data
contract":

1. A real 77-note `notes_data.json` reads without loss: Romanian
   diacritics, emoji, CJK, a 107,000-character note. No new dependency —
   `org.json` ships with Android.
2. The list draws the desktop's card: colour stripe, headline, preview,
   and the tags line with its date, comments, attachment, table,
   checklist and recurrence badges.
3. Tapping a card opens the note with its formatting drawn.
4. Search, by the desktop's own rule: title, body and comments, with a
   table's plumbing stripped so the words in its cells are found and its
   separator rows are not. Verified against the specification rather than
   against itself — the desktop's `_query_matches_note` was run over the
   same file in Python and the phone was made to type each query and
   report its count. Four queries, four exact agreements (8, 1, 6, 0 of
   77). A test suite proves the code does what it says; this proves it
   does what the desktop does.
5. Capture: a new note is written to the notebook. This is the only code
   here that writes, and it carries the desktop's guards — append to the
   array that was READ (never rebuild the file from the partial Kotlin
   model), refuse to overwrite a file that exists but would not parse,
   mend unpaired surrogates, write to a sibling `.tmp`, `sync()` it, then
   rename over the real file, and return the reason on failure instead of
   looking like it saved.
6. Editing an existing note's title and body. The formatting and the
   bullet markers are moved to follow the edit; see the trap below.
7. Sorting (the desktop's five orders, pinned notes floating on top),
   filtering by category, and ticking a checklist row.
8. Delete to the Trash, and restore from it. Nothing is destroyed: a
   deleted note moves to `trash.json` with a `deleted_at` stamp and the
   screen shows how long it has left. **The phone never purges** — see
   below.
9. Tables drawn as grids, with merged cells, checkbox cells, `<br>` line
   breaks, separator-driven alignment and sideways scrolling.
10. Category, colour, date and pin on a note, and checklist rows added,
    renamed and removed.
11. Applying formatting: bold, italic, underline, strike, text colour and
    highlight over a selection, shown live while writing.
12. Editing a table as a grid: tap a cell to change it, add or remove rows
    and columns, turn a cell into a checkbox.
13. Share codes, both directions: a note becomes a pasteable code, and a
    pasted code becomes a note.
14. **A note keeps one identity wherever it goes.** The desktop gained a
    `uid` beside its `id` (its version `0.73.159`), share codes carry it,
    and a note written here is born with one. So a code holding a note
    this device already has is recognised as *that note* rather than
    guessed at from its words — and the phone asks, in the desktop's own
    wording, whether to update the copy here or keep both. Nothing is
    written until the question is answered. See "What a shared note is
    allowed to change" below.
16. **One import route, not two.** `importShareCode` — the way a code
    became notes before identity existed — stayed behind when the screen
    moved to inspect-then-apply, reached only by its own six checks. It
    was not merely dead: it carried a SECOND copy of the new-note
    defaults, so a field added to the note shape would have rotted there
    in silence while its tests went on passing. Removed on 2026-09-13,
    its checks moved onto the route the screen actually uses, and the
    four that matter re-proved with mutants. One of them changed meaning
    on the way and says so: importing the same code twice still makes two
    notes when the code carries no identity — which is most codes from
    before — and is recognised when it carries one.
17. 222 JVM unit tests, mutation-tested — the rules ported from Python are
   pinned, and each mutant was watched failing before being reverted. The
   identity work's own round: eleven mutants, four surviving the first
   pass. Three were weak checks, one was the code (see the candidate
   array, below); after both were fixed, all eleven died and all eleven
   reached the tests.
   **A silent mutant is not a passing one**, and it has happened three
   times now with three different causes: the edit missed its pattern, it
   changed something other than the behaviour meant, and it produced code
   that would not compile. In every case Gradle printed no test results
   at all. Always check that the mutant reached the tests before drawing
   any conclusion about them.

   And a mutant runner must put the source back even when it crashes.
   One here did not: it failed while reaching for the previous round's
   helper and exited with a broken `Notebook.kt` still on disk. The
   restore belongs in a `finally`, not at the end of the happy path — a
   tool that can leave the code mutated is a worse bug than anything it
   was written to find.

**The one deliberate difference from the desktop, so far.** Search folds
diacritics: `si` finds `și`, `masina` finds `mașină`, and the cedilla
spellings older Windows keyboards produce (`ş`, `ţ`) match the correct
comma-below ones. The desktop compares the letters as typed. This is a
one-way widening — the folded search finds everything the strict one
finds and more — and the reason is the language: Romanian diacritics get
dropped constantly when typing on a phone, and a notebook written half
with them is unsearchable under a strict rule. **It is worth porting back
to the desktop** rather than leaving the two disagreeing. Every other
rule here matches the Python deliberately, including where the Python is
arguably worse, because a difference is a surprise, not a feature.

**The identity loop was walked on the emulator, in both directions, on
the built app rather than in a test.** Over a copy of the real 77-note
notebook, normalised through the desktop's own `normalize_note` so every
note had a `uid`:

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

**The write was judged by the desktop, not by itself.** After capturing a
note on the emulator over a copy of a real 77-note file, the result was
pulled back and checked in Python: all 77 existing notes identical field
for field, the new note passing `normalize_note` **unchanged**, every id
unique. A suite proves the code does what it says. This proves the two
programs agree about what a note is.

**A finding that changed the first screen.** 48 of those 77 notes have no
title and 15 more have one of three characters or fewer, so a list of
titles is a list of blanks. The headline falls through to the note's
first spoken line, set lighter than a real title so a named note still
reads as named.

**A mismatch found in the desktop, to be fixed THERE, not here.**
`trash_retention_days`' docstring says a hand-edited `0` or `-3` "reads as
the default". The code is `max(1, min(days, 365))`, which clamps them to
**one day**; only a value that is not a whole number at all reaches the
default. Both are safe — neither makes the purge take everything — but
the two statements disagree, and this project follows the code. Worth a
one-line docstring fix in the desktop repository.

**A bug that only the file showed.** The toolbar worked, the screen drew
bold text, and a NEW note saved with none of it: `formatting` had been
added as a parameter to `updateNote` and not to `createNote`. Nothing
looked wrong on screen. Found by reading the written file, not the code —
the same lesson as the desktop's two invisible bugs. The test that would
have caught it exists now.

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
format: see [What a shared note is allowed to
change](DATA_FORMAT.md#what-a-shared-note-is-allowed-to-change).

**Not built, and not pretended:** comments and attachments are read-only;
checklist rows cannot be reordered; font size and family can be read but
not applied; a table's column alignment and its merges can be read and
kept but not changed from the phone; a recurrence rule can be read but
neither set nor changed, which
is why clearing a date is refused rather than offered with a warning —
there would be no way back; the desktop's `_prev` rescue copy is written but nothing on
the phone can restore from it yet, so it is insurance for the desktop to
honour; the file is read and written on the UI thread, which is fine for
half a megabyte and will not be for a synchronising app; styling inside a
table's cells is not drawn; a vertical merge is parsed but drawn as an
empty cell rather than a joined one, which no real note has yet noticed
because none uses one; attachments are untested because a real notebook
has none; the recurrence badge is one word rather than the desktop's full
sentence; a code can be typed or pasted but not scanned — QR is discussed
and undecided, and it would cost a scanning library plus the camera
permission this app has so far needed none of; one screen size and one
theme have been seen.

Three claims that used to sit in this list were removed on 2026-09-13
because the list had drifted: tables *can* now be edited as a grid,
sorting and category filtering *do* exist, and the search *does* fold
diacritics — each contradicted by the numbered list above it in this same
file. A stale "not built" line is worse than no line: it sends the next
reader to build something twice.
