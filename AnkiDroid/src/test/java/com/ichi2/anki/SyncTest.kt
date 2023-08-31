/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.getString
import com.ichi2.testutils.targetContext
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class SyncTest : AndroidTest {

    @Test
    fun verifyCodeMessages() {
        val codeResponsePairs = hashMapOf(
            407 to getString(R.string.sync_error_407_proxy_required),
            409 to getString(R.string.sync_error_409),
            413 to getString(R.string.sync_error_413_collection_size),
            500 to getString(R.string.sync_error_500_unknown),
            501 to getString(R.string.sync_error_501_upgrade_required),
            502 to getString(R.string.sync_error_502_maintenance),
            503 to getString(R.string.sync_too_busy),
            504 to getString(R.string.sync_error_504_gateway_timeout)
        )

        for ((key, value) in codeResponsePairs) {
            Assert.assertEquals(getMessageFromSyncErrorCode(key), value)
        }
    }

    @Test
    fun verifyBadCodesNoMessage() {
        assertNull(getMessageFromSyncErrorCode(0))
        assertNull(getMessageFromSyncErrorCode(-1))
        assertNull(getMessageFromSyncErrorCode(1))
        assertNull(getMessageFromSyncErrorCode(Int.MIN_VALUE))
        assertNull(getMessageFromSyncErrorCode(Int.MAX_VALUE))
    }

    private fun getMessageFromSyncErrorCode(key: Int) = getMessageFromSyncErrorCode(targetContext.resources, key)
}
