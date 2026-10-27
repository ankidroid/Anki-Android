// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.pages

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest

/**
 * Screenshot tests for [CongratsPage]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.pages.CongratsPageScreenshotTest"`
 */
class CongratsPageScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = CongratsPage.getIntent(targetContext)
}
