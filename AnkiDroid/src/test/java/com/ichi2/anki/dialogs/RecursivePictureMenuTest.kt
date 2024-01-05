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
import com.ichi2.testutils.AnkiAssert
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecursivePictureMenuTest : RecursivePictureMenuUtilTest() {
    @Test
    fun removeChild() {
        val header = getHeaderWithSubItems(1)
        val child = header.children[0]
        val allItems = arrayListOf(header)
        RecursivePictureMenu.removeFrom(allItems, child)
        MatcherAssert.assertThat("child should be removed", header.children, Matchers.hasSize(0))
    }

    @Test
    fun removeNotExisting() {
        val header = getHeaderWithSubItems(1)
        val allItems = arrayListOf(header)
        AnkiAssert.assertDoesNotThrow { RecursivePictureMenu.removeFrom(allItems, getItemLinkingTo(R.string.link_anki_manual)) }
    }
}
