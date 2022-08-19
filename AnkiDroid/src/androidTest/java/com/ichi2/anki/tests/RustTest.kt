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
package com.ichi2.anki.tests

import com.ichi2.libanki.Storage
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.equalTo
import org.junit.Assume.assumeThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.IOException
import java.util.concurrent.TimeUnit

class RustTest : InstrumentedTest() {
    /** Ensure that the database isn't be locked
     * This happened before the database code was converted to use the Rust backend.
     */
    @get:Rule
    var timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    @Throws(BackendException::class, IOException::class)
    fun collectionIsVersion11AfterOpen() {
        assumeThat(BackendFactory.defaultLegacySchema, equalTo(true))
        // This test will be decommissioned, but before we get an upgrade strategy, we need to ensure we're not upgrading the database.
        val path = Shared.getTestFilePath(testContext, "initial_version_2_12_1.anki2")
        val collection = Storage.collection(testContext, path)
        val ver = collection.db.queryScalar("select ver from col")
        MatcherAssert.assertThat(ver, equalTo(11))
    }
}
