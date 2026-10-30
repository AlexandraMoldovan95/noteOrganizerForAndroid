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
import can be a notebook -- and under the desktop's own memory ceiling
(`desktop_guest`).

The cases since the seventh round: a 0.12.0 Trash entry restored, then
rewritten; 0.12.0's turn-off then turn-on as it was; an update's copy of a
series, which never moves; the same backup imported twice; the comments
rewritten over a big delete's copy; "Restore previous" after comments were
typed, and after the same words' styles changed.

And since the eighth (the desktop's 947e9d3): a day reset after a series is
turned back on as its copy's series, read again -- the copy's days spent --
by the editor's turn-on and by 0.12.0's notebook read; the backup made
before the reset, compared without either side's copy (an import asks
before it updates, as the desktop's _known_note_differs does); 0.12.0's own
backup imported where the move ran; a turn-off over a share code's Update
copy, which it keeps; and two letters of comments cleared after a long
comment was deleted.

And since the ninth (the desktop's 8b5d6cc): the copy's days taken back
into the note per date before they are spent -- 0.12.0 turned on as it
was, then another day edited, coloured, deleted or moved; the note's own
day over the copy's; the copy's moves onto dates the note's own move
holds; turned on from another start, then the start put back; back to the
first start with another rule in one Save -- a second turn-off over the
first one's copy of the same series, after a big delete and after the
comments were cleared; and an arriving note read as a load reads it, its
copy's days taken back, before both are compared bare.

And since the tenth (the desktop's round-ten data fix): a copy's day is
dropped only where the note deleted that day or wrote words of its own for
it -- 0.12.0 turned on as it was, then that very day moved, coloured (the
desktop's patch and the phone's), or moved with another put on its date;
the copy's moved day where the note moved another onto its date, coloured
it where it stood, or deleted it; a copy's move onto a day whose own move
stays behind; an edit the copy draws on no day; a second turn-off after
a line was added, which keeps the newest words; and a big delete, the repeat
off, on as another series (another start, every two weeks, an end date) and
off again, which keeps the deleted words.

    py tools/make_days_fixture.py
    NOTEORGANIZER_HOME=... py tools/make_days_fixture.py

Reads the desktop's Python and writes only this repository's fixture.
"""
import copy
import datetime
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
                              "3a2c7065e86642a5c8d22de46d4d6ddaa50ae335"),
    "save_current_note's series branch": (
        '                rescue_due = rescue_copy_due(note.get("title", ""), note.get("body", ""),',
        '                    note["moved"] = {}\n', "ee189b1fd26d1d315d9195c72bfa41faa2336c9f"),
    "restore_previous_version's write": ("        replaced = copy_replaced_by_restore(note, prev)\n",
                                         '            note["_prev"] = replaced\n',
                                         "307ce489636ae9adce9c88017b5f7d12b8769271"),
    "_reset_occurrence_override's write": ('        note["overrides"].pop(occurrence_date, None)\n',
                                           '            note["moved"].pop(origin, None)\n',
                                           "41d1e6cd94035d59b2b505b7a1dd709896ed5dc5"),
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
    sys.path.insert(0, HERE)
    from desktop_guest import settle
    settle(DESKTOP)
    isolate()
    sys.path.insert(0, DESKTOP)
    from noteorganizer.core.model import (apply_shared_update, comments_kept_by_copy, copy_holds_this_series,
                                          copy_replaced_by_restore, days_hold_own_text,
                                          kept_days_fit, normalize_note, rescue_copy_due,
                                          rescue_copy_spent, shared_update_changes,
                                          spend_series_days, update_copy, DAY_FIELDS,
                                          UPDATABLE_FROM_BACKUP, UPDATABLE_FROM_SHARED)
    from noteorganizer.core.recurrence import is_recurring, normalize_recurrence
    from noteorganizer.core.formatting import normalize_formatting, normalize_paragraphs
    from noteorganizer.core.storage import restore_many_from_trash
    from noteorganizer.core.tables import with_caret_line
    import noteorganizer.core.model as desktop_model

    # The desktop stamps "updated" from the clock, and the parity judges no
    # stamp: on a fixed clock a rebuild writes the same fixture, so one that
    # changes it is a rule that changed (the stamps alone changed it before).
    class FixedClock(datetime.datetime):
        @classmethod
        def now(cls, tz=None):
            return cls(2026, 10, 29, 12, 0, 0, tzinfo=tz)

    desktop_model.datetime = FixedClock

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
        turn-off, which the phone does not have: its held branch is never
        taken here."""
        prev = whole_note_copy(note)
        existing = note.get("_prev")
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
            if comments_alone:
                note["_prev"]["comments"] = comments_kept_by_copy(
                    note["_prev"].get("comments", ""), note.get("comments", ""))
        elif rescue_due:
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
        spend_series_days(note)
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
    # The eighth round's: a series whose one day of its own is the only day it
    # holds -- reset, it holds none, the state the copy's days came back into.
    one_day = dict(base, overrides={"2026-11-09": {"body": "Leg day - knee hurt, rest Thursday"}})
    back_on_one = dict(back_on, _prev=dict(back_on["_prev"], exceptions=[]))
    # The seventh round's comments: a long comment beside a paragraph.
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
        # The seventh round. A Trash entry is read when it is restored.
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
        # The eighth round. A series' own copy's days are spent once the note
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
    # The ninth round (the desktop's 8b5d6cc). The copy's days come back to
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
    # The tenth round (the desktop's round-ten data fix). A date the note holds
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
                     note.get("recurrence"), step.get("formatting"))
            elif step["do"] == "untrash":
                # The note lay in the Trash as written; restored as the
                # desktop's Trash page restores it.
                entry = dict(copy.deepcopy(start), deleted_at="2026-10-27 10:00:00")
                note = restore_many_from_trash([], [entry], [entry])[2][0]
            elif step["do"] == "read":
                # The notebook read again from its file, at the next start.
                note = normalize_note(json.loads(json.dumps(note)))
            elif step["do"] == "restore":
                restore(note)
            elif step["do"] == "reset":
                reset(note, step["day"])
            elif step["do"] == "share":
                imported(note, step["incoming"], UPDATABLE_FROM_SHARED)
            elif step["do"] == "backup":
                imported(note, step["incoming"], UPDATABLE_FROM_BACKUP)
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
