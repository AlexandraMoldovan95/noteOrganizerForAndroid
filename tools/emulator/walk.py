"""Walk the app on the emulator the way a person would, and read the FILE.

A suite proves the code does what it says; this proves the built app does
it on a phone. Each step presses what a person presses -- found by its
words on the screen, fresh every time -- and then reads the notebook the
app wrote, through `run-as`, which a debug build allows: the screen draws
what the program believes, the file is what it did.

It starts from nothing (`pm clear`), so the first screen is a brand-new
notebook's, and it writes only inside the app's own storage on the
emulator. Photographs are of the emulator's screen alone, saved in the
folder given.

    py tools/emulator/walk.py <photos folder>

Needs: a running emulator, and the debug APK built
(gradlew assembleDebug). Exit code 1 when any step's check fails.
"""
import io
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")

ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")
PACKAGE = "ro.noteorganizer.app"
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(os.path.dirname(HERE))
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
NOTEBOOK = "files/notebook"

failures = []


def adb(*args, binary=False, check=False):
    out = subprocess.run([ADB, *args], capture_output=True)
    if check and out.returncode != 0:
        raise RuntimeError("adb %s: %s" % (" ".join(args), out.stderr.decode(errors="replace")))
    return out.stdout if binary else out.stdout.decode("utf-8", errors="replace")


def wait_for_boot(limit=300):
    start = time.time()
    while time.time() - start < limit:
        if adb("shell", "getprop", "sys.boot_completed").strip() == "1":
            return True
        time.sleep(3)
    return False


def nodes():
    """The screen's UI tree, fresh: (text, description, centre) for each node."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for node in root.iter("node"):
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if len(bounds) != 4:
            continue
        x1, y1, x2, y2 = map(int, bounds)
        out.append((node.get("text", ""), node.get("content-desc", ""), ((x1 + x2) // 2, (y1 + y2) // 2)))
    return out


def find(label, exact=False):
    for text, desc, centre in nodes():
        for value in (text, desc):
            if value and ((value == label) if exact else (label in value)):
                return centre
    return None


def tap(label, exact=False, wait=1.0):
    at = find(label, exact)
    if at is None:
        fail("tap %r: not on the screen" % label)
        return False
    adb("shell", "input", "tap", str(at[0]), str(at[1]))
    time.sleep(wait)
    return True


def long_press(label):
    at = find(label)
    if at is None:
        fail("long press %r: not on the screen" % label)
        return False
    adb("shell", "input", "swipe", str(at[0]), str(at[1]), str(at[0]), str(at[1]), "900")
    time.sleep(1.0)
    return True


def type_text(text):
    # `input text` takes %s for a space and nothing else that needs care
    # for the plain words written here.
    adb("shell", "input", "text", text.replace(" ", "%s"))
    time.sleep(0.6)


def back(wait=1.0):
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(wait)


def back_to(*words, tries=4):
    """Back until the screen says these words. The first Back with the
    keyboard up only closes the keyboard -- a phone's habit, and the reason
    a single Back left an earlier run still inside the editor."""
    for _ in range(tries):
        if not screen_says(*words):
            return True
        back()
    return not screen_says(*words)


def screen_says(*words):
    seen = " | ".join(t or d for t, d, _ in nodes())
    missing = [w for w in words if w not in seen]
    return missing


def check(what, ok, detail=""):
    print(("PASS " if ok else "FAIL ") + what + (("  -- " + detail) if detail and not ok else ""))
    if not ok:
        failures.append(what)


def fail(what):
    print("FAIL " + what)
    failures.append(what)


def shot(folder, name, *must_say):
    """A photograph of the emulator's screen -- only when it says what it should."""
    missing = screen_says(*must_say)
    if missing:
        fail("photo %s: the screen does not say %s" % (name, missing))
        return
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    data = adb("exec-out", "cat", "/sdcard/shot.png", binary=True)
    with open(os.path.join(folder, name + ".png"), "wb") as f:
        f.write(data)
    print("     photo %s.png" % name)


def read_file(name):
    data = adb("exec-out", "run-as", PACKAGE, "cat", "%s/%s" % (NOTEBOOK, name), binary=True)
    try:
        return json.loads(data.decode("utf-8"))
    except ValueError:
        return None


def start_app():
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(3)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    folder = sys.argv[1]
    os.makedirs(folder, exist_ok=True)
    if not wait_for_boot():
        print("The emulator did not finish starting.")
        return 2
    # Off, once: the stylus tutorial swallows typed text on a fresh image.
    adb("shell", "settings", "put", "secure", "stylus_handwriting_enabled", "0")
    print("installing", APK)
    adb("install", "-r", APK, check=True)

    # ---- a brand-new notebook ----
    adb("shell", "pm", "clear", PACKAGE)
    start_app()
    notes = read_file("notes_data.json")
    check("a new notebook holds the welcome note",
          isinstance(notes, list) and len(notes) == 1 and notes[0].get("title", "").startswith("Welcome"))
    settings = read_file("settings.json") or {}
    check("the welcome note is remembered", settings.get("welcome_offered") is True and bool(settings.get("welcome_note_uid")))
    shot(folder, "01-new-notebook", "Welcome to Note Organizer", "Search notes")

    tap("Welcome to Note Organizer")
    shot(folder, "02-welcome-note", "Welcome to Note Organizer", "Write")
    back_to("Search notes")

    # ---- writing a note ----
    tap("+", exact=True, wait=1.5)
    check("+ opens an empty editor", not screen_says("Title"), str(screen_says("Title")))
    tap("Title")
    type_text("Groceries")
    tap("Write your note")
    type_text("milk and eggs")
    time.sleep(1.5)
    shot(folder, "03-editor", "Groceries", "milk and eggs")
    check("Back leaves the editor for the list", back_to("Search notes"))
    notes = read_file("notes_data.json") or []
    written = [n for n in notes if n.get("title") == "Groceries"]
    check("a typed note reaches the file, title and body", bool(written) and written[0].get("body") == "milk and eggs",
          json.dumps(written)[:200])
    if written:
        n = written[0]
        check("it is born with the desktop's whole shape",
              all(k in n for k in ("uid", "moved", "overrides", "exceptions", "recurrence", "paragraphs")))

    # ---- touch and hold: select, pin ----
    long_press("Groceries")
    shot(folder, "04-selection", "1 selected")
    tap("📌")
    notes = read_file("notes_data.json") or []
    check("pinning from the selection reaches the file",
          any(n.get("title") == "Groceries" and n.get("pinned") is True for n in notes))

    # ---- the Trash, and Undo ----
    long_press("Groceries")
    tap("🗑", wait=0.8)
    trash = read_file("trash.json") or []
    trashed = any(t.get("title") == "Groceries" for t in trash)
    check("Move to Trash puts the note in trash.json", trashed)
    shot(folder, "05-undo", "Moved to Trash", "Undo")
    tap("Undo", wait=1.5)
    notes = read_file("notes_data.json") or []
    trash = read_file("trash.json") or []
    # Only a note that LEFT can be brought back: without the step above
    # this check passed over a note that never moved.
    check("Undo brings it back, and out of the Trash",
          trashed and any(n.get("title") == "Groceries" for n in notes)
          and not any(t.get("title") == "Groceries" for t in trash))
    check("Undo opens the note it brought back", not screen_says("Groceries", "Auto-saved") or
          not screen_says("Groceries"))
    back_to("Search notes")

    # ---- Settings ----
    tap("Settings", exact=True)
    shot(folder, "06-settings", "Your name", "Appearance", "Backup")
    tap("Appearance")
    shot(folder, "07-appearance", "Follow the phone", "Midnight Purple", "Soft Lavender")
    tap("Follow the phone", wait=1.0)
    settings = read_file("settings.json") or {}
    check("turning Follow the phone off is saved", settings.get("phone_theme_follows_phone") is False)
    tap("Ocean Blue", wait=1.5)
    settings = read_file("settings.json") or {}
    check("a theme picked with following off is the desktop's own key", settings.get("theme") == "dark_ocean")
    shot(folder, "08-ocean-blue", "Ocean Blue")
    back_to("Your name", "Appearance")
    tap("Your name")
    tap("Name", exact=True)
    type_text("Maria")
    tap("Save", exact=True)
    settings = read_file("settings.json") or {}
    check("the name is saved under the desktop's key", settings.get("username") == "Maria")
    # The keyboard covers the bar at the bottom: down first. And the page
    # itself says "Hi Maria," in its explanation, so the greeting is looked
    # for together with the list's own search box.
    back()
    tap("Notes", exact=True)
    check("the list greets by name", not screen_says("Hi Maria,", "Search notes"),
          str(screen_says("Hi Maria,", "Search notes")))
    shot(folder, "09-greeting", "Hi Maria,", "Search notes")

    seeded(folder)
    outward(folder)
    changes(folder)

    print()
    if failures:
        print("%d step(s) failed." % len(failures))
        return 1
    print("Every step did what it should, on the phone and in the file.")
    return 0


# ---- the second walk: a notebook written for the camera ----

DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")

CAMERA = [
    {"id": 1, "uid": "walk-bread", "title": "Bread",
     "body": "500 g flour\n300 ml warm water\n10 g salt\n\nKnead ten minutes, rise two hours, "
             "bake 45 minutes at 220 degrees.",
     "category": "Personal",
     "formatting": [{"start": 0, "end": 11, "style": "bold"}],
     "checklist": [{"text": "flour", "done": True}, {"text": "yeast", "done": False}],
     "created": "2026-10-10 09:00:00", "updated": "2026-10-10 09:30:00"},
    {"id": 2, "uid": "walk-budget", "title": "October budget",
     "body": "What went where:\n| Item | Cost |\n| --- | ---: |\n| rent | 1500 |\n| power | 180 |",
     "category": "Work", "created": "2026-10-09 18:00:00", "updated": "2026-10-09 18:20:00"},
    {"id": 3, "uid": "walk-rescued", "title": "Meeting notes",
     "body": "",
     "category": "Work",
     "_prev": {"title": "Meeting notes", "body": "Decisions: ship on Friday; call the printer.",
               "formatting": [], "paragraphs": [], "comments": ""},
     "created": "2026-10-08 10:00:00", "updated": "2026-10-11 08:00:00"},
]


def push_json(name, value):
    local = os.path.join(os.environ.get("TEMP", "."), "walk_" + name)
    with io.open(local, "w", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
    adb("push", local, "/sdcard/Android/data/%s/files/%s" % (PACKAGE, name))
    os.remove(local)


def desktop_code(note):
    """A share code made by the DESKTOP's own encoder, when it is here."""
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.sharing import encode_share_code
        return encode_share_code([note])
    except Exception as problem:  # noqa: BLE001 -- the walk goes on without it
        print("     (the desktop's encoder could not be used: %s)" % problem)
        return None


def type_long(text):
    """`input text` cuts long text short; typed in pieces instead."""
    for i in range(0, len(text), 80):
        adb("shell", "input", "text", text[i:i + 80])
        time.sleep(0.3)


def note_by_uid(uid):
    for n in read_file("notes_data.json") or []:
        if n.get("uid") == uid:
            return n
    return None


def seeded(folder):
    print("\n-- a notebook copied in, written for the camera --")
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "mkdir", "-p", "/sdcard/Android/data/%s/files" % PACKAGE)
    push_json("notes_data.json", CAMERA)
    push_json("settings.json", {"categories": {"General": "#7c5cff", "Work": "#4cc9f0",
                                               "Personal": "#f9c74f"}, "username": "Maria"})
    start_app()
    notes = read_file("notes_data.json") or []
    check("the older folder's notebook is copied in, and no welcome note is added",
          [n.get("uid") for n in notes] == ["walk-bread", "walk-budget", "walk-rescued"])
    shot(folder, "10-copied-in", "Bread", "October budget", "Meeting notes", "brought into")

    # A table, edited from one cell.
    tap("October budget", wait=1.5)
    shot(folder, "11-table", "October budget", "rent", "1500")
    tap("1500")
    if find("Cell") is not None:
        # Into the field first: a key pressed with nothing focused goes
        # nowhere, which an earlier run took for a failed save.
        tap("Cell", exact=True, wait=0.8)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        for _ in range(4):
            adb("shell", "input", "keyevent", "KEYCODE_DEL")
        type_text("1600")
        tap("Save", exact=True, wait=1.5)
    time.sleep(1.5)
    budget = note_by_uid("walk-budget") or {}
    check("a cell edited on the phone is in the file, the table still a table",
          "| rent |" in budget.get("body", "") and "1600" in budget.get("body", "")
          and "1500" not in budget.get("body", ""), budget.get("body", "")[:160])
    back_to("Search notes")

    # The checklist.
    tap("Bread", exact=True, wait=1.5)
    tap("☐")
    time.sleep(1.0)
    bread = note_by_uid("walk-bread") or {}
    check("ticking a row reaches the file",
          [r.get("done") for r in bread.get("checklist", [])] == [True, True], str(bread.get("checklist")))
    check("the styling the desktop wrote is still there", bread.get("formatting") == CAMERA[0]["formatting"])
    shot(folder, "12-checklist", "Bread", "flour", "yeast")

    # The details: category, colour.
    tap("ⓘ", wait=1.2)
    shot(folder, "13-details", "Note details", "Category", "Colour")
    tap("Personal ▾")
    tap("Work", exact=True, wait=1.0)
    back()
    time.sleep(1.5)
    back_to("Search notes")
    bread = note_by_uid("walk-bread") or {}
    check("a category chosen in the details is saved", bread.get("category") == "Work", bread.get("category"))

    # Restore previous.
    tap("Meeting notes", wait=1.5)
    shot(folder, "14-restore-banner", "An earlier version", "Restore previous")
    tap("Restore previous")
    tap("Restore", exact=True, wait=1.5)
    meeting = note_by_uid("walk-rescued") or {}
    check("Restore previous brings the text back and spends the copy",
          meeting.get("body", "").startswith("Decisions") and "_prev" not in meeting, str(meeting)[:200])
    back_to("Search notes")

    # Categories: add one, rename one, and the notes follow.
    tap("Settings", exact=True)
    tap("Categories")
    tap("Work", exact=True)
    shot(folder, "15-category-sheet", "Name", "Colour", "Delete")
    tap("Name", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(4):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    type_text("Job")
    tap("Save", exact=True, wait=1.5)
    notes = read_file("notes_data.json") or []
    settings = read_file("settings.json") or {}
    check("a renamed category reaches every note filed under it",
          all(n.get("category") != "Work" for n in notes) and
          sum(1 for n in notes if n.get("category") == "Job") == 3)
    check("and the category list", "Job" in settings.get("categories", {}) and "Work" not in settings.get("categories", {}))
    back_to("Your name")

    # A share code the desktop made, typed into the import panel.
    code = desktop_code({"uid": "from-desktop", "title": "From the desktop", "body": "made there",
                         "category": "General"})
    if code:
        back_to("Search notes")
        tap("Notes", exact=True)
        tap("⋮")
        tap("Import a code")
        tap("A code starts with")
        type_long(code)
        tap("Import", exact=True, wait=2.0)
        check("a code the desktop made is read and added", note_by_uid("from-desktop") is not None)

    # Trash, from Settings.
    back_to("Search notes")
    long_press("From the desktop") if find("From the desktop") else long_press("Bread")
    tap("🗑", wait=1.0)
    tap("Settings", exact=True)
    tap("Trash")
    shot(folder, "16-trash", "Kept for", "days left")
    trash = read_file("trash.json") or []
    if trash:
        tap(trash[-1].get("title") or "x")
        tap("Restore 1 selected", wait=1.5)
        check("a note restored from the Trash page is back", not (read_file("trash.json") or []))

    # A backup, through Android's own picker.
    back_to("Your name")
    tap("Backup")
    tap("Export a backup", wait=3.0)
    if find("SAVE") is not None or find("Save") is not None:
        tap("SAVE", exact=True, wait=3.0) if find("SAVE") else tap("Save", exact=True, wait=3.0)
    settings = read_file("settings.json") or {}
    check("an export through the picker is remembered as the last", bool(settings.get("last_export_at")))
    listing = adb("shell", "ls", "/sdcard/Download")
    zips = [line.strip() for line in listing.splitlines() if line.strip().endswith(".zip")]
    check("the backup is where the picker put it", bool(zips), listing[:200])
    if zips:
        local = os.path.join(folder, "phone_backup.zip")
        with open(local, "wb") as f:
            f.write(adb("exec-out", "cat", "/sdcard/Download/" + zips[-1], binary=True))
        print("     backup copied to", local)

    # The same backup, imported back: every note is already here, unchanged,
    # so the one question is the desktop's "Already here, unchanged", and
    # skipping it changes nothing.
    before = read_file("notes_data.json")
    back_to("Backup", "Export a backup")
    tap("Import a backup", wait=3.0)
    if zips and find(zips[-1], exact=True) is None:
        tap("Downloads", wait=2.0) if find("Downloads") else None
    # By its exact name: "Preview the file <name>" holds the same words, and
    # a tap there opens a preview instead of choosing the file.
    if zips and tap(zips[-1], exact=True, wait=3.0):
        tap("Continue", exact=True, wait=1.5)
        shot(folder, "17-unchanged-question", "Already here, unchanged", "Add copies anyway")
        tap("Skip the", wait=2.0)
        check("re-importing your own backup changes nothing", read_file("notes_data.json") == before)


def foreground():
    """The package of the activity on top -- the app, the browser, a viewer."""
    out = adb("shell", "dumpsys", "activity", "activities")
    match = re.search(r"mResumedActivity: ActivityRecord\{\S+ \S+ ([\w.]+)/", out) or \
        re.search(r"topResumedActivity=ActivityRecord\{\S+ \S+ ([\w.]+)/", out)
    return match.group(1) if match else ""


def outward(folder):
    """The doors to the rest of the phone, and how the app looks under its settings."""
    print("\n-- the doors to the rest of the phone --")
    # A code shared from another app, as WhatsApp sends it.
    code = desktop_code({"uid": "shared-in", "title": "Shared in", "body": "from a chat",
                         "category": "General"})
    if code:
        adb("shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "text/plain",
            "--es", "android.intent.extra.TEXT", code, "-n", PACKAGE + "/.MainActivity")
        time.sleep(2.5)
        shot(folder, "18-shared-in", "Import a code", "Import")
        tap("Import", exact=True, wait=2.0)
        check("a code shared from another app is imported", note_by_uid("shared-in") is not None)

    # A note shared out: Android's own sheet opens over the app.
    back_to("Search notes")
    tap("Bread", exact=True, wait=1.5)
    tap("🔗", wait=2.5)
    check("sharing a note opens Android's share sheet", foreground() != PACKAGE or find("Share") is not None,
          foreground())
    shot(folder, "19-share-sheet")
    back(1.5)
    back_to("Search notes")

    # Feedback opens the browser on the site's form.
    tap("Settings", exact=True)
    tap("About")
    tap("Send feedback", wait=4.0)
    opened = foreground()
    check("Send feedback opens a browser, not the app", opened not in ("", PACKAGE), opened)
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
    time.sleep(2.0)
    back_to("Your name")
    back()


def changes(folder):
    """The phone changing under an OPEN note: going dark (followed, by
    default), larger letters, turned on its side. Each is a configuration
    change, and each used to rebuild the Activity and drop the person back
    on the list. These were photographs of the LIST once, which is why
    they never noticed."""
    print("\n-- the phone changing under an open note --")
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "cmd", "uimode", "night", "no")
    start_app()
    tap("Welcome to Note Organizer", wait=1.5)

    def still_in_note():
        return not screen_says("Welcome to Note Organizer") and bool(screen_says("Search notes"))

    adb("shell", "cmd", "uimode", "night", "yes")
    time.sleep(2.5)
    check("the phone going dark leaves the note open", still_in_note())
    shot(folder, "20-follows-dark", "Welcome to Note Organizer")
    adb("shell", "settings", "put", "system", "font_scale", "1.3")
    time.sleep(2.0)
    check("larger letters leave the note open", still_in_note())
    shot(folder, "21-large-letters", "Welcome to Note Organizer")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(2.5)
    check("turning the phone leaves the note open", still_in_note())
    shot(folder, "22-landscape-note", "Welcome to Note Organizer")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    adb("shell", "cmd", "uimode", "night", "no")
    time.sleep(2.0)
    back_to("Search notes")
    adb("shell", "settings", "put", "system", "user_rotation", "1")
    time.sleep(2.5)
    shot(folder, "23-landscape", "Search notes")
    adb("shell", "settings", "put", "system", "user_rotation", "0")
    time.sleep(1.5)



if __name__ == "__main__":
    sys.exit(main())
