"""The fingerprint of desktop code a fixture maker copies by hand.

`make_export_fixture.py` and `make_days_fixture.py` write out a few
lines of the desktop's window class, which cannot be imported without
starting the app, and stop when those lines change. They hashed the
source as it stood, comments too: the desktop's 2026-11-05 comment pass
changed no line that runs and stopped both. The hash is now of what
runs: comment lines and docstrings left out.

    from desktop_fingerprint import fingerprint
"""
import hashlib
import re

# A docstring: the string a def line (or the region's start) opens with.
_DOCSTRING = re.compile(r'(?m)(^[ \t]*def [^\n]*:\n|\A)[ \t]*"""[\s\S]*?"""[ \t]*\n')
_COMMENT_LINE = re.compile(r"(?m)^[ \t]*#[^\n]*\n")


def code_only(text):
    """*text* without its comment lines and docstrings."""
    return _COMMENT_LINE.sub("", _DOCSTRING.sub(r"\1", text))


def fingerprint(text):
    return hashlib.sha1(code_only(text).encode("utf-8")).hexdigest()
