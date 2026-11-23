# Driving the emulator

Small scripts that use the Android emulator the way a person would — tap
a button, type a share code, photograph a screen — so a change can be
proved on the running app rather than only in a test.

The rule they serve is in [CLAUDE.md](../../CLAUDE.md): anything provable
mechanically is proved on the real app before it is called done, and the
judgement calls a script cannot make are named out loud instead. These
are the hands that do the first half.

## The tools

Each one is kept because it survives a specific way the emulator lies to
a script. The first two were written during the identity work and lived
in a temporary folder until 2026-09-13, which meant the next session
would have written them again; the third came with the documentation
pictures on 2026-09-14; the walk came with Phase 1, on 2026-10-15;
`emulator_only.py`, the door the others send adb through, on 2026-10-16;
the group runner on 2026-11-12; the walk for older Androids on
2026-11-13; and the measure of a large notebook on 2026-11-23.

| Tool | What it does | Why it exists |
|---|---|---|
| `walk.py <folder>` | waits for an emulator just started to settle — it is busy for a minute and more after it says it has started ([why](../../docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)) — installs the debug build, starts from nothing (`pm clear`) and walks the app the way a person would — [its parts and checks](../../qa/README.md#the-emulator-walk), upright and on its side, at larger letters and on screens from a small phone's to a 14-inch tablet's, with a keyboard's copy and paste keys where the selection menu cannot be pressed — reading the notebook the app wrote through `run-as` after each step, and photographing the screens. `walk.py <folder> <part> [<part> ...]` walks the parts named alone, on the same road |  a suite proves the code does what it says; this proves the built app does it on a phone, on the screen **and** in the file. What it checks: [qa/README.md](../../qa/README.md#the-emulator-walk) |
| `emulator_only.py` | the one door every script here sends adb through: it picks the running emulator, honours `ANDROID_SERIAL` only when that names an emulator, and refuses a real phone outright (an emulator says `ro.kernel.qemu` or `ro.boot.qemu` is 1; a phone says 0 or nothing) | these scripts clear the app's data, write notebooks into it and install builds with mutations in them; with a person's own phone plugged in beside the emulator, a script that picked "the device" could do all of that to their notes |
| `tap_by_text.py "Some label"` | dumps the live UI tree, finds the node whose text contains the label, taps its centre | tapping remembered coordinates lands on whatever moved into that spot; the dump is taken FRESH every time |
| `walk_groups.py` | walks the whole walk in groups of ten parts, each on an emulator it starts cold, on its own port and without a window, and stops after; a group whose emulator, not the app, got stuck is walked once more. It writes one summary and an exit code that is 0 only when every part was walked and every check passed. `walk_groups_selftest.py` proves those promises against a fake emulator, adb and walk, and `--mutants` breaks each on purpose | a whole walk on one emulator lost it twice in five runs; the runner uses and stops only the emulator it started ([how it is run](../../qa/README.md#the-emulator-walk)) |
| `take_screenshots.py` | writes a notebook made for the camera straight into the app's own storage, walks twelve screens — three of them on its side, one with the keyboard up to write in a note — and photographs each one, then the same screens again in a dark theme (`NN-name-dark.png`). With `--gif`, the README's `demo.gif` instead: ten frames on the same notebook, a note written in it and read back from the file | the pictures in [docs/screenshots/](../../docs/screenshots/) are remade by one command instead of by hand, and each shot names the words that must be on screen — and those that must be gone — before the shutter opens, waiting up to ten seconds for them; a screen that does not say them is not photographed |
| `type_code.py <file> <x> <y>` | taps the field at (x, y), types the file's contents in 100-character pieces, then dumps the UI and compares what arrived against the original | `adb shell input text` truncated a 634-character share code at 181 — silently. A code cut short is indistinguishable from a damaged one, so the typing is verified rather than assumed |
| `older_android.py <folder> --avd <name> [--avd ...]` | on each emulator named, started cold and stopped after: the first start (and that no keyboard opens), a note written and read back, the calendar, a dark theme, the phone turned, the share sheet, the reminders page, the system's file picker, a restart, with Android's crash log read for the app after every step ([older Androids](#older-androids)) | the app says it runs from Android 8.0 and had run on 16 and 17 only; the whole walk reads the Android it was written on and loses its way on 8. Its first run found the search box's keyboard opening at every start on Android 8 |
| `big_notebook.py <folder> [<notes> ...]` | on an emulator it starts cold and stops after, at 1,000, 5,000 and 10,000 notes (or the sizes named): a notebook generated from a fixed seed written into the app, then two cold starts, fifteen flings down the list, a search typed, the newest note opened and a word typed in it, each read from the app's own frames and the slow messages on its main thread; the memory and the file's size too. It writes `results.json` and a photograph of the list, the search and the open note at each size, which are looked at | the largest real notebook seen so far is half a megabyte; what one ten times that costs, and which part of that grows with the notebook, is [measured](../../docs/ARCHITECTURE.md#a-large-notebook-measured) rather than assumed, and measured again by one command after a change that could move it |

All seven need the SDK's `adb` on the machine (found through
`local.properties`' `sdk.dir`, `ANDROID_HOME` or `ANDROID_SDK_ROOT`,
`%LOCALAPPDATA%\Android\Sdk`, then the `PATH`), and all seven are
Windows-flavoured. None of them reads or writes a real notebook. On the
emulator they write to the app's own storage and to scratch files under
`/sdcard` (its `Download` folder, `ui.xml`, `shot.png`) and `/data/local/tmp`; the walk also switches the
emulator's backup transport for its Google-backup part. On the computer,
`take_screenshots.py` rewrites `docs/screenshots/`, and `walk.py` writes
its photographs into the folder it is given and its temporary files under
`%TEMP%`.

**Start the emulator without a window.** `emulator -avd <name> -no-window`
runs it headless: the scripts see and photograph its screen through
`adb`, and nothing appears on — or rearranges — the desktop of the
machine they run on. Add `-gpu swiftshader_indirect`, drawing in
software: drawn by the graphics card, it fell over mid-walk (exit 139,
2026-10-29). Drawn in software, its launcher never rests, so Android
stops an app sent Home ten seconds later rather than at once; the walk
waits for that stop (`home()` in `walk.py`), never for a fixed time.

## Older Androids

The app is built for Android 8.0 and later (API 26). Its walks run on
Android 17 and it is used on 16; `older_android.py` walks what an older
system could break on 8.0, 11 and 13, as 13 November 2026's run did
(18 of 18 steps on each, after the fix to the keyboard Android 8 opened
at every start; then 19 of 19 on 8.0 the same day, with a step that taps
the search box and finds the keyboard there, since typed text proved
only the focus). Android Lint, run the same day, found no call newer
than API 26 anywhere. For the release, on 21 November 2026, 0.15.0's debug
build walked 19 of 19 on 8.0, 11 and 13, and on 17 beside them; the
photographs were looked at.

The emulators are made from Google's images, one per version, through
the SDK's own tools. Written with slashes, as the SDK's list writes them:
Git Bash rewrites an argument holding semicolons as a list of paths, and
the image is not found.

    sdkmanager.bat "system-images/android-26/google_apis/x86_64"
    avdmanager.bat create avd -n noteorg_api26 -k "system-images;android-26;google_apis;x86_64" -d medium_phone

(`avdmanager` wants the semicolons: give it `MSYS2_ARG_CONV_EXCL="*"` and
`cmd /c`.) Each AVD then takes `noteorg_test`'s screen and memory: 1080 by
2400 at 420 dpi, 2 GB. The images are large -- 3.2 GB for Android 8.0,
3.3 GB for 11, 8.2 GB for 13 -- so they are removed when the walk is done
(`sdkmanager.bat --uninstall` with the same names, and `avdmanager.bat
delete avd -n <name>`), and made again the next time.

## One heavy job at a time, and the emulator stopped after it

The emulator is a heavy job: two gigabytes and more of the computer's
memory while it runs. So it runs alone — never beside a Gradle build, a
mutant run or the desktop's suite — and it is stopped as soon as the walk
is done, photographs looked at or not:

    adb emu kill

Every adb call these scripts make has a time limit (`emulator_only.run`:
two minutes a call, ten for an install). An emulator that stops answering
— frozen, or starved by a computer out of memory — stopped a script on a
call that never returned, the emulator still up and holding its memory.
Past the limit the script stops, naming the call and saying to stop the
emulator first ([why](../../CLAUDE.md#one-heavy-job-at-a-time-and-nothing-left-running-after-it)).

The walk takes the machine's one turn itself, as it starts: the desktop's
guard (`tools/guest.py`, through `tools/desktop_guest.py`), which every run
of the desktop's suites takes too. A walk started while one of them runs
waits for it, saying whose turn it is, before it installs or taps
anything; refused the turn, it stops with exit 75 and says nothing was
walked -- stop the emulator then. Taken where the desktop's code was first
run (the export part), the walk ran sixteen parts beside a suite before it
waited. From the start the desktop's code the walk runs is under its memory
ceiling, and APPDATA and LOCALAPPDATA name empty folders of the run's own:
the path to adb is read before that, at import. Parts named after the
folder (`walk.py <folder> cell_menu long_lines`) are walked alone on the
same road, and a part called from a script of one's own takes the turn at
its first adb call: taken only by the whole walk, such a part walked
beside whatever else ran. `big_notebook.py` takes the turn too, once
its emulator has started: started under the turn's memory ceiling, which
a process started after it inherits, the emulator ended without a word.
The screenshot script and the small helpers take no turn; keep them to
one at a time by hand.

## Four traps, two of which these tools remove for themselves

The first two are handled inside the tools (the walk turns the stylus
panel off on every run; `emulator_only.adb()` sets `MSYS_NO_PATHCONV=1`,
and the tools' other adb calls, made from Python, never pass through Git
Bash's path conversion); they still apply to adb typed by hand. The last
two apply everywhere.

**A fresh emulator's stylus tutorial eats typed text.** A new Android
image opens a full-screen *"Try out your stylus"* panel the first time a
text field takes focus, and it swallows `adb shell input text` — the
words land in the tutorial's own box and the app looks broken. Turn it
off before a first run by hand:

    adb shell settings put secure stylus_handwriting_enabled 0
    adb shell settings put secure stylus_handwriting_default_value 0

When scripted input seems ignored, photograph the screen before
debugging the code: the first look answered this one.

**Git Bash rewrites Android paths.** `adb push file /sdcard/...` becomes
`C:/Programs/Git/Git/sdcard/...`, and the push fails with
`secure_mkdirs()` or lands where nobody looks: MSYS converts any argument
that looks like a Unix path. Export `MSYS_NO_PATHCONV=1` for the whole
script — every `adb shell`, `push` and `pull` in it needs it.

**PowerShell's `>` writes text, never binary.** `adb exec-out screencap
-p > shot.png` prepends a byte-order mark and rewrites line endings inside
the PNG, and the failure looks like a broken screenshot rather than a
broken pipe. Take the picture on the device and copy the finished file:

    adb shell screencap -p /sdcard/shot.png
    adb pull /sdcard/shot.png .

The same goes for any binary a tool writes to standard output — an APK,
a zip, a binary `logcat`: let the tool write its own file.

**A launcher hung into the quick-boot snapshot hangs at every start.**
The emulator saves `default_boot` as it is stopped, and starts from it
next time. Stopped once while its launcher was hung (started just after
the Linux image's run, 2026-11-08), it brought back "Pixel Launcher isn't
responding" at every start, over a part walked alone. Started once
without the snapshot, it was gone:

    emulator -avd noteorg_test -no-snapshot-load -no-window -gpu swiftshader_indirect

The walk answers another app's "isn't responding" with Wait and says so
in its output (`nodes()` in walk.py), so a hung launcher no longer fails a
check of an app that is fine; the same dialog about this app is left on
the screen for the checks to fail on. Many such lines in a run's output
mean the emulator needs that start without its snapshot.

## Two things the UI dump does not say, and three the keyboard does

**A node scrolled out of sight is still in the tree.** Only its box, cut
to what shows, says it is hidden — a title scrolled above its pane was
in the dump at 11 pixels tall. Check a box, not a count, when "seen"
matters.

**A button may say one thing and show another.** `✕` is said "Close
find": read a node's description as well as its text.

**On a phone on its side the keyboard covers most of the screen.**
Positions read before it rose land on its keys after; put it down
before tapping what was under it.

And one thing the keyboard does to what is typed: in a field that
capitalises sentences, `input text` sometimes arrives with a capital at
the start and sometimes not, run to run. Start such text with its
capital already, or compare in lower case.

And one thing it says late: `mInputShown` in `dumpsys input_method`
stays true for a moment after Back has put the keyboard down, over a
second on an emulator started that day. Ask until it says false before
asking again, or the second Back leaves the screen
([the story](../../docs/ISSUES_LOG.md#a-check-that-held-by-one-frame)).

The other traps met on this machine -- the Windows console, org.json on
the computer -- are in [CLAUDE.md](../../CLAUDE.md).
