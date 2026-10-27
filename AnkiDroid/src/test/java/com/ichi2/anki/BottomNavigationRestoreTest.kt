// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.testutils.withBooleanPreference
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class BottomNavigationRestoreTest : RobolectricTest() {
    @Test
    fun `a configuration change keeps the open tab on screen`() =
        forEachTab { tab ->
            val controller = launchOn(tab)
            controller.recreate()
            advanceRobolectricLooper()

            assertShowsTab(controller.get(), tab)
        }

    @Test
    fun `restoring after process death keeps the open tab on screen`() =
        forEachTab { tab ->
            assertShowsTab(restoreAfterProcessDeath(launchOn(tab)), tab)
        }

    @Test
    fun `back returns to the deck list after a restore`() =
        forEachTab { tab ->
            val deckPicker = restoreAfterProcessDeath(launchOn(tab))

            deckPicker.onBackPressedDispatcher.onBackPressed()
            advanceRobolectricLooper()

            assertThat("$tab: back selects Home", deckPicker.bottomNav.selectedItemId, equalTo(NavigationItem.HOME.id))
            assertThat("$tab: back shows the deck list", deckPicker.deckList.isVisible, equalTo(true))
        }

    @Test
    fun `restoring on Home after the browser was opened`() =
        withBottomNavigation {
            val controller = launchOn(NavigationItem.BROWSER)
            controller.get().select(NavigationItem.HOME)

            val deckPicker = restoreAfterProcessDeath(controller)

            assertThat(deckPicker.bottomNav.selectedItemId, equalTo(NavigationItem.HOME.id))
            assertThat(deckPicker.deckList.isVisible, equalTo(true))
        }

    @Test
    fun `restoring a browser after the bottom navigation is turned off`() =
        withBottomNavigation {
            val controller = launchOn(NavigationItem.BROWSER)

            withBooleanPreference(R.string.dev_bottom_nav_key, false) {
                val deckPicker = restoreAfterProcessDeath(controller)

                assertThat(deckPicker.bottomNav.isVisible, equalTo(false))
                assertThat(deckPicker.deckList.isVisible, equalTo(true))
            }
        }

    private fun withBottomNavigation(test: () -> Unit) =
        withBooleanPreference(R.string.dev_bottom_nav_key, true) {
            setIntroductionSlidesShown(true)
            test()
        }

    private fun forEachTab(test: (NavigationItem) -> Unit) =
        withBottomNavigation {
            listOf(NavigationItem.BROWSER, NavigationItem.STATS, NavigationItem.MORE).forEach(test)
        }

    private fun launchOn(tab: NavigationItem): ActivityController<DeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).also {
            it.get().select(tab)
        }

    private fun DeckPicker.select(tab: NavigationItem) {
        bottomNav.selectedItemId = tab.id
        advanceRobolectricLooper()
    }

    private fun restoreAfterProcessDeath(controller: ActivityController<DeckPicker>): DeckPicker {
        val savedState = Bundle()
        controller
            .pause()
            .stop()
            .saveInstanceState(savedState)
            .destroy()
        val restored =
            Robolectric
                .buildActivity(DeckPicker::class.java, Intent())
                .create(savedState)
                .start()
                .restoreInstanceState(savedState)
                .postCreate(savedState)
                .resume()
                .visible()
        saveControllerForCleanup(restored)
        advanceRobolectricLooper()
        return restored.get()
    }

    private fun assertShowsTab(
        deckPicker: DeckPicker,
        tab: NavigationItem,
    ) {
        val container = deckPicker.binding.bottomNavFragmentContainer!!
        assertThat("$tab is selected", deckPicker.bottomNav.selectedItemId, equalTo(tab.id))
        assertThat("$tab content is shown", container.isVisible, equalTo(true))
        assertThat("$tab hides the deck list", deckPicker.deckList.isVisible, equalTo(false))
        assertThat("$tab fragment is not hidden", deckPicker.supportFragmentManager.findFragmentByTag(tab.tag)?.isHidden, equalTo(false))
        assertThat(
            "$tab content clears the bar",
            (container.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin,
            equalTo(deckPicker.bottomNav.height),
        )
    }

    private val DeckPicker.bottomNav: BottomNavigationView
        get() = binding.bottomNavigation!!

    private val DeckPicker.deckList: View
        get() = binding.deckPickerContentWrapper!!
}
