"""Rebuild the days fixture, using the DESKTOP's own rules.

`app/src/test/resources/days_cases.json` holds a few notes of a weekly
series -- a day with its own text, a deleted day, a day with only its own
colour, a copy made by the phone's 0.12.0 -- each walked through what a
person does to it: the repeat turned off and on again, from the same start
and from another, a big delete, "Restore previous", a share code's
update, a backup's. For each, the note the desktop stores after every step
and the note its import makes of it. `DaysTest` walks the phone's own
functions through the same steps and fails on any field that differs: the
two programs keep a turned-off series' days in one place, the note (the
owner's choice, the final audit's sixth round).

What the desktop does is run, not described: its core's
`normalize_note`, `rescue_copy_due`, `kept_days_fit`, `days_hold_own_text`,
`apply_shared_update`, `copy_replaced_by_restore` and `rescue_copy_spent`,
imported. The few lines that make the editor's save and "Restore
previous" of them live in methods of the desktop's window class, which
cannot be imported without starting the app, so they are written out
here -- and the methods' source is fingerprinted: when the desktop's
lines change, this script stops and says so, rather than going on
producing answers from an old copy.

The desktop's code is imported with its home folders pointed at empty
folders under app/build, checked empty afterwards: nothing it reads at
import can be a notebook.

    py tools/make_days_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_days_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import copy
import hashlib
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", r"D:\Projects\NoteOrganizer")
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources", "days_cases.json")
HOMES = os.path.join(ANDROID, "app", "build", "desktop-home")

# The desktop's window methods this script copies, as they were when the
# copy below was written: each region of note_organizer.py, start to end.
REGIONS = {
    "_whole_note_rescue_copy": ("    def _whole_note_rescue_copy(note):", "        return prev\n",
                                "ac8e17277765dd96bf4eee47b5df41cbd085e0c2"),
    "_turn_off_rescue_copy": ("    def _turn_off_rescue_copy(self, note, takes_text=False):", "        return prev\n",
                              "0dbcd5f3d72b87ec64ad2c16b132be4e6aae9128"),
    "save_current_note's series branch": (
        '                rescue_due = rescue_copy_due(note.get("title", ""), note.get("body", ""),',
        '                    note["moved"] = {}\n', "fd5bcefb46001f178f53a98ccb469c6c1b25f0de"),
    "restore_previous_version's write": ("        replaced = copy_replaced_by_restore(note, prev)\n",
                                         '            note["_prev"] = replaced\n',
                                         "307ce489636ae9adce9c88017b5f7d12b8769271"),
}
PREV_EXTRA_KEYS = ("checklist", "date", "recurrence", "color", "category", "overrides",
                   "exceptions", "moved", "pinned")
RESCUE_TEXT_KEYS = ("title", "body", "formatting", "paragraphs", "comments")


def isolate():
    """The desktop's home folders, pointed at empty folders of this build."""
    for name in ("APPDATA", "LOCALAPPDATA", "USERPROFILE", "HOME"):
        path = os.path.join(HOMES, name.lower())
        os.makedirs(path, exist_ok=True)
        assert not os.listdir(path), "%s is not empty: %s" % (name, path)
        os.environ[name] = path


def check_isolated():
    for name in ("APPDATA", "LOCALAPPDATA", "USERPROFILE", "HOME"):
        path = os.path.join(HOMES, name.lower())
        assert not os.listdir(path), "the desktop's import wrote into %s" % path


def fingerprints():
    src = io.open(os.path.join(DESKTOP, "note_organizer.py"), encoding="utf-8").read()
    for name, (start, end, sha) in REGIONS.items():
        at = src.find(start)
        stop = src.find(end, at) if at >= 0 else -1
        if at < 0 or stop < 0:
            return "the desktop's %s is not where this script copied it from" % name
        if hashlib.sha1(src[at:stop + len(end)].encode("utf-8")).hexdigest() != sha:
            return ("the desktop's %s has changed since this script copied it: bring the copy below "
                    "and Days.kt / Notebook.kt into line, then update its fingerprint at the top" % name)
    return None


def main():
    if not os.path.isdir(DESKTOP):
        print("cannot find the desktop app at %s (set NOTEORGANIZER_HOME)" % DESKTOP)
        return 2
    problem = fingerprints()
    if problem:
        print(problem)
        return 1
    isolate()
    sys.path.insert(0, DESKTOP)
    from noteorganizer.core.model import (apply_shared_update, copy_replaced_by_restore,
                                          days_hold_own_text, kept_days_fit, normalize_note,
                                          rescue_copy_due, rescue_copy_spent,
                                          UPDATABLE_FROM_BACKUP, UPDATABLE_FROM_SHARED)
    from noteorganizer.core.recurrence import is_recurring, normalize_recurrence
    from noteorganizer.core.formatting import normalize_formatting, normalize_paragraphs
    from noteorganizer.core.tables import with_caret_line

    def whole_note_copy(note):
        """_whole_note_rescue_copy, as fingerprinted."""
        prev = {
            "title": note.get("title") or "",
            "body": note.get("body") or "",
            "formatting": copy.deepcopy(note.get("formatting") or []),
            "paragraphs": copy.deepcopy(note.get("paragraphs") or []),
            "comments": note.get("comments") or "",
        }
        for key in ("date", "recurrence", "exceptions", "overrides", "moved"):
            prev[key] = copy.deepcopy(note.get(key))
        return prev

    def turn_off_copy(note, takes_text):
        """_turn_off_rescue_copy, as fingerprinted -- no save held behind a
        turn-off, which the phone does not have."""
        prev = whole_note_copy(note)
        existing = note.get("_prev")
        if takes_text:
            pass
        elif (isinstance(existing, dict) and set(existing) <= set(RESCUE_TEXT_KEYS)
              and not rescue_copy_spent(note)):
            for key in RESCUE_TEXT_KEYS:
                prev[key] = copy.deepcopy(existing.get(key, prev[key]))
        return prev

    def save(note, title, body, comments, date_value, recurrence):
        """save_current_note's series branch, as fingerprinted: the parts a
        note's words, its date and its repeat take."""
        recurrence = normalize_recurrence(recurrence)
        rescue_due = rescue_copy_due(note.get("title", ""), note.get("body", ""), title, body,
                                     note.get("comments", ""), comments)
        days_lost = (is_recurring(note) and not recurrence.get("freq")
                     and days_hold_own_text(note))
        days_kept = days_hold_own_text(note)
        if not kept_days_fit(note, date_value, recurrence):
            note["exceptions"] = []
        if days_lost:
            note["_prev"] = turn_off_copy(note, takes_text=rescue_due)
        elif rescue_due:
            note["_prev"] = {
                "title": note.get("title", ""),
                "body": note.get("body", ""),
                "formatting": note.get("formatting", []),
                "paragraphs": note.get("paragraphs", []),
                "comments": note.get("comments", ""),
            }
        note["title"] = title
        note["body"] = body
        note["comments"] = comments
        note["date"] = date_value
        note["recurrence"] = recurrence
        if not recurrence["freq"] and not days_kept:
            note["exceptions"] = []
            note["overrides"] = {}
            note["moved"] = {}
        if rescue_copy_spent(note):
            note.pop("_prev", None)

    def restore(note):
        """restore_previous_version's write, as fingerprinted."""
        prev = note["_prev"]
        replaced = copy_replaced_by_restore(note, prev)
        note["title"] = prev.get("title", "")
        note["body"] = with_caret_line(prev.get("body", ""))
        note["comments"] = prev.get("comments", "")
        note["formatting"] = normalize_formatting(prev.get("formatting", []))
        note["paragraphs"] = normalize_paragraphs(prev.get("paragraphs", []), len(note["body"].split("\n")))
        for key in PREV_EXTRA_KEYS:
            if key in prev:
                note[key] = copy.deepcopy(prev[key])
        if replaced is None:
            note.pop("_prev", None)
        else:
            note["_prev"] = replaced

    weekly = {"freq": "weekly", "interval": 1}
    body = "Warm up.\nPhysio said: no squats for three weeks, swap in the bike.\nThen the plan."
    base = {"id": 1, "uid": "gym-days", "title": "Gym", "body": body, "comments": "", "category": "General",
            "color": "", "date": "2026-11-02", "recurrence": weekly, "exceptions": [], "overrides": {},
            "moved": {}, "checklist": [], "attachments": [], "formatting": [], "paragraphs": [],
            "pinned": False, "created": "2026-10-20 09:00:00", "updated": "2026-10-20 09:00:00"}
    own = dict(base, overrides={"2026-11-09": {"body": "Leg day - knee hurt, rest Thursday"}},
               exceptions=["2026-11-16"], moved={"2026-11-23": "2026-11-24"})
    deleted = dict(base, exceptions=["2026-11-16"])
    colour = dict(base, overrides={"2026-11-09": {"color": "#ff6b6b"}})
    paragraph = ("Warm-up ten minutes on the bike, then squats five by five and lunges three by "
                 "twelve, plank to finish.")
    chain = dict(base, body="Plan\n" + paragraph, exceptions=["2026-11-09"])
    # As the phone's 0.12.0 wrote a turn-off: the days moved out of the note
    # into the series' whole-note copy, the note holding none.
    shipped = dict(base, recurrence={"freq": ""}, exceptions=[], overrides={}, moved={},
                   _prev={"title": "Gym", "body": body, "comments": "", "formatting": [], "paragraphs": [],
                          "date": "2026-11-02", "recurrence": weekly, "exceptions": ["2026-11-16"],
                          "overrides": {"2026-11-09": {"body": "Leg day - knee hurt, rest Thursday"}},
                          "moved": {}})
    shipped_empty = dict(shipped, _prev=dict(shipped["_prev"], overrides={}))
    # An update's copy of a note that did not repeat: its days are the ones
    # the update replaced, on purpose.
    updated_one_off = dict(base, recurrence={"freq": ""},
                           _prev={"title": "Gym", "body": "Older words", "comments": "", "formatting": [],
                                  "paragraphs": [], "exceptions": ["2026-11-16"],
                                  "overrides": {"2026-11-09": {"body": "Leg day"}}, "moved": {}})

    def sender(note, **fields):
        out = {k: copy.deepcopy(v) for k, v in note.items() if k not in ("exceptions", "overrides", "moved", "_prev")}
        out.update(fields)
        return out

    off = {"do": "repeat", "date": "2026-11-02", "rule": {"freq": ""}}
    on_same = {"do": "repeat", "date": "2026-11-02", "rule": weekly}
    on_other = {"do": "repeat", "date": "2026-11-09", "rule": weekly}
    big_delete = {"do": "write", "title": "Gym", "body": "Warm up.", "comments": ""}
    cases = [
        ("a turn-off, a day with its own text", own, [off]),
        ("a turn-off, a deleted day only", deleted, [off]),
        ("a turn-off, a day with its own colour only", colour, [off]),
        ("a turn-off, then on again from the same start", own, [off, on_same]),
        ("a turn-off, then on again from another start", own, [off, on_other]),
        ("a turn-off, then a big delete", own, [off, big_delete]),
        ("a turn-off, a big delete, Restore previous", own, [off, big_delete, {"do": "restore"}]),
        ("a turn-off, Restore previous", own, [off, {"do": "restore"}]),
        ("a turn-off, Restore previous twice", own, [off, {"do": "restore"}, {"do": "restore"}]),
        ("a big delete, then a turn-off", own, [big_delete, off]),
        ("a share code turns it off", own, [{"do": "share", "incoming": sender(own, recurrence={"freq": ""})}]),
        ("a share code turns it off, then a big delete", own,
         [{"do": "share", "incoming": sender(own, recurrence={"freq": ""})}, big_delete]),
        ("a share code turns a series of deleted days off, then an edit", deleted,
         [{"do": "share", "incoming": sender(deleted, recurrence={"freq": ""})},
          {"do": "write", "title": "Gym!", "body": body, "comments": ""}]),
        ("a turn-off, then a share code turns it on from another start", own,
         [off, {"do": "share", "incoming": sender(own, date="2026-11-09")}]),
        ("a restarted series' deleted day, a big delete and Restore previous", chain,
         [{"do": "share", "incoming": sender(chain, recurrence={"freq": ""})},
          {"do": "repeat", "date": "2026-11-09", "rule": weekly},
          {"do": "write", "title": "Gym", "body": "Plan", "comments": ""},
          {"do": "restore"}]),
        ("a backup brings the turned-off series", own,
         [{"do": "backup", "incoming": dict(copy.deepcopy(own), recurrence={"freq": ""})}]),
        ("0.12.0's turn-off, read", shipped, []),
        ("0.12.0's turn-off, read, then a big delete", shipped, [big_delete]),
        ("0.12.0's turn-off, read, then Restore previous", shipped, [{"do": "restore"}]),
        ("0.12.0's turn-off of days with no text, read", shipped_empty, []),
        ("an update's copy of a one-off, read", updated_one_off, []),
    ]

    out = []
    for name, start, steps in cases:
        # As the desktop's load leaves a note: through normalize_note.
        note = normalize_note(copy.deepcopy(start))
        after = []
        for step in steps:
            if step["do"] == "repeat":
                save(note, note.get("title", ""), note.get("body", ""), note.get("comments", ""),
                     step["date"], step["rule"])
            elif step["do"] == "write":
                save(note, step["title"], step["body"], step["comments"], note.get("date", ""),
                     note.get("recurrence"))
            elif step["do"] == "restore":
                restore(note)
            elif step["do"] == "share":
                apply_shared_update(note, step["incoming"], UPDATABLE_FROM_SHARED)
            elif step["do"] == "backup":
                apply_shared_update(note, step["incoming"], UPDATABLE_FROM_BACKUP)
            after.append(json.loads(json.dumps(note)))
        out.append({"name": name, "note": start, "steps": steps,
                    "loaded": json.loads(json.dumps(normalize_note(copy.deepcopy(start)))),
                    "after": after,
                    # What the desktop's import makes of the note as it ends.
                    "imported": json.loads(json.dumps(normalize_note(copy.deepcopy(note))))})
    check_isolated()
    with io.open(FIXTURE, "w", encoding="utf-8", newline="\n") as f:
        json.dump({"source": "the desktop's core, %d cases" % len(out), "cases": out}, f,
                  ensure_ascii=False, indent=1, sort_keys=True)
        f.write("\n")
    print("wrote %d cases to %s" % (len(out), FIXTURE))
    return 0


if __name__ == "__main__":
    sys.exit(main())
