"""The app on the older Androids it says it runs on: what leans on the system, walked short.

    py -I tools/emulator/older_android.py <folder> --avd noteorg_api26 [--avd noteorg_api30 ...] [--apk APK]

The whole walk (walk.py) is written for the Android it runs on -- its
reads of dumpsys, the keyboard's insets, key combinations -- and on
Android 8 it lost its way in the tool, not the app. This walks what an
older Android could break: the first start (and that it opens no
keyboard), writing and saving a note, the calendar, a dark theme, the
phone turned, the share sheet, the reminders page, the system's file
picker, a restart; through the walk's own plain roads (tap by words,
type, read the file back), and with Android's crash log read for this
app after every step.

Each AVD named is started cold, without a window, on walk_groups' port,
walked, and stopped however its walk ends; each writes <folder>/<avd>/
(walk.out and the photographs, which are looked at). Exit code 0 only
when every AVD was walked and every step passed. The older system images
are large (Android 13's is 8 GB): tools/emulator/README.md says how they
are made and removed.
"""
import argparse
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)


def walk_one(apk, out):
    """The steps, on the emulator ANDROID_SERIAL names."""
    import walk
    from walk import adb, check, read_file, says_soon, screen_says, shot, tap, type_text, back_to
    pkg = walk.PACKAGE

    def crashed(step):
        mine = [l for l in adb("shell", "logcat", "-b", "crash", "-d").splitlines() if pkg in l]
        check("no crash of the app after %s" % step, not mine, " | ".join(mine)[:400])

    print("== Android API", adb("shell", "getprop", "ro.build.version.sdk").strip(), flush=True)
    walk.install(apk)
    adb("logcat", "-b", "crash", "-c")
    adb("shell", "pm", "clear", pkg)
    walk.start_app()
    walk.check_settled("a new notebook holds the welcome note", lambda: read_file("notes_data.json"),
                       lambda notes: isinstance(notes, list) and len(notes) == 1
                       and notes[0].get("title", "").startswith("Welcome"), within=15.0, first=0.0)
    shot(out, "01-start", "Welcome to Note Organizer", "Search notes")
    time.sleep(2.0)
    # Android 8 gave the search box the focus as the window started, and its
    # keyboard covered the list's buttons at every start.
    check("the app starts without the keyboard", "mInputShown=true" not in adb("shell", "dumpsys", "input_method"))
    crashed("the first start")

    tap("+", exact=True, wait=1.5)
    check("+ opens an empty editor", not says_soon("Title"), str(screen_says("Title")))
    tap("Title")
    type_text("Groceries")
    tap("Write your note")
    type_text("Milk and eggs")
    time.sleep(1.5)
    shot(out, "02-editor", "Groceries", "Milk and eggs")
    back_to("Search notes")
    written = [n for n in read_file("notes_data.json") or [] if n.get("title") == "Groceries"]
    check("a typed note reaches the file", bool(written) and written[0].get("body") == "Milk and eggs")
    crashed("writing a note")

    tap("Calendar", exact=True)
    month = time.strftime("%B")
    check("the calendar opens on this month", not says_soon(month), str(screen_says(month)))
    shot(out, "03-calendar", month)
    crashed("the calendar")

    tap("Settings", exact=True)
    tap("Appearance")
    tap("Dark", exact=True)
    time.sleep(2.0)
    shot(out, "04-dark", "Light", "Dark")
    theme = (read_file("settings.json") or {}).get("theme", "")
    check("the dark theme is kept", theme.startswith(("dark", "midnight")), theme)
    tap("Light", exact=True)
    time.sleep(1.5)
    crashed("the themes")

    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(3.0)
    shot(out, "05-on-its-side", "Appearance")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(3.0)
    crashed("turning the phone")

    back_to("Appearance", "Your name")
    tap("Notes", exact=True)
    walk.open_note("Groceries")
    tap("Share", exact=True)       # the editor's top bar, not its menu
    time.sleep(1.5)
    check("Share offers ways to send the note", not says_soon("Share this note as", "Share code"),
          str(screen_says("Share this note as", "Share code")))
    shot(out, "06-share", "Share this note as")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.0)
    back_to("Search notes")
    crashed("the share sheet")

    tap("Settings", exact=True)
    tap("Reminders")
    time.sleep(1.0)
    shot(out, "07-reminders", "Reminders")
    crashed("the reminders page")
    back_to("Your name")

    tap("Backup")
    tap("Save a backup")
    time.sleep(3.0)
    shot(out, "08-picker")
    for word in ("SAVE", "Save"):
        if not screen_says(word):
            tap(word, exact=True)
            break
    time.sleep(4.0)
    listing = adb("shell", "ls", "/sdcard/Download")
    check("the system's picker saves the backup", ".zip" in listing, listing.strip()[:200])
    crashed("the file picker")

    adb("shell", "am", "force-stop", pkg)
    time.sleep(1.5)
    walk.start_app()
    check("a restart keeps the note", not says_soon("Groceries"), str(screen_says("Groceries")))
    shot(out, "09-restarted", "Groceries")
    crashed("the restart")

    adb("shell", "pm", "clear", pkg)
    print("\n%d step(s) failed%s" % (len(walk.failures), (": " + "; ".join(walk.failures)) if walk.failures else "."))
    return 1 if walk.failures else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("folder")
    parser.add_argument("--avd", action="append", default=[])
    parser.add_argument("--apk", default=None)
    parser.add_argument("--walk", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args()
    import walk_groups as wg
    apk = args.apk or os.path.join(os.path.dirname(os.path.dirname(HERE)), "app", "build", "outputs",
                                   "apk", "debug", "app-debug.apk")
    if args.walk:
        return walk_one(apk, args.folder)
    if not args.avd:
        parser.error("name at least one --avd")
    wrong = []
    for avd in args.avd:
        folder = os.path.join(args.folder, avd)
        os.makedirs(folder, exist_ok=True)
        proc = None
        try:
            proc = wg.boot(avd, os.path.join(folder, "emulator.log"))
            if proc is None:
                wrong.append("%s: the emulator did not start" % avd)
                continue
            env = dict(os.environ, ANDROID_SERIAL=wg.SERIAL, PYTHONIOENCODING="utf-8")
            with open(os.path.join(folder, "walk.out"), "w", encoding="utf-8") as log:
                code = subprocess.run([sys.executable, "-I", os.path.abspath(__file__), folder, "--walk",
                                       "--apk", apk], env=env, stdout=log, stderr=subprocess.STDOUT,
                                      timeout=2400).returncode
            last = open(os.path.join(folder, "walk.out"), encoding="utf-8").read().strip().splitlines()[-1:]
            print("%s: %s" % (avd, last[0] if last else "no output"), flush=True)
            if code:
                wrong.append(avd)
        except subprocess.TimeoutExpired:
            wrong.append("%s: timed out" % avd)
        finally:
            wg.stop(proc, avd)
    print("every AVD walked, every step passed" if not wrong else "not passed: %s" % "; ".join(wrong))
    return 1 if wrong else 0


if __name__ == "__main__":
    sys.exit(main())
