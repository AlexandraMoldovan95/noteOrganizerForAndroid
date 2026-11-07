"""The one door the emulator scripts reach a device through: an emulator,
never a phone.

The walk and the screenshot script start every part by emptying the
app's data (`pm clear`), and the others tap and type as a person would.
On the emulator that is the point. On a person's phone, plugged in to
try the app, it is their notebook -- and a plain `adb` command goes to
whichever device happens to be the only one attached, so the day the
emulator is stopped and a phone is plugged in, the walk would have
wiped the phone.

So every command names its device, and the device is chosen here: an
emulator by the system's own word for one (`ro.kernel.qemu` or
`ro.boot.qemu` is 1; a phone says 0 or nothing), `ANDROID_SERIAL` only
if it names one. With no emulator attached the scripts stop and say so.

And every adb call through here has a time limit ([run]): an emulator that
stops answering -- frozen, or starved by a computer out of memory -- left a
script waiting on one call for ever, the emulator holding its gigabytes the
while, on a machine where one heavy job at a time is the rule. Past the
limit the script stops, naming the call, rather than wait on.
"""
import os
import subprocess
import time

# Read once, at import: the desktop's guard, which the walk takes as it
# starts (walk.take_the_turn), points LOCALAPPDATA at an empty folder of the
# run's own, and an adb looked for there after it is found nowhere.
ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")

# How long one adb call may take: the slowest an emulator answers is a dump
# of a long screen or of the log, a few seconds; an install, a minute or two.
ADB_SECONDS = 120
INSTALL_SECONDS = 600

_chosen = None


class AdbTimeout(SystemExit):
    """adb did not answer in time: the emulator is stuck. A SystemExit, so
    no part's own `except Exception` walks on into the next stuck call."""


def run(args, timeout=None, **kwargs):
    """`subprocess.run` of an adb command list, held to a time limit:
    [timeout] seconds, else [INSTALL_SECONDS] for an install and
    [ADB_SECONDS] for anything else."""
    limit = timeout or (INSTALL_SECONDS if "install" in args[:4] else ADB_SECONDS)
    try:
        return subprocess.run(args, timeout=limit, **kwargs)
    except subprocess.TimeoutExpired:
        raise AdbTimeout("adb did not answer within %d s: %s\nThe emulator is stuck: stop it "
                         "(adb emu kill) before anything else runs, then start it again."
                         % (limit, " ".join(str(a) for a in args[1:])[:300]))


def _prop(serial, name):
    out = run([ADB, "-s", serial, "shell", "getprop", name], timeout=30,
              capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()


def is_emulator(serial):
    """Whether the device [serial] says it is an emulator."""
    return _prop(serial, "ro.kernel.qemu") == "1" or _prop(serial, "ro.boot.qemu") == "1"


def device():
    """The emulator's serial -- or the script stops, rather than reach a phone."""
    global _chosen
    if _chosen:
        return _chosen
    listed = run([ADB, "devices"], timeout=30, capture_output=True, text=True,
                 encoding="utf-8", errors="replace").stdout
    attached = [line.split()[0] for line in listed.splitlines()[1:]
                if line.strip() and line.split()[-1] == "device"]
    wanted = os.environ.get("ANDROID_SERIAL")
    for serial in ([wanted] if wanted else attached):
        if serial in attached and is_emulator(serial):
            _chosen = serial
            return serial
    raise SystemExit("No emulator attached%s. These scripts empty the app's data and tap as a person "
                     "would, so they run on an emulator only, never on a phone." %
                     (" under ANDROID_SERIAL=%s" % wanted if wanted else ""))


def command(*args):
    """`adb -s <the emulator> ...` as a list, for subprocess."""
    return [ADB, "-s", device(), *args]


def adb(*args, binary=False, timeout=None):
    """One adb call to the emulator, held to a time limit ([run]): its output
    as text, or as bytes when [binary]. Path rewriting is off, which in a
    bash shell turned /sdcard/... into a Windows path."""
    out = run(command(*args), timeout=timeout, capture_output=True,
              env=dict(os.environ, MSYS_NO_PATHCONV="1"))
    return out.stdout if binary else out.stdout.decode("utf-8", errors="replace")


def adb_bytes(*args):
    """[adb], its output as bytes: what [fresh_tree] asks through."""
    return adb(*args, binary=True)


def fresh_tree(adb_bytes, tries=3):
    """The screen's UI tree as uiautomator dumps it, fresh, asked through
    [adb_bytes] (an adb call returning bytes -- this file's, or a tool's own),
    or "" when no tree was written in [tries] asks. The last dump's file goes first: a dump that
    writes nothing -- the app's thread held past uiautomator's patience --
    left the screen before it in the file, and a tool that read it judged,
    or tapped, a screen that was gone.
    The asks are spaced: right after an install and `pm clear`, three made
    within a second all wrote nothing, and a part's first look failed
    (twice, each part fine walked again). And the file is
    read only when this ask's dump says it wrote it: an ask adb lost on the
    way, 21 seconds, ran neither the rm nor the dump, and the walk judged the
    screen before a tap as the screen after it."""
    for attempt in range(tries):
        if attempt:
            time.sleep(1.5 * attempt)
        said = adb_bytes("shell", "rm -f /sdcard/ui.xml; uiautomator dump /sdcard/ui.xml")
        if b"dumped to" not in said:
            continue
        raw = adb_bytes("exec-out", "cat", "/sdcard/ui.xml").decode("utf-8", errors="replace")
        if "<?xml" in raw:
            return raw[raw.find("<?xml"):]
    return ""


SHOT = "/sdcard/shot.png"


def capture(adb_bytes):
    """The screen captured into [SHOT], fresh: True when this ask's screencap
    wrote it, to be read with `exec-out cat`. The last capture goes first,
    as [fresh_tree]'s dump file does: an ask adb lost on the way left it in
    place, and it was read as this one's."""
    return b"captured" in adb_bytes("shell", "rm -f %s; screencap -p %s && echo captured" % (SHOT, SHOT))
