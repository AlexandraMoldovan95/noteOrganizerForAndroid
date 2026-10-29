# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Twenty of the seventy-nine are
about the checking.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
| [2026-10-29](#a-days-text-in-a-copy-the-other-program-erased) | design | A turned-off series' days lived in the note on the desktop and in the series' copy alone on the phone: the desktop's next copy of words erased the phone's, and the phone's way of carrying days into every copy put a restarted series' old deleted day back; and a template being written was lost when Android ended the app behind Home | A thing kept in two places is kept by neither: where a value lives is part of the format, proved by running both programs on the same note |
| [2026-10-29](#a-cap-that-cut-what-it-was-meant-to-keep) | code | The audit's sixth round: a row's 2,000-letter cap cut the row's own end when a letter or a paste went in at its start; a row moved across the line of rows not drawn vanished among them; a row past the limit showed no word of itself, Find stopping on words nobody saw; the template editor drew 1,000 rows at once; Redo left the cursor above a table typed at the end; comments wiped kept no rescue copy | A cap is put on what comes in, never on what is already there; and a window drawn in part follows the row a person moves, as it follows the one a search steps to |
| [2026-10-29](#a-word-in-the-log-written-by-another-program) | checking | The full walk failed "99,000 characters of words reach the share sheet" with the sheet up: the log held "TransactionTooLargeException", written by Play services about a service of its own; three more checks read every process's log the same way | A check of the log reads the lines of the processes it is about, named by their pid before the act |
| [2026-10-29](#a-ceiling-for-hostile-notes-that-ordinary-ones-reached) | code | The audit's fifth round: a checklist outside round four's whole-note budget, 18 million letters from a code of 38 KB; Undo's 200 whole copies of a long note; fifty weekly tables reaching the ceiling of 100 pieces, their end read-only and unfound; a cursor left in a field its line had gone out of; a list's markers read as words lost | A limit meant for hostile sizes is set where ordinary notes cannot reach it, and what lies past it stays found and readable; a cursor whose line leaves every field is placed, never left |
| [2026-10-28](#what-one-note-draws-and-what-is-said-before-it-goes) | code | The audit's fourth round: what a note draws was held piece by piece -- ten tables of 1,000 cells drawn at once, 3,000 tiny tables, 200,000 short lines as one field; a rescue copy replaced unsaid three more ways the desktop had settled; one identity worn by two notes; and warnings given under the share sheet, after the tap, or in letters where Android counts bytes | A limit set for one piece limits nothing when the pieces can be many; a warning the person cannot read before choosing is an error they meet later |
| [2026-10-28](#guards-that-asked-another-question-than-the-cost) | code | The audit's third round went around the second round's guards: words joined by no-break spaces, a hundred lines each under the limit, a run inside a table's cell, a template's row, a bar quoting a row; a plan applied by place after the list moved; an "off" read from the wrong file; and a ceiling on what is handed to another app that was reasoned, and failed on the emulator at 180,000 characters without a word | A guard asks of what actually costs, everywhere it is drawn; a ceiling set by another program is measured on it, not reasoned |
| [2026-10-28](#fixes-that-were-holes-of-their-own) | code | The audit's second round found a third of its findings in the first round's fixes: a rule ported half, a count of the wrong cells, a flag set before the act, a switch that did not survive, a timer counted from the asking; and limits that held at the door, a 150,000-letter line from a 300-character code among them | A fix is a new surface: the round after attacks the fix itself, and a twin is read the day it changes |
| [2026-10-28](#answers-sent-to-a-screen-that-was-gone) | code | With the app's process ended behind Android's picker, Export as file left an empty file and Attach files attached nothing, without a word; and Back while a large file copied lost the file and left the copy in the cache | An answer from another app goes to what is always there; work that must finish outlives the screen that asked for it |
| [2026-10-28](#a-crash-in-front-of-the-crash-foretold) | code | The audit foretold that a template with 600,000 letters of comments would stop the app as Home was pressed; on the emulator it stopped sooner, as the template opened. With that fixed, Home stopped it exactly as foretold | A reproduction that fails earlier than predicted has found a second bug in front of the first: fix it and run again before calling the first disproved |
| [2026-10-27](#on-and-off-told-by-a-tint) | design | Whether B, a list, an alignment, a marker or a repeat day was on was a fill differing from off by 1.04 to 1.33 to 1, and nothing at all to a screen reader | A state needs a second sign for the eye and a word for the ear; a colour is neither alone |
| [2026-10-27](#arranging-only-a-finger-could-do) | design | Notes in Custom order and a checklist's rows moved only by dragging their handle; TalkBack read "Drag to move", and neither it nor Switch Access could drag | Every gesture needs a door that is not a gesture |
| [2026-10-27](#a-backup-promised-without-its-conditions) | design | Settings → Backup promised that Android keeps a copy of the notes, and the Data safety reasoning that the copy was end-to-end encrypted; Android stops at 25 MB, encrypts with the phone's lock only where there is one, and the person had no way to say no | A promise about another program's behaviour carries that program's conditions, and the person gets a say in it |
| [2026-10-27](#two-checks-that-read-the-screen-at-the-wrong-moment) | checking | A message read, then photographed after its four seconds were up; a selection the walk thought it had left, because the words it waited for never went away | A check reads what it acts on at the moment it acts, and waits for the change it caused, not for words that were there all along |
| [2026-10-27](#a-table-moved-out-of-its-grid-and-into-one-field) | code | A table too large for a grid, moved into the text around it, opened in nine seconds: one field of 362,000 letters, laid out whole and drawn whole, every line copying all its text again | Measure where the time goes before choosing where to move the work: moved whole, it costs the same in another place |
| [2026-10-27](#a-rows-second-letter-judged-by-the-last-drawing) | code | The walk renamed "flour" to "flour 500g" and pressed Home; the file read "flour ". The row's second letter was judged by a drawing that had not caught up with its first | A callback decides from the state as it is when it runs, not from what the last drawing captured |
| [2026-10-27](#a-rescue-copy-let-in-whole) | code | A backup's rescue copy came in exactly as written, and "↩ Restore previous" put it into the note: fifty thousand checklist rows past the cap of a thousand, two thousand styles on one line past the cap of five hundred | A ceiling holds only if every road into the note passes it, the one through a copy included |
| [2026-10-27](#a-question-asked-of-every-note-to-answer-one) | code | A 4.5 KB backup held a rescue copy of four million letters and a note that nearly matched it; every load asked of every note whether a restore would keep its words, by a search that took fourteen seconds, at each start and after each save | Work out an answer where it is asked, for what it is asked about; and when porting Python's `in`, port its cost too |
| [2026-10-27](#a-million-brackets-past-a-catch-for-exceptions) | code | A share code of 1,354 characters, or a backup of 1 KB, held a million nested brackets; Android's parser went one call deeper for each, and the StackOverflowError it ended in was no Exception, so no guard caught it and the app stopped | A catch names what it expects; what comes from outside is measured before a recursive reader is handed it |
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

## A day's text in a copy the other program erased

**2026-10-29.** The audit's sixth round left two questions for the owner,
and she answered both. The first was where a turned-off series' days
live. The phone, up to 0.12.0, moved them out of the note into the
series' rescue copy when its Repeat sheet turned the repeat off; the
desktop had kept them in the note since its round four. A note made on the
phone and opened on the desktop lost them at the desktop's next copy: a big
delete there made a copy of words alone, over the one slot, and "Leg day
- knee hurt" was in no file
(a6r3-seams-and-parity-days-in-copy-lost-on-desktop). The phone's own
answer to that slot, carryDays, carried a copy's days into every later
copy -- and so carried a restarted series' old deleted day back over its
new first day at the next "Restore previous"
(a6r1-fixes-data-restart-days-come-back-on-restore). Two places for one
thing, and each program's way of keeping them lost what the other kept.

Her answer: one place, the note, as the desktop has it. The turn-off keeps
every day in the note when one holds text of its own, inert while the note
does not repeat, the turn-off's copy the whole series as before; days that
hold none go, as the desktop's save clears them. No copy carries days any
more: a copy of words is words alone. And a notebook 0.12.0 wrote -- hers
-- takes its series' days back into the note as it is read, once, from
the series' own copy, as the desktop's new `_days_back_from_copy` does
(5e228c0 there), never from an update's copy of a note that did not
repeat. Every reader of a note's days was read again; all were already
gated since the phone's port of the desktop's b26394c, which made kept
days steer nothing while a note does not repeat.

The second was a template being written when Android ended the app
behind Home: the template editor saves only on Save, and everything typed
was gone. Her answer: a draft, kept aside as the app goes to the
background with changes nobody saved -- every field, the words waiting in
"A new item…", the template it edits -- in Android's no-backup folder,
and offered back: "You were writing a template. Continue it?" [Continue]
[Discard].

**How the agreement was proved:** not reasoned from the two codes, but run.
`tools/make_days_fixture.py` runs the desktop's own core -- its home
folders pointed at empty folders, checked empty after -- and copies,
fingerprinted, the few lines of its window class that make the editor's
save and "Restore previous" of them; 21 notes walked through turn-offs and
turn-ons from the same start and from another, big deletes, restores, a
share code's update and a backup's, and 0.12.0's notes read, each step's
stored note and the note its import makes. `DaysTest` walks the phone's
own functions through the same steps, and every field agrees. The first
run found one more difference the reading had not: turned back on as it
was, the desktop drops the spent copy, which the phone's Repeat sheet kept
(its banner offering nothing). And walking found that the Repeat sheet's
word that a turn-off keeps its days was said under the note's details,
still open, where no one could read it; the details sheet now shows the
app's messages over itself, as the template editor does since this
round's first part. On the emulator: the notebook 0.12.0 left has its
day's text in the note at the next write; the Repeat sheet's turn-off
keeps it there, and the big delete after it makes a copy of words alone
with the day still in the note; a template being written survives
`am kill` behind Home, Continue brings back every field and the waiting
words, and Discard leaves no draft. Mutants AS226 to AS236 killed.

**The lesson:** a thing kept in two places is kept by neither -- each
program trusted the place it wrote, and the other's next copy erased it;
when two programs share a file, the place a value lives is part of the
format, and is proved by running both on the same note, not by reading
either.

---

## A cap that cut what it was meant to keep

**2026-10-29.** The final audit's sixth round attacked round five's fixes
and came back, on the phone, with two highs, six mediums and four lows,
and four duplicates of them; one high and one medium, about where a
turned-off series' days live, wait on the owner's decision. The desktop's
round found one more that the phone shared. Round five had set its limits
where ordinary notes cannot reach them and promised that what lies past
them stays found and readable. The round after found the limits cutting
the very words they were there to keep, and what lay past them neither
found nor read.

- **A cap on the result, not on what came in.** A checklist row's field
  and "A new item…" kept the first 2,000 letters of what an edit left: a
  letter typed at the start of a row of 2,000 pushed its last letter off,
  and 800 letters pasted at the start of a row of 1,497 its last 297 --
  " Serve with rice." -- out of sight in a field of one line, with no Undo
  for a row and nothing said. The desktop's box keeps the words there and
  cuts only the paste. And the phone counted UTF-16 units where the
  desktop counts letters: 1,200 emoji typed kept 1,000, and a desktop row
  of 1,500 emoji was read-only on the phone. The template editor's box
  had no cap at all.
- **A row moved among the rows not drawn.** A list of 1,000 draws its
  first 200 rows and its last five. Move up on the first of the five, Move
  down on the 200th, or a drag over the "… 795 more rows" line put the row
  among those not drawn: it vanished, a row never shown stood in its
  place, and a screen reader, its focus left at the place, moved that row
  next.
- **What lay past the limit, unread.** A row longer than 2,000 letters
  was a read-only block in a row 44 dp tall: "149,994 characters in 1
  lines, more than is", and not a word of the row. Round five's own walk
  had photographed it, and its check had passed on the words the screen's
  tree held whole. A row past what a list draws to write in showed forty
  letters, and Find stopped on a word at its 1,501st and painted nothing.
- **The twin round five did not sweep.** The template editor still drew
  every row of a template at once, a box and two buttons each, and drew
  them all again at each letter typed in its name. At 1,000 rows its Add
  drew row 1,001 and Save dropped it, as it dropped the words waiting in
  the box, unsaid.
- **One more copy of a rule.** Undo and Redo placed the cursor by their
  own copy of the field rule's first half: Redo of a table row typed at
  the end left it above the grid, and the next row went in over the first.
  Insert table wrote at the end of a note past what it draws, out of sight.
- **A search drawing what was drawn.** A match in one of the last five
  rows -- the newest item, the likeliest search -- drew all 1,000 rows, at
  each letter typed in the bar.
- **Comments outside the copy's rule**, from the desktop's round: the
  rescue copy holds a note's comments, but whether a save keeps one was
  asked of the title and the body alone, so 780 letters of comments typed
  over with "ok" went with no copy, on both programs.

**The fix:** a row's field and the boxes for a new row keep the words
already there whole and cut only what came in, to the room left, never
through an emoji, and say the desktop's words once -- "Pasted text
shortened: a checklist item can be up to 2,000 characters", or "A
checklist item can be up to 2,000 characters" when nothing could come in;
a row is weighed in letters, as Python weighs it. A move widens what is
drawn, so every row drawn before is drawn after and the row moved with
it; a drag lands on the drawn row nearest where it is let go; rows are
keyed by their words, so the focus goes with the row. A row shown, not
written in, is a line of its start and "Read all N characters ›", read
whole in parts a phone wraps, and a search opens it on its word. The
template editor draws its rows as the note editor does, in a composable
of their own, refuses an Add at 1,000, and never counts words waiting with
no room as saved. Undo and Redo place the cursor by the one rule typing
uses, as do the three other copies the grep found; Insert table writes
nothing out of sight and says where to tap; a search draws more only for
a row not drawn. And the comments are weighed as a text of their own, as
the desktop now weighs them -- added to the body's text, a long comment
would make a whole body deleted read as a small share. Each with a test
and a mutant watched failing (AS206 to AS225, all twenty killed), and the
walk's new `limits_round6` part.

Against the build before the round, `limits_round6` failed fourteen of its
checks -- all but the three that guard against a loss: the file kept
whole, nothing saved over a template, a row moved back. The template of
1,000 rows of 2,000 letters, opened on the emulator in one session: 7.2 s,
then 4.3, before the round; 3.0, 1.9 and 1.4 after it; a letter typed in
its name, 0.52 s before, 0.12 after. A search for the last row of 1,000
held the bar 2.2 s before, under a second after. And walking found two
more in this round's own work. A row read in parts is taller than the
screen, and a search's stop in it brought the row's top into sight, the
word under the screen's edge: the part with the word is brought into
sight now. And the template editor's refusals were said where no one
could read them -- the app's messages are drawn in the window under the
editor, a dialog of its own -- as its refusal of a template past the
thousand had been since it was written: the editor shows them over itself
now.

**The lesson:** a cap is put on what comes in, never on what is already
there -- cut after the edit, it takes the person's own words from wherever
the edit was not. And a window drawn in part follows the row a person
moves, as it follows the one a search steps to: what it leaves out is left
out by the person's choice, never by where a row happened to go.

---

## A word in the log written by another program

**2026-10-29.** The full walk after the audit's fifth round failed one
check, "99,000 characters of words reach the share sheet", with the
share sheet on the screen. Its second condition was that the log held no
"TransactionTooLarge" — and the log held one. Run alone twice, the part
passed once and failed once.

The line was not the app's. Read with each line's process
(`logcat -v threadtime`), it came from Play services
(`com.google.android.gms.persistent`), about a network service of its
own it had no implementation for — written in the same seconds as the
share, by chance. The check read every line every process wrote, so any
program on the emulator could fail it. Three more read the log the same
way: the 55,000 Chinese characters' share, "no OutOfMemoryError" while a
two-million-letter note was typed, and the crash buffer after Home in
the template part, where any process's crash counted.

**The fix:** `logged(pids, *words)` in the walk returns only the lines
the named processes wrote. A share names the app (its pid read before
the tap, so an app the share stopped is still named), `system_server`
that starts the sheet, and the sheet; the typing names the app; the
crash buffer is asked for the app's package, as the walk's other crash
check already did. Fed the captured log offline, the new check finds
nothing where the old one failed, and still finds the word when the
app, the sheet or the system writes it.

**The lesson:** a check of the log reads the lines of the processes it
is about, named by their pid before the act — the log is the whole
phone's.

---

## A ceiling for hostile notes that ordinary ones reached

**2026-10-29.** The final audit's fifth round attacked round four's
fixes and their neighbourhood, and came back with two highs, five
mediums (two of them duplicates of another) and seven lows on the phone.
Round four had counted what a note draws for the whole note; the round
after found where the whole was not whole, and where the count was
reached by notes nobody would call hostile.

- **A section outside the count.** The budget held the body. A
  checklist's rows, up to 1,000, were still each weighed alone and all
  drawn at once as fields: 120 rows of 150,000 letters, from a share code
  of 38 KB, were 18 million letters laid out at every opening: 7.1
  seconds in one frame on the emulator, 1,000 rows of 19,000 letters
  11.3 -- and even 1,000 short rows, the most a list holds, 6.5, every
  row a piece of the screen of its own.
- **Copies nobody weighed.** Undo kept 200 steps, each the whole note:
  a note of two million letters, from a code of 8 KB, would keep some
  760 MB. On the emulator, whose app may use 192 MB, its heap went from
  36 MB to 195 in fifteen bursts of typing, and at the twentieth the app
  stopped with an OutOfMemoryError.
- **A ceiling an ordinary note reached.** 100 pieces, every table and
  every run of prose between two tables one: fifty weekly tables under
  their headings, 2,331 characters, made the fiftieth and all after it
  one read-only piece, with nowhere to write at the end; a training log
  of sixty sessions, 5 KB, hid its last ten from Find, which skipped
  everything read-only and said "0/0" while the list's search found the
  note.
- **A cursor left where its line had gone out.** Round four moved the
  cursor with a line that went into the next field, and did nothing when
  it went into no field: a table row typed by hand became a grid at its
  closing pipe and the next row was written above it, the grid upside
  down, in any note; Enter at the end of what a long note draws glued the
  next words onto the line before. The calculator, the cursor never in
  the note, wrote into the part nobody could see.
- **A marker read as a word.** The rescue copy measured words as
  whitespace splits them, a list's "1." and "•" among them: numbers
  turned into bullets read as the list lost, and the autosave's copy
  replaced the one holding a paragraph deleted a moment before. Found on
  the phone first; the desktop shared it.

And the lows: the Share sheet built the note's whole share code on the
screen's thread to grey its card; every save split the whole note into
words three times for one letter typed; underline and strike were merged
in quadratic time at every drawing; a turn-off refused a copy that
carried days; the Update question warned for a note that only gained a
file; a "^" under a row too short merged into a cell nobody has; a
row's failed write was said at every letter, the letters lost.

**The fix:** the checklist is counted -- a row written in up to the
desktop's 2,000 letters, 100,000 in all, the rest shown; Undo is held to
16 million characters, never fewer than its last step; a note may be 500
pieces, two hundred tables under their headings -- the hostile 3,000
tables open in 2.6 seconds at that, 6.2 at 2,000 -- and 40,000 lines,
twice round four's, a journal of 10,050 lines whole; a checklist draws
its first 200 rows and its last five until asked for more; and what lies
past any limit is found by Find, each line one stop -- a stop at every
match let a table of 90,000 cells use up the count, the reason it had
been skipped -- its line shown and read on. The cursor goes to the field
holding its place, or the first after the piece its line went into; with
none, the keyboard goes and the note says why. A list's markers are no
words, as the desktop's _words has it since its own fix, and a series
turned back on from another start drops its old deleted days, as its
kept_days_fit does. Each with a test and a mutant watched failing (AS172
to AS204), and the walk's new `limits_round5` part.

Against the build before the round the emulator showed both highs: the
120 long rows held the screen 7.1 seconds, and the two-million-letter note
stopped the app at its twentieth burst of typing. After it the same rows
open in 1.7 s -- the same text in a body takes 1.6 -- 1,000 short rows in
2.0 instead of 6.5, a letter in a row draws in 0.2 instead of 1.1, the
heap stays between 67 and 98 MB through forty bursts, and `limits_round5`
passes its twenty checks. The letters' budget alone had left the 1,000
short rows at 5.3 s: every row is a piece of the screen, whatever it holds,
so the rows drawn are counted too. The Share sheet of a 5 MB note could
not tell the two builds apart (1.3 to 1.6 s before, 0.8 to 1.4 after),
and no check claims it did. And walking found one more in round four's
own work: a template's comments too long to edit stood under no label of
their own and read as the body's -- the session's full walk had failed
`template_long_comments` on it.

**The lesson:** a limit meant for hostile sizes is set where ordinary
notes cannot reach it -- measured, not reasoned from the hostile case
alone -- and what lies past it stays found and readable, so reaching it
costs a moment, never a word. And a cursor whose line leaves every field
is placed somewhere, never left in the field it came from: left, it
writes on the line above.

---

## What one note draws, and what is said before it goes

**2026-10-28.** The final audit's fourth round came back with two highs,
three mediums and five lows on the phone, and one duplicate. The highs
were round three's guard asked once more of the wrong thing: it weighed
what a body gives the layout, and nothing weighed what the editor draws.

- **A limit for each piece, none for the note.** A table was held to
  1,000 cells on its own: ten of them, from a share code of 350
  characters, were drawn as 10,000 cells at once. 3,000 tables of two
  cells were 6,000 pieces, every one composed at once in the scrolling
  column. And once a body's layout limit was reached, an empty line,
  which costs the layout nothing, was let in between lines that were
  not: "x", "", "x", "" ... came out as ten thousand pieces.
- **A field of many short lines.** The layout limit counts runs between
  the places a line can break, and a line break ends one at no cost.
  200,000 empty lines, from a code of 666 characters, were one field --
  the very field the project had measured, each of its lines drawn by a
  call that copies the whole field again
  ([the story](#a-table-moved-out-of-its-grid-and-into-one-field)).
- **One copy, replaced unsaid.** The desktop's third and fourth rounds
  had settled three more ways a note's one rescue copy was replaced while
  the note still needed it: a column added, which pads every row again
  and read as the whole table lost; a repeat turned off after a big
  delete; an import's update. The phone had ported only the days.
- **One identity, worn twice.** A code or a backup holding two notes
  with one identity updated one note twice, its own words then in no
  copy; two arriving new were both added wearing it, and so was a note
  restored from the Trash while a backup was planned; and files picked
  for the second of two went to the first.
- **Said where it could not be read.** The PDF's warning was a bar the
  share sheet opened over; a code too long was refused only after the
  tap; and the words' ceiling counted letters where Android carries
  bytes -- three for a Latin letter, five for a Chinese one. One low
  more: "↩ Restore previous" on a day of a series whose copy has no
  repeat left the editor on the day, refusing every save.

**The fix:** what a body draws is counted for the whole body -- 1,000
grid cells, 20,000 lines in its fields, 100 pieces, the rest one
read-only piece that says how many lines it holds; a field holds at most
1,000 lines and the prose goes on in the next, the cursor following its
line when a letter moves one; empty lines between read-only ones join
them; and one line, a title or comments heavier to draw than the
150,000 letters measured at 0.17 s is read-only. The rescue copy follows
the desktop's rule as it stands: a save loses the smaller of the span it
changed and the words it took away, and its shares are of the old text
measured the same way, each word with one space (the desktop's fifth
round: half a table of short cells read as a quarter); a turn-off keeps
a big delete's words; an update's question says when it replaces an
earlier version.
Each identity is claimed by the first note arriving with it, and a note
is found by its id while that note still wears its uid. On the owner's
word, a code too long is greyed on its card with the reason, as the
words are; the ceiling is weighed in what Android carries, its sentence
saying "about N of them", true for Chinese as for English; and a PDF
that would leave part of a note out asks before anything is handed over
-- the web page, the PDF as it is, or Cancel -- with nothing written
into the PDF any more. A restore that ends the repeat leaves the day for
the whole note. Each with a test and a mutant watched failing (AS147 to
AS171), and the walk's new `limits_round4` part.

Against the build before this round, the emulator measured what the
audit had reasoned: ten tables of 1,000 cells held the screen 9.4 seconds
in one frame, 200,000 empty lines 8.1, and 3,000 tiny tables did not open
in 90 seconds. After it, the ten tables took about what one table at the
line takes alone on the same emulator (1.9 seconds against 1.7, and 3.2
against 2.4 on an earlier run), the empty lines 0.9 and the tiny tables
0.3; a journal of 7,000 lines opens in frames of under half a second, and
a letter typed in it draws in 0.1 to 0.4 -- the autosave's write of its
308,000 characters, a frame of 0.4 to 0.8 after it, is as it was. On the
build before the byte ceiling, 99,890 Chinese characters -- under its
100,000 -- were a parcel of 503,764 bytes the system failed to deliver to
the share sheet, unsaid; 94,990 went through.

**The lesson:** a limit set for one piece limits nothing when the pieces
can be many: count what is drawn for the whole of what is drawn, the
many small with the few large. And a warning the person cannot read
before choosing -- under a sheet, after a tap, in a unit that is not the
one that runs out -- is an error they meet later.

---

## Guards that asked another question than the cost

**2026-10-28.** The final audit's third round attacked the second
round's fixes by name and came back with twelve findings at medium or
above, three of them duplicates of another, and three lows. Most were
not new kinds: they were the same guard, asked a question that was not
the one the cost answers.

- **What the layout can break.** A run with no space past 10,000
  letters is shown read-only. "No space" was Kotlin's `isWhitespace`,
  which counts the no-break spaces the layout never breaks at: 150,000
  letters joined by them measured as runs of four, and went into a
  field. And each line was weighed alone, while the letter-by-letter
  work adds up: a hundred lines of 9,999 letters passed one by one.
- **Where it is drawn.** The guard stood in front of the body's lines,
  the title and the comments. A table's cell, 26 characters wide, took a
  150,000-letter run whole; the template editor drew a checklist row
  whole; and every bar and question that quoted a row, a title or a file
  name laid the whole of it out — the Undo bar for the very ✕ a person
  uses to be rid of the row.
- **A plan applied by place.** A backup's import is planned while the
  list stays usable. It remembered each note by its position: one note
  sent to the Trash meanwhile, and Update rewrote the note below the
  right one.
- **An answer read from the wrong file.** A phone restored from a backup
  made while Android's backup was off holds only the mark that says so,
  until the app's first start; the backup agent read settings.json,
  which a restore does not bring, as on — and deleted the mark.
- **Work with no line at all.** The page Share and Export make did
  quadratic work on a table too large to draw, on the screen's thread;
  Share → Text handed any length; the PDF stopped at forty pages, and cut
  what ran past the margin, unsaid.

**The fix:** one definition of a run, used by every guard: it ends only
where Unicode's line-breaking rules always break — after the spaces
between two words, between two ideographs, at a line break — so it may
be longer than the layout's own, never shorter; and a body, a title or
comments may give the layout at most twice one long run's work, counted
across lines and a table's cells together. Every message quotes at most
80 characters, a file keeping its type, and no bar or question takes more
than 1,000. The import finds each note by its identity. The agent asks
the page's own rule, the mark included. Each with a test and a mutant
watched failing (AS114 to AS146), and the walk's new `limits_round3`
part on the emulator. Against the build before this round, that part
measured what the audit had only reasoned: the no-break-space note held
the screen 7.6 seconds as it opened, the note with the long cell did not
open in thirty, nor did a page of the 50,000 empty cells reach the share
sheet; and `google_backup`, given a backup before the restored app's
first start, watched Android run it, the mark go, and the app come up
with the switch on. After it, all pass.

Two more came out of the work itself. The first ceiling on Share → Text
was the one share codes had, 250,000 characters, from "a buffer of
about a megabyte". Walked, 180,000 never reached the share sheet: the
system starts it with a one-way call held to half that buffer, about
three bytes a character, and it failed in the system's own process
after the app's call had returned — so the catch written for it saw
nothing, and the person saw nothing. Measured, the line is between
160,000 and 180,000; it is 100,000 now, for codes too. And making the
calendar stop reading the days a turned-off repeat keeps (the desktop's
fourth round, ported) failed a test that had passed for the wrong
reason: a restore's copy carried a repeat without the date it hangs on,
so the restore after it brought back no series — unseen while a day's
words showed whether the note repeated or not.

**The lesson:** a guard asks of what actually costs, and stands in front
of every place that draws, quotes or hands the thing over; and a ceiling
set by another program is measured on that program, not reasoned from
its documentation — a failure in another process tells the app nothing.

---

## Fixes that were holes of their own

**2026-10-28.** The final audit's second round attacked the first
round's fixes and set the desktop's twins beside the phone, and came
back with twenty-three findings at medium or above, three of them
duplicates. A third were holes in a fix made the day before:

- **The rule ported half.** A backup's Trash window was adopted whenever
  the notebook had never chosen one — a shorter one too, and with no
  Trash in the backup at all — so a backup from a seven-day notebook
  emptied this phone's own Trash at the next start. The desktop had
  shipped the same hole and closed it that morning: only a longer
  window, only with Trash to keep by it.
- **The count that measured the wrong thing.** A table past 1,000 cells
  is not drawn as a grid — counted as written. One row of 500 over 250
  rows of two came to 1,000 and was drawn as 125,500, every row padded to
  the widest. The template editor drew every table whatever its count,
  and Find stepped through cells nobody could see.
- **The flag set before the act.** The backup export marked its place
  "opened" before the open was tried, and a place that refused deleted
  the older backup the person had chosen to replace.
- **The answer that did not survive.** A Google-backup switch turned off
  read as on after a damaged settings file, and after a restore of the
  copy Android had kept from while it was on.
- **The timer counted from the asking.** An Undo's eight seconds began
  before the bar showed: queued, it flashed for a second or stayed for
  good.

The rest were limits that held at the door and not after it, as the
desktop found the same day: a share code of a few hundred characters
carrying a 150,000-letter line that froze the note at every opening
(12.6 s, measured; 0.17 s for the same length with spaces — the cost is
a word broken letter by letter, not length); 5,000 moved days read
5,000 times each; a pasted table padded to 120 million characters after
the 100,000 ceiling; 700,000 "painted days" that were no dates; NaN in
a list, which Android's parser takes and then cannot write, stopping
the app at the import's tap; a file name legal on Windows and too long
for Android, refusing the whole backup.

**The fix:** each by its class, with a test and a mutant watched
failing (AS58 to AS113, every one killed), and on the emulator where a
test cannot reach — and three walk parts shown failing against the app
before the fix. A repeat ended by a share code was ported from the
desktop's rule and then re-ported when the desktop's own third round
proved that rule wrong: the series' days are copied into the one rescue
slot, never moved there, and a new copy carries the days the old one
held. A run with no space past 10,000 letters is shown, not laid out —
the note keeps every byte.

**The lesson:** a fix is a new surface. The round after a fix attacks
the fix — its count, its flag, its timer, the half of the rule it
ported — and the twin on the other app is read the day it changes, or
the port is a day behind its own specification.

---

## Answers sent to a screen that was gone

**2026-10-28.** Found by the final audit in the code, reproduced on the
emulator by ending the app's process while Android's picker was in front
— what Android does to a backgrounded app when it needs the memory. The
picker for Export as file, and the one for Attach files, belonged to the
editor. The app came back on its list, with no editor to take the
answer: the export's picker had already made `Bread.html`, which stayed
at 0 bytes, the picked file was not attached, and nothing was said. The
answer was not even dropped, only kept: opening the note again later,
the editor took it then and attached the file, minutes after the person
had given up. The backup's own pickers lived in AppRoot, always there,
and came through.

The same reading found a second way to lose the files: the copy off the
picker ran in the editor's own scope, so Back while 300 MB copied ended
it — the file never attached, its copy left in the cache.

**The fix:** both pickers are AppRoot's now (`AppState.exporter`,
`AppState.pickAttachments`), with the job they answer kept in saved
state. While the editor is open it still takes the files — a new note
saved first, a full one asked about — and when it is not, AppRoot
attaches them itself, as many as fit, and says so; a new note never
saved says the files were not attached. The copy runs in the app's
scope and deletes what it copied in `finally`. The walk's
`answers_after_restart` ends the process behind each picker and presses
Back during a large copy: passing; against the app before the fix, all
three fail.

Android's "allow notifications?" was named too, and is unchanged: under
it the app is only paused — the event log shows no stop — so nothing is
saved and Android does not end a visible app for memory. Ended there by
hand, the answer is lost, but no ordinary route gets there.

**The lesson:** an answer from another app goes to what is always there,
and work that must finish outlives the screen that asked for it.

---

## A crash in front of the crash foretold

**2026-10-28.** The final audit read it in the code without running it:
the template editor kept its comments in `rememberTextFieldState`, which
hands its whole text to the system each time the app goes behind
another, and nothing caps a template's comments — a backup brings them as
they are. Past about 524,000 letters that handover is more than one
binder transaction carries, and the app stops as Home is pressed.

Run on the emulator with 600,000 letters, the app stopped before Home was
ever pressed, as the template opened: `Can't represent a width of 974
and height of 994854 in Constraints`. The comments field grew to fit its
text, and an outlined field measures itself in a size Compose cannot
hold past about 262,000 pixels tall at a phone's width — some 190,000
letters at ordinary size, far fewer at the largest. A long text in a
note's own comments, and in a template's body, opened and survived Home:
they are drawn by plain text fields, which never build that size.

**The fix:** the comments field stops growing at ten lines and scrolls
inside past them. Built with only that change, the walk's
`template_long_comments` opened the template — and pressing Home stopped
the app with `TransactionTooLargeException`, as foretold. So the
comments are now remembered the way the editor's name, title and body
beside them always were, and not saved for the system: saved, they
bought nothing, since an app started again opens on its list and not on
this editor. The walk's part passes; against the app before the fix it
fails at the opening.

The same walk also shared 120,000 characters of a code to the app
("Share → Note Organizer"): the dialog's field holds it, bounded by the
dialog, but laying out one unbroken word that long held the app until
Android offered to close it. Not changed here; it is in
[Still open](#still-open).

**The lesson:** a reproduction that fails earlier than predicted has
found a second bug in front of the first. Fix it and run again before
calling the first disproved.

---

## On and off, told by a tint

**2026-10-27.** Found by the final audit's accessibility reading, with
the contrast worked out from `Themes.kt`: a key that is on — B over bold
words, the bullet key in a list line, the chosen alignment — differed
from an off key only by its fill, 1.04 to 1.33 to 1 against it in the
eight themes, where 3 to 1 is what a boundary needs to be seen. And to a
screen reader there was no state at all: B read as "B", a marker as its
emoji, a repeat day as its number. The pattern was everywhere a choice
was drawn; stateDescription and toggleable stood in two places of the
app.

**The fix:** each of them is now said as what it is — a checkbox ticked
or not (B, I, U, S, the list keys, a day's markers), one choice of
several (the alignments, the swatches, the repeat sheet's chips, the
sort and Show in force) — and a key that is on wears a bar in the accent
under its fill. The rest of the audit's list went with it: swatches by
place and value, a group's fold, the size grid's squares, the Sort
button without its arrow's name, the find counter read out. The walk's
`states_said` reads checked and selected from the tree a screen reader
reads; how TalkBack words them waits for a person with it on.

**The lesson:** a state needs a second sign for the eye and a word for
the ear; a colour is neither alone.

---

## Arranging only a finger could do

**2026-10-27.** Found by the final audit's accessibility reading: the ⠿
handle on a card in Custom order, and on each checklist row, moved it by
a drag and by nothing else. A screen reader's double tap is a click, not
a drag; Switch Access selects, it does not drag; and nowhere in the app
was there an accessibility action. TalkBack said "Drag to move …", to
people who could not. The template editor's checklist had ↑ buttons all
along — the other way existed, one screen away.

**The fix:** a tap on a handle opens Move up and Move down, only the ways
it can go — for a hand that cannot drag too — and the same two are the
card's and the handle's actions, which TalkBack and Switch Access list.
They move by the drag's own rules (`Organizing.stepPlace`, held by a
test; a pinned note and a group keep their blocks). The walk's
`arrange_without_drag` taps the handles and reads the file; the actions
themselves, which no adb command can press, wait for a person with
TalkBack on.

**The lesson:** every gesture needs a door that is not a gesture.

---

## A backup promised without its conditions

**2026-10-27.** Three findings of the final audit, one page and one
paragraph. Settings → Backup said that, with backup on in the phone's
settings, "Android also keeps a copy of the app's notes in your Google
account" — while Android's own backup copies an app's data only up to
25 MB, and attached files count: one phone video and the notes stop
being copied too. Two lines above, the same page said a notebook with no
`.zip` yet was "the only copy of your notes outside this phone". And
STATUS.md's reasoning for Play's Data safety form called the copy "end
to end encrypted with the phone's lock", which holds only on a phone
with a lock: the rules do not require it.

None of it was a fault in the code; each was a sentence that took
another program's behaviour at its best. **What changed:** the page says
what Android does, when — about once a day, while the phone charges on
Wi-Fi — and the 25 MB, in the words of the privacy policy; the `.zip`
line no longer claims to be the only copy; the reasoning no longer rests
on encryption. And the owner made it the person's to refuse: a switch on
the page, on by default so nobody's backup stops unannounced, and
`NotesBackupAgent`, which hands Android nothing of the app's while it is
off. The walk's `google_backup` proves it on the emulator's local
transport: on, the app's files go; off, Android is handed nothing and
the package is refused; on again, they go. What an earlier backup left
in the account stays there; the app cannot reach it, and the page
promises only "from now on".

**The lesson:** a promise about another program's behaviour carries that
program's conditions — and when the program copies the person's notes,
the person gets a say in it.

---

## Two checks that read the screen at the wrong moment

**2026-10-27.** The first full walk of the audit's new parts failed two
of them on the walk's own account, the app right both times.

A backup too large for the phone was refused with a sentence in the bar
at the foot, and the check found it; the photograph, a separate look a
second or two later, did not — the bar stays four seconds, and looks at
the screen take one or two each. And the labels' part selected a card,
then went "back to the list" with `back_to("Search notes")`: the search
box stays on screen while a card is selected, so it stopped at once, and
the next tap on the card selected it again instead of opening it.

**The fix:** the photograph is the check — taken the moment the words
show, and its answer is the verdict — and the selection is cleared by
its own ✕, with a check that the bar is gone.

**The lesson:** a check reads what it acts on at the moment it acts, and
waits for the change it caused, not for words that were there all along.

---

## A table moved out of its grid and into one field

**2026-10-27.** The first fix for a table of 300 by 300 — a grid of
90,000 cells drawn at once — left a table past 2,000 cells in the text
around it, as its lines. The full walk timed the note opening at 9.8
seconds.

Measured on the emulator (`simpleperf` on the opening, and the frames'
own timings): two frames of 2.8 and 1.8 seconds, half of the work
drawing the text field and a quarter laying it out. The field held
362,000 letters; Compose lays a field out whole, and draws it whole when
it sits in a scrolling column, and each of its thousands of wrapped lines
was drawn by a call that copied the field's entire text again. The table
had been taken out of the grid and put, whole, somewhere just as costly.
The walk's own clock was off too: it timed the opening by looking at the
screen, and a look waits for the app to be idle, taking seconds of its
own — 5.0 seconds for a note later drawn in half a second.

**The fix:** a table past the line is said too large to draw, its first
forty lines shown read-only and cut to a phone's width; the note keeps
it whole. The grid's cost was measured too — 0.4 s for 100 cells, 1.0 s
for 400, 2.0 s for 1,000, 2.7 s for 2,000 — and the line came down to
1,000. The walk now times by the app's frames: 0.5 s for the large
table, 2.2 s for one at the line.

**The lesson:** measure where the time goes before choosing where to move
the work. Moved whole, it costs the same in another place.

---

## A row's second letter, judged by the last drawing

**2026-10-27.** The first full walk of the fix in
[words a field kept until it was left](#words-a-field-kept-until-it-was-left)
typed " 500g" after a checklist row's "flour" and pressed Home. The file
read "flour " — a space, and nothing after it; the photograph after a
restart showed the same.

The row's field asked, at each letter, whether another row's words were
still waiting, and judged it by two values the drawing had captured: no
rename waiting for this row, and the row reading "flour". The adb tool
types faster than the screen is drawn again — so did a person's keyboard,
now and then. At the second letter the drawing still said "nothing
waiting here", the waiting " " was taken for another row's and written,
and the new rename began from "flour", which the file no longer held; the
save on Home found the row changed under it and, as it should, dropped
the words rather than put them on another row.

**The fix:** `EditorModel.typeInRow` decides from the editor as it is
when the letter arrives, and the field only hands it the letters. A test
types four times into one row and finds nothing written until another
row is typed in; the walk's `checklist_words_kept` passes on the
emulator.

**The lesson:** a callback decides from the state as it is when it runs,
not from what the last drawing captured. Between two drawings, many
letters can arrive.

---

## A rescue copy let in whole

**2026-10-27.** Found by the final audit, reading the two
`normalize_note`s side by side: a backup's note "Groceries", one row on
its checklist, whose `_prev` held fifty thousand rows and two thousand
highlights on one line. Imported, its banner offered "↩ Restore
previous"; pressed, the note took all of it, and the editor, which draws
a checklist row by row, would compose fifty thousand rows.

Both apps cap a checklist at a thousand rows and a line at five hundred
styles, at every way into a note — but the phone's port of
`normalize_note` copied `_prev` whole, where the desktop's shapes it:
the words as text, the styles clamped against the copy's own text, and
of anything else only the fields an update from a backup replaces, each
through `normalize_note` itself. A restore then wrote the copy straight
into the note, past every ceiling.

**The fix:** `Normalize.shapePrevious` shapes it as the desktop does,
when it comes in; and a restore shapes it again before writing, for a
notebook written before. Two tests in `IdentityTest`, one at each door.
The same copy's text was behind [the freeze below](#a-question-asked-of-every-note-to-answer-one).

**The lesson:** a ceiling holds only if every road into the note passes
it. The rescue copy is a second note riding inside the first, and its
road in is the restore.

---

## A question asked of every note, to answer one

**2026-10-27.** Found by the final audit: a backup of 4,505 bytes whose
one note held twenty thousand `a` and a `b`, and a rescue copy of four
million `a`. Imported, it would hold the app still at every start, and
after every save.

The restore question promises "what the note holds now is kept" only
when it is, and that is worked out by asking whether the copy's text
contains the note's — the desktop's `in`. The phone worked it out while
reading the file, for every note and every Trash entry with a copy, at
every load: each start, and the refresh after each save. And it asked
with the JVM's `contains`, which starts the comparison again from each
place: millions of places times twenty thousand letters. Measured on the
computer, fourteen seconds a call; Python's `in` took twelve
milliseconds on the same strings.

**The fix:** the answer is worked out when the question is shown, for
that one note, and by a search whose time grows with the two texts
added, not multiplied (`PyText.contains`). `IdentityTest` loads that
note and asks, within a margin no machine's speed crosses either way,
and holds the search to the JVM's answers over three thousand cases.

**The lesson:** work out an answer where it is asked, for what it is
asked about. And a port of Python's `in` is a port of its cost too: the
same answer from a slower search is a different program.

---

## A million brackets past a catch for exceptions

**2026-10-27.** Found by the final audit, from the platform's own
source: a share code of 1,354 characters inflates to `{"v":1,"notes":`
and a million `[`. Every guard before the parser passes it — the code is
short, the text under the 20 MB ceiling — and Android's `org.json` reads
an array by calling itself for each one nested inside, a million calls
deep. The stack runs out, and `StackOverflowError` is an `Error`, not an
`Exception`: every parse of outside input here caught `Exception`, so
nothing caught it, and the app stopped. A 1 KB backup whose notes,
Trash, templates or settings file did the same stopped it too.

The desktop had met this one: its `JSON_UNREADABLE` counts Python's
`RecursionError` as damage, with a comment saying why. The port carried
the reading, not the catch. And the unit tests could not have seen it:
the computer's `org.json` refuses nesting past 512 levels with an
ordinary exception, so a test of a million brackets passes there either
way.

**The fix:** `Files.parseOutside` measures the nesting first — one pass
over the text, brackets inside strings not counted — and refuses past
100 levels, ten times as deep as any note goes, before the parser is
called; and a parser that runs out of stack anyway is damage too. Share
codes and every file a backup brings are read through it. The tests nest
150 deep, which the computer's parser takes and the guard refuses, so
they fail without it.

**And the app's own files, 2026-10-28.** The notes, the Trash, the
settings and the templates were still read with the bare catch: they are
the app's own, but not only the app writes them — Android puts a backup
back, a development build copies the desktop's in. One nested past the
stack, or too large for the phone, would have stopped the app at every
start. They are read through the same guard now (`Files.parseOwn`), and
the desktop's third kind of damage came with it: bytes that are not
UTF-8. Read leniently, a notes file saved in Windows' Romanian code page
came in with its letters as replacement marks, which the next save wrote
over the person's words; a share code or a backup file did the same. All
of them now refuse such bytes, and the notes file is kept aside whole.

**The lesson:** a catch names what it expects. What comes from outside
is measured before a reader that recurses is handed it, and the test is
built for the parser the phone runs, not the one the computer has.

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

### A very long code shared to the app holds it still

Found on 2026-10-28 while reproducing
[a crash in front of the crash foretold](#a-crash-in-front-of-the-crash-foretold):
120,000 characters shared to the app as text land in the code dialog's
field, which the dialog keeps to its own height, so nothing stops — but
laying out one unbroken word that long held the screen until Android
offered to close the app (photographed on the emulator). A share code is
Base64, one unbroken word by nature; a note of a few hundred thousand
letters makes a code that long. **Closed 2026-10-28**
([the story](#fixes-that-were-holes-of-their-own)): the dialog shows a
code past 5,000 characters as its start and its length, Copy taking the
whole of it, and no field the app draws is given a run of more than
10,000 letters with no space — a note's lines, title, comments and rows
holding one are shown read-only, every byte kept. The owner is to see
whether that is what the dialog should show.

### Two lows from the audit's third round, left

- **A paste that makes its own line read-only leaves the cursor above
  it.** Pasting more than 10,000 unbroken characters into the middle of
  a field cuts that field in two around the read-only notice; the
  keyboard stays in the field above, and the next words land at the end
  of the line before the paste. Nothing is lost and Undo puts it right.
  Left because the fix is in the editor's focus, where a change has been
  proved only by a person's keyboard
  ([the story](#what-a-persons-keyboard-found-that-the-walk-could-not)),
  and this round had no phone to prove it on. **Closed 2026-10-29**
  ([the story](#a-ceiling-for-hostile-notes-that-ordinary-ones-reached)):
  the cursor goes to the start of the field after the pasted line, by the
  rule that places it wherever its line leaves every field -- proved in
  the editor's model, not yet by a person's keyboard.
- **A desktop attachment renamed to fit the phone comes back to the
  desktop as a second attachment.** A stored name past Android's 255
  bytes is kept under a shorter name, and the note follows it; the
  desktop's import compares attachments by that stored name, so a
  backup made on the phone reads as changed there, and Update adds the
  same file again under the short name. The phone cannot keep a name it
  cannot store; the desktop's import is where a fitted name can be
  recognised. No data is lost, and the names involved are rare.

### What the audit's fourth round left

- **Backspace at the start of a field does not join it to the line
  above, where long text is cut into fields.** A note's text past 1,000
  drawn lines (some 32,000 letters) is several fields
  ([the story](#what-one-note-draws-and-what-is-said-before-it-goes));
  between two of them, Backspace at the start of the lower one does
  nothing, and a selection cannot run across both. A table or a
  read-only line between fields always had the same edge. Left because a
  soft keyboard's Backspace at a field's start reaches the app only as
  some keyboards choose to send it, which only a person's keyboard can
  prove.
- **Find does not search the read-only rest of a note past what it
  draws**, as it never searched a line too long to lay out. **Closed
  2026-10-29**: it searches every piece shown read-only, each line one
  stop, and the piece shows the line
  ([the story](#a-ceiling-for-hostile-notes-that-ordinary-ones-reached)).
- **The list's "Share as a code" for several notes is still refused
  after the tap** when the code is too long; the Share sheet of one note
  says so on its card before. (Since 2026-10-29 the code is built away
  from the screen's thread; the refusal still comes after the tap.)
- **The autosave of a very long note holds the screen for a moment.**
  Measured while walking this round: a journal of 308,000 characters
  draws a letter typed in 0.1 to 0.4 seconds on the emulator, and the
  save 800 ms after it is a frame of 0.4 to 0.8 seconds -- the whole
  notebook copied and written from the screen's thread, as before this
  round. Not a freeze, and not this round's; measured, not fixed.

### What the audit's fifth round left

- **A template being written is lost if Android ends the app behind
  Home.** The note editor saves on leaving; the template editor saves
  only on Save, and the app started again opens on its list. Saving a
  template on Home would make ✕'s "Discard" a lie about what is already
  saved, and a draft kept aside is a file of its own; which of the two,
  if either, is the owner's call. The words in its "A new item…" box
  are now saved by Save and asked about by ✕. **Closed 2026-10-29**
  ([the story](#a-days-text-in-a-copy-the-other-program-erased)): a draft,
  kept aside in Android's no-backup folder and offered back, the owner's
  choice.
- **A table row of three cells or more typed by hand becomes a grid at
  its second cell's closing pipe**, two cells being a row, and the cursor
  goes under the grid with the rest of the row. Two-cell rows come out in
  order; tables are made with the table bar, and rows added with it.
- **Past 40,000 drawn lines -- some 880,000 characters of a journal --
  the end of a note is read-only**, found and read but not written in on
  the phone: Enter there lets the keyboard go and says so. The budget was
  20,000 and is twice that, measured at 1.1 s against 0.8; a tail kept
  drawn past it, so the end stays a field whatever the size, was weighed
  and left -- the cut would move under the cursor at every new line.
- **The 200 rows a long checklist draws at first are drawn again at each
  letter typed into one of them** -- 0.2 s a letter on the emulator, 1.1
  s before the round. The rows are one composition; typing in the text
  above no longer draws them, and a row of its own that could skip was
  not worth the change at that cost.

### What the audit's sixth round left

- **Where a turned-off series' days live, two findings waiting on the
  owner.** A repeat turned off on the phone keeps the days' own text only
  in the rescue copy, which the desktop's own turn-off would replace; and
  "↩ Restore previous" gives a series restarted from another date its old
  deleted days back, through the days the copy carries. Both turn on one
  decision -- whether those days stay in the note, in the copy, or both --
  and the turn-off, the carrying of days and the restore are left as they
  are until it is made. **Closed 2026-10-29**
  ([the story](#a-days-text-in-a-copy-the-other-program-erased)): the note,
  the owner's choice, as the desktop keeps them; 0.12.0's come back into
  it as it is read, and the two programs store the same note at every step
  of 21 cases run on both.
- **A word a keyboard commits past the cap is cut as a paste is.** The
  desktop refuses a key past a checklist row's 2,000 letters and cuts only
  a paste; a phone's field is told the words it now holds, not whether a
  paste or a keyboard brought them, so anything that comes in at once --
  a swiped word, a suggestion -- is cut to the room left and "Pasted text
  shortened" said. A single letter is refused, as on the desktop.
- **A moved row has no Undo of its own**, as before: the opposite move
  puts it back, and stays in sight to do it. A screen reader's focus
  going with the row is what keying rows by their words gives; it was
  not heard with TalkBack on, which no walk turns on.
- **The template editor's rows are still a box and two buttons each**,
  200 and five of them drawn, the rest on asking: a template of 1,000 rows
  of 2,000 letters opens in 2.5 to 3.0 s the first time on the emulator,
  7.2 before. Rows drawn as the note editor draws them, a symbol for a
  box, would cost less and change how the editor looks; that is the
  owner's to choose.
- **A template being written is still lost if Android ends the app behind
  Home** (the fifth round's, above), the owner's call. **Closed the same
  day**: kept as a draft, and offered back.
- **A share code's turn-off keeps even days that hold no text of their
  own in the note**, as the desktop's apply_shared_update does, until the
  note's next edit clears them as the desktop's save does. A Repeat sheet
  turning such a note back on with its own start, an edit typed just
  before and not yet saved, clears them at that edit's save first, a
  moment the desktop folds into one save; the copy keeps them either way.
- **A row read in parts starts each part on a line of its own**, a short
  line standing every 300 letters or so; the owner is to say whether the
  reading looks right.
- **`long_lines` timed the list with a title of 150,000 letters at 1.26
  to 1.44 s** on the emulator on 2026-10-29, against its line of a second
  -- on the build before this round as on the build after it: the
  machine's that day, not the round's, left for the full walk to judge.

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
