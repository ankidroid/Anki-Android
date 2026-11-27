// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], qualifiers = "w411dp-h891dp")
class MoreFeedbackLocaleTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `More feedback uses the language selected while the app is running`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            val initialLocales = AppCompatDelegate.getApplicationLocales()
            try {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                setIntroductionSlidesShown(true)
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
                controller
                    .get()
                    .binding.bottomNavigation!!
                    .selectedItemId = R.id.nav_more
                advanceRobolectricLooper()

                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
                controller.recreate()
                advanceRobolectricLooper()

                val deckPicker = controller.get()
                composeRule.onNodeWithText(deckPicker.getString(CommonString.send_feedback)).performScrollTo().performClick()
                assertEquals(deckPicker.getString(R.string.link_help_ja), shadowOf(deckPicker).nextStartedActivity?.dataString)
            } finally {
                AppCompatDelegate.setApplicationLocales(initialLocales)
            }
        }
}
