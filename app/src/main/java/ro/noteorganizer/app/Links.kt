package ro.noteorganizer.app

import java.net.URLEncoder

/**
 * The project site's pages the app opens in the browser: the feedback form
 * and the privacy policy. Google Play is given the policy's address too, so
 * the two must name the same page -- the desktop's `core/diagnostics.py`
 * holds the same addresses, and its site build refuses a site without them.
 */
object Links {

    const val FEEDBACK = "https://note-organizer-8f7772.netlify.app/feedback.html"
    const val PRIVACY = "https://note-organizer-8f7772.netlify.app/privacy.html"

    /**
     * The feedback form, carrying [details] after a "#": a browser never
     * sends that part of a link, so the site receives them only with the
     * form, when Send is pressed -- what the privacy policy promises, and
     * why the Data safety form can say no data is collected. A detail with
     * no value is left out.
     */
    fun feedback(details: List<Pair<String, String>>): String =
        FEEDBACK + "#" + details.filter { it.second.isNotEmpty() }
            .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
}
