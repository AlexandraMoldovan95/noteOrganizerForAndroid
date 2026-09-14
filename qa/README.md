# How this app is tested

Four layers, and each one exists because the layer below it cannot see a
certain kind of failure. Read this before adding a check, so the new one
lands where it can actually fail.

| Layer | Where | What only it can catch |
|---|---|---|
| **Unit tests on the JVM** | `app/src/test/` | A rule ported from Python that does not match the Python |
| **The cross-program gate** | `tools/check_note_shape.py` | The two programs quietly disagreeing about what a note *is* |
| **The emulator tools** | `tools/emulator/` | A rule that is right and a screen that is wrong |
| **The test plan** | `NoteOrganizer_Android_TestPlan.xlsx` | Whether a tap lands, whether the keyboard covers what you are typing, whether the thing reads well at arm's length |

## The unit tests

    set JAVA_HOME=C:\Programs\android_studio\jbr
    gradlew.bat testDebugUnitTest

221 checks across six suites — `NotebookTest` (110), `TablesTest` (47),
`StylingTest` (23), `IdentityTest` (20), `SharingTest` (19) and
`InteropTest` (2). No device, no emulator: they test the four files that
contain no Android at all (see
[ARCHITECTURE](../docs/ARCHITECTURE.md#decision-the-rules-layer-knows-nothing-about-android)),
which is what makes them cost seconds instead of minutes.

**The task prints nothing useful when it fails.** The results are XML in
`app/build/test-results/testDebugUnitTest/`, and the count in those files
is the only proof the tests actually ran.

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

**A check that does the app's job proves nothing.** If the check computes
the expected answer the same way the app computes it, it agrees with the
app by construction — including when both are wrong. Compare against the
specification, against a hand-written expectation, or against the other
program.

**Read the file, not the screen.** The screen draws what the program
believes; the file is what it did. A new note once saved with none of its
formatting while the screen showed it correctly.
([the story](../docs/ISSUES_LOG.md#a-new-note-saved-without-its-formatting))

## The gate between the two programs

    py tools/check_note_shape.py

The premise of this whole project is that both programs read and write
the same file, and nothing enforced it: the agreement lives in two
languages in two repositories and drifts in silence — the code compiles,
the tests pass, and the damage shows up on a real notebook that has
travelled. It has already happened twice.

So the gate reads the desktop's Python **as the specification** and
compares four things: that a note here has the same fields the desktop
gives one, that the two places building a new note agree with each other,
that a share code carries what the desktop's codes carry, and that an
arriving note replaces what the desktop lets one replace. It reads that
source and never writes to it.

All four of its own kinds of drift were tried against it and all four
turned it red. Run it before calling anything about the note format done.

## What runs on every push

[.github/workflows/tests.yml](../.github/workflows/tests.yml) — three
jobs, deliberately separate because they answer three different
questions: do the rules still hold, is the rules layer still free of
Android, and do the two programs still agree.

The unit-test job does not trust Gradle's exit code alone: it runs
`tools/check_test_results.py` afterwards — with `if: always()`, so it
speaks even when the step before it failed — and the job goes red on zero
tests, on a total below the floor, or on any failure. The results are
kept as an artifact so a failure can be read rather than guessed at.

**The one thing CI needs set up by hand.** Both repositories are private,
so the contract job cannot read the desktop app with its own credentials.
It needs a secret named `DESKTOP_REPO_TOKEN`: a fine-grained personal
access token, **Contents: Read-only**, scoped to the desktop repository
alone, added under *Settings → Secrets and variables → Actions*. The job
checks for it first and says exactly this if it is missing, because
"Repository not found" is what an absent token looks like and it sends
the next person hunting for a repository that never moved.

## The emulator tools

[`tools/emulator/`](../tools/emulator/) — three scripts, each written to
survive a specific way the emulator lies to a script: tapping by fresh
text instead of by remembered coordinates, typing a long share code in
pieces and verifying what arrived, and photographing the documentation
screens after checking that the right screen is on them.

The identity work was walked there in both directions, on the built app
rather than in a test: a note edited on the desktop, typed in as a code,
recognised and applied in place — then edited on the phone, shared back,
and decoded **in the desktop's own Python**.

## The test plan

`NoteOrganizer_Android_TestPlan.xlsx` — 35 cases across nine areas
(Reading, Search, Writing, Organising, Trash, Sharing, The phone,
Interop, Look & feel), kept in this repository rather than the desktop's
because the two projects are deliberately separate.

Its **"Who runs it"** column is the point. 21 rows are already walked by
unit tests, 12 are driven on the emulator, and 12 need a person with a
real phone in their hand — the three add up to more than 35 because a row
can carry two, and the honest ones do: a rule proved on the JVM whose
*screen* still has to be looked at. Marking a row as already covered is what makes
a hand check a choice instead of a duty — and the rows that a script can
*never* answer are marked **NEEDS YOUR EYE**, because a script checks
what it was told to check and never notices that a button looks like a
trap.

Nothing is ever deleted from the plan. A case that gets automated keeps
its row and gains a note saying so.
