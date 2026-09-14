# Driving the emulator

Three small tools, kept because each exists to survive a specific way the
emulator lies to a script. The first two were written during the identity
work and lived in a temporary folder until 2026-09-13, which meant the
next session would have written them again; the third came with the
documentation pictures on 2026-09-14.

All three need the SDK's `adb` on the machine (`%LOCALAPPDATA%\Android\
Sdk\platform-tools`), and all three are Windows-flavoured.

| tool | what it does | why it exists |
|---|---|---|
| `tap_by_text.py "Some label"` | dumps the live UI tree, finds the node whose text contains the label, taps its centre | tapping remembered coordinates lands on whatever moved into that spot; the dump is taken FRESH every time |
| `take_screenshots.py` | seeds a notebook written for the camera, walks four screens and photographs each one | the pictures in [docs/screenshots/](../../docs/screenshots/) are remade by one command instead of by hand, and each shot names the words that must be on screen before the shutter opens — a screen that does not say them is not photographed |
| `type_code.py <file> <x> <y>` | taps the field at (x, y), types the file's contents in 100-character pieces, then dumps the UI and compares what arrived against the original | `adb shell input text` truncated a 634-character share code at 181 — silently. A code cut short is indistinguishable from a damaged one, so the typing is verified rather than assumed |

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

## What they are for

The rule is in [CLAUDE.md](../../CLAUDE.md): anything provable
mechanically is proved on the real app before it is called done, and the
judgement calls a script cannot make are named out loud instead. These
are the hands that do the first half.
