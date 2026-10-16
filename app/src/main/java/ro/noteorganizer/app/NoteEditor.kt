package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import android.content.ClipData
import android.content.ClipDescription
import android.os.SystemClock
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ro.noteorganizer.app.ui.theme.LocalPalette
import ro.noteorganizer.app.ui.theme.hexColor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Everything the editor holds for one note, and the one way it is saved.
 *
 * A new note is 0 until something worth keeping is in it: an entirely
 * blank note is a mis-tap, not a thought, and nothing is written for it.
 */
class EditorModel(private val state: AppState, note: Note?, val day: String? = null, newDate: String = "") {
    var id by mutableIntStateOf(note?.id ?: 0)
    var title by mutableStateOf(note?.title ?: "")
    var body by mutableStateOf(note?.body ?: "")
    var spans by mutableStateOf(note?.formatting ?: emptyList())
    var comments by mutableStateOf(note?.comments ?: "")
    var category by mutableStateOf(note?.category ?: Themes.PROTECTED_CATEGORY)
    var color by mutableStateOf(note?.color ?: "")
    var date by mutableStateOf(note?.date ?: newDate)
    var pinned by mutableStateOf(note?.pinned ?: false)

    /**
     * Each line's indent and alignment, kept in step with the text as it
     * changes; a list item's kind is its marker, in the text itself. A day
     * of a series has none of its own -- the desktop keeps them with the
     * series -- so day mode keeps none.
     */
    var props by mutableStateOf(linesOf(note))

    private fun linesOf(note: Note?): Map<Int, Paragraphs.Props> =
        if (day != null || note == null) emptyMap()
        else Paragraphs.propsOf(state.rawNote(note.id)?.opt("paragraphs"), note.body.split("\n").size)

    init {
        if (day != null && note != null) showDay(note.id, day)
    }

    /**
     * One day of the series, as that day shows it: its own edit over the
     * series'. A day's own text is plain -- the desktop keeps formatting
     * with the series -- so its spans are drawn only while the day still
     * shows the series' words.
     */
    private fun showDay(id: Int, day: String) {
        val raw = state.rawNote(id) ?: return
        val shown = Recurrence.effectiveNote(raw, day)
        val own = (raw.opt("overrides") as? org.json.JSONObject)?.optJSONObject(day)
        title = shown.opt("title") as? String ?: ""
        body = shown.opt("body") as? String ?: ""
        comments = shown.opt("comments") as? String ?: ""
        category = (shown.opt("category") as? String)?.ifEmpty { null } ?: Themes.PROTECTED_CATEGORY
        color = shown.opt("color") as? String ?: ""
        if (own?.has("body") == true) spans = emptyList()
    }

    /** Counts changes; the autosave runs a moment after the last one. */
    var edits by mutableIntStateOf(0)
    private var saved = 0
    var status by mutableStateOf<String?>(null)
    var problem by mutableStateOf<String?>(null)

    // ---- Undo and Redo ----

    /**
     * What Undo takes back: the note's words and their styling -- title,
     * text, spans, lines, comments -- as the desktop's Ctrl+Z does. A note's
     * details are not words: a colour or a date set stays set.
     */
    private data class Snap(val title: String, val body: String, val spans: List<FormatSpan>,
                            val props: Map<Int, Paragraphs.Props>, val comments: String)

    private fun snap() = Snap(title, body, spans, props, comments)
    private val history = Editing.History<Snap>()

    /** The note as the last change left it: what the next change records as "before". */
    private var settled: Snap? = null

    init {
        settled = snap()
    }

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private fun steps() {
        canUndo = history.canUndo
        canRedo = history.canRedo
    }

    fun undo() {
        history.undo(snap())?.let { restore(it) }
    }

    fun redo() {
        history.redo(snap())?.let { restore(it) }
    }

    private fun restore(to: Snap) {
        title = to.title
        body = to.body
        spans = to.spans
        props = to.props
        comments = to.comments
        // Places remembered in the old text mean nothing in this one.
        focused = null
        caret = null
        calcMark = null
        settled = to
        steps()
        edits += 1
        status = null
    }

    /** Which prose field has the cursor, and its value -- selection and all. */
    var focus by mutableStateOf<Int?>(null)
    var focused by mutableStateOf<TextFieldValue?>(null)

    /**
     * Where the cursor last was in the body, in code points -- kept when
     * the field lets go of it, as the desktop's insert mark is, so the
     * calculator writes where the person was writing.
     */
    var caret: Int? = null

    /** The end of the calculator's last insertion: the next goes after it. */
    private var calcMark: Int? = null

    /** The body has changed: the places remembered in it move with the text, and the lines' properties too. */
    private fun follow(next: String) {
        caret = Calculator.follow(body, next, caret)
        calcMark = Calculator.follow(body, next, calcMark)
        props = Paragraphs.follow(body, next, props)
    }

    /** A list, indent or alignment change, the styles carried through it exactly. */
    private fun apply(done: Paragraphs.Done) {
        spans = if (day != null) emptyList() else done.move(spans)
        caret = Calculator.follow(body, done.body, caret)
        calcMark = Calculator.follow(body, done.body, calcMark)
        props = done.props
        body = done.body
        changed()
    }

    /** The field at [index] as the body now holds it, its cursor or selection from [from] to [to]. */
    private fun fieldValue(index: Int, from: Int, to: Int = from): TextFieldValue? {
        val segment = Editing.segments(body).getOrNull(index) ?: return null
        val length = segment.text.codePointCount(0, segment.text.length)
        fun local(at: Int) = segment.text.offsetByCodePoints(0, (at - segment.cpStart).coerceIn(0, length))
        return TextFieldValue(segment.text, TextRange(local(from), local(to)))
    }

    /** The format bar's list and indent keys: the lines changed, the selection kept over the same words. */
    fun applyLines(done: Paragraphs.Done?, index: Int, from: Int, to: Int) {
        if (done == null) return
        apply(done)
        focused = fieldValue(index, done.movePoint(from), done.movePoint(to))
        caret = done.movePoint(to)
    }

    /** The format bar's alignment keys. */
    fun alignLines(first: Int, last: Int, align: String) {
        props = Paragraphs.setAlign(body, props, first, last, align)
        changed()
    }

    /**
     * A cell's own highlight, pipe to pipe -- where the desktop lays it, so
     * whatever is later written in the cell lands inside it -- or none.
     */
    fun highlightCell(segment: Editing.Segment, fileRow: Int, column: Int, colour: String) {
        val (from, to) = Exporting.cellRange(body, segment.firstLine + fileRow, column) ?: return
        spans = if (colour.isEmpty()) clearStyle(spans, from, to, "highlight")
                else setStyle(spans, from, to, "highlight", colour)
        changed()
    }

    /**
     * The whole table left, centred or right on the page: its lines'
     * alignment -- and its caption's, which goes with it: on the page the
     * two are one block, as the desktop keeps them.
     */
    fun placeTable(segment: Editing.Segment, align: String) {
        val caption = Editing.captionLines(body.split("\n"), segment.lastLine)
        props = withAlign(props, (segment.firstLine..segment.lastLine) + caption, align)
        changed()
    }

    private fun withAlign(from: Map<Int, Paragraphs.Props>, lines: Iterable<Int>, align: String?): Map<Int, Paragraphs.Props> {
        var next = from
        for (n in lines) {
            val p = (next[n] ?: Paragraphs.Props()).copy(align = align ?: "left")
            next = if (p.plain) next - n else next + (n to p)
        }
        return next
    }

    /** Asks the prose field at [index] to take the keyboard, with [value]'s selection. */
    var focusAsk by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    private fun ask(index: Int, value: TextFieldValue?) {
        if (index < 0 || value == null) return
        focus = index
        focused = value
        focusAsk = index to ((focusAsk?.second ?: 0) + 1)
    }

    /** The field asked has the keyboard: the ask is spent, and a field drawn later at that place is not asked again. */
    fun focusTaken() {
        focusAsk = null
    }

    /**
     * The desktop's "Add caption below…": a numbered label line and a source
     * line under the table, placed as the table is, no style carried onto
     * them from its edge -- and "description" selected, so what is typed
     * replaces it.
     */
    fun addCaption(segment: Editing.Segment) {
        val made = Editing.addCaption(body, segment.lastLine)
        val tableAlign = props[segment.firstLine]?.align
        val lines = made.body.split("\n")
        val from = lines.take(made.line).sumOf { it.codePointCount(0, it.length) + 1 }
        val to = from + lines[made.line].codePointCount(0, lines[made.line].length) + 1 +
            lines[made.line + 1].codePointCount(0, lines[made.line + 1].length)
        spans = if (day != null) emptyList() else remapSpans(body, made.body, spans).spans.let { moved ->
            moved.map { it.style }.toSet().fold(moved) { acc, style -> clearStyle(acc, from, to, style) }
        }
        follow(made.body)
        body = made.body
        if (day == null) props = withAlign(props, listOf(made.line, made.line + 1), tableAlign)
        changed()
        val index = Editing.segments(body).indexOfFirst { !it.table && made.line in it.firstLine..it.lastLine }
        ask(index, fieldValue(index, made.selectStart, made.selectEnd))
    }

    /** "Add a line above the table": an empty line there, the cursor on it. */
    fun addLineAbove(segment: Editing.Segment) {
        val lines = body.split("\n")
        val at = lines.take(segment.firstLine).sumOf { it.codePointCount(0, it.length) + 1 }
        val next = (lines.take(segment.firstLine) + "" + lines.drop(segment.firstLine)).joinToString("\n")
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        changed()
        val index = Editing.segments(body).indexOfFirst { !it.table && segment.firstLine in it.firstLine..it.lastLine }
        ask(index, fieldValue(index, at))
    }

    /**
     * A column's width, hidden mark or fit: the separator line alone
     * rewritten, or one put under the header row, placed as the table is.
     */
    fun editColumns(segment: Editing.Segment, change: (List<String>) -> List<String>) {
        val lines = body.split("\n")
        val table = lines.subList(segment.firstLine, segment.lastLine + 1)
        val changedTable = change(table)
        if (changedTable == table) return
        val next = replaceTable(body, segment.firstLine..segment.lastLine, changedTable)
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        if (changedTable.size > table.size && day == null) {
            props = withAlign(props, listOf(segment.firstLine + 1), props[segment.firstLine]?.align)
        }
        changed()
    }

    /** The table taken out of the note. */
    fun deleteTable(segment: Editing.Segment) {
        val next = Editing.deleteLines(body, segment.firstLine, segment.lastLine)
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        focused = null
        changed()
    }

    /** A blank table at the cursor -- the end, when it was never in the note. The line it starts on. */
    fun insertTable(rows: Int, columns: Int): Int {
        val length = body.codePointCount(0, body.length)
        val made = Editing.insertTable(body, caret ?: length, rows, columns)
        spans = if (day != null) emptyList() else remapSpans(body, made.body, spans).spans
        follow(made.body)
        body = made.body
        focused = null
        changed()
        return made.firstLine
    }

    /**
     * Something changed; the autosave runs a moment after. When the words or
     * their styling changed, the note as it was goes into the history --
     * [kind] typing joining the step before when it comes soon after it.
     */
    fun changed(kind: String = "edit") {
        val now = snap()
        val before = settled
        if (before != null && before != now) {
            history.record(before, kind, System.currentTimeMillis())
            steps()
        }
        settled = now
        edits += 1
        status = null
    }

    val unsaved: Boolean get() = edits != saved

    /** Writes what is on screen. True when the file now holds it. */
    fun save(force: Boolean = false): Boolean {
        if (!force && edits == saved) return true
        val target = edits
        val load = state.load
        var follows = false
        val result = if (day != null && id != 0) {
            // A day of a series saves to that day alone, never to the series.
            Days.saveDay(load, id, day, title, body, comments, category, color) { follows = it == Days.DaySaved.FOLLOWS }
        } else if (id == 0) {
            if (!force && title.isBlank() && body.isBlank() && comments.isBlank()) {
                saved = target
                return true
            }
            Notebook.createNote(load, title, body, category, color, date, pinned, spans,
                                paragraphs = Paragraphs.extract(body, props), comments = comments)
        } else {
            Notebook.updateNote(load, id, title, body, category, color, date, pinned,
                                formatting = spans, comments = comments,
                                paragraphs = Paragraphs.extract(body, props))
        }
        state.refresh()
        return when (result) {
            is SaveResult.Ok -> {
                if (id == 0) {
                    id = result.id
                    state.newNoteSaved(id)
                }
                saved = target
                problem = null
                status = if (follows) "follows" else "saved"
                if (result.lostStyles > 0) {
                    state.say("${result.lostStyles} styles could not be kept: their text was deleted.")
                }
                true
            }
            is SaveResult.Failed -> {
                problem = result.reason
                false
            }
        }
    }

    /** The note on disk again, after something other than typing changed it. */
    fun reloadFrom(note: Note) {
        title = note.title
        body = note.body
        spans = note.formatting
        comments = note.comments
        category = note.category
        color = note.color
        date = note.date
        pinned = note.pinned
        props = linesOf(note)
        if (day != null) showDay(note.id, day)
        focused = null
        // A body brought back whole: nothing remembered in the old one applies
        // -- the steps of Undo included, which would put the old one back.
        caret = null
        calcMark = null
        history.clear()
        settled = snap()
        steps()
        saved = edits
    }

    /**
     * One prose field's new text, the body rebuilt around it. In a list a
     * keystroke can mean more than its character -- Enter continues the
     * list, Backspace against a marker takes it away -- and then the field
     * is given back rewritten, its cursor where it belongs: the value to
     * show. Null when the text stands as typed.
     */
    fun typeInto(segment: Editing.Segment, typed: TextFieldValue, index: Int): TextFieldValue? {
        val next = Editing.replaceProse(body, segment, typed.text)
        if (next == body) return null
        if (day == null) {
            Paragraphs.afterTyping(body, next, props, caretIn(segment, typed))?.let { done ->
                apply(done)
                val at = done.caret ?: return null
                caret = at
                return fieldValue(index, at)
            }
        }
        // A day's own text is plain: once typed in, it carries no styling.
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        changed(Editing.History.TYPING)
        return null
    }

    /** One table changed through the table rules, the body rebuilt around it. */
    fun editTable(segment: Editing.Segment, change: (TableEdit) -> TableEdit) {
        val lines = body.split("\n").subList(segment.firstLine, segment.lastLine + 1)
        val next = replaceTable(body, segment.firstLine..segment.lastLine, change(TableEdit(lines)).lines())
        if (next == body) return
        spans = if (day != null) emptyList() else remapSpans(body, next, spans).spans
        follow(next)
        body = next
        changed()
    }

    /**
     * A calculation into the note: the whole sum, "5+3=8", at the cursor the
     * first time and then each on its own line after the one before -- the
     * desktop's Insert into note.
     */
    fun insertCalculation(formula: String) {
        val placed = Calculator.place(body, formula, calcMark, caret)
        spans = if (day != null) emptyList() else remapSpans(body, placed.body, spans).spans
        props = Paragraphs.follow(body, placed.body, props)
        body = placed.body
        calcMark = placed.mark
        caret = placed.mark
        // The field's own copy of its text is stale now: it takes the body's.
        focused = null
        changed()
    }

    // ---- Copy and paste, formatting kept ----

    /** A selection in the body, with the text it was made in: what a copy or a cut takes. */
    private class Picked(val from: Int, val to: Int, val body: String, val spans: List<FormatSpan>,
                         val props: Map<Int, Paragraphs.Props>)

    private var picked: Picked? = null

    /** The field's selection, noted for a copy or a cut: a cut takes the words out before it copies them. */
    fun pick(segment: Editing.Segment, value: TextFieldValue) {
        if (value.selection.collapsed) return
        val (from, to) = Editing.bodyRange(segment, value.selection.min, value.selection.max)
        picked = Picked(from, to, body, spans, props)
    }

    /**
     * A copy or a cut of [plain] from a field: when it is the selection
     * noted, the HTML to put beside it on the clipboard -- so it arrives
     * elsewhere formatted -- and the copy kept for a paste back into a
     * note. Null lets the plain copy stand: a day's own text is plain.
     */
    fun copyOut(plain: String): String? {
        if (day != null) return null
        val p = picked ?: return null
        val copy = Pasting.copyOut(p.body, p.spans, p.props, p.from, p.to) ?: return null
        if (copy.text != plain) return null
        state.ownCopy = copy
        return copy.html
    }

    /**
     * A paste the field was about to make, made here instead: HTML with its
     * formatting, or table rows on lines of their own. False lets the
     * field's own plain paste stand -- ordinary text, or a day's own text,
     * which is plain.
     */
    fun paste(segment: Editing.Segment, old: TextFieldValue, html: String?, plain: String): Boolean {
        if (day != null) return false
        val (from, to) = Editing.bodyRange(segment, old.selection.min, old.selection.max)
        val read = if (html.isNullOrEmpty()) null else Pasting.fromHtml(html, state.ownCopy)
        val done = if (read != null) {
            Pasting.insert(body, spans, props, from, to, read.text, read.formatting, read.paragraphs)
        } else {
            val rows = Pasting.pipeRows(plain) ?: return false
            Pasting.insert(body, spans, props, from, to, rows)
        }
        land(done)
        if (read?.textCut == true) {
            state.say("Only the first ${"%,d".format(Locale.ENGLISH, Pasting.MAX_PASTE_CHARS)} characters of what you " +
                "copied were pasted; the rest was left out. To paste all of it, undo, then choose " +
                "Paste as plain text: that pastes everything, without its formatting.")
        } else if (read?.stylesCut == true) {
            state.say("All of the text was pasted, but its formatting stops after the first " +
                "${"%,d".format(Locale.ENGLISH, Pasting.MAX_PASTE_SPANS)} styled pieces.")
        }
        return true
    }

    /**
     * "Paste as plain text": the words alone -- a PDF's column rejoined,
     * markers that would make a list taken off -- as the desktop's
     * Ctrl+Shift+V pastes them.
     */
    fun pastePlain(segment: Editing.Segment, old: TextFieldValue, raw: String) {
        val text = Pasting.plainText(raw.replace("\r\n", "\n").replace("\r", "\n"))
        if (text.isEmpty() && old.selection.collapsed) return
        val (from, to) = Editing.bodyRange(segment, old.selection.min, old.selection.max)
        land(Pasting.insert(body, spans, if (day != null) emptyMap() else props, from, to, text))
    }

    /** A paste written: the note, one step of Undo, and the cursor after what came in. */
    private fun land(done: Pasting.Inserted) {
        calcMark = Calculator.follow(body, done.body, calcMark)
        spans = if (day != null) emptyList() else done.spans
        props = if (day != null) emptyMap() else done.props
        body = done.body
        caret = done.caret
        changed()
        val chars = done.body.codePoints().toArray()
        val line = (0 until done.caret.coerceAtMost(chars.size)).count { chars[it] == '\n'.code }
        // Rows pasted become a table, and the field the cursor is in may be another one now.
        val index = Editing.segments(body).indexOfFirst { !it.table && line in it.firstLine..it.lastLine }
        if (index >= 0) ask(index, fieldValue(index, done.caret)) else focused = null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(state: AppState, openId: Int) {
    val palette = LocalPalette.current
    // Not keyed on the id: a new note's first save gives it one, and the
    // editor goes on as it was.
    val model = remember(state.opening, state.openDay) {
        EditorModel(state, state.note(openId), state.openDay, state.newNoteDate)
    }
    val calc = remember(model) { CalculatorState() }
    val exporter = rememberExporter(state)
    var exportAsk by remember { mutableStateOf(false) }
    var mailAsk by remember { mutableStateOf(false) }
    var pickTemplate by remember { mutableStateOf<List<org.json.JSONObject>?>(null) }
    var tableAsk by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf<org.json.JSONObject?>(null) }
    val templateNone = stringResource(R.string.template_none)
    val templateNothing = stringResource(R.string.template_nothing)
    val templateStarted = stringResource(R.string.template_started)
    val find = remember(model) { FindState() }
    val findFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Worked out again as the note or the search changes; nothing at all
    // while the bar is shut.
    val matches = remember(model.body, state.findQuery, state.findMatchCase, find.open) {
        if (find.open) Finding.scan(model.body, state.findQuery, state.findMatchCase) else emptyList()
    }
    val bySegment = remember(matches) { matches.groupBy { it.segment } }
    val currentIndex = Finding.clamp(find.index, matches.size)
    val currentMatch = matches.getOrNull(currentIndex)

    fun closeFind() {
        find.open = false
        focusManager.clearFocus()
    }
    val note = state.note(model.id)
    var details by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var askRestore by remember { mutableStateOf(false) }
    var cell by remember { mutableStateOf<Pair<Editing.Segment, CellAt>?>(null) }
    // One day of a series: what it holds of its own, and where it came from.
    val day = model.day
    val raw = state.rawNote(model.id)
    val ownEdit = day != null && (raw?.opt("overrides") as? org.json.JSONObject)?.has(day) == true
    val cameFrom = if (day != null && raw != null) Recurrence.movedFrom(raw, day) else null
    var asking by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf(false) }
    var walkingOff by remember { mutableStateOf<List<String>?>(null) }

    fun leave() {
        model.save()
        state.closeNote()
    }

    BackHandler { leave() }
    // Back closes the find bar first, then the calculator, then the note.
    BackHandler(enabled = calc.open) { closeCalculator(calc, focusManager) }
    BackHandler(enabled = find.open) { closeFind() }
    // Saved a moment after the last change, as the desktop's autosave is,
    // and when the app goes to the background -- where most notes on a
    // phone are left, by Home or by switching apps rather than by Back.
    LaunchedEffect(model.edits) {
        if (model.unsaved) {
            delay(800)
            model.save()
        }
    }
    DisposableEffect(model) {
        state.flush = { model.save() }
        onDispose { state.flush = null }
    }
    // Written from outside while open -- pinned from the list beside it,
    // updated by an import -- and saved first by whatever wrote it: read
    // back, so the next save cannot put the old fields back.
    val rewrittenAt = remember(model) { state.openNoteRewritten }
    LaunchedEffect(model, state.openNoteRewritten) {
        if (state.openNoteRewritten != rewrittenAt) state.note(model.id)?.let { model.reloadFrom(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            // A keyboard plugged into a tablet: the desktop's own keys.
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || !event.isCtrlPressed) {
                    return@onPreviewKeyEvent false
                }
                when {
                    event.key == androidx.compose.ui.input.key.Key.Z && event.isShiftPressed -> { model.redo(); true }
                    event.key == androidx.compose.ui.input.key.Key.Z -> { model.undo(); true }
                    event.key == androidx.compose.ui.input.key.Key.Y -> { model.redo(); true }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }) { Text("←", fontSize = 22.sp, color = palette.text) }
            // Undo and Redo, the desktop's Ctrl+Z and Ctrl+Y: the note's
            // words and their styling, a step at a time.
            val undoSaid = stringResource(R.string.undo)
            val redoSaid = stringResource(R.string.redo)
            IconButton(enabled = model.canUndo, onClick = { model.undo() },
                       modifier = Modifier.semantics { contentDescription = undoSaid }) {
                Text("↶", fontSize = 22.sp, color = if (model.canUndo) palette.text else palette.hairline)
            }
            IconButton(enabled = model.canRedo, onClick = { model.redo() },
                       modifier = Modifier.semantics { contentDescription = redoSaid }) {
                Text("↷", fontSize = 22.sp, color = if (model.canRedo) palette.text else palette.hairline)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    model.problem != null -> stringResource(R.string.not_saved)
                    model.status == "follows" -> stringResource(R.string.day_follows)
                    model.status == "saved" && day != null -> stringResource(R.string.day_saved)
                    model.status == "saved" -> stringResource(R.string.auto_saved)
                    else -> ""
                },
                color = if (model.problem != null) palette.dangerText else palette.subtext,
                fontSize = 12.sp,
            )
            IconButton(onClick = {
                if (model.save(force = model.id == 0)) state.shareCode(listOf(model.id))?.let { sendText(state, it) }
            }) { Text("🔗", fontSize = 18.sp) }
            IconButton(onClick = { details = true }) { Text("ⓘ", fontSize = 20.sp, color = palette.text) }
            Box {
                IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 20.sp, color = palette.text) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.find_in_note)) },
                        onClick = {
                            menu = false
                            // Words selected in the note are what is looked for.
                            Finding.prefill(model.focused?.let { it.text.substring(it.selection.min, it.selection.max) })
                                ?.let { state.findQuery = it }
                            // The last search, selected: typing replaces it.
                            find.field = TextFieldValue(state.findQuery, TextRange(0, state.findQuery.length))
                            find.index = 0
                            find.reveal += 1
                            find.open = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.calculator)) },
                        onClick = {
                            menu = false
                            // The note lets go of the keyboard, keeping where its
                            // cursor was: that is where Insert writes.
                            focusManager.clearFocus()
                            calc.open = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.template_use)) },
                        onClick = {
                            menu = false
                            val templates = Templates.load(state.dir)
                            if (templates.isEmpty()) {
                                state.say(templateNone)
                            } else {
                                // What is here is saved first: the template
                                // starts a new note, it replaces nothing.
                                model.save()
                                pickTemplate = templates
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.template_save_as)) },
                        onClick = {
                            menu = false
                            model.save()
                            val raw = state.rawNote(model.id)
                            // The note as saved -- with its styles and lines --
                            // or, on one day of a series, that day as shown.
                            val tpl = if (day == null && raw != null) {
                                Templates.fromNote("", raw.optString("title"), raw.optString("body"),
                                    raw.optJSONArray("formatting"), raw.optJSONArray("paragraphs"),
                                    raw.optString("comments"), raw.optString("category"), raw.optJSONArray("checklist"))
                            } else {
                                Templates.fromNote("", model.title, model.body, Templates.spansJson(model.spans), null,
                                    model.comments, model.category, raw?.optJSONArray("checklist"))
                            }
                            if (Templates.hasContent(tpl.optString("title"), tpl.optString("body"),
                                    tpl.optString("comments"), tpl.optJSONArray("checklist"))) {
                                saveTemplate = tpl
                            } else {
                                state.say(templateNothing)
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_duplicate)) },
                        onClick = {
                            menu = false
                            if (model.save(force = model.id == 0)) state.duplicate(model.id)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_export)) },
                        onClick = {
                            menu = false
                            // The note as saved is what goes out: saved first.
                            if (model.save(force = model.id == 0)) exportAsk = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_mail)) },
                        onClick = {
                            menu = false
                            // As an export: the note as saved is what goes.
                            if (model.save(force = model.id == 0)) mailAsk = true
                        },
                    )
                    if (day != null) {
                        // A day of a series is deleted on its own, or the series
                        // is: the desktop's two answers, as two entries.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_day_delete), color = palette.dangerText) },
                            onClick = { menu = false; model.save(); asking = "day" },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_series_delete), color = palette.dangerText) },
                            onClick = { menu = false; model.save(); asking = "series" },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_trash), color = palette.dangerText) },
                            onClick = {
                                menu = false
                                if (model.id == 0 && model.title.isBlank() && model.body.isBlank()) {
                                    state.closeNote()
                                } else if (model.save(force = model.id == 0)) {
                                    state.trash(listOf(model.id))
                                }
                            },
                        )
                    }
                }
            }
        }

        if (find.open) {
            FindBar(
                find = find,
                counter = Finding.counter(currentIndex, matches.size, state.findQuery),
                missed = state.findQuery.isNotEmpty() && matches.isEmpty(),
                matchCase = state.findMatchCase,
                focus = findFocus,
                onQuery = { value ->
                    find.field = value
                    if (value.text != state.findQuery) {
                        // A new search: its first match, brought into sight.
                        state.findQuery = value.text
                        find.index = 0
                        find.reveal += 1
                    }
                },
                // The same search counted again, where the reader already is.
                onCase = { state.findMatchCase = !state.findMatchCase },
                onStep = { find.step(it, matches.size) },
                onClose = { closeFind() },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            model.problem?.let {
                Text(stringResource(R.string.not_saved_because, it), color = palette.dangerText, fontSize = 13.sp,
                     modifier = Modifier.padding(bottom = 10.dp))
            }
            if (day != null) {
                // Day mode says so, and names the way to the whole series --
                // a field test once could not find how to edit just one day,
                // nor how to leave it.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .padding(12.dp),
                ) {
                    Text(stringResource(R.string.day_banner, Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day),
                         color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    // Wrapping: three ways out of one day do not fit a
                    // phone's width on one line.
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.day_whole_series), color = palette.accentText, fontSize = 13.sp,
                             fontWeight = FontWeight.Medium,
                             modifier = Modifier.clickable { model.save(); state.openWholeSeries() })
                        Text(stringResource(R.string.day_move), color = palette.accentText, fontSize = 13.sp,
                             fontWeight = FontWeight.Medium, modifier = Modifier.clickable { moving = true })
                        if (ownEdit || cameFrom != null) {
                            Text(stringResource(R.string.day_reset), color = palette.accentText, fontSize = 13.sp,
                                 fontWeight = FontWeight.Medium, modifier = Modifier.clickable { model.save(); asking = "reset" })
                        }
                    }
                }
            }
            // The whole series, with days whose own edits its repeat no longer
            // reaches: no calendar shows them, so the note says so and offers
            // the way to each -- the desktop's button under its banner.
            val offDays = if (day == null && raw != null) Recurrence.offScheduleDays(raw) else emptyList()
            if (offDays.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.off_schedule_banner, offDays.size, offDays.size),
                    color = palette.accentText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .clickable { model.save(); walkingOff = offDays }
                        .padding(12.dp),
                )
            }
            if (note?.hasPrevious == true) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.card)
                        .clickable { askRestore = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.restore_banner), color = palette.subtext, fontSize = 13.sp,
                         modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.restore_previous), color = palette.accentText,
                         fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            BasicTextField(
                value = model.title,
                onValueChange = {
                    // The desktop's ceiling: far more than a title needs.
                    model.title = it.take(200)
                    model.changed(Editing.History.TYPING + ":title")
                },
                textStyle = TextStyle(color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) model.focus = null },
                decorationBox = { field ->
                    if (model.title.isEmpty()) {
                        Text(stringResource(R.string.title_hint), color = palette.subtext, fontSize = 22.sp,
                             fontWeight = FontWeight.Medium)
                    }
                    field()
                },
            )
            if (model.title.length >= 160) {
                Text("${model.title.length}/200", fontSize = 11.sp,
                     color = if (model.title.length >= 200) palette.dangerText else palette.subtext)
            }
            Text(
                // On one day of a series, the day being written -- not the
                // series' first one, which read as the wrong date here.
                text = listOfNotNull(model.category, Recurrence.showDate(day ?: model.date).ifBlank { null }).joinToString("  ·  "),
                color = palette.legibleOnCard(model.color.ifEmpty { state.load.categories[model.category] ?: palette.hex.getValue("accent") }),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp).clickable { details = true },
            )

            val segments = remember(model.body) { Editing.segments(model.body) }
            segments.forEachIndexed { index, segment ->
                val here = bySegment[index].orEmpty()
                if (segment.table) {
                    val table = remember(segment.text) { parseTable(segment.text.split("\n")) }
                    // A cell's highlight as the export colours it, the
                    // file's line turned into the grid's row.
                    val fills = remember(model.body, model.spans, segment) {
                        Exporting.cellHighlights(model.body, model.spans, segment.firstLine, segment.lastLine)
                            .mapNotNull { (key, hex) ->
                                val (line, column) = key
                                if (line == table.separatorAt) return@mapNotNull null
                                val row = if (table.separatorAt in 0 until line) line - 1 else line
                                hexColor(hex)?.let { CellAt(row, column) to blend(it, palette.card, 0.45f) }
                            }.toMap()
                    }
                    TableView(
                        table = table,
                        modifier = Modifier.padding(vertical = 8.dp),
                        editing = true,
                        onCellTap = { at -> cell = segment to at },
                        place = model.props[segment.firstLine]?.align ?: "left",
                        highlights = fills,
                        found = here.mapNotNull { (it as? Finding.InCell)?.let { m -> CellAt(m.row, m.column) } }.toSet(),
                        current = (currentMatch as? Finding.InCell)?.takeIf { it.segment == index }
                            ?.let { CellAt(it.row, it.column) },
                        reveal = find.reveal,
                    )
                } else {
                    ProseField(
                        model, index, segment, placeholder = segments.size == 1,
                        found = here.filterIsInstance<Finding.InText>(),
                        match = (currentMatch as? Finding.InText)?.takeIf { it.segment == index },
                        reveal = find.reveal,
                    )
                }
            }

            ChecklistSection(state, model)
            CommentsSection(model)
            AttachmentsSection(state, model)
            Spacer(Modifier.height(48.dp))
        }

        // The calculator stands aside while the keyboard is up for the note --
        // the two together would leave no note to see -- and comes back when
        // it goes down. Typed into, its own display keeps it in place.
        if (calc.open && (!WindowInsets.isImeVisible || calc.displayFocused)) {
            CalculatorPanel(calc, onInsert = { model.insertCalculation(it) },
                            onClose = { closeCalculator(calc, focusManager) })
        } else if (model.focus != null) {
            if (day == null) FormatBar(model) { tableAsk = true }
            else Text(stringResource(R.string.day_plain), color = palette.subtext, fontSize = 12.sp,
                      modifier = Modifier.fillMaxWidth().background(palette.card).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
    walkingOff?.let { days ->
        OffScheduleQuestions(state, model.id, days, onDone = { walkingOff = null })
    }
    if (day != null && asking != null) {
        DayQuestions(state, model.id, day, cameFrom, ownEdit, asking) {
            asking = null
            // A reset changes what this day shows: shown again from the file.
            state.note(model.id)?.let { model.reloadFrom(it) }
        }
    }

    if (moving && day != null) {
        // The calendar opened on the day being moved; what was typed goes
        // with it -- the move saves it first.
        DateDialog(day, onPick = { to ->
            moving = false
            state.moveDay(model.id, day, to)
        }) { moving = false }
    }
    if (details) DetailsSheet(state, model, note, onDismiss = { details = false })
    if (tableAsk) {
        TablePicker(
            onPick = { rows, columns ->
                tableAsk = false
                // The new table's first cell, open for typing -- as the
                // desktop opens it the moment the table is made.
                val first = model.insertTable(rows, columns)
                Editing.segments(model.body).firstOrNull { it.table && it.firstLine == first }
                    ?.let { cell = it to CellAt(0, 0) }
            },
            onDismiss = { tableAsk = false },
        )
    }
    pickTemplate?.let { templates ->
        TemplatePicker(
            templates,
            onPick = { tpl ->
                pickTemplate = null
                state.useTemplate(tpl, templateStarted)
            },
            onDismiss = { pickTemplate = null },
        )
    }
    saveTemplate?.let { tpl ->
        SaveTemplateDialog(state, tpl, Templates.defaultName(tpl.optString("title")), onDone = { saveTemplate = null })
    }
    if (exportAsk) {
        ExportDialog(
            onPick = { format ->
                exportAsk = false
                exporter.export(model.id, format)
            },
            onDismiss = { exportAsk = false },
        )
    }
    if (mailAsk) MailDialog(state, model.id) { mailAsk = false }
    cell?.let { (segment, at) ->
        // Closed, the sheet hands the keyboard to nothing: left to itself,
        // focus came back to the title and its keyboard rose unasked.
        CellSheet(model, segment, at, onDone = {
            cell = null
            focusManager.clearFocus()
        })
    }
    if (askRestore && note != null) {
        val also = note.previousAlso
        Ask(
            title = stringResource(R.string.restore_title),
            text = if (also.isEmpty()) stringResource(R.string.restore_text_only)
                   else stringResource(R.string.restore_text_with, joinWithAnd(also)),
            confirm = stringResource(R.string.restore_confirm),
            onConfirm = {
                askRestore = false
                // Whatever waits for its autosave is saved first, so the
                // version being replaced is the one on screen.
                model.save()
                if (state.report(Notebook.restorePrevious(state.load, model.id))) {
                    state.note(model.id)?.let { model.reloadFrom(it) }
                    state.say("Restored the previous version")
                }
            },
            onDismiss = { askRestore = false },
        )
    }
}

private fun closeCalculator(calc: CalculatorState, focusManager: androidx.compose.ui.focus.FocusManager) {
    // The display's keyboard goes with it, if it was up.
    if (calc.displayFocused) focusManager.clearFocus()
    calc.displayFocused = false
    calc.open = false
}

/** "a", "a and b", "a, b and c" -- how the desktop's question lists things. */
private fun joinWithAnd(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/**
 * One run of prose, typed into directly, its styling drawn as it is
 * typed. The cursor and the selection are kept in the model, so the format
 * bar knows what they cover.
 */
@Composable
private fun ProseField(
    model: EditorModel,
    index: Int,
    segment: Editing.Segment,
    placeholder: Boolean,
    found: List<Finding.InText> = emptyList(),
    match: Finding.InText? = null,
    reveal: Int = 0,
) {
    val palette = LocalPalette.current
    val local = Editing.localSpans(model.spans, segment)
    val current = model.focused
    val value = if (model.focus == index && current != null && current.text == segment.text) current
                else TextFieldValue(segment.text, TextRange(segment.text.length))
    // The find bar's current match, scrolled into sight when it asks: the
    // line it is on, not merely this field, which may be pages long.
    val requester = remember { BringIntoViewRequester() }
    // A caption just added, a line just made above a table: the keyboard
    // here, at the place the model chose.
    val focuser = remember { FocusRequester() }
    LaunchedEffect(model.focusAsk) {
        if (model.focusAsk?.first == index) {
            focuser.requestFocus()
            model.focusTaken()
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    LaunchedEffect(reveal) {
        val at = match ?: return@LaunchedEffect
        val drawn = layout
        requester.bringIntoView(
            if (drawn != null && at.start < drawn.layoutInput.text.length) drawn.getBoundingBox(at.start) else null
        )
    }
    // The field's copies and pastes go through the note's clipboard, which
    // adds the HTML to a copy and makes a paste with its formatting.
    val context = LocalContext.current
    val platformClipboard = LocalClipboard.current
    val clipboard = remember(platformClipboard, model) { NoteClipboard(platformClipboard, model, context) }
    val shownNow by rememberUpdatedState(value)
    val plainLabel = stringResource(R.string.paste_plain)
    CompositionLocalProvider(LocalClipboard provides clipboard) {
    BasicTextField(
        value = value,
        onValueChange = { next ->
            val pasted = clipboard.pasteOf(value, next)
            if (pasted != null && model.paste(segment, value, pasted.html, pasted.text)) return@BasicTextField
            // A keystroke that meant a list operation comes back rewritten.
            val shown = (if (next.text != segment.text) model.typeInto(segment, next, index) else null) ?: next
            model.focus = index
            model.focused = shown
            model.caret = caretIn(segment, shown)
            model.pick(segment, shown)
        },
        visualTransformation = { text ->
            val drawn = renderBody(Note(body = text.text, formatting = local), palette.bg).text
            TransformedText(paintLines(paintFound(drawn, found, match, palette), segment.firstLine, model.props),
                            OffsetMapping.Identity)
        },
        onTextLayout = { layout = it },
        textStyle = TextStyle(color = palette.text, fontSize = BODY_SIZE.sp, lineHeight = BODY_LINE_HEIGHT.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (placeholder) 160.dp else 24.dp)
            .focusRequester(focuser)
            .bringIntoViewRequester(requester)
            // The desktop's Ctrl+Shift+V, for a keyboard: the words alone.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed || !event.isShiftPressed ||
                    event.key != Key.V) return@onPreviewKeyEvent false
                clipboard.plainNow()?.let { model.pastePlain(segment, shownNow, it) }
                true
            }
            // ...and on the selection menu, when what is on the clipboard has formatting to leave behind.
            .appendTextContextMenuComponents {
                if (clipboard.hasFormatting()) {
                    item(PASTE_PLAIN, plainLabel) {
                        clipboard.plainNow()?.let { model.pastePlain(segment, shownNow, it) }
                        close()
                    }
                }
            }
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    model.focus = index
                    if (model.focused?.text != segment.text) model.focused = value
                    model.caret = caretIn(segment, value)
                } else if (model.focus == index) {
                    model.focus = null
                }
            },
        decorationBox = { field ->
            if (placeholder && segment.text.isEmpty()) {
                Text(stringResource(R.string.body_hint), color = palette.subtext, fontSize = 16.sp)
            }
            field()
        },
    )
    }
}

/** The selection menu's own item, told apart from the field's. */
private val PASTE_PLAIN = Any()

/**
 * The phone's clipboard as a note's field sees it. A copy or a cut the
 * field makes gets its HTML beside the words; a paste the field makes is
 * recognised when its text arrives -- the clipboard read a moment before,
 * that very text put in place of the selection -- so the editor can make
 * it instead, with the formatting the clipboard's HTML carries.
 *
 * Done here, under the field, rather than by replacing the menu's Paste:
 * the menu's own items keep their place and their words, and a keyboard's
 * Ctrl+V comes the same way.
 */
private class NoteClipboard(private val inner: Clipboard, private val model: EditorModel,
                            private val context: android.content.Context) : Clipboard {
    private var seen: ClipData? = null
    private var seenAt = 0L

    override val nativeClipboard: android.content.ClipboardManager get() = inner.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? {
        val entry = inner.getClipEntry()
        seen = entry?.clipData
        seenAt = SystemClock.uptimeMillis()
        return entry
    }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        val plain = clipEntry?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        val html = plain?.let { model.copyOut(it) }
        inner.setClipEntry(if (plain != null && html != null) ClipEntry(ClipData.newHtmlText("Note", plain, html)) else clipEntry)
    }

    class Pasted(val text: String, val html: String?)

    /** What [next] pasted over [old], if it is the paste of what the clipboard just gave. */
    fun pasteOf(old: TextFieldValue, next: TextFieldValue): Pasted? {
        val clip = seen ?: return null
        if (SystemClock.uptimeMillis() - seenAt > 2000) return null
        val item = clip.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
        val text = item.text?.toString() ?: return null
        val lo = old.selection.min
        val hi = old.selection.max
        val expected = old.text.substring(0, lo) + text + old.text.substring(hi)
        if (next.text != expected) return null
        seen = null
        return Pasted(text, item.htmlText)
    }

    /** The clipboard's words, as text, now. */
    fun plainNow(): String? =
        nativeClipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()

    /** Whether what is on the clipboard carries formatting -- read from its description, not its content. */
    fun hasFormatting(): Boolean =
        nativeClipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
}

/** One step of indent, as drawn -- the desktop's margin step, near enough. */
private const val INDENT_STEP = 24

/** The note's text: its size and the height of its lines, in sp. */
private const val BODY_SIZE = 16
private const val BODY_LINE_HEIGHT = 24

/**
 * The lines of one field with their indent and alignment, and a list
 * item's wrapped lines hanging after its marker, as the desktop's margins
 * draw them. [firstLine] is the field's first line in the whole body.
 */
private fun paintLines(text: AnnotatedString, firstLine: Int, props: Map<Int, Paragraphs.Props>): AnnotatedString {
    val lines = text.text.split("\n")
    val styled = lines.withIndex().any { (i, line) -> props[firstLine + i] != null || Paragraphs.parseListMarker(line).kind != null }
    if (!styled) return text
    // Every line its own paragraph, and the line breaks drawn as nothing:
    // a paragraph always starts on a new line, and a break kept inside one
    // drew an empty line after it (a list came out double-spaced), while a
    // break left between two became an empty paragraph of its own. The
    // stand-in is one character for one, so every position stays where
    // the field's text has it.
    val paragraphs = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
    var at = 0
    for ((i, line) in lines.withIndex()) {
        val end = at + line.length + if (i < lines.lastIndex) 1 else 0
        val p = props[firstLine + i]
        val list = Paragraphs.parseListMarker(line).kind != null
        val indent = (p?.indent ?: 0) * INDENT_STEP
        val style = ParagraphStyle(
            textAlign = when (p?.align) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            },
            textIndent = TextIndent(indent.sp, (indent + if (list) 16 else 0).sp),
            // The field's own spacing, which a paragraph's style replaces --
            // untrimmed: trimmed at a paragraph's first and last line, a
            // one-line paragraph kept none of it and the lines bunched up.
            lineHeight = BODY_LINE_HEIGHT.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        if (end > at) paragraphs.add(AnnotatedString.Range(style, at, end))
        at = end
    }
    return AnnotatedString(text.text.replace('\n', '​'), text.spanStyles, paragraphs)
}

/**
 * The cursor of a field as a place in the whole body, in code points: the
 * field's start plus what comes before the cursor in its own text.
 */
private fun caretIn(segment: Editing.Segment, value: TextFieldValue): Int {
    val end = value.selection.end.coerceIn(0, value.text.length)
    return segment.cpStart + value.text.codePointCount(0, end)
}

/**
 * Bold, italic, underline, strike, a text colour and a highlight, over
 * what is selected in the field that has the cursor -- sitting above the
 * keyboard, where the thumb already is and where Android's own selection
 * menu does not reach.
 */
@Composable
private fun FormatBar(model: EditorModel, onTable: () -> Unit) {
    val palette = LocalPalette.current
    val segments = Editing.segments(model.body)
    val index = model.focus ?: return
    val segment = segments.getOrNull(index) ?: return
    val value = model.focused ?: return
    val hasSelection = value.selection.max > value.selection.min
    val (from, to) = Editing.bodyRange(segment, value.selection.min, value.selection.max)
    val active = stylesOver(model.spans, from, to)
    var palettePicker by remember { mutableStateOf("") }

    fun apply(style: String, colour: String = "") {
        if (!hasSelection) return
        model.spans = toggleStyle(model.spans, from, to, style, colour)
        model.changed()
    }

    Column(Modifier.fillMaxWidth().background(palette.sidebar)) {
        if ((palettePicker == "size" || palettePicker == "face") && hasSelection) {
            // The desktop's two drop-downs, as a row of choices: Default
            // takes the span away, so the note's own size or face shows.
            val style = if (palettePicker == "size") "font_size" else "font_family"
            val choices = if (palettePicker == "size") Fonts.SIZES.map { it.toString() } else Fonts.FAMILIES
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (choice in listOf(Fonts.DEFAULT) + choices) {
                    Text(
                        choice, color = palette.text, fontSize = 14.sp,
                        fontFamily = when (Fonts.kindOf(choice)) {
                            "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
                            "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
                            else -> null
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.card)
                            .clickable {
                                model.spans = if (choice == Fonts.DEFAULT) clearStyle(model.spans, from, to, style)
                                              else setStyle(model.spans, from, to, style, choice)
                                model.changed()
                                palettePicker = ""
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        } else if (palettePicker.isNotEmpty() && hasSelection) {
            val style = if (palettePicker == "text") "text_color" else "highlight"
            val colours = if (palettePicker == "text") Themes.PRESET_NOTE_COLORS else Themes.HIGHLIGHT_COLOR_PALETTE
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "✕",
                    color = palette.subtext,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.inset)
                        .clickable {
                            model.spans = clearStyle(model.spans, from, to, style)
                            model.changed()
                            palettePicker = ""
                        }
                        .padding(top = 6.dp),
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                for (colour in colours) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(hexColor(colour))
                            .clickable { apply(style, colour); palettePicker = "" },
                    )
                }
            }
        }
        if (!hasSelection) {
            Text(stringResource(R.string.select_to_format), color = palette.subtext, fontSize = 11.sp,
                 modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FormatKey("B", "bold" in active, hasSelection, weight = FontWeight.Bold) { apply("bold") }
            FormatKey("I", "italic" in active, hasSelection, italic = true) { apply("italic") }
            FormatKey("U", "underline" in active, hasSelection, decoration = TextDecoration.Underline) { apply("underline") }
            FormatKey("S", "strike" in active, hasSelection, decoration = TextDecoration.LineThrough) { apply("strike") }
            FormatKey("A", "text_color" in active, hasSelection, weight = FontWeight.Bold) {
                palettePicker = if (palettePicker == "text") "" else "text"
            }
            FormatKey("🖍", "highlight" in active, hasSelection) {
                palettePicker = if (palettePicker == "mark") "" else "mark"
            }
            FormatKey("Aa", "font_family" in active, hasSelection, weight = FontWeight.Medium) {
                palettePicker = if (palettePicker == "face") "" else "face"
            }
            // The size box says the size of what is selected, as the
            // desktop's does: "14" over text set at 14, "Default" over
            // text that has none of its own, or a mix. With nothing
            // selected, the size the next letter typed will take.
            val sizeHere = if (hasSelection) uniformValue(model.spans, from, to, "font_size")
                           else if (to > 0) uniformValue(model.spans, to - 1, to, "font_size") else null
            val sizeLabel = sizeHere ?: Fonts.DEFAULT
            FormatKey(sizeLabel, "font_size" in active, hasSelection, weight = FontWeight.Medium,
                      wide = sizeLabel.length > 3, said = stringResource(R.string.size_key_said, sizeLabel)) {
                palettePicker = if (palettePicker == "size") "" else "size"
            }
        }
        // The lines the cursor or the selection covers: lists, depth and
        // alignment, which need no selection -- the cursor's line will do.
        // Not in the empty field under a table that ends the note: it has
        // no line of its own until something is typed there.
        val lineKeys = !segment.isVirtual
        val firstLine = segment.firstLine + value.text.substring(0, value.selection.min).count { it == '\n' }
        val lastLine = segment.firstLine + value.text.substring(0, value.selection.max).count { it == '\n' }
        val lineText = model.body.split("\n").getOrElse(firstLine) { "" }
        val kind = Paragraphs.parseListMarker(lineText).kind
        val align = model.props[firstLine]?.align ?: "left"
        fun lines(done: Paragraphs.Done?) = model.applyLines(done, index, from, to)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FormatKey("•", kind == "bullet", lineKeys, said = stringResource(R.string.list_bullet)) {
                lines(Paragraphs.toggleList(model.body, model.props, firstLine, lastLine, "bullet"))
            }
            FormatKey("1.", kind == "number", lineKeys, said = stringResource(R.string.list_number)) {
                lines(Paragraphs.toggleList(model.body, model.props, firstLine, lastLine, "number"))
            }
            FormatKey("⇤", false, lineKeys, said = stringResource(R.string.indent_less)) {
                lines(Paragraphs.changeIndent(model.body, model.props, firstLine, lastLine, -1))
            }
            FormatKey("⇥", false, lineKeys, said = stringResource(R.string.indent_more)) {
                lines(Paragraphs.changeIndent(model.body, model.props, firstLine, lastLine, 1))
            }
            for ((way, said) in listOf("left" to R.string.align_left, "center" to R.string.align_center,
                                       "right" to R.string.align_right)) {
                AlignKey(way, align == way && (way != "left" || lineKeys), lineKeys, stringResource(said)) {
                    model.alignLines(firstLine, lastLine, way)
                }
            }
            FormatKey("▦", false, true, said = stringResource(R.string.table_insert), onClick = onTable)
        }
    }
}

/**
 * The desktop's size grid: the shape of a table is what is chosen, and a
 * grid shows it. Two columns at least -- a one-column run of pipes is not
 * a table row -- and the caption names the size as it is pointed at.
 */
@Composable
private fun TablePicker(onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var rows by remember { mutableIntStateOf(2) }
    var columns by remember { mutableIntStateOf(2) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.table_size, rows, columns)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Every column drawn, so the squares lit are the table's
                // shape; the first column chooses two, the least a table
                // has. (The desktop's grid starts at its second column, and
                // lights one column of squares for a two-column table.)
                for (r in 1..Editing.TABLE_ROWS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 1..Editing.TABLE_COLUMNS) {
                            val inside = r <= rows && c <= columns
                            val wide = maxOf(2, c)
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (inside) palette.accent else palette.inset)
                                    .clickable {
                                        // A first tap shows the shape; tapping the
                                        // shape already shown makes it.
                                        if (r == rows && wide == columns) onPick(r, wide) else { rows = r; columns = wide }
                                    },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.table_tap_again), color = palette.subtext, fontSize = 12.sp,
                     modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(rows, columns) }) {
                Text(stringResource(R.string.table_insert_button), color = palette.accentText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        containerColor = palette.card,
        titleContentColor = palette.text,
    )
}

/** An alignment key: four lines, drawn as the text would sit. */
@Composable
private fun AlignKey(align: String, on: Boolean, enabled: Boolean, said: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val ink = if (enabled) palette.text else palette.subtext.copy(alpha = 0.5f)
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) palette.calendarHasNotes else palette.card)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = said },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            listOf(1f, 0.6f, 0.85f, 0.5f).forEachIndexed { i, share ->
                val width = size.width * share
                val x = when (align) {
                    "center" -> (size.width - width) / 2
                    "right" -> size.width - width
                    else -> 0f
                }
                val y = size.height * (0.14f + i * 0.24f)
                drawLine(ink, Offset(x, y), Offset(x + width, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun FormatKey(
    label: String,
    on: Boolean,
    enabled: Boolean,
    weight: FontWeight = FontWeight.Normal,
    italic: Boolean = false,
    decoration: TextDecoration? = null,
    said: String? = null,
    // A word rather than a mark ("Default"): a wider key, smaller letters.
    wide: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .then(if (wide) Modifier.height(42.dp).widthIn(min = 64.dp) else Modifier.size(42.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) palette.calendarHasNotes else palette.card)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (said != null) Modifier.semantics { contentDescription = said } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) palette.text else palette.subtext.copy(alpha = 0.5f),
            fontSize = if (wide) 13.sp else 16.sp,
            fontWeight = weight,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = decoration,
            maxLines = 1,
            modifier = if (wide) Modifier.padding(horizontal = 8.dp) else Modifier,
        )
    }
}

/**
 * The checklist, under the text. Ticking, adding, renaming and removing
 * each write at once, as the desktop's do; the handle ⠿ drags a row to
 * another place.
 */
@Composable
private fun ChecklistSection(state: AppState, model: EditorModel) {
    val palette = LocalPalette.current
    val note = state.note(model.id)
    val items = note?.checklist ?: emptyList()
    var adding by remember { mutableStateOf("") }
    var open by remember(model.id) { mutableStateOf(items.isNotEmpty()) }

    fun ensureSaved(): Int? {
        if (model.id == 0 && !model.save(force = true)) return null
        return model.id
    }

    if (!open && items.isEmpty()) {
        Text(
            stringResource(R.string.add_checklist),
            color = palette.accentText,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 18.dp).clickable { open = true }.padding(vertical = 6.dp),
        )
        return
    }
    SectionLabel(stringResource(R.string.checklist_label, items.count { it.done }, items.size))
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val rowHeight = 44f * (androidx.compose.ui.platform.LocalDensity.current.density)
    items.forEachIndexed { index, item ->
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = if (dragging == index) offset else 0f }
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "⠿",
                color = palette.subtext,
                fontSize = 18.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .pointerInput(index, items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = index; offset = 0f },
                            onDragEnd = {
                                val to = (index + Math.round(offset / rowHeight)).coerceIn(0, items.size - 1)
                                dragging = -1
                                offset = 0f
                                if (to != index) state.report(Notebook.moveChecklistItem(state.load, model.id, index, to))
                            },
                            onDragCancel = { dragging = -1; offset = 0f },
                            onDrag = { change, amount -> change.consume(); offset += amount.y },
                        )
                    },
            )
            Text(
                if (item.done) "☑" else "☐",
                color = if (item.done) palette.successText else palette.text,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable { state.report(Notebook.setChecklistDone(state.load, model.id, index, !item.done)) }
                    .padding(end = 10.dp),
            )
            ChecklistText(item.text, item.done) { text ->
                state.report(Notebook.renameChecklistItem(state.load, model.id, index, text))
            }
            Text(
                "✕",
                color = palette.subtext,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { state.report(Notebook.removeChecklistItem(state.load, model.id, index)) }
                    .padding(8.dp),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        BasicTextField(
            value = adding,
            onValueChange = { adding = it },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { field ->
                if (adding.isEmpty()) Text(stringResource(R.string.checklist_new), color = palette.subtext, fontSize = 15.sp)
                field()
            },
        )
        Text(
            stringResource(R.string.add),
            color = if (adding.isBlank()) palette.subtext else palette.accentText,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(enabled = adding.isNotBlank()) {
                    val id = ensureSaved() ?: return@clickable
                    if (state.report(Notebook.addChecklistItem(state.load, id, adding.trim()))) adding = ""
                }
                .padding(12.dp),
        )
    }
}

/** A row's words, renamed when the field is left -- one write per edit, not per letter. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ChecklistText(
    text: String, done: Boolean, onRename: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var draft by remember(text) { mutableStateOf(text) }
    BasicTextField(
        value = draft,
        onValueChange = { draft = it },
        singleLine = true,
        textStyle = TextStyle(
            color = if (done) palette.subtext else palette.text,
            fontSize = 15.sp,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier
            .weight(1f)
            .onFocusChanged { if (!it.isFocused && draft != text) onRename(draft) },
    )
}

/** Comments: plain text under the note, saved with it. */
@Composable
private fun CommentsSection(model: EditorModel) {
    val palette = LocalPalette.current
    var open by remember(model.id) { mutableStateOf(model.comments.isNotEmpty()) }
    if (!open) {
        Text(
            stringResource(R.string.add_comments),
            color = palette.accentText,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp).clickable { open = true }.padding(vertical = 6.dp),
        )
        return
    }
    SectionLabel(stringResource(R.string.comments_label))
    BasicTextField(
        value = model.comments,
        onValueChange = { model.comments = it; model.changed(Editing.History.TYPING + ":comments") },
        textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.card)
            .padding(12.dp),
    )
}

/**
 * The note's attached files, each opened with the phone's own apps, taken
 * off with ✕ -- which asks first, as the desktop does, the stored copy
 * going with it -- and more added from Android's own file picker. A day of
 * a series shows the series' files and adds none: they are never a day's.
 */
@Composable
private fun AttachmentsSection(state: AppState, model: EditorModel) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val files = if (model.id == 0) emptyList() else state.attachmentsOf(model.id)
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tooMany by remember { mutableStateOf<Pair<List<android.net.Uri>, Int>?>(null) }
    val fullText = stringResource(R.string.attach_full, Normalize.MAX_ATTACHMENTS)

    fun attach(uris: List<android.net.Uri>) {
        val id = model.id
        scope.launch {
            // Copied off the picker's stream into the app's cache first,
            // away from the screen's thread; moved into place by the write.
            val copied = withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.mapNotNull { uri -> copyToCache(context, uri) }
            }
            state.attach(id, copied.map { (name, temp) ->
                Organizing.Incoming(name) { dest -> if (!temp.renameTo(dest)) { temp.copyTo(dest); temp.delete() } }
            }, unreadable = uris.size - copied.size)
            copied.forEach { (_, temp) -> temp.delete() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // A new note is saved first: only a saved note can hold files.
        if (!model.save(force = model.id == 0)) return@rememberLauncherForActivityResult
        val room = Organizing.roomFor(state.load, model.id)
        when {
            room == 0 -> state.say(fullText)
            uris.size > room -> tooMany = uris to room
            else -> attach(uris)
        }
    }

    if (files.isNotEmpty()) SectionLabel(stringResource(R.string.files_label, files.size))
    for ((name, stored) in files) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📎  $name",
                color = palette.accentText,
                fontSize = 15.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { state.openFile?.invoke(File(state.attachments, stored), name) }
                    .padding(vertical = 8.dp),
            )
            if (model.day == null) {
                val removeLabel = stringResource(R.string.attach_remove_said, name)
                Text("✕", color = palette.subtext, fontSize = 14.sp,
                     modifier = Modifier
                         .semantics { contentDescription = removeLabel }
                         .clickable { removing = name to stored }
                         .padding(10.dp))
            }
        }
    }
    if (model.day == null) {
        Text(
            stringResource(R.string.attach_files),
            color = palette.accentText,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 10.dp).clickable { picker.launch(arrayOf("*/*")) }.padding(vertical = 6.dp),
        )
    }
    removing?.let { (name, stored) ->
        Ask(
            title = stringResource(R.string.attach_remove_title),
            text = stringResource(R.string.attach_remove_text, name),
            confirm = stringResource(R.string.attach_remove_confirm),
            onConfirm = {
                removing = null
                state.detach(model.id, stored)
            },
            onDismiss = { removing = null },
            destructive = true,
        )
    }
    tooMany?.let { (uris, room) ->
        Ask(
            title = stringResource(R.string.attach_too_many_title),
            text = stringResource(R.string.attach_too_many_text, Normalize.MAX_ATTACHMENTS, room),
            confirm = stringResource(R.string.attach_first_n, room),
            onConfirm = {
                tooMany = null
                attach(uris.take(room))
            },
            onDismiss = { tooMany = null },
        )
    }
}

/**
 * A picked document copied into the app's cache, with the name to show for
 * it -- the provider's own, else the last part of its address. Null when it
 * cannot be read.
 */
private fun copyToCache(context: android.content.Context, uri: android.net.Uri): Pair<String, File>? {
    val name = try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    val temp = File(context.cacheDir, "attach_" + System.nanoTime())
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { input.copyTo(it, 1 shl 20) }
        } ?: return null
        name to temp
    } catch (e: Exception) {
        temp.delete()
        null
    }
}

/**
 * Everything the desktop keeps around its editor: category, date, colour,
 * pin -- in a sheet that rises from the bottom, so the page stays the
 * note's. Plus how long the note is, and what can be done with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsSheet(state: AppState, model: EditorModel, note: Note?, onDismiss: () -> Unit) {
    val palette = LocalPalette.current
    var picking by remember { mutableStateOf(false) }
    var dating by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    val onADay = model.day != null
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.note_details), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            DetailRow(stringResource(R.string.category)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { picking = true }) {
                    Dot(state.load.categories[model.category] ?: palette.hex.getValue("accent"))
                    Text("  ${model.category} ▾", color = palette.text, fontSize = 15.sp)
                }
            }
            DetailRow(stringResource(R.string.date)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Recurrence.showDate(model.date).ifBlank { stringResource(R.string.no_date) },
                        color = if (onADay) palette.subtext else palette.text,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(enabled = !onADay) { dating = true },
                    )
                    if (!onADay && model.date.isNotEmpty() && note?.isRecurring != true) {
                        Text("  ✕", color = palette.subtext, fontSize = 15.sp,
                             modifier = Modifier.clickable { model.date = ""; model.changed() })
                    }
                }
            }
            DetailRow(stringResource(R.string.repeat)) {
                Text(
                    note?.recurrenceLabel?.ifEmpty { null } ?: Recurrence.FREQ_LABELS.getValue(""),
                    color = if (onADay) palette.subtext else palette.text,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = !onADay) { repeating = true },
                )
            }
            if (onADay) {
                Text(stringResource(R.string.day_series_owned), color = palette.subtext, fontSize = 12.sp)
            } else if (note?.isRecurring == true) {
                Text(stringResource(R.string.repeats_note), color = palette.subtext, fontSize = 12.sp)
            }
            Text(stringResource(R.string.colour), color = palette.subtext, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Swatches(Themes.PRESET_NOTE_COLORS, model.color, allowNone = true) { model.color = it; model.changed() }
            // The pin is the whole note's: a day has nowhere to keep one.
            if (!onADay) {
                DetailRow(stringResource(R.string.pin_to_top)) {
                    Switch(checked = model.pinned, onCheckedChange = { model.pinned = it; model.changed() })
                }
            }
            val words = Editing.wordCount(model.body)
            Text(
                listOf(pluralWords(words), Editing.readingTime(words)).filter { it.isNotEmpty() }.joinToString(" · "),
                color = palette.subtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (picking) {
        CategorySheet(state.load.categories, model.category, onPick = {
            model.category = it
            model.changed()
            picking = false
        }, onDismiss = { picking = false })
    }
    if (dating) DateDialog(model.date, onPick = { model.date = it; model.changed(); dating = false }) { dating = false }
    if (repeating) RepeatSheet(state, model) { repeating = false }
}

private fun pluralWords(n: Int) = if (n == 1) "1 word" else "$n words"

@Composable
internal fun DetailRow(label: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.subtext, fontSize = 14.sp, modifier = Modifier.weight(1f))
        content()
    }
}

/**
 * The days off the schedule, one question each, in the desktop's words:
 * make the day a note of its own, move it to the Trash, or leave it. Each
 * day is looked at again when its turn comes -- an answer before it may
 * have changed the note -- and leaving one changes nothing. What was done
 * is said at the end.
 */
@Composable
private fun OffScheduleQuestions(state: AppState, id: Int, days: List<String>, onDone: () -> Unit) {
    val palette = LocalPalette.current
    var at by remember(days) { mutableIntStateOf(0) }
    var made by remember(days) { mutableIntStateOf(0) }
    var trashed by remember(days) { mutableIntStateOf(0) }

    fun finish() {
        val said = buildList {
            if (made > 0) add(if (made == 1) "1 made a note of its own" else "$made made notes of their own")
            if (trashed > 0) add("$trashed moved to the Trash")
        }
        if (said.isNotEmpty()) state.say("Days off the schedule: " + said.joinToString(", "))
        onDone()
    }

    val raw = state.rawNote(id)
    // The next day still keeping its edits off the schedule, from [at] on.
    val next = (at until days.size).firstOrNull { i ->
        raw != null && (raw.opt("overrides") as? org.json.JSONObject)?.has(days[i]) == true
    }
    LaunchedEffect(next) { if (next == null) finish() }
    if (next == null || raw == null) return
    val day = days[next]
    val shown = Recurrence.effectiveNote(raw, day)
    val title = (shown.opt("title") as? String)?.trim()?.ifEmpty { null } ?: "Untitled"
    val text = contentText(shown.opt("body") as? String ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ").let { if (it.length > 160) it.take(160).trimEnd() + "…" else it }
    val whenDay = Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day

    fun answer(how: Days.Settle?) {
        if (how != null && state.settleOffScheduleDay(id, day, how)) {
            if (how == Days.Settle.NOTE) made += 1 else trashed += 1
        }
        at = next + 1
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { answer(null) },
        title = { Text(stringResource(R.string.off_schedule_title, next + 1, days.size)) },
        text = {
            Text(stringResource(R.string.off_schedule_text, whenDay, title,
                                if (text.isEmpty()) "" else "\n$text", Recurrence.describe(raw), state.load.retentionDays))
        },
        confirmButton = {
            TextButton(onClick = { answer(Days.Settle.NOTE) }) {
                Text(stringResource(R.string.off_schedule_note), color = palette.accentText, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { answer(null) }) { Text(stringResource(R.string.off_schedule_leave)) }
                TextButton(onClick = { answer(Days.Settle.TRASH) }) {
                    Text(stringResource(R.string.off_schedule_trash), color = palette.dangerText)
                }
            }
        },
        containerColor = palette.card,
        titleContentColor = palette.text,
        textContentColor = palette.subtext,
    )
}

/** Android's own calendar picker, kept to the desktop's stored form. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val format = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    }
    val initial = if (Notebook.isDate(current)) format.parse(current.trim())?.time else null
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial ?: System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { onPick(format.format(Date(it))) } ?: onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = picker) }
}

/** What a cell holds, spelled the way the file spells it. */
private fun cellTextAt(table: TableBlock, at: CellAt): String {
    val cell = table.rows.getOrNull(at.row)?.getOrNull(at.column) ?: return ""
    return when (cell.checked) {
        // A checkbox is drawn as a box but edited as what it really is, so
        // it can be turned back into words by changing the marker.
        true -> "[x]"
        false -> "[ ]"
        null -> cell.lines.joinToString("<br>")
    }
}

/**
 * One cell, opened: its words, a checkbox, and the changes that only mean
 * something while a cell is chosen -- a row or a column beside this one,
 * or this one's taken away.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CellSheet(model: EditorModel, segment: Editing.Segment, at: CellAt, onDone: () -> Unit) {
    val palette = LocalPalette.current
    val lines = segment.text.split("\n")
    val table = remember(segment.text) { parseTable(lines) }
    var draft by remember(at) {
        val text = cellTextAt(table, at)
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val row = table.fileRow(at.row)
    val merged = remember(segment.text, at) { TableEdit(lines).isMerged(row, at.column) }
    // A day of a series keeps its text plain and its lines the series':
    // a cell's colour and the table's place belong to the whole note.
    val wholeNote = model.day == null
    var askDelete by remember { mutableStateOf(false) }
    fun change(edit: (TableEdit) -> TableEdit) {
        model.editTable(segment, edit)
        onDone()
    }
    // Ready to type into, as the desktop's cell editor is the moment it opens.
    val typing = remember { FocusRequester() }
    LaunchedEffect(at) { typing.requestFocus() }
    ModalBottomSheet(onDismissRequest = onDone, containerColor = palette.card) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.cell)) },
                modifier = Modifier.fillMaxWidth().focusRequester(typing),
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val next = if (checkboxState(draft.text) == null) "[ ]" else ""
                    draft = TextFieldValue(next, TextRange(next.length))
                }) {
                    Text(stringResource(R.string.cell_checkbox))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { change { it.setCell(row, at.column, draft.text) } },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.onAccent),
                ) { Text(stringResource(R.string.save)) }
            }

            // The desktop's cell menu, grouped as it groups it.
            SheetSection(stringResource(R.string.cell_align)) {
                for ((label, align) in listOf(R.string.align_left to CellAlign.LEFT, R.string.align_center to CellAlign.CENTER,
                                              R.string.align_right to CellAlign.RIGHT)) {
                    TextButton(onClick = { change { it.setAlignment(row, at.column, align) } }) { Text(stringResource(label)) }
                }
            }
            if (wholeNote) {
                SheetSection(stringResource(R.string.cell_highlight)) {
                    Text("✕", color = palette.subtext, fontSize = 14.sp, textAlign = TextAlign.Center,
                         modifier = Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(palette.inset)
                             .clickable { model.highlightCell(segment, row, at.column, ""); onDone() }.padding(top = 6.dp))
                    for ((i, colour) in Themes.HIGHLIGHT_COLOR_PALETTE.withIndex()) {
                        val said = stringResource(R.string.highlight_colour, i + 1)
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(hexColor(colour))
                                .semantics { contentDescription = said }
                                .clickable { model.highlightCell(segment, row, at.column, colour); onDone() })
                    }
                }
            }
            SheetSection(stringResource(R.string.cell_merge)) {
                TextButton(enabled = at.column > 0, onClick = { change { it.setMarker(row, at.column, "<") } }) {
                    Text(stringResource(R.string.merge_left))
                }
                TextButton(enabled = at.row > 0, onClick = { change { it.setMarker(row, at.column, "^") } }) {
                    Text(stringResource(R.string.merge_up))
                }
                TextButton(enabled = merged, onClick = { change { it.split(row, at.column) } }) {
                    Text(stringResource(R.string.split_cell))
                }
            }
            SheetSection(stringResource(R.string.rows_label)) {
                TextButton(onClick = { change { it.insertRowBefore(row) } }) { Text(stringResource(R.string.row_above)) }
                TextButton(onClick = { change { it.insertRowAfter(row) } }) { Text(stringResource(R.string.row_below)) }
                TextButton(onClick = { change { it.removeRow(row) } }) {
                    Text(stringResource(R.string.delete_row), color = palette.dangerText)
                }
            }
            SheetSection(stringResource(R.string.columns_label)) {
                TextButton(onClick = { change { it.insertColumnAfter(at.column - 1) } }) { Text(stringResource(R.string.column_left)) }
                TextButton(onClick = { change { it.insertColumnAfter(at.column) } }) { Text(stringResource(R.string.column_right)) }
                TextButton(onClick = { change { it.removeColumn(at.column) } }) {
                    Text(stringResource(R.string.delete_column), color = palette.dangerText)
                }
            }
            // The desktop sets a width by dragging a column's edge; a finger
            // gets steps instead, four characters each, the width written in
            // the separator as the desktop writes a dragged one.
            val widthNow = table.columns.getOrNull(at.column)?.width
                ?: minOf(table.charWidths.getOrElse(at.column) { 8 }, 26)
            fun columns(edit: (List<String>) -> List<String>) {
                model.editColumns(segment, edit)
                onDone()
            }
            SheetSection(stringResource(R.string.column_width)) {
                TextButton(onClick = { columns { setColumnWidth(it, at.column, widthNow + 4) } }) {
                    Text(stringResource(R.string.column_wider))
                }
                TextButton(enabled = widthNow > 4, onClick = { columns { setColumnWidth(it, at.column, widthNow - 4) } }) {
                    Text(stringResource(R.string.column_narrower))
                }
                TextButton(onClick = { columns { autofitColumn(it, at.column) } }) { Text(stringResource(R.string.column_autofit)) }
                TextButton(onClick = { columns { autofitAllColumns(it) } }) { Text(stringResource(R.string.column_autofit_all)) }
                TextButton(onClick = { columns { hideColumn(it, at.column) } }) { Text(stringResource(R.string.column_hide)) }
                TextButton(enabled = table.columns.any { it.hidden }, onClick = { columns { unhideAllColumns(it) } }) {
                    Text(stringResource(R.string.column_unhide_all))
                }
            }
            SheetSection(stringResource(R.string.table_label)) {
                if (wholeNote) {
                    for ((label, place) in listOf(R.string.table_left to "left", R.string.table_center to "center",
                                                  R.string.table_right to "right")) {
                        TextButton(onClick = { model.placeTable(segment, place); onDone() }) { Text(stringResource(label)) }
                    }
                }
                TextButton(onClick = { onDone(); model.addCaption(segment) }) { Text(stringResource(R.string.table_caption)) }
                TextButton(onClick = { onDone(); model.addLineAbove(segment) }) { Text(stringResource(R.string.table_line_above)) }
                TextButton(onClick = { askDelete = true }) {
                    Text(stringResource(R.string.delete_table), color = palette.dangerText)
                }
            }
        }
    }
    if (askDelete) {
        // Asked, though Undo would bring it back: a whole table is the most a
        // tap in a sheet can take away, and the arrow above is easy to miss.
        Ask(
            title = stringResource(R.string.delete_table),
            text = stringResource(R.string.delete_table_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                askDelete = false
                model.deleteTable(segment)
                onDone()
            },
            onDismiss = { askDelete = false },
            destructive = true,
        )
    }
}

/** A heading and a row of choices that wraps on a narrow screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Text(title, color = palette.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold,
         modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}
