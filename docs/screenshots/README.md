# Screenshots

Every image here is **this app running on an emulator** — nothing is a
mockup, a render, or a picture from anywhere else. The launcher icon in
them is not a picture at all: it is drawn from code by
[`tools/make_launcher_icon.py`](../../tools/make_launcher_icon.py), which
asks the desktop app's own drawing for Android's sizes.

## They are taken by a script, not by hand

    py tools/emulator/take_screenshots.py          the stills, both themes
    py tools/emulator/take_screenshots.py --gif    demo.gif, the README's moving picture

One command remakes the whole set, which is the point: a set refreshed by
hand is a set that quietly goes half-stale, one picture at a time. The
GIF is ten frames of the same kind, each taken only when the screen says
what it must; a note is written in it, its keyboard left in those frames
because that is what a person writing sees, and the note is read back
from the file before the GIF is written.

The script does four things worth knowing about:

**It writes its own notebook first.** A real notebook is somebody's
private writing, and documentation is the one place it must never end up.
So the app's data is cleared and four notes written *for* the camera go
straight into its own storage — a bread recipe with a checklist, a month
of expenses as a table, an untitled idea, a pinned reading list that
repeats every Monday and Thursday — chosen to show a headline, a
preview, the badges, a pin, a series and a note with no title at all.
The name, the theme and a painted day for the calendar are set the same
way, so the first screen is the list, with no welcome note and no notice
in the way.

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
| `01-note-list.png` | The note list in Soft Lavender: the greeting, search, sort and show, and the cards — colour stripe, headline, preview, and the category, date, table, checklist and repeat badges. The pinned note floats to the top; the last card has no title, so it borrows its first line, set lighter |
| `02-a-note.png` | A note open in the editor, its bold first line drawn and its checklist below, ticked rows struck through |
| `03-a-table.png` | A table drawn as a grid rather than as the pipe rows it is stored as, its figures right-aligned, as the spaces in those rows say |
| `04-search.png` | Search: one of four notes left, matched on a word in its body rather than its title |
| `05-appearance.png` | Settings → Appearance: the Light \| Dark switch, and the four light themes |
| `06-dark.png` | The same list in Midnight Purple |
| `07-calendar.png` | The calendar: a series' Mondays and Thursdays as dots, a dated note, and a birthday painted with its marker |
| `08-wide.png` | The phone on its side: the rail at the left, the list, and the note open beside it, its card outlined |
| `09-wide-calendar.png` | On its side: the calendar, the note's day chosen in the month and the series' days marked, the note still open beside it |
| `10-wide-settings.png` | On its side: Settings with Categories open across the width, each category's colour and how many notes it holds |
| `11-empty.png` | What a person meets before writing anything: the greeting, search, and "No notes yet. Tap + to write your first." over an empty list |
| `01-note-list-dark.png` | The note list in Midnight Purple: the greeting, search, sort and show, and the same cards with their stripes and badges, the pinned note at the top |
| `02-a-note-dark.png` | The bread note open in Midnight Purple, its bold first line and its checklist below, ticked rows struck through |
| `03-a-table-dark.png` | The expenses table in Midnight Purple, drawn as a grid, its figures right-aligned |
| `04-search-dark.png` | Search in Midnight Purple: one of four notes left, matched on a word in its body |
| `05-appearance-dark.png` | Settings → Appearance in a dark theme: the Light \| Dark switch, and the four dark themes |
| `07-calendar-dark.png` | The calendar in Midnight Purple: the series' Mondays and Thursdays as dots, a dated note, and the painted birthday |
| `08-wide-dark.png` | On its side in Midnight Purple: the rail, the list, and the note open beside it |
| `09-wide-calendar-dark.png` | On its side in Midnight Purple: the calendar, the note's day chosen in the month and the series' days marked, the note open beside it |
| `10-wide-settings-dark.png` | On its side in Midnight Purple: Settings with Categories open, each category's colour and how many notes it holds |
| `11-empty-dark.png` | The empty list in Midnight Purple, saying how to begin |
| `demo.gif` | The README's moving picture: the list, the bread note and its checklist, the expenses table, a note written on the phone's keyboard ("Weekend trip"), the list with it, the month on the calendar, and the list in Midnight Purple |

The files ending in `-dark` are the same screens taken again in Midnight
Purple, by the same run, after the light ones: the website shows every
picture in the theme its page is in, and a dark page showing a light
phone read as a mistake (the owner, 2026-10-24). `06-dark.png` stays as
the set's one dark picture where only one set is shown, as in the
README's gallery.

The calendar opens on the month it is run in, so the seed's dates are
worked out from that day: a month photographed later with an earlier
month's notes in it would be an empty grid.

**Current set:** captured 2026-11-12 on the emulator, in English, for
0.14.1, the light set and the dark one in one run, with the empty list
new in it (the review panel's ask). The notebook marks the
day's reminder as already shown, so a day the series falls on does not
put its bar over the list; the table's seed is written as the app writes
a table, its figures right-aligned by the spaces before them (a markdown
`---:` under them read as a column set by hand to four characters, and
the header was drawn "How…" — the 2026-10-23 retake). Against that
day's light set, these differ by the date the calendar opens on and the
dates worked out from it. `demo.gif` was made the same day for 0.14.2
(the review panel's ask). Update this line whenever the set is
recaptured.
