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
    py tools/emulator/walk.py <photos folder> <part> [<part> ...]

The second walks the parts named -- `cell_menu`, `limits_round8` -- alone,
on the same road: the machine's turn, the wait for the emulator, the
install. A part called from a script of its own takes the turn at its first
adb call.

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
from emulator_only import ADB, AdbTimeout, command, fresh_tree, run  # noqa: E402 -- every command goes to the emulator, never a phone

PACKAGE = "ro.noteorganizer.app"
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(os.path.dirname(HERE))
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
NOTEBOOK = "files/notebook"

failures = []


# Whether this run holds the machine's turn ([take_the_turn]).
turn_held = False


def adb(*args, binary=False, check=False, timeout=None):
    """One adb call to the emulator, held to a time limit (emulator_only.run):
    past it the walk stops, naming the call, rather than wait on for ever.
    The first takes the machine's turn when nothing has yet: a part called
    from a script of its own walked the emulator beside whatever else ran,
    with no memory ceiling and no homes of its own, and took the turn only at
    the export part, halfway (the final audit's ninth round)."""
    if not turn_held:
        stop = take_the_turn()
        if stop is not None:
            raise SystemExit(stop)
    out = run(command(*args), timeout=timeout, capture_output=True)
    if check and out.returncode != 0:
        raise RuntimeError("adb %s: %s" % (" ".join(args), out.stderr.decode(errors="replace")))
    return out.stdout if binary else out.stdout.decode("utf-8", errors="replace")


def busy_share(seconds=3.0):
    """How much of the emulator's processor was at work over [seconds], 0 to
    1, from the counters in /proc/stat (idle and waiting count as rest)."""
    def counters():
        # Read again while it comes back empty: right after an install the
        # phone's shell gave back nothing for a few seconds ([install]).
        for _ in range(15):
            values = [int(v) for v in adb("shell", "head", "-1", "/proc/stat").split()[1:]]
            if len(values) >= 5:
                return sum(values), values[3] + values[4]
            time.sleep(1.0)
        raise RuntimeError("the emulator's /proc/stat could not be read")
    total, rest = counters()
    time.sleep(seconds)
    total_after, rest_after = counters()
    spent = total_after - total
    return 1.0 - (rest_after - rest) / spent if spent > 0 else 0.0


def install(apk):
    """[apk] installed, and the phone answering again before anything looks
    at it: for some seconds after an install its shell gave back nothing, and
    a part's first look read no tree, or a boot check no counters (wave 2 and
    its lows wave, after each change of APK; each part fine walked again)."""
    adb("install", "-r", apk, check=True)
    deadline = time.time() + 60
    while time.time() < deadline and not fresh_tree(lambda *a: adb(*a, binary=True), tries=1):
        time.sleep(1.0)


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


# Looks at the screen that read no tree at all ([screen_xml]).
unreadable = 0


def screen_xml(tries=3):
    """The screen's tree as uiautomator dumps it -- fresh. The last dump's
    file goes first: a dump that writes nothing -- the app's thread held past
    uiautomator's patience ("null root node"), or ten seconds of events
    ("could not get idle state") -- left the screen before it in the file,
    and the walk read that: a freeze longer than the look's patience passed
    every check of the frames drawn in it, the worse the surer (the final
    audit's ninth round). Asked [tries] times; a screen that never reads is
    a failure of its own, so no check passes on a tree that is not there."""
    global unreadable
    said = []

    def asked(*args):
        out = adb(*args, binary=True)
        if args[0] == "shell":
            said.append(out.decode("utf-8", errors="replace"))
        return out

    raw = fresh_tree(asked, tries)
    if raw:
        return raw
    unreadable += 1
    fail("the screen could not be read: uiautomator wrote no tree (%s)"
         % (said[-1] if said else "").strip()[:160])
    return ""


def tree():
    """The screen's tree, fresh ([screen_xml]): (text, description, box,
    path) for each node, the path its place from the root down, so the
    block two nodes sit in apart can be found."""
    try:
        root = ET.fromstring(screen_xml())
    except ET.ParseError:
        return []
    out = []

    def down(node, path):
        box = re.findall(r"\d+", node.get("bounds", ""))
        if len(box) == 4:
            out.append((node.get("text", ""), node.get("content-desc", ""), tuple(map(int, box)), path))
        for k, child in enumerate(node.findall("node")):
            down(child, path + (k,))
    down(root, ())
    return out


def said_over(words, *targets, within=6.0, touching=False):
    """Waits until the screen says [words], and reads, on that same look,
    which of [targets] -- (name, test of a node's text and description) --
    the message's block covers: the words' ancestor just below the one they
    share with the target, the message with its margin. A message drawn just
    above the keyboard covered the editor's own bar and "A new item…" with
    its Add, and took their taps (the final audit's ninth round). With
    [touching], a target counts as covered when the block reaches into it
    anywhere, not only over its middle. The words missing, and the names of
    the targets covered or not on the screen."""
    seen = settle(tree, lambda nodes_: any(words in t or words in d for t, d, _, _ in nodes_),
                  within=within, first=0)
    said = next((n for n in seen if words in n[0] or words in n[1]), None)
    if said is None:
        return [words], []
    covered = []
    for name, test in targets:
        target = next((n for n in seen if test(n[0], n[1])), None)
        if target is None:
            covered.append(name + " (not on the screen)")
            continue
        common = 0
        while common < min(len(said[3]), len(target[3])) and said[3][common] == target[3][common]:
            common += 1
        block = next((n for n in seen if n[3] == said[3][:common + 1]), said)
        x1, y1, x2, y2 = block[2]
        tx1, ty1, tx2, ty2 = target[2]
        if touching:
            over = x1 < tx2 and tx1 < x2 and y1 < ty2 and ty1 < y2
        else:
            cx, cy = (tx1 + tx2) // 2, (ty1 + ty2) // 2
            over = x1 <= cx <= x2 and y1 <= cy <= y2
        if over:
            covered.append(name)
    return [], covered


def nodes():
    """The screen's UI tree, fresh: (text, description, centre) for each node."""
    raw = screen_xml()
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


def logged(pids, *words):
    """The lines of the log, since it was last cleared, that the processes
    [pids] wrote and that hold one of [words]. The log is every process's:
    Play services wrote a TransactionTooLargeException of its own while a
    note went to the share sheet (2026-10-29), and a check reading every
    line failed a step the app had passed -- once in three runs."""
    pids = {p for p in pids if p}
    found = []
    for line in adb("logcat", "-d", "-v", "threadtime").splitlines():
        m = re.match(r"\S+ \S+\s+(\d+)\s", line)
        if m and m.group(1) in pids and any(w in line for w in words):
            found.append(line)
    return found


def route_pids(app, sheet):
    """The processes a share goes through: the app ([app], its pid read
    before the tap, so an app the share stopped is still named), the system
    that starts the sheet, and the sheet itself, read once it is up."""
    return [app] + [adb("shell", "pidof", name).strip() for name in ("system_server", sheet)]


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
        # What was there instead, kept: the first look after a slow cold
        # start found something else on the screen for twenty seconds, and
        # a failure that kept nothing could not say what (2026-11-02).
        seen = " | ".join(part for t, d, _ in nodes() for part in (t, d) if part)
        fail("photo %s: the screen does not say %s -- it says %r" % (name, missing, seen[:200]))
        photograph(folder, name + "-FAILED")
        return False
    photograph(folder, name)
    return True


def shot_as_said(folder, name, *words, within=6.0, over_keys=False):
    """[says_soon], photographed the moment the screen is read saying
    [words]: a message lives four seconds, and [shot], reading the screen
    again first, photographed five of them gone -- the photos named for
    what was said showed none of it (the final audit's eighth round). The
    words missing, as [says_soon] gives them; no photo without them.
    [over_keys]: the keyboard is up here, and the first of [words] must be
    drawn above it -- the UI tree holds a node the keys cover, and the
    note's editor said its words under them unseen."""
    def read():
        seen = nodes()
        return seen, [w for w in words if not any(w in t or w in d for t, d, _ in seen)]
    seen, missing = settle(read, lambda r: not r[1], within=within, first=0)
    if missing:
        fail("photo %s: the screen does not say %s" % (name, missing))
        return missing
    photograph(folder, name)
    if over_keys:
        top = keyboard_top()
        y = next(c[1] for t, d, c in seen if words[0] in t or words[0] in d)
        check("%s: said above the keyboard, not under its keys" % name, top is not None and y < top,
              "the words at %d, the keyboard from %s" % (y, top))
    return missing


@contextlib.contextmanager
def messages_held(ms):
    """The app's messages held for [ms] while this is open: Android's own
    accessibility setting for how long words that need no answer stay, which
    Material's messages keep (SnackbarDuration.toMillis asks the
    accessibility manager). Put back as it was after."""
    name = "accessibility_non_interactive_ui_timeout_ms"
    was = adb("shell", "settings", "get", "secure", name).strip()
    adb("shell", "settings", "put", "secure", name, str(ms))
    try:
        yield
    finally:
        if was in ("", "null"):
            adb("shell", "settings", "delete", "secure", name)
        else:
            adb("shell", "settings", "put", "secure", name, was)


def keyboard_top():
    """Where the keyboard begins on the screen, from the window manager's
    insets; None while it is down."""
    if not keyboard_shown():
        return None
    at = re.search(r"type=ime frame=\[\d+,(\d+)\]\[\d+,\d+\][^\n]*visible=true", adb("shell", "dumpsys", "window"))
    return int(at.group(1)) if at else None


def photograph(folder, name):
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
    try:
        burst.wait(timeout=120)
    except subprocess.TimeoutExpired:
        burst.kill()
        raise AdbTimeout("the screen captures did not end within 120 s: the emulator is stuck -- "
                         "stop it (adb emu kill) before anything else runs")
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


def activity_state():
    """The app's screen as Android has it -- RESUMED, PAUSED, STOPPED --
    or None when it has none."""
    out = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"Hist\s+#\d+: ActivityRecord\{[^}]*%s/\.MainActivity[^}]*\}.*?state=(\w+)" % re.escape(PACKAGE),
                  out, re.S)
    return m.group(1) if m else None


def home(patience=15.0):
    """Home, and back once Android has stopped the app: the moment it
    writes what the editor holds (MainActivity.onStop). Android stops the
    app left when the launcher has nothing more to do, or ten seconds
    later; an emulator drawing in software keeps its launcher busy, and
    the stop came at ten seconds, every time (2026-10-30). Read a second
    or three after Home, the app was only paused -- its words not written
    yet, and an `am kill` of an app still on the screen did nothing, so a
    check that the words outlived the process passed with the process
    never ended."""
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    if stopped(patience):
        return True
    fail("Home: Android did not stop the app within %d s" % patience)
    return False


def stopped(patience=15.0):
    """Whether Android has stopped the app -- behind Home, or behind
    another app's screen such as its file picker -- within [patience]
    seconds, read from Android, not assumed after a pause."""
    deadline = time.time() + patience
    while time.time() < deadline:
        if activity_state() == "STOPPED":
            return True
        time.sleep(0.5)
    return False


def kill_behind():
    """Android ending the app behind Home, as it ends one whose memory it
    needs: True once the process is gone, read -- `am kill` ends only an
    app off the screen, and says nothing when it ends none."""
    adb("shell", "am", "kill", PACKAGE)
    deadline = time.time() + 5.0
    while adb("shell", "pidof", PACKAGE).strip() and time.time() < deadline:
        time.sleep(0.3)
    return adb("shell", "pidof", PACKAGE).strip() == ""


def seek(found, tries=8, fast=False):
    """Scrolled down until [found]() holds: the screen's tree holds only
    what is on the screen, and a word below it is sought as a person seeks it."""
    for _ in range(tries):
        if found():
            return True
        adb("shell", "input", "swipe", "900", "1900", "900", "700", "120" if fast else "400")
        time.sleep(0.8 if fast else 1.0)
    return found()


def take_the_turn():
    """The desktop's guard over this run ([desktop_guest]): the machine's
    turn, the memory ceiling, homes of its own. None once it is held; the
    exit code to stop with when another run holds the turn past the wait
    (the desktop guest's BUSY, 75), said with what was not done. Taken once
    a run: by [main] as it starts, or by the first adb call of a part
    walked on its own ([adb])."""
    global turn_held
    if turn_held:
        return None
    if not os.path.isdir(DESKTOP):
        print("machine : the desktop is not at %s -- its parts are skipped, and no turn is taken" % DESKTOP)
        turn_held = True
        return None
    sys.path.insert(0, os.path.join(ANDROID, "tools"))
    import desktop_guest
    try:
        desktop_guest.settle(DESKTOP)
    except SystemExit as stop:
        if stop.code == 2:
            raise
        print("\nThe walk did not get the machine's turn (exit %s): nothing was installed or walked. "
              "Stop the emulator (adb emu kill) if nothing else needs it." % stop.code)
        return stop.code if isinstance(stop.code, int) else 75
    turn_held = True
    return None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    folder = sys.argv[1]
    # Parts named after the folder are walked alone, on the same road as the
    # whole walk: the turn, the wait for the emulator, the install.
    parts = sys.argv[2:]
    # A part is what the whole walk calls below, not any function here:
    # "run" or "adb" passed as one and was run with the folder.
    import inspect
    known = set(re.findall(r"^    (\w+)\(folder\)$", inspect.getsource(main), re.M))
    unknown = [p for p in parts if p not in known]
    if unknown:
        print("no such part of the walk: %s" % ", ".join(unknown))
        return 2
    os.makedirs(folder, exist_ok=True)
    # The machine's one heavy-job turn, taken here, before anything runs,
    # and held for the whole walk: taken at the export part, where the
    # desktop's guard was first called, the walk ran its first sixteen parts
    # beside a desktop suite, then waited for it up to an hour with the
    # emulator up (the final audit's eighth round). The desktop's code the
    # walk runs (its share-code encoder, its export, its templates) runs under
    # its memory ceiling from here on. adb's path was read before this, at
    # import (emulator_only.ADB): the guard points LOCALAPPDATA at an empty
    # folder of the run's own.
    turn = take_the_turn()
    if turn is not None:
        return turn
    if not wait_for_boot():
        print("The emulator did not finish starting.")
        return 2
    # Off, once: the stylus tutorial swallows typed text on a fresh image.
    adb("shell", "settings", "put", "secure", "stylus_handwriting_enabled", "0")
    print("installing", APK)
    install(APK)

    if parts:
        for part in parts:
            globals()[part](folder)
        print("\n%d step(s) failed%s" % (len(failures), (": " + "; ".join(failures)) if failures else "."))
        return 1 if failures else 0

    brand_new(folder)
    seeded(folder)
    outward(folder)
    calendar(folder)
    move_day(folder)
    move_day_keeps_new_item(folder)
    off_schedule(folder)
    listing(folder)
    list_keeps_place(folder)
    what_hides_the_list(folder)
    arranged_from_the_top(folder)
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
    paste_at_marker(folder)
    reminders(folder)
    wide(folder)
    screen_sizes(folder)
    trash_select(folder)
    changes(folder)
    failed_save(folder)
    checklist_words_kept(folder)
    backup_too_large(folder)
    big_table(folder)
    code_started_once(folder)
    symbol_labels(folder)
    google_backup(folder)
    arrange_without_drag(folder)
    states_said(folder)
    calendar_large_letters(folder)
    import_questions_quick(folder)
    reminder_new_zone(folder)
    day_turns_open(folder)
    template_long_comments(folder)
    answers_after_restart(folder)
    limits_round3(folder)
    limits_round4(folder)
    limits_round5(folder)
    limits_round6(folder)
    days_in_the_note(folder)
    template_draft(folder)
    limits_round7(folder)
    limits_round8(folder)
    limits_round10(folder)
    # Written with their fixes on 2026-10-28 and walked alone then; the full
    # walk is the check before an install, so they belong in it.
    hostile_backups(folder)
    code_read_away(folder)
    long_lines(folder)
    kept_aside(folder)

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
    """A notebook copied in from the older folder, written for the camera:
    it is read whole, and its notes, list and calendar are photographed."""
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
    # The licenses: the app's own MIT license and those of the libraries it
    # is built with, on a page of their own -- and Back goes to About.
    if find("View", True) is None:
        adb("shell", "input", "swipe", "540", "1900", "540", "900", "300")
        time.sleep(0.8)
    tap("View", exact=True, wait=1.5)
    missing = screen_says("Open-source licenses", "MIT License", "Android Jetpack",
                          "org.jetbrains.kotlin:kotlin-stdlib", "Apache License")
    check("About → Open-source licenses → View shows the app's license and its libraries'",
          not missing, ", ".join(missing))
    shot(folder, "69b-open-source-licenses", "MIT License")
    back(1.2)
    missing = screen_says("Free software under the MIT license, provided as is.")
    check("Back from the licenses returns to About", not missing, ", ".join(missing))
    if find("Read", True) is None:
        adb("shell", "input", "swipe", "540", "1900", "540", "900", "300")
        time.sleep(0.8)
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


def seed_inside(notes, settings, shows, clear=True):
    """A notebook written for the walk, the app started on it -- and a
    failure, at once and by name, when the app does not show it. Once, of
    many runs, the first screen held a brand-new notebook's welcome note
    over a seed that was on disk; not reproduced since. Every later step
    then failed for that one reason, ten failures for a single cause.
    [clear] False keeps what the part put inside before calling it."""
    if clear:
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
    # By its value within its words: a swatch says its place and its value
    # ("Colour 5 of 12, #43aa8b") since the final audit, not the hex alone.
    tap(", #43aa8b", wait=1.2)
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


def trash_select(folder):
    """The Trash page: what its numbers set, said above them; two entries
    alike in id and moment, which closed the page; Select all and Unselect
    all; and one kept while the rest go for good (the owner, 2026-10-26)."""
    print("\n-- the Trash: Select all --")
    if not seed_inside([{"id": 1, "uid": "t-living", "title": "Living", "category": "General",
                         "updated": "2026-10-01 09:00:00"}],
                       {"welcome_offered": True, "trash_retention_days": 30}, "Living"):
        return
    now = time.strftime("%Y-%m-%d %H:%M:%S")
    put_inside("trash.json", [
        {"id": 2, "uid": "t-a", "title": "Twin A", "category": "General", "deleted_at": now},
        {"id": 2, "uid": "t-b", "title": "Twin B", "category": "General", "deleted_at": now},
        {"id": 3, "uid": "t-c", "title": "Keep me", "category": "General", "deleted_at": now}])
    adb("shell", "am", "force-stop", PACKAGE)
    start_app()
    tap("Settings", exact=True, wait=1.2)
    tap("Trash", exact=True, wait=1.5)
    check("two entries alike in id and moment: the Trash page opens and lists both",
          foreground() == PACKAGE and find("Twin A", exact=True) is not None
          and find("Twin B", exact=True) is not None, foreground())
    shot(folder, "73-trash-select-all", "Keep deleted notes for", "Select all")
    tap("Twin A", exact=True, wait=1.0)
    check("picking one of the two picks only it",
          count("☑") == 1 and find("↩ Restore 1 selected", exact=True) is not None, str(count("☑")))
    tap("Select all", exact=True, wait=1.0)
    check("Select all picks every entry, and offers Unselect all",
          count("☑") == 3 and find("↩ Restore 3 selected", exact=True) is not None
          and find("Unselect all", exact=True) is not None)
    tap("Unselect all", exact=True, wait=1.0)
    check("Unselect all picks none, and the buttons that act on them go",
          count("☑") == 0 and find("Restore") is None and find("Delete for good", exact=True) is None
          and find("Select all", exact=True) is not None)
    # One kept, the rest gone for good.
    tap("Select all", exact=True, wait=1.0)
    tap("Keep me", exact=True, wait=1.0)
    tap("Delete for good", exact=True, wait=1.2)
    tap_last("Delete for good", wait=1.5)
    left = [e.get("title") for e in read_file("trash.json") or []]
    check("Select all, one unpicked, Delete for good: only that one is left", left == ["Keep me"], repr(left))
    back(wait=1.0)
    back(wait=1.0)
    # A window that could not be read removes nothing, so no question may
    # promise "within 30 days" -- the default it fell back to.
    if not seed_inside([
        {"id": 1, "uid": "u-1", "title": "Twice", "body": "same", "created": "2026-10-01 09:00:00",
         "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "u-2", "title": "Twice", "body": "same", "created": "2026-10-02 09:00:00",
         "updated": "2026-10-02 09:00:00"},
    ], {"welcome_offered": True, "trash_retention_days": 30, "phone_trash_window_unknown": True}, "Twice"):
        return
    tap("Settings", exact=True, wait=1.2)
    tap("Duplicates", exact=True, wait=1.5)
    tap("Tick all but first", wait=0.8)
    tap("Move ticked duplicates", wait=1.2)
    check("with the window not known, moving duplicates promises no number of days",
          not screen_says("You can restore them from there.") and find("within 30 days") is None,
          " | ".join(t for t, d, _ in nodes() if t)[:200])
    back(wait=1.0)
    back_to("Your name")
    back()


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
    # Bold text (Android 12's, Samsung's "Bold font"): missing from the
    # changes the app takes itself until the final audit, it closed the note.
    adb("shell", "settings", "put", "secure", "font_weight_adjustment", "300")
    time.sleep(2.0)
    check("bold letters leave the note open", still_in_note())
    adb("shell", "settings", "put", "secure", "font_weight_adjustment", "0")
    time.sleep(1.0)
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


def writes_fail(on):
    """Every write of the notebook failing, as on a full disk -- or written
    again: a folder where the write puts its .tmp, which it cannot remove
    while a file is in it (the final audit's way in, 2026-10-27)."""
    tmp = "%s/notes_data.json.tmp" % NOTEBOOK
    if on:
        adb("shell", "run-as", PACKAGE, "mkdir", "-p", tmp)
        adb("shell", "run-as", PACKAGE, "touch", tmp + "/hold")
    else:
        adb("shell", "run-as", PACKAGE, "rm", "-r", tmp)


def failed_save(folder):
    """A note whose save fails is never closed from under its words. Back
    keeps it open and says why in the bar at the foot, which a screen
    reader reads out, offering to leave anyway; once the notebook can be
    written, Back saves and leaves. A new note left anyway is gone, as
    asked (ISSUES_LOG, "A way out that never asked whether the note was
    saved"). The rules are AppStateTest's, + and the rail included; this
    is the screen's half: Back reaching them."""
    print("\n-- a save that fails --")
    if not seed_inside([{"id": 1, "uid": "walk-f1", "title": "Shopping", "body": "milk",
                         "category": "General", "created": "2026-10-20 09:00",
                         "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True}, "Shopping"):
        return
    writes_fail(True)
    try:
        open_note("Shopping")
        tap("milk", exact=True, wait=0.8)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        type_text(" eggs")
        check("a save that fails is said, not only drawn", not says_soon("Not saved: "),
              str(screen_says("Not saved: ")))
        keyboard_down()
        back(wait=1.2)
        check("Back keeps the unsaved note open, and offers to leave anyway",
              not screen_says("ⓘ", "Not saved, so the note stays open", "Leave anyway", "milk eggs"),
              str(screen_says("ⓘ", "Not saved, so the note stays open", "Leave anyway", "milk eggs")))
        shot(folder, "83-not-saved-stays-open", "Leave anyway")
        writes_fail(False)
        back(wait=1.5)
        check("once it can be written, Back saves the note and leaves it", not says_soon("Search notes"))
        check_settled("... and the file holds the words typed while it could not be written",
                      lambda: (note_by_uid("walk-f1") or {}).get("body"), lambda b: b == "milk eggs")

        # A new note, left anyway: gone, as the person chose.
        writes_fail(True)
        tap("+", exact=True, wait=1.5)
        tap("Write your note")
        type_text("Call the dentist")
        time.sleep(1.5)
        keyboard_down()
        back(wait=1.2)
        check("a new note that cannot be saved stays open on Back",
              not screen_says("Call the dentist", "Leave anyway"))
        tap("Leave anyway", exact=True, wait=1.5)
        check("Leave anyway leaves it", not says_soon("Search notes"))
    finally:
        writes_fail(False)
    # Read first: a file not read passed as one that holds nothing.
    after = read_file("notes_data.json")
    check("... and nothing was written for it",
          after is not None and not any("dentist" in n.get("body", "") for n in after))


def checklist_words_kept(folder):
    """A checklist row renamed, and words in "A new item…", kept when the
    app goes to the background -- its field never left, as Home leaves it
    -- and after Android ends the process there; the keyboard's ✓ writes a
    rename too. They waited in the fields, and a swipe from Recents lost
    them (ISSUES_LOG, "Words a field kept until it was left"). The rules
    are AppStateTest's; this is the screen's half."""
    print("\n-- the checklist's words, kept without leaving the field --")
    if not seed_inside([{"id": 1, "uid": "walk-c1", "title": "Bread", "body": "Knead",
                         "category": "General", "created": "2026-10-20 09:00",
                         "updated": "2026-10-20 09:00",
                         "checklist": [{"text": "flour", "done": False}, {"text": "yeast", "done": False}]}],
                       {"welcome_offered": True}, "Bread"):
        return
    rows = lambda: [r.get("text") for r in (note_by_uid("walk-c1") or {}).get("checklist", [])]
    open_note("Bread")
    tap("yeast", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" 7g")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    check_settled("the keyboard's ✓ writes a renamed row", rows, lambda r: r == ["flour", "yeast 7g"])
    # Words left in A new item, Add not pressed; then a row renamed, its
    # field never left: Home, as a phone is put away.
    tap("A new item…", wait=0.8)
    type_text("salt")
    tap("flour", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" 500g")
    home()
    check_settled("Home writes the renamed row and the words waiting in A new item", rows,
                  lambda r: r == ["flour 500g", "yeast 7g", "salt"])
    ended = kill_behind()
    start_app()
    if open_note("Bread"):
        check("... and they are there after Android ended the process",
              ended and not screen_says("flour 500g", "yeast 7g", "salt"),
              "ended %s, missing %s" % (ended, screen_says("flour 500g", "yeast 7g", "salt")))
        shot(folder, "84-checklist-words-kept", "flour 500g", "salt")
    back_to("Search notes")


def backup_too_large(folder):
    """A backup of 48 KB whose notes unpack to 50 MB -- under the desktop's
    50 MB bomb floor, more than a phone app is given to read -- refused
    with a sentence, and the app goes on. The ceiling is a sixteenth of the
    heap the phone gives the app, which only the phone can say: BackupTest
    holds the rule with a heap it chooses, this holds it with the real one."""
    print("\n-- a backup too large for the phone --")
    import zipfile
    local = os.path.join(folder, "walk_too_large.zip")
    body = "é" * 24_900_000
    with zipfile.ZipFile(local, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        z.writestr("notes_data.json", '[{"id": 1, "title": "Big", "body": "%s"}]' % body)
    name = "walk_too_large.zip"
    adb("push", local, "/sdcard/Download/" + name)
    adb("shell", "pm", "clear", PACKAGE)
    start_app()
    tap("Settings", exact=True, wait=1.2)
    tap("Backup")
    tap("Import a backup", wait=3.0)
    if find(name, exact=True) is None:
        tap("Downloads", wait=2.0) if find("Downloads") else None
    if tap(name, exact=True, wait=0.5):
        # Photographed the moment it shows: the bar at the foot stays four
        # seconds, and a check that read it, then a photograph that looked
        # again, found it gone (the full walk, 2026-10-27).
        check("a backup too large for the phone is refused with a sentence",
              shot(folder, "85-backup-too-large", "more than this phone can read", patience=15.0))
        check("... and the app goes on", foreground() == PACKAGE, foreground())
    adb("shell", "rm", "-f", "/sdcard/Download/" + name)
    os.remove(local)
    back_to("Search notes")


def import_backup_file(folder, name, entries):
    """A backup .zip of [entries] (name -> text or bytes) imported through
    Settings -> Backup, as a person picks it from Downloads. True when the
    picker offered it and it was tapped."""
    import zipfile
    local = os.path.join(folder, name)
    with zipfile.ZipFile(local, "w", zipfile.ZIP_DEFLATED) as z:
        for entry, data in entries.items():
            z.writestr(entry, data)
    adb("push", local, "/sdcard/Download/" + name)
    os.remove(local)
    tap("Settings", exact=True, wait=1.2)
    tap("Backup")
    tap("Import a backup", wait=3.0)
    if find(name, exact=True) is None:
        tap("Downloads", wait=2.0) if find("Downloads") else None
    if not tap(name, exact=True, wait=3.0):
        return False
    # The import's own first question: what a backup brings, and Continue.
    tap("Continue", exact=True, wait=1.5)
    return True


def hostile_backups(folder):
    """Backups another program could write, imported: a template whose
    checklist holds NaN -- Android's reader takes it inside a list, and the
    import's copy of it stopped the app -- comes in with the number left
    out, and the app goes on."""
    print("\n-- hostile backups --")
    adb("shell", "pm", "clear", PACKAGE)
    adb("logcat", "-b", "crash", "-c")
    put_inside("notes_data.json", [])
    put_inside("settings.json", {"welcome_offered": True})
    start_app()
    name = "walk_nan_template.zip"
    picked = import_backup_file(folder, name, {
        "notes_data.json": '[{"id": 1, "uid": "nan-1", "title": "Plain note", "body": "words"}]',
        "templates.json": '[{"name": "Hostile template", "body": "b", "checklist": [{"text": "milk"}, NaN]}]',
    })
    check("the backup with NaN in a template's checklist is offered by the picker", picked)
    check_settled("... its note comes in", lambda: [n.get("title") for n in read_file("notes_data.json") or []],
                  lambda titles: titles == ["Plain note"], within=15.0)
    check("... and the app goes on, nothing in its crash log", foreground() == PACKAGE and
          PACKAGE not in adb("logcat", "-b", "crash", "-d"), foreground())
    templates = read_file("templates.json") or []
    check("... the template in, the number that is no number left out",
          [t.get("name") for t in templates] == ["Hostile template"]
          and [c.get("text") for c in templates[0].get("checklist", [])] == ["milk"], repr(templates)[:200])
    adb("shell", "rm", "-f", "/sdcard/Download/" + name)
    back_to("Search notes")
    # A desktop file named in Cyrillic, 279 bytes long: Windows takes it,
    # Android's storage does not, and the whole backup was refused for it.
    long = "20261028093000123456_" + "Договор_аренды_квартиры_" * 5 + "подписанный.pdf"
    name = "walk_long_name.zip"
    picked = import_backup_file(folder, name, {
        "notes_data.json": json.dumps([{"id": 1, "uid": "long-1", "title": "Lease",
                                        "attachments": [{"filename": "Договор.pdf", "stored_as": long}]},
                                       {"id": 2, "uid": "long-2", "title": "Second"}], ensure_ascii=False),
        "attachments/" + long: b"%PDF-1.4 walk",
    })
    check_settled("a backup with a file named longer than Android holds comes in whole",
                  lambda: sorted(n.get("title") for n in read_file("notes_data.json") or []),
                  lambda titles: picked and titles == ["Lease", "Plain note", "Second"], within=15.0)
    lease = next((n for n in read_file("notes_data.json") or [] if n.get("title") == "Lease"), {})
    stored = ((lease.get("attachments") or [{}])[0]).get("stored_as", "")
    held = adb("shell", "run-as", PACKAGE, "ls", NOTEBOOK + "/attachments")
    check("... its file kept under a name the phone holds, the note naming it",
          0 < len(stored.encode("utf-8")) <= 250 and stored.endswith(".pdf") and stored in held, repr(stored))
    adb("shell", "rm", "-f", "/sdcard/Download/" + name)
    back_to("Search notes")


def big_table(folder):
    """A note holding a table of 300 by 300 -- 90,000 cells, which a share
    code of 2.9 KB can carry -- opens at once, the table said too large to
    draw and its first lines shown, and nothing in the file changes; one at
    the line, 50 by 20, is still a grid and opens in time too. Past
    Editing.MAX_DRAWN_CELLS (1,000) a table is not drawn: EditingTest holds
    the line, this times the opening on the phone -- by the app's own frames,
    from the tap to the last one drawn. Timed by the walk's looks at the
    screen it read 9.8 s, then 5.0 s for a note drawn in half a second: a
    look waits for the app to be idle, and takes seconds of its own. The
    property the Looper reads is read as the app starts, so the app is
    started inside ([slow_dispatches_logged]). The walk's own reads of the
    frames are messages on that thread too, left out ([held_after_tap])."""
    print("\n-- a table too large to draw --")
    with slow_dispatches_logged():
        _tables_opened(folder)


def _tables_opened(folder):
    """[big_table]'s steps."""

    def drawn_in(label, shows, exact=False):
        """From the tap on [label] to the end of the last work the app's
        thread did for it ([held_after_tap]) -- the screen read for [shows]
        only then: no look at the screen falls inside the time measured.
        Read once the screen showed the words, an opening ended where the
        walk's own look had the app draw again: read 1.5 s after the tap, an
        opening frame still drawing was in no list (the final audit's ninth
        round); read after the look, a 0.5 s opening could fail by a redraw
        started 5.6 ms late, and 2.4 s of work drawing nothing passed by a
        look made sooner (the tenth)."""
        deadline = time.time() + 20.0
        at = find(label, exact)
        while at is None and time.time() < deadline:
            time.sleep(0.4)
            at = find(label, exact)
        if at is None:
            fail("tap %r: not on the screen" % label)
            return None
        time.sleep(1.0)         # the look's own redraws over before the time starts
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        adb("logcat", "-c")
        pid = adb("shell", "pidof", PACKAGE).strip()
        adb("shell", "input", "tap", str(at[0]), str(at[1]))
        spans = held_after_tap(pid, within=20.0)
        missing = says_soon(shows, within=20.0)
        if missing or not spans:
            print("     %r: missing %s, %d spans" % (label, missing, len(spans)))
            return None
        took = max(end for _, end in spans) - min(start for start, _ in spans)
        print("     %r opened in %.2f s: %d frames and slow messages on its thread" % (label, took, len(spans)))
        return took

    def grid(rows, columns):
        return "\n".join("| " + " | ".join("---" if r == 1 else "r%dc%d" % (r, c) for c in range(columns)) + " |"
                         for r in range(rows))
    big = "Opening words\n" + grid(300, 300)
    at_line = grid(50, 20)
    # 1,000 cells as written, 125,500 as drawn: every row padded to the
    # widest. A share code of 174 characters carries it.
    ragged = "\n".join(["|" + "x|" * 500] + ["| a | b |"] * 250)
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("templates.json", [{"name": "Big template", "title": "t", "body": big, "comments": "",
                                   "category": "General", "checklist": []}])
    if not seed_inside([{"id": 1, "uid": "walk-t1", "title": "Big table", "body": big,
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"},
                        {"id": 2, "uid": "walk-t2", "title": "Table at the line", "body": at_line,
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"},
                        {"id": 3, "uid": "walk-t3", "title": "Ragged table", "body": "Ragged\n" + ragged,
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True}, "Big table", clear=False):
        return
    took = drawn_in("Big table", "too large to draw here")
    check("a note with a 300 by 300 table is drawn within two seconds (0.5 s on 2026-10-27)",
          took is not None and took < 2.0, str(took))
    check("... the table said too large to draw, its first lines shown",
          not screen_says("Opening words", "too large to draw here", "| r0c0 | r0c1 |"),
          str(screen_says("Opening words", "too large to draw here", "| r0c0 | r0c1 |")))
    shot(folder, "86-big-table-as-text", "too large to draw here")
    back_to("Search notes")
    check("... and the file holds the note as it was",
          (note_by_uid("walk-t1") or {}).get("body") == big)
    took = drawn_in("Table at the line", "r0c0")
    check("a table of 1,000 cells, at the line, is drawn within five seconds (2.2 s on 2026-10-27)",
          took is not None and took < 5.0, str(took))
    check("... and is still a grid", find("r0c0", exact=True) is not None)
    back_to("Search notes")
    took = drawn_in("Ragged table", "Ragged")
    check("a ragged table drawn as 125,500 cells is weighed so, and drawn within two seconds",
          took is not None and took < 2.0 and not screen_says("too large to draw here"), str(took))
    back_to("Search notes")

    # The page first, so the frames timed are the template's alone.
    tap("Settings", exact=True, wait=1.5)
    tap("Templates", exact=True, wait=1.5)
    took = drawn_in("Big template", "too large to draw here", exact=True)
    check("a template holding the 300 by 300 table opens within two seconds, the table said too large",
          took is not None and took < 2.0 and not screen_says("too large to draw here"), str(took))
    shot(folder, "86b-big-table-in-template", "too large to draw here")
    back_to("Search notes")


def code_started_once(folder):
    """The app started by a share code -- cold, as "Share -> Note
    Organizer" from a chat starts it -- offers the code once. Ended by
    Android in the background and opened again, the activity is handed the
    same intent: it offered the code again, its "Update this note" ready to
    put the older words back over the newer. MainActivity's own; no unit
    test reaches an Activity's intent."""
    print("\n-- a share code that started the app, offered once --")
    code = desktop_code({"uid": "walk-once", "title": "Shared once", "body": "from a chat",
                         "category": "General"})
    if not code:
        fail("a code that started the app: the desktop's encoder could not be used, so nothing was tried")
        return
    if not seed_inside([{"id": 1, "uid": "walk-o1", "title": "Already here", "body": "x",
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True}, "Already here"):
        return
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "text/plain",
        "--es", "android.intent.extra.TEXT", code, "-n", PACKAGE + "/.MainActivity")
    time.sleep(3.0)
    tap("Import", exact=True, wait=2.0)
    check("a code that started the app is imported", note_by_uid("walk-once") is not None)
    home()
    ended = kill_behind()
    start_app()
    check("ended in the background and opened again, the app does not offer the code again",
          ended and bool(screen_says("Import a code")) and not screen_says("Already here", "Shared once"),
          "ended %s: " % ended + " | ".join(t for t, d, _ in nodes() if t)[:160])
    shot(folder, "87-code-offered-once", "Already here", "Shared once")
    back_to("Search notes")


def symbol_labels(folder):
    """The buttons drawn as a symbol say what they do to a screen reader --
    "+" is New note, "⋮" More options, "🗑" Move to Trash, a checklist row's
    "✕" Remove ... from the checklist -- where it read the symbol's name or
    nothing. Read from the UI tree's descriptions, which is what TalkBack
    reads; how TalkBack speaks them needs a person with it on."""
    print("\n-- what a screen reader is told about the symbol buttons --")
    if not seed_inside([{"id": 1, "uid": "walk-l1", "title": "Bread", "body": "Knead",
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00",
                         "checklist": [{"text": "flour", "done": False}]}],
                       {"welcome_offered": True}, "Bread"):
        return
    missing = screen_says("New note", "More options")
    check("the list's + and ⋮ are named", not missing, str(missing))
    if long_press("Bread"):
        missing = screen_says("Clear selection", "Pin", "Move to category", "Move to Trash", "More options")
        check("the selection bar's symbols are named", not missing, str(missing))
        # By its own ✕: the search box stays on screen while selecting, so
        # back_to("Search notes") stopped at once, and the next tap on the
        # card selected it instead of opening it (the full walk, 2026-10-27).
        tap("Clear selection", wait=1.0)
        check("... and the selection is cleared by its ✕", find("Clear selection") is None)
    if open_note("Bread"):
        missing = screen_says("Back", "Note details", "More options", "Checklist: flour",
                              "Remove flour from the checklist")
        check("the editor's symbols and a checklist row's box and ✕ are named", not missing, str(missing))
        # The only row has nowhere to go: no empty menu, a word instead --
        # the empty popup was a dead end for a screen reader.
        tap("Drag to move flour", exact=True, wait=0.3)
        check("the only row's move handle opens no empty menu, and says why",
              not says_soon("Nothing to move it past here.", within=4.0)
              and find("Move up", exact=True) is None and find("Move down", exact=True) is None)
    back_to("Search notes")


def google_backup(folder):
    """Settings -> Backup's switch for Android's own backup: on by default,
    written to the file when turned off, and then Android's backup takes
    only a mark saying so, none of the notes -- read from what the emulator's local transport is
    handed, with the app's switch on and off (NotesBackupAgent). Points the
    emulator's backup at its local transport; a phone never runs this."""
    print("\n-- Android's backup, and the app's switch for it --")
    if not seed_inside([{"id": 1, "uid": "walk-g1", "title": "Backed up", "body": "a line", "category": "General",
                         "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True}, "Backed up"):
        return
    adb("shell", "bmgr", "enable", "true")
    adb("shell", "bmgr", "transport", "com.android.localtransport/.LocalTransport")

    def backed_up():
        # Home first: a stopped app is not backed up at all, whatever it says.
        home()
        out = adb("shell", "bmgr", "backupnow", PACKAGE)
        start_app()
        return "%s with result: Success" % PACKAGE in out, out

    ok, out = backed_up()
    check("with the switch on by default, Android's backup takes the app", ok, out[-200:])
    tap("Settings", exact=True, wait=1.2)
    tap("Backup", exact=True, wait=1.2)
    adb("shell", "input", "swipe", "540", "1900", "540", "700", "300")
    time.sleep(1.0)
    check("the page says the switch is on", not screen_says("Android's backup to your Google account",
                                                             "On: Android may copy", "25 MB"))
    tap("Android's backup to your Google account", wait=1.2)
    check_settled("turned off, the file says so", lambda: (read_file("settings.json") or {}).get("phone_google_backup"),
                  lambda value: value is False)
    shot(folder, "88-google-backup-off", "Off: Android's next backup")
    # Handed nothing, Android kept the copy made while on, and a restore
    # put it back -- notes and all, switch on. Off, the backup now holds
    # only the mark saying so; restored, it brings back the answer alone.
    ok, out = backed_up()
    check("... and Android's backup then takes only the mark that it is off", ok, out[-200:])
    sets = adb("shell", "bmgr", "list", "sets")
    token = next((line.split(":")[0].strip() for line in sets.splitlines() if ":" in line), None)
    # Emptied first, as a reinstall or a new phone is: a restore lays the
    # backup's files over whatever is there and removes nothing.
    adb("shell", "pm", "clear", PACKAGE)
    restored = adb("shell", "bmgr", "restore", token or "1", PACKAGE) if token else ""
    time.sleep(3.0)
    held = adb("shell", "run-as", PACKAGE, "ls", NOTEBOOK)
    check("a restore of it brings back no note, only the mark",
          "phone_google_backup_off.json" in held and "notes_data.json" not in held,
          "sets: %r, restore: %r, notebook: %r" % (sets[-120:], restored[-200:], held[-200:]))
    # Android's backup can run on the restored app before it is first opened
    # -- a new phone charging overnight. Read from settings.json alone, which
    # a restore does not bring, that was "on": the mark, the only record of
    # the person's off, was deleted and nothing kept, and the app came up on.
    early = adb("shell", "bmgr", "backupnow", PACKAGE)
    ran = "%s with result: Success" % PACKAGE in early
    held = adb("shell", "run-as", PACKAGE, "ls", NOTEBOOK)
    check("a backup of the restored app before its first start keeps the mark, and takes nothing else",
          ran and "phone_google_backup_off.json" in held and "notes_data.json" not in held,
          "backup ran: %s (%r), notebook: %r" % (ran, early[-160:], held[-200:]))
    adb("shell", "pm", "clear", PACKAGE)
    if token:
        adb("shell", "bmgr", "restore", token, PACKAGE)
    time.sleep(3.0)
    held = adb("shell", "run-as", PACKAGE, "ls", NOTEBOOK)
    check("... and restored from that backup, the mark comes back", "phone_google_backup_off.json" in held,
          "notebook: %r" % held[-200:])
    start_app()
    check_settled("... and the app restored starts with the switch off",
                  lambda: (read_file("settings.json") or {}).get("phone_google_backup"), lambda value: value is False)
    restored = read_file("notes_data.json")
    check("... without the note the earlier backup held",
          restored is not None and not any(n.get("title") == "Backed up" for n in restored), repr(restored)[:200])
    put_inside("settings.json", {"welcome_offered": True, "phone_google_backup": True})
    ok, out = backed_up()
    check("turned on again, it takes the app again", ok, out[-200:])
    back_to("Search notes")


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
    # The keyboard up is the premise: on any screen without it, the button
    # missing passed.
    check("the panel stands aside while the keyboard is up for the note",
          keyboard_shown() and bool(screen_says(insert)) and not screen_says("Fuel and tolls:"))
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
        if normalize is None:
            # Skipped with a print, this passed unread, while its siblings fail().
            fail("the desktop's normalize_template could be read, to check the file's shape")
        else:
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
    # Words typed before a styled one: the bold moves with "call". Kept at
    # its offsets, it landed on other words, and every note made from the
    # template began with it there.
    tap("call mum", wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    time.sleep(0.4)
    type_text("Hi ")
    keyboard_down()
    tap("A new item", wait=0.8)
    # A capital already, as in a new note's body: the field capitalises.
    type_text("Socks")
    tap("+ Add", exact=True, wait=0.8)
    tap("Save", exact=True, wait=1.5)
    saved = on_disk()
    check("the edit is saved, in its place",
          len(saved) == 1 and [c.get("text") for c in saved[0].get("checklist", [])] == ["groceries", "Socks"],
          repr(saved))
    if saved:
        t = saved[0]
        moved = [t.get("body", "")[s.get("start", 0):s.get("end", 0)] for s in t.get("formatting", [])]
        check("words typed before a bold word leave the bold on it, and the bullet on its line",
              t.get("body", "").startswith("Hi • call mum") and moved == ["call"]
              and t.get("paragraphs") == [{"line": 0, "kind": "bullet"}], repr(t)[:300])
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
        # Two lines: a size larger than the body must not draw them over each
        # other (a size is points against a 12 pt body since round 6).
        {"id": 1, "uid": "walk-faces", "title": "Faces", "body": "plain words to style\nand a second line under them",
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
    tap("Colour", exact=True, wait=0.8)
    tap("Highlight colour 1", wait=1.2)
    choose_cell("Cells", "45")
    tap("Merge", exact=True, wait=0.8)
    tap("With the cell on the left", exact=True, wait=1.2)
    choose_cell("Cells", "fuel")
    tap("More", exact=True, wait=1.2)
    tap("Centre", exact=True, wait=1.2)
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


def move_day_keeps_new_item(folder):
    """Words typed in "A new item…" on a day of a series, then "Move this
    day…": refused onto a date the series has, and moved. The words stay in
    the box both times, made no row: moved, the editor built anew lost them
    (round 8); refused, they were finished as a row (round 10)."""
    from datetime import date, timedelta
    print("\n-- a day moved keeps the words waiting in A new item --")
    today = date.today()
    first = date(today.year, today.month, 1)
    monday = first + timedelta(days=(7 - first.weekday()) % 7)
    second, third = monday + timedelta(days=7), monday + timedelta(days=14)
    if not seed_inside([
        {"id": 1, "uid": "mv-items", "title": "Team meeting", "body": "agenda", "category": "Work",
         "date": monday.isoformat(), "recurrence": {"freq": "weekly", "weekdays": [0]},
         "checklist": [{"text": "minutes", "done": False}],
         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
    ], {"welcome_offered": True}, "Team meeting"):
        return
    rows = lambda: [r.get("text") for r in (note_by_uid("mv-items") or {}).get("checklist", [])]
    tap("Calendar", exact=True, wait=1.5)
    tap(said(second), wait=1.0)
    open_note("Team meeting")
    if not seek(lambda: find("A new item…") is not None):
        fail("A new item… not on the screen")
        return
    tap("A new item…", wait=0.8)
    type_text("Book the room")
    keyboard_down()
    adb("shell", "input", "swipe", "900", "700", "900", "1900", "300")   # back up to the banner
    time.sleep(1.0)
    for target, what in ((third, "refused"), (second + timedelta(days=2), "moved")):
        tap("Move this day", wait=1.5)
        tap(picker_day(target), wait=0.6)
        tap("OK", exact=True, wait=2.0)
        seek(lambda: find("Book the room") is not None)
        check("a day %s keeps the words in A new item, made no row" % what,
              rows() == ["minutes"] and find("Book the room") is not None, json.dumps(rows()))
        adb("shell", "input", "swipe", "900", "700", "900", "1900", "300")
        time.sleep(1.0)
    # The editor's own Reset, answered Cancel: its tap saved the box's words
    # as a row first (round 10).
    tap("Reset this day", wait=1.5)
    tap("Cancel", exact=True, wait=1.5)
    seek(lambda: find("Book the room") is not None)
    check("Reset this day answered Cancel keeps the words in A new item, made no row",
          rows() == ["minutes"] and find("Book the room") is not None, json.dumps(rows()))
    # ...and "Whole series", the strip's third link, which saved them as a
    # row before the editor built anew could carry them (round 11).
    adb("shell", "input", "swipe", "900", "700", "900", "1900", "300")
    time.sleep(1.0)
    tap("Whole series", wait=1.5)
    seek(lambda: find("Book the room") is not None)
    check("Whole series keeps the words in A new item, made no row",
          rows() == ["minutes"] and find("Book the room") is not None, json.dumps(rows()))
    # A sum typed in the calculator on a day, not put in yet, then "Whole
    # series": built anew, the editor shut the calculator and the sum went
    # with it (round 15).
    back_to("Search notes")
    tap("Calendar", exact=True, wait=1.5)
    tap(said(third), wait=1.0)
    open_note("Team meeting")
    tap("⋮", exact=True)
    tap("🧮 Calculator", wait=1.2)
    for key in ("4", "5", "7"):
        tap(key, exact=True, wait=0.3)
    adb("shell", "input", "swipe", "900", "500", "900", "1200", "300")    # back up to the banner
    time.sleep(1.0)
    tap("Whole series", wait=1.5)
    check("Whole series keeps the calculator open with the sum typed in it",
          find("⬇ Insert into note") is not None and find("457", exact=True) is not None)
    back_to("Search notes")


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
    # The second card dragged up over the first, at the list's top, and held:
    # the trade took the list along by a card, and the card under the finger
    # was drawn above the screen until let go (round 16).
    handle, first = find("Drag to move Note 02"), find("Note 01", exact=True)
    if not handle or not first:
        fail("the long list's first two cards not on the screen")
        return
    adb("shell", "input", "motionevent", "DOWN", str(handle[0]), str(handle[1]))
    for y in range(handle[1], first[1] - 60, -40):
        adb("shell", "input", "motionevent", "MOVE", str(handle[0]), str(y))
    time.sleep(1.5)
    unseen = screen_says("Note 02")                  # the words missing, while held
    adb("shell", "input", "motionevent", "UP", str(handle[0]), str(first[1] - 60))
    check_settled("the second card dragged over the first, still held, stays on screen, and lands first",
                  placed, lambda p: not unseen and p[:2] == ["Note 02", "Note 01"], first=0)
    # The same card dragged again, one place down: its handle's gesture read
    # the list as it was at its first touch, redrew that order and wrote a
    # place counted from it (round 17).
    mine, theirs = find("Drag to move Note 02"), find("Drag to move Note 01")
    if not mine or not theirs:
        fail("the first two cards' handles not on the screen after the drag")
        return
    adb("shell", "input", "motionevent", "DOWN", str(mine[0]), str(mine[1]))
    for y in range(mine[1], theirs[1] + 40, 40):
        adb("shell", "input", "motionevent", "MOVE", str(mine[0]), str(y))
    time.sleep(1.0)
    adb("shell", "input", "motionevent", "UP", str(mine[0]), str(theirs[1] + 40))
    check_settled("the same card dragged a second time, one place down, lands one place down",
                  placed, lambda p: p[:3] == ["Note 01", "Note 02", "Note 03"], first=0)
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
    # The same carry by a finger that never stays quite still: a trade the edge
    # scroll made lost its hold to the next move's measure, the list followed
    # its top card, and the carried card went out of sight with its drag --
    # the note left where it was (round 23).
    if not seed_inside(many, {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
        return
    for _ in range(4):
        adb("shell", "input", "swipe", "540", "1700", "540", "500", "200")
        time.sleep(0.6)
    handle, top = find("Drag to move Note 30"), find("Sort: Custom order")
    if not handle or not top:
        fail("the long list again: the last card or the list's top not on the screen")
        return
    adb("shell", "input", "motionevent", "DOWN", str(handle[0]), str(handle[1]))
    for y in range(handle[1], top[1] + 140, -60):
        adb("shell", "input", "motionevent", "MOVE", str(handle[0]), str(y))
    for i in range(40):                              # held, trembling by a pixel
        adb("shell", "input", "motionevent", "MOVE", str(handle[0]), str(top[1] + 140 + i % 2))
    adb("shell", "input", "motionevent", "UP", str(handle[0]), str(top[1] + 140))
    # Measured: the carry lands about place 8; the lost hold stopped it at 22.
    order = settle(placed, lambda o: "Note 30" in o and o.index("Note 30") <= 14)
    print("     (Note 30 landed at place %s)" % (order.index("Note 30") + 1 if "Note 30" in order else "-"))
    check("on a long list a card held at the edge by a trembling finger rides the scroll up, and lands there",
          "Note 30" in order and order.index("Note 30") <= 14
          and sorted(order) == ["Note %02d" % i for i in range(1, 31)], repr(order[:24]))

    # Cards of mixed heights: a short card held past the list's foot, the
    # list scrolling under it. A taller card coming in at the edge took the
    # short one's slot out of the viewport, and the drag went (round 17).
    mixed = [dict(n, body="A first line of words\nand a second line" if n["id"] % 2 == 0 else "")
             for n in many]
    if not seed_inside(mixed, {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
        return
    handle, tabs = find("Drag to move Note 03"), find("Calendar", exact=True)
    if not handle or not tabs:
        fail("the mixed list: Note 03's handle or the tabs not on the screen")
        return
    adb("shell", "input", "motionevent", "DOWN", str(handle[0]), str(handle[1]))
    for y in range(handle[1], tabs[1], 60):
        adb("shell", "input", "motionevent", "MOVE", str(handle[0]), str(y))
    time.sleep(3)                                    # held past the foot: the list scrolls
    adb("shell", "input", "motionevent", "UP", str(handle[0]), str(tabs[1]))
    order = settle(placed, lambda o: "Note 03" in o and o.index("Note 03") > 4)
    check("a short card held past the list's foot rides on past taller cards, and lands down the list",
          "Note 03" in order and order.index("Note 03") > 4
          and sorted(order) == ["Note %02d" % i for i in range(1, 31)], repr(order[:12]))


# gfxinfo's flag on a frame the render thread drew alone (FrameInfoFlags::RTAnimation).
RENDER_THREAD_ANIMATION = 2

# A message on the app's thread that runs this long, in ms, is logged by
# Android's Looper ("Slow dispatch took N ms main"), once the app is started
# with the property set ([slow_dispatches_logged]).
SLOW_DISPATCH_MS = 20

# The message `dumpsys gfxinfo` sends the app's thread -- the walk's own read
# of the frames (ActivityThread.H.DUMP_GFXINFO), logged as "h=android.app.
# ActivityThread$H ... m=165" when it runs that long.
WALK_READ_MESSAGE = re.compile(r"h=android\.app\.ActivityThread\$H\b.*\bm=165\b")


def frames_since_reset(starts=False):
    """The app's frames since `gfxinfo reset`, each from its intended start
    to its end, in seconds -- what held the screen, as the app drew it,
    which the walk's own looks (a second or more each) cannot time. With
    [starts], each as (its start, in seconds on the device's clock, its
    length). Only frames that have ENDED are listed: read this after
    [frames_once_shown]'s look at the screen, never after a fixed pause.

    A frame the render thread drew alone, for an animation of its own
    (gfxinfo's flag 2), is not listed: no code of the app ran in it. The
    ripple of a row tapped to open a dialog went on so for seven seconds
    under it, and a template opening in half a second read as 3.2 (the walk
    after the final audit's ninth round). Work that draws nothing is in no
    list; [held_after_tap] reads it from the log."""
    out = adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    # One block per window -- a dialog is a window of its own -- each between
    # two "---PROFILEDATA---" lines, with its own header.
    frames = []
    for block in out.split("---PROFILEDATA---")[1::2]:
        lines = [line for line in block.splitlines() if line.strip()]
        if len(lines) < 2:
            continue
        at = {h: k for k, h in enumerate(lines[0].split(","))}
        for line in lines[1:]:
            r = line.split(",")
            if line[0].isdigit() and len(r) > max(at["FrameCompleted"], at["IntendedVsync"]) \
                    and not int(r[at["Flags"]]) & RENDER_THREAD_ANIMATION:
                began = int(r[at["IntendedVsync"]])
                took = (int(r[at["FrameCompleted"]]) - began) / 1e9
                frames.append((began / 1e9, took) if starts else took)
    return frames


@contextlib.contextmanager
def slow_dispatches_logged():
    """Android's Looper logs each message on the app's main thread that runs
    [SLOW_DISPATCH_MS] or more -- with the property set before the app
    starts, which reads it as its thread begins: start the app inside."""
    uid = re.search(r"uid:(\d+)", adb("shell", "pm", "list", "packages", "-U", PACKAGE))
    prop = "log.looper.%s.main.slow" % (uid.group(1) if uid else "0")
    adb("shell", "setprop", prop, str(SLOW_DISPATCH_MS))
    try:
        yield
    finally:
        adb("shell", "setprop", prop, "0")


def held_after_tap(pid, quiet=1.0, within=20.0):
    """What the app's thread did after a tap, each as (start, end) in
    seconds on the device's monotonic clock: every frame it drew (the render
    thread's own left out, [frames_since_reset]) and every message on it the
    Looper logged as slow ([slow_dispatches_logged]) -- work that draws
    nothing, which no frame lists. Read again, with no look at the screen,
    until neither has grown for [quiet] seconds, or [within] are up; the
    opening is what came before the first [quiet] seconds of nothing, on the
    device's clock.

    Read once the screen showed the words, the end of an opening was where
    the walk's own look had the app draw again: a trivial redraw started
    5.6 ms late was kept as work, and a mutant holding the thread 2.4 s,
    drawing nothing, read 2.04 to 2.10 s -- killed by 2 to 5% over the line,
    and let through by a look made sooner (the final audit's tenth round).
    And each read here is a message on the app's thread ([WALK_READ_MESSAGE]):
    with a template of 90,000 cells open it ran 40 ms, logged as slow, so
    every read was work found and the reading never went quiet -- an opening
    of 0.6 s, read with no read during it, timed 4.5 to 8.3 s, a lone frame
    seconds later taken in at its end (the full walk after the tenth)."""
    deadline = time.time() + within
    seen, still_since, spans = None, time.time(), []
    while True:
        frames = [(began, began + took) for began, took in frames_since_reset(starts=True)]
        slow = []
        for line in adb("logcat", "-d", "-v", "monotonic", "--pid=" + pid, "-s", "Looper").splitlines():
            m = re.match(r"\s*(\d+\.\d+)\s.*Slow dispatch took (\d+)ms main", line)
            if m and not WALK_READ_MESSAGE.search(line):
                slow.append((float(m.group(1)) - int(m.group(2)) / 1000.0, float(m.group(1))))
        spans = frames + slow
        # By the newest end too: gfxinfo keeps a window's last 120 frames,
        # and past them a new frame leaves the count as it was.
        now = (len(frames), len(slow), max((end for _, end in spans), default=0.0))
        if now != seen:
            seen, still_since = now, time.time()
        elif spans and time.time() - still_since >= quiet:
            break
        if time.time() > deadline:
            break
        time.sleep(0.25)
    opening, end = [], None
    for start, stop in sorted(spans):
        if end is not None and start - end >= quiet:
            break
        opening.append((start, stop))
        end = stop if end is None else max(end, stop)
    return opening


def frames_once_shown(*words, within=30.0, gap=None, starts=False):
    """The frames of an act since `gfxinfo reset`, read once the screen
    shows [words] -- the act's own result. A look at the screen waits for
    the app's thread, so the frame that drew them has ended: read after a
    fixed pause, a frame longer than the pause was in no list yet, and the
    longest frame taken was a ripple's or the cursor's -- "Read all" with
    round seven's slow search put back passed (the final audit's eighth
    round). A look waits only as long as uiautomator does: one that read no
    tree waited for nothing, and the frames are [99.0] -- read anyway, a
    freeze past its patience passed (the ninth). With [gap], only the frames
    that began within [gap] seconds of the first: an autosave due 800 ms
    after a letter is a frame of its own. With [starts], each as (its start,
    its length). The words missing, and the frames ([99.0] when none were
    listed)."""
    looked = unreadable
    missing = says_soon(*words, within=within)
    time.sleep(0.4)
    timed = frames_since_reset(starts=True)
    if not timed or unreadable != looked:
        return missing, [(0.0, 99.0)] if starts else [99.0]
    first = min(began for began, _ in timed)
    kept = [(began, took) for began, took in timed if gap is None or began - first <= gap]
    return missing, kept if starts else [took for _, took in kept]


def reminder_new_zone(folder):
    """The day's reminder, set for 09:00, after the phone moves to another
    time zone with the app closed: set again for 09:00 there, where it kept
    the old instant and rang at 03:00 New York time. Read from the alarm
    Android holds (dumpsys, which prints it in the zone in force). The
    emulator's zone is put back after."""
    print("\n-- the reminder across a time zone --")

    def alarm_at():
        out = adb("shell", "dumpsys", "alarm")
        for m in re.finditer(r"ro\.noteorganizer\.app", out):
            chunk = out[max(0, m.start() - 300):m.start() + 300]
            when = re.search(r"origWhen=(\S+ \S+)", chunk)
            if when and "RTC_WAKEUP" in chunk:
                return when.group(1)
        return None

    was = adb("shell", "getprop", "persist.sys.timezone").strip() or "Europe/Bucharest"
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    if not seed_inside([{"id": 1, "uid": "tz-1", "title": "Today", "category": "General",
                         "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True, "phone_reminder_notify": True, "phone_reminder_hour": 9}, "Today"):
        return
    home()
    before = alarm_at()
    try:
        adb("shell", "service", "call", "alarm", "3", "s16", "America/New_York")
        time.sleep(4.0)
        after = alarm_at()
        check("set for 09:00, the reminder is at 09:00 in the new zone too",
              bool(before) and bool(after) and before.endswith("09:00:00.000") and after.endswith("09:00:00.000"),
              "before %s, after %s" % (before, after))
    finally:
        adb("shell", "service", "call", "alarm", "3", "s16", was)
        time.sleep(2.0)
    start_app()
    back_to("Search notes")


def day_turns_open(folder):
    """The app left open across midnight, never left: the day turns on the
    screen too. Read once, today stayed on the day the app was opened --
    "Calendar: soonest" kept yesterday's note first and the new day's bar
    never came, until the person left and returned. The emulator's clock is
    set to half a minute before midnight, and put back after by the
    computer's own clock."""
    print("\n-- the day turning with the app open --")
    import datetime
    offset = int(adb("shell", "date", "+%s").strip() or 0) - time.time()
    auto_was = adb("shell", "settings", "get", "global", "auto_time").strip()
    day = datetime.date.fromisoformat(adb("shell", "date", "+%Y-%m-%d").strip())
    nxt = day + datetime.timedelta(days=1)
    stamp = "%s 09:00" % (day - datetime.timedelta(days=7)).isoformat()
    notes = [{"id": 1, "uid": "turn-1", "title": "Due today", "category": "General", "date": day.isoformat(),
              "created": stamp, "updated": stamp},
             {"id": 2, "uid": "turn-2", "title": "Due tomorrow", "category": "General", "date": nxt.isoformat(),
              "created": stamp, "updated": stamp}]

    def order():
        ys = {t: c[1] for t, d, c in nodes() if t in ("Due today", "Due tomorrow")}
        return sorted(ys, key=ys.get) if len(ys) == 2 else list(ys)

    try:
        adb("shell", "settings", "put", "global", "auto_time", "0")
        adb("shell", "su", "0", "date", day.strftime("%m%d2359%Y.10"))
        if not seed_inside(notes, {"welcome_offered": True, "note_sort": "Calendar: soonest"}, "Due tomorrow"):
            return
        check("before midnight, today's note comes first", order() == ["Due today", "Due tomorrow"], repr(order()))
        # Today's bar, closed: the one that comes after midnight is the new day's.
        check("today's bar, closed", tap("Close the reminder") and find("You have 1 note") is None)
        while adb("shell", "date", "+%Y-%m-%d").strip() == day.isoformat():
            time.sleep(1.0)
        # Looked at once a minute while in front, at midnight itself here.
        check_settled("past midnight, without leaving, the new day's note comes first",
                      order, lambda o: o == ["Due tomorrow", "Due today"], within=70.0, first=2.0)
        check("and the new day's bar comes", not says_soon("You have 1 note for today", within=10.0))
        shot(folder, "91-day-turned-open", "Due tomorrow")
    finally:
        adb("shell", "su", "0", "date", "@%d" % int(time.time() + offset))
        adb("shell", "settings", "put", "global", "auto_time", auto_was or "1")
        print("     clock back: %s" % adb("shell", "date").strip())
    back_to("Search notes")


def template_long_comments(folder):
    """A template whose comments run to 600,000 letters -- a backup can
    bring one -- opened, then the app left with Home. Grown to fit them,
    the comments field outgrew the tallest size Compose can measure and the
    app stopped as the template opened; and its words were kept for the
    system to hold while the app is away, past what one handover carries."""
    print("\n-- a template with very long comments --")
    text = ("A line of comments, words and spaces. " * 16000)[:600000]
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("notes_data.json", [{"id": 1, "uid": "long-1", "title": "Living", "body": "x", "category": "General",
                                    "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}])
    put_inside("settings.json", {"welcome_offered": True})
    put_inside("templates.json", [{"name": "Long comments", "title": "t", "body": "b", "comments": text,
                                   "category": "General", "checklist": []}])
    adb("logcat", "-b", "crash", "-c")
    start_app()
    says_soon("Search notes", within=10.0)
    alive = adb("shell", "pidof", PACKAGE).strip()
    tap("Settings", exact=True, wait=1.5)
    tap("Templates", exact=True, wait=1.5)
    tap("Long comments", exact=True, wait=3.0)
    # Its words make the screen's tree slow to read: given time.
    opened = says_soon("Comments (optional)", "Save", within=15.0)
    check("the template opens, the app still running",
          not opened and adb("shell", "pidof", PACKAGE).strip() == alive, "missing %s" % opened)
    shot(folder, "92-template-long-comments", "Comments")
    # Stopped, the editor's words written: a crash in the writing ends the
    # process before Android can say so.
    home()
    # The app's own crash: the buffer holds every process's (logged).
    crash = adb("logcat", "-b", "crash", "-d").strip()
    check("left with Home, the app is still running and nothing stopped it",
          adb("shell", "pidof", PACKAGE).strip() == alive and PACKAGE not in crash, crash[:300])
    start_app()
    back_to("Search notes")


def answers_after_restart(folder):
    """Export as file and Attach files, each with the app's process ended
    behind Android's picker -- as Android ends a backgrounded app it needs
    the memory of -- before the picker answers. The app starts again on its
    list, where no editor was to take the answer: the exported file stayed
    empty, the files were not attached, and nothing was said. Then a large
    file attached and the note left at once with Back: the copy ended with
    the editor, the file was never attached, and the copy stayed behind."""
    print("\n-- answers that arrive after Android ended the app --")
    exported = r"Bread( \(\d+\))?\.html"
    clear_from_downloads(exported)

    def put_in_downloads(name, *dd):
        """A file in Downloads, and in Android's index of it, which the
        picker lists from: one pushed and not indexed was not listed, and
        files deleted since were. The newest, so the first in the list."""
        if dd:
            adb("shell", "dd", "if=/dev/zero", "of=/sdcard/Download/" + name, *dd)
        else:
            local = os.path.join(folder, name)
            with open(local, "w", encoding="utf-8") as f:
                f.write("TICKET 9")
            adb("push", local, "/sdcard/Download/" + name)
        adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
            "-d", "file:///sdcard/Download/" + name)
        time.sleep(2.0)

    def out_of_downloads(name):
        adb("shell", "rm", "-f", "/sdcard/Download/" + name)
        adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
            "-d", "file:///sdcard/Download/" + name)

    def end_the_app():
        # Once Android has stopped the app behind the picker, as Android's
        # own ending meets it: it stops one behind another app's screen when
        # that screen rests, or ten seconds later (home()), and saves its state
        # then. Ended while only paused, the app had saved nothing, and the
        # answer was lost for the walk's reason, not the app's (the final
        # audit's eighth round). And read that the process is gone.
        held = stopped()
        pid = adb("shell", "pidof", PACKAGE).strip()
        if pid:
            adb("shell", "su", "0", "kill", "-9", pid)
        deadline = time.time() + 5.0
        while adb("shell", "pidof", PACKAGE).strip() and time.time() < deadline:
            time.sleep(0.3)
        if not held:
            print("     (Android had not stopped the app behind the picker within 15 s)")
        return held and bool(pid) and not adb("shell", "pidof", PACKAGE).strip()

    try:
        if not seed_inside([{"id": 1, "uid": "ar-bread", "title": "Bread", "body": "Knead the dough",
                             "created": "2026-10-15 09:00:00", "updated": "2026-10-15 09:00:00"}],
                           {"welcome_offered": True}, "Bread"):
            return
        open_note("Bread")
        tap("⋮", exact=True)
        tap("💾 Export as file", wait=1.2)
        tap("Web page (.html)", wait=1.0)
        button = picker_save_button()
        ended = end_the_app()
        if button:
            tap(button, exact=True, wait=1.0)
        said = not says_soon("Exported to Bread.html", within=10.0)
        size = adb("shell", "stat", "-c", "%s", "/sdcard/Download/Bread.html").strip()
        check("ended behind the picker, the export still writes its file and says so",
              ended and said and size.isdigit() and int(size) > 0, "ended %s, said %s, size %r" % (ended, said, size))
        back_to("Search notes")

        put_in_downloads("walk-ticket.txt")
        open_note("Bread")
        tap("⋮", exact=True, wait=1.0)
        tap("📎 Attach files", exact=True, wait=3.0)
        ended = end_the_app()
        tap("walk-ticket.txt", exact=True, wait=1.0, patience=10.0)
        said = not says_soon("1 file attached to “Bread”", within=8.0)
        atts = settle(lambda: (note_by_uid("ar-bread") or {}).get("attachments") or [], lambda a: len(a) == 1)
        check("ended behind the picker, the file is still attached to the note, and that is said",
              ended and said and len(atts) == 1 and atts[0].get("filename") == "walk-ticket.txt",
              "said %s, %s" % (said, json.dumps(atts)))
        back_to("Search notes")

        put_in_downloads("walk-large.bin", "bs=1048576", "count=300")
        open_note("Bread")
        tap("⋮", exact=True, wait=1.0)
        tap("📎 Attach files", exact=True, wait=3.0)
        if tap("walk-large.bin", exact=True, wait=0.3, patience=10.0):
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        atts = settle(lambda: (note_by_uid("ar-bread") or {}).get("attachments") or [], lambda a: len(a) == 2,
                      within=40.0, first=3.0)
        left = adb("shell", "run-as", PACKAGE, "ls", "cache").split()
        check("left with Back while a large file copies, the file is attached all the same, no copy left over",
              len(atts) == 2 and atts[1].get("filename") == "walk-large.bin"
              and not any(n.startswith("attach_") for n in left), "%s; cache %s" % (json.dumps(atts), left))
    finally:
        clear_from_downloads(exported)
        out_of_downloads("walk-ticket.txt")
        out_of_downloads("walk-large.bin")
        start_app()
        back_to("Search notes")


def import_questions_quick(folder):
    """A backup of 1,000 notes into a notebook of 1,000 others: the plan and
    the look-alikes, which normalize every note of both, are worked out away
    from the screen. Worked out while the questions were drawn, the screen
    held still 1.3 s on the emulator; after, 0.5 s, the import itself."""
    print("\n-- an import's questions, a thousand notes each side --")
    import zipfile

    def note(i, prefix):
        body = "\n".join("%s line %d of note %d, with a few words to read" % (prefix, j, i) for j in range(12))
        return {"id": i, "uid": "%s-%d" % (prefix, i), "title": "%s %d" % (prefix, i), "body": body,
                "category": "General", "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00",
                "formatting": [{"start": 0, "end": 5, "style": "bold"}]}
    local = os.path.join(folder, "walk_thousand.zip")
    with zipfile.ZipFile(local, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("notes_data.json", json.dumps([note(i, "Theirs") for i in range(1, 1001)]))
    name = "walk_thousand.zip"
    adb("push", local, "/sdcard/Download/" + name)
    if seed_inside([note(i, "Mine") for i in range(1, 1001)], {"welcome_offered": True}, "Mine 1"):
        tap("Settings", exact=True, wait=1.2)
        tap("Backup", exact=True, wait=1.2)
        tap("Import a backup", wait=3.0)
        if find(name, exact=True) is None:
            tap("Downloads", wait=2.0) if find("Downloads") else None
        tap(name, exact=True, wait=4.0)
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        tap("Continue", exact=True, wait=0.2)
        check("the thousand notes come in", not says_soon("Added 1000 notes", within=60.0))
        longest = max(frames_since_reset() or [0.0])
        check("... and no frame holds the screen a second (1.3 s before, 2026-10-27)", longest < 1.0,
              "%.2f s" % longest)
    adb("shell", "rm", "-f", "/sdcard/Download/" + name)
    os.remove(local)
    back_to("Search notes")


def code_read_away(folder):
    """A share code of a few kilobytes that unpacks to 14 MB and 200 notes,
    shared to the app: read, planned and weighed for look-alikes away from
    the screen. Done in the tap on Import, the screen held still while a
    code that size was inflated, parsed and set against the notebook."""
    print("\n-- a large share code, read away from the screen --")
    import base64
    import zlib
    body = "A line of words, read and read again.\n" * 2400
    notes = [{"uid": "read-1", "title": "Known", "body": "changed\n" + body, "category": "General"}] + \
            [{"uid": "read-%d" % i, "title": "Read %d" % i, "body": body, "category": "General"} for i in range(2, 201)]
    payload = json.dumps({"v": 1, "notes": notes}).encode("utf-8")
    code = "NOTE1:" + base64.urlsafe_b64encode(zlib.compress(payload, 9)).decode("ascii")
    print("     %d characters of code, %d bytes unpacked" % (len(code), len(payload)))
    # A notebook of a thousand, each of the code's notes weighed against all.
    mine = [{"id": 1, "uid": "read-1", "title": "Known", "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}] + \
           [{"id": i, "uid": "mine-%d" % i, "title": "Mine %d" % i, "category": "General",
             "body": "\n".join("line %d of note %d, with a few words to read" % (j, i) for j in range(12)),
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"} for i in range(2, 1001)]
    if not seed_inside(mine, {"welcome_offered": True}, "Mine 2"):
        return
    # Too long for a Windows command line: handed over in a file, and read
    # into the intent by the emulator's own shell.
    local = os.path.join(folder, "walk_code.txt")
    with io.open(local, "w", encoding="ascii") as f:
        f.write(code)
    adb("push", local, "/data/local/tmp/walk_code.txt")
    os.remove(local)
    adb("shell", "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "
        "\"$(cat /data/local/tmp/walk_code.txt)\" -n %s/.MainActivity" % PACKAGE)
    adb("shell", "rm", "-f", "/data/local/tmp/walk_code.txt")
    says_soon("Import a code", within=10.0)
    time.sleep(2.0)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    adb("logcat", "-c")
    pid = adb("shell", "pidof", PACKAGE).strip()
    tap("Import", exact=True, wait=0.2)
    asked = not says_soon("1 note you already have", within=30.0)
    longest = max(frames_since_reset() or [0.0])
    # Work done in the tap itself falls between two frames, where the
    # frames' own timings do not look: the screen's thread held still shows
    # as Choreographer's "Skipped N frames" when it is let go.
    skipped = [int(n) for n in re.findall(r"Skipped (\d+) frames", adb("logcat", "-d", "--pid=" + pid, "-s", "Choreographer"))]
    check("the code's question comes", asked)
    check("... and the screen is never held a second while it is read",
          longest < 1.0 and max(skipped or [0]) < 60, "longest frame %.2f s, frames skipped %s" % (longest, skipped))
    tap("Cancel", exact=True, wait=1.0)
    back_to("Search notes")


def arranged_from_the_top(folder):
    """Another sort, or a first pin, with the list at its top: the list
    starts at its new top. Keyed by note, it kept the card it had in sight,
    and Title A–Z seemed to begin there, a pinned note above the screen
    (wave 2, round 8)."""
    print("\n-- a new order starts at the list's top --")
    fruit = ["Apple", "Banana", "Cherry", "Damson", "Elder", "Fig", "Grape", "Hazel",
             "Kiwi", "Lemon", "Mango", "Nectarine"]
    if not seed_inside([{"id": i + 1, "uid": "walk-o%d" % i, "title": name, "body": "x",
                         "category": "General", "created": "2026-10-%02d 09:00" % (i + 1),
                         "updated": "2026-10-%02d 09:00" % (i + 1)} for i, name in enumerate(fruit)],
                       {"welcome_offered": True}, "Nectarine"):
        return
    tap("Sort: Recently updated", wait=1.0)
    tap("Title A–Z", exact=True, wait=1.5)
    check("Title A–Z chosen at the list's top shows its first note", not says_soon("Apple"))
    if long_press("Cherry"):
        tap("📌", wait=1.5)
        check("a note pinned from the list's top is shown at its new place", not says_soon("Cherry", "Apple"))
    # The top card unpinned: the list held by place, the note still pinned in
    # sight; kept by its first card, it followed the card (round 10).
    if long_press("Banana"):
        tap("📌", wait=1.5)
    if long_press("Banana"):
        tap("📌", wait=1.5)
        check("the top card unpinned leaves the list at its top, the note still pinned in sight",
              not says_soon("Cherry", "Apple"))
    # Two notes deleted from the top and taken back: brought into sight, where
    # they came back above the screen and the Undo seemed to do nothing.
    if long_press("Cherry"):
        tap("Apple", exact=True, wait=0.8)
        tap("🗑", wait=1.0)
        undo_at = find("Undo", exact=True)
        if undo_at:
            adb("shell", "input", "tap", str(undo_at[0]), str(undo_at[1]))
            time.sleep(1.5)
        check("two notes taken back from the Trash are shown where they came back",
              bool(undo_at) and not says_soon("Cherry", "Apple"))
    back_to("Search notes")
    # Grouped by category, a pin rises only to the top of its own group: the
    # top of the list was the wrong place, screens from it (round 9).
    if not seed_inside([{"id": i + 1, "uid": "walk-g%d" % i, "title": name, "body": "x",
                         "category": "General" if i < 12 else "Work",
                         "created": "2026-10-%02d 09:00" % (i + 1), "updated": "2026-10-%02d 09:00" % (i + 1)}
                        for i, name in enumerate(fruit + ["Work plan %d" % n for n in range(1, 9)])],
                       {"welcome_offered": True, "group_by_category": True, "note_sort": "Title A–Z",
                        "categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}, "Apple"):
        return
    seek(lambda: find("Work plan 6") is not None)
    if long_press("Work plan 6"):
        tap("📌", wait=1.5)
        check("grouped, a note pinned in a later group is shown at the top of its group",
              not says_soon("Work plan 6"))
    back_to("Search notes")
    # The list's own top card among those moved, and moved down by them: the
    # list followed it, and the card now first was above the screen (round 14).
    # Filed together, the two take one stamp and fall into file order.
    plain = [{"id": i + 1, "uid": "walk-t%d" % i, "title": name, "body": "x", "category": "General",
              "created": "2026-10-%02d 09:00" % (i + 1), "updated": "2026-10-%02d 09:00" % (i + 1)}
             for i, name in enumerate(fruit)]
    colours = {"welcome_offered": True, "categories": {"General": "#7c5cff", "Work": "#4cc9f0"}}
    if not seed_inside(plain, colours, "Nectarine"):
        return
    if long_press("Nectarine"):
        tap("Mango", exact=True, wait=0.8)
        tap("Move to category", wait=1.0)
        tap("Work", exact=True, wait=1.5)
        filed = [n.get("title") for n in read_file("notes_data.json") or [] if n.get("category") == "Work"]
        check("the top two cards filed together are both shown, the one now first too",
              sorted(filed) == ["Mango", "Nectarine"] and not says_soon("Mango", "Nectarine"), repr(filed))
    back_to("Search notes")
    # Pinned together, the top card already pinned: the newer goes first.
    keys = {"id": 99, "uid": "walk-keys", "title": "Keys", "body": "x", "category": "General",
            "pinned": True, "created": "2026-09-01 09:00", "updated": "2026-09-01 09:00"}
    if not seed_inside(plain + [keys], colours, "Keys"):
        return
    if long_press("Keys"):
        tap("Nectarine", exact=True, wait=0.8)
        tap("📌", wait=1.5)
        check("the pinned top card pinned again with a newer note: the newer one, now first, is shown",
              not says_soon("Nectarine", "Keys"))
    back_to("Search notes")
    # A long list: past about a hundred rows the list does not follow its
    # first card, and keeps its place by index -- filed from the top of
    # "Oldest updated", the card went last and nothing scrolled (round 15).
    import datetime
    start = datetime.date(2026, 1, 1)
    many = [{"id": i, "uid": "walk-l%d" % i, "title": "Old %03d" % i, "body": "x", "category": "General",
             "created": "%s 09:00" % (start + datetime.timedelta(days=i)),
             "updated": "%s 09:00" % (start + datetime.timedelta(days=i))} for i in range(1, 141)]
    if not seed_inside(many, dict(colours, note_sort="Oldest updated"), "Old 001"):
        return
    if long_press("Old 001"):
        tap("Move to category", wait=1.0)
        tap("Work", exact=True, wait=2.0)
        check("on a list of 140, the top card filed (now the last) is shown where it went",
              not says_soon("Old 001"))
    back_to("Search notes")


def what_hides_the_list(folder):
    """The final audit's second wave, on the list, by the owner's choices:
    a list Show leaves empty says so and offers Show all notes; the search
    finds "Crème brûlée" by "creme" and a note by its checklist's row; a new
    note Show hides is said as it closes, with Show all; text shared to the
    app that holds no code is a new note, and a code with words around it is
    still a code."""
    print("\n-- what hides the list's notes --")
    import base64
    import zlib
    stamp = {"created": "2026-11-01 09:00", "updated": "2026-11-01 09:00"}
    older = {"created": "2026-10-01 09:00", "updated": "2026-10-01 09:00"}
    if not seed_inside([
            dict(stamp, id=1, uid="w2-1", title="Crème brûlée night", body="x", category="General"),
            dict(stamp, id=2, uid="w2-2", title="Shopping", body="", category="General",
                 checklist=[{"text": "Gruyère", "done": False}])]
            + [dict(older, id=10 + i, uid="w2-w%d" % i, title="Work item %d" % i, body="x", category="Work")
               for i in range(30)],
            {"welcome_offered": True, "note_filter": "Home",
             "categories": {"General": "#7c5cff", "Work": "#4cc9f0", "Home": "#f9c74f"}},
            "No notes match Show: Home."):
        return
    shot(folder, "130-empty-list-names-show", "No notes match Show: Home.", "Show all notes")
    tap("Show all notes", exact=True, wait=1.5)
    check("a list Show leaves empty names it, and Show all notes brings the notes back",
          not says_soon("Crème brûlée night", "Shopping", "Show: All"))

    def search(words):
        tap("Search notes", wait=0.8)
        type_text(words)
        time.sleep(1.0)
        on = [t for t in ("Crème brûlée night", "Shopping") if not screen_says(t)]
        tap("Clear search", exact=True, wait=1.0)
        keyboard_down()
        return on
    check("the search finds Crème brûlée by creme, and a note by its checklist's row",
          search("creme") == ["Crème brûlée night"] and search("gruyere") == ["Shopping"])

    # A new note filed under General while Show is at Work, the list
    # scrolled down: Show all brings the new note's card into sight, where
    # the list kept the card it had and the new note came in above it.
    tap("Show: All", wait=1.0)
    tap("Work", exact=True, wait=1.5)
    for _ in range(2):
        adb("shell", "input", "swipe", "540", "1900", "540", "700", "300")
        time.sleep(0.8)
    tap("+", exact=True, wait=1.5)
    tap("Title")
    type_text("Call the dentist")
    back_to("Search notes")
    said = not says_soon("Saved in General, hidden by Show: Work", "Show all", within=8.0)
    shot(folder, "131-new-note-hidden-said", "hidden by Show: Work")
    tap("Show all", exact=True, wait=1.5)
    check("a new note Show hides is said as it closes, and Show all puts it on the list",
          said and not says_soon("Call the dentist", "Show: All"))

    # Notes the Trash's Undo puts back where Show now hides them are said,
    # with Show all: they came back without a word (wave 2, round 11). Two,
    # as one put back opens in the editor.
    long_press("Crème brûlée night")
    tap("Shopping", exact=True, wait=0.6)
    tap("🗑", wait=0.8)
    tap("Show: All", wait=1.0)
    tap("Work", exact=True, wait=1.5)
    tap("Undo", exact=True, wait=1.0)
    said = not says_soon("Put back, 2 of them hidden by Show: Work", "Show all", within=8.0)
    tap("Show all", exact=True, wait=1.5)
    check("notes the Trash's Undo puts back where Show hides them are said, and Show all shows them",
          said and not says_soon("Crème brûlée night", "Shopping", "Show: All"))

    # Text shared to the app: a new note; a code among words: a code.
    adb("shell", "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "
        "\"Buy milk and bread\" -n %s/.MainActivity" % PACKAGE)
    made = not says_soon("Saved as a new note", within=8.0)
    back_to("Search notes")
    titles = [n.get("title") for n in read_file("notes_data.json") or []]
    check("text shared to the app that holds no code is a new note, its first line the title",
          made and "Buy milk and bread" in titles, repr(titles))
    payload = json.dumps({"v": 1, "notes": [{"title": "From Ana", "body": "hello", "category": "General"}]})
    code = "NOTE1:" + base64.urlsafe_b64encode(zlib.compress(payload.encode("utf-8"), 9)).decode("ascii")
    adb("shell", "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "
        "\"Hi! Here it is: %s Thanks\" -n %s/.MainActivity" % (code, PACKAGE))
    check("a code shared with words around it is still a code", not says_soon("Import a code", within=10.0))
    tap("Cancel", exact=True, wait=1.0)
    back_to("Search notes")


def long_lines(folder):
    """Text with a run of 150,000 letters and no space -- a share code of a
    few hundred characters brings one -- in a body, a title, comments and a
    checklist row: each shown read-only, its start and its length, and the
    note opens at once, where the body held the screen 12.6 s and the title
    the list 3.8 s; the file keeps every byte. A code of 120,000 characters
    shared to the app shows its start and its length the same way."""
    print("\n-- lines too long to lay out --")
    w = "W" * 150_000

    def note(i, title, **fields):
        n = {"id": i, "uid": "long-line-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n
    notes = [note(1, "Long body", body="Words before\n" + w + "\nwords after"),
             note(2, "T" * 150_000),
             note(3, "Long comments", comments=w),
             note(4, "Long row", checklist=[{"text": w, "done": False}]),
             # Pinned, so it stays the top card as the rest come in: the
             # list keeps the card it has in sight, and they came in above it.
             note(5, "Warm start", category="Work", pinned=True)]
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("notes_data.json", notes)
    # Started on Show: Work, the long notes off the list, and timed as Show
    # goes back to All: timed from the start, the app's own first frames
    # were counted as the title's -- 1.3 to 3.75 s on the emulator, the most
    # when this part ran first after the install, the title's fix in place.
    put_inside("settings.json", {"welcome_offered": True, "note_filter": "Work",
                                 "categories": {"General": "#7c5cff", "Work": "#4cc9f0"}})
    start_app()
    says_soon("Warm start", within=20.0)
    time.sleep(1.5)
    tap("Show: Work", wait=1.0)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tap("All", exact=True, wait=0.2)
    says_soon("Long body", within=20.0)
    longest = max(frames_since_reset() or [0.0])
    check("the list with a title of 150,000 letters is drawn within a second (3.8 s before)", longest < 1.0,
          "%.2f s" % longest)
    for title, label in (("Long body", "the body's line"), ("TTTTTTTTTT", "the title"),
                         ("Long comments", "the comments"), ("Long row", "the checklist row")):
        back_to("Search notes")
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        opened = open_note(title, limit=30.0)
        time.sleep(1.5)
        # A row shown, not written in, says how long it is and offers to be
        # read whole (since the final audit's sixth round); the rest say why
        # they are shown read-only.
        says = "Read all 150,000 characters" if title == "Long row" else "too long to show here"
        if title in ("Long comments", "Long row") and screen_says(says):
            # Folded away under its label: opened.
            tap("Comments" if title == "Long comments" else "Checklist", wait=1.5)
        # The screen read first, the frames once it says so (frames_once_shown).
        missing, frames = frames_once_shown(says, within=20.0)
        took = max(frames)
        check("%s, too long to lay out, opens within a second and says so" % label,
              opened and took < 1.0 and not missing, "%.2f s, %s" % (took, missing))
        if title == "Long body":
            shot(folder, "92-line-too-long", "too long to show here")
    back_to("Search notes")
    kept = read_file("notes_data.json") or []
    check("... and the file holds every note as it was",
          [n.get("body") for n in kept] == [n["body"] for n in notes]
          and [n.get("title") for n in kept] == [n["title"] for n in notes]
          and kept[2].get("comments") == w and kept[3].get("checklist", [{}])[0].get("text") == w)
    # A code that long, shared to the app: its start and its length.
    local = os.path.join(folder, "walk_long_code.txt")
    with io.open(local, "w", encoding="ascii") as f:
        f.write("NOTE1:" + "QUJD" * 30_000)
    adb("push", local, "/data/local/tmp/walk_long_code.txt")
    os.remove(local)
    adb("logcat", "-c")
    adb("shell", "am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "
        "\"$(cat /data/local/tmp/walk_long_code.txt)\" -n %s/.MainActivity" % PACKAGE)
    adb("shell", "rm", "-f", "/data/local/tmp/walk_long_code.txt")
    shown = not says_soon("A code of 120,006 characters", within=15.0)
    pid = adb("shell", "pidof", PACKAGE).strip()
    skipped = [int(n) for n in re.findall(r"Skipped (\d+) frames", adb("logcat", "-d", "--pid=" + pid, "-s", "Choreographer"))]
    check("a code of 120,006 characters shared to the app shows its start and its length, the screen not held",
          shown and find("Copy", exact=True) is not None and max(skipped or [0]) < 60, "skipped %s" % skipped)
    shot(folder, "93-code-too-long", "A code of")
    tap("Cancel", exact=True, wait=1.0)
    back_to("Search notes")


def limits_round3(folder):
    """The final audit's third round, walked: text the layout cannot break
    that the first rule let through -- joined by no-break spaces, a hundred
    lines just under the limit, a run inside a table's cell -- each opens at
    once, shown read-only; a template saved from a note with a 150,000-letter
    row opens at once; the bar quotes that row short when its ✕ takes it off,
    and Undo puts it back whole; Duplicate quotes a 150,000-letter title
    short; a note too long for a message says so on the Share sheet; a page
    shared of a table too large to draw is made without holding the app; and
    a PDF that stops at forty pages says so."""
    print("\n-- round three's limits: what the layout cannot break, and what is quoted --")
    w = "W" * 150_000
    heavy = "a" * 9_999

    def note(i, title, **fields):
        n = {"id": i, "uid": "r3-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n
    notes = [note(1, "NBSP body", body="Above\n" + "word\u00a0" * 30_000 + "\nBelow"),
             note(2, "Cell run", body="Words before\n| " + w + " | x |\nwords after"),
             # In the comments, a field of their own near the top: in the body
             # the two lines let in fill a screen a hundred times over, and the
             # read-only rest is out of sight. The body's rule is the same
             # function, held by EditingTest.
             note(3, "Many heavy", comments="\n".join([heavy] * 100)),
             note(4, "Row for template", checklist=[{"text": w, "done": False}, {"text": "milk", "done": False}]),
             note(5, "T" * 150_000)]
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("notes_data.json", notes)
    put_inside("settings.json", {"welcome_offered": True})
    start_app()
    says_soon("NBSP body", within=20.0)

    def opens_read_only(title, label):
        back_to("Search notes")
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        opened = open_note(title, limit=30.0)
        time.sleep(1.5)
        if screen_says("too long to show here") and screen_says("Comments") == []:
            tap("Comments", wait=1.5)       # folded away under its label
        missing, frames = frames_once_shown("too long to show here", within=20.0)
        took = max(frames)
        check("%s opens within a second, read-only, and says so" % label,
              opened and took < 1.0 and not missing, "%.2f s, %s" % (took, missing))
        return opened

    if opens_read_only("NBSP body", "a line of 150,000 letters joined by no-break spaces"):
        shot(folder, "94-nbsp-line-read-only", "too long to show here")
    opens_read_only("Cell run", "a table whose cell holds 150,000 letters")
    if opens_read_only("Many heavy", "a hundred lines of 9,999 letters"):
        shot(folder, "95-heavy-lines-read-only", "too long to show here")

    # The ✕ of a row too long to lay out: the bar quotes it short, and Undo
    # puts back every letter.
    back_to("Search notes")
    open_note("Row for template", limit=30.0)
    time.sleep(1.0)
    if screen_says("milk"):
        tap("Checklist", wait=1.5)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tapped = tap("Remove WWWWWWWWWW", wait=0.3)
    message = settle(lambda: next((t for t, d, _ in nodes() if t.startswith("Removed “")), ""), bool, within=6.0, first=0)
    took = max(frames_since_reset() or [99.0])
    check("the ✕ of a 150,000-letter row is said in a bar of a short quote, the screen not held",
          tapped and message.startswith("Removed “WWWW") and message.endswith("” from the checklist")
          and len(message) < 130 and took < 1.0, "%r (%d), %.2f s" % (message[:140], len(message), took))
    shot(folder, "96-row-removed-quoted", "Removed")
    tap_last("Undo", wait=1.5)       # the bar's, not the editor's ↶, also called Undo
    rows = settle(lambda: (note_by_uid("r3-4") or {}).get("checklist") or [], lambda r: len(r) == 2, first=0.5)
    check("... and Undo puts the row back whole", [r.get("text") for r in rows] == [w, "milk"],
          repr([len(r.get("text", "")) for r in rows]))

    # Saved as a template, the row goes whole into it; the template editor
    # opens at once and shows the row as the note does.
    tap("⋮", exact=True)
    tap("🧩 Save as template", wait=1.2)
    tap("Save", exact=True, wait=1.5)
    saved = read_file("templates.json") or []
    check("the template keeps the row whole", bool(saved) and (saved[0].get("checklist") or [{}])[0].get("text") == w)
    back_to("Search notes")
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tap("Row for template", exact=True, wait=0.3)
    # Shown by its start, read whole on asking (the final audit's sixth round).
    missing, frames = frames_once_shown("Edit template", "Read all 150,000 characters", within=20.0)
    took = max(frames)
    check("the template editor opens within a second, the long row read-only",
          took < 1.0 and not missing, "%.2f s, %s" % (took, missing))
    shot(folder, "97-template-long-row", "Read all 150,000 characters")
    back_to("Search notes", tries=6)

    # Duplicate's bar quotes a 150,000-letter title short.
    open_note("TTTTTTTTTT", limit=30.0)
    tap("⋮", exact=True)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tap("📄 Duplicate", wait=0.3)
    message = settle(lambda: next((t for t, d, _ in nodes() if t.startswith("Duplicated as")), ""), bool, within=6.0, first=0)
    took = max(frames_since_reset() or [99.0])
    check("Duplicate of a 150,000-letter title says so in a short quote, the screen not held",
          message.startswith("Duplicated as “TTTT") and len(message) < 110 and took < 1.0,
          "%r (%d), %.2f s" % (message[:120], len(message), took))
    copies = [n for n in read_file("notes_data.json") or [] if (n.get("title") or "").startswith("TTTT")]
    check("... and the copy keeps the whole title", len(copies) == 2 and all(len(n["title"]) >= 150_000 for n in copies),
          repr([len(n.get("title", "")) for n in copies]))
    back_to("Search notes")
    kept = read_file("notes_data.json") or []
    check("the file holds every note as it was",
          [n.get("body") for n in kept[:5]] == [n["body"] for n in notes]
          and [n.get("comments") for n in kept[:5]] == [n.get("comments") for n in notes])

    # What goes out: a notebook of its own, so each note is on the screen.
    shared = [note(9, "Two hundred thousand", body="Today I walked to the river and back again.\n" * 4_545),
              note(10, "Under the line", body="Today I walked to the river and back again.\n" * 2_250),
              note(6, "Journal", body="Today I walked to the river and back again.\n" * 7_000),
              note(7, "Empty cells", body="|" * 50_001),
              note(8, "Forty pages", body="\n".join(
                  "Line %d of a note that goes on far past forty pages." % i for i in range(1, 3001)))]
    if not seed_inside(shared, {"welcome_offered": True}, "Two hundred thousand"):
        return

    def focused():
        """The package of the window with the focus: the share sheet is a
        window over the app (the sharing part's own reading)."""
        out = adb("shell", "dumpsys", "window")
        match = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([\w.]+)/", out)
        return match.group(1) if match else foreground()

    def handed(seconds):
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

    # Words too long for a message: the Share sheet says so before the tap.
    open_note("Journal", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    check("a note of 308,000 characters offers no words alone, and says why",
          find("Too long for a message") is not None)
    shot(folder, "98-share-too-long-for-words", "Too long for a message")
    tap("Text", exact=True, wait=1.2)
    check("... and Text does nothing", find("Share this note as") is not None and focused() == PACKAGE, focused())
    back()
    back_to("Search notes")
    # The line where Android delivers the share sheet: 200,000 characters
    # never reached it, unsaid; refused here, with the reason. And words
    # under the line still go.
    open_note("Two hundred thousand", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    check("200,000 characters, past what Android delivers to the share sheet, are refused with the reason",
          find("Too long for a message") is not None, " | ".join(t for t, d, _ in nodes() if t)[:200])
    back()
    back_to("Search notes")
    open_note("Under the line", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    adb("logcat", "-c")
    app = adb("shell", "pidof", PACKAGE).strip()
    tap("Text", exact=True, wait=0.3)
    sheet = handed(15.0)
    failed = logged(route_pids(app, sheet), "TransactionTooLarge")
    check("99,000 characters of words reach the share sheet",
          sheet != PACKAGE and not failed, "%s %s" % (sheet, failed[:2]))
    back_in()
    back_to("Search notes")

    # A page of a table of 50,000 empty cells: made away from the screen,
    # in a moment, where it took billions of steps on the screen's thread.
    open_note("Empty cells", limit=30.0)
    tap("Share", exact=True, wait=1.0)
    adb("logcat", "-c")
    started = time.time()
    tapped = tap("Web page", exact=True, wait=0.2)
    sheet = handed(30.0)
    took = time.time() - started
    pid = adb("shell", "pidof", PACKAGE).strip()
    anr = "ANR in " + PACKAGE in adb("logcat", "-d", "-b", "events,main,system")
    check("a page of a table of 50,000 empty cells reaches the share sheet without holding the app",
          tapped and sheet != PACKAGE and not anr and bool(pid), "%s after %.1f s, ANR %s" % (sheet, took, anr))
    back_in()
    back_to("Search notes")

    # A PDF that stops at forty pages says so before anything is handed
    # over: a question since round four (limits_round4 walks its answers),
    # where it was a bar the share sheet covered.
    open_note("Forty pages", limit=30.0)
    tap("Share", exact=True, wait=1.0)
    tap("PDF", exact=True, wait=0.3)
    asked = settle(lambda: find("PDF, first 40 pages", exact=True), lambda at: at is not None, within=30.0, first=0)
    shot(folder, "99-pdf-first-pages", "first 40 pages")
    tap("PDF, first 40 pages", exact=True, wait=0.3)
    sheet = handed(30.0)
    pdf = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Forty pages.pdf", binary=True)
    with open(os.path.join(folder, "99-forty-pages.pdf"), "wb") as f:
        f.write(pdf)
    check("a PDF of a note longer than forty pages is asked about, then is the forty pages",
          asked is not None and sheet != PACKAGE and page_count(pdf) == 40, "%s, %d pages" % (sheet, page_count(pdf)))
    back_in()
    back_to("Search notes")


def limits_round4(folder):
    """The final audit's fourth round, walked: what one note draws, counted
    for the whole note -- ten tables of 1,000 cells, 200,000 empty lines,
    3,000 tables of two cells, "x" and "" after a spent limit, and a journal
    of 7,000 lines -- each opened and timed by the app's own frames, a
    letter typed into the journal timed too; then what goes out: a note of
    Chinese text past what Android carries says so on Share -> Text, a code
    too long says so on its card before the tap, and a PDF that would leave
    part of the note out asks first -- Cancel hands nothing over, Web page
    hands the page, the PDF is forty pages with nothing written into it."""
    print("\n-- round four's limits: what one note draws, and what is asked before it goes --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r4-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def grid(rows, columns):
        return "\n".join("| " + " | ".join("---" if r == 1 else "r%dc%d" % (r, c) for c in range(columns)) + " |"
                         for r in range(rows))
    journal = "Today I walked to the river and back again.\n" * 7_000
    notes = [note(1, "Ten tables", body="\n\n".join([grid(50, 20)] * 10)),
             note(2, "Many lines", body="Above\n" + "\n" * 200_000),
             note(3, "Tiny tables", body="\n\n".join(["| a | b |"] * 3_000)),
             note(4, "Alternating", body="\n".join(["a" * 10_000] * 2 + ["x", ""] * 5_000)),
             note(5, "Journal", body=journal),
             # Where the screen shows them: a table of 1,000 cells two rows
             # tall, a small one under it; and many lines in the comments,
             # near the top. In a body the rest lies a screen's height times
             # thousands below, out of the tree the walk reads. Pinned, so
             # their cards are above the fold of the list.
             note(6, "Wide then small", body=grid(2, 500) + "\n\n" + grid(3, 3), pinned=True),
             note(7, "Lines in comments", comments="\n" * 200_000, pinned=True)]

    def opened_in(title):
        back_to("Search notes")
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        if not open_note(title, limit=40.0):
            return None
        time.sleep(1.5)
        _, frames = frames_once_shown()     # a look, which waits for the frames still drawing to end
        longest = max(frames)
        print("     %s: the longest frame %.2f s" % (title, longest))
        return longest

    # What one table at the line takes on this emulator, now: ten of them
    # are drawn as one, so they take about as long -- a slow emulator's
    # second is not the app's (big_table times the table alone, 2.2 s).
    if not seed_inside([note(9, "One table", body=grid(50, 20))], {"welcome_offered": True}, "One table"):
        return
    one = opened_in("One table") or 99.0
    if not seed_inside(notes, {"welcome_offered": True}, "Ten tables"):
        return
    took = opened_in("Ten tables")
    check("ten tables of 1,000 cells open with one drawn, about as long as one table alone",
          took is not None and took < 1.5 * one + 1.0 and find("r0c0", exact=True) is not None,
          "%s s, one alone %.2f s" % (took, one))
    took = opened_in("Wide then small")
    check("a table under one that drew 1,000 cells is shown as its lines, and says why",
          took is not None and took < 1.5 * one + 1.0 and not screen_says("already fill the 1,000 cells"), "%s s" % took)
    shot(folder, "100-table-past-the-others", "already fill")
    took = opened_in("Many lines")
    check("200,000 empty lines open within two seconds", took is not None and took < 2.0 and find("Above") is not None,
          "%s s" % took)
    took = opened_in("Lines in comments")
    if screen_says("more than is shown here at once") and screen_says("Comments") == []:
        tap("Comments", wait=1.5)       # folded away under its label
    check("... and comments of 200,000 lines are shown read-only, with how many lines they hold",
          took is not None and took < 1.0 and not screen_says("more than is shown here at once", "more lines"),
          "%s s, %s" % (took, screen_says("more than is shown here at once", "more lines")))
    shot(folder, "101-many-lines-read-only", "more than is shown here at once")
    took = opened_in("Tiny tables")
    # Since the fifth round a note draws 500 pieces, not 100 -- fifty weekly
    # tables reached 100 -- and these, the hostile case, measured 1.7 to 2.7 s
    # there (0.3 s at 100; held still before round four, past 90 s).
    check("3,000 tables of two cells open within three seconds", took is not None and took < 3.0, "%s s" % took)
    took = opened_in("Alternating")
    check("'x' and '' five thousand times after a spent limit open within a second", took is not None and took < 1.0,
          "%s s" % took)
    took = opened_in("Journal")
    check("a journal of 7,000 lines opens within two seconds, every line a field", took is not None and took < 2.0,
          "%s s" % took)
    shot(folder, "101b-journal-fields", "Today I walked")
    # A letter typed into it: one field drawn again, not the whole note --
    # read before the autosave's 800 ms, whose write of the notebook is a
    # frame of its own.
    tap("Today I walked to the river and back again.", wait=0.8)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    adb("shell", "input", "text", "Z")
    # Read once the letter is on the screen, the frames of the first 0.7 s
    # alone: read half a second on, a frame longer than that was in no list,
    # and a check of "within a second" could not fail (the final audit's
    # eighth round).
    _, frames = frames_once_shown("Z", within=20.0, gap=0.7)
    typed = max(frames)
    print("     Journal, a letter typed: the longest frames %s s" % ["%.2f" % f for f in sorted(frames)[-3:]])
    saved = settle(lambda: (note_by_uid("r4-5") or {}).get("body", ""), lambda b: "Z" in b, within=8.0)
    # Measured 2026-10-28 on the emulator: a letter's frames 0.1 to 0.4 s,
    # the autosave's write of the 308,000 characters 0.4 to 0.8 s after it;
    # the build before this round held 8 s and more just opening the note.
    check("... and a letter typed into it is drawn within a second, and saved",
          typed < 1.0 and "Z" in saved and len(saved) == len(journal) + 1, "%.2f s" % typed)
    back_to("Search notes")
    kept = read_file("notes_data.json") or []
    check("the file holds every other note as it was",
          [n.get("body") for n in kept[:4]] == [n["body"] for n in notes[:4]]
          and (kept[6] if len(kept) > 6 else {}).get("comments") == notes[6]["comments"])

    # What goes out, in a notebook of its own.
    cjk = "今日は漢字の練習をしました。"
    import random
    rng = random.Random(7)
    noise = "".join(chr(97 + rng.randrange(26)) for _ in range(150_000))
    shared = [note(11, "Long Chinese", body=cjk * (99_947 // len(cjk))),
              note(12, "Short Chinese", body=cjk * (55_000 // len(cjk))),
              note(13, "Noise", body=noise),
              note(14, "Forty pages", body="\n".join(
                  "Line %d of a note that goes on far past forty pages." % i for i in range(1, 3001))),
              note(15, "Wide table", body=grid(4, 30))]
    if not seed_inside(shared, {"welcome_offered": True}, "Short Chinese"):
        return

    def focused():
        out = adb("shell", "dumpsys", "window")
        match = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([\w.]+)/", out)
        return match.group(1) if match else foreground()

    def handed(seconds):
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

    open_note("Long Chinese", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    said = next((t for t, d, _ in nodes() if t.startswith("Too long for a message")), "")
    numbers = [int(n.replace(",", "")) for n in re.findall(r"\d[\d,]*", said)]
    print("     the Text card: %s" % said)
    check("99,950 Chinese characters, past what Android carries, are refused on the card, its numbers agreeing",
          len(numbers) == 2 and numbers[1] < numbers[0] and 55_000 < numbers[1] < 65_000 and "of them at most" in said,
          repr(said))
    shot(folder, "102-share-chinese-too-long", "Too long for a message")
    back()
    back_to("Search notes")
    open_note("Short Chinese", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    adb("logcat", "-c")
    app = adb("shell", "pidof", PACKAGE).strip()
    tap("Text", exact=True, wait=0.3)
    sheet = handed(15.0)
    failed = logged(route_pids(app, sheet), "TransactionTooLarge", "Failed to deliver")
    check("55,000 Chinese characters reach the share sheet, no binder failure in the log",
          sheet != PACKAGE and not failed, "%s %s" % (sheet, failed[:2]))
    back_in()
    back_to("Search notes")

    open_note("Noise", limit=30.0)
    tap("Share", exact=True, wait=1.5)
    check("a code too long to hand over says so on its card, before the tap",
          find("Too long for a code") is not None, " | ".join(t for t, d, _ in nodes() if t)[:300])
    shot(folder, "103-share-code-too-long", "Too long for a code")
    tap("Share code", exact=True, wait=1.2)
    check("... and the card does nothing", find("Share this note as") is not None and focused() == PACKAGE, focused())
    back()
    back_to("Search notes")

    def ask_pdf(title, button):
        open_note(title, limit=30.0)
        adb("shell", "run-as", PACKAGE, "rm", "-rf", "cache/share")
        tap("Share", exact=True, wait=1.0)
        tap("PDF", exact=True, wait=0.3)
        return settle(lambda: find(button, exact=True), lambda at: at is not None, within=30.0, first=0)

    def in_share():
        out = adb("shell", "run-as", PACKAGE, "ls", "cache/share")
        return [] if "No such file" in out else [f.strip() for f in out.split("\n") if f.strip()]

    asked = ask_pdf("Forty pages", "PDF, first 40 pages")
    check("a PDF of more than forty pages asks first, with the web page, the forty pages and Cancel",
          asked is not None and not screen_says("Not all of this note fits a PDF", "only its first 40 pages",
                                                 "Web page", "Cancel"))
    shot(folder, "104-pdf-asks-first", "Not all of this note fits a PDF")
    tap("Cancel", exact=True, wait=1.5)
    check("... Cancel hands nothing over and keeps no file", focused() == PACKAGE and in_share() == [], repr(in_share()))
    back_to("Search notes")
    ask_pdf("Forty pages", "Web page")
    tap("Web page", exact=True, wait=0.3)
    sheet = handed(20.0)
    files = in_share()
    check("... Web page hands the page over instead", sheet != PACKAGE and files == ["Forty pages.html"], repr(files))
    back_in()
    back_to("Search notes")
    ask_pdf("Forty pages", "PDF, first 40 pages")
    tap("PDF, first 40 pages", exact=True, wait=0.3)
    sheet = handed(30.0)
    pdf = adb("exec-out", "run-as", PACKAGE, "cat", "cache/share/Forty pages.pdf", binary=True)
    with open(os.path.join(folder, "104-forty-pages.pdf"), "wb") as f:
        f.write(pdf)
    # The foot line was text drawn on the page, a font in the file; the
    # pages themselves are pictures.
    check("... the PDF is the forty pages, with nothing written into it",
          sheet != PACKAGE and page_count(pdf) == 40 and b"/Font" not in pdf, "%d pages, font %s" % (page_count(pdf), b"/Font" in pdf))
    back_in()
    back_to("Search notes")
    asked = ask_pdf("Wide table", "PDF, cut at the edge")
    check("a note wider than the page asks the same, the PDF cut at the edge",
          asked is not None and not screen_says("cut at the page's edge"))
    shot(folder, "105-pdf-wide-asks", "cut at the page's edge")
    tap("Cancel", exact=True, wait=1.5)
    back_to("Search notes")


def limits_round5(folder):
    """The final audit's fifth round, walked. What an ordinary note keeps:
    fifty weekly tables and sixty sessions with tables, each a grid to the
    end, Find reaching their ends; a checklist of 1,000 short rows opened and
    typed into, and one of 120 rows of 150,000 letters opened within seconds,
    its rows shown short. A note past what the phone draws: a word in its
    rest found and its line shown, read on; Enter at the end of what is drawn
    lets the keyboard go and says why, nothing glued on the line above; the
    calculator refuses to write out of sight. A table typed row by row by
    hand comes out in order; a two-million-letter note typed in forty times
    keeps its memory; and the template editor saves the words left in its A
    new item box. (The Share sheet of a 5 MB note was timed too, and held
    1.3 to 1.6 s before the round, 0.8 to 1.4 after: no check, since it
    could not tell the two builds apart.)"""
    print("\n-- round five's limits: what an ordinary note keeps, and where the cursor goes --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r5-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def opened_in(title, limit=40.0):
        back_to("Search notes")
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        if not open_note(title, limit=limit):
            return None
        time.sleep(1.5)
        _, frames = frames_once_shown()     # a look, which waits for the frames still drawing to end
        longest = max(frames)
        print("     %s: the longest frame %.2f s" % (title, longest))
        return longest

    def find_in_note(word):
        # The bar opens with the last search: taken out, the new one typed.
        if not find("Close find"):
            tap("⋮", exact=True)
            tap("🔍 Find in note", wait=1.2)
        adb("shell", "input", "keyevent", *["KEYCODE_DEL"] * 24)
        type_text(word)
        time.sleep(1.2)

    def close_find():
        for _ in range(3):
            if not find("Close find"):
                break
            back(wait=1.2)

    def counter():
        return next((t for t, d, _ in nodes() if re.fullmatch(r"\d+/\d+\+?", t or "")), "")

    weeks = "\n".join("Week %d\n| Day | Spent |\n|---|---|\n| Mon | %d |" % (w, w) for w in range(1, 51))
    sessions = "\n".join("Session %d%s\n| Set | Reps | Kg |\n|---|---|---|\n| 1 | 8 | %d |"
                         % (s, ": deadlift" if s == 57 else "", 40 + s) for s in range(1, 61))
    # Every line drawn as one line, so the fields draw exactly what a body
    # may (Editing.MAX_DRAWN_LINES, 40,000): "Last", a field of its own under
    # a table, is the last of them, and the 20,000 lines after it the rest.
    past = "\n".join(["line %d" % k for k in range(39_999)] + ["| a | b |", "Last"]
                     + ["line %d" % k + (" needle" if k == 50_000 else "") for k in range(40_000, 60_000)])
    short_rows = [{"text": "item %d" % k, "done": False} for k in range(1, 1001)]
    wide_rows = [{"text": ("word " * 30_000)[:149_994], "done": False} for _ in range(120)]
    notes = [note(1, "Fifty weeks", body=weeks), note(2, "Sixty sessions", body=sessions),
             note(3, "Past the lines", body=past), note(4, "Thousand rows", body="Shop", checklist=short_rows)]
    if not seed_inside(notes, {"welcome_offered": True}, "Fifty weeks"):
        return

    # Fifty weeks, 100 pieces: the 50th table was the read-only rest.
    took = opened_in("Fifty weeks")
    find_in_note("Spent")
    check("fifty weekly tables: Find counts the header of every one", counter() == "1/50", counter())
    find_in_note("Week 50")
    # The keyboard down: the 50th grid lies under it, below its heading.
    keyboard_down()
    rest_said = not screen_says("more than is shown here at once")
    check("... and the last week is a heading, its table a grid under it, nothing read-only",
          find("Week 50") is not None and find("50", exact=True) is not None and not rest_said
          and took is not None and took < 3.0, "%s s, %s, rest said %s" % (took, counter(), rest_said))
    shot(folder, "106-fifty-weeks-end", "Week 50")
    close_find()
    back_to("Search notes")

    opened_in("Sixty sessions")
    find_in_note("deadlift")
    check("sixty sessions with tables: a word in the 57th is found, not 0/0", counter() == "1/1", counter())
    shot(folder, "107-sixty-sessions-found", "deadlift")
    close_find()
    back_to("Search notes")

    # Past what the phone draws: the rest found, read on, and its end.
    opened_in("Past the lines")
    find_in_note("needle")
    check("a word in a note's read-only rest is found, its line shown",
          counter() == "1/1" and find("line 50000 needle") is not None
          and not screen_says("Lines 10,000 to 10,002 of 20,000"),
          "%s, %s" % (counter(), screen_says("Lines 10,000 to 10,002 of 20,000")))
    shot(folder, "108-rest-found", "line 50000 needle")
    tap("Read on ›", exact=True, wait=1.5)
    # The forty lines push the footer under the keyboard: the first of them is read instead.
    check("... and Read on shows the next forty of its lines",
          find("line 50002", exact=True) is not None and find("line 50000 needle") is None,
          " | ".join(t for t, d, _ in nodes() if t)[:200])
    find_in_note("Last")
    close_find()
    tap("Last", exact=True, wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    time.sleep(0.5)
    # A message of four seconds, read by looks of two seconds and more on the
    # walk's heaviest screen, raced its own reading: read once in one run of
    # two, the app having said it both times. Held for fifteen by Android's
    # own setting, which the app's messages keep; photographed as read; the
    # Enter's frames timed, so a hold by the app is told from a slow look
    # (the final audit's tenth round).
    with messages_held(15_000):
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        adb("shell", "input", "keyevent", "KEYCODE_ENTER")
        missing = shot_as_said(folder, "109-end-past-drawn", "The end of this note is more than the phone shows at once",
                               within=15.0)
        longest = max(frames_since_reset() or [0.0])
    time.sleep(1.0)
    type_text("glued")
    check("Enter at the end of what is drawn lets the keyboard go", not keyboard_shown())
    check("... and says why", not missing, "missing %s; the longest frame since the Enter %.2f s" % (missing, longest))
    back_to("Search notes")
    body = (note_by_uid("r5-3") or {}).get("body", "")
    lines = body.split("\n")
    check("... the line typed is kept, and nothing is glued onto the line above",
          len(lines) == 60_002 and lines[40_000] == "Last" and lines[40_001] == "" and "glued" not in body,
          repr(lines[39_999:40_003]))
    # Opened again, the cursor never in it: the calculator does not write out of sight.
    open_note("Past the lines", limit=40.0)
    tap("⋮", exact=True)
    tap("🧮 Calculator", wait=1.2)
    for key in ("1", "+", "1", "="):
        tap(key, exact=True, wait=0.3)
    tap("⬇ Insert into note", wait=0.3)
    said = not says_soon("Tap in its text where the answer should go", within=6.0)
    back_to("Search notes")
    check("the calculator writes nothing where no field shows it, and says where to tap",
          said and (note_by_uid("r5-3") or {}).get("body", "") == body, str(said))

    # The checklists: 1,000 short rows, typed into; 120 rows of 150,000 letters.
    took = opened_in("Thousand rows")
    if screen_says("item 1") and find("CHECKLIST"):
        tap("CHECKLIST", wait=1.5)
    check("a checklist of 1,000 short rows opens within three seconds", took is not None and took < 3.0, "%s s" % took)
    tap("item 1", exact=True, wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    type_text("0")
    _, frames = frames_once_shown()     # a look, which waits for the letter's frame to end
    typed = max(frames)
    check("... and a letter typed into a row is drawn within half a second", typed < 0.5, "%.2f s" % typed)
    back_to("Search notes")
    check("... and saved", ((note_by_uid("r5-4") or {}).get("checklist") or [{}])[0].get("text") == "item 10")
    # In a notebook of its own: 18 MB, and the app reads one file at a time.
    if not seed_inside([note(5, "Wide rows", body="Rows", checklist=wide_rows)], {"welcome_offered": True}, "Wide rows"):
        return
    took = opened_in("Wide rows", limit=60.0)
    # Measured 2026-10-29: 7.07 s before the round, 1.74 after; the same text in a body, 1.63.
    check("a checklist of 120 rows of 150,000 letters opens within two and a half seconds, each row shown short",
          took is not None and took < 2.5 and not screen_says("Read all 149,994 characters"), "%s s" % took)
    shot(folder, "110-wide-rows-short", "Rows")
    back_to("Search notes")
    kept = read_file("notes_data.json") or []
    check("... and the file keeps every row whole",
          len(kept) == 1 and [r.get("text") for r in kept[0].get("checklist", [])] == [r["text"] for r in wide_rows])

    # A table typed by hand, row by row: in order, under its grid.
    back_to("Search notes")
    tap("+", exact=True, wait=1.5)
    tap("Title")
    type_text("Typed table")
    tap("Write your note")
    type_text("Shopping")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    # Key by key, at a fast typist's pace: sent as one burst a millisecond
    # apart, just after the cursor moved, the keyboard's own handling
    # mangled the second row -- input no person makes (2026-10-29: right at
    # a key every 80 ms, mangled as a burst).
    for first, second in (("a", "b"), ("c", "d")):
        for key in ("\\|", "%s", first, "%s", "\\|", "%s", second, "%s", "\\|"):
            adb("shell", "input", "text", key)
            time.sleep(0.1)
        time.sleep(1.5)
    shot(folder, "111-typed-table", "Typed table")
    back_to("Search notes")
    typed_note = next((n for n in read_file("notes_data.json") or [] if n.get("title") == "Typed table"), {})
    # Lower-cased: the keyboard makes the first letter of a line a capital.
    check("a table typed row by row by hand keeps its rows in order, under the line before it",
          (typed_note.get("body") or "").lower() == "shopping\n| a | b |\n| c | d |", repr(typed_note.get("body")))

    # A two-million-letter note typed in forty times: the Undo's steps are held to what a phone can keep.
    # One letter past ASCII, so the phone keeps every copy at two bytes a letter.
    two = note(7, "Two million", body="Café " + "x" * 1_999_000 + "\nend")
    if not seed_inside([two], {"welcome_offered": True}, "Two million"):
        return
    open_note("Two million", limit=60.0)
    tap("end", exact=True, wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("logcat", "-c")
    typing = adb("shell", "pidof", PACKAGE).strip()
    heaps = []
    for k in range(1, 41):
        type_text("w%d" % k)
        time.sleep(1.2)
        if k % 10 == 0:
            m = re.search(r"Java Heap:\s+(\d+)", adb("shell", "dumpsys", "meminfo", PACKAGE))
            heaps.append(int(m.group(1)) // 1024 if m else -1)
    alive = adb("shell", "pidof", PACKAGE).strip()
    print("     the Java heap after 10, 20, 30, 40 bursts: %s MB" % heaps)
    check("a two-million-letter note typed in forty bursts keeps running, its heap no longer growing",
          bool(alive) and len(heaps) == 4 and heaps[-1] - heaps[1] < 40 and not logged([typing, alive], "OutOfMemoryError"),
          "%s, %s" % (alive, heaps))
    back_to("Search notes")

    # The template editor: the words left in A new item are saved with it.
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("+ New template", wait=1.5)
    tap("Template name", exact=True, wait=0.6)
    type_text("Trip")
    keyboard_down()
    tap("A new item", wait=0.8)
    type_text("Passport")
    tap("Save", exact=True, wait=1.5)
    saved = read_file("templates.json") or []
    check("a template saved with words in its A new item box keeps them as its last row",
          [[c.get("text") for c in t.get("checklist", [])] for t in saved if t.get("name") == "Trip"] == [["Passport"]],
          repr(saved)[:200])
    back_to("Search notes")


def limits_round6(folder):
    """The final audit's sixth round, walked. A checklist row at the
    desktop's 2,000 letters keeps its own words when a letter or a paste goes
    in at its start: the letter refused, the paste cut, each said. A row
    moved up or down across the line of rows not drawn, or dragged over it,
    stays in sight; a search for one of the last rows draws no more of them.
    A row longer than one is written in, and one past what a list draws to
    write in, are read whole, and Find shows where their word is. A template
    of 1,000 rows of 2,000 letters opens with its rows drawn in part; one of
    1,000 rows refuses an Add and never drops the waiting words unsaid. Redo
    of a table row typed at the end puts the cursor under the grid; and
    Insert table writes nothing where it would not be seen, and says so."""
    print("\n-- round six's limits: a row's own words, rows in sight, rows read whole --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r6-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def rows_of(uid):
        return [r.get("text") for r in (note_by_uid(uid) or {}).get("checklist") or []]

    def opened_in(title, opens=None, limit=40.0):
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        if not (opens or (lambda: open_note(title, limit=limit)))():
            return None
        time.sleep(1.5)
        _, frames = frames_once_shown()     # a look, which waits for the frames still drawing to end
        longest = max(frames)
        print("     %s: the longest frame %.2f s" % (title, longest))
        return longest

    def find_in_note(word):
        if not find("Close find"):
            tap("⋮", exact=True)
            tap("🔍 Find in note", wait=1.2)
        adb("shell", "input", "keyevent", *["KEYCODE_DEL"] * 24)
        type_text(word)
        time.sleep(1.2)

    def close_find():
        for _ in range(3):
            if not find("Close find"):
                break
            back(wait=1.2)

    def counter():
        return next((t for t, d, _ in nodes() if re.fullmatch(r"\d+/\d+\+?", t or "")), "")

    def shown_beside(word):
        """Whether [word] is on the screen in a node of more than itself: the
        find bar's own field holds the word alone."""
        return any(word in t and t.strip() != word for t, d, _ in nodes())

    def keys(*names):
        adb("shell", "input", "keycombination", *("KEYCODE_" + n for n in names))
        time.sleep(1.0)

    def unfold(first_row):
        if screen_says(first_row) and find("CHECKLIST"):
            tap("CHECKLIST", wait=1.5)


    key_row = "a" * (2_000 - 16) + "DON'T FORGET KEY"
    recipe = "r" * 1_480 + " Serve with rice."
    thousand = [{"text": "item %d" % k, "done": False} for k in range(1_000)]
    words = " ".join("word%d" % k for k in range(600))
    long_row = (words[:1_500] + " PINEAPPLE " + words[1_500:2_900] + " ZUCCHINI tail")
    inventory = [{"text": ("stock " * 400)[:1_500] + "ITEM%04dX" % k + (" more" * 200)[:491], "done": False}
                 for k in range(60)]
    notes = [note(1, "Key row", checklist=[{"text": key_row, "done": False}, {"text": "milk", "done": False}]),
             note(2, "Recipe row", checklist=[{"text": recipe, "done": False}, {"text": "salt", "done": False}]),
             note(3, "Paste source", body="p" * 800),
             note(4, "Long list", body="Shop", checklist=thousand),
             note(5, "Long words", body="Words", checklist=[{"text": long_row, "done": False}]),
             note(6, "Inventory", body="Stock", checklist=inventory)]
    if not seed_inside(notes, {"welcome_offered": True}, "Key row"):
        return

    # A row at the cap: a letter at its start is refused, the row whole.
    open_note("Key row")
    unfold("milk")
    tap("DON'T FORGET KEY", wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    time.sleep(0.4)
    type_text("X")
    said = not shot_as_said(folder, "112-row-cap-said", "A checklist item can be up to 2,000 characters",
                            over_keys=True)
    tap("milk", exact=True, wait=1.0)
    back_to("Search notes")
    kept = rows_of("r6-1")
    check("a letter typed at the start of a row at 2,000 letters is refused, said, the row whole",
          said and kept[:1] == [key_row], "said %s, %r" % (said, (kept[0][-20:], len(kept[0])) if kept else kept))

    # 800 letters pasted at the start of a row of 1,497: 503 of them, the row whole after them.
    open_note("Paste source")
    tap("pppp", wait=0.8)
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "C")
    back_to("Search notes")
    open_note("Recipe row")
    unfold("salt")
    tap("Serve with rice", wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    time.sleep(0.4)
    keys("CTRL_LEFT", "V")
    said = not says_soon("Pasted text shortened: a checklist item can be up to 2,000 characters", within=6.0)
    tap("salt", exact=True, wait=1.0)
    back_to("Search notes")
    kept = rows_of("r6-2")
    check("a paste at the start of a long row is cut to the room left, the row's own words whole, and said",
          said and kept[:1] == ["p" * 503 + recipe],
          "said %s, %r" % (said, (kept[0][:10], kept[0][-20:], len(kept[0])) if kept else kept))

    # A list of 1,000: the first 200 rows and the last five drawn, the line between.
    open_note("Long list", limit=30.0)
    unfold("item 0")
    find_in_note("item 995")
    close_find()
    hidden = lambda: next((t for t, d, _ in nodes() if "more rows here" in t), "")
    tap("Drag to move item 995", exact=True, wait=1.0)
    tap("Move up", exact=True, wait=1.5)
    order = settle(lambda: rows_of("r6-4"), lambda r: r[994:995] == ["item 995"], first=0.5)
    check("Move up on the first of the last rows: it stays in sight, one place up",
          order[994:996] == ["item 995", "item 994"] and find("Drag to move item 995", exact=True) is not None
          and "794 more rows" in hidden(), "%r, %r" % (order[993:997], hidden()))
    shot(folder, "113-moved-row-in-sight", "item 995")
    tap("Drag to move item 995", exact=True, wait=1.0)
    tap("Move down", exact=True, wait=1.5)
    order = settle(lambda: rows_of("r6-4"), lambda r: r[995:996] == ["item 995"], first=0.5)
    check("... and Move down puts it back in its place, still in sight",
          order[994:996] == ["item 994", "item 995"] and find("Drag to move item 995", exact=True) is not None,
          repr(order[993:997]))
    # The last of the first 200, moved down past the line, and back.
    find_in_note("item 199")
    close_find()
    tap("Drag to move item 199", exact=True, wait=1.0)
    tap("Move down", exact=True, wait=1.5)
    order = settle(lambda: rows_of("r6-4"), lambda r: r[200:201] == ["item 199"], first=0.5)
    check("Move down on the 200th row: it stays in sight, one place down",
          order[199:201] == ["item 200", "item 199"] and find("Drag to move item 199", exact=True) is not None,
          repr(order[198:202]))
    tap("Drag to move item 199", exact=True, wait=1.0)
    tap("Move up", exact=True, wait=1.5)
    settle(lambda: rows_of("r6-4"), lambda r: r[199:200] == ["item 199"], first=0.5)
    # Dragged from the last rows over the line onto the 200th: there, both in sight.
    find_in_note("item 997")
    close_find()
    # Both on the screen: the found row may stand at the top of it, the 200th above.
    for _ in range(3):
        if find("Drag to move item 199", exact=True):
            break
        adb("shell", "input", "swipe", "900", "900", "900", "1300", "400")
        time.sleep(1.0)
    start, end = find("Drag to move item 997", exact=True), find("Drag to move item 199", exact=True)
    if start and end:
        adb("shell", "input", "swipe", str(start[0]), str(start[1]), str(end[0]), str(end[1]), "1500")
        time.sleep(1.5)
    order = settle(lambda: rows_of("r6-4"), lambda r: r[199:200] == ["item 997"], first=0.5)
    check("a row dragged over the line of rows not drawn lands where it was let go, and every row stays in sight",
          bool(start and end) and order[199:201] == ["item 997", "item 199"]
          and find("Drag to move item 997", exact=True) is not None and find("Drag to move item 199", exact=True) is not None,
          "%s %s, %r" % (start, end, order[198:202]))
    # A search for one of the last rows draws no more of them.
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    find_in_note("item 998")
    _, frames = frames_once_shown()     # a look, which waits for the search's frames to end
    took = max(frames)
    check("Find on one of the last rows draws no more rows, each letter in the bar quick",
          counter() == "1/1" and "more rows here" in hidden() and took < 1.0, "%s, %r, %.2f s" % (counter(), hidden(), took))
    close_find()
    back_to("Search notes")

    # A row longer than one is written in: read whole on asking; Find shows its word.
    open_note("Long words")
    unfold("Read all")
    asked = find("Read all %s characters" % format(len(long_row), ","))
    if asked:
        tap("Read all", wait=1.5)
        # Its middle lies under the screen's edge, as it would for a person: read down to it.
        seek(lambda: shown_beside("PINEAPPLE"))
    check("a row of %s letters says it can be read whole, and shows its middle when asked" % format(len(long_row), ","),
          asked is not None and shown_beside("PINEAPPLE"), str(asked))
    shot(folder, "114-long-row-read", "PINEAPPLE")
    seek(lambda: find("‹ Show less", exact=True) is not None)
    tap("‹ Show less", exact=True, wait=1.0)
    find_in_note("ZUCCHINI")
    check("... and Find opens it on the part its word is in", counter() == "1/1" and shown_beside("ZUCCHINI"),
          counter())
    close_find()
    back_to("Search notes")
    # Past what a list draws to write in: row 56 of 60 rows of 2,000 letters.
    open_note("Inventory", limit=30.0)
    unfold("stock")
    find_in_note("ITEM0055X")
    check("a row shown short, past what a list draws to write in, shows the word Find stopped on",
          counter() == "1/1" and shown_beside("ITEM0055X"), counter())
    keyboard_down()
    shot(folder, "115-shown-short-found", "ITEM0055X")
    close_find()
    back_to("Search notes")
    kept = read_file("notes_data.json") or []
    check("... and the file keeps every row as it was",
          [r.get("text") for r in next((n for n in kept if n.get("uid") == "r6-6"), {}).get("checklist", [])]
          == [r["text"] for r in inventory]
          and [r.get("text") for r in next((n for n in kept if n.get("uid") == "r6-5"), {}).get("checklist", [])]
          == [long_row])

    # Templates: one of 1,000 rows of 2,000 letters, and one of 1,000 rows, full.
    adb("shell", "pm", "clear", PACKAGE)
    long_rows = [{"text": ("long words " * 200)[:1_990] + " row%04d" % k, "done": False} for k in range(1_000)]
    full_rows = [{"text": "row %d" % k, "done": False} for k in range(1_000)]
    put_inside("templates.json", [
        {"name": "Thousand long", "title": "t", "body": "b", "comments": "", "category": "General", "checklist": long_rows},
        {"name": "Full template", "title": "t", "body": "b", "comments": "", "category": "General", "checklist": full_rows}])
    if not seed_inside([note(7, "Past the tables", body="\n".join("Heading %d\n| a%d | b |" % (k, k) for k in range(1, 601)))],
                       {"welcome_offered": True}, "Past the tables", clear=False):
        return
    tap("Settings", exact=True, wait=1.5)
    tap("Templates", exact=True, wait=1.5)
    took = opened_in("Thousand long", opens=lambda: tap("Thousand long", exact=True, wait=0.5, patience=20.0))
    # Measured 2026-10-29 in one session, the first opening and two more:
    # 7.2 and 4.3 s before the round, 3.0, 1.9 and 1.4 after it.
    check("a template of 1,000 rows of 2,000 letters opens within four seconds, its rows drawn in part",
          took is not None and took < 4.0 and not screen_says("Edit template"), "%s s" % took)
    tap("Template name", exact=True, wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    type_text("s")
    # Read once the letter is drawn, not 1.4 s on (frames_once_shown).
    _, frames = frames_once_shown("Thousand longs", within=20.0)
    letter = max(frames)
    print("     a letter in its name: the longest frame %.2f s" % letter)
    check("... and a letter typed in its name is drawn within a quarter of a second", letter < 0.25, "%.2f s" % letter)
    tap("Close", exact=True, wait=1.0)
    if find("Don't save", exact=True):
        tap("Don't save", exact=True, wait=1.5)
    # Full at 1,000: Add refused and said; the words waiting asked about, never dropped.
    tap("Full template", exact=True, wait=2.0, patience=10.0)
    # Under the 205 rows drawn: scrolled to, as a person would.
    seek(lambda: find("A new item") is not None, tries=40, fast=True)
    check("a template of 1,000 rows draws its first 200 and its last five, the line between saying so",
          not screen_says("795 more rows here, not drawn yet. Show 200 of them", "row 999"),
          str(screen_says("795 more rows here, not drawn yet. Show 200 of them", "row 999")))
    tap("A new item", wait=0.8)
    type_text("Passport")
    keyboard_down()
    tap("+ Add", exact=True, wait=0.3)
    said = not shot_as_said(folder, "116-template-full-said", "A checklist holds at most 1000 items.", "Passport")
    tap("Close", exact=True, wait=1.0)
    asked = find("Save changes?") is not None
    tap_last("Save", wait=0.3)
    still = not says_soon("A checklist holds at most 1000 items.", within=6.0) and find("Passport") is not None
    check("a template of 1,000 rows refuses Add and says so; closing asks, and Save keeps the editor open, the words in it",
          said and asked and still, "said %s, asked %s, still open %s" % (said, asked, still))
    tap("Close", exact=True, wait=1.0)
    tap("Don't save", exact=True, wait=1.5)
    saved = read_file("templates.json") or []
    full = next((t for t in saved if t.get("name") == "Full template"), {})
    check("... and nothing was saved over it", [c.get("text") for c in full.get("checklist", [])] == [r["text"] for r in full_rows])
    back_to("Search notes")

    # Insert table past what the note draws: nothing written, and said.
    open_note("Past the tables", limit=40.0)
    find_in_note("Heading 250")
    close_find()
    before = (note_by_uid("r6-7") or {}).get("body")
    tap("Heading 250", exact=True, wait=1.0)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    time.sleep(0.4)
    tap("Insert table", wait=1.2)
    tap("Insert", exact=True, wait=0.3)
    said = not says_soon("Tap in its text where the table should go", within=6.0)
    back_to("Search notes")
    check("Insert table where the note's drawing has ended writes nothing, and says where to tap",
          said and (note_by_uid("r6-7") or {}).get("body") == before, str(said))

    # Redo of a table row typed at the end: the cursor under the grid, the next row after it.
    tap("+", exact=True, wait=1.5)
    tap("Title")
    type_text("Redo table")
    tap("Write your note")
    type_text("Costs")
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    for k, key in enumerate(("\\|", "%s", "a", "%s", "\\|", "%s", "b", "%s", "\\|")):
        adb("shell", "input", "text", key)
        time.sleep(0.1)
    time.sleep(1.5)
    tap("Undo", exact=True, wait=1.2)
    tap("Redo", exact=True, wait=1.2)
    for key in ("\\|", "%s", "c", "%s", "\\|", "%s", "d", "%s", "\\|"):
        adb("shell", "input", "text", key)
        time.sleep(0.1)
    time.sleep(1.5)
    shot(folder, "117-redo-under-grid", "Redo table")
    back_to("Search notes")
    typed = next((n for n in read_file("notes_data.json") or [] if n.get("title") == "Redo table"), {})
    check("after Undo and Redo of a table row typed at the end, the next row goes under the grid",
          (typed.get("body") or "").lower() == "costs\n| a | b |\n| c | d |", repr(typed.get("body")))


def days_in_the_note(folder):
    """A turned-off series' days live in one place, the note (the owner's
    choice, the final audit's sixth round). A notebook as the phone's 0.12.0
    left one -- the days in the series' copy alone -- has them back in the
    note at its next write; a series turned off from the Repeat sheet keeps
    a day's own text in the note, and a big delete after it makes a copy of
    words alone, the day still in the note: on the desktop that delete's copy
    was where the day's text was erased. Read back from the file."""
    print("\n-- a turned-off series' days, in the note --")
    days = {"overrides": {"2026-11-09": {"body": "Leg day - knee hurt"}}, "exceptions": ["2026-11-16"]}
    series = {"date": "2026-11-02", "recurrence": {"freq": "weekly", "interval": 1}}
    gym = dict({"id": 1, "uid": "dn-gym", "title": "Gym", "category": "General",
                "body": "Warm up.\nPhysio said: no squats for three weeks, swap in the bike.\nThen the plan.",
                "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:03:00"}, **series, **days)
    shipped = {"id": 2, "uid": "dn-shipped", "title": "Swim", "body": "Lengths.", "category": "General",
               "date": "2026-11-02", "recurrence": {"freq": ""}, "exceptions": [], "overrides": {}, "moved": {},
               "_prev": dict({"title": "Swim", "body": "Lengths.", "comments": "", "formatting": [], "paragraphs": [],
                              "overrides": {"2026-11-09": {"body": "Arm day - elbow"}}, "exceptions": [], "moved": {}},
                             **series),
               "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:02:00"}
    other = {"id": 3, "uid": "dn-other", "title": "Other", "body": "x", "category": "General",
             "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:01:00"}
    if not seed_inside([gym, shipped, other], {"welcome_offered": True}, "Gym"):
        return

    def note_said(uid):
        return note_by_uid(uid) or {}

    def day_text(note, day):
        return ((note.get("overrides") or {}).get(day) or {}).get("body")

    # Any write keeps what the read brought back into 0.12.0's note.
    open_note("Other")
    tap_last("x", wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text("y")
    back_to("Search notes")
    swim = settle(lambda: note_said("dn-shipped"), lambda n: day_text(n, "2026-11-09") is not None)
    check("a series' day 0.12.0 kept only in its copy is in the note, the copy keeping it too",
          day_text(swim, "2026-11-09") == "Arm day - elbow"
          and day_text(swim.get("_prev") or {}, "2026-11-09") == "Arm day - elbow", json.dumps(swim)[:300])

    # Turned off from the Repeat sheet: the day's own text stays in the note.
    open_note("Gym")
    tap("ⓘ", wait=1.2)
    tap("Every week", wait=1.5)
    tap("Does not repeat", exact=True, wait=0.8)
    tap("Save", exact=True, wait=0.3)
    said = not shot_as_said(folder, "118-repeat-off-kept", "Restore previous brings it back", "Note details")
    back_to("Search notes")
    off = settle(lambda: note_said("dn-gym"), lambda n: not (n.get("recurrence") or {}).get("freq"))
    check("a series turned off from the Repeat sheet keeps its day's own text in the note, and the series in its copy",
          said and not (off.get("recurrence") or {}).get("freq") and day_text(off, "2026-11-09") == "Leg day - knee hurt"
          and off.get("exceptions") == ["2026-11-16"]
          and ((off.get("_prev") or {}).get("recurrence") or {}).get("freq") == "weekly",
          "said %s, %s" % (said, json.dumps(off)[:300]))

    # A big delete after it: a copy of words alone, the day still in the note.
    open_note("Gym")
    tap("Physio said", wait=0.8)
    adb("shell", "input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_A")
    time.sleep(0.6)
    adb("shell", "input", "keyevent", "KEYCODE_DEL")
    time.sleep(1.5)
    back_to("Search notes")
    cut = settle(lambda: note_said("dn-gym"), lambda n: (n.get("body") or "") == "")
    copy = cut.get("_prev") or {}
    check("a big delete after the turn-off keeps a copy of words alone, the day's text still in the note",
          (cut.get("body") or "") == "" and "Physio said" in (copy.get("body") or "") and "overrides" not in copy
          and day_text(cut, "2026-11-09") == "Leg day - knee hurt", json.dumps(cut)[:300])


def template_draft(folder):
    """A template being written when Android ends the app behind Home: kept
    aside in the app's storage that Android's backup leaves out, and offered
    back -- "You were writing a template. Continue it?" -- Continue bringing
    every field back, the words left in A new item among them, and Save
    keeping them; Discard leaving no draft and the template as it was."""
    print("\n-- a template being written, kept as a draft --")
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("templates.json", [{"name": "Trip", "title": "Trip list", "body": "", "comments": "",
                                   "category": "General", "checklist": [{"text": "tickets", "done": False}]}])
    if not seed_inside([{"id": 1, "uid": "td-1", "title": "Living", "body": "x", "category": "General",
                         "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True}, "Living", clear=False):
        return

    def draft():
        data = adb("exec-out", "run-as", PACKAGE, "cat", "no_backup/template_draft.json", binary=True)
        try:
            return json.loads(data.decode("utf-8"))
        except ValueError:
            return None

    def ended_behind_home():
        return home() and kill_behind()

    def to_templates():
        start_app()
        says_soon("Search notes", within=10.0)
        tap("Settings", exact=True, wait=1.5)
        tap("Templates", exact=True, wait=1.5)

    # A new one: a name, a title, a row added, and words left in A new item.
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("+ New template", wait=1.5)
    tap("Template name", exact=True, wait=0.6)
    type_text("Packing")
    tap("Note title (optional)", wait=0.6)
    type_text("Weekend")
    keyboard_down()
    tap("A new item", wait=0.8)
    type_text("socks")
    tap("+ Add", exact=True, wait=0.8)
    tap("A new item", wait=0.8)
    type_text("Passport")
    gone = ended_behind_home()
    kept = draft() or {}
    fields = kept.get("fields") or {}
    check("Home with a template being written keeps it aside, in the storage Android's backup leaves out",
          gone and fields.get("name") == "Packing" and kept.get("waiting") == "Passport" and kept.get("edits") is None
          and [r.get("text") for r in fields.get("checklist") or []] == ["socks"]
          and not any(t.get("name") == "Packing" for t in read_file("templates.json") or []),
          "ended %s, %s" % (gone, json.dumps(kept)[:300]))
    to_templates()
    asked = not says_soon("You were writing a template. Continue it?", within=6.0)
    check("... and Settings > Templates asks about it, Continue and Discard said",
          asked and find("Continue", exact=True) is not None and find("Discard", exact=True) is not None, str(asked))
    shot(folder, "119-template-draft-asked", "Continue it?")
    # The question asked and Continue pressed, read: with neither, the editor
    # left open by an `am kill` that ended nothing passed this check (the
    # walk of 2026-10-30, the final audit's eighth round).
    continued = asked and tap("Continue", exact=True, wait=2.0)
    back_open = not screen_says("Packing", "Weekend", "socks", "Passport")
    check("Continue brings every field back, the words waiting in A new item among them", continued and back_open,
          "continued %s, missing %s" % (continued, screen_says("Packing", "Weekend", "socks", "Passport")))
    shot(folder, "120-template-draft-continued", "Passport")
    tap("Save", exact=True, wait=1.5)
    saved = next((t for t in read_file("templates.json") or [] if t.get("name") == "Packing"), {})
    check("... and Save keeps them, the draft gone",
          [r.get("text") for r in saved.get("checklist") or []] == ["socks", "Passport"]
          and saved.get("title") == "Weekend" and draft() is None, json.dumps(saved)[:200])

    # Editing one that exists, then Discard: nothing kept, the template as it was.
    tap("Trip", exact=True, wait=1.5)
    tap("Trip list", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(" changed")
    gone = ended_behind_home()
    kept = draft() or {}
    check("a template being edited is kept aside over the one it edits",
          gone and kept.get("edits") == "Trip" and (kept.get("fields") or {}).get("title") == "Trip list changed",
          json.dumps(kept)[:200])
    to_templates()
    asked_again = not says_soon("You were writing a template. Continue it?", within=6.0)
    discarded = asked_again and tap("Discard", exact=True, wait=1.5)
    trip = next((t for t in read_file("templates.json") or [] if t.get("name") == "Trip"), {})
    check("Discard leaves no draft, and the template as it was saved",
          discarded and draft() is None and trip.get("title") == "Trip list" and not screen_says("Trip")
          and screen_says("You were writing a template"), "discarded %s, %s" % (discarded, json.dumps(trip)[:200]))
    back_to("Search notes")


def limits_round7(folder):
    """The final audit's seventh round, walked. A checklist row of a million
    letters with no space is read in parts at once, its "Read all" and a
    search's stop in it no frame of seconds; a title, and a template's name,
    keep their own words at 200 letters, the key refused and said; a full
    list's box says it is full as words begin there; a second long paste
    into "A new item…" after Add is said again; a template being written
    taken off the screen by the app itself -- the reminder's notification
    tapped while the app is in front -- is kept as a draft and offered back;
    and a draft taken back by hand leaves none behind."""
    print("\n-- round seven's limits: a long unbroken row, the boxes' caps, the template the app closed --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r7-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def draft():
        data = adb("exec-out", "run-as", PACKAGE, "cat", "no_backup/template_draft.json", binary=True)
        try:
            return json.loads(data.decode("utf-8"))
        except ValueError:
            return None

    def keys(*names):
        adb("shell", "input", "keycombination", *("KEYCODE_" + n for n in names))
        time.sleep(1.0)

    def unfold(first_row):
        if screen_says(first_row) and find("CHECKLIST"):
            tap("CHECKLIST", wait=1.5)

    title_200 = "t" * 199 + "!"
    # Five million letters, as a share code of 7 KB brings: round seven's
    # slow search put back costs tens of seconds there, where on a million
    # it cost 0.4 to 3.3 s, about the check's line (the final audit's eighth).
    notes = [note(1, "Unbroken row", body="Row", checklist=[{"text": "a" * 5_000_000, "done": False},
                                                           {"text": "milk", "done": False}]),
             note(2, title_200),
             note(3, "Full list", body="Shop", checklist=[{"text": "item %d" % k, "done": False} for k in range(1_000)]),
             note(4, "Paste source", body="p" * 3_000),
             note(5, "Box note", body="Shop", checklist=[{"text": "bread", "done": False}])]
    if not seed_inside(notes, {"welcome_offered": True}, "Unbroken row"):
        return

    # A row of a million letters and no space: each part looked back to the
    # row's start for one, the square of the row read on the screen's thread.
    open_note("Unbroken row", limit=30.0)
    unfold("milk")
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    tapped = tap("Read all 5,000,000 characters", wait=0.3)
    # Read, the row says so over its parts ("Show less" is under the first
    # seven, far below the screen) -- and the frames read once it says so,
    # the slow frame ended (frames_once_shown).
    reading, frames = frames_once_shown("5,000,000 characters, shown here to read", within=60.0)
    longest = max(frames)
    check("Read all on a row of five million letters with no space reads it in parts at once, no frame of seconds",
          tapped and longest < 2.0 and reading == [], "longest frame %.2f s, missing %s" % (longest, reading))
    shot(folder, "121-unbroken-row-read", "Unbroken row")
    back_to("Search notes")

    # A title at 200 letters: a letter at its start refused, said, the title whole.
    open_note(title_200)
    tap(title_200[:40], wait=0.8)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    time.sleep(0.4)
    type_text("X")
    said_title = not says_soon("A note title can be up to 200 characters", within=6.0)
    back_to("Search notes")
    kept = (note_by_uid("r7-2") or {}).get("title")
    check("a letter typed at the start of a title of 200 letters is refused, said, the title whole",
          said_title and kept == title_200, "said %s, %r" % (said_title, (kept or "")[-10:]))

    # A full list's box says it is full as the words begin.
    open_note("Full list", limit=30.0)
    unfold("item 0")
    # Under the 205 rows drawn: scrolled to, as a person would.
    seek(lambda: find("A new item") is not None, tries=40, fast=True)
    tap("A new item", wait=0.8)
    type_text("Passport")
    full_said = not says_soon("A checklist holds at most 1000 items.", within=6.0)
    check("words typed into a full list's box are told it is full as they begin", full_said)
    # Back with the words still waiting: a full list takes no more, so the
    # note stays open and offers to leave anyway -- the tap fails if not.
    keyboard_down()
    back()
    tap("Leave anyway", exact=True, wait=1.5)
    back_to("Search notes")

    # Two long pastes into "A new item…", Add between them: each said.
    open_note("Paste source")
    tap("pppp", wait=0.8)
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "C")
    back_to("Search notes")
    open_note("Box note")
    unfold("bread")
    shortened = "Pasted text shortened: a checklist item can be up to 2,000 characters"
    tap("A new item", wait=0.8)
    keys("CTRL_LEFT", "V")
    # Read on the look that finds the words: drawn just above the keyboard,
    # the message lay over the box being typed in and its Add, and took
    # their taps for its four seconds (the final audit's ninth round).
    missing, covered = said_over(shortened, ("the box being typed in", lambda t, d: t.startswith("pppp")),
                                 ("its Add", lambda t, d: t == "Add"))
    first = not missing
    check("... the words said above the box being typed in and its Add, not over them",
          first and not covered, "missing %s, over %s" % (missing, covered))
    tap("Add", exact=True, wait=3.0)
    tap("A new item", wait=0.8)
    keys("CTRL_LEFT", "V")
    second = not says_soon(shortened, within=6.0)
    check("a second long paste into A new item after Add is said again, as the first was",
          first and second, "first %s, second %s" % (first, second))
    back_to("Search notes")

    # A template's name at 200 letters, then the reminder's notification
    # tapped with the editor open: the app opens the calendar, and the
    # template is kept as a draft, offered back.
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("+ New template", wait=1.5)
    tap("Template name", exact=True, wait=0.6)
    type_text("n" * 200)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_HOME")
    time.sleep(0.4)
    type_text("X")
    said_name = not says_soon("A template name can be up to 200 characters", within=6.0)
    check("a letter at the start of a template's name of 200 letters is refused, said over the editor", said_name)
    adb("shell", "am", "start", "-n", PACKAGE + "/.MainActivity", "-a", PACKAGE + ".TODAY")
    time.sleep(3.0)
    kept = draft() or {}
    check("the reminder's notification tapped while a template is written keeps it as a draft",
          (kept.get("fields") or {}).get("name") == "n" * 200 and find("Template name", exact=True) is None,
          json.dumps(kept)[:160])
    shot(folder, "122-template-closed-by-the-app", "Calendar")
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    asked = not says_soon("You were writing a template. Continue it?", within=6.0)
    check("... and Settings > Templates offers it back", asked)
    tap("Discard", exact=True, wait=1.5)

    # A template edited, the app sent away (a draft kept), the change taken
    # back by hand, the app sent away again: no draft stays to be offered.
    put_inside("templates.json", [{"name": "Trip", "title": "Trip list", "body": "", "comments": "",
                                   "category": "General", "checklist": []}])
    # Started again, to read it: the app is on the screen, where `am kill` ends nothing.
    adb("shell", "am", "force-stop", PACKAGE)
    start_app()
    says_soon("Search notes", within=10.0)
    tap("Settings", exact=True, wait=1.2)
    tap("Templates", exact=True, wait=1.2)
    tap("Trip", exact=True, wait=1.5)
    tap("Trip list", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text("x")
    home()
    written = (draft() or {}).get("fields", {}).get("title")
    start_app()
    time.sleep(1.5)
    adb("shell", "input", "keyevent", "KEYCODE_DEL")
    time.sleep(0.6)
    home()
    check("a draft whose change was taken back by hand is gone at the next trip to the background",
          written == "Trip listx" and draft() is None, "written %r, now %s" % (written, draft()))
    start_app()
    time.sleep(1.5)
    back_to("Search notes")


def limits_round8(folder):
    """The final audit's eighth round, walked. A category's name already
    used is refused and said over its sheet, which stays open -- said in the
    window under it, Save looked as if it did nothing; a title holding a line
    break takes the letters typed into it, and a tap on it writes nothing;
    Your name and Save as template's name say a paste was cut, under the box;
    the title's counter counts 170 emoji as 170; and on a phone on its side
    the note's details scroll to their foot, and the Repeat sheet's word that
    a turn-off keeps the days is seen over them. Each read back from the
    screen or the file."""
    print("\n-- round eight's limits: refusals over a sheet, a title holding a break, the caps' words, the details on their side --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r8-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def keys(*names):
        adb("shell", "input", "keycombination", *("KEYCODE_" + n for n in names))
        time.sleep(1.0)

    gym = note(4, "Gym", body="Warm up.", date="2026-11-02", recurrence={"freq": "weekly", "interval": 1},
               overrides={"2026-11-09": {"body": "Leg day - knee hurt"}}, exceptions=[])
    notes = [note(1, "Groceries\n", body="Milk"), note(2, "😀" * 170), note(3, "Paste source", body="p" * 3_000), gym]
    if not seed_inside(notes, {"welcome_offered": True}, "Paste source"):
        return

    # A category's name already used: refused, and said over the sheet.
    tap("Settings", exact=True, wait=1.2)
    tap("Categories", exact=True, wait=1.2)
    tap("Add a category", wait=1.5)
    tap("Name", exact=True, wait=0.8)
    type_text("General")
    tap("Save", exact=True, wait=0.3)
    said = not shot_as_said(folder, "123-category-refused-over-its-sheet", "There is already a category called",
                            over_keys=True)
    stays = not screen_says("Add a category", "Name")
    check("a category's name already used is refused and said over its sheet, which stays open",
          said and stays, "said %s, the sheet open %s" % (said, stays))
    back_to("Your name")
    back_to("Search notes")

    # Three thousand letters to paste.
    open_note("Paste source")
    tap("pppp", wait=0.8)
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "C")
    back_to("Search notes")

    # Your name and Save as template's name: a paste cut, said under the box.
    tap("Settings", exact=True, wait=1.2)
    tap("Your name", wait=1.2)
    tap("Name", exact=True, wait=0.8)
    keys("CTRL_LEFT", "V")
    name_said = not says_soon("Pasted text shortened: your name can be up to 80 characters", within=6.0)
    check("a paste cut to Your name's 80 letters is said under the box", name_said)
    shot(folder, "124-your-name-cap-said", "Pasted text shortened")
    keyboard_down()
    back_to("Search notes")
    open_note("Paste source")
    tap("⋮", exact=True)
    tap("🧩 Save as template", wait=1.2)
    tap("Template name", exact=True, wait=0.8)
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "V")
    template_said = not says_soon("Pasted text shortened: a template name can be up to 200 characters", within=6.0)
    check("a paste cut to a template name's 200 letters is said under the box, in the dialog", template_said)
    shot(folder, "125-template-name-cap-said", "Pasted text shortened")
    keyboard_down()
    tap("Cancel", exact=True, wait=1.0)
    back_to("Search notes")

    # The title's counter, in letters: 170 emoji are 170, not 340.
    open_note("😀😀😀")
    counted = not says_soon("170/200", within=6.0)
    check("a title of 170 emoji is counted 170 of 200, as the desktop counts it", counted,
          str(screen_says("170/200", "340/200")))
    back_to("Search notes")

    # A title holding a line break, as the phone before 0.11 left one: a tap
    # on it writes nothing; a letter typed after the break is kept.
    open_note("Groceries")
    tap("Groceries", wait=2.0)
    back_to("Search notes")
    held = settle(lambda: note_by_uid("r8-1") or {}, lambda n: True, first=1.0)
    check("a tap on a title holding a break writes nothing",
          held.get("title") == "Groceries\n" and held.get("updated") == "2026-10-20 09:00", json.dumps(held)[:160])
    open_note("Groceries")
    tap("Groceries", wait=1.0)
    keys("CTRL_LEFT", "MOVE_END")
    type_text(" list")
    back_to("Search notes")
    typed = settle(lambda: note_by_uid("r8-1") or {}, lambda n: (n.get("title") or "").endswith("list"))
    check("letters typed after a title's break go into the title, none into the note's words",
          typed.get("title") == "Groceries  list" and typed.get("body") == "Milk", json.dumps(typed)[:160])

    # On its side: the details scroll to their foot, and the Repeat sheet's
    # word that a turn-off keeps the days is seen over them. Opened upright:
    # on its side the list shows two notes, and Gym is the fourth.
    try:
        open_note("Gym")
        turn(1)
        tap("ⓘ", wait=1.2)
        tap("Every week", wait=1.5)
        tap("Does not repeat", exact=True, wait=0.8)
        tap("Save", exact=True, wait=0.3)
        off_said = not shot_as_said(folder, "126-details-on-its-side", "The repeat is off", "Note details")
        check("on a phone on its side, the turn-off's word is seen over the note's details", off_said)
        foot = False
        for _ in range(4):
            if find("2 words"):
                foot = True
                break
            adb("shell", "input", "swipe", "1200", "900", "1200", "350", "400")
            time.sleep(1.0)
        check("... and the details scroll to their foot, the word count", foot)
    finally:
        turn(0)
    back_to("Search notes")


def limits_round10(folder):
    """The final audit's tenth round, walked. A message said while a
    checklist row is renamed stands above the row and its ✕: the row is
    brought just above the keyboard, where the window's messages stood, and
    one stood over it and took the taps meant for its ✕. Read back from the
    screen, on the look that finds the words."""
    print("\n-- round ten's limits: a message above the row being renamed --")

    def note(i, title, **fields):
        n = {"id": i, "uid": "r10-%d" % i, "title": title, "body": "x", "category": "General",
             "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}
        n.update(fields)
        return n

    def keys(*names):
        adb("shell", "input", "keycombination", *("KEYCODE_" + n for n in names))
        time.sleep(1.0)

    words = "\n".join("Line %d of the note's own words." % k for k in range(1, 61))
    notes = [note(1, "Paste source", body="p" * 3_000),
             note(2, "Low rows", body=words, checklist=[{"text": "bread", "done": False},
                                                         {"text": "milk", "done": False}])]
    if not seed_inside(notes, {"welcome_offered": True}, "Paste source"):
        return

    # Three thousand letters to paste.
    open_note("Paste source")
    tap("pppp", wait=0.8)
    keys("CTRL_LEFT", "A")
    keys("CTRL_LEFT", "C")
    back_to("Search notes")

    # The rows under sixty lines of words: renamed, the row is brought just
    # above the keys, and a paste past its cap is said there.
    open_note("Low rows")
    # Scrolled to the note's foot: the last row lies low, under where the
    # keyboard rises, and is brought up just above its keys when tapped.
    seek(lambda: False, tries=12, fast=True)
    tap("milk", exact=True, wait=1.5)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    time.sleep(0.4)
    keys("CTRL_LEFT", "V")
    shortened = "Pasted text shortened: a checklist item can be up to 2,000 characters"
    missing, covered = said_over(shortened, ("the row being renamed", lambda t, d: t.startswith("milk")),
                                 ("its remove button", lambda t, d: "Remove" in d and "milk" in d),
                                 touching=True)
    photograph(folder, "127-message-above-the-row-renamed")
    check("a message said while a row is renamed stands above the row and its ✕, not over them",
          not missing and not covered, "missing %s, over %s" % (missing, covered))
    keyboard_down()
    back_to("Search notes")


def calendar_large_letters(folder):
    """Today's cell, holding a note and a marker, at the phone's largest
    letters: the cell grows, where at a fixed 56 dp the marker was cut in
    half and the note's dot pushed out of sight. Read from the cell's box in
    the UI tree; the photograph shows the dot."""
    print("\n-- the calendar at the largest letters --")
    import datetime
    today = datetime.date.today()
    if not seed_inside([{"id": 1, "uid": "cl-1", "title": "Dentist", "date": today.isoformat(),
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True, "day_emoji": {today.isoformat(): ["⭐"]}}, "Dentist"):
        return
    try:
        adb("shell", "settings", "put", "system", "font_scale", "2.0")
        time.sleep(2.5)
        tap("Calendar", exact=True, wait=2.0)
        box = bounds_of_desc(said(today))
        density = int((re.findall(r"\d+", adb("shell", "wm", "density")) or ["420"])[-1]) / 160.0
        check("at the largest letters a day's cell grows past 56 dp to hold its marker and dot",
              box is not None and (box[3] - box[1]) > 56 * density + 4, "%s at %.2f px a dp" % (box, density))
        shot(folder, "90-calendar-large-letters", "Calendar")
    finally:
        adb("shell", "settings", "put", "system", "font_scale", "1.0")
        time.sleep(1.5)
    back_to("Search notes")


def bounds_of_desc(start):
    """The box of the first node whose description starts with [start]."""
    raw = screen_xml()
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return None
    for node in root.iter("node"):
        if node.get("content-desc", "").startswith(start):
            box = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
            if len(box) == 4:
                return box
    return None


def node_attrs(label):
    """The state of the control whose text or description is exactly
    [label] -- checkable, checked, selected: what a screen reader is told,
    which nodes() leaves out. The words sit on a child of the control in the
    tree uiautomator dumps (a screen reader hears them merged), so the
    nearest clickable node holding them is the one read."""
    raw = screen_xml()
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return {}

    def search(node, clickable):
        here = node if node.get("clickable") == "true" else clickable
        if label in (node.get("text", ""), node.get("content-desc", "")):
            return dict((here if here is not None else node).attrib)
        for child in node:
            found = search(child, here)
            if found:
                return found
        return {}
    return search(root, None)


def states_said(folder):
    """What is on, said and not only tinted: a list key on in a list line,
    the sort in force in its menu, a swatch by its place and value. Read from
    the UI tree's own checked and selected, what a screen reader reads; the
    tint's own contrast is the palette's, not checked here."""
    print("\n-- what is on, said --")
    if not seed_inside([{"id": 1, "uid": "st-1", "title": "Shopping", "body": "Today\n• milk",
                         "paragraphs": [{"line": 1, "kind": "bullet"}],
                         "category": "General", "created": "2026-10-20 09:00", "updated": "2026-10-20 09:00"}],
                       {"welcome_offered": True, "note_sort": "Title A–Z"}, "Shopping"):
        return
    attrs = node_attrs("Sort: Title A–Z")
    check("the sort pill says its words, not the arrow's", bool(attrs), str(attrs)[:120])
    tap("Sort: Title A–Z", wait=1.0)
    attrs = node_attrs("Title A–Z")
    # Compose tells Android "selected" as checked, outside a tab.
    check("the sort in force is said selected in its menu",
          "true" in (attrs.get("selected"), attrs.get("checked")), str(attrs)[:200])
    attrs = node_attrs("Custom order")
    check("... and another is not", attrs.get("checked") == "false" and attrs.get("selected") == "false",
          str(attrs)[:200])
    back(wait=0.8)
    if open_note("Shopping"):
        tap("milk", wait=1.0)
        attrs = node_attrs("Bullet list")
        check("in a list line the bullet key is said ticked",
              attrs.get("checkable") == "true" and attrs.get("checked") == "true", str(attrs)[:200])
        attrs = node_attrs("Bold")
        check("B is said as a checkbox, not ticked", attrs.get("checkable") == "true"
              and attrs.get("checked") == "false", str(attrs)[:200])
        keyboard_down()
        tap("ⓘ", exact=True, wait=1.2)
        said = [d for t, d, _ in nodes() if d.startswith("Colour ") and " of " in d]
        check("a colour swatch says its place and its value", bool(said),
              " | ".join(d for t, d, _ in nodes() if d)[:200])
        back(wait=0.8)
    back_to("Search notes")


def arrange_without_drag(folder):
    """Custom order's cards and a checklist's rows moved one place without
    a drag: a tap on the handle offers Move up and Move down, the same moves
    a screen reader and Switch Access find among the card's or the
    handle's actions (which no adb command can press, so those are named
    here and not pressed). Read back from the file."""
    print("\n-- arranging without a drag --")
    if not seed_inside([
        {"id": 1, "uid": "nd-a", "title": "Alpha", "order": 0, "checklist": [
            {"text": "flour", "done": False}, {"text": "yeast", "done": False}],
         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"},
        {"id": 2, "uid": "nd-b", "title": "Bravo", "order": 1,
         "created": "2026-10-02 09:00:00", "updated": "2026-10-02 09:00:00"},
    ], {"welcome_offered": True, "note_sort": "Custom order"}, "Alpha"):
        return

    def placed():
        notes = read_file("notes_data.json") or []
        return [n.get("title") for n in sorted(notes, key=lambda n: n.get("order", 99))]

    tap("Drag to move Alpha", wait=1.0)
    shot(folder, "89-move-menu", "Move down")
    check("a tap on a card's handle offers to move it, with no way up from the top",
          find("Move down", exact=True) is not None and find("Move up", exact=True) is None)
    tap("Move down", exact=True, wait=1.5)
    check_settled("Move down puts the card below its neighbour", placed, lambda p: p == ["Bravo", "Alpha"])
    if open_note("Alpha"):
        tap("Drag to move flour", wait=1.0)
        tap("Move down", exact=True, wait=1.5)
        check_settled("a checklist row's handle moves it down a place",
                      lambda: [r.get("text") for r in (note_by_uid("nd-a") or {}).get("checklist", [])],
                      lambda rows: rows == ["yeast", "flour"])
        # A row's ✕ says what it took, and Undo puts it back where it was.
        tap("Remove yeast from the checklist", wait=0.5)
        # The row gone first: an Undo that came back to an unchanged list passed.
        check("... the ✕ takes the row off", not find("Remove yeast from the checklist"))
        tap_last("Undo", wait=1.5)       # the bar's, not the editor's ↶, also called Undo
        check_settled("a row taken off by its ✕ comes back with Undo, in its place",
                      lambda: [r.get("text") for r in (note_by_uid("nd-a") or {}).get("checklist", [])],
                      lambda rows: rows == ["yeast", "flour"])
    back_to("Search notes")
    # A list longer than the screen: a card stepped past the top card went
    # above the screen, the list following the top card by key (round 16).
    if not seed_inside([{"id": i, "uid": "nd-l%d" % i, "title": "Note %02d" % i, "order": i,
                         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"}
                        for i in range(1, 31)],
                       {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
        return
    tap("Drag to move Note 02", wait=1.0)
    tap("Move up", exact=True, wait=1.5)
    check("on a long list, the second card moved up is on screen, first",
          placed()[:2] == ["Note 02", "Note 01"] and not says_soon("Note 02", "Note 01"), repr(placed()[:3]))
    tap("Drag to move Note 02", wait=1.0)
    tap("Move down", exact=True, wait=1.5)
    check("...and the top card moved down shows below the one it passed, both on screen",
          placed()[:2] == ["Note 01", "Note 02"] and not says_soon("Note 01", "Note 02"), repr(placed()[:3]))
    # A card's handle tapped again after a step: its gesture read the list as
    # at its first touch, and the tap drew -- or wrote -- that order (round 17).
    tap("Drag to move Note 04", wait=1.0)
    tap("Move up", exact=True, wait=1.5)
    tap("Drag to move Note 04", wait=1.5)
    adb("shell", "input", "keyevent", "4")              # its menu put away
    time.sleep(1.0)
    check_settled("a card's handle tapped again after a step leaves the step as it is",
                  placed, lambda p: p[:4] == ["Note 01", "Note 02", "Note 04", "Note 03"])

    # Mid-list, the card at the top of the screen stepped up, and the card
    # just above the + button stepped down: held at the index, the first went
    # above the screen and the second below the + (round 17).
    def handles():
        return sorted((centre[1], desc[len("Drag to move "):]) for _t, desc, centre in nodes()
                      if desc.startswith("Drag to move Note "))

    # A step menu open as the phone turns closes with its card, which the
    # shorter screen leaves out -- and it came back open by itself once the
    # card scrolled back into sight (round 22).
    shown = handles()
    low = shown[-2][1] if len(shown) > 1 else None
    if low:
        tap("Drag to move " + low, wait=1.0)
        turn(1)
        left_out = find("Drag to move " + low) is None
        for _ in range(8):
            if find("Drag to move " + low):
                break
            adb("shell", "input", "swipe", "600", "850", "600", "550", "300")
            time.sleep(1.0)
        back_in = find("Drag to move " + low) is not None
        reopened = find("Move down", exact=True) is not None or find("Move up", exact=True) is not None
        if reopened:
            adb("shell", "input", "keyevent", "4")
            time.sleep(0.8)
        turn(0)
        check("a step menu whose card the turned screen leaves out is not open again when the card comes back",
              left_out and back_in and not reopened,
              "left out %s, back in %s, menu open %s" % (left_out, back_in, reopened))
    else:
        fail("the step menu through a turn: no card's handle low on the screen")
    adb("shell", "input", "swipe", "540", "1700", "540", "1150", "400")
    time.sleep(1.2)
    shown = handles()
    if not shown:
        fail("mid-list: no card's handle on the screen")
        return
    top_card = shown[0][1]
    was = placed().index(top_card)
    tap("Drag to move " + top_card, wait=1.0)
    tap("Move up", exact=True, wait=2.0)
    order = placed()
    check("mid-list, the card at the top of the screen moved up is still on screen",
          order.index(top_card) == was - 1 and not says_soon(top_card), repr(order[:12]))
    # A press held still on the top card's handle: the list scrolled under it
    # at the edge, and the note moved with no move of the finger (round 17).
    shown, before = handles(), placed()
    at = find("Drag to move " + shown[0][1]) if shown else None
    if at:
        adb("shell", "input", "motionevent", "DOWN", str(at[0]), str(at[1]))
        time.sleep(1.5)
        adb("shell", "input", "motionevent", "UP", str(at[0]), str(at[1]))
        time.sleep(1.0)
        adb("shell", "input", "keyevent", "4")          # its menu put away
        time.sleep(0.8)
    check("a press held still on the top card's handle moves nothing", at is not None and placed() == before,
          repr(placed()[:12]))
    plus = find("New note", exact=True)
    picked = None
    for _ in range(5):
        shown = handles()
        under = [i for i, (y, _n) in enumerate(shown) if plus and y < plus[1] - 60]
        if under and under[-1] + 1 < len(shown) and shown[under[-1] + 1][0] >= plus[1]:
            picked = shown[under[-1]][1]
            break
        adb("shell", "input", "swipe", "540", "1500", "540", "1400", "300")
        time.sleep(1.0)
    if not picked:
        fail("mid-list: no card just above the + button with the next one under it")
        return
    tap("Drag to move " + picked, wait=1.0)
    tap("Move down", exact=True, wait=2.0)
    after = dict((name, y) for y, name in handles())
    check("...and the card just above the + button moved down stays in sight above it",
          picked in after and plus is not None and after[picked] < plus[1], "%s at %s, the + at %s" % (picked, after.get(picked), plus))

    # The last card on screen, nothing under it in sight, stepped down: out of
    # the layout, it was taken to the top of the screen, a screen's jump, and
    # the card it passed went above (round 18).
    last = None
    for _ in range(8):
        shown = handles()
        order = placed()
        if shown and plus:
            y, name = shown[-1]
            nxt = order[order.index(name) + 1] if order.index(name) + 1 < len(order) else None
            clear_of_plus = abs(y - plus[1]) > 110
            if nxt and clear_of_plus and screen_says(nxt) == [nxt]:
                last, passed_by = name, nxt
                break
        adb("shell", "input", "swipe", "540", "1500", "540", "1440", "300")
        time.sleep(1.0)
    if not last:
        fail("mid-list: no last card on screen with nothing under it in sight, clear of the +")
        return
    tap("Drag to move " + last, wait=1.0)
    tap("Move down", exact=True, wait=2.0)
    after = dict((name, y) for y, name in handles())
    check("the last card on screen moved down shows just under the card it passed, both in sight",
          last in after and not screen_says(passed_by) and placed().index(last) == placed().index(passed_by) + 1,
          "%s, %s: %s" % (last, passed_by, sorted(after.items(), key=lambda kv: kv[1])[:6]))

    # The top card on screen dragged one place down: the edge scroll took the
    # list up first, against the finger, and the drag landed nowhere (round 18).
    shown, pill = handles(), find("Sort: Custom order")
    # The first card whose handle the list shows whole: its top within the
    # top edge's reach, below the pills.
    reachable = [h for h in shown if pill and h[0] > pill[1] + 120]
    if len(reachable) >= 3 and reachable[0] == shown[0]:
        (y0, top_name), (y1, _second), (y2, _third) = reachable[:3]
        was = placed().index(top_name)
        x = find("Drag to move " + top_name)[0]
        # A whole card's height and more: a handle cut by the top edge is
        # centred on what shows of it, nearer the next card than it is.
        to = y0 + (y2 - y1) + 60
        adb("shell", "input", "motionevent", "DOWN", str(x), str(y0))
        for y in range(y0, to, 30):
            adb("shell", "input", "motionevent", "MOVE", str(x), str(y))
        time.sleep(0.8)
        adb("shell", "input", "motionevent", "UP", str(x), str(to))
        check_settled("the top card on screen dragged one place down lands one place down",
                      lambda: (placed().index(top_name), was, top_name, shown[:3], pill),
                      lambda got: got[0] == was + 1)
        # A tap whose finger shifts a few pixels is still a tap: any shift was
        # a drag, and the handle's menu never opened (round 19).
        shown = handles()
        tap_name = shown[1][1] if len(shown) > 1 else None
        at = find("Drag to move " + tap_name) if tap_name else None
        if at:
            adb("shell", "input", "motionevent", "DOWN", str(at[0]), str(at[1]))
            adb("shell", "input", "motionevent", "MOVE", str(at[0]), str(at[1] + 3))
            adb("shell", "input", "motionevent", "UP", str(at[0]), str(at[1] + 3))
            time.sleep(1.2)
        menu_open = find("Move down", exact=True) is not None or find("Move up", exact=True) is not None
        check("a tap on a handle with a few pixels' shift opens its menu", bool(at) and menu_open, tap_name)
        if menu_open:
            adb("shell", "input", "keyevent", "4")          # its menu put away
            time.sleep(0.8)
        # The top card nudged down, a hair's recoil, then held: the recoil
        # read as a turn, the list scrolled up against the finger and the note
        # was saved places ABOVE where it was (round 19).
        shown, before = handles(), placed()
        reachable = [h for h in shown if pill and h[0] > pill[1] + 120]
        if reachable and reachable[0] == shown[0]:
            y0, name = reachable[0]
            x = find("Drag to move " + name)[0]
            adb("shell", "input", "motionevent", "DOWN", str(x), str(y0))
            for y in range(y0, y0 + 56, 11):                 # down to y0 + 55
                adb("shell", "input", "motionevent", "MOVE", str(x), str(y))
            adb("shell", "input", "motionevent", "MOVE", str(x), str(y0 + 52))   # 3 px back
            time.sleep(1.5)
            adb("shell", "input", "motionevent", "UP", str(x), str(y0 + 52))
            check_settled("the top card nudged down, a hair back and held, moves nothing",
                          lambda: placed()[:20], lambda p: p == before[:20])
        else:
            fail("mid-list: the top card's handle not whole on the screen for the nudge: %s" % shown[:3])
        # The top card drawn down, put back where it was pressed and held: the
        # turn back scrolled the list under it, and the note was saved a place
        # or two up (round 20).
        shown, before = handles(), placed()
        reachable = [h for h in shown if pill and h[0] > pill[1] + 120]
        if reachable and reachable[0] == shown[0]:
            y0, name = reachable[0]
            x = find("Drag to move " + name)[0]
            adb("shell", "input", "motionevent", "DOWN", str(x), str(y0))
            for y in range(y0, y0 + 166, 15):                # down 165 px, short of a trade
                adb("shell", "input", "motionevent", "MOVE", str(x), str(y))
            for y in range(y0 + 150, y0 - 1, -15):           # back to the press...
                adb("shell", "input", "motionevent", "MOVE", str(x), str(y))
            adb("shell", "input", "motionevent", "MOVE", str(x), str(y0 - 4))    # ...a hair over
            time.sleep(1.2)
            adb("shell", "input", "motionevent", "UP", str(x), str(y0 - 4))
            check_settled("the top card drawn down, put back and held, moves nothing",
                          lambda: placed()[:20], lambda p: p == before[:20])
        else:
            fail("mid-list: the top card's handle not whole on the screen to put back: %s" % shown[:3])
        # A press the system cancels -- a Back swipe from the edge across a
        # handle, a call -- read as a tap: the menu opened by itself (round 25).
        shown, before = handles(), placed()
        at = find("Drag to move " + shown[1][1]) if len(shown) > 1 else None
        if at:
            adb("shell", "input", "motionevent", "DOWN", str(at[0]), str(at[1]))
            adb("shell", "input", "motionevent", "CANCEL", str(at[0]), str(at[1]))
            time.sleep(1.2)
        check("a handle's press the system cancels opens no menu and moves nothing",
              bool(at) and find("Move down", exact=True) is None and find("Move up", exact=True) is None
              and placed()[:20] == before[:20])
        if find("Move down", exact=True) or find("Move up", exact=True):
            adb("shell", "input", "keyevent", "4")
            time.sleep(0.8)
        # A fling stopped by a tap on a handle: the tap only stops it, and the
        # next tap there opens the menu. Read at the handle's last lift, the
        # first opened it and the second did nothing (the lows wave's round 1).
        if at:
            def menu_up():
                return find("Move down", exact=True) is not None or find("Move up", exact=True) is not None
            # Where a handle lands after a fling cannot be read while it runs:
            # a tap on a card's words opens the note instead, and another
            # height is tried.
            outcome = None
            for y in (1300, 1370, 1230, 1440):
                adb("shell", "input", "swipe", "540", "1900", "540", "700", "80")      # a fling
                adb("shell", "input", "tap", str(at[0]), str(y))
                time.sleep(1.2)
                first = not menu_up()
                adb("shell", "input", "tap", str(at[0]), str(y))
                time.sleep(1.2)
                if find("ⓘ", exact=True):
                    back_to("Search notes")
                    continue
                outcome = (first, menu_up())
                if menu_up():
                    adb("shell", "input", "keyevent", "4")
                    time.sleep(0.8)
                break
            check("a tap on a handle that stops a fling only stops it, and the next tap opens the menu",
                  outcome == (True, True), "the first opened nothing, the second the menu: %s" % (outcome,))
    else:
        fail("mid-list: the top card's handle not whole on the screen for the drag: %s, the pills at %s"
             % (shown[:3], pill))
    back_to("Search notes")
    # A phone on its side: the top card stepped down went under the + button,
    # brought into sight with the card it passed, the two taller than the
    # list (round 20).
    if seed_inside([{"id": i, "uid": "nd-s%d" % i, "title": "Note %02d" % i, "order": i,
                     "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"}
                    for i in range(1, 31)],
                   {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
        turn(1)
        tap("Drag to move Note 01", wait=1.0)
        tap("Move down", exact=True, wait=2.0)
        shot(folder, "89-landscape-step", "Note 01")
        shown, plus = handles(), find("New note", exact=True)
        at = find("Drag to move Note 01")
        check("on its side, the top card moved down stays on top, clear of the + button",
              placed()[:2] == ["Note 02", "Note 01"] and bool(shown) and shown[0][1] == "Note 01"
              and at is not None and plus is not None and at[1] < plus[1] - 80,
              "%s, Note 01 at %s, the + at %s" % (shown[:3], at, plus))
        # ...and a drag of one place there: every resting card sat in an
        # edge zone, the list slid at once, and the note landed two or three
        # places down (round 22).
        if seed_inside([{"id": i, "uid": "nd-s%d" % i, "title": "Note %02d" % i, "order": i,
                         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"}
                        for i in range(1, 31)],
                       {"welcome_offered": True, "note_sort": "Custom order"}, "Note 01"):
            shown = handles()
            if len(shown) >= 2 and shown[0][1] == "Note 01":
                (y0, _first), (y1, _second) = shown[:2]
                x = find("Drag to move Note 01")[0]
                h = y1 - y0
                adb("shell", "input", "motionevent", "DOWN", str(x), str(y0))
                for y in (y0 + h * 2 // 5, y0 + h * 4 // 5, y0 + h + 40):   # a quick drag
                    adb("shell", "input", "motionevent", "MOVE", str(x), str(y))
                time.sleep(0.3)
                adb("shell", "input", "motionevent", "UP", str(x), str(y0 + h + 40))
                check_settled("on its side, a card dragged one place down lands one place down",
                              lambda: placed()[:3], lambda p: p == ["Note 02", "Note 01", "Note 03"])
            else:
                fail("on its side: Note 01 and the next card's handles not on the screen: %s" % shown[:3])
        turn(0)
        back_to("Search notes")


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

    # A file gone from the folder, tapped: said, as the desktop says it -- it
    # opened nothing, without a word (wave 2, round 5).
    adb("shell", "run-as", PACKAGE, "rm", "%s/attachments/%s" % (NOTEBOOK, stored))
    tap("📎  walk-receipt.txt", exact=True, wait=0.5)
    check("a file gone from the folder, tapped, is said missing", not says_soon("can't be found"))

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

    # This app is left off its own share sheet: a code sent to itself opened
    # the import of notes already here (wave 2, round 9).
    ask("Trip")
    tap("Share code", exact=True, wait=1.0)
    sheet = handed()
    shot(folder, "60d-share-sheet-without-the-app")
    check("the phone's share sheet does not offer this app", sheet != PACKAGE
          and find("Note Organizer", exact=True) is None, sheet)
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
        # Longer than a PDF holds: since round four the first share asks
        # before it hands anything over (limits_round4 walks the question).
        if settle(lambda: find("PDF, first 40 pages", exact=True), lambda at: at is not None, within=20.0, first=0):
            tap("PDF, first 40 pages", exact=True, wait=0.3)
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


def paste_at_marker(folder):
    """Copied items pasted where Enter in a list leaves the cursor -- a new
    empty item -- and a whole item with its line break pasted right after an
    item's number: the item kept its number and the paste brought its own,
    "3. 1. Kiwi" (the lows wave's verification, rounds 8 and 9). The items
    are copied as words from a note's comments, the plain field a walk can
    fill. The cursor is placed by Ctrl+A and Left or Right, which close the
    selection at the start or the end, then Right a counted number of times:
    Up and Down, sent by adb, did not move it at all, Ctrl+Home neither, and
    where a tap lands depends on the screen (the lows wave's walk, 2026-11-02)."""
    print("\n-- items pasted at an item's number --")
    if not seed_inside([
        {"id": 1, "uid": "pm-two", "title": "Two items", "body": "words", "comments": "1. Kiwi\n2. Plum",
         "created": "2026-11-02 09:00:00", "updated": "2026-11-02 09:00:00"},
        {"id": 2, "uid": "pm-one", "title": "One item", "body": "words", "comments": "1. Fig\n",
         "created": "2026-11-02 09:00:01", "updated": "2026-11-02 09:00:01"},
        {"id": 3, "uid": "pm-list", "title": "Shop list", "body": "Shop\n1. Milk\n2. Bread",
         "paragraphs": [{"line": 1, "kind": "number"}, {"line": 2, "kind": "number"}],
         "created": "2026-11-02 09:00:02", "updated": "2026-11-02 09:00:02"},
    ], {"welcome_offered": True}, "Two items"):
        return

    def keys(*codes):
        adb("shell", "input", "keycombination", *map(str, codes))
        time.sleep(1.0)

    def copy_comments(title, words):
        open_note(title)
        tap(words, wait=0.8)
        keys(113, 29)                              # Ctrl+A: the comments' words
        keys(113, 31)                              # Ctrl+C
        keyboard_down()
        back_to("Search notes")

    def body():
        return (note_by_uid("pm-list") or {}).get("body") or ""

    copy_comments("Two items", "Plum")
    open_note("Shop list")
    tap("Bread", wait=0.8)
    keys(113, 29)                                  # Ctrl+A
    adb("shell", "input", "keyevent", "22")        # Right: the note's end
    time.sleep(0.4)
    adb("shell", "input", "keyevent", "66")        # Enter: a new empty item
    time.sleep(0.8)
    keys(113, 50)                                  # Ctrl+V
    keyboard_down()
    check_settled("copied items pasted into the new empty item fill it, one number each",
                  body, lambda b: b == "Shop\n1. Milk\n2. Bread\n3. Kiwi\n4. Plum")
    shot(folder, "72c-pasted-into-new-item", "Kiwi", "Plum")
    back_to("Search notes")

    copy_comments("One item", "Fig")
    open_note("Shop list")
    tap("Milk", wait=0.8)
    keys(113, 29)                                  # Ctrl+A
    adb("shell", "input", "keyevent", "21")        # Left: the note's start
    for _ in range(len("Shop\n1. ")):
        adb("shell", "input", "keyevent", "22")    # Right, past "1. Milk"'s number
    time.sleep(0.4)
    keys(113, 50)                                  # Ctrl+V
    keyboard_down()
    check_settled("a whole item with its line break pasted right after an item's number goes in before it",
                  body, lambda b: b == "Shop\n1. Fig\n2. Milk\n3. Bread\n4. Kiwi\n5. Plum")
    shot(folder, "72d-pasted-before-item", "Fig", "Milk")
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
    raw = screen_xml()
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
    raw = screen_xml()
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
    # A checklist, or comments, asked for from ⋮ on a new note stay through
    # its first save. Kept by the id it gets there, they went, and the words
    # waiting in A new item went out of sight with them (round 14).
    tap("+", exact=True, wait=1.5)
    tap("⋮", exact=True, wait=1.0)
    tap("☑ Add a checklist", exact=True, wait=1.5)
    type_text("Mil")
    tap("Title", wait=0.8)
    type_text("Shop")
    time.sleep(2.0)
    made = [n for n in read_file("notes_data.json") or [] if n.get("title") == "Shop"]
    check("a checklist asked for on a new note stays through its first save, the words in A new item with it",
          not says_soon("Mil") and len(made) == 1 and not made[0].get("checklist"), repr(made))
    keyboard_down()
    back_to("Search notes")
    tap("+", exact=True, wait=1.5)
    tap("⋮", exact=True, wait=1.0)
    tap("💬 Add comments", exact=True, wait=1.5)
    tap("Title", wait=0.8)
    type_text("Trip")
    time.sleep(2.0)
    check("comments asked for on a new note stay through its first save",
          # The label as the screen draws it: capitals.
          not says_soon("COMMENTS") and any(n.get("title") == "Trip" for n in read_file("notes_data.json") or []))
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
    raw = screen_xml()
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
    raw = screen_xml()
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


def kept_aside(folder):
    """A big delete after a share code's Update: the Update's copy -- the
    note as it was before the Update, in no other file -- stays, and the
    words the delete took become a note of their own, said with Show; typed
    back, that note goes again (the desktop's wave 2; a big delete replaced
    the copy here until the lows wave found it)."""
    print("\n-- the words a big delete takes beside an Update's copy --")
    words = "Book the physio for Friday, then the bike at seven, then the plan for the knee"
    # A checklist row the note kept aside does not take: a search for it
    # finds the note and hides that one, for the wide screen below.
    rows = [{"text": "passport", "done": False}]
    if not seed_inside([{"id": 1, "uid": "ka-trip", "title": "Trip", "body": "Mine before", "checklist": rows,
                         "created": "2026-10-01 09:00:00", "updated": "2026-10-01 09:00:00"}],
                       {"welcome_offered": True}, "Trip"):
        return
    code = desktop_code({"uid": "ka-trip", "title": "Trip", "body": words, "category": "General",
                         "checklist": rows, "updated": "2026-10-15 10:00:00"})
    if not code:
        fail("the words kept aside: the desktop's encoder could not be used, so nothing was tried")
        return
    adb("shell", "am", "start", "-a", "android.intent.action.SEND", "-t", "text/plain",
        "--es", "android.intent.extra.TEXT", code, "-n", PACKAGE + "/.MainActivity")
    time.sleep(2.5)
    tap("Import", exact=True, wait=1.5)
    tap("Update this note", wait=1.5)
    check_settled("the code's Update reaches the note", lambda: (note_by_uid("ka-trip") or {}).get("body"),
                  lambda b: b == words)

    def asides():
        return [n for n in read_file("notes_data.json") or [] if n.get("title") == "Trip (earlier text)"]

    back_to("Search notes")
    open_note("Trip")
    tap_last(words, wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["67"] * (len(words) - len("Book"))))
    # Read the moment it comes, the keyboard still up: the bar goes on its
    # own, and putting the keyboard down first left too little of its time.
    said = not says_soon("kept as a note of their own", "Show", within=10.0)
    shot(folder, "128-words-kept-aside", "kept as a note of their own")
    tap("Show", exact=True, wait=1.5)
    check("...said, with a Show that opens the note kept aside",
          said and not says_soon("Trip (earlier text)", words[:20], "ⓘ"))
    keyboard_down()
    check_settled("a big delete after it keeps the Update's copy, the note as it was before",
                  lambda: ((note_by_uid("ka-trip") or {}).get("body"),
                           ((note_by_uid("ka-trip") or {}).get("_prev") or {}).get("body")),
                  lambda pair: pair == ("Book", "Mine before"))
    check_settled("...and the words it took are a note of their own", asides,
                  lambda made: [n.get("body") for n in made] == [words])
    back_to("Search notes")
    open_note("Trip")
    tap_last("Book", wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    type_text(words[len("Book"):])
    said = not says_soon("gone again", within=10.0)
    keyboard_down()
    check_settled("the words typed back, the note kept aside of them goes again", asides, lambda made: made == [])
    check("...said", said)
    # The same slip again, put back before Show is tapped: the bar that
    # offered Show goes with the note -- left up, its Show opened an empty new
    # note (the lows wave's round 1).
    tap_last(words, wait=0.6)
    adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    adb("shell", "input", "keyevent", *(["67"] * (len(words) - len("Book"))))
    offered = not says_soon("kept as a note of their own", "Show", within=10.0)
    type_text(words[len("Book"):])
    # At once, not once the bar's ten seconds run out: waited for that long,
    # a bar left up looked the same (the mutant passed).
    gone = not says_soon("gone again", within=4.0)
    check("put back before Show is tapped, the note kept aside goes, and its Show with it",
          offered and gone and find("Show", exact=True) is None, "offered %s, gone said %s" % (offered, gone))
    back_to("Search notes")
    # With the list beside the note, the list says where it hides the note
    # kept aside, in a bar waiting behind the editor's: taken back, that one
    # goes too -- shown, its Show all emptied the search for a note gone
    # (the lows wave's round 2). A 14-inch tablet, as screen_sizes draws it.
    adb("shell", "wm", "size", "1848x2960")
    adb("shell", "wm", "density", "340")
    time.sleep(3.0)
    try:
        back_to("Search notes")
        tap("Search notes", wait=0.8)
        type_text("passport")
        keyboard_down()
        if not open_note("Trip"):
            return
        tap_last(words, wait=0.6)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb("shell", "input", "keyevent", *(["67"] * (len(words) - len("Book"))))
        offered = not says_soon("kept as a note of their own", "Show", within=10.0)
        type_text(words[len("Book"):])
        gone = not says_soon("gone again", within=4.0)
        # The list's bar would come once the editor's goes: looked for over
        # the whole of the bars' time.
        stale = settle(lambda: not screen_says("hidden by your search"), lambda seen: seen, within=12.0, first=0)
        check("...and on a wide screen, the list's bar for it goes too",
              offered and gone and not stale,
              "offered %s, gone said %s, the list's bar shown %s" % (offered, gone, stale))
        keyboard_down()
    finally:
        adb("shell", "wm", "size", "reset")
        adb("shell", "wm", "density", "reset")
    back_to("Search notes")


if __name__ == "__main__":
    sys.exit(main())
