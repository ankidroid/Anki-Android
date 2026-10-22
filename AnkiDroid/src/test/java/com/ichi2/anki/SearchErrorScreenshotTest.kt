// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import org.robolectric.shadows.ShadowDialog

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.SearchErrorScreenshotTest"`
 */
class SearchErrorScreenshotTest : ScreenshotTest() {
    @Test
    fun `browser search errors`() =
        runTest {
            throwOnShowError = false
            addBasicNote()
            val browser = startRegularActivity<CardBrowser>()
            for ((query, name) in listOf("and" to "inline_code", "re:b[" to "regex_diagnostic")) {
                browser.searchCardsSync(query)
                advanceRobolectricLooper()
                captureScreen(name)
                ShadowDialog.getLatestDialog().dismiss()
            }
        }
}
