// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CrashReportData.Companion.isDeckNotFoundInLimitsMapException
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class CrashReportDataTest : RobolectricTest() {
    /** #15195: corrupt deck hierarchy raises 'deck not found in limits map' */
    @Test
    fun `deck not found in limits map regression test`() {
        triggerDeckNotFoundInLimitsMap()

        val ex = assertFailsWith<Exception> { col.sched.counts() }
        assertTrue(ex.isDeckNotFoundInLimitsMapException())
    }
}
