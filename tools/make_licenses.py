"""Write the licenses Settings -> About shows, from what the release build carries.

    py tools/make_licenses.py           # write res/raw/open_source_licenses.txt
    py tools/make_licenses.py --check   # after assembleRelease: the APK carries nothing unlisted

WHY IT ASKS GRADLE
------------------
The app is built with other people's libraries -- AndroidX, Jetpack
Compose, Kotlin and its coroutines -- and their licenses ask to be shown
to the people who get the app. The list is the release build's own:
Gradle's releaseRuntimeClasspath, read, never typed. A library from a
project not in PROJECTS stops this script, so a new dependency's license
is looked at by a person before it ships, rather than assumed.

The Apache License text is the one the AndroidX libraries carry inside
their own archives (META-INF/androidx/.../LICENSE.txt), read from Gradle's
cache. The app's own MIT license is LICENSE, beside this folder.

--check reads the built APK: every AndroidX library in it leaves a
META-INF/<group>_<name>.version file, and each must be in the list. Run it
with check_release_lookups.py, before a build goes to a phone.

Gradle needs JAVA_HOME set (README, "Building and running it").
"""
import glob
import os
import re
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
OUT = os.path.join(ANDROID, "app", "src", "main", "res", "raw", "open_source_licenses.txt")
APK = os.path.join(ANDROID, "app", "build", "outputs", "apk", "release", "app-release.apk")
CACHE = os.path.join(os.environ.get("GRADLE_USER_HOME") or os.path.join(os.path.expanduser("~"), ".gradle"),
                     "caches", "modules-2", "files-2.1")
# Short enough for the narrowest phone: at 60, it broke over two lines.
RULE = "=" * 24

# Each project the release build takes libraries from: who makes it, and its
# license -- checked against the project's own published license on
# 2026-10-27. A group matches the first prefix it starts with. All are
# Apache 2.0, so the file carries that one text; a project under another
# license needs its text added as well.
PROJECTS = [
    ("androidx.", "Android Jetpack (AndroidX), with Jetpack Compose -- Google", "Apache License 2.0"),
    ("org.jetbrains.kotlinx", "kotlinx.coroutines and kotlinx.serialization -- JetBrains", "Apache License 2.0"),
    ("org.jetbrains.kotlin", "The Kotlin standard library -- JetBrains", "Apache License 2.0"),
    ("org.jetbrains", "JetBrains Java Annotations -- JetBrains", "Apache License 2.0"),
    ("org.jspecify", "JSpecify -- the JSpecify group", "Apache License 2.0"),
    ("com.google.guava", "Guava's ListenableFuture -- Google", "Apache License 2.0"),
]


def classpath():
    """Every group:name the release build resolves, without versions."""
    gradlew = os.path.join(ANDROID, "gradlew.bat" if os.name == "nt" else "gradlew")
    if not os.environ.get("JAVA_HOME"):
        sys.exit("Set JAVA_HOME first (README, \"Building and running it\").")
    report = subprocess.run(
        [gradlew, ":app:dependencies", "--configuration", "releaseRuntimeClasspath", "-q"],
        cwd=ANDROID, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if report.returncode != 0:
        sys.exit(report.stdout + report.stderr)
    found = set()
    for line in report.stdout.splitlines():
        if "---" not in line or line.rstrip().endswith("(c)"):
            continue   # (c): a version constraint, not a library
        # The version may be absent: a library the BOM sets is listed bare.
        m = re.search(r"--- ([\w.-]+):([\w.-]+)(?=[:\s]|$)", line)
        if m and not m.group(2).endswith("-bom"):   # a BOM sets versions; it holds no code
            found.add((m.group(1), m.group(2)))
    if not found:
        sys.exit("Gradle listed no libraries:\n" + report.stdout[-2000:])
    return found


def project_of(group):
    for prefix, name, license_name in PROJECTS:
        if group.startswith(prefix):
            return name, license_name
    return None


def apache_text():
    """The Apache License as an AndroidX archive in Gradle's cache carries it."""
    for path in sorted(glob.glob(os.path.join(CACHE, "androidx.*", "*", "*", "*", "*.aar"))
                       + sorted(glob.glob(os.path.join(CACHE, "androidx.*", "*", "*", "*", "*.jar")))):
        with zipfile.ZipFile(path) as archive:
            for name in archive.namelist():
                if name.startswith("META-INF/androidx/") and name.endswith("/LICENSE.txt"):
                    text = archive.read(name).decode("utf-8").strip()
                    if "Apache License" in text and "Version 2.0" in text:
                        return text
    sys.exit("No AndroidX library in Gradle's cache carries its license: build the app once first.")


def section(title, body):
    return "%s\n%s\n%s\n\n%s\n" % (RULE, title, RULE, body)


def unwrapped(text):
    """Each paragraph on one line, for the phone to wrap at its own width:
    wrapped at 80 characters, the lines broke twice on a phone's screen."""
    return "\n\n".join(" ".join(line.strip() for line in paragraph.splitlines())
                       for paragraph in re.split(r"\n\s*\n", text.strip()))


def write():
    libraries = classpath()
    unknown = sorted("%s:%s" % lib for lib in libraries if project_of(lib[0]) is None)
    if unknown:
        sys.exit("Not in PROJECTS -- look up each one's license, then add its project:\n  "
                 + "\n  ".join(unknown))
    by_project = {}
    for group, name in sorted(libraries):
        by_project.setdefault(project_of(group), []).append("%s:%s" % (group, name))
    listing = "\n\n".join("%s\n%s\n%s" % (name, license_name, "\n".join("  " + lib for lib in libs))
                          for (name, license_name), libs in by_project.items())
    with open(os.path.join(ANDROID, "LICENSE"), encoding="utf-8") as fh:
        own = fh.read().strip()
    text = "\n".join([
        "Note Organizer for Android is free software under the MIT license (the "
        "first section). It is built with the libraries below, each under the "
        "Apache License 2.0, whose text is the last section.\n",
        section("Note Organizer -- MIT License", unwrapped(own)),
        section("The libraries the app is built with", listing),
        section("Apache License 2.0", unwrapped(apache_text())),
    ])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)
    print("wrote %s: %d libraries from %d projects" % (OUT, len(libraries), len(by_project)))


def check():
    if not os.path.isfile(APK):
        print("no release APK at %s: gradlew.bat assembleRelease first" % APK)
        return 2
    with open(OUT, encoding="utf-8") as fh:
        listed = set(re.findall(r"^  ([\w.-]+:[\w.-]+)$", fh.read(), re.M))
    with zipfile.ZipFile(APK) as apk:
        carried = {"%s:%s" % m.groups() for name in apk.namelist()
                   for m in [re.match(r"META-INF/(androidx\.[\w.-]+?)_([\w.-]+)\.version$", name)] if m}
    missing = sorted(carried - listed)
    for lib in missing:
        print("  not in the licenses: %s" % lib)
    if missing:
        print("%d of the APK's %d AndroidX libraries are missing: py tools/make_licenses.py"
              % (len(missing), len(carried)))
        return 1
    print("The licenses name all %d AndroidX libraries the APK carries." % len(carried))
    return 0


if __name__ == "__main__":
    if "--check" in sys.argv:
        sys.exit(check())
    write()
