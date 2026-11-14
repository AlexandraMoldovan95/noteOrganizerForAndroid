package ro.noteorganizer.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * The open note and everything else that can reach it. On a phone the
 * editor covers the screen; on a wide one the list stays beside it, and
 * the list can pin, move, copy or share the very note the editor holds.
 *
 * The editor keeps its own copy of every field and writes them all back
 * when it saves, so the order is the whole rule: what it holds is saved
 * BEFORE anything else touches the note, and it reads the note back
 * AFTER anything else wrote it. Here the editor is stood in for by its
 * flush, which records what the file held at the moment it was asked --
 * the one thing a tap through the emulator cannot time.
 */
class AppStateTest {

    private fun state(): AppState {
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "A", "body": "first", "category": "General", "date": "2026-10-05"},
                {"id": 2, "uid": "b", "title": "B", "body": "second", "category": "General"}]""")
        File(dir, "attachments").mkdirs()
        return AppState(dir, CoroutineScope(Dispatchers.Unconfined))
    }

    @Test
    fun `a refused write is said with what went unsaved, not the bare cause, nor twice`() {
        // A tick on a full phone said only "the phone's storage refused the write"; a day's
        // edit said "Not saved: Your notes could not be saved: …".
        val s = state()
        File(s.dir, "notes_data.json.tmp").mkdir()
        assertFalse(s.report(Notebook.createNote(s.load, "New", "words")))
        assertEquals("Not saved: the phone's storage refused the write.", s.snackbar.currentSnackbarData?.visuals?.message)
        s.snackbar.currentSnackbarData?.dismiss()
        // The refused write took its empty .tmp folder away with it.
        File(s.dir, "notes_data.json.tmp").mkdir()
        val day = Days.setRepeat(s.load, 1, null) as SaveResult.Failed
        assertEquals(Files.STORAGE_REFUSED, day.reason)
        // ...and a delete the Trash refused keeps its cause: "try again in a moment" could not help.
        File(s.dir, "trash.json.tmp").mkdir()
        val trashed = Organizing.trashNotes(s.load, listOf(2)) as SaveResult.Failed
        assertEquals("The Trash could not be saved: the phone's storage refused the write. Nothing was deleted.", trashed.reason)
    }

    @Test
    fun `a note's folds go with it, and a new note's become its own once saved`() {
        val s = state()
        s.folded["2:text"] = true
        s.folded["1:comments"] = true
        s.trash(listOf(2))
        assertNull("a deleted note's fold is no next note's", s.folded["2:text"])
        assertEquals(true, s.folded["1:comments"])
        // Folded while new, then saved as 3: the fold is note 3's, no later new note's.
        s.folded["0:checklist"] = true
        s.newNoteSaved(3)
        assertNull(s.folded["0:checklist"])
        assertEquals(true, s.folded["3:checklist"])
        // Made from a calendar day, the list is told of it too, to say so
        // where it hides it: unsaid, it was nowhere on it.
        s.newNoteMade = null
        s.tab = Tab.CALENDAR
        s.newNoteSaved(4)
        assertEquals(4, s.newNoteMade)
    }

    @Test
    fun `the calendar goes with a day moved from the editor on it, and back with its Undo`() {
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.selectedDay = "2026-10-12"
        s.calendarMonth = java.time.YearMonth.of(2026, 10)
        s.openNote(1, "2026-10-12")
        s.moveDay(1, "2026-10-12", "2026-11-03")
        assertEquals("2026-11-03", s.selectedDay)
        assertEquals(java.time.YearMonth.of(2026, 11), s.calendarMonth)
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("2026-10-12", s.selectedDay)
        assertEquals(java.time.YearMonth.of(2026, 10), s.calendarMonth)
    }

    @Test
    fun `the calendar goes home with a moved day reset`() {
        // Reset this day moves the day too, and the calendar stayed on the date
        // it left, the card gone from it.
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.selectedDay = "2026-10-12"
        s.calendarMonth = java.time.YearMonth.of(2026, 10)
        s.openNote(1, "2026-10-12")
        s.moveDay(1, "2026-10-12", "2026-11-03")
        s.resetDay(1, "2026-11-03", dropText = false)
        assertEquals("2026-10-12", s.selectedDay)
        assertEquals(java.time.YearMonth.of(2026, 10), s.calendarMonth)
        assertEquals("2026-10-12", s.openDay)
    }

    @Test
    fun `a note gone by the time it is opened is said, and the note open stays`() {
        // An '(earlier text)' note spent by the very save that leaves the open
        // note: an empty new note opened in its place.
        val s = state()
        s.openNote(1)
        s.flush = { null }
        s.openNote(99)
        assertEquals(1, s.openId)
        assertEquals("That note is no longer here", s.snackbar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun `a note kept aside taken back takes its own bars, shown or waiting, and no other`() {
        // The bar on screen dismissed, whatever it was: another note kept
        // aside lost its Show and this one's then showed, or another
        // action's Undo went.
        val s = state()
        val gone = "The words are back in the note; the note kept aside of them is gone again"
        s.offerAbout(listOf(1), "Kept 1", "Show") {}
        s.offerAbout(listOf(2), "Kept 2", "Show") {}
        s.asidesSpent(listOf(2))
        assertEquals("Kept 1", s.snackbar.currentSnackbarData?.visuals?.message)
        s.snackbar.currentSnackbarData!!.dismiss()
        assertEquals("the one waiting went with its note", gone, s.snackbar.currentSnackbarData?.visuals?.message)
        s.snackbar.currentSnackbarData!!.dismiss()
        s.offerAbout(listOf(1), "Kept 1", "Show") {}
        s.trash(listOf(2))
        s.asidesSpent(listOf(1))
        assertEquals("an Undo on screen stays", "Undo", s.snackbar.currentSnackbarData?.visuals?.actionLabel)
        s.snackbar.currentSnackbarData!!.dismiss()
        assertEquals(gone, s.snackbar.currentSnackbarData?.visuals?.message)
        // ...and the list's word on a note gone, not yet said: said later, it
        // named the next new note, given the same id.
        s.arrivals = listOf(AppState.Word(listOf(1, 7), "Kept aside"))
        s.refresh()
        assertEquals(listOf(AppState.Word(listOf(1), "Kept aside")), s.arrivals)
        s.trash(listOf(1))
        assertTrue(s.arrivals.isEmpty())
        // ...and the new note's word the same way: it lent its id, and its
        // "Saved in", to the next note restored or imported.
        s.newNoteMade = 7
        s.refresh()
        assertNull(s.newNoteMade)
    }

    @Test
    fun `an Undo takes the bar at once, and the offers waiting come after it, asked again`() {
        // Behind a note's Show it waited ten seconds; thrown away, a hidden note's
        // word was never said. The bar on screen is replaced, as the desktop's
        // banner is, and what waited is asked again once the Undo is done.
        val s = state()
        var asked = 0
        s.offer("Exported", "Open") {}
        s.offer("Saved, hidden", "Show all") {}
        s.offerAbout(listOf(2), "About B", "Show") {}
        s.offerAbout(listOf(1), "Kept aside, hidden", "Show all", again = { asked += 1 }) {}
        s.trash(listOf(2))
        assertEquals("Undo", s.snackbar.currentSnackbarData?.visuals?.actionLabel)
        assertEquals("held back until it is done", 0, asked)
        s.snackbar.currentSnackbarData!!.dismiss()
        assertEquals("the list's word is asked again, not shown as made", 1, asked)
        assertEquals("Saved, hidden", s.snackbar.currentSnackbarData?.visuals?.message)
        s.snackbar.currentSnackbarData!!.dismiss()
        assertNull("the bar on screen went, and none about a note gone", s.snackbar.currentSnackbarData)
    }

    @Test
    fun `the list's words wait in turn, by name, and a new note's survives the next one and its own Undo`() {
        // One slot each: a later word wrote over an earlier one, never said,
        // and the first of two new notes in a row went unsaid; a new note
        // deleted from the Calendar and put back lost its word.
        val s = state()
        s.waitWord(listOf(1), "Put back")
        s.waitWord(listOf(2), "Kept aside")
        assertEquals(listOf(AppState.Word(listOf(1), "Put back"), AppState.Word(listOf(2), "Kept aside")), s.arrivals)
        s.arrivals = emptyList()
        s.noteMade(1)
        s.noteMade(2)
        assertEquals(2, s.newNoteMade)
        assertEquals(listOf("“A” saved in General"), s.wordsNow())
        assertEquals(listOf(1), s.arrivals.single().ids)
        s.tab = Tab.CALENDAR
        s.trash(listOf(2))
        assertNull(s.newNoteMade)
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("B", s.newNoteMade?.let { s.note(it)?.title })
        // ...and a word already in the queue, too.
        s.trash(listOf(1))
        assertTrue(s.arrivals.isEmpty())
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals(listOf("“A” saved in General"), s.wordsNow())
        assertEquals("A", s.note(s.arrivals.single().ids.single())?.title)
    }

    @Test
    fun `a word on several notes is given back whole by one's Undo, on any tab`() {
        // Given back beside the part still waiting, the same news came twice; on
        // the Notes tab it was not given back, and "Put back" followed it, or,
        // taken meanwhile on two panes, the list's own reading missed it.
        for (tab in listOf(Tab.CALENDAR, Tab.NOTES)) {
            val s = state()
            s.arrivals = listOf(AppState.Word(listOf(1, 2), "Added 2 notes"))
            s.tab = tab
            s.trash(listOf(1))
            assertEquals(listOf(AppState.Word(listOf(2), "Added 2 notes")), s.arrivals)
            s.snackbar.currentSnackbarData!!.performAction()
            assertEquals(listOf("Added 2 notes"), s.wordsNow())
            assertEquals("$tab", setOf("A", "B"), s.arrivals.single().ids.mapNotNull { s.note(it)?.title }.toSet())
            if (tab == Tab.NOTES) assertEquals("the Undo decides it, not the list", emptySet<Int>(), s.revealAfterUndo!!.unsaid)
        }
    }

    @Test
    fun `an arrival's notes and its earlier text are said each under its own word, one by name`() {
        // An import's '(earlier text)' notes came under "Added N notes",
        // then under no word at all, and a restore's
        // still under "Restored".
        val s = state()
        val before = s.noteIds()
        assertTrue(s.report(Notebook.createNote(s.load, "New one", "")))
        assertTrue(s.report(Notebook.createNote(s.load, "Its earlier text", "")))
        s.arrived(before, 1, "added", "Added 1 notes")
        assertEquals(listOf("“New one” added", "“Its earlier text” kept as a note of its own"), s.wordsNow())
        s.arrivals = emptyList()
        s.arrived(before, 0, "added", "Added 0 notes")
        assertEquals(listOf("Earlier text kept as 2 notes of their own"), s.wordsNow())
    }

    @Test
    fun `a word about one note names it as it is when said, renamed or refiled meanwhile`() {
        // Named when queued, a note renamed before the list said it was said by a
        // name no note had.
        val s = state()
        s.wordOn(listOf(1), "restored", "Restored 1 notes")
        s.noteMade(2)
        s.noteMade(1)
        assertTrue(s.report(Notebook.updateNote(s.load, 1, "Sourdough", "first")))
        assertTrue(s.report(Notebook.updateNote(s.load, 2, "Rye", "second", category = "Work")))
        assertEquals(listOf("“Sourdough” restored", "“Rye” saved in Work"), s.wordsNow())
        assertEquals(listOf(true, true), s.arrivals.map { it.named })
        assertTrue(s.report(Organizing.trashNotes(s.load, listOf(1))))
        assertNull("a note gone says nothing", s.wordText(AppState.Word(listOf(1), "restored", named = true)))
    }

    /** What each waiting word says now. */
    private fun AppState.wordsNow(): List<String?> = arrivals.map { wordText(it) }

    @Test
    fun `words are said one at a time, and one about one note names it`() {
        // Merged, two restores' words doubled a count the text carried,
        // or left the second note's folded group unsaid; each
        // as made and unnamed, two bars said the same news.
        val s = state()
        s.tab = Tab.SETTINGS
        for (id in listOf(1, 2)) {
            s.trash(listOf(id))
            s.snackbar.currentSnackbarData!!.dismiss()
        }
        for (entry in s.load.trash.toList()) s.restoreFromTrash(listOf(Organizing.keyOf(entry)))
        assertEquals(listOf("“A” restored", "“B” restored"), s.wordsNow())
        assertEquals("“A” restored", s.takeWord()?.let { s.wordText(it) })
        assertEquals("“B” restored", s.takeWord()?.let { s.wordText(it) })
        assertNull(s.takeWord())
        // A word about several, trimmed to one by a delete, is still counted.
        val both = s.load.notes.map { it.id }
        s.wordOn(both, "restored", "Restored 2 notes")
        assertTrue(s.report(Organizing.trashNotes(s.load, listOf(both[1]))))
        assertEquals(listOf(both[0]), s.arrivals.single().ids)
        assertFalse(s.arrivals.single().named)
    }

    @Test
    fun `a failed import that kept categories shows them, read from the files`() {
        // The screens read the notebook from before the import: the Trash page
        // showed none of a Trash it said it kept, and a category added next
        // wrote over the categories it said it kept.
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "a", "title": "A", "body": "first"}]""")
        File(dir, "settings.json").writeText("{}")
        File(dir, "attachments").mkdirs()
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        File(dir, "notes_data.json.tmp").mkdir()      // the notes' own write fails
        val plan = Importing.plan(s.load.raw!!, listOf(JSONObject("""{"uid": "n1", "title": "New"}""")),
            Importing.Source.BACKUP)
        finishImport(s, plan, Importing.Answers(), File(dir, "attachments"),
            Importing.Extras(categories = JSONObject("""{"Travel": "#336699"}""")))
        assertTrue("the category it kept is in the list: ${s.load.categories.keys}", "Travel" in s.load.categories)
        assertEquals("and no note came in", listOf("A"), s.load.notes.map { it.title })
    }

    @Test
    fun `text shared in becomes a note even while the open note cannot be saved`() {
        // Held for "Leave anyway", the words lived in that bar alone: its ✕,
        // its ten seconds or an Undo dropped them.
        val s = state()
        s.openNote(1)
        s.flush = { "the disk is full" }
        s.noteFromShared("Shared words\nand more", "Saved as a new note")
        assertEquals(1, s.openId)
        assertEquals(listOf("and more"), s.load.notes.filter { it.title == "Shared words" }.map { it.body })
    }

    @Test
    fun `the offers an Undo held back outlive a second Undo, and come after the last`() {
        val s = state()
        s.say("one")
        s.offer("Saved, hidden", "Show all") {}
        s.trash(listOf(1))
        s.trash(listOf(2))
        s.snackbar.currentSnackbarData!!.performAction()
        // By title: put back, a note may be given another id.
        assertEquals("the Undo shown is the last delete's", listOf("B"), s.rawNotes().map { it.optString("title") })
        assertEquals("Saved, hidden", s.snackbar.currentSnackbarData?.visuals?.message)
    }

    @Test
    fun `an earlier Undo still waiting its turn gives way to the next`() {
        val s = state()
        s.say("one")
        s.say("two")
        s.trash(listOf(1))
        s.trash(listOf(2))
        s.snackbar.currentSnackbarData!!.performAction()
        // By title: put back, a note may be given another id.
        assertEquals("the Undo shown is the last delete's", listOf("B"), s.rawNotes().map { it.optString("title") })
    }

    @Test
    fun `a moved day's Undo reads back only the editor on that day`() {
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1)
        s.flush = { null }
        s.moveDay(1, "2026-10-12", "2026-10-13")
        val rewritten = s.openNoteRewritten
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("the whole series shows no day", rewritten, s.openNoteRewritten)
    }

    @Test
    fun `Leave anyway tapped once another note is open sets no day on it`() {
        // It set the day on whatever note was open, a plain one "on a day",
        // where every save was refused.
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1)
        s.flush = { "the disk is full" }
        s.flushStaying = { "the disk is full" }
        s.openNote(1, "2026-10-12")
        val offer = s.snackbar.currentSnackbarData!!
        assertEquals("Leave anyway", offer.visuals.actionLabel)
        s.flush = { null }
        s.flushStaying = { null }
        s.openNote(2)
        offer.performAction()
        assertEquals(2, s.openId)
        assertNull(s.openDay)
    }

    @Test
    fun `the open note is read back only when a change or an import wrote it`() {
        val s = state()
        s.openNote(1)
        s.flush = { null }
        val rewritten = s.openNoteRewritten
        // A day's delete refused -- note 1 repeats not -- wrote nothing.
        s.deleteDay(1, "2026-10-05")
        assertEquals(rewritten, s.openNoteRewritten)
        // An import that never touched it...
        assertTrue(s.beforeImport())
        s.afterImport()
        assertEquals(rewritten, s.openNoteRewritten)
        // ...and one that did.
        assertTrue(s.beforeImport())
        assertTrue(s.report(Notebook.updateNote(s.load, 1, "A", "from the code")))
        s.afterImport()
        assertEquals(rewritten + 1, s.openNoteRewritten)
        // On the very day open, a delete refused -- the series ended behind
        // the editor -- answers in a pair, and wrote nothing.
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1, "2026-10-12")
        assertTrue(s.report(Days.setRepeat(s.load, 1, JSONObject("""{"freq": ""}"""))))
        val before = s.openNoteRewritten
        s.deleteDay(1, "2026-10-12")
        assertEquals(before, s.openNoteRewritten)
    }

    @Test
    fun `the notes kept aside this session outlive a read of the notebook from the disk`() {
        val s = state()
        s.load.keptAside["a"] = mutableListOf(Notebook.KeptAside("x", mapOf("body" to "first"), "look"))
        s.reload()
        assertEquals("x", s.load.keptAside["a"]?.single()?.uid)
    }

    @Test
    fun `the day turning with the app open moves today, drops the earlier day's bar and asks for the new one's`() {
        // Read once, today stayed on the day the app opened: past midnight the
        // calendar's ring, "Calendar: soonest" and the bar kept to yesterday.
        val state = state()
        state.dayIs("2026-10-04")
        assertEquals("2026-10-04", state.today)
        assertNull("no note on the 4th", state.reminderDue)
        state.dayIs("2026-10-05")
        assertEquals("2026-10-05", state.today)
        assertEquals("the new day's bar", 1, state.reminderDue)
        assertEquals("2026-10-05", state.settings.opt(Reminders.LAST_SHOWN))
        state.dayIs("2026-10-06")
        assertEquals("2026-10-06", state.today)
        assertNull("the 5th's bar goes with the 5th", state.reminderDue)
    }

    @Test
    fun `a Trash window chosen ends one lost with an unreadable file`() {
        val state = state()
        state.putSetting(Settings.PHONE_TRASH_WINDOW_UNKNOWN, true)
        assertNull(state.load.settings?.let { Settings.purgeWindow(it) })
        assertTrue(state.chooseTrashWindow(60))
        val written = JSONObject(File(state.dir, "settings.json").readText())
        assertEquals(60, written.opt(Settings.RETENTION_DAYS))
        assertFalse("the mark is taken off", written.has(Settings.PHONE_TRASH_WINDOW_UNKNOWN))
        assertEquals(60, state.load.settings?.let { Settings.purgeWindow(it) })
    }

    @Test
    fun `a notebook that followed the phone keeps the theme it wears, once`() {
        val state = state()
        state.putSetting(Settings.PHONE_DARK_THEME, "dark_ocean")
        state.settleTheme(phoneIsDark = true)
        val written = JSONObject(File(state.dir, "settings.json").readText())
        assertEquals("the theme worn is the one kept", "dark_ocean", written.opt(Settings.THEME))
        assertEquals(false, written.opt(Settings.PHONE_FOLLOWS_PHONE))
        // The phone turned light afterwards: the app no longer turns with it,
        // and settling again does nothing.
        state.settleTheme(phoneIsDark = false)
        assertEquals("dark_ocean", state.themeName(phoneIsDark = false))
    }

    @Test
    fun `the welcome note's Show finds it by its own words when its uid was not kept`() {
        // That write refused on a full phone, every press added another welcome note.
        val state = state()
        showWelcome(state)
        val count = state.load.notes.size
        state.putSetting(Settings.WELCOME_NOTE_UID, null)
        // ...a later start (nothing in memory) finds it by its words...
        val later = AppState(state.dir, CoroutineScope(Dispatchers.Unconfined))
        showWelcome(later)
        assertEquals("the same welcome note opened, none added", count, later.load.notes.size)
        // ...and this run, after its words were changed, by the uid it kept.
        val welcome = state.load.notes.last()
        assertTrue(state.report(Notebook.updateNote(state.load, welcome.id, welcome.title, "changed")))
        showWelcome(state)
        assertEquals("found by the uid this run kept", count, state.load.notes.size)
        // ...and over a stale uid in settings (its note deleted, the new uid's write
        // refused), by this run's: the stale one won, and Show added a second.
        state.putSetting(Settings.WELCOME_NOTE_UID, "a-welcome-note-deleted-since")
        showWelcome(state)
        assertEquals("found by this run's uid over a stale one", count, state.load.notes.size)
    }

    @Test
    fun `over a settings file held, the theme is not written at each start, and the reminder bar shows once a run`() {
        val state = state()
        Files.holdWrites(File(state.dir, "settings.json"))
        // The theme kept is refused over a held file, and was said at every activity made.
        state.settleTheme(phoneIsDark = true)
        assertNull("nothing asked of a held file, nothing said", state.snackbar.currentSnackbarData)
        // The bar's mark cannot be written: it came back at every return to the app.
        state.checkReminder("2026-10-05")
        assertEquals(1, state.reminderDue)
        state.reminderDue = null
        state.checkReminder("2026-10-05")
        assertNull("once a run, its mark held in memory", state.reminderDue)
        // ...and once the notification opened the day, the bar does not say it again.
        val tapped = state()
        Files.holdWrites(File(tapped.dir, "settings.json"))
        tapped.showToday("2026-10-05")
        tapped.checkReminder("2026-10-05")
        assertNull("the notification's day is the bar's, held in memory too", tapped.reminderDue)
    }

    @Test
    fun `Light and Dark go back to the last theme worn in each mode`() {
        val state = state()
        state.settleTheme(phoneIsDark = false)
        assertEquals("light", state.themeName(phoneIsDark = false))
        assertTrue(state.chooseMode(dark = true))
        assertEquals("the original dark one, never chosen before", "dark", state.themeName(false))
        assertTrue(state.chooseTheme("dark_forest"))
        assertTrue(state.chooseMode(dark = false))
        assertEquals("light", state.themeName(false))
        assertTrue(state.chooseTheme("light_warm"))
        assertTrue(state.chooseMode(dark = true))
        assertEquals("the dark one worn last", "dark_forest", state.themeName(false))
        assertTrue(state.chooseMode(dark = false))
        assertEquals("the light one worn last", "light_warm", state.themeName(true))
    }

    @Test
    fun `a cleared day's Undo never paints over what was chosen since`() {
        // Cleared red, painted green within the Undo's seconds, Undo tapped:
        // the green stays, as the desktop's _undo_day_clear refuses.
        val state = state()
        state.paintDay("2026-10-24", "#ff0000")
        state.paintDay("2026-10-24", "")
        state.paintDay("2026-10-24", "#00ff00")
        assertFalse(state.undoDayClear("2026-10-24", colour = "#ff0000"))
        assertEquals("#00ff00", state.dayColours["2026-10-24"])
        // Still empty, it is put back.
        state.paintDay("2026-10-25", "#ff0000")
        state.paintDay("2026-10-25", "")
        assertTrue(state.undoDayClear("2026-10-25", colour = "#ff0000"))
        assertEquals("#ff0000", state.dayColours["2026-10-25"])
        // The markers the same.
        state.markDay("2026-10-26", listOf("⭐"))
        state.markDay("2026-10-26", emptyList())
        state.markDay("2026-10-26", listOf("🎂"))
        assertFalse(state.undoDayClear("2026-10-26", markers = listOf("⭐")))
        assertEquals(listOf("🎂"), state.dayMarkers["2026-10-26"])
        state.markDay("2026-10-26", emptyList())
        assertTrue(state.undoDayClear("2026-10-26", markers = listOf("⭐")))
        assertEquals(listOf("⭐"), state.dayMarkers["2026-10-26"])
    }

    @Test
    fun `the calendar's month stays within the years a date is written in, as the desktop's shift_calendar`() {
        // Past December 9999 a day's key no longer read back as a date.
        val state = state()
        state.calendarMonth = java.time.YearMonth.of(9999, 12)
        state.calendarMonth = state.calendarMonth.plusMonths(1)
        assertEquals(java.time.YearMonth.of(9999, 12), state.calendarMonth)
        state.calendarMonth = java.time.YearMonth.of(1, 1).minusMonths(1)
        assertEquals(java.time.YearMonth.of(1, 1), state.calendarMonth)
    }

    /** The editor's save, standing in: the body it holds, written now. */
    private fun AppState.editorHolds(id: Int, body: String, seen: MutableList<String>) {
        flush = {
            seen.add("flush")
            Notebook.updateNote(load, id, note(id)!!.title, body)
            refresh()
            null
        }
    }

    private fun AppState.pinnedOnDisk(id: Int) = rawNote(id)?.optBoolean("pinned") == true

    @Test
    fun anotherCardTappedSavesTheOpenNoteFirst() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush while ${s.openId} open"); null }
        val before = s.opening
        s.openNote(2)
        assertEquals(listOf("flush while 1 open"), seen)
        assertEquals(2, s.openId)
        assertEquals(before + 1, s.opening)
    }

    @Test
    fun theOpenNotesOwnCardChangesNothing() {
        val s = state()
        s.openNote(1)
        val before = s.opening
        s.openNote(1)
        assertEquals(before, s.opening)
    }

    @Test
    fun aSecondNewNoteIsAFreshEditor() {
        val s = state()
        s.newNote()
        val first = s.opening
        s.newNoteSaved(3)
        assertEquals(3, s.openId)
        s.newNote()
        assertEquals(0, s.openId)
        assertEquals(first + 1, s.opening)
        // A save that reaches it only once it is no longer new changes nothing.
        s.openNote(2)
        s.newNoteSaved(9)
        assertEquals(2, s.openId)
    }

    @Test
    fun aPinFromTheListLandsOnTheWordsJustTypedAndIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = {
            seen.add("flush, pinned on disk: ${s.pinnedOnDisk(1)}")
            Notebook.updateNote(s.load, 1, "A", "typed last")
            s.refresh()
            null
        }
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(1), true)
        assertEquals(listOf("flush, pinned on disk: false"), seen)
        assertTrue(s.pinnedOnDisk(1))
        assertEquals("typed last", s.rawNote(1)?.optString("body"))
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun aChangeToAnotherNoteLeavesTheEditorAlone() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush"); null }
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(2), true)
        s.moveToCategory(listOf(2), "General")
        assertTrue(seen.isEmpty())
        assertEquals(rewritten, s.openNoteRewritten)
    }

    @Test
    fun aCategoryFromTheListIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        val rewritten = s.openNoteRewritten
        s.moveToCategory(listOf(1), "General")
        assertEquals(listOf("flush"), seen)
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun `notes moved to one category go back each where it was filed, by the move's Undo`() {
        // One tap on the sheet moved them all, and where each had been was
        // kept nowhere.
        val s = state()
        s.moveToCategory(listOf(1), "Work")
        s.snackbar.currentSnackbarData!!.dismiss()
        val stamped = s.rawNote(2)?.opt("updated")
        s.moveToCategory(listOf(1, 2), "Home")
        assertEquals("Home", s.rawNote(1)?.optString("category"))
        assertTrue(s.rawNote(2)?.opt("updated") != stamped)
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("Work", s.rawNote(1)?.optString("category"))
        assertEquals("General", s.rawNote(2)?.optString("category"))
        assertEquals(stamped, s.rawNote(2)?.opt("updated"))
    }

    @Test
    fun `a move's Undo follows a category renamed meanwhile, and leaves one deleted meanwhile`() {
        // The phone took a rename for a delete, where the desktop carries it into its offer:
        // the Undo said the notes could not go back, or were no longer where the move put them.
        val s = state()
        s.moveToCategory(listOf(1), "Work")
        s.snackbar.currentSnackbarData!!.dismiss()
        s.moveToCategory(listOf(1), "Home")
        assertTrue(s.report(Organizing.renameCategory(s.load, "Work", "Job")))
        s.categoryRenamed("Work", "Job")
        assertTrue(s.report(Organizing.renameCategory(s.load, "Home", "House")))
        s.categoryRenamed("Home", "House")
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("Job", s.rawNote(1)?.optString("category"))
        // ...and one the notes came from, deleted meanwhile, leaves them where the move put them.
        s.snackbar.currentSnackbarData?.dismiss()
        s.moveToCategory(listOf(1), "House")
        assertTrue(s.report(Organizing.deleteCategory(s.load, "Job", trashNotes = false)))
        s.categoryDeleted("Job")
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("House", s.rawNote(1)?.optString("category"))
    }

    @Test
    fun `a move's Undo leaves a note filed again since where it is now`() {
        val s = state()
        val marks = Organizing.filedNow(s.load, listOf(1, 2))
        assertTrue(s.report(Organizing.moveToCategory(s.load, listOf(1, 2), "Home")))
        assertTrue(s.report(Organizing.moveToCategory(s.load, listOf(2), "Shopping")))
        assertTrue(s.report(Organizing.putBackFiled(s.load, marks, "Home")))
        assertEquals("General", s.rawNote(1)?.optString("category"))
        assertEquals("Shopping", s.rawNote(2)?.optString("category"))
    }

    @Test
    fun `a save the editor stays open after leaves the words in A new item in their box`() {
        // A row's Undo, the note's own card tapped again and a pin from the
        // list beside it made them a finished row and emptied the box; the
        // Trash still takes them with the note.
        val s = state()
        assertTrue(s.report(Notebook.addChecklistItem(s.load, 1, "milk")))
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("whole"); null }
        s.flushStaying = { seen.add("staying"); null }
        s.removeChecklistRow(1, 0)
        s.snackbar.currentSnackbarData!!.performAction()
        s.openNote(1)
        s.setPinned(listOf(1), true)
        s.trash(listOf(1))
        assertEquals(listOf("staying", "staying", "staying", "whole"), seen)
    }

    @Test
    fun aCopyAndACodeCarryTheLastWordsTypedWithoutRereadingTheNote() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        val rewritten = s.openNoteRewritten
        val code = kotlinx.coroutines.runBlocking { s.shareCode(listOf(1)) }!!
        val shared = (Sharing.decode(code) as Sharing.Decoded.Notes).notes.single()
        assertEquals("typed last", shared.optString("body"))
        s.editorHolds(1, "typed later", seen)
        // The copy's id, for the list to show it where the sort puts it.
        val copy = s.duplicate(1)
        assertTrue(copy != null && copy != 1 && s.note(copy)?.body == "typed later")
        // Read, not written: the editor keeps its cursor.
        assertEquals(rewritten, s.openNoteRewritten)
        assertEquals(listOf("flush", "flush"), seen)
    }

    @Test
    fun theTrashGetsTheOpenNoteWithItsLastWords() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.editorHolds(1, "typed last", seen)
        s.trash(listOf(1))
        assertEquals(listOf("flush"), seen)
        assertNull(s.openId)
        assertEquals("typed last", s.load.trash.last().body)
    }

    @Test
    fun aRepeatSetOnTheOpenNoteIsReadBack() {
        val s = state()
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush"); null }
        val rewritten = s.openNoteRewritten
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        assertEquals(listOf("flush"), seen)
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun aDayChangedFromTheCalendarBesideTheNoteIsSavedFirstAndReadBackOnlyWhereItShows() {
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1)
        val seen = ArrayList<String>()
        s.flush = { seen.add("flush"); null }
        val rewritten = s.openNoteRewritten
        s.resetDay(1, "2026-10-12")
        s.deleteDay(1, "2026-10-19")
        assertEquals(listOf("flush", "flush"), seen)
        // The whole series shows no day: read back, it lost its Undo for
        // nothing. Open on the day changed, it is.
        assertEquals(rewritten, s.openNoteRewritten)
        s.openNote(1, "2026-10-26")
        s.resetDay(1, "2026-10-26")
        assertEquals(rewritten + 1, s.openNoteRewritten)
    }

    @Test
    fun `only a change that closes the editor writes the words in A new item first`() {
        // A day moved with them left in the box built the editor anew, the words
        // kept nowhere; written as a row, a change beside the editor or one
        // refused finished them while it stayed. An editor built anew carries them.
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1, "2026-10-12")
        val seen = ArrayList<String>()
        s.flush = { seen.add("whole"); null }
        s.flushStaying = { seen.add("staying"); null }
        s.moveDay(1, "2026-10-12", "2026-10-13")
        assertEquals("2026-10-13", s.openDay)
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals("2026-10-12", s.openDay)
        s.resetDay(1, "2026-10-19")
        s.deleteDay(1, "2026-10-26")
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "daily"}""")))
        s.deleteDay(1, "2026-10-12")
        assertEquals(listOf("staying", "staying", "staying", "staying", "staying", "whole"), seen)
    }

    @Test
    fun `the same note opened on another day or as the series keeps the words in A new item`() {
        // From the calendar beside the editor, through leaving() the whole
        // save made them a row; built anew, the editor carries them.
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1, "2026-10-12")
        val seen = ArrayList<String>()
        s.flush = { seen.add("whole"); null }
        s.flushStaying = { seen.add("staying"); null }
        val opening = s.opening
        s.openNote(1)
        assertNull(s.openDay)
        s.openNote(1, "2026-10-19")
        assertEquals("2026-10-19", s.openDay)
        assertEquals(listOf("staying", "staying"), seen)
        assertEquals(opening, s.opening)
        s.openNote(2)
        assertEquals(listOf("staying", "staying", "whole"), seen)
    }

    @Test
    fun `a day move that moves nothing, or a change refused, leaves the editor unread`() {
        // Read back for nothing, the editor lost its Undo and its cursor:
        // the picker answered on the day itself, a reset refused.
        val s = state()
        assertTrue(s.setRepeat(1, JSONObject("""{"freq": "weekly"}""")))
        s.openNote(1, "2026-10-12")
        s.flushStaying = { null }
        var rewritten = s.openNoteRewritten
        s.moveDay(1, "2026-10-12", "2026-10-12")
        s.moveDay(1, "2026-10-12", "2026-10-19")
        assertEquals("2026-10-12", s.openDay)
        assertEquals(rewritten, s.openNoteRewritten)
        s.openNote(2)
        rewritten = s.openNoteRewritten
        s.resetDay(2, "2026-10-12")
        assertEquals("a reset refused: the note does not repeat", rewritten, s.openNoteRewritten)
    }

    @Test
    fun `the Trash's Undo asks the list to show its notes only when it was tapped on the list`() {
        // From the Calendar, the reveal waited and moved the list later.
        val s = state()
        s.tab = Tab.CALENDAR
        s.trash(listOf(2))
        s.snackbar.currentSnackbarData!!.performAction()
        assertNull(s.revealAfterUndo)
        s.tab = Tab.NOTES
        s.trash(listOf(1))
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals(1, s.revealAfterUndo?.ids?.size)
        assertEquals("no waiting word took it back: said Put back", s.revealAfterUndo?.ids, s.revealAfterUndo?.unsaid)
    }

    @Test
    fun anImportReadsTheOpenNoteBackAndOnlyAnOpenOne() {
        val s = state()
        val rewritten = s.openNoteRewritten
        s.afterImport()
        assertEquals(rewritten, s.openNoteRewritten)
        s.openNote(1)
        var flushed = false
        s.flush = { flushed = true; null }
        s.beforeImport()
        assertTrue(flushed)
        // Saved first, and read back only if the import changed it: this one
        // did not (the one that does is pinned below).
        s.afterImport()
        assertEquals(rewritten, s.openNoteRewritten)
    }

    @Test
    fun customOrderStartsFromTheOrderShownOnlyWhenNothingIsPlacedYet() {
        val s = state()
        File(s.dir, "settings.json").writeText("{}")
        s.reload()
        s.chooseSort(SortOrder.CUSTOM, from = SortOrder.TITLE_ZA)
        val placed = { s.rawNotes().associate { it.getInt("id") to it.optInt("order", -1) } }
        assertEquals(mapOf(2 to 0, 1 to 1), placed())
        assertEquals("Custom order", s.settings.optString(Settings.NOTE_SORT))
        // Arranged by hand since: choosing Custom order again from another
        // sort must not start it over from that sort.
        s.moveNote(1, anchor = 2, before = true, grouped = false)
        s.chooseSort(SortOrder.TITLE_AZ, from = SortOrder.CUSTOM)
        s.chooseSort(SortOrder.CUSTOM, from = SortOrder.TITLE_ZA)
        assertEquals(mapOf(1 to 0, 2 to 1), placed())
    }

    @Test
    fun theRailSavesTheNoteItLeaves() {
        val s = state()
        s.openNote(1)
        var flushed = false
        s.flush = { flushed = true; null }
        s.saveAndCloseNote()
        assertTrue(flushed)
        assertNull(s.openId)
        assertFalse(s.openDay != null)
    }

    /**
     * Every write of the notes failing, as on a full disk: where the write
     * puts its .tmp is a folder it cannot remove. Returns the way to free it.
     */
    private fun AppState.writesFail(): () -> Unit {
        val blocker = File(dir, "notes_data.json.tmp")
        File(blocker, "hold").apply { parentFile!!.mkdirs(); writeText("x") }
        return { File(blocker, "hold").delete(); blocker.delete() }
    }

    /** The editor open on [id] (0: a new note), registered as NoteEditor registers it, holding [body] unsaved. */
    private fun AppState.editing(id: Int, body: String): EditorModel {
        val editor = EditorModel(this, note(id))
        flush = { editor.write() }
        editor.body = body
        editor.changed()
        return editor
    }

    /** What the bar at the foot says -- the one line a screen reader reads out. */
    private fun AppState.said() = snackbar.currentSnackbarData?.visuals

    @Test
    fun `a note whose save fails stays open on Back, another card, + and the rail, and says why`() {
        val s = state()
        s.openNote(1)
        s.editing(1, "first, and the words typed since")
        val free = s.writesFail()
        val opening = s.opening
        s.saveAndCloseNote()
        s.openNote(2)
        s.newNote()
        assertEquals(1, s.openId)
        assertEquals(opening, s.opening)
        val said = s.said()!!
        assertTrue(said.message, said.message.startsWith("Not saved, so the note stays open: "))
        assertEquals("Leave anyway", said.actionLabel)
        // Saved once it can be, and left only then.
        free()
        s.saveAndCloseNote()
        assertNull(s.openId)
        assertEquals("first, and the words typed since", s.rawNote(1)?.optString("body"))
    }

    @Test
    fun `a new note that cannot be saved is closed only when the person says to leave anyway`() {
        val s = state()
        s.newNote()
        s.editing(0, "Call the dentist Tuesday 10:30")
        s.writesFail()
        s.saveAndCloseNote()
        assertEquals("the editor, the words' only copy, stays", 0, s.openId)
        s.snackbar.currentSnackbarData!!.performAction()
        assertNull(s.openId)
        assertTrue(s.load.notes.none { it.body.startsWith("Call") })
    }

    @Test
    fun `a pin, the Trash or an import waits for an open note that cannot be saved`() {
        val s = state()
        s.openNote(1)
        s.editing(1, "typed, not saved")
        s.writesFail()
        val rewritten = s.openNoteRewritten
        s.setPinned(listOf(1), true)
        s.trash(listOf(1))
        assertEquals("the editor is not read back over its words", rewritten, s.openNoteRewritten)
        assertEquals(1, s.openId)
        assertTrue(s.said()!!.message.startsWith("Not saved, so the open note was left as it is: "))
        assertFalse(s.beforeImport())
    }

    @Test
    fun `a failed autosave is said once, and a save a tap asked for every time`() {
        // Drawn in red only, it reached nobody using a screen reader.
        val s = state()
        val editor = s.editing(1, "typed")
        s.writesFail()
        assertFalse(editor.save(auto = true))
        assertTrue(s.said()!!.message.startsWith("Not saved: "))
        s.snackbar.currentSnackbarData!!.dismiss()
        editor.body = "typed more"
        editor.changed()
        assertFalse(editor.save(auto = true))
        assertNull("the autosave does not say it again", s.said())
        assertFalse(editor.save())
        assertTrue(s.said()!!.message.startsWith("Not saved: "))
    }

    /** A note whose checklist reads [rows], open in an editor registered as NoteEditor registers it. */
    private fun checklist(vararg rows: String): Pair<AppState, EditorModel> {
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        val items = rows.joinToString(", ") { """{"text": "$it", "done": false}""" }
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "Bread", "body": "Knead", "category": "General", "checklist": [$items]}]""")
        File(dir, "attachments").mkdirs()
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        s.openNote(1)
        val editor = EditorModel(s, s.note(1))
        s.flush = { editor.write() }
        return s to editor
    }

    private fun AppState.rowsOnDisk(): List<String> {
        val note = org.json.JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0)
        val rows = note.getJSONArray("checklist")
        return (0 until rows.length()).map { rows.getJSONObject(it).getString("text") }
    }

    @Test
    fun `a row being renamed and the words in A new item are written by the flush, no field left`() {
        // Home, or Recents: the activity's onStop runs the flush, and nothing
        // moves the focus. The words waited in the fields, and a process
        // killed in the background took them.
        val (s, editor) = checklist("flour", "yeast")
        editor.renaming = EditorModel.Renaming(1, "yeast", "yeast 7g")
        editor.adding = "salt"
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("flour", "yeast 7g", "salt"), s.rowsOnDisk())
        assertNull(editor.renaming)
        assertEquals("", editor.adding)
    }

    @Test
    fun `a rename waiting for a row the list moved under it is dropped, never put on another row`() {
        val (s, editor) = checklist("flour", "yeast", "salt")
        editor.renaming = EditorModel.Renaming(1, "yeast", "yeast 7g")
        // From elsewhere: the rows move up one under the waiting words.
        Notebook.removeChecklistItem(s.load, 1, 0)
        s.refresh()
        assertNull(editor.write())
        assertEquals(listOf("yeast", "salt"), s.rowsOnDisk())
    }

    @Test
    fun `letters typed into a row wait together, and go whole when another row is typed in`() {
        // The walk typed " 500g" after "flour" and pressed Home: the file
        // read "flour " -- the second letter, judged by a drawing that had
        // not caught up, wrote the first alone and lost the rest.
        val (s, editor) = checklist("flour", "yeast")
        for (now in listOf("flour ", "flour 5", "flour 50", "flour 500g")) assertNull(editor.typeInRow(0, now))
        assertEquals("nothing is written while one row is typed in", listOf("flour", "yeast"), s.rowsOnDisk())
        assertNull(editor.typeInRow(1, "yeast 7g"))
        assertEquals(listOf("flour 500g", "yeast"), s.rowsOnDisk())
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("flour 500g", "yeast 7g"), s.rowsOnDisk())
    }

    @Test
    fun `a row's words the phone cannot write are said once, and the next row's letters are kept`() {
        // Each letter typed into another row tried the first row's write
        // again and queued another bar; the letters themselves vanished, the
        // row showing its old words while the first row's waited.
        val (s, editor) = checklist("flour", "yeast")
        assertNull(editor.typeInRow(0, "flour 500g"))
        val free = s.writesFail()
        assertNotNull("the first failure is said", editor.typeInRow(1, "yeast 7"))
        assertNull("and not again at every letter", editor.typeInRow(1, "yeast 7g"))
        assertEquals("flour 500g", editor.waitingIn(0, "flour"))
        assertEquals("yeast 7g", editor.waitingIn(1, "yeast"))
        // Back to the first row: its words go on from where they were held.
        assertNull(editor.typeInRow(0, "flour 500g!"))
        assertEquals("flour 500g!", editor.waitingIn(0, "flour"))
        assertEquals(listOf("flour", "yeast"), s.rowsOnDisk())
        free()
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("flour 500g!", "yeast 7g"), s.rowsOnDisk())
        assertNull(editor.renaming)
        assertTrue(editor.unwritten.isEmpty())
    }

    @Test
    fun `a row taken off by its cross is said, and Undo puts it back where it was, whole`() {
        // Gone at once, unsaid, with no way back: the editor's Undo holds no checklist.
        val (s, _) = checklist("flour", "yeast", "salt")
        s.removeChecklistRow(1, 1)
        assertEquals(listOf("flour", "salt"), s.rowsOnDisk())
        val offer = s.snackbar.currentSnackbarData!!
        assertEquals("Removed “yeast” from the checklist", offer.visuals.message)
        offer.performAction()
        assertEquals(listOf("flour", "yeast", "salt"), s.rowsOnDisk())
    }

    @Test
    fun `a bar quotes a long row or title short, and Undo still puts back every letter`() {
        // The bar laid out the whole of a 150,000-letter row -- taken off by
        // its ✕, the only thing a read-only row offers -- and froze the app.
        val long = "a".repeat(150_000)
        val (s, _) = checklist("flour", long)
        s.removeChecklistRow(1, 1)
        val offer = s.snackbar.currentSnackbarData!!
        val message = offer.visuals.message
        assertTrue(message.length.toString(), message.length < QUOTED + 40)
        assertEquals("Removed “${"a".repeat(QUOTED - 1)}…” from the checklist", message)
        offer.performAction()
        assertEquals(listOf("flour", long), s.rowsOnDisk())
        // Duplicate's bar quotes the copy's title the same way.
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        File(dir, "notes_data.json").writeText("""[{"id": 1, "uid": "a", "title": "${"T".repeat(150_000)}", "category": "General"}]""")
        File(dir, "attachments").mkdirs()
        val d = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        d.duplicate(1)
        val said = d.snackbar.currentSnackbarData!!.visuals.message
        assertEquals("Duplicated as “${"T".repeat(QUOTED - 1)}…”", said)
        assertEquals("the copy keeps its whole title", 150_000 + " (copy)".length, d.note(2)!!.title.length)
        d.snackbar.currentSnackbarData!!.dismiss()
        // And whatever a reason carries, the bar is given at most MAX_SAID.
        d.say("x".repeat(200_000))
        assertEquals(MAX_SAID, d.snackbar.currentSnackbarData!!.visuals.message.length)
    }

    @Test
    fun `a row's Undo writes the words being typed into another row before it puts the row back`() {
        // Put back under them, the row moved the one being typed in, and its
        // words -- waiting to be written -- were dropped.
        val (s, editor) = checklist("eggs", "bread", "milk", "butter")
        s.removeChecklistRow(1, 1)
        editor.renaming = EditorModel.Renaming(2, "butter", "butter 250g")
        s.snackbar.currentSnackbarData!!.performAction()
        assertEquals(listOf("eggs", "bread", "milk", "butter 250g"), s.rowsOnDisk())
    }

    @Test
    fun `two deletes in a row, and the Undo on screen brings back the second`() {
        // The second bar waited behind the first, so the Undo a thumb reached
        // took back the note deleted before.
        val dir = dirOf(scratch("""[{"id": 1, "uid": "a", "title": "One"}, {"id": 2, "uid": "b", "title": "Two"}]""",
                                attachments = true))
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        s.trash(listOf(1))
        s.trash(listOf(2))
        s.snackbar.currentSnackbarData!!.performAction()
        // By title: a note back from the Trash takes the next free id.
        assertEquals(listOf("Two"), s.load.notes.map { it.title })
    }

    @Test
    fun `an Undo bar lasts from when it shows, for as long as the phone asks`() {
        // Its own eight seconds, counted from the asking, flashed or stayed for
        // good behind another message. Android's long length counts from when
        // it shows, stretched by the phone's "Time to take action".
        val (s, _) = checklist("eggs", "bread")
        s.removeChecklistRow(1, 0)
        val offer = s.snackbar.currentSnackbarData!!
        assertEquals("Undo", offer.visuals.actionLabel)
        assertEquals(androidx.compose.material3.SnackbarDuration.Long, offer.visuals.duration)
    }

    @Test
    fun `half-typed words written as a row on the way to the background are back in the box on return`() {
        // Kept as a row in case Android ends the app; left so, the person
        // came back to a finished item and an empty box.
        val (s, editor) = checklist("flour")
        editor.adding = "bre"
        assertNull(editor.writeAway())
        assertEquals(listOf("flour", "bre"), s.rowsOnDisk())
        assertEquals("", editor.adding)
        editor.backAgain()
        assertEquals(listOf("flour"), s.rowsOnDisk())
        assertEquals("bre", editor.adding)
        // Once only, and never over a row changed since.
        editor.backAgain()
        assertEquals(listOf("flour"), s.rowsOnDisk())
        editor.adding = "milk"
        assertNull(editor.writeAway())
        assertTrue(Notebook.addChecklistItem(s.load, editor.id, "eggs") is SaveResult.Ok)
        editor.backAgain()
        assertEquals(listOf("flour", "milk", "eggs"), s.rowsOnDisk())
        assertEquals("", editor.adding)
    }

    @Test
    fun `text shared in on the way back leaves the half-typed words as the row they were written as`() {
        // The share opens a note of its own before the editor hears it is
        // back: the words were taken out of the row into an editor about to
        // close, and kept nowhere.
        val (s, editor) = checklist("flour")
        editor.adding = "plums"
        assertNull(editor.writeAway())
        s.noteFromShared("A recipe link", "Saved as a new note")
        editor.backAgain()
        assertEquals(listOf("flour", "plums"), s.rowsOnDisk())
    }

    @Test
    fun `one day of a series goes out as a note of its own on that date, with that day's words`() {
        // Shared or exported from a day, the series' own words went out and a
        // code carried the whole series.
        val dir = dirOf(scratch("""[{"id": 1, "uid": "s", "title": "Team meeting", "body": "agenda",
            "formatting": [{"start": 0, "end": 6, "style": "bold"}],
            "date": "2026-11-02", "recurrence": {"freq": "weekly"},
            "overrides": {"2026-11-09": {"body": "minutes of the day"}}}]""", attachments = true))
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        val day = s.outgoingNote(1, "2026-11-09")!!
        assertEquals("minutes of the day", day.optString("body"))
        // ...with the day's looks: the series' bold, placed on other words by
        // its places, went out where the editor showed the day plain.
        assertEquals(0, day.optJSONArray("formatting")!!.length())
        assertEquals(1, s.outgoingNote(1)!!.optJSONArray("formatting")!!.length())
        assertEquals("2026-11-09", day.optString("date"))
        assertFalse(Recurrence.isRecurring(day))
        assertFalse(day.has("uid"))
        assertEquals("agenda", s.outgoingNote(1)!!.optString("body"))
        assertTrue(Recurrence.isRecurring(s.outgoingNote(1)!!))
    }

    @Test
    fun `notes restored from the Trash are handed to the list, to say if it hides them`() {
        // Restored where Show did not look, a note landed unseen under
        // "Restored".
        val dir = dirOf(scratch("""[{"id": 1, "uid": "a", "title": "One"}, {"id": 2, "uid": "b", "title": "Two"}]""",
                                attachments = true))
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        s.trash(listOf(2))
        s.restoreFromTrash(s.load.trash.map { Organizing.keyOf(it) })
        val word = s.arrivals.single()
        assertEquals(listOf("Two"), word.ids.mapNotNull { s.note(it)?.title })
        assertEquals("“Two” restored", s.wordText(word))
    }

    @Test
    fun `shared text with a first line longer than a title keeps all of it`() {
        // Cut to a title's 200 letters, the rest of a shared paragraph was
        // lost while the app said it was saved.
        val paragraph = "word ".repeat(70).trim()
        val (title, body) = sharedAsNote(paragraph + "\nsecond line")
        assertEquals(Editing.TITLE_CHARS, title.length)
        assertEquals(paragraph + "\nsecond line", body)
        assertEquals("Buy milk" to "and bread", sharedAsNote("Buy milk\nand bread\n"))
        assertEquals("One line" to "", sharedAsNote("  One line  "))
        // A .txt saved by Notepad starts with a byte-order mark: not in the title.
        assertEquals("Buy milk" to "and bread", sharedAsNote(Char(0xFEFF) + "Buy milk\r\nand bread"))
    }

    @Test
    fun `the autosave leaves the checklist's half-typed words where they are`() {
        val (s, editor) = checklist("flour")
        editor.adding = "bre"
        editor.body = "Knead ten minutes"
        editor.changed()
        assertTrue(editor.save(auto = true))
        assertEquals(listOf("flour"), s.rowsOnDisk())
        assertEquals("bre", editor.adding)
    }

    @Test
    fun `a paste into a long row keeps the row's own words whole in the file, the rest of the note untouched`() {
        // The field's take() wrote the paste's first 2,000 letters and lost
        // " Serve with rice." -- the row's own end, without a word.
        val recipe = "r".repeat(1_480) + " Serve with rice."
        val dir = NioFiles.createTempDirectory("appstate").toFile()
        // A note crossing the heavy features: a table, a repeat, a file, styles.
        File(dir, "notes_data.json").writeText(JSONObject()
            .put("id", 1).put("uid", "a").put("title", "Dinner").put("category", "General")
            .put("body", "Costs\n| a | 1 |\n| b | 2 |").put("date", "2026-10-05")
            .put("recurrence", JSONObject().put("freq", "weekly").put("weekdays", org.json.JSONArray().put(0)))
            .put("formatting", org.json.JSONArray().put(JSONObject().put("start", 0).put("end", 5).put("style", "bold")))
            .put("attachments", org.json.JSONArray().put(JSONObject().put("filename", "menu.pdf").put("stored_as", "1_menu.pdf")))
            .put("checklist", org.json.JSONArray().put(JSONObject().put("text", recipe).put("done", false))
                .put(JSONObject().put("text", "milk").put("done", true)))
            .let { "[$it]" })
        File(dir, "attachments").mkdirs()
        File(dir, "attachments/1_menu.pdf").writeText("pdf")
        val s = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        s.openNote(1)
        val editor = EditorModel(s, s.note(1))
        s.flush = { editor.write() }
        val before = org.json.JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0)
        val fit = editor.rowCap.fit(recipe, "p".repeat(800) + recipe)
        assertNull(editor.typeInRow(0, fit.text))
        assertNull(s.flush!!.invoke())
        assertEquals(listOf("p".repeat(503) + recipe, "milk"), s.rowsOnDisk())
        assertEquals("Pasted text shortened: a checklist item can be up to 2,000 characters", s.said()!!.message)
        val after = org.json.JSONArray(File(dir, "notes_data.json").readText()).getJSONObject(0)
        for (key in listOf("body", "formatting", "recurrence", "attachments", "date", "uid")) {
            assertEquals(key, before.opt(key).toString(), after.opt(key).toString())
        }
        assertTrue("its tick kept", after.getJSONArray("checklist").getJSONObject(1).getBoolean("done"))
    }

    @Test
    fun `words typed into a full list's box are told it is full as they begin, not lost unsaid behind Home`() {
        // At 1,000 rows the box took words, and only Add or Back said the
        // list was full: Home ran the save, which failed with no one to tell,
        // and Android ending the app there took the words unsaid.
        val (s, editor) = checklist(*Array(Notebook.MAX_CHECKLIST_ITEMS) { "row $it" })
        editor.typeAdding("P")
        assertEquals(Notebook.CHECKLIST_FULL, s.said()!!.message)
        s.snackbar.currentSnackbarData!!.dismiss()
        editor.typeAdding("Pa")
        assertNull("said once as they begin, not at each letter", s.said())
        editor.typeAdding("")
        editor.typeAdding("x")
        assertEquals(Notebook.CHECKLIST_FULL, s.said()!!.message)
        // With room, nothing is said.
        val (room, roomy) = checklist("flour")
        roomy.typeAdding("salt")
        assertNull(room.said())
    }

    @Test
    fun `the title keeps its own words at 200 letters, its cursor after what came in, said in the desktop's words`() {
        val (s, editor) = checklist("flour")
        val title = "a".repeat(150) + " end"
        editor.title = title
        // A paste at the start of a long title: only what came in is cut.
        val shown = TextFieldValue(title, TextRange(0))
        val pasted = "p".repeat(100)
        val kept = editor.typeTitle(shown, TextFieldValue(pasted + title, TextRange(100)))
        assertEquals("p".repeat(46) + title, editor.title)
        assertEquals(TextRange(46), kept.selection)
        assertEquals("Pasted title shortened to 200 characters", s.said()!!.message)
        // Lines pasted in go on one line, as the desktop's title paste puts
        // them (a break alone pastes nothing there, and is Enter here); a
        // letter at the cap is refused, the title whole.
        assertEquals("one line", "xa by", EditorModel(s, s.note(1)).let { e ->
            e.title = "xy"
            e.typeTitle(TextFieldValue("xy", TextRange(1)), TextFieldValue("xa\nby", TextRange(4)))
            e.title
        })
        val full = editor.title
        editor.typeTitle(TextFieldValue(full, TextRange(0)), TextFieldValue("Z$full", TextRange(1)))
        assertEquals(full, editor.title)
    }

    @Test
    fun `while a sheet draws the app's messages the window's host draws none, and draws them again after`() {
        // Drawn by both, each message over a sheet was two live regions, and
        // TalkBack could read it twice.
        val messages = Any()
        val first = Any()
        val second = Any()
        assertFalse(MessagesShown.elsewhere(messages))
        MessagesShown.shown(messages, first, 1)
        MessagesShown.shown(messages, second, 1)
        assertTrue("a sheet over the window draws them", MessagesShown.elsewhere(messages))
        MessagesShown.gone(messages, second)
        assertTrue("a second still does", MessagesShown.elsewhere(messages))
        MessagesShown.gone(messages, first)
        assertFalse("the window draws them again once none does", MessagesShown.elsewhere(messages))
        assertFalse("another state's are its own", MessagesShown.elsewhere(Any()))
    }

    @Test
    fun `of two sheets drawing the app's messages, the one opened over the other draws them, the other none`() {
        // The Repeat sheet over the note's details: both drew each message,
        // two live regions again. The deeper
        // draws them; once it goes, the one under it does again.
        val messages = Any()
        val details = Any()
        val repeat = Any()
        MessagesShown.shown(messages, details, 1)
        assertTrue(MessagesShown.drawn(messages, details))
        MessagesShown.shown(messages, repeat, 2)
        assertFalse("the details sheet, under it", MessagesShown.drawn(messages, details))
        assertTrue("the Repeat sheet, over it", MessagesShown.drawn(messages, repeat))
        // Registered in the other order -- a sheet composed in its own window
        // may be -- the deeper still draws.
        val again = Any()
        val under = Any()
        val over = Any()
        MessagesShown.shown(again, over, 2)
        MessagesShown.shown(again, under, 1)
        assertTrue(MessagesShown.drawn(again, over))
        assertFalse(MessagesShown.drawn(again, under))
        MessagesShown.gone(messages, repeat)
        assertTrue("the details draw them again", MessagesShown.drawn(messages, details))
        // Of two at one depth, the newest.
        val newer = Any()
        MessagesShown.shown(messages, newer, 1)
        assertTrue(MessagesShown.drawn(messages, newer))
        assertFalse(MessagesShown.drawn(messages, details))
        for (host in listOf(details, newer)) MessagesShown.gone(messages, host)
        for (host in listOf(under, over)) MessagesShown.gone(again, host)
        assertFalse(MessagesShown.elsewhere(messages) || MessagesShown.elsewhere(again))
    }
}
