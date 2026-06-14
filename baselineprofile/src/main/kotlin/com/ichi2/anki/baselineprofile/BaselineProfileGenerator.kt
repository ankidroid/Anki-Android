/*
 *  Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile that captures the critical user journey for AnkiDroid's
 * cold start: launching the app and reaching the main [com.ichi2.anki.DeckPicker] screen.
 *
 * The generator includes two test cases to ensure full coverage of startup paths:
 *
 * 1. [generate] — captures the cold-start launch path:
 *    - [com.ichi2.anki.IntentHandler] — the MAIN/LAUNCHER activity declared in
 *      the manifest. `startActivityAndWait()` launches this.
 *    - [com.ichi2.anki.DeckPicker] — IntentHandler.onCreate() immediately
 *      creates an intent for DeckPicker and calls `launchDeckPickerIfNoOtherTasks()`.
 *    - The `device.wait` + `device.waitForIdle()` calls wait for DeckPicker to
 *      fully render before the trace window closes.
 *
 * 2. [confirmOnResumeIsCalled] — specifically exercises [com.ichi2.anki.DeckPicker.onResume]
 *    by navigating to [com.ichi2.anki.PreferencesActivity] via the navigation drawer
 *    and returning to [com.ichi2.anki.DeckPicker]. This ensures the profile captures
 *    the logic performed when the app returns to the foreground.
 *
 * On a fresh install, [com.ichi2.anki.IntroductionActivity] appears before
 * DeckPicker. The journeys handle this by tapping the "Get Started" button
 * (resource id `get_started`) if it is present, so the profile captures the
 * full path to DeckPicker regardless of device state.
 *
 * Run on a connected API 28+ physical device with:
 * ```
 * ./gradlew :AnkiDroid:generateBaselineProfile
 * ```
 * The generated profile is committed to
 * `AnkiDroid/src/main/baselineProfiles/baseline-prof.txt` by the
 * [androidx.baselineprofile] plugin and is automatically embedded in subsequent
 * app builds.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        val targetPackage =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw IllegalStateException("targetAppId not passed as instrumentation runner arg")

        rule.collect(packageName = targetPackage) {
            pressHome()
            // Launches IntentHandler (MAIN/LAUNCHER), which routes to DeckPicker.
            startActivityAndWait()

            // On a fresh install, IntroductionActivity appears before DeckPicker.
            // Dismiss it by tapping "Get Started" so the profile always captures
            // the full path through to DeckPicker, regardless of device state.
            device.findObject(By.res(targetPackage, "get_started"))?.click()

            // Wait for DeckPicker to fully render so all startup classes are captured.
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 5_000)
            device.waitForIdle()
        }
    }

    @Test
    fun confirmOnResumeIsCalled() {
        val targetPackage =
            InstrumentationRegistry.getArguments().getString("targetAppId")
                ?: throw IllegalStateException("targetAppId not passed as instrumentation runner arg")

        rule.collect(packageName = targetPackage) {
            pressHome()
            startActivityAndWait()

            // Dismiss introduction if present.
            device.findObject(By.res(targetPackage, "get_started"))?.click()

            // Wait for DeckPicker to fully render.
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 5_000)
            device.waitForIdle()

            // Exercise DeckPicker:onResume by navigating away and back.
            // Open the navigation drawer.
            device.findObject(By.desc("Open drawer"))?.click()
            // Wait for "Settings" to be visible and click it.
            device.wait(Until.hasObject(By.text("Settings")), 2_000)
            device.findObject(By.text("Settings"))?.click()

            // Wait for Settings to be fully rendered.
            device.waitForIdle()

            // Return to DeckPicker.
            device.pressBack()

            // Wait for DeckPicker to be resumed and fully rendered.
            device.wait(Until.hasObject(By.pkg(targetPackage).depth(0)), 5_000)
            device.waitForIdle()
        }
    }
}
