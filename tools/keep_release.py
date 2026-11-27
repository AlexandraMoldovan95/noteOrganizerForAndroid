"""Keep a release's bundle and its R8 mapping, named for the version.

Google Play is given an app bundle (.aab). R8 renames the app's code in it,
so a crash's trace from Google Play reads through the mapping R8 wrote for
that very build -- and R8 writes it to app/build/outputs/mapping/release/,
where the next build writes over it. This keeps both, once per version, in
a folder outside the repository:

    gradlew.bat bundleRelease        first, with the upload key named
    py tools/keep_release.py         then this, into ../androidVersion-releases
    py tools/keep_release.py <folder>

It refuses:
- a bundle signed with a debug key, which Google Play refuses too (the
  upload key is named as app/build.gradle.kts says);
- a bundle older than the code, or a mapping not written by the same build;
- a version already kept, so a kept release is never written over.

It writes <folder>/<versionName>-<versionCode>/ with the bundle, the
mapping and SHA256SUMS.txt, which also names the key that signed it.
"""
import hashlib
import os
import re
import shutil
import subprocess
import sys

sys.stdout.reconfigure(encoding="utf-8")
HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
GRADLE = os.path.join(ANDROID, "app", "build.gradle.kts")
SOURCES = [os.path.join(ANDROID, "app", "src", "main"), GRADLE]
BUNDLE = os.path.join(ANDROID, "app", "build", "outputs", "bundle", "release", "app-release.aab")
MAPPING = os.path.join(ANDROID, "app", "build", "outputs", "mapping", "release", "mapping.txt")
DEFAULT_FOLDER = os.environ.get("NOTEORG_RELEASES",
                                os.path.join(os.path.dirname(ANDROID), "androidVersion-releases"))
# R8 writes the mapping, then the bundle is packed: minutes apart at most.
SAME_BUILD_SECONDS = 30 * 60


def version():
    text = open(GRADLE, encoding="utf-8").read()
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    if not name or not code:
        sys.exit("app/build.gradle.kts names no versionName or versionCode")
    return name.group(1), code.group(1)


def newest_source():
    newest = 0.0
    for place in SOURCES:
        if os.path.isfile(place):
            newest = max(newest, os.path.getmtime(place))
            continue
        for root, _dirs, files in os.walk(place):
            for name in files:
                newest = max(newest, os.path.getmtime(os.path.join(root, name)))
    return newest


def keytool():
    """keytool from JAVA_HOME, or from Android Studio's own JDK."""
    places = [os.environ.get("JAVA_HOME", ""), r"C:\Programs\android_studio\jbr",
              r"C:\Program Files\Android\Android Studio\jbr"]
    for home in places:
        tool = os.path.join(home, "bin", "keytool.exe" if os.name == "nt" else "keytool")
        if home and os.path.isfile(tool):
            return tool
    sys.exit("no keytool: set JAVA_HOME to a JDK, as gradlew needs anyway")


def signer(bundle):
    """The bundle's signing certificate, as keytool prints its owner."""
    out = subprocess.run([keytool(), "-printcert", "-jarfile", bundle],
                         capture_output=True, text=True, timeout=120)
    owner = re.search(r"^Owner:\s*(.+)$", out.stdout, re.M)
    if not owner:
        sys.exit("the bundle carries no signature keytool can read:\n%s%s" % (out.stdout, out.stderr))
    sha256 = re.search(r"SHA256:\s*([0-9A-F:]+)", out.stdout)
    return owner.group(1).strip(), sha256.group(1) if sha256 else ""


def digest(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    folder = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_FOLDER
    name, code = version()
    for path, what in ((BUNDLE, "bundle"), (MAPPING, "mapping")):
        if not os.path.isfile(path):
            print("No %s at %s: run `gradlew.bat bundleRelease` first" % (what, os.path.relpath(path, ANDROID)))
            return 2
    built = os.path.getmtime(BUNDLE)
    if newest_source() > built:
        print("The bundle is older than the code: build it again (`gradlew.bat bundleRelease`)")
        return 1
    mapped = os.path.getmtime(MAPPING)
    if mapped > built + 60 or built - mapped > SAME_BUILD_SECONDS:
        print("The mapping was not written by the bundle's build: build it again")
        return 1
    owner, fingerprint = signer(BUNDLE)
    if "CN=Android Debug" in owner:
        print("The bundle is signed with a debug key (%s): Google Play refuses it. Name the upload key"
              " (noteorgUploadKey or NOTEORG_UPLOAD_KEY, as app/build.gradle.kts says) and build again."
              % owner)
        return 1
    target = os.path.join(folder, "%s-%s" % (name, code))
    if os.path.exists(target):
        print("%s is kept already: a kept release is never written over" % target)
        return 1
    if os.path.realpath(target).startswith(os.path.realpath(ANDROID) + os.sep):
        print("%s is inside the repository: keep releases outside it" % target)
        return 1
    os.makedirs(target)
    kept_bundle = os.path.join(target, "note-organizer-%s.aab" % name)
    kept_mapping = os.path.join(target, "mapping-%s.txt" % name)
    shutil.copy2(BUNDLE, kept_bundle)
    shutil.copy2(MAPPING, kept_mapping)
    with open(os.path.join(target, "SHA256SUMS.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# Note Organizer for Android %s (versionCode %s)\n" % (name, code))
        f.write("# signed by: %s\n" % owner)
        if fingerprint:
            f.write("# certificate SHA-256: %s\n" % fingerprint)
        for path in (kept_bundle, kept_mapping):
            f.write("%s  %s\n" % (digest(path), os.path.basename(path)))
    print("Kept %s (versionCode %s), signed by %s" % (name, code, owner))
    for path in sorted(os.listdir(target)):
        print("   %s" % os.path.join(target, path))
    return 0


if __name__ == "__main__":
    sys.exit(main())
