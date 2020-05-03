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

    protected CollectionTask.TaskData execute(int taskType) {
        CollectionTask task = CollectionTask.launchCollectionTask(taskType);
        try {
            return task.execute().get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected <T> T assertResultArraySingleton(CollectionTask.TaskData result, Class<T> clazz) {
        assertThat("The result object should be non-null", result.getObjArray(), notNullValue());
        assertThat("There should only be one result object", result.getObjArray(), arrayWithSize(1));
        assertThat(String.format("Result should be instance of type '%s'", clazz.getName()), result.getObjArray()[0], instanceOf(clazz));
        //noinspection unchecked
        return (T) result.getObjArray()[0];
    }
}
