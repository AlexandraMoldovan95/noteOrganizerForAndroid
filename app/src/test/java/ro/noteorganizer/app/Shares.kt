package ro.noteorganizer.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * A share code as the tests make and take one. The code is a note as it
 * sits on disk, encoded, as [AppState.shareCode] makes it; the import goes
 * through [Importing], as the screen's does, with one answer to the
 * question it asks: [update] replaces the notes this device already has,
 * or adds the code's beside them without their identity.
 */
object Shares {
    fun codeFor(load: Notebook.Load, id: Int): String? {
        val array = load.raw ?: return null
        val note = (0 until array.length()).mapNotNull { array.opt(it) as? JSONObject }
            .firstOrNull { (it.opt("id") as? Number)?.toInt() == id } ?: return null
        return Sharing.encode(listOf(note))
    }

    fun apply(load: Notebook.Load, notes: List<JSONObject>, update: Boolean): SaveResult {
        val plan = Importing.plan(load.raw ?: JSONArray(), notes, Importing.Source.CODE)
        val answers = Importing.Answers(update = update, copyUnchanged = !update)
        return when (val result = Importing.apply(load, plan, answers, attachmentsDir = null)) {
            is Importing.Result.Failed -> SaveResult.Failed(result.reason)
            is Importing.Result.Done ->
                SaveResult.Ok(result.outcome.firstId,
                              imported = result.outcome.added + result.outcome.updated)
        }
    }
}
