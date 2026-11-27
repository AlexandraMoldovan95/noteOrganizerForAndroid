"""Draw Google Play's feature graphic, 1024 x 500, from the app itself.

The banner at the top of the app's page on Google Play (docs/PLAY_LISTING.md):
the app's name and one line on the left, a real screen of the app on the
right, on Midnight Purple's ground -- the owner's choice of layout and
colour, 2026-11-27. Nothing in it comes from a template or a stock library:

- the colours are Midnight Purple's, read from Themes.kt, which
  tools/check_palettes.py holds to the desktop's;
- the screen is the listing's first screenshot, the note list, which
  `take_screenshots.py --play` takes on the emulator on a notebook written
  for the camera;
- the letters are Roboto, Android's own font (Apache License 2.0), read
  from the emulator's system, where the app's words are drawn in it too.

    py tools/emulator/take_screenshots.py --play   first: the screen it shows
    py tools/make_feature_graphic.py               then this

Needs Pillow and the emulator running (the font is read from it). Writes
build/play-listing/feature-graphic.png, a 24-bit PNG without alpha, as
Google Play asks.
"""
import os
import re
import sys
import tempfile

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(HERE, "emulator"))
from emulator_only import adb  # noqa: E402 -- the font is read from the emulator, never a phone

THEMES = os.path.join(ANDROID, "app", "src", "main", "java", "ro", "noteorganizer", "app", "Themes.kt")
OUT = os.path.join(ANDROID, "build", "play-listing")
SCREEN = os.path.join(OUT, "1-note-list.png")
FONT = "/system/fonts/Roboto-Regular.ttf"

SIZE = (1024, 500)
TITLE = "Note Organizer"
LINE = "Offline notes, on your phone"
LEFT = 72                    # the words' margin, clear of the edge Google Play may crop
PHONE_WIDTH = 262            # the screen's width on the banner; its foot runs off the edge
PHONE_TOP = 58
PHONE_CENTRE = 800
BEZEL = 8
RADIUS = 30


def palette(name="dark"):
    """One theme's colours from Themes.kt, as {role: (r, g, b)}."""
    text = open(THEMES, encoding="utf-8").read()
    block = re.search(r'"%s" to mapOf\((.*?)\)' % re.escape(name), text, re.S)
    if not block:
        sys.exit("Themes.kt has no %r theme" % name)
    return {role: tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))
            for role, value in re.findall(r'"(\w+)" to "(#[0-9a-fA-F]{6})"', block.group(1))}


def roboto(folder):
    """Roboto read from the emulator into [folder]: its path."""
    data = adb("exec-out", "cat", FONT, binary=True)
    if not data.startswith((b"\x00\x01\x00\x00", b"true", b"OTTO")):
        sys.exit("the emulator gave no font at %s (is it running?)" % FONT)
    path = os.path.join(folder, "Roboto-Regular.ttf")
    with open(path, "wb") as f:
        f.write(data)
    return path


def font(path, size, weight):
    """Roboto at [size] px and [weight], through its weight axis when it has
    one (Android's is a variable font); its one weight when it has not."""
    from PIL import ImageFont
    face = ImageFont.truetype(path, size)
    try:
        axes = face.get_variation_axes()
    except (OSError, AttributeError):
        return face
    values = [weight if axis.get("name") in (b"Weight", "Weight") else axis.get("default", 0)
              for axis in axes]
    face.set_variation_by_axes(values)
    return face


def draw(font_path):
    from PIL import Image, ImageDraw

    colours = palette("dark")
    banner = Image.new("RGB", SIZE, colours["bg"])
    pen = ImageDraw.Draw(banner)

    # A wide disc of the sidebar's shade behind the screen, so the phone
    # stands on something rather than floats.
    pen.ellipse((PHONE_CENTRE - 300, -60, PHONE_CENTRE + 300, SIZE[1] + 140), fill=colours["sidebar"])

    # The screen, with a bezel in the cards' shade, its foot past the edge.
    screen = Image.open(SCREEN).convert("RGB")
    height = round(screen.height * PHONE_WIDTH / screen.width)
    screen = screen.resize((PHONE_WIDTH, height), Image.LANCZOS)
    left = PHONE_CENTRE - PHONE_WIDTH // 2
    pen.rounded_rectangle((left - BEZEL, PHONE_TOP - BEZEL, left + PHONE_WIDTH + BEZEL,
                           PHONE_TOP + height + BEZEL), RADIUS + BEZEL, fill=colours["card"])
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1), RADIUS, fill=255)
    banner.paste(screen, (left, PHONE_TOP), mask)

    # The words: an accent rule, the name, the line.
    title = font(font_path, 66, 700)
    line = font(font_path, 31, 400)
    title_box = pen.textbbox((0, 0), TITLE, font=title)
    line_box = pen.textbbox((0, 0), LINE, font=line)
    block = 6 + 26 + (title_box[3] - title_box[1]) + 22 + (line_box[3] - line_box[1])
    top = (SIZE[1] - block) // 2
    pen.rounded_rectangle((LEFT, top, LEFT + 64, top + 6), 3, fill=colours["accent"])
    y = top + 6 + 26
    pen.text((LEFT - title_box[0], y - title_box[1]), TITLE, font=title, fill=colours["text"])
    y += (title_box[3] - title_box[1]) + 22
    pen.text((LEFT - line_box[0], y - line_box[1]), LINE, font=line, fill=colours["subtext"])

    words_right = LEFT + max(title_box[2] - title_box[0], line_box[2] - line_box[0])
    if words_right > left - BEZEL - 24:
        sys.exit("the words reach %d px, into the screen at %d" % (words_right, left - BEZEL))
    return banner


def main():
    try:
        import PIL  # noqa: F401
    except ImportError:
        print("This needs Pillow (py -m pip install Pillow)")
        return 2
    if not os.path.exists(SCREEN):
        print("No %s: run `py tools/emulator/take_screenshots.py --play` first"
              % os.path.relpath(SCREEN, ANDROID))
        return 2
    with tempfile.TemporaryDirectory(prefix="noteorg_font_") as folder:
        banner = draw(roboto(folder))
    path = os.path.join(OUT, "feature-graphic.png")
    banner.save(path, "PNG", optimize=True)
    print("Google Play's feature graphic, %dx%d, %s, %d KB"
          % (banner.width, banner.height, banner.mode, os.path.getsize(path) // 1024))
    print("   %s" % os.path.relpath(path, ANDROID))
    return 0


if __name__ == "__main__":
    sys.exit(main())
