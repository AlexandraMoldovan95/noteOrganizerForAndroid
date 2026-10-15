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

The script does four things worth knowing about:

**It writes its own notebook first.** A real notebook is somebody's
private writing, and documentation is the one place it must never end up.
So the app's data is cleared and four notes written *for* the camera go
straight into its own storage — a bread recipe with a checklist, a month
of expenses as a table, an untitled idea, a pinned reading list — chosen
to show a headline, a preview, the badges, a pin and a note with no title
at all. The name and the theme are set the same way, so the first screen
is the list, with no welcome note and no notice in the way.

**It names what must be on screen before the shutter opens** — and, for
the search, what must be *gone*. Each shot carries a short list of words,
the live UI tree is read and checked against it, and a screen that does
not say them is not photographed. A tool that captures whatever happens
to be there is how a picture of the wrong screen ends up in the
documentation looking like proof — which is exactly what nearly happened
here, when a failed tap left the search shot showing the plain list
([the story](../ISSUES_LOG.md#a-photograph-of-the-wrong-screen-filed-as-proof)).

**It quiets the phone's own furniture.** Android's SystemUI demo mode
fixes the clock at 9:30 and the battery at full, so the frame is the app
rather than whatever notification the emulator had pending that
afternoon; the mobile-signal icon is hidden, because demo mode draws it
white whatever the page behind it. It is all turned off again at the
end. The on-screen keyboard is dismissed before every capture — it is
half the screen and none of it is this app.

**The pictures are looked at before they are committed.** Two bugs were
found that way while this set was made: the card printing a date the way
the file stores it rather than the way the desktop shows it
([the story](../ISSUES_LOG.md#the-same-date-written-two-ways)), and that
white signal icon.

## The set

| File | What it shows |
|---|---|
| `01-note-list.png` | The note list in Soft Lavender: the greeting, search, sort and show, and the cards — colour stripe, headline, preview, and the category, date, table and checklist badges. The pinned note floats to the top; the last card has no title, so it borrows its first line, set lighter |
| `02-a-note.png` | A note open in the editor, its bold first line drawn and its checklist below, ticked rows struck through |
| `03-a-table.png` | A table drawn as a grid rather than as the pipe rows it is stored as, with the right-aligned column the separator row asked for |
| `04-search.png` | Search: one of four notes left, matched on a word in its body rather than its title |
| `05-appearance.png` | Settings → Appearance: "Follow the phone", and the eight themes, dark and light |
| `06-dark.png` | The same list in Midnight Purple |

**Current set:** captured 2026-10-15 on the emulator, in English, for
Phase 1. Update this line whenever the set is recaptured.
