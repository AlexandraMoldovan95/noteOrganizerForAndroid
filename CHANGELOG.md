# Changelog

What changed in the Android app, newest first. The desktop app keeps its
own changelog in its own repository; this one covers only what runs on
Android.

## How the numbers work

`versionName` is `0.MINOR.PATCH`, and the first number stays `0` until
the app is handed to somebody as a real release rather than installed by
hand.

- **MINOR** moves when the app can do something it could not do before:
  read a note, write one, edit a table, recognise a note it has already
  met. The numbered list in [docs/STATUS.md](docs/STATUS.md) is the same
  list told as status instead of as history.
- **PATCH** moves for fixes and for changes that add no new capability.
- **`versionCode`** is the number of released version sections in this
  file. Android requires it to increase and never repeat; tying it to the
  sections means it cannot quietly drift away from the name beside it.

A version is cut when an APK is built to actually be used — the same
moment the desktop app cuts one for an `.exe` rebuild. Documentation-only
changes do not move the number, because they do not call for a build.

**Everything below `0.5.0` is reconstructed.** The work happened on the
dates given and in that order — the git history, the test suites and
`docs/STATUS.md` all agree — but the phone ran debug builds throughout,
so no numbered release existed at the time. It is written down so the
trail is not lost, not to claim releases that were never cut.

**`0.6.0` to `0.8.0` were not installed for use on their own.** Each is
a phase, finished and tested on the emulator in the order given; the
first build used on a phone is `0.9.0`, which carries all three. They
are numbered as the steps they were, each adding what the app could not
do before, not as releases anyone ran.

---

## [Unreleased]

### Added

- **Settings → Backup turns off the app's part in Android's own
  backup** to the person's Google account, which stays on by default, as
  it always was. Off, Android's backup — and its transfer to a new phone
  — keeps none of the notes from then on, only that it is off: the copy
  made while it was on is replaced, so a reinstall or a new phone no
  longer brings it back with the switch on. A settings file that cannot
  be read turns it off until the person turns it on again, and the start
  says so; it read as on, and copied the notes. The page says what Android
  does, about once a day while the phone charges on Wi-Fi, and that it
  copies nothing more once the app's data passes 25 MB, where it used to
  promise a copy with no condition; the welcome note says where the
  switch is. Asked for by the owner after the final audit, 2026-10-27.

### Fixed

- **A note's words typed while the phone could not write stay "Not
  saved" until they are written.** When a save failed — a full disk —
  the new words stayed in the note the screen reads, so the next save
  found nothing to write and said "Auto-saved", and a restart lost them.
  Found by the final audit's break-it round.
- **A note that cannot be saved stays open.** Back, another note, + and
  the rail closed it anyway — a new note's words with it, kept nowhere
  else — and a pin, a category or the Trash from the list beside it read
  it back over the unsaved words. Now the note stays, the bar at the foot
  says why with **Leave anyway**, and a change from outside waits. A
  failed save is said aloud as well, where it was only drawn in red, out
  of a screen reader's hearing. Found by the final audit's break-it round.
- **A checklist row's new words, and words left in "A new item…", are
  kept when the app goes to the background.** They waited in their field
  until it was left, and Home leaves no field: swiped away from Recents,
  or ended by Android, the app came back with the old words. The
  keyboard's ✓ writes a renamed row now too, where it only put the
  keyboard down. Found by the final audit's break-it round.
- **A backup restored onto a new phone keeps its Trash as long as it
  was kept before.** The Trash came with the days its notes were
  deleted, but not the window: the phone, never told otherwise, read 30
  days, and the next start emptied what had been kept for 60 or 90. A
  notebook that never chose a window now takes the backup's when it is
  longer and comes with Trash to keep by it; one chosen here is kept,
  and a shorter one is never taken, so the phone's own Trash keeps the
  30 days it was promised. The desktop takes the same rule. Found by the
  final audit's break-it round, the shorter window by the round after.
- **A share code or a backup nested deeper than any note is called
  damaged, where it stopped the app.** A code of a few lines could hold
  a million brackets one inside another, and the reader went one step
  deeper for each until it ran out of room. Found by the final audit's
  break-it round.
- **A backup whose notes cannot be read says so in a sentence**, where it
  showed the reader's own message — which on Android holds the whole
  file it was reading: every note in the backup, PINs and all, in the
  bar at the foot of the screen, and from a hostile file, tens of
  megabytes of text. Found by the final audit's break-it round.
- **A backup too large for the phone to read is refused with a sentence**,
  where it could stop the app. A 48 KB backup could unpack to a 50 MB
  note — under the desktop's bomb guard, sized for a computer — and the
  phone ran out of memory reading it. Each JSON file a backup brings is
  now held to a sixteenth of the memory Android gives the app, running
  out of memory anyway reads as "too large", and a note's letters are
  counted without an object for each. A notebook grown past that line is
  still saved as a backup, and the bar says this phone cannot read it
  back and the desktop can, where it said "Backup saved." and the import
  then refused it. Found by the final audit's break-it round, the
  backup's own side by the round after.
- **A backup holding a long rescue copy no longer holds the app still at
  every start.** Whether "↩ Restore previous" would keep a note's words
  was worked out for every note at every load, by a search that, on a
  copy of millions of letters, took seconds; it is worked out now when
  the question is asked, for that note, by a search as quick as the
  desktop's. Found by the final audit's break-it round.
- **"↩ Restore previous" holds a backup's rescue copy to a note's
  limits**, as the desktop does: the copy came in exactly as written, and
  a restore could put fifty thousand checklist rows into the note, where
  a checklist holds a thousand. It is shaped as it comes in, and again
  when it is restored. Found by the final audit's break-it round.
- **A note with a huge table opens.** A share code of 2.9 KB could carry
  a table of 300 by 300, and the editor laid out its 90,000 cells at
  once. A table of more than 1,000 cells now says it is too large to draw
  and shows its first lines, read-only; the note keeps it whole. Its cells
  are counted as the grid draws them, so a ragged table — one row of 500
  over rows of two — no longer slips under the line; the template editor
  shows such a table the same way, where it drew the grid; and Find
  leaves it out, where its counter stepped through cells nobody could
  see. Found by the final audit's break-it round, the three holes by the
  round after.
- **A share code followed by a full stop, a quote or a bracket imports**,
  as on the desktop, which drops what is not part of a code where the
  phone called the code damaged — as it is when it ends a sentence in a
  mail. Found by the final audit's break-it round.
- **A share code that started the app is offered once.** Sent to Note
  Organizer from a chat while the app was closed, the code was offered
  again each time Android brought the app back after ending it in the
  background — with "Update this note" ready to put the older words back
  over the newer. Found by the final audit's break-it round.
- **Bold text, or a new language, leaves the open note open**, as
  turning the phone does: both still rebuilt the screen and closed the
  note, and an import's question with it, whose unpacked backup then
  stayed in the app's cache. What an import or an attaching leaves there
  is cleared at the next start. Found by the final audit's break-it
  round.
- **A backup saved over an older one keeps the older one when the new
  one fails.** The file chosen was opened, and emptied, before the new
  backup was built; a cache too full to build it then left the old
  backup empty and deleted it. It is opened only once the new one is
  built and checked, and a place that refuses to open leaves it as it
  was, where the refusal still deleted it. Found by the final audit's
  break-it round, the refusal by the round after.
- **The buttons drawn as a symbol say what they do to a screen reader**:
  + is "New note", ⋮ "More options", ← "Back", ⓘ "Note details", and the
  selection bar's ✕, 📌, 🗂 and 🗑 their actions, as are the search's and
  the start line's ✕. A checklist row's box is a checkbox named by the
  row, and its ✕ says "Remove … from the checklist". TalkBack read the
  symbols' names, or nothing. Found by the final audit's break-it round.
- **What is on is said, and shown twice.** B, I, U, S and the list keys
  are said as ticked or not, the alignment keys as chosen or not, a
  day's markers and the repeat sheet's choices too; a key that is on
  wears a bar in the accent under its fill, which alone differed from an
  off key's by too little to see. The sort and Show in force carry a ✓,
  colour swatches say their place and value, a group of notes says it is
  folded, the size grid's squares say the table they make, the Sort and
  Show buttons no longer read their arrow's name, and the find counter
  is read out as it changes. Found by the final audit's break-it round.
- **The day's reminder keeps its hour across a time zone.** Set for
  09:00 and carried from Bucharest to New York with the app closed, it
  rang at 03:00 until the app was opened; a new zone or clock now sets
  it again. Found by the final audit, checked on the emulator.
- **Today moves at midnight with the app open.** Left open across
  midnight, the app kept the day it was opened on: "Calendar: soonest"
  still put yesterday's note first, the calendar's ring stayed on
  yesterday, and the new day's reminder bar never came until the app was
  left and opened again. It now looks once a minute while in front, as
  the desktop does. Found by the final audit, checked on the emulator.
- **A template with very long comments opens, and survives Home.** With
  600,000 letters of comments, which a backup can bring, the app stopped
  as the template opened, and once that was fixed, as Home was pressed.
  The comments field now stops at ten lines and scrolls inside, and its
  words are no longer handed to the system while the app is away. Found
  by the final audit, checked on the emulator.
- **Export as file and Attach files finish when Android restarts the
  app.** If Android ended the app behind its file picker, as it does when
  it needs the memory, the exported file stayed empty and the picked
  files were not attached, without a word; and Back while a large file
  was copying lost it. Both now finish, and say so. Found by the final
  audit, checked on the emulator.
- **The app no longer takes in a notebook from its shared folder.** At a
  first start, the released app copied in any notebook found in a folder
  that other apps could write to on Android 8 to 10, and a file there too
  large to read stopped it at every start. Only development builds look
  there now, and one too large is left alone. Found by the final audit,
  checked on the emulator.
- **The app's own files are read as carefully as a backup.** A notes,
  Trash, settings or templates file nested too deep, or too large for the
  phone, could have stopped the app at every start; it is now kept aside
  and the app opens. And a file, backup or share code whose letters are
  not in UTF-8 is refused, as the desktop refuses it, instead of coming
  in with its letters turned into marks that the next save kept.
- **A large backup's questions no longer hold the screen still.** With a
  thousand notes on each side, working out which arriving notes look
  like ones already here held it 1.3 seconds on the emulator, two or
  three times over; it is worked out once now, away from the screen. Found
  by the final audit, measured on the emulator.
- **A notebook file the app could not read can be reached.** It was kept
  aside in the app's own storage, which no person can open, and the
  notice said "beside the notes"; a backup saved at Settings → Backup
  now carries it, in a folder of its own that an import leaves alone.
  Found by the final audit's break-it round.
- **At the phone's largest letters a calendar day keeps its marks.** Its
  cell was held at one height, and a day with a marker lost half of it
  and its note's dot below the edge; a week's cells now grow together to
  hold them. Found by the final audit, seen on the emulator.
- **A switch that is off, and a field's border, can be seen.** Both were
  drawn in the theme's hairline, 1.2 to 1 from what lies around them; they
  are drawn in its secondary text colour now, three to one at least in
  every theme. Found by the final audit's break-it round.
- **A checklist row taken off by its ✕ can be brought back.** It was
  gone at once, unsaid, and the editor's Undo holds no checklist; now the
  bar at the foot says what was taken, with Undo, as the desktop's toast
  offers. Found by the final audit's break-it round.
- **Every Undo at the foot lasts from the moment it shows, as long as
  the phone asks.** Its eight seconds were counted from when it was
  asked for: queued behind another message it flashed for a second, or
  outlived its count and stayed for good, holding back every message
  after it; and Android's "Time to take action" was never read. It now
  lasts Android's long length, ten seconds from when it shows, longer
  when the phone is set to give more time. A checklist row's Undo writes
  the words being typed into another row first, where it dropped them.
  Found by the final audit's second round.
- **Undo on the bar, and a dialog's Cancel, can be read in every
  theme.** Android drew them in the theme's raw accent: Undo stood 2.1
  to 2.7 to 1 from the bar in six of the eight themes, the default Dark
  among them, and Cancel 3.1 to 1 in Light Mint and Light Warm. Both,
  and a focused field's label and the date picker's day, are drawn in a
  shade of the accent held to 4.5 to 1 now. Found by the final audit's
  second round.
- **An attached file is opened as the kind of file it is, and its name
  hides nothing.** The name shown — which a backup chooses freely —
  decided what kind of file another app was handed: "Holiday photo.jpg"
  over a web page went out as a picture, and a bidi mark could hide
  ".apk" behind ".jpg". The stored file decides now, and the shown name
  ends in its type and loses the characters that reorder or hide text,
  as on the desktop. Found by the final audit's second round, the twin
  of the desktop's.
- **A share code sent as styled text arrives.** An app sharing
  formatted text hands it over in a form the app did not read, and the
  code was dropped without a word. Found by the final audit's second
  round.
- **The last symbol buttons say what they do, and a handle with
  nowhere to go says so.** The template editor's checklist ↑, ✕ and box,
  the repeat's − and +, and a table cell's "no highlight" ✕ were read by
  their symbols' names; they are named now. The move handle of the only
  note in its block, or of a one-row checklist, opened an empty menu; it
  now says there is nothing to move it past. Found by the final audit's
  second round.
- **Notes in Custom order and checklist rows move without a drag.** A
  tap on the ⠿ handle offers Move up and Move down, and a screen reader
  or Switch Access finds the same two among the card's or the handle's
  actions; dragging was the only way, and those could not arrange at
  all. Found by the final audit's break-it round.
- **A repeat ended by a share code keeps every day's own text.** An
  Update whose copy no longer repeated left the series' days in the
  note, out of sight, and kept no copy of them; a Save in the Repeat
  sheet then cleared them for good, and "↩ Restore previous" brought the
  repeat back without them. The days now stay in the note, a copy goes
  beside the repeat in the rescue copy, a note that does not repeat
  keeps its days, and a new rescue copy carries the days the one it
  replaces held. The desktop takes the same rule. Found by the final
  audit's second round.
- **A series with thousands of moved days opens at once.** Its banner,
  its days off the schedule and the History asked about each moved day
  in turn, and each question read every moved day again: 5,000 of them,
  which a backup may bring, made 25 million readings, and the note froze
  as it opened and at every letter typed. The moved days are read once
  for all the questions now, with the same answers. Found by the final
  audit's second round, the twin of the desktop's.
- **A pasted table can no longer grow past the paste ceiling once its
  columns are lined up.** Every cell was padded to its column's widest
  after the 100,000-character ceiling was checked: a page with one long
  cell over 2,000 rows became 120 million characters and the app ran out
  of memory, and a spreadsheet with one long comment made a note of three
  million. A table whose padding would pass 100,000 spaces is left
  ragged — each cell keeps the spaces its alignment needs, and the grid
  looks the same — the same rule holds when a table is edited, and what
  lands is held to the ceiling, said as before. Found by the final
  audit's second round, the twin of the desktop's.
- **Editing a template's text keeps its bold, colours and list lines on
  the words they were on.** Settings → Templates kept them at their
  places while the words moved: a line typed above put the bold on
  other words, and every note made from the template began that way.
  They move with the words now, as in a note. Found by the final audit's
  second round, the twin of the desktop's.
- **The list sorted by "Calendar: soonest" no longer stalls after every
  save.** Each series' next day was worked out inside the sort's
  comparisons, twice per comparison and each time over the whole
  notebook: 15 million notes read for 2,000 of them. It is worked out
  once per note now. Found by the final audit's second round.
- **A backup's settings and templates come in within the desktop's
  limits.** 700,000 "painted days" under keys that were no dates,
  markers of 200,000 letters and 140,000 templates came in whole, and
  left the calendar, every setting changed and the template picker slow
  for good. The calendar now keeps real days only, 20,000 painted and as
  many marked, each marker an emoji long, and templates stop at 1,000 —
  a new day or template past that is refused with a word, never one
  already there dropped. Found by the final audit's second round, the
  twin of the desktop's.
- **A backup or share code with "NaN" in a list no longer stops the
  app.** Android's reader takes a number that is no number inside a
  list, and every copy of the list then failed: a backup's template with
  one in its checklist stopped the app at the import's tap. Such a
  number now comes in as nothing, as the desktop's normalize drops it,
  and anything else an import cannot read is refused with a sentence.
  Found by the final audit's second round.
- **A backup holding a file with a long name in Cyrillic, Greek or
  Chinese comes in.** Windows counts a name's length in letters and
  Android in bytes, so a name the desktop kept could be one the phone
  cannot store, and the whole backup was refused with an error code and
  an inner path. Such a file now comes in under a shorter name that
  keeps its start and its type, and the note follows it; a file attached
  on the phone with a long name fits the same way. Found by the final
  audit's second round.
- **A large import no longer holds the screen still.** A backup's
  attached files were copied a second time, on the screen's own thread,
  from the unpacked backup into the notebook — seconds for a notebook of
  photos, with Android offering to close the app; they are moved now,
  which costs nothing. And a share code is read, planned and weighed for
  look-alikes away from the screen, as a backup is, with "Reading the
  code…" while it is: a code of a few kilobytes can unpack to 20 MB.
  Found by the final audit's second round.
- **Text with a very long run of letters and no space opens at once.**
  A share code of a few hundred characters could bring a body line, a
  title, comments or a checklist row of 150,000 letters with no space;
  laid out in a field, broken letter by letter at the edge, it held the
  note 12.6 seconds at every opening, and a title such held the list 3.8
  seconds at every start. A run past 10,000 letters is now shown as what
  it is and its start, read-only — the note keeps every byte, and the
  lines around it stay editable — and a list, a day or a picker shows a
  glimpse of a title or a name. The code dialog shows a code past 5,000
  characters as its start and its length, with Copy for the whole code:
  120,000 characters shared to the app held the screen until Android
  offered to close it. Found by the final audit's second round.
- **Files picked for a note go to that note only.** The copy goes on
  after the note is left; if, meanwhile, the note went to the Trash and
  a new one took its number, the files went to the new note, unsaid. The
  note is now known by its own identity too, and the files are not
  attached elsewhere — the bar says the note is no longer here. Found by
  the final audit's second round.
- **Android's backup turned off stays off on a phone restored and not
  yet opened.** Restored from a backup made while it was off, the phone
  holds only the mark that says so, until the app's first start reads
  it; Android's backup, run before that start, read the missing
  settings as on, deleted the mark and kept nothing, and the app came up
  on. The mark is now the answer until the start writes it into the
  settings, for Android's backup and for Settings → Backup alike. Found
  by the final audit's third round.
- **A note sent to the Trash while a backup is read can no longer turn
  its Update onto another note.** The import's plan is made while the
  list stays usable, and it remembered each note by its place in the
  list: one note trashed meanwhile moved the rest up, and "Update" wrote
  the backup's copy over the note below the right one. Each is found by
  its own identity now; one that has gone in the meantime comes in as a
  new note, as it would have had it gone first. Found by the final
  audit's third round.
- **Text joined by no-break spaces, or many lines just under the limit,
  opens at once too.** The rule that shows a run of more than 10,000
  letters read-only counted a no-break space as a place to break the
  line, which the layout never does: 150,000 letters joined by them went
  into a field and held the note as before. A run now ends only where a
  line can certainly break, and what a body's lines cost to lay out adds
  up: past what one note may give its fields, the heavy lines left are
  shown read-only as well — a hundred lines of 9,999 letters each passed
  one by one before. Chinese and Japanese ideographs, which break between
  any two, are not counted as one run. The note keeps every byte. Found
  by the final audit's third round.
- **A table holding a very long run of letters opens at once.** The rule
  above looked at the lines around tables and never inside one: a row of
  two cells, one of them 150,000 letters long, was drawn as a grid, its
  letters in a column a few words wide, and froze the note as the line
  alone had before. A table's cells are weighed as lines are now, and
  such a table is shown as its start, read-only, in the note and in the
  template editor. Found by the final audit's third round.
- **A message or a question quotes a long title, row or file name
  short.** Only the lists were given a glimpse: the Undo bar for a
  checklist row taken off, Duplicate's bar, the attached-files bar, the
  question about a day off the schedule, a template's save, replace and
  delete, and an attached file's row and its Remove question took the
  whole of what they named — and froze the app on a row or a title of
  150,000 letters, by the very ✕ a person uses to be rid of the row. They
  quote 80 characters now, with "…" where they cut, a file keeping its
  type at the end; a screen reader's names for a row's controls are
  glimpses too, and no bar or question is given more than 1,000
  characters whatever a reason carries. The note keeps every byte, and
  Undo puts it all back. Found by the final audit's third round.
- **A template saved from a note with a very long checklist row opens
  at once.** The template editor drew each row whole, wrapping: a row of
  150,000 letters, kept by "Save as template", froze it as it opened,
  where the note itself showed the row read-only. A row too long to lay
  out is shown there as in the note, and any other row past 300
  characters as its start with "…"; the template keeps every row whole.
  Found by the final audit's third round.
- **Sharing or exporting a note with a table too large to draw no
  longer holds the app.** The page Share and Export make looked for a
  row's pipes again for each empty cell, and asked every merge in the
  table about every row: a row of 50,000 empty cells, from a code of 186
  characters, came to billions of steps on the screen's own thread, until
  Android offered to close the app. Each row is read once now, and the
  page is made away from the screen. Found by the final audit's third
  round.
- **Share → Text says when a note is too long for a message.** It handed
  Android the whole note with no limit, and a note of more than about
  250,000 characters — a long journal, or a desktop note brought by a
  backup — stopped the app at the share sheet. Past the ceiling a share
  code is held to, Text is shown greyed with the reason and the way
  that carries everything, the web page; a mail's subject is a long
  title's start; and whatever Android still refuses to hand over is
  said, never the app stopped. Found by the final audit's third round.
- **A shared PDF says what it leaves out.** It stopped at 40 pages, and
  cut anything wider than the paper at the margin — a long link's tail,
  a table of many columns — and neither the person sending nor the one
  reading was told. Any line now breaks at the paper's edge; what still
  does not fit, or does not fit in 40 pages, is said in the bar before
  the share sheet opens, with the web page named as carrying all of it,
  and in a small line at the foot of the PDF's page. Found by the final
  audit's third round.
- **Export as file names a note's file as the desktop now does.** A
  title holding a control character other than a line break or a tab
  kept it in the name offered, and one holding a mark that reorders text
  could show the file as another kind; both are taken out, as the
  desktop's export takes them out since its own audit's second round.
- **A note whose repeat was turned off is itself on its day.** A share
  code or "↩ Restore previous" that ends a repeat keeps the series' days
  in the note, for a repeat brought back; the calendar still read them:
  the note's row on its date showed the first day's old title and words,
  marked ✎, and a series whose first day had been deleted fell off the
  calendar, and out of that day's reminder. They are read only while the
  note repeats, as the desktop reads them since its fourth round; and a
  restore's copy now carries the date the repeat hangs on, so the restore
  after it brings the series back. Found by the desktop's fourth round.
- **What is handed to another app is held to what Android delivers.** A
  share code, and now a note's words, were refused past 250,000
  characters, a ceiling reasoned rather than measured: on the emulator,
  180,000 characters and more never reached the share sheet — Android
  failed in its own process, after the app's call had returned, and
  nothing was said. The ceiling is 100,000 now, under the 160,000 that
  went through; past it a code says to share fewer notes or a backup, as
  before, and Text says to send the web page.
- **Files picked for a note that went to the Trash and came back by Undo
  are attached to it.** Undo brings a note back under a new number, and
  the files, still copying, were refused as for a note "no longer here"
  with the note in the list; the note is found by its own identity first
  now. Found by the final audit's third round.
- **A note of many tables, or of many short lines, opens at once.** Each
  table was held to 1,000 cells on its own, so ten such tables, from a
  share code of 350 characters, were drawn at once; 3,000 small tables
  were 6,000 pieces of the screen; and 200,000 empty lines were one field,
  each line drawn by copying the whole field again. A note now draws at
  most 1,000 cells as grids, 20,000 lines in its text and 100 pieces in
  all; past that, the rest is shown as its start, read-only, with how
  many lines it holds. Long text is drawn as several fields of at most
  1,000 lines each, every one typed in as before, so a letter typed draws
  a little again rather than the whole note. Found by the final audit's
  fourth round.
- **"↩ Restore previous" keeps a deleted paragraph through a column
  added, a repeat turned off and an import's update.** A column added
  pads every row again, and read as the whole table lost: the copy of a
  paragraph deleted just before was replaced by one of the table. A
  repeat turned off after a big delete kept only the note as it stood.
  An update replaced the copy in silence. Now a column added loses
  nothing, a turn-off keeps the deleted words beside the series, and the
  update's question says it replaces an earlier version — as the
  desktop does since its fourth round; and, as since its fifth, five
  rows of a table's eight deleted at once keep a copy, where the table's
  padding made them read as under a third of the note. Found by the
  final audit's fourth round.
- **Two notes with one identity no longer turn an update, a file or a
  day's Undo onto the wrong one.** A code or a backup holding two notes
  with one identity updated one note twice, its own words then in no
  copy; files picked for the second of two went to the first. The second
  comes in as a note of its own now, and so does a note restored from the
  Trash while a backup was being read. Found by the final audit's fourth
  round.
- **Share says before the tap what is too long, by what Android
  carries.** A share code too long to hand over is shown greyed with the
  reason, as Text is, where it was refused only after the tap. The
  ceiling is weighed in what Android carries rather than in letters: a
  Chinese, Japanese or Korean letter takes as much room as five bytes, a
  Latin one three, so Text says "about 60,000 of them" for a note in
  Chinese and 100,000 for one in English — and the number it quotes is
  the one it compares. Asked for by the owner after the final audit's
  fourth round.
- **A PDF that would leave part of a note out asks first, and nothing is
  written into it.** The bar that said a PDF held only its first 40
  pages, or cut what was wider than the page, opened under the share
  sheet, and the small line written at the foot of the PDF is gone. A
  question comes before anything is handed over: the web page, which
  carries all of it, the PDF as it is, or Cancel. Asked for by the owner
  after the final audit's fourth round.
- **"↩ Restore previous" on one day of a series that no longer repeats
  opens the whole note.** Restoring a copy without the repeat left the
  editor on that day, and every save was refused: "This note no longer
  repeats". Found by the final audit's fourth round.
- **A checklist of very long rows opens at once.** Each row was weighed
  alone and every row drawn as a field, all at once: 120 rows of 150,000
  letters, from a share code of 38 KB, were 18 million letters laid out
  at every opening. A row longer than 2,000 letters — the most the
  desktop draws of one — is shown read-only, its start; the rows written
  in hold 100,000 letters in all, and past that a row is shown short,
  the list saying so. A row's field and "A new item…" take 2,000 letters,
  as the desktop's box does. A list draws its first 200 rows and its last
  five, a line between drawing 200 more when tapped: 1,000 short rows held
  the screen 6.5 s on the emulator, and open in 2 now. Found by the final
  audit's fifth round.
- **Typing in a very long note no longer runs the app out of memory.**
  Undo kept up to 200 steps, each the whole note: for a note of two
  million letters, some 760 MB. It now keeps what a phone can hold —
  about 50 steps of a journal of 300,000 letters, 200 of a page — and
  always the last change. Found by the final audit's fifth round.
- **The cursor goes where its line went.** A table row typed by hand
  became a grid at its closing pipe and the next row was written above
  it; Enter at the end of what a long note draws glued the next words onto
  the line before; a paste too long to lay out left the cursor on the
  line above it. Now the cursor goes under the grid, or past the
  read-only line; where no field follows, the keyboard goes and the note
  says why. The calculator no longer writes where no field shows it.
  Found by the final audit's fifth round.
- **A note of many small tables is drawn to its end, and what a note
  shows read-only is found and read.** Fifty weekly tables under their
  headings made the fiftieth and everything after it read-only, with
  nowhere to write at the end; a note may now be 500 pieces, and 40,000
  lines of text rather than 20,000, so a journal of 10,000 lines is
  written in to its end. Find
  searches what is shown read-only — a line too long to lay out, the rest
  past what a note draws, a table too large to draw — each line one stop,
  and shows the line it stopped at; such a part can be read on, forty
  lines at a time. It said "0/0" there, while the list's search found
  the note. Found by the final audit's fifth round.
- **The template editor keeps the words left in "A new item…".** Save
  left them out unsaid, and ✕ closed without asking when they were the
  only change. Comments too long to edit are shown under their own label;
  alone, they read as part of the body. Found by the final audit's fifth
  round, and its walk.
- **"↩ Restore previous" keeps a deleted paragraph when a list's markers
  change after.** Numbers turned into bullets, or bullets taken one level
  in, counted each marker as a word lost, and the copy of the paragraph
  was replaced by the list's old markers. As the desktop does since the
  same round, where it was found on the phone first.
- **A series turned back on from another start no longer brings back its
  old deleted days**, which could hide the new series' first day; days
  with words of their own still come back. As the desktop does since the
  same round.
- **Smaller ones from the same round.** A note's Share sheet opens at
  once however long the note (its code was built on the screen's thread
  to grey its card); a letter typed in a long note saves without
  splitting the whole note into words; underline and strike over
  thousands of words no longer slow each letter typed; a repeat turned
  off after a big delete keeps the deleted words when the series had been
  turned off once before; the backup Update question no longer warns of
  replacing an earlier version for a note that only gains a file; a "^"
  under a row too short to have its column shows as text, as the desktop
  shows it; a checklist row the phone cannot write is said once, and the
  letters typed in the next row are kept. Found by the final audit's
  fifth round.

---

## [0.13.0] — 2026-10-27 — Open-source licenses

### Added

- **Settings → About → Open-source licenses**: the app's own MIT
  license, and the libraries it is built with — Android Jetpack, Compose,
  Kotlin — with the Apache License they come under. The list is the
  release build's own, written by `tools/make_licenses.py`, and the
  repository now carries the `LICENSE` the app has always named.

---

## [0.12.0] — 2026-10-27 — Sections fold, and the Trash picks all

### Added

- **A note's text, checklist and comments fold away**, each under its
  label — ▾ open, ▸ folded — to make room for the others; the text gets
  its label once there is a checklist or comments to make room for, and
  shows again while a search in it is open. A fold is kept while the app
  runs. Asked for by the owner on case 38; the desktop waits to see it.
- **The table's bar merges a cell with the one on its right, or below**,
  besides the left and above it already offered — every way the
  desktop's selection merges. Asked for by the owner on case 72.
- **Find in a note looks through its checklist and comments too**, after
  its text and tables, as the note reads; a folded section shows while
  the search is open, and the match in it is painted and brought into
  sight as in the text. The desktop's bar looks through the text alone.
  Asked for by the owner on case 176.
- **The Trash page picks every entry at once**: Select all, and Unselect
  all once everything is picked, so a long Trash is restored, or a few of
  it kept while the rest go for good, without a tap per row. The desktop
  gets the same. Asked for by the owner on 2026-10-26.

### Fixed

- **Restore previous keeps a note's words when they are replaced, not
  only when they shrink**, as on the desktop. A note of a few short
  lines replaced by a new line before it saved, or a paragraph replaced
  by one as long, kept no rescue copy: the copy was made when the text got
  shorter by more than 40 characters. It is now decided by how much of
  the old text is gone — emptied, a big part of it, or most of a short
  note. Found by the owner on the desktop's test case 692.
- **A single cell or a single column copied from a page pastes as its
  words**, as on the desktop. It came in framed in pipes, "| rent |",
  which is no table here, a table having two columns at least.
- **Words pasted from a web page are found by search**, as on the
  desktop. A browser puts a non-breaking space at each edge of a bold
  word or a link, and those came in as they were: the note looked the
  same, but a search for the words either side found nothing. Seen
  testing the owner's case 94 on the emulator, a page copied in Chrome.
- **A checklist row drags from its handle at once.** The handle waited
  for a long press nobody could guess, and sat close enough to the box
  to tick it by mistake; it is a finger wide now, and apart. The owner's
  case 38.
- **A day's markers sit under its number**, where the corner put them
  over it on a phone's narrow cell and the day could not be read. The
  owner's case 60.
- **A sheet's values sit right after their labels** — the repeat sheet's
  Starts, Every and After, the details' Category, Date and Repeat — in
  one column, where the label took the width and threw the value to the
  far edge. The owner's case 62.
- **A paragraph pasted right after a note's last table keeps the table
  whole.** Pasted with its formatting into the place to write under the
  table, it was glued onto the end of the table's last row: the row read
  as words, drawn as a line of bars, and the table lost it. Words pasted
  at a row's end, or its start, now take a line of their own. Seen by
  the owner on case 180, and not again until the route was found: only
  a paste that brings formatting, only under a note's last table.
- **A cell merged down shows its words in the middle of the rows it
  covers**, as the desktop draws it, where they sat in the first row and
  the rest of the cell stayed empty. The owner's case 179.
- **The Trash page says what its numbers set**: "Keep deleted notes for"
  above 7, 14, 30, 60 and 90, the desktop's words for its dropdown. The
  numbers stood alone. Found checking the Trash for the owner,
  2026-10-26.
- **Two Trash entries with the same id, deleted in the same second, are
  two rows.** An id is handed out again once its note is deleted, and a
  row was known by its id and that moment alone: picking one picked both,
  and the page — whose list must never hold one key twice — closed the
  app every time it opened, until the window took them. A row is known by
  its uid too now, as on the desktop, and the list's keys can never repeat.
- **A start that cannot read the notes empties nothing from the Trash.**
  Such a start shows an empty list, so a file only those notes used was
  not spared, and went with an expired entry that shared it. The desktop
  skips that start's purge too.
- **No question promises "within 30 days" when the Trash's window could
  not be read** — moving duplicates, deleting a category with its notes,
  sending a day off the schedule to the Trash. Nothing leaves the Trash by
  itself then, and 30 was only the default the reading fell back to.

### Testing

- `NotebookTest` holds the rule case by case — a short note replaced and
  a paragraph replaced by one as long keep a copy; a word changed, words
  added, a tiny note retyped and a title renamed keep none — and through
  `updateNote`, the old words in `_prev`. Mutants RA1 and RA2 watched
  failing.
- The emulator walk selects a note's whole text, types a new line in its
  place and finds the old words kept for Restore previous.
- The walk drags a checklist row by its handle with a short swipe, no
  long press; folds the checklist and the text and opens them again;
  marks a painted day and reads the marker from the file, photographing
  where it sits; and merges a cell with the one on its right and the one
  below, reading the desktop's `<` and `^`.
- `PastingTest` holds one cell and one column coming in as their words,
  a lone cell's colour and line breaks with them, and a page's
  non-breaking spaces as spaces; the paste fixture, rebuilt from the
  desktop's reader, changed in 94 of its 567 fragments, and the phone
  reads every one as the desktop does. Mutants PA1–PA5 watched failing.
- `PastingTest` pastes words at the end of a note's last table, at a
  row's start, and rows after it; `FindingTest` finds words in the
  checklist and the comments, after the body, case asked or not.
  Mutants PA6, PA7, FI1 and FI2 watched failing. The walk pastes a
  formatted paragraph under a note's last table and reads the file; finds
  the checklist's row and the comments, the checklist folded before the
  search; and photographs a merge down, its words in the middle. 41
  parts, 343 checks.
- `OrganizingTest` restores one of two entries alike in id and moment and
  keeps the other, and gives every row of a Trash with twins in all three
  a key of its own; `StartupTest` starts over notes it cannot read and
  finds an expired entry and its file both kept. Mutants TK1, TK2 and ST1
  watched failing. The walk opens a Trash holding two such entries, picks
  one, Selects all and Unselects all, keeps one while the rest go for
  good, and moves duplicates with the window not known. 42 parts, 351
  checks.

---

## [0.11.1] — 2026-10-24 — A pasted table fits its words

### Fixed

- **A table pasted as markdown fits its words**, as on the desktop. A
  table copied as text — from a chat, a `.md` file, a page's code — came
  in with its columns cut short, "How much" drawn "How…": markdown spells
  a right-aligned column `---:`, and the app read every separator cell
  longer than three characters as a width set by hand. A paste from
  elsewhere now spells the separator at markdown's minimum, its colons and
  hidden marks kept — into a note's text, a day of a series and a
  template's text, as words or as a page's HTML. The app's own copy keeps
  the widths set in it, and notes already saved are read as before.
  Found in the documentation's own photograph of a table.

### Testing

- The unit tests' temporary folders go under `app/build/test-tmp/`, one
  per test task, emptied when the task starts. In the system's temp folder
  they were never removed — `deleteOnExit` takes only an empty folder —
  and 30,640 had piled up there by 2026-10-23, 665 more on every run.
  `TempPlaceTest` holds both halves: a folder made under `build`, and none
  left from an earlier run. Two mutants watched failing — the second only
  on a second run, the first having left the folder it finds.
- The paste fixture gains 86 texts with markdown tables, each with what
  the desktop's `forget_widths` makes of it and where it moved the text;
  `PastingTest` asks the phone the same. `EditorCopyTest` pastes a table
  into a note in the middle of words, on its own lines, plainly and into a
  day of a series, and a note's own copy back by the plain route and by its
  HTML, its widths kept. A new walk part copies the table from a note's
  comments, pastes it into a template's text and into a note, reads both
  files and measures the drawn header against "rent"; on the build before,
  all three of its checks failed (the header 82 pixels wide, "rent" 80).
  701 JVM unit tests in 31 suites. Mutants PT1–PT12 watched failing: eleven
  on the rules and the editor's model, caught by the unit tests, and one
  on the template's field, an APK caught by the walk part.
- **Screens of every size.** A walk part sets the emulator's display to a
  small phone and to tablets of 7, 10 and 14 inches, upright: the tabs a
  bar or a rail, a note in the list's place or beside it, + on the screen
  and the calendar's whole week in sight. The app needed no change; a rail
  moved to 700dp and two panes to 900dp each failed it.
- **The walk leaves the emulator's Downloads as it found them.** Every
  walk had left its exported files and its backup there, and at 32
  copies of one name Android's picker refused the next, its Save doing
  nothing: a full run lost six checks, the app not at fault. The walk now
  removes what it saved once it has read it back. Four checks that read
  the screen once after the calendar opened now wait for what they look
  for. 41 parts, 332 checks.
- **The documentation's screenshots in both themes**, every screen taken
  again in Midnight Purple as `NN-name-dark.png`, and two more on its
  side: the calendar with a note open beside it, and Settings with
  Categories open.

---

## [0.11.0] — 2026-10-23 — The day's reminder, and the note's menu

### Added

- **On a wide screen the list can be put aside for a wider note** — asked
  for on the owner's phone, turned on its side. « beside ← in the note's
  top bar hides the list (or the calendar) and the note takes the whole
  width; » brings it back, as do closing the note and the rail's own
  button. The choice holds from note to note until the app is closed.
- **The day's reminder bar, the desktop's** — the owner's ask. On a day
  that has notes, repeats included, the app opens with "🔔 You have 2
  notes for today (Friday, 23 October)." above the notes, once a day, by
  the desktop's rule and under its key; a tap opens the day on the
  calendar, ✕ puts it away.
- **A notification for the day's notes**, the phone's own — the owner's
  ask. Off until turned on in the new Settings → Reminders, where Android
  asks first whether the app may send notifications; then, at the hour
  chosen (nine unless changed), with the app closed, it says how many notes
  the day has — never their words, which a locked screen would show — and
  a tap opens the day. The phone may bring it a few minutes late to spare
  its battery: an exact hour needs a permission the app does without. It
  only reads the notebook, and sets itself again after a restart or an
  update.

### Fixed

- **A tap past the end of a line puts the cursor at the end of that
  line** — found on the owner's phone. In any note holding a list, it went
  to the start of the next line instead: words added to an item went in
  front of the next item's number ("2. two" then "more3. three"), and
  Enter after the last item broke the line before the empty "4." under it,
  leaving an empty line. Each line's break is drawn as an invisible
  stand-in at the end of the line, which a tap could land after; such a
  tap is now put back before it. Since lists were drawn a line at a time
  (2026-10-15).
- **The empty line a list leaves at the end of a note is drawn.** Enter on
  an empty item to leave a list left the note ending in an empty line
  that was not drawn: the cursor shown at the end of the last item was on
  it, and a tap at the end of the item landed there ("2. two" then a new
  line "s"). Found by an agent playing an ordinary user, like the four
  below.
- **After Undo or Redo, typing goes on where the change was.** The next
  letter went to the end of the note, the keyboard still up but the
  cursor nowhere, the format bar gone. It now stands where the change
  undone or redone was, as on the desktop, or where it stood when only
  styles changed.
- **Enter after a word the keyboard corrected continues the list.**
  Gboard's autocorrect sends the corrected word and the line break as one
  edit ("monday" becomes "Monday"), which the list did not take for an
  Enter: the new line came with no number or bullet, an indented item lost
  its depth, and in the middle of a list it left an empty line.
- **The keyboard's ✓ in a checklist's "A new item…" adds the row**, as
  Add does, the keyboard left up for the next; it only closed the
  keyboard. The desktop's Enter in its entry does the same. And in a long
  note the box stands whole above the keyboard, with its Add: the field
  brought only its line of words into view, the rest under the keys.
- **The note follows the cursor as you write** — found on the owner's
  phone. Enter on the last line the keyboard left showing made the next
  one under the format bar and the keys, and the screen stayed where it
  was: what was typed there could not be seen until the note was scrolled
  by hand. The field brought the cursor into view only when it took the
  focus; it now does after every change to its words — and so do the
  note's comments, a table's cell being written, and a template's text
  and comments, which had the same fault. And on a phone on its side, a
  tap on line 2 of a long note no longer shows lines 20 to 22 once the
  keyboard is up: the line with the cursor stays in sight.
- **The checklist row just added stays in sight** — found on the owner's
  phone. Each row added pushed the "A new item…" box a row further down,
  under the keyboard, and the screen stayed where it was, the new row out
  of sight with it. The screen now follows the box after each row.
- **Enter after a word breaks one line with Samsung's keyboard** — found
  on the owner's phone. With Samsung's own keyboard and its predictive text
  on, as it comes, every Enter after a word skipped a line: the keyboard
  finishes the word and presses Enter in one batch, the field reports the
  result twice, and the note's text applied the second report too. The
  same change from the same value, a moment apart, is now taken once.
  Gboard never sends it that way, so no emulator showed it.
- **An empty last line takes its own alignment** — found on the owner's
  phone. Centre (or Right, or an indent) on a new last line kept the
  cursor at the left until a letter was typed, and Left on it under a
  centred line kept it in the middle: drawn inside the line above, the
  empty line wore that line's style. It is now a line of its own, and the
  cursor moves at once; a tap past the end of the line above still lands
  on that line.
- **Settings → Your name says "Saved" under the box**, in green, where it
  shows above the keyboard — found on the owner's phone, where the message
  at the foot of the screen was under the keys. The keyboard's ✓ saves too.
- **Settings' list keeps its place** — found on the owner's phone. Back
  from a page low in it, the Trash say, came back at the list's top, the
  row just left out of sight. The calendar's month and its History did
  the same to each other. Each tab already kept its place while out of
  sight; the screens inside a tab now do too.
- **Beside the calendar, the empty pane no longer offers a + it does not
  have.** It read "Pick a note to open it here, or tap + for a new one",
  and the calendar's tab has no +; it now reads "Pick a note of the day to
  open it here."
- **Enter in the title goes on to the note's words**, and a line break
  pasted into the title becomes a space; it put a break in the title, which
  the list then cut at.
- **The phone on its side keeps the note in sight while you type.** With
  the keyboard up, the top bar, a line of hints and two rows of format keys
  took all the height it left, and not a word of the note showed. On a
  window that short the format keys are one row that scrolls, with no
  line of hints, and while the keyboard is up the top bar stands aside, its
  Undo and Redo at the start of that row: three lines of the note show
  above the keys. And + no longer sits on the search box while the
  keyboard is up, on its side or upright; it comes back when it goes down.
- **One note is said as one** in the questions an import asks: "All 1
  notes", "Skip the 1" and "1 notes you already have" now read "The note…",
  "Skip it", "1 note you already have", as the desktop words them.

### Changed

- **A tap on a list's number or bullet puts the cursor after it**, at the
  item's words — the owner's call, on both apps. Before it, a letter typed
  made "x3. ham", a line that is no item any more.
- **"↩ Restore previous" keeps what it replaces, and its banner leaves
  once the text is back** — the owner's call, on both apps. After a big
  deletion taken back by Undo, the banner stayed, and pressed later it put
  the old version over everything written since, with no way back. Now a
  save that holds what the copy kept spends it, and a restore keeps the
  text it replaces as the copy, so the same banner brings that back —
  unless it holds nothing the restored text lacks, such as an emptied note.
- **A checklist, comments and files are added from the note's ⋮** — the
  owner's call. Three links under every note made it busier than it needs
  to be; ⋮ now holds ☑ Add a checklist, 💬 Add comments and 📎 Attach
  files, the desktop's marks. A checklist or comments asked for there
  come with the keyboard in them; ⋮ stops offering what the note already
  shows; a note that has them shows them as before. The welcome note says
  where they are.

### Testing

- `tools/emulator/walk.py` gains a part for the day's reminder: the bar on
  a day with a note, its day written under the desktop's key, a tap on it
  opening the day, no bar on a second start; the notification turned on
  through Android's question, its alarm set, fired (the debug build lets
  the walk fire it) and saying the count without the note's words, a tap
  on it opening the day, and turned off with its alarm gone. The editor's
  corners and the attaching part reach the checklist, the comments and
  files through ⋮, and check the links under the note are gone.
- `tools/emulator/walk.py` gains two parts. One taps past the end of an
  item, of a plain line, and of an item before Enter; the build before
  failed all three, on every run, the way the owner's phone did. The other
  walks what the agent found: a tap at the end of the last item over the
  hidden empty line, a tap on a marker, a letter after Undo, Enter in the
  title, the checklist's tick — all five failed on the build before — and,
  in a long note, the checklist's box and Add above the keyboard (a build
  without the fix put Add 32 pixels under its top edge). The part for a
  wide window checks, the keyboard up on its side, that the note's words
  stand above the keys and + is gone; the build before showed no word of
  the note, and its + was there. And, from the owner's phone: three rows
  added with the tick in a long note, the newest and the box under it above
  the keyboard; Enter after Enter at the foot of what shows, the line being
  written in sight above the format bar — measured by how far the note
  moved. A build without either fix fails its own check: the newest row out
  of sight, the note moved 0 pixels and its last line 116 under the bar.
  The same Enter in the comments, measured from the field's own top; on
  its side, a tap on line 2 of a long note, the cursor looked for in the
  note's pane by its colour once the keyboard is up (none of it there on
  the build before). The wide part puts the list aside and back with a
  find bar open, closes and opens notes with it aside, scrolls Settings'
  list and the calendar's month and comes back to each after a page or the
  History, and reads the empty pane beside the calendar.
- The walk waits for the phone's "save as" picker to offer Save, instead
  of looking once after three seconds: a picker slower than that left a
  backup unmade and three checks failed for one cause. All three of the
  walk's picker steps, the backup, the Trash page's and the export.
- 690 JVM unit tests in 30 suites, from 675 in 29: `RemindersTest` holds
  what the reminder counts, when its bar shows, the notification's hour and
  when it is next due; `EditingTest` holds where a change ends,
  Enter at the end of the text included, and a change told twice taken
  once, as Samsung's keyboard told it on the owner's phone; `ParagraphsTest` holds Enter with a
  corrected word — at the end of a list, on an indented item, in the
  middle of one, the styles after it moved; `IdentityTest` holds the copy
  spent by a save that puts its text back, kept by one that does not, the
  replaced text kept by a restore, a second restore bringing it back,
  nothing kept over an emptied note, and the question promising to keep
  only what it will. Five mutants watched failing.
- The agent itself, playing an ordinary user on the emulator and typing
  on Gboard's keys, is a new way of testing here
  ([the story](docs/ISSUES_LOG.md#what-a-persons-keyboard-found-that-the-walk-could-not)).

---

## [0.10.1] — 2026-10-22 — Numbered lists, and a search from the top

### Fixed

- **A numbered list stays numbered when you delete across it** — the
  desktop's fix, ported with its rules. Items selected and deleted left
  the numbers after them as they were ("1. one / 4. four"); two items
  joined from either side kept the second one's number among the words
  ("2. two3. three"); typing or pasting over whole items glued the next
  item's number onto what went in ("x4. four"). Now the list renumbers at
  once, a joined item's number goes, and what is typed or pasted over
  whole items takes their place while the next item keeps its own.
  Enter over a selection stands as before.

### Changed

- **A new search shows its results from the top** — the owner's call,
  on both apps. With the list scrolled down, a search typed kept the
  list where it was, and its first matches could sit above the screen.
  Each new search, and clearing one, now starts at the top; Back from a
  note, the search is the one the list was drawn for, and it keeps its
  place.

### Testing

- 675 JVM unit tests in 29 suites, from 665: `ParagraphsTest` holds
  `deletionRange` to the desktop's `list_deletion_range` and walks each
  keyboard route through `afterTyping` (styles and depths carried along);
  `PastingTest` a paste over whole items; `EditorTypingTest` the editor
  itself. Seven mutants watched failing, one of them Enter over a
  selection, which must stand as typed. `tools/emulator/walk.py` passed
  all 258 with every keystroke now passing through it.
- `tools/emulator/walk.py`: 259 checks, from 258 — a search typed with
  the list scrolled down shows Card 19, its first result, at the top;
  the build before it failed that check on every run, at Card 14. The
  part no longer scrolls up before it searches, which is what the new
  check is for.
- `tools/emulator/walk.py` waits for an emulator just started to settle
  (its processor under a quarter busy for 30 seconds running), and
  watches the theme's dissolve with the animations five times slower,
  then puts them back. The fade had held by about three captures when
  warm and by one or none on an emulator just started; slowed, it holds
  twelve even there, and a build that cuts at once still fails. And
  `keyboard_down` returns only once the system says the keyboard has
  gone: on an emulator started that day the answer stayed "up" for over
  a second after the Back, and a second call pressed Back off a table
  cell, four runs in four; `take_screenshots.py` waits the same way.
  ([The story](docs/ISSUES_LOG.md#a-check-that-held-by-one-frame).)

---

## [0.10.0] — 2026-10-21 — The privacy policy

### Added

- **The privacy policy, from Settings → About** — Google Play asks for it
  to be reachable inside the app as well as on the store page. Privacy
  policy → Read opens it, on Google Sites: the same page as the
  desktop's, covering both apps. Its line says the policy tells what
  leaves the phone, and when.

### Removed

- **Send feedback, and the site's form behind it** — the owner's choice:
  feedback is a review on Google Play. Once the app is published there
  (`Links.STORE`), About shows a Feedback section whose Write a review
  opens its page; until then there is none. The form held every message,
  with its sender's address, on the site's host; now the app hands
  nobody anything, and the Data safety answer stays "no data collected".

### Fixed

- **The welcome note no longer says the app can follow the phone's dark
  mode** — "Follow the phone" left in 0.9.1, and the note a new notebook
  opens on went on promising it. It now names the Light | Dark switch.
- **The welcome note's Write heading lines up with the others.** Its ✍
  was drawn in black and white and narrower than the other icons, so
  "Write" started left of every other title. It is 📝 now, from the
  emoji block like the rest — the desktop's note made the same change.

### Changed

- **The welcome note gives your privacy a section of its own** — 🛡 Your
  privacy: the app collects nothing and cannot use the internet, and
  Settings → About → Privacy policy has the whole of it — so a new
  notebook meets the policy on its first page.
- **About says the app is free software under the MIT license, provided
  as is** — where someone who installed it from Google Play, and never
  sees the license file, will read it. The line about the notes' backup
  follows it, as before.
- **The WebView a note is printed to PDF with sends Google no usage
  statistics.** Android's WebView may send them when the person has
  allowed them for the phone; the app opts its own out in the manifest,
  so what the privacy policy says — the app sends nothing — holds for it
  too.

### Testing

- 665 JVM unit tests in 29 suites, from 660: `LinksTest` holds the
  policy's address to the desktop's, keeps the store page unset until
  publishing, and reads the WebView's opt-out from the manifest.
- `tools/emulator/walk.py`: 258 checks in 36 parts, from 256 — it reads
  the as-is line on About, finds no Feedback section there before the app
  is on Google Play, and taps Privacy policy → Read to find a browser in
  front. On 0.10.0 it passed all 258, on an emulator given a minute and a
  half to settle; on one started a moment before, the dissolve's check
  had failed three times, and the check, not the app, still needs
  mending. The list-keeps-its-place part now scrolls until the top card
  is seen before it searches: two blind flings stopped anywhere from
  Card 14 to Card 16, and a third of the runs then lost "Card 15" above
  the screen.
- `OrganizingTest`: the welcome note gives privacy a section of its own,
  under its own heading, which says the app collects nothing and points
  to the privacy policy; no icon in it carries the emoji variation
  selector the desktop draws as a gap; it no longer promises to follow
  the phone's dark mode; and every heading's icon is from the emoji
  block.

---

## [0.9.1] — 2026-10-20 — Light | Dark

### Changed

- **Appearance: one Light | Dark switch, and no "Follow the phone"** —
  the owner's ask. The switch is the app's mode: moved, the app wears the
  last theme of that mode at once, as the desktop's quick toggle does,
  and below it are that mode's four themes, the one worn ticked. A
  notebook that followed the phone — the default until now — keeps the
  theme it was wearing, settled once at the first start; the phone
  turning dark or light no longer turns the app.
- **A change of theme dissolves** — from the owner's report of a flicker
  between a light and a dark one. The whole screen cut from white to
  dark, or back, in one frame, and the clock and the battery took the
  new contrast a moment after it. A first answer made the cut sharper —
  everything in the same frame — and she saw the flicker more, not less.
  Now the screen as it is is kept as a picture, the theme changes under
  it, and the picture fades out over 320 ms; the bars, and the window's
  own colour, follow halfway through.

### Testing

- 660 JVM unit tests in 28 suites, from 656. Five mutants against the
  theme's rules (a theme not kept as its mode's last, a choice leaving
  the phone followed, Light | Dark going to the other mode, settling on
  the other mode's theme, a settled notebook still following), all
  killed; three built into the app — a notebook never settled, a switch
  that only chose which tiles to show, and the theme cut at once with no
  picture fading over it — all killed by the walk's first part.
- `tools/emulator/walk.py`: 256 checks in 36 parts, from 253 — the first
  part walks Light | Dark, a tile kept as its mode's last, the phone's own
  dark mode leaving the theme alone, and the change dissolving: raw
  captures taken as fast as the emulator can while Dark is tapped must
  hold a shade between the light page and the dark one. Cut in one frame,
  none does ([242, 242, 242, 242, 35, ...]). The full run passed all 256
  on its first try. The documentation's pictures were taken again.
- The change measured on the emulator, capture by capture, the page's
  brightness: cut, 240 then 38 with nothing between; dissolving, 240,
  187, 59, 38 and back 38, 119, 234, 240, the bars moving with the page.
  Half the frames around a change came in late before (19–27 ms the
  median), a few now (17–18 ms). An emulator draws in software, so how it
  looks on a phone was the owner's to judge.
- On the owner's phone, release build, twice, each time 7 checks passed
  and nothing written: the theme she wore kept, the page without "Follow
  the phone", one mode's four themes. Her judgement of the change: the
  sharper cut flickered more; the dissolve "is better now".

---

## [0.9.0] — 2026-10-20 — The first test on a real phone

What the app's first afternoon on a real phone asked for — a Samsung
Galaxy S24 Ultra, its letters set to 115% — and the release build it is
tested as from now on. Built on Phase 3.

### Added

- **Share**, the mark at the top of a note: the note as a PDF of its
  page — A4, tables and colours as shown, made on the phone — as a web
  page, as its words in the message, or as a share code, handed to the
  phone's own share sheet: WhatsApp, a mail app, Teams, Drive and the
  people those apps put first. The words draw the checklist's boxes as ☐
  and ☑; a note with a table is not offered as words, which cannot draw
  one. The app itself sends nothing and remembers nothing. It took the
  place of "Send by email", made earlier in this round, and of the 🔗:
  one door out instead of two, and a table that read as lines of a
  message no longer sent that way.
- **"Fit all columns to their words"**, in the table's More, beside the
  one-column fit: the whole table fitted in one step, a hidden column
  left hidden.
- **A table's edges, dragged**: the chosen cell shows a handle on its
  right edge, for its column's width, and one on its bottom edge, for its
  row's height — the lines the desktop drags, made a finger's size. A
  width is the separator's dashes and a height empty lines in the row's
  tallest cell, as the desktop writes them, never cutting into words;
  one Undo takes a drag back.
- **A release build**: shrunk by R8 to 2.0 MB, not debuggable, its one
  native library 16 KB-aligned, and an app bundle beside it. Signed for
  now with this machine's debug key, so it installs over a debug build
  and keeps the notebook.
- **B, I, U and S with nothing selected style what is typed next**, until
  pressed again — the desktop's pending style, by the owner's choice. The
  line over the keys says which are on. A colour, a size or a face still
  asks for a selection.
- **A repeating note's own view says what its edits reach**, in the
  desktop's banner: "This note repeats (every week on Mon) — edits here
  apply to every day · 3 days keep their own edits (✎) · 1 day moved
  (↷)". An edit not reaching three days looked like one that failed.
- **One note imported is offered to open**, where the desktop opens it
  for review; "Added 1 note" left it to be found in the list.
- **A day of a series says what is its own**, as the desktop's does: its
  banner where it was moved from, its checklist "· whole series" — a tick
  there is a tick on every day — and its details "This day's category"
  and "This day's colour".

### Changed

- **Appearance, following the phone**, shows one mode's four themes at a
  time, under a Dark | Light switch opened on the mode the phone is in;
  eight tiles with two ticks read as "which one is on?". Not following,
  all eight, as before.
- **Every entry of the editor's ⋮ menu has its icon**; Duplicate and Move
  to Trash had none.
- **Export as file shows its two kinds as cards**, each saying what it
  gives — a web page for a browser, plain text for any app — in the
  Share sheet's look.
- **A backup at hand.** Settings → Backup's button is "Save a backup",
  and the page says where one can go — Google Drive, the phone's files,
  anywhere the phone offers — and that Android's own backup may keep a
  copy too, coming back only when the app is installed again. The Trash
  page has the same button. The app stays offline: the Drive app does
  the sending.
- **A table is written in place**, shaped on Samsung Notes' table at the
  owner's request. A tap on a cell opens it for typing right in the
  table, and a bar over the keyboard takes the place of the cell's
  menu, a sheet of eleven entries: Row, Column, Merge, Color, Align,
  Box, Delete and More, one word each. A button with a choice opens it
  as a row above the bar — Above or Below, Left or Right — and More
  holds the rarer changes: widths, hiding a column, a caption, a line
  above, the table's place on the page. The file is written as before,
  the rows the desktop reads. A tap on a merged cell's lower part chooses
  the merged cell.

### Fixed

- **Back from a note finds the list where it was**, scrolled to the card
  that was opened, its search still typed — field-reported by the owner.
  On a phone an open note takes the list's place, and the list built
  again came back at its top with the search box empty; the History's
  search and its place went the same way. Each tab now keeps what it
  remembers while it is out of sight.
- **A card says a table is there and never draws it.** A row left open
  or typed past its last cell showed on the card as a line of bars; the
  card now leaves out every line a person reads as a table, by the
  desktop's `preview_text`, and says "▦ 1 table". A calendar day's rows
  say a note's tables too.
- **The list scrolls smoothly.** Each card was handed its note's whole
  text, worked its preview out on every drawing and was measured twice;
  on the phone 3% of frames were late, the slowest 44 ms.
- **A table's cells hold their words at any text size.** The widths were
  in a unit that does not grow with the phone's letters, so at 115% a
  cell's word broke across two lines.
- **Statistics' tiles are all one size.**
- **The size box says the size of what is selected** — "14" for words at
  14, where it stayed at 12.
- **A template's body shows its tables as tables**, where its box showed
  the rows as bars and dashes above the drawn table.
- **A copy made right after styling carries the styling.** Words
  selected, made bold or 14 from the format bar and copied at once went
  out plain: the copy used the formatting the words had when they were
  selected. A cut the same.
- **Deleting a table's first row keeps it a table.** The dashes under
  the header were left on top and the lines stopped reading as one; the
  next row is the header now, as on the desktop.
- **Deleting a column of a two-column table empties it**, as the desktop
  does. It was taken away, and one column reads as prose, so the table
  turned into lines of bars.
- **A table's columns are at least eight characters wide**, as on the
  desktop, unless a width is set by hand: a new table's empty cells were
  drawn as slivers too narrow for a finger.
- **A merge left joining nothing is undone** when a row or a column goes,
  as the desktop's `_dissolve_orphan_markers` undoes it; its `<` or `^`
  stayed in the file.
- **A merge keeps both cells' words**, joined in the cell kept in reading
  order, as the desktop's merge joins them, with their styles. The phone
  wrote the merge's marker over the chosen cell, and its words went —
  words merged with an empty cell came out empty. A merge down is drawn
  as one cell, where the cell under it stood apart and empty.
- **"Row → Below" on the header keeps the table the desktop's**: the new
  row goes under the separator, which it had pushed to the third line,
  where the desktop reads none.
- **A row of dashes below the second line is a row**, drawn and edited
  as one, as the desktop reads it; the phone took it for the separator.
- **A table typed past its last cell keeps its separator** through an
  edit: squared up with empty cells, the separator stopped being one.
- **Rows and columns added or taken away keep the cells' styles** — a
  cell's colour and the bold on its words, carried cell by cell as the
  desktop carries them. Re-padded rows lost every style between the
  first change and the last; aligning a cell lost its own words' bold.
- **A right-aligned or centred cell typed full keeps its alignment**: with
  no room left for the padding that says it, it read back as left.
- **A table's first row is its header**, drawn as one with or without a
  separator, as on the desktop; a new table had none until a width was
  set.
- **Centring a caption takes its table along**, as on the desktop.
- **A cell's words sit where its own padding says**, as on the desktop
  and in the export; a `:---:` column drew centred on the phone alone.
- **"This column wider" never makes a long column narrower** than its
  words; it went four characters past the phone's 26-character drawing.
- **The last column in sight cannot be hidden**: with none left, the
  table drew nothing and Show could not be reached.
- **"<5 €" or "[ ] milk" typed in a cell stay words**: on the way, the
  cell held "<" or "[ ]" alone, was written as a merge or a box, and its
  field vanished under the finger.
- **An emoji counts as one character** in a table's widths, as on the
  desktop.
- **Select all in a grouped list takes only the notes in sight**: with a
  group folded, its notes were chosen too, and 🗑 sent them to the Trash
  unseen.
- **A note restored from the Trash takes a new identity** when a copy
  that came by code has taken its own, as the desktop's restore does:
  two notes with one identity, the next code updated one and never the
  other.
- **A backup's notes keep their "↩ Restore previous"**: a notebook moved
  to a new phone lost every one.
- **An import compares notes as a load shapes them**, as the desktop
  does: an unchanged note filed under General for a category this phone
  lacks, or differing only by a day's empty colour or a closing table's
  line, no longer reads as "edited elsewhere" — where an Update spent its
  only rescue copy on a copy of itself. "Updated" counts only notes that
  changed, and an arriving note takes the desktop's shape: known styles,
  line entries within its lines, the line under a closing table.
- **A share code keeps the desktop's limits** — at most 200 notes, and
  none past the size the import takes or past what can be handed to
  another app — and says so in the desktop's words, where the phone made
  codes both apps refused. A code from a note with files says the files
  stay behind.
- **A pin leaves a note's "updated" time alone**, as on the desktop.
- **Notes sharing an id get their own at start**, as the desktop's
  `repair_note_ids` gives them: two such notes were edited as one.
- **Bold and italic stored as two styles are read as one**, as the
  desktop reads them: B took the italic off with the bold.
- **Enter or Backspace in a centred or indented line keeps it so**, both
  halves of a broken line and the upper of two joined; a centred list
  item continues centred.
- **A History row off the schedule opens its series**, whose banner
  settles such days, as the desktop's History does: opened as a day, it
  could be moved or deleted where no calendar would show it again. Such
  a day is never moved or deleted as one of the series' days, and a
  deleted day's Undo is refused once the repeat no longer gives it.
- **Turning a repeat off while changing its start** keeps the old start
  in the rescue copy.
- **A date from any year opens in the date picker**, and the calendar
  stops at December 9999: a note dated 1026 by a slip, or a step past
  9999, stopped the app.
- **"↩ Restore previous" saves first, then asks**, as on the desktop, so
  the question names the copy it will bring back.
- **Letters typed beside a styled word take styles as on the desktop**:
  glued after it they wear its bold, colour, highlight, size and face, up
  to a space; typed before it they stay plain. The phone did the
  opposite at both edges.
- **A colour tapped a second time stays**; only ✕ takes it away, as the
  desktop's swatch always sets. Tapped again "to be sure", it vanished.
- **A day given only its own colour or category keeps the series'
  styles**, as the desktop keeps them while the day's words are the
  series'; the phone showed every day with an edit plain.
- **A cleared day's Undo never paints over what was chosen since**: a
  colour or markers cleared, then chosen again within the Undo's
  seconds, stay, and the desktop's words say why.
- **The calculator's answer goes into the cell being written in**, at its
  cursor, where it landed on a line under the table.
- **Letters both underlined and struck through show both lines**; one
  hid the other while S said both.
- **A list entry whose marker is gone keeps its alignment, not its
  depth**, as the desktop reads it: the phone indented a plain line and
  saved the indent.
- **Bullets pasted as words take the depth their glyph says** — "◦ " one
  in, "▪ " two — where they sat at the margin.
- **A table pasted into the comments is left out**, its prose kept, and
  the line at the foot says so: the comments draw no table, and the
  desktop's showed its rows as bars.
- **A three-digit colour reads as six**, `#f00` as `#ff0000`, in a note,
  a day, a category and the text's own colours, as the desktop expands
  it; drawn as six digits' worth, it was a near-black green.
- **The duplicate check takes every Unicode space for a space**, as
  Python's split does: a non-breaking space pasted from the web made two
  copies look different.
- **A category's name is counted in characters**: an emoji counted twice,
  and a long name with a few was dropped that the desktop keeps.
- **A day filed under a category this phone lacks comes in under
  General**, as the note itself does, where it kept a name no Show here
  offers.
- **Styles piled too deep on one line are dropped at the door**, by the
  desktop's two ceilings, counted where a style covers — a note made to
  stop the drawing cannot come in by code or backup.
- **The Trash is emptied only by a window that can be read.** Settings
  that could not be read gave the default 30 days, and the start purged
  by it whatever window the person had chosen — notes kept for 90 went
  at 31; a hand-edited 0 read as one day. Now nothing is purged until
  the window is known, the start and the Trash page say so, and choosing
  a window again ends it.
- **A sheet from the bottom is drawn for the app's own dark or light, and
  opens whole.** On a light phone with the app dark, the phone's bar of
  buttons came out white over every sheet and the clock dark on dark;
  and a sheet taller than half the screen opened half-way, Share's last
  card under the buttons. Seen on the owner's phone, which has three
  buttons where the emulator had gestures.

### Testing

- 656 JVM unit tests in 28 suites, from 572 in 24. 102 mutants against
  them: 99 killed — one, a deleted row's orphan marker, only after a
  test was written for it — and three equivalent, each removing a check
  another line already made, taken out with that redundant check. The
  seven against the email's rules went with that code, and twelve
  against Share's took their place, four of them on its one share at a
  time; nine are on the Trash's window, and thirty on the desktop's old
  way brought over. Thirty more built into the app: twenty-eight
  caught by the walk — among them the list built again on Back, which
  came back at its top with its search gone, the three against the sheets' bars and
  opening, the Trash page's backup opening a file to import, caught only
  once its check asked for a Save button, and the five on the old way's
  screens; one of those, the series' banner, first written so it would
  not build and not counted until it did; two only on a second try, after the
  walk learned to type a letter after Undo and to type "<" on its own;
  one, a paste losing its formatting in the release build only, caught
  by the new release gate and not by any screen; and one survived the
  walk — a second Share started over the first, whose failure message
  the screen's pixels cannot tell from the refusal's. That rule moved to
  the rules side, where the unit tests kill it. The four against the
  email's hand-off went with that code, among them the one that had
  survived. The size box's and the template body's fixes had no mutant
  run; Statistics' tiles, the menu's icons, the table's handles and a
  merge down were photographed, and the PDF's file written off the main
  thread was measured, not mutated.
- The table's bar on the owner's own phone, release build, in a note of
  the check's own: 25 checks, all passed, twice — the second after the
  phone showed the empty cells too narrow to tap.
- `tools/check_release_lookups.py`, a gate on the built release APK:
  every file its code looks up by name, found where the renamed code
  will look.
- `tools/emulator/emulator_only.py`: every emulator script reaches an
  emulator, never a phone that happens to be plugged in, even when
  `ANDROID_SERIAL` names the phone.
- `tools/emulator/walk.py`: 253 checks in 36 parts, from 177 in 25 —
  previews, the list's place and search kept while a note is open, a
  table at larger letters, sharing a note, a sheet over
  three buttons, a copy and a cut made right after styling, typing next
  to styles, the calculator's answer in a cell, a table pasted into the
  comments, the table written in place and its dragged edges, Select all
  with a group folded, a History row off the schedule, the Trash page's
  backup, and checks grown in older parts. Every read of a file the autosave writes
  now waits for the save (`settle`), where one Redo read a moment too
  soon; so does the first read after a start, where a brand-new notebook
  was read before the first start after an install had written it; and
  a photograph waits for its screen, where one was taken before that
  start had drawn the list. The last full run, after the list learned to
  keep its place, passed all 253 on its first try. Two mutants' first runs failed for a
  reason that was not theirs — the list not yet on the screen after an
  install, and the emulator's System UI not answering on a computer
  short of memory — and were not counted: run again, one was killed and
  one survived.
- A Trash window that could not be read, walked on the emulator with a
  damaged `settings.json`: nothing purged at that start or the next, the
  start and the Trash page saying so, a window chosen ending it —
  photographed, and each step read back from the files.

## [0.8.0] — 2026-10-16 — Phase 3

The rest of what the desktop does with a note, each kept to what a
phone's screen holds: arranging the list by hand, moving one day of a
series and settling days its schedule left behind, attaching files,
finding duplicates, a table's caption and column widths, undo and redo,
and copy and paste that keep the formatting. Built on Phase 2. What is
still left is in
[docs/STATUS.md](docs/STATUS.md#not-built-yet).

### Added

- **Custom order arranged by hand**: a ⠿ handle on each card, the list
  scrolling by itself at its edges, a note moving within its own block —
  pinned or not, its category when grouped — and every note numbered as
  the desktop numbers them, with no "updated" stamp.
- **"📅 Move this day…"**: one day of a series held on another date,
  with its Undo, in the desktop's words when a date is taken.
- **Days off the schedule**: when a repeat changes and days with their
  own edits fall off it, the series says so, and each is settled with
  one question — a note of its own, the Trash, or left as it is.
- **Attach files** from the phone's picker, stored as the desktop
  stores them, and take one off after a question.
- **Settings → Duplicates**: the desktop's finder and its clean-up, with
  Undo, a file a copy carried handed to the note kept.
- **A table's caption, its columns' widths and a line above it**, from
  the cell's menu: Add caption below…, Wider, Narrower, Autofit, Hide
  column, Unhide all columns, Add a line above the table.
- **Undo and Redo** in the editor, a burst of typing at a time, from ↶ ↷
  and from a keyboard's Ctrl+Z and Ctrl+Y.
- **Copy and paste with the formatting**: a paste reads HTML as the
  desktop's paste does; a copy puts HTML beside the words; the app's own
  copy brings back sizes and faces; "Paste as plain text" and
  Ctrl+Shift+V paste the words alone. A paste the caps cut says so.

### Changed

- A day of a series, its move undone or its date reset, comes home only
  to a date no other day of the series is on; the desktop brings it home
  on top of the other.
- A pasted page's second table keeps its styles on their words, where
  the desktop's padding moves them beside them.

### Testing

- 572 JVM unit tests in 24 suites, from 516 in 23. 94 mutants against
  them: 90 killed, 4 equivalent. Four more built into the app and caught
  by the walk on the emulator.
- **A sixth port held to a fixture the desktop's own Python wrote**:
  pasting — 567 HTML fragments through the desktop's `html_to_note` and
  Python's own parser, the HTML 80 of the app's own copies were written
  as, and 117 plain texts rejoined and unmarked; the 20 fragments where
  the phone parts from the desktop on purpose are kept with the
  desktop's answer beside them.
- `tools/emulator/walk.py`: 177 checks in 25 parts, from 127 in 17 —
  a part for each Phase 3 feature, the keyboard's copy and paste keys
  where the selection menu cannot be pressed. The first full run from
  start to end failed five steps, all the walk's own, in older parts the
  new screens had changed under them; fixed, the second passed all 177.

## [0.7.0] — 2026-10-16 — Phase 2

The desktop's remaining everyday features, each kept to what a phone's
screen holds: the calendar and repeating notes, the whole format bar,
templates, find, the calculator, export, statistics, and a layout for
wide screens. Built on Phase 1. What is left for Phase 3 is in
[docs/STATUS.md](docs/STATUS.md#not-built-yet).

### Added

- **The calendar**, a tab of its own: the month, Monday first, a dot for
  each colour of note on a day, painted days and their markers, today
  and the open day marked, a swipe for the month; the open day's notes
  under it with "+ Add note"; a day held down to add, paint or mark,
  with Undo for what is cleared; and the History in the month's place.
- **Repeating notes**: the Repeat sheet in a note's details, which
  chooses rather than types, saying the rule in the desktop's words and
  showing its next three days. The desktop's recurrence engine, ported
  whole.
- **One day of a series on its own**, the desktop's day mode: written to
  that day alone, "Whole series" and "Reset this day" a tap away, a day
  deleted from its series with Undo.
- **The desktop's whole Sort and Show**: Calendar: soonest and oldest,
  Custom order as arranged on the desktop, the Repeating, Dated and With
  files filters — and the list grouped by category, its groups folding.
- **Settings → Statistics**, the desktop's snapshot of the notebook.
- **The calculator**, docked under the note: the desktop's keypad, More
  functions, a history, and Insert into note.
- **Find in note**: the count, back and next, case, prose and table cells
  together, every match painted.
- **Export as file**, `.html` or `.txt`, through Android's "save as" —
  the desktop's page byte for byte.
- **Templates**: start a note from one, save a note as one, and edit them
  in Settings → Templates, in the desktop's `templates.json`.
- **Size and face** on the format bar, and the desktop's faces drawn as
  the kind they are — serif, fixed-width or plain.
- **Lists, indent and alignment**, with the desktop's Enter and Backspace
  in a list and its renumbering.
- **A new table** from a size grid, its first cell open to type.
- **The cell menu**: a cell's alignment and highlight, merge and split,
  rows and columns, the table's place on the page, and delete the table.
- **Wide screens**: a rail down the left edge from 600 dp, and from 840
  dp the note beside the notes or the calendar, its card outlined.

### Changed

- The bar at the bottom has three places: Notes, Calendar, Settings.
  Back from the Calendar goes to the month from the History, then to
  Notes.
- Every Undo — a note to the Trash, a day removed, a day's colour or
  markers cleared — is one eight-second offer.
- A note's details show its repeat, and open the Repeat sheet.

### Fixed

- Every font size set on the desktop was read as empty and written back
  without a value, so the desktop dropped it; and typing into a list
  item or an aligned line dropped that line's entry.
- A change made to the open note from outside the editor — most of all
  a code shared in over it and applied as an update — was put back by
  the editor's next save.
- A cell's own alignment was not drawn, and a merged cell was drawn
  short of the columns it covers.
- A table edit that changed nothing — the last row asked to go —
  re-padded the whole table.

### Testing

- 516 JVM unit tests in 23 suites, from 317 in 13. 190 mutants against
  them: 187 killed, 3 equivalent. Three more built into the app and
  caught by the walk on the emulator.
- **Five ports held to fixtures the desktop's own Python wrote**: the
  recurrence engine (300 notes expanded, 400 dates read), the calculator
  (661 sums, 378 shown values), the export (400 notes, 794 files byte for
  byte), templates (302) and renumbering (300). The new sorts were
  checked against the desktop's `_sort_notes`, and all 400 export notes
  round-trip through a save on the phone.
- `tools/emulator/walk.py`: 127 checks in 17 parts, from
  33 in four — a part for each feature, and one turned on its side for
  wide screens. Its `screen_says` reads what a node says as well as what
  it shows, opening a note waits for the editor, and a title is checked
  by the box it shows, not by being in the tree.
- Two Phase 2 commit messages carry counts that were wrong:
  `e29e9a1` (24 mutants tried, 22 killed, 2 equivalent — not 26, 23 and
  3) and `314e89a` (`DaysTest` has 16 cases, not 17).

## [0.6.0] — 2026-10-15 — Phase 1

Phase 1: from a viewer for the desktop's file to a notes app of its own,
ready for a store listing. What it covers, and what is left for Phases 2
and 3, is in [docs/STATUS.md](docs/STATUS.md).

### Added

- **A notebook of its own**, in the app's private storage. A notebook an
  earlier build left in the shared folder is copied in once — never
  moved — and a brand-new one opens on a welcome note written for the
  phone. A file that will not parse is kept aside as
  `notes_data_corrupt_<stamp>.json`, as the desktop keeps one, instead of
  stopping every write.
- **One screen for reading and writing**, saving itself 0.8 seconds after
  the last change, on Back and when the app leaves the screen.
- Checklist rows dragged into a new order; comments; attached files
  opened in the phone's own apps; note details with Android's calendar,
  the colour swatches, the pin, and the word count with reading time.
- **"↩ Restore previous"**: the desktop's `_prev` rescue copy, restorable
  on the phone — the text, and everything an update replaced.
- Long press to select: pin, move to a category, duplicate, share as one
  code, or move several notes to the Trash, with Undo for eight seconds.
- Categories managed in Settings: add, recolour, rename everywhere —
  repeating days and rescue copies included — and delete with a question
  about the notes filed under it.
- **The Trash as a page**: restore several, delete for good, empty, and
  the window (7 to 90 days). It empties itself at start, sparing any file
  a note still uses.
- **The desktop's backup `.zip`**, both ways, through Android's file
  picker: export the whole notebook, import one with the desktop's guards
  and its three questions. Nothing is removed by an import, and a note
  already here is only replaced when the person chooses to update it.
- Share codes arrive from any app through Android's share sheet.
- **The desktop's eight themes** and **"Follow the phone"**, on by
  default, under keys of the phone's own so the desktop's theme is never
  changed by it. Settings also holds the name the list greets with, and
  About: the version, the welcome note, and Send feedback.

### Changed

- **The interface is English**, in the desktop's own words wherever the
  desktop already has words for the same thing. The rules layer's
  messages too.
- The screens are rebuilt on Material 3, coloured from the desktop's
  palettes, with a bottom bar for Notes and Settings; the 1,850-line
  `MainActivity.kt` became twelve drawing files beside fourteen rules
  files ([the map](docs/ARCHITECTURE.md#the-map)).
- An update arriving by code or backup keeps the whole `_prev`, not only
  the text, and an update that would change nothing leaves the note
  untouched. A category the device does not have files the note under
  "General", as `normalize_note` does.
- The startup categories, the highlight palette and the category
  colours are the desktop's, and a deleted category no longer comes back.
- A share code whose version is not a number is refused as damaged, as
  the desktop refuses it.

### Fixed

- The first edit on the phone of a note with a centred or indented line
  turned that line into a bullet and dropped every alignment and indent
  in the note: paragraph entries were rebuilt from two fields instead of
  copied whole.
- Turning the phone, its dark mode coming on, or larger letters closed
  the note being written and went back to the list.
- The card printed a date as the file stores it (`2026-10-18`), the editor
  as the desktop shows it (`18.10.2026`); both now show the desktop's.
- A new note was missing the desktop's `moved` field, which the
  note-shape gate caught after the desktop's `0.92.374`.

### Testing

- 317 JVM unit tests in thirteen suites, from 221 in six; every new rule
  mutation-tested, about seventy mutants in all.
- `tools/check_palettes.py`: the themes and colours compared with the
  desktop's Python, in CI beside the note-shape gate.
- `tools/check_test_results.py` refuses results older than the code — it
  once printed "221 passed" over a build that had not compiled.
- `tools/check_layering.py` fails on a Kotlin file it has not been told
  about, rather than leaving it unchecked.
- `tools/check_english.py` reads the app too, now that it speaks English;
  four files may carry diacritics, for the search that folds them, but no
  Romanian words.
- `tools/emulator/walk.py`: 33 checks in four parts on the built app,
  reading the file after every step — and failing when a step never ran,
  after four checks were found that passed over exactly that. A backup
  made through the phone's picker was read by the desktop's own import
  functions.
- The documentation screenshots retaken in English, six of them, the
  notebook written straight into the app's storage.
- The test plan grows from 35 cases to 57.

### Earlier, before Phase 1

The entries below were collected under [Unreleased] before Phase 1
began, and ship with it.

#### Testing

- Four documentation screenshots, taken on the emulator by
  `tools/emulator/take_screenshots.py`. The script seeds a notebook
  written for the camera — documentation is the one place somebody's real
  writing must never end up — and every shot names the words that must be
  on screen before the shutter opens. The first run showed why: a tap
  that missed the search box left the plain list on screen, and the
  picture was saved and filed as "search" anyway.

- Two more gates, and CI to run all three on every push. The rules layer
  is checked for Android imports (`tools/check_layering.py`), and the
  unit-test job no longer trusts Gradle's exit code alone: it reads the
  result XML afterwards (`tools/check_test_results.py`), because a task
  that never ran prints exactly what a task with no failures prints.
- The interop fixture stops carrying a real note. It held a share code
  made from somebody's actual notebook; a share code is not encryption,
  and this repository is on GitHub now. `tools/make_interop_fixture.py`
  rebuilds it from an invented note, using the desktop's own encoder.

#### Removed

- Android Studio's example test, which checked that 2 + 2 is 4 and was
  counted in "222 unit tests". 221 then, all of them this project's.

#### Changed

- The launcher icon's provenance, the emulator tools and the screenshots
  are described where a reader meets them, rather than being folder
  contents nobody can account for.
- It runs on **Android**, not on phones. The title said "Note Organizer,
  on a phone", which is a claim about the platform that the platform does
  not make — Android is on tablets and foldables too, and the minimum
  here is an API level, not a form factor. Where the word stood in for
  the platform it now says Android; where it means the thing somebody is
  holding, it stays.
- CI runs once per change rather than twice, skips what cannot break a
  test, cancels runs a newer push has made pointless, and carries a
  timeout on every job — the default being six hours.

---

## [0.5.0] — 2026-09-14

### Added

- **The app wears the desktop's icon.** `tools/make_launcher_icon.py`
  imports the desktop app's own `tools/make_icon.py` and asks it for the
  drawing at Android's sizes — one drawing, two programs, rather than a
  second icon that merely rhymes with the first. Ships as
  `mipmap-*/ic_launcher*.webp` at five densities plus the two adaptive
  layers and their `mipmap-anydpi-v26` XML.

### Fixed

- The mark was drawn at the full 72/108 safe square and came back from
  the phone with its frame cut off at every corner: a circle mask keeps
  only the circle *inscribed* in that square, so a square mark has to fit
  inside it — 72/108 ÷ √2, about 47%. The preview that had approved the
  first attempt was composing the layers **without** the system's crop,
  which flattered the icon by exactly the amount that mattered.

### Removed

- Android Studio's template icon — the green robot — and the two vector
  drawables it arrived with.

### Testing

- `qa/NoteOrganizer_Android_TestPlan.xlsx`: 35 cases across nine areas,
  in the phone's own repository rather than the desktop's, each marked
  with who runs it — a unit test, an emulator tool, or a person with a
  phone in their hand.

---

## [0.4.0] — 2026-09-13

### Added

- **A note keeps one identity wherever it goes.** The desktop gained a
  `uid` beside its `id` (its version `0.73.159`), share codes carry it,
  and a note written here is born with one. A code holding a note this
  device already has is recognised as *that note* rather than guessed at
  from its words, and the phone asks — in the desktop's own wording —
  whether to update the copy here or keep both. Nothing is written until
  the question is answered.
- Share codes in both directions: a note becomes a pasteable code, and a
  pasted code becomes a note.

### Changed

- One import route instead of two. `importShareCode` — the way a code
  became notes before identity existed — had stayed behind when the
  screen moved to inspect-then-apply, reachable only by its own six
  checks. It was not merely dead: it carried a **second copy** of the
  new-note defaults, so a field added to the note shape would have rotted
  there in silence while its tests went on passing.

### Testing

- `tools/check_note_shape.py`: a gate that reads the desktop's Python
  **as the specification** and fails when the two programs stop agreeing
  about what a note is. Its own four kinds of drift were tried against it
  and all four turned it red — a gate nobody has seen failing is
  decoration.
- The identity loop walked on the emulator in both directions, on the
  built app rather than in a test, over a copy of a real 77-note
  notebook: the desktop's edit recognised here and applied in place; the
  phone's edit decoded back **in the desktop's own Python**; and the
  "keep both" answer adding a note with an identity of its own, leaving
  all 77 earlier ones untouched.

---

## [0.3.0] — 2026-09-13

### Added

- Editing an existing note's title and body, with the formatting spans
  and the bullet markers moved to follow the edit.
- Applying formatting over a selection — bold, italic, underline,
  strike, text colour and highlight — shown live while writing.
- Tables drawn as grids, with merged cells, checkbox cells, `<br>` line
  breaks, separator-driven alignment and sideways scrolling; and editing
  one as a grid: tap a cell to change it, add or remove rows and columns,
  turn a cell into a checkbox.
- Category, colour, date and pin on a note; checklist rows added,
  renamed, removed and ticked.
- Sorting (the desktop's five orders, pinned notes floating on top) and
  filtering by category.
- Delete to the Trash and restore from it. Nothing is destroyed: a
  deleted note moves to `trash.json` with a `deleted_at` stamp and the
  screen shows how long it has left. **The phone never purges** — only
  the desktop can also delete a purged note's attachments.

### Fixed

- A new note saved with none of its formatting. The toolbar worked and
  the screen drew bold text, but `formatting` had been added as a
  parameter to `updateNote` and not to `createNote`. Nothing looked
  wrong; the written file said otherwise.
- An edit reached the screen before it reached the file, because the
  copy handed to the screen and the copy being written were the same
  object. Found by a mutant that should have died and did not.

---

## [0.2.0] — 2026-09-12

### Added

- **Capture: a new note is written to the notebook.** The only code here
  that writes, and it carries the desktop's guards — append to the array
  that was READ rather than rebuilding the file from a partial model,
  refuse to overwrite a file that exists but would not parse, mend
  unpaired surrogates, write to a sibling `.tmp`, `sync()` it, then
  rename over the real file, and return the reason on failure instead of
  looking like it saved.

### Testing

- The write was judged by the desktop, not by itself: the result was
  pulled back off the emulator and checked in Python — all 77 existing
  notes identical field for field, the new note passing the desktop's
  `normalize_note` **unchanged**, every id unique.

---

## [0.1.0] — 2026-09-12

### Added

- A real 77-note `notes_data.json` reads without loss: Romanian
  diacritics, emoji, CJK, a 107,000-character note. No new dependency —
  `org.json` ships with Android.
- The note list draws the desktop's card: colour stripe, headline,
  preview, and the tags line with its date, comments, attachment, table,
  checklist and recurrence badges.
- Tapping a card opens the note with its formatting drawn.
- Search, by the desktop's own rule: title, body and comments, with a
  table's plumbing stripped so the words in its cells are found and its
  separator rows are not.

### Changed

- The headline falls through to the note's first spoken line when a note
  has no title, set lighter than a real title so a named note still reads
  as named. 48 of those 77 notes have no title and 15 more have one of
  three characters or fewer: a list of titles would have been a list of
  blanks.
- Search folds diacritics — `si` finds `și`, and the cedilla spellings
  older Windows keyboards produce match the comma-below ones. This is the
  one deliberate difference from the desktop so far, and it is a one-way
  widening: the folded search finds everything the strict one finds and
  more. Romanian diacritics get dropped constantly when typing on a
  phone, and a notebook written half with them is unsearchable under a
  strict rule.

### Testing

- Search verified against the specification rather than against itself:
  the desktop's `_query_matches_note` was run over the same file in
  Python, and the phone was made to type each query and report its count.
  Four queries, four exact agreements — 8, 1, 6 and 0 of 77.
