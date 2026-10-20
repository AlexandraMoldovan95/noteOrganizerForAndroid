package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/** The site's pages the app opens, and what the feedback link may carry. */
class LinksTest {

    private val details = listOf(
        "version" to "android 0.9.1", "theme" to "Midnight Purple", "notes" to "12",
        "files" to "0", "mb" to "", "build" to "android",
    )

    @Test
    fun `the feedback link carries its details after the hash, which a browser never sends`() {
        val link = URI(Links.feedback(details))
        assertEquals("/feedback.html", link.path)
        assertEquals(null, link.rawQuery)
        assertEquals("version=android+0.9.1&theme=Midnight+Purple&notes=12&files=0&build=android",
            link.rawFragment)
    }

    @Test
    fun `a detail with no value is left out, and the words are encoded`() {
        val link = Links.feedback(listOf("theme" to "Rose & Gold", "mb" to ""))
        assertTrue(link.endsWith("#theme=Rose+%26+Gold"))
        assertFalse(link.contains("mb="))
    }

    @Test
    fun `both pages are on the site the desktop opens, the policy where the stores are told`() {
        assertEquals("https://note-organizer-8f7772.netlify.app/feedback.html", Links.FEEDBACK)
        assertEquals("https://note-organizer-8f7772.netlify.app/privacy.html", Links.PRIVACY)
    }
}
