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


def tap(label, exact=False, wait=1.0, patience=3.0):
    """Tap what says [label] -- looked for again for a moment before giving
    up: the first start after an install drew a note's top bar before its
    text, and a tap that looked once found the text not there yet."""
    at = find(label, exact)
    deadline = time.time() + patience
    while at is None and time.time() < deadline:
        time.sleep(0.4)
        at = find(label, exact)
    if at is None:
        fail("tap %r: not on the screen" % label)
        return False
    adb("shell", "input", "tap", str(at[0]), str(at[1]))
    time.sleep(wait)
    return True


def tap_last(label, wait=1.0):
    """The LAST node whose text is exactly [label]: a dialog's button, where
    its title says the same words above it."""
    hits = [centre for text, desc, centre in nodes() if label in (text, desc)]
    if not hits:
        fail("tap %r: not on the screen" % label)
        return False
    adb("shell", "input", "tap", str(hits[-1][0]), str(hits[-1][1]))
    time.sleep(wait)
    return True


def open_note(title, limit=10.0):
    """Tap a note open and wait for the EDITOR, not for a fixed time. The
    first start after an install is slower, and a step that followed a
    fixed pause once found itself still on the list -- where "⋮" is the
    list's own menu, so the note's was never opened. "ⓘ" is drawn by the
    editor alone."""
    if not tap(title, wait=0.3):
        return False
    deadline = time.time() + limit
    while time.time() < deadline:
        if find("ⓘ", exact=True):
            time.sleep(0.5)
            return True
        time.sleep(0.4)
    fail("open %r: the editor did not appear" % title)
    return False


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
    # The text AND the description: a button drawn as "✕" is said "Close
    # find", and reading only one of them, a check looking for the other
    # never saw it -- and passed or failed on what it could not see.
    seen = " | ".join(part for t, d, _ in nodes() for part in (t, d) if part)
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

    brand_new(folder)
    seeded(folder)
    outward(folder)
    calendar(folder)
    move_day(folder)
    listing(folder)
    statistics(folder)
    calculator(folder)
    finding(folder)
    exporting(folder)
    styles_through_an_edit(folder)
    templates(folder)
    sizes_and_faces(folder)
    lists(folder)
    new_table(folder)
    cell_menu(folder)
    wide(folder)
    changes(folder)

    print()
    if failures:
        print("%d step(s) failed." % len(failures))
        return 1
    print("Every step did what it should, on the phone and in the file.")
    return 0


def brand_new(folder):
    """A brand-new notebook: the welcome note, a note typed, a selection and
    a pin, the Trash and its Undo, Settings' theme and name."""
    print("\n-- a brand-new notebook --")
    adb("shell", "pm", "clear", PACKAGE)
    start_app()
    notes = read_file("notes_data.json")
    check("a new notebook holds the welcome note",
          isinstance(notes, list) and len(notes) == 1 and notes[0].get("title", "").startswith("Welcome"))
    settings = read_file("settings.json") or {}
    check("the welcome note is remembered", settings.get("welcome_offered") is True and bool(settings.get("welcome_note_uid")))
    shot(folder, "01-new-notebook", "Welcome to Note Organizer", "Search notes")

    open_note("Welcome to Note Organizer")
    shot(folder, "02-welcome-note", "Welcome to Note Organizer", "Write")
    back_to("Search notes")

    # ---- writing a note ----
    tap("+", exact=True, wait=1.5)
    check("+ opens an empty editor", not screen_says("Title"), str(screen_says("Title")))
    tap("Title")
    type_text("Groceries")
    tap("Write your note")
    # A capital already: the body starts its sentences with one, and the
    # keyboard applies that to typed-in text on some runs and not on others
    # -- "milk" came back "Milk" once, after many runs that kept it.
    type_text("Milk and eggs")
    time.sleep(1.5)
    shot(folder, "03-editor", "Groceries", "Milk and eggs")
    check("Back leaves the editor for the list", back_to("Search notes"))
    notes = read_file("notes_data.json") or []
    written = [n for n in notes if n.get("title") == "Groceries"]
    check("a typed note reaches the file, title and body", bool(written) and written[0].get("body") == "Milk and eggs",
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
    # In the editor, not on the list: the list says "Groceries" too, and an
    # earlier form of this check passed there.
    check("Undo opens the note it brought back",
          not screen_says("Groceries") and bool(screen_says("Search notes")))
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
    open_note("October budget")
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
    open_note("Meeting notes")
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
    if not code:
        fail("a code the desktop made: the desktop's encoder could not be used, so nothing was tried")
    else:
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
    if not trash:
        fail("a note restored from the Trash page: the delete before it put nothing in the Trash")
    else:
        title = trash[-1].get("title") or "x"
        tap(title)
        tap("Restore 1 selected", wait=1.5)
        # Both halves read back: an unreadable trash.json must not pass as
        # an empty one.
        after = read_file("trash.json")
        notes = read_file("notes_data.json") or []
        check("a note restored from the Trash page is back",
              after == [] and any(n.get("title") == title for n in notes), str(after)[:120])

    # A backup, through Android's own picker. The emulator's Downloads keeps
    # every backup earlier walks made (clearing the app does not reach it),
    # so the one this walk made is the one that was not there before --
    # never "the last name ls prints", which was the OLDEST, at the bottom
    # of the picker's list where a tap can land on the screen's edge.
    def backups():
        listing = adb("shell", "ls", "/sdcard/Download")
        return {line.strip() for line in listing.splitlines() if line.strip().endswith(".zip")}

    earlier = backups()
    back_to("Your name")
    tap("Backup")
    tap("Export a backup", wait=3.0)
    if find("SAVE") is not None or find("Save") is not None:
        tap("SAVE", exact=True, wait=3.0) if find("SAVE") else tap("Save", exact=True, wait=3.0)
    settings = read_file("settings.json") or {}
    check("an export through the picker is remembered as the last", bool(settings.get("last_export_at")))
    made = sorted(backups() - earlier)
    check("the backup is where the picker put it", len(made) == 1, str(made))
    mine = made[0] if len(made) == 1 else None
    if mine:
        local = os.path.join(folder, "phone_backup.zip")
        with open(local, "wb") as f:
            f.write(adb("exec-out", "cat", "/sdcard/Download/" + mine, binary=True))
        print("     backup copied to", local)

    # The same backup, imported back: every note is already here, unchanged,
    # so the one question is the desktop's "Already here, unchanged", and
    # skipping it changes nothing. The question has to be SEEN for that to
    # mean anything: an import that never happened changes nothing too,
    # and this check once passed over exactly that.
    before = read_file("notes_data.json")
    back_to("Backup", "Export a backup")
    tap("Import a backup", wait=3.0)
    if mine and find(mine, exact=True) is None:
        tap("Downloads", wait=2.0) if find("Downloads") else None
    # By its exact name: "Preview the file <name>" holds the same words, and
    # a tap there opens a preview instead of choosing the file.
    asked = False
    if mine and tap(mine, exact=True, wait=3.0):
        tap("Continue", exact=True, wait=1.5)
        asked = not screen_says("Already here, unchanged", "Add copies anyway")
        shot(folder, "17-unchanged-question", "Already here, unchanged", "Add copies anyway")
        tap("Skip the", wait=2.0)
    check("re-importing your own backup asks, and skipping changes nothing",
          asked and read_file("notes_data.json") == before)


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
    if not code:
        fail("a code shared from another app: the desktop's encoder could not be used, so nothing was tried")
    else:
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


LONG_DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
LONG_MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August",
               "September", "October", "November", "December"]


def put_inside(name, value):
    """A file written straight into the app's own notebook folder, through run-as."""
    local = os.path.join(os.environ.get("TEMP", "."), "walk_inside_" + name)
    with io.open(local, "w", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
    staging = "/data/local/tmp/walk_" + name
    adb("push", local, staging)
    os.remove(local)
    adb("shell", "chmod", "644", staging)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", NOTEBOOK)
    adb("shell", "run-as", PACKAGE, "cp", staging, "%s/%s" % (NOTEBOOK, name))
    adb("shell", "rm", "-f", staging)


def seed_inside(notes, settings, shows):
    """A notebook written for the walk, the app started on it -- and a
    failure, at once and by name, when the app does not show it. Once, of
    many runs, the first screen held a brand-new notebook's welcome note
    over a seed that was on disk; not reproduced since. Every later step
    then failed for that one reason, ten failures for a single cause."""
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("notes_data.json", notes)
    put_inside("settings.json", settings)
    on_disk = [n.get("uid") for n in read_file("notes_data.json") or []]
    start_app()
    ok = on_disk == [n.get("uid") for n in notes] and not screen_says(shows)
    check("the notebook written for this part is the one the app shows", ok,
          "on disk: %s, on screen: %s" % (on_disk, " | ".join(t for t, d, _ in nodes() if t)[:160]))
    return ok


def said(day):
    """A day as the calendar's cells name it: Monday, 12 October."""
    return "%s, %d %s" % (LONG_DAYS[day.weekday()], day.day, LONG_MONTHS[day.month - 1])


def picker_day(day):
    """A day as Android's own date picker names it: Wednesday, October 14,
    2026 -- today's with "Today, " in front, which a part of it still finds."""
    return "%s, %s %d, %d" % (LONG_DAYS[day.weekday()], LONG_MONTHS[day.month - 1], day.day, day.year)


def calendar(folder):
    """The calendar tab: a series and a dated note in this month, one day of
    the series edited on its own, deleted and brought back, a day painted,
    the History, and a repeat set from the sheet -- each read back from the
    file the app wrote."""
    from datetime import date, timedelta
    print("\n-- the calendar --")
    today = date.today()
    first = date(today.year, today.month, 1)
    monday = first + timedelta(days=(7 - first.weekday()) % 7)
    second = monday + timedelta(days=7)
    other = first + timedelta(days=20)
    if not seed_inside([
        {"id": 1, "uid": "cal-standup", "title": "Stand-up", "body": "agenda", "category": "Work",
         "date": monday.isoformat(), "recurrence": {"freq": "weekly", "weekdays": [0]},
         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "cal-dentist", "title": "Dentist", "body": "at nine", "category": "Personal",
         "date": other.isoformat(), "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
    ], {"username": "Maria", "welcome_offered": True}, "Stand-up"):
        return
    tap("Calendar", exact=True, wait=1.5)
    check("the calendar opens on this month", not screen_says(LONG_MONTHS[today.month - 1] + " " + str(today.year)))
    check("a day the series lands on says it holds a note", not screen_says(said(second) + ", 1 note"))
    shot(folder, "24-calendar", LONG_MONTHS[today.month - 1])

    # One day of the series, on its own.
    tap(said(second), wait=1.0)
    open_note("Stand-up")
    check("a day of a series opens in day mode", not screen_says("Only this day"))
    shot(folder, "25-day-mode", "Only this day", "Whole series")
    tap("Stand-up", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" room 2")
    time.sleep(1.5)
    series = note_by_uid("cal-standup") or {}
    own = (series.get("overrides") or {}).get(second.isoformat()) or {}
    check("the day's own title is written to that day alone",
          own.get("title") == "Stand-up room 2" and series.get("title") == "Stand-up", json.dumps(series)[:240])
    # Back to the calendar by a word only its own bar says: the day's banner
    # names the month too, and an earlier run took the editor for the month.
    back_to("📋 History")

    # The day deleted from the series, and its Undo.
    long_press("Stand-up room 2")
    tap("Delete this day", wait=1.0)
    # The question's title and its button say the same words: the button
    # is the later of the two.
    tap_last("Delete this day", wait=1.0)
    series = note_by_uid("cal-standup") or {}
    deleted = second.isoformat() in (series.get("exceptions") or [])
    check("deleting one day skips it, and only it", deleted and series.get("recurrence", {}).get("freq") == "weekly")
    shot(folder, "26-day-removed", "Day removed", "Undo")
    tap("Undo", wait=1.5)
    series = note_by_uid("cal-standup") or {}
    check("its Undo brings the day back with its own text",
          deleted and not series.get("exceptions")
          and ((series.get("overrides") or {}).get(second.isoformat()) or {}).get("title") == "Stand-up room 2")

    # A day painted.
    long_press(said(other))
    tap("Colour this day", wait=1.2)
    tap("#43aa8b", exact=True, wait=1.2)
    settings = read_file("settings.json") or {}
    check("a painted day is kept under the desktop's key",
          (settings.get("day_colors") or {}).get(other.isoformat()) == "#43aa8b", json.dumps(settings)[:200])
    shot(folder, "27-painted-day", LONG_MONTHS[today.month - 1])

    # The History.
    tap("History", wait=1.5)
    check("the History lists the dated note and the series",
          not screen_says("Dentist", "Stand-up"), str(screen_says("Dentist", "Stand-up")))
    shot(folder, "28-history", "Dentist", "Stand-up")
    tap("📅 Calendar", wait=1.0)

    # A repeat set from the sheet, on the dated note.
    tap(said(other), wait=1.0)
    open_note("Dentist")
    tap("ⓘ", wait=1.2)
    tap("Does not repeat", wait=1.5)
    tap("Monthly", exact=True, wait=0.8)
    shot(folder, "29-repeat-sheet", "Repeat", "Every month")
    tap("Save", exact=True, wait=1.5)
    dentist = note_by_uid("cal-dentist") or {}
    check("a repeat chosen on the sheet reaches the file",
          (dentist.get("recurrence") or {}).get("freq") == "monthly", json.dumps(dentist)[:200])
    back_to("📋 History")


def listing(folder):
    """The list's Show and its groups: a type filter, grouping by category,
    a group folded -- the choices kept under the desktop's own keys."""
    print("\n-- the list: Show and groups --")
    if not seed_inside([
        {"id": 1, "uid": "l-dated", "title": "Dated one", "category": "Work", "date": "2026-11-02",
         "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "l-series", "title": "Weekly one", "category": "Work", "date": "2026-10-05",
         "recurrence": {"freq": "weekly"}, "updated": "2026-10-02 09:00:00"},
        {"id": 3, "uid": "l-plain", "title": "Plain one", "category": "General", "updated": "2026-10-03 09:00:00"},
    ], {"welcome_offered": True, "categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}, "Plain one"):
        return
    tap("Show: All", wait=1.0)
    tap("📅 Dated", exact=True, wait=1.2)
    seen = " | ".join(t or d for t, d, _ in nodes())
    check("Show 📅 Dated keeps the dated note and drops the series and the plain one",
          "Dated one" in seen and "Weekly one" not in seen and "Plain one" not in seen, seen[:200])
    settings = read_file("settings.json") or {}
    check("the choice is kept under the desktop's key and words", settings.get("note_filter") == "📅 Dated")
    tap("Show: 📅 Dated", wait=1.0)
    tap("All", exact=True, wait=1.0)

    tap("⋮", wait=1.0)
    tap("Group by category", wait=1.5)
    shot(folder, "30-grouped", "General", "Work")
    tap("Work", wait=1.2)
    settings = read_file("settings.json") or {}
    seen = " | ".join(t or d for t, d, _ in nodes())
    check("grouping and a folded group are kept, and the folded notes are hidden",
          settings.get("group_by_category") is True and settings.get("collapsed_groups") == ["Work"]
          and "Weekly one" not in seen and "Plain one" in seen, json.dumps(settings)[:200])


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
    open_note("Welcome to Note Organizer")

    # By the editor's own "ⓘ": on its side the phone keeps the list beside
    # the note, and "the list is gone" no longer means "the note is open".
    def still_in_note():
        return not screen_says("Welcome to Note Organizer", "ⓘ")

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


def statistics(folder):
    """Settings -> Statistics over a notebook whose figures are known."""
    print("\n-- Statistics --")
    seed_inside([
        {"id": 1, "uid": "walk-s1", "title": "One", "category": "Work",
         "created": "2026-10-13 09:00", "updated": "2026-10-15 08:00",
         "checklist": [{"text": "x", "done": False}]},
        {"id": 2, "uid": "walk-s2", "title": "Two", "category": "General",
         "created": "2026-10-02 09:00", "updated": "2026-10-02 09:00"},
        {"id": 3, "uid": "walk-s3", "title": "Three", "category": "Personal",
         "created": "2025-06-01 09:00", "updated": "2025-06-01 09:00"},
    ], {"welcome_offered": True, "first_launch_date": "2026-09-01",
        "categories": {"General": "#7c5cff", "Work": "#4cc9f0", "Personal": "#f9c74f"}}, "Three")
    tap("Settings", exact=True, wait=1.2)
    tap("Statistics", wait=1.5)
    check("the page counts the notebook's notes and says since when",
          not screen_says("Total notes", "Using Note Organizer since 01.09.2026"),
          str(screen_says("Total notes", "Using Note Organizer since 01.09.2026")))
    shot(folder, "31-statistics", "Total notes")
    back_to("Search notes")


def calculator(folder):
    """The editor's calculator: keys, an answer inserted at the end of the
    note, the next on its own line after it, a line of the history put in
    again -- and the panel standing aside while the keyboard is up for the
    note. Then the FILE, which is what the inserts did."""
    print("\n-- the calculator --")
    seed_inside([
        {"id": 1, "uid": "walk-sums", "title": "Trip costs", "body": "Fuel and tolls:\n",
         "category": "Personal", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Trip costs")
    open_note("Trip costs")
    tap("⋮", exact=True)
    tap("🧮 Calculator", wait=1.2)

    def press(*keys):
        for key in keys:
            tap(key, exact=True, wait=0.3)

    insert = "⬇ Insert into note"
    check("the menu opens the panel, Insert waiting for an answer", not screen_says(insert, "More functions"))
    press("1", "2", "0", "+", "4", "5", ".", "5", "=")
    check("equals shows the answer", bool(find("165.5", exact=True)))
    tap(insert)
    check("Insert says what it put in the note", not screen_says("Added to the note: 120+45.5=165.5"))
    press("×", "2", "=")
    tap(insert)
    shot(folder, "32-calculator", insert, "Fuel and tolls:")

    # The note's own field takes the keyboard: the panel steps aside, and
    # comes back when the keyboard goes down.
    tap("Fuel and tolls:", wait=1.5)
    check("the panel stands aside while the keyboard is up for the note", bool(screen_says(insert)))
    back(wait=1.5)
    check("and is back when the keyboard is down", not screen_says(insert))

    tap("More functions")
    press("√", "9", "=")
    press("5", "÷", "0", "=")
    check("a division by zero says Error", bool(find("Error", exact=True)))
    tap("History")
    check("the history keeps the answers, newest first, and says what a tap does",
          not screen_says("√9=3", "165.5×2=331", "120+45.5=165.5", "Tap a line to insert it into the note"))
    shot(folder, "33-calculator-history", "√9=3", "Tap a line to insert it into the note")
    tap("120+45.5=165.5", exact=True)

    back(wait=1.2)
    check("Back closes the panel and leaves the note open",
          bool(screen_says(insert)) and not screen_says("Fuel and tolls:"))
    back_to("Search notes")
    notes = read_file("notes_data.json") or []
    body = notes[0].get("body") if notes else None
    want = "Fuel and tolls:\n120+45.5=165.5\n165.5×2=331\n120+45.5=165.5"
    check("the file holds each sum on its own line, in the order inserted", body == want,
          "body: %r" % body)


def finding(folder):
    """Find in note: prose and table cells counted together, stepped
    through in reading order, case on and off, a miss -- and the FILE
    untouched afterwards, since finding only ever paints."""
    print("\n-- find in note --")
    body = "\n".join(["milk, bread", "Milk again", "| Item | Note |", "| --- | --- |",
                      "| milk | fresh |", "| eggs | MILK too |", "after milk"])
    seed_inside([
        {"id": 1, "uid": "walk-find", "title": "Shopping", "body": body,
         "category": "Personal", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Shopping")
    open_note("Shopping")
    tap("⋮", exact=True)
    tap("🔍 Find in note", wait=1.2)
    type_text("milk")
    time.sleep(0.8)
    check("two in the prose, two cells, one after the table: 1/5", bool(find("1/5", exact=True)),
          " | ".join(t for t, d, _ in nodes() if t)[:200])
    shot(folder, "34-find", "1/5")
    tap("Next match", wait=0.6)
    tap("Next match", wait=0.8)
    check("the third stop is the table's first matching cell", bool(find("3/5", exact=True)))
    shot(folder, "35-find-cell", "3/5")
    tap("Match case", wait=0.8)
    check("with case on, Milk and MILK drop out: three", bool(find("3/3", exact=True)))
    tap("Match case", wait=0.6)
    type_text("zzz")
    time.sleep(0.8)
    check("words found nowhere count 0/0", bool(find("0/0", exact=True)))
    # The first Back takes the keyboard down, the next closes the bar.
    # (back_to waits for words to APPEAR; here they are to go.)
    for _ in range(3):
        if not find("Close find"):
            break
        back(wait=1.2)
    check("Back closes the bar and leaves the note open",
          not find("0/0", exact=True) and not screen_says("Shopping"),
          " | ".join(t or d for t, d, _ in nodes())[:200])
    tap("⋮", exact=True)
    tap("🔍 Find in note", wait=1.2)
    check("the bar opens again with the last search", not screen_says("milkzzz"))
    back_to("Search notes")
    notes = read_file("notes_data.json") or []
    check("finding never changed the note", bool(notes) and notes[0].get("body") == body,
          "body: %r" % (notes[0].get("body") if notes else None))


def desktop_export():
    """The desktop's own export of a note, through the fixture tool's
    fingerprinted copy -- or a reason it cannot be had here."""
    sys.path.insert(0, os.path.join(ANDROID, "tools"))
    try:
        import make_export_fixture
        return make_export_fixture.desktop_export()
    except Exception as problem:  # noqa: BLE001 -- the walk goes on without it
        return "the fixture tool could not be used: %s" % problem


def exporting(folder):
    """Export as file, both formats, through Android's own "save as" -- and
    the file read back off the phone and compared, byte for byte, with what
    the desktop's renderer writes for the same note. Then Open."""
    print("\n-- export a note as a file --")
    body = "\n".join(["Fuel and tolls", "• keep receipts", "| Item | Cost |", "| --- | ---: |",
                      "| fuel |   120 |", "| tolls |  45.5 |", "the end"])
    seed_inside([
        {"id": 1, "uid": "walk-export", "title": "Trip: day 1/2", "body": body,
         "formatting": [{"start": 0, "end": 4, "style": "bold"},
                        {"start": 9, "end": 14, "style": "highlight", "value": "#fff3b0"}],
         "paragraphs": [{"line": 1, "kind": "bullet"}],
         "checklist": [{"text": "book the hotel", "done": True}],
         "comments": "paid by card",
         "category": "Personal", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Trip: day 1/2")

    def downloads():
        listing = adb("shell", "ls", "/sdcard/Download")
        return {line.strip() for line in listing.splitlines() if line.strip()}

    desktop = desktop_export()
    for fmt, label, ext in (("html", "Web page (.html)", ".html"), ("txt", "Plain text (.txt)", ".txt")):
        earlier = downloads()
        open_note("Trip: day 1/2")
        tap("⋮", exact=True)
        tap("💾 Export as file", wait=1.2)
        tap(label, wait=3.0)
        check("the picker offers the desktop's file name (%s)" % fmt, bool(find("Trip_ day 1_2" + ext)),
              " | ".join(t for t, d, _ in nodes() if t)[:200])
        if find("SAVE") is not None or find("Save") is not None:
            tap("SAVE", exact=True, wait=3.0) if find("SAVE") else tap("Save", exact=True, wait=3.0)
        made = sorted(f for f in downloads() - earlier if f.endswith(ext))
        check("one %s file is where the picker put it" % fmt, len(made) == 1, str(made))
        check("the message says where it went", not screen_says("Exported to"))
        if fmt == "html":
            shot(folder, "36-exported", "Exported to")
        if len(made) == 1:
            written = adb("exec-out", "cat", "/sdcard/Download/" + made[0], binary=True).decode("utf-8")
            with open(os.path.join(folder, "phone_export" + ext), "w", encoding="utf-8", newline="") as f:
                f.write(written)
            if isinstance(desktop, str):
                fail("the desktop's export to compare with: %s" % desktop)
            else:
                compose, _name = desktop
                note = (read_file("notes_data.json") or [{}])[0]
                title = (note.get("title") or "").strip() or "Untitled note"
                check("the phone's %s file is the desktop's, byte for byte" % fmt,
                      written == compose(note, fmt, title))
        if fmt == "html":
            tap("Open", exact=True, wait=3.0)
            check("Open shows the page in another app", foreground() not in ("", PACKAGE), foreground())
            start_app()
        back_to("Search notes")


def styles_through_an_edit(folder):
    """What the desktop set, through a letter typed on the phone: a size,
    drawn and written back as the number the desktop keeps (it once came in
    empty and went back with no value, which the desktop dropped), and a
    list item's bullet, kept on the line typed into (it was dropped on
    every letter)."""
    print("\n-- desktop styles through a phone edit --")
    seed_inside([
        {"id": 1, "uid": "walk-sizes", "title": "Sizes", "body": "Big heading\n• small print",
         "formatting": [{"start": 0, "end": 11, "style": "font_size", "value": 24}],
         "paragraphs": [{"line": 1, "kind": "bullet"}],
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Sizes")
    open_note("Sizes")
    shot(folder, "37-desktop-size", "Big heading")
    tap("small print", wait=1.0)
    type_text("x")
    back_to("Search notes")
    note = (read_file("notes_data.json") or [{}])[0]
    spans = note.get("formatting") or []
    check("the edit was saved", "x" in note.get("body", ""), repr(note.get("body")))
    check("the size goes back as the desktop's number",
          any(s.get("style") == "font_size" and s.get("value") == 24 for s in spans), repr(spans))
    check("the list item typed into keeps its bullet",
          (note.get("paragraphs") or []) == [{"line": 1, "kind": "bullet"}], repr(note.get("paragraphs")))


def desktop_normalize_template():
    """The desktop's own normalize_template, when it is here."""
    sys.path.insert(0, DESKTOP)
    try:
        from noteorganizer.core.storage import normalize_template
        return normalize_template
    except Exception as problem:  # noqa: BLE001 -- the walk goes on without it
        print("     (the desktop's normalize_template could not be used: %s)" % problem)
        return None


def templates(folder):
    """Templates: a note saved as one, its name asked about when taken, a
    new note started from it with everything it held, one edited and one
    deleted in Settings -- and templates.json read back each time, in the
    shape the desktop's normalize_template keeps."""
    print("\n-- templates --")
    body = "• call mum\n| Day | Task |\n| --- | --- |\n| Mon | gym |"
    seed_inside([
        {"id": 1, "uid": "walk-tpl", "title": "Weekly plan", "body": body,
         "formatting": [{"start": 2, "end": 6, "style": "bold"}],
         "paragraphs": [{"line": 0, "kind": "bullet"}],
         "checklist": [{"text": "groceries", "done": False}],
         "comments": "every Sunday", "category": "Work",
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True, "categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}, "Weekly plan")
    normalize = desktop_normalize_template()

    def on_disk():
        return read_file("templates.json") or []

    open_note("Weekly plan")
    tap("⋮", exact=True)
    tap("🧩 Save as template", wait=1.2)
    check("the name offered is the note's title", bool(find("Weekly plan", exact=True)))
    tap("Save", exact=True, wait=1.5)
    saved = on_disk()
    check("one template, named after the note", [t.get("name") for t in saved] == ["Weekly plan"], repr(saved))
    if saved:
        t = saved[0]
        check("it holds the note: text, styles, lines, comments, category, checklist",
              t.get("body") == body and t.get("formatting") == [{"start": 2, "end": 6, "style": "bold"}]
              and t.get("paragraphs") == [{"line": 0, "kind": "bullet"}] and t.get("comments") == "every Sunday"
              and t.get("category") == "Work" and t.get("checklist") == [{"text": "groceries", "done": False}],
              repr(t))
        if normalize is not None:
            check("the file is already in the shape the desktop keeps", normalize(t) == t, repr(normalize(t)))

    # The same name again: asked, never replaced silently.
    tap("⋮", exact=True)
    tap("🧩 Save as template", wait=1.2)
    tap("Save", exact=True, wait=1.2)
    check("a name already taken asks first", not screen_says("Template name taken"))
    tap("Replace", exact=True, wait=1.2)
    check("replaced, still one", len(on_disk()) == 1)

    # A new note from it: everything it held, ticks and all.
    tap("⋮", exact=True)
    tap("📎 Use template", wait=1.2)
    shot(folder, "38-template-picker", "Use a template")
    tap("🧩  Weekly plan", wait=2.0)
    notes = read_file("notes_data.json") or []
    check("a second note, started from the template", len(notes) == 2, str(len(notes)))
    if len(notes) == 2:
        new = notes[1]
        check("it has the template's text, styles, lines, comments, category and checklist",
              new.get("title") == "Weekly plan" and new.get("body") == body
              and new.get("formatting") == [{"start": 2, "end": 6, "style": "bold"}]
              and new.get("paragraphs") == [{"line": 0, "kind": "bullet"}]
              and new.get("comments") == "every Sunday" and new.get("category") == "Work"
              and new.get("checklist") == [{"text": "groceries", "done": False}], repr(new))
    back_to("Search notes")

    # Settings: one more checklist item, saved; then deleted for good.
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("Weekly plan", exact=True, wait=1.5)
    shot(folder, "39-template-editor", "Edit template")
    tap("A new item", wait=0.8)
    # A capital already, as in a new note's body: the field capitalises.
    type_text("Socks")
    tap("+ Add", exact=True, wait=0.8)
    tap("Save", exact=True, wait=1.5)
    saved = on_disk()
    check("the edit is saved, in its place",
          len(saved) == 1 and [c.get("text") for c in saved[0].get("checklist", [])] == ["groceries", "Socks"],
          repr(saved))
    tap("Delete template", wait=1.2)
    check("deleting asks, and says it is for good", not screen_says("for good"))
    tap("Delete", exact=True, wait=1.2)
    check("gone", on_disk() == [], repr(on_disk()))
    back_to("Search notes")


def sizes_and_faces(folder):
    """A word chosen in the editor, given a size and a face from the
    format bar's two new keys -- and the file, where both must be in the
    shape the desktop keeps: the size a number, the face one of its own."""
    print("\n-- sizes and faces --")
    seed_inside([
        {"id": 1, "uid": "walk-faces", "title": "Faces", "body": "plain words to style",
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Faces")
    open_note("Faces")
    # Everything selected, as a keyboard's Ctrl+A does: Android's own
    # "Select all" floats in a window of its own, which the screen's tree
    # does not hold, so it cannot be found and tapped from here.
    tap("plain words to style", wait=1.0)
    adb("shell", "input", "keycombination", "113", "29")
    time.sleep(0.8)
    tap("12", exact=True, wait=0.8)
    tap("20", exact=True, wait=0.8)
    tap("Aa", exact=True, wait=0.8)
    tap("Georgia", exact=True, wait=0.8)
    shot(folder, "40-size-and-face", "Faces")
    back_to("Search notes")
    spans = ((read_file("notes_data.json") or [{}])[0]).get("formatting") or []
    check("the size is the number 20", any(s.get("style") == "font_size" and s.get("value") == 20 for s in spans),
          repr(spans))
    check("the face is Georgia", any(s.get("style") == "font_family" and s.get("value") == "Georgia" for s in spans),
          repr(spans))


def lists(folder):
    """The format bar's line keys and a list's keystrokes: lines numbered,
    Enter continuing the list and numbering on, Enter on an empty item
    leaving it, every line indented and centred -- and the file, its
    paragraphs in the desktop's shape, the kind read off each marker."""
    print("\n-- lists, depth and alignment --")
    seed_inside([
        {"id": 1, "uid": "walk-lists", "title": "Lists", "body": "milk\nbread\neggs",
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Lists")
    open_note("Lists")
    tap("milk", wait=1.0)
    adb("shell", "input", "keycombination", "113", "29")       # Ctrl+A
    time.sleep(0.6)
    tap("Numbered list", wait=1.0)

    def note():
        return (read_file("notes_data.json") or [{}])[0]

    # Down to the last line and to its end, by the arrow keys: Ctrl+End,
    # injected, left the selection standing, and Enter replaced it.
    for _ in range(4):
        adb("shell", "input", "keyevent", "20")                # down
    adb("shell", "input", "keyevent", "123")                   # end of line
    time.sleep(0.4)
    adb("shell", "input", "keyevent", "66")                    # Enter
    time.sleep(0.6)
    type_text("jam")
    adb("shell", "input", "keyevent", "66")
    time.sleep(0.6)
    adb("shell", "input", "keyevent", "66")                    # on the empty item: out
    time.sleep(0.6)
    type_text("after")
    time.sleep(1.5)
    # Lower case: the keyboard capitalises a line's first word as it likes.
    check("numbered, continued by Enter, left by Enter on an empty item",
          (note().get("body") or "").lower() == "1. milk\n2. bread\n3. eggs\n4. jam\nafter", repr(note().get("body")))

    adb("shell", "input", "keycombination", "113", "29")
    time.sleep(0.6)
    tap("More indent", wait=0.8)
    tap("Centre", wait=1.0)
    shot(folder, "41-lists", "1. milk")
    back_to("Search notes")
    saved = note()
    paragraphs = saved.get("paragraphs") or []
    check("every line one deeper and centred, the items numbered by their markers",
          paragraphs == [{"line": n, "kind": "number", "indent": 1, "align": "center"} for n in range(4)]
          + [{"line": 4, "indent": 1, "align": "center"}], repr(paragraphs))
    check("a deeper list counts from one again", saved.get("body", "").startswith("1. milk\n2. bread"),
          repr(saved.get("body")))


def new_table(folder):
    """A table made from the format bar: the size grid, the table on lines
    of its own after the cursor's line, its first cell open for typing --
    and the file, which holds a table the desktop reads as one."""
    print("\n-- a new table --")
    seed_inside([
        {"id": 1, "uid": "walk-table", "title": "Plan", "body": "intro",
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Plan")
    open_note("Plan")
    tap("intro", wait=1.0)
    adb("shell", "input", "keyevent", "123")                   # end of line
    time.sleep(0.4)
    tap("Insert table", wait=1.2)
    shot(folder, "42-table-size", "2 x 2 table")
    tap("Insert", exact=True, wait=1.5)
    check("the new table's first cell is open for typing", not screen_says("Cell"))
    type_text("Day")
    tap("Save", exact=True, wait=1.2)
    back_to("Search notes")
    body = ((read_file("notes_data.json") or [{}])[0]).get("body") or ""
    lines = body.split("\n")
    check("the table sits on its own lines under the text, its first cell written",
          lines[0] == "intro" and len(lines) == 4 and lines[1].startswith("| Day") and lines[3] == "",
          repr(body))


def cell_menu(folder):
    """The cell's menu: a cell highlighted pipe to pipe, a cell merged into
    the one on its left, the table centred on the page -- and the file,
    where each is what the desktop writes. Then the table deleted, asked."""
    print("\n-- the cell menu --")
    body = "\n".join(["| Item | Cost |", "| --- | --- |", "| fuel | 120 |", "| tolls | 45 |"])
    seed_inside([
        {"id": 1, "uid": "walk-cells", "title": "Cells", "body": body,
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Cells")

    def note():
        return (read_file("notes_data.json") or [{}])[0]

    def sheet(cell, to_the_end=False):
        open_note("Cells")
        tap(cell, exact=True, wait=1.2)
        back(wait=0.8)                       # the keyboard down, the sheet's actions in sight
        if to_the_end:
            # The table's own actions close the sheet, below its fold.
            adb("shell", "input", "swipe", "540", "2100", "540", "700", "400")
            time.sleep(1.0)

    sheet("120")
    tap("Highlight colour 1", wait=1.2)
    sheet("45")
    tap("Merge with cell to the left", wait=1.2)
    sheet("fuel", to_the_end=True)
    tap("Center table on page", wait=1.2)
    shot(folder, "43-cell-menu", "Item")
    back_to("Search notes")
    saved = note()
    lines = saved.get("body", "").split("\n")
    span = next((s for s in saved.get("formatting") or [] if s.get("style") == "highlight"), None)
    line2_start = sum(len(lines[n]) + 1 for n in range(2))
    pipes = [i for i, ch in enumerate(lines[2]) if ch == "|"] if len(lines) > 2 else []
    check("the highlight lies pipe to pipe over the cell",
          span is not None and len(pipes) > 2
          and (span["start"], span["end"]) == (line2_start + pipes[1] + 1, line2_start + pipes[2]), repr(span))
    check("the merge is the desktop's marker", len(lines) > 3 and lines[3].replace(" ", "") == "|tolls|<|", repr(lines))
    check("the table is centred: its lines' alignment",
          (saved.get("paragraphs") or []) == [{"line": n, "align": "center"} for n in range(4)],
          repr(saved.get("paragraphs")))

    sheet("fuel", to_the_end=True)
    tap("Delete table", wait=1.0)
    check("deleting the table asks first", not screen_says("Delete this table and everything in it?"))
    tap_last("Delete", wait=1.2)
    back_to("Search notes")
    check("the table is gone", note().get("body") == "", repr(note().get("body")))


def move_day(folder):
    """One day of a series held on another date -- the desktop's "Move this
    day…": refused onto a date the series already has, then moved with its
    own edit, the editor following it, and put back by Undo. Each read back
    from the file."""
    from datetime import date, timedelta
    print("\n-- one day of a series moved --")
    today = date.today()
    first = date(today.year, today.month, 1)
    monday = first + timedelta(days=(7 - first.weekday()) % 7)
    second, third = monday + timedelta(days=7), monday + timedelta(days=14)
    to = second + timedelta(days=2)
    if not seed_inside([
        {"id": 1, "uid": "mv-standup", "title": "Stand-up", "body": "agenda", "category": "Work",
         "date": monday.isoformat(), "recurrence": {"freq": "weekly", "weekdays": [0]},
         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
    ], {"welcome_offered": True}, "Stand-up"):
        return

    def series():
        return note_by_uid("mv-standup") or {}

    tap("Calendar", exact=True, wait=1.5)
    tap(said(second), wait=1.0)
    open_note("Stand-up")
    tap("Stand-up", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" room 2")
    back()                                    # the keyboard down; the move saves what was typed

    # Onto another Monday of the series: refused, in the desktop's words.
    tap("Move this day", wait=1.5)
    tap(picker_day(third), wait=0.6)
    tap("OK", exact=True, wait=1.5)
    check("a day cannot move onto another day of its series",
          not screen_says("already has a day on") and not (series().get("moved") or {}),
          json.dumps(series().get("moved")))

    tap("Move this day", wait=1.5)
    tap(picker_day(to), wait=0.6)
    tap("OK", exact=True, wait=1.5)
    moved = series()
    check("the day goes to its new date with what was typed on it",
          (moved.get("moved") or {}).get(second.isoformat()) == to.isoformat()
          and ((moved.get("overrides") or {}).get(to.isoformat()) or {}).get("title") == "Stand-up room 2",
          json.dumps({k: moved.get(k) for k in ("moved", "overrides")}))
    # One look at the screen, then the photograph and the Undo at once: the
    # offer lasts eight seconds, and each look takes one or two.
    missing = screen_says("Only this day: " + said(to), "Moved to", "Undo")
    check("the editor follows the day, and the move offers its Undo", not missing, str(missing))
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    with open(os.path.join(folder, "48-day-moved.png"), "wb") as f:
        f.write(adb("exec-out", "cat", "/sdcard/shot.png", binary=True))
    tap("Undo", wait=1.5)
    back_home = series()
    check("Undo puts it back, its own edit with it",
          not (back_home.get("moved") or {})
          and ((back_home.get("overrides") or {}).get(second.isoformat()) or {}).get("title") == "Stand-up room 2",
          json.dumps({k: back_home.get(k) for k in ("moved", "overrides")}))
    back_to("📋 History")


def turn(rotation):
    """The phone turned: 0 upright, 1 on its side."""
    adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
    adb("shell", "settings", "put", "system", "user_rotation", str(rotation))
    time.sleep(2.5)


def count(label):
    """How many nodes say exactly [label]: a card and the editor both may."""
    return sum(1 for t, d, _ in nodes() if label in (t, d))


def bounds(label):
    """The on-screen boxes of the nodes whose text is exactly [label].
    A node scrolled out of a pane is still in the tree -- counting
    nodes says it is there -- but its box is cut to what shows."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for node in root.iter("node"):
        if node.get("text", "") == label:
            box = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(box) == 4:
                out.append(tuple(box))
    return out


def wide(folder):
    """A wide window -- the phone on its side. The bar becomes a rail, and
    a note opens beside the list, which stays in reach: so the list can
    change the very note the editor holds. Each change is read back from
    the file, where the editor's next save must not undo it, nor it lose
    what the editor held. Then a code shared in over an open note, the
    phone upright, as it always could. Then a medium window, where the
    note takes the list's place beside the rail."""
    print("\n-- a wide window --")
    if not seed_inside([
        {"id": 1, "uid": "w-groceries", "title": "Groceries", "body": "milk", "category": "General",
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:02:00"},
        {"id": 2, "uid": "w-plans", "title": "Plans", "body": "trip", "category": "General",
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:01:00"},
    ], {"welcome_offered": True}, "Groceries"):
        return

    def groceries():
        return note_by_uid("w-groceries") or {}

    # Turned with something half done: a selection on the list, then a
    # find bar in an open note. The layout changes around them; they must
    # not be built again, which would drop both.
    long_press("Plans")
    turn(1)
    check("a selection survives the phone turning", not screen_says("1 selected"))
    back()
    turn(0)
    open_note("Groceries")
    tap("⋮", wait=1.0)
    tap("Find in note", wait=1.0)
    type_text("milk")
    turn(1)
    check("an open find bar survives the phone turning", not screen_says("Close find"))
    back_to("Pick a note")

    turn(1)
    notes_at, settings_at = find("Notes", exact=True), find("Settings", exact=True)
    check("on its side the bar is a rail down the left edge",
          bool(notes_at and settings_at) and notes_at[0] < 300 and settings_at[0] < 300
          and notes_at[1] < settings_at[1], "%s %s" % (notes_at, settings_at))
    check("the second pane waits for a note", not screen_says("Search notes", "Pick a note"))
    shot(folder, "44-wide-list", "Search notes", "Pick a note")

    open_note("Groceries")
    check("a note opens beside the list, which stays", not screen_says("Search notes", "Plans", "ⓘ"))
    # The editor's body, not the card's first words: the last of the two.
    tap_last("milk", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" and eggs")
    back()                                    # the keyboard down
    shot(folder, "45-wide-note", "Groceries", "milk and eggs", "ⓘ")

    # Pinned from the list while open: the pin must reach the file, and the
    # editor -- which writes every field it holds -- must not put it back.
    long_press("Groceries")
    tap("📌", wait=1.0)
    check("pinned from the list beside the open note", groceries().get("pinned") is True
          and groceries().get("body") == "milk and eggs", json.dumps(groceries())[:200])
    tap_last("milk and eggs", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" and bread")
    # On its side the keyboard covers the list: down first, which also lets
    # the autosave run. Whether a card tapped BEFORE it saves the words is
    # a race no tap through adb can win reliably -- AppStateTest holds it.
    back(wait=1.5)
    saved = groceries()
    check("the editor's next save keeps the pin it did not make", saved.get("pinned") is True
          and saved.get("body") == "milk and eggs and bread", json.dumps(saved)[:200])
    tap("Plans", exact=True, wait=1.5)
    # Its title SHOWN: the editor stays on screen from note to note, and
    # once kept the last note's scroll, the new title above the pane --
    # where it was still in the tree, so counting it passed over that.
    titles = sorted(bounds("Plans"))
    shown = titles[-1][3] - titles[-1][1] if len(titles) == 2 else 0
    check("another card's note takes the pane, its title in sight", count("trip") == 2 and shown >= 40,
          "boxes %s" % titles)

    tap("Calendar", exact=True, wait=1.5)
    check("the note stays open beside the calendar", not screen_says("📋 History", "ⓘ", "trip"))
    shot(folder, "46-wide-calendar", "📋 History", "ⓘ")
    tap("Settings", exact=True, wait=1.5)
    check("Settings has no note beside it: the note is closed", not screen_says("Your name") and find("ⓘ") is None)
    tap("Notes", exact=True, wait=1.2)

    # Two new notes in a row: the second + must give a second, empty note
    # -- both are "note 0" until saved, and the pane once stayed on the first.
    tap("+", exact=True, wait=1.5)
    tap("Title", exact=True, wait=0.6)
    type_text("First new")
    back(wait=1.5)
    tap("+", exact=True, wait=1.5)
    check("a second + beside a new note opens another, empty", find("Title", exact=True) is not None
          and count("First new") == 1, str(count("First new")))
    tap("Title", exact=True, wait=0.6)
    type_text("Second new")
    back(wait=1.5)
    titles = [n.get("title") for n in read_file("notes_data.json") or []]
    check("both new notes reach the file", "First new" in titles and "Second new" in titles, repr(titles))
    back_to("Pick a note")

    # Upright: a code shared in from another app over the open note, the
    # very note it updates. The editor must show the update, and its next
    # save keep it -- before, it held the old words and put them back.
    turn(0)
    open_note("Groceries")
    code = desktop_code({"uid": "w-groceries", "title": "Groceries", "body": "milk, from the desktop",
                         "category": "General", "updated": "2026-10-15 10:00:00"})
    if not code:
        fail("a code over an open note: the desktop's encoder could not be used, so nothing was tried")
    else:
        adb("shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "text/plain",
            "--es", "android.intent.extra.TEXT", code, "-n", PACKAGE + "/.MainActivity")
        time.sleep(2.5)
        tap("Import", exact=True, wait=1.5)
        tap("Update this note", wait=1.5)
        check("the open note shows the update", not screen_says("milk, from the desktop", "ⓘ"))
        tap_last("milk, from the desktop", wait=0.6)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        type_text(" ok")
        back()
        time.sleep(1.5)
        check("the editor's next save keeps the update", groceries().get("body") == "milk, from the desktop ok",
              repr(groceries().get("body")))
    back_to("Search notes")

    # A medium window -- 685 by 411 on its side, a small tablet's width:
    # the rail, and the note in the list's place.
    adb("shell", "wm", "size", "1080x1800")
    turn(1)
    # The pinned note, at the top: a short window shows few cards.
    open_note("Groceries")
    check("a medium window opens the note in the list's place, beside the rail",
          find("Search notes") is None and find("Settings", exact=True) is not None)
    shot(folder, "47-medium-note", "milk, from the desktop", "ⓘ")
    tap("Notes", exact=True, wait=1.5)
    check("the rail leaves the note for the list", not screen_says("Search notes") and find("ⓘ") is None)
    adb("shell", "wm", "size", "reset")
    turn(0)


if __name__ == "__main__":
    sys.exit(main())
