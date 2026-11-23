package ro.noteorganizer.app

import org.json.JSONObject

/**
 * The restore the app runs ([Organizing.restore]), asked by the id an
 * entry has in the Trash's file, as the tests know their notes. The screen
 * picks rows by [Organizing.TrashKey]; this builds the keys from the file.
 */
object Trashed {
    fun restore(load: Notebook.Load, id: Int): SaveResult {
        val raw = load.trashRaw
        val keys = (0 until (raw?.length() ?: 0)).mapNotNull { raw!!.opt(it) as? JSONObject }
            .filter { (it.opt("id") as? Number)?.toInt() == id }
            .map { Organizing.TrashKey(id, it.opt("deleted_at") as? String ?: "", it.opt("uid") as? String ?: "") }
        return Organizing.restore(load, keys)
    }
}
