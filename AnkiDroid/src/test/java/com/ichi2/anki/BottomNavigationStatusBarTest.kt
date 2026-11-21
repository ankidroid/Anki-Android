// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.AppTheme
import com.ichi2.testutils.withPref
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class BottomNavigationStatusBarTest : RobolectricTest() {
    @Test
    fun `light browser uses dark status icons across tab changes and recreation`() =
        runTest {
            withBrowser { controller ->
                val activity = controller.get()
                assertThat("the light browser needs dark icons", activity.hasDarkStatusIcons, equalTo(true))
                activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.HOME.id
                advanceRobolectricLooper()
                assertThat("Decks restores light icons over its coloured toolbar", activity.hasDarkStatusIcons, equalTo(false))
                activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.BROWSER.id
                advanceRobolectricLooper()
                assertThat(activity.hasDarkStatusIcons, equalTo(true))
                controller.recreate()
                advanceRobolectricLooper()
                assertThat("the restored browser keeps dark icons", controller.get().hasDarkStatusIcons, equalTo(true))
            }
        }

    @Test
    fun `dark browser keeps light status icons`() =
        runTest {
            withBrowser(theme = AppTheme.NIGHT) { controller ->
                assertThat(controller.get().hasDarkStatusIcons, equalTo(false))
            }
        }

    @Test
    fun `legacy browser keeps light status icons over its coloured toolbar`() =
        runTest {
            withBrowser(searchView = false) { controller ->
                assertThat(controller.get().hasDarkStatusIcons, equalTo(false))
            }
        }

    private fun withBrowser(
        theme: AppTheme = AppTheme.DAY,
        searchView: Boolean = true,
        block: (ActivityController<DeckPicker>) -> Unit,
    ) = withPref(Prefs::appTheme, theme) {
        withPref(Prefs::devBottomNavEnabled, true) {
            withPref(Prefs::devUsingCardBrowserSearchView, searchView) {
                setIntroductionSlidesShown(true)
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
                val activity = controller.get()
                activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.BROWSER.id
                advanceRobolectricLooper()
                block(controller)
            }
        }
    }

    private val DeckPicker.hasDarkStatusIcons: Boolean
        get() = WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars
}
