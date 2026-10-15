# Issues log

What actually went wrong, how it was found, and what the failure turned
out to be a *kind* of. Each entry is here because the mechanism can
recur — a bug that could only ever happen once is a changelog line, not a
story.

Newest first. The *Kind* column says where the failure lived: in **the
checking** (a tool or a test whose verdict could not be trusted), in
**the code**, or in **the design**. Four of the eight are about the
checking.

| Date | Kind | What happened | The lesson |
|---|---|---|---|
| [2026-09-14](#a-photograph-of-the-wrong-screen-filed-as-proof) | checking | A screenshot tool saved the wrong screen and labelled it "search" | A tool that captures whatever is there produces documentation that *looks* like proof |
| [2026-09-14](#the-preview-that-flattered-the-icon) | checking | The launcher icon came back from the phone with its corners cut off | A preview that skips the system's step approves what the system will reject |
| [2026-09-13](#a-dead-route-carrying-a-second-copy-of-the-rules) | code | `importShareCode` survived as an unreachable route holding its own copy of the new-note defaults | Dead code that duplicates a *rule* rots silently while its tests keep passing |
| [2026-09-13](#an-update-that-reached-the-screen-before-it-reached-the-file) | code | Copying a JSON array copies references, so an edit changed the live note before the write could fail | Copy the container, and you have copied nothing |
| [2026-09-13](#a-mutant-runner-that-left-the-code-broken) | checking | A runner crashed mid-round and exited with a mutated `Notebook.kt` still on disk | Restore in a `finally`; a tool that can break the code is worse than what it hunts |
| [2026-09-12](#a-silent-mutant-is-not-a-passing-one) | checking | Three mutants "survived" without the tests ever running | A verdict counts only if the suite reached its checks |
| [2026-09-12](#a-new-note-saved-without-its-formatting) | code | The toolbar worked, the screen drew bold, the file held none of it | Read the file, not the screen |
| [2026-09-12](#a-list-of-titles-would-have-been-a-list-of-blanks) | design | 48 of 77 real notes have no title at all | Design against the real notebook, not against the example one |

Two findings still open are at the [end](#still-open).

---

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

### A mismatch in the DESKTOP app, to be fixed there

`trash_retention_days`' docstring says a hand-edited `0` or `-3` "reads as
the default". The code is `max(1, min(days, 365))`, which clamps them to
**one day**; only a value that is not a whole number at all reaches the
default. Both behaviours are safe — neither makes the purge take
everything — but the two statements disagree, and this project follows
the code. Worth a one-line docstring fix in the desktop repository.

### A deliberate difference worth porting back

Search here folds diacritics; the desktop compares the letters as typed.
What the folding does, and why it is worth having, is in
[STATUS.md](STATUS.md#the-one-deliberate-difference-from-the-desktop) —
the one place this app's rules knowingly differ from the Python's. This
one should stop being a difference by the desktop adopting it.
