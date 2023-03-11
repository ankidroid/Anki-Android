/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki

import android.database.sqlite.SQLiteDatabaseLockedException
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.testutils.AnkiAssert.assertDoesNotThrow
import com.ichi2.testutils.createTransientFile
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class StorageLockingTest : RobolectricTest() {

    private var toCleanup: Collection? = null

    @After
    fun after() {
        toCleanup?.close()
        Storage.unlockCollection()
    }

    @Test
    fun test_normal_open() {
        assertDoesNotThrow { successfulOpen() }
    }

    @Test
    fun open_fails_if_locked() {
        Storage.lockCollection()
        assertFailsWith<SQLiteDatabaseLockedException> { successfulOpen() }
    }

    @Test
    fun lock_sets_value() {
        Storage.lockCollection()
        assertThat("locking the collection sets isLocked", Storage.isLocked, equalTo(true))
        Storage.unlockCollection()
        assertThat("unlocking the collection sets isLocked", Storage.isLocked, equalTo(false))
    }

    @Test
    fun unlock_works() {
        open_fails_if_locked()
        Storage.unlockCollection()
        test_normal_open()
    }

    @Test
    fun lock_does_nothing_if_open() {
        assertDoesNotThrow {
            successfulOpen()
            Storage.lockCollection()
        }
    }

    @Test
    fun collection_unlocked_by_default() {
        assertThat("by default, collection should be unlocked", Storage.isLocked, equalTo(false))
    }

    /** Opens a valid collection */
    private fun successfulOpen() {
        toCleanup = Storage.collection(getApplicationContext(), createTransientFile(extension = "anki2").path)
    }
}
