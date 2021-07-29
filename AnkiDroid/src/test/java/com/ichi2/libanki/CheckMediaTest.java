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

package com.ichi2.libanki;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.anki.RunInBackground;
import com.ichi2.async.CollectionTask;
import com.ichi2.async.TaskManager;
import com.ichi2.utils.Computation;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(AndroidJUnit4.class)
public class CheckMediaTest extends RobolectricTest {

    @Override
    protected boolean useInMemoryDatabase() {
        return false;
    }


    @Test
    @SuppressWarnings("deprecation") // #7108: AsyncTask
    @RunInBackground
    public void checkMediaWorksAfterMissingMetaTable() throws ExecutionException, InterruptedException {
        // 7421
        getCol().getMedia().getDb().getDatabase().execSQL("drop table meta");

        assertThat(getCol().getMedia().getDb().queryScalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta';"), is(0));

        CollectionTask<Void, Computation<List<List<String>>>> task = (CollectionTask<Void, Computation<List<List<String>>>>) TaskManager.launchCollectionTask(new CollectionTask.CheckMedia());

        task.get();

        assertThat(getCol().getMedia().getDb().queryScalar("SELECT count(*) FROM sqlite_master WHERE type='table' AND name='meta';"), is(1));
    }
}
