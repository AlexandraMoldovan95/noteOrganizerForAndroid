# How this app is tested

Each layer exists because the one below it cannot see a certain kind of
failure. Read this before adding a check, so the new one
lands where it can actually fail.

| Layer | Where | What only it can catch |
|---|---|---|
| **Unit tests on the JVM** | `app/src/test/` | A rule ported from Python that does not match the Python — held, where the rule can be run on its own, to answers the desktop's own Python wrote |
| **The cross-program gates** | `tools/check_note_shape.py`, `tools/check_palettes.py`; `tools/check_interop.py` after the unit tests (not in CI) | The two programs quietly disagreeing about what a note *is*, or what a theme looks like, or the desktop reading a code made here differently |
| **The release gate** | `tools/check_release_lookups.py`, on the built release APK | Code the shrinker renamed, still looking a file up by its old place |
| **The licenses gate** | `tools/make_licenses.py --check`, on the built release APK | A library the app carries that its Open-source licenses page does not name |
| **The emulator walk and tools** | `tools/emulator/` | A rule that is right and a screen that is wrong |
| **The test plan** | `NoteOrganizer_Android_TestPlan.xlsx` | Whether a tap lands, whether the keyboard covers what you are typing, whether the thing reads well at arm's length |

## The unit tests

    set JAVA_HOME=<Android Studio's folder>\jbr
    gradlew.bat testDebugUnitTest
    py tools\check_test_results.py

988 tests across thirty-one suites on 7 November 2026 — `NotebookTest` (144),
`DaysTest` (75), `TablesTest` (72), `AppStateTest` (68), `RecurrenceTest` (51),
`EditingTest` (49), `PastingTest` (46), `OrganizingTest` (46), `IdentityTest` (40),
`ImportingTest` (35), `StylingTest` (34), `CalculatorTest` (34), `ParagraphsTest` (33),
`EditorTypingTest` (32), `SharingTest` (26), `SettingsTest` (24), `BackupTest` (23),
`TemplatesTest` (21), `ListingTest` (21), `FindingTest` (20), `ExportingTest` (16),
`EditorTableTest` (16), `StartupTest` (15), `OutgoingTest` (12), `ThemesTest` (9),
`EditorCopyTest` (8), `RemindersTest` (7), `StatsTest` (4), `LinksTest` (3),
`InteropTest` (2) and `TempPlaceTest` (2). This is the one place the count is
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
and never in the app's own copy. `TempPlaceTest` checks the run itself:
its temporary folders made under `app/build/`, which each run empties
first, and never left in the system's temp folder (30,640 were, by
2026-10-23).

### Fixtures the desktop wrote

Seven suites are held to answers the desktop's own Python gave, over
cases generated for them. Each fixture in `app/src/test/resources/` is
written by a tool in `tools/` that imports the desktop's code, runs it,
and never writes to it:

| Fixture | Written by | What the desktop answered |
|---|---|---|
| `recurrence_cases.json` | `make_recurrence_fixture.py` | 300 seeded notes expanded, 400 typed dates read |
| `calculator_cases.json` | `make_calculator_fixture.py` | 661 sums evaluated, 378 values displayed |
| `export_cases.json` | `make_export_fixture.py` | 409 notes exported as `.html` and `.txt`, 815 files |
| `template_cases.json` | `make_template_fixture.py` | 302 templates normalised |
| `days_cases.json` | `make_days_fixture.py` | 103 notes walked through turn-offs, turn-ons, deletes, restores, updates (the same backup twice), a Trash restore, rewritten comments, a day reset after a turn-on, a turn-off over an import's copy, a copy's days taken back by the date that holds them, a start put back, a second turn-off, each step's stored note and its import |
| `renumber_cases.json` | `make_renumber_fixture.py` | 300 bodies renumbered |
| `paste_cases.json` | `make_paste_fixture.py` | 567 HTML fragments read by `html_to_note`, 80 of them the app's own copies with the HTML they were copied as, 117 plain texts rejoined and unmarked, and 86 texts with markdown tables whose separators `forget_widths` spells at the minimum |

The paste fixture leans on one more file the app itself carries:
`html_entities.json`, the HTML named references and Python's rules for
numeric ones, written by `make_html_entities.py` out of the Python the
desktop is built with.

Run a tool again after the desktop's function changes, and read the
diff of the fixture before believing the new answers. Where the
desktop's lines live in a window method that cannot be imported, the
export tool copies them and fingerprints the original: when the
original changes, the tool stops and says so instead of copying the
past. Where the phone answers differently on purpose, the paste tool
makes its answers by the smallest change to the desktop's own function,
in its own process, keeps the desktop's answer beside each case it
changes, and stops when the line it changes is gone.

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
began that still aim at today's code -- 281 on 7 November 2026, 263 judged
by a unit test and 18 by a part of the walk; 97 more written during the
audit aimed at code that has changed since, and went. `tools/check_mutant_targets.py`, a gate in CI, fails when a mutant
no longer finds its code.

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
compares five things: that a note here has the same fields the desktop
gives one, that the two places building a new note agree with each other,
that a share code carries what the desktop's codes carry, that an
arriving note — from a code or from a backup — replaces what the desktop
lets one replace, and that `docs/DATA_FORMAT.md`'s field list is the
desktop's. It reads that source and never writes to it.

All five of its own kinds of drift were tried against it and all five
turned it red (the fifth with "pinned" taken out of the document's list). Run it before calling anything about the note format done.

The palette gate does the same for the look: the eight themes, every
role and colour; the category, note and highlight colours, in order; and
the colours the contrast rules pick for words on a fill. A theme that is
"close enough" is how two apps that are meant to be one product start to
look like two.

## What runs in CI

Two workflows, three jobs, answering three different questions: do the
rules still hold ([tests.yml](../.github/workflows/tests.yml)); is the
rules layer still free of Android and the repository still English; and
do the two programs still agree about a note and about its colours
([gates.yml](../.github/workflows/gates.yml)).

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
a minute and macOS ten times. And `workflow_dispatch` is on both, so
either can still be run by hand from the Actions tab.

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
notebook the app wrote is read back through `run-as`. 74 parts and about
626 checks (the full run of 7 November 2026, on a fresh boot: all 626
passed; the run before it failed 2 of 613, both the walk's own, both
causes fixed). The desktop's `site/build.py` reads "N parts and about N checks"
and "N tests across … suites" above, so keep their words. The parts: a brand-new notebook, a notebook copied in, the
doors to the rest of the phone (share sheet, file picker, browser), a
part for each Phase 2 and Phase 3 feature over a notebook written for
it, a wide window with the phone on its side, screens of every size
(a small phone, tablets of 7, 10 and 14 inches), and the phone changing
under an open note (dark mode, larger letters, turned on its side). The list of parts
is `main()` in `tools/emulator/walk.py`, and what each one checks is its
docstring there; [STATUS](../docs/STATUS.md#an-emulator-walk-that-reads-the-files)
tells what each walks, phase by phase, and [below](#the-full-runs-newest-first)
is every full run. The
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

The last is dated [above](#the-emulator-walk). During the final audit
(2026-10-28 to 2026-11-02) the walk ran part by part as each round's
fixes came, and in full between rounds. Before it, each full run, with
what was new and how it ended:

- **2026-10-27, for 0.13.0** — About's Open-source licenses read, and
  Back from them landing on About. An emulator just started; it waited 42
  seconds, then passed all 353.
- **2026-10-27** — the Trash's Select all, two of its entries alike in id
  and moment, and duplicates moved while its window could not be read. An
  emulator already running; 31 seconds; all 351.
- **2026-10-26** — a paragraph pasted under a note's last table, the
  checklist and comments found by a search, a merge down photographed.
  Started cold; 31 seconds; all 343.
- **2026-10-25** — a checklist row dragged at once, a note's sections
  folded and opened, a day marked, cells merged right and down. Started
  cold; 31 seconds; all 340.
- **2026-10-24, evening** — a note's whole text replaced by a new line and
  its words kept for Restore previous. Started cold; 31 seconds; all 333.
- **2026-10-24** — the part for screens of every size, and the walk's own
  files taken out of Downloads again; 31 seconds; all 332. The two before
  it lost six checks to Android's picker refusing a 33rd file of one name
  ([the story](../docs/ISSUES_LOG.md#thirty-two-copies-and-a-save-that-did-nothing)),
  then one to the calendar's row of the day read once, a moment before it
  was drawn; that check, and three more read once after the calendar
  opened, now wait for what they look for.
- **2026-10-23, late** — a markdown table pasted into a note, a template
  added. Just started, alone on the computer; 66 seconds; all 313.
- **2026-10-23, evening** — an empty last line put back to the left under
  a centred one; 69 seconds; all 309.
- **2026-10-23, afternoon** — the centred empty last line, Your name's
  Saved; all 308.
- **2026-10-23, morning** — the day's reminder and the note's ⋮; 72
  seconds; all 305. The two before it, on emulators just started too, lost
  four checks and then two, every one a check whose margin was the
  machine's speed and none the app — Undo tapped after its offer had gone,
  the theme's fade between two captures, the calculator looked for before
  the keyboard was down, the cursor's line looked for before the note had
  scrolled back to it. Each check was changed, not run again until it
  passed ([the story](../docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)).
  Earlier that day, with the checks for what the owner's phone found — the
  screen following the cursor in the text and the comments, the newest
  checklist row, the keyboard rising on its side, the list put aside,
  Settings' list and the month keeping their places — all 291; on the first
  of those fixes, all 278.
- **2026-10-22** — the two parts for what the owner's phone and an agent
  playing an ordinary user found: the checklist's box above the keyboard,
  the note in sight on its side. 106 seconds; all 273. The two before it,
  with fewer of those checks, all 271 and all 269. A run before them, made
  while the computer also built the app and ran the desktop's suite,
  failed a hundred checks: the emulator's own home screen hung under the
  load ("Pixel Launcher isn't responding"). The walk wants the machine to
  itself.
- **The walk's own fixes** — the first run on them, begun the moment an
  emulator had started: 93 seconds, all 259. The one before it, the same
  but for `keyboard_down`, lost two checks in the sheet part to a Back
  pressed off a table cell. Before them, after a new search began to show
  its results from the top, all 259; with deletes across a numbered list
  fixed, all 258.
- **2026-10-21, 0.10.0** — all 258, on an emulator given a minute and a
  half to settle. A run on the same build less the WebView's opt-out lost
  one: a search typed after two blind flings up the list, which stopped
  anywhere from Card 14 to Card 16, so the results opened with "Card 15"
  above the screen. The setup now scrolls until the top card is seen, and
  the part passed six times running.
- **After About gained the privacy policy** — 257, the first run on an
  emulator started a moment before: its first typing never reached the
  editor, so the first part failed in a chain, six of its checks, and the
  dissolve's shades jumped from light to dark between two frames. The
  first three parts, walked again once the emulator was warm, passed every
  check; the other 33 had passed in the full run. The walk now waits for a
  just-started emulator to settle, and watches the dissolve slowed
  fivefold.
- **After Appearance became one Light | Dark switch** — all 256, first
  try. After the list learned to keep its place, all 253.
- **After the desktop's old way was brought over** — all 250, first try.
- **After the sheets were fixed** — all 238 it then had. The parts written
  after it were walked on their own, once a photograph taken on the file's
  word, before the first start after an install had drawn the list,
  learned to wait for the screen.
- **After Share** — 229 passed and two failed: a brand-new notebook read
  three seconds after the first start following an install, on an
  emulator short of memory, before the app had written it. Those reads
  now wait for the file, as every read of the autosave does.
- **After the table's audit** — all 228 it then had, first try. One run
  earlier failed three: two at larger letters, where a cell's style built
  anew had lost the theme's line height, and one Redo read a moment too
  soon, which every read of the autosave now waits out (`settle`).
- **The first run of Phase 3** failed five steps, all the walk's own, in
  older parts the new screens had changed under them
  ([the story](../docs/ISSUES_LOG.md#old-steps-walking-through-new-screens)).

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

`NoteOrganizer_Android_TestPlan.xlsx` — 475 cases on 7 November 2026,
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
the desktop shows it. Phase 3's are 86 to 100, and three of its rows
need a hand on a real phone for a plain reason: what a web page or a
mail app makes of a paste, a keyboard's Ctrl+Z, and the selection menu's
own buttons, which the walk can photograph but not press.

Nothing is ever deleted from the plan. A case that gets automated keeps
its row and gains a note saying so; one folded into another, or about a
feature that is gone, moves to Merged cases with the case that took its
place. Rows only scripts reach are filled in as Pass by them, with the
build and the date of the last run; a row that needs a person stays Not
Tested until she runs it, whatever the scripts already cover.
