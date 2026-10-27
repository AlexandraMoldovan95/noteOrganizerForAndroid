# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Eighteen of the sixty-one are about
the checking.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
| [2026-10-27](#a-trash-restored-without-its-window) | design | A backup restored onto a new phone brought its Trash, every entry with the day it was deleted, and not the 90 days it was kept for: the next start emptied what was older than the default 30 | Data that carries a date to be judged by a rule must travel with the rule |
| [2026-10-27](#words-a-field-kept-until-it-was-left) | code | A checklist row renamed, then Home and the app swiped away: the new words were gone, as were words left in "A new item…"; the keyboard's ✓ had only put the keyboard down | What the save on leaving covers is what the editor holds; words a field keeps for itself are outside it |
| [2026-10-27](#a-way-out-that-never-asked-whether-the-note-was-saved) | code | With the phone unable to write, Back closed a new note whose save had just failed, and its words, kept nowhere else, went with it; a pin from the list read an unsaved note back over its words | A save whose answer nobody reads is a save that cannot fail -- in the code's eyes |
| [2026-10-27](#a-save-that-failed-then-said-it-had-saved) | code | With the phone unable to write, a note's new words stayed on the screen after its save failed; the next save found nothing to write, said "Auto-saved", and a restart lost them | A sweep for a mistake in a shape must also find the places that skip the shape |
| [2026-10-24](#a-rescue-copy-measured-by-how-much-shorter) | code | On the desktop, a short note replaced by a new line before it saved kept no rescue copy, its words gone; the phone, holding the desktop's thresholds by design, did the same | A guard has to measure the harm itself, not something that usually moves with it |
| [2026-10-24](#226-errors-that-a-whole-compile-did-not-have) | checking | The release's compile failed with 226 unresolved names, all in NoteEditor.kt, and passed run again; the day before the debug compile had done the same. Each time an incremental compile, each time the whole compile after it clean | A tool that recompiles only what changed is a tool whose answer can be stale: what ships is built whole |
| [2026-10-24](#thirty-two-copies-and-a-save-that-did-nothing) | checking | A full walk lost six checks in the export part: Save in Android's picker did nothing, because earlier walks had left 32 copies of the file's name in the emulator's Downloads, the most Android numbers | A check that leaves its work behind changes the ground the next run stands on |
| [2026-10-23](#a-separators-dashes-read-as-a-width-nobody-set) | code | The documentation's photograph of a table showed its header "How…": its separator was markdown's `---:`, which both apps read as a column set by hand to four characters | When a format borrows another's syntax, decide whose a form is where that is still known -- at the way in -- and leave what is stored alone |
| [2026-10-23](#one-change-told-twice-by-samsungs-keyboard) | code | With Samsung's keyboard every Enter after a word skipped a line, on the owner's phone and on no emulator; the field reported one change twice and the note applied both | When a report and a reproduction disagree, read what the real device sends -- a log that writes no letter of the person's |
| [2026-10-23](#a-field-that-looked-for-its-cursor-once) | code | On the owner's phone, Enter at the foot of a long note made a line under the keyboard and the screen stayed still; each checklist row added went the same way; on its side the rising keyboard showed another part of the note; Settings' list forgot its place under a page | An answer given once, when the focus came, holds only until something moves; and a fix made at one level of a pattern is worth a grep one level down |
| [2026-10-22](#what-a-persons-keyboard-found-that-the-walk-could-not) | checking | An agent playing an ordinary user, typing on the keyboard's keys, found in forty minutes what 259 checks had walked past: Enter after an autocorrected word, the cursor after Undo, a hidden last line, a tap on a marker | The walk proves the app's rules with letters it injects; a person's keyboard feeds them differently, and only someone using it finds the seam |
| [2026-10-22](#a-break-drawn-as-a-character-a-tap-could-land-after) | code | On the owner's phone a tap past a list item's end put the cursor at the next line's start: words went in front of its number, and Enter made an empty line | A field drawn differently from its text answers taps in the drawing's terms: test where a tap lands, not only where text goes |
| [2026-10-22](#a-check-that-held-by-one-frame) | checking | The walk's first run on an emulator just started lost its first typing and saw the dissolve cut; the fade had always held by about three captures, and on a busy emulator by one or none. Then a keyboard flag read too soon pressed Back off a table cell | A check whose margin is the machine's speed is not about the app: slow down what is watched, and wait for the machine the check was written on |
| [2026-10-20](#a-flicker-a-sharper-cut-made-worse) | design | A theme change flickered; made a cleaner cut, it flickered more on the owner's phone; dissolved over 320 ms, "better now" | A change of the whole screen wants easing, and the eye is the verdict |
| [2026-10-20](#letters-typed-beside-a-style-read-from-the-text-alone) | code | Letters typed after a styled word stayed plain and letters typed before it took its style — the desktop does the opposite at both edges | An edit worked out from the text alone cannot know where the caret was; typing knows, and should say so |
| [2026-10-20](#a-trash-emptied-by-a-window-nobody-chose) | design | Settings that could not be read made the start purge the Trash at the default 30 days, whatever window the person had chosen | When the rule for deleting is not known, delete nothing — and remember that it is not known |
| [2026-10-19](#a-sheet-dressed-for-the-phone-not-the-app) | design | On the owner's phone every sheet put a white bar of buttons over the dark app and the clock dark on dark, and Share's opened half-way, its last card under the buttons | A window of its own takes none of the app's settings; and walk with the phone's own navigation, not only the emulator's |
| [2026-10-19](#a-freeze-blamed-on-the-pdf-and-the-one-it-would-have-been) | code | "Not responding" while sharing on the emulator was the computer building, not the PDF; a 40-page PDF did hold the app 1.2 s, and a second Share cleared the first one's folder | Read a freeze's own report before changing code, then size the work by the largest input it allows; hold an order in a test, not in racing taps |
| [2026-10-19](#ports-read-side-by-side-with-the-desktop-they-came-from) | design | A merge lost a cell's words; read side by side, the port held about 58 more of the desktop's rules copied in part | A desktop rule lives in more than its function: read its whole route, and compare the two apps' answers regularly |
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

## A Trash restored without its window

**2026-10-27.** Found by the final audit: a backup from a notebook that
kept its Trash for 90 days, imported onto a phone just installed. The
import said "Also restored 3 trashed notes", the Trash page said "Kept
for 30 days", and the next start would have removed two of the three for
good, with their files.

The import brings the backup's Trash with each entry's `deleted_at`, as
it should; from the backup's settings it took the categories and the
painted days, and not `trash_retention_days`. The new phone had never
chosen a window, so it read the default, 30 — the class of
[a Trash emptied by a window nobody chose](#a-trash-emptied-by-a-window-nobody-chose),
reached by another road. The desktop's import did the same, and takes
the same rule, agreed for both apps.

**The fix:** a notebook that has never chosen a window takes the
backup's, when it is a whole number of days from 1 to 365. A window
chosen here is kept, and so is one lost with an unreadable settings file:
it was chosen, only not known. Two tests in `ImportingTest`.

**The lesson:** data that carries a date to be judged by a rule must
travel with the rule. Moved alone, it is judged by whatever rule the new
place assumes.

---

## Words a field kept until it was left

**2026-10-27.** Found by the final audit reading the code, twice over:
a checklist row's words changed — "yeast" to "yeast 7g" — then the
keyboard's ✓, then Home. The row read "yeast 7g"; the file read "yeast".
Swiped away from Recents, or ended by Android in the background, the app
came back with "yeast". Words typed in "A new item…" and never added went
the same way.

On a phone most notes are left by Home, not by Back, so the editor saves
when the app leaves the screen
([turning the phone closed the note](#turning-the-phone-closed-the-note)
is the same family). That save wrote what the editor model held — the
title, the text, the comments, a cell's words, all written into it as they
are typed. A row's new words were not: the row's field kept them, to
write them once it was left, one write per edit. Home leaves no field,
and ✓, with nothing told to happen, only put the keyboard down. A rename
was also kept by its place: removing a row above moved another row under
the waiting words.

**The fix:** the waiting words live in the editor model, and the save
made on leaving the note or the app writes them. ✓ writes a rename; a
row removed or dragged writes the waiting rename first; and a rename goes
only onto a row that still reads what it read, so a list changed from
elsewhere drops it rather than renaming another row. The autosave leaves
them alone, since it runs while they are being typed. Three tests in
`AppStateTest`; the walk's `checklist_words_kept` holds Home and a
process ended in the background.

**The lesson:** what the save on leaving covers is what the editor
holds. Words a field keeps for itself are outside it, however safe they
look on the screen.

---

## A way out that never asked whether the note was saved

**2026-10-27.** The same round of the final audit, the same full disk:
a new note typed, "Not saved" in red, then Back. The note closed, no
message followed, and the list had no such note. A new note lives only
in the editor until its first save; closed, its words went with it.

`leave()` was two lines, save and close, and the save's answer was never
read. Nor was it on the other ways out — another card, +, the rail —
which all went through the editor's flush, typed to return nothing.
"Move to Trash" alone asked. The flush had a second family of callers:
a pin, a category, the Trash, a day changed from the calendar beside the
note, an import. Each saved the editor first and went on, and those that
write the note then read it back into the editor — over the words that
had just failed to save. And the red line was all there was: drawn, not
said, so a screen reader never told anyone.

**The fix:** the flush says why it could not save. The ways out keep the
editor open and say so in the bar at the foot, which a screen reader
reads out, with **Leave anyway** — without it, a notebook that cannot be
written would hold the person in the note. A change from outside waits,
and says why; the editor's own links (the whole series, Restore
previous, a template) go on only after a save. The autosave says the
first failure aloud, and a save a tap asked for says each one.
`AppStateTest` holds the rules with the full disk the audit used; the
walk's `failed_save` holds Back.

**The lesson:** a save whose answer nobody reads is a save that cannot
fail — in the code's eyes. Typing a function to return nothing decided,
for every caller at once, that failure was not their business.

---

## A save that failed, then said it had saved

**2026-10-27.** The final audit's break-it round made every write of the
notebook fail, as a full disk does — a folder where the write puts its
temporary file — and typed into an open note. The editor said "Not
saved", as it should. Then Home, and back: the editor said "Auto-saved"
and the red line was gone. A restart showed the note without the words.

`Notebook.updateNote` changed the note in the live array, the one the
list and the editor read, and then wrote the file; when the write failed
it put nothing back. The next save compared the editor's words with that
note, found them equal, wrote nothing and answered Ok. Every other
writer in `Notebook.kt` builds a candidate and lets the screen catch up
after the write — the rule since
[an update reached the screen before the file](#an-update-that-reached-the-screen-before-it-reached-the-file),
on 2026-09-13, when every place that built a candidate was walked for
the same mistake. `updateNote` built none, so the walk never met it: the
editor's own save, the busiest writer of all.

**The fix:** the note is edited as a copy, written in a copy of the
array, and put in place only once the file has it. A test makes the
write fail with that folder, finds the old words in memory and on disk,
then frees the folder and finds the retry writes the new ones.

**The lesson:** a sweep for a mistake in a shape must also find the
places that skip the shape. Walking every writer that made a candidate
could not find the one that made none.

---

## A rescue copy measured by how much shorter

**2026-10-24.** Walking the desktop's test case 692 on its `.exe`, the
owner selected a note of three short lines, deleted them and typed a new
line before the autosave ran: no **↩ Restore previous**, and the three
lines were gone. `Notebook.shouldKeepPrevious` holds the desktop's
thresholds by design, and did the same.

The rule made a copy when the text got shorter by more than 40
characters (30% of it, or 200) or was emptied. Thirty characters
replaced by ten shrink by twenty, under the bar; a paragraph replaced by
one as long does not shrink at all, in a note of any length. The rule
measured how much shorter the text was, while what it guards is how much
of it is gone.

Both apps now count what is gone (`textLost`: the old text's length
less the longest start and end the two share), with the same bars, plus
one for most of a short note — 70% of it, 15 characters at least. A word
changed, words added, a tiny note retyped and a title renamed still keep
no copy. The desktop's story: ["The safety net that measured the wrong
thing"](https://github.com/AlexandraMoldovan95/noteOrganizer/blob/main/docs/ISSUES_LOG.md#the-safety-net-that-measured-the-wrong-thing).

**The lesson:** a guard has to measure the harm itself, not something
that usually moves with it. A proxy holds until the day the two part
ways.

---

## 226 errors that a whole compile did not have

**2026-10-24.** Building 0.11.1, the release's Kotlin compile failed:
226 errors, every one in `NoteEditor.kt`, every one a name from another
file of the app — `LocalPalette`, `tableBlocks`, `FindBar`, `TableView`
— "unresolved", while the classes those files hold (`FormatSpan`) were
found. Run again on its own, the same sources compiled clean. The day
before, the debug compile had failed the same way, with the same 226.

The Kotlin daemon's logs told the two compiles apart. Each failure was an
incremental compile — 8 seconds on the 23rd, 13 on the 24th — which
recompiles the files that changed against the classes kept from last
time; each pass after it was a whole compile, 23 and 21 seconds. What
the failing ones had lost was the record of the module's top-level
declarations, which is why classes resolved and functions did not. A
fresh copy of the repository, taken through the same steps, did not fail:
the fault is in Kotlin 2.2.10's incremental compilation, and its trigger
is not known.

A failure that loud does no harm. The same fault made quiet would put
stale classes in what ships. So the release compile is now never
incremental (`app/build.gradle.kts`): about 10 seconds instead of 4,
and nothing kept from an earlier compile can reach a build that goes to a
phone. The debug compile stays incremental, for speed, the owner's choice
(2026-10-24); should it fail this way again, a whole compile is the
answer, not a retry. The setting is made after the build is evaluated:
set in a plain `configureEach`, the Android plugin's own setting came
later and won, which a probe printing the task's `incremental` showed.

**The lesson:** a tool that recompiles only what changed is a tool whose
answer can be stale. Speed is worth it while working; what ships is built
whole.

---

## Thirty-two copies, and a Save that did nothing

**2026-10-24.** A full walk lost six checks in the part that exports a
note through Android's own "save as": the picker opened, offered the
desktop's file name, and its Save was pressed — and no file arrived, no
"Exported to" followed, and the picker stayed on the screen. Run again
alone, the part failed the same way every time. Nothing in the app or in
that part had changed.

The picker's own list said why: `Trip_ day 1_2 (32).txt` at its top.
Every walk since the part was written had saved its two files into the
emulator's Downloads and left them there — clearing the app does not
reach that folder — and Android names a second file of one name
"name (1)", up to 32, then refuses the next without a word: Save does
nothing. The backups the walk saves were climbing the same way, a day's
worth of runs at a time.

So the walk now takes out what it saves once it has read it back, and
clears what earlier walks left before it saves (`clear_from_downloads`),
each with a check that the folder holds none of its files afterwards.
Downloads keeps one file of the walk's, `walk-receipt.txt`, which it
pushes under the same name every time.

**The lesson:** a check that leaves its work behind changes the ground the
next run stands on. Here the residue was invisible for weeks and then
became a limit all at once; a part that makes a file should leave the
folder as it found it.

---

## A separator's dashes, read as a width nobody set

**2026-10-23.** The photograph of a table taken for this repository's
documentation showed its header as "How…", the column barely wider than
"1500", and its caption promised a right-aligned column that the picture
did not have. The note had been written for the camera the way GitHub's
markdown writes such a table: `| What | How much |` over `| --- | ---: |`.

Not the camera's fault, and not the phone's alone. Both apps keep a
column's hand-set width in the separator, as its dash count, and draw a
set width exactly, cut short with an ellipsis. Three characters or fewer
was already read as markdown's spelling rather than a width — but
markdown spells an aligned column in four or five, and a column as wide
as its header in more. Any table pasted as text, from a chat's Copy
button or a `.md` file, came in cut the same way. (And a cell's alignment
is its padding in both apps, so the colon right-aligned nothing.)

The file cannot say whose dashes a separator holds; a paste can. So
`Pasting.forgetWidths`, the desktop's `tables.forget_widths`, spells every
separator in text from outside the app at the minimum — `---:` becomes
`--:`, colons and `~` kept — inside `htmlToNote` for a page's HTML, and
through `foreignPlain` for words: a note's paste, its plain paste, a day
of a series, and a template's text, whose plain field takes it from
`forgetPastedWidths` when more than a line arrives at once. The app's own
copy keeps its dashes, and notes already saved are read as before: a
changed reading rule would have widened every column someone narrowed on
purpose. The paste fixture holds the port to the desktop's answers on 86
texts, and the walk measures the drawn header.

**The lesson:** when a format borrows another's syntax, the borrowed form
arrives written the way the other world writes it. Decide whose it is
where that is still known — at the way in — and leave what is already
stored alone. The seed for the photograph is now written as the app
writes a table.

---

## One change told twice by Samsung's keyboard

**2026-10-23.** On the owner's phone, Enter after a word made an empty
line and put the cursor on the line after it. On the emulator it never
did, typed on Gboard's own keys. A photograph of the cursor there showed
it where it belonged, and for a while the report looked like a drawing
problem.

It was not. A test note on her phone, read back through the app's own
screen tree, held `ABC\nGgh\n\nGff\n\n`: one break after "ABC", two
after every other word. The difference was the keyboard's: Samsung's,
with predictive text on as it comes, keeps a word "composing" while it
offers completions, and "ABC" in capitals it did not. Turned off, the
breaks were single — her test, which settled the cause.

What the keyboard sends then was read from the field itself: a build
logging each change it reported, as a position, a count and the breaks,
never a letter of what was typed, installed with her consent and read by
the app's own tag. Enter on a composing word came as one batch, the word
finished and the key pressed together, and the field reported the result
**twice**, a millisecond apart, both times from the value before it. The
note's text applies a change to the body as it is now, through the field's
segment as it was drawn; the second report was applied to a body the
first had already changed, and broke a second line. Every other field —
the title, the comments, a cell, a template — writes its value whole, so
a second report changed nothing there.

The same change from the same value within 150 ms is now taken once
(`Editing.Once`, held by `EditingTest`): a person cannot repeat a
keystroke from the very same state that fast. On her phone the log then
showed each second report skipped, and two Enters pressed on purpose
on an empty line, 270 ms apart, both kept.

What is not covered: no emulator here can send Samsung's batch, and the
walk cannot check the field's wiring; the rule is held on the JVM and
the fix was seen on the owner's phone. The test plan marks it for her eye.

**The class:** a report a reproduction cannot meet is evidence about the
device, not about the person. Read what the real device sends before
changing anything, in a form that carries no word of theirs.

## A field that looked for its cursor once

**2026-10-23.** On the owner's phone, writing at the foot of a long note
with the keyboard up: Enter made the new line under the keyboard, and the
screen stayed where it was. What she typed next could not be seen until
she scrolled to it with a finger. Adding checklist rows did the same: each
row pushed the "A new item…" box a row lower, and the row just added went
out of sight with it.

The emulator showed it at once, in a note of thirty lines: Enter on the
last line above the format bar, a word, Enter again, and the screen never
moved. A Compose text field over a `TextFieldValue` or a `String` brings
its cursor into view when it takes the focus, and not after; in a column
that scrolls, every line it grows past the bottom of the screen goes there
unseen. Every field of that kind that can grow in a scrolling column had
it: the note's text, its comments, a table's cell being written, and a
template's text and comments.

They now share one answer, `Following`: after each change to the words,
once the field has laid them out, the place where the change ended is
brought into view. A template's comments, a Material field with no layout
to ask, moved to the newer field over a `TextFieldState`, which keeps its
cursor in sight itself. The checklist asks once more after each row it
adds.

Checking it on its side found the other end of the same answer. A tap on
line 2 of a thirty-line note, the keyboard rising, left lines 20 to 22 in
sight and the cursor nowhere: as the screen shrinks, Compose keeps the
focused *field* in view, and a field taller than what is left is in view
whatever part of it shows. The note's text now also asks for its cursor's
line at each height the keyboard passes through. It still jumps away
first and scrolls back: Compose's own move comes first at each height, and
a request can only answer it. On an emulator just started the way back
took two seconds after the keyboard was up, which a first check, looking
once two seconds after the tap, read as a failure; it now looks until the
line is there, eight seconds at most.

The walk now presses Enter again and again at the foot of what shows, in
the text and in the comments, and adds three rows with the keyboard's tick
in a long note, measuring how far the note moved and where its last line
is now. A build without the fix fails each: the note moved 0 pixels, its
last line 116 under the format bar; the newest row out of sight.

**The class:** an answer given once, at a moment (the focus taken, the
keyboard risen), holds only as long as nothing moves after it, and typing
moves the very thing it brought into view. Ask again at each change, once
it is laid out.

The same evening's report held a sibling of an older fix. Back from a page
low in Settings, the Trash, the list came back at its top. In 0.9.0 each
*tab* was given a holder that keeps what it remembers while out of sight
([the list built again on Back](../CHANGELOG.md#090--2026-10-20--the-first-test-on-a-real-phone));
the screens *inside* a tab, Settings' list under its pages and the
calendar's month under its History, were swapped the same way and kept
nothing. They now keep theirs. A fix made at one level of a pattern is
worth a grep for the same pattern one level down.

## What a person's keyboard found that the walk could not

**2026-10-22.** After the tap past a line's end turned up on the owner's
phone, she asked for an agent that uses the app the way an ordinary
person does, finds what is wrong, and hands it over to be fixed. It ran
on the emulator for forty minutes with one rule beyond the owner's
safety ones: anything typed in or near a list, after moving the cursor,
was typed by tapping the on-screen keyboard's letters, Enter and
Backspace, the way a thumb does.

It reported ten findings. Four were confirmed on the emulator at once,
each in a place no check had been: Gboard's autocorrect sends the
corrected word and the line break as one edit, which the list did not
take for an Enter; after Undo the cursor was nowhere and the next letter
went to the end of the note; the empty line a list leaves at the end of
a note was not drawn; a tap on a marker put the cursor before it. The
rest were a rescue copy that outlived its use, a checklist's ✓, Enter in
the title, an import's wording, a layout on a phone on its side, and an
Undo in two steps. Every time it compared, the saved file matched the
screen.

None of it was within the walk's reach, and the reason is how the walk
types: it injects finished letters with `input text`, taps by the words
on the screen, and puts the cursor only where it is already going. A
keyboard composes a word, corrects it, and sends it with the break; a
person taps past a line's end, on a number, on an empty line. The walk
proves the app's rules; the keyboard is a second program feeding them,
in its own way.

The four confirmed are fixed, each with a check that failed on the build
before: three in a walk part of their own, the autocorrect one in the
JVM tests, which can send exactly the edit Gboard sends. Two more
behaviours, the rescue copy and the tap on a marker, were the desktop's
too, and the owner chose new ones for both apps.

**The class:** a check that only injects input tests the rules and
never the way input really arrives. An exploratory pass by someone using
the real keyboard, reporting rather than fixing, finds the seam between
the two; every finding is then confirmed by hand before it is believed,
and fixed with a check of its own.

## A break drawn as a character a tap could land after

**2026-10-22.** On the owner's phone, in a numbered list typed item by
item: a tap after item 2, to finish its idea, put the words in front of
"3." instead, and Enter after item 3 made an empty line rather than a
"4.". On the emulator it took two taps to see: a tap *on* a letter
landed where it was aimed, and a tap to the right of a line's words
landed at the start of the next line.

The cause was in the drawing, not in the list rules. Since lists were
drawn a line at a time ([the double spacing](#a-list-drawn-double-spaced-then-bunched)),
every line is a paragraph of its own, and the break between two is
drawn as a zero-width stand-in kept at the end of the line's paragraph:
one character for one, so every offset stays where the text has it. But
a stand-in is a character the layout can be tapped after, which a real
line break is not. A tap past a line's end asks for the end of that
line, and the end of the line is after the stand-in, the first position
of the next line. A field showing a list draws every line this way,
plain lines included.

The tap is now put back: when the cursor lands just after a break and
the press came down on a line above the one it landed on, it moves
before the break (`tapLanding`). A walk part taps past the end of an
item, of a plain line, and of an item before Enter, and reads the file;
the build before failed all three on every run, as the phone had.

The next day the drawing moved again. An empty last line had been kept as
a real break inside the line above, so that it was drawn at all, and it
wore that line's style: centred on its own, its cursor stayed at the left
until a letter came; put back to the left under a centred line, it stayed
in the middle (the owner's phone, 2026-10-23). It now has a stand-in of
its own after the text, one character more than the text, with a mapping
that sends it back to the end. And a tap past the end of the line above
then landed on the end of the text, where an unfocused field already keeps
its cursor: the field reported no change, and the correction that runs on
a change never ran. The press is now also put back a frame after it is
let go, when nothing was reported; the walk's tap at the end of the last
item failed without it.

**The class:** a field drawn differently from its text answers taps in
the drawing's terms. Where the drawing adds or moves even an invisible
character, test where a tap lands, not only where the text goes. A walk
that types only where the cursor already is never asks.

## A check that held by one frame

**2026-10-22.** The walk's first run on an emulator started a moment
before (2026-10-21) failed its first part: its first typing never
reached the editor, failing the checks after it in a chain, and the
capture burst behind the dissolve went from the light theme to the dark
one between two frames, which is what the check reads as no fade at all.
Walked again once the emulator was warm, everything passed.

Measured after a fresh start, the emulator's processor was fully busy
until 68 seconds of uptime, quiet for twelve, busy again until 102, and
quiet after that, at 1% once warm. `boot_completed` had said 1 at 30.
And the dissolve's check never had much room: the fade lasts 320 ms and
the emulator takes a capture every 100 to 130 ms, so a warm run caught
about three shades between the two themes. Started a moment after a
fresh start, the old check passed again with exactly one. It passed or
failed by a single frame.

Two changes, one for each failure. Before its first step, the walk now
waits until the processor has been under a quarter busy for 30 seconds
running, past twice the false lull. And the dissolve is watched with
the system's animator duration scale at five, which Compose follows, put
back afterwards. On an emulator just started, without that wait, the
burst now holds twelve shades between 242 and 35. A build with the fade
cut at once still goes from 242 to 35 in one frame, and fails. The lost
typing did not come back to be watched; the wait is there for it, the
first part being the one that had run on a busy machine.

The first full walk with both, begun on an emulator just started, then
lost two checks in its nineteenth part: a table cell chosen, the
keyboard put down, and the table's bar gone. The part puts the keyboard
down twice, once inside `choose_cell` and once after it, and
`keyboard_down` asks the system whether the keyboard is up. The answer
stays "up" for over a second after the Back that hid it on an emulator
started that day; read in that second, it pressed Back again, off the
cell. Four runs in four failed there, the old walk's too, where it had
passed all day on an emulator two hours old. `keyboard_down` now
returns only once the answer says the keyboard has gone, and the part
passed four in four on the same emulator. `take_screenshots.py` asked
the same question a second after its BACK and could send a second one
the same way; it waits for the answer too. It does not wait for a
settled emulator: a shot whose screen does not say its words is
refused, so a busy emulator costs it a retake, never a wrong picture.

The same day, a part walked on its own failed three checks for one
cause: the backup was never made. The walk tapped "Save a backup", waited
three seconds, and looked once for the Save of the phone's own picker,
another app, which on that run had not drawn it yet; nothing was pressed.
The walk's three picker steps now wait for Save to be offered, up to
fifteen seconds, and say so by name when it never is.

On 2026-10-23 a full walk on an emulator just started lost three checks of
its first part, both passing at once on a warm one. The dissolve, slowed
five times to 1.6 seconds, fell between two captures, each now slower
than the whole fade; it is watched at ten times, by forty captures. And
Undo was tapped where it had been: the photograph of the offer made its
own look at the screen, a second look found Undo, and the ten seconds the
offer lasts were gone between the two. One look now finds it, the
photograph is a plain capture, and the tap follows at once. The next run,
also on an emulator just started, lost two more of the kind: the
calculator's panel looked for 1.5 seconds after Back, before the keyboard
had gone, and the cursor's line on its side looked for before the note had
scrolled back to it. The first now waits for the keyboard to be down, the
second looks until the line is there. The run after passed all 305.

**The class:** a check whose margin is the machine's speed passes on a
fast day and fails on a slow one, and neither verdict is about the app.
Slow down what is watched rather than hurrying the watcher, and wait for
the machine the check was written on.

## A flicker a sharper cut made worse

**2026-10-20.** The owner saw the change between a light and a dark
theme flicker, "as if the picture builds itself towards dark". Measured
on the emulator, capture by capture: the page cut from one theme to the
other in a single frame, and the phone's bars took the new contrast a
frame or two later, through an effect that set the whole window up edge
to edge again, over a window that stayed white underneath.

The first answer made the cut cleaner: bars and window in the same frame
as the page. Measured, it was cleaner. On her phone it flickered **more**.
A whole screen turning from white to near-black in one frame is itself
what the eye reads as a flash; the stagger had only been smearing it.

The second answer stopped cutting. The screen as it is is kept as a
picture (`ThemeVeil`: the app's content recorded into a graphics layer
on every drawing, taken as a bitmap on the change), the theme changes
underneath, and the picture fades over 320 ms; the bars follow halfway.
Captured again, the page passes through 187 and 59 on its way from 240
to 38. On her phone: "it is better now". The walk keeps it: a capture
burst while Dark is tapped must hold a shade between the two, and the
mutant that cuts at once leaves none.

**The class:** a transition measured correct is not a transition seen as
smooth. When the complaint is how a change looks, the person's eye is
the verdict, and a cleaner instant is still an instant — a change of the
whole screen wants to be eased, not sharpened.

## Letters typed beside a style, read from the text alone

**2026-10-20.** Found by the audit: "ly" typed after a highlighted
"quick" came out plain, and "Very " typed before a bold "Important" came
out bold. The desktop does the opposite at both edges — Tk gives typed
letters the tags on both sides of them, so nothing joins a word from
the front, and its `_spread_typed_run` gives letters glued after a
styled word the word's styles, up to a space.

The phone moved styles through typing by the rule every edit uses:
compare the text before and after, find what changed, keep each style's
start where it was. That rule is right when nothing says where the edit
happened — a paste, a table rebuilt — and wrong for typing, because
"keep the start" puts letters typed at a style's first character inside
it, and "keep the end" leaves letters typed at its last outside. It was
also blind where the text repeats: "l" typed into "he|lo" is found at
either "l".

Typing knows where the caret is. The edit is now found from the caret —
what follows it is the old text's end — and the desktop's rules applied
there (`spansAfterTyping`). The same place holds the pending styles of
a B pressed with nothing selected. Three of the mutants run on it
survived for a good reason: each removed a check another line already
made, and the redundant checks were taken out with them.

**The class:** a rule that works an edit out from the text alone is the
fallback for edits that cannot say where they happened. When an edit can
say — typing has a caret — use it, or the rule decides what the person
never meant.

## A Trash emptied by a window nobody chose

**2026-10-20.** The owner asked what happens to the Trash when a new
version comes out: a person keeps deleted notes for 90 days, a release
arrives while one has waited 45 — is it lost? It is not. The purge does
not belong to releases; it runs at every start, by the window the
person chose, and an update from the store keeps the app's files.

Reading the code for that answer found the case where it would be.
The window lives in `settings.json`. A file that cannot be read — or a
release that, by a slip, reads the window wrongly — gives the default,
30 days, and the start that follows purges by it: the notes kept for 90
go at 31. Worse, a fresh `settings.json` is written soon after, and it
reads as "never chosen", so even skipping one start would only have
moved the loss to the next. And the window's clamp, right for showing
it, read a hand-edited 0 as one day.

Now the start purges only by a window it can read — chosen and a whole
number from 1 to 365, or never chosen at all, the default as the
desktop reads it. Settings that could not be read are marked
(`phone_trash_window_unknown`) before anything writes over them, and
while the mark stands nothing is purged; the list says so at the start,
the Trash page says so and counts no days, and choosing a window there
ends it. The desktop's
[purge](#the-desktops-startup-purge-goes-by-a-window-it-could-not-read)
does as the phone did, and waits for its own change.

**The class:** a deletion that runs on its own must know its rule, not
assume it. When the rule cannot be read, delete nothing — a note kept
too long can still be deleted, one deleted too soon cannot come back —
and remember that it is not known, or the next run assumes it again.

## A sheet dressed for the phone, not the app

**2026-10-19.** Share, checked on the owner's phone, opened its sheet
with a white bar of buttons across its bottom and the last card, Share
code, under the buttons. Her phone is light and the app dark, and her
phone has three buttons where the emulator had gestures; on the emulator
made the same, every sheet in the app did it, and the clock above them
was dark on dark.

Two causes, neither in Share. A bottom sheet is a window of its own, and
Android draws a window's bars for whatever that window asks — the
sheet asked for the phone's light, not the app's dark, which only the
app's own window had been told. And a sheet taller than half the screen
opens half-way by default, the rest below the screen's edge until it is
dragged up: what looked like a card under the buttons was a sheet never
opened. Padding the sheet by the buttons' height, tried first, changed
nothing, which is how the second cause showed itself.

Every sheet in the app is now one `AppSheet`, which asks for the app's
light or dark and opens whole. A walk part puts the emulator on three
buttons and the app dark on a light phone, and reads it: the sheet's
last line above the buttons, by its box; the buttons' bar dark and the
clock light, by the screen's pixels, since the bars are in no UI tree.

**The class:** a window of its own takes none of the app's settings —
its bars, its theme — and a framework's default opening is a choice
too. Walk with the phone's own navigation as well as the emulator's: the
gestures had hidden both.

## A freeze blamed on the PDF, and the one it would have been

**2026-10-19.** Walking the new Share on the emulator, Android said "Note
Organizer isn't responding". The PDF was the obvious suspect: it is
drawn on the main thread, as a WebView must be, and the sheet had just
closed onto it.

Measured instead of believed, it was not the PDF. The report Android
wrote at the moment of the freeze showed the app's main thread idle,
waiting for work; in the same seconds every process on the emulator was
dropping frames, the system's own among them — the app was being built
on the same computer. The 13-second frame logged earlier was the app's
first after its install. Timed step by step, a one-page PDF took half a
second from the tap to the share sheet, a three-page one 0.7, and the
longest the app was held at once was 0.2 s.

The suspicion still found something, by asking what the largest note
does. Forty pages, the most a PDF here holds, held the app 1.2 s at
once — 0.29 s drawing the pages, 0.85 s writing the file — and 73
frames were dropped. A phone a few times slower than this emulator
would leave a touch unanswered past the five seconds after which
Android calls an app not responding. The file is now written on another
thread, and for forty pages the longest hold is the drawing, 0.35 s.

And while it is made, the Share button is there again. Asked for a
second time, a second PDF cleared the folder the first was still
writing into. It is now refused with a word — "Still getting the last
one ready" — and the walk asks twice, tapping places read once with no
UI dump between: one share sheet, a message on the screen, the whole
PDF. That check passed with the refusal taken out. The first share
then failed, its folder cleared, and said so in a message the screen's
pixels could not tell from the refusal's, and the second opened the one
share sheet. The rule moved to the rules side, `Outgoing.OneAtATime`,
where a unit test holds the first share at its drawing and asks again:
each of its four mutants is killed there.

What no check holds: how long the pause before the share sheet feels on
a slow phone. It is in the test plan, for a person.

**The class:** an app that freezes on an emulator while the computer is
building may be the computer. Read the freeze's own report and the other
processes' frames before changing code — and then size the work by the
largest input it allows, not by the test's. And an order between two
of a person's asks belongs in a test that can hold the first one still,
not in taps that race it.

## Ports read side by side with the desktop they came from

**2026-10-19.** On the owner's phone, a cell with words merged with the
empty cell beside it came out empty. The phone merged by writing the
merge's marker over the chosen cell — the desktop's `_grid_set_marker`,
one path of two — while the desktop's merge proper,
`_grid_merge_selection`, joins every cell's words and says why in its
comment: "Merging 'A' with an empty neighbour used to keep whichever cell
was the anchor -- and lose the text". The phone had copied the older
path.

Asked whether more of the port was like that, five readers went through
the app area by area, each with the desktop's current code beside it and
the phone's notes on what differs on purpose. They found about 58
differences on the phone and 7 on the desktop. Almost none were rules
the desktop changed after the port: they were rules it already had,
written in another function, a docstring or a guard the port did not
read to the end.

The tables' share, fixed together:

* the new merge keeps every word, as the desktop's does, and carries
  their styles;
* "Row → Below" on the header pushed the separator to the third line,
  where the desktop reads none;
* the phone read a separator on any line, the desktop only in the first
  two — and the merge being written numbered its rows one way and wrote
  them another, so on such a table it would have written over the wrong
  row: found by reading, before anything ran;
* a table squared up wrote an empty cell into its separator;
* structural edits re-padded every row and let the style remap drop
  everything between the first change and the last, where the desktop
  carries each cell's styles cell by cell;
* a right-aligned cell typed full lost the room for the padding that
  says it;
* and smaller ones: the header drawn only with a separator, a caption
  centred alone, the separator's colons drawn over a cell's own padding,
  Wider measured from the phone's drawing cap, the last column hidden
  with no way back, "<" typed turning a cell into a merge.

**The class:** a port is a copy of a rule as the porter read it. The
desktop's rule lives in more places than its function — in another
function on the same menu, in the guard at the top, in the comment that
says why — and a port that reads one place carries that one. Read the
desktop's whole route for a behaviour, and compare the two apps' answers
to the same input from time to time, not only when something breaks.

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

*Since 2026-10-19* the app names no mail app: Share hands a note to the
phone's own share sheet, which needs no `<queries>`, and the surviving
mutant went with the code it mutated.

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

Findings, not failures. Most wait on a change in the desktop repository
rather than here; the first section is what the phone still owes from
the audit of 2026-10-19.

### The phone's, from the audit of 2026-10-19

In the owner's order, what loses data or writes a file the desktop reads
differently came first, and is fixed — the tables' share
([the story](#ports-read-side-by-side-with-the-desktop-they-came-from)),
and the rest: Select all with a group folded, a restored note's
identity, a backup's rescue copies, the import's "is this the same
note?", the share code's limits, a pin's "updated", shared ids, bold
with italic, a line's alignment through Enter and Backspace, History's
days off the schedule and a deleted day's Undo, the rescue copy's start,
and the two ways the app could stop on a date (all in the
[changelog](../CHANGELOG.md)).

The rest followed on 2026-10-20, each proved by a test and its mutants:
the two parts of `normalize_note` not yet ported (the per-line ceiling
on styles, a day's own category the phone lacks) and the desktop's old
way — typing next to a styled word
([the story](#letters-typed-beside-a-style-read-from-the-text-alone)),
a colour tapped twice, B with nothing selected (the desktop's now, by
the owner's choice), the calculator's Insert in a cell, a day's own
colour losing the series' formatting, a cleared colour's Undo
overwriting what came after, the series banner's counts, a single
imported note offered to open, 3-digit colours, Unicode spaces in the
duplicate check, a stale list entry's indent, underline with strike,
pasted bullets' depth, a table pasted into the comments, a category
name's length, and the three labels of a day of a series. 🔗 on an
empty note went with 🔗, Share refusing an empty note.

Left, each small and none losing anything: the History's search worked
out again on every letter (not measured on a large notebook), its rows
showing fewer counts than the desktop's, and the desktop's "notes for
today" reminder, not built.

### Seven DESKTOP findings from the audit of 2026-10-19

**All seven fixed in the desktop's 0.95.391** (2026-10-20, its issue
#287), each with a test and its mutant; kept here as found.

* **Right-click → Merge with the cell to the left, or above, loses the
  merged cell's words** — `_grid_set_marker` writes the marker over
  them; the selection's merge, `_grid_merge_selection`, keeps them.
* **"Insert row above" on the header row of a table with a separator**
  pushes the separator to the third line, where `separator_index` no
  longer reads it.
* **`read_date` can raise on superscript or circled digits** ("17²"):
  they pass `_DATE_RUNS`, then `_DATE_FIGURES.match` returns None.
* **Bold, or italic, over a partly italic or bold selection** strips the
  other style from the whole selection.
* **Enter with the cursor before or inside a list marker** writes a new
  marker there ("• • milk").
* **Enter with words selected in a list item** inserts the new item and
  leaves the selection.
* **Backspace with a selection ending just after a marker** climbs the
  list instead of deleting the selection.

Deleting a column a merge passes through keeps the merge on the phone;
the desktop dissolves it. The phone's is kept, as losing less.

### The DESKTOP's startup purge goes by a window it could not read

**Fixed in the desktop's 0.95.391** (2026-10-20): it keeps the lost
window as a marker of its own, `desktop_trash_window_unknown`, as the
phone does, and purges nothing by a window it cannot read.

Found 2026-10-20, from the owner's question about releases
([the phone's fix](#a-trash-emptied-by-a-window-nobody-chose)). The
desktop's `load_settings` answers `{}` for a `settings.json` it cannot
read, `trash_retention_days()` then answers the default 30, and
`purge_expired_trash` empties by it at the next start — a person who
kept the Trash for 90 days loses what was deleted 31 to 89 days ago.
And the clamp `max(1, min(days, 365))` reads a hand-edited 0 as one
day. The phone purges nothing in both cases, and remembers a lost window
until one is chosen again. The desktop's change goes through its own
process (the interaction pass, a plan, approval).

### The DESKTOP's automatic Trash purge can delete a file a living note uses

**Fixed in the desktop's 0.95.391** (2026-10-20): the startup purge and
removing one attachment both spare a file still in use, as suggested
below.

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
