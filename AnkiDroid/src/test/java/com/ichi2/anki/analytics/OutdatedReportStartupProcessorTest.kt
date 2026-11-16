// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.acra.config.CoreConfiguration
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.LimiterData
import org.acra.prefs.SharedPreferencesFactory
import org.acra.startup.StartupProcessor
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class OutdatedReportStartupProcessorTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `an app update resets the crash report limits once`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        val config = CrashReportService.acraCoreConfigBuilder.build()
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        SharedPreferencesFactory(application, config).create().edit(commit = true) {
            putLong(ACRA.PREF_LAST_VERSION_NR, version - 1)
        }
        val limits = application.getFileStreamPath("ACRA-limiter.json")
        val sentReport = """[{"stacktrace":"java.lang.IllegalStateException"}]"""

        limits.writeText(sentReport)
        runStartupProcessors(application, config)
        assertEquals(0, LimiterData.load(application).reportMetadata.size, "An update must reset the limits")

        limits.writeText(sentReport)
        runStartupProcessors(application, config)
        assertEquals(1, LimiterData.load(application).reportMetadata.size, "Later startups must keep the limits")
    }

    private fun runStartupProcessors(
        context: Context,
        config: CoreConfiguration,
    ) {
        config.pluginLoader.loadEnabled(config, StartupProcessor::class.java).forEach {
            it.processReports(context, config, emptyList())
        }
    }
}
