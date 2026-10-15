# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Seven of the sixteen are about the
checking.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
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
