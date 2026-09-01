// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.mediacheck

import android.content.Intent
import com.ichi2.anki.SingleFragmentScreenshotTest

/**
 * Screenshot tests for [MediaCheckFragment]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.mediacheck.MediaCheckScreenshotTest"`
 */
class MediaCheckScreenshotTest : SingleFragmentScreenshotTest() {
    override fun buildIntent(): Intent = MediaCheckFragment.getIntent(targetContext)
}
