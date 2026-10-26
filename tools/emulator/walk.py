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
import contextlib
import io
import json
import os
import re
import struct
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emulator_only import ADB, command  # noqa: E402 -- every command goes to the emulator, never a phone

PACKAGE = "ro.noteorganizer.app"
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(os.path.dirname(HERE))
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
NOTEBOOK = "files/notebook"

failures = []


def adb(*args, binary=False, check=False):
    out = subprocess.run(command(*args), capture_output=True)
    if check and out.returncode != 0:
        raise RuntimeError("adb %s: %s" % (" ".join(args), out.stderr.decode(errors="replace")))
    return out.stdout if binary else out.stdout.decode("utf-8", errors="replace")


def busy_share(seconds=3.0):
    """How much of the emulator's processor was at work over [seconds], 0 to
    1, from the counters in /proc/stat (idle and waiting count as rest)."""
    def counters():
        values = [int(v) for v in adb("shell", "head", "-1", "/proc/stat").split()[1:]]
        return sum(values), values[3] + values[4]
    total, rest = counters()
    time.sleep(seconds)
    total_after, rest_after = counters()
    spent = total_after - total
    return 1.0 - (rest_after - rest) / spent if spent > 0 else 0.0


def wait_for_boot(limit=300, quiet_for=30.0):
    """Until the emulator has started AND settled. boot_completed comes long
    before the work a start sets going is done: measured, the processor was
    busy to 68 s of uptime, quiet for 12, busy again to 102 -- and a walk
    begun a moment after a start lost its first typing, which never reached
    the editor, and saw the dissolve jump from light to dark between two
    captures (2026-10-21). Settled is under a quarter busy for [quiet_for]
    seconds running, past twice that lull; warm, it idles at 1%."""
    start = time.time()
    while adb("shell", "getprop", "sys.boot_completed").strip() != "1":
        if time.time() - start > limit:
            return False
        time.sleep(3)
    quiet_since = time.time()
    while time.time() - quiet_since < quiet_for:
        if time.time() - start > limit:
            print("   the emulator was still busy after %d s: walking anyway" % limit)
            return True
        if busy_share() >= 0.25:
            quiet_since = time.time()
    print("   the emulator settled %.0f s after the walk began waiting" % (time.time() - start))
    return True


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


def keyboard_shown():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def keyboard_down(patience=4.0):
    """Back to put the keyboard down -- once it is up, and only then. It
    rises half a second after a field takes the cursor, later on a busy
    emulator; a Back pressed before it rose closed what was open instead
    (a cell's sheet, once in a full run, and every step after it failed).
    And it is down when the system says so: the flag stays up for a moment
    after the Back, over a second on an emulator started that day, and a
    second keyboard_down read in that moment pressed Back again -- off the
    cell, the table's bar gone with it (2026-10-22)."""
    deadline = time.time() + patience
    while time.time() < deadline:
        if keyboard_shown():
            back(wait=0.8)
            gone_by = time.time() + patience
            while keyboard_shown() and time.time() < gone_by:
                time.sleep(0.3)
            return True
        time.sleep(0.3)
    return False


def back_to(*words, tries=4):
    """Back until the screen says these words. The first Back with the
    keyboard up only closes the keyboard -- a phone's habit, and the reason
    a single Back left an earlier run still inside the editor."""
    for _ in range(tries):
        if not screen_says(*words):
            return True
        back()
    return not screen_says(*words)


def says_soon(*words, within=6.0):
    """[screen_says], asked again until every word is there or [within]
    seconds pass: what a tab draws in a moment, not read once after a fixed
    pause (the calendar's row of the day, 2026-10-24). Words that never
    come are still reported missing."""
    return settle(lambda: screen_says(*words), lambda missing: not missing, within=within, first=0)


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


def settle(read, ok, within=8.0, first=1.5):
    """What [read] returns once the autosave has had its [first] seconds --
    read again every half second until [ok] holds of it or [within] more
    seconds are up. One read after a fixed sleep failed once, a busy
    emulator saving late (Redo, 2026-10-19), and the read for the failure's
    own message a moment later held the right words. The last value comes
    back either way, so a change that never arrives still fails; and the
    first wait stays, so a check whose old state would also pass is read no
    sooner than before."""
    time.sleep(first)
    deadline = time.time() + within
    value = read()
    while not ok(value) and time.time() < deadline:
        time.sleep(0.5)
        value = read()
    return value


def check_settled(what, read, ok, within=8.0, first=1.5):
    """[check] on what [read] settles to ([settle]), the value it judged
    shown when it fails -- never a second read."""
    value = settle(read, ok, within, first)
    check(what, ok(value), repr(value))
    return value


def fail(what):
    print("FAIL " + what)
    failures.append(what)


def shot(folder, name, *must_say, patience=6.0):
    """A photograph of the emulator's screen -- only when it says what it
    should, waited for a moment rather than read once: the first start
    after an install drew the list after the file was already written,
    and a photograph taken on the file's word found an empty screen."""
    missing = screen_says(*must_say)
    deadline = time.time() + patience
    while missing and time.time() < deadline:
        time.sleep(0.5)
        missing = screen_says(*must_say)
    if missing:
        fail("photo %s: the screen does not say %s" % (name, missing))
        return
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    data = adb("exec-out", "cat", "/sdcard/shot.png", binary=True)
    with open(os.path.join(folder, name + ".png"), "wb") as f:
        f.write(data)
    print("     photo %s.png" % name)


def shades_while_tapping(label, frames=10):
    """How bright the page is, capture after capture, from just before
    [label] is tapped until well after: the emulator takes raw captures as
    fast as it can (about one every 100 ms) while the tap is made. Each is
    read as the mean of the page between the bars -- a sample of its pixels,
    0 for black, 255 for white. For a change that should be seen arriving
    in steps, not in one frame."""
    at = find(label, exact=True)
    if at is None:
        fail("tap %r: not on the screen" % label)
        return []
    script = ("i=0\nwhile [ $i -lt %d ]; do\n  screencap /data/local/tmp/shade_$i.raw\n"
              "  i=$((i+1))\ndone\n" % frames)
    local = os.path.join(os.environ.get("TEMP", "."), "walk_shades.sh")
    with open(local, "w", newline="\n") as f:
        f.write(script)
    adb("push", local, "/data/local/tmp/shades.sh")
    os.remove(local)
    burst = subprocess.Popen(command("shell", "sh", "/data/local/tmp/shades.sh"),
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.25)
    adb("shell", "input", "tap", str(at[0]), str(at[1]))
    burst.wait(timeout=120)
    shades = []
    for i in range(frames):
        raw = adb("exec-out", "cat", "/data/local/tmp/shade_%d.raw" % i, binary=True)
        if len(raw) < 16:
            continue
        w, h = struct.unpack_from("<II", raw, 0)
        head = len(raw) - w * h * 4
        seen = []
        for y in range(int(h * 0.12), int(h * 0.88), 24):
            for x in range(0, w, 24):
                p = head + (y * w + x) * 4
                seen.append(0.299 * raw[p] + 0.587 * raw[p + 1] + 0.114 * raw[p + 2])
        shades.append(round(sum(seen) / len(seen)))
    adb("shell", "rm", "-f", "/data/local/tmp/shades.sh", "/data/local/tmp/shade_*.raw")
    return shades


@contextlib.contextmanager
def animations_slowed(times):
    """The system's animator duration scale at [times] for the steps inside,
    then back to what it was -- an emulator setting, which Compose's
    animations follow. A fade can then be watched by captures that come
    slower than it runs; a cut stays a cut at any speed."""
    was = adb("shell", "settings", "get", "global", "animator_duration_scale").strip()
    adb("shell", "settings", "put", "global", "animator_duration_scale", str(times))
    try:
        yield
    finally:
        if was in ("", "null"):
            adb("shell", "settings", "delete", "global", "animator_duration_scale")
        else:
            adb("shell", "settings", "put", "global", "animator_duration_scale", was)


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
    off_schedule(folder)
    listing(folder)
    list_keeps_place(folder)
    previews(folder)
    arranging(folder)
    attaching(folder)
    duplicates(folder)
    statistics(folder)
    calculator(folder)
    calculator_in_cell(folder)
    finding(folder)
    exporting(folder)
    sharing(folder)
    sheets_over_buttons(folder)
    styles_through_an_edit(folder)
    templates(folder)
    sizes_and_faces(folder)
    lists(folder)
    tap_past_line_end(folder)
    editor_corners(folder)
    new_table(folder)
    cell_menu(folder)
    table_bar(folder)
    table_resize(folder)
    large_letters_table(folder)
    table_extras(folder)
    undoing(folder)
    pasting(folder)
    copy_after_styling(folder)
    typing_styles(folder)
    comments_paste(folder)
    pasted_tables(folder)
    reminders(folder)
    wide(folder)
    screen_sizes(folder)
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
    # Waited for, not read once after start_app's pause: the first start
    # after an install, on an emulator short of memory, wrote the notebook
    # later than that, and every step of this part failed on one read.
    check_settled("a new notebook holds the welcome note", lambda: read_file("notes_data.json"),
                  lambda notes: isinstance(notes, list) and len(notes) == 1
                  and notes[0].get("title", "").startswith("Welcome"), within=12.0, first=0.0)
    check_settled("the welcome note is remembered", lambda: read_file("settings.json") or {},
                  lambda s: s.get("welcome_offered") is True and bool(s.get("welcome_note_uid")),
                  within=6.0, first=0.0)
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
    # Read once, act at once: the offer lasts ten seconds, and on an
    # emulator just started a photograph's own look at the screen and a
    # second look for Undo took them all -- the tap landed where Undo had
    # been, and the note stayed in the Trash (2026-10-23). One look finds
    # Undo; the photograph is a plain capture, with no look of its own.
    undo_at = find("Undo", exact=True)
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    trash = read_file("trash.json") or []
    trashed = any(t.get("title") == "Groceries" for t in trash)
    if undo_at is not None:
        adb("shell", "input", "tap", str(undo_at[0]), str(undo_at[1]))
        time.sleep(1.5)
    with open(os.path.join(folder, "05-undo.png"), "wb") as f:
        f.write(adb("exec-out", "cat", "/sdcard/shot.png", binary=True))
    check("Move to Trash puts the note in trash.json", trashed)
    check("...and offers Undo", undo_at is not None)
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
    # Light | Dark is the app's mode: the page shows that mode's four themes,
    # the one worn ticked, and moved, the app wears that mode's last theme.
    # "Follow the phone" is gone: a fresh notebook is settled at its first
    # start on the original of the mode the phone was in.
    settings = read_file("settings.json") or {}
    check("a fresh notebook is settled on an original theme, following the phone no more",
          settings.get("theme") in ("light", "dark") and settings.get("phone_theme_follows_phone") is False,
          json.dumps(settings)[:200])
    tap("Appearance")
    check("no Follow the phone on the page", screen_says("Follow the phone") == ["Follow the phone"])
    tap("Light", exact=True, wait=1.5)
    shot(folder, "07-appearance", "Light", "Dark", "Soft Lavender", "The light themes.")
    check("in Light, the page shows the light themes and not the dark",
          not screen_says("Soft Lavender", "Warm Cream") and screen_says("Midnight Purple") == ["Midnight Purple"])
    # The change dissolves: captured as fast as the emulator can while Dark
    # is tapped, the page passes through a shade between the light one and
    # the dark one. Cut in one frame, as it was, no capture can hold one.
    # Watched at ten times its length: 320 ms held about three captures
    # on a warm emulator and none on one just started; at five times, on an
    # emulator just started, one capture still took longer than the whole
    # fade (2026-10-23, [242, 242, 35, ...]).
    with animations_slowed(10):
        # Enough captures to outlast the slowed fade on a warm emulator too,
        # so the last one is the dark page the fade ends on.
        shades = shades_while_tapping("Dark", frames=40)
    between = [s for s in shades[1:-1] if shades[0] - 40 > s > shades[-1] + 40]
    check("Light to Dark dissolves: the page passes through shades between the two",
          len(shades) >= 4 and bool(between), "page shades %s" % shades)
    time.sleep(1.0)
    settings = read_file("settings.json") or {}
    check("Dark puts the app in the dark original at once, and shows the dark themes",
          settings.get("theme") == "dark"
          and not screen_says("Midnight Purple", "Warm Charcoal", "The dark themes.")
          and screen_says("Soft Lavender") == ["Soft Lavender"], json.dumps(settings)[:200])
    tap("Forest Green", wait=1.5)
    settings = read_file("settings.json") or {}
    check("a dark tile is the theme worn, and the dark mode's last",
          settings.get("theme") == "dark_forest" and settings.get("phone_theme_dark") == "dark_forest")
    shot(folder, "07b-appearance-dark-side", "Forest Green", "Light")
    tap("Light", exact=True, wait=1.5)
    tap("Dark", exact=True, wait=1.5)
    settings = read_file("settings.json") or {}
    check("Light, then Dark, comes back to the dark theme worn last", settings.get("theme") == "dark_forest")
    tap("Ocean Blue", wait=1.5)
    settings = read_file("settings.json") or {}
    check("a theme picked is the desktop's own key", settings.get("theme") == "dark_ocean")
    # The phone turning light no longer turns the app.
    adb("shell", "cmd", "uimode", "night", "yes")
    time.sleep(1.5)
    adb("shell", "cmd", "uimode", "night", "no")
    time.sleep(1.5)
    settings = read_file("settings.json") or {}
    check("the phone's own dark mode, turned on and off, leaves the theme alone",
          settings.get("theme") == "dark_ocean" and not screen_says("Ocean Blue", "Forest Green"))
    shot(folder, "08-ocean-blue", "Ocean Blue")
    back_to("Your name", "Appearance")
    tap("Your name")
    tap("Name", exact=True)
    type_text("Maria")
    tap("Save", exact=True)
    settings = read_file("settings.json") or {}
    check("the name is saved under the desktop's key", settings.get("username") == "Maria")
    # Said under the box, above the keyboard: said at the foot of the screen
    # it was under the keys (the owner's phone, 2026-10-23).
    said, keyboard = bounds("✓ Saved"), bar_frame("ime")
    check("...and says so under the box, in sight above the keyboard",
          len(said) == 1 and keyboard is not None and keyboard[0] > 0 and said[0][3] <= keyboard[0],
          "Saved %s, the keyboard from %s" % (said, keyboard))
    # The keyboard covers the bar at the bottom: down first. And the page
    # itself says "Hi Maria," in its explanation, so the greeting is looked
    # for together with the list's own search box.
    keyboard_down()
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
    # Waited for, as the brand-new notebook is: the copy is made at start.
    check_settled("the older folder's notebook is copied in, and no welcome note is added",
                  lambda: [n.get("uid") for n in read_file("notes_data.json") or []],
                  lambda uids: uids == ["walk-bread", "walk-budget", "walk-rescued"], within=12.0, first=0.0)
    shot(folder, "10-copied-in", "Bread", "October budget", "Meeting notes", "brought into")

    # A table, edited from one cell.
    open_note("October budget")
    shot(folder, "11-table", "October budget", "rent", "1500")
    # Written right in the cell, as Samsung Notes writes it: tapped, the
    # cell takes the cursor at its end.
    tap("1500", wait=1.2)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(4):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    type_text("1600")
    check_settled("a cell edited on the phone is in the file, the table still a table",
                  lambda: (note_by_uid("walk-budget") or {}).get("body", ""),
                  lambda b: "| rent |" in b and "1600" in b and "1500" not in b)
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

    # The handle drags at once: a short swipe, no long press, from yeast's
    # handle to above flour -- it waited for a long press nobody could guess
    # (the owner's case 38).
    handle, target = find("Drag to move yeast"), find("flour", exact=True)
    if handle and target:
        adb("shell", "input", "swipe", str(handle[0]), str(handle[1]), str(handle[0]), str(target[1] - 30), "300")
        time.sleep(1.5)
    check_settled("a checklist row dragged by its handle at once, no long press, lands where it is let go",
                  lambda: [r.get("text") for r in (note_by_uid("walk-bread") or {}).get("checklist", [])],
                  lambda order: order == ["yeast", "flour"])
    # The note's sections fold away to make room for the others, and open again.
    tap("CHECKLIST", wait=1.0)
    check("the checklist folds away under its label, its rows out of sight",
          find("Drag to move yeast") is None and find("CHECKLIST") is not None)
    tap("TEXT", wait=1.0)
    check("the text folds away too, once there is a checklist to make room for",
          find("Knead ten minutes") is None and find("TEXT") is not None)
    shot(folder, "12b-folded", "TEXT", "CHECKLIST")
    tap("TEXT", wait=1.0)
    tap("CHECKLIST", wait=1.0)
    check("and both open again", find("Knead ten minutes") is not None and find("Drag to move yeast") is not None)

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
    # Replaced, not shrunk: the whole text selected and a new line typed
    # before the note saves -- 34 characters shorter, under the old bar of
    # 40, so no copy and the words gone (the desktop's test case 692,
    # 2026-10-24). Counted by what is gone, 44 characters: a copy.
    tap("Decisions", wait=1.0)
    adb("shell", "input", "keycombination", "113", "29")       # Ctrl+A
    time.sleep(0.6)
    type_text("A new line")
    keyboard_down()
    back_to("Search notes")
    replaced = settle(lambda: note_by_uid("walk-rescued") or {},
                      lambda n: isinstance(n.get("_prev"), dict))
    check("a note's whole text replaced by a new line keeps its words for Restore previous",
          (replaced.get("_prev") or {}).get("body", "").startswith("Decisions")
          and replaced.get("body", "").lower() == "a new line", str(replaced)[:200])
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
        # The one note offered to open, as the desktop opens it -- read
        # once and tapped at once: the offer does not wait for long.
        opened = tap("Open", exact=True, wait=1.5, patience=2.0)
        check("a code the desktop made is read and added", note_by_uid("from-desktop") is not None)
        check("...and the one note that came is offered to open, and opens",
              opened and find("ⓘ", exact=True) is not None and not screen_says("From the desktop"),
              str(screen_says("From the desktop")))

    # Trash, from Settings.
    back_to("Search notes")
    long_press("From the desktop") if find("From the desktop") else long_press("Bread")
    tap("🗑", wait=1.0)
    tap("Settings", exact=True)
    tap("Trash")
    shot(folder, "16-trash", "Kept for", "days left")
    # A backup at hand on the Trash page: the phone's own "save as", which
    # offers Google Drive where Drive is signed in. Cancelled, nothing saved.
    if tap("Save a backup — the Trash too", wait=1.0):
        saving = picker_save_button() is not None
        picker = foreground()
        check("the Trash page's backup opens the phone's own save as", picker != PACKAGE and saving,
              "%s, a Save button: %s" % (picker, saving))
        # Back, then the app waited for before another: the activity on top
        # was read as the picker's a moment after it had gone, and a second
        # Back left the Trash page.
        for _ in range(2):
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            deadline = time.time() + 5.0
            while foreground() != PACKAGE and time.time() < deadline:
                time.sleep(0.5)
            if foreground() == PACKAGE:
                break
        time.sleep(1.0)
        check("...and cancelled, the Trash page is back", not screen_says("Kept for"), str(screen_says("Kept for")))
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
        return {name for name in downloads_list() if name.endswith(".zip")}

    # The backups of walks before this one, gone first: the picker stops
    # at 32 files of one name (clear_from_downloads).
    walk_backups = r"NotesBackup_\d{4}-\d\d-\d\d( \(\d+\))?\.zip"
    clear_from_downloads(walk_backups)
    earlier = backups()
    back_to("Your name")
    tap("Backup")
    tap("Save a backup", exact=True, wait=1.0)
    button = picker_save_button()
    if button:
        tap(button, exact=True, wait=1.0)
    else:
        fail("the phone's picker offered no Save")
    settings = settle(lambda: read_file("settings.json") or {}, lambda s: bool(s.get("last_export_at")))
    check("an export through the picker is remembered as the last", bool(settings.get("last_export_at")))
    made = settle(lambda: sorted(backups() - earlier), lambda m: len(m) == 1)
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
    back_to("Backup", "Save a backup")
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
    left = clear_from_downloads(walk_backups)
    check("...and the walk takes its backup out of Downloads again", not left, str(left))


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

    # A note shared out as a code: Share, then Android's own sheet over the app.
    back_to("Search notes")
    tap("Bread", exact=True, wait=1.5)
    tap("Share", exact=True, wait=1.5)
    tap("Share code", exact=True, wait=2.5)
    check("sharing a note opens Android's share sheet", foreground() != PACKAGE, foreground())
    shot(folder, "19-share-sheet")
    back(1.5)
    back_to("Search notes")

    # About: the privacy policy, which Google Play asks to be reachable in
    # the app, and -- feedback being a review on Google Play -- no Feedback
    # section until the app is published there (Links.STORE).
    tap("Settings", exact=True)
    tap("About")
    missing = screen_says("Free software under the MIT license, provided as is.")
    check("About says the app is free software under the MIT license, provided as is",
          not missing, ", ".join(missing))
    if find("Read", True) is None:
        adb("shell", "input", "swipe", "540", "1900", "540", "900", "300")
        time.sleep(0.8)
    check("before the app is on Google Play, About has no Feedback section",
          find("Write a review", True) is None and find("FEEDBACK", True) is None)
    shot(folder, "69-about-privacy-policy", "PRIVACY POLICY", "Read")
    tap("Read", exact=True, wait=4.0)
    opened = foreground()
    check("Privacy policy → Read opens a browser, not the app", opened not in ("", PACKAGE), opened)
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


def downloads_list():
    """The names in the emulator's Downloads, one per line as ls gives them."""
    listing = adb("shell", "ls", "/sdcard/Download")
    return {line.strip() for line in listing.splitlines() if line.strip()}


def clear_from_downloads(keep_out):
    """Every file in the emulator's Downloads whose name [keep_out] matches,
    removed: what a walk saved there through the picker, once it has been
    read back. Android's picker names a second file of the same name
    "name (1)", and so on up to 32 -- then refuses the next, its Save
    pressed and doing nothing. Every walk since the export part was
    written had left its two files, and by 2026-10-24 there were 32 of
    each: that run failed six checks on it, the app not at fault."""
    for name in sorted(downloads_list()):
        if re.fullmatch(keep_out, name):
            adb("shell", "rm", "-f", "'/sdcard/Download/%s'" % name)
    return [name for name in downloads_list() if re.fullmatch(keep_out, name)]


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
    # Looked for until it is there, not once after start_app's pause: the
    # first start after an install drew the list later than that, and a
    # seed that was on screen a moment afterwards was reported as missing.
    deadline = time.time() + 10.0
    while screen_says(shows) and time.time() < deadline:
        time.sleep(0.5)
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
    check("the calendar opens on this month", not says_soon(LONG_MONTHS[today.month - 1] + " " + str(today.year)))
    check("a day the series lands on says it holds a note", not says_soon(said(second) + ", 1 note"))
    shot(folder, "24-calendar", LONG_MONTHS[today.month - 1])

    # One day of the series, on its own.
    tap(said(second), wait=1.0)
    open_note("Stand-up")
    check("a day of a series opens in day mode", not screen_says("Only this day"))
    shot(folder, "25-day-mode", "Only this day", "Whole series")
    tap("Stand-up", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" room 2")

    def day_alone(series):
        own = (series.get("overrides") or {}).get(second.isoformat()) or {}
        return own.get("title") == "Stand-up room 2" and series.get("title") == "Stand-up"

    series = settle(lambda: note_by_uid("cal-standup") or {}, day_alone)
    check("the day's own title is written to that day alone", day_alone(series), json.dumps(series)[:240])
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

    # The same day marked: the marker under the day's number, where the corner
    # put it over the number on a phone's narrow cell (the owner's case 60).
    # Where it sits is for the photograph: the cell is one node to the phone's
    # tree, its number and markers inside it unseen apart.
    long_press(said(other))
    tap("Mark this day", wait=1.2)
    tap("⭐", exact=True, wait=0.8)
    tap("Save", exact=True, wait=1.5)
    settings = read_file("settings.json") or {}
    check("a marked day is kept under the desktop's key",
          (settings.get("day_emoji") or {}).get(other.isoformat()) == ["⭐"], json.dumps(settings)[:200])
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
    # Select all takes what shows: the folded group's two notes are counted
    # in its header, not chosen -- chosen, the Trash button sent them away
    # unseen, as the desktop's _visible_note_ids learnt.
    # The one note in sight, chosen, is all there is to choose: the menu
    # offers Unselect all. Counting the folded group's two, it offered
    # Select all, and took three.
    long_press("Plain one")
    chosen = find("1 selected", exact=True) is not None
    tap("⋮", exact=True, wait=1.0)               # its menu is a window of its own
    check("with a group folded, the one note in sight is all Select all would take",
          chosen and find("Unselect all", exact=True) is not None,
          " | ".join(t for t, d, _ in nodes() if t)[:200])
    back(wait=0.8)
    tap("✕", exact=True, wait=1.0)


def list_keeps_place(folder):
    """The list keeps its place, and its search, while a note is open. On a
    phone the note takes the list's place, and the list built again on Back
    came back at its top with the search box empty (the owner's report)."""
    print("\n-- the list keeps its place while a note is open --")
    notes = [{"id": i, "uid": "kp-%02d" % i, "title": "Card %02d" % i, "category": "General",
              "body": "words %02d" % i, "updated": "2026-10-%02d 09:00:00" % i} for i in range(1, 31)]
    if not seed_inside(notes, {"welcome_offered": True}, "Card 30"):
        return

    def cards():
        return [t for t, d, _ in nodes() if t.startswith("Card ")]

    for _ in range(3):
        adb("shell", "input", "swipe", "540", "1900", "540", "700", "300")
        time.sleep(0.8)
    before = cards()
    if len(before) < 2 or "Card 30" in before:
        fail("the list did not scroll down: %s" % before[:4])
        return
    target = before[1]
    if not open_note(target):
        return
    back_to("Show: All")
    time.sleep(1.0)
    after = cards()
    check("Back from a note brings the list back where it was, %s at its top" % before[0],
          after[:1] == before[:1] and target in after, "before %s, after %s" % (before[:3], after[:3]))
    shot(folder, "31-list-kept-its-place", target)

    # Searched with the list still scrolled down: a new search starts its
    # results at the top. Kept in place, they opened at whichever card the
    # list had stopped on, and "Card 15" was above the screen in one walk of
    # three (2026-10-21); the owner chose the top, on both apps.
    tap("Search notes", wait=0.8)
    type_text("Card 1")
    keyboard_down()
    # The search box's own words read "Card 1" too: left out. Waited for:
    # the list drawn again at the top is read mid-redraw on a first start.
    check_settled("a new search shows its results from the top, Card 19 first",
                  lambda: [t for t in cards() if t != "Card 1"],
                  lambda found: found[:1] == ["Card 19"], within=6.0, first=0.5)
    if not open_note("Card 15"):
        return
    back_to("Show: All")
    time.sleep(1.0)
    found = cards()
    check("...and a search is still there, its results with it",
          find("Card 1", exact=True) is not None and "Card 15" in found and "Card 30" not in found,
          " | ".join(found)[:160])


def previews(folder):
    """A note's card and its calendar row say a table is there and never
    draw it -- rows left open or typed past included, which the strict row
    rule once let through as "only vertical lines" (field-reported from a
    real phone). The desktop's preview_text rule."""
    from datetime import date
    print("\n-- previews without tables --")
    today = date.today().isoformat()
    body = "Packing\n| Item | Qty\n| socks | 3 |and more\nDone soon"
    if not seed_inside([
        {"id": 1, "uid": "pv-trip", "title": "Trip", "body": body, "date": today,
         "created": "2026-10-16 09:00:00", "updated": "2026-10-16 09:00:00"},
        {"id": 2, "uid": "pv-grid", "title": "", "body": "| a | b |\n| c | d |",
         "created": "2026-10-16 09:00:01", "updated": "2026-10-16 09:00:01"},
    ], {"welcome_offered": True}, "Trip"):
        return
    seen = [t for t, d, _ in nodes() if t]
    card = [t for t in seen if "Packing" in t or "Done soon" in t]
    check("the card shows the note's words, no table line among them",
          bool(card) and not any("|" in t for t in seen), repr(seen[:12]))
    check("and says the table is there", any("▦ 1 table" in t for t in seen), repr(seen[:12]))
    check("an untitled note of nothing but a table is untitled, not empty",
          "(untitled)" in seen and "(empty)" not in seen, repr(seen[:12]))
    shot(folder, "57-previews", "Trip", "▦ 1 table")
    tap("Calendar", exact=True, wait=1.5)
    # Looked for until it is drawn, not read once: on a full run the month
    # was in the tree and the day's row under it not yet (2026-10-24).
    row = settle(lambda: [t for t, d, _ in nodes() if t and "▦×1" in t], bool, within=6.0, first=0)
    check("the note's row on the calendar says its table too", bool(row),
          repr([t for t, d, _ in nodes() if t][:40]))
    tap("Notes", exact=True, wait=1.0)


def large_letters_table(folder):
    """A table drawn at the phone's larger text sizes keeps each word on
    one line: a cell's width was a fixed number while its letters grew,
    and at 115% -- a real phone's setting -- every short word lost its last
    letter to a second line. A six-letter cell must stand as tall as a
    one-letter one."""
    print("\n-- a table at larger text sizes --")
    body = "\n".join(["| x | socket |", "| --- | --- |", "| a | bridge |"])
    if not seed_inside([{"id": 1, "uid": "ll-table", "title": "Sizes", "body": body,
                         "created": "2026-10-16 09:00:00", "updated": "2026-10-16 09:00:00"}],
                       {"welcome_offered": True}, "Sizes"):
        return
    for scale in ("1.15", "1.3"):
        adb("shell", "settings", "put", "system", "font_scale", scale)
        time.sleep(2.0)
        open_note("Sizes")
        short = bounds("x") + bounds("a")
        long = bounds("socket") + bounds("bridge")
        heights = [b[3] - b[1] for b in short + long]
        check("at %d%% every cell is one line tall" % round(float(scale) * 100),
              len(heights) == 4 and max(heights) - min(heights) <= 4, repr(short + long))
        if scale == "1.15":
            shot(folder, "58-table-larger-letters", "socket", "bridge")
        back_to("Search notes")
    adb("shell", "settings", "put", "system", "font_scale", "1.0")
    time.sleep(1.5)


def changes(folder):
    """The phone changing under an OPEN note: going dark (the app no longer
    follows it, but still receives it), larger letters, turned on its side.
    Each is a configuration
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


def calculator_in_cell(folder):
    """A cell being written in, the calculator opened from the menu, Insert:
    the answer in the cell, as the desktop puts it there -- the phone put it
    on a line under the table."""
    print("\n-- the calculator's answer in a cell --")
    if not seed_inside([
        {"id": 1, "uid": "walk-cell-sum", "title": "Costs", "body": "Costs\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |",
         "category": "Personal", "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:00:00"},
    ], {"welcome_offered": True}, "Costs"):
        return
    choose_cell("Costs", "120")
    tap("⋮", exact=True)
    tap("🧮 Calculator", wait=1.2)
    keyboard_down()
    for key in ("4", "+", "5", "="):
        tap(key, exact=True, wait=0.3)
    tap("⬇ Insert into note", wait=1.2)
    body = (note_by_uid("walk-cell-sum") or {}).get("body", "")
    rows = body.split("\n")
    check("the calculator's answer goes into the cell being written in, not under the table",
          len(rows) == 4 and "4+5=9" in rows[3].split("|")[2], repr(body))
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
    # Down for certain, then looked for until it is there: one look 1.5 s
    # after Back, on an emulator just started, came before the keyboard
    # had gone (2026-10-23).
    keyboard_down()
    check_settled("and is back when the keyboard is down", lambda: screen_says(insert), lambda m: not m)

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
         "checklist": [{"text": "buy milk", "done": False}], "comments": "milk from the farm",
         "category": "Personal", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Shopping")
    open_note("Shopping")
    # Folded before the search: the search opens it, its words being found too.
    tap("CHECKLIST", wait=1.0)
    tap("⋮", exact=True)
    tap("🔍 Find in note", wait=1.2)
    type_text("milk")
    time.sleep(0.8)
    check("two in the prose, two cells, one after the table, the checklist's row, the comments: 1/7",
          bool(find("1/7", exact=True)), " | ".join(t for t, d, _ in nodes() if t)[:200])
    shot(folder, "34-find", "1/7")
    tap("Next match", wait=0.6)
    tap("Next match", wait=0.8)
    check("the third stop is the table's first matching cell", bool(find("3/7", exact=True)))
    shot(folder, "35-find-cell", "3/7")
    for _ in range(3):
        tap("Next match", wait=0.8)
    check("the sixth stop is the checklist's row, shown while the search is open though it was folded",
          bool(find("6/7", exact=True)) and bool(find("buy milk", exact=True)))
    shot(folder, "35b-find-checklist", "6/7", "buy milk")
    tap("Next match", wait=0.8)
    check("the seventh, the comments", bool(find("7/7", exact=True)))
    tap("Match case", wait=0.8)
    check("with case on, Milk and MILK drop out: five", bool(find("5/5", exact=True)))
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

    # The files of walks before this one, gone first: 32 of them had
    # filled every name the picker would give (clear_from_downloads).
    walk_exports = r"Trip_ day 1_2( \(\d+\))?\.(html|txt)"
    clear_from_downloads(walk_exports)
    downloads = downloads_list
    desktop = desktop_export()
    for fmt, label, ext in (("html", "Web page (.html)", ".html"), ("txt", "Plain text (.txt)", ".txt")):
        earlier = downloads()
        open_note("Trip: day 1/2")
        tap("⋮", exact=True)
        tap("💾 Export as file", wait=1.2)
        tap(label, wait=1.0)
        button = picker_save_button()
        check("the picker offers the desktop's file name (%s)" % fmt, bool(find("Trip_ day 1_2" + ext)),
              " | ".join(t for t, d, _ in nodes() if t)[:200])
        if button:
            tap(button, exact=True, wait=1.0)
        else:
            fail("the phone's picker offered no Save (%s)" % fmt)
        made = settle(lambda: sorted(f for f in downloads() - earlier if f.endswith(ext)), lambda m: len(m) == 1)
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
    left = clear_from_downloads(walk_exports)
    check("the walk takes its exported files out of Downloads again", not left, str(left))


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
    # Its table as a table, never as pipe rows above one: the desktop's
    # template editor hides the rows from the body box.
    words = [t for t, d, _ in nodes() if t]
    check("the template's body shows its words and its table's cells, no pipe rows",
          not any("|" in t for t in words) and "Mon" in words and "gym" in words
          and any("call mum" in t for t in words), repr(words[:16]))
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
    # The size key says the size of what is selected: "Default" over words
    # that have none, the number once one is set -- as the desktop's box.
    check("over words with no size of their own the size key says Default",
          find("Size: Default", exact=True) is not None, repr([d for t, d, _ in nodes() if d][:12]))
    tap("Size: Default", exact=True, wait=0.8)
    tap("20", exact=True, wait=0.8)
    check("and once a size is set, the key says it", find("Size: 20", exact=True) is not None,
          repr([d for t, d, _ in nodes() if d][:12]))
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
    # Lower case: the keyboard capitalises a line's first word as it likes.
    check_settled("numbered, continued by Enter, left by Enter on an empty item",
                  lambda: note().get("body") or "",
                  lambda b: b.lower() == "1. milk\n2. bread\n3. eggs\n4. jam\nafter")

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
    # The first cell written in place, the table's bar over the keyboard.
    check("the new table's first cell is open for typing, the table's bar at hand",
          find("Cell", exact=True) is not None and not screen_says("Row", "Column", "More"))
    type_text("Day")
    back_to("Search notes")
    body = ((read_file("notes_data.json") or [{}])[0]).get("body") or ""
    lines = body.split("\n")
    check("the table sits on its own lines under the text, its first cell written",
          lines[0] == "intro" and len(lines) == 4 and lines[1].startswith("| Day") and lines[3] == "",
          repr(body))


def table_bar(folder):
    """The table written in place, as Samsung Notes writes one: words typed
    right in a cell, spaces and all, and one Undo for them; the bar's Row,
    Column, Align, Box, Split and Delete -- each read back from the file."""
    print("\n-- a table written in place --")
    body = "\n".join(["| Item | Cost |", "| --- | --- |", "| fuel | 120 |", "| tolls | 45 |"])
    if not seed_inside([{"id": 1, "uid": "tb-1", "title": "Bar", "body": body,
                         "created": "2026-10-19 09:00:00", "updated": "2026-10-19 09:00:00"}],
                       {"welcome_offered": True}, "Bar"):
        return

    def rows():
        lines = ((note_by_uid("tb-1") or {}).get("body") or "").split("\n")
        return [[c.strip() for c in l.strip().strip("|").split("|")] for l in lines if l.strip().startswith("|")]

    def right_aligned(line):
        cell = line.split("|")[2] if line.count("|") > 2 else ""
        return cell.strip() == "120" and len(cell) - len(cell.lstrip()) > len(cell) - len(cell.rstrip())

    open_note("Bar")
    tap("fuel", exact=True, wait=1.2)
    type_text(" and oil")
    check_settled("words typed right in the cell, the spaces between them kept",
                  rows, lambda r: ["fuel and oil", "120"] in r)
    shot(folder, "63-cell-written", "fuel and oil", "Row", "More")
    keyboard_down()
    tap("Undo", exact=True, wait=1.5)
    check_settled("one Undo takes back the words typed in the cell, all of them", rows, lambda r: ["fuel", "120"] in r)
    # The cell's field keeps its own copy of the words: after Undo it must
    # start again from the file's, or the next letter writes back the words
    # just taken away.
    type_text("s")
    check_settled("the next letter after Undo goes after the cell's words as they are now",
                  rows, lambda r: ["fuels", "120"] in r)
    keyboard_down()
    tap("Undo", exact=True, wait=1.5)

    choose_cell("Bar", "fuel")
    tap("Row", exact=True, wait=0.8)
    tap("Above", exact=True, wait=1.2)
    type_text("first")
    check_settled("Row, Above: a row over the cell, and it is the one written in next",
                  rows, lambda r: [x[0] for x in r] == ["Item", "---", "first", "fuel", "tolls"])
    choose_cell("Bar", "120")
    tap("Column", exact=True, wait=0.8)
    tap("Right", exact=True, wait=1.2)
    type_text("EUR")
    check_settled("Column, Right: a column after the cell, written in next",
                  rows, lambda r: ["fuel", "120", "EUR"] in r and len(r[0]) == 3)
    choose_cell("Bar", "120")
    tap("Align", exact=True, wait=0.8)
    tap("Right", exact=True, wait=1.2)
    check_settled("Align, Right: the cell's padding puts its words at the right -- more space before them than after",
                  lambda: next((l for l in ((note_by_uid("tb-1") or {}).get("body") or "").split("\n") if "120" in l), ""),
                  right_aligned)
    choose_cell("Bar", "EUR")
    tap("Box", exact=True, wait=1.2)
    check_settled("Box: the cell becomes a checkbox", rows, lambda r: ["fuel", "120", "[ ]"] in r)
    tap("☐", exact=True, wait=1.2)
    check_settled("a tap on the box ticks it", rows, lambda r: ["fuel", "120", "[x]"] in r)
    shot(folder, "64-box-ticked", "☑")
    choose_cell("Bar", "tolls")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell above", exact=True, wait=1.2)
    # Both cells' words are kept, joined in the cell kept, as the desktop's
    # merge joins them: the phone's first merge lost the chosen cell's.
    check_settled("Merge, with the cell above: the desktop's ^, both cells' words in the cell kept",
                  rows, lambda r: len(r) > 4 and r[-1][0] == "^" and r[3][0] == "fuel tolls")
    shot(folder, "65-merged-down", "fuel tolls")
    choose_cell("Bar", "fuel tolls")
    tap("Merge", exact=True, wait=0.8)
    tap("Split", exact=True, wait=1.2)
    check_settled("Split takes the marker away, the words staying where they were joined",
                  rows, lambda r: len(r) > 4 and r[-1][0] == "" and r[3][0] == "fuel tolls")
    # Every way round, as the desktop's selection merges (the owner's case 72):
    # with the cell on the right, and with the one below -- each split again.
    choose_cell("Bar", "first")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell on the right", exact=True, wait=1.2)
    check_settled("Merge, with the cell on the right: the desktop's < beside the cell kept",
                  rows, lambda r: len(r) > 2 and r[2][:2] == ["first", "<"])
    choose_cell("Bar", "first")
    tap("Merge", exact=True, wait=0.8)
    tap("Split", exact=True, wait=1.2)
    choose_cell("Bar", "120")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell below", exact=True, wait=1.2)
    check_settled("Merge, with the cell below: both cells' words in the cell kept, the desktop's ^ under it",
                  rows, lambda r: len(r) > 4 and r[3][1] == "120 45" and r[4][1] == "^")
    choose_cell("Bar", "120 45")
    tap("Merge", exact=True, wait=0.8)
    tap("Split", exact=True, wait=1.2)
    choose_cell("Bar", "first")
    tap("Delete", exact=True, wait=0.8)
    tap("This row", exact=True, wait=1.2)
    check_settled("Delete, This row", rows, lambda r: [x[0] for x in r] == ["Item", "---", "fuel tolls", ""])
    choose_cell("Bar", "Cost")
    tap("Delete", exact=True, wait=0.8)
    tap("This column", exact=True, wait=1.2)
    check_settled("Delete, This column", rows, lambda r: bool(r) and r[0][:2] == ["Item", ""])
    back_to("Search notes")


def table_resize(folder):
    """A table changed by a finger on its edges, as the desktop's lines are
    dragged: the chosen cell's right handle makes its column wider -- the
    separator's dashes -- and one Undo takes it back; its bottom handle
    makes the row taller -- an empty <br> line in its tallest cell -- and
    dragged back up the line goes, the words staying. Then a merge down,
    photographed: one cell, both cells' words in the one kept."""
    print("\n-- a table's edges dragged, a merge down --")
    body = "\n".join(["| Item | Cost |", "| --- | --- |", "| fuel | 120 |", "| tolls | 45 |"])
    if not seed_inside([{"id": 1, "uid": "rs-1", "title": "Edges", "body": body,
                         "created": "2026-10-19 09:00:00", "updated": "2026-10-19 09:00:00"}],
                       {"welcome_offered": True}, "Edges"):
        return

    def lines():
        return ((note_by_uid("rs-1") or {}).get("body") or "").split("\n")

    def dashes(l):
        return l[1].split("|")[1].strip() if len(l) > 1 and l[1].count("|") > 1 else ""

    def drag(label, dx, dy):
        at = find(label)
        if not at:
            fail("drag %r: not on the screen" % label)
            return
        adb("shell", "input", "swipe", str(at[0]), str(at[1]), str(at[0] + dx), str(at[1] + dy), "900")
        time.sleep(0.8)

    choose_cell("Edges", "fuel")
    check("the chosen cell shows its edges' handles",
          find("Drag to change the column's width") is not None and find("Drag to change the row's height") is not None)
    shot(folder, "66-handles", "fuel")
    drag("Drag to change the column's width", 220, 0)
    check_settled("dragged right, the column is wider: the separator's dashes", lines, lambda l: len(dashes(l)) >= 12)
    tap("Undo", exact=True, wait=1.2)
    check_settled("one Undo takes the width back", lines, lambda l: dashes(l) == "---")
    choose_cell("Edges", "fuel")
    drag("Drag to change the row's height", 0, 160)
    check_settled("dragged down, the row is taller: an empty line in its tallest cell",
                  lines, lambda l: len(l) > 2 and "<br>" in l[2] and "fuel" in l[2])
    shot(folder, "67-row-taller", "fuel")
    drag("Drag to change the row's height", 0, -400)
    check_settled("dragged back up, the empty lines go and the words stay",
                  lines, lambda l: len(l) > 2 and "<br>" not in l[2] and "fuel" in l[2])

    # "<5" typed: on its way, the cell holds "<" alone -- a merge's marker,
    # which written at once turned the cell into a merge under the finger.
    choose_cell("Edges", "120")
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(3):
        adb("shell", "input", "keyevent", "KEYCODE_DEL")
    # One key at a time: typed as one, "<5" reaches the field in one change
    # and the "<" alone is never there to be written (a mutant survived it).
    adb("shell", "input", "text", "'<'")              # quoted: "<" is the shell's
    time.sleep(1.5)
    check("a lone \"<\" typed leaves the cell a cell, its field still there",
          find("Cell", exact=True) is not None and "<" not in (lines()[2].split("|")[2] if len(lines()) > 2 else ""),
          repr(lines()[:3]))
    adb("shell", "input", "text", "5")
    check_settled("words that begin as a merge's marker are written as words",
                  lines, lambda l: len(l) > 2 and l[2].split("|")[2].strip() == "<5")

    choose_cell("Edges", "tolls")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell above", exact=True, wait=1.2)
    check_settled("merged down: both cells' words in the cell kept, the desktop's ^ under it",
                  lines, lambda l: len(l) > 3 and l[2].split("|")[1].strip() == "fuel tolls" and l[3].split("|")[1].strip() == "^")
    keyboard_down()
    back(wait=1.0)
    shot(folder, "68-merged-down", "fuel tolls")
    back_to("Search notes")


def choose_cell(title, cell):
    """A cell of a note's table chosen, the keyboard put down, the table's
    bar in sight -- the note opened first when the list is showing."""
    if find("Search notes"):
        open_note(title)
    elif find("More", exact=True):
        # A cell already chosen is let go of first, so the one wanted is
        # plain words on the screen: a cell being written in once reported
        # its words at its own edge, and the tap took the row above.
        keyboard_down(patience=1.0)
        back(wait=1.0)
    tap(cell, exact=True, wait=1.2)
    keyboard_down()


def cell_menu(folder):
    """The table's bar: a cell highlighted pipe to pipe, a cell merged into
    the one on its left, the table centred on the page -- and the file,
    where each is what the desktop writes. Then the table deleted, asked."""
    print("\n-- the table's bar --")
    body = "\n".join(["| Item | Cost |", "| --- | --- |", "| fuel | 120 |", "| tolls | 45 |"])
    seed_inside([
        {"id": 1, "uid": "walk-cells", "title": "Cells", "body": body,
         "category": "General", "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Cells")

    def note():
        return (read_file("notes_data.json") or [{}])[0]

    choose_cell("Cells", "120")
    tap("Color", exact=True, wait=0.8)
    tap("Highlight colour 1", wait=1.2)
    choose_cell("Cells", "45")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell on the left", exact=True, wait=1.2)
    choose_cell("Cells", "fuel")
    tap("More", exact=True, wait=1.2)
    tap("Center", exact=True, wait=1.2)
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
    check("the merge is the desktop's marker, both cells' words in the cell kept",
          len(lines) > 3 and [x.strip() for x in lines[3].strip().strip("|").split("|")] == ["tolls 45", "<"],
          repr(lines))
    check("the table is centred: its lines' alignment",
          (saved.get("paragraphs") or []) == [{"line": n, "align": "center"} for n in range(4)],
          repr(saved.get("paragraphs")))

    choose_cell("Cells", "fuel")
    tap("Delete", exact=True, wait=0.8)
    tap("The whole table", exact=True, wait=1.0)
    check("deleting the table asks first", not screen_says("Delete this table and everything in it?"))
    tap_last("Delete", wait=1.2)
    back_to("Search notes")
    gone = note().get("body")
    check("the table is gone", gone == "", repr(gone))


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
    keyboard_down()                           # the move saves what was typed

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
    # offer lasts eight seconds, and each look takes one or two. The offer
    # is the node whose WORDS are "Undo": the editor's own ↶ is said "Undo"
    # too, and finding by either, the check passed and the tap pressed ↶.
    seen = nodes()
    shown = " | ".join(part for t, d, _ in seen for part in (t, d) if part)
    offer = [centre for t, d, centre in seen if t == "Undo"]
    # The banner says where the day came from, as the desktop's strip does.
    missing = [w for w in ("Only this day: " + said(to), "moved from " + said(second), "Moved to") if w not in shown]
    check("the editor follows the day, and the move offers its Undo", not missing and bool(offer),
          str(missing + ([] if offer else ["the offer's Undo"])))
    adb("shell", "screencap", "-p", "/sdcard/shot.png")
    with open(os.path.join(folder, "48-day-moved.png"), "wb") as f:
        f.write(adb("exec-out", "cat", "/sdcard/shot.png", binary=True))
    if offer:
        adb("shell", "input", "tap", str(offer[-1][0]), str(offer[-1][1]))

    def put_back(s):
        return not (s.get("moved") or {}) and \
            ((s.get("overrides") or {}).get(second.isoformat()) or {}).get("title") == "Stand-up room 2"

    back_home = settle(series, put_back)
    check("Undo puts it back, its own edit with it", put_back(back_home),
          json.dumps({k: back_home.get(k) for k in ("moved", "overrides")}))
    back_to("📋 History")


def off_schedule(folder):
    """Days a series keeps its own edits for, which its repeat no longer
    reaches: the note says so, and walks them one question at a time -- one
    made a note of its own, one sent to the Trash -- each read back."""
    print("\n-- days off the schedule --")
    if not seed_inside([
        {"id": 1, "uid": "os-series", "title": "Yoga", "body": "mat", "category": "General",
         "date": "2026-10-06", "recurrence": {"freq": "weekly", "weekdays": [1]},
         "overrides": {"2026-10-12": {"title": "Yoga in the park"}, "2026-10-19": {"title": "Yoga at home"}},
         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
    ], {"welcome_offered": True}, "Yoga"):
        return
    # From the History, a day off the schedule opens the series -- its
    # banner leads to the questions -- never the day, as one no calendar
    # shows, to move or delete where nothing would show it again.
    tap("Calendar", exact=True, wait=1.5)
    tap("📋 History", wait=1.5)
    tap("Yoga", wait=1.2)
    tap("off the schedule", wait=2.0)
    check("a History row off the schedule opens the series, not that day",
          find("Only this day") is None and not screen_says("2 days' own edits are off the schedule"),
          " | ".join(t for t, d, _ in nodes() if t)[:200])
    back(wait=1.0)
    tap("Notes", exact=True, wait=1.5)
    open_note("Yoga")
    check("the series says two days' edits are off its schedule",
          not screen_says("2 days' own edits are off the schedule"))
    # And, above it, that its edits reach every day: the desktop's banner.
    # Both days are off the schedule, so none is counted as keeping its own.
    check("the series says its edits apply to every day, counting no day off the schedule as its own",
          not screen_says("This note repeats", "edits here apply to every day")
          and find("keep their own edits") is None and find("keeps its own edits") is None)
    tap("off the schedule", wait=1.5)
    check("one question a day, in the desktop's words", not screen_says("Day 1 of 2 off the schedule", "Yoga in the park"))
    shot(folder, "54-off-schedule", "Day 1 of 2 off the schedule")
    tap("Make it a note", exact=True, wait=1.5)
    check("then the next", not screen_says("Day 2 of 2 off the schedule", "Yoga at home"))
    tap("Move to the Trash", exact=True, wait=1.0)
    # At once: the message lasts a few seconds.
    check("and what was done is said", not screen_says("1 made a note of its own, 1 moved to the Trash"))
    notes = read_file("notes_data.json") or []
    trash = read_file("trash.json") or []
    series = next((n for n in notes if n.get("uid") == "os-series"), {})
    made = [n for n in notes if n.get("title") == "Yoga in the park"]
    check("one day a note of its own on its date, the other in the Trash, the series keeping neither",
          len(made) == 1 and made[0].get("date") == "2026-10-12"
          and [t.get("title") for t in trash] == ["Yoga at home"] and not (series.get("overrides") or {}),
          json.dumps({"made": made, "trash": [t.get("title") for t in trash]})[:240])
    # "own edits are": the summary said after says "off the schedule" too.
    check("the banner is gone once nothing is off the schedule", find("own edits are off the schedule") is None)
    back_to("Search notes")


def arranging(folder):
    """Custom order, arranged by hand: chosen from another sort, it starts
    from the order shown, every note placed; a card dragged by its handle
    lands where it was let go; and a pinned note keeps its block. Each read
    back from the file's "order" fields."""
    print("\n-- Custom order, arranged by hand --")
    if not seed_inside([
        {"id": 1, "uid": "ar-b", "title": "Bravo", "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "ar-a", "title": "Alpha", "created": "2026-10-02 09:00:00", "updated": "2026-10-02 09:00:00"},
        {"id": 3, "uid": "ar-d", "title": "Delta", "created": "2026-10-03 09:00:00", "updated": "2026-10-03 09:00:00"},
        {"id": 4, "uid": "ar-p", "title": "Pinned", "pinned": True,
         "created": "2026-10-04 09:00:00", "updated": "2026-10-04 09:00:00"},
    ], {"welcome_offered": True, "note_sort": "Title A–Z"}, "Alpha"):
        return

    def placed():
        notes = read_file("notes_data.json") or []
        return [n.get("title") for n in sorted(notes, key=lambda n: n.get("order", 99))]

    tap("Sort: Title A–Z", wait=1.0)
    tap("Custom order", exact=True, wait=1.5)
    check("Custom order starts from the order shown, every note placed",
          placed() == ["Pinned", "Alpha", "Bravo", "Delta"]
          and all("order" in n for n in read_file("notes_data.json") or []), repr(placed()))
    shot(folder, "49-custom-order", "Drag to move Delta")

    def drag(card, onto, above):
        """A card's handle dragged to [above] pixels over the card [onto] --
        a drag that cannot be aimed is a failure, not a step skipped."""
        handle, target = find("Drag to move " + card), find(onto, exact=True)
        if not handle or not target:
            fail("drag %r onto %r: not on the screen" % (card, onto))
            return
        adb("shell", "input", "swipe", str(handle[0]), str(handle[1]), str(handle[0]), str(target[1] - above), "1500")
        time.sleep(1.5)

    # The drag has waited for its card to land: read at once, then until settled.
    drag("Delta", "Alpha", 40)
    check_settled("a card dragged by its handle lands where it is let go",
                  placed, lambda p: p == ["Pinned", "Delta", "Alpha", "Bravo"], first=0)
    # Above the pinned note it may not go: it stays first among the rest.
    # (A pinned card's title starts with its pin.)
    drag("Bravo", "📌 Pinned", 60)
    check_settled("a pinned note keeps its place above the rest",
                  placed, lambda p: p == ["Pinned", "Bravo", "Delta", "Alpha"], first=0)

    # A long list: the last card held at the top edge, the list scrolling
    # under it. Its old place scrolls far out of sight, and a card that
    # stayed there would be dropped by the list -- its drag with it.
    many = [{"id": i, "uid": "ar-%02d" % i, "title": "Note %02d" % i, "order": i - 1,
             "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"} for i in range(1, 31)]
    if not seed_inside(many, {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
        return
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1700", "540", "500", "200")
        time.sleep(0.6)
    handle, top = find("Drag to move Note 30"), find("Sort: Custom order")
    if not handle or not top:
        fail("the long list: the last card or the list's top not on the screen")
        return
    adb("shell", "input", "motionevent", "DOWN", str(handle[0]), str(handle[1]))
    for y in range(handle[1], top[1] + 140, -60):
        adb("shell", "input", "motionevent", "MOVE", str(handle[0]), str(y))
    time.sleep(9)                                    # held at the edge: the list scrolls
    adb("shell", "input", "motionevent", "UP", str(handle[0]), str(top[1] + 140))
    order = settle(placed, lambda o: "Note 30" in o and o.index("Note 30") <= 2)
    check("on a long list a card held at the edge rides the scroll to the top",
          "Note 30" in order and order.index("Note 30") <= 2
          and sorted(order) == ["Note %02d" % i for i in range(1, 31)], repr(order[:5]))


def attaching(folder):
    """Files attached from Android's own picker and taken off again: the
    record and the stored copy read back off the phone, under a name the
    desktop's folder takes; a removal that asks first, and deletes."""
    print("\n-- attaching files --")
    local = os.path.join(folder, "walk-receipt.txt")
    with open(local, "w", encoding="utf-8") as f:
        f.write("RECEIPT 120")
    adb("push", local, "/sdcard/Download/walk-receipt.txt")
    if not seed_inside([{"id": 1, "uid": "at-trip", "title": "Trip", "body": "fuel",
                         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"}],
                       {"welcome_offered": True}, "Trip"):
        return

    def trip():
        return note_by_uid("at-trip") or {}

    open_note("Trip")
    # Adding to a note lives in ⋮ (2026-10-23): under the text, nothing
    # offers it any more.
    check("no link under the note offers a checklist, comments or files; ⋮ does",
          find("+ Attach files") is None and find("+ Add a checklist") is None and find("+ Add comments") is None)
    tap("⋮", exact=True, wait=1.0)
    tap("📎 Attach files", exact=True, wait=3.0)
    tap("walk-receipt.txt", exact=True, wait=3.0)
    atts = trip().get("attachments") or []
    stored = atts[0].get("stored_as", "") if atts else ""
    check("a file picked is attached under its own name, kept under a stamped one",
          len(atts) == 1 and atts[0].get("filename") == "walk-receipt.txt"
          and re.fullmatch(r"\d{20}_walk-receipt\.txt", stored) is not None, json.dumps(atts))
    kept = adb("exec-out", "run-as", PACKAGE, "cat", "%s/attachments/%s" % (NOTEBOOK, stored), binary=True)
    check("its bytes are in the attachments folder", kept == b"RECEIPT 120", repr(kept[:40]))
    shot(folder, "50-attached", "walk-receipt.txt", "Remove walk-receipt.txt")

    tap("Remove walk-receipt.txt", wait=1.2)
    check("removing a file asks first, and says the copy goes", not screen_says("can't be undone"))
    tap_last("Remove", wait=1.5)
    gone = adb("shell", "run-as", PACKAGE, "ls", "%s/attachments" % NOTEBOOK)
    check("removed: off the note, and its stored copy deleted",
          not (trip().get("attachments") or []) and stored and stored not in gone, gone[:200])
    back_to("Search notes")


def table_extras(folder):
    """A table's caption, its columns' widths and a line above it: each
    from the table's More, each read back from the file in the desktop's
    shape -- the caption numbered and placed with its table, a width as
    the separator's dashes, a hidden column as its "~"."""
    print("\n-- captions, column widths, a line above --")
    body = "\n".join(["| Item | Cost |", "| --- | ---: |", "| fuel | 120 |"])
    if not seed_inside([
        {"id": 1, "uid": "tx-costs", "title": "Costs", "body": body,
         "paragraphs": [{"line": n, "align": "center"} for n in range(3)],
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
    ], {"welcome_offered": True}, "Costs"):
        return

    def note():
        return note_by_uid("tx-costs") or {}

    def menu(cell, entry):
        """A cell chosen, the bar's More opened, [entry] scrolled into sight and tapped."""
        choose_cell("Costs", cell)
        tap("More", exact=True, wait=1.2)
        for _ in range(3):
            if find(entry, exact=True):
                break
            adb("shell", "input", "swipe", "540", "2100", "540", "1300", "400")
            time.sleep(0.8)
        tap(entry, exact=True, wait=1.5)

    def body():
        return note().get("body") or ""

    def separator():
        lines = body().split("\n")
        return lines[1] if len(lines) > 1 else ""

    menu("fuel", "Add a caption below")
    type_text("Fuel costs")                  # typed over the selected placeholder
    keyboard_down()
    captioned = settle(note, lambda n: (n.get("body") or "").split("\n")[3:5] == ["Table 1: Fuel costs", "Source: "])
    check("a caption goes under its table, numbered, its placeholder typed over",
          (captioned.get("body") or "").split("\n")[3:5] == ["Table 1: Fuel costs", "Source: "],
          repr((captioned.get("body") or "").split("\n")))
    paragraphs = captioned.get("paragraphs") or []
    check("the caption is placed as its table is",
          {"line": 3, "align": "center"} in paragraphs and {"line": 4, "align": "center"} in paragraphs, repr(paragraphs))
    shot(folder, "52-caption", "Table 1: Fuel costs")
    back_to("Search notes")

    # Four characters wider than drawn -- and drawn, the column is the
    # desktop's eight at least, "fuel" or not: twelve dashes.
    menu("fuel", "This column wider")
    check_settled("a wider column is written as the separator's dashes",
                  separator, lambda s: s.count("|") > 1 and s.split("|")[1].strip() == "-" * 12, first=0)
    back_to("Search notes")
    menu("120", "Hide this column")
    sep = settle(separator, lambda s: s.count("|") > 2 and s.split("|")[2].strip() == "~", first=0)
    check("a hidden column is the separator's ~, and the grid leaves it out",
          sep.count("|") > 2 and sep.split("|")[2].strip() == "~" and find("120", exact=True) is None, repr(sep))
    back_to("Search notes")
    menu("fuel", "Show hidden columns")
    check("Show hidden columns brings it back", find("120", exact=True) is not None)
    back_to("Search notes")
    # From a cell of the FIRST column, so a fit of that column alone would
    # leave the second at the "---" Unhide gave it.
    menu("fuel", "Fit all columns to their words")
    check_settled("Autofit all fits every column, not only the one tapped",
                  separator, lambda s: s == "| -------- | -------- |", first=0)
    back_to("Search notes")

    menu("fuel", "Add a line above the table")
    type_text("Above")
    keyboard_down()
    check_settled("a line above the table, the cursor on it", body, lambda b: b.startswith("Above\n| Item"))
    # Undo takes back the table's changes too: the word, then the line.
    tap("Undo", exact=True, wait=0.8)
    tap("Undo", exact=True, wait=2.0)
    check_settled("Undo takes back the word typed, then the line made above the table",
                  body, lambda b: b.startswith("| Item"), first=0)
    back_to("Search notes")


def pdf_pages(pdf):
    """The pictures a PDF the app printed holds, one a page, as raw pixels:
    the app's PDF draws each page as one picture, Flate-compressed RGB."""
    import zlib
    out = []
    for m in re.finditer(rb"/Subtype\s*/Image\s*/Width (\d+)\s*/Height (\d+).*?/Length (\d+)>> stream\n", pdf, re.S):
        try:
            out.append(zlib.decompress(pdf[m.end():m.end() + int(m.group(3))]))
        except zlib.error:
            out.append(b"")
    return out


def page_count(pdf):
    return len(re.findall(rb"/Type\s*/Page[^s]", pdf))


def message_shown():
    """Whether the bottom of the screen holds the dark band a message is
    drawn on, in the light theme: read from the screen's raw pixels, since
    the share sheet covers the message sooner than a UI dump could read
    its words. Some row of the band, between its lines of words, is all
    dark; a note's lines on the light page never are."""
    raw = adb("exec-out", "screencap", binary=True)
    if len(raw) < 16:
        return False
    w, h = struct.unpack_from("<II", raw, 0)
    head = len(raw) - w * h * 4
    if w <= 0 or h <= 0 or head < 12:
        return False
    xs = range(int(w * 0.08), int(w * 0.85), 6)
    for y in range(int(h * 0.86), int(h * 0.96), 3):
        at = head + y * w * 4
        dark = sum(1 for x in xs if sum(raw[at + x * 4:at + x * 4 + 3]) < 3 * 90)
        if dark >= 0.95 * len(xs):
            return True
    return False


def sharing(folder):
    """Share: one button, the note as a PDF, a web page, its words or a
    share code, handed to the phone's own share sheet. The app picked does
    the sending, so what is checked is what the app hands over: the share
    sheet in front, and the PDF or page written where the app picked reads
    it -- and, for a note with a table, no words offered at all."""
    print("\n-- sharing a note --")
    body = "Bring:\n| Item | Qty |\n| --- | --- |\n| socks | 3 |"
    long_body = "\n".join("Line %d of a note long enough to need several pages of paper to print." % i
                          for i in range(1, 121))
    if not seed_inside([{"id": 1, "uid": "sh-trip", "title": "Trip", "body": body,
                         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"},
                        {"id": 2, "uid": "sh-long", "title": "Long", "body": long_body,
                         "created": "2026-10-14 09:00:00", "updated": "2026-10-14 09:00:00"},
                        {"id": 3, "uid": "sh-words", "title": "Shop", "body": "milk and eggs",
                         "checklist": [{"text": "bread", "done": True}],
                         "created": "2026-10-13 09:00:00", "updated": "2026-10-13 09:00:00"},
                        {"id": 4, "uid": "sh-huge", "title": "Huge", "body": "\n".join(
                            "Line %d of a note far longer than anyone mails." % i for i in range(1, 1801)),
                         "created": "2026-10-12 09:00:00", "updated": "2026-10-12 09:00:00"}],
                       {"welcome_offered": True}, "Trip"):
        return

    def ask(title):
        back_to("Search notes")
        open_note(title)
        tap("Share", exact=True, wait=1.5)

    def focused():
        """The package of the window that has the focus. The share sheet is
        a window over the app, and the resumed activity was once still the
        app's with the sheet already up (the photo showed it)."""
        out = adb("shell", "dumpsys", "window")
        match = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([\w.]+)/", out)
        return match.group(1) if match else foreground()

    def handed(seconds=20.0):
        """The share sheet in front -- the system's, not the app's -- waited
        for, not timed: a PDF is printed before it opens."""
        deadline = time.time() + seconds
        while focused() == PACKAGE and time.time() < deadline:
            time.sleep(0.5)
        return focused()

    def back_in():
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.2)
        if focused() != PACKAGE:
            adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity")
            time.sleep(2.0)

    ask("Trip")
    shot(folder, "59-share-sheet", "Share this note as", "PDF", "Web page", "Share code")
    check("a note with a table offers no words alone, and says why",
          find("Not for a note with a table") is not None)
    tap("Text", exact=True, wait=1.2)
    check("...and Text does nothing", find("Share this note as") is not None and focused() == PACKAGE, focused())
    tap("PDF", exact=True, wait=1.0)
    sheet = handed()
    check("PDF opens the phone's share sheet", sheet != PACKAGE, sheet)
    shot(folder, "60-share-chooser")
    # A PDF of the note's page, not the page: Gmail showed a .html as its code.
    pdf = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Trip.pdf", binary=True)
    with open(os.path.join(folder, "60b-share.pdf"), "wb") as f:
        f.write(pdf)
    pictures = pdf_pages(pdf)
    check("the share sheet is handed a PDF of the note: one A4 page, not blank",
          pdf.startswith(b"%PDF") and page_count(pdf) == 1 and b"/MediaBox [0 0 595 842]" in pdf
          and len(pictures) == 1 and pictures[0].count(b"\xff") < len(pictures[0]),
          "%d bytes, %d pages, %d pictures" % (len(pdf), page_count(pdf), len(pictures)))
    back_in()

    ask("Trip")
    tap("Web page", exact=True, wait=1.0)
    sheet = handed()
    page = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Trip.html", binary=True)
    check("Web page hands over the export's page, its table drawn",
          sheet != PACKAGE and page.lstrip().lower().startswith(b"<!doctype html") and b"<table" in page,
          "%s, %d bytes" % (sheet, len(page)))
    back_in()

    # A long note: a picture of its own for each page, each with something
    # on it. Painted on one shared picture, every page once came out as the
    # same page; read too soon, the page once came out blank.
    ask("Long")
    tap("PDF", exact=True, wait=1.0)
    handed(20.0)
    pdf = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Long.pdf", binary=True)
    with open(os.path.join(folder, "60c-share-long.pdf"), "wb") as f:
        f.write(pdf)
    pictures = pdf_pages(pdf)
    check("a long note's PDF: several pages, each its own and none blank",
          page_count(pdf) >= 3 and len(pictures) == page_count(pdf) and len(set(pictures)) == len(pictures)
          and all(p.count(b"\xff") < len(p) for p in pictures),
          "%d pages, %d pictures, %d distinct" % (page_count(pdf), len(pictures), len(set(pictures))))
    back_in()

    # Asked for twice: the Share button is there again while a long note's
    # PDF is made, and a second PDF cleared the folder the first was still
    # writing into. The taps go in at places read once, with no UI dump
    # between them -- a dump takes a second, the first PDF two. A busy
    # emulator makes the first slower, so the second lands sooner within it.
    # Which message is on the screen -- the refusal, or the first share
    # failing because the second cleared its folder -- the screen's pixels
    # cannot say: with the refusal taken out, this check passed. The order
    # is held by OutgoingTest; here, what the person is left with.
    back_to("Search notes")
    open_note("Huge")
    button = find("Share", exact=True)
    tap("Share", exact=True, wait=1.5)
    card = find("PDF", exact=True)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)
    if button and card:
        adb("logcat", "-b", "events", "-c")
        for at, pause in ((button, 1.2), (card, 0.3), (button, 1.0), (card, 0.0)):
            adb("shell", "input", "tap", str(at[0]), str(at[1]))
            time.sleep(pause)
        told = any(message_shown() for _ in range(4))
        time.sleep(5.0)
        sheets = len(re.findall(r"wm_create_activity.*ChooserActivity", adb("logcat", "-b", "events", "-d")))
        pdf = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Huge.pdf", binary=True)
        check("asked for twice while the PDF is made: one share sheet, a message, the whole PDF",
              sheets == 1 and told and page_count(pdf) == 40,
              "%d share sheets, message %s, %d pages" % (sheets, "shown" if told else "not seen", page_count(pdf)))
        back_in()
    else:
        fail("asked for twice: the Share button or the PDF card was not found")

    ask("Shop")
    check("a note without a table offers its words", find("Just the words, in the message itself.") is not None)
    tap("Text", exact=True, wait=1.0)
    sheet = handed()
    check("Text opens the phone's share sheet", sheet != PACKAGE, sheet)
    back_in()
    ask("Shop")
    tap("Share code", exact=True, wait=1.0)
    sheet = handed()
    check("Share code opens the phone's share sheet", sheet != PACKAGE, sheet)
    back_in()
    back_to("Search notes")


def bar_frame(kind):
    """Where Android draws a system bar, top and bottom, from its own
    account of the window: "statusBars" or "navigationBars"."""
    match = re.search(r"type=%s frame=\[\d+,(\d+)\]\[\d+,(\d+)\]" % kind, adb("shell", "dumpsys", "window"))
    return (int(match.group(1)), int(match.group(2))) if match else None


def bars_drawn():
    """Whether the bar of buttons is dark, and the clock light: read from
    the screen's raw pixels, the bars being Android's and in no UI tree.
    The buttons' bar is dark when most of it is; the clock is light when
    some of the status bar is near white -- the battery's green is not."""
    raw = adb("exec-out", "screencap", binary=True)
    w, h = struct.unpack_from("<II", raw, 0)
    head = len(raw) - w * h * 4
    buttons, status = bar_frame("navigationBars"), bar_frame("statusBars")
    if head < 12 or not buttons or not status:
        return None, None

    def light(x, y):
        at = head + (y * w + x) * 4
        r, g, b = raw[at], raw[at + 1], raw[at + 2]
        return 0.299 * r + 0.587 * g + 0.114 * b

    seen = [light(x, y) for y in range(buttons[0] + 2, buttons[1] - 2, 4) for x in range(0, w, 8)]
    dark = sum(1 for v in seen if v < 90) > 0.6 * len(seen)
    clock = any(light(x, y) > 200 for y in range(status[0], status[1], 2) for x in range(0, w // 2, 2))
    return dark, clock


def accent_pixels(left, top, right, bottom):
    """How many pixels of the default theme's accent -- a cursor, its handle
    -- the screen holds in a box, sampled every other pixel."""
    raw = adb("exec-out", "screencap", binary=True)
    w, h = struct.unpack_from("<II", raw, 0)
    head = len(raw) - w * h * 4
    if head < 12 or bottom <= top:
        return 0
    seen = 0
    for y in range(max(0, top), min(h, bottom), 2):
        for x in range(max(0, left), min(w, right), 2):
            at = head + (y * w + x) * 4
            r, g, b = raw[at], raw[at + 1], raw[at + 2]
            if b > 200 and r < 150 and g < 130:
                seen += 1
    return seen


def sheets_over_buttons(folder):
    """A sheet over three-button navigation, the app dark on a light phone:
    the owner's phone. A sheet is a window of its own, and Android drew its
    bars for the phone's light, not the app's dark -- a white bar of buttons
    over the sheet, the clock dark on dark. And a sheet taller than half
    the screen opened half-way: Share's last card lay under the buttons."""
    print("\n-- a sheet over three buttons --")
    adb("shell", "cmd", "overlay", "enable", "com.android.internal.systemui.navbar.threebutton")
    adb("shell", "cmd", "uimode", "night", "no")
    time.sleep(3)
    try:
        body = "Bring:\n| Item | Qty |\n| --- | --- |\n| socks | 3 |"
        if not seed_inside([{"id": 1, "uid": "sb-trip", "title": "Trip", "body": body,
                             "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"}],
                           {"welcome_offered": True, "theme": "dark", "phone_theme_follows_phone": False}, "Trip"):
            return
        buttons = bar_frame("navigationBars")
        check("the emulator shows three buttons, as the owner's phone does",
              buttons is not None and buttons[1] - buttons[0] > 100, str(buttons))
        open_note("Trip")
        tap("Share", exact=True, wait=2.0)
        last = bounds(OPENS_NEXT)
        check("Share's sheet opens whole, its last line above the buttons",
              buttons is not None and len(last) == 1 and last[0][3] <= buttons[0] and last[0][3] > last[0][1],
              "line %s, buttons from %s" % (last, buttons))
        dark, clock = bars_drawn()
        check("over the sheet, the bar of buttons is dark, as the app is", dark is True, str(dark))
        check("over the sheet, the clock is light, as the app is dark", clock is True, str(clock))
        shot(folder, "61-sheet-over-buttons", "Share code")
        back()
        choose_cell("Trip", "socks")
        keyboard_down()
        tap("More", exact=True, wait=2.0)
        last = bounds("Delete the table")
        check("the table's More opens whole too, its last entry above the buttons",
              buttons is not None and len(last) == 1 and last[0][3] <= buttons[0] and last[0][3] > last[0][1],
              "entry %s, buttons from %s" % (last, buttons))
        dark, clock = bars_drawn()
        check("...and its bars are drawn for the app's dark", dark is True and clock is True, "%s %s" % (dark, clock))
        back_to("Search notes")
    finally:
        adb("shell", "cmd", "overlay", "disable", "com.android.internal.systemui.navbar.threebutton")
        time.sleep(2)


OPENS_NEXT = ("Your phone's share sheet opens next: WhatsApp, mail, Teams and the rest. "
              "Nothing is sent until you send it there.")


def typing_styles(folder):
    """What letters typed wear, by the desktop's rules, which the phone had
    the wrong way round: glued after a highlighted word, its highlight;
    before a bold word, nothing; with B pressed and nothing selected, bold,
    until B is pressed again. And a colour tapped a second time stays --
    it once took itself away."""
    print("\n-- typing next to styles --")
    if not seed_inside([
        {"id": 1, "uid": "ts-words", "title": "Words", "body": "Important quick",
         "formatting": [{"start": 0, "end": 9, "style": "bold"},
                        {"start": 10, "end": 15, "style": "highlight", "value": "#fff3b0"}],
         "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:00:00"},
    ], {"welcome_offered": True}, "Words"):
        return

    def note():
        return note_by_uid("ts-words") or {}

    open_note("Words")
    tap("Important quick", wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text("ly")
    tap("B", exact=True, wait=0.8)
    check("B with nothing selected says it waits for what is typed",
          find("On for what you type next: bold") is not None)
    type_text(" big")
    tap("B", exact=True, wait=0.8)
    type_text(" end")
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    type_text("Very ")
    want = [{"start": 5, "end": 14, "style": "bold"},
            {"start": 15, "end": 22, "style": "highlight", "value": "#fff3b0"},
            {"start": 22, "end": 26, "style": "bold"}]
    check_settled("typed letters take the desktop's styles: glued after, not before, and B's until pressed again",
                  lambda: (note().get("body"), sorted(note().get("formatting") or [], key=lambda s: (s["start"], s["style"]))),
                  lambda got: got == ("Very Important quickly big end", want))
    shot(folder, "63-typing-styles", "Very Important quickly big end")

    # The same colour twice, over everything: it stays.
    adb("shell", "input", "keycombination", "113", "29")      # Ctrl+A
    time.sleep(1.0)
    for _ in range(2):
        tap("A", exact=True, wait=0.8)
        tap("Text colour 1", exact=True, wait=1.0)
    keyboard_down()
    check_settled("a colour tapped a second time stays",
                  lambda: [s for s in note().get("formatting") or [] if s.get("style") == "text_color"],
                  lambda got: len(got) == 1 and got[0]["start"] == 0 and got[0]["end"] == 30)
    back_to("Search notes")


def comments_paste(folder):
    """A table's row pasted into the comments, which draw no table: left
    out, and the person told -- the desktop's _on_comments_paste. The row
    is copied from the title, the one plain field a walk can fill with it."""
    print("\n-- a table pasted into the comments --")
    if not seed_inside([
        {"id": 1, "uid": "cp-note", "title": "| a | b |", "body": "words", "comments": "seen",
         "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:00:00"},
    ], {"welcome_offered": True}, "| a | b |"):
        return

    def keys(*codes):
        adb("shell", "input", "keycombination", *map(str, codes))
        time.sleep(1.0)

    open_note("| a | b |")
    tap("| a | b |", exact=True, wait=0.8)
    keys(113, 29)                                  # Ctrl+A: the title's words, a row
    keys(113, 31)                                  # Ctrl+C
    tap("seen", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    keys(113, 50)                                  # Ctrl+V
    told = find("Comments keep text only") is not None
    keyboard_down()
    check_settled("a table's row pasted into the comments is left out, and that is said",
                  lambda: ((note_by_uid("cp-note") or {}).get("comments"), told),
                  lambda got: got == ("seen", True))
    back_to("Search notes")


def separator_widths(text):
    """The widths each markdown separator in [text] spells, the desktop's
    rule: a cell over three characters is a width, one of three or fewer
    (or the hidden mark) none."""
    out = []
    for line in (text or "").split("\n"):
        cells = line.strip()
        if not re.fullmatch(r"\|(\s*(:?-+:?|~)\s*\|)+", cells):
            continue
        out.append([len(c.strip()) if len(c.strip()) > 3 else None for c in cells[1:-1].split("|")])
    return out


def pasted_tables(folder):
    """A markdown table pasted from elsewhere -- copied as words, its
    separator "---:", markdown's right-aligned column -- into a note and into
    a template's text. "---:" is four characters, which both apps read as a
    column set by hand to four: the header was drawn "How…". Pasted, the
    separator is spelled at markdown's minimum and the column fits what it
    holds. The words are copied from a note's comments, the plain field a
    walk can fill with them; read back from both files, and the drawn
    header measured against a word of four letters."""
    print("\n-- a markdown table pasted in --")
    markdown = "Costs:\n| What | How much |\n| --- | ---: |\n| rent | 1500 |\nthat is all"
    if not seed_inside([
        {"id": 1, "uid": "pt-source", "title": "Copied from", "body": "words", "comments": markdown,
         "created": "2026-10-23 09:00:00", "updated": "2026-10-23 09:00:00"},
        {"id": 2, "uid": "pt-note", "title": "Pasted into", "body": "Start here",
         "created": "2026-10-23 09:00:01", "updated": "2026-10-23 09:00:01"},
        {"id": 3, "uid": "pt-words", "title": "Words", "body": "A new paragraph.",
         "formatting": [{"start": 2, "end": 5, "style": "bold"}],
         "created": "2026-10-23 09:00:02", "updated": "2026-10-23 09:00:02"},
        {"id": 4, "uid": "pt-end", "title": "Ends with a table",
         "body": "Costs:\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |",
         "created": "2026-10-23 09:00:03", "updated": "2026-10-23 09:00:03"},
    ], {"welcome_offered": True}, "Copied from"):
        return

    def keys(*codes):
        adb("shell", "input", "keycombination", *map(str, codes))
        time.sleep(1.0)

    def paste_after(words):
        tap(words, wait=0.8)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb("shell", "input", "keyevent", "KEYCODE_ENTER")
        time.sleep(0.5)
        keys(113, 50)                              # Ctrl+V

    open_note("Copied from")
    tap("that is all", wait=0.8)
    keys(113, 29)                                  # Ctrl+A: the comments' words
    keys(113, 31)                                  # Ctrl+C
    keyboard_down()
    # A template first, made from the note to paste into while it still
    # holds its two words.
    back_to("Search notes")
    open_note("Pasted into")
    tap("⋮", exact=True)
    tap("🧩 Save as template", wait=1.2)
    tap("Save", exact=True, wait=1.5)
    back_to("Search notes")

    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("Pasted into", exact=True, wait=1.5)
    paste_after("Start here")
    keyboard_down()
    tap("Save", exact=True, wait=1.5)
    saved = (read_file("templates.json") or [{}])[0]
    check("a template's text takes the table without the width its separator spelled",
          separator_widths(saved.get("body")) == [[None, None]] and "How much" in (saved.get("body") or ""),
          repr(saved.get("body")))
    back_to("Search notes")

    open_note("Pasted into")
    paste_after("Start here")
    keyboard_down()
    pasted = settle(lambda: note_by_uid("pt-note") or {}, lambda n: "How much" in (n.get("body") or ""))
    check("a note takes it the same way, the words around it kept",
          separator_widths(pasted.get("body")) == [[None, None]]
          and (pasted.get("body") or "").endswith("that is all"), repr(pasted.get("body")))
    # Measured, not read: the tree holds a cell's whole words even when
    # they are drawn cut short. Cut to four characters, the header was as
    # wide as "rent"; drawn whole, it is twice that.
    header, word = bounds("How much"), bounds("rent")
    wide = bool(header and word) and (header[0][2] - header[0][0]) > 1.6 * (word[0][2] - word[0][0])
    check("and its header is drawn whole, twice as wide as a word of four letters", wide, repr((header, word)))
    shot(folder, "72-pasted-markdown-table", "How much")
    back_to("Search notes")

    # A paragraph pasted with its formatting right after a note's last table:
    # the place to write there pasted at the end of the table's last row, the
    # words glued onto it -- the row read as words, the table one row short
    # (the owner's case 180). Copied from a note, so the clipboard holds HTML.
    open_note("Words")
    tap("A new paragraph", wait=0.8)
    keys(113, 29)                                  # Ctrl+A
    keys(113, 31)                                  # Ctrl+C
    keyboard_down()
    back_to("Search notes")
    open_note("Ends with a table")
    keyboard_down()
    cell, first = bounds("120"), bounds("fuel")
    if cell and first:
        # Under the grid: the table's own margin, then the place to write.
        adb("shell", "input", "tap", str(first[0][0] + 20), str(cell[0][3] + 55))
        time.sleep(1.0)
        keys(113, 50)                              # Ctrl+V
    ended = settle(lambda: note_by_uid("pt-end") or {}, lambda n: "paragraph" in (n.get("body") or ""))
    check("a paragraph pasted after a note's last table goes on its own line, the table's last row kept",
          (ended.get("body") or "") == "Costs:\n| Item | Cost |\n| --- | --- |\n| fuel | 120 |\nA new paragraph.",
          repr(ended.get("body")))
    keyboard_down()
    shot(folder, "72b-pasted-after-table", "A new paragraph", "fuel")
    back_to("Search notes")


def copy_after_styling(folder):
    """Words selected, made bold and 14 from the format bar, copied at once
    and pasted into another note -- the order a person styles and copies
    in. The selection was noted before the styling, and the copy went out
    without it: found by the rehearsal of a check for a real phone."""
    print("\n-- a copy made right after styling --")
    if not seed_inside([
        {"id": 1, "uid": "cs-src", "title": "Src", "body": "Tom & Jerry <3 socks",
         "created": "2026-10-16 09:00:00", "updated": "2026-10-16 09:00:00"},
        {"id": 2, "uid": "cs-dst", "title": "Dst", "body": "Start",
         "created": "2026-10-16 09:00:01", "updated": "2026-10-16 09:00:01"},
    ], {"welcome_offered": True}, "Src"):
        return

    def keys(*codes):
        adb("shell", "input", "keycombination", *map(str, codes))
        time.sleep(1.0)

    open_note("Src")
    tap("Tom & Jerry", wait=0.8)
    keys(113, 29)                                  # Ctrl+A
    tap("B", exact=True, wait=0.8)
    tap("Size: Default", exact=True, wait=0.8)
    tap("14", exact=True, wait=0.8)
    keys(113, 31)                                  # Ctrl+C, at once
    keyboard_down()
    back_to("Search notes")
    open_note("Dst")
    tap("Start", wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" ")
    keys(113, 50)                                  # Ctrl+V
    time.sleep(1.5)
    keyboard_down()
    shot(folder, "62-copy-after-styling", "Start")
    back_to("Search notes")
    dst = note_by_uid("cs-dst") or {}
    spans = dst.get("formatting") or []
    check("the copy carries the bold and the size just given",
          {"start": 6, "end": 26, "style": "bold"} in spans
          and {"start": 6, "end": 26, "style": "font_size", "value": 14} in spans,
          repr((dst.get("body"), spans)))

    # A cut, the same way: its words are out of the note before it copies.
    open_note("Src")
    tap("Tom & Jerry", wait=0.8)
    keys(113, 29)                                  # Ctrl+A
    tap("I", exact=True, wait=0.8)
    keys(113, 52)                                  # Ctrl+X
    keyboard_down()
    back_to("Search notes")
    open_note("Dst")
    tap("Start", wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    keys(113, 50)                                  # Ctrl+V
    time.sleep(1.5)
    keyboard_down()
    back_to("Search notes")
    dst = note_by_uid("cs-dst") or {}
    spans = dst.get("formatting") or []
    check("a cut carries the italic just given, its words gone from where they were",
          (note_by_uid("cs-src") or {}).get("body") == ""
          and any(s.get("style") in ("italic", "bold_italic") and s.get("start") == 26 for s in spans),
          repr((dst.get("body"), spans)))


def undoing(folder):
    """Undo and Redo: two bursts of typing, a pause between, taken back one
    burst at a time and brought back -- from the editor's buttons and from a
    keyboard's Ctrl+Z -- each read back from the file the autosave wrote."""
    print("\n-- undo and redo --")
    if not seed_inside([{"id": 1, "uid": "un-1", "title": "Draft", "body": "first",
                         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"}],
                       {"welcome_offered": True}, "Draft"):
        return

    def body():
        return (note_by_uid("un-1") or {}).get("body")

    open_note("Draft")
    tap_last("first", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" second")
    time.sleep(1.5)                          # a pause: the next burst is a step of its own
    type_text(" third")
    keyboard_down()
    check_settled("typed", body, lambda b: b == "first second third")
    tap("Undo", exact=True, wait=1.0)
    check_settled("Undo takes back the last burst of typing", body, lambda b: b == "first second")
    shot(folder, "53-undo", "first second", "Redo")
    tap("Undo", exact=True, wait=1.0)
    check_settled("and the one before it", body, lambda b: b == "first")
    tap("Redo", exact=True, wait=1.0)
    check_settled("Redo brings it back", body, lambda b: b == "first second")
    # Ctrl+Z from a keyboard is not walked: with no hardware keyboard the
    # emulator's on-screen one takes the keys first and hands the text
    # field its own small undo, so what a tablet with a keyboard would do
    # cannot be seen here.
    back_to("Search notes")


def pasting(folder):
    """Copy and paste with the formatting: a note's words copied from the
    selection menu, pasted into another note with all they carried -- a
    size too, which only the app's own copy brings back; then, the app
    started again so that copy is forgotten, the same clipboard pasted as
    the HTML it is, the size left behind; then "Paste as plain text", the
    markers left behind too. Each read back from the file."""
    print("\n-- copy and paste, formatting kept --")
    source = "Big bold words\n• milk\n• eggs"
    if not seed_inside([
        {"id": 1, "uid": "pa-source", "title": "Source", "body": source,
         "formatting": [{"start": 0, "end": 3, "style": "font_size", "value": 24},
                        {"start": 4, "end": 8, "style": "bold"}],
         "paragraphs": [{"line": 1, "kind": "bullet"}, {"line": 2, "kind": "bullet"}],
         "created": "2026-10-16 09:00:00", "updated": "2026-10-16 09:00:00"},
        {"id": 2, "uid": "pa-own", "title": "Own copy", "body": "Start here",
         "created": "2026-10-16 09:00:01", "updated": "2026-10-16 09:00:01"},
        {"id": 3, "uid": "pa-html", "title": "From HTML", "body": "Start here",
         "created": "2026-10-16 09:00:02", "updated": "2026-10-16 09:00:02"},
        {"id": 4, "uid": "pa-plain", "title": "Plain", "body": "Start here",
         "created": "2026-10-16 09:00:03", "updated": "2026-10-16 09:00:03"},
    ], {"welcome_offered": True}, "Source"):
        return

    def note(uid, body):
        """The note once its body is [body], or as it is when the wait is up."""
        return settle(lambda: note_by_uid(uid) or {}, lambda n: n.get("body") == body)

    def keys(*names):
        adb("shell", "input", "keycombination", *("KEYCODE_" + n for n in names))
        time.sleep(1.0)

    def at_the_end(words):
        """The cursor at the end of a field's words: a tap in it, then End.
        Found by a part of its text: a field of several lines is read with
        an invisible mark between them, never as the words alone."""
        tap(words, wait=0.8)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        time.sleep(0.4)

    # The keys a keyboard sends, not the selection menu's buttons: the menu
    # is a floating bar the phone's own UI tree leaves out, so no step can
    # find its words to press them. Ctrl+C and Ctrl+V go through the very
    # copy and paste the menu's Copy and Paste call. No Back after opening
    # a note, either: with no keyboard up, Back leaves the note.
    expected = "Start here Big bold words\n• milk\n• eggs"
    open_note("Source")
    at_the_end("Big bold words")
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "C")
    back_to("Search notes")

    open_note("Own copy")
    at_the_end("Start here")
    type_text(" ")
    keys("CTRL_LEFT", "V")
    own = note("pa-own", expected)
    check("the copy pasted after the words, the list on lines of its own",
          own.get("body") == expected, repr(own.get("body")))
    check("the app's own copy brings its size back, and its bold",
          {"start": 11, "end": 14, "style": "font_size", "value": 24} in (own.get("formatting") or [])
          and {"start": 15, "end": 19, "style": "bold"} in (own.get("formatting") or []), repr(own.get("formatting")))
    check("its lines are list lines", [p.get("kind") for p in own.get("paragraphs") or []] == ["bullet", "bullet"],
          repr(own.get("paragraphs")))
    keyboard_down()
    shot(folder, "55-pasted", "milk", "eggs")
    back_to("Search notes")

    # Started again: the copy the app remembered is gone, the clipboard is not.
    adb("shell", "am", "force-stop", PACKAGE)
    start_app()
    open_note("From HTML")
    at_the_end("Start here")
    type_text(" ")
    keys("CTRL_LEFT", "V")
    read = note("pa-html", expected)
    check("pasted as the HTML it is: the words and the list as they were",
          read.get("body") == expected, repr(read.get("body")))
    check("the bold read from the HTML, the size left behind as a web page's is",
          read.get("formatting") == [{"start": 15, "end": 19, "style": "bold"}], repr(read.get("formatting")))
    back_to("Search notes")

    open_note("Plain")
    # The menu, with formatting on the clipboard: photographed for the eye,
    # since its words cannot be read here (see above).
    long_press("Start here")
    shot(folder, "56-paste-plain-menu", "Plain")
    back(wait=0.8)
    at_the_end("Start here")
    type_text(" ")
    keys("CTRL_LEFT", "SHIFT_LEFT", "V")
    plain = note("pa-plain", "Start here Big bold words\nmilk\neggs")
    check("Ctrl+Shift+V, the plain paste: the words alone, the markers left behind",
          plain.get("body") == "Start here Big bold words\nmilk\neggs" and not plain.get("formatting"),
          repr((plain.get("body"), plain.get("formatting"))))
    back_to("Search notes")

    # A cut is a copy too: everything cut, then pasted back whole. Last,
    # because it puts these words on the clipboard in place of the others.
    open_note("Own copy")
    at_the_end("Start here")
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "X")
    cut = note("pa-own", "")
    check("a cut takes the words out", cut.get("body") == "", repr(cut.get("body")))
    keys("CTRL_LEFT", "V")
    back_in = note("pa-own", expected)
    check("and pasted back they come with their size, their bold and their list",
          back_in.get("body") == expected
          and {"start": 11, "end": 14, "style": "font_size", "value": 24} in (back_in.get("formatting") or [])
          and len(back_in.get("paragraphs") or []) == 2, repr(back_in))
    back_to("Search notes")


def put_bytes_inside(path, data):
    """A file of any kind written into the app's own notebook folder."""
    local = os.path.join(os.environ.get("TEMP", "."), "walk_bytes_" + os.path.basename(path))
    with open(local, "wb") as f:
        f.write(data)
    staging = "/data/local/tmp/walk_bytes"
    adb("push", local, staging)
    os.remove(local)
    adb("shell", "chmod", "644", staging)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", "%s/%s" % (NOTEBOOK, os.path.dirname(path) or "."))
    adb("shell", "run-as", PACKAGE, "cp", staging, "%s/%s" % (NOTEBOOK, path))
    adb("shell", "rm", "-f", staging)


def duplicates(folder):
    """Settings -> Duplicates: the groups found by the desktop's own key,
    the copies ticked "all but first" moved to the Trash after a question,
    and the file one of them carried handed, as a copy of its own, to the
    note kept. Each read back from the files."""
    print("\n-- duplicates --")
    if not seed_inside([
        {"id": 1, "uid": "dp-1", "title": "Trip", "body": "fuel", "created": "2026-10-01 09:00:00",
         "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "dp-2", "title": "Trip (copy)", "body": "fuel ", "created": "2026-10-02 09:00:00",
         "updated": "2026-10-02 09:00:00", "attachments": [{"filename": "map.png", "stored_as": "dp_map.png"}]},
        {"id": 3, "uid": "dp-3", "title": "Plan", "body": "p", "created": "2026-10-03 09:00:00",
         "updated": "2026-10-03 09:00:00"},
        {"id": 4, "uid": "dp-4", "title": "Plan", "body": "p", "created": "2026-10-04 09:00:00",
         "updated": "2026-10-04 09:00:00"},
    ], {"welcome_offered": True}, "Trip"):
        return
    put_bytes_inside("attachments/dp_map.png", b"MAP")
    tap("Settings", exact=True, wait=1.2)
    tap("Duplicates", exact=True, wait=1.5)
    check("two groups found, by the desktop's key", not screen_says("Found 2 group(s)", "“Trip” — 2 copies"),
          str(screen_says("Found 2 group(s)", "“Trip” — 2 copies")))
    check("the group with a file says it will be merged", not screen_says("differing attachments"))
    shot(folder, "51-duplicates", "Found 2 group(s)")
    tap("Tick all but first", wait=0.8)
    tap("Move ticked duplicates", wait=1.2)
    check("cleaning up asks first", not screen_says("Move 1 selected note(s) to the Trash?"))
    tap_last("Move to Trash", wait=1.5)
    notes = read_file("notes_data.json") or []
    trash = read_file("trash.json") or []
    kept = next((n for n in notes if n.get("uid") == "dp-1"), {})
    merged = (kept.get("attachments") or [{}])[0].get("stored_as", "")
    check("the copy goes to the Trash, its file handed to the note kept, as a copy of its own",
          [t.get("uid") for t in trash] == ["dp-2"] and len(notes) == 3
          and merged.endswith("_dp_map.png") and merged != "dp_map.png", json.dumps(kept)[:240])
    check("the other group is left as it was", [n.get("uid") for n in notes if n.get("title") == "Plan"] == ["dp-3", "dp-4"])
    back_to("Your name")
    back()


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


def picker_save_button(within=15.0):
    """The phone's own "save as" picker's Save button, waited for. The
    picker is another app, slower to come up than any fixed wait on a busy
    emulator: looked for once, three seconds after the tap, a Save not
    drawn yet was never pressed, no file was made, and three checks failed
    for one cause (2026-10-22)."""
    deadline = time.time() + within
    while time.time() < deadline:
        for label in ("SAVE", "Save"):
            if find(label, exact=True) is not None:
                return label
        time.sleep(0.5)
    return None


def lines_of_field(words):
    """The note's text field holding [words]: its box and its lines. The tree
    gives the field as one node, its lines joined by the stand-in each break
    is drawn as (U+200B); where a line is comes from [body_line_px], the box
    being taller than its words when they are few."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return None, []
    for node in root.iter("node"):
        text = node.get("text", "")
        if words in text and node.get("class") == "android.widget.EditText":
            box = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(box) == 4:
                return tuple(box), text.split("​")
    return None, []


def body_line_px():
    """One line of a note's text on this screen: the editor's 24 sp
    (BODY_LINE_HEIGHT), at the emulator's density and letter size."""
    density = int(re.findall(r"\d+", adb("shell", "wm", "density"))[-1])
    try:
        scale = float(adb("shell", "settings", "get", "system", "font_scale").strip())
    except ValueError:
        scale = 1.0
    return 24 * density / 160 * scale


def tap_past_line_end(folder):
    """A tap past a line's end puts the cursor at that line's end. Each break
    is drawn as a stand-in at the end of its line, and a tap past the end
    landed after it, at the next line's start: words typed there went in
    front of its marker ("more3. three"), and Enter after "3. three" broke
    before "4. " -- found on the owner's phone, 2026-10-22."""
    print("\n-- a tap past a line's end --")
    if not seed_inside([{"id": 1, "uid": "walk-tap-end", "title": "Taps", "body": "Plan\n1. one\n2. two\n3. three",
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Taps"):
        return
    if not open_note("Taps"):
        return

    def body():
        return (note_by_uid("walk-tap-end") or {}).get("body") or ""

    row = body_line_px()

    def past_end_of(line):
        box, lines = lines_of_field("Plan")
        if box is None or line >= len(lines):
            fail("the note's text field is not on the screen")
            return False
        adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * (line + 0.5))))
        time.sleep(1.0)
        return True

    if past_end_of(2):
        type_text("X")
    check_settled("a tap past a list item's end, then a letter: the letter ends the item",
                  body, lambda b: b == "Plan\n1. one\n2. twoX\n3. three")
    if past_end_of(0):
        type_text("Y")
    check_settled("...and the same past a plain line's end",
                  body, lambda b: b == "PlanY\n1. one\n2. twoX\n3. three")
    if past_end_of(1):
        adb("shell", "input", "keyevent", "66")                    # Enter
        time.sleep(0.6)
    check_settled("...and Enter there continues the list under that item",
                  body, lambda b: b == "PlanY\n1. one\n2. \n3. twoX\n4. three")
    keyboard_down()
    back_to("Search notes")


def editor_corners(folder):
    """What an agent playing an ordinary user found in the editor, typing on
    the phone's own keyboard (2026-10-22), each walked here: the empty line
    a list leaves at the end of a note was not drawn, and a tap at the end of
    the last item landed on it; a tap on a marker put the cursor before it;
    after Undo the next letter went to the end of the note; Enter in the
    title broke the title; the keyboard's tick did not add a checklist row.
    (Enter with a word the keyboard corrected is held by ParagraphsTest: the
    walk's injected letters are never corrected.)"""
    print("\n-- the editor's corners --")
    if not seed_inside([{"id": 1, "uid": "walk-corners", "title": "Corners", "body": "1. one\n2. two\n",
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Corners"):
        return
    if not open_note("Corners"):
        return

    def note():
        return note_by_uid("walk-corners") or {}

    row = body_line_px()

    def tap_line(line, x=None):
        box, lines = lines_of_field("one")
        if box is None:
            fail("the note's text field is not on the screen")
            return
        adb("shell", "input", "tap", str(box[2] - 40 if x is None else box[0] + x),
            str(int(box[1] + row * (line + 0.5))))
        time.sleep(1.0)

    tap_line(1)
    type_text("s")
    check_settled("a tap at the end of the last item, the note ending in an empty line: the letter ends the item",
                  lambda: note().get("body"), lambda b: b == "1. one\n2. twos\n")
    tap_line(0, x=8)
    type_text("x")
    check_settled("a tap on a marker puts the cursor after it, at the item's words",
                  lambda: note().get("body"), lambda b: b == "1. xone\n2. twos\n")
    tap("Undo", exact=True, wait=1.0)
    type_text("y")
    check_settled("after Undo the next letter goes where the change undone was, not to the end",
                  lambda: note().get("body"), lambda b: b == "1. yone\n2. twos\n")
    tap("Corners", exact=True, wait=1.0)
    adb("shell", "input", "keyevent", "123")                       # end of line
    adb("shell", "input", "keyevent", "66")                        # Enter
    time.sleep(0.8)
    type_text("z")
    check_settled("Enter in the title goes on to the note's words, the title one line",
                  note, lambda n: n.get("title") == "Corners" and "z" in (n.get("body") or ""))
    keyboard_down()
    # From ⋮ (2026-10-23): the checklist shows with the keyboard up in its
    # box, so the words go there with no tap of their own.
    tap("⋮", exact=True, wait=1.0)
    tap("☑ Add a checklist", exact=True, wait=1.5)
    type_text("bags")
    adb("shell", "input", "keyevent", "66")                        # the keyboard's tick
    check_settled("⋮'s checklist comes with the keyboard in its box, and the tick adds the row, as Add does",
                  # Lower case: the field capitalises a row's first word as it likes.
                  lambda: [(i.get("text") or "").lower() for i in note().get("checklist") or []],
                  lambda c: c == ["bags"])
    keyboard_down()
    tap("⋮", exact=True, wait=1.0)
    check("...and ⋮ no longer offers the checklist the note now shows", find("☑ Add a checklist") is None
          and find("💬 Add comments") is not None and find("📎 Attach files") is not None)
    tap("💬 Add comments", exact=True, wait=1.5)
    type_text("Packed")
    check_settled("⋮'s comments come with the cursor in them",
                  lambda: note().get("comments"), lambda c: (c or "").lower() == "packed")
    keyboard_down()
    back_to("Search notes")
    # A long note: the new row's box near the foot of the screen. The field
    # brought only its line of words into view, and the box's lower half and
    # Add stayed under the keyboard.
    long_body = "\n".join("%d. item number %d" % (i, i) for i in range(1, 19))
    if not seed_inside([{"id": 1, "uid": "walk-long", "title": "Long", "body": long_body,
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Long"):
        return
    if not open_note("Long"):
        return
    tap("⋮", exact=True, wait=1.0)
    tap("☑ Add a checklist", exact=True, wait=2.5)
    keyboard, add = bar_frame("ime"), bounds("Add")
    check("in a long note the new row's box and its Add stand whole above the keyboard",
          keyboard is not None and keyboard[0] > 0 and len(add) == 1 and add[0][3] <= keyboard[0],
          "Add %s, the keyboard from %s" % (add, keyboard))
    # Rows added one after another with the keyboard's tick: each pushed the
    # box a row further down, under the keys, and the screen stayed where it
    # was -- the newest row out of sight with it (Alexandra's phone, 2026-10-23).
    for word in ("Bread", "Milk", "Eggs"):
        type_text(word)
        adb("shell", "input", "keyevent", "66")
        time.sleep(1.0)
    check_settled("three rows added with the keyboard's tick",
                  lambda: [(i.get("text") or "").lower() for i in (note_by_uid("walk-long") or {}).get("checklist") or []],
                  lambda c: c == ["bread", "milk", "eggs"])
    keyboard, newest, add = bar_frame("ime"), bounds("Eggs"), bounds("Add")
    check("...and the newest row and the box under it stay in sight above the keyboard",
          keyboard is not None and keyboard[0] > 0 and len(newest) == 1 and len(add) == 1
          and newest[0][3] - newest[0][1] >= 20 and newest[0][3] <= keyboard[0] and add[0][3] <= keyboard[0],
          "Eggs %s, Add %s, the keyboard from %s" % (newest, add, keyboard))
    keyboard_down()
    back_to("Search notes")

    # Enter after Enter at the foot of what shows: each new line went under
    # the format bar and the keys, the screen still, and what was typed there
    # could not be seen (Alexandra's phone, 2026-10-23). Measured from the
    # category line above the text, which stays in sight: how far the note
    # moved, and so where its last line now is.
    if not seed_inside([{"id": 1, "uid": "walk-follow", "title": "Follow", "body": "a\nb\nc",
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Follow"):
        return
    if not open_note("Follow"):
        return
    box, _lines = lines_of_field("a")
    if box is None:
        fail("the note's text field is not on the screen")
        return
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 2.5)))
    time.sleep(2.0)
    box, _lines = lines_of_field("a")
    category, hint = bounds("General"), bounds("Select text to format it, or tap B, I, U or S for what you type next")
    if box is None or len(category) != 1 or len(hint) != 1:
        fail("the note's text, its category and the format bar are on the screen (%s %s %s)" % (box, category, hint))
        return
    # Enough breaks that, the screen standing still, the last would end a
    # line and a half below the top of the format bar.
    enters = max(1, int((hint[0][1] + 1.5 * row - box[1]) / row + 0.999) - 3)
    for _ in range(enters):
        adb("shell", "input", "keyevent", "66")
        time.sleep(0.3)
    type_text("end")
    check_settled("Enter at the foot of what shows, again and again: every break in the note",
                  lambda: (note_by_uid("walk-follow") or {}).get("body"),
                  lambda b: b == "a\nb\nc" + "\n" * enters + "end")
    after = bounds("General")
    moved = category[0][3] - after[0][3] if len(after) == 1 else None
    last = None if moved is None else box[1] - moved + row * (3 + enters)
    check("...and the line being written follows the cursor up, in sight above the format bar",
          last is not None and last <= hint[0][1] + row * 0.3,
          "the last line ends at %s, the format bar from %s (the note moved %s px, %d breaks)"
          % (last, hint[0][1], moved, enters))
    keyboard_down()
    back_to("Search notes")

    # The same in the comments, under a long note -- another field of the
    # editor's, given the same following. Measured from the field's own top,
    # which the few lines it has to move leave in sight.
    if not seed_inside([{"id": 1, "uid": "walk-remarks", "title": "Remarks",
                         "body": "\n".join("Line %d" % i for i in range(1, 25)), "comments": "First",
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Remarks"):
        return
    if not open_note("Remarks"):
        return
    for _ in range(2):
        adb("shell", "input", "swipe", "540", "1900", "540", "500", "300")
        time.sleep(0.8)
    spoken, _lines = lines_of_field("First")
    if spoken is None:
        fail("the comments are on the screen")
        return
    line = 22 * row / 24                                           # the comments' line, at this density
    adb("shell", "input", "tap", str(spoken[2] - 40), str((spoken[1] + spoken[3]) // 2))
    time.sleep(2.0)
    (spoken, _lines), keyboard = lines_of_field("First"), bar_frame("ime")
    if spoken is None or keyboard is None or keyboard[0] <= 0:
        fail("the comments field took the keyboard (%s, %s)" % (spoken, keyboard))
        return
    # Its top taken for its first line's: a line and a half past the keys,
    # wherever the words sit inside the box.
    enters = max(1, int((keyboard[0] + 1.5 * line - spoken[1]) / line + 0.999) - 1)
    for _ in range(enters):
        adb("shell", "input", "keyevent", "66")
        time.sleep(0.3)
    type_text("end")
    check_settled("Enter after Enter in the comments: every break kept",
                  lambda: ((note_by_uid("walk-remarks") or {}).get("comments") or "").lower(),
                  lambda c: c == "first" + "\n" * enters + "end")
    after, _lines = lines_of_field("First")
    last = None if after is None else after[1] + line * (enters + 1)
    check("...and the comments' line being written stays in sight above the keyboard",
          last is not None and last <= keyboard[0] + line * 0.3,
          "the last line ends at %s, the keyboard from %s (%d breaks; the field from %s, then %s)"
          % (last, keyboard[0], enters, spoken, after))
    keyboard_down()
    back_to("Search notes")

    # A new last line centred before a word is typed on it: the cursor moves
    # to the middle at once. An empty last line was drawn as no paragraph at
    # all, so it took no alignment, and the cursor stayed at the left until
    # a letter came (the owner's phone, 2026-10-23). Seen by the handle a tap
    # on the line puts under the cursor, in the accent: the cursor itself
    # blinks about once a second, as often as a capture comes, and eight
    # captures in a row once all fell on it dark.
    # Not titled "Centre": a tap on the key of that name found the title first.
    if not seed_inside([{"id": 1, "uid": "walk-centre", "title": "Middle", "body": "Hello\nWorld",
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}],
                       {"welcome_offered": True, "categories": {"General": "#2a9d8f"}}, "Middle"):
        return
    if not open_note("Middle"):
        return
    box, _lines = lines_of_field("Hello")
    if box is None:
        fail("the note's text field is not on the screen")
        return
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 1.5)))
    time.sleep(1.0)
    adb("shell", "input", "keyevent", "66")
    time.sleep(1.0)
    tap("Centre", exact=True, wait=1.0)
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 2.5)))
    time.sleep(1.0)
    middle = (box[0] + box[2]) // 2
    top, bottom = int(box[1] + row * 2), int(box[1] + row * 3 + 90)
    seen, deadline = [], time.time() + 5.0
    while time.time() < deadline and (not seen or seen[-1][0] < 20):
        seen.append((accent_pixels(middle - 80, top, middle + 80, bottom), accent_pixels(box[0], top, box[0] + 80, bottom)))
    check("a new last line centred has its cursor in the middle before a word is typed, not at the left",
          (note_by_uid("walk-centre") or {}).get("body") == "Hello\nWorld\n"
          and max(m for m, _ in seen) >= 20 and all(left == 0 for _, left in seen),
          "accent in the middle and at the left, look by look: %s" % seen)
    # ...and put back to the left under a centred line, it goes to the left:
    # kept inside the line above, the empty line wore that line's centring
    # and its cursor stayed in the middle (the owner's phone, 2026-10-23).
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 1.5)))
    time.sleep(1.0)
    tap("Centre", exact=True, wait=1.0)
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 2.5)))
    time.sleep(1.0)
    tap("Align left", exact=True, wait=1.0)
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 2.5)))
    time.sleep(1.0)
    seen, deadline = [], time.time() + 5.0
    while time.time() < deadline and (not seen or seen[-1][1] < 20):
        seen.append((accent_pixels(middle - 80, top, middle + 80, bottom), accent_pixels(box[0], top, box[0] + 80, bottom)))
    check("...and put back to the left under a centred line, its cursor goes to the left",
          max(left for _, left in seen) >= 20 and all(m == 0 for m, _ in seen),
          "accent in the middle and at the left, look by look: %s" % seen)
    keyboard_down()
    back_to("Search notes")

    # On its side, a tap on line 2 of a long note: the keyboard rose and the
    # note showed lines 20 to 22, the cursor nowhere -- Compose keeps the
    # whole focused field in view as the screen shrinks (2026-10-23). Seen
    # by the cursor itself: it and its handle are drawn in the accent, the
    # only thing in the note's pane that is; looked for in three photographs,
    # the cursor blinking.
    if not seed_inside([{"id": 1, "uid": "walk-sideways", "title": "Sideways",
                         "body": "\n".join("Line %d" % i for i in range(1, 31)),
                         "category": "General", "created": "2026-10-22 09:00:00",
                         # A category in green: its name, over the text, is
                         # then no accent the check could take for the cursor.
                         "updated": "2026-10-22 09:00:00"}],
                       {"welcome_offered": True, "categories": {"General": "#2a9d8f"}}, "Sideways"):
        return
    turn(1)
    if not open_note("Sideways"):
        turn(0)
        return
    box, _lines = lines_of_field("Line 1")
    if box is None:
        fail("the long note's text is beside the list, on its side")
        turn(0)
        return
    adb("shell", "input", "tap", str(box[2] - 40), str(int(box[1] + row * 1.5)))
    time.sleep(2.0)
    keyboard = bar_frame("ime")
    # Looked for until it is there, up to eight seconds: the note jumps away
    # as the keyboard rises and scrolls back, which on an emulator just
    # started took two seconds after the keyboard was up. Without the fix
    # it never comes back, so waiting costs the check nothing.
    inked, deadline = [], time.time() + 8.0
    while time.time() < deadline and (not inked or inked[-1] < 40):
        inked.append(accent_pixels(box[0], 0, box[2], keyboard[0] if keyboard else 0))
    check("on its side, the keyboard risen over a long note leaves the cursor's line in sight",
          keyboard is not None and keyboard[0] > 0 and max(inked) >= 40,
          "accent pixels above the keys %s, the keyboard from %s" % (inked, keyboard))
    keyboard_down()
    back_to("Search notes")
    turn(0)


def switches():
    """The centres of the app's switches on the screen: a switch says no
    words, so it is found by what it is -- a node that can be checked."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for node in root.iter("node"):
        if node.get("checkable") == "true" and node.get("package") == PACKAGE:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            out.append(((x1 + x2) // 2, (y1 + y2) // 2))
    return out


def our_alarm():
    """Whether an alarm of the app's is waiting, read from the system's own
    list of pending alarms -- not its statistics, which remember an alarm
    long after it was cancelled."""
    pending = adb("shell", "dumpsys", "alarm").split("Past-due non-wakeup alarms")[0]
    return re.search(r"Alarm\{[^}]*\b" + re.escape(PACKAGE) + r"\}", pending) is not None


def reminders(folder):
    """The day's reminder (2026-10-23, the owner's ask): the desktop's bar,
    once a day, its day written to settings.json under the desktop's key, a
    tap on it opening the day; and the phone's notification -- turned on in
    Settings, where Android asks first, set as an alarm, said when it fires
    (the debug build lets the walk fire it), opening the day when tapped,
    and the alarm gone when turned off."""
    print("\n-- the day's reminder --")
    today = adb("shell", "date", "+%Y-%m-%d").strip()
    if not seed_inside([{"id": 1, "uid": "rem-today", "title": "Dentist", "body": "at ten", "date": today,
                         "category": "General", "created": "2026-10-22 09:00:00",
                         "updated": "2026-10-22 09:00:00"}], {"welcome_offered": True}, "Dentist"):
        return
    check("on a day with a note, the bar says so above the notes",
          not screen_says("You have 1 note for today"), " | ".join(t for t, d, _ in nodes() if t)[:200])
    check("...and its day is written where the desktop writes it",
          (read_file("settings.json") or {}).get("reminder_last_shown") == today,
          repr((read_file("settings.json") or {}).get("reminder_last_shown")))
    shot(folder, "71-reminder-bar", "You have 1 note for today")
    # Started again before the bar is touched: only the day it wrote can
    # keep it away (a tap on it writes the day too).
    adb("shell", "am", "force-stop", PACKAGE)
    start_app()
    check("a second start the same day shows no bar", find("You have 1 note for today") is None
          and not screen_says("Dentist"))
    # The day forgotten, the bar back, and tapped.
    adb("shell", "am", "force-stop", PACKAGE)
    put_inside("settings.json", {"welcome_offered": True})
    start_app()
    tap("You have 1 note for today", wait=1.5)
    check("a tap on the bar opens the day on the calendar",
          not screen_says("📋 History", "Dentist", "+ Add note") and find("You have 1 note for today") is None)

    # The notification: off until turned on, Android asked first.
    tap("Settings", exact=True, wait=1.2)
    tap("Reminders", exact=True, wait=1.2)
    toggle = switches()
    if len(toggle) != 1 or find("Notify me about today's notes", exact=True) is None:
        fail("Settings → Reminders shows its switch (%s)" % toggle)
        back_to("Search notes")
        return
    adb("shell", "input", "tap", str(toggle[0][0]), str(toggle[0][1]))
    time.sleep(2.0)
    allow = find("Allow", exact=True)
    if allow is not None:
        adb("shell", "input", "tap", str(allow[0]), str(allow[1]))
        time.sleep(1.5)
    check("turned on, after Android asked: the setting saved, an hour shown",
          (read_file("settings.json") or {}).get("phone_reminder_notify") is True and not screen_says("09:00"),
          repr(read_file("settings.json")))
    check("...and the next one set as an alarm", our_alarm())
    adb("shell", "am", "broadcast", "-n", PACKAGE + "/.ReminderReceiver", "-a", "ro.noteorganizer.app.REMIND")
    time.sleep(2.0)
    said = adb("shell", "dumpsys", "notification", "--noredact")
    check("fired, it says how many notes the day has, and not their words",
          "You have 1 note for today" in said and "Dentist" not in said,
          "the notification %s" % ("found" if "You have 1 note" in said else "missing"))
    back_to("Search notes")
    adb("shell", "cmd", "statusbar", "expand-notifications")
    time.sleep(1.5)
    if tap("You have 1 note for today", wait=2.5):
        check("a tap on the notification opens the day on the calendar",
              not screen_says("📋 History", "Dentist", "+ Add note"))
    adb("shell", "cmd", "statusbar", "collapse")
    time.sleep(1.0)
    tap("Settings", exact=True, wait=1.2)
    tap("Reminders", exact=True, wait=1.2)
    toggle = switches()
    if len(toggle) == 1:
        adb("shell", "input", "tap", str(toggle[0][0]), str(toggle[0][1]))
        time.sleep(1.5)
    check("turned off: the setting saved, and the alarm gone",
          (read_file("settings.json") or {}).get("phone_reminder_notify") is False and not our_alarm())
    back_to("Search notes")


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
    # The keyboard up, the phone on its side: the top bar, a line of words
    # and two rows of format keys took every pixel it left, and not a word
    # of the note showed; + sat on the search box (an agent playing an
    # ordinary user, 2026-10-22). A line at least now, and no +.
    box, _lines = lines_of_field("milk and eggs")
    keyboard = bar_frame("ime")
    check("on its side with the keyboard up, the note's words stay in sight above the keys",
          box is not None and keyboard is not None and keyboard[0] > 0
          and box[3] - box[1] >= body_line_px() and box[1] < keyboard[0],
          "field %s, the keyboard from %s" % (box, keyboard))
    check("...and + keeps off the search box while the keyboard is up", find("+", exact=True) is None)
    keyboard_down()
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

    # « beside the note puts the list aside for a wider note, » brings it
    # back (asked for on the owner's phone, 2026-10-23). The editor is not
    # built again: its find bar stays open across both.
    # The note's title, measured: the card's own is the one further left.
    narrow = max(bounds("Plans"), default=None)
    tap_last("⋮", wait=1.0)                                       # the note's; the list has one too
    tap("Find in note", wait=1.0)
    type_text("trip")
    keyboard_down()
    tap("Hide the list", exact=True, wait=1.5)
    wider = bounds("Plans")
    check("« puts the list aside: the note takes its width, its find bar still open",
          find("Search notes") is None and narrow is not None and len(wider) == 1
          and wider[0][0] < narrow[0] - 200 and not screen_says("Close find"),
          "the title from %s, then %s" % (narrow, wider))
    shot(folder, "70-wide-list-aside", "trip", "Show the list")
    tap("Show the list", exact=True, wait=1.5)
    check("» brings the list back beside the note, the find bar still open",
          not screen_says("Search notes", "Groceries", "Close find", "Hide the list"))
    tap("Hide the list", exact=True, wait=1.5)
    tap("←", exact=True, wait=1.5)
    check("with the list aside, closing the note shows the list", not screen_says("Search notes", "Pick a note"))
    tap("Plans", exact=True, wait=1.5)
    check("...and the next note opens with the list still aside, until the app is closed",
          find("Search notes") is None and not screen_says("Show the list"))
    tap("Notes", exact=True, wait=1.5)
    check("the rail's Notes brings the list back beside the note",
          not screen_says("Search notes", "Hide the list"))

    tap("Calendar", exact=True, wait=1.5)
    check("the note stays open beside the calendar", not says_soon("📋 History", "ⓘ", "trip"))
    shot(folder, "46-wide-calendar", "📋 History", "ⓘ")
    # The month keeps its place while the History is shown, as Settings'
    # list does: scrolled down to the day's notes, then History and back.
    days = bounds("Mon")
    adb("shell", "input", "swipe", "700", "950", "700", "350", "400")
    time.sleep(1.0)
    gone, header = bounds("Mon"), bounds("+ Add note")
    tap("📋 History", exact=True, wait=1.5)
    tap("📅 Calendar", exact=True, wait=1.5)
    again = bounds("+ Add note")
    check("the month, scrolled, keeps its place after the History and back",
          len(days) == 1 and (not gone or days[0][1] - gone[0][1] >= 100)
          and len(header) == 1 and len(again) == 1 and abs(again[0][1] - header[0][1]) <= 8,
          "Mon at %s, then %s; + Add note at %s, after the History %s" % (days, gone, header, again))
    tap("Settings", exact=True, wait=1.5)
    check("Settings has no note beside it: the note is closed", not screen_says("Your name") and find("ⓘ") is None)
    # Its list keeps its place while a page is open: on its side the list
    # scrolls, and Back from the Trash came back at its top, the row just
    # left below the screen (the owner's phone, 2026-10-23).
    # Templates, in sight from the start, says the list really moved.
    before = bounds("Templates")
    adb("shell", "input", "swipe", "1200", "900", "1200", "350", "400")
    time.sleep(1.0)
    moved, trash = bounds("Templates"), bounds("Trash")
    tap("Trash", exact=True, wait=1.5)
    back(wait=1.5)
    again = bounds("Trash")
    # Moved up, or off the top: a longer list scrolls it further.
    check("Settings' list, scrolled, keeps its place after a page and Back",
          len(before) == 1 and (not moved or before[0][1] - moved[0][1] >= 100)
          and len(trash) == 1 and len(again) == 1 and abs(again[0][1] - trash[0][1]) <= 8,
          "Templates at %s, scrolled to %s; Trash at %s, after Back %s" % (before, moved, trash, again))
    # No note open beside the calendar: the pane offered a + that is only on
    # the list's tab.
    tap("Calendar", exact=True, wait=1.5)
    check("beside the calendar the empty pane points to the day's notes, not to a + it has not",
          not screen_says("Pick a note of the day") and find("tap +") is None)
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
        keyboard_down()
        check_settled("the editor's next save keeps the update", lambda: groceries().get("body"),
                      lambda b: b == "milk, from the desktop ok")
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


def box_of(label):
    """The one on-screen box whose text or description is exactly [label],
    or None: the tabs are drawn as both."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    raw = raw[raw.find("<?xml"):] if "<?xml" in raw else raw
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if label in (node.get("text", ""), node.get("content-desc", "")):
            box = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(box) == 4:
                return tuple(box)
    return None


def screen_sizes(folder):
    """Screens the app was not made on: a small phone, and tablets of 7, 10
    and 14 inches, upright (the owner has no tablet to try it on,
    2026-10-24). A small phone keeps the tabs in a bar along the bottom;
    from 600dp they are a rail down the left edge, and a note takes the
    list's place; from 840dp the note opens beside the list, upright too.
    On each the week's seven days are in sight, Sunday's edge inside the
    screen, and + is on it -- nothing laid out past the edge."""
    print("\n-- screens of every size --")
    if not seed_inside([
        {"id": 1, "uid": "s-groceries", "title": "Groceries", "body": "milk and eggs", "category": "General",
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:02:00"},
        {"id": 2, "uid": "s-plans", "title": "Plans", "body": "trip", "category": "Work",
         "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:01:00"},
    ], {"welcome_offered": True}, "Groceries"):
        return
    # name, pixels, density (dp = px * 160 / density), the tabs, the note beside
    sizes = [("a small phone", "720x1280", 320, "bar", False),        # 360 x 640 dp
             ("a 7-inch tablet", "1200x1920", 320, "rail", False),    # 600 x 960 dp
             ("a 10-inch tablet", "1600x2560", 320, "rail", False),   # 800 x 1280 dp
             ("a 14-inch tablet", "1848x2960", 340, "rail", True)]    # 870 x 1393 dp
    turn(0)
    try:
        for name, size, density, tabs, beside in sizes:
            adb("shell", "wm", "size", size)
            adb("shell", "wm", "density", str(density))
            time.sleep(3.0)
            width, height = map(int, size.split("x"))
            tag = name.split()[-1] if tabs == "bar" else name.split()[1]
            back_to("Search notes", "Groceries")
            notes_at, settings_at = box_of("Notes"), box_of("Settings")
            if tabs == "rail":
                ok = (notes_at is not None and settings_at is not None and notes_at[2] < width * 0.15
                      and notes_at[1] < settings_at[1])
                said_as = "a rail down the left edge"
            else:
                ok = (notes_at is not None and settings_at is not None and notes_at[1] == settings_at[1]
                      and notes_at[1] > height * 0.8 and notes_at[0] < settings_at[0])
                said_as = "a bar along the bottom"
            check("on %s the tabs are %s" % (name, said_as), ok, "Notes %s, Settings %s" % (notes_at, settings_at))
            plus = box_of("+")
            check("...and + is on the screen", plus is not None and plus[2] <= width and plus[3] <= height,
                  str(plus))
            shot(folder, "80-size-%s-list" % tag, "Search notes", "Groceries")
            open_note("Groceries")
            if beside:
                check("...a note opens beside the list, upright", not screen_says("Search notes", "Plans", "milk and eggs"))
            else:
                check("...a note opens in the list's place", find("Search notes") is None
                      and not screen_says("milk and eggs"))
            shot(folder, "81-size-%s-note" % tag, "Groceries", "ⓘ")
            back_to("Search notes", "Groceries")
            tap("Calendar", exact=True, wait=1.5)
            sun, mon = settle(lambda: (box_of("Sun"), box_of("Mon")), lambda days: all(days),
                              within=6.0, first=0)
            check("...the calendar's week fits, Monday to Sunday in sight",
                  sun is not None and mon is not None and sun[2] <= width and sun[2] - sun[0] >= 10
                  and mon[0] >= 0 and mon[1] == sun[1], "Mon %s, Sun %s" % (mon, sun))
            shot(folder, "82-size-%s-calendar" % tag, "📋 History", "Sun")
            tap("Notes", exact=True, wait=1.5)
    finally:
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
        turn(0)


if __name__ == "__main__":
    sys.exit(main())
