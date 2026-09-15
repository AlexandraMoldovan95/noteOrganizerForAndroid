# Screenshots

Every image here is **this app running on an emulator** — nothing is a
mockup, a render, or a picture from anywhere else. The launcher icon in
them is not a picture at all: it is drawn from code by
[`tools/make_launcher_icon.py`](../../tools/make_launcher_icon.py), which
asks the desktop app's own drawing for Android's sizes.

## They are taken by a script, not by hand

    py tools/emulator/take_screenshots.py

One command remakes the whole set, which is the point: a set refreshed by
hand is a set that quietly goes half-stale, one picture at a time.

The script does three things worth knowing about:

**It writes its own notebook first.** A real notebook is somebody's
private writing, and documentation is the one place it must never end up.
So the phone is seeded with four notes written *for* the camera — a bread
recipe, a month of expenses as a table, an untitled note, a reading list —
chosen to show a headline, a preview, the badges, a checklist and a note
with no title at all.

**It names what must be on screen before the shutter opens.** Each shot
carries a short list of words, and the live UI tree is read and checked
against it; a screen that does not say them is not photographed. A tool
that captures whatever happens to be there is how a picture of the wrong
screen ends up in the documentation looking like proof — which is exactly
what nearly happened here, when a failed tap left the search shot
showing the plain list.

**It quiets the phone's own furniture.** Android's SystemUI demo mode
fixes the clock at 9:30 and the battery at full, so the frame is the app
rather than whatever notification the emulator had pending that
afternoon; it is turned off again at the end. The on-screen keyboard is
dismissed before every capture — it is half the screen and none of it is
this app.

## The set

| File | What it shows |
|---|---|
| `01-note-list.png` | The note list: colour stripe, headline, preview, and the category, date, table and checklist badges. The third card has no title, so it borrows its first line — set lighter, because a named note must still read as named |
| `02-a-note.png` | A note open with its formatting drawn and its checklist below, ticked rows struck through |
| `03-a-table.png` | A table read as a grid rather than as the pipe rows it is stored as, with the right-aligned column the separator row asked for |
| `04-search.png` | Search: one of four notes left, matched on a word in its body rather than its title |

**Current set:** captured 2026-09-14 on the emulator, in the app's only
theme so far. Update this line whenever the set is recaptured.
