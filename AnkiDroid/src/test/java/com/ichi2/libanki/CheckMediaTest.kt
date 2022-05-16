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
package com.ichi2.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.RunInBackground
import com.ichi2.async.CollectionTask
import com.ichi2.async.CollectionTask.CheckMedia
import com.ichi2.async.TaskManager
import com.ichi2.utils.KotlinCleanup
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ExecutionException

@KotlinCleanup("is -> equalTo")
@RunWith(AndroidJUnit4::class)
class CheckMediaTest : RobolectricTest() {
    override fun useInMemoryDatabase(): Boolean {
        return false
    }

    @Test // #7108: AsyncTask
    @RunInBackground
    @Suppress("deprecation")
    @Throws(ExecutionException::class, InterruptedException::class)
    fun checkMediaWorksAfterMissingMetaTable() {
        // 7421
        col.media.db.database.execSQL("drop table meta")
        assertThat(
            col.media.db.queryScalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta';"),
            `is`(0)
        )
        val task =
            TaskManager.launchCollectionTask(CheckMedia()) as CollectionTask<*, *>
        task.get()
        assertThat(
            col.media.db.queryScalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta';"),
            `is`(1)
        )
    }
}
