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
package com.ichi2.libanki.sched

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Test
import org.junit.runner.RunWith

/** Issue 8926  */
@RunWith(AndroidJUnit4::class)
class SchedUpgradeTest : RobolectricTest() {
    override fun useInMemoryDatabase(): Boolean {
        // We want to be able to close the collection.
        return false
    }

    @Test
    fun schedulerForNewCollectionIsV2() {
        assertThat(
            "A new collection should be sched v2",
            col.sched,
            not(
                instanceOf(
                    Sched::class.java
                )
            )
        )
        assertThat(col.schedVer(), equalTo(2))
    }

    @Test
    fun schedulerForV1CollectionIsV1() {
        // A V1 collection does not have the schedVer variable. This is not the same as a downgrade.
        col.config.remove("schedVer")
        col.close()

        assertThat(
            "A collection with no schedVer should be v1",
            col.sched,
            instanceOf(
                Sched::class.java
            )
        )
        assertThat(col.schedVer(), equalTo(1))
    }
}
