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
import com.ichi2.testutils.assertThrows
import com.ichi2.testutils.createTransientFile
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageLockingTest : RobolectricTest() {

    private var toCleanup: Collection? = null
    private var lock: Any? = null
    @After
    fun after() {
        toCleanup?.close()
        if (lock != null) {
            unlockCollection()
        }
    }

    @Test
    fun test_normal_open() {
        assertDoesNotThrow { successfulOpen() }
    }

    @Test
    fun open_fails_if_locked() {
        lockCollection()
        assertThrows<SQLiteDatabaseLockedException> { successfulOpen() }
    }

    @Test
    fun lock_sets_value() {
        lockCollection()
        assertThat("locking the collection sets isLocked", Storage.isLocked(), equalTo(true))
        unlockCollection()
        assertThat("unlocking the collection sets isLocked", Storage.isLocked(), equalTo(false))
    }

    @Test
    fun unlock_works() {
        open_fails_if_locked()
        unlockCollection()
        test_normal_open()
    }

    @Test
    fun lock_does_nothing_if_open() {
        assertDoesNotThrow {
            successfulOpen()
            lockCollection()
        }
    }

    @Test
    fun two_locks_fails() {
        lockCollection()
        assertThat("second lock should fail", Storage.lockCollection(), equalTo(null))
    }

    @Test
    fun two_unlocks_fails() {
        lockCollection()
        // Make a copy because [unlockCollection] set [lock] to null.
        val local_lock = lock
        unlockCollection()
        assertThat("second lock should fail", Storage.unlockCollection(local_lock!!), equalTo(false))
    }

    @Test
    fun unlocks_wrong_object_fails() {
        lockCollection()
        assertThat("second lock should fail", Storage.unlockCollection(Object()), equalTo(false))
    }

    @Test
    fun collection_unlocked_by_default() {
        assertThat("by default, collection should be unlocked", Storage.isLocked(), equalTo(false))
    }

    /** Opens a valid collection */
    private fun successfulOpen() {
        toCleanup = Storage.Collection(getApplicationContext(), createTransientFile(extension = "anki2").path)
    }

    /** Lock the collection and store the lock. Collection should be unlocked.*/
    private fun lockCollection() {
        assertThat("no two locks should occur", lock, equalTo(null))
        lock = Storage.lockCollection()
        assertThat("A lock should succeed", lock, not(equalTo(null)))
    }

    /** Unlock the lock with currently held collection.*/
    private fun unlockCollection() {
        assertThat("A lock should occur when unlocking exists", lock, not(equalTo(null)))
        assertThat("unlock should succeed", Storage.unlockCollection(lock!!), equalTo(true))
        lock = null
    }
}
