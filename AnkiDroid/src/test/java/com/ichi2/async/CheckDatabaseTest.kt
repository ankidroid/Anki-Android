/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.async

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.async.CollectionTask.CheckDatabase
import com.ichi2.testutils.CollectionUtils
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckDatabaseTest : RobolectricTest() {
    @Test
    fun checkDatabaseWithLockedCollectionReturnsLocked() {
        lockDatabase()
        val result = CheckDatabase().execTask(col, mock())
        assertFalse(result.first, "The result should specify a failure")
        val checkDbResult = result.second!!
        assertTrue(checkDbResult.databaseLocked, "The result should specify the database was locked")
    }

    private fun lockDatabase() {
        CollectionUtils.lockDatabase(col)
    }
}
