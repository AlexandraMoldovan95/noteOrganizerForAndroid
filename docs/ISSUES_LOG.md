# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Fourteen of the forty-two are
about the checking.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
| [2026-10-19](#a-cells-words-held-by-its-field-after-the-cell-changed) | code | A cell written in place kept the words it had before Undo, and before a row was put over it | A new copy is a new holder, asked the old question the day it is made |
| [2026-10-19](#two-deletes-the-desktop-had-already-settled) | code | Deleting a table's header row, or a column of two, stopped the table being a table; a new table's empty cells were slivers | Port a function's guards, not only its name and main path |
| [2026-10-19](#a-cells-style-built-from-nothing) | code | A cell's text style, built anew to share with the field it is written in, lost the theme's line height: header and plain rows of two heights | A value pulled out of a call carries only what the call wrote, not what it inherited |
| [2026-10-19](#a-bar-whose-last-button-was-off-the-screen) | design | The table's new bar pushed More past the screen's edge, to be found only by scrolling a bar that did not look as if it scrolled | A row of buttons shares the width it has |
| [2026-10-16](#a-note-mailed-as-its-code-and-a-pdf-of-one-page-four-times) | design | A note mailed from a real phone came as bars in the message and as code in its attachment; the PDF that replaced the attachment went wrong four ways before it was right | A page drawn where no screen shows it keeps none of a screen's guarantees: size, timing, or when its pixels are read |
| [2026-10-16](#a-copy-of-the-selection-as-it-was-when-selected) | code | Words selected, made bold and 14, then copied, went out plain | A note of the state taken at one moment and used at another is stale by the time it is used |
| [2026-10-16](#a-file-looked-up-by-a-name-the-release-build-changed) | code | The release build renamed the class that reads the HTML entities, and a paste from a web page would have lost its formatting without a word | The debug build is not the one people run: check the artifact |
| [2026-10-16](#mail-apps-only-said-a-way-android-17-no-longer-accepts) | code | "Send by email" said there was no mail app, with Gmail installed | A hand-off between apps is verified on a current Android, from the log |
| [2026-10-16](#widths-in-one-unit-letters-in-another) | code | At the phone's 115% letters, a table's word broke over two lines | A size that holds text is measured from the text |
| [2026-10-16](#two-lines-of-text-that-cost-a-whole-note) | code | The list stuttered: each card took its note's whole text to show two lines, and was measured twice | What a card is given should be what it shows |
| [2026-10-16](#a-table-read-by-a-stricter-rule-than-a-persons) | code | Cards and a template's body showed a table as lines of bars | Leave out what a reader takes for a table, not only what the drawing accepts |
| [2026-10-16](#old-steps-walking-through-new-screens) | checking | The phase's first full walk failed five checks, all in older parts the new screens had changed under them | After a phase, walk the whole app, not only the new parts |
| [2026-10-16](#a-menu-the-phones-own-tree-leaves-out) | checking | The selection menu's Copy and Paste are on the screen and in no UI tree the walk can read | When a button cannot be found, press another door to the same code, and say which was not pressed |
| [2026-10-16](#an-escape-written-as-the-letter-it-stood-for) | checking | The fixture's case for the Kelvin sign held a plain K, and its mutant survived | A case for an invisible difference must be written so the difference survives being written |
| [2026-10-16](#a-drag-that-stopped-judging-when-the-finger-stopped) | code | Held at the list's edge, a card scrolled the list but kept its old place | When the world moves under a still finger, judge the drag on every tick |
| [2026-10-16](#a-gesture-inside-a-gesture) | code | Pressing and holding a card's ⠿ handle selected the card instead of lifting it | A gesture inside another must claim the touch when it lands |
| [2026-10-16](#an-offer-read-after-it-had-gone) | checking | The walk looked for an eight-second Undo, photographed it, then tapped — after it had gone | A screen dump takes seconds; read once, act at once |
| [2026-10-16](#taps-aimed-through-the-keyboard) | checking | On its side, the walk's taps landed on the keyboard, and a check passed because the autosave had already run | A race an emulator tap cannot win belongs in a test that can record the order |
| [2026-10-16](#what-a-phone-resets-for-free) | code | Beside the list, the editor kept the last note's scroll, and a second + opened nothing | A screen that goes away resets its state for free; one that stays must reset it on purpose |
| [2026-10-16](#an-update-the-editor-put-back) | code | A code shared in over the open note updated the file; the editor's next save put the old words back | Whatever keeps a copy of a record must be told when the record changes under it |
| [2026-10-15](#a-table-edit-nobody-asked-for) | code | A table edit that changed nothing re-padded the whole table; a row above the header broke the separator | An edit that touches nothing must write nothing |
| [2026-10-15](#a-list-drawn-double-spaced-then-bunched) | design | Lists drawn with a paragraph style per line came out double-spaced, then crushed together | The drawing library's paragraph is not the file's line |
| [2026-10-15](#a-desktop-note-lost-its-sizes-on-the-way-through-the-phone) | code | Every font size from the desktop read as empty and was written back without a value | A reader that knows one spelling of a value is a writer that deletes the others |
| [2026-10-15](#save-under-the-status-bar) | design | The template editor's Save went under the status bar when the keyboard rose | A full-screen dialog left to the window is a third Android default |
| [2026-10-15](#the-answer-written-by-hand-wrong-twice) | checking | Two expectations written by hand for the export were wrong; the desktop's own output was right | When the specification can be run, run it instead of reading it |
| [2026-10-15](#the-walk-read-half-of-what-the-screen-said) | checking | The walk read only a node's text, never its description, and tapped a note's menu before the note was there | A check can only see what its reader reads |
| [2026-10-15](#checks-that-were-also-true-when-nothing-happened) | checking | "Re-importing changes nothing" passed over an import that never happened | A check must be false when the action is skipped, not only when it goes wrong |
| [2026-10-15](#turning-the-phone-closed-the-note) | code | Turning the phone, or its dark mode coming on, closed the note being written | A walk that photographs the list after a change never sees what the change did to an open note |
| [2026-10-15](#the-same-date-written-two-ways) | code | The card printed `2026-10-18`, the editor `18.10.2026` | A display rule written inside one screen is a rule the next screen does not know |
| [2026-10-15](#a-setting-saved-and-a-switch-that-did-not-move) | code | The file said "off", the switch stayed on | Compose redraws on a *different* value; an object changed in place and set again is "no change" |
| [2026-10-15](#two-android-defaults-that-were-wrong-here) | design | Back on Settings closed the app; Undo was gone before a thumb could reach it | A framework's default is a decision too, and the walk on the phone is where it shows |
| [2026-10-15](#a-centred-line-that-came-back-as-a-bullet) | code | The first edit on the phone turned a centred desktop line into a bullet | Carry the whole entry, not the fields you expect it to have |
| [2026-10-15](#a-pass-printed-over-a-build-that-failed) | checking | The result checker reported "221 passed" after a build that never compiled | Results older than the code are not results |
| [2026-10-15](#the-tests-json-library-is-not-the-phones) | checking | On the computer, `7.5` arrived as a `BigDecimal` and a map forgot its order | The test classpath's library is a different library |
| [2026-09-14](#a-photograph-of-the-wrong-screen-filed-as-proof) | checking | A screenshot tool saved the wrong screen and labelled it "search" | A tool that captures whatever is there produces documentation that *looks* like proof |
| [2026-09-14](#the-preview-that-flattered-the-icon) | checking | The launcher icon came back from the phone with its corners cut off | A preview that skips the system's step approves what the system will reject |
| [2026-09-13](#a-dead-route-carrying-a-second-copy-of-the-rules) | code | `importShareCode` survived as an unreachable route holding its own copy of the new-note defaults | Dead code that duplicates a *rule* rots silently while its tests keep passing |
| [2026-09-13](#an-update-that-reached-the-screen-before-it-reached-the-file) | code | Copying a JSON array copies references, so an edit changed the live note before the write could fail | Copy the container, and you have copied nothing |
| [2026-09-13](#a-mutant-runner-that-left-the-code-broken) | checking | A runner crashed mid-round and exited with a mutated `Notebook.kt` still on disk | Restore in a `finally`; a tool that can break the code is worse than what it hunts |
| [2026-09-12](#a-silent-mutant-is-not-a-passing-one) | checking | Three mutants "survived" without the tests ever running | A verdict counts only if the suite reached its checks |
| [2026-09-12](#a-new-note-saved-without-its-formatting) | code | The toolbar worked, the screen drew bold, the file held none of it | Read the file, not the screen |
| [2026-09-12](#a-list-of-titles-would-have-been-a-list-of-blanks) | design | 48 of 77 real notes have no title at all | Design against the real notebook, not against the example one |

Findings still open, and those closed since, are at the
[end](#still-open).

---

## A cell's words held by its field after the cell changed

**2026-10-19.** The owner found the phone's table hard to use: a tap on
a cell opened a sheet of eleven entries, and a cell's words were typed
in a box of their own, away from the table. Shaped on Samsung Notes'
table, at her request, a cell is now written in place, and a bar over
the keyboard holds what the sheet held.

Writing in place gave each cell a field, and the field keeps its own
copy of the words as they are typed, so a letter does not wait for the
table to be written out again. The walk's new part found that copy
outliving its cell twice, before any of it reached the phone:

* **After Undo**, the file had its old words back while the field still
  showed the ones typed; the next letter would have written them in
  again.
* **After Row, Above**, the new empty row took the chosen cell's place.
  The place had not changed, so the field was kept, still holding the
  old cell's words, and the next letter typed went in after them — into
  the new row.

The field is now made again whenever its cell changes under it — Undo,
Redo, a change from the bar, another cell chosen — through a count each
of these moves on, which the field's copy is keyed by. Typing does not
move it: there, the field is the one writing.

Each of the two places that move the count, taken out of a built app,
was caught by the walk — the one in Undo only on the second try. The
walk's Undo step read the file, and the file was right: the stale words
were in the field, which the step never typed into. It now types a
letter after Undo and reads where it went.

**The class:** the one in
[An update the editor put back](#an-update-the-editor-put-back) — a
holder of a copy, not told when the original changes — three days
later and one level down: there the editor's copy of the note, here a
cell's copy of its words. A new copy is a new holder, and is asked the
old question on the day it is made.

## Two deletes the desktop had already settled

**2026-10-19.** Walking the new bar's Delete against the desktop found
two of the phone's table rules wrong since Phase 2:

* **The header row deleted** left the dashes beneath it on top, and the
  lines stopped reading as a table. The desktop writes the dashes under
  whichever row is first now.
* **A column deleted from a table of two** left one column, which reads
  as prose — `| just one |` is deliberately not a row — so the table
  became a column of bars. The desktop empties the column instead.

And a merge left joining nothing, its partner gone with a row or a
column, stayed a `<` or `^` in the file, where the desktop's
`_dissolve_orphan_markers` empties it. All three now do what the
desktop does, each with a test, and each change, taken out, was caught —
the orphan's on a deleted row only once a test was written for it: its
mutant survived the first round, every test's deleted row joining
nothing.

The same day, the bar walked on the owner's own phone showed a third:
a new table's empty cells drawn as slivers, too narrow for a finger to
find. The desktop draws every column at least `MIN_COLUMN` — eight
characters — wide unless a width is set by hand; the phone's drawing
had taken each column's words and skipped the floor. It keeps it now,
in one place both the drawing and More's width steps read.

**The class:** a port of a function's name and main path, not of its
guards. Most of the lines in `_grid_delete_row` and `_grid_delete_col`
are their edge cases, the second with a docstring saying why, and the
floor is one line in `_build_table_grid`; read to the end, a
port carries them.

## A cell's style built from nothing

**2026-10-19.** To draw the field a cell is written in exactly as the
cell's words, the cell's text style became one value handed to both.
Written as a new `TextStyle(...)`, it began from nothing, where a
`Text` given a size and a face had laid them over the theme's style —
and with them the theme's line height. Without one, each face took its
own: the bold header row stood shorter than the plain row under it.

The walk's table at larger letters caught it, its check that every cell
stands one line tall failing on two heights, at 115% and at 130%. The
style is laid over the theme's again.

**The class:** a value pulled out of a call carries only what was
written in the call, not what the call inherited. When drawing
parameters become a style object, it starts from the inherited one.

## A bar whose last button was off the screen

**2026-10-19.** The table's new bar gave each of its eight buttons a
fixed width and let the row scroll sideways. On a phone's width More,
the last, sat past the edge — found only by scrolling a bar that gave
no sign it scrolled. It was caught on the emulator, before the bar
reached the phone.

The buttons now share the bar's width, each a word under a mark, the
marks in one box so the words beneath sit on one line.

**The class:** a row of controls that scrolls hides its end from
anyone who does not know it scrolls. Share the width there is; put
what does not fit behind a button that says More.

## A note mailed as its code, and a PDF of one page four times

**2026-10-16.** The first note mailed from a real phone, Gmail its mail
app, came back wrong in three places. The table in the message was
lines of bars; the checklist was `[x]` and `[ ]`; and the attached web
page would not open on the phone, while on a computer Gmail showed it
as its code — `<!DOCTYPE html>` and every tag.

The message's words cannot hold a table. A mail app takes plain text
from another app, and Android's HTML-to-text keeps no table either, so
the words are now written for a person: each row a line of its cells,
named by their columns — "• Day: Mon · Task: gym" — and the checklist's
boxes drawn as ☐ and ☑. The page became a PDF, which a mail app shows.
The owner chose all three.

The PDF, printed on the phone with the system's WebView, went wrong four
times before it was right, each found by reading the file back:

* **A few dozen pixels wide.** Laid out before it had a height, the page
  took no width either, and a table's cells held one letter to a line.
  It is laid out at a page's size from the start.
* **A short note on two pages.** A document is never shorter than its
  window, and a page's height in pixels is not a whole number. A pixel's
  slack.
* **One page, four times.** Android's PDF records each page and writes
  the pictures at the end; one picture painted again for every page
  came out as the last of them on all four. Each page has its own.
* **One blank page.** Its height was read 300 ms after the window was
  resized — before the page had laid itself out again. It is now read
  until it holds still, and a page with nothing drawn on it is tried
  once more and then refused: the person is told, never sent a blank
  page.

And one seen only by looking: a page break moved into the gap between
two lines landed in the letters' tails, because the gap was found in a
drawing a quarter the size. It is moved to the gap's middle.

Two things it does not do, said rather than hidden: its pages are
pictures, so the words cannot be selected; and the walk checks that the
pages are distinct and inked, not where they break.

**The class:** a page drawn where no screen will show it keeps none of
a screen's guarantees — its size, its timing, when its pixels are there
to read. Each one has to be made true by hand, and each was found by
reading the output, not the code.

## A copy of the selection as it was when selected

**2026-10-16.** A check written to walk the app on a real phone was
rehearsed on the emulator first. Its steps selected a line, made it
bold and 14 from the format bar, copied it, and later pasted it — and the
pasted words came in plain.

The editor notes the selection when it is made — its place and the
note's formatting at that moment — and a copy is made from that note.
A style put on from the format bar changes the note's formatting without
any field saying so, so the note was taken before the bold existed, and
the copy carried none of it. A cut the same, and worse placed: its words
leave the note before it copies them, so only the note taken earlier
could say what they were. Nothing walked it, because every copy the walk
made was of formatting already in the note.

The selection is now brought up to the formatting it has *now*, while
its words are unchanged: as a copy is made, and as a field's change
arrives, before a cut can take the words out. `EditorCopyTest` holds
both; a walk part does what the rehearsal did, a copy and a cut, read
back from the file; and each of the three changes, taken out, was caught.

**The class:** a note of the state taken at one moment and used at
another is stale as soon as anything the note did not hear about
changes. Take the note where it is used, or bring it up to date there;
and walk the order a person works in — style, *then* copy.

## A file looked up by a name the release build changed

**2026-10-16.** The first release build is shrunk by R8, which renames
classes and moves them out of their packages: `Pasting` became `ca1`, in
no package at all. `Pasting` read its table of HTML entities by a name
relative to itself, so the renamed class looked in the wrong folder and
found nothing.

Nothing crashed. The parser's guard — there so that a strange page can
never take the editor down — turned the missing table into "nothing
read", and the paste fell back to plain words. Every paste from a web
page with an `&`, a `<` or an `&nbsp;` in it, which is most of them,
would have lost its formatting without a word.

It was found by reading the code for lookups by name before the release
build reached a phone, and confirmed in the built code with `dexdump`.
The fix looks the file up from the root, by its path. The release build
was then walked on the emulator, and the walk passed the old line too:
a screen cannot tell a paste with its bold from one without.
`tools/check_release_lookups.py` reads the built APK instead — each
lookup, the path it resolves to, whether the file is there — and on the
old line says MISSING.

**The class:** the debug build is not the build people run. Anything
found by name — a file, a class — can break under the shrinker, and a
guard that turns a failure into "nothing" makes the failure silent.
Check the artifact, not the source.

## Mail apps only, said a way Android 17 no longer accepts

**2026-10-16.** "Send by email" hands its message to a mail app, and the
usual way to say *mail apps only* is a `mailto:` selector on an
`ACTION_SEND`. On the emulator — Android 17, Gmail installed — the app
answered "No mail app on this phone can send it." Three causes, one
under another, each read from the log:

* Android 11 and later hide other apps from an app whose manifest does
  not say what it looks for. `<queries>` for `mailto:` fixed that, and
  only that.
* The start was still refused, `result code=-91`
  (START_INTENT_NOT_RESOLVED): the selector's `SENDTO` and the message's
  `SEND` are two actions, and the filter that matched took only one.
* Naming the Gmail app instead, Android asked which of two Gmail windows
  should take it — its mail, or its Chat.

Each mail app's own writing window is now named: found as what answers
a `mailto:` link, and kept only if it takes the message too.

A walk check was written for the third cause, and it could not be made
to fail. Once started without an account, the emulator's Gmail turned
its Chat off, and since then nothing on the screen or in the log tells
the two apart. The check was taken out rather than kept as a claim it
could not test, and its mutant is recorded as surviving.

**The class:** how one app hands work to another changes between
Android versions. Verify the hand-off on a current one, with a real
receiving app, and read it from the log rather than from what came to
the front. When the environment loses the condition a fix exists for,
say so: a check that cannot fail is not a check.

## Widths in one unit, letters in another

**2026-10-16.** At the phone's 115% letters, a table's cells broke
"socks" across two lines. A column's width was its characters times a
fixed 8.2 dp; the letters are in sp, which grows with the phone's text
setting, and dp does not. On the emulator at 100% the two agreed
exactly, which is why nothing showed there.

The width of one character is now measured from the cell's own face
and size — the wider of bold and plain — so it grows with the letters.
A walk part draws a table at 115% and 130% and checks every cell stands
one line tall; built with the old fixed width, the app failed it at
both sizes.

**The class:** a size that holds text is measured from the text, in the
text's own unit. A check at the default setting is a check of one
setting.

## Two lines of text that cost a whole note

**2026-10-16.** Scrolling the list on the phone stuttered now and then.
It was measured before anything was touched — `dumpsys gfxinfo` over a
scroll of a real notebook: 3.2% of 433 frames late, the slowest 44 ms,
on a screen that draws one every 8. Reading what one card did on every
frame found three things:

* each card was handed its note's **whole** text — tens of thousands of
  characters on a long note — to show two lines of it;
* it worked its preview out again on every drawing;
* its colour stripe, a child stretched to the card's height with
  `IntrinsicSize.Min`, made every card be measured twice, its text laid
  out twice.

A card now gets a glimpse of 300 characters (never cut through an
emoji), the preview is worked out once per note, and the stripe is
painted behind the card. The scroll is to be measured again on the same
phone once it runs this build; until then the fix is reasoned, not
measured.

**The class:** a list's cost is paid per card, per frame — what a card
is given should be what it shows. And a layout that asks its children
their size first doubles the work of everything inside it.

## A table read by a stricter rule than a person's

**2026-10-16.** On a real phone, cards in the list showed lines of
vertical bars where a note had a table, and a template's body showed the
same bars above the table drawn properly under them.

The card left out the lines the *editor* draws as a table: rows with a
pipe at both ends. A row left open (`| a | b`) or typed past its last
cell (`| a | b |and then`) is no row to the editor — but a person reads
it as one, and the card printed it. The desktop met the same report
twice, from its calendar, and its answer is `preview_text`: a line that
opens with a pipe and a space and holds two pipes is left out, and the
same pass counts the tables, so "▦ 1 table" and the words beside it
cannot disagree. The card, the calendar's rows and their counts now use
it.

The template was the same thing in another place: its body box edited
the stored text, rows and all, and drew the table again beneath. The
desktop's template editor hides the rows. The box is now cut as the note
editor cuts a note — words in fields, each table drawn in its place.

**The class:** a screen that shows stored text where the person expects
the drawn thing. What to leave out is decided by what a *reader* takes
for a table, not only by what the drawing code accepts — and every place
that shows a note's words can show its plumbing.

## Old steps walking through new screens

**2026-10-16.** Each Phase 3 feature was walked as it was built, its own
part run on its own. The first run of the whole walk from start to end
passed 174 checks of 179. The five that failed were all in older parts,
and none was the app's fault:

* **A button that borrowed a word.** Undo and Redo gave the editor a ↶
  button, which a screen reader says as "Undo". The part that moves a day
  found the move's eight-second offer by the word "Undo" and tapped it —
  and found and tapped ↶ instead. Worse, its check that the move *offers*
  an Undo had been passing on ↶'s description: true whether the offer
  was there or not. It now finds the node whose shown words are "Undo",
  and the offer passed for real once it looked.
* **A swipe counted for a shorter sheet.** The cell-menu part swiped once
  to reach "Delete table". Phase 3 added the columns' widths and the
  caption to the same sheet, and one swipe no longer reached it; four
  checks fell after it. It now scrolls until the button is in sight.

Running that part again found a third, once in three runs: a Back meant
to put the keyboard down, pressed before the keyboard had risen, closed
the cell's sheet instead. The walk now presses Back for the keyboard
only once the phone says the keyboard is up (`mInputShown=true`), at
every step where that is what the Back is for.

**The class:** a new screen changes the ground older steps walk on — a
word one button now shares with another, a sheet grown longer, a
timing. Walking only the new parts cannot see it. After a phase, run
the whole walk; find by the words shown when two controls may share a
word; seek what a step needs instead of counting the gestures it once
took; and wait for the state a step relies on rather than for a time.

## A menu the phone's own tree leaves out

**2026-10-16.** Copy and paste with the formatting were to be walked as a
person does them: hold a word down, choose Select all and Copy, hold a
word down in another note, choose Paste. Every step failed: the menu was
"not on the screen".

It was on the screen — the photograph showed Cut, Copy, Select all. It is
a floating bar the system draws above the selection, and no dump of the
phone's UI tree held its words, not even one that asks for every
window. The walk finds what it presses by its words, so it could not
press it.

**What was done instead:** the keys a keyboard sends — Ctrl+A, Ctrl+C,
Ctrl+X, Ctrl+V, Ctrl+Shift+V, through `input keycombination` — which go
through the very copy and paste the menu's buttons call. The menu is
photographed, with "Paste as plain text" on it, for a person's eye, and
the walk says in its own comments that the buttons themselves were not
pressed.

**The class:** a check can only press what its reader can find. When a
door cannot be found, use another door to the *same* code — not a
different route that merely ends in the same place — and write down which
door was left unpressed.

## An escape written as the letter it stood for

**2026-10-16.** The paste fixture holds a case for each rule of the
desktop's reader by name. One is the Kelvin sign: Python's
case-insensitive match takes U+212A for a `k`, so `<bloc&#x212A;quote>`
— written with the sign itself — counts as a block tag, and the phone's
port names that letter on purpose. Its mutant, the Kelvin rule taken
out, **survived**: the case in the generator was a plain capital K,
which matches `k` under any rule at all.

The script had been written through a tool that turns `K` into the
character it names, and the Kelvin sign looks exactly like a K. Two other
special letters in the same list came through as themselves and worked.
The Kotlin port had the same thing happen; it now compares those letters
by number (`c.code == 0x212A`), and the generator spells the case
`"\N{KELVIN SIGN}"`, which no tool rewrites. The mutant was killed on
the next run.

**The class:** a test case for a difference the eye cannot see must be
written so the difference survives being written — by name or by number,
never as the glyph. And a surviving mutant is a question about the
*case* before it is a question about the code.

## A drag that stopped judging when the finger stopped

**2026-10-16.** Custom order is arranged by dragging a card by its ⠿
handle; held against the top or bottom edge, the list scrolls by itself.
On a thirty-note list the walk held a card at the edge: the list
scrolled, and the card, dropped, went back to the place it came from.

Whether the dragged card trades places with the one under it was judged
when the finger *moved*. Held still at the edge, the finger does not
move — the list moves under it, and the card it now sits over changes
with every step of the scroll, but nothing asked.

**The fix:** each step of the edge scroll judges the drag again, as a
move of no distance. **The class:** when the world moves under a still
pointer, the drag must be re-judged on every tick, not only on the
pointer's own events.

## A gesture inside a gesture

**2026-10-16.** A card is selected by holding it down; its ⠿ handle,
inside the card, is dragged to arrange the list. Pressing the handle and
holding it before moving — which is how a person picks something up —
selected the card instead.

The handle's drag waited to see the finger travel before it claimed the
touch, as a drag usually does. Until then the touch was free, and the
card's own long press, timing it, took it. **The fix:** the handle
claims the touch the moment it lands, so a hold on the handle can only
ever be the start of a drag. **The class:** a gesture nested inside
another must claim the pointer at the down, or the outer one claims the
hold.

## An offer read after it had gone

**2026-10-16.** Undo is offered for eight seconds. A walk step found the
offer by reading the screen, took its photograph — reading the screen
again first, as every photograph does — and then tapped Undo. It was
gone. Another step checked a summary message after other reads, and it
had faded.

A dump of the phone's UI tree takes a second or two; three of them and a
screenshot spend most of an eight-second offer. **The fix:** one read,
then the photograph straight from the screen and the tap, with nothing
in between; a passing message is checked first, before anything else is
read. **The class:** what the screen shows for a few seconds must be read
once and acted on at once — every look costs time the screen does not
wait for.

## Taps aimed through the keyboard

**2026-10-16.** The walk's wide part turned the emulator on its side,
typed into the note open beside the list, and tapped another card at
once — to prove that words typed just before the switch are saved with
the note they were typed in. The check passed. The next three failed.

On a phone on its side the keyboard covers most of the screen. The
card's position had been read before the keyboard rose, and the tap
landed on the keys. The note never switched; the following checks found
themselves on the wrong screen. And the check that passed did so
because the 0.8-second autosave had run while the walk was busy failing
to switch — the words were saved, by a route that was not the one being
tested.

**The class:** [a check also true when the step never ran](#checks-that-were-also-true-when-nothing-happened),
again, through a race: "saved before the switch" and "saved by the timer
anyway" leave the same file.

**The fix:** the walk puts the keyboard down before touching the list,
and no longer claims the race. The order — the open note saved *before*
another one takes its place — is held where it can be recorded:
`AppStateTest` stands in for the editor with a save that notes what the
file held when it was called. Each of its rules was shown failing by a
mutant.

The same evening, a build that failed to compile was followed by the
walk on the app already installed, because the commands were joined
with `;` rather than `&&`. Every check failed, loudly, so nothing was
believed — but it is [the pass printed over a failed build](#a-pass-printed-over-a-build-that-failed)
in another form, and the build, install and walk are now chained.

And on the next full run, the keyboard had a say of its own. "milk and
eggs", typed at the start of a new note's body, reached the file as
"Milk and eggs": the body asks for a capital at the start of a sentence,
and the keyboard gave it to the typed-in text — on that run, after many
that had kept the letter small. The lists part had met this already and
compared in lower case; the first part had not, and neither had a
checklist item typed into a template. Text that starts a capitalising
field is now typed with its capital, so the keyboard has nothing to
change, whichever way it decides.

## What a phone resets for free

**2026-10-16.** With a note open beside the list on a wide screen, the
photograph of the second note opened showed its text and not its title.
The editor had stayed on screen from one note to the next, and kept the
first note's scroll, pushed down by the keyboard while typing in it.

On a phone the editor covers the screen and closes with Back, so each
note opens in an editor built for it. Nothing in the code said "start at
the top"; the editor going away said it. Beside the list it never goes
away. Two more things followed from the same fact:

* **A second + opened nothing.** A new note is "note 0" until its first
  save, so a second new note was "note 0" again — the same note, as far
  as the screen could tell — and the pane stayed on the first.
* **Turning the phone would have rebuilt the editor.** The first layout
  drew the editor in one place on a narrow screen and another on a wide
  one. Compose keeps a screen's state by its place, so turning the phone
  would have built a new editor: the find bar shut, the cursor lost, and
  words not yet saved left in the old one. Found by reasoning before it
  was ever seen.

**The class:** state that was reset by its screen leaving. When a layout
keeps a screen on, every reset it used to get for free has to be made on
purpose — or it was never a rule, only a habit of the phone.

**The fix:** each note opened gets an editor of its own, counted by
`AppState.opening`, and a new note becomes its id at its first save. One
layout serves every width — the rail, the panes and the bar each there
or not — so the list and the editor keep their places when the phone
turns. The walk checks a selection and an open find bar surviving the
turn, and a second note shown from its title; each of the three was
built into the app as a mutant and caught there.

The third was not caught at first. The walk counted the nodes that say
the note's title, found two — the card's and the editor's — and passed
over the mutant whose photograph showed no title at all: **a node
scrolled out of a pane is still in the tree.** Its box is not; cut to
what shows, the hidden title was 11 pixels tall. The walk reads the box
now, and the mutant fails.

## An update the editor put back

**2026-10-16.** Planning the wide layout raised the question of what the
list may do to the note open beside it: pin it, file it, send it to the
Trash. The editor keeps its own copy of every field of the note it holds
and writes them all back when it saves — so a pin from the list would
last until the next letter typed.

Looking for the same shape on a phone found it already there. A code
shared in from another app arrives over whatever is open. "Update this
note" replaced the note on disk while the editor kept showing the old
words, and the first thing typed afterwards saved them back over the
update.

**The class:** a holder of a copy, not told when the original changes.
It is not the route — an import, a pin — but the copy.

**The fix:** every change made from outside the editor goes through one
door in `AppState`. When the open note is among the notes it touches,
what the editor holds is saved first — so the change lands on the words
on screen, and a copy or a code carries the last ones typed — and the
editor reads the note back after. Eight tests and fourteen mutants hold
the door; the walk holds the editor's half, with a pin from the list
beside the note and a code shared in over it.

## A table edit nobody asked for

**2026-10-15.** Three findings from one feature, the table's cell menu,
two of them in its photographs:

* **An edit that changed nothing re-padded the table.** Asking for a
  merge marker on the separator, or for the last row to go, was refused —
  and the table was written back anyway, every cell padded again into a
  diff nobody made.
* **A row inserted above the header broke the separator.** It pushed the
  dashes to the third line, where they are no longer read as one, and
  they became a row of text.
* **A merged cell was drawn short.** It counted each swallowed column's
  grid line but not its padding, so the grid showed through at its right.

**The class** of the first two: an edit that assumes it changed
something. A table's lines are rebuilt from the edit's grid, and
"rebuilt" is a change even when the grid is the one that was read.

**The fix:** a table edit remembers whether it touched anything and,
when it did not, hands back the lines it was given. The separator keeps
its place under the first row, as on the desktop, and the merged cell
counts the whole of each column it covers. The first two are pinned by a
test and a mutant each; the third is a drawing, held by the walk's
photograph.

## A list drawn double-spaced, then bunched

**2026-10-15.** The desktop indents a list item and aligns a line by
paragraph, so the editor gave each line a paragraph style of its own.
The list came out double-spaced. Fixed that way, it came out crushed.

Compose's text field has its own idea of a paragraph. A line break kept
inside a styled paragraph drew an empty line after it; a break left
between two styled paragraphs became an empty paragraph of its own; and
a paragraph of one line had its spacing trimmed to nothing.

**The class:** a library's word that sounds like the file's. A
"paragraph" in the drawing is not a "line" in `paragraphs`.

**The fix:** each line is a paragraph, and its break is drawn as an
invisible character of the same length — so every position in the
drawing is still the position in the text, and the styles, the cursor
and the find bar's matches stay where they were — at the body's own
line height, untrimmed.

## A desktop note lost its sizes on the way through the phone

**2026-10-15.** Found while porting templates, by sending every note of
the export fixture through a save on the phone and asking for back what
the desktop would keep:

* **Every font size was lost.** The desktop stores a size as a number;
  the phone read a span's value only when it was text. Every size came
  in empty, was not drawn, and was written back with no value — which
  the desktop's `normalize_formatting` drops.
* **Typing a letter into a list item dropped its line entry.** The line
  was inside the edited stretch, and every line there was dropped for
  fear of moving a marker onto a stranger's line. A stretch with as many
  lines after as before was typed into, not reshaped.

**The class:** the [partial reader](DATA_FORMAT.md#a-partial-reader-must-not-become-a-total-writer)
again, this time inside a value: a reader that knows one spelling of a
field writes back a field without it. `check_note_shape` could not see
it — it compares the fields a note has, not the shape of what is inside
them.

**The fix:** sizes are read and written as numbers, and only as numbers,
as the desktop keeps them. A line typed into keeps its entry. The test
that found them stays: 400 notes, styles valid and not, round-tripped
through a save.

## Save, under the status bar

**2026-10-15.** The template editor is a dialog that takes the whole
screen. On the emulator, with the keyboard up, its top bar — Save with
it — had gone under the status bar, out of reach.

Left to itself, a dialog's window fits inside the system's bars and is
pushed up whole by the keyboard. The app around it draws edge to edge
and keeps its own room; the dialog did neither.

**The class:** [an Android default](#two-android-defaults-that-were-wrong-here),
a third: what a window does when nobody says.

**The fix:** the dialog draws edge to edge, keeps the keyboard's room at
its bottom itself, and puts the status bar's icons on the theme's side.

## The answer written by hand, wrong twice

**2026-10-15.** The export was checked against 794 files the desktop's
own renderer wrote. Before that fixture existed, two expectations were
written by hand from reading the Python — a file name, and a highlighted
cell's HTML — and both were wrong. The phone's output was right, and
the desktop's agreed with it.

**The class:** when a specification can be run, reading it is a second
implementation, with its own bugs. The [house rule](../qa/README.md#the-house-rules)
already said not to check the app against itself; this is its sibling —
do not check it against a memory of the spec either.

**The fix:** the expectations were taken from the desktop's output. Every
port in this phase is held to a fixture its desktop function wrote:
recurrence, the calculator, the export, templates and renumbering.

A smaller cousin from the same day: the tool used to write the export's
source turned the escape `�` into the character itself, invisible
in review. The source now says `Char(0xFFFD)`, which no tool rewrites.

## The walk read half of what the screen said

**2026-10-15.** Two of the walk's readers were narrower than the screen:

* **It read a node's text and never its description.** The find bar's
  close button is drawn `✕` and said "Close find". A check looking for
  either one could not see the other, and passed or failed on what it
  could not see.
* **It tapped a note's ⋮ before the note was there.** After a fresh
  install the first note opened slowly, a fixed pause was not long
  enough, and the tap found the list's own ⋮ in the same corner.

**The class:** a check can only see what its reader reads, and only when
it reads it.

**The fix:** `screen_says` reads both, and opening a note waits for the
editor's own "ⓘ" rather than for a length of time.

## Checks that were also true when nothing happened

**2026-10-15.** The final emulator walk reported all its checks passed —
and three failed steps on the way. The backup it was meant to import back
had never been chosen: the picker held every backup earlier runs had
made, and the walk took the last name `ls` printed, which was the
*oldest*, at the bottom of the list, where the tap landed on the edge of
the screen. No import happened. And the check, "re-importing your own
backup changes nothing", passed — because nothing is exactly what an
import that never happens changes.

A sweep of the walk for the same shape found three more:

* "Undo opens the note it brought back" was satisfied by the list, which
  says "Groceries" too.
* "A note restored from the Trash page is back" read an unreadable
  `trash.json` as an empty one.
* The two checks that need a code from the desktop's own encoder were
  skipped in silence when it could not be loaded.

**The class:** a check whose condition is also met when the step it
checks never ran. It cannot tell "done right" from "not done", so it
only ever fails for the second-worst outcome. The walk had met it once
already — the Undo check's comment records the same lesson about a note
that never left — which is why it gets its own story now.

**The fix:** the walk imports the backup that appeared with *this*
export; the re-import check requires the question to have been seen;
the Undo check requires the editor, not the list; the restore check
reads both files back; and a test that cannot run says so as a failure.
Each was shown failing on the case it had passed over.

## Turning the phone closed the note

**2026-10-15.** Found while writing the documentation, by reading the
manifest rather than by any check: the Activity declared no
`configChanges`. So on every configuration change — the phone turned on
its side, its dark mode switching on at sunset, larger letters, a split
screen — Android destroyed the Activity and built a new one. All of the
app's state lives inside it, in Compose's `remember`, and went with it.

Turned on the emulator with a note open, the app came back on the list.
The note itself was safe — the editor saves in `onStop`, which runs
before the Activity goes — but the person was thrown out of what they
were writing. And with "Follow the phone" on by default, the dark mode
coming on in the evening would have done the same to anyone mid-note.

The walk had photographed landscape and large letters for weeks. It
photographed them **on the list**, where a rebuilt Activity looks exactly
like one that was never touched, and the large-letters shot opened a
note *after* the change instead of before it.

**The class:** a check placed where the failure cannot show. A
configuration change can only lose state that exists, so it has to be
made with state in hand — a note open, mid-edit.

**The fix:** the Activity declares the changes it handles itself —
orientation, size, dark mode, density, font scale, keyboard — and
Compose redraws with the new configuration instead of the app being
rebuilt; the theme follows the phone live, status bar included. The
walk now opens a note *first*, then turns the phone dark, grows its
letters and turns it on its side, checking after each that the note is
still open. All three checks were run against the old build first and
failed there.

What is left is named in [ARCHITECTURE](ARCHITECTURE.md#where-this-will-hurt-next):
if Android kills the process while the app is in the background, it
reopens on the list. The note is saved; where the person was is not.

## The same date, written two ways

**2026-10-15.** The documentation pictures were retaken for the English
app, and looked at before being committed. The list's card said
`📅 2026-10-18`; the same note open in the editor said `18.10.2026`.

The desktop shows a date one way everywhere — `show_date`, in its
`core/recurrence.py`: `17.10.2026`, with anything that is not a date shown
exactly as it was typed. The phone had ported that rule, but into the
editor's drawing code, as a helper the editor called. The card is drawn
from the rules layer's `tagLine()`, which had never heard of it and
printed the stored form.

**The class:** a rule about what the user SEES, living inside one screen.
The next screen to show the same thing does not inherit it; it writes its
own, or none.

**The fix:** `Notebook.showDate` in the rules layer, the one place every
screen asks — checked against the desktop's own `show_date` on the same
inputs, including the ones that are not dates, and pinned by three
mutants. No test could have caught it before, because no test had ever
read the card's tags line.

## A setting saved, and a switch that did not move

**2026-10-15.** The emulator walk turned "Follow the phone" off and read
`settings.json`: off. The check passed. The photograph showed the switch
still on.

The app keeps the loaded notebook, settings included, in one Compose
state. Saving a setting changed that `JSONObject` in place and assigned
the same object back — and Compose redraws when a state's value is
*different*. The same object, changed inside, compares equal to itself.
The file was right and the screen kept drawing the past.

**The class:** a mutable object inside state that compares by equality.
Every write here goes through the same state, so the fix had to be there
rather than at the switch.

**The fix:** the state is created with `neverEqualPolicy()`, so every
assignment redraws. The walk's check now looks at the screen as well as
the file — which is the point of reading both.

## Two Android defaults that were wrong here

**2026-10-15.** Both found by walking the built app, neither visible in
the code, because neither was written down anywhere: they were what
Android does when nobody says otherwise.

* **Back on the Settings tab closed the app.** With nothing handling
  Back, a press on the root screen finishes the Activity. On a phone with
  two tabs, the person expects the Notes tab — the way back to where
  they started — not the home screen. Now Back leaves a settings page for
  the list of pages, and the Settings tab for Notes.
* **"Moved to Trash · Undo" was gone before a thumb could reach it.**
  The short snackbar lasts about four seconds; the walk's own tap missed
  it, and a person reading the line first and reaching for the button
  second would have too. It now stays for eight seconds and goes on its
  own.

**The class:** a framework's default is a decision, and an unexamined one.
The first was found by the walk's next step landing on the launcher; the
second by a check that could not find the button it had to press.

## A centred line that came back as a bullet

**2026-10-15.** The desktop keeps a note's line styling in `paragraphs`:
bullets and numbers, but also a line's alignment and indent — and a
centred line has an entry with **no `kind` at all**. When an edit moves
lines up or down, the phone renumbers those entries. It rebuilt each one
as `{"line", "kind"}`, with `"bullet"` as the default kind.

So the first edit on the phone of any note with a centred heading turned
the heading into a bullet, and dropped every indent and alignment in the
note, silently. Found by reading the desktop's `paragraphs` format while
porting the welcome note, which centres its title.

**The class:** a writer that rebuilds a record from the fields it expects
it to have. It is the [partial reader](DATA_FORMAT.md#a-partial-reader-must-not-become-a-total-writer)
rule again, one level down: inside a note, too, what this app does not
read must survive what it writes.

**The fix:** the whole entry is copied and only its line number changes.
A test carries a centred line and an indented, right-aligned number
through an edit.

## A pass printed over a build that failed

**2026-10-15.** `tools/check_test_results.py` exists because Gradle's
console says nothing useful about tests: it reads the result XML and
counts. After a change that did not compile, it printed **"221 passed"**.

The build had failed before the tests ran, so no new XML was written —
and the XML from the previous, successful run was still in the folder.
The checker read yesterday's results and reported them as today's. In CI
a fresh checkout has no old XML, so it could only lie on a developer's
machine, which is exactly where it is trusted most.

**The class:** a verdict read from an artefact without asking when the
artefact was made — the same family as
[the silent mutant](#a-silent-mutant-is-not-a-passing-one).

**The fix:** results older than the newest Kotlin source are refused, by
name, with the advice to run the tests again.

## The test's JSON library is not the phone's

**2026-10-15.** The unit tests run on the computer, where Android's
`org.json` is only a stub, so a real `org.json` is put on the test
classpath ([why](ARCHITECTURE.md#decision-orgjson-and-the-trap-that-comes-with-it)).
It has the same names and different habits:

* **It reads `7.5` as a `BigDecimal`**; Android reads it as a `Double`.
  The Trash's retention window refused decimals by checking for
  `Double` — so on the computer a hand-edited `7.5` slipped through as
  some other `Number` and became 7. The desktop accepts whole numbers
  only, so the rule is now written that way: anything else is the
  default, whatever class it arrives as.
* **Its `JSONObject` does not keep insertion order**; Android's does.
  The order of the categories is the order in Settings, and a rename
  must keep a category in its place — a rule no test could pin on an
  object that shuffles its keys. That rule now lives in a function over
  an ordered map (`Organizing.renamed`), tested there.

**The class:** a test passing or failing for a reason that belongs to
the test's tools rather than to the code. Neither would have shown on a
phone; both would have made a test say something untrue.

## A photograph of the wrong screen, filed as proof

**2026-09-14.** The Android documentation had no pictures, so a script
was written to take them: seed a notebook, walk four screens, photograph
each one. It reported four screenshots taken. Three were right.

The fourth was supposed to show search narrowing the list. The tap aimed
at the placeholder text *"Cauta in notite…"*, which is drawn **behind**
the text field rather than inside it, so no node carried that text and
the tap never landed. The script printed a line saying it had not found
it — and then photographed the screen anyway, pulled it into
`docs/screenshots/04-cautare.png`, and finished with "Gata".

The picture was of the plain note list. Nothing about it looked broken;
it was simply a photograph of a different screen, about to be committed
as evidence of a feature.

**The class:** a verification tool whose output does not depend on what
it claims to verify. Same shape as the icon preview below, three days
apart, which is why both are here rather than in the changelog.

**The fix** is not "check the return value". Each shot now carries the
words that must be **on the screen** before the shutter opens; the live
UI tree is read and compared, and a screen that does not say them is not
photographed. The failure is now noisy and the picture cannot be wrong.
The keyboard is dismissed and the status bar frozen in the same pass, for
the unrelated reason that both were making the pictures ugly.

## The preview that flattered the icon

**2026-09-14.** The phone's launcher icon is drawn by the desktop app's
own code, at Android's sizes. The first attempt drew the mark at the full
safe-zone size — the middle 72 of the adaptive canvas's 108 units, which
is what the documentation calls safe — and a preview script composed the
two layers and masked them into a circle, a squircle and a rounded
square. It looked right in all three.

On the phone, the frame around the mark was cut off at every corner.

Two things were wrong, and only the second one mattered. The system does
not simply mask the canvas: it first **keeps the middle 72 of 108 and
scales that up**, and only then masks. The preview skipped that step, so
it was flattering the icon by exactly the amount that was about to be
cropped. And the safe *square* is not the safe *area*: a circle mask
keeps only the circle inscribed in that square, so a square mark has to
fit inside the inscribed circle — 72/108 ÷ √2, about 47% of the canvas,
not 67%.

**The fix:** the mark is drawn at 47%, and the preview now performs the
system's crop before masking. It was re-checked by installing the app and
looking at the launcher, not by looking at the preview again.

## A dead route carrying a second copy of the rules

**2026-09-13.** Before notes had identities, a pasted share code became
notes through `importShareCode`. When the screen moved to
inspect-then-apply — decode, ask what to do, then write — that function
stopped being called by anything. It was not deleted, because it still
had six tests and they all passed.

What made it dangerous was not that it was dead. It was that it carried
its **own copy of the new-note defaults**: the field list a note is born
with. Add a field to the note shape — as the `uid` work was about to —
and the copy in the live route learns about it while the copy in the dead
route does not. Its six tests would have gone on passing, describing a
note shape the app no longer used, in a function nobody could reach.

**The fix:** the route was removed and its checks moved onto the route
the screen actually uses, with the four that matter re-proved by mutants.
One of them changed meaning on the way and says so in its own name:
importing the same code twice still makes two notes when the code carries
no identity — which is most codes written before identity existed — and
is recognised when it carries one.

## An update that reached the screen before it reached the file

**2026-09-13.** Every write here follows one shape: build a *candidate*
copy of the notes array, write the candidate to disk atomically, and only
then let the in-memory array — the one the screen reads — catch up. If
the write fails, the screen never showed the change.

Applying a share code broke that shape without looking like it did. It
copied the array, found the note to update in the copy, and edited it.

But copying a `JSONArray` copies *references*: the candidate held the
same note objects as the live array. Editing the note "in the copy" was
editing the note on the screen. The write could still fail — disk full,
file replaced underneath, anything — and the user would be looking at an
update that no file agreed with.

It was found by a mutant that should have died and did not: the check
that was supposed to prove the file is written before the screen changes
was passing either way, because both happened at once.

**The fix:** the note is rebuilt from its own text (`JSONObject(o.toString())`)
and the rebuilt one replaces the entry in the candidate. The comment
explaining why sits on the line, because the next person to read it will
see a copy being made of something that was already copied.

**The class:** "I made a copy" is not the same as "nothing shared". Every
place in `Notebook.kt` that builds a candidate was walked for the same
mistake, and the rule now stands with the format's
[rules for writing the file](DATA_FORMAT.md#a-copy-of-the-array-is-not-a-copy-of-the-notes).

## A mutant runner that left the code broken

**2026-09-13.** Mutation testing here means: break a rule on purpose, run
the suite, watch it fail, put the code back. A script does it so the
"put the code back" step cannot be forgotten.

One round reached for a helper the previous round had defined, threw, and
exited — with the mutated `Notebook.kt` still on disk. The next build
failed for a reason that had nothing to do with the code anybody had
written.

**The fix:** the restore belongs in a `finally`, not at the end of the
happy path. A tool that can leave the source broken is a worse bug than
anything it was written to find.

A second lesson from the same family, learned on the desktop and imported
here: a runner must read and write the file as **bytes**. Reading text
and writing it back rewrites every line ending, so a "restore" that
changed nothing still left the file rewritten end to end — `git diff`
empty, `git status` disagreeing.

## A silent mutant is not a passing one

**2026-09-12 onwards.** Three times, a mutant was introduced, Gradle was
run, and no test failed — which reads as "the suite does not cover this"
and sends you off to write a check that already exists.

All three times, the tests had not run at all:

1. the edit missed its pattern, so nothing was actually mutated;
2. it changed something other than the behaviour meant;
3. it produced Kotlin that would not compile.

In each case Gradle printed **no test results**, and "no failures" looked
identical to "no tests".

**The rule:** before drawing any conclusion from a mutant, check that it
reached the tests — the XML in
`app/build/test-results/testDebugUnitTest/` is the only proof, because
the task's console output says nothing useful when it fails.

## A new note saved without its formatting

**2026-09-12.** The formatting toolbar worked. The screen drew the bold
text while it was being typed. Saving reported success. The note came out
with no formatting at all.

`formatting` had been added as a parameter to `updateNote` and not to
`createNote`. Editing an existing note kept its styling; making a new one
dropped it on the floor. Nothing on screen could show this, because the
screen was drawing what it had in memory, not what had been written.

**It was found by reading the written file** — the same discipline that
found two invisible bugs in the desktop app. The screen is a rendering of
what the program believes; the file is what it did.

**The fix** carries the test that would have caught it: write a note with
formatting, read the file back, compare.

## A list of titles would have been a list of blanks

**2026-09-12.** Not a bug — a measurement that changed the first screen
before it was built.

The list was going to show note titles, the way the desktop's sidebar
does. Counting the real 77-note notebook first: **48 notes have no title
at all**, and 15 more have one of three characters or fewer. A list of
titles would have been a column of blanks with a few fragments in it.

**The design that shipped:** the headline falls through to the note's
first spoken line when there is no title, set lighter than a real title
so a named note still reads as named.

**The class:** design against the real notebook, not the example one. The
example notebook always has titles; that is what makes it an example.

---

## Still open

Findings, not failures: each one waits on a change in the desktop
repository rather than here.

### The DESKTOP's automatic Trash purge can delete a file a living note uses

Found 2026-10-15, while porting the purge. When a note leaves the Trash
for good, its attached files are deleted with it. The desktop's manual
"Delete for good" and "Empty Trash" pass a *spare* list — the files the
notes in the app still use — so a shared file survives. Its automatic
purge at startup (`purge_expired_trash` in `core/storage.py`) calls the
same helper **without** one.

A stored file is shared between two notes only in older notebooks, or
after a hand edit, so this is rare. But the startup purge is the one
deletion nobody watches, and on such a notebook it can remove a file a
living note still shows. The phone's purge spares those files
([the difference](STATUS.md#where-it-differs-from-the-desktop-on-purpose)).
The desktop fix is one argument; removing a single attachment in its
`ui/attachments.py` calls the same helper without one too, and is worth
checking in the same change.

### Seven DESKTOP findings from porting Phase 2

Porting a feature means reading its Python closely enough to answer as
it answers, and running it over hundreds of generated cases. That turned
up seven things in the desktop, each left for its own repository; the
phone matches the desktop wherever the difference is not a fault.

* **"Title Z–A" puts the untitled notes first.** Its sort reverses the
  whole key of "Title A–Z", whose comment says untitled notes sink to the
  bottom — so reversed, they rise to the top. The phone keeps them at the
  bottom in both orders, as that comment means
  ([a deliberate difference](STATUS.md#where-it-differs-from-the-desktop-on-purpose)).
* **The calculator raises `RecursionError` at about 190 nested
  brackets** instead of returning `None` like every other failure. The
  phone shows Error there.
* **The calculator's display refuses `-`, `*` and `/` typed on a
  keyboard**, taking only its own `−`, `×` and `÷`.
* **Find steps through a table's matches column by column** — a side
  effect of how it sorts them. The phone goes row by row, as a table is
  read.
* **The export raises `IndexError` on a `^` merge under a shorter row**
  (`merge_map` reads a cell without asking whether it exists): 3 of the
  400 fixture notes. The phone writes those notes.
* **The table size grid lights a single column for a two-column table**,
  because it starts at its second column. The phone's grid draws every
  column.
* **A table inserted right above another leaves two blank lines** between
  them.

### Two DESKTOP findings from porting Phase 3

* **A day's return hides another day moved onto its date.** When a day
  of a series has been moved away and another day of the same series
  moved onto its date, Undo of the first move, or "Reset this day" on it,
  brings the first day home on top of the second, and the calendar shows
  one of them where there were two. Confirmed with the desktop's own
  recurrence code. The phone refuses that return and says why
  ([a deliberate difference](STATUS.md#where-it-differs-from-the-desktop-on-purpose)).
* **A pasted page's second table puts its styles beside their words.**
  `tables.align` re-pads every table in a paste and reports where it put
  spaces in or took them out; `shift_spans` judges those places against
  the text as it came. For the first table they are counted that way;
  for every later one, in the text the earlier tables' padding has
  already changed — so a bold word in a second table lands on the spaces
  next to it, and every style after it slides too. 20 of the 567
  fragments in the paste fixture show it. The phone counts them in the
  text as it came; the fixture's generator makes the phone's answers by
  that same two-line change to the desktop's own function, run in its
  own process, and keeps the desktop's answer beside each of the 20.

The desktop's `remove_attachment` deleting a file another note still
uses is the second half of
[the Trash purge finding above](#the-desktops-automatic-trash-purge-can-delete-a-file-a-living-note-uses);
the phone's "take off" spares such a file, as its purge does.

### On the phone: a highlight from the format bar covers a line's end

The desktop lays a highlight line by line and never over a line's end —
covering one paints a bar to the window's edge in Tk — so its files hold
a highlight across three lines as three spans. The phone's paste does
the same; its format bar, older, lays one span across the line ends.
The desktop draws that span correctly when it opens the note (it splits
it as it loads), so nothing shows; but the two programs write the same
highlight differently, and the phone's should follow the desktop's.

### A walk that once showed a new notebook over its seed

Once, of many runs, a seeded part of the walk opened on a brand-new
notebook's welcome note although the seed was on disk. Not reproduced
since. Every seeded part now fails at once, by name, when the app does
not show the notebook it was given, so a second sighting says what it
is instead of failing ten later checks for one cause.

On 2026-10-16 the same step failed once differently: on the first start
after an install, three seconds after the app was started, its list was
not drawn yet. The step now looks for the notebook for up to ten
seconds instead of once. The first sighting — the welcome note shown
over the seed — is still unexplained, and this entry stays open for it.

### A deliberate difference worth porting back

Search here folds diacritics; the desktop compares the letters as typed.
What the folding does, and why it is worth having, is in
[STATUS.md](STATUS.md#where-it-differs-from-the-desktop-on-purpose),
beside the other places this app's rules knowingly differ from the
Python's. This one should stop being a difference by the desktop
adopting it.

## Closed since

### A mismatch in the DESKTOP's retention docstring

Opened 2026-09-13: `trash_retention_days`' docstring said a hand-edited
`0` or `-3` "reads as the default", while the code clamps them to one
day. Both safe, but the two statements disagreed. **Closed** — the
desktop's docstring now says what the code does ("clamped to 1-365"),
and this app follows the same code.
