/*
 *  Copyright (c) 2022 Saurav Rao <sauravrao637@gmail.com>
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
package com.ichi2.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountModelsTest : RobolectricTest() {

    @Test
    fun testModelsCount() = runTest {
        val initialCount = getAllModelsAndNotesCount().first.size /** number of models in the collection */

        addNonClozeModel("testModel", arrayOf("front", "back"), qfmt = "{{front}}", afmt = "{{FrontSide}}\n\n<hr id=answer>\n\n{{ back }}")

        val finalCount = getAllModelsAndNotesCount().first.size
        assertEquals(initialCount + 1, finalCount)
    }
}
