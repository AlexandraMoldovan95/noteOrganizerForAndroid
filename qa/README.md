# How this app is tested

What could go wrong, what holds it, and how each check is run; then each
layer in depth. Read this before adding a check, so the new one lands
where it can actually fail.

## In half a screen

| What could go wrong | What holds it | What is not held |
|---|---|---|
| **A note lost or damaged** | Every write goes to a copy, then to disk, then to the screen, and one that fails says so ([why](../docs/ARCHITECTURE.md#decision-write-to-a-copy-then-to-disk-then-to-the-screen)); unit tests of the save, the start and the backup; the walk reads back the file the app wrote at every step it checks; the final audit's first wave, 25 rounds | A phone dying in the middle of a write is reasoned about (the file is renamed into place whole), not reproduced |
| **The two apps disagreeing** | Two gates read the desktop's Python as the specification of a note and of its colours; eight fixtures hold the ported rules to answers the desktop's own code gave; the desktop's import reads a code made here | That last check runs on this machine, not in CI; where the phone answers differently on purpose ([listed](../docs/STATUS.md#where-it-differs-from-the-desktop-on-purpose)), only the test plan looks |
| **A note carried between the two by hand** (there is no sync) | A note keeps its identity wherever it goes; one that arrives asks before it updates the note it matches, and an update keeps what it replaced, to be restored (`IdentityTest`); the walk carried notes both ways through the desktop's Python | A note changed on both devices is not merged: the update brings the arriving one, and the one it replaced is kept aside |
| **A hostile or broken code or backup** | Read with limits before anything is written: size, depth, encoding, paths that leave the folder, a zip that unpacks to more than it says (`SharingTest`, `BackupTest`); the audit's hostile and huge input | No fuzzer runs on each change: the hostile cases are the ones written down |
| **A very large note or notebook** | The walk opens a checklist of 120 rows of nearly 150,000 characters each; the list and the editor were measured | Measured on the emulator and on one fast phone, not on a slow one; notes are written on the main thread, fine for the half-megabyte notebooks seen so far |

And a screen that is wrong while its rule is right: the walk and the test
plan, below.

## Every check, and how to run it

Each layer exists because the one below it cannot see a certain kind of
failure.

| Check | Run it with | What only it can catch |
|---|---|---|
| **Unit tests on the JVM** ([how many](#the-unit-tests)) | `gradlew.bat testDebugUnitTest`, then `py tools/check_test_results.py` | A rule ported from Python that does not match the Python — held, where the rule can be run on its own, to answers the desktop's own Python wrote |
| **The note-shape and palette gates** | `py tools/check_note_shape.py`, `py tools/check_palettes.py` | The two programs quietly disagreeing about what a note *is*, or what a theme looks like |
| **The interop gate** | `py tools/check_interop.py`, after the unit tests (not in CI) | The desktop's own Python reading a code made here differently |
| **The boundaries** | `py tools/check_layering.py`, `check_english.py`, `check_comments.py`, `check_mutant_targets.py`, `check_workflows.py`, `check_fix_mutants.py` | Android leaking into the rules layer, which would push its tests onto a device; Romanian in the repository; a story told in a comment; a mutant that no longer finds its code; a workflow whose token may write, or an action not pinned to a commit; a change to the app's code that names no mutant |
| **The test plan's count** | `py tools/check_test_plan.py` | The plan's numbers written down wrong where this page gives them |
| **The release gate** | `py tools/check_release_lookups.py`, after `gradlew.bat assembleRelease` | Code the shrinker renamed, still looking a file up by its old place |
| **The licenses gate** | `py tools/make_licenses.py --check`, after `gradlew.bat assembleRelease` | A library the app carries that its Open-source licenses page does not name |
| **The emulator walk** | `py tools/emulator/walk.py <folder>` ([the other scripts](../tools/emulator/)) | A rule that is right and a screen that is wrong, each step read back from the file the app wrote |
| **The test plan** | `NoteOrganizer_Android_TestPlan.xlsx`, by a person | Whether a tap lands, whether the keyboard covers what you are typing, whether the thing reads well at arm's length |

## The unit tests

    set JAVA_HOME=<Android Studio's folder>\jbr
    gradlew.bat testDebugUnitTest
    py tools\check_test_results.py

1,094 tests across thirty-two suites on 14 November 2026 — `NotebookTest` (153),
`DaysTest` (77), `AppStateTest` (74), `TablesTest` (72), `OrganizingTest` (63),
`ImportingTest` (62), `RecurrenceTest` (52), `EditingTest` (49), `PastingTest` (47),
`IdentityTest` (43), `SharingTest` (38), `CalculatorTest` (34), `StylingTest` (34),
`ParagraphsTest` (33), `EditorTypingTest` (32), `BackupTest` (29), `SettingsTest` (29),
`TemplatesTest` (23), `ListingTest` (22), `FindingTest` (20), `EditorTableTest` (16),
`ExportingTest` (16), `StartupTest` (16), `OutgoingTest` (12), `UnknownFieldsTest` (11),
`ThemesTest` (10), `EditorCopyTest` (8), `RemindersTest` (7), `StatsTest` (5),
`LinksTest` (3), `InteropTest` (2) and `TempPlaceTest` (2). This is the one place the count is
kept; the other documents link here. No device, no emulator:
they test the twenty-seven files that contain no Android at all (see
[ARCHITECTURE](../docs/ARCHITECTURE.md#decision-the-rules-layer-knows-nothing-about-android)),
which is what makes them cost seconds instead of minutes — and two
drawing-side classes: `AppState`, whose order of saves around the open
note no emulator tap can time, and the editor's model, whose copy must
carry styling put on after the words were selected, whose typed letters
take styles by the caret they were typed at, whose calculator writes
into the cell being written in, and whose paste of a markdown table
leaves its separator's widths behind — in a note, in a day of a series,
and never in the app's own copy; three suites reach that side for one
piece each (`OutgoingTest` an `AppState`, `NotebookTest` `RichText`'s
drawing of a body, `ImportingTest` the import's result words,
`importMessage`); and, beside `AppState`, `AppStateTest` reaches the
import's finish (`finishImport`), the messages' `MessagesShown` and the
welcome note's `showWelcome`, and the note text shared in from another
app becomes (`sharedAsNote`); `EditorTableTest` the table's
`ActiveCell`; and `EditorTypingTest` the one way a capped box keeps what
is typed (`keptBy`). `TempPlaceTest` checks the run itself:
its temporary folders made under `app/build/`, which each run empties
first, and never left in the system's temp folder (30,640 were, by
2026-10-23).

### Fixtures the desktop wrote

Ten suites are held to what the desktop's own Python wrote: nine to its
answers over cases generated for them, in the eight fixtures below
(`NotebookTest` reads the export's too), and `InteropTest` to a code it
wrote, `desktop_code.txt` (`make_interop_fixture.py`). Each fixture in `app/src/test/resources/` is
written by a tool in `tools/` that imports the desktop's code, runs it,
and never writes to it:

| Fixture | Written by | What the desktop answered |
|---|---|---|
| `recurrence_cases.json` | `make_recurrence_fixture.py` | 300 seeded notes expanded, 400 typed dates read |
| `calculator_cases.json` | `make_calculator_fixture.py` | 661 sums evaluated, 378 values displayed |
| `export_cases.json` | `make_export_fixture.py` | 409 notes exported as `.html` and `.txt`, 818 files |
| `template_cases.json` | `make_template_fixture.py` | 302 templates normalised |
| `days_cases.json` | `make_days_fixture.py` | 108 notes walked through turn-offs, turn-ons, deletes, restores, updates (the same backup twice), a Trash restore, rewritten comments, a day reset after a turn-on, a turn-off over an import's copy, a copy's days taken back by the date that holds them, a start put back, a second turn-off, each step's stored note and its import |
| `renumber_cases.json` | `make_renumber_fixture.py` | 300 bodies renumbered |
| `paste_cases.json` | `make_paste_fixture.py` | 567 HTML fragments read by `html_to_note`, 80 of them the app's own copies with the HTML they were copied as, 118 plain texts rejoined and unmarked, and 86 texts with markdown tables whose separators `forget_widths` spells at the minimum |
| `unknown_cases.json` | `make_unknown_fixture.py` | 307 notes holding fields this version does not know, as `unknown_fields` keeps them: within the bounds, past them, nested, of every kind |

The paste fixture leans on one more file the app itself carries:
`html_entities.json`, the HTML named references and Python's rules for
numeric ones, written by `make_html_entities.py` out of the Python the
desktop is built with.

Run a tool again after the desktop's function changes, and read the
diff of the fixture before believing the new answers. Where the
desktop's lines live in a window method that cannot be imported, the
export tool copies them and fingerprints the original: when the
original changes, the tool stops and says so instead of copying the
past. Every answer in the paste fixture is the desktop's own since its
cb12941 (2026-11-11), the one place the phone had answered differently
on purpose answering alike since, and `PastingTest` fails if a case ever
parts from it.

Every tool here that runs the desktop's code does it under the desktop's
own memory ceiling: it calls the desktop's `tools/guest.py` first
(`tools/desktop_guest.py`), and stops if that file is not there. Point
`NOTEORGANIZER_HOME` at a `git archive` of the desktop's commit, not at a
working tree someone is editing, when the answers must be a commit's.

**The task prints nothing useful when it fails.** The results are XML in
`app/build/test-results/testDebugUnitTest/`, and the count in those files
is the only proof the tests actually ran. `tools/check_test_results.py`
reads them — and refuses any older than the newest Kotlin source, because
a build that fails to compile leaves the last run's results behind, and
it once reported those as a pass
([the story](../docs/ISSUES_LOG.md#a-pass-printed-over-a-build-that-failed)).

**The test classpath's `org.json` is not the phone's.** On the computer a
decimal arrives as a `BigDecimal` and a `JSONObject` forgets its key
order. A test that depends on either is testing the library
([the story](../docs/ISSUES_LOG.md#the-tests-json-library-is-not-the-phones)).

## The house rules

These are not style preferences. Each one is here because ignoring it
produced a wrong answer that was believed for a while.

**Every check is watched failing.** Break the rule on purpose, run the
suite, read the failure, put the code back. A check nobody has seen fail
proves nothing; it is decoration that costs time to run.

**A silent mutant is not a passing one.** Three times here, a mutant
"survived" and the tests had never run at all — the edit missed its
pattern, it changed something other than the behaviour meant, or it
produced Kotlin that would not compile. Gradle printed no test results,
and "no failures" looked exactly like "no tests". Check the XML before
drawing any conclusion. ([the story](../docs/ISSUES_LOG.md#a-silent-mutant-is-not-a-passing-one))

**A mutant runner restores in a `finally`, and reads and writes bytes.**
One exited on an error with a mutated `Notebook.kt` still on disk. And a
runner that reads text and writes it back rewrites every line ending in
the file, so a "restore" that changed nothing leaves the file rewritten
end to end. ([the story](../docs/ISSUES_LOG.md#a-mutant-runner-that-left-the-code-broken))
`tools/mutate.py` keeps both rules by never touching the repository: it
unpacks a commit into a folder of its own and mutates that. Its catalogue,
`tools/mutants.json`, holds the mutants written since the final audit
began that still aim at today's code -- 286 on 8 November 2026, 266 judged
by a unit test and 20 by a part of the walk; 97 more written during the
audit aimed at code that has changed since, and went; and the first ones,
AS1 to AS259, written in its first seven rounds before the catalogue
began at AS260 in the eighth, were never kept (each plan row naming one
says so). The unit ones were
first run all together that day: 257 of 263 as the catalogue said. Of the
six others, two had been re-aimed while it ran; four had lost their test to
a later fix, and were given one or, where two guards now overlap, marked
equivalent with the reason. For the release, on 21 November 2026, all 509
on 0.15.0: the 466 the unit tests judge, 466 of 466 as the catalogue
says (three left alive, each an equivalent with its reason), in about
six hours; and the 43 the walk judges, each built into the app and its
part walked on an emulator started cold, 43 of 43 (one, AS582, alive as
its entry says), in under four. `tools/check_mutant_targets.py`, a gate in CI, fails when a mutant
no longer finds its code, or is left alive without saying why.

**A check that does the app's job proves nothing.** If the check computes
the expected answer the same way the app computes it, it agrees with the
app by construction — including when both are wrong. Compare against the
specification, against a hand-written expectation, or against the other
program.

**Read the file, not the screen.** The screen draws what the program
believes; the file is what it did. A new note once saved with none of its
formatting while the screen showed it correctly.
([the story](../docs/ISSUES_LOG.md#a-new-note-saved-without-its-formatting))

## The gates between the two programs

    py tools/check_note_shape.py
    py tools/check_palettes.py
    py tools/check_interop.py     # after the unit tests; not run in CI

The premise of this whole project is that both programs read and write
the same file, and nothing enforced it: the agreement lives in two
languages in two repositories and drifts in silence — the code compiles,
the tests pass, and the damage shows up on a real notebook that has
travelled. It has already happened twice.

So the gate reads the desktop's Python **as the specification** and
compares six things: that a note here has the same fields the desktop
gives one, that the two places building a new note agree with each other,
that a share code carries what the desktop's codes carry, that an
arriving note — from a code or from a backup — replaces what the desktop
lets one replace, that `docs/DATA_FORMAT.md`'s field list is the
desktop's, and that the two apps know the same fields and keep the same
bounds on the ones they do not (`KNOWN_NOTE_FIELDS`). It reads that source
and never writes to it.

Its first five kinds of drift were each tried against it and turned it
red (the fifth with "pinned" taken out of the document's list). Run it before calling anything about the note format done.

The palette gate does the same for the look: the eight themes, every
role and colour; the category, note and highlight colours, in order; and
the colours the contrast rules pick for words on a fill. A theme that is
"close enough" is how two apps that are meant to be one product start to
look like two.

## What runs in CI

Three workflows, four jobs, answering four different questions: do the
rules still hold ([tests.yml](../.github/workflows/tests.yml)); is the
rules layer still free of Android and the repository still English; do
the two programs still agree about a note and about its colours
([gates.yml](../.github/workflows/gates.yml)); and, on a change to the
words or the test plan (only words run neither of those; the plan runs
the gates too), do the documents still give the plan's own numbers, in English
([docs.yml](../.github/workflows/docs.yml), `tools/check_test_plan.py`).
Each workflow's token may only read this repository (`permissions:
contents: read`): nothing in CI pushes, comments or publishes. The
desktop's code is read with a token of its own, Contents: Read-only on
that one repository. Every action is pinned to the commit its tag named
when it was chosen, the tag beside it; `tools/check_workflows.py`, in the
boundaries job, holds both, and says how to move an action to a newer
release.

The unit-test job does not trust Gradle's exit code alone: it runs
`tools/check_test_results.py` afterwards — with `if: always()`, so it
speaks even when the step before it failed — and the job goes red on zero
tests, on a total below the floor, or on any failure. The results are
kept as an artifact so a failure can be read rather than guessed at.

### Kept cheap on purpose

This is a private repository, so every minute is billed against the
account's free allowance. Four measures, in order of how much they save:

1. **Once per change, not twice.** Pushes are watched only on `main`;
   work on a branch is covered by its pull request. A branch with an open
   PR would otherwise run everything twice, on the push *and* on the PR.
2. **Only when it could tell us something.** The Gradle job runs for
   changes under `app/`, the build files or its own workflow — a
   screenshot cannot break a test. The gates skip documentation.
3. **A new push cancels the run it has already made pointless**
   (`concurrency` with `cancel-in-progress`).
4. **A timeout on every job.** The default is six hours; one hung job
   would eat a fifth of the month's allowance on its own.

Everything runs on Linux runners, which bill at 1× — Windows costs double
a minute and macOS ten times. And `workflow_dispatch` is on all three,
so any can still be run by hand from the Actions tab.

**The one thing CI needs set up by hand.** Both repositories are private,
so the contract job cannot read the desktop app with its own credentials.
It needs a secret named `DESKTOP_REPO_TOKEN`: a fine-grained personal
access token, **Contents: Read-only**, scoped to the desktop repository
alone, added under *Settings → Secrets and variables → Actions*. The job
checks for it first and says exactly this if it is missing, because
"Repository not found" is what an absent token looks like — and what an
**expired** one will look like, months from now, to somebody who has
forgotten a token was ever involved.

## The emulator walk

    py tools/emulator/walk.py <photos folder>

The built app, driven the way a person would drive it — every tap aimed
by the words on the screen, found fresh — and after each step the
notebook the app wrote is read back through `run-as`. 79 parts and about
671 checks (the full run of 21 November 2026, for the release: 668 passed,
every part in `main()`'s order, in eight groups, each on an
emulator started cold and stopped after it, by `walk_groups.py`; see
below why. The 19 that failed were one part's, the walk's own, fixed,
and the part walked again: [the run](WALK_RUNS.md)).
<!-- The desktop's site/build.py reads "N parts and about N checks" and
"N tests across … suites" in this file: keep their words. -->
The parts: a brand-new notebook, a notebook copied in, the
doors to the rest of the phone (share sheet, file picker, browser), a
part for each Phase 2 and Phase 3 feature over a notebook written for
it, a wide window with the phone on its side, screens of every size
(a small phone's, and 7, 10 and 14-inch tablets', set on the emulator),
and the phone changing under an open note (dark mode, larger letters,
turned on its side). The list of parts
is `main()` in `tools/emulator/walk.py`, and what each one checks is its
docstring there; [STATUS](../docs/STATUS.md#an-emulator-walk-that-reads-the-files)
tells what each walks, phase by phase, and [WALK_RUNS.md](WALK_RUNS.md)
holds every full run, the latest first. The
photographs are looked at, not only saved: several bugs the checks
passed over were found in them ([the stories](../docs/ISSUES_LOG.md)).

One part can be run on its own by naming it after the folder —
`py tools/emulator/walk.py <folder> cell_menu` — on the whole walk's road:
the machine's turn, the wait for the emulator, the install of the build
in `app/build`. It is how a mutant built into the app is walked without
the whole hour. A part called from a script of one's own (`import walk`,
`walk.cell_menu(folder)`) takes the turn at its first adb call: taken only
by the whole walk, a part walked that way ran beside whatever else ran,
and took the turn halfway, at the export part (the final audit's ninth
round).

The whole walk can be walked the same way, in groups of parts, each on
an emulator started for it and stopped after it: every part writes the
notebook it walks on, so a part walks the same in a group as in the
whole run. On a laptop of 16 GB, where the emulator takes about 4, one
emulator kept up for the whole walk was lost on two of three runs on 8
and 9 November 2026 (the one between, started cold, walked 621 of 627),
its own process no longer answering after 10 to 40 minutes; the groups,
none longer than 40 minutes, walked all 628.
`py -I tools/emulator/walk_groups.py <folder>` walks it that way: the parts
read from `main()` in its order, ten to a cold emulator stopped after
them, a stuck group walked once more and said. Every adb call it makes has
a time limit: `adb emu kill` sent to an emulator that no longer answers
waits for ever, and held the machine's turn for 13 hours.

It reports a pass only when every part main() names was walked and no
check failed on any try (a check that failed on a try that got stuck is
exit 7, though the retry passed it), and it uses and stops only the
emulator it started (on its own port, naming its own AVD, its process
still running). Review rounds on 12 November 2026 found it able to do
otherwise, one way after another; each way is a scenario in
`py -I tools/emulator/walk_groups_selftest.py`, which fakes the emulator,
adb and the walk, so it starts nothing, and `--mutants` breaks each
promise in a copy to watch a scenario fail. Run both after any change to
the runner, and the scenarios before a full walk.

Every part, however it ends, puts the emulator's settings back (letter
size, bold text, rotation, dark mode, window size: `settle_device`), and
writes `summary.json` beside the photographs: each part's checks passed
and failed, the commit and the APK it walked. A walk that dies still says
how far it went; WALK_RUNS.md's line for a run is read from it.

Two house rules came out of it:

- **Read the screen AND the file.** A setting can reach the file while
  the screen keeps drawing the old one
  ([the story](../docs/ISSUES_LOG.md#a-setting-saved-and-a-switch-that-did-not-move)).
- **Make a change with state in hand.** Turning the phone was
  photographed on the list for weeks, where a rebuilt app looks
  untouched; with a note open, it closed the note
  ([the story](../docs/ISSUES_LOG.md#turning-the-phone-closed-the-note)).

Phase 2 added three more:

- **A node in the tree is not a node in sight.** A title scrolled out of
  its pane is still in the UI dump; only its box, cut to what shows, says
  it is gone ([the story](../docs/ISSUES_LOG.md#what-a-phone-resets-for-free)).
- **Read what a node says as well as what it shows.** A button drawn
  `✕` is said "Close find"
  ([the story](../docs/ISSUES_LOG.md#the-walk-read-half-of-what-the-screen-said)).
- **Do not race the app through adb.** A tap cannot reliably land inside
  the 0.8 seconds before an autosave; an order like "saved before the
  switch" is held by a unit test that records it
  ([the story](../docs/ISSUES_LOG.md#taps-aimed-through-the-keyboard)).
  And on a phone on its side, put the keyboard down before tapping
  anything under it.

Phase 3 added three more:

- **After a phase, walk the whole app.** New screens change the ground
  older parts walk on: a word a new button shares with an old one, a
  sheet grown longer, a keyboard slower to rise. Seek what a step needs,
  find by the words shown, and wait for the state, not for a time
  ([the story](../docs/ISSUES_LOG.md#old-steps-walking-through-new-screens)).
- **When a button cannot be found, press another door to the same
  code.** The selection menu is in no UI tree; Ctrl+C and Ctrl+V go
  through the copy and paste its buttons call, and the menu is
  photographed for a person's eye
  ([the story](../docs/ISSUES_LOG.md#a-menu-the-phones-own-tree-leaves-out)).
- **Read a passing message once, and act at once.** A ten-second
  offer does not wait for three screen dumps
  ([the story](../docs/ISSUES_LOG.md#an-offer-read-after-it-had-gone)).

The first test on a real phone added three more:

- **Check at the phone's own settings, not only the defaults.** Its
  letters at 115% broke a table's words that fitted at 100%; a part now
  draws the table at 115% and 130%
  ([the story](../docs/ISSUES_LOG.md#widths-in-one-unit-letters-in-another)).
- **The release build is walked too, and judged by its artifact.** It
  cannot be read through `run-as`, and a screen cannot tell a paste with
  its formatting from one without; the release gate reads what the
  shrunk code looks for
  ([the story](../docs/ISSUES_LOG.md#a-file-looked-up-by-a-name-the-release-build-changed)).
- **A check that cannot fail is taken out, and its mutant called a
  survivor.** When the emulator lost the condition a fix existed for —
  Gmail's Chat, turned off — no screen or log could tell the fix from
  its absence, and the walk says so in a comment instead
  ([the story](../docs/ISSUES_LOG.md#mail-apps-only-said-a-way-android-17-no-longer-accepts)).

Share added two more:

- **Walk with the phone's own navigation too.** The emulator had
  gestures; the owner's phone has three buttons, and on it every sheet
  showed a white bar and Share's opened half-way under the buttons. A
  part now puts the emulator on three buttons, the app dark on a light
  phone, and reads the bars from the screen's pixels
  ([the story](../docs/ISSUES_LOG.md#a-sheet-dressed-for-the-phone-not-the-app)).
- **Nothing heavy beside a walk.** A build on the same computer made
  every process on the emulator drop frames, and Android called the app
  not responding. A freeze is read from Android's own report before any
  code is changed — that one showed the app idle
  ([the story](../docs/ISSUES_LOG.md#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been)).

The final audit added one more:

- **Read the log of the processes the check is about.** The log is the
  whole phone's: Play services wrote "TransactionTooLargeException" of
  its own during a share, and a check reading every line failed a step
  the app had passed. `logged(pids, *words)` reads only the processes
  named, their pids taken before the act
  ([the story](../docs/ISSUES_LOG.md#a-word-in-the-log-written-by-another-program)).

### The full runs, newest first

The latest is dated [above](#the-emulator-walk); every full run, the
latest among them, with what was new and how it ended, is kept in
[WALK_RUNS.md](WALK_RUNS.md).

### The other emulator scripts

The other scripts in [`tools/emulator/`](../tools/emulator/) each survive
a specific way the emulator lies to a script: tapping by fresh text,
typing a long share code in pieces and verifying what arrived, and
photographing the documentation screens only once the right screen is on
them.

The identity work was walked there in both directions, on the built app
rather than in a test: a note edited on the desktop, typed in as a code,
recognised and applied in place — then edited on the phone, shared back,
and decoded **in the desktop's own Python**.

## The test plan

`NoteOrganizer_Android_TestPlan.xlsx` — 538 cases on 23 November 2026,
across eighteen areas (Reading, Search, Writing, Editing, Organising,
Trash, Backup, Sharing, Settings, The phone, Interop, Look & feel,
Reminders, and since Phase 2 Calendar, Repeating, Tools, Templates and
Wide screens), kept in this repository rather than
the desktop's because the two projects are deliberately separate. Since
2026-10-20 it has the desktop plan's shape — its sixteen columns, a
Summary that counts itself, and a **Merged cases** sheet where a case
folded into another, or written for what the app no longer does (Follow
the phone), is kept with the reason.

Its **"Automation status (Claude)"** column is the point. Each row says
whether unit tests already walk it, whether the emulator drives it, and
whether it needs a person with a real phone in their hand — often two or
three of those, and the honest ones do: a rule proved on the JVM
whose *screen* still has to be looked at. Marking a row as already covered is
what makes a hand check a choice instead of a duty — and the rows that a
script can *never* answer are marked **NEEDS YOUR EYE**, because a script
checks what it was told to check and never notices that a button looks
like a trap.

A row whose behaviour changed keeps its number and says so: cases 11 and
19 were rewritten on 2026-10-15, when the phone took the desktop's answer
to a broken file and started emptying its own Trash, cases 106 and
107 on 2026-10-19, when "Send by email" became Share, and case 55 on
2026-10-21, when Send feedback left and About opened the privacy policy
instead. Phase 2's cases are
58 to 85; two of them carry a question only a person can answer about
the desktop's side of a trip — a day or a template made on the phone, as
the desktop shows it. Phase 3's are 86 to 100, and six of its rows
(93 to 98) need a hand on a real phone for a plain reason: what a web
page or a mail app makes of a paste, a keyboard's Ctrl+Z, a long
formatted page pasted, and the selection menu's own buttons, which the
walk can photograph but not press.

Nothing is ever deleted from the plan. A case that gets automated keeps
its row and gains a note saying so; one folded into another, or about a
feature that is gone, moves to Merged cases with the case that took its
place. A row's Status, Build, Tested By and Test Date say who recorded
its result, on which build: no run of the scripts writes them in by
itself. Until the final audit's end a Pass by Claude stood only where the
scripts walked all of the case, on the build named. Seventy rows Claude had
marked Pass from runs whose scripts walked only part of them went back
to Not Tested in the final audit (fifty-two of 0.9.1, one of 0.10.0,
one of 12 November and sixteen of 13 November), and so did the
twenty-six it had marked Pass on 0.9.1 (one, 150, on 0.9.0) whose
checks, named today, were partly written after that run; each keeps its
run in Actual Result, the automation column no longer names the scripts'
runs of 0.9.1 beside today's checks (a date left there marks a one-off:
a measurement, or a script on her phone), and
the release's runs fill in the rows only scripts reach. On 23 November
2026 the owner chose otherwise: every row the unit tests or the walk
reach, but those that need her eye, is Pass by *Claude (automated, final
audit)* on 0.15.2's source, a pass for what the scripts walk, with what
they leave to the hand named in Actual Result and walked by no one; the
rows she passed by hand keep her name. A row that
needs a person stays Not Tested until she runs it (or a check she
allowed a script to make on her phone walks it, as Tested By then
says), whatever the scripts already cover. The unit tests have no
screen, so a row whose steps are taps and whose checks are unit tests
alone says "Automated in part" and leaves the case's own steps on
screen to the hand. Where setting a case up, or taking its steps, on
purpose takes more than ordinary use or cannot be done (a refused read,
a held file, a full disk, a picker that covers the app), the row says
so in its precondition: most with "Not a hand check" or "Not practical
by hand" (the two mean the same), some by naming the debug build and the
adb steps it takes ("which no ordinary use makes"); and it names what is
left. A backup a script crafts is one a person
can import. A row
called whole reads all of a message it quotes; one whose checks read a
message by some of its words says "in part" and leaves the rest to the
hand. The walk times a screen by its longest frame from the tap on,
which catches work that delays a frame already due, but not work done in
the tap itself, nor work that draws nothing: a row promising how long something
takes leaves that time to the hand (big_table alone times an open from
the tap; code_read_away and long_lines also read Android's "Skipped N
frames", which does catch work in the tap). What an "in part" row names as left is what is known, a guide
rather than a full list: the hand check walks the case whole, and a
part found unnamed is added when found (the owner's rule of 15
November, which grades such an omission low).
