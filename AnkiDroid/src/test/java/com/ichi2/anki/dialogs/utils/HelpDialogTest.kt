/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs.utils

import android.annotation.SuppressLint
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.BuildConfig
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.dialogs.HelpDialog.createInstance
import com.ichi2.anki.dialogs.HelpDialog.createInstanceForSupportAnkiDroid
import com.ichi2.anki.dialogs.RecursivePictureMenu
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil.Companion.getRecyclerViewFor
import com.ichi2.utils.IntentUtil
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpDialogTest : RobolectricTest() {
    @Test
    @RunInBackground
    fun testMenuDoesNotCrash() {
        val dialog = createInstance() as RecursivePictureMenu
        openDialogFragment(dialog)
        val v = getRecyclerViewFor(dialog)
        MatcherAssert.assertThat(v.adapter!!.itemCount, Matchers.equalTo(4))
    }

    @Test
    @RunInBackground
    fun testMenuSupportAnkiDroidDoesNotCrash() {
        val dialog = createInstanceForSupportAnkiDroid(targetContext) as RecursivePictureMenu
        openDialogFragment(dialog)
        val v = getRecyclerViewFor(dialog)
        val rateAvailable = IntentUtil.canOpenIntent(
            targetContext,
            AnkiDroidApp.getMarketIntent(targetContext)
        )
        MatcherAssert.assertThat(v.adapter!!.itemCount, Matchers.equalTo(expectedSupportMenuCount(rateAvailable)))
        assertDonateTitleMatchesFlavor(dialog)
    }

    @Test
    @RunInBackground
    fun testMenuSupportAnkiDroidShowsRateWhenPossible() {
        mockkStatic(IntentUtil::canOpenIntent)
        every { IntentUtil.canOpenIntent(targetContext, any()) } returns true
        val dialog = createInstanceForSupportAnkiDroid(targetContext) as RecursivePictureMenu
        openDialogFragment(dialog)
        val v = getRecyclerViewFor(dialog)
        MatcherAssert.assertThat(v.adapter!!.itemCount, Matchers.equalTo(expectedSupportMenuCount(rateAvailable = true)))
        assertDonateTitleMatchesFlavor(dialog)
        unmockkStatic(IntentUtil::canOpenIntent)
    }

    @Test
    fun testShowDonateLinksMatchesFlavor() {
        val expectedShowDonate = when (BuildConfig.FLAVOR) {
            "play" -> false
            "amazon", "full" -> true
            else -> throw AssertionError("Unexpected product flavor: ${BuildConfig.FLAVOR}")
        }
        MatcherAssert.assertThat(
            "SHOW_DONATE_LINKS must match flavor ${BuildConfig.FLAVOR}",
            BuildConfig.SHOW_DONATE_LINKS,
            Matchers.equalTo(expectedShowDonate)
        )
    }

    /**
     * Support menu always includes translate, develop, other, and feedback (4).
     * Donate is flavor-dependent ([BuildConfig.SHOW_DONATE_LINKS]).
     * Rate is included when a market intent is available.
     */
    private fun expectedSupportMenuCount(rateAvailable: Boolean): Int {
        val donateCount = if (BuildConfig.SHOW_DONATE_LINKS) 1 else 0
        val rateCount = if (rateAvailable) 1 else 0
        return 4 + donateCount + rateCount
    }

    private fun assertDonateTitleMatchesFlavor(dialog: RecursivePictureMenu) {
        val items = BundleCompat.getParcelableArrayList(
            dialog.requireArguments(),
            "bundle",
            RecursivePictureMenu.Item::class.java
        )!!
        val titles = items.map { it.title }
        val donateTitle = R.string.help_item_support_opencollective_donate
        if (BuildConfig.SHOW_DONATE_LINKS) {
            MatcherAssert.assertThat(titles, Matchers.hasItem(donateTitle))
        } else {
            MatcherAssert.assertThat(titles, Matchers.not(Matchers.hasItem(donateTitle)))
        }
    }

    @SuppressLint("CheckResult") // openDialogFragmentUsingActivity
    private fun openDialogFragment(dialog: RecursivePictureMenu) {
        super.openDialogFragmentUsingActivity(dialog)
    }
}
