// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.app.Application
import android.os.Looper
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ALWAYS
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_KEY
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.anki.setProductionACRAConfig
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.common.OS
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.acra.ACRAConstants
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.acra.file.CrashReportPersister
import org.acra.file.ReportLocator
import org.acra.interaction.ToastInteraction
import org.acra.plugins.Plugin
import org.acra.plugins.PluginLoader
import org.acra.prefs.SharedPreferencesFactory
import org.acra.scheduler.SchedulerStarter
import org.acra.startup.StartupProcessor
import org.acra.startup.StartupProcessorExecutor
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class CrashReportUpdateSchedulingTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `startup removes obsolete reports before scheduling`() {
        assumeFalse("ACRA report names contain a colon", OS.WINDOWS.isRunning())
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        application.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ALWAYS) }
        setProductionACRAConfig(application.sharedPrefs())
        mockkConstructor(ToastInteraction::class)
        every { anyConstructed<ToastInteraction>().performInteraction(any(), any(), any()) } returns true
        val builder = CrashReportService.acraCoreConfigBuilder
        val loader = StartupThreadTracker(builder.build().pluginLoader)
        val config = builder.withPluginLoader(loader).build()
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        SharedPreferencesFactory(application, config).create().edit(commit = true) { putLong(ACRA.PREF_LAST_VERSION_NR, version - 1) }
        val locator = ReportLocator(application)

        fun report(
            folder: File,
            timestamp: Long,
            appVersion: Long,
        ): File {
            val name = SimpleDateFormat(ACRAConstants.DATE_TIME_FORMAT_STRING, Locale.ENGLISH).format(Date(timestamp))
            return File(folder, name + ACRAConstants.REPORTFILE_EXTENSION).also {
                CrashReportPersister().store(
                    CrashReportData().apply {
                        put(ReportField.FILE_PATH, application.filesDir.absolutePath)
                        put(ReportField.USER_CRASH_DATE, name)
                        put(ReportField.APP_VERSION_CODE, appVersion)
                    },
                    it,
                )
            }
        }
        val obsolete = report(locator.approvedFolder, 0, version - 1)
        report(locator.unapprovedFolder, 1000, version)
        val scheduledBeforeCleanup = AtomicBoolean(false)
        val scheduler = mockk<SchedulerStarter>(relaxed = true)
        every { scheduler.scheduleReports(any(), any()) } answers { if (obsolete.exists()) scheduledBeforeCleanup.set(true) }

        StartupProcessorExecutor(application, config, scheduler).processReports(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(loader.started.await(10, TimeUnit.SECONDS), "ACRA startup worker did not start")
        loader.worker.join(10_000)
        assertFalse(loader.worker.isAlive, "ACRA startup worker did not finish")

        assertFalse(scheduledBeforeCleanup.get(), "The sender must not be scheduled while obsolete reports remain")
    }

    private class StartupThreadTracker(
        private val delegate: PluginLoader,
    ) : PluginLoader by delegate {
        val started = CountDownLatch(1)
        lateinit var worker: Thread

        override fun <T : Plugin> loadEnabled(
            config: CoreConfiguration,
            clazz: Class<T>,
        ): List<T> {
            if (clazz == StartupProcessor::class.java) {
                worker = Thread.currentThread()
                started.countDown()
            }
            return delegate.loadEnabled(config, clazz)
        }
    }
}
