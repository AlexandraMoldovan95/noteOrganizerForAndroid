"""Find a node in the emulator's UI tree by its text and tap its centre.

The dump is taken fresh each time: the list may have scrolled since the
last one, and tapping stale coordinates opens whatever moved into that
spot -- which is how an automated tap lands on the wrong thing.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from emulator_only import command, fresh_tree, run  # noqa: E402 -- the emulator, never a phone

NEEDLE = sys.argv[1]


def adb(*args):
    # Held to a time limit (emulator_only.run): a stuck emulator stops the script.
    return run(command(*args), capture_output=True).stdout


xml = fresh_tree(adb)
if not xml:
    print("the screen could not be read: uiautomator wrote no tree")
    sys.exit(1)

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
print("found: %r at (%d, %d)" % (text[:60], x, y))
adb("shell", "input", "tap", str(x), str(y))
print("tapped")
