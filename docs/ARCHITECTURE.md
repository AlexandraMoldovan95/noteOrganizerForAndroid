# How the Android app is put together

The shape of the code, and the handful of decisions that explain why it
is that shape rather than another. Read the first section if you only
want to know where things are; the rest is the reasoning, and it is where
the surprises live.

- [The short version](#the-short-version)
- [The map](#the-map)
- [Decision: a note has two representations, on purpose](#decision-a-note-has-two-representations-on-purpose)
- [Decision: write to a copy, then to disk, then to the screen](#decision-write-to-a-copy-then-to-disk-then-to-the-screen)
- [Decision: the rules layer knows nothing about Android](#decision-the-rules-layer-knows-nothing-about-android)
- [Decision: no ViewModel, no repository, no injection](#decision-no-viewmodel-no-repository-no-injection)
- [Decision: `org.json`, and the trap that comes with it](#decision-orgjson-and-the-trap-that-comes-with-it)
- [The desktop is the specification](#the-desktop-is-the-specification)
- [Where this will hurt next](#where-this-will-hurt-next)

## The short version

One Activity, no fragments, no navigation library: the whole app is
Compose, and which screen you are looking at is a variable in `App()`.
Below that sit four files of plain Kotlin that know the rules — what a
note is, how a table is written down, what a share code contains — and
nothing about Android. That split is why 221 of the tests run on a laptop
in seconds, with no emulator anywhere.

There is no database, no network, no account and no background service.
The app reads and writes the same JSON files the desktop app reads and
writes, in the app's own folder on the device.

## The map

About 4,800 lines of Kotlin, in seven files that matter.

| File | Lines | What lives there |
|---|---|---|
| `MainActivity.kt` | 1,850 | Every screen, as 18 composables: the list, a note, the editor, the trash, the share and import panels, the format bar, the cell editor. Holds the app's state |
| `Notebook.kt` | 1,665 | The `Note` model and everything about reading and writing the notebook: load, create, update, trash, restore, checklists, categories, sorting, search |
| `Tables.kt` | 494 | The table grammar — parsing pipe rows into a grid, laying one back out, and editing it (`TableEdit`) |
| `Styling.kt` | 241 | Format spans over a selection: what styles cover it, toggling, setting and clearing them |
| `TableView.kt` | 173 | The grid, drawn |
| `Sharing.kt` | 171 | Share codes: Base64 of zlib of JSON, and the limits that make a pasted one safe to touch |
| `RichText.kt` | 100 | A note plus its spans, turned into a Compose `AnnotatedString` |

The tests mirror it: `NotebookTest` (110 checks), `TablesTest` (47),
`StylingTest` (23), `IdentityTest` (20), `SharingTest` (19),
`InteropTest` (2).

## Decision: a note has two representations, on purpose

This is the one that explains the most code.

`Notebook.load()` returns a `Load` holding **both** a `List<Note>` — the
model the screen draws — and `raw: JSONArray`, the array exactly as it
was parsed from the file.

`Note` deliberately reads only what the screen needs. It does not carry
`attachments`, `exceptions` or `overrides`, and it cannot carry a field
that a future desktop version adds and this app has never heard of.

So a write **never rebuilds the file from `Note`**. Serialising that
model back would silently delete the parts it never read — from every
note in the file, on the first save, invisibly. Writes work on `raw`:
they append to the array that was read, or replace one entry inside it,
and leave every other byte alone.

The rule this comes from is the desktop's, and it is written out in
[DATA_FORMAT.md](DATA_FORMAT.md): *a partial reader must not become a
total writer.*

## Decision: write to a copy, then to disk, then to the screen

Every write in `Notebook.kt` follows the same three steps:

1. build a **candidate** — a copy of the array, with the change applied;
2. write the candidate to a sibling `.tmp`, `sync()` it, rename it over
   the real file;
3. only now update the in-memory array the screen is reading.

If step 2 fails, the screen never showed a change the file does not have,
and the failure comes back as a `SaveResult.Failed` carrying a reason to
show — not as a save that looked like it worked.

Two traps live here, and both have drawn blood:

- **Copying a `JSONArray` copies references.** The candidate holds the
  same note objects as the live array, so editing a note "in the copy"
  edits what is on the screen. A note being changed must be rebuilt from
  its own text first. See the [issues log](ISSUES_LOG.md#an-update-that-reached-the-screen-before-it-reached-the-file).
- **The file may exist and not parse.** Then `raw` is null and every
  write refuses, rather than replacing a file it could not understand
  with one it invented.

## Decision: the rules layer knows nothing about Android

`Notebook.kt`, `Tables.kt`, `Styling.kt` and `Sharing.kt` contain **zero**
`import android` and **zero** `import androidx`. This is not a
coincidence to be preserved by good intentions; it is the same rule the
desktop keeps between its `core/` layer and tkinter, and it buys two
things:

- the tests for those four files run on the JVM, with no device and no
  emulator — which is why there are 221 of them and why running them
  costs seconds;
- the rules can be read, and compared against the Python they were ported
  from, without Compose in the way.

`RichText.kt` and `TableView.kt` are the deliberate exceptions: they
exist to turn rules into pixels, so they import Compose and are checked
on the emulator instead.

## Decision: no ViewModel, no repository, no injection

`App()` holds the whole of the app's state in a handful of
`remember { mutableStateOf(...) }` values — which note is open, whether
the editor is up, whether the trash is showing — and passes callbacks
down. There is no ViewModel, no DI container, no repository interface and
no abstraction over the file.

At 4,800 lines and one screen at a time, those layers would be ceremony:
every one of them exists to decouple things that are not currently
coupled. The cost is named rather than hidden — see below — and the day
the app survives process death, or writes from more than one place at
once, is the day this stops being the right answer.

## Decision: `org.json`, and the trap that comes with it

The notebook is parsed with `org.json`, which **ships inside Android** —
so the whole app has no JSON dependency at all.

The trap: the SDK jar that JVM unit tests compile against contains only
*stubs* of those classes, and every one of them throws "not mocked" at
runtime. Without a real implementation on the test classpath, every test
that parses a note fails for a reason that has nothing to do with the
code being tested. Hence the single line in `app/build.gradle.kts`:

    testImplementation("org.json:json:20240303")

Test scope only. The app itself keeps using the platform's own.

## The desktop is the specification

Where a rule here answers *"what should this do?"*, the answer comes from
reading the desktop app's Python — including where the Python is arguably
the worse choice, because a difference between the two programs is a
surprise rather than a feature. The one deliberate exception, and the
reason for it, is in [STATUS.md](STATUS.md).

That agreement is not left to memory. `tools/check_note_shape.py` reads
the desktop's source **as the specification** and compares four things:
that a note here has the same fields the desktop gives one, that the two
places building a new note agree, that a share code carries what the
desktop's codes carry, and that an arriving note replaces what the
desktop lets one replace. It reads that source and never writes to it —
the dependency runs one way, always.

## Where this will hurt next

Named here so nobody has to discover them by surprise:

- **The file is read and written on the UI thread.** Fine for half a
  megabyte; not fine for an app that synchronises, and not fine on a slow
  phone with a large notebook.
- **State does not survive process death.** Compose's `remember` is not
  `rememberSaveable`, and nothing is persisted about where you were.
- **`MainActivity.kt` is 1,850 lines.** It is one file because every
  screen shares the same state and the same callbacks; the moment a
  screen needs state the others do not, that is the seam to split on —
  not before, and not by line count.
- **One screen size and one theme have been seen.** There is no light
  theme, no tablet layout, and no landscape work.
