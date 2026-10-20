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
"""
import os
import subprocess

ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")

_chosen = None


def _prop(serial, name):
    out = subprocess.run([ADB, "-s", serial, "shell", "getprop", name],
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
    listed = subprocess.run([ADB, "devices"], capture_output=True, text=True,
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
