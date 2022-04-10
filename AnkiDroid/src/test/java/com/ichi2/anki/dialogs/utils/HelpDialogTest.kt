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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.anki.dialogs.HelpDialog.createInstance
import com.ichi2.anki.dialogs.HelpDialog.createInstanceForSupportAnkiDroid
import com.ichi2.anki.dialogs.RecursivePictureMenu
import com.ichi2.anki.dialogs.utils.RecursivePictureMenuUtil.Companion.getRecyclerViewFor
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HelpDialogTest : RobolectricTest() {
    @Test
    @RunInBackground
    fun testMenuDoesNotCrash() {
        val dialog = createInstance(targetContext) as RecursivePictureMenu
        super.openDialogFragmentUsingActivity(dialog)
        val v = getRecyclerViewFor(dialog)
        MatcherAssert.assertThat(v.childCount, Matchers.`is`(4))
    }

    @Test
    @RunInBackground
    fun testMenuSupportAnkiDroidDoesNotCrash() {
        val dialog = createInstanceForSupportAnkiDroid(targetContext) as RecursivePictureMenu
        super.openDialogFragmentUsingActivity(dialog)
        val v = getRecyclerViewFor(dialog)
        MatcherAssert.assertThat(v.childCount, Matchers.`is`(5))
    }
}
