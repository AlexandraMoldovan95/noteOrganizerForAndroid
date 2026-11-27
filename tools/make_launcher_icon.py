"""Draw this app's launcher icon, from the desktop app's own drawing.

WHY IT READS THE DESKTOP'S SCRIPT
---------------------------------
The two programs are one product wearing two coats, and an icon is the
first thing anybody sees of either. Drawing a second one here would make
them siblings at best; drawing the SAME one makes them the same app.

So this imports `tools/make_icon.py` from the desktop project and asks it
for the icon at Android's sizes. It reads that file and never writes to
it -- the same rule the note-shape gate follows, and this project's hard
rule generally. If the desktop is not on this machine, the script says so
and stops, rather than inventing a fallback nobody chose.

WHAT ANDROID NEEDS, AND WHY THERE ARE TWO KINDS OF FILE
-------------------------------------------------------
Android 8 and later draw an ADAPTIVE icon: two layers, background and
foreground, which the launcher masks into whatever shape the phone
prefers -- circle, squircle, rounded square. The system keeps the
middle 72 of 108 units and masks that, so the foreground is drawn at
72/108 / sqrt(2), 47%, and centred, or a circle eats its corners.

Older phones get the flat `mipmap-*/ic_launcher.webp` instead, at five
densities. Both are produced here.

    py tools/make_launcher_icon.py            # write them
    py tools/make_launcher_icon.py --preview  # one big PNG to look at
    py tools/make_launcher_icon.py --play     # Google Play's 512 px icon

--play writes build/play-listing/icon-512.png for the store listing
(docs/PLAY_LISTING.md): the adaptive icon's two layers as one full square,
which Google Play rounds itself. Its mask is a rounded square, not a
circle, so the mark is drawn larger there than on a launcher.

Set NOTEORGANIZER_HOME if the desktop project is not at its usual path.
"""
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True      # no __pycache__ left in the desktop's tree

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
RES = os.path.join(ANDROID, "app", "src", "main", "res")

# The five densities a launcher icon ships at, and the pixel size each
# one means. 48dp is the icon; the multiplier is the screen density.
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

# The adaptive layers are 108dp, and the launcher keeps only the middle
# 72 -- then masks THAT into a circle, a squircle or a rounded square,
# whichever the phone prefers.
ADAPTIVE = 432          # 108dp at xxxhdpi, the size Studio's own export uses
SAFE_SQUARE = 72.0 / 108.0

# Our mark is a SQUARE with a border, and a circle mask keeps only the
# circle inscribed in that safe square -- so a square drawn to fill the
# safe zone loses its corners, and with them its border. The largest
# square that survives a circle of diameter 72 has a side of 72/sqrt(2),
# which is this fraction of the 108 canvas.
#
# Measured, not reasoned about: at 72/108 the icon came back from a real
# phone with its frame cut off at every corner. The preview that had
# approved it composed the two layers WITHOUT the system's 108 -> 72
# crop, so it was flattering by exactly the amount that mattered.
SAFE_FRACTION = SAFE_SQUARE / (2 ** 0.5)

# Google Play's icon: 512 px, a full square, which Google Play masks into a
# rounded square -- its corners cut on a radius of about a fifth of the side,
# nothing else. A mark at 70% of the side stands clear of those corners
# (its own corner is 77 px in, inside the 102 px radius's kept area), where
# the launcher's 47% is sized for a circle and would look lost here.
PLAY_ICON = 512
PLAY_MARK = 0.70
PLAY_MAX_BYTES = 1024 * 1024
PLAY_OUT = os.path.join(ANDROID, "build", "play-listing")


def load_desktop_drawing():
    """The desktop's icon script, imported as a module."""
    script = os.path.join(DESKTOP, "tools", "make_icon.py")
    if not os.path.exists(script):
        print("Cannot find the desktop app at: %s" % DESKTOP)
        print("Point NOTEORGANIZER_HOME at its folder and try again.")
        print("(The icon is drawn by the same code as the desktop's --")
        print(" without it no second icon is drawn here, so that one")
        print(" product cannot end up wearing two different marks.)")
        return None
    import importlib.util
    spec = importlib.util.spec_from_file_location("desktop_make_icon", script)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, os.path.join(DESKTOP, "tools"))
    try:
        spec.loader.exec_module(module)
    finally:
        sys.path.pop(0)
    return module


def flat_icon(drawing, size):
    """The icon as it is on the desktop, at one size."""
    return drawing.draw_icon(size).convert("RGBA")


def adaptive_layers(drawing):
    """Background and foreground for Android 8+.

    The desktop's icon draws its own ground, so the background layer is
    that ground as a flat colour and the foreground is the icon itself,
    shrunk into the safe zone. Cropping the drawn icon instead would cut
    its own border off on a round launcher.
    """
    from PIL import Image

    base = flat_icon(drawing, ADAPTIVE)
    # The ground colour, read from the drawing rather than typed -- but
    # read as the colour that covers the MOST of it, not as one pixel.
    # The chosen design draws no tile, so its corner is transparent, and
    # asking the corner produced a pure black background under a dark
    # navy icon: correct code, wrong question.
    counts = {}
    pixels = base.load()
    step = max(1, ADAPTIVE // 120)          # a sample, not every pixel
    for y in range(0, ADAPTIVE, step):
        for x in range(0, ADAPTIVE, step):
            colour = pixels[x, y]
            if colour[3] > 200:
                counts[colour[:3]] = counts.get(colour[:3], 0) + 1
    ground = max(counts.items(), key=lambda kv: kv[1])[0] + (255,)

    background = Image.new("RGBA", (ADAPTIVE, ADAPTIVE), ground)

    inner = int(ADAPTIVE * SAFE_FRACTION)
    foreground = Image.new("RGBA", (ADAPTIVE, ADAPTIVE), (0, 0, 0, 0))
    shrunk = flat_icon(drawing, inner)
    offset = (ADAPTIVE - inner) // 2
    foreground.paste(shrunk, (offset, offset), shrunk)
    return background, foreground, ground


def write_all(drawing):
    from PIL import Image

    written = []
    for density, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-%s" % density)
        if not os.path.isdir(folder):
            os.makedirs(folder)
        icon = flat_icon(drawing, size)
        for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
            path = os.path.join(folder, name)
            icon.save(path, "WEBP", quality=95, method=6)
            written.append(path)

    background, foreground, ground = adaptive_layers(drawing)
    folder = os.path.join(RES, "mipmap-xxxhdpi")
    background.save(os.path.join(folder, "ic_launcher_background.png"), "PNG")
    foreground.save(os.path.join(folder, "ic_launcher_foreground.png"), "PNG")
    written += [os.path.join(folder, "ic_launcher_background.png"),
                os.path.join(folder, "ic_launcher_foreground.png")]

    # The adaptive icon points at the two layers. Written here rather
    # than left as the template's vector, which drew Android's own robot.
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
           '    <background android:drawable="@mipmap/ic_launcher_background" />\n'
           '    <foreground android:drawable="@mipmap/ic_launcher_foreground" />\n'
           '</adaptive-icon>\n')
    folder = os.path.join(RES, "mipmap-anydpi-v26")
    if not os.path.isdir(folder):
        os.makedirs(folder)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = os.path.join(folder, name)
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(xml)
        written.append(path)

    hexed = "#%02x%02x%02x" % ground[:3]
    print("drawn by the desktop's code, background %s" % hexed)
    for path in written:
        print("   %s" % os.path.relpath(path, ANDROID))
    return written


def play_icon(drawing):
    """build/play-listing/icon-512.png: the launcher icon's ground as a full
    square, the mark on it at PLAY_MARK of the side. Its path, or None."""
    from PIL import Image

    _background, _foreground, ground = adaptive_layers(drawing)
    # Drawn at twice the size and shrunk once, for clean edges; the mark is
    # what the drawing paints, measured by its own alpha.
    flat = flat_icon(drawing, PLAY_ICON * 2)
    mark = flat.crop(flat.getbbox())
    side = int(PLAY_ICON * PLAY_MARK)
    mark = mark.resize((side, round(side * mark.height / mark.width)), Image.LANCZOS)
    icon = Image.new("RGBA", (PLAY_ICON, PLAY_ICON), ground)
    icon.alpha_composite(mark, ((PLAY_ICON - mark.width) // 2, (PLAY_ICON - mark.height) // 2))
    os.makedirs(PLAY_OUT, exist_ok=True)
    path = os.path.join(PLAY_OUT, "icon-512.png")
    icon.save(path, "PNG", optimize=True)
    size = os.path.getsize(path)
    if size > PLAY_MAX_BYTES:
        print("%s is %d KB, over Google Play's 1,024 KB" % (path, size // 1024))
        return None
    print("Google Play's icon, %dx%d, %d KB, ground #%02x%02x%02x"
          % (icon.width, icon.height, size // 1024, *ground[:3]))
    print("   %s" % os.path.relpath(path, ANDROID))
    return path


def preview(drawing):
    from PIL import Image

    sizes = [48, 72, 96, 144, 192]
    pad = 16
    width = sum(sizes) + pad * (len(sizes) + 1)
    height = max(sizes) + pad * 2
    sheet = Image.new("RGBA", (width, height), (24, 24, 32, 255))
    x = pad
    for size in sizes:
        icon = flat_icon(drawing, size)
        sheet.paste(icon, (x, (height - size) // 2), icon)
        x += size + pad
    out = os.path.join(HERE, "launcher_preview.png")
    sheet.save(out, "PNG")
    print("previzualizare: %s" % out)


if __name__ == "__main__":
    drawing = load_desktop_drawing()
    if drawing is None:
        sys.exit(2)
    if "--preview" in sys.argv:
        preview(drawing)
    elif "--play" in sys.argv:
        sys.exit(0 if play_icon(drawing) else 1)
    else:
        write_all(drawing)
