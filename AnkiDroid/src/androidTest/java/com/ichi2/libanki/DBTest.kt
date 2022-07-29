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
import com.ichi2.anki.tests.InstrumentedTest
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.Assume.assumeThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class DBTest : InstrumentedTest() {
    /** mDatabase.disableWriteAheadLogging(); is called in DB init  */
    @Test
    fun writeAheadLoggingIsDisabled() {
        assumeThat(BackendFactory.defaultLegacySchema, equalTo(true))
        // An old comment noted that explicitly disabling the WAL was no longer necessary after API 16:
        // https://github.com/ankidroid/Anki-Android/commit/6e34663ba9d09dc8b023230811c3185b72ee7eec#diff-4fdbf41d84a547a45edad66ae1f543128d1118b0e831a12916b4fac11b483688

        // TODO: We haven't done this yet.
        // Please see the following for implementation details
        // https://github.com/ankidroid/Anki-Android/pull/7977#issuecomment-751780273
        // https://www.sqlite.org/pragma.html#pragma_journal_mode
        val journalMode = col.db.queryString("PRAGMA journal_mode")
        assertThat(journalMode.lowercase(Locale.ROOT), not(equalTo("wal")))
    }
}
