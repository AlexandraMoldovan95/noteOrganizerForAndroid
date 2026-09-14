# androidVersion — Claude Code Instructions

## What this is

An exploration, not a product yet: a Kotlin + Jetpack Compose project used
to find out what an Android companion to **Note Organizer** would look
like. Nothing here is decided. The first question being answered is
whether the desktop app's data format survives the trip to a phone, and
what it would cost to work in Compose.

Note Organizer itself is a finished, shipping Windows desktop app —
Python and Tkinter, fully offline, at version `0.73.165`. It lives at
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
| `noteorganizer/core/` | ~5,250 in 14 modules | data and rules — **never imports tkinter** |
| `noteorganizer/ui/` + the entry file | ~18,600 | the desktop interface |

So there is no "port". Four fifths of that app *is* the desktop
interface, and any Android version rewrites the interface whatever the
technology. What is portable is ~4,500 lines of rules: recurrence, rich
text, tables, the note model, share codes. The rest of `core/` —
filesystem paths, the single-instance lock, the clipboard — is desktop
too.

**The Python is the specification.** When a rule is re-implemented here,
the question "what should this do?" is answered by reading the Python,
and `D:\Projects\NoteOrganizer\test_*.py` are its acceptance tests. Do
not invent behaviour that the desktop app already defines.

**And one gate now enforces the part that matters most.** `py
tools/check_note_shape.py` reads that Python and fails when the two
programs stop agreeing about what a note is — its fields, what a share
code carries, what an arriving note may replace — and when the two places
here that build a new note stop agreeing with each other. Run it before
calling anything about the note format done; it is seconds. It reads the
desktop's source and never writes to it, like everything else here.

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
  button *looks* like a trap. Those go to her, and into the desktop
  project's test plan.

### Every picture in this repo is ours, and the icon is drawn from code

The launcher icon used to be **Android Studio's template** — the green
robot in a circle — and this section used to say so, because claiming
authorship of a template is worse than having no icon at all. On
**2026-09-15** it was replaced.

`py tools/make_launcher_icon.py` draws it, by importing the DESKTOP app's
`tools/make_icon.py` and asking for the same drawing at Android's sizes.
Not a copy of it, and not a second icon that merely rhymes: the same
code, so the two programs are one product rather than two that look
related. It reads that file and never writes to it, like everything else
here that touches the desktop.

What it produces: `mipmap-*/ic_launcher*.webp` at five densities for
older phones, plus the two adaptive layers and the `mipmap-anydpi-v26`
XML for Android 8 and later. The foreground is drawn at 66% and centred
because the launcher crops to the middle 72 of 108 units — our mark is a
square with a border, and a circle mask eats the corners of anything
larger.

Two things that cost a run each, both written into the script: the
design draws no tile, so **its corner pixel is transparent** and reading
the background colour from there produced pure black under a dark navy
icon; and the flat `.webp` files are what old launchers use, so they are
the icon as-is rather than the cropped adaptive foreground.

Screenshots of the app, when this project has any, are captures of it
running on the emulator. Say so; nothing here comes from anywhere else.

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

### No heredocs for code containing escapes

Corrupted files five or more times in the desktop project. Write the file
with a tool, parse it before believing it.

### PowerShell redirection writes TEXT, never binary

`adb exec-out screencap -p > shot.png` produces a corrupt file on
Windows: PowerShell's `>` is a text pipeline, so it prepends a UTF-8 BOM
and rewrites line endings inside the PNG. The image opens as garbage and
the failure looks like a broken screenshot rather than a broken pipe.

Take the picture on the device and copy the finished file instead:

    adb shell screencap -p /sdcard/shot.png
    adb pull /sdcard/shot.png .

Same trap for any binary a tool writes to stdout — an APK, a zip, a
`logcat -b all --dump` in binary form. If the bytes must cross
PowerShell, they arrive damaged; let the tool write its own file.

### Git Bash rewrites Android paths into Windows paths

`adb push file /sdcard/Android/data/...` from Git Bash silently becomes
`C:/Programs/Git/Git/sdcard/Android/data/...`, and the push either fails
with `secure_mkdirs()` or lands somewhere nobody looks. MSYS converts any
argument that looks like a Unix path. Export `MSYS_NO_PATHCONV=1` for the
whole script — every `adb shell`, `push` and `pull` in it needs it.

### The emulator's stylus tutorial eats typed text

A fresh emulator opens a full-screen *"Try out your stylus"* panel the
first time a text field takes focus. It sits over the app and swallows
`adb shell input text`, so the words land in the tutorial's own box while
the app's field stays empty — and the app looks broken. Dismiss it once
and turn it off:

    adb shell settings put secure stylus_handwriting_enabled 0
    adb shell settings put secure stylus_handwriting_default_value 0

The general lesson: when scripted input seems ignored, photograph the
screen before debugging the code. The first look answered it.

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

### org.json exists on the phone and is a stub on the computer

The Android jar the JVM unit tests compile against contains signatures
only: every call throws `Method ... not mocked`. Ten tests failed for
this and none of them was about the code. `testImplementation` a real
implementation, test scope only — the app keeps using the platform's.

The same rule applies to `android.graphics.Color.parseColor` and
everything else in `android.*`: if a piece of logic is worth testing,
write it in pure Kotlin so it can be.
