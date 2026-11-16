// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_NEVER
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.anki.servicelayer.ThrowableFilterService
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.acra.file.CrashReportPersister
import org.acra.file.ReportLocator
import org.acra.sender.HttpSender
import org.acra.sender.SendingConductor
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class ProfileCrashReportNeverTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `Never stops sending that profile's queued reports`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        mockkObject(AnkiDroidUsageAnalytics)
        every { AnkiDroidUsageAnalytics.reinitialize(any()) } returns Unit
        mockkObject(ThrowableFilterService)
        every { ThrowableFilterService.reInitialize() } returns Unit
        mockkConstructor(HttpSender::class)
        every { anyConstructed<HttpSender>().send(any(), any(), any()) } returns Unit
        val never = ProfileContextWrapper.create(application, ProfileId("p_never"), File(application.dataDir, "p_never"))
        val other = ProfileContextWrapper.create(application, ProfileId("p_other"), File(application.dataDir, "p_other"))
        queuedReport(never, "never.stacktrace")
        queuedReport(other, "other.stacktrace")

        CrashReportService.onPreferenceChanged(never, FEEDBACK_REPORT_NEVER)
        val config = CrashReportService.acraCoreConfigBuilder.withSendReportsInDevMode(true).build()
        SendingConductor(application, config).sendReports(false, Bundle())

        verify(exactly = 0) { anyConstructed<HttpSender>().send(any(), match { it.isFrom(never) }, any()) }
        verify(exactly = 1) { anyConstructed<HttpSender>().send(any(), match { it.isFrom(other) }, any()) }
    }

    private fun queuedReport(
        context: Context,
        name: String,
    ) {
        val data = CrashReportData().apply { put(ReportField.FILE_PATH, context.filesDir.absolutePath) }
        CrashReportPersister().store(data, File(ReportLocator(context).approvedFolder, name))
    }

    private fun CrashReportData.isFrom(context: Context) = getString(ReportField.FILE_PATH) == context.filesDir.absolutePath
}
