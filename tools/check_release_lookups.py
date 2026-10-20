"""Fail when the release build looks for a file by a name it cannot find.

WHY THIS GATE EXISTS
--------------------
The release build is shrunk by R8, which renames classes -- and moves them
out of their package: `Pasting` became `ca1`, in no package at all. Code
that looks a file up RELATIVE to its class (`getResourceAsStream("x.json")`)
then looks in the new class's package, where the file is not. For the
entities table that a paste of HTML needs, the parser's guard turned the
missing file into "nothing read", and every paste from a web page with an
&, a < or an &nbsp; in it came in as plain words: no crash, no message,
formatting gone. The debug build, never renamed, could not show it, and
nor could a screen.

So the built APK itself is read: each lookup in its code, the path that
lookup resolves to, and whether the archive holds that file.

    gradlew.bat assembleRelease
    py tools/check_release_lookups.py
    py tools/check_release_lookups.py path/to/other.apk

Exit code 0 when every lookup finds its file, 1 when one does not, 2 when
there is nothing to read.
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "release", "app-release.apk")


def dexdump():
    """The newest build-tools' dexdump, from the SDK local.properties names."""
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    props = os.path.join(ANDROID, "local.properties")
    if not sdk and os.path.exists(props):
        for line in open(props, encoding="utf-8"):
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
    found = sorted(glob.glob(os.path.join(sdk or "", "build-tools", "*", "dexdump*")),
                   key=lambda p: [int(x) if x.isdigit() else 0 for x in re.split(r"[.\-]", os.path.basename(os.path.dirname(p)))])
    return found[-1] if found else None


def main():
    apk = sys.argv[1] if len(sys.argv) > 1 else APK
    tool = dexdump()
    if not os.path.exists(apk) or not tool:
        print("Nothing to read: %s" % ("no APK at " + apk if not os.path.exists(apk) else "no dexdump in the SDK"))
        return 2
    archive = zipfile.ZipFile(apk)
    names = set(archive.namelist())
    lookups, missing = 0, 0
    with tempfile.TemporaryDirectory() as tmp:
        for dex in sorted(n for n in names if re.fullmatch(r"classes\d*\.dex", n)):
            lines = subprocess.run([tool, "-d", archive.extract(dex, tmp)], capture_output=True,
                                   text=True, errors="replace").stdout.splitlines()
            for i, line in enumerate(lines):
                if "Ljava/lang/Class;.getResourceAsStream" not in line and "Ljava/lang/Class;.getResource:" not in line:
                    continue
                window = "\n".join(lines[max(0, i - 4):i])
                cls = re.findall(r"const-class v\d+, L([\w/$]+);", window)
                name = re.findall(r'const-string v\d+, "([^"]+)"', window)
                lookups += 1
                if not cls or not name:
                    # A name worked out at run time cannot be checked here;
                    # said, so the count is not mistaken for coverage.
                    print("UNREAD   a lookup whose class or name is not a constant: %s" % line.strip()[:100])
                    missing += 1
                    continue
                package = cls[-1].rpartition("/")[0]
                wanted = name[-1][1:] if name[-1].startswith("/") else (package + "/" if package else "") + name[-1]
                ok = wanted in names
                missing += not ok
                print("%-8s %s looks up %r, which is %s" % ("ok" if ok else "MISSING", cls[-1], name[-1], wanted))
    if missing:
        print("\n%d of %d lookups would find nothing in the release build." % (missing, lookups))
        return 1
    print("\nEvery lookup in the release build finds its file (%d)." % lookups)
    return 0


if __name__ == "__main__":
    sys.exit(main())
