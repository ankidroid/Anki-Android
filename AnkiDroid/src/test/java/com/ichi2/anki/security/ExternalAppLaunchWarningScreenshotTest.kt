// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.security

import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.ScreenshotTest
import org.junit.Test
import org.robolectric.Robolectric.buildActivity

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.security.ExternalAppLaunchWarningScreenshotTest"`
 */
class ExternalAppLaunchWarningScreenshotTest : ScreenshotTest() {
    @Test
    fun `external app launch blocked dialog`() =
        buildActivity(AnkiActivity::class.java).use { controller ->
            val activity = controller.setup().get()

            ExternalAppLaunchWarning(activity).show()
            advanceRobolectricLooper()

            captureScreen("external_app_launch_blocked_dialog")
        }
}
