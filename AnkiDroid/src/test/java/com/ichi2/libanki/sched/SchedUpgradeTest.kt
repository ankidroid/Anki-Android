/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.libanki.sched;

import com.ichi2.anki.RobolectricTest;
import com.ichi2.libanki.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Issue 8926 */
@RunWith(AndroidJUnit4.class)
public class SchedUpgradeTest extends RobolectricTest {

    @Override
    protected boolean useInMemoryDatabase() {
        // We want to be able to close the collection.
        return false;
    }


    @Test
    public void schedulerForNewCollectionIsV2() {
        assertThat("A new collection should be sched v2", getCol().getSched(), not(instanceOf(Sched.class)));
        assertThat(getCol().schedVer(), is(2));
    }

    @Test
    public void schedulerForV1CollectionIsV1() {
        // A V1 collection does not have the schedVer variable. This is not the same as a downgrade.
        getCol().remove_config("schedVer");
        getCol().close();


        assertThat("A collection with no schedVer should be v1", getCol().getSched(), instanceOf(Sched.class));
        assertThat(getCol().schedVer(), is(1));

    }
}
