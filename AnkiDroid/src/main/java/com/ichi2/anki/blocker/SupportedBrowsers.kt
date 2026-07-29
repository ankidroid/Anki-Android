// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Tim Farrelly <timf34@gmail.com>

package com.ichi2.anki.blocker

/**
 * Browsers whose address bar the blocker can read for website blocking, and the
 * `viewIdResourceName` of their URL bar. Kept in one table because these ids
 * drift across browser versions; a failed lookup degrades to "no website
 * detection in that browser", never a crash or a false gate.
 *
 * Known limitation: WebViews embedded in other apps have no address bar and are
 * not detectable. Chrome Custom Tabs run under the browser's own package and
 * are covered.
 */
data class SupportedBrowser(
    val packageName: String,
    val urlBarViewIds: List<String>,
)

object SupportedBrowsers {
    private fun chromiumLike(packageName: String) = SupportedBrowser(packageName, listOf("$packageName:id/url_bar"))

    private val all =
        listOf(
            chromiumLike("com.android.chrome"),
            chromiumLike("com.chrome.beta"),
            chromiumLike("com.chrome.dev"),
            chromiumLike("com.chrome.canary"),
            chromiumLike("com.brave.browser"),
            chromiumLike("com.vivaldi.browser"),
            chromiumLike("com.microsoft.emmx"),
            chromiumLike("com.kiwibrowser.browser"),
            SupportedBrowser(
                "org.mozilla.firefox",
                listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
            ),
            SupportedBrowser(
                "org.mozilla.firefox_beta",
                listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"),
            ),
            SupportedBrowser(
                "org.mozilla.focus",
                listOf("org.mozilla.focus:id/mozac_browser_toolbar_url_view", "org.mozilla.focus:id/display_url"),
            ),
            SupportedBrowser(
                "com.sec.android.app.sbrowser",
                listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text"),
            ),
            SupportedBrowser(
                "com.opera.browser",
                listOf("com.opera.browser:id/url_field"),
            ),
            SupportedBrowser(
                "com.duckduckgo.mobile.android",
                listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
            ),
        )

    private val byPackage = all.associateBy(SupportedBrowser::packageName)

    val packageNames: Set<String> = byPackage.keys

    fun byPackage(packageName: String?): SupportedBrowser? = packageName?.let(byPackage::get)

    /**
     * Extracts a bare lowercase host from address-bar text, or null when the text
     * is not a web address (placeholder text, search suggestions, blank).
     *
     * `https://www.X.com/foo?bar` → `x.com`; `Search or type web address` → null.
     */
    fun parseHost(input: String?): String? {
        var text = input?.trim() ?: return null
        if (text.isEmpty() || text.any(Char::isWhitespace)) return null
        text = text.removePrefix("https://").removePrefix("http://")
        text = text.substringBefore('/').substringBefore('?').substringBefore('#')
        text = text.substringBefore(':')
        text = text.lowercase().removePrefix("www.")
        if (text.isEmpty() || '.' !in text) return null
        return text
    }
}
