// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import com.ichi2.utils.IntentUtil
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MoreRateAvailabilityTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `More does not launch a store that became unavailable`() =
        withPref(Prefs::devBottomNavEnabled, true) {
            setIntroductionSlidesShown(true)
            val deckPicker = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val marketIntent = AnkiDroidApp.getMarketIntent(deckPicker)
            val packageManager = shadowOf(deckPicker.packageManager)
            val store = ComponentName("com.example.store", "com.example.store.StoreActivity")
            packageManager.addActivityIfNotPresent(store)
            packageManager.addIntentFilterForActivity(
                store,
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme(checkNotNull(marketIntent.scheme))
                },
            )
            assertTrue(IntentUtil.canOpenIntent(deckPicker, marketIntent))
            deckPicker.binding.bottomNavigation!!.selectedItemId = R.id.nav_more
            advanceRobolectricLooper()

            val rate = composeRule.onNodeWithText(deckPicker.getString(CommonString.help_item_support_rate_ankidroid))
            rate.performScrollTo()
            packageManager.removeActivity(store)
            assertFalse(IntentUtil.canOpenIntent(deckPicker, marketIntent))

            rate.performClick()

            assertNull(shadowOf(deckPicker).nextStartedActivity?.dataString, "The unavailable market must not be launched")
        }
}
