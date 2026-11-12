# androidVersion — Claude Code Instructions

## What this is

The Android companion to **Note Organizer**, in Kotlin and Jetpack
Compose. It began as an exploration — does the desktop's data format
survive the trip to Android? — and since **2026-10-15** it is being built
into a real app, in three phases: what the phone does today, what comes
next and what is deliberately not built are in
[docs/STATUS.md](docs/STATUS.md). The interface is English, as the
desktop's is. No sync: notes move by share code or backup, and an
arriving copy never overwrites a note without the person choosing.

Note Organizer itself is a finished, shipping Windows desktop app —
Python and Tkinter, fully offline; its version is its `APP_VERSION`. It lives at
`D:\Projects\NoteOrganizer` and has **its own `CLAUDE.md` with its own
rules**. Read that file before touching anything there.

### The hard rule

**Never modify the desktop app from this project.** Not its code, not its
docs, not its data files. This project reads the desktop app's *format*;
it is not a second place to edit the app. If something in the desktop app
needs changing, that is a separate piece of work in its own repository,
under its own rules.

The two are deliberately separate repositories. A Gradle/Kotlin build
mixed into a Python desktop repo makes both harder to reason about, and
the contract between them is the data format — which is already stable
and documented.

## The important fact about the desktop app

Its logic and its interface are already separated, and the numbers
matter for anything planned here:

| layer | lines | what it is |
|---|---|---|
| `noteorganizer/core/` | ~10,400 in 18 modules | data and rules — **never imports tkinter** |
| `noteorganizer/ui/` + the entry file | ~37,500 | the desktop interface |

(Measured on 2 November 2026.)

So there is no "port". Four fifths of that app *is* the desktop
interface, and any Android version rewrites the interface whatever the
technology. What is portable is the rules: recurrence, rich text,
tables, the note model, share codes, backups, themes. The rest of
`core/` — filesystem paths, the single-instance lock, the clipboard — is
desktop too. Here the rules are twenty-seven Kotlin files with no Android in
them ([the map](docs/ARCHITECTURE.md#the-map)).

**The Python is the specification.** When a rule is re-implemented here,
the question "what should this do?" is answered by reading the Python,
and `D:\Projects\NoteOrganizer\test_*.py` are its acceptance tests. Do
not invent behaviour that the desktop app already defines.

**And two gates enforce the parts that matter most.** `py
tools/check_note_shape.py` reads that Python and fails when the two
programs stop agreeing about what a note is — its fields, what a share
code carries, what an arriving note may replace — and when the two places
here that build a new note stop agreeing with each other. `py
tools/check_palettes.py` does the same for the themes and colours. Run
them before calling anything about the note format or the look done;
they take seconds. They read the desktop's source and never write to it,
like everything else here.

## The data contract, and where it is written down

Plain JSON, no database, shared byte for byte with the desktop app. The
shape of a note, the formatting spans and their UTF-16 trap, share codes,
tables, recurrence, the write ordering that makes a delete safe, and what
an arriving shared note is allowed to replace all live in one place:

**[docs/DATA_FORMAT.md](docs/DATA_FORMAT.md)** — read it before touching
anything that reads or writes a note.

**[docs/STATUS.md](docs/STATUS.md)** — what works today, what is
deliberately not built, and what was verified how. Update it in the same
change that makes it untrue, not afterwards.

**[README.md](README.md)** — what this project is, for a reader who has
not seen it before.

## Working rules

These match how the desktop project is worked on, and are here so a
session that starts in this folder behaves the same way.

### Ask before acting on anything hard to reverse

Explicit approval is needed **every time**, even if something similar was
approved before: `git push`, merges, creating PRs, force pushes, deleting
branches, rewriting history, installing software, and anything that
writes to the user's real data.

Explain what the command will do in plain words before asking — not just
a summary, the actual effect.

### Measure before believing

The desktop project's hardest-won lesson, and it applies here too: when
something looks like a rendering or performance problem, profile it
before reaching for a fix. Three attempts at hiding a Tkinter flicker
were spent before anyone measured where the time went, and the
measurement pointed straight at the answer.

Related: **re-check what the project says about itself.** Documents drift
and a number nobody re-reads buys false confidence. If a comment or a
document claims a measurement, verify it before acting on it.

### Verify on the artifact

Two real bugs in one day were invisible in the source and obvious the
moment the built app was photographed. Reading the code is not the same
as running the thing.

**Who verifies what (agreed 2026-09-13, and it holds in both projects).**
On the desktop, four user-visible bugs shipped that its suite could not
reach — each needed a restart, a sequence, a second visit, or what
happens on the save *after* the change. The division that came out of it:

- **Anything provable mechanically is proved before saying it is done.**
  Here that means the emulator, not a unit test: install the APK, drive
  it (`tools/emulator/`), pull the written file back and check it **in
  the desktop's own Python**. A suite proves the code does what it says;
  that proves the two programs agree about what a note is.
- **What needs human perspective is named out loud** — whether a screen
  reads as confusing, whether the safer answer is the one the eye lands
  on. A script checks what it was told to check; it never notices that a
  button *looks* like a trap. Those go to her, and into this project's own
  test plan (`qa/NoteOrganizer_Android_TestPlan.xlsx`), marked NEEDS YOUR
  EYE.

### The commands before a push

None of them needs a device, and each one exists because something got
past the others:

    gradlew.bat testDebugUnitTest      # then:
    py tools/check_test_results.py     # the XML, refused if older than the code
    py tools/check_interop.py          # the desktop reads a code made here
    py tools/check_layering.py         # Android has not leaked into the rules
    py tools/check_note_shape.py       # the two programs agree on a note
    py tools/check_palettes.py         # ... and on the themes and colours
    py tools/check_english.py          # the repository is English
    py tools/check_comments.py         # comments say why; stories go to the issues log
    py tools/check_mutant_targets.py   # every mutant finds its code, and one left alive says why
    py tools/check_workflows.py        # CI's token only reads; every action is pinned to a commit
    py tools/check_test_plan.py        # the test plan's count in qa/README is the plan's

And for a change anybody will see, the walk on the emulator
(`py tools/emulator/walk.py <folder>`), with its photographs looked at.

Before a build goes to a phone, the release build itself — the one
people run, shrunk and renamed by R8, which the debug build never is:

    gradlew.bat assembleRelease
    py tools/check_release_lookups.py  # every file the code looks up is where the renamed code looks
    py tools/make_licenses.py --check  # the licenses page names every library the APK carries

A phone is never given a test-only build that needs an uninstall to
replace: an uninstall deletes the notebook. The release build is signed
with the debug key until the upload key exists, so the two install over
each other.

CI runs the first list, all but `check_interop.py`, which needs the
tests' output beside the desktop's code
([tests.yml](.github/workflows/tests.yml),
[gates.yml](.github/workflows/gates.yml) and, for the test plan,
[docs.yml](.github/workflows/docs.yml)),
which is a safety net rather than a substitute: finding out on GitHub what
a five-second command would have said on this machine wastes the round
trip, and the contract gate needs a token in CI that it does not need here.

### Every picture in this repo is ours, and the icon is drawn from code

The launcher icon used to be **Android Studio's template** — the green
robot in a circle — and this section used to say so, because claiming
authorship of a template is worse than having no icon at all. On
**2026-09-14** it was replaced.

`py tools/make_launcher_icon.py` draws it with the desktop's own
`tools/make_icon.py` ([how](README.md#the-icon-is-the-desktops-icon)).

What it produces: `mipmap-*/ic_launcher*.webp` at five densities for
older devices, plus the two adaptive layers and the `mipmap-anydpi-v26`
XML for Android 8 and later. The mark is drawn at 72/108 ÷ √2, 47% of
the canvas, and centred: the launcher keeps the middle 72 of 108 units and
then masks that into a circle, and our mark is a square with a border, so
only a square of 72/√2 keeps its corners whole
([the story](docs/ISSUES_LOG.md#the-preview-that-flattered-the-icon)).

Two things that cost a run each, both written into the script: the
design draws no tile, so **its corner pixel is transparent** and reading
the background colour from there produced pure black under a dark navy
icon; and the flat `.webp` files are what old launchers use, so they are
the icon as-is rather than the cropped adaptive foreground.

Screenshots of the app are captures of it running on the emulator, made
by `tools/emulator/take_screenshots.py` on a notebook written for the
camera ([how](docs/screenshots/README.md)). Say so; nothing here comes
from anywhere else.

### The emulator runs without a window

Start it with `-no-window`, and photograph the emulator's screen only,
never the computer's ([how](tools/emulator/README.md#the-five-tools)).

### One heavy job at a time, and nothing left running after it

The computer this is built on has 16 GB of memory, often a quarter of
it free, and on 2026-10-29 one runaway test run took it down (the
desktop's Issues Log, "The test that took the computer down"). So one
heavy job runs at a time -- a Gradle build or test run, a mutant run, the
emulator and a walk on it, the desktop's suite -- never two at once. And
nothing heavy outlives its job:

- **Gradle leaves no process behind.** No daemon between builds
  (`org.gradle.daemon=false` in `gradle.properties`, and `--no-daemon` on
  the command line), and the Kotlin compiler run inside the build
  (`kotlin.compiler.execution.strategy=in-process`), not as a daemon of its
  own that outlived every build with its memory.
- **The emulator is stopped after a walk**, and every adb call has a time
  limit ([the emulator's side](tools/emulator/README.md#one-heavy-job-at-a-time-and-the-emulator-stopped-after-it)).
- **The desktop's code runs under the desktop's memory ceiling.** The
  fixture makers import its core into their own process; they call its
  `tools/guest.py` first (`tools/desktop_guest.py`), and stop if it is
  missing.

### Plain language

The person working here is learning as she goes and asked for plain
explanations, explicit approvals, and questions rather than assumptions.
Explain *why*, not just *what*. If two readings of a request are
possible, ask.

### The repository talks about the work, and about nothing else

Code, commits, PRs, issues and documentation describe what the software
does, how it behaves and why it was built that way. They never describe
the author's circumstances, plans or reasons for building it. Anything
written for a reader is framed as readability: what the next person needs
in order to understand this code. Carried over from the desktop project.

### A commit's subject names the part of the app, then what changed

The owner's rule, 2026-11-07, the same as the desktop's: `area: what
changed`, in plain words, for example `snackbar: name the note when the
list says it`, read from `git log --oneline` by someone who never saw the
work. A review panel found subjects such as "a word names its note as it
is when said" unreadable there. The body tells the story; the house words
below belong in the body and the issues log, not in a subject. Earlier
history stays as it is.

#### House words

The issues log, the commit bodies and the agent's reports use a few words
in a sense of their own, the same as the desktop's:

| Word | Means |
|---|---|
| bar (phone), banner (desktop) | the short message at the foot of the screen, or near the top of the desktop's window, often with one button: Undo, Show |
| word | what a bar or banner says, as in "the list's word about a hidden note" |
| road | one way a person can reach a piece of code: the same fix asked again "by every road" |
| round | one pass of reviewer agents over the app, after which their findings are fixed |
| wave | a group of rounds with one aim (lost data, an ordinary person's week, AI slop, the review panel) |
| the lows | findings of low severity, fixed in a wave of their own |
| mutant | a fix broken on purpose, to watch the check that guards it fail |
| the walk | the scripted run of the built app on an emulator (`tools/emulator/walk.py`) |
| a look | one reading of the screen during the walk |

### No heredocs for code containing escapes

Corrupted files five or more times in the desktop project. Write the file
with a tool, parse it before believing it.

### Four traps on this machine, told where the emulator is driven

PowerShell's `>` damages any binary it carries, Git Bash rewrites
Android paths into Windows ones, a fresh emulator's stylus tutorial
eats typed text, and a launcher hung into the quick-boot snapshot hangs
at every start. Each, with its commands, is in
[tools/emulator/README.md](tools/emulator/README.md#four-traps-these-tools-do-not-remove).

### An emulator just started is busy for a minute and more

`sys.boot_completed` says 1 about 30 seconds after a start, and the
processor stays fully busy until about 100, with a quiet gap in between
that looks like the end. A walk begun then lost its first typing and
read a 320 ms fade as a cut. `walk.wait_for_boot()` waits for 30 quiet
seconds running; a script of its own should call it, or wait as long
([the story](docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)).

### The Windows console kills a script before it acts

`py` writes to a cp1252 console here, so printing a `▦` or a `‹` raises
`UnicodeEncodeError` — and it does so *before* whatever the script was
about to do. One helper printed what it had found and died there, so the
tap it was going to send never happened and the run looked like the app
had ignored it. Put `sys.stdout.reconfigure(encoding="utf-8")` at the top
of any helper that prints text from the app, rather than relying on
remembering `PYTHONIOENCODING` at every call site.

### Where a placeholder is drawn decides whether a script can see it

A hint drawn as a sibling *behind* `BasicTextField` (a `Box` with the
hint and the field in it) does not reach `uiautomator dump`: a script
looking for the field by its placeholder finds nothing and reports the
wrong screen. The same hint passed through the field's own
`decorationBox` **does** appear. Both draw identically; only one is
visible to a test. Prefer `decorationBox`, and when anchoring on
something else, anchor on a real neighbour — an icon, a label.

### org.json exists on Android and is a stub on the computer

The Android jar the JVM unit tests compile against contains signatures
only: every call throws `Method ... not mocked`. Ten tests failed for
this and none of them was about the code. `testImplementation` a real
implementation, test scope only — the app keeps using the platform's.

The same rule applies to `android.graphics.Color.parseColor` and
everything else in `android.*`: if a piece of logic is worth testing,
write it in pure Kotlin so it can be.
