package ro.noteorganizer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pages the app opens: the privacy policy, and its store page once
 * published -- and the WebView it prints with, which sends Google nothing.
 */
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

    // The WebView PdfPrint prints with may send Google usage statistics when
    // the person allowed them for the phone; the policy says the app sends
    // nothing. Read from the manifest itself, where the opt-out lives.
    @Test
    fun `the WebView that prints a PDF is opted out of Google's usage statistics`() {
        val android = "http://schemas.android.com/apk/res/android"
        val manifest = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(java.io.File("src/main/AndroidManifest.xml"))
        val metas = manifest.getElementsByTagName("meta-data")
        val optOut = (0 until metas.length).map { metas.item(it) as org.w3c.dom.Element }
            .firstOrNull { it.getAttributeNS(android, "name") == "android.webkit.WebView.MetricsOptOut" }
        assertEquals("application", optOut?.parentNode?.nodeName)
        assertEquals("true", optOut?.getAttributeNS(android, "value"))
    }
}
