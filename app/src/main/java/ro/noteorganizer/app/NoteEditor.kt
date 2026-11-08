package ro.noteorganizer.app

import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

    /** Whether this editor began as a new note -- still true after its first save, which a template asks after. */
    val startedNew: Boolean = note == null

    /**
     * Each line's indent and alignment, kept in step with the text as it
     * changes; a list item's kind is its marker, in the text itself. A day
     * of a series shows the series' while it shows the series' words, and
     * once it has text of its own, only its list lines' depth, which it
     * keeps in its own paragraphs ([Paragraphs.dayProps]).
     */
    var props by mutableStateOf(linesOf(note))

    private fun linesOf(note: Note?): Map<Int, Paragraphs.Props> {
        val raw = note?.let { state.rawNote(it.id) } ?: return emptyMap()
        val onDay = day ?: return Paragraphs.propsOf(raw.opt("paragraphs"), note.body)
        val shown = Recurrence.effectiveNote(raw, onDay).opt("body") as? String ?: return emptyMap()
        val own = (raw.opt("overrides") as? org.json.JSONObject)?.optJSONObject(onDay)?.opt("paragraphs")
        return Paragraphs.dayProps(raw.opt("paragraphs"), shown, Normalize.sameBody(shown, raw.opt("body") as? String ?: ""), own)
    }

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
        // A day's own text is plain -- but a day given only its own colour
        // or category keeps the series' words, and with them their styles,
        // as the desktop's load_note keeps them while the two bodies agree.
        if (own?.has("body") == true && !Normalize.sameBody(body, raw.opt("body") as? String ?: "")) spans = emptyList()
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
                            val props: Map<Int, Paragraphs.Props>, val comments: String) {
        /** What it holds, in characters: its words, and each style and line's entry as a short word's worth. */
        fun weight(): Long = title.length.toLong() + body.length + comments.length + 24L * (spans.size + props.size)
    }

    private fun snap() = Snap(title, body, spans, props, comments)

    /**
     * A step of Undo: the note as it was, and the edits that led from it to
     * the note after it, each in the text the one before left, so the places
     * remembered in the text ([caret], [calcMark]) go back and forth as Tk's
     * marks do (ISSUES_LOG, "Every place a change made"). Mutable: a typing
     * step joins letters typed after it, and its edits go from Undo to Redo.
     */
    private class Step(val words: Snap, val edits: MutableList<Paragraphs.Edit> = ArrayList())

    private val history = Editing.History<Step>(budget = Editing.HISTORY_CHARS, weigh = { it.words.weight() })

    /** The edits made since the note was last settled: what the next step records. */
    private val made = ArrayList<Paragraphs.Edit>()

    /** The step last kept, which typing joins. */
    private var newest: Step? = null

    /** How many steps Undo can take now: the tests' window on [history]'s budget. */
    @VisibleForTesting
    internal val undoSteps: Int get() = history.steps

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
        writeHeldCell()
        val now = Step(snap())
        val back = history.undo(now) ?: return
        // Redo goes forward through the same edits.
        now.edits.addAll(back.edits)
        restore(back.words, back.edits, forward = false)
    }

    fun redo() {
        writeHeldCell()
        val now = Step(snap())
        val ahead = history.redo(now) ?: return
        // ...and Undo back through them.
        now.edits.addAll(ahead.edits)
        restore(ahead.words, ahead.edits, forward = true)
    }

    private fun restore(to: Snap, through: List<Paragraphs.Edit>, forward: Boolean) {
        val was = body
        val kept = focus to focused
        title = to.title
        body = to.body
        spans = to.spans
        props = to.props
        // The cell being written in shows its words as they are now.
        cellEpoch += 1
        comments = to.comments
        // The places remembered in the text move through the step's own edits
        // ([Step]), as Tk's marks through Ctrl+Z: every change to the words keeps
        // its edit, so a step with none changed only styles, title or comments.
        focused = null
        caret = if (forward) Calculator.follow(through, caret) else Calculator.back(through, caret)
        calcMark = if (forward) Calculator.follow(through, calcMark) else Calculator.back(through, calcMark)
        made.clear()
        settled = to
        steps()
        edits += 1
        status = null
        // ...but the cursor stays in the note, as Tk leaves it, placed by the
        // rule typing uses ([placeCaret]): left with no place, the next letter
        // went to the note's end; by a copy of the rule, a Redo landed above a grid.
        val (index, value) = kept
        if (was == body) {
            // Only styles changed: the field asked back with its own value,
            // the place it writes at kept above.
            if (index != null && value != null) ask(index, value)
        } else {
            changeSite(was, body)?.let { at ->
                // A change in the table whose cell is being written in leaves the
                // cursor in the cell, its field reading the words again
                // ([cellEpoch]); by prose's rule the line under the grid took it.
                if (inActiveTable(at)) return@let
                caret = at
                placeCaret(at)
            }
        }
    }

    /** True when [at], a place in the body in code points, is in the table that holds the cell being written in. */
    private fun inActiveTable(at: Int): Boolean {
        val cell = activeCell ?: return false
        val table = Editing.segments(body).firstOrNull { it.table && it.firstLine == cell.firstLine } ?: return false
        return at >= table.cpStart && at <= table.cpStart + table.text.codePointCount(0, table.text.length)
    }

    /** Where [was] and [now] differ, as a place in [now], in code points: the end of what changed. */
    private fun changeSite(was: String, now: String): Int? =
        if (was == now) null else Editing.changeEndPoints(was, now)

    /** Which prose field has the cursor, and its value -- selection and all. */
    var focus by mutableStateOf<Int?>(null)
    var focused by mutableStateOf<TextFieldValue?>(null)

    /**
     * B, I, U or S pressed with nothing selected: what is typed next wears
     * them, until the key is pressed again -- the desktop's pending style.
     * Never in a day of a series, whose own text is plain.
     */
    var pending by mutableStateOf(emptySet<String>())

    /** A line at the foot of the screen, from the editor. */
    fun say(message: String) = state.say(message)

    /**
     * Where the cursor is in the words of the cell being written in, as its
     * field counts them, and where it is put when the cell is read again
     * after the calculator wrote into it.
     */
    var cellCaret: Int? = null
    var cellCaretNext: Int? = null

    /**
     * While a letter typed in a cell is written: the cursor in the cell's
     * words as they are stored (a line break counted as its "<br>"), so the
     * words' styles move by the edit as made ([carryCellStyles]).
     */
    var cellWordsCaret: Int? = null

    /**
     * A cell's words as its field gives them, the cursor at [caret]: written,
     * or held while they read as a merge's marker or a box ([isCellToken]: "<"
     * on its way to "<5 €"), which written would turn the cell under the
     * finger. Held words are written when the cell is left, as the desktop
     * does, and first in every table operation (ISSUES_LOG, "A mark held").
     */
    fun typeInCell(segment: Editing.Segment, table: TableBlock, at: CellAt, words: String, caret: Int) {
        val stored = words.replace("\n", LINE_BREAK)
        if (isCellToken(words)) {
            cellHeld = HeldCell(segment.firstLine, at, stored, body)
            return
        }
        cellHeld = null
        val upTo = words.substring(0, caret.coerceIn(0, words.length)).replace("\n", LINE_BREAK)
        cellWordsCaret = maxOf(0, upTo.codePointCount(0, upTo.length) - (stored.length - PyText.lstrip(stored).length))
        editTable(segment, Editing.History.TYPING + ":cell") { it.setCell(table.fileRow(at.row), at.column, stored) }
        cellWordsCaret = null
    }

    /** Words [typeInCell] held: the table by its first line, the cell, the words as stored, the body they were typed over. */
    class HeldCell(val firstLine: Int, val at: CellAt, val words: String, val over: String)

    var cellHeld: HeldCell? = null

    /** Writes [cellHeld], once -- not when the note has changed under it since. */
    fun writeHeldCell() {
        val held = cellHeld ?: return
        cellHeld = null
        if (body != held.over) return
        val segment = Editing.segments(body).firstOrNull { it.table && it.firstLine == held.firstLine } ?: return
        val table = parseTable(segment.text.split("\n"))
        editTable(segment, Editing.History.TYPING + ":cell") { it.setCell(table.fileRow(held.at.row), held.at.column, held.words) }
    }

    /**
     * The table cell being written in, by the line its table starts on --
     * a table's segment is made anew with every edit, its first line is
     * not -- and the cell. Null when no cell is: a prose field taking the
     * cursor lets go of it, and so does Back.
     */
    var activeCell by mutableStateOf<ActiveCell?>(null)

    /**
     * Counts the times the cell being written in changed under its field:
     * Undo, Redo, a row put over it, a cell chosen. The field keeps its own
     * copy of the words as they are typed, and kept across these, it showed
     * the cell's old words -- after Undo, and after a row made above, where
     * the next word typed went in after the old cell's words and wrote them
     * into the new row. Typing does not count: the field is the one writing.
     */
    var cellEpoch by mutableIntStateOf(0)

    /**
     * Where the cursor last was in the body, in code points -- kept when
     * the field lets go of it, as the desktop's insert mark is, so the
     * calculator writes where the person was writing.
     */
    var caret: Int? = null

    /** The end of the calculator's last insertion: the next goes after it. */
    private var calcMark: Int? = null

    /**
     * The body has changed in one place: the places remembered in it move
     * with the text, and the lines' properties too -- through [edit], the
     * change as it was made, when the one making it knows its place: read
     * off the two texts alone ([Calculator.changeOf]), a change that begins
     * with what follows it is read as made further on, and a place right at
     * it stayed behind.
     */
    private fun follow(next: String, edit: Paragraphs.Edit? = null) {
        carry(listOfNotNull(edit ?: Calculator.changeOf(body, next)))
        props = Paragraphs.follow(body, next, props, edit)
    }

    /**
     * The body changes by [edits], each in the text the one before left: the
     * places remembered in it carried through them one by one, as a Tk mark
     * of right gravity moves, and the edits kept for the step Undo records.
     */
    private fun carry(edits: List<Paragraphs.Edit>) {
        caret = Calculator.follow(edits, caret)
        calcMark = Calculator.follow(edits, calcMark)
        made.addAll(edits)
    }

    /**
     * A list, indent or alignment change, the styles carried through it
     * exactly -- and the places remembered in the text with them: followed
     * as one change, a list's markers put on three lines, or Enter in a
     * numbered list above the calculator's last answer, sent its next one
     * into a later line ("3×3=9Rent", or into "4.").
     */
    private fun apply(done: Paragraphs.Done) {
        spans = if (day != null) emptyList() else done.move(spans)
        carry(done.inTurn())
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
        val start = done.movePoint(from)
        val end = done.movePoint(to)
        caret = end
        // Markers put on make lines longer, and a full field gives its last
        // line to the next: then the rule for a cursor whose line moved.
        val segment = Editing.segments(body).getOrNull(index)
        if (segment != null && holds(segment, start) && holds(segment, end)) focused = fieldValue(index, start, end)
        else placeCaret(end, start)
    }

    /** The format bar's alignment keys. */
    fun alignLines(first: Int, last: Int, align: String) {
        var next = Paragraphs.setAlign(body, props, first, last, align)
        // A caption carries its table along, as the desktop's
        // `_set_paragraph_align` has it: centred alone, the label stood
        // over a table left where it was.
        val lines = body.split("\n")
        for (block in tableBlocks(body)) {
            val caption = Editing.captionLines(lines, block.last)
            if (caption.none { it in first..last }) continue
            next = withAlign(next, block + caption, next[caption.first()]?.align ?: "left")
        }
        props = next
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
     * After a letter typed into the field at [index], shown as [shown]: when
     * the body is now cut otherwise -- a field past its share of lines
     * ([Editing.FIELD_LINES]) gives its last line to the next -- the cursor
     * goes to the field its line went to. Left where it was, the field
     * showed its new text with the cursor at its end, and the next letter
     * went there.
     */
    fun keepCaret(index: Int, shown: TextFieldValue) {
        val at = caret ?: return
        val segments = Editing.segments(body)
        if (segments.getOrNull(index)?.let { it.text == shown.text && holds(it, at) } == true) return
        placeCaret(at)
    }

    private fun holds(s: Editing.Segment, at: Int) =
        s.field && at >= s.cpStart && at <= s.cpStart + s.text.codePointCount(0, s.text.length)

    /**
     * Counts the times the keyboard was let go of because the line being
     * written is in no field ([placeCaret]): the screen takes it away.
     */
    var letGo by mutableIntStateOf(0)
        private set

    /** Whether the reason the keyboard went was said: once a visit. */
    private var letGoSaid = false

    /**
     * The cursor put at [at], in code points, after a change: in the field
     * that holds it, or past a table's closing pipe or a read-only piece, at
     * the next field's start; with none after it, the keyboard goes, said
     * once. The one rule for where the cursor goes after any change: each
     * asked a copy of it, and the next letters went onto the line above, or
     * out of sight. A selection from [from] to [at] is kept within one field.
     */
    private fun placeCaret(at: Int, from: Int = at) {
        val segments = Editing.segments(body)
        val within = segments.indexOfFirst { holds(it, at) }
        if (within >= 0) {
            ask(within, fieldValue(within, if (holds(segments[within], from)) from else at, at))
            return
        }
        val piece = segments.indexOfLast { at >= it.cpStart }
        val next = (piece + 1 until segments.size).firstOrNull { segments[it].field }
        if (next != null) {
            caret = segments[next].cpStart
            ask(next, fieldValue(next, segments[next].cpStart))
            return
        }
        focused = null
        focus = null
        letGo += 1
        if (!letGoSaid) {
            letGoSaid = true
            state.say("The end of this note is more than the phone shows at once. It is kept whole, shown " +
                "read-only here; write there on the computer.")
        }
    }

    /**
     * The desktop's "Add caption below…": a numbered label line and a source
     * line under the table, placed as the table is, no style carried onto
     * them from its edge -- and "description" selected, so what is typed
     * replaces it.
     */
    fun addCaption(segment: Editing.Segment) {
        writeHeldCell()
        val made = Editing.addCaption(body, segment.lastLine)
        val tableAlign = props[segment.firstLine]?.align
        val lines = made.body.split("\n")
        val from = Paragraphs.lineStart(lines, made.line)
        // The Source line's own line break too, as the desktop's clears to
        // "{cap_line + 2}.0": a style past the table's end kept it, and its bar
        // ran to the margin there.
        val to = minOf(from + lines[made.line].codePointCount(0, lines[made.line].length) + 1 +
            lines[made.line + 1].codePointCount(0, lines[made.line + 1].length) + 1,
            made.body.codePointCount(0, made.body.length))
        // Put in at the end of the table's last line, as the desktop's
        // insert at "{below}.end": the edit as made, not read off the texts --
        // the styles too, or a run starting where it went in took it all.
        val edit = putIn(made.body, from - 1)
        spans = if (day != null) emptyList() else Paragraphs.moveSpans(spans, listOf(edit)).let { moved ->
            moved.map { it.style }.toSet().fold(moved) { acc, style -> clearStyle(acc, from, to, style) }
        }
        follow(made.body, edit)
        body = made.body
        if (day == null) props = withAlign(props, listOf(made.line, made.line + 1), tableAlign)
        changed()
        caret = made.selectEnd
        placeCaret(made.selectEnd, made.selectStart)
    }

    /** "Add a line above the table": an empty line there, the cursor on it. */
    fun addLineAbove(segment: Editing.Segment) {
        writeHeldCell()
        val lines = body.split("\n")
        val at = Paragraphs.lineStart(lines, segment.firstLine)
        val next = (lines.take(segment.firstLine) + "" + lines.drop(segment.firstLine)).joinToString("\n")
        // One line break at the table's first line's start, as the desktop's insert at "{first}.0".
        val edit = putIn(next, at)
        spans = if (day != null) emptyList() else Paragraphs.moveSpans(spans, listOf(edit))
        follow(next, edit)
        body = next
        changed()
        caret = at
        placeCaret(at)
    }

    /** What putting text in at [at], in code points, and nothing else made of the body [next]: the edit as made. */
    private fun putIn(next: String, at: Int): Paragraphs.Edit =
        Paragraphs.Edit(at, 0, next.codePointCount(0, next.length) - body.codePointCount(0, body.length))

    /**
     * A column's width, hidden mark or fit: the separator line alone
     * rewritten, or one put under the header row, placed as the table is.
     */
    fun editColumns(segment: Editing.Segment, change: (List<String>) -> List<String>) {
        writeHeldCell()
        val lines = body.split("\n")
        val table = lines.subList(segment.firstLine, segment.lastLine + 1)
        val changedTable = change(table)
        if (changedTable == table) return
        val next = replaceTable(body, segment.firstLine..segment.lastLine, changedTable)
        // The separator line alone put in or rewritten ([withColumnMechanics]),
        // the edit as made: read off the texts, it was found inside the next row,
        // and that row's first cell's colour spread over the separator.
        fun width(line: String) = line.codePointCount(0, line.length)
        fun startOf(line: Int) =
            if (line < lines.size) Paragraphs.lineStart(lines, line) else width(body)
        val edit = if (changedTable.size == table.size + 1) {
            Paragraphs.Edit(startOf(segment.firstLine + 1), 0, width(changedTable[1]) + 1)
        } else {
            table.indices.singleOrNull { table[it] != changedTable[it] }
                ?.let { Paragraphs.Edit(startOf(segment.firstLine + it), width(table[it]), width(changedTable[it])) }
        }
        spans = if (day != null) emptyList()
                else edit?.let { Paragraphs.moveSpans(spans, listOf(it)) } ?: remapSpans(body, next, spans).spans
        follow(next, edit)
        body = next
        if (changedTable.size > table.size && day == null) {
            props = withAlign(props, listOf(segment.firstLine + 1), props[segment.firstLine]?.align)
        }
        changed()
    }

    /** The table taken out of the note. */
    fun deleteTable(segment: Editing.Segment) {
        writeHeldCell()
        val next = Editing.deleteLines(body, segment.firstLine, segment.lastLine)
        // Taken out from its first line's start: the edit as made, the styles too.
        val at = Paragraphs.lineStart(body.split("\n"), segment.firstLine)
        val edit = Paragraphs.Edit(at, body.codePointCount(0, body.length) - next.codePointCount(0, next.length), 0)
        spans = if (day != null) emptyList() else Paragraphs.moveSpans(spans, listOf(edit))
        follow(next, edit)
        body = next
        focused = null
        changed()
    }

    /**
     * A blank table at the cursor -- the end, when it was never in the note.
     * The line it starts on; null when it would not be drawn as a table
     * there -- past what the note draws, the cursor never in it or left in
     * that part when the keyboard went -- and nothing is written, said. As
     * the calculator's sum, it went where nothing showed it, one more blank
     * table at every tap.
     */
    fun insertTable(rows: Int, columns: Int): Int? {
        writeHeldCell()
        val length = body.codePointCount(0, body.length)
        val made = Editing.insertTable(body, caret ?: length, rows, columns)
        if (Editing.segments(made.body).none { it.table && it.firstLine == made.firstLine }) {
            state.say("The end of this note is more than the phone shows at once. Tap in its text where the " +
                "table should go, then insert it.")
            return null
        }
        // Put in at the cursor (or the end, on a line of its own,
        // [Editing.insertTable]): the edit as made, or a run starting at the
        // cursor took the whole table.
        val edit = putIn(made.body, (caret ?: length).coerceIn(0, length))
        spans = if (day != null) emptyList() else Paragraphs.moveSpans(spans, listOf(edit))
        follow(made.body, edit)
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
            val step = Step(before, ArrayList(made))
            // Joined to the step before, its edits follow that step's.
            if (history.record(step, kind, System.currentTimeMillis())) newest = step
            else newest?.edits?.addAll(made)
            steps()
        }
        made.clear()
        settled = now
        edits += 1
        status = null
    }

    val unsaved: Boolean get() = edits != saved

    /**
     * Nothing written in this note: no title, words, comments or checklist --
     * one answer for Share and Move to Trash. Move to Trash asked of the title
     * and words alone, and a new note of comments closed with no save.
     */
    val blank: Boolean get() = title.isBlank() && body.isBlank() && comments.isBlank() && adding.isBlank() &&
        ((state.rawNote(id)?.opt("checklist") as? org.json.JSONArray)?.length() ?: 0) == 0

    /**
     * Writes what is on screen. True when the file now holds it. A failure
     * is said as well as drawn in red, which a screen reader never reads
     * out: every time when a tap asked for the save -- it is why the tap did
     * nothing -- and by the [auto]save once, when saving starts to fail.
     * [scheduleFollows]: the Repeat sheet's save, a write of the repeat
     * after it ([Notebook.updateNote]).
     */
    fun save(force: Boolean = false, auto: Boolean = false, scheduleFollows: Boolean = false): Boolean {
        val first = problem == null
        // The checklist's waiting words go with a save a tap asks for, never
        // with the autosave: it runs while a row or "A new item…" is being
        // typed in, and would add the half-typed words as a row.
        val why = (if (auto) writeNote(force) else write(force, scheduleFollows)) ?: return true
        if (!auto || first) state.say("Not saved: $why")
        return false
    }

    /**
     * Writes what is on screen, the checklist's waiting words too, saying
     * nothing: null when the file now holds it, or why it does not. What the
     * app asks before the note is left or changed from outside
     * (AppState.flush), which says it in its own words.
     */
    fun write(force: Boolean = false, scheduleFollows: Boolean = false): String? {
        writeHeldCell()
        return writeNote(force, scheduleFollows) ?: writeChecklist()
    }

    /**
     * [write] for a save the editor stays open after -- a change from the
     * list beside it, an Undo, an import -- leaving "A new item…" as it is:
     * its words went in as a finished row, the box emptied under the thumb
     *. A read-back ([reloadFrom]) keeps the box.
     */
    fun writeStaying(): String? {
        writeHeldCell()
        return writeNote(false) ?: writeRename()
    }

    /**
     * [save] for a tap that asks something first -- reset or delete a day,
     * the days off the schedule -- leaving "A new item…" in its box: the
     * change asked about writes it if it closes the editor, and a Cancel left
     * the item finished as a row.
     */
    fun saveStaying(): Boolean {
        val why = writeStaying() ?: return true
        state.say("Not saved: $why")
        return false
    }

    /** The box's words a trip to the background wrote as a row: the note, the row, the words. */
    private var awayRow: Triple<Int, Int, String>? = null

    /**
     * [write], as the app goes to the background: the words in "A new
     * item…" go in as a row, kept if Android ends the app -- and given back
     * to the box when the person returns ([backAgain]). Left as a row, a
     * half-typed item came back finished and the box empty.
     */
    fun writeAway(): String? {
        val words = adding
        val why = write()
        val rows = if (why == null && words.isNotBlank() && adding.isEmpty()) {
            state.rawNote(id)?.optJSONArray("checklist")
        } else null
        awayRow = if (rows != null && rows.length() > 0) Triple(id, rows.length() - 1, words) else null
        return why
    }

    /**
     * Back in front: the row [writeAway] made, if it is still the last and as
     * written, back in the box -- of the note still open only. Text shared in
     * on the way back opens a note of its own first, and the words taken out
     * of the row went into an editor about to close, kept nowhere.
     */
    fun backAgain() {
        val (noteId, index, words) = awayRow ?: return
        awayRow = null
        if (noteId != id || state.openId != id || adding.isNotEmpty()) return
        val rows = state.rawNote(id)?.optJSONArray("checklist") ?: return
        if (rows.length() != index + 1 || rows.optJSONObject(index)?.optString("text") != words.trim()) return
        if (state.report(Notebook.removeChecklistItem(state.load, id, index))) adding = words
    }

    /** A checklist row being renamed: the row at [index] read [was] when the change began. */
    data class Renaming(val index: Int, val was: String, val now: String)

    /**
     * The checklist's words not written yet: a row being renamed -- written
     * when its field is left, or by the keyboard's ✓ -- and the words in "A
     * new item…", added by Add or ✓. Held here, not in the fields, so the
     * flush writes them too: going to the background moves no focus, and a
     * row renamed, then the app swiped away from Recents, lost its new words
     * (ISSUES_LOG, "Words a field kept until it was left").
     */
    var renaming by mutableStateOf<Renaming?>(null)
    var adding by mutableStateOf("")

    /** The top of "A new item…" with its Add, in the window, while it is typed in; null otherwise ([AppState.editorFootTop]). */
    var addBoxTop by mutableStateOf<Float?>(null)

    /**
     * The checklist row being renamed -- its key, and its top in the window
     * -- for the window's messages to stand above it, its ⠿, ☐ and ✕ with it
     * ([AppState.editorFootTop]); null while none is. Counted not, the row
     * was brought just above the keyboard and a message stood over it,
     * taking the taps meant for its ✕ -- or its own Undo's.
     */
    var renamingRow by mutableStateOf<Pair<Any, Float>?>(null)

    /**
     * A row's field and "A new item…" kept to a row's length, as the
     * desktop keeps its box ([Editing.fitRow]): the row's own words whole,
     * only what came in cut, said once while edits in one box meet the cap.
     */
    val rowCap = Editing.BoxCap({ state.say(it) })

    /** The title kept to the desktop's 200 letters the same way, said in its title box's words. */
    val titleCap = Editing.BoxCap({ state.say(it) }, Editing.TITLE_CHARS, "A note title",
                                  "Pasted title shortened to ${Editing.TITLE_CHARS} characters")

    /**
     * [now] typed into "A new item…". A full list takes no more, and said
     * only when Add or leaving the note tried, words waiting there were lost
     * unsaid if Android ended the app behind Home -- the save on going to
     * the background has no one to tell:
     * the cap is said as the words begin, while the person is there to read it.
     */
    fun typeAdding(now: String) {
        val began = adding.isBlank() && now.isNotBlank()
        adding = now
        if (began && (state.note(id)?.checklist?.size ?: 0) >= Notebook.MAX_CHECKLIST_ITEMS) state.say(Notebook.CHECKLIST_FULL)
    }

    /**
     * The title's box, [field] as it was last left: the title on one line
     * from the start, a break it holds shown as the space it becomes. Shown
     * with the break, the first letter typed rewrote the box under the
     * keyboard, and letters arriving in that moment were lost -- " list"
     * came back " li" (the walk). The note
     * keeps its break until something is typed in.
     */
    fun titleBox(field: TextFieldValue): TextFieldValue {
        val line = Editing.oneLine(title)
        return if (field.text == line) field else TextFieldValue(line, TextRange(line.length))
    }

    /**
     * [next] typed into the title, shown as [shown]: one line, as the
     * desktop's, a paste's breaks made spaces ([Editing.titleTyped]), and kept
     * to 200 letters by [titleCap], only what came in weighed. The value to
     * show, its cursor after what came in. A tap, moving only the cursor,
     * writes nothing: a title holding a break was rewritten by a tap alone.
     */
    fun typeTitle(shown: TextFieldValue, next: TextFieldValue): TextFieldValue {
        if (next.text == shown.text) return next
        val (text, at) = Editing.titleTyped(shown.text, next.text, next.selection.end.takeIf { next.selection.collapsed })
        val typed = if (at == null) next.copy(text = text) else TextFieldValue(text, TextRange(at))
        val base = shown.copy(text = Editing.oneLine(shown.text))
        val kept = keptBy(titleCap, base, typed)
        // Refused at the cap: the title as it was, breaks and all.
        if (kept.text == base.text && typed.text != base.text) return shown
        if (kept.text != title) {
            title = kept.text
            changed(Editing.History.TYPING + ":title")
        }
        return kept
    }

    /**
     * Rows renamed whose words could not be written when another row was
     * typed in -- the phone refusing the file: held, in the order typed, and
     * written first by the next write. Dropped, the second row's letters
     * vanished one by one as they were typed.
     */
    val unwritten = androidx.compose.runtime.mutableStateListOf<Renaming>()

    /** The words row [index] shows while they wait: being typed, or held by a failed write. */
    fun waitingIn(index: Int, text: String): String? =
        (renaming?.takeIf { it.index == index } ?: unwritten.lastOrNull { it.index == index })
            ?.takeIf { it.was == text }?.now

    /** The failure a row's write last met, said once until a write succeeds. */
    private var renameFailure: String? = null

    /**
     * [now] typed into row [index]'s words: they wait, to be written when the
     * field is left, another row's first. Read from the editor as it is, not
     * the last drawing (ISSUES_LOG, "A row's second letter"). Null, or why the
     * other row's words could not be written, said once as the autosave says
     * its failure: tried at every letter, each queued a bar of its own.
     */
    fun typeInRow(index: Int, now: String): String? {
        val pending = renaming
        var why: String? = null
        if (pending != null && pending.index != index) {
            why = writeRename()
            if (why != null) {
                // Held for the next write; the words typed here wait beside them.
                renaming?.let { unwritten.add(it) }
                renaming = null
                if (why == renameFailure) why = null else renameFailure = why
            }
        }
        val held = unwritten.indexOfLast { it.index == index }
        val was = if (renaming?.index == index) renaming!!.was
                  else if (held >= 0) unwritten.removeAt(held).was
                  else state.note(id)?.checklist?.getOrNull(index)?.text ?: return why
        renaming = Renaming(index, was, now)
        return why
    }

    /** The rows being renamed, written -- those held first: null, or why one could not be. */
    fun writeRename(): String? {
        while (true) {
            val r = unwritten.firstOrNull() ?: renaming ?: break
            // Only onto the row that still reads what it read: every change from
            // here writes this first, so a list changed under it came from
            // elsewhere, and the words are dropped, never put on another row.
            val row = state.note(id)?.checklist?.getOrNull(r.index)
            if (row != null && row.text == r.was && r.now != r.was) {
                val result = Notebook.renameChecklistItem(state.load, id, r.index, r.now)
                state.refresh()
                if (result is SaveResult.Failed) return result.reason
            }
            if (unwritten.isNotEmpty()) unwritten.removeAt(0) else renaming = null
        }
        renameFailure = null
        return null
    }

    /** The row being renamed, then the words in "A new item…" added: null, or why not. */
    fun writeChecklist(): String? {
        writeRename()?.let { return it }
        if (adding.isBlank()) return null
        // Only a saved note holds a checklist.
        if (id == 0) writeNote(force = true)?.let { return it }
        val result = Notebook.addChecklistItem(state.load, id, adding.trim())
        state.refresh()
        if (result is SaveResult.Failed) return result.reason
        adding = ""
        return null
    }

    private fun writeNote(force: Boolean, scheduleFollows: Boolean = false): String? {
        if (!force && edits == saved) return null
        val target = edits
        val load = state.load
        var follows = false
        val result = if (day != null && id != 0) {
            // A day of a series saves to that day alone, never to the series.
            Days.saveDay(load, id, day, title, body, comments, category, color, Paragraphs.extract(body, props)) {
                follows = it == Days.DaySaved.FOLLOWS
            }
        } else if (id == 0) {
            if (!force && title.isBlank() && body.isBlank() && comments.isBlank()) {
                saved = target
                return null
            }
            Notebook.createNote(load, title, body, category, color, date, pinned, spans,
                                paragraphs = Paragraphs.extract(body, props), comments = comments)
        } else {
            Notebook.updateNote(load, id, title, body, category, color, date, pinned,
                                formatting = spans, comments = comments,
                                paragraphs = Paragraphs.extract(body, props), scheduleFollows = scheduleFollows)
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
                // The words taken beside an Update's copy, a note of their own:
                // said with the way to it, and said by the list where it hides
                // them (the desktop's `_keep_text_aside`).
                state.load.notes.firstOrNull { result.keptAside != 0 && it.id == result.keptAside }?.let { made ->
                    val what = if (result.keptComments) "The comments this cleared" else "The words this took away"
                    state.offerAbout(listOf(made.id), "$what are kept as a note of their own: ${quoted(made.title)}",
                                     "Show") { state.openNote(made.id) }
                    state.waitWord(listOf(made.id), "kept as a note of its own", named = true)
                }
                // The bar that offered its Show goes with the note: left up, it
                // waited for a note just gone.
                state.asidesSpent(result.spentAside)
                null
            }
            is SaveResult.Failed -> {
                problem = result.reason
                result.reason
            }
        }
    }

    /** The note on disk again, after something other than typing changed it. */
    fun reloadFrom(note: Note) {
        // Open on a day the series no longer draws: the whole note is
        // opened in this editor's place ([AppState.leaveDayIfNotDrawn]).
        if (day != null && state.leaveDayIfNotDrawn(note.id)) return
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
        activeCell = null
        // A body brought back whole: nothing remembered in the old one applies
        // -- the steps of Undo included, which would put the old one back.
        caret = null
        calcMark = null
        // As the desktop clears its pending styles when a note is loaded:
        // a stale one would style the next word typed in the new body.
        pending = emptySet()
        history.clear()
        made.clear()
        newest = null
        settled = snap()
        steps()
        saved = edits
    }

    /**
     * One prose field's new text, the body rebuilt around it. In a list a key
     * can mean more than its character (Enter continues the list, Backspace
     * at a marker takes it away), and the field is given back rewritten: the
     * value to show; null when the text stands as typed. A CR LF pasted in is
     * the one line break the desktop's paste puts in ([Editing.breaksCame]).
     */
    fun typeInto(segment: Editing.Segment, given: TextFieldValue, index: Int): TextFieldValue? {
        val broken = Editing.breaksCame(segment.text, given.text, given.selection.end.takeIf { given.selection.collapsed })
        val typed = if (broken == null) given else TextFieldValue(broken.first, TextRange(broken.second))
        val next = Editing.replaceProse(body, segment, typed.text)
        if (next == body) return if (broken == null) null else typed
        // The list rules on a day too, as the desktop's day has them: a day keeps
        // its lists (ISSUES_LOG, "A day's lists, read by line"), and without them
        // Enter then Backspace at an item's start lost its depth.
        Paragraphs.afterTyping(body, next, props, caretIn(segment, typed))?.let { done ->
            apply(done)
            if (day != null) props = Paragraphs.listDepths(props, body)
            val at = done.caret ?: return null
            caret = at
            return fieldValue(index, at)
        }
        // A day's own text is plain: once typed in, it carries no styling.
        // Otherwise the letters take styles as the desktop's text box gives
        // them, found from the caret ([spansAfterTyping]).
        spans = if (day != null) emptyList() else spansAfterTyping(body, next, caretIn(segment, typed), spans, pending)
        // The places and the lines' looks carried through the edit read at
        // the cursor, as the list's path reads Enter ([Editing.proseEdit]).
        follow(next, Editing.proseEdit(segment, typed.text, cursorOf(typed)))
        body = next
        // ...and of the series' lines' looks, its lists' depth alone, as the
        // day reads back once saved ([Paragraphs.dayProps]).
        if (day != null) props = Paragraphs.listDepths(props, next)
        changed(Editing.History.TYPING)
        return if (broken == null) null else typed
    }

    /**
     * One table changed through the table rules, the body rebuilt around it.
     * [kind] is the step's name for Undo: words typed in a cell join one
     * step, as typing in the note's text does.
     */
    fun editTable(segment: Editing.Segment, kind: String = "edit", change: (TableEdit) -> TableEdit) {
        writeHeldCell()
        if (applyTable(segment, change(TableEdit(tableLines(segment))))) changed(kind)
    }

    private fun tableLines(segment: Editing.Segment): List<String> =
        body.split("\n").subList(segment.firstLine, segment.lastLine + 1)

    /** One table edit written into the body, its styles carried ([carryCellStyles]). True when anything changed. */
    private fun applyTable(segment: Editing.Segment, edit: TableEdit): Boolean {
        val written = edit.lines()
        val next = replaceTable(body, segment.firstLine..segment.lastLine, written)
        if (next == body) return false
        spans = if (day != null) emptyList() else carryCellStyles(body, next, segment, written.size, edit.origins())
        follow(next)
        body = next
        return true
    }

    /**
     * A table's styles carried through an edit cell by cell, as the desktop's
     * `_grid_read_cell_styles` and `_grid_lay_cell_styles` carry them: a cell's
     * words' styles go with the unchanged words, a highlight pipe to pipe with
     * its cell. By the remap alone, a re-padding edit dropped every style in
     * between. Changed cells, and styles outside or across cells, are remapped.
     */
    private fun carryCellStyles(old: String, new: String, segment: Editing.Segment, count: Int,
                                origins: List<List<Pair<Int, Int>?>>): List<FormatSpan> {
        val before = cellPlaces(old, segment.firstLine..segment.lastLine)
        val after = cellPlaces(new, segment.firstLine until segment.firstLine + count)
        val oldPoints = codePointsOf(old)
        val newPoints = codePointsOf(new)
        fun words(points: IntArray, p: CellPlace) = String(points, p.wordsStart, p.wordsEnd - p.wordsStart)
        // Each old cell's new place, when the edit kept it, and whether its words are the same.
        val goesTo = HashMap<Pair<Int, Int>, Pair<CellPlace, Boolean>>()
        for ((key, p) in after) {
            val origin = origins.getOrNull(key.first)?.getOrNull(key.second) ?: continue
            val was = before[origin] ?: continue
            if (origin !in goesTo) goesTo[origin] = p to (words(oldPoints, was) == words(newPoints, p))
        }
        // The one cell a typed letter changed: its styles move by the edit as
        // made, as the desktop's cell editor styles a typed letter; read off the
        // texts, a padded or aligned cell's styles were cut or dropped.
        val typedIn = goesTo.entries.singleOrNull { !it.value.second }
        val typedWords = typedIn?.let { words(oldPoints, before.getValue(it.key)) }
        val typed = cellWordsCaret?.let { caret ->
            typedIn?.let { cellEdit(typedWords!!, words(newPoints, it.value.first), caret) } }
        val afterBreak = typed != null && typed.at >= LINE_BREAK.length &&
            String(codePointsOf(typedWords!!), typed.at - LINE_BREAK.length, LINE_BREAK.length) == LINE_BREAK
        val carried = ArrayList<FormatSpan>()
        val rest = ArrayList<FormatSpan>()
        // Only a style inside the table is looked for among its cells: this
        // runs on every letter typed in a cell, and a long note's other
        // styles need not be.
        val low = before.values.minOfOrNull { it.start } ?: 0
        val high = before.values.maxOfOrNull { it.end } ?: 0
        for (span in spans) {
            if (span.end <= low || span.start >= high) {
                rest.add(span)
                continue
            }
            var placed = false
            for ((origin, target) in goesTo) {
                val was = before.getValue(origin)
                val (now, same) = target
                if (span.start == was.start && span.end == was.end) {
                    carried.add(span.copy(start = now.start, end = now.end))
                    placed = true
                } else if (same && span.end > span.start && span.start >= was.wordsStart && span.end <= was.wordsEnd) {
                    val shift = now.wordsStart - was.wordsStart
                    carried.add(span.copy(start = span.start + shift, end = span.end + shift))
                    placed = true
                } else if (typed != null && origin == typedIn?.key && span.end > span.start &&
                           span.start >= was.wordsStart && span.end <= was.wordsEnd) {
                    cellSpanThrough(span.start - was.wordsStart, span.end - was.wordsStart, typed, afterBreak)
                        ?.let { (s, e) -> carried.add(span.copy(start = now.wordsStart + s, end = now.wordsStart + e)) }
                    placed = true
                }
                if (placed) break
            }
            if (!placed) rest.add(span)
        }
        return (remapSpans(old, new, rest).spans + carried).sortedWith(compareBy({ it.start }, { it.end }))
    }

    /** A cell's words [old] become [new] with the cursor at [caret]: the edit as made, or null when what follows the cursor is not the old words' end. */
    private fun cellEdit(old: String, new: String, caret: Int): Paragraphs.Edit? {
        val a = codePointsOf(old)
        val b = codePointsOf(new)
        val c = caret.coerceIn(0, b.size)
        val tail = b.size - c
        if (tail > a.size) return null
        for (i in 0 until tail) if (a[a.size - tail + i] != b[c + i]) return null
        var at = 0
        val limit = minOf(c, a.size - tail)
        while (at < limit && a[at] == b[at]) at++
        return Paragraphs.Edit(at, a.size - tail - at, c - at)
    }

    /**
     * A style on a cell's words, [s] to [e], through [edit]: the letters put
     * in take the style of the letter before them -- of the one ahead at the
     * words' start, and none after a line break -- the desktop's
     * `_editor_typing_style`. Null when nothing of it is left.
     */
    private fun cellSpanThrough(s: Int, e: Int, edit: Paragraphs.Edit, afterBreak: Boolean): Pair<Int, Int>? {
        val at = edit.at
        val k = edit.inserted
        val s1 = if (s <= at) s else maxOf(at, s - edit.removed)
        val e1 = if (e <= at) e else maxOf(at, e - edit.removed)
        val takes = k > 0 && !afterBreak && (if (at > 0) s1 <= at - 1 && at - 1 < e1 else s1 == 0 && e1 > 0)
        val s2 = if (s1 > at || (s1 == at && !takes)) s1 + k else s1
        val e2 = if (e1 > at || (e1 == at && takes)) e1 + k else e1
        return if (e2 > s2) s2 to e2 else null
    }

    /**
     * A cell merged with its neighbour, on its left or above, the desktop's
     * way ([mergePlan]): every word kept, joined in the cell kept, each
     * piece's styles carried with it. One cell is written at a time, so each
     * remap of the styles sees one small change; all of it one step for Undo.
     * The cell kept, or null when there was nothing to merge with.
     */
    fun mergeCells(segment: Editing.Segment, at: CellAt, up: Boolean): CellAt? {
        writeHeldCell()
        val lines = body.split("\n").subList(segment.firstLine, segment.lastLine + 1)
        val plan = mergePlan(lines, at, up) ?: return null
        val table = parseTable(lines)

        // Where a cell's words start, in code points of [text], and how long they are.
        fun words(text: String, cell: CellAt): Pair<Int, Int>? {
            val line = segment.firstLine + table.fileRow(cell.row)
            val (from, to) = Exporting.cellRange(text, line, cell.column) ?: return null
            val points = codePointsOf(text)
            var start = from
            while (start < to && points[start] == ' '.code) start++
            var end = to
            while (end > start && points[end - 1] == ' '.code) end--
            return start to (end - start)
        }

        // The styles wholly on each piece's words, placed as they will sit
        // in the joined words: each piece after the ones before it and a space.
        val carried = ArrayList<FormatSpan>()
        val moved = HashSet<FormatSpan>()
        if (day == null) {
            var offset = 0
            for (piece in plan.pieces) {
                val (start, length) = words(body, piece) ?: continue
                for (span in spans) {
                    if (span.start >= start && span.end <= start + length && span.end > span.start) {
                        carried.add(span.copy(start = span.start - start + offset, end = span.end - start + offset))
                        moved.add(span)
                    }
                }
                offset += length + 1
            }
        }
        spans = spans.filterNot { it in moved }
        writeCells(segment, table, plan.writes)
        if (carried.isNotEmpty()) {
            words(body, plan.anchor)?.let { (start, _) ->
                spans = (spans + carried.map { it.copy(start = it.start + start, end = it.end + start) })
                    .sortedWith(compareBy({ it.start }, { it.end }))
            }
        }
        changed()
        return plan.anchor
    }

    /**
     * Several cells of one table written, one at a time: each a change the
     * style remap can place exactly. Written together, re-padded, the remap
     * saw one stretch from the first to the last and dropped every style
     * between them. No step for Undo here; the caller makes one.
     */
    private fun writeCells(segment: Editing.Segment, table: TableBlock, writes: List<CellWrite>) {
        for (write in writes) {
            applyTable(segment, TableEdit(tableLines(segment)).setCell(table.fileRow(write.at.row), write.at.column, write.text))
        }
    }

    /** A row made [lines] lines tall by a drag on its edge ([rowLinesPlan]); one step for Undo. */
    fun setRowLines(segment: Editing.Segment, row: Int, lines: Int) {
        writeHeldCell()
        val table = body.split("\n").subList(segment.firstLine, segment.lastLine + 1)
        val writes = rowLinesPlan(table, row, lines)
        if (writes.isEmpty()) return
        writeCells(segment, parseTable(table), writes)
        changed()
    }

    /**
     * A calculation into the note: the whole sum, "5+3=8", at the cursor the
     * first time and then each on its own line after the one before -- the
     * desktop's Insert into note.
     */
    fun insertCalculation(formula: String) {
        // A cell being written in takes the answer at its cursor, as the
        // desktop's _calc_insert_formula puts it in the open cell; the phone
        // put it on a line under the table.
        writeHeldCell()
        val cell = activeCell
        val segment = cell?.let { c -> Editing.segments(body).firstOrNull { it.table && it.firstLine == c.firstLine } }
        if (cell != null && segment != null) {
            val table = parseTable(segment.text.split("\n"))
            // A box is ticked, not written in: the answer went in after its
            // mark, and the box turned into the words "[x]12×3=36".
            if (table.rows.getOrNull(cell.at.row)?.getOrNull(cell.at.column)?.checked != null) {
                state.say("A box holds no answer. Tap a cell with words, or the note's text, where it should go.")
                return
            }
            val words = cellTextAt(table, cell.at).replace(LINE_BREAK, "\n")
            val at = (cellCaret ?: words.trimEnd('\n').length).coerceIn(0, words.length)
            val now = (words.substring(0, at) + formula + words.substring(at)).replace("|", "/")
            editTable(segment, "calculation") { it.setCell(table.fileRow(cell.at.row), cell.at.column, now.replace("\n", LINE_BREAK)) }
            cellCaretNext = at + formula.length
            cellEpoch += 1
            return
        }
        val placed = Calculator.place(body, formula, calcMark, caret)
        // Written where no field shows it -- the end of a note past what the
        // phone draws, the cursor never in it -- the sum was out of sight,
        // and every tap added another.
        if (Editing.segments(placed.body).none { holds(it, placed.mark) }) {
            state.say("The end of this note is more than the phone shows at once. Tap in its text where the " +
                "answer should go, then insert it.")
            return
        }
        // Where it went in, exactly, for Undo and the styles: found by
        // comparing the texts, "\n3×3=9" put before a line break read as
        // "3×3=9\n" after it, and a run starting on the next line took it.
        val came = placed.body.codePointCount(0, placed.body.length) - body.codePointCount(0, body.length)
        val edit = Paragraphs.Edit(placed.mark - came, 0, came)
        spans = if (day != null) emptyList() else Paragraphs.moveSpans(spans, listOf(edit))
        props = Paragraphs.follow(body, placed.body, props, edit)
        made.add(edit)
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
     * The selection noted, brought up to the styling it has NOW, while its
     * words are the same: a style put on after selecting reaches no field,
     * and the copy went out plain (ISSUES_LOG, "A copy of the selection").
     * Called as a copy is made, and before a field's change is applied, as a
     * cut takes the words out before it copies them.
     */
    fun freshenPick() {
        val p = picked ?: return
        if (p.body == body) picked = Picked(p.from, p.to, body, spans, props)
    }

    /**
     * A copy or a cut of [plain] from a field: when it is the selection
     * noted, the HTML to put beside it on the clipboard -- so it arrives
     * elsewhere formatted -- and the copy kept for a paste back into a
     * note. A day's own text is plain: its copy carries its lists' depth
     * alone, as the desktop's day copies it -- cut plain, a nested item
     * pasted back went to the margin.
     */
    fun copyOut(plain: String): String? {
        freshenPick()
        val p = picked ?: return null
        val copy = (if (day != null) Pasting.copyOut(p.body, emptyList(), Paragraphs.listDepths(p.props, p.body), p.from, p.to)
                    else Pasting.copyOut(p.body, p.spans, p.props, p.from, p.to)) ?: return null
        if (copy.text != plain) return null
        state.ownCopy = copy
        return copy.html
    }

    /**
     * A paste the field was about to make, made here as the desktop's is:
     * the lists it lands in renumbered and given their depth
     * ([Pasting.insert]), HTML with its formatting, table rows on lines of
     * their own ([Pasting.foreignPlain]). Left to the field, a list line cut
     * and pasted back read 1, 2, 2. True: the paste is made.
     */
    fun paste(segment: Editing.Segment, old: TextFieldValue, html: String?, plain: String): Boolean {
        val (from, to) = Editing.bodyRange(segment, old.selection.min, old.selection.max)
        if (day != null) {
            // A day's own text takes the words, and of its own copy the
            // lists' depth -- never styles or centring.
            val mine = state.ownCopy?.takeIf { !html.isNullOrEmpty() && html == it.html }
            val text = mine?.text ?: Pasting.foreignPlain(plain, state.ownCopy)
            val lines = mine?.paragraphs.orEmpty().filter { it.kind != null }.map { it.copy(align = null) }
            land(Pasting.insert(body, emptyList(), Paragraphs.listDepths(props, body), from, to, text, emptyList(), lines))
            return true
        }
        val read = if (html.isNullOrEmpty()) null else Pasting.fromHtml(html, state.ownCopy)
        val done = if (read != null) {
            Pasting.insert(body, spans, props, from, to, read.text, read.formatting, read.paragraphs)
        } else {
            // Words from elsewhere: a table's separator at markdown's minimum.
            val text = Pasting.foreignPlain(plain, state.ownCopy)
            Pasting.insert(body, spans, props, from, to, Pasting.pipeRows(text) ?: text)
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
        val text = Pasting.plainText(Pasting.foreignPlain(raw, state.ownCopy))
        if (text.isEmpty() && old.selection.collapsed) return
        val (from, to) = Editing.bodyRange(segment, old.selection.min, old.selection.max)
        // On a day, its lists' depth goes in, or the paste renumbered a nested
        // list as one run.
        land(Pasting.insert(body, spans, if (day != null) Paragraphs.listDepths(props, body) else props, from, to, text))
    }

    /** A paste written: the note, one step of Undo, and the cursor after what came in. */
    private fun land(done: Pasting.Inserted) {
        // Through the paste and each marker it renumbered, one by one: as one
        // change, a paste in a list above the calculator's last answer sent
        // its next one into a later line.
        carry(done.edits.ifEmpty { listOfNotNull(Calculator.changeOf(body, done.body)) })
        spans = if (day != null) emptyList() else done.spans
        props = if (day != null) Paragraphs.listDepths(done.props, done.body) else done.props
        body = done.body
        caret = done.caret
        changed()
        // Rows pasted become a table, and words too long to lay out a piece
        // shown read-only: the field the cursor is in may be another one now.
        placeCaret(done.caret)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(state: AppState, openId: Int, beside: Boolean = false) {
    val palette = LocalPalette.current
    // Not keyed on the id, which a new note's first save gives. Built anew on
    // another of its days, the note takes the words waiting in "A new item…"
    // along: left in the editor replaced, they were kept nowhere.
    val built = remember { arrayOfNulls<EditorModel>(1) }
    val model = remember(state.opening, state.openDay) {
        EditorModel(state, state.note(openId), state.openDay, state.newNoteDate).also { made ->
            built[0]?.takeIf { made.id != 0 && it.id == made.id }?.let { made.adding = it.adding }
            built[0] = made
        }
    }
    // For the visit, as the checklist's and comments' flags are: rebuilt on
    // the whole series or another day, it shut the calculator with its sum
    // and the find bar. Both read the model at use.
    val calc = remember { CalculatorState() }
    var exportAsk by remember { mutableStateOf(false) }
    var shareAsk by remember { mutableStateOf(false) }
    var pickTemplate by remember { mutableStateOf<List<org.json.JSONObject>?>(null) }
    var tableAsk by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf<org.json.JSONObject?>(null) }
    val templateNone = stringResource(R.string.template_none)
    val templateNothing = stringResource(R.string.template_nothing)
    val templateStarted = stringResource(R.string.template_started)
    val find = remember { FindState() }
    val findFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Worked out again as the note or the search changes; nothing at all
    // while the bar is shut.
    val checkWords = state.note(model.id)?.checklist.orEmpty().map { it.text }
    val matches = remember(model.body, checkWords, model.comments, state.findQuery, state.findMatchCase, find.open) {
        if (find.open) Finding.scan(model.body, state.findQuery, state.findMatchCase, checkWords, model.comments)
        else emptyList()
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
    // The checklist and comments: shown when the note has them, or once asked
    // for from ⋮ (the owner's call), then for this visit even emptied, so a field
    // never goes from under the finger. A visit is key(state.opening), not the id.
    var checklistOpen by remember { mutableStateOf(note?.checklist.orEmpty().isNotEmpty()) }
    var commentsOpen by remember { mutableStateOf(model.comments.isNotEmpty()) }
    val checklistShown = checklistOpen || note?.checklist.orEmpty().isNotEmpty()
    val commentsShown = commentsOpen || model.comments.isNotEmpty()
    var checklistAsk by remember { mutableIntStateOf(0) }
    var commentsAsk by remember { mutableIntStateOf(0) }
    var attachAsk by remember { mutableIntStateOf(0) }
    var askRestore by remember { mutableStateOf(false) }
    var tableMore by remember { mutableStateOf(false) }
    var askDeleteTable by remember { mutableStateOf(false) }
    // The table and the cell being written in, found again in the body as
    // it is now: an edit makes the segment anew.
    val activeSegment = model.activeCell?.let { a ->
        Editing.segments(model.body).firstOrNull { it.table && it.firstLine == a.firstLine }
    }
    LaunchedEffect(model.activeCell, activeSegment == null) {
        // Its table gone -- deleted, undone away -- the cell lets go.
        if (model.activeCell != null && activeSegment == null) model.activeCell = null
    }
    // One day of a series: what it holds of its own, and where it came from.
    val day = model.day
    val raw = state.rawNote(model.id)
    val ownEdit = day != null && (raw?.opt("overrides") as? org.json.JSONObject)?.has(day) == true
    val cameFrom = if (day != null && raw != null) Recurrence.movedFrom(raw, day) else null
    var asking by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf(false) }
    var walkingOff by remember { mutableStateOf<List<String>?>(null) }

    // Closed once its words are saved; kept open, and asked, when they
    // cannot be (AppState.leaving).
    fun leave() = state.saveAndCloseNote()

    BackHandler { leave() }
    // Back closes the find bar first, then the calculator, then a cell being
    // written in, then the note.
    BackHandler(enabled = model.activeCell != null) {
        model.activeCell = null
        focusManager.clearFocus()
    }
    BackHandler(enabled = calc.open) { closeCalculator(calc, focusManager) }
    BackHandler(enabled = find.open) { closeFind() }
    // Saved a moment after the last change, as the desktop's autosave is,
    // and when the app goes to the background -- where most notes on a
    // phone are left, by Home or by switching apps rather than by Back.
    LaunchedEffect(model.edits) {
        if (model.unsaved) {
            delay(800)
            model.save(auto = true)
        }
    }
    DisposableEffect(model) {
        state.flush = { model.write() }
        state.flushStaying = { model.writeStaying() }
        state.flushAway = { model.writeAway() }
        state.backAgain = { model.backAgain() }
        onDispose {
            state.flush = null
            state.flushStaying = null
            state.flushAway = null
            state.backAgain = null
        }
    }
    // The line being written went where no field is: the keyboard goes
    // with it, rather than writing on the line above (EditorModel.placeCaret).
    LaunchedEffect(model.letGo) { if (model.letGo > 0) focusManager.clearFocus() }
    // Written from outside while open -- pinned from the list beside it,
    // updated by an import -- and saved first by whatever wrote it: read
    // back, so the next save cannot put the old fields back.
    val rewrittenAt = remember(model) { state.openNoteRewritten }
    LaunchedEffect(model, state.openNoteRewritten) {
        if (state.openNoteRewritten != rewrittenAt) state.note(model.id)?.let { model.reloadFrom(it) }
    }
    // The keys in a box the note's Undo does not hold -- a checklist row, "A
    // new item…", Find, the calculator -- are its own: Ctrl+Z there took the
    // body's last words instead, again at every press.
    var rowsHaveKeys by remember { mutableStateOf(false) }
    var findHasKeys by remember { mutableStateOf(false) }
    var calcHasKeys by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            // A keyboard plugged into a tablet: the desktop's own keys.
            .onPreviewKeyEvent { event ->
                if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown || !event.isCtrlPressed
                    || rowsHaveKeys || findHasKeys || calcHasKeys) {
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
        // The phone on its side with the keyboard up: no top bar, Undo and
        // Redo at the start of the format bar's one row instead, so a few of
        // the note's lines stay above the keys. With the bar, none did.
        val compact = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP && WindowInsets.isImeVisible
        if (!compact) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { leave() }, modifier = Modifier.said(R.string.back_said)) {
                Text("←", fontSize = 22.sp, color = palette.text)
            }
            // Beside the list or the calendar: « puts it aside for a wider
            // note, » brings it back (asked for on the owner's phone).
            if (beside) {
                val aside = state.listAside
                val asideSaid = stringResource(when {
                    state.tab == Tab.CALENDAR -> if (aside) R.string.show_calendar else R.string.hide_calendar
                    else -> if (aside) R.string.show_list else R.string.hide_list
                })
                IconButton(onClick = { state.listAside = !aside },
                           modifier = Modifier.semantics { contentDescription = asideSaid }) {
                    Text(if (aside) "»" else "«", fontSize = 22.sp, color = palette.text)
                }
            }
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
            // The status takes what the buttons leave, and shortens: measured
            // before them, "Saved for this day only" squeezed ⋮ to a sliver,
            // and at a larger font to nothing.
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
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
            )
            // Share: one door out -- a PDF, a page, the words, a code --
            // where 🔗 and "Send by email" were two. A note with nothing in
            // it yet is not saved to be shared, as the desktop refuses it.
            val shareSaid = stringResource(R.string.share_title)
            val nothingYet = stringResource(R.string.share_nothing_yet)
            IconButton(onClick = {
                if (model.blank) state.say(nothingYet)
                else if (model.save(force = model.id == 0)) shareAsk = true
            }, modifier = Modifier.semantics { contentDescription = shareSaid }) { ShareMark(palette.text) }
            IconButton(onClick = { details = true }, modifier = Modifier.said(R.string.note_details_said)) {
                Text("ⓘ", fontSize = 20.sp, color = palette.text)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.said(R.string.more_options_said)) {
                    Text("⋮", fontSize = 20.sp, color = palette.text)
                }
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
                    if (!checklistShown) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_checklist)) },
                            onClick = { menu = false; checklistOpen = true; checklistAsk += 1 },
                        )
                    }
                    if (!commentsShown) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_comments)) },
                            onClick = { menu = false; commentsOpen = true; commentsAsk += 1 },
                        )
                    }
                    // A day of a series adds no files: they are the series'.
                    if (day == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.attach_files)) },
                            onClick = { menu = false; attachAsk += 1 },
                        )
                    }
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
                                if (model.save()) pickTemplate = templates
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
                    // Not on a day: it copied the whole series, and the calendar
                    // then showed two notes on every one of its days.
                    if (day == null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_duplicate)) },
                            onClick = {
                                menu = false
                                if (model.save(force = model.id == 0)) state.duplicate(model.id)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.note_export)) },
                        onClick = {
                            menu = false
                            // The note as saved is what goes out: saved first.
                            if (model.save(force = model.id == 0)) exportAsk = true
                        },
                    )
                    if (day != null) {
                        // A day of a series is deleted on its own, or the series
                        // is: the desktop's two answers, as two entries.
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_day_delete), color = palette.dangerText) },
                            onClick = { menu = false; if (model.saveStaying()) asking = "day" },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_series_delete), color = palette.dangerText) },
                            onClick = { menu = false; if (model.saveStaying()) asking = "series" },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_trash), color = palette.dangerText) },
                            onClick = {
                                menu = false
                                if (model.id == 0 && model.blank) {
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

        if (find.open) Box(Modifier.onFocusChanged { findHasKeys = it.hasFocus }) {
            DisposableEffect(Unit) { onDispose { findHasKeys = false } }
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
                    // A moved day says where it came from, as the desktop's strip says it.
                    val from = cameFrom?.let { Recurrence.parseDate(it) }?.let { Recurrence.showDay(it, year = false) }
                    Text(stringResource(R.string.day_banner, Recurrence.parseDate(day)?.let { Recurrence.showDay(it) } ?: day) +
                         (from?.let { " " + stringResource(R.string.day_moved_from, it) } ?: ""),
                         color = palette.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    // Wrapping: three ways out of one day do not fit a
                    // phone's width on one line.
                    androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.day_whole_series), color = palette.accentText, fontSize = 13.sp,
                             fontWeight = FontWeight.Medium,
                             // The editor built anew on the series carries "A new item…".
                             modifier = Modifier.clickable { if (model.saveStaying()) state.openWholeSeries() })
                        Text(stringResource(R.string.day_move), color = palette.accentText, fontSize = 13.sp,
                             fontWeight = FontWeight.Medium, modifier = Modifier.clickable { moving = true })
                        if (ownEdit || cameFrom != null) {
                            Text(stringResource(R.string.day_reset), color = palette.accentText, fontSize = 13.sp,
                                 fontWeight = FontWeight.Medium, modifier = Modifier.clickable { if (model.saveStaying()) asking = "reset" })
                        }
                    }
                }
            }
            // The whole series, with days keeping their own edits, which no
            // calendar shows: the note says so and offers the way to each, as the
            // desktop's banner does. Worked out once per write, not per letter.
            val counts = remember(raw, state.revision, day) {
                if (day == null && raw != null && Recurrence.isRecurring(raw)) Recurrence.seriesCounts(raw) else null
            }
            if (day == null && raw != null && Recurrence.isRecurring(raw) && counts != null) {
                val (own, held) = counts
                val line = buildString {
                    append(stringResource(R.string.series_banner, Recurrence.describe(raw)))
                    if (own > 0) append(" · ").append(pluralStringResource(R.plurals.series_own_edits, own, own))
                    if (held > 0) append(" · ").append(pluralStringResource(R.plurals.series_moved, held, held))
                }
                Text(line, color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            val offDays = remember(raw, state.revision, day) {
                if (day == null && raw != null) Recurrence.offScheduleDays(raw) else emptyList()
            }
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
                        .clickable { if (model.saveStaying()) walkingOff = offDays }
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
                        // Saved first, as the desktop's restore saves first, so the
                        // question names the copy that save just made: saved only on
                        // yes, it brought back the words just deleted instead.
                        .clickable { if (model.save()) askRestore = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.restore_banner), color = palette.subtext, fontSize = 13.sp,
                         modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.restore_previous), color = palette.accentText,
                         fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            // The title with its cursor, so a paste cut to fit leaves it after
            // what came in ([fittedValue]); the words themselves are the model's.
            var titleField by remember { mutableStateOf(model.titleBox(TextFieldValue())) }
            val titleShown = model.titleBox(titleField)
            // A title too long to lay out -- a share code can bring one -- is
            // shown, not edited: as a field it froze the note at every opening.
            if (Editing.tooLongToLayOut(model.title)) LongText(model.title) else BasicTextField(
                value = titleShown,
                onValueChange = { next ->
                    // Enter goes on to the note's words, the title as it was:
                    // told by what came in, not by the whole value.
                    if (Editing.isEnter(titleShown.text, next.text)) focusManager.moveFocus(FocusDirection.Down)
                    else titleField = model.typeTitle(titleShown, next)
                },
                textStyle = TextStyle(color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().onFocusChanged {
                    if (it.isFocused) {
                        model.focus = null
                        model.activeCell = null
                    }
                },
                decorationBox = { field ->
                    if (model.title.isEmpty()) {
                        Text(stringResource(R.string.title_hint), color = palette.subtext, fontSize = 22.sp,
                             fontWeight = FontWeight.Medium)
                    }
                    field()
                },
            )
            // In letters, as the cap and the desktop's counter count them.
            val titleUsed = Editing.rowChars(model.title)
            if (titleUsed >= 160) {
                Text("$titleUsed/${Editing.TITLE_CHARS}", fontSize = 11.sp,
                     color = if (titleUsed >= Editing.TITLE_CHARS) palette.dangerText else palette.subtext)
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
            // The text folds away too, once there is a checklist or comments to
            // make room for -- never while a search in it is open.
            val textKey = "${model.id}:text"
            val textFoldable = checklistShown || commentsShown
            val textFolded = textFoldable && state.folded[textKey] == true && !find.open
            if (textFoldable) {
                FoldLabel(stringResource(R.string.note_text_label), textFolded) {
                    if (!textFolded) {
                        focusManager.clearFocus()
                        model.focus = null
                        model.activeCell = null
                    }
                    state.folded[textKey] = !textFolded
                }
            }
            if (!textFolded) segments.forEachIndexed { index, segment ->
                val here = bySegment[index].orEmpty()
                // A piece shown read-only is searched as written ([Finding.scan]).
                val foundIn = { here.filterIsInstance<Finding.InText>().map { it.start to it.end } }
                val currentIn = (currentMatch as? Finding.InText)?.takeIf { it.segment == index }?.let { it.start to it.end }
                if (segment.long) {
                    LongText(segment.text, segment.heavy, foundIn(), currentIn, find.reveal)
                } else if (segment.table && !segment.drawn) {
                    LargeTable(segment, foundIn(), currentIn, find.reveal)
                } else if (segment.table) {
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
                    val writing = model.activeCell?.takeIf { it.firstLine == segment.firstLine }?.at
                    TableView(
                        table = table,
                        modifier = Modifier.padding(vertical = 8.dp),
                        editing = true,
                        onCellTap = { at -> chooseCell(model, segment, table, at) },
                        place = model.props[segment.firstLine]?.align ?: "left",
                        highlights = fills,
                        found = here.mapNotNull { (it as? Finding.InCell)?.let { m -> CellAt(m.row, m.column) } }.toSet(),
                        current = (currentMatch as? Finding.InCell)?.takeIf { it.segment == index }
                            ?.let { CellAt(it.row, it.column) },
                        reveal = find.reveal,
                        active = writing,
                        editor = writing?.let { at -> { style -> CellEditor(model, segment, table, at, style) } },
                        onResizeColumn = { column, chars ->
                            model.editColumns(segment) { setColumnWidth(it, column, chars) }
                        },
                        onResizeRow = { row, lines ->
                            model.setRowLines(segment, row, lines)
                            // The cell being written in may have gained or lost its empty lines.
                            model.cellEpoch += 1
                        },
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

            // The same lists while the search is the same: made anew at every
            // pass, they made the checklist draw every row again for each
            // letter typed in the text above it.
            val inChecklist = remember(matches) { matches.filterIsInstance<Finding.InChecklist>() }
            val inComments = remember(matches) { matches.filterIsInstance<Finding.InComments>() }
            // A Column: the section draws its label, rows and box one under
            // another, and a Box laid them over each other.
            Column(Modifier.onFocusChanged { rowsHaveKeys = it.hasFocus }) {
                ChecklistSection(state, model, shown = checklistShown, focusAsk = checklistAsk,
                                 found = inChecklist,
                                 current = currentMatch as? Finding.InChecklist, finding = find.open, reveal = find.reveal)
            }
            CommentsSection(state, model, shown = commentsShown, focusAsk = commentsAsk,
                            found = inComments,
                            current = currentMatch as? Finding.InComments, finding = find.open, reveal = find.reveal)
            AttachmentsSection(state, model, pickAsk = attachAsk)
            Spacer(Modifier.height(48.dp))
        }

        // The calculator stands aside while the keyboard is up for the note --
        // the two together would leave no note to see -- and comes back when
        // it goes down. Typed into, its own display keeps it in place.
        val writingIn = model.activeCell
        // Where the foot drawn here begins, and the box being typed in, told
        // to the window: its messages are drawn above them ([AppState.editorFootTop]).
        var footTop by remember { mutableStateOf<Float?>(null) }
        val footShown = listOfNotNull(footTop, model.addBoxTop, model.renamingRow?.second).minOrNull()
        LaunchedEffect(footShown) { state.editorFootTop = footShown }
        DisposableEffect(Unit) { onDispose { state.editorFootTop = null } }
        Box(Modifier.fillMaxWidth().onGloballyPositioned {
            footTop = if (it.size.height > 0) it.boundsInWindow().top else null
        }) {
        if (calc.open && (!WindowInsets.isImeVisible || calc.displayFocused)) {
            Box(Modifier.onFocusChanged { calcHasKeys = it.hasFocus }) {
                DisposableEffect(Unit) { onDispose { calcHasKeys = false } }
                CalculatorPanel(calc, onInsert = { model.insertCalculation(it) },
                                onClose = { closeCalculator(calc, focusManager) })
            }
        } else if (writingIn != null && activeSegment != null) {
            // In a table, the table's own bar takes the format bar's place.
            TableBar(model, activeSegment, writingIn.at,
                     onMore = { tableMore = true }, onDeleteTable = { askDeleteTable = true })
        } else if (model.focus != null) {
            if (day == null) FormatBar(model) { tableAsk = true }
            else Text(stringResource(R.string.day_plain), color = palette.subtext, fontSize = 12.sp,
                      modifier = Modifier.fillMaxWidth().background(palette.card).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        }
    }
    walkingOff?.let { days ->
        OffScheduleQuestions(state, model.id, days, onDone = { walkingOff = null })
    }
    if (day != null && asking != null) {
        // What a change makes the day show is read back by the change itself
        // (AppState.outside): read back here too, a Cancel emptied the text's
        // Undo.
        DayQuestions(state, model.id, day, cameFrom, ownEdit, asking) { asking = null }
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
                if (first != null && Editing.segments(model.body).any { it.table && it.drawn && it.firstLine == first }) {
                    model.activeCell = ActiveCell(first, CellAt(0, 0))
                }
            },
            onDismiss = { tableAsk = false },
        )
    }
    pickTemplate?.let { templates ->
        TemplatePicker(
            templates,
            onPick = { tpl ->
                pickTemplate = null
                // A new note started on a calendar day keeps the day, typed in
                // first or not: the template's note was made undated.
                val begunNew = model.startedNew || (model.id != 0 && model.id == state.templateDayNote)
                state.useTemplate(tpl, templateStarted, date = if (begunNew) model.date else "")
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
                state.exporter?.export(model.id, format, day)
            },
            onDismiss = { exportAsk = false },
        )
    }
    if (shareAsk) ShareSheet(state, model.id, day) { shareAsk = false }
    if (tableMore && model.activeCell != null && activeSegment != null) {
        TableMoreSheet(model, activeSegment, model.activeCell!!.at,
                       onDeleteTable = { tableMore = false; askDeleteTable = true },
                       onDone = { tableMore = false })
    } else if (tableMore) {
        tableMore = false
    }
    if (askDeleteTable && activeSegment != null) {
        // Asked, though Undo would bring it back: a whole table is the most
        // one tap can take away.
        Ask(
            title = stringResource(R.string.delete_table),
            text = stringResource(R.string.delete_table_text),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                askDeleteTable = false
                model.activeCell = null
                model.deleteTable(activeSegment)
                focusManager.clearFocus()
            },
            onDismiss = { askDeleteTable = false },
            destructive = true,
        )
    } else if (askDeleteTable) {
        askDeleteTable = false
    }
    if (askRestore && note != null) {
        val also = note.previousAlso
        // Worked out for this note, once, when the question is asked.
        val keeps = remember(note) { Notebook.previousKeeps(state.load, note.id) }
        Ask(
            title = stringResource(R.string.restore_title),
            // ...and kept, said only when it is: an emptied note is not.
            text = (if (also.isEmpty()) stringResource(R.string.restore_text_only)
                    else stringResource(R.string.restore_text_with, joinWithAnd(also))) +
                   (if (keeps) " " + stringResource(R.string.restore_kept) else "") +
                   // On one day of a series, as the desktop asks: the copy is
                   // of the whole note, and "the current text" read as the
                   // day's.
                   (if (day != null) "\n\n" + stringResource(R.string.restore_whole_note) else ""),
            confirm = stringResource(R.string.restore_confirm),
            onConfirm = {
                askRestore = false
                val onADay = day != null
                // What waits for its autosave is saved first, so the version
                // replaced is the one on screen; when it cannot be, nothing is,
                // or the restore would take the place of words no file holds.
                if (model.save() && state.report(Notebook.restorePrevious(state.load, model.id))) {
                    state.note(model.id)?.let { model.reloadFrom(it) }
                    state.say(if (onADay) "Restored the previous version of the whole note" else "Restored the previous version")
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
    // Where the last press came down, for [tapLanding].
    var pressedAt by remember { mutableStateOf<Offset?>(null) }
    // A press let go that the field said nothing of (it landed where the cursor
    // already was, past an empty last line's stand-in): put back here, a frame
    // later, once the field has had its say.
    var letGo by remember { mutableIntStateOf(0) }
    LaunchedEffect(letGo) {
        if (letGo == 0) return@LaunchedEffect
        withFrameNanos { }
        val pressed = pressedAt ?: return@LaunchedEffect
        val now = model.focused?.takeIf { model.focus == index && it.text == segment.text } ?: return@LaunchedEffect
        pressedAt = null
        val landed = tapLanding(now, pressed, layout)
        if (landed != now) {
            model.focused = landed
            model.caret = caretIn(segment, landed)
            model.pick(segment, landed)
        }
    }
    // The line being written kept in sight as it is typed, and as the keyboard rises.
    val following = rememberFollowing(cursor = value.selection.end.takeIf { model.focus == index })
    val once = remember { Editing.Once() }
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
            // Told twice, taken once ([Editing.Once]): Samsung's keyboard's
            // Enter on a word it is composing.
            if (!once.news(value, next, SystemClock.uptimeMillis())) return@BasicTextField
            // Before anything moves: a cut arrives here with its words already gone.
            model.freshenPick()
            val pasted = clipboard.pasteOf(value, next)
            if (pasted != null && model.paste(segment, value, pasted.html, pasted.text)) return@BasicTextField
            val pressed = pressedAt
            if (next.selection != value.selection) pressedAt = null
            // A keystroke that meant a list operation comes back rewritten;
            // a tap past a line's end, put back on that line.
            val shown = (if (next.text != segment.text) model.typeInto(segment, next, index) else null)
                ?: if (pressed != null && next.text == value.text) tapLanding(next, pressed, layout) else next
            following.typed(value.text, shown.text, shown.selection.end)
            model.focus = index
            model.focused = shown
            model.caret = caretIn(segment, shown)
            model.pick(segment, shown)
            if (next.text != segment.text) model.keepCaret(index, shown)
        },
        visualTransformation = { text ->
            val drawn = renderBody(Note(body = text.text, formatting = local), palette.bg).text
            val painted = paintLines(paintFound(drawn, found, match, palette), segment.firstLine, model.props)
            TransformedText(painted,
                            if (painted.length > text.length) StandInAtEnd(text.length) else OffsetMapping.Identity)
        },
        onTextLayout = { layout = it; following.laidOut(it) },
        textStyle = TextStyle(color = palette.text, fontSize = BODY_SIZE.sp, lineHeight = BODY_LINE_HEIGHT.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (placeholder) 160.dp else 24.dp)
            .focusRequester(focuser)
            .bringIntoViewRequester(requester)
            .bringIntoViewRequester(following.requester)
            .pointerInput(Unit) {
                awaitEachGesture {
                    pressedAt = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).position
                    if (waitForUpOrCancellation(PointerEventPass.Initial) != null) letGo += 1
                }
            }
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
                    // Writing in the note's text lets go of a table's cell.
                    model.activeCell = null
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
 * The phone's clipboard as a note's field sees it: a copy or a cut the field
 * makes gets its HTML beside the words, and a paste is recognised when its
 * text arrives in place of the selection, so the editor can make it with the
 * clipboard's formatting. Done under the field rather than by replacing the
 * menu's Paste: the menu keeps its items, and a keyboard's Ctrl+V comes too.
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

/** What a painted line break, and an empty last line, are drawn as: a zero-width space. */
const val STAND_IN = '​'

/**
 * The lines of one field with their indent and alignment, and a list
 * item's wrapped lines hanging after its marker, as the desktop's margins
 * draw them. [firstLine] is the field's first line in the whole body.
 */
private fun paintLines(text: AnnotatedString, firstLine: Int, props: Map<Int, Paragraphs.Props>): AnnotatedString {
    val lines = text.text.split("\n")
    val grow = linesGrown(text, lines)
    val styled = lines.withIndex().any { (i, line) ->
        props[firstLine + i] != null || Paragraphs.parseListMarker(line).kind != null || grow[i] > 1f }
    if (!styled) return text
    // Every line its own paragraph, the breaks drawn as nothing, one character
    // for one so positions stay (ISSUES_LOG, "A list drawn double-spaced"); a
    // tap can land after a break ([tapLanding]).
    val paragraphs = ArrayList<AnnotatedString.Range<ParagraphStyle>>()
    // An empty last line gets a stand-in drawn after the text ([StandInAtEnd]),
    // so a paragraph and style of its own: without one it was not drawn at all,
    // or wore the line above's centring. A press there is put back ([tapLanding]).
    val standIn = lines.last().isEmpty()
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
            // The field's own spacing, which a paragraph's style replaces,
            // untrimmed (trimmed, a one-line paragraph bunched up), and grown
            // with its largest size, in em as the letters are.
            lineHeight = if (grow[i] > 1f) (BODY_LINE_HEIGHT / BODY_SIZE.toFloat() * grow[i]).em
                         else BODY_LINE_HEIGHT.sp,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        )
        val last = standIn && i == lines.lastIndex
        if (end > at || last) paragraphs.add(AnnotatedString.Range(style, at, if (last) end + 1 else end))
        at = end
    }
    val drawn = text.text.replace('\n', STAND_IN) + if (standIn) STAND_IN.toString() else ""
    return AnnotatedString(drawn, text.spanStyles, paragraphs)
}

/**
 * Each of [lines]' largest size, in ems of the body ([RichText]'s sizes),
 * 1 for none larger: held to the body's 24 sp, two lines of 20 pt ran into
 * each other. Each size span placed by its lines' starts,
 * not each span tried against each line: a note keeps up to 10,000 spans.
 */
private fun linesGrown(text: AnnotatedString, lines: List<String>): FloatArray {
    val grow = FloatArray(lines.size) { 1f }
    val starts = IntArray(lines.size)
    for (i in 1 until lines.size) starts[i] = starts[i - 1] + lines[i - 1].length + 1
    fun lineOf(at: Int): Int {
        val i = java.util.Arrays.binarySearch(starts, at)
        return if (i >= 0) i else -i - 2
    }
    for (span in text.spanStyles) {
        val size = span.item.fontSize
        if (!size.isEm || size.value <= 1f || span.end <= span.start) continue
        for (i in lineOf(span.start).coerceAtLeast(0)..lineOf(span.end - 1).coerceAtLeast(0)) {
            if (size.value > grow[i]) grow[i] = size.value
        }
    }
    return grow
}

/**
 * The positions of a field drawn with one stand-in more than its text, after
 * an empty last line ([paintLines]): every position of the text where it was,
 * and the stand-in's own, after it, the end of the text.
 */
private class StandInAtEnd(private val length: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset
    override fun transformedToOriginal(offset: Int): Int = minOf(offset, length)
}

/**
 * Where a tap meant the cursor to go. Each break is a stand-in a tap can
 * land after ([paintLines]): a tap past a line's end went to the next line's
 * start, before its marker, and is put back before the break. A tap on a
 * list's marker puts the cursor after it: before it, a letter made "x3. ham"
 * (ISSUES_LOG, "A break drawn as a character").
 */
private fun tapLanding(value: TextFieldValue, pressed: Offset, layout: TextLayoutResult?): TextFieldValue {
    val drawn = layout ?: return value
    var at = value.selection.start
    if (!value.selection.collapsed || at > value.text.length || at > drawn.layoutInput.text.length) return value
    if (at > 0 && value.text[at - 1] == '\n' &&
        drawn.getLineForVerticalPosition(pressed.y) < drawn.getLineForOffset(at)) at -= 1
    val lineStart = value.text.lastIndexOf('\n', at - 1) + 1
    val lineEnd = value.text.indexOf('\n', lineStart).let { if (it < 0) value.text.length else it }
    val parsed = Paragraphs.parseListMarker(value.text.substring(lineStart, lineEnd))
    if (parsed.kind != null && at < lineStart + parsed.marker.length) at = lineStart + parsed.marker.length
    return if (at == value.selection.start) value else value.copy(selection = TextRange(at))
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
 * The styles, colours, lists, depth, alignment, face, size, a table and
 * Undo/Redo, over what is selected in the field that has the cursor or its
 * lines -- sitting above the
 * keyboard, where the thumb already is and where Android's own selection
 * menu does not reach.
 */
@OptIn(ExperimentalLayoutApi::class)
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

    // With nothing selected, B, I, U and S wait for what is typed next.
    val waits = !hasSelection && model.day == null
    fun apply(style: String, colour: String = "") {
        if (!hasSelection) {
            if (waits && style in PENDING_STYLES) {
                model.pending = if (style in model.pending) model.pending - style else model.pending + style
            }
            return
        }
        model.spans = toggleStyle(model.spans, from, to, style, colour)
        model.changed()
    }
    fun on(style: String) = if (hasSelection) style in active else style in model.pending

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
                for ((n, colour) in colours.withIndex()) {
                    // Named, as the table bar's swatches are: a screen reader
                    // said nothing for them, and the walk could not find one.
                    val said = stringResource(if (style == "highlight") R.string.highlight_colour else R.string.text_colour, n + 1)
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(hexColor(colour))
                            .semantics { contentDescription = said }
                            .clickable { apply(style, colour); palettePicker = "" },
                    )
                }
            }
        }
        // The phone on its side: one row of keys that scrolls, no line of words
        // above it (they took all the height the keyboard left); the keys'
        // pressed look says what waits for the next letter.
        val short = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
        if (!hasSelection && !short) {
            val waiting = PENDING_STYLES.filter { it in model.pending }.map { stringResource(styleName(it)) }
            Text(if (waiting.isNotEmpty()) stringResource(R.string.pending_on, waiting.joinToString(", "))
                 else stringResource(R.string.select_to_format),
                 color = if (waiting.isNotEmpty()) palette.accentText else palette.subtext, fontSize = 11.sp,
                 modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        }
        // The lines the cursor or selection covers, for lists, depth and
        // alignment; not in the empty field under a table ending the note, which
        // has no line of its own until something is typed there.
        val lineKeys = !segment.isVirtual
        val firstLine = segment.firstLine + value.text.substring(0, value.selection.min).count { it == '\n' }
        val lastLine = segment.firstLine + value.text.substring(0, value.selection.max).count { it == '\n' }
        val lineText = model.body.split("\n").getOrElse(firstLine) { "" }
        val kind = Paragraphs.parseListMarker(lineText).kind
        val align = model.props[firstLine]?.align ?: "left"
        fun lines(done: Paragraphs.Done?) = model.applyLines(done, index, from, to)
        @Composable
        fun lineButtons() {
            FormatKey("•", kind == "bullet", lineKeys, said = stringResource(R.string.list_bullet), toggle = true) {
                lines(Paragraphs.toggleList(model.body, model.props, firstLine, lastLine, "bullet"))
            }
            FormatKey("1.", kind == "number", lineKeys, said = stringResource(R.string.list_number), toggle = true) {
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // The top bar stands aside on its side with the keyboard up: its
            // Undo and Redo come here.
            if (short && WindowInsets.isImeVisible) {
                FormatKey("↶", false, model.canUndo, said = stringResource(R.string.undo)) { model.undo() }
                FormatKey("↷", false, model.canRedo, said = stringResource(R.string.redo)) { model.redo() }
            }
            FormatKey("B", on("bold"), hasSelection || waits, weight = FontWeight.Bold,
                      said = stringResource(R.string.key_bold), toggle = true) { apply("bold") }
            FormatKey("I", on("italic"), hasSelection || waits, italic = true,
                      said = stringResource(R.string.key_italic), toggle = true) { apply("italic") }
            FormatKey("U", on("underline"), hasSelection || waits, decoration = TextDecoration.Underline,
                      said = stringResource(R.string.key_underline), toggle = true) { apply("underline") }
            FormatKey("S", on("strike"), hasSelection || waits, decoration = TextDecoration.LineThrough,
                      said = stringResource(R.string.key_strike), toggle = true) { apply("strike") }
            FormatKey("A", "text_color" in active, hasSelection, weight = FontWeight.Bold,
                      said = stringResource(R.string.key_text_colour)) {
                palettePicker = if (palettePicker == "text") "" else "text"
            }
            FormatKey("🖍", "highlight" in active, hasSelection, said = stringResource(R.string.key_highlight)) {
                palettePicker = if (palettePicker == "mark") "" else "mark"
            }
            FormatKey("Aa", "font_family" in active, hasSelection, weight = FontWeight.Medium,
                      said = stringResource(R.string.key_face)) {
                palettePicker = if (palettePicker == "face") "" else "face"
            }
            // The size box says the selection's size, as the desktop's does
            // ("Default" for none of its own, or a mix); with nothing selected,
            // the size the next letter typed will take.
            val sizeHere = if (hasSelection) uniformValue(model.spans, from, to, "font_size")
                           else if (to > 0) uniformValue(model.spans, to - 1, to, "font_size") else null
            val sizeLabel = sizeHere ?: Fonts.DEFAULT
            FormatKey(sizeLabel, "font_size" in active, hasSelection, weight = FontWeight.Medium,
                      wide = sizeLabel.length > 3, said = stringResource(R.string.size_key_said, sizeLabel)) {
                palettePicker = if (palettePicker == "size") "" else "size"
            }
            if (short) lineButtons()
        }
        if (!short) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { lineButtons() }
        }
    }
}

/** Under this height in dp the window is a phone on its side: the format bar takes one row. */
private const val SHORT_SCREEN_DP = 480

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
                // Every column drawn, so the squares lit are the table's shape;
                // the first chooses two, the least a table has (the desktop's grid
                // starts at its second, lighting one column for two).
                for (r in 1..Editing.TABLE_ROWS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 1..Editing.TABLE_COLUMNS) {
                            val inside = r <= rows && c <= columns
                            val wide = maxOf(2, c)
                            // Each square says the shape it makes: they were
                            // buttons with no words at all.
                            val shapeSaid = stringResource(R.string.table_size, r, wide)
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (inside) palette.accent else palette.inset)
                                    .semantics { contentDescription = shapeSaid }
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

/**
 * A key's second sign that it is on, beside its fill: a bar in the accent
 * along its foot. The fill alone differed from an off key's by 1.04 to 1.33
 * to 1, under any theme -- not a difference most eyes can rely on.
 */
private fun Modifier.onBar(colour: Color): Modifier = drawBehind {
    val bar = 3.dp.toPx()
    drawRect(colour, topLeft = Offset(size.width * 0.2f, size.height - bar - 2.dp.toPx()),
             size = Size(size.width * 0.6f, bar))
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
            .then(if (on) Modifier.onBar(palette.accent) else Modifier)
            // One of three, as a radio button is: said selected or not.
            .selectable(selected = on, enabled = enabled, role = Role.RadioButton, onClick = onClick)
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

/** A pending style's name, as the format bar says it. */
private fun styleName(style: String): Int = when (style) {
    "bold" -> R.string.style_bold
    "italic" -> R.string.style_italic
    "underline" -> R.string.style_underline
    else -> R.string.style_strike
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
    // On and off, as B is: said as a checkbox is, ticked or not. A key that
    // opens a choice (A, 🖍, Aa, the size) says, when on, that the selection
    // uses it.
    toggle: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val usedSaid = stringResource(R.string.key_used)
    Box(
        Modifier
            .then(if (wide) Modifier.height(42.dp).widthIn(min = 64.dp) else Modifier.size(42.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) palette.calendarHasNotes else palette.card)
            .then(if (on) Modifier.onBar(palette.accent) else Modifier)
            .then(if (toggle) {
                Modifier.toggleable(value = on, enabled = enabled, role = Role.Checkbox) { onClick() }
            } else {
                Modifier.clickable(enabled = enabled, onClick = onClick)
                    .then(if (on) Modifier.semantics { stateDescription = usedSaid } else Modifier)
            })
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

/** "… N more rows here", in a long checklist's gap: [onShow] draws the next of them. */
@Composable
internal fun HiddenRowsLink(hidden: Int, onShow: () -> Unit) {
    Text(stringResource(R.string.checklist_rows_hidden, hidden, minOf(hidden, Editing.CHECKLIST_ROWS)),
         color = LocalPalette.current.accentText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
         modifier = Modifier.fillMaxWidth().clickable(onClick = onShow).padding(vertical = 10.dp))
}

/**
 * The checklist, under the text, when [shown]. Ticking, adding, renaming
 * and removing each write at once, as the desktop's do; the handle ⠿ drags
 * a row to another place. [focusAsk] counts the times ⋮ asked for it: the
 * keyboard then comes up in "A new item…", ready for the first row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistSection(state: AppState, model: EditorModel, shown: Boolean, focusAsk: Int,
                             found: List<Finding.InChecklist> = emptyList(), current: Finding.InChecklist? = null,
                             finding: Boolean = false, reveal: Int = 0) {
    val palette = LocalPalette.current
    val note = state.note(model.id)
    val items = note?.checklist ?: emptyList()
    // The new row's box whole above the keyboard, Add included, asked again at
    // each height the keyboard passes as it rises: asked once, the screen had
    // not shrunk yet (ISSUES_LOG, "A field that looked for its cursor once").
    val addRow = remember { BringIntoViewRequester() }
    var addFocused by remember { mutableStateOf(false) }
    val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(addFocused, keyboardHeight) {
        if (addFocused && keyboardHeight > 0) addRow.bringIntoView()
    }
    // Where the box and its Add stand while typed in, for the window's
    // messages to be drawn above them, never over them ([AppState.editorFootTop]).
    var addTop by remember { mutableStateOf<Float?>(null) }
    // Asked of each model: the same note built anew on another day keeps the
    // box focused where it was, and messages were drawn over it.
    LaunchedEffect(model, addFocused, addTop) { model.addBoxTop = if (addFocused) addTop else null }
    DisposableEffect(model) { onDispose { model.addBoxTop = null } }
    // And again after each row added, once it is laid out: the new row
    // pushed the box down a row at a time, under the keys, and the newest
    // item was out of sight with it.
    var added by remember { mutableIntStateOf(0) }
    LaunchedEffect(added) {
        if (added == 0) return@LaunchedEffect
        withFrameNanos { }
        addRow.bringIntoView()
    }
    val addFocus = remember { FocusRequester() }

    // A row being renamed is written before the rows move under it: kept by
    // its place, it would land on another row, or be dropped.
    fun renameWritten(): Boolean = model.writeRename()?.let { state.say(it); false } ?: true

    // The last ask from ⋮ the box was given: kept while the section is folded,
    // so a section opened again does not raise the keyboard for an old ask.
    var answered by remember { mutableIntStateOf(0) }
    if (!shown) return
    val foldKey = "${model.id}:checklist"
    // ⋮ asking for the checklist opens it, folded or not.
    LaunchedEffect(focusAsk) { if (focusAsk > answered) state.folded.remove(foldKey) }
    // Shown while a search is open, as the text is: its words are found too.
    val folded = state.folded[foldKey] == true && !finding
    // On one day of a series the checklist is the series': a tick here is
    // a tick on every day, and the label says so, as the desktop's does.
    FoldLabel(stringResource(R.string.checklist_label, items.count { it.done }, items.size) +
              if (model.day != null) " " + stringResource(R.string.whole_series_mark) else "", folded) {
        state.folded[foldKey] = !folded
    }
    if (folded) return
    var dragging by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    val tickedSaid = stringResource(R.string.checklist_ticked)
    val notTickedSaid = stringResource(R.string.checklist_not_ticked)
    val upSaid = stringResource(R.string.move_up)
    val downSaid = stringResource(R.string.move_down)
    val nowhereSaid = stringResource(R.string.move_nowhere)
    // The row whose handle was tapped: its Move up and Move down.
    var rowMenu by remember { mutableIntStateOf(-1) }
    // Which rows are written in as fields, the rest shown short
    // (Editing.checklistFields); and a search's matches, row by row, worked
    // out once a search, not at every letter typed into a row.
    val writable = remember(items) { Editing.checklistFields(items.map { it.text }) }
    val firstShort = remember(writable, items) {
        items.indices.firstOrNull { !writable[it] && Editing.rowChars(items[it].text) <= Editing.CHECKLIST_ROW_CHARS } ?: -1
    }
    val foundByRow = remember(found) { found.groupBy({ it.item }, { it.start to it.end }) }
    // The rows drawn: the first ones and the last few, where a new item
    // lands, a search's row among them; "Show" draws more (Editing.checklistShown).
    var head by remember(model.id) { mutableIntStateOf(Editing.CHECKLIST_ROWS) }
    var tail by remember(model.id) { mutableIntStateOf(Editing.CHECKLIST_TAIL_ROWS) }
    val reach = current?.item ?: -1
    val shownRows = remember(items.size, head, tail, reach) { Editing.checklistShown(items.size, head, reach, tail) }
    // Each row drawn under a key of its words, so a moved row takes its focus
    // along; kept by place, the focus stayed on the row a move put there, and
    // a screen reader's next Move moved a row nobody chose.
    val rowKeys = remember(items) { rowKeys(items.map { it.text }) }
    // Where each drawn row lies, by its key: what a drag is dropped on.
    val placed = remember(model.id) { HashMap<Any, LayoutCoordinates>() }

    // A row moved from [from] to [to] -- by its handle's menu, a screen
    // reader's or Switch Access's actions, or a drag -- and still drawn
    // after it, the stretch it went into shown (Editing.keepMovedInSight).
    fun move(from: Int, to: Int) {
        val size = state.note(model.id)?.checklist?.size ?: return
        if (to !in 0 until size || to == from || !renameWritten()) return
        if (state.report(Notebook.moveChecklistItem(state.load, model.id, from, to))) {
            val (h, t) = Editing.keepMovedInSight(size, head, tail, reach, from, to)
            head = h
            tail = t
        }
    }

    // Where a row dragged from [from] by [by] pixels lands: on the drawn row
    // its middle came nearest (Editing.dropTarget).
    fun dropped(from: Int, by: Float): Int {
        val middles = HashMap<Int, Float>()
        for (i in shownRows) {
            val at = placed[rowKeys.getOrNull(i) ?: continue]?.takeIf { it.isAttached } ?: continue
            middles[i] = at.positionInRoot().y + at.size.height / 2f
        }
        val own = middles[from] ?: return from
        return Editing.dropTarget(middles, own + by, from)
    }

    var drawnUpTo = -1
    for (index in shownRows) key(rowKeys[index]) {
        val item = items[index]
        if (index > drawnUpTo + 1) {
            val upTo = drawnUpTo
            HiddenRowsLink(index - drawnUpTo - 1) { head = upTo + 1 + Editing.CHECKLIST_ROWS }
        }
        drawnUpTo = index
        if (index == firstShort) {
            Text(stringResource(R.string.checklist_shown_short, Editing.CHECKLIST_CHARS), color = palette.subtext,
                 fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }
        // The row's spoken name, as the template editor's: a glimpse, never
        // the whole of a row a backup can bring at 150,000 letters, three
        // times over for each row.
        val rowName = glimpse(item.text)
        val moveLabel = stringResource(R.string.drag_to_move, rowName)
        val rowSaid = stringResource(R.string.checklist_row_said, rowName)
        val removeSaid = stringResource(R.string.checklist_remove_said, rowName)
        // The row the current match is in, brought into sight as the bar steps.
        val sight = remember { BringIntoViewRequester() }
        val here = current?.takeIf { it.item == index }
        // A row shown, not written in, brings the part its word is in into
        // sight itself (ReadOnlyRow): brought whole, a row taller than the
        // screen showed its top.
        val readOnly = Editing.rowTooLong(item.text) || !writable[index]
        if (here != null && !readOnly) LaunchedEffect(reveal, here) { sight.bringIntoView() }
        val rowKey = rowKeys[index]
        // Where the row stands while it is renamed, for the window's messages
        // to stand above it ([EditorModel.renamingRow]); let go with the row.
        var rowFocused by remember { mutableStateOf(false) }
        fun renamed(on: Boolean) {
            rowFocused = on
            val at = placed[rowKey]?.takeIf { it.isAttached }?.boundsInWindow()?.top
            if (on && at != null) model.renamingRow = rowKey to at
            else if (model.renamingRow?.first == rowKey) model.renamingRow = null
        }
        DisposableEffect(rowKey) { onDispose { renamed(false) } }
        // Told again to a model built anew on another day: the row keeps its
        // focus, and the new model knew nothing of it.
        LaunchedEffect(model) { if (rowFocused) renamed(true) }
        Row(
            Modifier
                .fillMaxWidth()
                // Where the row lies, before a drag moves its drawing.
                .onPlaced { placed[rowKey] = it }
                .onGloballyPositioned { if (model.renamingRow?.first == rowKey) renamed(true) }
                .graphicsLayer { translationY = if (dragging == index) offset else 0f }
                // A finger's height at least; a row shown to be read is as
                // tall as what it shows. Held to 44 dp, a row's read-only
                // block was cut to the start of its first line.
                .heightIn(min = 44.dp)
                .bringIntoViewRequester(sight),
            // A row read whole keeps its handle, box and ✕ beside its start.
            verticalAlignment = if (readOnly) Alignment.Top else Alignment.CenterVertically,
        ) {
            // The handle drags at once, a finger wide and apart from the box (the
            // owner's call); a tap or a screen reader's actions step one place
            // (ISSUES_LOG, "Arranging only a finger could do").
            fun step(by: Int) = move(index, index + by)
            Box { Text(
                "⠿",
                color = palette.subtext,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(width = 40.dp, height = 44.dp)
                    .wrapContentHeight()
                    .semantics {
                        contentDescription = moveLabel
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(upSaid) { step(-1); true }.takeIf { index > 0 },
                            CustomAccessibilityAction(downSaid) { step(1); true }.takeIf { index < items.size - 1 },
                        )
                    }
                    // A menu only when the row can go somewhere: the only
                    // row opened an empty one, a dead end for a screen reader.
                    .clickable { if (items.size > 1) rowMenu = index else state.say(nowhereSaid) }
                    .pointerInput(index, shownRows, rowKeys) {
                        detectDragGestures(
                            onDragStart = { dragging = index; offset = 0f },
                            onDragEnd = {
                                val to = dropped(index, offset)
                                dragging = -1
                                offset = 0f
                                move(index, to)
                            },
                            onDragCancel = { dragging = -1; offset = 0f },
                            onDrag = { change, amount -> change.consume(); offset += amount.y },
                        )
                    },
            )
            DropdownMenu(expanded = rowMenu == index, onDismissRequest = { rowMenu = -1 }) {
                if (index > 0) DropdownMenuItem(text = { Text(upSaid) }, onClick = { rowMenu = -1; step(-1) })
                if (index < items.size - 1) {
                    DropdownMenuItem(text = { Text(downSaid) }, onClick = { rowMenu = -1; step(1) })
                }
            } }
            Text(
                if (item.done) "☑" else "☐",
                color = if (item.done) palette.successText else palette.text,
                fontSize = 20.sp,
                modifier = Modifier
                    // A box a screen reader can name: the row's words, ticked
                    // or not, a checkbox -- not the symbol's name alone.
                    .semantics {
                        contentDescription = rowSaid
                        stateDescription = if (item.done) tickedSaid else notTickedSaid
                        role = Role.Checkbox
                    }
                    .clickable { state.report(Notebook.setChecklistDone(state.load, model.id, index, !item.done)) }
                    .padding(end = 10.dp),
            )
            val rowFound = foundByRow[index].orEmpty()
            val rowCurrent = here?.let { it.start to it.end }
            // A row too long to write in is shown, not edited, as the body's long
            // lines are, and past what the list draws a row is shown short; either
            // is read whole on asking, and a search opens it.
            if (readOnly) {
                ReadOnlyRow(item.text, item.done, rowFound, rowCurrent, reveal, Modifier.weight(1f))
            } else ChecklistText(model.waitingIn(index, item.text) ?: item.text, item.done,
                          found = rowFound, current = rowCurrent,
                          onEdit = { was, now, cursor ->
                              // Kept to a row's length, the row's own words whole.
                              model.rowCap.fit(was, now, box = index, cursor = cursor).also { fit ->
                                  if (fit.text != was) model.typeInRow(index, fit.text)?.let { state.say(it) }
                              }
                          },
                          onLeave = { if (model.renaming?.index == index) renameWritten() },
                          onFocus = { renamed(it) })
            Text(
                "✕",
                color = palette.subtext,
                fontSize = 14.sp,
                modifier = Modifier
                    .semantics { contentDescription = removeSaid }
                    .clickable { if (renameWritten()) state.removeChecklistRow(model.id, index) }
                    .padding(8.dp),
            )
        }
    }
    // The keyboard's ✓ adds the row as Add does, the keyboard left up for the
    // next -- the desktop's Enter in its entry. It only closed the keyboard,
    // the words left in the box and the list still empty.
    fun add() {
        if (model.adding.isBlank()) return
        model.writeChecklist()?.let { state.say(it); return }
        added += 1
    }
    // The box's words with their cursor, so a paste cut to fit leaves it
    // after what came in ([fittedValue]); the words themselves are the model's.
    var addField by remember { mutableStateOf(TextFieldValue(model.adding, TextRange(model.adding.length))) }
    val addShown = if (addField.text == model.adding) addField
                   else TextFieldValue(model.adding, TextRange(model.adding.length))
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp).bringIntoViewRequester(addRow)
            .onGloballyPositioned { addTop = it.boundsInWindow().top }) {
        BasicTextField(
            value = addShown,
            // At most what a row is written in at, as the desktop's box takes:
            // the words already in it whole, what comes in cut, said once.
            onValueChange = { next ->
                if (next.text == addShown.text) {
                    addField = next
                } else {
                    addField = keptBy(model.rowCap, addShown, next, box = Editing.NEW_ROW_BOX)
                    model.typeAdding(addField.text)
                }
            },
            singleLine = true,
            textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
            cursorBrush = SolidColor(palette.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences,
                                              imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(addFocus)
                .onFocusChanged { addFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(palette.card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            decorationBox = { field ->
                if (model.adding.isEmpty()) Text(stringResource(R.string.checklist_new), color = palette.subtext, fontSize = 15.sp)
                field()
            },
        )
        Text(
            stringResource(R.string.add),
            color = if (model.adding.isBlank()) palette.subtext else palette.accentText,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clickable(enabled = model.adding.isNotBlank()) { add() }
                .padding(12.dp),
        )
    }
    // Run once the box is there to take it: asked for from ⋮.
    LaunchedEffect(focusAsk) {
        if (focusAsk > answered) {
            answered = focusAsk
            addFocus.requestFocus()
        }
    }
}

/** How much of a table too large to draw is shown: its first lines, each cut to a phone's worth. */
private const val LARGE_TABLE_LINES = 40
private const val LARGE_TABLE_CHARS = 300

/** How many of a too-long text's lines are shown, each cut as a large table's are. */
private const val LONG_TEXT_LINES = 3

/**
 * Text holding a run too long to lay out ([Editing.MAX_UNBROKEN]) -- of a
 * body, a title, comments or a row -- shown as what it is and its start,
 * read-only, each line slid sideways, never wrapped letter by letter; the
 * note keeps it whole and the desktop shows it. A [heavy] one is more than
 * is drawn here at once ([Editing.Segment.heavy]), and says so. Either way,
 * how many lines it holds past those shown.
 */
@Composable
internal fun LongText(text: String, heavy: Boolean = remember(text) { !Editing.runsTooLong(text) },
                      found: List<Pair<Int, Int>> = emptyList(), current: Pair<Int, Int>? = null, reveal: Int = 0) {
    val palette = LocalPalette.current
    val lines = remember(text) { text.split("\n") }
    val length = remember(text) { text.codePointCount(0, text.length) }
    ReadOnlyBlock(lines, LONG_TEXT_LINES, found, current, reveal) {
        Text(if (heavy) stringResource(R.string.text_too_much, length, lines.size)
             else stringResource(R.string.text_too_long, length), color = palette.subtext, fontSize = 13.sp)
    }
}

/** How many parts of a row a reading of it shows first: a desktop row of 2,000 letters, whole. */
private const val ROW_PARTS_SHOWN = 7

/**
 * A checklist row shown, not written in (too long, past what the list draws
 * to write in, or in the template editor): its start and "Read all N
 * characters ›"; read, the whole row in parts a phone's width wraps
 * ([Editing.rowParts]), and a search's stop opens it on its part, painted
 * (ISSUES_LOG, "A cap that cut what it was meant to keep").
 */
@Composable
internal fun ReadOnlyRow(text: String, done: Boolean, found: List<Pair<Int, Int>> = emptyList(),
                         current: Pair<Int, Int>? = null, reveal: Int = 0, modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    var read by remember(text) { mutableStateOf(false) }
    val length = remember(text) { Editing.rowChars(text) }
    Column(modifier.padding(vertical = 4.dp)) {
        if (!read && current == null) {
            Text(paintRanges(AnnotatedString(drawnRow(text)), found, null, palette),
                 color = if (done) palette.subtext else palette.text, fontSize = 15.sp, maxLines = 1,
                 overflow = TextOverflow.Ellipsis, textDecoration = if (done) TextDecoration.LineThrough else null)
            Text(stringResource(R.string.row_read, length), color = palette.accentText, fontSize = 13.sp,
                 fontWeight = FontWeight.Medium, modifier = Modifier.clickable { read = true }.padding(vertical = 4.dp))
            return@Column
        }
        val parts = remember(text) { Editing.rowParts(text) }
        val lines = remember(parts) { parts.map { (from, to) -> text.substring(from, to) } }
        val starts = remember(parts) { IntArray(parts.size) { parts[it].first } }
        ReadOnlyBlock(lines, ROW_PARTS_SHOWN, found, current, reveal, parts = starts) {
            Text(stringResource(R.string.row_read_header, length), color = palette.subtext, fontSize = 13.sp)
        }
        if (read) {
            Text(stringResource(R.string.row_read_less), color = palette.accentText, fontSize = 13.sp,
                 fontWeight = FontWeight.Medium, modifier = Modifier.clickable { read = false }.padding(vertical = 4.dp))
        }
    }
}

/**
 * What a box kept to a length by [cap] shows after [next]: a move of the
 * cursor as it is, an edit as the cap keeps it ([fittedValue]) -- what came
 * in read up to the cursor [next] leaves ([cursorOf]): every capped box, the
 * title, a row, "A new item…", a template's name and title, the person's
 * name, is kept this one way.
 */
internal fun keptBy(cap: Editing.BoxCap, shown: TextFieldValue, next: TextFieldValue, box: Any = Unit): TextFieldValue =
    if (next.text == shown.text) next else fittedValue(shown, next, cap.fit(shown.text, next.text, box, cursorOf(next)))

/** Where a field leaves its cursor after an edit; null while words are selected. */
internal fun cursorOf(value: TextFieldValue): Int? = value.selection.end.takeIf { value.selection.collapsed }

/**
 * A box's next value under its cap ([Editing.fitRow]): [next] as typed
 * when it fits; what [fit] kept, the cursor after what came in, when it was
 * cut; the box as it was, [shown], when nothing could come in.
 */
internal fun fittedValue(shown: TextFieldValue, next: TextFieldValue, fit: Editing.Fit): TextFieldValue = when {
    fit.text == next.text -> next
    fit.caret != null -> TextFieldValue(fit.text, TextRange(fit.caret))
    else -> shown
}

/** Each checklist row's key: its words, and how many rows before it read the same -- one per row, moving with it. */
internal fun rowKeys(texts: List<String>): List<Pair<String, Int>> {
    val seen = HashMap<String, Int>()
    return texts.map { text -> text to (seen.merge(text, 1, Int::plus) ?: 1) }
}

/** How many lines a read-only piece shows at a time once the person reads on. */
private const val READ_ON_LINES = 40

/**
 * A piece shown read-only, under its [header]: its first [shown] lines, each
 * slid sideways, cut to a phone's worth, then [READ_ON_LINES] at a time. A
 * search's matches are painted, and the one stepped to brings its line into
 * sight, so the part past what the phone draws is read and found. Given a
 * row's [parts] ([ReadOnlyRow]), each is shown whole, wrapped.
 */
@Composable
private fun ReadOnlyBlock(lines: List<String>, shown: Int, found: List<Pair<Int, Int>>, current: Pair<Int, Int>?,
                          reveal: Int, parts: IntArray? = null, header: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val starts = remember(lines, parts) { parts ?: Finding.lineStartsUtf16(lines) }
    var first by remember(lines) { mutableIntStateOf(0) }
    var count by remember(lines) { mutableIntStateOf(shown) }
    val sight = remember { BringIntoViewRequester() }
    // The part a search stopped in, itself: a row read in parts is taller
    // than the screen, and brought into sight whole, its top was shown and
    // the word lay under the screen's edge (the walk, 2026-10-29).
    val partSight = remember { BringIntoViewRequester() }
    val matchLine = current?.let { Finding.lineAt(starts, it.first) }
    LaunchedEffect(reveal, matchLine) {
        if (matchLine == null) return@LaunchedEffect
        if (matchLine !in first until first + count) first = maxOf(0, matchLine - 1)
        withFrameNanos { }
        if (parts != null) partSight.bringIntoView() else sight.bringIntoView()
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).bringIntoViewRequester(sight)) {
        header()
        Column((if (parts == null) Modifier.horizontalScroll(rememberScrollState()) else Modifier).padding(top = 6.dp)) {
            for (i in first until minOf(lines.size, first + count)) {
                val line = lines[i]
                val start = starts[i]
                val at = current?.first?.takeIf { it in start..start + line.length }?.minus(start)
                val cut = if (parts != null) Finding.Cut(0, line.length) else Finding.cutAround(line, LARGE_TABLE_CHARS, at)
                // The matches on the part shown, counted from where it is cut.
                fun local(range: Pair<Int, Int>): Pair<Int, Int>? {
                    val a = maxOf(range.first - start, cut.from)
                    val b = minOf(range.second - start, cut.to)
                    return if (b > a) (a - cut.from) to (b - cut.from) else null
                }
                val painted = paintRanges(AnnotatedString(line.substring(cut.from, cut.to)),
                                          found.mapNotNull { local(it) }, current?.let { local(it) }, palette)
                val drawn = buildAnnotatedString {
                    if (cut.from > 0) append("…")
                    append(painted)
                    if (cut.to < line.length) append("…")
                }
                if (parts != null) Text(drawn, color = palette.text, fontSize = 15.sp,
                                        modifier = if (i == matchLine) Modifier.bringIntoViewRequester(partSight) else Modifier)
                else Text(drawn, color = palette.text, fontSize = 13.sp, maxLines = 1, softWrap = false)
            }
        }
        // Three lines shown of five hundred said nothing of the rest.
        if (lines.size > first + count || first > 0) {
            Text(if (parts != null) stringResource(R.string.row_parts, first + 1, minOf(lines.size, first + count), lines.size)
                 else if (first == 0) stringResource(R.string.table_more_lines, lines.size - count)
                 else stringResource(R.string.read_only_lines, first + 1, minOf(lines.size, first + count), lines.size),
                 color = palette.subtext, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (first > 0) {
                    Text(stringResource(R.string.read_only_earlier), color = palette.accentText, fontSize = 13.sp,
                         fontWeight = FontWeight.Medium,
                         modifier = Modifier.clickable { first = maxOf(0, first - READ_ON_LINES); count = READ_ON_LINES }
                             .padding(vertical = 6.dp))
                }
                if (first + count < lines.size) {
                    Text(stringResource(R.string.read_only_later), color = palette.accentText, fontSize = 13.sp,
                         fontWeight = FontWeight.Medium,
                         modifier = Modifier.clickable { first += count; count = READ_ON_LINES }.padding(vertical = 6.dp))
                }
            }
        }
    }
}

/**
 * A table too large to draw as a grid ([Editing.MAX_DRAWN_CELLS]): what it
 * is, and its first lines as written, read-only, each on one line the
 * person can slide sideways. The note keeps the table whole; only the
 * drawing is short, so the note opens at once. The template editor shows
 * one the same way: it drew the grid whatever its size, and a template saved
 * from such a note froze the app as it opened.
 */
@Composable
internal fun LargeTable(segment: Editing.Segment, found: List<Pair<Int, Int>> = emptyList(),
                        current: Pair<Int, Int>? = null, reveal: Int = 0) {
    val palette = LocalPalette.current
    val lines = remember(segment.text) { segment.text.split("\n") }
    // As the grid would be drawn, which is what made it too large -- or,
    // under the line, the tables above it, which drew what a note may.
    val cells = remember(segment.text) { Editing.gridCells(lines) }
    ReadOnlyBlock(lines, LARGE_TABLE_LINES, found, current, reveal) {
        Text(if (cells > Editing.MAX_DRAWN_CELLS) stringResource(R.string.table_too_large, cells, lines.size)
             else stringResource(R.string.table_past_others, cells, lines.size, Editing.MAX_DRAWN_CELLS),
             color = palette.subtext, fontSize = 13.sp)
    }
}

/**
 * A row's words. What is typed waits in the editor ([EditorModel.renaming]),
 * written when the field is left, by the keyboard's ✓ or by the save on
 * leaving: one write per edit, not per letter. [onEdit] is asked of each
 * change, with the cursor the field leaves ([cursorOf]), and answers what is
 * kept ([Editing.fitRow]); a paste cut to fit leaves the cursor after it.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.ChecklistText(
    text: String, done: Boolean, found: List<Pair<Int, Int>> = emptyList(), current: Pair<Int, Int>? = null,
    onEdit: (was: String, now: String, cursor: Int?) -> Editing.Fit, onLeave: () -> Unit,
    onFocus: (Boolean) -> Unit = {},
) {
    val palette = LocalPalette.current
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val shown = if (field.text == text) field else TextFieldValue(text, TextRange(text.length))
    var focused by remember { mutableStateOf(false) }
    // A search's stop in a row not being typed in: the field, one line,
    // slides to show it -- it showed its start, and a word past it was
    // stepped to out of sight.
    LaunchedEffect(current) {
        if (current != null && !focused && current.second <= shown.text.length) {
            field = shown.copy(selection = TextRange(current.second))
        }
    }
    BasicTextField(
        value = shown,
        onValueChange = { next ->
            field = if (next.text == shown.text) next else fittedValue(shown, next, onEdit(shown.text, next.text, cursorOf(next)))
        },
        singleLine = true,
        // ✓ writes the words and puts the keyboard down. It only put the
        // keyboard down: the words stayed in the field, looking kept.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            onLeave()
            defaultKeyboardAction(ImeAction.Done)
        }),
        // A search's matches painted on the words, as the note's own are.
        visualTransformation = { words -> TransformedText(paintRanges(words, found, current, palette), OffsetMapping.Identity) },
        textStyle = TextStyle(
            color = if (done) palette.subtext else palette.text,
            fontSize = 15.sp,
            textDecoration = if (done) TextDecoration.LineThrough else null,
        ),
        cursorBrush = SolidColor(palette.accent),
        modifier = Modifier
            .weight(1f)
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
                if (!it.isFocused) onLeave()
            },
    )
}

/**
 * Comments: plain text under the note, saved with it, when [shown]. Asked
 * for from ⋮ ([focusAsk]), the cursor goes into them.
 */
@Composable
private fun CommentsSection(state: AppState, model: EditorModel, shown: Boolean, focusAsk: Int,
                            found: List<Finding.InComments> = emptyList(), current: Finding.InComments? = null,
                            finding: Boolean = false, reveal: Int = 0) {
    val palette = LocalPalette.current
    // As the checklist's: the last ask from ⋮ answered, kept while folded.
    var answered by remember { mutableIntStateOf(0) }
    if (!shown) return
    val foldKey = "${model.id}:comments"
    LaunchedEffect(focusAsk) { if (focusAsk > answered) state.folded.remove(foldKey) }
    // Shown while a search is open, as the text is: its words are found too.
    val folded = state.folded[foldKey] == true && !finding
    FoldLabel(stringResource(R.string.comments_label), folded) { state.folded[foldKey] = !folded }
    if (folded) return
    val following = rememberFollowing()
    val typing = remember { FocusRequester() }
    LaunchedEffect(focusAsk) {
        if (focusAsk > answered) {
            answered = focusAsk
            typing.requestFocus()
        }
    }
    // The current match brought into sight as the bar steps, its own line.
    if (current != null) LaunchedEffect(reveal, current) {
        val laid = following.layout
        following.requester.bringIntoView(
            if (laid != null && current.start < laid.layoutInput.text.length) laid.getBoundingBox(current.start) else null)
    }
    val spots = found.map { it.start to it.end }
    // Comments too long to lay out are shown, not edited, as the body's lines
    // are -- and searched as those are, the line of the match shown.
    if (Editing.tooLongToLayOut(model.comments)) {
        LongText(model.comments, found = spots, current = current?.let { it.start to it.end }, reveal = reveal)
    } else BasicTextField(
        value = model.comments,
        onValueChange = { given ->
            // A CR that came in is one line break, as the desktop's paste puts it.
            val now = Editing.breaksCame(model.comments, given)?.first ?: given
            // A table pasted here showed as rows of bars: the comments draw no table.
            val kept = Pasting.commentsAfter(model.comments, now)
            following.typed(model.comments, kept ?: now)
            model.comments = kept ?: now
            if (kept != null) model.say("Comments keep text only — the table wasn't added")
            model.changed(Editing.History.TYPING + ":comments")
        },
        onTextLayout = following::laidOut,
        // A search's matches painted on the words, as the note's own are.
        visualTransformation = { words ->
            TransformedText(paintRanges(words, spots, current?.let { it.start to it.end }, palette), OffsetMapping.Identity)
        },
        textStyle = TextStyle(color = palette.text, fontSize = 15.sp, lineHeight = 22.sp),
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .focusRequester(typing)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.card)
            .padding(12.dp)
            .bringIntoViewRequester(following.requester),
    )
}

/**
 * The note's attached files, each opened with the phone's own apps, taken
 * off with ✕ -- which asks first, as the desktop does, the stored copy
 * going with it -- and more added from Android's own file picker, which
 * ⋮'s Attach files opens ([pickAsk] counts the times). A day of a series
 * shows the series' files and adds none: they are never a day's.
 */
@Composable
private fun AttachmentsSection(state: AppState, model: EditorModel, pickAsk: Int) {
    val palette = LocalPalette.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val files = if (model.id == 0) emptyList() else state.attachmentsOf(model.id)
    var removing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var tooMany by remember { mutableStateOf<Pair<List<android.net.Uri>, Int>?>(null) }
    val fullText = stringResource(R.string.attach_full, Normalize.MAX_ATTACHMENTS)

    fun attach(uris: List<android.net.Uri>) = attachPicked(state, context, model.id, uris)

    // The picker is AppRoot's (AppState.pickAttachments); while this note
    // is open, its answer comes here.
    DisposableEffect(model) {
        state.attachHere = handler@{ id, uris ->
            // Asked for on this note new: saved meanwhile, as the app went
            // behind the picker, it read as another note's, and the files
            // were said lost.
            if (id != model.id && !(id == 0 && model.startedNew)) return@handler false
            // A new note is saved first: only a saved note can hold files.
            if (!model.save(force = model.id == 0)) return@handler true
            val room = Organizing.roomFor(state.load, model.id)
            when {
                room == 0 -> state.say(fullText)
                uris.size > room -> tooMany = uris to room
                else -> attach(uris)
            }
            true
        }
        onDispose { state.attachHere = null }
    }
    LaunchedEffect(pickAsk) { if (pickAsk > 0 && model.day == null) state.pickAttachments?.invoke(model.id) }

    if (files.isNotEmpty()) SectionLabel(stringResource(R.string.files_label, files.size))
    for ((name, stored) in files) {
        // Drawn short, its type kept: a backup can label a file with
        // 150,000 letters, and the row laid them all out at every opening.
        val shown = quotedFile(name)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📎  $shown",
                color = palette.accentText,
                fontSize = 15.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable { state.openFile?.invoke(File(state.attachments, stored), name) }
                    .padding(vertical = 8.dp),
            )
            if (model.day == null) {
                val removeLabel = stringResource(R.string.attach_remove_said, shown)
                Text("✕", color = palette.subtext, fontSize = 14.sp,
                     modifier = Modifier
                         .semantics { contentDescription = removeLabel }
                         .clickable { removing = name to stored }
                         .padding(10.dp))
            }
        }
    }
    removing?.let { (name, stored) ->
        Ask(
            title = stringResource(R.string.attach_remove_title),
            text = stringResource(R.string.attach_remove_text, quotedFile(name)),
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
 * Picked files attached to the note [id]: copied off the picker's stream
 * into the app's cache first, away from the screen's thread, and moved into
 * place by the write. In the app's own scope, not the editor's: Back while
 * a large file was copying ended the copy's scope, the files were never
 * attached, nothing was said, and the copy stayed in the cache.
 */
fun attachPicked(state: AppState, context: android.content.Context, id: Int, uris: List<android.net.Uri>,
                 then: (() -> Unit)? = null) {
    // The note they are for, by its uid too, taken now: the copy can outlive
    // the editor, and the id be another note's by the time it ends.
    val uid = state.rawNote(id)?.opt("uid") as? String
    state.scope.launch {
        val copied = mutableListOf<Pair<String, File>>()
        try {
            withContext(kotlinx.coroutines.Dispatchers.IO) {
                uris.forEach { uri -> copyToCache(context, uri)?.let { copied.add(it) } }
            }
            state.attach(id, copied.map { (name, temp) ->
                Organizing.Incoming(name) { dest -> if (!temp.renameTo(dest)) { temp.copyTo(dest); temp.delete() } }
            }, unreadable = uris.size - copied.size, uid = uid)
            then?.invoke()
        } finally {
            copied.forEach { (_, temp) -> temp.delete() }
        }
    }
}

/**
 * Picked files with no editor open for them: Android ended the app behind
 * the picker, and started it again on its list. Attached to the note
 * still, as many as it has room for, and said so -- the note is not on the
 * screen to show them; a new note never saved has nowhere to keep them,
 * and says that instead.
 */
fun attachAfterReturn(state: AppState, context: android.content.Context, id: Int, uris: List<android.net.Uri>) {
    val note = state.rawNote(id)
    if (id == 0 || note == null) {
        state.say(context.getString(R.string.attach_lost))
        return
    }
    val room = Organizing.roomFor(state.load, id)
    if (room == 0) {
        state.say(context.getString(R.string.attach_full, Normalize.MAX_ATTACHMENTS))
        return
    }
    val before = state.attachmentsOf(id).size
    attachPicked(state, context, id, uris.take(room)) {
        val added = state.attachmentsOf(id).size - before
        when {
            uris.size > room -> state.say(context.getString(R.string.attach_cut, room, Normalize.MAX_ATTACHMENTS))
            added > 0 -> state.say(context.resources.getQuantityString(
                R.plurals.attached_after_return, added, added, quoted(Exporting.titleOf(note))))
        }
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
    // The Repeat sheet, opened from here, says what a turn-off kept while this stays open.
    AppSheet(onDismissRequest = onDismiss, messages = state.snackbar) {
        // Scrolled, on a phone on its side: taller than the window there, its
        // foot was cut away -- the word count, on a repeating note Pin too --
        // and the message under it had no room.
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text(stringResource(R.string.note_details), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            // On one day, its own category and colour: said, as the desktop
            // puts "This day:" beside them.
            DetailRow(stringResource(if (onADay) R.string.this_day_category else R.string.category)) {
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
            Text(stringResource(if (onADay) R.string.this_day_colour else R.string.colour), color = palette.subtext, fontSize = 14.sp,
                 modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
            Swatches(Themes.PRESET_NOTE_COLORS, model.color, allowNone = true) { model.color = it; model.changed() }
            // The pin is the whole note's: a day has nowhere to keep one.
            if (!onADay) {
                DetailRow(stringResource(R.string.pin_to_top)) {
                    Switch(checked = model.pinned, onCheckedChange = { model.pinned = it; model.changed() })
                }
            }
            val words = Editing.wordCount(model.body)
            Text(
                listOf(pluralStringResource(R.plurals.note_words, words, words), Editing.readingTime(words))
                    .filter { it.isNotEmpty() }.joinToString(" · "),
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

@Composable
internal fun DetailRow(label: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    // The value right after its label, in a column the rows share: the label
    // took the whole width and threw the value to the far edge, and reading
    // a sheet was a slalom from side to side (the owner, 2026-10-25).
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = palette.subtext, fontSize = 14.sp,
             modifier = Modifier.widthIn(min = 104.dp).padding(end = 12.dp))
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
    val title = quoted((shown.opt("title") as? String)?.trim()?.ifEmpty { null } ?: "Untitled")
    val text = contentText(shown.opt("body") as? String ?: "").split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ").let { val cut = Editing.firstLetters(it, 160); if (cut != it) cut.trimEnd() + "…" else it }
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
                                if (text.isEmpty()) "" else "\n$text", Recurrence.describe(raw),
                                state.trashWindow?.let { stringResource(R.string.off_schedule_trash_days, it) }
                                    ?: stringResource(R.string.off_schedule_trash_kept)))
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
    // Every year the desktop reads, 1 to 9999: by the picker's 1900 to 2100, a
    // note dated 1026 stopped the app. Today is the phone's own, at UTC midnight,
    // as the picker reads days: the moment itself opened on the wrong day.
    val today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial ?: today,
                                         yearRange = 1..9999)
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
        null -> cell.lines.joinToString(LINE_BREAK)
    }
}

// ---- A table, written in place ----
// The owner's way: a tap on a cell writes in it; a bar of one-word buttons over
// the keyboard, the rarer things in More, not one wall of twenty-one buttons.

/**
 * A cell tapped: chosen to write in -- or, a checkbox, ticked or unticked
 * there and then, as a box is, and chosen too, so the bar is at hand.
 */
private fun chooseCell(model: EditorModel, segment: Editing.Segment, table: TableBlock, at: CellAt) {
    val cell = table.rows.getOrNull(at.row)?.getOrNull(at.column)
    val checked = cell?.checked
    if (checked != null) {
        model.editTable(segment) { it.setCell(table.fileRow(at.row), at.column, if (checked) "[ ]" else "[x]") }
    }
    model.focus = null
    model.activeCell = ActiveCell(segment.firstLine, at)
    model.cellEpoch += 1
}

/**
 * The words of the cell being written in, typed right in the cell. The
 * field keeps its own copy as it is typed -- the cell trims its ends when
 * it is written, and read back on every letter, the space before the next
 * word was gone before the word was. A line break is the cell's `<br>`; a
 * pipe, which would split the cell in two, becomes "/", as the table
 * rules write it.
 */
@Composable
private fun CellEditor(model: EditorModel, segment: Editing.Segment, table: TableBlock, at: CellAt,
                       style: TextStyle) {
    val palette = LocalPalette.current
    val key = Triple(segment.firstLine, at, model.cellEpoch)
    var draft by remember(key) {
        val text = cellTextAt(table, at).replace(LINE_BREAK, "\n")
        // At the end of the words, not under the empty lines a row made
        // taller keeps: typed there, the words went to its last line. After
        // the calculator wrote in, after what it wrote.
        val end = (model.cellCaretNext ?: text.trimEnd('\n').length).coerceIn(0, text.length)
        model.cellCaretNext = null
        model.cellCaret = end
        mutableStateOf(TextFieldValue(text, TextRange(end)))
    }
    val typing = remember(key) { FocusRequester() }
    LaunchedEffect(key) { typing.requestFocus() }
    // The cell left: words it held back are written now ([EditorModel.typeInCell]).
    DisposableEffect(key) { onDispose { model.writeHeldCell() } }
    val said = stringResource(R.string.cell)
    val following = rememberFollowing()
    BasicTextField(
        value = draft,
        onValueChange = { given ->
            // A CR that came in is one line break, as the desktop's paste puts it.
            val next = Editing.breaksCame(draft.text, given.text, given.selection.end.takeIf { given.selection.collapsed })
                ?.let { (text, at) -> TextFieldValue(text, TextRange(at)) } ?: given
            val clean = next.text.replace("|", "/")
            following.typed(draft.text, clean, next.selection.end)
            draft = next.copy(text = clean)
            model.cellCaret = next.selection.end
            model.typeInCell(segment, table, at, clean, next.selection.end)
        },
        onTextLayout = following::laidOut,
        textStyle = style,
        cursorBrush = SolidColor(palette.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().focusRequester(typing).semantics { contentDescription = said }
            .onFocusChanged { if (it.isFocused) model.focus = null }
            .bringIntoViewRequester(following.requester),
    )
}

/**
 * The table's bar, over the keyboard while a cell is chosen: one word to a
 * button. A button with choices opens them in a row above the bar --
 * Above or Below, Left or Right -- and More holds the rest in a sheet.
 */
@Composable
private fun TableBar(model: EditorModel, segment: Editing.Segment, at: CellAt,
                     onMore: () -> Unit, onDeleteTable: () -> Unit) {
    val palette = LocalPalette.current
    val lines = segment.text.split("\n")
    val table = remember(segment.text) { parseTable(lines) }
    val row = table.fileRow(at.row)
    val cell = table.rows.getOrNull(at.row)?.getOrNull(at.column)
    val merged = remember(segment.text, at) { TableEdit(lines).isMerged(row, at.column) }
    // A day of a series keeps its text plain and its lines the series':
    // a cell's colour belongs to the whole note.
    val wholeNote = model.day == null
    var open by remember(segment.firstLine, at) { mutableStateOf<String?>(null) }
    fun edit(moveTo: CellAt? = at, change: (TableEdit) -> TableEdit) {
        model.editTable(segment, change = change)
        open = null
        model.activeCell = moveTo?.let { ActiveCell(segment.firstLine, it) }
        model.cellEpoch += 1
    }

    Column(Modifier.fillMaxWidth().background(palette.sidebar)) {
        when (open) {
            "row" -> ChoiceRow {
                // The new row is where the cell was, or under it: written in next.
                Choice(stringResource(R.string.tb_above)) { edit(at) { it.insertRowBefore(row) } }
                Choice(stringResource(R.string.tb_below)) { edit(CellAt(at.row + 1, at.column)) { it.insertRowAfter(row) } }
            }
            "column" -> ChoiceRow {
                Choice(stringResource(R.string.tb_left)) { edit(at) { it.insertColumnAfter(at.column - 1) } }
                Choice(stringResource(R.string.tb_right)) { edit(CellAt(at.row, at.column + 1)) { it.insertColumnAfter(at.column) } }
            }
            "merge" -> ChoiceRow {
                if (merged) {
                    Choice(stringResource(R.string.tb_split)) { edit(at) { it.split(row, at.column) } }
                } else {
                    // Both cells' words joined in the one kept, left or above, as the
                    // desktop's merge joins them ([EditorModel.mergeCells]); with the
                    // cell right or below, that cell merges with this one (her call).
                    fun merge(from: CellAt, up: Boolean) {
                        val kept = model.mergeCells(segment, from, up)
                        open = null
                        model.activeCell = ActiveCell(segment.firstLine, kept ?: at)
                        model.cellEpoch += 1
                    }
                    if (at.column > 0) Choice(stringResource(R.string.tb_merge_left)) { merge(at, up = false) }
                    if (cell != null && at.column + 1 < table.rows[at.row].size) {
                        Choice(stringResource(R.string.tb_merge_right)) { merge(CellAt(at.row, at.column + 1), up = false) }
                    }
                    if (at.row > 0) Choice(stringResource(R.string.tb_merge_up)) { merge(at, up = true) }
                    if (at.row + 1 < table.rows.size) {
                        Choice(stringResource(R.string.tb_merge_down)) { merge(CellAt(at.row + 1, at.column), up = true) }
                    }
                }
            }
            "color" -> ChoiceRow {
                Text("✕", color = palette.subtext, fontSize = 14.sp, textAlign = TextAlign.Center,
                     modifier = Modifier.said(R.string.highlight_none)
                         .size(34.dp).clip(RoundedCornerShape(17.dp)).background(palette.inset)
                         .clickable { model.highlightCell(segment, row, at.column, ""); open = null }.padding(top = 7.dp))
                for ((i, colour) in Themes.HIGHLIGHT_COLOR_PALETTE.withIndex()) {
                    val said = stringResource(R.string.highlight_colour, i + 1)
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(hexColor(colour))
                            .semantics { contentDescription = said }
                            .clickable { model.highlightCell(segment, row, at.column, colour); open = null })
                }
            }
            "align" -> ChoiceRow {
                for ((label, align) in listOf(R.string.tb_left to CellAlign.LEFT, R.string.tb_center to CellAlign.CENTER,
                                              R.string.tb_right to CellAlign.RIGHT)) {
                    Choice(stringResource(label)) { edit(at) { it.setAlignment(row, at.column, align) } }
                }
            }
            "delete" -> ChoiceRow {
                Choice(stringResource(R.string.tb_delete_row), danger = true) { edit(null) { it.removeRow(row) } }
                Choice(stringResource(R.string.tb_delete_column), danger = true) { edit(null) { it.removeColumn(at.column) } }
                Choice(stringResource(R.string.tb_delete_table), danger = true) { open = null; onDeleteTable() }
            }
        }
        // Every button in sight, the width shared out: with a fixed width
        // each, More ran off the screen's edge, to be found only by
        // scrolling a bar nobody knew scrolled.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val share = Modifier.weight(1f)
            BarButton("＋", stringResource(R.string.tb_row), open == "row", share) { open = if (open == "row") null else "row" }
            BarButton("⊞", stringResource(R.string.tb_column), open == "column", share) {
                open = if (open == "column") null else "column"
            }
            BarButton("⧉", stringResource(R.string.tb_merge), open == "merge", share) { open = if (open == "merge") null else "merge" }
            if (wholeNote) {
                BarButton("◐", stringResource(R.string.tb_color), open == "color", share) {
                    open = if (open == "color") null else "color"
                }
            }
            BarButton("≡", stringResource(R.string.tb_align), open == "align", share) { open = if (open == "align") null else "align" }
            // A box, or words again: the cell's own marker, "[ ]" or nothing.
            BarButton(if (cell?.checked != null) "✎" else "☑", stringResource(R.string.tb_box), false, share) {
                edit(at) { it.setCell(row, at.column, if (cell?.checked != null) "" else "[ ]") }
            }
            BarButton("🗑", stringResource(R.string.tb_delete), open == "delete", share, danger = true) {
                open = if (open == "delete") null else "delete"
            }
            BarButton("⋯", stringResource(R.string.tb_more), false, share) { open = null; onMore() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun Choice(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Text(label, color = if (danger) palette.dangerText else palette.text, fontSize = 14.sp,
         modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(palette.card)
             .border(1.dp, palette.hairline, RoundedCornerShape(18.dp))
             .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
}

/** A button of the table's bar: its mark over one word. */
@Composable
private fun BarButton(mark: String, word: String, open: Boolean, modifier: Modifier, danger: Boolean = false,
                      onClick: () -> Unit) {
    val palette = LocalPalette.current
    // Open, it is filled: its warning and its word take the shade that reads there.
    val warning = palette.dangerTextOn(open)
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(if (open) palette.calendarHasNotes else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The marks' own heights differ ("＋" stands taller than "⊞"): one
        // box for all, so the words beneath sit on one line.
        Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            Text(mark, fontSize = 18.sp, color = if (danger) warning else palette.text)
        }
        Text(word, fontSize = 11.sp, color = if (danger) warning else palette.subtextOn(open), maxLines = 1,
             softWrap = false)
    }
}

/**
 * The table's rarer changes, in one sheet: widths, a hidden column, a
 * caption, a line above, where the table sits, and deleting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableMoreSheet(model: EditorModel, segment: Editing.Segment, at: CellAt,
                           onDeleteTable: () -> Unit, onDone: () -> Unit) {
    val palette = LocalPalette.current
    val table = remember(segment.text) { parseTable(segment.text.split("\n")) }
    val wholeNote = model.day == null
    // The desktop sets a width by dragging a column's edge; a finger gets
    // steps instead, four characters each, written in the separator as the
    // desktop writes a dragged one.
    val widthNow = table.drawnChars(at.column)
    fun columns(keep: Boolean = true, edit: (List<String>) -> List<String>) {
        model.editColumns(segment, edit)
        if (!keep) model.activeCell = null
        onDone()
    }
    AppSheet(onDismissRequest = onDone) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.more_title), color = palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                 modifier = Modifier.padding(bottom = 6.dp))
            MoreRow("↔", stringResource(R.string.more_fit_all)) { columns { autofitAllColumns(it) } }
            MoreRow("↦", stringResource(R.string.more_fit)) { columns { autofitColumn(it, at.column) } }
            MoreRow("←→", stringResource(R.string.more_wider)) {
                columns { setColumnWidth(it, at.column, table.widerChars(at.column)) }
            }
            if (widthNow > 4) {
                MoreRow("→←", stringResource(R.string.more_narrower)) { columns { setColumnWidth(it, at.column, widthNow - 4) } }
            }
            if (table.columns.any { it.hidden }) {
                MoreRow("👁", stringResource(R.string.more_show)) { columns { unhideAllColumns(it) } }
            }
            // Never the last column in sight: with every column hidden the
            // table draws nothing, and no cell is left to tap for Show --
            // the desktop's "hiding every column would lock Unhide away".
            if (table.columns.count { !it.hidden } > 1) {
                MoreRow("⊘", stringResource(R.string.more_hide)) { columns(keep = false) { hideColumn(it, at.column) } }
            }
            MoreRow("✎", stringResource(R.string.more_caption), stringResource(R.string.more_caption_sub)) {
                model.activeCell = null
                onDone()
                model.addCaption(segment)
            }
            MoreRow("↥", stringResource(R.string.more_line_above)) {
                model.activeCell = null
                onDone()
                model.addLineAbove(segment)
            }
            if (wholeNote) {
                val place = model.props[segment.firstLine]?.align ?: "left"
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoreMark("▤")
                    Text(stringResource(R.string.more_place), color = palette.text, fontSize = 15.sp,
                         modifier = Modifier.padding(start = 12.dp).weight(1f))
                    for ((label, where) in listOf(R.string.tb_left to "left", R.string.tb_center to "center", R.string.tb_right to "right")) {
                        val on = where == place
                        Text(stringResource(label), fontSize = 13.sp, color = if (on) palette.onAccent else palette.text,
                             modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(16.dp))
                                 .background(if (on) palette.accent else palette.inset)
                                 .clickable { model.placeTable(segment, where); onDone() }
                                 .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            MoreRow("🗑", stringResource(R.string.more_delete), danger = true) { onDeleteTable() }
        }
    }
}

@Composable
private fun MoreMark(mark: String) {
    val palette = LocalPalette.current
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(palette.calendarHasNotes),
        // The arrows draw small for their size: 20 makes them read, and a
        // pair of them ("←→") takes less, to fit the box.
        contentAlignment = Alignment.Center) {
        Text(mark, fontSize = if (mark.codePointCount(0, mark.length) > 1) 13.sp else 20.sp, color = palette.text,
             maxLines = 1, softWrap = false)
    }
}

@Composable
private fun MoreRow(mark: String, label: String, sub: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        MoreMark(mark)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, color = if (danger) palette.dangerText else palette.text, fontSize = 15.sp)
            if (sub != null) Text(sub, color = palette.subtext, fontSize = 12.sp)
        }
    }
}
