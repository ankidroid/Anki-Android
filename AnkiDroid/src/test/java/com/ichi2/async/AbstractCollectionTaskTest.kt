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
import com.ichi2.utils.KotlinCleanup
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
abstract class AbstractCollectionTaskTest : RobolectricTest() {
    @Suppress("deprecation") // #7108: AsyncTask
    @KotlinCleanup("see if we can make return value non-null")
    protected fun <Progress, Result> execute(task: TaskDelegate<Progress, Result>?): Result? {
        @Suppress("UNCHECKED_CAST")
        val collectionTask = TaskManager.launchCollectionTask(task) as CollectionTask<Progress, Result>
        return try {
            collectionTask.get()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    protected fun <Progress, Result> waitForTask(task: TaskDelegate<Progress, Result>, listener: TaskListener<Progress, Result>?) {
        TaskManager.launchCollectionTask(task, listener)
        waitForAsyncTasksToComplete()
    }
}
