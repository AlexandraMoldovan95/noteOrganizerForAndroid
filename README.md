# Note Organizer, on a phone

An Android companion to [Note Organizer](https://github.com/AlexandraMoldovan95/noteOrganizer),
the offline Windows notes app. It reads and writes the **same notes file**
the desktop app writes — same JSON, same bytes, no conversion step and no
server in between.

This is an exploration rather than a product. It exists to answer two
questions honestly: does the desktop app's data format survive the trip
to a phone, and what does a phone-sized version of it actually cost?

---

## What it does today

Open a note and read it with its styling intact. Search the whole
notebook. Write a new note, edit an existing one, tick a checklist, file
it under a category, pin it, colour it, give it a date. Read a table as a
grid and change its cells. Delete to the Trash and restore from it —
nothing is destroyed on the phone, and the phone never empties the Trash,
because only the desktop can also delete a purged note's attachments.

And it can trade notes with the desktop, one note at a time, through a
**share code**: a long line of text you copy from one device and paste
into the other. A code that carries a note the phone already has is
recognised as *that note* — so it asks whether to update the copy here or
keep both, instead of quietly leaving you with two.

What it deliberately does not do is as much a part of the design as what
it does; [docs/STATUS.md](docs/STATUS.md) lists both, and is kept honest
on purpose.

## What it is not

**It is not sync.** Share codes move a note you choose, when you choose.
Two devices editing the same notebook still drift apart, and nothing here
reconciles that. There is no account, no server, no background exchange —
by choice, the same choice the desktop app made.

**A share code is not encryption.** It is text, compressed and encoded so
it survives a chat window. Anyone holding the code can read the note.

## Reading the documentation

| Document | What is in it |
|---|---|
| [docs/DATA_FORMAT.md](docs/DATA_FORMAT.md) | What a note IS, in both programs — and the traps that come with re-implementing the same rules in a second language. Read before touching anything that reads or writes a note. |
| [docs/STATUS.md](docs/STATUS.md) | What works, what was verified and how, what is deliberately unbuilt, and the questions still open. |
| [CLAUDE.md](CLAUDE.md) | How work is done in this folder: the rules, the approvals, and the environment traps that cost a day each. |

The desktop app is the specification. Where a rule here answers "what
should this do?", the answer comes from reading its Python, not from
inventing behaviour — including in the places where the Python is
arguably the worse choice, because a difference between the two is a
surprise rather than a feature.

## Building and running it

Requirements: Android Studio (or a JDK plus the Android SDK) and an
emulator or a phone with USB debugging on. Minimum Android 8.0 (API 26).

`gradlew` needs a JDK and will not start without one — there is no
system-wide Java on this machine, and the only JDK is the one bundled
inside Android Studio:

    set JAVA_HOME=C:\Programs\android_studio\jbr

    gradlew.bat assembleDebug          # build the APK
    gradlew.bat testDebugUnitTest      # 222 JVM unit tests, no device needed
    adb install -r app\build\outputs\apk\debug\app-debug.apk

The test task prints nothing useful when it fails; the results are XML in
`app\build\test-results\testDebugUnitTest\`, and the count in those files
is the only proof the tests actually ran.

The app looks for `notes_data.json` in its own app-private folder on the
device. To try it against a real notebook, copy one in:

    adb push notes_data.json /sdcard/Android/data/com.example.noteorganizer/files/

That copy is yours to break — it is not the desktop's file, and nothing
here ever reaches back to the machine that wrote it.

## The gate that guards the contract

    py tools/check_note_shape.py

This project's whole premise is that both programs read and write the
same file, and nothing enforced that: the agreement lives in two
languages in two repositories, and drifts in silence — the code compiles,
the tests pass, and the damage shows up on a real notebook that has
travelled. It has already happened twice.

So the gate reads the desktop's Python **as the specification** and
compares four things: that a note here has the same fields the desktop
gives one, that the two places building a new note agree with each other,
that a share code carries what the desktop's codes carry, and that an
arriving note replaces what the desktop lets one replace. It reads that
source and never writes to it.

Run it before calling anything about the note format done. Its own four
kinds of drift were tried against it and all four turned it red — a gate
that has never been seen failing is decoration.

## How it is tested

The unit tests run on the JVM without a device, and they pin the rules
that were ported from Python rather than the screens. Every one of them
has been watched failing: the check is broken on purpose, the suite is
run, and the failure is read before the break is reverted. A mutant that
produces no test results at all is not a passing one — that has happened
three times here, for three different reasons, and
[docs/STATUS.md](docs/STATUS.md) keeps the list.

The parts that matter most are not judged by this app at all. A note
captured on the phone is pulled back and checked **in the desktop's own
Python**: every pre-existing note identical field for field, the new note
passing the desktop's `normalize_note` unchanged, every id unique. A
suite proves the code does what it says it does; that proves the two
programs agree about what a note is.
