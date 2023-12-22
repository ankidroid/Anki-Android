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
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.dialogs.utils.FragmentTestActivity
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil
import java.util.ArrayList

open class RecursivePictureMenuUtilTest : RobolectricTest() {
    var activity: FragmentTestActivity? = null

    fun getRecyclerViewFor(vararg items: RecursivePictureMenu.Item): RecyclerView {
        val itemList = ArrayList(listOf(*items))
        val menu = RecursivePictureMenu.createInstance(itemList, R.string.help)
        activity = openDialogFragmentUsingActivity(menu)
        return RecursivePictureMenuUtil.getRecyclerViewFor(menu)
    }

    fun clickChildAtIndex(
        v: RecyclerView,
        index: Int,
    ) {
        RobolectricTest.advanceRobolectricLooperWithSleep()
        val childAt = v.getChildAt(index) // This is null without appropriate looper calls
        childAt.performClick()
    }

    fun getItemLinkingTo(linkLocation: Int): RecursivePictureMenu.Item {
        return HelpDialog.LinkItem(
            R.string.help_item_ankidroid_manual,
            R.drawable.ic_manual_black_24dp,
            UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL,
            linkLocation,
        )
    }

    fun getHeaderWithSubItems(count: Int): RecursivePictureMenu.ItemHeader {
        val items = arrayOfNulls<RecursivePictureMenu.Item>(count)
        for (i in 0 until count) {
            items[i] = getItemLinkingTo(R.string.link_manual)
        }
        return RecursivePictureMenu.ItemHeader(
            R.string.help_item_ankidroid_manual,
            R.drawable.ic_manual_black_24dp,
            UsageAnalytics.Actions.OPENED_ANKIDROID_MANUAL,
            *items,
        )
    }
}
