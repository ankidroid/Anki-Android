// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.os.LocaleListCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.dialogs.help.HelpDialog
import com.ichi2.anki.dialogs.help.HelpItemActionsDispatcher
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], qualifiers = "w411dp-h891dp")
class MoreHelpLinkLocaleTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `visiting More before changing language keeps nested Help links current`() =
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
                val openedUrls = mutableListOf<String>()
                val actions = mockk<HelpItemActionsDispatcher>()
                every { actions.onOpenUrl(capture(openedUrls)) } just Runs
                tapHelpLink(deckPicker, CommonString.help_title_using_ankidroid, CommonString.help_item_ankidroid_manual, actions)
                tapHelpLink(deckPicker, CommonString.help_title_get_help, CommonString.help_item_report_bug, actions)
                assertEquals(listOf(deckPicker.getString(R.string.link_manual_ja), deckPicker.getString(R.string.link_help_ja)), openedUrls)
            } finally {
                AppCompatDelegate.setApplicationLocales(initialLocales)
            }
        }

    private fun tapHelpLink(
        deckPicker: DeckPicker,
        @StringRes section: Int,
        @StringRes link: Int,
        actions: HelpItemActionsDispatcher,
    ) {
        composeRule.onNodeWithText(deckPicker.getString(section)).performScrollTo().performClick()
        advanceRobolectricLooper()

        val help =
            deckPicker.supportFragmentManager.fragments
                .filterIsInstance<HelpDialog>()
                .single()
        help.actionsDispatcher = actions
        onView(withText(link)).inRoot(isDialog()).perform(click())
        help.dismissNow()
        advanceRobolectricLooper()
    }
}
