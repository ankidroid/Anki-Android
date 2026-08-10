// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragment
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerAnalyticsOptInDialogTest {
    @Before
    fun setUp() {
        mockkObject(AnkiDroidUsageAnalytics)
        every { AnkiDroidUsageAnalytics.isEnabled = any() } returns Unit
        // the base AnalyticsDialogFragment reports a screen view in onResume
        every { AnkiDroidUsageAnalytics.sendAnalyticsScreenView(any<Any>()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkObject(AnkiDroidUsageAnalytics)
    }

    @Test
    fun `opting in enables analytics for the current session`() {
        showDialog(optIn = true)

        verify(exactly = 1) { AnkiDroidUsageAnalytics.isEnabled = true }
    }

    @Test
    fun `declining disables analytics for the current session`() {
        showDialog(optIn = false)

        verify(exactly = 1) { AnkiDroidUsageAnalytics.isEnabled = false }
    }

    @Test
    fun `cancelling does not change the opt-in state`() {
        launchFragment<DeckPickerAnalyticsOptInDialog>(
            themeResId = R.style.Theme_Light,
            initialState = Lifecycle.State.RESUMED,
        ).onFragment { fragment ->
            (fragment.dialog as AlertDialog).cancel()
        }

        verify(exactly = 0) { AnkiDroidUsageAnalytics.isEnabled = any() }
    }

    private fun showDialog(optIn: Boolean) {
        launchFragment<DeckPickerAnalyticsOptInDialog>(
            themeResId = R.style.Theme_Light,
            initialState = Lifecycle.State.RESUMED,
        )
        // the checkbox starts unticked, so only opting in needs a click
        if (optIn) {
            onView(withId(R.id.checkbox)).inRoot(isDialog()).perform(click())
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }
}
