# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Twenty-eight of the hundred and eleven
are about the checking. A few words carry a sense of their own here (a
bar's *word*, a *road*, a *look*, a *round*, *the lows*):
[the house words](../CLAUDE.md#house-words).

**Who is speaking.** The entries are written by the AI agent the app is
built with (Claude Code), so a first person ("my own last fix") is the
agent; **the owner** or **she** is Alexandra.

Smaller lessons, each once a long comment in the code, are collected
[at the end](#smaller-lessons-moved-out-of-the-code), by the name the code
points with.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
| [2026-11-13](#a-keyboard-nobody-asked-for-on-android-8) | code | The app says it runs from Android 8.0 and had run on 16 and 17 only; on an 8.0 emulator every start opened the keyboard over the list, the search box given the focus by the system | A version the app says it supports is one it has been run on; a tool written for one system judges that system |
| [2026-11-13](#the-lows-and-the-second-copy-fixed-before-the-stores) | code | The full reading's lows, fixed before the stores: state kept by an id, an import failing halfway, a day trashed twice, a shared file read on the screen's thread, a name lost behind Home -- and the answers the second copy gave otherwise than the desktop | A low is written down to be fixed, not lived with; ask the first copy the odd questions too |
| [2026-11-12](#a-desktop-fix-the-port-was-never-asked-about) | code | The desktop's Statistics began counting every category on 11 November; the phone, whose tile was a port of the old rule, still counted those holding notes, and nothing compared the two | When the desktop changes a rule the phone ports, the phone is asked in the same change: a sweep by the fix's question crosses the repositories |
| [2026-11-07](#a-message-read-after-it-went-a-tap-between-two-cards-a-list-left-mid-way) | checking | The full walk after the lows wave: a four-second message read by a five-second look, a tap that stopped a fling between two cards, and steps that search the list started where a turn of the phone had left it | A step that searches the screen starts where the step before it stopped |
| [2026-11-07](#two-looks-that-judged-what-they-never-read) | checking | A walk's early steps failed twice and passed walked again: a search judged on words never read back, and a look that read the screen from before a tap because adb lost its dump on the way | A file on the device is evidence only when the command that wrote it says it ran |
| [2026-11-07](#a-bars-button-tapped-after-the-bar-had-gone) | checking | A walk on a freshly booted emulator tapped a bar's Show all after the bar's ten seconds were up, opened the card beneath, and failed 25 steps from one | A check that taps something with a lifetime must own that lifetime, or the machine's speed decides the verdict |
| [2026-11-07](#the-lists-words-merged-and-unmerged) | design | The lows wave's verification, rounds 5 to 11: what the list says of notes it hides -- one slot, then all at once, then merged, unmerged, merged again -- my own last fix found wrong five times, until each word named its note, as the desktop's does | When two fixes keep undoing each other, the choice between them is the wrong question: ask what the other program does |
| [2026-11-06](#the-bar-that-went-with-whichever-note-was-on-screen) | code | The lows wave's verification, rounds 1 to 4: a note kept aside went and left its Show; dismissing "the bar on screen" took another action's Undo; an Undo then waited behind offers, or threw them away | Dismiss what you mean, by name: on a queue, "the current one" is a guess |
| [2026-11-06](#the-walks-first-look-after-an-install) | checking | Mutant walks failed at the start, "uiautomator wrote no tree": right after an install the emulator's shell answers nothing for seconds, and a mutant walk installs every time | A flake that comes "now and then" may come after one thing every time: find what it follows before widening a timeout |
| [2026-11-06](#a-rule-the-phone-never-got-behind-a-fingerprint-comments-had-stopped) | checking | The second wave changed what the desktop keeps after an Update and a big delete; the phone never followed, because the fixture comparing them had stopped on the desktop's comment pass and nobody ran it again | A guard that stops for a harmless reason will be treated as harmless the day it stops for a real one |
| [2026-11-06](#custom-order-twelve-rounds-to-settle) | design | The final audit's second wave, rounds 14 to 25: Custom order's steps and drag -- cards out of sight, a drag against the finger, every card in an edge zone on its side, a hold lost to the next finger event -- most of it pushed in by the round before | Where the frame order decides, a gesture's rules are one machine: every decision in a pure function, and the whole machine re-attacked |
| [2026-11-06](#walk-checks-that-could-not-tell) | checking | The same rounds: five walk checks were not the check they were written to be -- a label in capitals, a stale gesture, a cut handle, a recoil the wrong way, a pass mark from one build | A walk check is a claim about the screen: watch it fail on the bug, pass on the fix, and measure its mark on both |
| [2026-11-04](#a-fix-the-next-round-found-wrong) | code | The final audit's second wave, rounds 1 to 13: an ordinary week, and my own fixes found wrong by the next round -- "A new item…" lost on a moved day, a flag on the call, a reveal on the old layout, parts stacked | A fix is a new road: re-attack it by name, and look at the photographs |
| [2026-11-02](#a-mark-held-in-a-cell-and-the-roads-that-changed-the-table-first) | code | The final audit's third wave: a cell left on "[x]" kept "[x" -- the held mark was written before the table's bar, Undo and the calculator, not before Caption, Line above, Delete or Insert table; and the Duplicates clean-up merged a Duplicate's files twice | A value held back must be written by every road that can replace what it belongs to; find the roads by what they change |
| [2026-11-02](#a-paste-meeting-a-list-one-road-at-a-time) | code | The lows wave's verification, rounds 5 to 11: one class found a road a round -- a pasted line read as a corrected word, a paste with no list handling, a copy carrying the next line's or its first line's looks, a whole item pasted over another keeping the covered marker ("2. 3. Bread", "3. 1. Kiwi") -- until round 9 tested the rule's whole matrix | When each fix of a rule leaves the case beside it, write the rule down and test its whole matrix on both apps at once; a fix by road is a fix of one cell |
| [2026-11-01](#a-days-lists-read-by-line) | design | The lows wave's verification, rounds 1 to 4: a day of its own text took its lists' depth from the series by line, and Enter renumbered and stored it; the owner chose that a day keeps its own lists | What a copy can change, the copy keeps; structure borrowed by line number holds only until the first line moves |
| [2026-11-01](#a-fuzz-that-never-took-the-road-it-guarded) | checking | The lows wave: the fuzz of a series' days passed on its seeds, never reached round 21's road in thousands of roads, excused a spent copy as a replaced one, and the gate let 26 of 31 test classes vanish one at a time | A random test proves only the roads it takes: count that it reaches the branch it guards, and take that branch's fix out to watch it fail alone |
| [2026-11-01](#the-day-the-editor-stayed-on) | code | The audit's twenty-third round: Restore previous tapped on a day brought back a version with that day deleted, and the editor stayed on it; what was typed there was "Saved for this day only" to a day no screen showed and no search found | A guard written for one way a day stops being drawn is a question about every way: ask whether the day is drawn, not why it might not be |
| [2026-11-01](#two-moves-onto-one-date-and-a-list-that-threw) | code | The audit's nineteenth round: a series holding two moves onto one date -- 0.13.0 after three ordinary taps, or an Undo beside a dead move -- gave History two rows of one key, and the list threw at every unfolding; graded low by the finder, taken as a crash by the session | A grade is about the road a person takes: a crash an upgrade can reach is not low because this build alone makes it rarely |
| [2026-11-01](#history-and-the-days-it-never-asked-about) | code | The audit's eighteenth round: a series with an end listed in History only the days between its start and end, so a day moved past the end was drawn and in no row, its words found by no search; an endless series skipped every deleted date, a day moved onto one missed the same way | A fix to one reader of a shape is a question to every reader of it |
| [2026-11-01](#words-that-never-move-on-their-own) | design | The audit's seventeenth round, both apps: round sixteen's moving of words sent home one by one wrote one day's text over another's after a week shifted a day at a time; a day moved onto a deleted date listed nowhere; an Update's copy, as a restore leaves it, replaced with its days' words; a series turned back on as it was read as another for a weekday lit once. The owner chose: words never move on their own, and the audit changed how it runs | Do not move a person's words on their own: drop what hides them, and show the rest |
| [2026-11-01](#a-home-the-rule-no-longer-gives-and-a-move-the-calendar-never-saw-go) | code | The audit's sixteenth round, both apps: round fifteen's movesHome sent a day "home" to a date the rule no longer gives, its words off the calendar, and walked a chain once a link; the phone's calendar read notes no read had sent home, and Restore previous sent none, so a delete there took another day; a copy's day the note had moved since set aside as words it still held; the start's notes made again at every launch | A rule that moves something "back" must ask whether "back" still exists, and run where the reader reads |
| [2026-11-01](#four-ways-of-adding-and-a-note-of-its-own) | design | The audit's fifteenth round, both apps: another series' days kept in a copy the next turn-off, big delete or Update replaced; a moved line read back as added text; a day moved onto a date a new rule gives drawn nowhere; a day only coloured read as words by a title's space. The owner chose the simpler rule: the copy's words for a day holding words of its own become a note of their own | When each fix of a merging rule leaves a new case, stop merging: keep both where the person sees them |
| [2026-10-31](#a-day-cut-off-by-a-tables-line-the-old-text-again-a-cells-bold-dropped) | code | The audit's fourteenth round: round thirteen's line under a day's table, without the series' too, cut a day off from its series and read a colour as words; a day corrected in its middle got its old text again; a letter typed in a padded cell dropped its bold | A rule written into one side of a comparison is written into both; to keep what is lacking, compare what both hold, in order |
| [2026-10-31](#a-days-words-added-twice-and-slowly-a-tables-line-the-phone-never-wrote) | code | The audit's thirteenth round: round twelve's added words came twice where a day had grown from the copy's text, and were read off a copy of the whole note for each day -- a year of days froze the turn-on; a day ending in a table typed on the phone lacked the line under it, and an import read it as changed; a column's width spread a cell's colour | A rule that adds asks what is already there, as lines; a cost per item never carries the whole |
| [2026-10-31](#words-kept-where-nothing-looked-a-days-undo-over-another-styles-read-off-the-texts) | code | The audit's twelfth round: the words round eleven kept in the copy were read again by date at every start, lost or put on the wrong day; a deleted day's Undo came back over a day moved onto its date; a table or an answer inserted where a styled run began took its style | A place the next read looks at again is not a place to keep things; a change that knows where it was made says so to everything it moves |
| [2026-10-31](#a-day-moved-onto-words-no-calendar-showed-a-copys-words-no-day-could-take) | code | The audit's eleventh round, on both apps at once: a day moved onto a date that kept the words of a day the calendar no longer showed wrote over them; a day coloured before its series' words changed read as words of its own, and the copy's for that day were dropped | Ask a date what it holds, not only what it draws; a day's words are its own only where they are not its series' |
| [2026-10-30](#a-day-read-by-its-date-a-change-read-by-its-texts) | code | The audit's tenth round: a series' copy's words for a day spent where the note had only moved or coloured that day, on the desktop too; a turn-off promising a Restore previous its own save dropped; Enter and a paste read off the two texts; the smaller findings are in the story | Read a thing by what it is: a day by what it holds, a change by where it was made, a copy's words by what the note lost |
| [2026-10-30](#every-place-a-change-made-and-every-date-a-note-holds) | code | The audit's ninth round: the calculator's place followed as one change through a list's markers, a paste and an Undo; a series' copy's days spent that the note never took; a second turn-off over the first one's copy; the smaller findings are in the story | A change is the edits that made it, and a copy is not the note: ask what each edit did, and of each date whether the note holds it |
| [2026-10-30](#kept-where-it-was-found-and-kept-on-after-it-moved) | code | The audit's eighth round: a series' days kept in its copy after the note took them back, so a day reset came back at every start; the calculator's place kept through an Undo that changed nothing; the smaller findings are in the story | What a fix keeps it must also let go of, and say when: ask of each thing kept what makes it stop being the one to keep |
| [2026-10-30](#a-stop-android-made-ten-seconds-late) | checking | The first full walk since round five failed 16 steps: Android stopped an app sent Home ten seconds later on an emulator drawing in software, and the walk read the save, and ended the app, before it was stopped; round seven's checks, never run, looked below the screen; and an Undo in a table cell, broken since round six, sent the next letter under the table | A wait for a fixed time is a guess about the machine: wait for the state the check is about, and read that the act happened before judging what follows from it |
| [2026-10-29](#a-rule-fixed-where-it-was-found-and-asked-again-beside-it) | code | The audit's seventh round: "Read all" took seconds on a row with no space; the app took a template being written off the screen with no draft; the days' move asked the raw file and skipped the Trash; the smaller findings are in the story | A rule put right where it was found is still asked the old way next door: sweep for the fix's question, not its line |
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

## A keyboard nobody asked for, on Android 8

**2026-11-13.** The app says it runs from Android 8.0 (API 26), and had
run on 16 and 17 only. Asked whether it was ready for the stores as a
senior team would ask it, the owner had the older systems tried: Google's
emulator images for 8.0, 11 and 13 were made into emulators of the same
screen as the walk's.

The whole walk, run on 8.0, failed 132 of its first group's checks, and
almost all of them were the tool: it reads the system's own accounts
(`dumpsys`, the keyboard's insets) as Android 17 writes them, and once a
step lost its way every step after it did. A short walk was written for
what an older system could break instead -- `tools/emulator/older_android.py`,
plain taps and typing, the file read back, Android's crash log read for
the app after every step.

Its first photograph on 8.0 showed the list with the search box focused
and the keyboard open over the list's buttons. Nobody had touched
anything: Android 8 gives the window's first view that takes focus by
touch the focus as it starts, and the search box was that view; Android 9
stopped doing so, which is why 11, 13, 16 and 17 never showed it. Every
start on an Android 8 phone opened the keyboard.

On Android 8 alone an empty view now comes first in the window and
catches that focus; a tap still focuses whatever it lands on. The short
walk's check that no keyboard opens at the start failed on the build
before the fix and passes after it; on 8.0, 11, 13 and 17 it walks 18 of
18 steps. That a tap still brings the keyboard was claimed and not walked
(typed text needs only the focus) until a review asked; a step now taps
the search box and finds the keyboard there, 19 of 19 on 8.0. Android
Lint, run the same day, found no call newer than API 26 anywhere in the
app.

**The lesson:** a version the app says it supports is a version it has
been run on, or the claim is a guess. And a tool written for one system
judges that system: on another, read its failures as the tool's before
the app's, and write the small walk the other system needs.

---

## The lows and the second copy, fixed before the stores

**2026-11-13.** The full reading of 11 November left findings of low
severity, which the audit's rule leaves for a wave of their own; the
owner chose to fix every one before the stores, or say why not.

- **State kept by an id a new note can take**: the list's selection and
  the History's open series (written down under Still open the day
  before). Both now keep who the note is -- its uid, else its id, as the
  desktop's `_order_identity` -- and a pick whose note is gone, or whose
  id a new note took, drops out (`Note.identity`, `Note.stillPicked`).
- **A backup's import that failed halfway** left its settings, templates
  and Trash written, the files it had moved in for the notes named by
  nothing, and said only the write's error -- or, for a failure in the
  work done after the first write, "your notes are as they were". All the
  work now comes before the first write, a failed write takes back the
  files the import brought, and the words name what was kept.
- **A day off the schedule sent to the Trash went in twice** when the
  notes' write after the Trash's failed, or the phone stopped between
  them; the Trash skips a day it already holds. The desktop had the same.
- **A text file shared from Drive was read on the screen's thread**, and
  Drive fetches as it is read: the screen waited. It is read on
  `Dispatchers.IO` now.
- **The name typed in Settings was lost** when Android ended the app
  behind Home; it is kept as the app goes to the background, as the
  template's draft is.

And the second copy of every rule: answers the phone gave otherwise than
the desktop's Python, on data a person or another program wrote. Each was
asked of the desktop and brought into line -- Python's spaces in a uid and
a signature (Kotlin's `trim()` keeps U+0085), words counted across a
non-breaking space, a file's size rounded as Python rounds, a share code
holding a list, a template's styles clamped by the line, a card's line
squeezed, a rescue copy spent by its styles normalized, a note's created
stamp at import. One went the other way: a pin and a tick count only when
they are `true`, on both apps now, where the desktop's `bool()` counted a
hand-written "false".

Two of the phone's own tools went the same way. The days fixture maker
had stopped on two fingerprints since 6 November: read code only, the
desktop had changed only how its banner waits behind an Undo, and the
fingerprints moved with no case changing. And the tests' temporary folder,
emptied at each run, kept 276 of the last run's folders once when Lint ran
beside the tests; the emptying is asked again, and a folder it cannot
empty stops the build saying so.

The round that attacked these fixes found what they had not reached, all
of low severity on the phone, each fixed the same day:

- **The shared file read away from the screen's thread had one slot to
  arrive in.** A file read while the app was behind Home waited there for
  the next frame, and words shared meanwhile took its place: the file's
  text never became a note, and nothing said so. Shares now wait in turn.
- **The failed import's words** called a code's import "the backup's",
  and said "what it brings besides them was kept" over a Trash whose own
  write had failed. They name what was kept now. And something in a
  backup the app could not read, met after the notes' files were moved
  in, left those files named by nothing; they are taken back as a failed
  write's are.
- **A day off the schedule the Trash already held** kept that entry's
  first clock, so its words could leave sooner than the question had
  just promised; the clock starts again (the desktop the same).
- **More of the second copy's corners**: a rescue copy's colour, ticks
  and date compared as written rather than as a load shapes them; a code
  holding one plain value called damaged, and one with text after its
  object taken as whole (text after it that Android's reader takes for a
  comment still passes); dates, colours and the name trimmed by Kotlin's
  `trim()`; a size's figures written in the phone's own digits.
- **A selection on a big phone** was lost when the phone was turned
  upright with a note open beside the list (it is kept now, up to about
  400 notes, what one handover of the screen's state safely carries), and
  a note restored from the Trash without a uid kept none, so the list knew
  it by an id a later note could take.

The second round found the first round's fixes holding, and their
neighbours not yet: a long shared file cut at 500,000 characters without
a word (a medium: it is said now); the share queue taking files in the
order their reads finished, so a slow file's code replaced one shared
after it (each share now holds its place from the moment it comes); a
failed import's kept categories and Trash left out of the screens, the
next category written over them (it reads the files again, and says its
reason in the long bar); a code of one 300,000-letter string, whose check
recursed past the thread's stack (now walked, not matched); and the
second copy's next corners -- a colour in another script's figures,
category names and a restored uid trimmed by `trim()`, days left counted
past the window for a stamp ahead of the clock. Left, each answered: a
second code shared over an open code dialog takes its place, as the walk
asks; the figures Android writes in words on a phone set to another
script; a date written by hand with a space inside it; and Android's
reader taking a comment after a code's object.

Two more came from the walk, not the reviewers. Two of the first round's
commits said "the walk's shared-file part goes on reading a file into a
note": no part of the walk had ever shared a file. One does now
(`shared_files`, through the app's own provider), and it checks the cut.
Its first check of the order could not fail -- a plain file is read in
milliseconds, before the words could come, under the old order too -- as
the third round showed; it shares a pipe now, which the read waits on
until the walk writes into it, and checks that the words wait. And the
walk's restore from the Trash failed three times in a row: a note picked
there right after it was deleted showed no Restore button, because the
delete's "Moved to Trash · Undo" bar lay over it for its ten seconds --
passed before only because the bar had gone by then. Dismissing the bar
on a pick, the first answer, also dismissed Undos that were the only way
back (a day's words); the third round said so, and the Trash page now
tells the window where its buttons are, and messages are drawn above them,
as above the editor's foot.

The third round also found: a shared file that could not be read, or held
no text, dropped without a word, and one whose read never returned
holding back every share after it (each is said now, given up after half
a minute); a share code cut off with its file called damaged (said as cut
now); and more of the second copy's corners -- categories adopted from
notes judged by `trim()` and a title of one mark by `isBlank()`, a restored
note with no stamps left unstamped, and the code question's catch narrower
than Android's JSONException.

The fourth round found two roads older than the wave. On a phone on its
side the Trash page's top (the window's days, the backup, Select all) took
nearly all the height, leaving its list less than a row and its buttons
squeezed; the top scrolls with the list now, and a new walk part,
`trash_wide`, brings a row into sight there and restores it. And a .txt
saved in Notepad's old "ANSI" came in with every ă turned into a
replacement mark, a UTF-16 one as letters and NULs, both said "Saved as a
new note": a shared file is read in the encoding its first bytes name and
refused, and said, when it is not UTF-8, as is a file of a BOM or a NEL
alone. The restore's stamps had overshot: a stamp of spaces, which the
desktop keeps, was replaced by the moment. A share's extras are read
inside a try, where a sender's odd one ended the app; a code whole within
the first 500,000 characters of a long file goes on as any code; the cut
message no longer asks for the code as words, which cannot carry one that
long. The walk now shares a file the app's own provider cannot open, and
checks it is said and that the words after it still become a note; its
pipe's write is bounded, so an app that never reads it fails a check
instead of stalling the walk. Left: shares still waiting when Android ends
the process, or when Back closes the app during a slow read, go without a
word; a cancelled read keeps its thread until the provider answers; the
restore's question reads a hand-made `_prev` unshaped where the restore
shapes it; and Statistics and Duplicates read the notes as stored, not as
the desktop normalizes them.

The fifth round found the largest letters again, seen on the emulator this
time. Upright at 200%, a note ticked in the Trash left "Delete for good"
beside Restore 76 dp wide, a few letters a line, five lines tall; the two
buttons flow now, the second onto a line of its own. On a 360 dp phone
Statistics broke a four-digit figure between its digits ("123" over "4"),
the fourth round's "Left" had said "may wrap": two tiles a row at that
size now, figures kept on one line, a long category name no longer taking the
line from its count. Both are walked (`trash_wide`, and `statistics` at
200% on a 360 dp screen), and the build before fails each. A UTF-16 file
without its BOM still came in as letters and NULs: a file holding a NUL is
refused now, since no note holds one. Half an emoji cut at the 500,000th
character is left out, words of a NEL alone are judged as a file's are, a
code whole in a cut file reaches its dialog without the file around it
(though a code with no "=" at its end still takes the words after it, so
the dialog names a longer code than it is), and the code question
catches the stack overflow of a hand-made code nested past what it checks.
Case 509 said no walk could share a code that long; the walk now shares a
code whole in a long file (offered and imported) and one the cut broke
(said cut). Its first look at the cut's message missed it once: the
import's own message was still up, and the cut's waited behind it; the
walk puts such messages away before it shares. Left: a weekday written
`true` in a hand-made note signs as 1 for Duplicates where the desktop
keeps it apart; a living note with an empty "updated" sorts oldest here,
newest on the desktop; a wallpaper change on Android 12 and later restarts
the screen, which drops a share still being read and an open code dialog.

The sixth round found the largest letters in three more places. On a
phone narrower than about 400 dp the Trash's row of days left "days" a
letter a line and broke "90" over its digits: the days flow now, walked on
a 360 dp screen. A figure kept on one line was cut where it did not fit (a
four-digit count in the time table on a 320 dp phone): every Statistics
figure now shrinks to its room instead. And a question's text did not
scroll: at the largest letters, or on a phone on its side, the import's
question lost its last sentence under its answers -- the warning that
updating replaces an earlier version -- with nothing to show there was
more; every question's text scrolls now. A share code followed by "=)" or
a line of "=" signs was read on the desktop and called damaged here
(Java's decoder refuses padding past the code's own): the padding is left
to the decoder now. NULs some apps pad a file's end with no longer refuse
it. In the test plan, a second reader found four mediums, older than the
wave: the PDF question quoted in its words of before 2 November (cases 242
and 262), a greyed card's count without the title it adds (265), an "ANSI"
file said refused when one of English letters is UTF-8 to the byte (511),
and Summary's count of the cases that need the owner's eye leaving out 28
that said so only in their title or as "Needs her eye"; each is true now,
and the fifth round's commit had swept that last class wrongly (only one
of its five was uncounted). Left: the selection bar's "12 selected" and
the table bar's words are cut at the largest letters (the table's symbols
still say what each does); and the unit tests' org.json is laxer and
stricter than Android's in corners only hand-made files reach.

The seventh round found the sixth's "every question's text scrolls" true
only of the questions built on Ask and Choice: the off-schedule question
(the day's own text in it, its last paragraph cut), deleting a category,
putting a day back, the PDF question, a note's export and the templates'
questions were drawn straight on AlertDialog. They all scroll now
(`DialogBody`, keyed by the day where one dialog asks day after day), and
the code paste box, which grew with a long code until the problem line and
Paste had no room, is held to a height. At the largest letters
Statistics' "Updated" broke mid-word on a 360 dp phone and the calendar's
month title read "Novembe" over "r 2026": both shrink to their room now.
The walk's photographs of that fix showed three things its checks had
passed: the title, on one line, read "November 202", having stopped
shrinking at 12 sp; the time table's header broke "this month" as "this" /
"mont" / "h"; and its figures ran together, a "0" beside "1234" read as
"01234". The title shrinks to 6 sp now, which fits "September 2026" on a
360 dp phone; a label has no more lines than words; each column keeps a
gap. The walk counts the title's letters drawn on the screen, and measures
the header's lines and the gaps (AS709 to AS711); its first check of
"Updated" measured the label's column, not its word, and compares heights
now. The sixth round's padding fix overshot: a code that lost one of
its two "=" read here and not on the desktop; the phone follows Python's
rule now, too much padding ignored and too little refused. The plan's
Writing cases held fourteen mediums older than the wave: links the
editor moved into ⋮ on 23 October, a Find said to skip a table it now
searches, line and piece limits of before (20,000 and 100, now 40,000 and
500), a long press and a "Never" chip that do not exist, a row's words of
before f873ee2, a paste said to be cut that is refused, a table key and a
menu entry named wrong, letters said kept apart that both apps now fold
(the owner's choice of 3 November), and a size, 18, that neither app
offers. Each is true now. Left: at the largest letters on a 320 dp phone
a tile's caption of one long word ("Categories") may still break mid-word.

The eighth round found two of the seventh's subjects true only in part.
"The month's year is never cut" held at 360 dp and up: on a 320 dp phone
at the largest letters, or in a tablet's calendar column, the arrows and
"📋 History" left the title less room than "September 2026" needs at
6 sp. And the class was left in the other bars: the selection count broke
as "3" / "select" / "ed" beside its four buttons, the bar cutting it right
before 🗑. Every top bar's title is OneLine now, one line, shrunk to its
room and ended in "…" where even 6 sp does not fit, and beside the arrows
at 150% letters History is its mark alone, read out whole. The walk's
photograph at 320 dp then showed the calendar's weekdays ("Mo" / "n") and
the bottom bar's word ("Calenda" / "r") breaking the same way: they are
OneLine too, the rail's words with them. "Every
dialog's text scrolls" missed the table grid, whose last rows a phone on
its side squashed to nothing, and the code box's problem line still fell
under the fold there: the grid scrolls, the problem is brought into view.
A NEL inside a code read on the desktop and not here (Python's whitespace
holds it, isWhitespace does not); the export dialog had lost a gap. The
walk measures each of these (dialogs_on_side, new; the 320 dp step; the
selection count; AS712 to AS716). The plan's reader went through the
Repeating, Organising, Backup and Calendar cases and found fourteen
mediums, none of this wave: Undo said to last eight seconds where
Android's long message gives ten (43, 60), a series' line quoted with
"every day" for "the whole series" (145), the desktop said to bring a day
back over another where it refuses (88), a pinned card said to say why it
will not move where it stops without a word (86), a Clean up button a
single group does not have (91), a sort by Recently created (469), an
import said never to say "Added 0 notes" (480), "Importing..." for
"Reading the backup…" (231, 258), two list words quoted without the note's
name (439, 459), Delete this day held on a day's cell where it sits on
the series' row (415, 64), Off for "Does not repeat" in six cases, a
picker a person could switch notes under (224), and 'Only the date'
offered to a moved day with no text of its own (461). Each is true now,
with the lows, two new rows for the round's fixes, and every row of the
plan naming mutants the catalogue never kept (42, found by a sweep of the
whole plan rather than one area a round) saying so. Beside the round
came the owner's decision D2: a note keeps the top-level fields this
version does not know, by the desktop's rule to the letter, checked
against a fixture of the desktop's own answers (DATA_FORMAT, "Fields a
version does not know"); the desktop's eleventh round then had a backup's
Update keep the note's own in its rescue copy, and the phone does the
same. Left: the same tile caption at 320 dp.

The ninth round attacked D2 first and found it held on every road the
phone takes, and the eighth round's bars, dialogs and NEL held too. One
medium, older than the wave: the Repeat sheet's seven weekday chips sat
in a plain Row, so on a 320 dp phone, or at large letters on 360, Sa and
Su were squeezed to slivers no thumb could tap, and the Ends row broke
inside "After". Its chip rows flow now, as the month's days always did,
walked at 320 dp and 200% (AS730). The lows: the duplicate signatures
held every whole number past 2^63 as one (toLong); a code found without
its prefix kept a NEL; the walk's History check could pass with the
words shown, and an ellipsis's dots counted as letters ("Calen…" as
eight); and no fixture case nested objects past the depth allowed
(AS729). Each is fixed. DATA_FORMAT says what Android's reader changes
(a whole number past 64 bits, 1.0, -0.0, Infinity, a lone surrogate) and
that the phone shapes a note at its door, not at each load. The plan's
last eleven areas, so every case of the plan has been read once since
the wave began, held eleven mediums: the desktop said not to fold
diacritics (7), the shared folder read by a release (56), an old version
number (55), a long comment's field that is a block to read (206), a
template draft said to be lost behind Home (277), four rows naming a
walk or a test that reads nothing of theirs (454, 229, 34, 54), and two of
the eighth round's own new rows, a Settings page that does not exist
(517) and a hand check that could not fail (518). Each is true now.
Left: on the road a code takes without its prefix, the phone's strict
decoder refuses some damaged codes the desktop's lenient one reads.

The tenth round found the ninth's own fix had opened a loss, graded high
by the owner's rule, as the desktop's thirteenth found its twin. The
spending rule, which rightly lets a field the backup added not count, was
also the question Restore previous asked of what it replaces; but the
restore puts back the copy's unknown fields alone, so that field is what
the restored version lacks, and with an older backup's words held in the
note's no copy was made and the field was gone. Each question has its
own function now (unknownHeld, unknownKeptByRestore; AS733). One medium
older than the wave, measured from the ninth's own photo: the Repeat
sheet's stepper put its + last in a plain Row, and on a 320 dp phone at
the largest letters "2 months" left it the row's very end, so a number of
two digits took it away. The measure had said "2 months" already; the
walk's photo showed it whole, and the first fix, the value given the room
but left to wrap, broke the word ("month" / "s"). The value is one line
now, shrunk to what the buttons leave, walked at 12 months (AS732, AS738). Three
sentences a person reads were untrue: the welcome note's "It all stays on
this phone", against Android's backup, which the same note describes;
Settings → Backup's paragraph on that backup, shown as true with the
switch off; and the README's "with Undo" for pin and duplicate. The lows:
numbers compared as doubles, so a copy the desktop keeps was spent here
(AS734); keys written unquoted in the signature (AS735); and what the
ninth left, now both ways: a code whose prefix the wrapping broke is read
from that prefix on both apps, and text shared in holding one is a code,
not a note (AS736, AS737). The desktop's fourteenth round then found that
rule too wide: words saying "NOTE 1:" were called a damaged code, and
shared in they were refused, no note made. A hidden prefix counts now only
when the code it leads to reads (AS739, AS740). And the restore question,
"as it was before it was updated" whenever the copy held more than text,
was asked again over the copy a restore keeps, the updated version itself:
it names the previous version now, as the button does, on both apps.

The eleventh round fuzzed the code reading against the desktop's (two
hundred thousand texts, none read differently) and found one medium of
the tenth's own making: the line after a repeat turned off said Restore
previous brings back the version kept before "not the series", where a
note that repeated before the Update gets the series back; it says the
version kept before now, both apps. The word on notes restored or
imported where the list hides them named the first one's folded group for
all, Show all unfolding that group alone, as the desktop's did (said of
them all now, `Showing.hiddenBy` and `groupsOf`; AS741, AS742). Its
second reader read STATUS and the words written in the Kotlin: the
desktop's toggle said to remember a theme for a session only (it keeps
them since that day's change), seven fixtures where there are eight, the
paste fixture said to keep the desktop's answers beside twenty cases it
no longer holds, 78 walk parts where there are 79, and a damaged code's
words sending its sender to "Settings → Backup → Export", which the phone
has not (Settings → Backup, the place both apps have, now). The lows: a
newer version's code behind a hidden prefix called no share code (AS743);
a double compared by its printing, not its value (AS744); the stepper's −
and + under a thumb's 48 dp; "After 1 times"; and Android's reader
changing integers below −2^63 as well, said now in DATA_FORMAT.

The twelfth round corrected two of the eleventh's sentences. The two
hundred thousand texts read or were refused alike, but not always in the
same words: a code cut within its first sixty characters, before any of
its stream came out, was called too large to import, where the desktop
says damaged or incomplete (alike now, AS748). And AS743 was fixed on the
Import alone: shared in from a chat app, the same newer version's code
behind a broken prefix was still saved as a note (AS745), and from a file
read only in part a whole one was called incomplete (AS749). Its medium:
a settings.json or templates.json that would not read and could not be
copied aside either was written over, at the next start or the next
template saved, where the desktop holds such a file (`Files.holdWrites`
now, both: AS746, AS747; a template's draft held is not deleted either,
AS750). Its second reader read DATA_FORMAT and
ARCHITECTURE whole, and found DATA_FORMAT promising more than both apps
do: a backup carrying the person's choices both ways (it carries the
categories, the calendar's marks and the Trash window), a short "#rgb"
text colour widened where both apps leave it out, a set-aside day titled
with the series' name before its own, styling in a table said unshown
where a cell's highlight is its fill, and no size cap on a backup where
the phone refuses one larger than a sixteenth of its memory. Its
ARCHITECTURE: seven fixtures where there are eight, and line counts that
did not match the day they were measured on. "Every week, 1 times" under
the stepper that says "1 time" went on both apps' series line.

The thirteenth round found the twelfth's fixes held, and one sentence of
its own untrue: DATA_FORMAT said a cell's highlight is drawn as its fill,
where only one covering the whole cell is, and not in the template
editor's tables nor in a table shown as text. And the line counts, said
corrected, were still not the day's in nine rows. The lows, at the hold's
edges and older: a file held stayed held after a later read in the same
run copied it aside (AS753); the start advised turning on Android's
backup and choosing the Trash window again over a settings file nothing
could be written to, and wrote the theme, and said its refusal, at every
activity made, and the reminder bar came back at every return to the app
(AS754, AS755); a code whose stream asks for a preset dictionary, which
Python refuses, read as empty (AS751); with the notes file set aside
unreadable, deleting for good deleted a file a note never read may use,
where the desktop keeps every file then (AS752, AS757); and a backup's
templates were skipped without a word over a templates file here that did
not read, now copied aside and taken as the desktop's empty list takes
them (AS756). Its second reader read qa/README, WALK_RUNS, the
emulator's README and the release steps: counts behind their fixtures
(103 days cases where there are 108, 815 export files where there are
818), eight suites held to the desktop's answers where nine are, the
note-shape gate's five checks where it has six, and the release's fourth
step without the two checks only the release build can answer. Left, with
their reasons: a backup restored over a settings file held stops at the
refused write and says none of its notes came in (true, nothing lost;
the desktop goes on, and the phone's import stops at its first refused
write by design); and a templates file held shows no templates without a
word of its own (rare: it needs a full phone as the page opens; nothing
is lost, and a template saved then is refused, saying why).

The fourteenth round found the thirteenth's worst slip: ending a hold
once a later read in the run copied the file aside let the next write of
settings go out without the marks the start could not write, the Trash's
window unknown and Android's backup off, so the next start would empty the
Trash at thirty days and send the notebook to Google against the person's
"off". A hold lasts the run again, as on the desktop (AS753, aimed now at
that slip). Its second reader checked, on the phone, each rule the desktop
had learned in three rounds, and found two of its words not followed: a
backup's note that differs by an attached file alone was promised Restore
previous and called OLDER, Add put first (counted apart now, its own
words; AS760, AS761), and "Already here, unchanged" said nothing of the
fields a newer version wrote that the backup holds and the note here
lacks (AS762, AS763); STATUS said the same of the update. The lows: a
backup over a templates or Trash file held dropped them without a word,
where every other refused write stops the import and says so (AS764,
AS767); a backup's template with no name was left out, where the desktop
takes it as "Untitled template" (AS758); and the reminder bar came back
once after the notification had opened the day (AS759).

The fifteenth round found the fourteenth's repair too narrow on one side
and too wide on the other. A settings file that would not read but was
copied aside was never held, and the start's own write of its marks could
fail on a full phone: the next write that went out, once room was freed,
dropped them (the Trash at thirty days again, the backup on). Such a file
now reads as its two marks in memory, as the desktop's every read of one
gives its own, so any write carries them (AS766). With that, a hold can end
again once a later read copies the file aside, which on Android 8 to 11 is
the app opened again after Back ended its activity, not its process: held
for the run, every write was refused under words saying the file was kept
(AS753, AS747). Its second reader checked every claim of wave 6 in this
log against the code and found one narrowed: the reload after an import
cleared the mark that keeps attached files while the notes file is unread
(kept for the run now, as the desktop's). The lows: a nameless template
here beside a nameless one in a backup made two (AS765); the Empty Trash
question named attached files that a run keeping them all, or entries with
none, would not delete; and a held Trash file's stop had no test. Taken
back: the fourteenth round's "Not saved:" before a checklist write's
refusal, put on three roads of six and before a cap that is no refusal;
the writer's own reason names the refusal on every road. Left, with its
reason: a backup whose new notes all look like notes here offers Import
anyway or Cancel, where the desktop also offers Skip when the backup
brings more (older than wave 6; nothing is lost, the backup holds all).

The sixteenth round walked a nearly full phone's week end to end and
found three of medium severity on it. The load's problem, never cleared
by a write that worked, kept the backup switch Off after it was turned
on (while the file, and the backup agent, read on) and the Trash's window
unknown after one was chosen: the rules read the marks now, not the
problem (AS769, AS770). A read the storage refused once, then allowed,
was taken for damage: a good settings file was copied aside and the
start wrote its marks over it, and a templates file the same; such a
read now holds the file, never copying it aside, as the desktop's does,
for settings, templates, the draft, the notes, the Trash and an import's
templates. And Save as template over a refused save made the template of
the version saved before, not of the screen: it stops on the "Not saved"
now. Its first reader found the test plan's 283 still expecting the
"Not saved:" the fifteenth round took back. The lows: the backup said off
only when the start's own write of it worked (said whenever the file is
not held now); the unread-notes mark lasting past a reopening on Android
8 to 11, where the desktop clears it at each start (cleared at
Startup.prepare); the refusal's words promising a copy aside a reopening
on Android 12 does not attempt ("until the app is closed and opened
again", as before); the Empty Trash question's choice of words without a
test (AS768); and the welcome note's Show adding another each press when
its uid was not kept (found by its words now, AS771). Left, with their
reasons: a notes file holding the same id twice, met on a full phone, is
repaired only once it can be written, and its list can stop the app
until then (neither app writes such a file; the repair at the start
writes it when it can); a rename refused at the notes file leaves the
Trash and templates renamed (a retry makes them agree); a refused tick
keeps its new stamp; and settling the theme on a full phone says the bare
reason at each activity made.

The seventeenth round found the sixteenth's own rule too wide in two
places. A template's draft held after a refused read dropped every new
draft without a word, and a template typed then went with the app when
Android ended it: a draft is copied aside again, meant to be held never
(the eighteenth round found the copy failing on the same refusal, below;
AS775 guards the words of Use template). And a hold set by a refused read had no
way to end on Android 8 to 11, where Back ends the activity and not the
process: every write was refused after the reopened app read the file
fine. A new start clears the run's holds and unread marks now, each set
again if its file still does not read (AS773). Android's backup agent,
reading settings that would not read, wrote its off mark, which outlived
the read and turned a backup the person never touched off for good: it
hands nothing over then, writing no mark (AS774; since the eighteenth round,
only over a file held, a damaged one copied aside being off by its marks).
Use template over a
templates file that did not read said the person had none (it says the
file could not be read, AS775), and no test reached a refused read at
all: one does now, through a seam that makes the read refuse (AS772). The
desktop, read for this round, held only its settings on a refused read:
its notes, Trash and templates were copied aside when the copy's read got
through, and are held now too. Its second reader found the parity lows:
Remove attachment promising a stored copy gone in a run that keeps every
file, the Trash page advising and asking a choice a held settings file
refuses, and a welcome note typed in and asked for again duplicated (the
uid kept in memory for the run now). Left, with its reason: the import's
plan is made before the editor's last words are saved, so words typed in
a note the backup also holds, between opening the import and answering
it, are not asked about again as the desktop asks (rare: Settings must be
left mid-import; Restore previous brings the words back).

The eighteenth round found three of the seventeenth's sentences untrue on
the next road. The draft's copy aside read the file again, past the seam a
test refuses through: on a refusal that lasts it failed too, held the draft,
and every new one was refused unsaid; on one that passed, a good draft went
where nothing offers it. A refused read is tried once more now and offered
if it gets through, and the copy reads through the seam (AS777, AS776); a
draft still held has the template editor say only Save keeps what is typed.
Settings → Templates over a file that did not read said "No templates yet"
(it says the file could not be read, with a list of junk set aside counted
as unreadable; AS778 guards that count, not the page's words). And Settings → Backup over a held settings file read
Off, "replaced at that next backup", while the agent handed nothing: the
switch is shut there now and the page says so; the agent hands nothing while
the app holds the file, and a damaged file copied aside is off by its marks,
as the page says (AS774). The lows: a restore's off mark deleted over settings
held at the start, the off only in memory, so the next start read on and
handed everything (AS779); settings held at the start read for real later
in the run, the Trash page then contradicting its own chips (held all run
now, as the desktop's, AS780); and a stale welcome uid in settings winning
over the run's (AS781). And the desktop's twentieth round, read for this one,
found the phone's Trash page as open as the desktop's dropdown: over a Trash
held at the start, read empty, a shorter window asked nothing, and the next
start took the old notes and their files; the window cannot be chosen while
the Trash is held now, and the page says why (AS782). Left, with their reasons: a read refused after a
good one in the same run shows the marks, or an empty list, where the desktop
keeps its last good read (it takes the phone's own storage failing mid-run;
the file refused is then held, so nothing is written over it, and since the
twentieth round no attached file is deleted while the Trash is unread); an
import that would bring entries into a held Trash, or templates into a held
templates file, stops whole, where the desktop brings the notes in
(said, and nothing is lost); and on Android 12 and later Back keeps the app
in memory, so "closed and opened again" ends a hold only once Android or
the person ends the app (the words stay true, nothing is lost).

The nineteenth round found one of medium severity in the code and four in the
documents. A template's draft refused at the background for want of room was
thrown away unsaid, the class the eighteenth round answered only for a held
draft: the person back while the app lives is told now that only Save keeps
it. The test plan's case 520 wrote its broken file where the app never reads,
and 522 asked a new note over a notes file whose read was refused, which holds
it; both are set up again, and a case of its own walks the Trash held (523).
DATA_FORMAT said the templates' page says nothing over a broken file, and that
the desktop keeps a long number whole, both no longer so. The lows: a draft
refused twice, its copy's read getting through, set a good draft aside where
nothing offers it (offered from the copy now, AS783); Templates.unreadable said
none where a read refused at load and allowed after found templates (AS784); a
notes or Trash file held at the start read for real later in the run (held all
run now, as settings are, AS785); the Trash page said "The Trash is empty" and
"Kept for N days" over a held Trash; over held settings, every return to the
app cancelled the daily notification, the marks saying it is off (the alarm set
before stays now); Duplicate and Clean up duplicates left their file copies
behind on a refused write (taken back, AS786 for Duplicate, AS788 since the
twentieth round for Clean up); and a development build's copy
of an older notebook said it was brought in after a write was refused (AS787).
Left, with its reason: Android's backup agent, run before any start, can hand
the off mark for a damaged settings file before the start has said the backup
is off; that is the rule as written, and the start says it when the app opens.

The twentieth round found one of high severity on a rare road, the desktop's
twenty-first found again: with the Trash held at the start, or set aside
damaged, a backup import gave a note it brought the file of a trashed note it
could not see, and Remove attachment on that note deleted the file the trashed
note still names. A run that did not read the Trash now deletes no attached
file, as one that did not read the notes, and Remove attachment's question
gives either as the reason (AS789). The lows: when the daily notification
fired, or the phone restarted, over settings that would not read, the alarm
was cancelled (kept now, and set a day on when it fires unread; after a
restart, which clears it, set again at the default hour since the twenty-first
round); Clean up duplicates' take-back had no check (AS788); the bar for a
draft not kept was short, advised a Save a full phone refuses and said all was
lost where an older draft stays (long now; "what you typed may be lost" since
the twenty-first round); templates held at the start
read for real later in the run, as the notes and the Trash no longer do
(AS790); and a category renamed over a held templates file stopped after the
Trash was written, its entries filed under a name no category had (the
templates first now, a held file left as the desktop leaves it, AS791).
DATA_FORMAT's sentences on a later read copying a held file aside, on a draft
offered from its copy, and on files deleted over an unread Trash were made
true of the code.

The twenty-first round found the desktop's twenty-second high on the phone:
over settings held at the start, a backup's Trash came in without the window
it needed, and the next start removed it with its files. The class is closed
as the desktop's twenty-third closed it: each of a backup's trashed notes comes
in only if the window this phone's next start purges by keeps it, the rest
staying in the backup, said with the window; over held settings that window is
not known, so the import stops, as over a held Trash (AS792, AS793). Its
medium: a category renamed on a nearly full phone stopped after its first
writes, the rest filed under a name no category had; the notes go first now,
and a later refusal puts back what was written (AS794). The lows: Statistics
counted 0 over a held Trash or templates file, and counted a templates file's
raw entries (a dash now, and the templates the page lists); the draft-not-kept
bar's words; a held draft read for real later in the run, offered under the
editor's "could not be read" (AS795); after a restart over settings that would
not read, no alarm at all; Continue on a draft over a held templates file said
its template was gone; and the alarm's reader and the count read a held file
for real. Left, with their reasons: a notes file held at the start shows "No
notes yet" once its notice is dismissed, as the desktop's list does (the notice
said it, and nothing is lost); and a run that keeps every file over an unread
Trash leaves orphans no later run takes (no loss).

The twenty-second round found two mediums, both on moving a notebook. A
backup's note filed under a category its settings lacked came in under
General for good, unsaid, where the desktop's import adopts it; and this
phone's own backups lack such categories, because its start adopts them in
memory and never writes them. The import adopts them now, for its notes and
the trashed notes it brings, a name past the ceilings said (AS796, AS797,
AS799). And a backup made while the notes file the start could not read was
still on disk packed it damaged: neither app read the backup back, yet the
phone said "Backup saved." and recorded the date. That file now goes in as
`[]` with its bytes under `unreadable/`, and the phone says the backup holds
no notes as notes and keeps the date it had (AS800 to AS802). The lows: of two
twins in a backup's Trash the older, left out, took the newer with it (AS798);
the twenty-first round's put-back covered the rename's own writes, not the
map's write after them, and a rename or delete over held settings moved every
holder before the map's refusal (refused before anything now, and a refused
map write puts every holder back, AS803 to AS805); AS793 could not fail, its
test holding the settings only after they were read (held before the load
now); a test with a fixed date that would have failed on 1 December; the
import's longer message said in 4 seconds; Statistics showing 0 for a
templates file first refused on its own page; and a new clock or time zone
over unread settings moving the person's alarm to 9 o'clock. Left, with their
reasons: the notification's count reads the notes file without asking about a
hold (it only counts, writes nothing, and a refused read posts nothing); and
over settings that stay unread the alarm is set again each day even for a
person who turned notifications off (it posts nothing while they are unread).

The twenty-third round found the twenty-second's adoption unfinished: the
import adopted a backup's categories, but the plan that sorts its notes was
still made with this phone's names, so a note refiled here under General and
filed under the name in the backup read as unchanged and was skipped, its
category coming back empty -- the very road of re-importing after the bug.
The plan and the import now take their names from one place
(`Importing.merged`), as the desktop adopts before it compares (AS796). The
lows: on the desktop, twins in a backup's Trash under a name it adopts were
signed before the name was known, and both came in; here the round's own
filing under General would have done the same (the Trash's names are
adopted before anything is signed, AS797); a trashed note under a name past
the ceilings kept it, and a note
under one was no look-alike of the same note here under General (filed
under General, as `normalize_note` files it, AS806, AS807); "1 categories",
and no reason given (AS812); the export checked a notes file the start had
read, so a moment short of memory could have packed a good notebook as `[]`
(only a file the start could not read is checked now, AS809), took any older
copy kept aside for this file's (AS808), packed one too large to check as if
sound, said saved and dated (AS810), and put the system's own words, a
private path in them, on a refused file (AS811); the twenty-second's
alarm fix kept the old zone's hour after a new time zone (only a new clock
keeps the alarm now, AS813); and AS793 named a value its own text made
nullable, so it never compiled. Left, with its reason: a category deleted
with its notes sent to the Trash, then its map write refused on a full
phone, leaves those notes in the Trash under the old name (nothing is lost,
the delete is said to have failed, and the notes are restorable).

The twenty-fourth round's medium was in the test plan: case 528 called "not
practical by hand" a road four steps walk (case 525's backup imported, its
category deleted, the backup imported again). The lows: a move's Undo put a
note back under a category deleted since, and the next refresh brought the
deleted name back, while after the category moved to was renamed the Undo
said the notes were no longer where the move put them (a note whose category
is gone stays, and is said, as the desktop says it; the category moved to,
renamed or deleted, ends the Undo with words of its own, AS815, AS816, AS511);
apply adopted a backup's names in another order than the plan, so at the 200
ceiling each kept other names (the file's order in both, AS814); a delete
over held settings asked its question and only then refused (refused before
it now, and said to have changed nothing); AS809 could not fail, a copy
kept aside standing in for the check it watched (the file the start read is
read back as it is now); a copy compared byte for byte was read outside the
words for a refused file; an export's failure was said in four seconds; the
words for a file too large to check also covered a moment short of memory;
and a backup holding no notes as notes dropped the words for a file past
what the phone reads back. Left, with its reason: an added or recoloured
category over held settings is refused here and kept for the session on the
desktop, whose start already said no setting is saved; nothing is lost.

The twenty-fifth round's medium was in the test plan again: case 529's
second step named "keeping its notes" on a category the step before had
emptied, whose question offers only "Delete the category". The steps of
the round's new cases were read against the app's own words this time.
In the code, the desktop's twenty-seventh round found a backup's trashed
note whose file a note here uses brought in again at every import, on
another copy of the file; the phone had the same road, and the same fix:
an entry that matches one here in all but its files' stored names is known
by those files' names and bytes (AS822). The lows: a move's Undo took a
category renamed meanwhile for a deleted one and said the notes could not
go back, where the desktop carries the rename into its offer, and took a
category the map lacked only because its last note had moved out (the
phone's start does not save the names it adopts) for a deleted one (the
offer now follows a rename and knows a delete by the delete itself, AS817,
AS818, AS815); it counted notes filed again since among those that could
not go back; a delete over a notes file not read asked "No note is filed
under it" before refusing (AS819); this Trash was signed by a refused
name as stored, so a twin filed under General came in twice (signed as
the desktop signs it, AS821); a trashed series restored kept a day under
a category gone since, which the next refresh brought back (filed under
General, AS820); and a rename's refused map write said the system's
words, a private path in them (AS823). Left, with its reason: "Day
removed"'s Undo puts a day back under its own category even if that
category was deleted meanwhile, as the desktop does (seconds-wide; no note
is lost).

The twenty-sixth round's medium was in the test plan once more: case 529's
fourth step still expected the words of a rename the Undo now follows (it
puts the note back, saying nothing). The other medium was in the code: the
twenty-fifth round's match by a file's bytes looked only in this phone's
folder, where a backup's file lands only when the import places it, so once
the file a copy was made from was gone from here, the same backup brought
its trashed note in again, where the desktop, which copies every file in
first, did not (the unpacked backup is looked in too, AS824). With it came
the desktop's twenty-eighth round, the same mechanism on the notes' road:
a note that came in on a copy of its file was offered as changed at every
import, and each Update added another copy (its files are known by name and
bytes now, in the plan and the Update, AS828). The lows: the bytes were
compared one at a time (a block at a time now, AS825); a category deleted
and made again before a move's Undo kept its note, said to have nowhere to
go, where the desktop asks the map (AS826); and a refused write of the
map, or of the notes, the Trash or the templates inside a rename or
delete, said the system's words with the app's private path in them
(words of their own there, AS823, AS827; the twenty-seventh round took
the rest). Left, with their reasons: the move's bar keeps the old name
after a rename, where the desktop shows its banner again (the Undo itself
follows the rename); "renamed or deleted since" in putBackFiled is reached
now only by a category the map lost without a delete, an adopted name
whose last note moved away; and the bytes are still compared on the
screen's thread while the import writes, as the desktop's are (slow only
for very large files).

The twenty-seventh round's medium was parity, older than the wave: a
skipped look-alike was dropped with its files, where the desktop has given
them, since wave 2, to the note here it looks like, so the same backup and
the same answer left a file on one app's note and not the other's. The
phone gives them now, and says how many (AS829). The lows: a category
delete's own Trash move, putBackFiled and every other write the notebook
refused still said the system's words: the atomic write now gives the
cause alone ("the phone's storage is full", "refused the write"), so no
road shows the private path, and Organizing's writes say which file
(AS830); a delete whose notes had gone to the Trash said only "the
category was left as it was" when a later write was refused (it names the
Trash now); case 531 called its hand check a choice where its own test
hands the folders to the plan and nothing else reaches MainActivity's
wiring. Left, with their reasons: when every new note in a backup is a
look-alike and they would bring only files, the question offers Import
anyway or Cancel, not Skip, as the fifteenth round left it (the files stay
in the backup); a file held after the load turns refile's refusal into
"try again" where reopening is what helps (very rare); and the count of
notes that "took attached files" is made before their copies, as the
desktop's is, so a copy refused on a full phone is counted.

The twenty-eighth round graded that first "left" a medium, as the round
before had graded its partial case: when every new note in a backup looks
alike, the question offered Import anyway or Cancel, and the default Cancel
dropped what Skip would have brought, where the desktop asks whether the
backup brings more. The phone asks it now (`Importing.bringsMore`, worked
out before the question opens): a file a look-alike gives the note here it
looks like, a category, a template, a Trash entry or a day's marks this
phone lacks, each tested as the import itself tests it, the Trash signed,
the templates named and the days merged by the same helpers apply uses
(AS831 to AS836). The other medium was older than the wave: a backup
bigger than the room left failed while it was copied in, and the phone said
"That file couldn't be read as a backup: write failed: ENOSPC", which
blamed the file in the system's words. A full phone is said as one now,
on every backup road, and no road shows the app's private path (AS837 to
AS839). The documents: case 531 named a button a new test notebook never
shows ("Skip it", where its welcome note makes it "Skip the 2"), and this
file's data format said Skip was always offered, and that the files went
to "that note" where two notes may look alike. The round's parity reader
also found the desktop dropping a look-alike's file when the same import
updated the note it looks like, and giving a file to the last of two
look-alikes where the phone gives it to the first: fixed there, the first
one now on both. The lows: a write's bare cause reached the bar alone, and
twice inside another sentence ("Not saved: Your notes could not be saved:
…"); a delete the Trash refused told a full phone to try again in a
moment; "1 duplicates skipped"; and the look-alikes' second pass ran with
none skipped. Left, with its reason: over a held templates, Trash or
settings file the words "Skipped, the rest of this import still happens"
are followed by the import stopping at that file, which is said (rare,
and stopping at the first refused write is by design).

The twenty-ninth round's parity reader found the phone a step behind the
desktop on the first question, since the desktop's change of 6 November:
a note known here, edited elsewhere only in its date, colour or pin and
answered "Add as a new note", was put to the look-alike question again,
looked like itself, and the question's Cancel undid the whole import (or
Skip skipped the very note chosen to be added, its files given to the
original). Those copies are added past the question now, as copies asked
for, and count as the import going on (AS847 to AS849). And a high by its
kind, on a rare road: apply looked for a skipped look-alike's twin by the
note as updated, while the question had keyed it by the backup's copy, so
a note holding a field a newer version wrote found no twin and the
look-alike's file was dropped after Skip was offered for it. The question,
the skip and the twins now share one map (`twinsOf`), as the desktop's
`_twins_after` (AS846). The attacker found the round's own claims short in
three places: a Trash entry older than this phone keeps was still counted
as something Skip brings (apply leaves it in the backup; AS850), the
export could still show the cache's path (its private root was the
notebook's folder, which does not hold the cache; AS851), and a checklist
row's refused write still reached the bar as its bare cause (it goes
through the same words as every other refusal now). The lows also:
`warm` worked out what Skip brings for the Update answer, which nothing
reads, and the singular "1 was already here" had no mutant (AS852).
Left, with their reasons: a category's refused write still says "try
again in a moment", in the desktop's own words for it; over held settings
a backup's category or day marks are counted by the desktop's question
and not by the phone's, as the desktop keeps them for the session and the
phone stops the import at that write.

The thirtieth round's medium was the class question answered yes on the
copies themselves: a copy asked for ("Add as a new note", "Add a copy
anyway") kept the original's rescue copy, where the desktop's
`_without_identity` has dropped it since 13 November and the phone's own
Duplicate drops it, so the new note offered "↩ Restore previous" and put
the original's older words over a note made now. Copies are made by
`asCopy` now, without the identity or the rescue copy (AS853). The data
format's duplicate key left out the fields a newer version wrote, which
it compares (the screens leave them out on purpose: no one sees them);
it names them now. The lows: a painted day here this phone cannot show
held the day, so the backup's mark never came in, where the desktop's
store is cleaned at every start (AS854); the editor's two "Not saved:"
lines ended a bare cause without the full stop the bar's other refusals
have (AS855; the round after found four more, and the red line); and
case 535 named a Cancel the question about notes already here does not
have. The round's parity reader found on the desktop the Trash's window
asked before the merge adopts the backup's longer one: fixed there.
Left, with their reasons: the questions' count of new notes and the
checklist rows' refusals are drawn on screen, where no JVM test reaches
(the hand cases and the walk do); and a twin
already holding 200 files is offered Skip and said to take files it
cannot (both apps alike, very rare, nothing lost).

The thirty-first round found the code holding and the documents short:
the data format still said the backup's painted days join "without
replacing anything", where the round before had made a mark stored here
that this app cannot show give way to the backup's (counted a medium,
as untrue in a document the release relies on; it says so now), and it
said a Trash entry older than "its Trash keeps" stays out, where the
backup's longer length counts when it is taken. The lows: four more
"Not saved, so …" lines and the editor's red line ended a bare cause
without its full stop (`Files.ended`, AS859; the tests now read two of
those sentences and the editor's whole, AS860); the day markers were read
past the desktop's first 64 entries and measured after their selector
came off, so a day showed a mark the desktop drops (AS857, AS858); the
cap the round before had made count only the marks shown had no mutant
(AS856); an entry of templates.json that
is no template counted toward the cap of templates, where the desktop's
list holds none (AS862); and nothing asked the Skip question with a
backup's longer window (AS861). The round before's medium has a case of
its own (536). Left, with their reasons: when the backup's window cannot
be written at that moment, the desktop's question has offered Skip by it
and its merge keeps by its own (the harmless way; the phone stops the
import there); and a backup whose JSON begins with a byte-order mark may
read here where the desktop refuses it (not seen on a device).

The thirty-second round read the data format whole against both apps.
Both agents graded one finding high by the letter of the owner's rule:
a template is rebuilt from the fields this version knows, on both apps,
so a key a newer version adds to one is lost at the next template saved
or imported; the decision of 13 November covers a note's top level, so
the data format now says so of templates too, and the question whether
to bring templates under it is the owner's. Two sentences of the data
format were untrue, counted medium as the release relies on it: the
desktop's date reader was said to take full-width digits and to read a
day kept under such a key (it takes a year in other digits only, and its
maps are looked up by the written form, so such a key matches no day),
and every write was said to mend unpaired surrogates (the phone mends
the editor's fields and writes any other back as `?`). A third was short
(the copy-in-turn weighs every field it brings back), and ARCHITECTURE
gave Notebook.kt's size of 11 November. The lows: the templates' and the
backup's refusals, a held file's reason and the start's settings lines
ended without a full stop (`Files.ended` now gives one to any reason
without it; AS864); the import's own refusal sentence had no test
(AS863); AS857 removed the measuring instead of putting the selector's
removal back before it (re-aimed); two sentences of the round before
gave the tests and the cap more than they did; and case 536 did not say
how Trip gets its rescue copy. Left, with its reason: the red line above
the note and the line for the same note opened on another of its days
are drawn on screen, where no JVM test reaches.

The owner answered the templates question the same day: a template keeps
the fields a newer version wrote, bounded as a note's, on both apps.
`Templates.normalize` keeps any key beyond the eight it knows
(`KNOWN_TEMPLATE_FIELDS`, through `Normalize.unknownFields`), so they
survive `templates.json`, a backup's import and every save (AS865); the
template editor's Save carries them over from the template it edits
(`Templates.collected`'s `keeping`, AS866); and the note's-shape gate
now holds the two apps' known template fields together. Case 537 walks
it by hand, through a desktop backup.

The thirty-third round found the decision holding on every road of both
apps, and case 537 short: it called its hand check a choice, where the
template editor's Save, which hands the template it edits to
`Templates.collected`, is reached by no test and no walk part (a medium,
in a document the release relies on); it says "Automated in part" now,
and takes a .zip whose templates.json is edited by hand, so no desktop
build of its own is needed. The data format's "Known" bullet, read after
the template sentence, seemed to give a template a note's names, and its
sentence on Infinity and NaN was true of a backup's notes alone (the
phone leaves out, unsaid, a templates, Trash or settings file holding
one, where the desktop reads it less that field). The lows: a held
settings file's start line and an unreadable backup's words still ended
without a full stop (AS868, AS870), and nothing tested the settings
lines' full stops (AS867, AS869); case 528 called "Update this note"
"Update". Left, with their reasons: an import writes this phone's own
templates.json back as the file holds it, so a 33rd field stays until
the next template save (it keeps more, never less); a template replaced
by name, on the person's Replace, takes its newer fields with it, on
both apps, as "Replace the old template with this one" says; and a
damaged settings file beside no notes file on the very first start is
read clean after the welcome note's write, unsaid (older, very rare).

The thirty-fourth round found case 537 still short as a person would
walk it: the backup's template could be one the phone already holds by
name, which an import skips unsaid, and the case named no way to change
a file inside a .zip that works (Windows' own zipped-folder view never
writes it back). It names Weekly now, a template the phone lacks, and
edits templates.json as case 524 edits trash.json: unzipped, changed,
and the files zipped again from inside the folder. The data format's
sentence on Infinity and NaN said "it" where it meant the phone. Left,
with its reason: a held settings file's start line on the first start
says "could not be read or copied aside", true of a refused read too
(it was neither), where the ordinary start's says "could not be opened".

Its second agent read every case's automation column from 500 on
against the tests it names, and found three claiming more than they
check (mediums, in a document the release relies on): case 528's
MainActivity line that hands the plan the merged categories and case
529's Settings → Categories calls after a rename or a delete are
reached by no test, so those cases say "Automated in part" and name the
hand check as the one that walks them; and cases 524 and 525 promised
the result's words, which no test read (they read them whole now,
AS871, AS872). The lows: 521 and 527 said "Automated" over screen
parts no test reaches; 513 and 519 left out part of what is still for
the eye; four cases said a walk part reached no road the walk in fact
passes through (it checks nothing there); and the How to use tab and
qa/README said script-only rows are filled in as Pass with their last
run, where those added in the final audit wait for the release run, and
did not define the column's words. Left, with its reason: the walk's
part for case 505 does not check that the bar is still up when it taps
(a slow run passes with the bar gone; the bar's time is the owner's to
judge by hand).

The thirty-fifth round read the automation column of cases 250 to 499
and found the same class again (mediums): cases 448, 449 and 455 said
"Automated" over promises decided on screen (which note Share and
Export take from a day, a template on a day with a word typed first,
the question's Skip carrying an agreed Update), and seven cases of a
start changed in the Repeat sheet (280, 305, 366, 368, 376, 378, 404)
over the sheet's own Save, which its tests replay by hand; each says
"Automated in part" now and names the hand's part. And the sentence
the round before wrote, that the final audit's script-only rows wait
for the release run, was untrue of sixteen rows the runs of the unit
tests and of the emulator scripts (the walk, and `older_android.py`) on
13 November filled in as Pass under a build
"wave 6" that no screen shows: they name 0.14.2 (wave 6 audit build)
now, and the sentence, on the How to use tab and in qa/README, says how
such rows are filled in and that the release runs them again. Its first
agent found the same, and, low: cases 521 and 527 said "in part" and
"Not a hand check" both, which the new definition did not allow (they
say what no one walks now, and the tab defines "Not walked"); 525 named
two mutants that guard another case; the import's words in their
other one and many forms were read by no test (some were then, every
form since the round after); and
ARCHITECTURE and qa/README named too few of the suites that reach the
drawing side. The lows: the rows naming mutants the catalogue never
kept, most without saying so (it began at AS260; the round-8 sentence
that every such row says so was not true), say so now, from the
catalogue itself (two, 260 and 323, since the round after); case 336 called AS267 not kept, which is; 367
left out AS309; 414 named its fixture entry by a number that is
another's; 451 and 452 named no test; 447 named a walk part that does
not exist; 431, 443, 471 and 313 claimed more than their tests read;
436 named a mutant that guards another fold; 260 a probe in no
repository. Left, with its reason: the other plain "Automated (JVM)"
rows of the editor's, pasting's and days' tests were not traced from
the screen to the model; the next rounds read the cases before 250.

The thirty-sixth round read the cases before 250 and found the class
there too (mediums): fifteen cases of 0.9.1 named only that day's runs,
"unit tests 660/660 and the emulator walk 256/256", over steps no check
of their own walks (22 and 24's "Add as a new note", 41's colour, date
and pin, 45's Empty Trash, 59's "+ Add note", 61's History search, 65's
Reset this day, 91's Undo, 147's offered Undo, 26's and 127's words, and
the lows 3, 13, 109 and 110), and Claude had marked them Pass from those
runs. Each says "Automated in part" now, or "Not walked", and names the
hand's part; and since a Pass says the Expected Result was seen, they
went back to Not Tested, the run kept in Actual Result. So did 505,
whose walk part taps without reading the Undo bar it promises (round 34
had left it). Cases 191, 201 and 214 said "Nothing for a person to
walk", "Optional hand check" and "A hand check can follow these steps"
over roads nothing reaches (two of 191's three, a change of language,
the Repeat sheet's own Save). And the How to use tab and qa/README said
rows only scripts reach are filled in as Pass by a recorded run, where
most stay Not Tested and three were marked Pass by hand: they say now
who records a row's result, and that a Pass by Claude stands only where
the scripts walked all of the case. The lows: 171, 172, 179, 241 and
244 say what is left to the hand; 260 and 323 say their mutants were
not kept; 180 spells its range out; 209 says which of its range guarded
other cases; every form of the import's words is read whole
(ImportingTest; AS873, AS874), as case 533's step 4 reads them; qa/README
names what `AppStateTest` and `EditorTableTest` reach of the drawing
side, and the never-kept mutants from AS1. Left, with its reason: the
other cases of 0.9.1 that name only that day's runs were read by
sampling; the next round reads each.

**The lesson:** a low is written down to be fixed, not lived with. And
the second copy drifts in the corners nobody types into: ask the first
copy the odd questions too.

---

## A desktop fix the port was never asked about

**2026-11-12.** The phone's release build of 0.14.1 was walked on the
emulator, and its photograph of Settings → Statistics said "1
Categories" over a notebook whose Categories page listed five. The tile
was right by the rule it was ported from: the desktop's Statistics
counted the categories notes were filed under. On 11 November the owner
chose that it count every category, and the desktop changed (its
`01cec1e`); the phone, ported from the old line, kept the old answer.

The desktop's fixes of that week that the phone had found were carried
over the same night, each place here that still called them differences
closed. The fixes the desktop's own full reading found were not asked
about here at all, and the Statistics tile was one of them. Nothing
compares the two tiles: the interop fixture holds notes and files, not
what a page counts.

The tile now counts the categories the notebook has, Settings' list with
any name a note brought in, as the desktop's `len(CATEGORY_COLORS)`
does; the list below it still holds only the categories with notes.
`StatsTest` pins it with five categories and notes in one, and the walk's
Statistics part reads two tiles off the screen: Total notes, and
Categories counting one that holds no note. Mutants AS655 (the unit
test) and AS656 (the walk) are the old line put back.

The rest of that reading's desktop fixes were then asked here one by one.
A file taken off a note or deleted from the Trash for good is already
deleted only after the write that stops naming it; the month's buttons
are hidden while the History is open, so they cannot move a month behind
it; and the phone has no bulk export. Two lows of the same class as the
desktop's Share panel fix, state kept by an id a new note can take, are
written down under [Still open](#state-kept-by-an-id-a-new-note-can-take),
not fixed.

**The lesson:** a port is a second copy of every rule, and the desktop's
fixes change rules. When the desktop changes one the phone ports, the
phone is asked in the same change: a sweep by the fix's question does not
stop at the repository's edge.

---

## A message read after it went, a tap between two cards, a list left mid-way

**2026-11-07.** The full walk after the lows wave failed two of its 613
steps, and the same parts walked again failed two others. Each step
had passed in earlier walks, and the app under them had changed only in
its comments. All of them were the walk.

Insert table, on a note of 600 tables, says where to tap for four
seconds. Its first look took five: uiautomator waits for the screen to go
still, and the keyboard coming back over that note kept it busy. The
emulator's log, kept larger for the second walk, shows Insert tapped at
04:39:01.7 and the look's file read at 07.1; no other look in the part
took three seconds. The tenth round had held the same message, said by
Enter on the note of 60,002 lines, for fifteen seconds with Android's own
accessibility setting ([A day read by its date, a change read by its
texts](#a-day-read-by-its-date-a-change-read-by-its-texts)), but not its
two siblings: Insert table and the calculator's Insert say it on the same
heavy notes. Both are held now.

A tap that stops a fling, then a second tap at the same place: the first
must only stop the list, the second open the step menu. Where the list
stops cannot be read while it runs. A tap that landed on a card's words
was already tried again at another height; one that stopped the list
with the gap between two cards under it opened nothing either time, and
read as a second tap that failed. That height is now tried again too, the
handles' boxes read once the list is still. The mutant of the fix this
step guards (AS610, the tap read at the handle's last lift), rebuilt,
still fails it.

The steps in the middle of the list look for places by small swipes --
the last card with nothing under it and its handle clear of the + button
is a band of 18 pixels -- and were written to start with the list at its
top. Round 22 put a turn of the phone before them, and the turn's swipes
leave the list wherever they stop. The list is flung back to its top
first now. One failure was a look that held no card at all. A probe
showed that a look taken while a step menu is open holds only the menu's
window, nine nodes and no card, but no menu should have been open there,
and the cause is not proven. If it comes back, it is said with the
screen's words and a photograph; and the turn's "card left out" is now
read with no menu up.

**The lesson:** a step that searches the screen for a place starts
wherever the step before it stopped, so a step put in front of it changes
its start. A sweep for a fix's siblings goes by the message and the
screen it is said on, not by the step that failed.

---

## Two looks that judged what they never read

**2026-11-07.** Two walks of the part about what hides the list failed
one early step each, and each step passed walked again.

The first: the search for "creme" showed neither card for six seconds,
the field holding something all the while, and the failure kept no
picture. The step had judged the list on words it never read back. It now
reads the field first: a field not holding the words typed is said with
what the screen says, photographed and typed again, and a field holding
them still fails on a wrong list. A probe that typed "crme" for "creme"
got "No notes match your search", the same empty answer, which the step
now catches. A letter lost on the way in is the likely cause, not a
proven one; the typing lost in October ([A check that held by one
frame](#a-check-that-held-by-one-frame)) may have been the same.

The second was proved from the emulator's own log. "Show all notes" was
tapped at 01:39:57.6. The walk's next look sent "remove the last dump;
dump the screen", and adb lost it on the way: for 22 seconds the emulator
received nothing, no dump ran, and the `cat` that followed read the file
of the look before the tap -- "No notes match Show: Home." -- as the
screen after it. The ninth round had put the removal in the same command
as the dump, which mends a dump that writes nothing, not a command that
never arrives. A look now reads the file only when its own dump says it
wrote it ("UI hierchary dumped to"), and a photograph only when its own
capture says so; the colour burst clears its last captures first. A probe
with the first ask lost read the stale screen before the fix and the
fresh one after it.

**The lesson:** a file on the device is evidence of the command that
wrote it only when that command says it ran. Clearing it in the same
command guards against a writer that fails, never against one that never
ran -- and a stale screen can pass a check as easily as fail one.

---

## A bar's button tapped after the bar had gone

**2026-11-07.** A walk on a freshly booted emulator failed 25 steps from
one: "Show all puts the new note on the list". The bar was right in its
photograph. The emulator's own log timed it: the bar rose at 00:00:57, a
Long bar lives ten seconds, and the reads before the tap -- 2.5 s each on
a cold emulator -- put the tap at 00:01:07.5. The tap landed on the card
beneath, opened another note, and every step after ran from the wrong
screen until a Back left the app. Walked again, warm, it passed.

**The fix.** The part holds its bars as Android's Time to take action asks
-- a real setting, which Material's bars keep -- and the step taps past the
bar's own ten seconds, so it fails if the hold does and never because the
machine was slow.

**The lesson:** a check that taps something with a lifetime must own that
lifetime, or the machine's speed decides the verdict.

---

## The list's words, merged and unmerged

**2026-11-07.** What the list says of notes it hides -- restored, imported,
put back, new -- waits until the list is on screen. Rounds 5 to 10 of the
lows wave's verification found my own last fix wrong five times:

- **Round 6.** One slot: a later word wrote over an earlier one.
- **Round 7 (medium).** A queue, but every word shown at once, each saying
  what hid its notes when it was queued -- "hidden by Show: Work" over a
  list showing all. One bar at a time, worded when its turn comes. The
  first cut passed every unit test and failed on the emulator: the list
  asked of the next word before it recomposed, reading Show from the last
  drawing. It now reads the settings as they are.
- **Round 8 (medium).** I had merged words with the same text, and a count
  the text carried was doubled. Unmerged.
- **Round 9 (low).** Unmerged, two restores made one at a time gave two
  bars saying "Restored, hidden by its folded group", the same news twice.
  I merged words about one note each.
- **Round 10 (lows).** That merge undid round 8's fixes: "Added 1 note, 2
  of them hidden", and one Show all for two folded groups.

**The fix** is the one the round-9 finder offered first, and the desktop's
own: a word about one note names it -- "“Taxes” restored", "“Bread”
added" -- so two words are two notes, each with its Show all, and nothing
is merged. The same round gave an import's and a restore's "(earlier
text)" notes a word of their own, and a delete's Undo its note back on
every tab.

Round 11 found the naming done too early. The name was written when the
word was queued, so a note opened from the import's Open and renamed before
the list came back was said by its old name: "“Bread” added" over a list
holding Sourdough. A word about one note now keeps its verb and is named
when it is said (`AppState.Word`, `wordText`), and a new note's word reads
its category then too. And on two panes, where the list never leaves the
screen, the Trash's Undo could give a note back into a waiting word and
then say it again as "Put back". The Undo now records what it gave back
(`UndoReveal`), and the list says only the rest.

**The lesson:** when two fixes keep undoing each other, the choice between
them is the wrong question. Ask what the other program does; here it had
already answered.

---

## The bar that went with whichever note was on screen

**2026-11-06.** When an Update's words are kept aside, the bar at the foot
says so with Show. When the words come back -- an Undo, a paste -- that
note goes again. The lows wave's first verification round found its Show
still on screen, and Show opened an empty new note (medium). The fix
dismissed the bar on screen as the note went.

Round 2 found what "the bar on screen" means on Android. The bars wait in a
queue (Material3's `SnackbarHostState`), so the one on screen can be another
action's Undo -- a checklist row's only way back. Or it can be the bar of a
second note kept aside, whose own stale Show then came up next. On a tablet
the list beside the note queues its own "Kept aside, hidden by" bar, which
outlived the note and whose Show all emptied the search for nothing.

**The fix.** A bar about a note is kept by that note's id
(`AppState.offerAbout`, `offer` returning its Job). A take-back cancels
exactly those bars, on screen or still waiting their turn (`asidesSpent`).
Show all for a note gone says so and changes nothing.

Round 3 found the queue's other side. An Undo waited ten seconds behind an
offer still queued, and a second delete then showed the FIRST delete's
Undo -- the medium wave 2's first round had fixed by dismissing the bar on
screen. Every offer now keeps its Job, and an Undo cancels those still shown
or waiting, and an earlier Undo still waiting. And the list's word on a note
kept aside, held until the list is next on screen, named the next new note,
which is given the same id; it is dropped when the note goes.

Round 4 found that round 3's fix overshot. To let the Undo through, it
threw the waiting offers away, so a hidden import's "hidden by … Show all"
was never said -- a word wave 2 had added on purpose. Each offer is now
kept as made. The ones an Undo goes before come back after it, as the
desktop holds a banner back behind an Undo. The list's waiting word now
drops every gone note at each refresh, on any road, not only the
take-back.

**The lesson:** dismiss what you mean, by name -- not whatever happens to
be showing. On a queue, "the current one" is a guess, and "the newest takes
the bar" holds only if the ones waiting are taken out too.

---

## The walk's first look after an install

**2026-11-06.** Mutant walks failed at the start, "uiautomator wrote no
tree", and once a boot check crashed reading `/proc/stat`. Each part was
fine walked again. Measured: right after `adb install -r` the emulator's
shell answers with nothing for a few seconds -- longer than three quick
asks. It came after every change of APK, which is exactly what a mutant
walk does. The walk now waits after installing until the screen reads
again (`walk.install`), and the boot check reads its counters again while
they come back empty. A first attempt only spaced the screen's asks: it
was not enough, because the next run's boot check met the same silence.

**The lesson:** a flake that comes "now and then" may come after one thing
every time; find what it follows before widening a timeout.

---

## A rule the phone never got, behind a fingerprint comments had stopped

**2026-11-06.** The second wave's lows, on the phone. Rounds 4 to 9 of the
second wave changed what the DESKTOP keeps when a note updated from a backup
or a share code then loses much of its text: the Update's rescue copy -- the
note as it was here before the Update, in no other file -- stays, and the
words the delete takes become a note of their own, "<title> (earlier text)".
The phone never followed. On the phone, one big delete after an Update
replaced that copy with a copy of the backup's words, which the backup still
holds; the note as it was before was gone.

Nothing compared the two. The days fixture (`tools/make_days_fixture.py`)
runs the desktop's own save and holds the phone to it, but it fingerprints
the desktop lines it copies, and the desktop's comment pass of 5 November
had changed those lines' comments: the fixture maker stopped, as it should,
and nobody ran it again -- its stop looked like the comment pass, not like a
change of rule. Two of its regions had changed in code as well.

It was found by the export walk: the same kind of fingerprint stopped it,
and looking at why showed only comments had changed there -- then the days
fixture's regions were checked one by one.

**The fix.** `Notebook.updateNote` keeps an unspent Update copy and makes
the aside note in the same write (`keepTextAside`); a note kept aside goes
again when its words come back in the same session and it is untouched
(`takeBackAside`), and is forgotten when put back from the Trash. The
fixture copies the desktop's branches, with five new cases. And both
fixture makers now fingerprint the code alone (`desktop_fingerprint.py`):
a comment rewritten on the desktop no longer stops them, a changed line
still does.

**The lesson:** a guard that stops for a harmless reason will be treated as
harmless the day it stops for a real one. Make it stop only for what it
guards -- and when it does stop, read the diff before deciding it is noise.

---

## Custom order, twelve rounds to settle

**2026-11-06.** The final audit's second wave, rounds 14 to 25, on the phone
alone: the desktop had met the stop rule at round 14. Custom order arranges
notes by dragging a card's handle, or one place at a time with Move up and
Move down from the handle's menu, TalkBack or Switch Access. Nine of these
twelve rounds found something of medium or above in that one small machine,
and most of it came from the round before: a fix for one road moved what the
next road stood on.

What the rounds found, in order:

- A moved or pinned card went out of sight on a long list (rounds 14 to 16).
  The list follows its first card by key, but only within about a hundred
  rows; past that it keeps the index. It is now held by index.
- A step past the top card or the foot left the card above the screen, under
  the +, or as a strip (rounds 17 to 21). `Showing.intoSight` came first, then
  `afterStep` brought in the card it passed, and in the end the pair comes in
  only when it fits.
- A drag scrolled the list against the finger: it read its way from one
  frame's sign, and turned on a hair's recoil (rounds 17 to 20). The fix was
  `dragHeading` with the touch slop, and an edge that scrolls only past the
  press.
- On its side every card sat inside a 56 dp edge zone, so any drag slid the
  list, and a move of one place went two or three (round 22). `edgeZone` and
  a ramped `edgeStep` followed, as Android's own reorder does it.
- A trade the edge loop made between frames lost its hold to the next finger
  event, whose measure ran in the old order, and the carried card vanished
  with its drag (round 23). The hold is now asked again once the new order
  is composed. In the same round the slow start chained trades with cards
  lying in the list's 4 dp top padding; a card that leads now trades so once.

**The mechanism.** A `LazyColumn` keeps its place either by the first card's
key or by an index asked for the next measure only. Which measure comes next
depends on whether a finger event, a recomposition or the edge loop runs
first. Each fix was right for the frame order it was written against.

**The fix,** as it stands at 97b4811:

- Every decision is a pure `Showing` function pinned by `ListingTest`.
- The walk steps, drags, nudges, puts back and carries with a finger that
  trembles, upright and on its side.
- Mutants AS541 to AS577 were run against it.

Rounds 24 and 25 found nothing of medium or above. The lows they and the
earlier rounds left wait in the lows wave: a slow start that may feel
sluggish, a step TalkBack does not announce, a Back swipe on a handle that
opens its menu.

**The lesson:** where the frame order decides, a gesture's rules are one
machine. Put every decision in a pure function, and re-attack the whole
machine, not only the last fix.

---

## Walk checks that could not tell

**2026-11-06.** The same rounds, on the emulator. Five times a walk check
was not the check it was written to be:

- The comments' label is drawn in capitals, "COMMENTS". A check looking for
  "Comments" failed on a correct screen (571cc61).
- A second drag of the same card could not tell the stale gesture from the
  right one, and AS557 survived. It was replaced by a tap after a step
  (de6afd1).
- The top card's drag measured a handle cut by the top edge, centred on what
  showed of it, and stopped short of a trade on the fixed build. A
  photographed probe found it (6320095).
- The recoil "3 px back" went 8 px on (dbd52dd).
- A pass mark set from one build only. The trembling carry's mark is now set
  between where the fixed build lands and where the mutant stopped, measured
  on both: places 7 and 8 against 22 (97b4811).

Two things the emulator cannot do. adb sends no MOVE while a finger is held,
so round 23's lost hold needed a trembling finger written into the walk. And
AS558, a drag lost beside a taller card, never happened on the emulator, so
it is pinned in `ListingTest` alone.

**The lesson:** a walk check is a claim about the screen. Watch it fail on
the bug and pass on the fix, and measure its mark on both builds.

---

## A fix the next round found wrong

**2026-11-03 to 2026-11-04.** The final audit's second wave, rounds 1 to 13,
on the phone: an ordinary person's first week, and a careless hand. Every
search now folds case and accents by the desktop's rule. Shared text becomes
a note. A note that Show hides is said, with Show all. Round after round,
the next round also found my own fixes wrong:

- Round 7's way of keeping "A new item…" lost it when a day was moved. Round
  8 graded that high.
- Round 8's `closes = true` was set on the call, not on its result (round 9).
- Round 9's reveal read the layout from before the change. The walk and a
  probe caught it.
- A `Box` round the checklist's section stacked its parts on one another,
  and an em size overlapped the rows. Only the walk's photographs saw
  either.
- The keyboard's foot was counted twice on a wide screen.
- The Trash's Undo counted a strip under the list's padding as visible,
  which logcat showed.

**The lesson:** a fix is a new road. The next round re-attacks it by name,
and the photographs see what no check was told to look for.

---

## A mark held in a cell, and the roads that changed the table first

**2026-11-02.** The final audit's third wave, a read of the code and the
documents for what was written by rote; rounds 7 and 8. A table cell's
field holds its words back while they read as a merge's marker or a box
("[x]", "^"): written at once, the cell would turn into a box under the
finger. Only the next letter wrote them, so a cell left on "[x]" kept "[x".
Round 7 held them in the editor and wrote them when the cell was left --
before the table's bar, Undo, Redo and the calculator changed the note.
Round 8 found four more doors that changed the table first: More → Caption
and Line above, Delete table, Insert table. Each dropped the held mark as
stale, and the cell kept "[x" again.

**The mechanism.** The roads were listed by the ones that came to mind. What
they share is a function -- each changes the table's lines -- and a held
value must be written by every caller of that function, not by every button
remembered.

**The fix.** `EditorModel.writeHeldCell` runs first in every operation that
changes a table, and in `write()`; `EditorTableTest` walks each one. The
same wave found the Duplicates clean-up merging the app's own Duplicate's
files a second time: it told a file the kept note already had by its stored
name, which a Duplicate always changes. It now compares the shown name and
the bytes, as the desktop does (AS469-AS476).

**The lesson:** a value held back must be written by every road that can
replace what it belongs to; find the roads by what they change, not by
the buttons that come to mind.

---

## A paste meeting a list, one road at a time

**2026-11-02.** The lows wave's verification, rounds 5 to 11, one agent a
round at the last fix by name. Each found the next case of one class --
what a copy carries and where a paste puts it, at a list. A list line cut
and pasted back on a day read "• • Bread": the rule for Gboard's
autocorrect sending a word and Enter in one edit took the paste for
one. Kept from that rule, the field's paste got no list handling at all: a
numbered line pasted back read 1, 2, 2, a nested one went to the margin, a
day was stored apart from its series. A copy ending in a line break carried
the next line's looks, one begun at a line's end that line's, one begun on
a word the level of the item it came from. A whole item pasted over
another kept the covered marker, "2. 3. Bread" -- over a selection ending
at the next item, then over the last item, then into a new empty item,
"3. 1. Kiwi".

**The mechanism.** Two rules were never written down, so each road answered
for itself: what of a line's looks a copy holds, and what a paste bringing
its own marker does at an item's marker. Each fix covered the road it was
found on, and the next round found the branch beside it; three times the
fix itself had opened it (the corrected-word rule kept off the paste took
the list rules with it; a fix moving the paste to the line's start made the
landing-line rules read it as made there).

**The fix.** The rules as they now stand, on both apps. Every paste the
editor recognises is made by `Pasting.insert`, on a day too, which
renumbers and gives depth as the desktop's paste does; a day's copy carries
its lists' depth. A line's looks travel with its start: a copy holds the
looks only of lines it has from their start. A paste over a list deletes as
typing over it would, except that what the paste brings counts: its own
marker takes the covered one's place when the selection begins at an
item's marker, its own break the covered break's. With nothing selected at
an item's marker end, the item keeps its marker, kind and looks, and the
paste's own marker goes, as Word keeps a paragraph's list when a paste
brings no paragraph mark. Round 9 gave the agent the rule written out and
asked for its whole matrix -- 84,138 cases replayed, the two apps' deletion
rules agreeing on all 6,146 compared -- rather than one road; rounds 10 and
11 found one more case, on the desktop's side, then none. AS441-AS460.

**The lesson:** when each fix of a rule leaves the case beside it, write
the rule down and test its whole matrix on both apps at once; a fix by road
is a fix of one cell.

---

## A day's lists, read by line

**2026-11-01.** The lows wave's verification, rounds 1 to 4. The wave
stopped a day of its own text from wearing the series' looks, and the
first round found what that took away: a nested numbered list on such a day
showed flat, and the next Enter renumbered it and stored it. Given its depth
back from the series, the second round found the older form of it: add an
item on the day, or a line above its list, and the items below took the
depth of whatever stood on their line in the series. Then a day lacked the
whole note's list rules, so Enter then Backspace at a nested item's start
put it at the margin, stored; and a paste through the field, which the line
remap reads as lines taken away, dropped a nested item's depth.

**The mechanism.** A day's override kept its words and nothing of its
lists, and a list's depth lives in the paragraph entries, which only the
series had, numbered by line: every read of a day's lists asked the series
what stood on the same line.

**The fix.** The owner's call, "a day keeps its own lists": an override
stores `paragraphs`, its own list lines (kind and depth, never alignment),
at every save of the day; a day saved before falls back to the series by
line; it follows the series again only when its lists match too
(DATA_FORMAT). A day runs the list rules the whole note runs, and the lines
an edit leaves take the looks of the line it began in, as the desktop's
text gives inserted text the tags around it. AS431-AS440.

**The lesson:** what a copy can change, the copy keeps; structure borrowed
by line number holds only until the first line moves.

---

## A fuzz that never took the road it guarded

**2026-11-01.** The owner's lows wave, after wave 1 closed on both apps.
DaysTest's fuzz of a series' days, written in the seventeenth round so the
rounds would stop finding lost words one road at a time, passed on its 120
seeds. The rounds since measured it: it never took Reset, the Undos, Make
it a note, the Trash and back; it never reached round 21's road (a copy's
words for a day the note deleted) in thousands of roads; three of the next
12,000 seeds doubled a word.

**The mechanism.** Round 21's shape comes from 0.13.0 -- a series turned
off and on as it was, its days in the copy alone -- and no step built it.
With the step added, the fuzz still let a loss through: it excused any word
whose copy "changed", as the one-slot rule allows for a copy replaced, and
a copy spent back into the note changes too. And the gate reading the
results had a floor of 880 against 924 tests: one class at a time could
stop being discovered, the gate green.

**The fix.** The fuzz is rewritten: 360 roads through the phone's own
actions, those it never took among them, 0.13.0's turn-off and turn-on
followed by the install's read, and the copy's repeat put back. It must
reach round 21's road; it excuses a copy replaced, never one spent; words
shown twice -- the duplicates the owner chose to leave -- are counted, not
failed; History must find every day the calendar draws.
`check_test_results.py` holds each class to the @Test count its file
holds, and a newer fixture makes the results stale. days_cases.json takes
two moves onto one date in both orders. The same wave fixed what the
rounds had recorded as low where a person can meet it (the commit lists
them); hand-made date forms are written down in DATA_FORMAT.

**The lesson:** a random test proves only the roads it takes: count that it
reaches the branch it guards, and take that branch's fix out to watch it
fail alone.

---

## The day the editor stayed on

**2026-11-01.** The final audit's twenty-third round. A weekly meeting's
day deleted, then a backup of the notebook from before the delete
imported with Update: the day was back, and the note kept a copy holding
it deleted. Open that day, tap "↩ Restore previous": the restored version
deletes the day again, and the editor stayed on it. What was typed next
said "Saved for this day only", and went to a day the calendar did not
draw, the list of days off the schedule did not list, and History's search
did not find -- on the phone, and on the desktop after a backup.

**The mechanism.** The editor read the note back after the restore and
left the day only when the note no longer repeated (round four's
`leaveDayIfNotRepeating`). A note can still repeat and not draw that day:
deleted, or a rule that no longer gives the date. And `Days.saveDay` asked
only whether the note repeats, where `deleteDay` and `moveDay` asked
whether the series draws the day (`givenDay`).

**The fix.** `AppState.leaveDayIfNotDrawn` leaves the day for the whole
note whenever the series no longer draws it; `Days.saveDay` refuses a day
it does not draw, as the day's other actions do. The desktop the same:
`load_note` opens a day only where the series draws one. EditorTypingTest,
DaysTest; mutants AS415 and AS416.

**The lesson:** a guard written for one way a day stops being drawn is a
question about every way: ask whether the day is drawn, not why it might
not be.

---

## Two moves onto one date, and a list that threw

**2026-11-01.** The final audit's nineteenth round. A series holding two
moves onto one date gave History two rows of one key, and the Compose list
threw at every unfolding of that series. 0.13.0 leaves that shape after
three ordinary taps -- a day moved, the repeat made every two weeks, another
day moved onto the same date -- and the read kept both moves in the raw
notes the calendar and History read; this build made it only by the Undo of
a deleted moved day beside a dead move. The finder graded it low, by the
owner's rule, on this build's road; the session took it as the crash it is
on the road through the install over 0.13.0.

**The fix.** `Notebook.settleAsRead` keeps one move a date in the raw notes
(`Normalize.cleanMoves`, the view's own rule); `Recurrence.history` lists a
moved date once, for the move the calendar draws; `Days.undoDeleteDay`
drops a dead move onto the date, as `Recurrence.moveDay` does. The desktop
the same, where it drew a double row. DaysTest; mutants AS410 to AS412.

**The twentieth round on this fix.** Of two moves onto one date,
`cleanMoves` kept the one whose own date sorts first -- often the dead one
-- and this round's fix carried that into the raw notes the calendar reads:
a meeting moved under 0.13.0 jumped back to its own date at the install,
its words listed off the schedule. `cleanMoves` now keeps the move whose
own date the rule gives (`Recurrence.ruleDaysAmong`, asked only of dates
two moves land on), as the desktop's read does. Mutant AS413.

**The twenty-first round, the owner's call.** A day the note deleted won
over the copy's words for it, which were dropped (the tenth round's rule).
But 0.13.0, turned off and on as it was, showed such a day blank, its words
in the copy alone: deleting a day that looked empty erased words the person
never saw, at the install. Now the day stays deleted and the copy's words
are set aside, a note "(earlier text)" (`copyDaysPlaced`, as the desktop's
`_copy_days_placed`). days_cases.json 99 cases; mutant AS414.

**The lesson:** a grade is about the road a person takes: a crash an
upgrade can reach is not low because this build alone makes it rarely.

---

## History and the days it never asked about

**2026-11-01.** The final audit's eighteenth round, small, the first under
the owner's severity rule. A series with an end, or a count, listed its
days in History only between its start and end: a day moved past the end,
or before the start, was drawn on the calendar and in no row, so a search
for its words -- History is the one search of a day's own words -- found
nothing, and the row counted one day fewer. An endless series skipped every
deleted date, so a day moved onto one was missed the same way; round
seventeen had fixed that for the off-schedule list, not for History. The
desktop's `history_entries` did the same.

The desktop's round found one more on both: a copy's rule compared as
stored (`spent`, `replacedByRestore`) while round seventeen's `sameSeries`
read a weekday lit or not as one rule, so a series turned back on as it
was kept its copy unspent, Restore previous offered for nothing.

**The fix.** `Recurrence.history` adds the dates moved days are drawn on to
a bounded series' days, and skips a deleted date only when no move lands
on it, drawn or off the schedule. `copyFieldHeld` compares a copy's rule by
the days it gives. DaysTest's fuzz now shifts a run of days a day later and
counts a word kept only where a person can reach it. Mutants AS407 to
AS409.

**What it left:** the round's lows are on the audit's list for a wave of
their own (an old notebook's two moves onto one date; an Undo putting a
move back beside a dead one; the fuzz's roads not taken, and its counting
a word kept wherever the file holds it).

**The lesson:** a fix to one reader of a shape is a question to every
reader of it.

---

## Words that never move on their own

**2026-11-01.** The final audit's seventeenth round, small, on round
sixteen's fixes and the series' days as a whole. The desktop's round found
a day's text lost: a week shifted a day at a time (Wed to Thu, Tue to Wed,
Mon to Tue), then the shift made the rule, and round sixteen's moving of
words sent home one by one wrote what was left over the Wednesday's. The
phone's round found two of its own:

- **A day listed nowhere.** A day moved onto a deleted date, its own date
  then no longer the rule's: drawn by no day, and `offScheduleDays` left
  out every deleted date.
- **An Update's copy, as a restore leaves it**, replaced by a big delete
  with its days' words in it alone: `copyDaysSetAside` asked the copy's
  shape, not what it holds.

And on both: a dead move's date taken by a second move, read as the dead
one; a weekly series turned back on as it was read as another when its
start's weekday was lit once and not the next time.

**The fix -- the owner's choice.** Since round eleven nearly every finding
came from rules that moved a day's words on their own, and each was the
next round's finding. Words now never move on their own:
`Normalize.dropHiddenMoves` (was `movesHome`) drops a move onto a date the
rule now gives, its words staying where they showed. `offScheduleDays`
asks a deleted date a day was moved onto of the series as it is.
`Recurrence.moveDay` drops a dead move onto the date it moves a day to.
`Notebook.sameSeries` reads a weekly rule's start weekday, or a monthly
rule's day, lit or left to the start, as one rule (`repeatsAs`,
`keptDaysFit`). `copyDaysSetAside` takes any copy that repeats, except the
one a turn-off keeps whole (`turnOffKeepsCopy`); a backup's Update counts
its own days as held. `Startup` writes the notes made at the start
whatever the Trash or settings file holds. days_cases.json: 98 cases;
DaysTest gains the deleted date listed, the dead move, the weekday lit,
and a fuzz of a series' days through the phone's own actions. Mutants
AS397 to AS406.

**And the audit itself, the owner's choice:** a committed fuzz of a
series' days on both apps; a finding keeps a round open only if ordinary
steps in the app reproduce it (losing data always counts); lows wait for a
wave of their own.

**What it left:** a rule changed and changed back does not bring back a
move it dropped; Restore previous of a copy holding some day kinds and not
others puts those beside the current series' others.

**The lesson:** do not move a person's words on their own -- drop what
hides them, and show the rest.

---

## A home the rule no longer gives, and a move the calendar never saw go

**2026-11-01.** The final audit's sixteenth round, small, on round
fifteen's rule, found the same two on both apps, and two of the phone's own.

- **Words sent off the calendar.** A weekly day moved onto a Thursday,
  the series then moved to Thursdays: `movesHome` sent the day "home" to
  its Monday, a date the rule no longer gives, its words now a day off the
  schedule.
- **A chain walked once a link.** Each pass sent one link of a chain of
  moves home; a crafted backup froze the desktop's window for 8.5 s.
- **The phone's calendar read what no read had sent home.** `movesHome`
  ran in `Normalize`, but the calendar and every day's action read the raw
  notes; and Restore previous, on both apps, sent no day home. A day moved
  under a newer rule stayed on a date the older rule gives, drawn nowhere,
  and Delete this day there deleted the hidden day instead.
- **Smaller.** A copy of another series asked for each day by its date
  alone, so a day the note had moved since was set aside as earlier text
  it still held. The start's notes were written only by the next save, so
  each launch made them again under new identities. `dayMatchesSeries`
  and the day's save trimmed a title with Kotlin's `trim`, which keeps a
  NEL the desktop strips. DaysTest read the notes made from memory, never
  from the file a save wrote.

**The fix.** `Normalize.movesHome` walks each chain once
(`linksLeaving`, asked once a link); a day goes home only where the rule
still gives its own date and it was not deleted, else the move goes and
its words stay where they showed. `Notebook.settleAsRead` -- the load and
both restores from the Trash -- takes the copy's days back and sends days
home in the raw notes; `restorePrevious` sends them home too.
`copyDaysSetAside` looks for a day where the note holds it now. `Startup`
writes the notes a load made at once. `pyStrip` in `dayMatchesSeries` and
`saveDay`. DaysTest compares the notes made from the file a step wrote.
days_cases.json: five cases more, and the fixture tool fingerprints four
more regions of the desktop. Mutants AS387 to AS396.

**What it left:** a rule changed and changed back does not bring back a
move it dropped. A share code's Update keeps no move in its copy, so
Restore previous after it brings the rule back, not the move.
`contentSignature` and the copy-suffix rule still trim the Kotlin way:
both compare the phone's own notes with each other. A made note keeps a
category the notebook no longer has.

**The lesson:** a rule that moves something "back" must ask whether
"back" still exists -- and run where the reader reads, not only where the
other app does.

---

## Four ways of adding, and a note of its own

**2026-11-01.** The final audit's fifteenth round, small, on round
fourteen's fixes, found on both apps that four rounds of adding a copy's
words to a day had each left a case.

- **Another series' days in no file.** A note repeating as another
  series than its copy's -- turned on from another start or rule -- kept
  the old series' days with words in the copy alone; the next turn-off,
  big delete or import Update replaced the copy, and they were gone.
- **The line comparison's own cases.** A line moved within a day came
  back as added text, and the copy's text, kept raw, read a day of the
  same words as different.
- **A day gone under a new rule.** A day moved onto a Tuesday, then the
  repeat made daily: the Tuesday was the rule's own day, the moved day
  was drawn nowhere, and a delete there took it.
- **A day only coloured read as words** when its title had a space after
  it.

**The fix -- the owner's choice, the simpler rule.** A day holding words
of its own keeps them exactly; the copy's different words for it are set
aside (`_set_aside`, carried by `Normalize` as the desktop carries it)
and become a note of their own on that date, titled with the series'
name and "(earlier text)" (`asideNotes`): at the load, which says so at
the top of the list, and in the same write as every save that sets some
aside (`settleAside`). `wordsAdded`, `linesLacked` and `lineKey` are
gone. `copyDaysSetAside` runs before a turn-off, a big delete or an
Update replaces another series' copy. A day moved onto a date its rule
gives by itself goes home with its words (`Normalize.movesHome`,
`Recurrence.ruleDaysAmong`), at every read and every save or update of
the series. `sameWord` reads a title without its spaces
and a text with a table's line on both sides or neither, as the
desktop's `_same_word`. days_cases.json: 88 cases, the notes made
compared too; DaysTest walks a start moved in the editor and a share
code made daily. A file's `_set_aside` is read at most
`MAX_OCCURRENCES` long, as moved days are (a commit's security review:
each entry becomes a note at the start). Mutants AS364 to AS386.

**What it left:** the desktop says on its status line when a save makes
such a note; the phone says it only at the start. A note set aside may
repeat lines the day also holds: only the same words count as held.

**The lesson:** when each fix of a merging rule leaves a new case, stop
merging -- keep both, side by side, where the person sees them.

---

## A day cut off by a table's line, the old text again, a cell's bold dropped

**2026-10-31.** The final audit's fourteenth round, small, on round
thirteen's fixes, came back with a high and a medium on the phone, and a
medium on both apps from the desktop's round.

- **A day cut off by a table's line.** Round thirteen gave a day's text
  the line under a closing table, as the desktop does; the series' text
  typed on this phone stays as typed. Every reader comparing the two saw
  a difference nobody made: a day put back to its series' words stayed
  its own edit, cut off from the series' later edits; a day given only a
  colour read as holding words, and the repeat's turn-off made a copy,
  said the days' text was kept, and kept the days.
- **The old text again.** Round thirteen's `linesLacked` added every line
  after the first difference: a day corrected in its first line or its
  middle, or one table cell edited, got its old text again under it.
- **A cell's bold dropped.** A letter typed in a table cell moved the
  cell's styles off the texts: a cell narrower than its column, or aligned
  right or centre, cut or dropped the bold on its words. The phone's own
  paste of a web table brings such bold; nothing shows it on the phone,
  and the desktop and the exports show it lost.

**The fix:** `Normalize.sameBody` compares a day's and its series' text
with the line under a closing table on both sides or neither, in every
reader. `linesLacked` adds the copy's lines outside the longest run the
two share in order, a table's rows compared by their cells' words, with
its header and separator (`lineKey`) -- the desktop's rule. The cell's
field hands its cursor to `carryCellStyles` (`cellWordsCaret`), which
moves the typed cell's styles by the edit as made, the letters taking
the style of the one before them, as the desktop's cell editor does.
check_test_results' floor rises to 880. Mutants AS358 to AS363.

**What it left:** the calculator's answer and a paste into a cell still
move the cell's styles off the texts; a keyboard line break put in at
once (a clipboard chip, dictation) drops the line's look; a deleted day's
kept edit is counted in the series' banner.

**The lesson:** a rule written into one side of a comparison is written
into both. And to keep what is lacking, compare what both hold, in order.

---

## A day's words added twice and slowly, a table's line the phone never wrote

**2026-10-31.** The final audit's thirteenth round, small, on round
twelve's fixes, came back with two highs and a medium on the phone, and
the desktop's with the same doubled words.

- **Words added twice.** Round twelve's `wordsAdded` added a copy's day's
  words whole whenever they differed from the day's: a day grown from the
  copy's text got it again under its own, and an emptied word a blank line.
- **Added slowly.** It read the day's words off a copy of the whole note
  made for each day -- written out and parsed again. Turning on a series
  with a year of days of its own grew with the square of the days, on the
  main thread.
- **A table's line.** The desktop's `clean_override` gives a day's text
  ending in a table the line under it; the phone's `cleanOverride`, said
  to be the same, did not, and its day save neither. The same note then
  differed between the apps: an import of the desktop's backup read it as
  changed, its Update spending the real rescue copy on a copy of itself,
  and a copy's table added under it was glued onto it.
- **A column's width.** `editColumns` moved the styles off the texts: a
  separator put in above a row beginning "| " was found two letters into
  that row, the first cell's colour spread over it, and the next row
  added dropped it. Round twelve's sweep of the editor's inserts missed it.

**The fix:** `linesLacked`, as the desktop's `_lines_lacked`: nothing
when the day holds the copy's lines as one run, else all but those it
begins with, whole. `wordsAdded` reads the day's edit and the note, not a
copy -- 90 days 3 ms, 360 days 13 ms, a test timing one against the
other. `cleanOverride` passes a day's body through `withCaretLine`.
`editColumns` moves the styles through the edit as made; `addCaption`
clears the Source line's own break. Mutants AS352 to AS357 -- AS352
passed until the fixture held a day ending in a table with nothing in
the copy for it.

**What it left:** a cell's words still move their styles off the texts
(typing, pasting and the calculator's answer in a cell); a keyboard that
edits an earlier word and leaves the cursor at the end drops the styles
between.

**The lesson:** a rule that adds asks what is already there, as lines.
And a cost paid for each item never carries the whole.

---

## Words kept where nothing looked, a day's Undo over another, styles read off the texts

**2026-10-31.** The final audit's twelfth round -- small, its areas
turned to round eleven's fixes and what rounds ten and eleven had not
covered -- came back on the phone with a high and two mediums; the
desktop's, with two highs on the same keep.

- **Round eleven's keep.** The words a copy's day held that no day could
  take stayed in the copy -- still the series' own copy, so every start
  read it again by date, without the copy's moves and deleted days: a
  reset day took the old words back, and words filed under another day's
  own date were dropped or put on the wrong day at the second start. On
  the desktop, Restore previous of such a copy parked every other day's
  edits in its one slot.
- **A deleted day's Undo over another day.** Delete a day, move another
  onto its date, Undo the delete -- the snackbars queue, so the Undo
  waits through the move: the day came back over the moved one, which no
  calendar drew; its words read as the deleted day's, the deleted day's
  own lost. `undoDeleteDay` asked whether the rule gives the day, not
  whether the date was free. Both apps.
- **Styles read off the texts.** A table, a caption, a line above and a
  calculator answer moved the styles by `remapSpans`, which reads an
  insert at the end of the longest common prefix: a run starting where
  the insert landed grew over all of it -- a whole table highlighted --
  though the editor already built the edit as made for the places and
  Undo. A letter deleted beside its twin took its style, the same way.

**The fix:** by the owner's choice, a copy's day's own words that a day
already holding words lacks go on that day, after its own (`wordsAdded`),
and the copy's days are spent as before; nothing is kept in the copy.
`undoDeleteDay` refuses a date another day or a kept edit holds, saying
which. The styles move through the edit as made (`Paragraphs.moveSpans`)
for every insert the editor makes and for a deletion at the cursor. The
refusal sentences say "text or a colour of its own". check_test_results
fails on any skip but the clock's one. Mutants AS346 to AS351.

**What it left:** the template editor still moves its styles by
`remapSpans` at every change, with no edit to read; a deleted day's edit
kept from an old notebook blocks its date and no screen lists it.

**The lesson:** a place the next read looks at again is not a place to
keep things -- keep them where they are seen. And a change that knows
where it was made says so to everything it moves.

---

## A day moved onto words no calendar showed, a copy's words no day could take

**2026-10-31.** The final audit's eleventh round -- small, two auditors a
side, by the owner's call on the week's usage -- found the same high on
both apps, each auditor in its own, and a second high the desktop's
auditor proved on both.

- **A move over words nobody could see.** A weekly series changed from
  Mondays to Tuesdays keeps the Mondays' own words, off the schedule.
  "📅 Move this day…" onto such a Monday was accepted: `dayIsFree` asked
  only whether the calendar draws a day there. The move wrote over the
  kept words, and its Undo took the day back without them; a day with no
  words of its own took them as its own instead, and the Undo filed them
  under the wrong day.
- **A copy's words read as the note's.** A day coloured while the series
  said one thing keeps the series' words as they were. The series' text
  edited after, the day held words no other did, and `dayHasWords` read
  them as its own: the take-back dropped the copy's own text for that
  day, and 0.12.0's notebook -- the one on the owner's phone -- would
  have lost it at the next build's first start. The tie the tenth round
  called "a shape neither app writes", two of the copy's days for one
  date, the move above writes.

**The fix:** `dayIsFree` also refuses a date the series keeps an edit for
(`keepsWordsOn`), and the move says why. A copy's day is dropped only
where the note deleted it; one whose own words the note's day does not
hold (`ownWordsHeld` -- the series' words are not the day's), or that
lost its date to another of the copy's, stays in the copy under the date
it wanted, and Restore previous brings it -- in `spendSeriesDays` and in
the notebook's read, `daysBackAsRead`, which emptied the copy on its own
and was found by the fixture before any test of its own. The owner chose
this over a finer test of the words. The desktop does the same, and asks
the phone's question before a day comes home. `days_cases.json`, rebuilt
from the desktop's core, holds 68 cases; of the 62 before one changed, as
intended. Mutants AS339 to AS345 (AS344 not offered: its shape is one the
fixture cannot tell apart).

**What it left:** a copy is one slot -- another turn-off or big delete on
that note makes a new one, and the kept words go with the old. The
walk's timing, a low: its opening is cut at the first idle second, not
anchored at the tap, so work moved off the main thread or behind a delay
could pass, and a blinking cursor would fail at twenty seconds; today's
openings run on the main thread with no field focused.

**The lesson:** ask a date what it holds, not only what it draws; a day's
words are its own only where they are not its series'.

---

## A day read by its date, a change read by its texts

**2026-10-30.** The final audit's tenth round attacked round nine's fixes
and came back, on the phone, with a high and two mediums shared with the
desktop's own round and two lows beside them, seven lows of the phone's
own -- two of them only plausible -- and the walk's two, besides five
duplicates; the desktop's round brought its data rules again (04044ce
there, then 99f041e). Most were one thing read by another beside it: a day by a date
that named it, a change by the two texts around it, a copy's words by
whether they differed from the note's.

- **A day's words spent where only its date was held.** Round nine took a
  series' copy's days back by the date: any date the note's own days
  named -- a day moved away, a day only coloured -- won it, and the copy's
  own words for that very day were spent with the copy's days. A 0.12.0
  notebook, or the desktop's 0.97.405, turned off and back on as it was,
  then that day moved or coloured, lost the words at the first start of
  this build; and in this build, Restore previous and then the start put
  back lost a day's words written under the other start. The desktop
  measured the same rule over 20,000 shapes either editor makes: 1,879
  lost a day's words.
- **Older words given back, and a big delete's not.** A second turn-off
  gave the first one's copy its words whenever the two differed: a line
  added between went back to the older words at Restore previous, and the
  next turn-off put it in no file. And it gave them only from a copy of
  words or of this very series: back on from another start, every two
  weeks or to an end date, then off again, and a big delete's words were
  replaced by the short text -- on both apps.
- **A promise its own save dropped.** After an Update that only turned the
  repeat on, the turn-off's copy was the note again in full, and the same
  save dropped it; the Repeat sheet still said Restore previous brings it
  back.
- **A change read off the two texts.** Enter typed at the calculator's
  last answer, a line below it, was read as the break after the old one,
  and the place stayed before the new line: the next answer went above the
  words typed under the last, where the desktop's Tk mark of right gravity
  goes after them. A centred line broken there left its new half at the
  margin; Backspace at an empty line's start glued the next answer to the
  line below. At a box's cap, the title's paste, read at the cursor, was
  read again off the texts: " fresh" pasted after "Buy" kept "fre", glued
  to "milk". And a paste's own trailing space, all a cap took, was said to
  be shortened.
- **An id at the edge.** Round nine held an id written past an Int at
  2,147,483,647, and the next new note's id was that and one more:
  -2,147,483,648, the same for every new note after it, two notes edited as
  one. A number past an Int was cut down onto another's, 4,294,967,301
  read as 5.
- **Letters folded that the desktop keeps.** In-note find folded U+023A and
  U+023E, whose lower case takes a byte more in UTF-8, and every letter
  past the basic plane; the desktop's Tcl fold (098d5a8) keeps them all.
- **Messages over a row, and drawn by two sheets.** A checklist row being
  renamed was not counted at the editor's foot: brought just above the
  keyboard, it stood under the window's messages, its ✕ under a message's
  Undo -- round nine's left list said it held no key a message could take.
  And the Repeat sheet over the note's details drew each message as the
  details did under it, two live regions; round nine silenced only the
  window's host.
- **A skip counted as a pass.** EditorTypingTest's Assume that two letters
  made one step of Undo ended the whole method when the clock said
  otherwise, and the list's and the paste's cases went unrun with it.
- **The walk's own.** `drawn_in` timed an opening to the last frame its
  thread held 5 ms or more: a redraw started 5.6 ms late, as the walk's own
  look had the app draw again, set the reading, and a mutant holding the
  thread 2.4 s, drawing nothing, read 2.04 to 2.10 s against a line of
  two. And a message of four seconds was read by looks of two seconds and
  more on the walk's heaviest screen: once in two runs the race was lost.

**The fix:** the desktop's rule, ported: a copy's day is dropped only where
the note deleted that day or wrote words of its own for it; anywhere else
its words go to the date the note draws the day on, and a note's edit
there with no words of its own takes them, its colour and category laid
over (`Notebook.copyDaysPlaced`). The copy's deleted days and moves still
ask which dates the note holds, and a copy's move onto a day whose own
move stays behind stays behind too, followed in one worklist
(`copyMovesTaken`). A turn-off gives an earlier copy's words -- any copy
but an Update's, which is kept whole -- only when the note has lost words
to it, the big delete's own test from the copy to the note. The Repeat
sheet promises Restore previous only while a copy the note does not hold
again is left (`Days.repeatOffWord`). A typed edit is read up to the
cursor the field leaves, and a deletion at it (`Editing.proseEdit`,
`changeAt`); the calculator's place and the lines' looks are carried
through it, and a table, a caption and a line above a table are carried
through the edit as made. Every capped box reads a paste up to its cursor,
and says it cut only when words were left out (`fitRow`). An id an Int
cannot hold takes a new number as a shared one does, and past the highest
an Int holds a new note takes the lowest number free (`Organizing.Ids`);
the desktop keeps such an id. Find folds as Tcl does, in the basic plane
only, never into more bytes. A row being renamed is counted at the
editor's foot, and of the hosts over the window only the deepest draws.
The Assume guards its own two cases. The walk times an opening with no
look inside the time -- the app's frames and the slow messages on its
thread, read until both are still -- and holds a message for fifteen
seconds while it reads it. Each with a test and a mutant watched failing:
AS317 to AS325 for the desktop's rules, AS326 to AS338 for the phone's
own, twenty-one of twenty-two killed at the first run. AS322 -- an edit
the copy keeps under a day it deleted, read as that day's -- survived:
the fixture holds no such shape. `DaysTest` now holds the desktop's own
answer for it, asked of its core. The renamed row at the editor's foot
and the walk's changes have no mutant; the walk reads the first. `days_cases.json`, rebuilt from the desktop's
core at 99f041e, holds 62 cases, thirteen new; none of the 49 before
changed. The unit tests count 896.

**The lesson:** read a thing by what it is, not by what stands beside it
-- a day by what it holds, not by a date its neighbours name; a change by
the place it was made, not by the two texts around it; a copy's words by
what the note has lost, not by whether the two differ.

**The full walk after it** passed 538 steps and failed one, the same
step as round nine's, and again the step was wrong: the template of
90,000 cells read 4.5 s, then 8.3, 8.3 and 5.9 run alone, where the
same build had read 0.98 s two hours before. Its opening, read once ten
seconds after the tap with no read in between, was 0.62 s. The rest was
the walk's: each of its reads of the frames is a message on the app's
thread (`ActivityThread$H`, `m=165`, gfxinfo's dump), and with that
template open it ran 40 ms, over the 20 ms the Looper logs. So every read
was work found, the reading never went still, and a lone frame seconds
later was taken in at its end. The walk now leaves out its own reads, and
the opening ends at the first second of nothing on the device's clock:
0.59 to 0.67 s, three runs in three, and the template mutant, holding the
thread 2.4 s, read 3.27 s and failed.

---

## Every place a change made, and every date a note holds

**2026-10-30.** The final audit's ninth round attacked round eight's fixes
and came back, on the phone, with two mediums and eleven lows, three of
them duplicates, and a high shared with the desktop; the desktop's round
brought its data rules again (8b5d6cc there). Most were one change read as
if it were one place, or one copy read as if it were the note.

- **The calculator's place, followed as one change.** Round eight carried
  the place through Undo by the change between the two texts, from where
  they first differ to where they last do. A list's markers put on three
  lines, Enter in a numbered list above the answer, a paste that renumbered
  the list below it, and an Undo of letters typed in two places within a
  second each changed several places, and the place between them went to
  the end of the last: "3×3=9Rent", or "4" left alone on a line and
  "3×3=9. Rent".
- **A copy's days spent that the note never took.** Round eight spent a
  series' own copy's days once the note repeated as that series again,
  taken back or not: 0.12.0's series turned back on as it was, then any
  other day touched there, held its first day's own text in the copy
  alone, and the first start of the new build erased it; turned on again
  from another start, then the start put back, the save spent a deleted
  day the note never held, and it came back on the calendar for good.
- **A second turn-off over the first one's copy.** A big delete, the
  repeat off, back on as it was, off again: the second turn-off's copy
  replaced the first one's of the same series, and the deleted words --
  or a long comment the comments' own save had kept there -- were in no
  file.
- **The Repeat sheet's two writes.** It saves the start, then the rule;
  the first write was asked whether the note repeats as its copy's series
  with the new start and the old rule, and spent and dropped a copy the
  desktop's one save keeps.
- **A title paste by the desktop's old rule.** Round eight copied the
  desktop's `" ".join(pasted.split())` an hour before the desktop replaced
  it: " and more" copied with its break went in glued, "Groceriesand
  more", and a tab stayed in the title. Read off the two texts alone, a
  paste before the title's own space took that space into its break.
- **What Python reads, read otherwise.** `int()` skips its own six spaces,
  not `str.isspace()`'s: an interval of U+001C then 7 was 7 here and
  refused there, weekly against daily; an id written as "1_0" or as a
  float was read by `toIntOrNull`. A plain paste in CR LF kept every CR in
  the note's text, its comments and a cell, where the capped boxes had
  been mended. Restore previous weighed the styles of the same words only,
  as the desktop did before its ae6efe2.
- **Messages over what is being typed.** Drawn just above the keyboard,
  the window's messages covered the editor's format bar, "A new item…" and
  its Add, and took their taps; over a sheet, each message was drawn by two
  hosts, two live regions TalkBack could read twice.
- **The walk's own.** A look at the screen read the file of the look
  before when uiautomator gave up on a frozen app, so the longer the freeze
  the surer a frame check passed; one frame check read its frames 1.5 s
  after a tap, with no look; and a part walked on its own took no turn.

**The fix:** places remembered in the text are carried through each edit
of a change in turn, as the desktop's Tk mark of right gravity moves
through each insert and delete -- a list's edits (`Paragraphs.Done.inTurn`),
a paste's (`Pasting.Inserted.edits`), and, for Undo and Redo, the edits each
step recorded (`EditorModel.Step`). A series' own copy's days come back to
the note on every date its own days leave free, the note's own day and move
winning their dates, and only then are the copy's spent -- at every read
and in the save that turns the series back on, which on the phone is the
Repeat sheet's write of the repeat, never its write of the start
(`Notebook.updateNote`'s `scheduleFollows`). A turn-off keeps an unspent
copy of this same series' words as it keeps a copy of words. An arriving
note is read as a load reads it, its copy's days taken back, before both
sides are compared bare. The turn-off over an import's Update says that
Restore previous brings the note from before the update. A read shapes
only a copy that can be a series' own: round eight's guard shaped every
note an import's Update had made a copy of, twice a start -- measured on
the computer, 11 to 16 ms a read for 1,000 such notes, the read itself
76 to 78 ms; not timed on a phone. The title's paste
is the desktop's 51f0c44 join, in one pass, read up to the cursor the field
leaves. `int()` is ported with its own spaces; a CR that comes into a box
is one line break; a style on part of the copy's words is weighed as lost.
The window's messages stand above what the editor draws at its foot, and
while a sheet draws them the window draws none. The walk reads a look that
gave up as a failure, reads every frame after a look, and takes the turn
at its first adb call. Each with a test and a mutant watched failing:
AS288 to AS305 for the phone's own findings, AS306 to AS315 for the
desktop's rules; AS316, round eight's wider guard on the read put back,
survived as it must -- the same answers, only more work, which the
measurement above is for. The editor's foot and the window's host hidden
under a sheet are drawn, with no unit test to reach them: the walk reads
the first. `days_cases.json`, rebuilt from the desktop's core at
8b5d6cc, holds 49 cases, twelve new; one answer changed -- the eighth
round's backup made before a reset now reads, truly, as changed.

**The lesson:** a change is the edits that made it, and a copy is not the
note. Read as the difference between before and after, three edits were
one and a place between them went to the far end; read as "the note holds
them now", days the note never took were spent. Ask what each edit did,
and of each date whether the note holds it -- not whether the two ends
look alike.

**The full walk after it** failed one step, and the step was wrong: "a
template holding the 300 by 300 table opens within two seconds" read
3.2 s, three times in three, for an opening of half a second. Round nine
had the walk read the frames once the screen showed the result, not 1.5 s
after the tap, and what it then read was more than the opening. A row
tapped to open a dialog kept its ripple going under it for about seven
seconds, drawn by the render thread alone (gfxinfo's flag 2, no code of
the app's in it); and each of the walk's looks at the screen had the app
draw again what it showed. The walk now lists no render-thread frame, and
times an opening by the frames the app's thread held before drawing them
-- 5 ms or more, or 0.1 s in all: 0.44 to 0.77 s. A template editor made
to spend 2.4 s in thirty frames of 80 ms read 2.04 to 2.10 s and failed;
gfxinfo lists only frames that draw, so work that draws nothing shows only
as the next frame's wait. The ripple is Android's own: the row stays
shaded while its dialog is open and fades a few seconds after it closes.

---

## Kept where it was found, and kept on after it moved

**2026-10-30.** The final audit's eighth round attacked round seven's
fixes and came back, on the phone, with four mediums and thirteen lows,
one of them a duplicate; the desktop's own round brought four data rules
the phone then took word for word (947e9d3 there). Most were a thing kept
-- a place, a copy's days, a copy's comments, a copy -- kept on one road
and not on the next, or kept on after it had moved.

- **The calculator's place, half kept.** Round seven kept the place the
  calculator's last answer ended through an Undo that left the words as
  they were; one that changed them still emptied it, and the next answer
  was glued on where the cursor was: Insert, ↶, ↷, Insert gave
  "Total: 2+2=43×3=9". The desktop's Tk mark moves through Ctrl+Z and
  Ctrl+Y. And on d6b5850's road, an Undo in a table cell, the cursor's
  place was emptied too: once the cell let go, the answer went to the
  note's end.
- **A series' days kept twice.** Round seven took a series' days back
  from its own copy into a note repeating as that series again, and left
  them in the copy as well: a day the person then reset -- the note holding
  no day, the very state the move was for -- came back from the copy at the
  next start, in a duplicate, after a Trash restore, and through a backup
  made a minute before, which, compared with its copy, read as changed.
- **The comments taken whichever way the loss ran.** Round seven's save
  of the comments alone kept the waiting copy's words and always took the
  comments: two letters cleared after a long comment was deleted wrote
  over it.
- **An import's copy replaced.** A repeat turned off after an import's
  Update made the turn-off's copy over the Update's: the note as it was
  before the Update was in no file.
- **Enter told by length.** Round seven moved the title to a value with its
  cursor, and kept the old Enter test -- one letter longer, one line break
  in all. In a title already holding a break (the phone before 0.11, a
  desktop template, a share code) every letter passed it: dropped, the
  cursor sent to the note's words, the next letters written there. And a
  tap that only moved the cursor rewrote such a title and saved the note.
- **Said where no one could see.** The category sheet's refusals -- a
  name used, one over 80 letters -- went to the window under it, and Save
  looked as if it did nothing; the Repeat sheet's the same. On a phone on
  its side the note's details were taller than the window, their foot cut
  and the message under them given no room. The words a dialog says under
  its own box were read out to no one.
- **A paste weighed as copied.** The phone weighed CR LF as two letters
  and kept the CR in every capped box, and took a title paste of two lines
  as two lines with a CR between; the desktop's round seven had settled
  both.
- **The walk's own.** Its frame checks read the frames a fixed time after
  the act, and only frames that have ended are listed: a frame longer than
  the pause was in no list, and round seven's "Read all" passed with its
  slow search put back. It took the machine's one turn halfway through, at
  the export part, sixteen parts beside whatever ran. And two of its PASS
  lines claimed a Continue and a Discard that were never pressed.

**The fix:** the calculator's place and the cursor's move through Undo and
Redo as typing moves them (`Calculator.follow`). A series' own copy's days
are spent once the note repeats as that series -- at every read, after the
move, and in every save of the whole note, the Repeat sheet's turn-on among
them; a note that does not repeat keeps its copy's days. An arriving note
is compared without its copy, as the note here is. A waiting copy keeps
the comments whose loss would be the larger. A turn-off keeps an unspent
Update copy whole, giving it the series only where it lacks it. Enter is
what came in being one line break, and a tap writes nothing. Every sheet
that stays open over a refusal shows the app's messages over itself, the
details and the category sheets scroll, and words said under a box are a
live region. A paste is weighed as the desktop puts it in. The walk takes
its turn as it starts, reads every frame after a look at the screen, and
reads its premises back; and `refusals_and_caps_said` walks the round. Each with a
test and a mutant watched failing: AS260 to AS277 for the phone's own
findings, AS278 to AS286 for the desktop's rules. `days_cases.json`,
rebuilt from the desktop's core at 947e9d3, holds 37 cases: six new -- a
day reset after the editor's turn-on and after 0.12.0's, read again; the
backup made before the reset; 0.12.0's own backup where the move ran; a
turn-off over a share code's Update; two letters of comments over a long
one -- and two answers changed, both a 0.12.0 series turned back on as it
was, read: its copy no longer holds the days the note took back. And
`DaysTest` judges a step that writes nothing -- a read, an import that
changes nothing -- by the note as the app holds it, which its next write
stores: read from the file, 0.12.0's own backup imported where the move
had run showed the notebook as 0.12.0 left it, the moved days not yet
written.

**The lesson:** what a fix keeps, it must also let go of, and say when. A
place kept through one kind of Undo was still dropped by the other; days
kept in a copy after the note had taken them back came back over a reset;
comments kept whichever way the loss ran overwrote the larger. For each
thing kept, ask what makes it stop being the one to keep -- and keep that
answer in the same place as the keeping.

**The full walk after it** failed ten steps, all in the round's own part,
and was right twice. Typed at once after a title's break, " list" came
back " li": the box showed the break, the first letter made the editor
rewrite it under the keyboard, and letters arriving in that moment were
lost -- a letter every tenth of a second kept them all. The box now shows
the title on one line from the start, so nothing is rewritten; the note
keeps its break until something is typed in (6d8e458, AS287). And looking
at its photographs found the second: five photos named for a message
showed none of it. Four had been taken after a second reading of the
screen, the message's four seconds over -- the walk's own, now photographed
the moment the words are read (`shot_as_said`). The fifth was not late:
in the note's editor, the keyboard up, the app's words were drawn behind
the keys. The window's messages kept above the bar and the +, never above
the keyboard; the sheets and the template editor already did. They are
now, and the walk reads the words' place against the keyboard's top
(failed before: the words at 2,220, the keyboard from 1,517). The rest
were the walk's: on its side the list shows two of four notes. A word in
the screen's tree is not a word seen; a photo is looked at for what it is
named for.

---

## A stop Android made ten seconds late

**2026-10-30.** The first full walk since the audit's fifth round, run
after its seventh, failed 16 of its steps. It ran on an emulator drawing
in software: drawn by the graphics card, the emulator had fallen over in
the middle of the walk before. None of the 16 was understood until a
probe wrote down what Android said around every Home.

- **A save read before it was made.** Android stops an app sent Home
  once the launcher has nothing more to do, or ten seconds later.
  Drawing in software, the launcher never rests, and the stop -- the
  moment the app writes what the editor holds -- came at ten seconds,
  every time. The walk read the file one to three seconds after Home, and
  the words were not there yet; read again once the check had failed,
  they were. And `am kill`, the walk's "Android ended the app behind
  Home", ends only an app off the screen: of one still paused it ended
  nothing and said nothing. So "the words are there after Android ended
  the process" passed with the process never ended -- twice, once in a
  part that had passed for weeks. Every Home in the walk now waits until
  Android says the app is stopped (`home()`), and every ending reads that
  the process is gone (`kill_behind()`).
- **Checks never run.** Round seven's `unbroken_rows_and_caps` was written with
  the round and left for the emulator. It looked for "Show less" under a
  row of a million letters, seventy lines below the screen; tapped a full
  list's "A new item…" without scrolling past its 205 rows; and left that
  note with words the full list could not take -- where Back keeps the
  note open and offers "Leave anyway" -- so every step after it was on
  the wrong screen. Each now does what a person does, and all nine pass.
- **A bug two rounds old.** The walk's oldest table check, not run since
  round five, failed for the app: words typed in a cell, Undo, and the
  next letter went to the line under the table. Round six had put the
  cursor after Undo by the rule for prose (91c0264), and a place inside a
  table is in no prose field: the line under the grid took the cursor,
  and the cell let go. A change inside the table whose cell is being
  written in now leaves the cursor in the cell (`EditorTableTest`;
  mutants AS258 and AS259, both killed).

**The lesson:** a wait for a fixed time is a guess about the machine.
Wait for the state the check is about, and read that the act happened --
a process ended, a file written -- before judging what follows from it.
And the walk run in parts is not the walk: a part nobody ran is a check
nobody has.

---

## A rule fixed where it was found, and asked again beside it

**2026-10-29.** The final audit's seventh round attacked round six's fixes
and the shapes around them, and came back, on the phone, with three
highs, five mediums and five lows, and five duplicates of them. Almost
every one was round six's own rule, put right where it was found and
still asked the old way one place over: by the box beside the one fixed,
the reader beside the one that moved, the copy beside the one weighed.

- **A search held to nothing.** Round six's "Read all" cut a long row into
  parts of 300, each ending at the last space within the 60 letters
  before its end -- and looked for that space with `lastIndexOf`, back to
  the row's very start. A row with no space in it -- one long word,
  Chinese, a pasted blob -- read the whole row before every part: the
  square of its length over 600 letters read on the screen's thread, and
  a row of five million letters, from a share code of 7 KB, held "Read
  all" or a search's stop in it for tens of seconds. Nothing else the
  grep found asks a near question with a far search: Find, the field
  rule and the read-only pieces all go forward once.
- **The cap's neighbours.** Round six replaced a checklist row's
  `take(2,000)` with the desktop's `_limit_entry`; the note's title, a
  template's name and title, Save as template's name and the name in
  Settings kept `take` of each new value, in UTF-16 units: a letter
  typed at the start of a full title pushed its last letter off unsaid, a
  title of 150 emoji -- one the desktop holds -- kept 100 at the first
  letter typed, and a letter typed at the cap could be cut in two. And
  the row cap's words were said once an editor: a second long paste after
  Add, or into another row, was cut without a word.
- **A place emptied where nothing moved.** Undo and Redo forgot where the
  cursor had been writing, and only a change of the words put it back:
  B, then ↶, then ⊞ on the same bar put the table at the note's end --
  onto a table closing it, refused with words about a note too large.
- **The draft's two holes.** Round six kept a template being written as
  a draft when the app went to the background. The app taking the editor
  off the screen itself -- the reminder's notification tapped while the
  app was in front, which opens the calendar, or an import's Open --
  went past it: no draft, no question, everything typed gone. And a draft
  was never deleted when the changes were taken back by hand, so an older
  one was offered later, and Continue then Save wrote back words the
  person had removed.
- **Words behind a full list.** At 1,000 rows the note's "A new item…"
  box took words, and only Add or leaving the note said there was no
  room; Home ran a save that failed with no one to tell.
- **The units of the rescue rule.** The phone counted what an edit takes
  away in UTF-16 units where the desktop counts letters: eight emoji
  deleted read as sixteen gone, the phone kept a copy the desktop did
  not, and it replaced the one holding a deleted paragraph.
- **The days' move, asked of the wrong note, from the wrong copy, on one
  road of three.** Round six brought a series' days back from 0.12.0's
  copy into the note at load. It asked the file as it lay, where the
  desktop asks the note as its load shapes it; it never asked a Trash
  entry being restored, so a series 0.12.0 turned off and deleted kept
  its days in the copy alone again, and the next copy of words erased
  them; it skipped 0.12.0's "turned off, then back on as it was", which
  left the days in the copy too; and it took days from an update's copy
  of a series as well, which on the desktop made the same backup read as
  changed the second time and its Update spend the only earlier version.
- **The comments, weighed for the copy and not for the restore.** Round
  six let wiping the comments make a rescue copy, and that copy, of the
  note as it stood, replaced one holding a deleted body. And "↩ Restore
  previous" weighed the body and the title it wrote over, not the
  comments: comments typed since the copy went to no file, the question
  promising nothing to keep.

**The fix:** the space is looked for within the slack alone. Every box
kept to a length is kept by the one rule, its own words whole, only what
came in cut, in letters, said in the desktop's words for that box -- over
the template editor, and under the box where a dialog or a page stands
over the app's messages -- and said again at the next cut that does not
go on from the words the last one left. Undo and Redo keep the place
written at when the words did not change. The template editor keeps a
draft whenever it leaves the screen without being closed, and a draft
of nothing changed is deleted. A full list's box says so as the words
begin. What an edit takes away is counted in letters, as Python counts.
And the days' move and the comments follow the desktop's own fix
(cdbb89e there): the move is asked of the note as a load shapes it,
at load and as a Trash entry is restored; only from the series' own
copy, which holds none of an update's category, colour, checklist and
pin; into a note that no longer repeats or repeats again as that same
series; and an update's comparison reads the note without its copy. A
save due a copy for its comments alone keeps the waiting copy's words
and takes only the comments; a restore weighs the comments it writes
over, and the styles of the same words. `days_cases.json`, rebuilt by
the desktop's own core at cdbb89e, holds 31 cases now, the ten new ones
the Trash, 0.12.0's turn-on, the same backup twice, the comments over a
body's copy, a restore after comments and after bold; the 21 before it
answered as they did. Each fix with a test and a mutant watched failing
(AS237 to AS257, all 21 killed), and the walk's new `unbroken_rows_and_caps`,
written with the round and left for the emulator. The tools came out of
the round holding to the computer they run on: no Gradle or Kotlin
daemon left after a build, every adb call timed, the desktop's code run
under the desktop's own memory ceiling.

**The lesson:** a rule put right where it was found is a rule still
asked the old way next door. The sweep for a fix is not for its line but
for its question -- every box with a cap, every road a note is read by,
every copy a save makes or a restore spends -- and each of them gets the
same answer, or a written reason why not.

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
walk's new `a_rows_own_words` part.

Against the build before the round, `a_rows_own_words` failed fourteen of its
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
to AS204), and the walk's new `an_ordinary_note_at_its_size` part.

Against the build before the round the emulator showed both highs: the
120 long rows held the screen 7.1 seconds, and the two-million-letter note
stopped the app at its twentieth burst of typing. After it the same rows
open in 1.7 s -- the same text in a body takes 1.6 -- 1,000 short rows in
2.0 instead of 6.5, a letter in a row draws in 0.2 instead of 1.1, the
heap stays between 67 and 98 MB through forty bursts, and `an_ordinary_note_at_its_size`
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
AS171), and the walk's new `one_note_drawn_and_sent` part.

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
watched failing (AS114 to AS146), and the walk's new `text_the_layout_cannot_break`
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

Findings, not failures: what the audits left on the phone, round by
round, then what waits on the desktop repository. An item settled since
says **Closed**, with the change that closed it, where it stands.

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

### What the audit's seventh round left

- **Words waiting in a full list's box are said, not kept.** At 1,000
  rows, "A new item…" says the list is full as words begin there; if
  Android then ends the app behind Home, the words are gone, as before,
  though no longer unsaid. The desktop keeps such words waiting for their
  note while it stays open and asks at its close; a phone app has no
  close to ask at, and keeping them aside as the template editor's draft
  is would be a file of its own -- the owner's call.
- **The template editor's draft on the app's own way out is walked by
  one road of two.** The reminder's notification is sent by its own
  intent with the editor open; the notification tapped from the shade,
  and an import's Open in split screen, are not walked. Both leave the
  editor the same way, which is what keeps the draft.
- **Bold, Undo, Insert table** is proved in the editor's model, not on a
  phone: selecting a word and the bar's ↶ are not scripted, and whether a
  person's keyboard keeps the field's focus through B and ↶ is the phone's.
- **Settings' name box holds 80 letters, the desktop's 30.** It keeps its
  own words at its cap now, as every box does; the number was the
  phone's before this round and is left.
- **Two Trash restores.** The Trash page restores through
  `Organizing.restore`; `Notebook.restoreFromTrash`, an older route, is
  reached by tests alone. Both take a 0.12.0 series' days back now; the
  older one is a second copy of a rule, [the kind that
  rots](#a-dead-route-carrying-a-second-copy-of-the-rules), left for a
  round that may remove it. **Closed 2026-11-02** by ecdbe34:
  `Notebook.restoreFromTrash` is gone, and the restore tests and the days
  fuzz run on `Organizing.restore`.
- **A word a keyboard commits past a cap is cut as a paste is**, the
  sixth round's, now in the title and the template's boxes too: a
  suggestion or a swiped word that does not fit is cut to the room left,
  and a paste's words said.

### State kept by an id a new note can take

Two lows, found on 2026-11-12 while asking the desktop's fixes of
11 November here ([the story](#a-desktop-fix-the-port-was-never-asked-about)).
A new note takes one more than the highest id, so a note deleted from
the top frees an id the next one takes; the desktop's Share panel and
History learned to keep their state by the note, not the id.

- **The list's selection is kept by id.** On a wide screen, a selected
  note moved to the Trash from the editor beside the list leaves its id
  selected; a note arriving meanwhile, from a share code sent in, takes
  that id and shows ticked, and the selection's Trash takes it with the
  rest. The + button is hidden while a selection is on, so a new note
  from the list cannot reach it; the Trash gives back what it took.
  **Closed 2026-11-13** by 4cad569: a pick keeps who its note is.
- **The History's open series are kept by id,** for as long as the
  History is on screen: a series made there, beside a deleted one, could
  open unfolded. Nothing is changed or lost. **Closed 2026-11-13** by
  6ebc82f: kept by the series' uid.

### What the audit's tenth round left

- **A day reset under another start comes back when the start is put
  back -- on both apps, decided as the round's rule has it.** Off, on again
  from another start, a day reset there, then the first start put back:
  the note repeats as its copy's series again, the reset day holds nothing
  in the note, and the copy's own words for it come back. A copy's day is
  dropped only where the note deleted that day or wrote words of its own
  for it, and a reset is neither; the desktop's own auditor found its twin
  and judged it contrived, and the two programs answer it the same way.
  Nothing is lost, and the day can be reset again.
- **The capped boxes say a cut only when words were left out**; the
  desktop's title says it so since its 737efe7, but its other boxes
  (`_limit_entry`) still say "Pasted text shortened" when a paste's own
  trailing space was all the cap took. The desktop's to change.
  **Closed 2026-10-30** by the desktop's 491e942: those boxes say nothing
  when only the paste's own spaces were left out.
- **An id past an Int is given a new number on the phone, and kept on the
  desktop.** Python's numbers hold it; the phone's cannot. A note's id is
  this notebook's own -- a note's identity between the two programs is its
  uid -- and no road but a file written by hand brings one.
- **A caption, a line above a table and a deleted table** are carried
  through their edits as made, as the calculator's place needs; no place
  the calculator or the cursor can hold reads differently there from the
  texts' reading, so no test fails without them and no mutant was offered.
  A column's width and a cell's words are still read off the texts: their
  edits lie inside the table's lines, which begin and end with a bar.
- **Two of the copy's edits for one date**, a shape neither app writes,
  are ordered as the desktop orders them -- words first, then the day's
  own date, a move, a day drawn on none; no fixture case holds one.
- **Find's fold is Tcl's, on this phone's Unicode tables**, which the
  desktop's Python may not share for letters newer than either.
- **The renamed row's place at the editor's foot and the deepest host's
  drawing have no unit test that reaches the drawing**; the full walk
  reads the first (`message_over_a_renamed_row`, photo 127). Its mutant -- the row
  left out of the foot -- passes on the emulator's screen, where the
  message already stands above the row by its own margin.
- **The comments being typed in are still not counted at the editor's
  foot**: they hold no key a message can take.

### What the audit's ninth round left

- **Decided, left as they were:** a check that passes because its state
  held before its tap, swept where a process ends but not through the
  whole walk; and a row read in parts cut inside a Thai or Lao syllable
  with AM, or inside a Devanagari conjunct -- the short form of Unicode's
  rules round eight named.
- **What the editor's foot counts.** The window's messages stand above
  the format bar, the table's bar, the calculator and "A new item…" while
  it is typed in. A checklist row being renamed and the comments are not
  counted: a message there can stand over the row, as over the lines of
  the note's text being written; neither holds a key a message can take.
  *The tenth round* found the row does -- its ⠿, ☐ and ✕ -- and counts it.
- **The window's host hidden under a sheet** is drawn by nothing a script
  can read -- uiautomator dumps the sheet's window alone -- and TalkBack's
  reading of a message once is not heard. *The tenth round* found two
  sheets, one over the other, each drawing the message; the deeper alone
  draws it now.
- **A CR in a template's comments.** The template's text takes a CR that
  comes in as one break; its comments, a field of another kind, do not yet.
- **An id past an Int.** Written as text, it is held at the edge, as the
  rule's numbers are; the desktop keeps a whole number of any size.
  *The tenth round* found the edge wrapped the next new note's id to a
  negative one, shared; such an id is given a new number now.
- **The walk's look that gives up** is a failure of its own now; an
  Android "not responding" window over a freeze is read as a screen like
  any other.

### What the audit's eighth round left

- **A title's paste of one line goes in as copied.** The desktop weighs
  every title paste as `" ".join(pasted.split())` -- spaces at its ends
  dropped, runs of them made one. The phone joins only what came in
  holding a line break: it cannot tell a paste of one line from a
  keyboard's word committed with its space, which joined would lose the
  space. And a break a title already held becomes a space at its first
  real edit, as since 0.11, where the desktop keeps it: "Groceries" and a
  break, then " list" typed after it, is "Groceries  list". *Since the
  ninth round* the desktop's paste keeps its spaces (its 51f0c44), and the
  phone takes every paste and every letter by that rule: the first half of
  this line no longer holds; the break a title held still becomes a space.
- **A search past 10,000 letters is not handed to Android.** After Android
  ends the app behind Home, or a tab is left and drawn again, such a search
  comes back empty -- the price of not stopping the app as Home is pressed.
  The search still folds its words once a note, as before; measured at no
  size a person types.
- **The turn-off's comments-alone branch has no road of its own here.**
  The desktop takes the comments and the repeat in one save; the phone's
  Repeat sheet saves the note's words first and the repeat after, and the
  copy comes out the same, through the save's rule and the turn-off's copy.
  "Restore previous" and an import's Update spend no copy's days
  themselves, as the desktop's do not; the next read does. *The ninth
  round* found one copy that did not come out the same: an earlier
  turn-off's copy of the same series, its long comment kept by the
  comments' save, was replaced by the next turn-off on both apps -- now it
  gives its words to the new copy.
- **A letter's edges by a short form of Unicode's rules.** Marks, ZWJ,
  variation selectors, skin tones, tags, Hangul jamo and flags; no
  prepended marks. A letter longer than the slack -- sixty units of
  accents on one letter -- is cut where the part ends, never in an emoji.
- **Walked by none:** the Repeat sheet's refusal over itself (it needs the
  phone unable to write with the sheet open), the calculator after ↶ and ↷
  (neither is scripted), a CR LF paste (the emulator's clipboard cannot be
  given one by a script), and TalkBack's reading of the words under a box.
  `refusals_and_caps_said` was written with the round and left for the emulator.
  **Closed 2026-10-30**: the full walk after the round ran it, and its ten
  failures were the round's own, fixed.
- **PASS after a failed premise.** The two named in the walk read their
  premise now; the class -- a check whose state already held before its tap
  -- was swept only where a process ends, not through all 6,400 lines.
- **The desktop's CLAUDE.md** says the Android build and its emulator take
  no turn; the walk takes it now, as it starts. That line is the desktop
  repository's to change. **Closed 2026-10-30** by the desktop's 3e4200b:
  its CLAUDE.md says the Android walk takes the turn as it starts.

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
today" reminder, not built. **The reminder closed** in 0.11.0: the day's
reminder bar, the desktop's, and its notification.

### Seven DESKTOP findings from the audit of 2026-10-19

**Closed**, all seven, in the desktop's 0.95.391 (2026-10-20, its issue
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

**Closed** in the desktop's 0.95.391 (2026-10-20): it keeps the lost
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

**Closed** in the desktop's 0.95.391 (2026-10-20): the startup purge and
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

**Closed**, all seven: the last five on 2026-11-11, before the final
audit's pull request, each with a check and its mutant (the desktop's
PF03–PF06, and its wave 2 for the calculator); kept here as found.

Porting a feature means reading its Python closely enough to answer as
it answers, and running it over hundreds of generated cases. That turned
up seven things in the desktop, each left for its own repository; the
phone matches the desktop wherever the difference is not a fault.

* **"Title Z–A" puts the untitled notes first.** Its sort reverses the
  whole key of "Title A–Z", whose comment says untitled notes sink to the
  bottom — so reversed, they rise to the top. The phone keeps them at the
  bottom in both orders, as that comment means. **Closed 2026-11-11** by
  the desktop's 3ea673c.
* **The calculator raises `RecursionError` at about 190 nested
  brackets** instead of returning `None` like every other failure. The
  phone shows Error there. **Closed 2026-10-27** by the desktop's
  fface5e: its calculator catches it and answers `None`.
* **The calculator's display refuses `-`, `*` and `/` typed on a
  keyboard**, taking only its own `−`, `×` and `÷`. **Closed** in the
  final audit's second wave (the desktop's 2a31b35).
* **Find steps through a table's matches column by column** — a side
  effect of how it sorts them. The phone goes row by row, as a table is
  read. **Closed 2026-11-11** by the desktop's 15d6dae.
* **The export raises `IndexError` on a `^` merge under a shorter row**
  (`merge_map` reads a cell without asking whether it exists): 3 of the
  400 fixture notes. The phone writes those notes. **Closed 2026-10-28**
  by the desktop's f8818dd: `merge_map` asks first.
* **The table size grid lights a single column for a two-column table**,
  because it starts at its second column. The phone's grid draws every
  column. **Closed 2026-11-11** by the desktop's 5f82daa.
* **A table inserted right above another leaves two blank lines** between
  them. **Closed 2026-11-11** by the desktop's 495fdf2 -- and on the phone,
  which had copied the rule, the same day (b2f1420).

### Two DESKTOP findings from porting Phase 3

* **A day's return hides another day moved onto its date.** When a day
  of a series has been moved away and another day of the same series
  moved onto its date, Undo of the first move, or "Reset this day" on it,
  brings the first day home on top of the second, and the calendar shows
  one of them where there were two. Confirmed with the desktop's own
  recurrence code. The phone refuses that return and says why
  ([a deliberate difference](STATUS.md#where-it-differs-from-the-desktop-on-purpose)).
  **Closed** on the desktop in the final audit's eleventh round
  (`c55a9dd`), where "Only the date" was found to write over the other
  day's words too.
* **A pasted page's second table puts its styles beside their words.**
  `tables.align` re-pads every table in a paste and reports where it put
  spaces in or took them out; `shift_spans` judges those places against
  the text as it came. For the first table they are counted that way;
  for every later one, in the text the earlier tables' padding has
  already changed — so a bold word in a second table lands on the spaces
  next to it, and every style after it slides too. 20 of the 567
  fragments in the paste fixture show it. The phone counts them in the
  text as it came; the fixture's generator made the phone's answers by
  that same two-line change to the desktop's own function, run in its
  own process, and kept the desktop's answer beside each of the 20.
  **Closed 2026-11-11** by the desktop's cb12941: the two now answer alike.

The desktop's `remove_attachment` deleting a file another note still
uses was the second half of
[the Trash purge finding above](#the-desktops-automatic-trash-purge-can-delete-a-file-a-living-note-uses),
**Closed** with it in 0.95.391; the phone's "take off" spares such a file, as its purge does.

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

## Closed since

### A deliberate difference worth porting back

Opened when the list's search was built: search here folded diacritics,
`si` finding `și` and `masina` finding `mașină`, the cedilla spellings
older Windows keyboards produce (`ş`, `ţ`) matching the comma-below ones,
while the desktop compared the letters as typed. A one-way widening — the
folded search finds everything the strict one finds and more — and the
reason was the phone: diacritics get dropped constantly when typing on
one, and a notebook written half with them is unsearchable under a strict
rule. It was to stop being a difference by the desktop adopting it.

**Closed** in the final audit's second wave (2026-11-03), by the owner's
choice: every search on both apps — the list, Find in note, the
Calendar's History — folds case and diacritics by one rule, the
desktop's `fold_for_search` and this app's `searchFold`, a letter always
into one letter so a match's place in the text is its place in the fold;
Title A–Z sorts Ă Î Ș Ț beside A I S T. Written for both, the rule first
cut every character to the first letter of its decomposition, and a
Korean syllable — letters, not a letter and a mark — found every syllable
starting with the same one; only marks are dropped now, on both.

### A mismatch in the DESKTOP's retention docstring

Opened 2026-09-13: `trash_retention_days`' docstring said a hand-edited
`0` or `-3` "reads as the default", while the code clamps them to one
day. Both safe, but the two statements disagreed. **Closed** — the
desktop's docstring now says what the code does ("clamped to 1-365"),
and this app follows the same code.

## Smaller lessons, moved out of the code

*Each of these was once a long comment beside the code it explains. The
code now says why in a line or three and points here, by name, for what
happened.*

### From `NotesBackupAgent.kt`

- **The backup's off mark** — With Settings → Backup's switch off, the
  agent first handed Android nothing at all. Android then kept the copy
  made while the switch was on — notes and all, with a settings file that
  could not say off — and a reinstall or a new phone brought it back,
  switch on. One small file saying off replaces that copy at the next
  backup, and a restore brings back the answer, not the notes.

### From `Importing.kt`

- **The backup's Trash window** — A backup's Trash entries come with the
  days they were deleted. Kept by this notebook's default of 30 days, a
  backup from a notebook that kept its Trash for 60 or 90 emptied, at the
  next start, what the person had kept. Taken whole, the window went wrong
  the other way: a backup from a 7-day notebook, with no Trash in it at
  all, emptied this notebook's own Trash at the next start, files and all.
  So only a longer window comes in, only with Trash to keep by it, and only
  where this notebook never chose one, as the desktop's import takes it
  (e69721e).
