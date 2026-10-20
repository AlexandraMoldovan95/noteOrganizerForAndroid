"""Type a long share code into the phone, in pieces.

`adb shell input text` truncated a 634-character code at 181. Whatever
the limit is -- argument length, a buffer in the input command -- one
long call is not reliable, and a code cut short is indistinguishable from
a damaged one. Sent in chunks and verified against the original
afterwards, so a silent truncation cannot pass for a successful type.
"""
import io
import os
import re
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emulator_only import command  # noqa: E402 -- the emulator, never a phone

CHUNK = 100

code = io.open(sys.argv[1], encoding="utf-8").read().strip()
x, y = int(sys.argv[2]), int(sys.argv[3])


def adb(*args):
    return subprocess.run(command(*args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


adb("shell", "input", "tap", str(x), str(y))
for i in range(0, len(code), CHUNK):
    adb("shell", "input", "text", code[i:i + CHUNK])

adb("shell", "uiautomator", "dump", "/sdcard/u.xml")
adb("pull", "/sdcard/u.xml", "ui_typed.xml")
xml = io.open("ui_typed.xml", encoding="utf-8", errors="replace").read()
got = None
for m in re.finditer(r'text="([^"]*)"', xml):
    if m.group(1).startswith("NOTE1"):
        got = m.group(1)
        break

if got == code:
    print("typed in full: %d characters, identical" % len(code))
else:
    print("NOT typed correctly: expected %d characters, got %s"
          % (len(code), len(got) if got else "nothing"))
    sys.exit(1)
