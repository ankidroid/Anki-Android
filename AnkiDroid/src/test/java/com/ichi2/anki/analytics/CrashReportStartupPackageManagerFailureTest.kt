// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.startup.StartupProcessor
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class CrashReportStartupPackageManagerFailureTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @SuppressLint("NewApi")
    fun `startup processors tolerate unavailable package information`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        val unavailable = mockk<PackageManager>()
        every { unavailable.getPackageInfo(any<String>(), any<Int>()) } throws
            PackageManager.NameNotFoundException("temporarily unavailable")
        every {
            unavailable.getPackageInfo(any<String>(), any<PackageManager.PackageInfoFlags>())
        } throws PackageManager.NameNotFoundException("temporarily unavailable")
        val context =
            object : ContextWrapper(application) {
                override fun getPackageManager(): PackageManager = unavailable
            }
        val config = CrashReportService.acraCoreConfigBuilder.build()

        // StartupProcessorExecutor invokes these without catching exceptions on its worker thread.
        config.pluginLoader.loadEnabled(config, StartupProcessor::class.java).forEach {
            it.processReports(context, config, emptyList())
        }
    }
}
