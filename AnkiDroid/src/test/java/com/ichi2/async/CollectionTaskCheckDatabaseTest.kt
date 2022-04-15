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

import android.util.Pair;

import com.ichi2.anki.RunInBackground;
import com.ichi2.libanki.Collection;
import com.ichi2.testutils.CollectionUtils;

import org.junit.Ignore;
import org.junit.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CollectionTaskCheckDatabaseTest extends AbstractCollectionTaskTest {

    @Test
    @RunInBackground
    public void checkDatabaseWithLockedCollectionReturnsLocked() {
        lockDatabase();

        advanceRobolectricLooper();
        Pair<Boolean, Collection.CheckDatabaseResult> result = super.execute(new CollectionTask.CheckDatabase());

        assertThat("The result should specify a failure", result.first, is(false));
        Collection.CheckDatabaseResult checkDbResult = result.second;

        assertThat("The result should specify the database was locked", checkDbResult.getDatabaseLocked());
    }

    private void lockDatabase() {
        CollectionUtils.lockDatabase(getCol());
    }

}
