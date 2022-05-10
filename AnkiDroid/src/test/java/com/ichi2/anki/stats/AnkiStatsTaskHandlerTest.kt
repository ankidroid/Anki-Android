/****************************************************************************************
 * Copyright (c) 2021 Rodrigo Silva <dev,rodrigosp@gmail.com>                              *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/
package com.ichi2.anki.stats

import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.stats.AnkiStatsTaskHandler.Companion.createReviewSummaryStatistics
import com.ichi2.annotations.NeedsTest
import com.ichi2.libanki.Collection
import com.ichi2.utils.KotlinCleanup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import java.util.concurrent.ExecutionException

@RunWith(AndroidJUnit4::class)
@KotlinCleanup("make mocks lateinit")
@KotlinCleanup("`when` -> whenever")
class AnkiStatsTaskHandlerTest : RobolectricTest() {
    @Mock
    private val mCol: Collection? = null

    @Mock
    private val mView: TextView? = null
    @Before
    override fun setUp() {
        MockitoAnnotations.openMocks(this)
        `when`(mCol!!.db).thenReturn(null)
        `when`(mCol.dbClosed).thenReturn(true)
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    @NeedsTest("explain this test")
    @Suppress("deprecation") // #7108: AsyncTask
    fun testCreateReviewSummaryStatistics() {
        verify(mCol, atMost(0))!!.db
        val result = createReviewSummaryStatistics(mCol!!, mView!!)
        result.get()
        advanceRobolectricLooper()
        verify(mCol, atLeast(0))!!.db
        verify(mCol, atLeast(1))!!.dbClosed
    }
}
