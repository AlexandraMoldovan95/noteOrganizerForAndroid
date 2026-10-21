package ro.noteorganizer.app

/**
 * The pages the app opens in the browser: the privacy policy, and -- once
 * the app is published -- its own Google Play page, where feedback is a
 * review. The desktop's `core/diagnostics.py` holds the same policy
 * address, and both stores are given it.
 */
object Links {

    /**
     * The privacy policy, on Google Sites: the desktop repository's
     * docs/PRIVACY.md, copied there by hand and checked at every release.
     */
    const val PRIVACY = "https://sites.google.com/view/noteorganizer-privacy"

    /**
     * The app's Google Play page, once it is published there. While it is
     * null, About has no Feedback section: a review is the only way in.
     */
    val STORE: String? = null
}
