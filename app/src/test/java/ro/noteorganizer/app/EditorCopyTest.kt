package ro.noteorganizer.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files as NioFiles

/**
 * What a copy from the editor carries: the selection's formatting as it is
 * when the copy is made. The selection is noted when it is made, and a
 * style put on from the format bar afterwards changes the note without any
 * field saying so -- on a real phone's rehearsal, words selected, made bold
 * and 14, copied, came back pasted plain.
 */
class EditorCopyTest {

    private val words = "Tom & Jerry"

    private fun editor(): Pair<AppState, EditorModel> {
        val dir = NioFiles.createTempDirectory("editorcopy").toFile()
        File(dir, "notes_data.json").writeText(
            """[{"id": 1, "uid": "a", "title": "A", "body": "$words", "category": "General"}]""")
        File(dir, "attachments").mkdirs()
        val state = AppState(dir, CoroutineScope(Dispatchers.Unconfined))
        return state to EditorModel(state, state.note(1))
    }

    private fun EditorModel.selectAll() =
        pick(Editing.segments(body).first(), TextFieldValue(body, TextRange(0, body.length)))

    /** The format bar's B, as it applies it: the spans changed, no field told. */
    private fun EditorModel.boldFromTheBar() {
        spans = setStyle(spans, 0, words.length, "bold")
        changed()
    }

    @Test
    fun `a style put on after the words were selected goes out with their copy`() {
        val (state, model) = editor()
        model.selectAll()
        model.boldFromTheBar()
        assertNotNull(model.copyOut(words))
        assertEquals(listOf(FormatSpan(0, words.length, "bold")), state.ownCopy!!.spans)
    }

    @Test
    fun `a cut made after a style keeps it -- the words are gone by the time it copies`() {
        val (state, model) = editor()
        model.selectAll()
        model.boldFromTheBar()
        // The field's change arrives first, the words already taken out.
        model.freshenPick()
        model.body = ""
        model.changed()
        assertNotNull(model.copyOut(words))
        assertEquals(listOf(FormatSpan(0, words.length, "bold")), state.ownCopy!!.spans)
    }

    @Test
    fun `a copy of words that are no longer the selection's goes out plain, as before`() {
        val (_, model) = editor()
        model.selectAll()
        assertNull(model.copyOut("Jerry"))
        assertTrue(model.copyOut(words) != null)
    }
}
