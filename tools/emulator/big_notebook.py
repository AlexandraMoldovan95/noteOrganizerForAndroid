"""The app on a large notebook: what it costs at 1,000, 5,000 and 10,000 notes, on a cold emulator.

    py -I tools/emulator/big_notebook.py <folder> [<notes> ...]      (default: 1000 5000 10000)

Measured as the walk measures, from the app's own frames (gfxinfo) and the slow messages on its
main thread (held_after_tap), never from the walk's looks at the screen. For each size: the app's
data cleared and a notebook generated from a fixed seed put inside, then

  - two cold starts (force-stop, am start -W): to the first frame, and the longest piece after;
  - the list scrolled with 15 flings: its frames, the longest, those over 50 ms;
  - a search typed ("cinnamon", in 1 note of 100): the longest piece, and that it found them;
  - the newest note opened from the list, then a word typed in it: the longest piece of each,
    and that the word reached the notes file;
  - the app's memory (PSS) and the notes file's size.

A field with the focus keeps the thread from going quiet (the caret blinks), so after the search
and the typing only the longest piece means anything. The emulator draws in software, so a
frame there is slower than on a phone: the numbers compare the sizes with each other.

The emulator (noteorg_test) is started cold on walk_groups' port, then the machine's turn is
taken (walk.take_the_turn), the emulator waited for until it has settled (walk.wait_for_boot),
and stopped however the run ends; the app's data is cleared after.
It writes <folder>/results.json and a photograph of the list, the search and the open note at
each size, which are looked at.
"""
import json
import os
import random
import re
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import walk  # noqa: E402
import walk_groups  # noqa: E402
from walk import PACKAGE, NOTEBOOK, adb, find, frames_since_reset, held_after_tap, put_inside, says_soon  # noqa: E402
from walk import slow_dispatches_logged  # noqa: E402

SIZES = (1000, 5000, 10000)
TOPICS = ["Groceries", "Meeting", "Trip plan", "Recipe", "Budget", "Reading", "Workout", "Garden",
          "Project", "Call notes", "Ideas", "Shopping", "Car service", "Birthday", "Lecture"]
WORDS = ("milk bread apples call the bank before noon pick up parcel bring the charger book a table "
         "for four review the slides send the invoice water the plants buy stamps renew the "
         "insurance check the tyres fix the shelf ask about the contract print tickets pack light "
         "walk thirty minutes read two chapters plan the week clean the desk order filters").split()


def notebook(n, seed=7):
    """n notes from a fixed seed: the newest is note n, at the list's top."""
    rnd = random.Random(seed)
    cats = ["General", "Work", "Personal", "Ideas"]
    notes = []
    for i in range(1, n + 1):
        body = "\n".join(" ".join(rnd.choice(WORDS) for _ in range(rnd.randint(6, 14)))
                         for _ in range(rnd.randint(2, 6)))
        if i % 100 == 1:
            body += "\ncinnamon rolls on Sunday"
        minute = i  # updated rises with i, so note n is the newest
        stamp = "2026-%02d-%02d %02d:%02d" % (1 + (minute // 40000) % 11, 1 + (minute // 1440) % 27,
                                              (minute // 60) % 24, minute % 60)
        note = {"id": i, "uid": "big-%05d" % i, "title": "%s %d" % (rnd.choice(TOPICS), i), "body": body,
                "category": cats[i % 4], "created": stamp, "updated": stamp}
        if i % 5 == 0:
            note["checklist"] = [{"text": rnd.choice(WORDS), "done": k % 2 == 1} for k in range(4)]
        if i % 10 == 0:
            note["date"] = "2026-12-%02d" % (1 + i % 28)
        notes.append(note)
    return notes


def longest_ms(spans):
    """The longest slow message among [spans], in ms; None when there was none."""
    return round(max(e - s for s, e in spans) * 1000) if spans else None


def pid():
    return adb("shell", "pidof", PACKAGE).strip()


def longest_after(action, within):
    """The longest piece of the app's main thread after [action], in ms."""
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    adb("logcat", "-c")
    p = pid()
    action()
    return longest_ms(held_after_tap(p, within=within))


def cold_start(newest):
    """(ms to the first frame, the longest piece after it in ms, whether the newest note is shown)."""
    adb("shell", "am", "force-stop", PACKAGE)
    time.sleep(1.0)
    adb("logcat", "-c")
    out = adb("shell", "am", "start", "-S", "-W", "-n", PACKAGE + "/.MainActivity", timeout=120)
    total = re.search(r"TotalTime: (\d+)", out)
    p = pid()
    longest = longest_ms(held_after_tap(p, within=90.0)) if p else None
    shown = not says_soon(newest, within=20.0)
    return (int(total.group(1)) if total else None), longest, shown


def measure(n, folder):
    notes = notebook(n)
    newest = notes[-1]["title"]
    adb("shell", "pm", "clear", PACKAGE)
    put_inside("notes_data.json", notes)
    put_inside("settings.json", {"welcome_offered": True})
    size = adb("shell", "run-as", PACKAGE, "stat", "-c", "%s", NOTEBOOK + "/notes_data.json").strip()
    row = {"notes": n, "file_kb": int(size) // 1024 if size.isdigit() else size}
    with slow_dispatches_logged():
        row["start1_ms"], row["start1_longest_ms"], row["start1_shown"] = cold_start(newest)
        row["start2_ms"], row["start2_longest_ms"], row["start2_shown"] = cold_start(newest)
        walk.shot(folder, "big-%05d-list" % n, newest)
        # 15 flings down the list.
        adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        for _ in range(15):
            adb("shell", "input", "swipe", "540", "1900", "540", "500", "120")
            time.sleep(0.15)
        time.sleep(1.5)
        frames = frames_since_reset()
        row["scroll_frames"] = len(frames)
        row["scroll_longest_ms"] = round(max(frames) * 1000) if frames else None
        row["scroll_over_50ms"] = sum(1 for f in frames if f > 0.05)
        meminfo = adb("shell", "dumpsys", "meminfo", PACKAGE)
        mem = re.search(r"TOTAL PSS:\s+(\d+)", meminfo) or re.search(r"TOTAL\s+(\d+)", meminfo)
        row["pss_mb"] = int(mem.group(1)) // 1024 if mem else None
        # Back to the top, and a search typed.
        adb("shell", "am", "force-stop", PACKAGE)
        walk.start_app()
        says_soon(newest, within=30.0)
        at = find("Search notes")
        if at:
            adb("shell", "input", "tap", str(at[0]), str(at[1]))
            time.sleep(1.0)
            row["search_longest_ms"] = longest_after(lambda: adb("shell", "input", "text", "cinnamon"), 20.0)
            row["search_found"] = not says_soon("cinnamon", within=10.0)
            walk.shot(folder, "big-%05d-search" % n, "cinnamon")
            walk.back(1.0)
            walk.back(1.0)
        # The newest note opened, then a word typed in it, and its autosave.
        adb("shell", "am", "force-stop", PACKAGE)
        walk.start_app()
        says_soon(newest, within=30.0)
        card = find(newest)
        if card:
            row["open_longest_ms"] = longest_after(
                lambda: adb("shell", "input", "tap", str(card[0]), str(card[1])), 20.0)
            walk.shot(folder, "big-%05d-open" % n, newest)
            time.sleep(1.0)
            word = find(notes[-1]["body"].split()[0])   # the caret into the note's own text
            if word:
                adb("shell", "input", "tap", str(word[0]), str(word[1]))
                time.sleep(1.0)
            row["type_longest_ms"] = longest_after(lambda: adb("shell", "input", "text", "Hello"), 15.0)
            time.sleep(3.0)
            on_disk = adb("shell", "run-as", PACKAGE, "cat", NOTEBOOK + "/notes_data.json")
            row["typed_saved"] = "Hello" in on_disk
            walk.back(1.0)
    return row


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    folder = os.path.abspath(sys.argv[1])
    sizes = [int(a) for a in sys.argv[2:]] or list(SIZES)
    os.makedirs(folder, exist_ok=True)
    # Started before the turn is taken, as walk_groups starts it: under the turn's memory
    # ceiling, which a process started after inherits, the emulator ended without a word.
    proc = walk_groups.boot("noteorg_test", os.path.join(folder, "emulator.log"))
    if proc is None:
        print("The emulator did not start.")
        return 4
    os.environ["ANDROID_SERIAL"] = walk_groups.SERIAL
    rows = []
    try:
        turn = walk.take_the_turn()
        if turn is not None:
            return turn
        walk.wait_for_boot()         # a start measured while the emulator still settles is its, not the app's
        walk.install(walk.APK)
        for n in sizes:
            print("\n== %d notes" % n, flush=True)
            rows.append(measure(n, folder))
            print(json.dumps(rows[-1]), flush=True)
            with open(os.path.join(folder, "results.json"), "w", encoding="utf-8") as f:
                json.dump(rows, f, indent=1)
    finally:
        if walk.turn_held:           # refused the turn, nothing was installed: no adb, which would ask again
            adb("shell", "pm", "clear", PACKAGE)
        walk_groups.stop(proc, "noteorg_test")
    return 0


if __name__ == "__main__":
    sys.exit(main())
