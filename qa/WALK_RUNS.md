# The emulator walk's full runs, newest first

A journal kept for the record; [the walk's own section](README.md#the-emulator-walk)
dates the latest run. During the final audit (from 2026-10-28) the walk
ran part by part as each round's fixes came, and in full between rounds;
the full run of 2026-11-07 passed all 626 on a fresh boot, after one that
failed 2 of 613, both the walk's own, both causes fixed. The morning of
2026-11-08, 627 of 628: the one was the walk's own, a check on a phone
on its side that still expected the list to keep room under its cards
for the +, now in the rail; rewritten, its part walked again, all 25
passed.

For the release cut, 0.14.0, on 2026-11-08 and 09, five runs:

- The first lost the emulator after about 155 checks: it stopped
  writing its screen's tree, then crashed.
- The second, started cold, passed 621 of 627. All six failures were the
  walk's own. One title came back typed "GGroceries" and took its part's
  next three checks with it; it was not seen again in 32 tries, and
  whether the keyboard or the app doubled the letter was not proved. Two
  checks read the screen once, a fixed moment after Back or Show, and
  the bar they looked for came a moment later: photographed, it was
  there, and Show opened the note it named. Both now wait for what they
  look for, and so does every check of that kind, about 75 of them; nine
  still read once on purpose (a loop that reads again itself, an
  absence, a bar that must still be up at a given second).
- The third lost the emulator again, after 134 checks, all passed. The
  script that ran it then waited 13 hours on `adb emu kill`.
- The fourth, from the quick-boot snapshot, was stopped after three
  failures: the emulator's System UI hung and a long press was read as a
  tap, on a laptop with under 1 GB free. The app had done each step; the
  welcome note was there in the photograph.
- The fifth walked every part in `main()`'s order in eight groups, each
  on an emulator started cold and stopped after it: all 628 passed, no
  group walked twice.

Before the audit, each full run, with what was new and how it ended:

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
