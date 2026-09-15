"""Photograph the app on the emulator, for the documentation.

The pictures in docs/screenshots/ are captures of THIS app running --
nothing here comes from anywhere else. This script is how they are made,
so remaking them is one command rather than an afternoon of tapping.

It seeds a small, tidy notebook of its own first: a real notebook is
somebody's private writing, and a documentation screenshot is the one
place it must never end up. The notes below are written for the picture.

Every shot names what must be ON the screen before the shutter opens, and
a shot whose screen does not say so is not saved. A screenshot tool that
photographs whatever happens to be there is how a wrong picture ends up
in the documentation looking like proof.

    py tools/emulator/take_screenshots.py

Needs: a running emulator with the app installed, and adb on the SDK path.
"""
import io
import json
import os
import re
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")

ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk",
                   "platform-tools", "adb.exe")
PACKAGE = "com.example.noteorganizer"
FILES = "/sdcard/Android/data/%s/files" % PACKAGE
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(ANDROID, "docs", "screenshots")

# Written for the camera: short enough to read at phone size, real
# enough to show what the app is for.
NOTEBOOK = [
    {"id": 1, "title": "Paine de casa",
     "body": "500g faina\n300ml apa calduta\n10g sare\n\nFramantat 10 minute, "
             "crescut 2 ore, copt 45 de minute la 220 de grade.",
     "category": "General", "date": "2026-09-20",
     "checklist": [{"text": "faina", "done": True},
                   {"text": "drojdie", "done": True},
                   {"text": "seminte", "done": False}],
     "formatting": [{"start": 0, "end": 10, "style": "bold"}],   # "500g faina"
     "created": "2026-09-14 09:12:00", "updated": "2026-09-14 09:40:00"},
    {"id": 2, "title": "Cheltuieli septembrie",
     "body": "| Ce | Cat |\n| --- | ---: |\n| chirie | 1500 |\n"
             "| curent | 180 |\n| internet | 60 |",
     "category": "General",
     "created": "2026-09-13 18:00:00", "updated": "2026-09-13 18:20:00"},
    {"id": 3, "title": "",
     "body": "Ideea pentru cadoul de ziua Anei: o plimbare cu barca si "
             "prajitura de la locul din centru.",
     "category": "General",
     "created": "2026-09-12 21:05:00", "updated": "2026-09-12 21:05:00"},
    {"id": 4, "title": "De citit",
     "body": "cartea imprumutata de la Radu\narticolul despre somn",
     "category": "General",
     "checklist": [{"text": "capitolul 1", "done": True},
                   {"text": "capitolul 2", "done": False}],
     "created": "2026-09-10 08:00:00", "updated": "2026-09-11 07:30:00"},
]

# name, caption, the taps that get there, and what the screen must say
# once it has arrived.
SHOTS = [
    ("01-note-list", "The note list: cards with their headline, preview and badges",
     [],
     ["Paine de casa", "Cheltuieli septembrie", "De citit", "4 notite"]),
    ("02-a-note", "A note open, with its styling drawn",
     [("tap_text", "Paine de casa")],
     ["Paine de casa", "faina", "Editeaza"]),
    ("03-a-table", "A table drawn as a grid rather than as pipe rows",
     [("back",), ("tap_text", "Cheltuieli")],
     ["Cheltuieli septembrie", "chirie", "1500"]),
    ("04-search", "Search, by the desktop's own rule",
     [("back",), ("tap_edit",), ("type", "prajitura")],
     ["prajitura", "1 din 4 notite", "Ideea pentru cadoul"]),
]


def adb(*args, **kwargs):
    env = dict(os.environ)
    env["MSYS_NO_PATHCONV"] = "1"   # or a bash shell rewrites /sdcard/...
    kwargs.setdefault("env", env)
    return subprocess.run([ADB] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace", **kwargs)


def seed():
    """A notebook written for the camera, in place of anybody's real one."""
    local = os.path.join(HERE, "_docs_notebook.json")
    io.open(local, "w", encoding="utf-8").write(
        json.dumps(NOTEBOOK, ensure_ascii=False, indent=2))
    adb("push", local, FILES + "/notes_data.json")
    adb("shell", "rm", "-f", FILES + "/trash.json")
    os.remove(local)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-n", "%s/.MainActivity" % PACKAGE)
    time.sleep(4)


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
                       ["command", "network", "-e", "mobile", "show",
                        "-e", "level", "4", "-e", "datatype", "none"],
                       ["command", "notifications", "-e", "visible", "false"]):
            adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo",
                "-e", *extras)
    else:
        adb("shell", "am", "broadcast", "-a", "com.android.systemui.demo",
            "-e", "command", "exit")


NODE = re.compile(r"<node[^>]*>")
ATTR = re.compile(r'(\w[\w-]*)="([^"]*)"')


def nodes():
    """What is on the screen right now, as a list of dicts."""
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    dump = os.path.join(HERE, "_ui.xml")
    adb("pull", "/sdcard/ui.xml", dump)
    xml = io.open(dump, encoding="utf-8", errors="replace").read()
    os.remove(dump)
    found = []
    for match in NODE.finditer(xml):
        found.append(dict(ATTR.findall(match.group(0))))
    return found


def centre(node):
    left, top, right, bottom = (
        int(n) for n in re.findall(r"\d+", node.get("bounds", "")))
    return (left + right) // 2, (top + bottom) // 2


def find(match):
    for node in nodes():
        if match(node):
            return node
    return None


def act(step):
    kind = step[0]
    if kind in ("tap_text", "tap_edit"):
        if kind == "tap_text":
            node = find(lambda n: step[1] in n.get("text", ""))
            what = repr(step[1])
        else:
            # The search box has no text of its own to aim at: its
            # placeholder is drawn behind the field, not in it.
            node = find(lambda n: n.get("class", "").endswith("EditText"))
            what = "the search field"
        if node is None:
            print("   not found on screen: %s" % what)
            return False
        spot = centre(node)
        adb("shell", "input", "tap", str(spot[0]), str(spot[1]))
    elif kind == "type":
        adb("shell", "input", "text", step[1])
    elif kind == "back":
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    return True


def hide_keyboard():
    """The keyboard is half the screen and none of it is this app.

    It is asked whether it is showing rather than blindly sent a BACK:
    a BACK with no keyboard up leaves the screen we came to photograph.
    """
    for _ in range(3):
        shown = adb("shell", "dumpsys", "input_method").stdout
        if "mInputShown=true" not in shown:
            return
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1)


def on_screen(words):
    """Every word the shot promised, actually drawn."""
    text = "   ".join(n.get("text", "") for n in nodes())
    return [word for word in words if word not in text]


def main():
    if not os.path.exists(ADB):
        print("adb is not at %s" % ADB)
        return 2
    if "device" not in adb("devices").stdout:
        print("No emulator running. Start one and try again.")
        return 2
    if not os.path.isdir(OUT):
        os.makedirs(OUT)

    seed()
    demo_status_bar(True)
    failed = []
    try:
        for name, caption, steps, expect in SHOTS:
            print("-- %s: %s" % (name, caption))
            if not all(act(step) for step in steps):
                failed.append(name)
                continue
            hide_keyboard()
            missing = on_screen(expect)
            if missing:
                print("   the screen does not say: %s -- not photographing"
                      % ", ".join(repr(m) for m in missing))
                failed.append(name)
                continue
            adb("shell", "screencap", "-p", "/sdcard/shot.png")
            target = os.path.join(OUT, name + ".png")
            adb("pull", "/sdcard/shot.png", target)
            print("   %s" % os.path.relpath(target, ANDROID))
    finally:
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
