"""Write the HTML named character references, and Python's rules for the
numeric ones, as the phone's paste needs them.

Pasting turns HTML into a note the way the desktop's ``html_to_note``
does, and that runs on Python's ``html.parser``, which unescapes with
``html.unescape``: the HTML 5 table of 2,231 named references, a map for
numbers that are not what they say (``&#128;`` is the euro sign, as
Windows-1252 has it), and a set of numbers that become nothing. All three
are read here from the Python running this script and written to

    app/src/main/resources/ro/noteorganizer/app/html_entities.json

in ASCII, every other character escaped -- the repository is English, and
a table of entities is full of letters that are not.

    py tools/make_html_entities.py
"""
import html
import json
import os
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "app", "src", "main", "resources",
                   "ro", "noteorganizer", "app", "html_entities.json")


def main():
    table = {
        "python": sys.version.split()[0],
        "html5": dict(sorted(html.entities.html5.items())),
        "invalid_charrefs": {str(k): v for k, v in sorted(html._invalid_charrefs.items())},
        "invalid_codepoints": sorted(html._invalid_codepoints),
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="ascii", newline="\n") as f:
        json.dump(table, f, ensure_ascii=True, indent=0, sort_keys=False)
        f.write("\n")
    print("wrote %s: %d named references, Python %s" % (OUT, len(table["html5"]), table["python"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())
