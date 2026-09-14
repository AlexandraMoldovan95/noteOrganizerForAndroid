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
prefers -- circle, squircle, rounded square. The system crops to the
middle 72 of 108 units, so the foreground is drawn at 66% and centred,
or the launcher eats its edges.

Older phones get the flat `mipmap-*/ic_launcher.webp` instead, at five
densities. Both are produced here.

    py tools/make_launcher_icon.py            # write them
    py tools/make_launcher_icon.py --preview  # one big PNG to look at

Set NOTEORGANIZER_HOME if the desktop project is not at its usual path.
"""
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
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


def load_desktop_drawing():
    """The desktop's icon script, imported as a module."""
    script = os.path.join(DESKTOP, "tools", "make_icon.py")
    if not os.path.exists(script):
        print("Nu gasesc aplicatia desktop la: %s" % DESKTOP)
        print("Pune NOTEORGANIZER_HOME pe folderul ei si incearca din nou.")
        print("(Iconita se deseneaza din acelasi cod ca a desktopului --")
        print(" fara el nu desenez alta, ca sa nu ajunga doua icoane diferite")
        print(" pentru acelasi produs.)")
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
    print("desenat din codul desktopului, fundal %s" % hexed)
    for path in written:
        print("   %s" % os.path.relpath(path, ANDROID))
    return written


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
    else:
        write_all(drawing)
