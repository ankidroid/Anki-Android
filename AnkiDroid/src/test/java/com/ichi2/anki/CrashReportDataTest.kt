// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CrashReportData.Companion.isDeckNotFoundInLimitsMapException
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CrashReportDataTest : JvmTest() {
    /** #15195: corrupt deck hierarchy raises 'deck not found in limits map' */
    @Test
    fun `deck not found in limits map regression test`() {
        triggerDeckNotFoundInLimitsMap()

        val ex = assertFailsWith<Exception> { col.sched.counts() }
        assertTrue(ex.isDeckNotFoundInLimitsMapException())
    }
}
