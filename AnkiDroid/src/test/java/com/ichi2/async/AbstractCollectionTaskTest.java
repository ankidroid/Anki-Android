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

package com.ichi2.async;

import com.ichi2.anki.RobolectricTest;

import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(AndroidJUnit4.class)
public abstract class AbstractCollectionTaskTest extends RobolectricTest {

    protected <Progress, Result> Result execute(CollectionTask.Task<Progress, Result> task) {
        CollectionTask<Progress, Progress, Result, Result> collectionTask = TaskManager.launchCollectionTask(task);
        try {
            return collectionTask.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected <Progress, Result> void waitForTask(CollectionTask.Task<Progress, Result> task, TaskListener<Progress, Result> listener) {
        TaskManager.launchCollectionTask(task, listener);

        waitForAsyncTasksToComplete();
    }
}
