"""Find a node in the emulator's UI tree by its text and tap its centre.

The dump is taken fresh each time: the list may have scrolled since the
last one, and tapping stale coordinates opens whatever moved into that
spot -- which is how an automated tap lands on the wrong thing.
"""
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emulator_only import command  # noqa: E402 -- the emulator, never a phone

NEEDLE = sys.argv[1]
XML = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ui.xml")


def adb(*args):
    return subprocess.run(command(*args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
adb("pull", "/sdcard/ui.xml", XML)

xml = open(XML, encoding="utf-8", errors="replace").read()

# Each node carries text="..." and bounds="[l,t][r,b]" in the same tag, so
# one pattern over the whole tag keeps them together.
pattern = re.compile(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
hits = []
for match in pattern.finditer(xml):
    text = match.group(1)
    if NEEDLE in text:
        left, top, right, bottom = (int(match.group(i)) for i in range(2, 6))
        hits.append((text, (left + right) // 2, (top + bottom) // 2))

if not hits:
    print("did not find %r on screen" % NEEDLE)
    sys.exit(1)

text, x, y = hits[0]
print("gasit: %r la (%d, %d)" % (text[:60], x, y))
adb("shell", "input", "tap", str(x), str(y))
print("apasat")
