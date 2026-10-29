# Driving the emulator

Small scripts that use the Android emulator the way a person would — tap
a button, type a share code, photograph a screen — so a change can be
proved on the running app rather than only in a test.

The rule they serve is in [CLAUDE.md](../../CLAUDE.md): anything provable
mechanically is proved on the real app before it is called done, and the
judgement calls a script cannot make are named out loud instead. These
are the hands that do the first half.

## The five tools

Each one is kept because it survives a specific way the emulator lies to
a script. The first two were written during the identity work and lived
in a temporary folder until 2026-09-13, which meant the next session
would have written them again; the third came with the documentation
pictures on 2026-09-14; the walk came with Phase 1, on 2026-10-15; and
`emulator_only.py`, the door the other four send adb through, on
2026-10-16.

| Tool | What it does | Why it exists |
|---|---|---|
| `walk.py <folder>` | waits for an emulator just started to settle — it is busy for a minute and more after it says it has started ([why](../../docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)) — installs the debug build, starts from nothing (`pm clear`) and walks the app the way a person would — 353 checks in 42 parts, upright and on its side, at larger letters and on screens from a small phone's to a 14-inch tablet's, with a keyboard's copy and paste keys where the selection menu cannot be pressed — reading the notebook the app wrote through `run-as` after each step, and photographing the screens. One part can be run on its own by importing `walk` and calling it | a suite proves the code does what it says; this proves the built app does it on a phone, on the screen **and** in the file. What it checks: [qa/README.md](../../qa/README.md#the-emulator-walk) |
| `emulator_only.py` | the one door every script here sends adb through: it picks the running emulator, honours `ANDROID_SERIAL` only when that names an emulator, and refuses a real phone outright (an emulator says `ro.kernel.qemu` or `ro.boot.qemu` is 1; a phone says 0 or nothing) | these scripts clear the app's data, write notebooks into it and install builds with mutations in them; with a person's own phone plugged in beside the emulator, a script that picked "the device" could do all of that to their notes |
| `tap_by_text.py "Some label"` | dumps the live UI tree, finds the node whose text contains the label, taps its centre | tapping remembered coordinates lands on whatever moved into that spot; the dump is taken FRESH every time |
| `take_screenshots.py` | writes a notebook made for the camera straight into the app's own storage, walks ten screens — three of them on its side — and photographs each one, then the same screens again in a dark theme (`NN-name-dark.png`) | the pictures in [docs/screenshots/](../../docs/screenshots/) are remade by one command instead of by hand, and each shot names the words that must be on screen — and those that must be gone — before the shutter opens; a screen that does not say them is not photographed |
| `type_code.py <file> <x> <y>` | taps the field at (x, y), types the file's contents in 100-character pieces, then dumps the UI and compares what arrived against the original | `adb shell input text` truncated a 634-character share code at 181 — silently. A code cut short is indistinguishable from a damaged one, so the typing is verified rather than assumed |

All five need the SDK's `adb` on the machine
(`%LOCALAPPDATA%\Android\Sdk\platform-tools`), and all five are
Windows-flavoured. They write only inside the app's own storage on the
emulator, never anywhere a real notebook lives.

**Start the emulator without a window.** `emulator -avd <name> -no-window`
runs it headless: the scripts see and photograph its screen through
`adb`, and nothing appears on — or rearranges — the desktop of the
machine they run on.

## One heavy job at a time, and the emulator stopped after it

The emulator is a heavy job: two gigabytes and more of the computer's
memory while it runs. So it runs alone — never beside a Gradle build, a
mutant run or the desktop's suite — and it is stopped as soon as the walk
is done, photographs looked at or not:

    adb emu kill

Every adb call these scripts make has a time limit (`emulator_only.run`:
two minutes a call, ten for an install). An emulator that stops answering
— frozen, or starved by a computer out of memory — stopped a script on a
call that never returned, the emulator still up and holding its memory.
Past the limit the script stops, naming the call and saying to stop the
emulator first ([why](../../CLAUDE.md#one-heavy-job-at-a-time-and-nothing-left-running-after-it)).

## Before the first run on a fresh emulator

A new Android image opens a full-screen *"Try out your stylus"* panel the
first time a text field takes focus, and it swallows `adb shell input
text` — the words land in the tutorial and the app looks broken:

    adb shell settings put secure stylus_handwriting_enabled 0
    adb shell settings put secure stylus_handwriting_default_value 0

## Two traps these tools do not remove

**Git Bash rewrites Android paths.** `adb push file /sdcard/...` becomes
`C:/Programs/Git/Git/sdcard/...` unless `MSYS_NO_PATHCONV=1` is exported
for the whole script.

**Take a picture on the device, not through PowerShell.** `adb exec-out
screencap -p > shot.png` corrupts the file, because `>` is a text
pipeline. Use `adb shell screencap -p /sdcard/shot.png` and pull it.

## Three things the UI dump does not say, and two the keyboard does

**A node scrolled out of sight is still in the tree.** Only its box, cut
to what shows, says it is hidden — a title scrolled above its pane was
in the dump at 11 pixels tall. Check a box, not a count, when "seen"
matters.

**A button may say one thing and show another.** `✕` is said "Close
find": read a node's description as well as its text.

**On a phone on its side the keyboard covers most of the screen.**
Positions read before it rose land on its keys after; put it down
before tapping what was under it.

And one thing the keyboard does to what is typed: in a field that
capitalises sentences, `input text` sometimes arrives with a capital at
the start and sometimes not, run to run. Start such text with its
capital already, or compare in lower case.

And one thing it says late: `mInputShown` in `dumpsys input_method`
stays true for a moment after Back has put the keyboard down, over a
second on an emulator started that day. Ask until it says false before
asking again, or the second Back leaves the screen
([the story](../../docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)).

Each of these, and the other environment traps met on this machine, is
told in full in [CLAUDE.md](../../CLAUDE.md).
