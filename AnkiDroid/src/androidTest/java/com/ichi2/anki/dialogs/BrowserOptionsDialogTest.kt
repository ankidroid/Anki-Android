// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests [BrowserOptionsDialog] */
@RunWith(AndroidJUnit4::class)
class BrowserOptionsDialogTest : InstrumentedTest() {
    @get:Rule
    val activityRule = ActivityScenarioRule(CardBrowser::class.java)

    @Test
    fun dialogLoads() {
        activityRule.scenario.onActivity { activity ->
            BrowserOptionsDialog
                .newInstance(
                    CardsOrNotes.CARDS,
                    isTruncated = true,
                ).show(activity.supportFragmentManager, "BrowserOptionsDialog")
        }
    }
}
