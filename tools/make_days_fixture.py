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
owner's choice).

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
import can be a notebook -- and under the desktop's own memory ceiling
(`desktop_guest`).

The cases, in the order they were added: a 0.12.0 Trash entry restored, then
rewritten; 0.12.0's turn-off then turn-on as it was; an update's copy of a
series, which never moves; the same backup imported twice; the comments
rewritten over a big delete's copy; "Restore previous" after comments were
typed, and after the same words' styles changed.

Then (the desktop's 947e9d3): a day reset after a series is
turned back on as its copy's series, read again -- the copy's days spent --
by the editor's turn-on and by 0.12.0's notebook read; the backup made
before the reset, compared without either side's copy (an import asks
before it updates, as the desktop's _known_note_differs does); 0.12.0's own
backup imported where the move ran; a turn-off over a share code's Update
copy, which it keeps; and two letters of comments cleared after a long
comment was deleted.

Then (the desktop's 8b5d6cc): the copy's days taken back
into the note per date before they are spent -- 0.12.0 turned on as it
was, then another day edited, coloured, deleted or moved; the note's own
day over the copy's; the copy's moves onto dates the note's own move
holds; turned on from another start, then the start put back; back to the
first start with another rule in one Save -- a second turn-off over the
first one's copy of the same series, after a big delete and after the
comments were cleared; and an arriving note read as a load reads it, its
copy's days taken back, before both are compared bare.

Then (a data fix of the desktop's): a copy's day is
dropped only where the note deleted that day or wrote words of its own for
it -- 0.12.0 turned on as it was, then that very day moved, coloured (the
desktop's patch and the phone's), or moved with another put on its date;
the copy's moved day where the note moved another onto its date, coloured
it where it stood, or deleted it; a copy's move onto a day whose own move
stays behind; an edit the copy draws on no day; a second turn-off after
a line was added, which keeps the newest words; and a big delete, the repeat
off, on as another series (another start, every two weeks, an end date) and
off again, which keeps the deleted words.

Then (two more of the desktop's data fixes): a copy's day whose words the
note's day lacks puts them on the day,
after its own -- 0.12.0 turned on as it was, that day coloured, then the
series' words changed: read, read again, Restore previous, the day reset;
and two of the copy's days for one date, the note's day moved onto a day off
the copy's schedule. Then only the lines a day lacks -- a
day grown from the copy's text, the copy's text mid-way in the day's, and a
day ending in a table typed on the phone with the copy's a table.

Then, by the desktop's rule: a big delete after a share
code's Update keeps the Update's copy, the words it takes made a note of
their own, "(earlier text)", and that note gone again when they are typed
back -- not after a read, which forgets it -- for a series, a one-off (its
date along) and comments alone. `aside_after` lists those notes with the
days set aside, in the order the desktop adds them.

    py tools/make_days_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_days_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import copy
import datetime
import io
import json
import os
import sys

sys.stdout.reconfigure(encoding="utf-8")
sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from desktop_fingerprint import fingerprint  # noqa: E402 -- found beside this script
ANDROID = os.path.dirname(HERE)
DESKTOP = os.environ.get("NOTEORGANIZER_HOME", os.path.join(os.path.dirname(ANDROID), "NoteOrganizer"))
FIXTURE = os.path.join(ANDROID, "app", "src", "test", "resources", "days_cases.json")
HOMES = os.path.join(ANDROID, "app", "build", "desktop-home")

# The desktop's window methods this script copies, as they were when the
# copy below was written: each region of note_organizer.py, start to end.
REGIONS = {
    "_whole_note_rescue_copy": ("    def _whole_note_rescue_copy(note):", "        return prev\n",
                                "9aeaff3d3afe725936b0102de6da9e3bd99cb4cc"),
    "_turn_off_rescue_copy": ("    def _turn_off_rescue_copy(self, note, takes_text=False):", "        return prev\n",
                              "23c6377199fd22afb79412540a2d6fb67d4564e4"),
    "save_current_note's series branch": (
        '                rescue_due = rescue_copy_due(note.get("title", ""), note.get("body", ""),',
        '                    note["moved"] = {}\n', "5237d72b1c7f2b3b7c7dd2bb7c650ba7b36af059"),
    "restore_previous_version's write": ("        replaced = copy_replaced_by_restore(note, prev)\n",
                                         '            note["_prev"] = replaced\n',
                                         "ea1cff24afe31035e3486915cfa611433e5837be"),
    "_reset_occurrence_override's write": ('        note["overrides"].pop(occurrence_date, None)\n',
                                           '            note["moved"].pop(origin, None)\n',
                                           "b21c7986bf36cc7434987c1713e9787960321c15"),
    # The desktop code the copy below takes besides (a change in any of it
    # once left every fingerprint above as it was).
    "_holds_unspent_rescue": ("    def _holds_unspent_rescue(note):",
                              '        return isinstance(note.get("_prev"), dict) and not rescue_copy_spent(note)\n',
                              "dcf14508ca760ec11cf7992af4be7f96298ec33f"),
    "the spent copy's drop after a save": ("            # The note holds again what its rescue copy kept -- Undo after\n",
                                           "            self._take_back_aside(note)\n",
                                           "1a34c7dee7a03901635c6922eec0214655b076bd"),
    # ...and the words a big delete takes beside an Update's copy.
    "_keep_text_aside": ("    def _keep_text_aside(self, note, comments_only=False):",
                         "            (kept.get(\"uid\"), words, self._aside_look(kept)))\n",
                         "5bf13b7ba649ec352d859841e0099bf005bba52d"),
    "_take_back_aside": ("    def _take_back_aside(self, note):",
                         "            del self._kept_asides[note.get(\"uid\")]\n",
                         "4d24c617f68c2a3ca0e23412fe0138f37c8b502f"),
    "_set_aside_days": ("    def _set_aside_days(self, say=True):", "        return made\n",
                        "89a665cc3ec167a38c3714fb709ac87b868653ba"),
    "save_notes' head": ("    def save_notes(self):", "        self._set_aside_days()\n",
                         "750c23787a4014458cb007cad0ae1964e4b1c478"),
    # ...and what the copy below writes out by hand (PREV_EXTRA_KEYS, the
    # turn-off's two questions), which no fingerprint watched.
    "_PREV_EXTRA_LABELS": ("    _PREV_EXTRA_LABELS = (", '        ("pinned", "pin"),\n    )\n',
                           "95c49ecfde5f333f72ad84911010a0f06304f71a"),
    "_repeat_off_loses_days": ("    def _repeat_off_loses_days(self, note, recurrence):",
                               "        return self._days_hold_own_text(note)\n",
                               "4ce9009f5f6418ce8ac3f7faef705fd7414b498d"),
    "_days_kept_off_repeat": ("    def _days_kept_off_repeat(self, note):",
                              "        return self._days_hold_own_text(note)\n",
                              "e18df89814c158f99f8b1de13b7463b0f2f3cb77"),
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
        if fingerprint(src[at:stop + len(end)]) != sha:
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
    from desktop_guest import settle
    settle(DESKTOP)
    isolate()
    sys.path.insert(0, DESKTOP)
    from noteorganizer.core.model import (apply_shared_update, comments_kept_by_copy, copy_holds_this_series,
                                          copy_days_set_aside, copy_replaced_by_restore, days_hold_own_text,
                                          kept_days_fit, drop_hidden_moves, normalize_note, notes_set_aside,
                                          rescue_copy_due,
                                          rescue_copy_spent, shared_update_changes,
                                          spend_series_days, update_copy, words_lost, DAY_FIELDS,
                                          UPDATABLE_FROM_BACKUP, UPDATABLE_FROM_SHARED)
    from noteorganizer.core.recurrence import is_recurring, normalize_recurrence
    from noteorganizer.core.formatting import normalize_formatting, normalize_paragraphs
    from noteorganizer.core.storage import restore_many_from_trash
    from noteorganizer.core.tables import with_caret_line
    import noteorganizer.core.model as desktop_model
    import noteorganizer.core.storage as desktop_storage

    # The desktop stamps "updated" from the clock, and the parity judges no
    # stamp: on a fixed clock a rebuild writes the same fixture, so one that
    # changes it is a rule that changed (the stamps alone changed it before).
    class FixedClock(datetime.datetime):
        @classmethod
        def now(cls, tz=None):
            return cls(2026, 10, 29, 12, 0, 0, tzinfo=tz)

    # The stamps are storage's now_stamp since the desktop's d1bb902: fixed
    # in model alone, every update stamped the clock again at each rebuild.
    desktop_model.datetime = FixedClock
    desktop_storage.datetime = FixedClock

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

    # The notes made of words kept aside in this case, each with the words
    # that spend it: [words, note]. A read is the next start, which no
    # longer knows them: their words go None, and they stay for good.
    kept = []

    def keep_text_aside(note, comments_only=False):
        """_keep_text_aside, as fingerprinted: the note it makes."""
        title, body = note.get("title") or "", note.get("body") or ""
        comments = note.get("comments") or ""
        if comments_only:
            body = ""
        if not (title.strip() or body.strip() or comments.strip()):
            return
        made = normalize_note({
            "id": 0, "title": (title + " (earlier text)").strip(), "body": body,
            "formatting": [] if comments_only else copy.deepcopy(note.get("formatting") or []),
            "paragraphs": [] if comments_only else copy.deepcopy(note.get("paragraphs") or []),
            "comments": comments, "category": note.get("category") or "General",
            "date": "" if is_recurring(note) else note.get("date") or "",
            "color": note.get("color") or ""})
        words = {"comments": comments} if comments_only else {"title": title, "body": body, "comments": comments}
        kept.append([words, made])

    def take_back_aside(note):
        """_take_back_aside, as fingerprinted: a note kept aside goes once
        the note holds its words again (none is touched here)."""
        kept[:] = [entry for entry in kept
                   if entry[0] is None
                   or not all(words_lost(value, note.get(key) or "") == 0 for key, value in entry[0].items())]

    def turn_off_copy(note, takes_text):
        """_turn_off_rescue_copy, as fingerprinted -- no save held behind a
        turn-off, which the phone does not have: its held branch is never
        taken here."""
        existing = note.get("_prev")
        if takes_text and update_copy(existing) and not rescue_copy_spent(note):
            keep_text_aside(note)
            takes_text = False
        if takes_text or not (update_copy(existing) and not rescue_copy_spent(note)):
            copy_days_set_aside(note)
        prev = whole_note_copy(note)
        if takes_text:
            pass
        elif update_copy(existing) and not rescue_copy_spent(note):
            kept = copy.deepcopy(existing)
            for key in ("date", "recurrence") + DAY_FIELDS:
                if key not in kept:
                    kept[key] = copy.deepcopy(prev.get(key))
            return kept
        elif (isinstance(existing, dict)
              and rescue_copy_due(existing.get("title") or "", existing.get("body") or "",
                                  note.get("title") or "", note.get("body") or "",
                                  existing.get("comments") or "", note.get("comments") or "")):
            for key in RESCUE_TEXT_KEYS:
                prev[key] = copy.deepcopy(existing.get(key, prev[key]))
        return prev

    def holds_unspent_rescue(note):
        """_holds_unspent_rescue, the desktop's window method, whole."""
        return isinstance(note.get("_prev"), dict) and not rescue_copy_spent(note)

    def save(note, title, body, comments, date_value, recurrence, formatting=None):
        """save_current_note's series branch, as fingerprinted: the parts a
        note's words, its date and its repeat take -- and its styles, which
        the window writes from the editor, when a step gives them."""
        recurrence = normalize_recurrence(recurrence)
        rescue_due = rescue_copy_due(note.get("title", ""), note.get("body", ""), title, body,
                                     note.get("comments", ""), comments)
        comments_alone = (rescue_due and holds_unspent_rescue(note)
                          and not rescue_copy_due(note.get("title", ""), note.get("body", ""), title, body))
        days_lost = (is_recurring(note) and not recurrence.get("freq")
                     and days_hold_own_text(note))
        days_kept = days_hold_own_text(note)
        if not kept_days_fit(note, date_value, recurrence):
            note["exceptions"] = []
        if days_lost:
            note["_prev"] = turn_off_copy(note, takes_text=rescue_due and not comments_alone)
            if comments_alone and update_copy(note["_prev"]):
                keep_text_aside(note, comments_only=True)
            elif comments_alone:
                note["_prev"]["comments"] = comments_kept_by_copy(
                    note["_prev"].get("comments", ""), note.get("comments", ""))
        elif rescue_due and update_copy(note.get("_prev")) and not rescue_copy_spent(note):
            keep_text_aside(note, comments_only=comments_alone)
        elif rescue_due:
            if not comments_alone:
                copy_days_set_aside(note)
            note["_prev"] = dict(copy.deepcopy(note["_prev"]), comments=comments_kept_by_copy(
                note["_prev"].get("comments", ""),
                note.get("comments", ""))) if comments_alone else {
                "title": note.get("title", ""),
                "body": note.get("body", ""),
                "formatting": note.get("formatting", []),
                "paragraphs": note.get("paragraphs", []),
                "comments": note.get("comments", ""),
            }
        note["title"] = title
        note["body"] = body
        if formatting is not None:
            note["formatting"] = copy.deepcopy(formatting)
        note["comments"] = comments
        note["date"] = date_value
        note["recurrence"] = recurrence
        drop_hidden_moves(note)
        spend_series_days(note)
        if not recurrence["freq"] and not days_kept:
            note["exceptions"] = []
            note["overrides"] = {}
            note["moved"] = {}
        if rescue_copy_spent(note):
            note.pop("_prev", None)
        take_back_aside(note)

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
        drop_hidden_moves(note)
        if replaced is None:
            note.pop("_prev", None)
        else:
            note["_prev"] = replaced

    def reset(note, day):
        """_reset_occurrence_override's write, as fingerprinted, for a day
        with its own text and no move: its own edit goes, and the notebook
        is saved as it is."""
        note["overrides"].pop(day, None)

    def imported(note, incoming, fields):
        """An import's update of a note already here: asked first, as the
        desktop's _known_note_differs asks it of the note as it arrived, and
        applied only when it changes a field (apply_shared_update)."""
        if shared_update_changes(note, incoming, fields):
            apply_shared_update(note, incoming, fields)

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
    # 0.12.0's turn-off, then on again as it was: its turn-on wrote the rule
    # alone, the note repeating with no days, the series' copy holding them.
    back_on = dict(shipped, recurrence=weekly)
    back_on_elsewhere = dict(shipped, recurrence=weekly, date="2026-11-09")
    # A series whose one day of its own is the only day it
    # holds -- reset, it holds none, the state the copy's days came back into.
    one_day = dict(base, overrides={"2026-11-09": {"body": "Leg day - knee hurt, rest Thursday"}})
    back_on_one = dict(back_on, _prev=dict(back_on["_prev"], exceptions=[]))
    # Comments: a long comment beside a paragraph.
    trip_body = ("Day 2: the hike to the lake starts at 7, bring the blue tent, two litres of water each, "
                 "and the map Ioana printed. Day 3: rest, then the castle at noon. ") * 2
    trip_comments = ("Booking refs and phone numbers for the trip: hotel 88231, car 1192-A, "
                     "guide +40 721 000 111. ") * 8
    trip = dict(base, title="Trip", body=trip_body, comments=trip_comments, recurrence={"freq": ""},
                date="")
    renovation = dict(base, title="Renovation", recurrence={"freq": ""}, date="",
                      body="Kitchen renovation plan: order the tiles, call Dan about the sink, measure the window. " * 3)
    bold = [{"start": 0, "end": 7, "style": "bold"}]

    def sender(note, **fields):
        out = {k: copy.deepcopy(v) for k, v in note.items() if k not in ("exceptions", "overrides", "moved", "_prev")}
        out.update(fields)
        return out

    off = {"do": "repeat", "date": "2026-11-02", "rule": {"freq": ""}}
    on_same = {"do": "repeat", "date": "2026-11-02", "rule": weekly}
    on_other = {"do": "repeat", "date": "2026-11-09", "rule": weekly}
    big_delete = {"do": "write", "title": "Gym", "body": "Warm up.", "comments": ""}
    untrash = {"do": "untrash"}
    ended_by_backup = {"do": "backup", "incoming": dict(copy.deepcopy(own), recurrence={"freq": ""},
                                                        exceptions=[], overrides={}, moved={})}
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
        # A Trash entry is read when it is restored.
        ("0.12.0's turn-off in the Trash, restored", shipped, [untrash]),
        ("0.12.0's turn-off in the Trash, restored, then a big delete", shipped, [untrash, big_delete]),
        ("0.12.0's turn-off then on as it was, read", back_on, []),
        ("0.12.0's turn-off then on as it was, read, then a big delete", back_on, [big_delete]),
        ("0.12.0's turn-off then on from another start, read", back_on_elsewhere, []),
        # An update's copy repeats when the series did: never taken in, so the
        # same backup is the same note the second time.
        ("a backup's update ends a series, read, the same backup again", own,
         [ended_by_backup, {"do": "read"}, ended_by_backup]),
        ("a share code's update ends a series, read", own,
         [{"do": "share", "incoming": sender(own, recurrence={"freq": ""})}, {"do": "read"}]),
        ("the comments rewritten over a big delete's copy", trip,
         [{"do": "write", "title": "Trip", "body": "Day 2.", "comments": trip_comments},
          {"do": "write", "title": "Trip", "body": "Day 2.", "comments": trip_comments + "Car returned."},
          {"do": "write", "title": "Trip", "body": "Day 2.", "comments": "ok, all booked"},
          {"do": "restore"}]),
        ("Restore previous after comments were typed", renovation,
         [{"do": "write", "title": "Renovation", "body": "", "comments": ""},
          {"do": "write", "title": "Renovation", "body": "",
           "comments": "Dan called back: plumber Tuesday 10:00, gate code 4471, quote 1,250 EUR."},
          {"do": "restore"}]),
        ("Restore previous after the same words were made bold", renovation,
         [{"do": "write", "title": "Renovation", "body": "Kitchen", "comments": ""},
          {"do": "write", "title": "Renovation", "body": renovation["body"], "comments": "", "formatting": bold},
          {"do": "restore"}]),
        # A series' own copy's days are spent once the note
        # repeats as that series again: a day reset after stays reset.
        ("one day of its own: off, an edit, on as it was, the day reset, read", one_day,
         [off, {"do": "write", "title": "Gym!", "body": body, "comments": ""}, on_same,
          {"do": "reset", "day": "2026-11-09"}, {"do": "read"}]),
        ("0.12.0's turn-off then on as it was, its one day reset, read again", back_on_one,
         [{"do": "reset", "day": "2026-11-09"}, {"do": "read"}]),
        # ...and a backup made before the reset, whose copy still holds the
        # day, is the same note: both sides compared without their copies.
        ("0.12.0's turn-off then on as it was, its one day reset, the backup made before", back_on_one,
         [{"do": "reset", "day": "2026-11-09"}, {"do": "backup", "incoming": copy.deepcopy(back_on_one)}]),
        # The edge said: imported where the move already ran, it is asked
        # about, and its update changes nothing.
        ("0.12.0's turn-off then on as it was, its own backup again", back_on,
         [{"do": "backup", "incoming": copy.deepcopy(back_on)}]),
        # A turn-off keeps an import's Update copy, the series given to it.
        ("a share code's update, the repeat turned off, Restore previous", own,
         [{"do": "share", "incoming": sender(own, body="Warm up.\nThe laptop's plan: bike, then weights.")},
          off, {"do": "restore"}]),
        # Two letters cleared after a long comment was deleted keep it.
        ("a long comment deleted, two letters typed and cleared, Restore previous", trip,
         [{"do": "write", "title": "Trip", "body": trip_body, "comments": ""},
          {"do": "write", "title": "Trip", "body": trip_body, "comments": "ok"},
          {"do": "write", "title": "Trip", "body": trip_body, "comments": ""},
          {"do": "restore"}]),
    ]
    # The desktop's 8b5d6cc. The copy's days come back to
    # the note on every date its own days leave free, then the copy's are
    # spent: 0.12.0 turned back on as it was, then any other day touched,
    # held its first day's own text in the copy alone, and the spend erased it.
    later = "2026-11-23"
    for what, days in (("another day's text", {"overrides": {later: {"body": "Swim instead"}}}),
                       ("another day's colour", {"overrides": {later: {"color": "#ff6b6b"}}}),
                       ("another day deleted", {"exceptions": [later]}),
                       ("another day moved", {"moved": {later: "2026-11-24"}})):
        cases.append(("0.12.0's turn-off then on as it was, then %s, read" % what, dict(back_on, **days), []))
    cases.append(("0.12.0's turn-off then on as it was, then another day's text, a big delete",
                  dict(back_on, overrides={later: {"body": "Swim instead"}}), [big_delete]))
    # The note's own day keeps its date; a copy's move never goes onto a date the note holds.
    cases.append(("the note's own day over the copy's, read",
                  dict(back_on, overrides={"2026-11-09": {"body": "The note's own Monday"}}), []))
    cases.append(("the copy's moves onto dates the note's own move holds, read",
                  dict(back_on, moved={later: "2026-11-25"},
                       _prev=dict(back_on["_prev"], exceptions=[],
                                  moved={"2026-11-30": "2026-11-25", "2026-12-07": later,
                                         "2026-12-14": "2026-12-15"})), []))
    # Turned on again from another start, then the start put back: the save
    # spent a deleted day the note never took, back on the calendar for good.
    cases.append(("0.12.0's turn-off then on from another start, the start put back", back_on_elsewhere, [on_same]))
    cases.append(("a turn-off, on from another start, the start put back", own, [off, on_other, on_same]))
    # The phone's Repeat sheet saves the start, then the rule: the copy is
    # spent once, on both, as the desktop's one save spends it.
    cases.append(("a turn-off, on from another start, back to the first start every two weeks in one Save", own,
                  [off, on_other, {"do": "repeat", "date": "2026-11-02", "rule": {"freq": "weekly", "interval": 2}}]))
    # A second turn-off keeps the words the first one's copy of the same series held.
    cases.append(("a big delete, off, on as it was, off again, Restore previous", own,
                  [big_delete, off, on_same, off, {"do": "restore"}]))
    cases.append(("a series' long comment cleared while off, on as it was, off again, Restore previous",
                  dict(own, comments=trip_comments),
                  [off, {"do": "write", "title": "Gym", "body": body, "comments": ""}, on_same, off,
                   {"do": "restore"}]))
    # A date the note holds
    # with no words of its own no longer spends the copy's own text for that
    # day: 0.12.0 turned on as it was, then THAT day moved, or only coloured.
    first = "2026-11-09"
    for what, days in (("that day moved", {"moved": {first: "2026-11-10"}}),
                       ("that day coloured, the desktop's shape",
                        {"overrides": {first: {"title": "Gym", "body": body, "comments": "",
                                               "color": "#ff6b6b", "category": "Work"}}}),
                       ("that day coloured, the phone's shape", {"overrides": {first: {"color": "#ff6b6b"}}}),
                       ("that day moved, another moved onto its date",
                        {"moved": {first: "2026-11-11", "2026-11-23": first}})):
        cases.append(("0.12.0's turn-off then on as it was, then %s, read" % what, dict(back_on, **days), []))
    # A copy's move the note's days leave no room for keeps its words on the day.
    copy_moved = dict(back_on["_prev"], exceptions=[], moved={first: "2026-11-10"},
                      overrides={"2026-11-10": {"body": "Leg day - knee hurt, rest Thursday"}})
    for what, days in (("another day moved onto its date", {"moved": {"2026-11-23": "2026-11-10"}}),
                       ("the day coloured where it stood", {"overrides": {first: {"color": "#ff6b6b"}}}),
                       ("the day deleted", {"exceptions": [first]})):
        cases.append(("the copy's moved day, %s, read" % what, dict(back_on, _prev=copy_moved, **days), []))
    # A copy's move onto a day whose own move stays behind stays behind too.
    cases.append(("the copy's moves onto a day whose own move stays behind, read",
                  dict(back_on, moved={"2026-11-23": "2026-11-10"},
                       _prev=dict(copy_moved, moved={first: "2026-11-10", "2026-11-16": first},
                                  overrides={"2026-11-10": {"body": "Monday 9th's words"},
                                             first: {"body": "Monday 16th's words"}})), []))
    # An edit the copy draws on no day stays on its date (the note holds a
    # day of its own, so the copy's come back one by one, not all at once).
    cases.append(("an edit the copy draws on no day, read",
                  dict(back_on, overrides={"2026-11-30": {"body": "Rest week"}},
                       _prev=dict(copy_moved, overrides=dict(
                      copy_moved["overrides"], **{first: {"body": "Stray words"}}))), []))
    # A second turn-off gives the first one's copy its words only when the
    # note has lost words to it: a line added between stays the newest.
    cases.append(("off, a line added, on as it was, off again, Restore previous", own,
                  [off, {"do": "write", "title": "Gym", "body": body + "\nAdded later: bring water",
                         "comments": ""}, on_same, off, {"do": "restore"}]))
    # Any earlier copy but an Update's gives its words when the note has lost
    # them, whatever series it held: back on as another series, the first
    # turn-off's copy was replaced, a big delete's words in no file.
    for what, step in (("from another start", on_other),
                       ("every two weeks", {"do": "repeat", "date": "2026-11-02",
                                            "rule": {"freq": "weekly", "interval": 2}}),
                       ("ending 1 March 2027", {"do": "repeat", "date": "2026-11-02",
                                                "rule": {"freq": "weekly", "interval": 1,
                                                         "end": "2027-03-01"}})):
        cases.append(("a big delete, off, on %s, off again, Restore previous" % what, own,
                      [big_delete, off, step, off, {"do": "restore"}]))
    # A day coloured before the series' words changed holds the old words,
    # read as its own, and the copy's for it were dropped; now they are set
    # aside, a note of their own.
    stale = dict(back_on, body=body + "\nBring water.",
                 overrides={first: {"title": "Gym", "body": body, "comments": "",
                                    "color": "#ff6b6b", "category": "Work"}})
    for what, steps in (("read", []), ("read again", [{"do": "read"}]),
                        ("Restore previous", [{"do": "restore"}]),
                        ("the day reset, read again", [{"do": "reset", "day": first}, {"do": "read"}])):
        cases.append(("0.12.0 on as it was, that day coloured, then the series' words changed, %s" % what,
                      stale, steps))
    # ...and two of the copy's for one date: the note moved its day onto a
    # day off the copy's schedule, which holds words of its own.
    tie = dict(back_on, moved={"2026-11-16": "2026-11-10"},
               _prev=dict(back_on["_prev"], exceptions=[],
                          overrides={"2026-11-10": {"body": "Off-schedule words"},
                                     "2026-11-16": {"body": "Monday 16th's own"}}))
    cases.append(("two of the copy's days for one date, read", tie, []))
    cases.append(("two of the copy's days for one date, read again", tie, [{"do": "read"}]))
    # Only the lines a day lacks are added: a day grown
    # from the copy's text, or holding it mid-way, is left as it is; and a day
    # ending in a table the phone typed, with no line under it, keeps the copy's
    # table apart from its own.
    for what, mine, theirs in (
            ("a day grown from the copy's text", "Agenda.\nBook room\nCancelled", "Agenda.\nBook room"),
            ("the copy's text a line in the day's middle", "Room 4\nBook room\nAt 10", "Book room"),
            ("the copy's text the day's and a line more", "Agenda.", "Agenda.\nBook room"),
            ("a day's text ending in a table, the copy's a table",
             "Room\n| a | b |\n| --- | --- |\n| 1 | 2 |", "| x | y |\n| --- | --- |\n| 3 | 4 |"),
            # The lines the day lacks, not all after the first difference.
            ("the day's first line corrected", "Agenda (Monday).\nBook room\nCall Ann",
             "Agenda.\nBook room\nCall Ann"),
            ("a line changed in the day's middle", "Agenda.\nBook room 4\nCall Ann", "Agenda.\nBook room\nCall Ann"),
            ("a table's cell edited, its column re-padded",
             "Plan\n| Task | Who        |\n| ---- | ---------- |\n| Room | Ann (done) |\n| Food | Bob        |",
             "Plan\n| Task | Who |\n| ---- | --- |\n| Room | Ann |\n| Food | Bob |"),
            ("a checkbox ticked in a table",
             "| Done | Task |\n| --- | --- |\n| [x] | Order lunch |\n| [ ] | Call Ann |",
             "| Done | Task |\n| --- | --- |\n| [ ] | Order lunch |\n| [ ] | Call Ann |")):
        grown = dict(back_on, overrides={first: {"body": mine}},
                     _prev=dict(back_on["_prev"], exceptions=[], overrides={first: {"body": theirs}}))
        cases.append(("%s, read" % what, grown, []))
    # ...and a day ending in a table, nothing in the copy for it: stored with
    # the line under the table, as the desktop stores it.
    cases.append(("a day's text ending in a table typed on the phone, read",
                  dict(back_on, overrides={first: {"body": "Room\n| a | b |\n| --- | --- |\n| 1 | 2 |"}},
                       _prev=dict(back_on["_prev"], exceptions=[], overrides={})), []))
    # The owner's choice: a copy's day whose words a day
    # holding words lacks is set aside, to be a note of their own -- above; and
    # a copy of another series is set aside before a turn-off, a big delete or
    # an import's Update replaces it, its days with words in it alone.
    elsewhere_own = dict(back_on_elsewhere, overrides={"2026-11-16": {"body": "Swim instead"}})
    for what, steps in (("turned off", [off]), ("a big delete", [big_delete]),
                        ("a backup's Update", [{"do": "backup", "incoming": dict(
                            copy.deepcopy(elsewhere_own), body="Warm up.\nThe laptop's plan.")}]),
                        ("read again", [{"do": "read"}])):
        cases.append(("0.12.0's turn-off then on from another start, a day of its own, %s" % what,
                      elsewhere_own, steps))
    # ...nothing set aside of a day the note holds again, its series renamed
    # since: the series' words are not the day's; and a note in the Trash
    # sets its aside as it is restored.
    cases.append(("the copy's day the note holds again, its series renamed, read",
                  dict(back_on, title="Gym!", overrides={first: {"body": "Leg day - knee hurt, rest Thursday"}}), []))
    cases.append(("a day of its own over the copy's, in the Trash, restored",
                  dict(back_on, overrides={first: {"body": "Swim instead"}}), [untrash]))
    # ...a day moved onto a date its rule now gives goes home with its words,
    # a chain of moves too; a chain on dates the rule leaves free holds.
    daily = {"freq": "daily", "interval": 1}
    held = dict(base, moved={first: "2026-11-10", "2026-11-16": first},
                overrides={"2026-11-10": {"body": "Monday 9th's words"}, first: {"body": "Monday 16th's words"}})
    cases.append(("a chain of moves, read", held, []))
    cases.append(("a chain of moves, the repeat made daily, read", held,
                  [{"do": "repeat", "date": "2026-11-02", "rule": daily}, {"do": "read"}]))
    cases.append(("a day moved onto a deleted day, made daily, read",
                  dict(base, exceptions=["2026-11-10"], moved={first: "2026-11-10"},
                       overrides={"2026-11-10": {"body": "Held on Tuesday"}}),
                  [{"do": "repeat", "date": "2026-11-02", "rule": daily}, {"do": "read"}]))
    # ...and a day's words read alike: a title with a space after it, a text
    # ending in a table with the line under it or without.
    table = "Plan\n| a | b |\n| --- | --- |\n| 1 | 2 |"
    cases.append(("that day coloured, its title with a space after it, read",
                  dict(back_on, overrides={first: {"title": "Gym ", "body": body, "color": "#ff6b6b"}}), []))
    cases.append(("the copy's day only coloured, its table with the line under it, read",
                  dict(back_on, overrides={first: {"body": "Own words"}},
                       _prev=dict(back_on["_prev"], body=table, exceptions=[],
                                  overrides={first: {"title": "Gym ", "body": table + "\n",
                                                     "color": "#ff6b6b"}})), []))
    # A day goes home only where the rule still gives its
    # own date: moved onto a Thursday, the series then moved to Thursdays, its
    # words stay on the Thursday they showed on, and the move goes.
    thursday = dict(base, moved={first: "2026-11-12"}, overrides={"2026-11-12": {"body": "dentist at 4"}})
    cases.append(("a day moved onto a Thursday, the series moved to Thursdays, back again", thursday,
                  [{"do": "repeat", "date": "2026-11-05", "rule": weekly}, {"do": "read"}, on_same]))
    # ...read as 0.97.405 or 0.13.0 left it, on the phone too: its calendar
    # reads the notes as written, not the desktop's view of them.
    cases.append(("a day moved onto a date its rule now gives, read",
                  dict(base, recurrence=daily, moved={first: "2026-11-10"},
                       overrides={"2026-11-10": {"body": "Held on Tuesday"}}), []))
    cases.append(("a day moved onto a Thursday, the series on Thursdays since, read",
                  dict(thursday, date="2026-11-05"), []))
    # ...an older rule brought back by Restore previous sends a day moved since
    # home too: a share code made the daily series weekly, a day was moved.
    coded = normalize_note(dict(base, recurrence=daily))
    apply_shared_update(coded, sender(base), UPDATABLE_FROM_SHARED)
    coded = dict(json.loads(json.dumps(coded)), moved={first: "2026-11-11"},
                 overrides={"2026-11-11": {"body": "Moved day's words"}})
    cases.append(("a share code made it weekly, a day moved, Restore previous", coded,
                  [{"do": "restore"}, {"do": "read"}]))
    # The owner's choice: words never move on their
    # own. A week shifted a day at a time, the shift then made the rule: the
    # moves go, each day's words where they showed.
    mon_wed = {"freq": "weekly", "interval": 1, "weekdays": [0, 1, 2]}
    cases.append(("a week shifted a day at a time, the shift then made the rule",
                  dict(base, recurrence=mon_wed,
                       moved={"2026-11-04": "2026-11-05", "2026-11-03": "2026-11-04", "2026-11-02": "2026-11-03"},
                       overrides={"2026-11-05": {"body": "WED"}, "2026-11-04": {"body": "TUE"},
                                  "2026-11-03": {"body": "MON"}}),
                  [{"do": "repeat", "date": "2026-11-02", "rule": dict(mon_wed, weekdays=[1, 2, 3])}, {"do": "read"}]))
    # ...a weekly series turned back on as it was, its Monday lit before and
    # not after, is the same series: its deleted day stays deleted.
    lit = dict(own, recurrence={"freq": "weekly", "interval": 1, "weekdays": [0]})
    cases.append(("Monday lit, off, on again with no weekday lit", lit, [off, on_same, {"do": "read"}]))
    # ...an Update's copy of another series, as a restore leaves it, sets its
    # days with words aside when a big delete replaces it.
    upd_copy = dict(base, recurrence=daily, date="2026-11-04",
                    _prev={"title": "Gym", "body": body, "comments": "", "formatting": [], "paragraphs": [],
                           "category": "Work", "color": "", "date": "2026-11-02", "recurrence": weekly,
                           "exceptions": [], "overrides": {"2026-11-22": {"body": "Room W2"}}, "moved": {}})
    cases.append(("an Update's copy of another series, then a big delete", upd_copy, [big_delete]))
    # The desktop's rule: an Update's copy is the note as it was
    # before the Update, in no other file, and a big delete keeps it; the
    # words the delete takes become a note of their own, gone again when the
    # note holds them once more -- in the same session, not after a start.
    laptop = body + "\nFrom the laptop: book the physio for Friday, and the bike at 7."
    shared = {"do": "share", "incoming": sender(base, body=laptop)}
    typed_back = {"do": "write", "title": "Gym", "body": laptop, "comments": ""}
    cases.append(("a share code's Update, then a big delete", base, [shared, big_delete]))
    cases.append(("a share code's Update, a big delete, the words typed back", base,
                  [shared, big_delete, typed_back]))
    cases.append(("a share code's Update, a big delete, read, the words typed back", base,
                  [shared, big_delete, {"do": "read"}, typed_back]))
    one_off = dict(renovation, date="2026-11-04")
    cases.append(("a share code's Update of a one-off, then a big delete", one_off,
                  [{"do": "share", "incoming": sender(one_off, body=one_off["body"] + " And paint the hall.")},
                   {"do": "write", "title": "Renovation", "body": "Tiles.", "comments": ""}]))
    remarks = dict(base, comments="Knee: ask the physio whether the bike is fine before Friday's session.")
    cases.append(("a share code's Update, then the comments cleared", remarks,
                  [{"do": "share", "incoming": sender(remarks, body=laptop)},
                   {"do": "write", "title": "Gym", "body": laptop, "comments": ""}]))
    # ...and a backup's Update counts the days it brings as held.
    held_back = dict(base, recurrence=daily, date="2026-11-04",
                     _prev={"title": "Gym", "body": body, "comments": "", "formatting": [], "paragraphs": [],
                            "date": "2026-11-02", "recurrence": weekly, "exceptions": [], "moved": {},
                            "overrides": {first: {"body": "ROOM 4B"}}})
    # ...a turn-off keeps an import's Update copy of another series whole,
    # its days in it, nothing set aside.
    cases.append(("a backup of another start, the repeat turned off, Restore previous", own,
                  [{"do": "backup", "incoming": dict(copy.deepcopy(own), date="2026-11-03",
                                                     overrides={"2026-11-10": {"body": "PHONE TUESDAY"}})},
                   off, {"do": "restore"}]))
    cases.append(("a backup's Update bringing the copy's day words back", held_back,
                  [{"do": "backup", "incoming": dict(copy.deepcopy(base), body="Warm up, from the laptop.",
                                                     overrides={first: {"body": "ROOM 4B"}})}]))
    # The owner's choice: a day the note deleted stays
    # deleted, but the copy's words for it are set aside -- 0.13.0 turned off
    # and on as it was showed that day blank, its words in the copy alone.
    cases.append(("0.12.0's turn-off then on as it was, the blank-looking day deleted, read",
                  dict(back_on, exceptions=[first]), [{"do": "read"}]))
    # 0.13.0 leaves two moves onto one date (a day
    # moved, the repeat made every two weeks, another day moved onto the same
    # date); the read keeps the one the calendar draws, whichever the file
    # lists first -- parity once rested on one hand-written case a side.
    fortnight = dict(weekly, interval=2)
    for order, moves in (("the live move first", [("2026-11-16", "2026-11-18"), ("2026-11-09", "2026-11-18")]),
                         ("the dead move first", [("2026-11-09", "2026-11-18"), ("2026-11-16", "2026-11-18")])):
        cases.append(("two moves onto one date, %s, read" % order,
                      dict(base, recurrence=fortnight, moved=dict(moves),
                           overrides={"2026-11-18": {"body": "Budget review"}}), [{"do": "read"}]))
        cases.append(("two moves onto one date, %s, Restore previous" % order,
                      dict(base, recurrence=fortnight, moved=dict(moves),
                           overrides={"2026-11-18": {"body": "Budget review"}},
                           _prev={"title": "Gym", "body": "Earlier plan", "comments": "", "formatting": [],
                                  "paragraphs": []}), [{"do": "restore"}, {"do": "read"}]))
    # ...and a copy of another series asks for each day where the note holds
    # it now: moved since, a day's words are held, not set aside as earlier.
    cases.append(("on from another start, the copy's day moved since, turned off",
                  dict(base, date="2026-10-26", moved={first: "2026-11-11"},
                       overrides={"2026-11-11": {"body": "legs + towel"}},
                       _prev={"title": "Gym", "body": body, "comments": "", "formatting": [], "paragraphs": [],
                              "date": "2026-11-02", "recurrence": weekly, "exceptions": [],
                              "overrides": {first: {"body": "legs + towel"}}, "moved": {}}),
                  [off]))

    def aside(note):
        """The notes the desktop makes of *note*'s days set aside
        (_set_aside_days and the load, through the core's settle_set_aside):
        each through normalize_note, as it makes them."""
        return [{key: made[key] for key in ("title", "body", "comments", "category", "color", "date")}
                for made in (normalize_note(dict(fresh, id=0))
                             for fresh in notes_set_aside(json.loads(json.dumps(note))))]

    def made_after(note, timeline):
        """The notes made so far, in the order the desktop adds them: the
        words kept aside during a save, then the days its write sets aside
        -- a note kept aside and spent again gone from the list."""
        days = aside(note)
        timeline[:] = [(kind, it) for kind, it in timeline
                       if (kind == "kept" and any(it is entry for entry in kept)) or (kind == "day" and it < len(days))]
        timeline += [("kept", entry) for entry in kept if not any(it is entry for _k, it in timeline)]
        timeline += [("day", i) for i in range(sum(1 for kind, _i in timeline if kind == "day"), len(days))]
        if not any(kind == "kept" for kind, _it in timeline):
            return days
        return [{key: it[1][key] for key in ("title", "body", "comments", "category", "color", "date")}
                if kind == "kept" else days[it] for kind, it in timeline]

    out = []
    for name, start, steps in cases:
        # As the desktop's load leaves a note: through normalize_note.
        note = normalize_note(copy.deepcopy(start))
        after = []
        aside_after = []
        kept.clear()
        timeline = []
        for step in steps:
            if step["do"] == "repeat":
                save(note, note.get("title", ""), note.get("body", ""), note.get("comments", ""),
                     step["date"], step["rule"])
            elif step["do"] == "write":
                save(note, step["title"], step["body"], step["comments"], note.get("date", ""),
                     note.get("recurrence"), step.get("formatting"))
            elif step["do"] == "untrash":
                # The note lay in the Trash as written; restored as the
                # desktop's Trash page restores it.
                entry = dict(copy.deepcopy(start), deleted_at="2026-10-27 10:00:00")
                note = restore_many_from_trash([], [entry], [entry])[2][0]
            elif step["do"] == "read":
                # The notebook read again from its file, at the next start.
                note = normalize_note(json.loads(json.dumps(note)))
                for entry in kept:
                    entry[0] = None
            elif step["do"] == "restore":
                restore(note)
            elif step["do"] == "reset":
                reset(note, step["day"])
            elif step["do"] == "share":
                imported(note, step["incoming"], UPDATABLE_FROM_SHARED)
            elif step["do"] == "backup":
                imported(note, step["incoming"], UPDATABLE_FROM_BACKUP)
            after.append(json.loads(json.dumps(note)))
            aside_after.append(made_after(after[-1], timeline))
        loaded = json.loads(json.dumps(normalize_note(copy.deepcopy(start))))
        out.append({"name": name, "note": start, "steps": steps,
                    "loaded": loaded, "aside_loaded": aside(loaded),
                    "after": after, "aside_after": aside_after,
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
