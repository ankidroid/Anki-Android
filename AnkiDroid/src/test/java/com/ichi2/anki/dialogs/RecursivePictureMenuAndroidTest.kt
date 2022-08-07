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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecursivePictureMenuAndroidTest : RecursivePictureMenuUtilTest() {
    @Test
    @RunInBackground
    fun testNormalStartupSelectingItem() {
        val linkedItem = getItemLinkingTo(R.string.link_manual)
        val v = getRecyclerViewFor(linkedItem)
        clickChildAtIndex(v, 0)
        MatcherAssert.assertThat(activity!!.lastUrlOpened, Matchers.equalTo(getResourceString(R.string.link_manual)))
    }

    @Test
    @RunInBackground
    fun testSelectingHeader() {
        val numberOfChildItems = 2
        val header: RecursivePictureMenu.Item = getHeaderWithSubItems(numberOfChildItems)
        val v = getRecyclerViewFor(header)
        clickChildAtIndex(v, 0)
        val currentMenu = activity!!.lastShownDialogFragment as RecursivePictureMenu
        val rv = RecursivePictureMenuUtil.getRecyclerViewFor(currentMenu)
        MatcherAssert.assertThat("Unexpected number of items - check the adapter", rv.childCount, Matchers.equalTo(numberOfChildItems))
    }

    @Test
    @Ignore("Not implemented")
    fun removeFromRoot() {
        val header: RecursivePictureMenu.Item = getHeaderWithSubItems(1)
        val allItems = arrayListOf(header)
        RecursivePictureMenu.removeFrom(allItems, header)

        // Do we return, or check to see if the list is mutated?
    }
}
