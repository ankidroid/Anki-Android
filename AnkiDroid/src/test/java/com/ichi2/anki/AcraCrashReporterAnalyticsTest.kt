// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_KEY
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_NEVER
import com.ichi2.anki.common.preferences.sharedPrefs
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import net.ankiweb.rsdroid.exceptions.BackendSyncException.BackendSyncServerMessageException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcraCrashReporterAnalyticsTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        targetContext.sharedPrefs().edit { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_NEVER) }
        mockkObject(Analytics)
        every { Analytics.sendAnalyticsException(any(), any()) } returns Unit
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkObject(Analytics)
    }

    @Test
    fun `an exception carrying a sync server message is not reported`() {
        val withPii = Exception("outer", BackendSyncServerMessageException(backendError {}))

        CrashReportService.sendExceptionReport(withPii, "test", null, false, targetContext)

        verify(exactly = 0) { Analytics.sendAnalyticsException(any(), any()) }
    }

    @Test
    fun `an ordinary exception is still reported`() {
        CrashReportService.sendExceptionReport(IllegalStateException("safe"), "test", null, false, targetContext)

        verify(exactly = 1) { Analytics.sendAnalyticsException(any(), any()) }
    }
}
