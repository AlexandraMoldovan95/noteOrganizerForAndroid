"""Photograph the app on the emulator, for the documentation.

The pictures in docs/screenshots/ are captures of THIS app running --
nothing here comes from anywhere else. This script is how they are made,
so remaking them is one command rather than an afternoon of tapping.

It seeds a small, tidy notebook of its own first: a real notebook is
somebody's private writing, and a documentation screenshot is the one
place it must never end up. The notes below are written for the picture.
The app's data is cleared first (`pm clear`) and the notebook is written
straight into the app's own folder through `run-as`, which a debug build
allows -- so the first screen is the list, with no "brought in" notice
and no welcome note in the way.

The set is taken twice in one run: in Soft Lavender, then every screen
again in Midnight Purple as NN-name-dark.png, for the website, which shows
each picture in the theme its page is in.

Every shot names what must be ON the screen before the shutter opens --
and, where it matters, what must be GONE from it. A shot whose screen
does not say so is not saved. A screenshot tool that photographs whatever
happens to be there is how a wrong picture ends up in the documentation
looking like proof.

    py tools/emulator/take_screenshots.py          the stills, both themes
    py tools/emulator/take_screenshots.py --gif    demo.gif alone (needs Pillow)
    py tools/emulator/take_screenshots.py --play   Google Play's set (needs Pillow)

--play takes the listing's eight (docs/PLAY_LISTING.md) at the size Google
Play accepts: the emulator's screen set to 1080 x 1920 for the run and put
back after it, each picture saved without alpha into build/play-listing/,
numbered in the listing's order. The README's set is 1080 x 2400, longer
than twice its width, which Google Play refuses.

Needs: a running emulator with the debug app installed, and adb on the
SDK path. Writes the app's own storage and scratch files on the emulator,
and on the computer rewrites docs/screenshots/ (or build/play-listing/
with --play); never a real notebook.
"""
import io
import json
import os
import re
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from datetime import date, timedelta

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emulator_only import ADB, SHOT, adb, adb_bytes, capture, fresh_tree  # noqa: E402 -- every command goes to the emulator, never a phone

PACKAGE = "ro.noteorganizer.app"
NOTEBOOK = "files/notebook"
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ANDROID, "docs", "screenshots")

# The calendar opens on the month it is run in, so the dates are worked
# out from today: a month photographed in November with October's notes
# in it would be an empty grid.
TODAY = date.today()
FIRST = TODAY.replace(day=1)
FIRST_MONDAY = FIRST + timedelta(days=(7 - FIRST.weekday()) % 7)

# Written for the camera: short enough to read at phone size, real
# enough to show what the app is for -- a headline, a preview, the
# badges, a checklist, a table, a pinned note and one with no title,
# and a series for the calendar.
NOTES = [
    {"id": 1, "uid": "shot-bread", "title": "Home-made bread",
     "body": "500 g flour\n300 ml warm water\n10 g salt\n\nKnead for 10 "
             "minutes, let it rise for 2 hours, bake for 45 minutes at 220 °C.",
     "category": "Personal", "date": (TODAY + timedelta(days=2)).isoformat(), "color": "#f9c74f",
     "checklist": [{"text": "flour", "done": True},
                   {"text": "yeast", "done": True},
                   {"text": "seeds", "done": False}],
     "formatting": [{"start": 0, "end": 11, "style": "bold"}],   # "500 g flour"
     "created": "2026-10-14 09:12:00", "updated": "2026-10-14 09:40:00"},
    {"id": 2, "uid": "shot-expenses", "title": "September expenses",
     # Written as the app writes a table: the figures right-aligned by
     # the spaces before them, which is where both apps keep a cell's
     # alignment. A markdown "---:" under them read as a column set by
     # hand to four characters, and the header was drawn "How…". Three
     # columns, the day each was paid between the two: two filled a
     # third of the screen and left the rest empty (the review panel,
     # 2026-11-15).
     "body": "| What        | Paid on  |  How much |\n"
             "| rent        | 1 Sep    |      1500 |\n"
             "| electricity | 12 Sep   |       180 |\n"
             "| internet    | 20 Sep   |        60 |",
     "category": "General",
     "created": "2026-10-13 18:00:00", "updated": "2026-10-13 18:20:00"},
    {"id": 3, "uid": "shot-idea", "title": "",
     "body": "An idea for Ana's birthday: a boat trip, then cake from the "
             "little place in the centre.",
     "category": "Ideas",
     "created": "2026-10-12 21:05:00", "updated": "2026-10-12 21:05:00"},
    {"id": 4, "uid": "shot-reading", "title": "To read",
     "body": "the book borrowed from Radu\nthe article about sleep",
     "category": "Work", "pinned": True,
     "checklist": [{"text": "chapter 1", "done": True},
                   {"text": "chapter 2", "done": False}],
     # A series, for the calendar's dots: every Monday and Thursday.
     "date": FIRST_MONDAY.isoformat(),
     "recurrence": {"freq": "weekly", "interval": 1, "weekdays": [0, 3]},
     "created": "2026-10-10 08:00:00", "updated": "2026-10-11 07:30:00"},
]

# Ana's birthday, from the idea above: a painted day with its marker -- in
# the month the calendar opens on, or the photo shows a grid without it.
_MONTH_END = (TODAY.replace(day=28) + timedelta(days=4)).replace(day=1) - timedelta(days=1)
BIRTHDAY = min(TODAY + timedelta(days=7), _MONTH_END).isoformat()

# The bread's day as the calendar's cell names it ("Monday, 26 October"),
# for the shot of the calendar with the note open beside it.
_BREAD = TODAY + timedelta(days=2)
BREAD_DAY = "%s, %d %s" % (_BREAD.strftime("%A"), _BREAD.day, _BREAD.strftime("%B"))

SETTINGS = {
    "username": "Maria",
    "categories": {"General": "#7c5cff", "Work": "#4cc9f0",
                   "Personal": "#f9c74f", "Ideas": "#f77f00",
                   "Urgent": "#ff5c7c"},
    "welcome_offered": True,
    # Light for the main set, whatever the emulator's own mode is today.
    "phone_theme_follows_phone": False,
    "theme": "light",
    "day_colors": {BIRTHDAY: "#f9c74f"},
    "day_emoji": {BIRTHDAY: ["🎂"]},
    # The day's reminder already shown today: on a Monday or a Thursday the
    # series falls on the day, and the list would open under its bar -- the
    # pictures would change with the day they were taken.
    "reminder_last_shown": TODAY.isoformat(),
}

# The last shot: the same list in a dark theme.
DARK = dict(SETTINGS, theme="dark")

# Then every screen again in Midnight Purple, as NN-name-dark.png: the
# website shows each picture in the theme its page is in (the owner,
# 2026-10-24). 06-dark is the light set's one dark picture, so it is not
# taken twice. Settings shows the dark themes there, not the light ones.
DARK_PASS_SKIPS = {"06-dark"}
DARK_WORDS = {
    "05-appearance": (["Light", "Dark", "Midnight Purple", "Ocean Blue"],
                      ["Sky Blue", "Follow the phone"]),
}

# name, what it shows, the steps that get there, what the screen must
# say, and what it must NOT say once it has arrived. KEYBOARD after them
# keeps the keyboard in the picture, and refuses one taken without it.
KEYBOARD = "keyboard"
SHOTS = [
    ("01-note-list", "The note list: greeting, cards, badges, a pinned note",
     [],
     ["Hi Maria,", "Home-made bread", "September expenses", "To read"], []),
    ("02-a-note", "A note open in the editor, its styling and checklist drawn",
     [("tap", "Home-made bread")],
     ["Home-made bread", "500 g flour", "yeast"], []),
    # The same note being written in: the cursor in its words, the format
    # bar above the keyboard. The stills had shown the editor only at rest,
    # its tools out of sight (the review panel, 2026-11-15). Nothing is
    # typed, so the note is not changed.
    ("12-writing", "Writing in a note: the cursor in its words, the format bar above the keyboard",
     [("tap_end", "Knead for 10")],
     ["Home-made bread", "Select text to format it"], [], KEYBOARD),
    ("03-a-table", "A table drawn as a grid rather than as pipe rows",
     [("back_to", "Search notes"), ("tap", "September expenses")],
     ["September expenses", "Paid on", "electricity", "1500"], []),
    ("04-search", "Search narrowing four notes to one, on a word in its body",
     [("back_to", "Search notes"), ("tap", "Search notes"), ("type", "boat")],
     ["boat trip"], ["Home-made bread", "September expenses"]),
    # Not the other mode's first theme as what must be gone: the line under
    # the switch names it ("Tap Dark for Midnight Purple, ...", since
    # 2026-11-22), so its second theme stands for the list.
    ("05-appearance", "Settings, Appearance: the Light | Dark switch and the light themes",
     [("clear_search",), ("tap_exact", "Settings"), ("tap", "Appearance")],
     ["Light", "Dark", "Soft Lavender", "Warm Cream"], ["Ocean Blue", "Follow the phone"]),
    ("07-calendar", "The calendar: a series' days, a dated note, a painted day",
     [("back_to", "Your name"), ("tap_exact", "Calendar")],
     ["📋 History", "Mon", "Sun", "🎂"], []),
    ("08-wide", "On its side: the note beside the list, the rail at the left",
     [("tap_exact", "Notes"), ("turn", 1), ("tap", "Home-made bread")],
     ["Search notes", "Home-made bread", "500 g flour", "Calendar"], []),
    # The note stays open beside the calendar, its day chosen in the month
    # (the day's notes under the month are below the foot of a phone on its
    # side).
    ("09-wide-calendar", "On its side: the calendar, the note's day chosen, the note open beside it",
     [("tap_exact", "Calendar"), ("tap", BREAD_DAY)],
     ["📋 History", "Home-made bread", "500 g flour"], []),
    # Settings has no note beside it: a section open across the width.
    # Categories, which fits a phone on its side; Appearance's four themes
    # run off its foot.
    ("10-wide-settings", "On its side: Settings, Categories open",
     [("tap_exact", "Settings"), ("tap_exact", "Categories")],
     ["General", "Work", "Personal", "Ideas", "Urgent", "1 note"], ["500 g flour", "Your name"]),
    # What a person meets before writing anything (the review panel's ask,
    # 2026-11-08): the notebook emptied, in the pass's own theme.
    ("11-empty", "A notebook with no notes yet: the list says how to begin",
     [("turn", 0), ("notes", [])],
     ["Hi Maria,", "No notes yet. Tap + to write your first."], ["Home-made bread", "To read"]),
    ("06-dark", "The same list in Midnight Purple",
     [("notes", NOTES), ("settings", DARK)],
     ["Hi Maria,", "Home-made bread", "To read"], []),
]

# The README's moving picture (--gif; the review panel's ask, 2026-11-08):
# one frame after each step, on the same notebook and with the same guard
# as every shot. A note is written in it, so the keyboard stays up while
# its words are typed -- that is what a person writing sees -- and is
# put away for every other frame. Each frame: how long it shows (ms), the
# steps, what the screen must say, what it must not, and whether the
# keyboard may be in it.
GIF_FRAMES = [
    (1800, [], ["Hi Maria,", "Home-made bread", "To read"], [], False),
    (1800, [("tap", "Home-made bread")], ["Home-made bread", "500 g flour", "yeast"], [], False),
    (1800, [("back_to", "Search notes"), ("tap", "September expenses")],
     ["September expenses", "electricity", "1500"], [], False),
    (700, [("back_to", "Search notes"), ("tap_exact", "+"), ("tap", "Title"), ("type", "Weekend")],
     ["Weekend"], [], True),
    (700, [("type", " trip")], ["Weekend trip"], [], True),
    (700, [("tap", "Write your note"), ("type", "Pack the tent")], ["Pack the tent"], [], True),
    (1300, [("type", " and the stove.")], ["Pack the tent and the stove."], [], True),
    (1800, [("back_to", "Search notes")], ["Weekend trip", "Home-made bread"], [], False),
    (1800, [("tap_exact", "Calendar")], ["📋 History", "Mon", "🎂"], [], False),
    (2200, [("tap_exact", "Notes"), ("settings", DARK)], ["Hi Maria,", "Weekend trip"], [], False),
]
GIF_WIDTH = 360

# Google Play's set (--play): the shots the listing shows, by the number it
# shows them in -- the list, a note, a table, writing, the calendar,
# search, the dark list, the themes. The others are not run: the wide ones
# turn the phone, and Google Play's phone set is upright.
PLAY_OUT = os.path.join(ANDROID, "build", "play-listing")
PLAY_SIZE = (1080, 1920)
PLAY_ORDER = {"01-note-list": 1, "02-a-note": 2, "03-a-table": 3, "12-writing": 4,
              "07-calendar": 5, "04-search": 6, "06-dark": 7, "05-appearance": 8}
# On a 9:16 screen the editor scrolls the cursor's line above the keyboard
# and the note's title goes up out of view: the words around the cursor
# and the format bar's hint say the shot instead.
PLAY_WORDS = {
    "12-writing": (["Knead for 10", "Select text to format it"], []),
}


def screen_size():
    """The emulator's screen as `wm size` says it: (physical, override or None)."""
    said = adb("shell", "wm", "size")
    physical = re.search(r"Physical size:\s*(\d+x\d+)", said)
    override = re.search(r"Override size:\s*(\d+x\d+)", said)
    return (physical.group(1) if physical else None,
            override.group(1) if override else None)


def play_picture(data, name):
    """One of Google Play's set: checked for its size, saved without alpha.
    Its path, or None, said why."""
    from PIL import Image
    image = Image.open(io.BytesIO(data))
    if image.size != PLAY_SIZE:
        print("   %dx%d, not %dx%d -- not saved" % (image.size + PLAY_SIZE))
        return None
    path = os.path.join(PLAY_OUT, "%d-%s.png" % (PLAY_ORDER[name], name[3:]))
    image.convert("RGB").save(path, "PNG", optimize=True)
    return path


def put(name, value):
    """A file written straight into the app's own notebook folder."""
    fd, local = tempfile.mkstemp(suffix=".json")
    with io.open(fd, "w", encoding="utf-8") as f:
        json.dump(value, f, ensure_ascii=False, indent=2)
    staging = "/data/local/tmp/shot_" + name
    adb("push", local, staging)
    os.remove(local)
    adb("shell", "chmod", "644", staging)
    adb("shell", "run-as", PACKAGE, "mkdir", "-p", NOTEBOOK)
    adb("shell", "run-as", PACKAGE, "cp", staging, "%s/%s" % (NOTEBOOK, name))
    adb("shell", "rm", "-f", staging)


def read(name):
    data = adb("exec-out", "run-as", PACKAGE, "cat",
               "%s/%s" % (NOTEBOOK, name), binary=True)
    try:
        return json.loads(data.decode("utf-8"))
    except ValueError:
        return None


def restart():
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", "%s/.MainActivity" % PACKAGE)
    time.sleep(4)


def seed(settings=SETTINGS):
    """A notebook written for the camera, in place of anybody's real one."""
    adb("shell", "pm", "clear", PACKAGE)
    put("notes_data.json", NOTES)
    put("settings.json", settings)
    restart()
    titles = [n.get("title") for n in read("notes_data.json") or []]
    if titles != [n["title"] for n in NOTES]:
        print("The seeded notebook did not arrive as written: %s" % titles)
        return False
    return True


def demo_status_bar(on):
    """A fixed clock and a full battery, so the frame is the app's.

    The emulator's own status bar carries whatever notification happens to
    be pending that afternoon. Android's demo mode replaces it with a
    stable one -- it is a SystemUI feature, not a trick, and it is turned
    off again at the end.
    """
    if on:
        adb("shell", "settings", "put", "global", "sysui_demo_allowed", "1")
        for extras in (["command", "enter"],
                       ["command", "clock", "-e", "hhmm", "0930"],
                       ["command", "battery", "-e", "level", "100",
                        "-e", "plugged", "false"],
                       ["command", "network", "-e", "wifi", "show",
                        "-e", "level", "4"],
                       # The mobile icon is left off: in demo mode it is
                       # drawn white whatever the page behind it, which
                       # on a light theme reads as a smudge.
                       ["command", "network", "-e", "mobile", "hide"],
                       ["command", "notifications", "-e", "visible", "false"]):
            adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo",
                "-e", *extras)
    else:
        adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo",
            "-e", "command", "exit")


def nodes(boxes=False):
    """The screen's UI tree, fresh: (text, description, centre, class), or
    with [boxes] the node's box (left, top, right, bottom) for its centre."""
    raw = fresh_tree(adb_bytes)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    found = []
    for node in root.iter("node"):
        bounds = [int(n) for n in re.findall(r"\d+", node.get("bounds", ""))]
        if len(bounds) != 4:
            continue
        centre = ((bounds[0] + bounds[2]) // 2, (bounds[1] + bounds[3]) // 2)
        found.append((node.get("text", ""), node.get("content-desc", ""),
                      bounds if boxes else centre, node.get("class", "")))
    return found


def screen_text():
    return "   ".join(t or d for t, d, _c, _k in nodes())


def tap(label, exact=False, patience=3.0):
    """Tap what says [label], looked for again for a moment before giving
    up, as the walk's tap: a screen still arriving answers one read
    without it."""
    deadline = time.time() + patience
    while True:
        for text, desc, centre, _kind in nodes():
            for value in (text, desc):
                if value and ((value == label) if exact else (label in value)):
                    adb("shell", "input", "tap", str(centre[0]), str(centre[1]))
                    time.sleep(1.5)
                    return True
        if time.time() > deadline:
            break
        time.sleep(0.4)
    print("   not found on screen: %r" % label)
    return False


def tap_end(label, patience=3.0):
    """Tap the foot of the text field that says [label], at its right: the
    cursor goes to the end of its words, and its handle hangs under them.
    Tapped at the centre, the cursor landed on the bread's empty line and
    its handle stood over the K of "Knead" (2026-11-21)."""
    deadline = time.time() + patience
    while True:
        for text, _desc, box, kind in nodes(boxes=True):
            if label in text and kind.endswith("EditText"):
                adb("shell", "input", "tap", str(box[2] - 24), str(box[3] - 12))
                time.sleep(1.5)
                return True
        if time.time() > deadline:
            break
        time.sleep(0.4)
    print("   no text field says: %r" % label)
    return False


def keyboard_up():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def hide_keyboard():
    """The keyboard is half the screen and none of it is this app.

    It is asked whether it is showing rather than blindly sent a BACK:
    a BACK with no keyboard up leaves the screen we came to photograph.
    And asked again until it says it has gone: the answer stays "showing"
    for a moment after the BACK that hid it, over a second on an emulator
    started that day, and a second BACK sent in that moment leaves the
    screen just the same (the walk's keyboard_down, 2026-10-22).
    """
    for _ in range(2):
        if not keyboard_up():
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        for _ in range(15):
            time.sleep(0.3)
            if not keyboard_up():
                return


def act(step):
    kind = step[0]
    if kind == "tap":
        return tap(step[1])
    if kind == "tap_exact":
        return tap(step[1], exact=True)
    if kind == "tap_end":
        return tap_end(step[1])
    if kind == "type":
        adb("shell", "input", "text", step[1].replace(" ", "%s"))
        time.sleep(1.5)
        return True
    if kind == "back_to":
        # Back until the screen says it; the first Back with the keyboard
        # up only closes the keyboard. Each Back is given three seconds to
        # arrive before the next: one sent past a slow screen leaves the app.
        for _ in range(4):
            if step[1] in screen_text():
                return True
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
            deadline = time.time() + 3
            while time.time() < deadline:
                time.sleep(0.4)
                if step[1] in screen_text():
                    return True
        return False
    if kind == "clear_search":
        # The search box keeps its words; select them all and delete, so
        # the next screen is not reached through a filtered list.
        for _text, _desc, centre, cls in nodes():
            if cls.endswith("EditText"):
                adb("shell", "input", "tap", str(centre[0]), str(centre[1]))
                adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
                for _ in range(12):
                    adb("shell", "input", "keyevent", "KEYCODE_DEL")
                break
        hide_keyboard()
        return True
    if kind == "settings":
        put("settings.json", step[1])
        restart()
        return True
    if kind == "notes":
        # The notebook written again, and read back as written: an empty
        # one for the first screen, the camera's own to go on after it.
        put("notes_data.json", step[1])
        restart()
        stored = read("notes_data.json")
        return [n.get("uid") for n in stored or []] == [n["uid"] for n in step[1]]
    if kind == "turn":
        # 0 upright, 1 on its side; the phone's own turning held off.
        adb("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        adb("shell", "settings", "put", "system", "user_rotation", str(step[1]))
        time.sleep(2.5)
        return True
    raise ValueError(kind)


def shutter(present, absent, keyboard=False):
    """The screen as PNG bytes, once it says every word in [present] and
    none in [absent]; None otherwise, said why, with what it said. The
    keyboard is put away first unless [keyboard] lets it be in the picture.

    Read again for up to ten seconds, not once: a screen read a fixed
    moment after its step is the walk's old flake (its says_soon, 8 Nov),
    and the first frame of the GIF, the app's first start on a cold
    emulator, was refused by one read that came before the list."""
    if not keyboard:
        hide_keyboard()
    time.sleep(0.8)
    deadline = time.time() + 10
    late = False
    while True:
        text = screen_text()
        missing = [w for w in present if w not in text]
        still = [w for w in absent if w in text]
        if not (missing or still) or time.time() > deadline:
            break
        time.sleep(0.5)
        late = True
    if missing or still:
        print("   the screen does not say %s%s -- not photographing; it says: %s"
              % (", ".join(repr(m) for m in missing),
                 (" and still says %s" % ", ".join(repr(s) for s in still))
                 if still else "", text[:300] or "(nothing read)"))
        return None
    if late:
        time.sleep(0.6)  # arrived during the wait: let its animation end
    if not capture(adb_bytes):
        print("   the screen could not be captured -- not photographing")
        return None
    return adb("exec-out", "cat", SHOT, binary=True)


def take_gif():
    """docs/screenshots/demo.gif from GIF_FRAMES; what failed, or nothing.
    One frame that fails, or a note not saved as typed, writes no GIF: the
    one there stays as it was."""
    try:
        from PIL import Image
    except ImportError:
        print("--gif needs Pillow (py -m pip install Pillow)")
        return ["Pillow"]
    if not seed(SETTINGS):
        return ["seed"]
    demo_status_bar(True)
    frames = []
    for number, (ms, steps, present, absent, keyboard) in enumerate(GIF_FRAMES, 1):
        print("-- frame %d" % number)
        if not all(act(step) for step in steps):
            return ["frame %d" % number]
        data = shutter(present, absent, keyboard)
        if data is None:
            return ["frame %d" % number]
        image = Image.open(io.BytesIO(data)).convert("RGB")
        height = round(image.height * GIF_WIDTH / image.width)
        frames.append((image.resize((GIF_WIDTH, height), Image.LANCZOS), ms))
    # The words typed in the picture, read back from the file the app wrote.
    written = [n for n in read("notes_data.json") or [] if n.get("title") == "Weekend trip"]
    if len(written) != 1 or written[0].get("body") != "Pack the tent and the stove.":
        print("   the note written in the picture was not saved as typed: %r" % written)
        return ["the written note"]
    images = [im.convert("P", palette=Image.ADAPTIVE, colors=128) for im, _ms in frames]
    path = os.path.join(OUT, "demo.gif")
    images[0].save(path, save_all=True, append_images=images[1:],
                   duration=[ms for _im, ms in frames], loop=0, optimize=True)
    print("   %s: %d frames, %d KB" % (os.path.relpath(path, ANDROID), len(frames),
                                     os.path.getsize(path) // 1024))
    return []


def main():
    if not os.path.exists(ADB):
        print("adb is not at %s" % ADB)
        return 2
    if "\tdevice" not in adb("devices"):
        print("No emulator running. Start one and try again.")
        return 2
    os.makedirs(OUT, exist_ok=True)
    # The turning as found, put back in the finally below.
    found = {key: adb("shell", "settings", "get", "system", key).strip()
             for key in ("accelerometer_rotation", "user_rotation")}
    # The keyboard follows the phone's own light or dark, not the app's: the
    # dark pass makes the phone dark too, as it mostly is under a dark theme,
    # or 12-writing-dark showed a white keyboard under a dark note. "Night
    # mode: no", as found, put back in the finally below.
    night = adb("shell", "cmd", "uimode", "night").strip().rsplit(":", 1)[-1].strip()

    gif = "--gif" in sys.argv[1:]
    play = "--play" in sys.argv[1:]
    if gif and play:
        print("--gif and --play are two runs: ask for one at a time")
        return 2
    if play:
        try:
            import PIL  # noqa: F401 -- asked now, not after the emulator's screen has changed
        except ImportError:
            print("--play needs Pillow (py -m pip install Pillow)")
            return 2
        os.makedirs(PLAY_OUT, exist_ok=True)
    # The screen's size as found, put back in the finally below: --play
    # sets it to Google Play's 9:16 for the run.
    _physical, found_override = screen_size()
    if gif:
        passes = ()
    elif play:
        passes = ((SETTINGS, ""),)
    else:
        passes = ((SETTINGS, ""), (DARK, "-dark"))
    failed = []
    try:
        if play:
            adb("shell", "wm", "size", "%dx%d" % PLAY_SIZE)
            time.sleep(2)
            if screen_size()[1] != "%dx%d" % PLAY_SIZE:
                print("the emulator's screen did not take %dx%d: %s" % (PLAY_SIZE + (adb("shell", "wm", "size").strip(),)))
                return 1
        for settings, suffix in passes:
            adb("shell", "cmd", "uimode", "night", "yes" if suffix else "no")
            if not seed(settings):
                return 1
            demo_status_bar(True)
            for name, caption, steps, present, absent, *rest in SHOTS:
                if suffix and name in DARK_PASS_SKIPS:
                    continue
                if play and name not in PLAY_ORDER:
                    continue
                if play:
                    present, absent = PLAY_WORDS.get(name, (present, absent))
                if suffix:
                    present, absent = DARK_WORDS.get(name, (present, absent))
                keyboard = KEYBOARD in rest
                name += suffix
                print("-- %s: %s" % (name, caption))
                if not all(act(step) for step in steps):
                    failed.append(name)
                    continue
                if keyboard and not any(keyboard_up() or time.sleep(0.5) for _ in range(10)):
                    print("   no keyboard came up -- not photographing")
                    failed.append(name)
                    continue
                data = shutter(present, absent, keyboard)
                if data is None:
                    failed.append(name)
                    continue
                if play:
                    target = play_picture(data, name)
                    if target is None:
                        failed.append(name)
                        continue
                else:
                    target = os.path.join(OUT, name + ".png")
                    with open(target, "wb") as f:
                        f.write(data)
                print("   %s" % os.path.relpath(target, ANDROID))
            # Upright again: the dark pass ends on its side, at 08-wide.
            act(("turn", 0))
        if gif:
            failed = take_gif()
    finally:
        # However the run ended: a failed seed or a stuck adb left the
        # emulator on its side, its own turning off.
        # A key that was never set reads "null": it is unset again, not left
        # at the run's value. One that fails does not keep the other back.
        # Read back, and said when it is not as found: adb's settings
        # command does not fail on a write it refused.
        for key, value in found.items():
            try:
                if value.isdigit():
                    adb("shell", "settings", "put", "system", key, value)
                elif value == "null":
                    adb("shell", "settings", "delete", "system", key)
                now = adb("shell", "settings", "get", "system", key).strip()
            except (Exception, SystemExit):  # noqa: BLE001 -- under the run's own error
                now = "(adb did not answer)"
            if now != value:
                print("the emulator's %s is %s, not %r as found: set it back by hand" % (key, now, value))
        try:
            if night in ("yes", "no", "auto"):
                adb("shell", "cmd", "uimode", "night", night)
            now = adb("shell", "cmd", "uimode", "night").strip().rsplit(":", 1)[-1].strip()
        except (Exception, SystemExit):  # noqa: BLE001 -- under the run's own error
            now = "(adb did not answer)"
        if now != night:
            print("the emulator's night mode is %s, not %r as found: set it back by hand" % (now, night))
        if play:
            try:
                if found_override:
                    adb("shell", "wm", "size", found_override)
                else:
                    adb("shell", "wm", "size", "reset")
                now = screen_size()[1]
            except (Exception, SystemExit):  # noqa: BLE001 -- under the run's own error
                now = "(adb did not answer)"
            if now != found_override:
                print("the emulator's screen size is %s, not %s as found: `adb shell wm size reset`"
                      % (now or "its own", found_override or "its own"))
        demo_status_bar(False)

    print()
    if failed:
        print("NOT TAKEN: %s" % ", ".join(failed))
        return 1
    print("Done. Every picture is this app, on a notebook written for")
    print("the camera -- never on anybody's real one.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
