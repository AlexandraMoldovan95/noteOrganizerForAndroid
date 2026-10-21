package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pages the app opens: the privacy policy, and its store page once published. */
class LinksTest {

    @Test
    fun `the privacy policy is the page the desktop opens and the stores are given`() {
        assertEquals("https://sites.google.com/view/noteorganizer-privacy", Links.PRIVACY)
    }

    // Changed on purpose the day the app is published: the Feedback section
    // appears only then, and this is where that decision is seen.
    @Test
    fun `before the app is on Google Play there is no store page to send feedback to`() {
        assertNull(Links.STORE)
    }
}
