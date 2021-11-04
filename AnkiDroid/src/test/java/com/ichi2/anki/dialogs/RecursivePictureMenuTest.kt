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
package com.ichi2.anki.dialogs

import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.HelpDialog.LinkItem
import com.ichi2.anki.dialogs.RecursivePictureMenu.ItemHeader
import com.ichi2.anki.dialogs.utils.FragmentTestActivity
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil
import com.ichi2.testutils.AnkiAssert
import com.ichi2.utils.ArrayUtil.toArrayList
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class RecursivePictureMenuTest : RobolectricTest() {
    private var mActivity: FragmentTestActivity? = null
    @Test
    @RunInBackground
    fun testNormalStartupSelectingItem() {
        val linkedItem = getItemLinkingTo(R.string.link_anki)
        val v = getRecyclerViewFor(linkedItem)
        clickChildAtIndex(v, 0)
        MatcherAssert.assertThat(mActivity!!.lastUrlOpened, Matchers.`is`(getResourceString(R.string.link_anki)))
    }

    @Test
    @RunInBackground
    fun testSelectingHeader() {
        val numberOfChildItems = 2
        val header: RecursivePictureMenu.Item = getHeaderWithSubItems(numberOfChildItems)
        val v = getRecyclerViewFor(header)
        clickChildAtIndex(v, 0)
        val currentMenu = mActivity!!.lastShownDialogFragment as RecursivePictureMenu
        val rv = RecursivePictureMenuUtil.getRecyclerViewFor(currentMenu)
        MatcherAssert.assertThat("Unexpected number of items - check the adapter", rv.childCount, Matchers.`is`(numberOfChildItems))
    }

    @Test
    @Ignore("Not implemented")
    fun removeFromRoot() {
        val header: RecursivePictureMenu.Item = getHeaderWithSubItems(1)
        val allItems = toArrayList(arrayOf(header))
        RecursivePictureMenu.removeFrom(allItems, header)

        // Do we return, or check to see if the list is mutated?
    }

    @Test
    fun removeChild() {
        val header = getHeaderWithSubItems(1)
        val child = header.children[0]
        val allItems = toArrayList(arrayOf<RecursivePictureMenu.Item>(header))
        RecursivePictureMenu.removeFrom(allItems, child)
        MatcherAssert.assertThat("child should be removed", header.children, Matchers.hasSize(0))
    }

    @Test
    fun removeNotExisting() {
        val header = getHeaderWithSubItems(1)
        val allItems = toArrayList(arrayOf<RecursivePictureMenu.Item>(header))
        AnkiAssert.assertDoesNotThrow { RecursivePictureMenu.removeFrom(allItems, getItemLinkingTo(R.string.link_anki_manual)) }
    }

    private fun getRecyclerViewFor(vararg items: RecursivePictureMenu.Item): RecyclerView {
        val itemList = ArrayList(listOf(*items))
        val menu = RecursivePictureMenu.createInstance(itemList, R.string.help)
        mActivity = openDialogFragmentUsingActivity(menu)
        return RecursivePictureMenuUtil.getRecyclerViewFor(menu)
    }

    private fun clickChildAtIndex(v: RecyclerView, index: Int) {
        advanceRobolectricLooperWithSleep()
        val childAt = v.getChildAt(index) // This is null without appropriate looper calls
        childAt.performClick()
    }

    private fun getItemLinkingTo(linkLocation: Int): RecursivePictureMenu.Item {
        return LinkItem(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, linkLocation)
    }

    private fun getHeaderWithSubItems(count: Int): ItemHeader {
        val items = arrayOfNulls<RecursivePictureMenu.Item>(count)
        for (i in 0 until count) {
            items[i] = getItemLinkingTo(R.string.link_anki)
        }
        return ItemHeader(R.string.help_item_ankidroid_manual, R.drawable.ic_manual_black_24dp, UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL, *items)
    }
}
