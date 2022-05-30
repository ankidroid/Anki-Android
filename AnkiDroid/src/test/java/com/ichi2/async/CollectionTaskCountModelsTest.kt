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
import com.ichi2.anki.RunInBackground
import com.ichi2.async.CollectionTask.CountModels
import org.hamcrest.MatcherAssert.*
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

@RunWith(AndroidJUnit4::class)
class CollectionTaskCountModelsTest : AbstractCollectionTaskTest() {

    @Test
    @RunInBackground
    fun testModelsCount() {
        val task = CountModels()
        val initialCount = execute(task)!!.first.size

        addNonClozeModel("testModel", arrayOf<String>(), qfmt = "{{ front }}", afmt = "{{FrontSide}}\n\n<hr id=answer>\n\n{{ back }}")

        val finalCount = execute(task)!!.first.size
        assertEquals(initialCount + 1, finalCount)
    }
}
