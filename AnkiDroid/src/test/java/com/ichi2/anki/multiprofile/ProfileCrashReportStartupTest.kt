// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ALWAYS
import com.ichi2.anki.common.crashreporting.CrashReporter.Companion.FEEDBACK_REPORT_ASK
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
import io.mockk.verify
import org.acra.ACRA
import org.acra.ACRAConstants
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.CollectorException
import org.acra.collector.PackageManagerCollector
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
import org.junit.Before
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class ProfileCrashReportStartupTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        assumeFalse("ACRA's report file names contain ':', which Windows doesn't allow", OS.WINDOWS.isRunning())
        application = ApplicationProvider.getApplicationContext()
        // Build AnkiDroid's real configuration without installing a process-wide crash handler.
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        // ToastInteraction always accepts; skip its private looper and display delay.
        mockkConstructor(ToastInteraction::class)
        every { anyConstructed<ToastInteraction>().performInteraction(any(), any(), any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `first startup of a new profile preserves another profile's pending approved report`() {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        markCurrentVersion(application, config)
        val report = pendingReport(application, approved = true)
        val newProfile = profile("p_new")
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(newProfile, scheduler)

        assertTrue(report.exists(), "Starting a new profile must not discard another profile's pending report")
    }

    @Test
    fun `Always in another profile does not approve a report awaiting consent`() {
        val askProfile = profile("p_ask")
        val alwaysProfile = profile("p_always")
        askProfile.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ASK) }
        alwaysProfile.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ALWAYS) }
        setProductionACRAConfig(alwaysProfile.sharedPrefs())
        val config = CrashReportService.acraCoreConfigBuilder.build()
        markCurrentVersion(askProfile, config)
        markCurrentVersion(alwaysProfile, config)
        // Back/dismiss leaves an Ask profile's report in the unapproved queue.
        val report = pendingReport(askProfile, approved = false)
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(alwaysProfile, scheduler)

        verify(exactly = 0) { scheduler.scheduleReports(report, any()) }
        assertTrue(report.exists(), "A report awaiting consent must remain pending")
    }

    @Test
    fun `Always still approves pending reports from the same profile`() {
        val profile = profile("p_always")
        profile.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ALWAYS) }
        setProductionACRAConfig(profile.sharedPrefs())
        markCurrentVersion(profile, CrashReportService.acraCoreConfigBuilder.build())
        val report = pendingReport(profile, approved = false)
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(profile, scheduler)

        verify(exactly = 1) { scheduler.scheduleReports(report, false) }
    }

    @Test
    fun `app update clears old pending reports when their profile opens after another profile`() {
        val laterProfile = profile("p_later")
        laterProfile.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ALWAYS) }
        setProductionACRAConfig(laterProfile.sharedPrefs())
        val config = CrashReportService.acraCoreConfigBuilder.build()
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        for (context in listOf(application, laterProfile)) {
            SharedPreferencesFactory(context, config).create().edit(commit = true) {
                putLong(ACRA.PREF_LAST_VERSION_NR, version - 1)
            }
        }
        val report = pendingReport(laterProfile, approved = false)
        val persister = CrashReportPersister()
        val data = persister.load(report).apply { put(ReportField.APP_VERSION_CODE, version - 1) }
        persister.store(data, report)
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        // The first startup after an update must not consume another profile's cleanup.
        processReports(application, scheduler)
        processReports(laterProfile, scheduler)

        assertFalse(report.exists(), "Reports from before the update must be purged for every profile")
        verify(exactly = 0) { scheduler.scheduleReports(report, any()) }
    }

    @Test
    fun `app update removes approved reports from the previous profile-local folder`() {
        val laterProfile = profile("p_later")
        val config = CrashReportService.acraCoreConfigBuilder.build()
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        for (context in listOf(application, laterProfile)) {
            SharedPreferencesFactory(context, config).create().edit(commit = true) {
                putLong(ACRA.PREF_LAST_VERSION_NR, version - 1)
            }
        }
        // Before the shared queue, approved reports were stored inside each profile.
        val legacyFolder = File(application.dataDir, "p_later/ACRA-approved").apply { mkdirs() }
        val timestamp = SimpleDateFormat(ACRAConstants.DATE_TIME_FORMAT_STRING, Locale.ENGLISH).format(Date(0))
        val report = File(legacyFolder, timestamp + ACRAConstants.REPORTFILE_EXTENSION)
        CrashReportPersister().store(
            CrashReportData().apply {
                put(ReportField.APP_VERSION_CODE, version - 1)
                put(ReportField.FILE_PATH, laterProfile.filesDir.absolutePath)
                put(ReportField.USER_CRASH_DATE, timestamp)
            },
            report,
        )
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(application, scheduler)
        processReports(laterProfile, scheduler)

        assertFalse(report.exists(), "An update must remove obsolete reports from the old profile-local approved queue")
    }

    @Test
    fun `app update removes reports whose package version could not be collected`() {
        val laterProfile = profile("p_later")
        laterProfile.sharedPrefs().edit(commit = true) { putString(FEEDBACK_REPORT_KEY, FEEDBACK_REPORT_ALWAYS) }
        setProductionACRAConfig(laterProfile.sharedPrefs())
        val config = CrashReportService.acraCoreConfigBuilder.build()
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        val version = PackageInfoCompat.getLongVersionCode(packageInfo)
        packageInfo.lastUpdateTime = TimeUnit.DAYS.toMillis(1)
        shadowOf(application.packageManager).installPackage(packageInfo)
        for (context in listOf(application, laterProfile)) {
            SharedPreferencesFactory(context, config).create().edit(commit = true) {
                putLong(ACRA.PREF_LAST_VERSION_NR, version - 1)
            }
        }
        val timestamp = SimpleDateFormat(ACRAConstants.DATE_TIME_FORMAT_STRING, Locale.ENGLISH).format(Date(0))
        val report = File(ReportLocator(laterProfile).unapprovedFolder, timestamp + ACRAConstants.REPORTFILE_EXTENSION)
        val data =
            CrashReportData().apply {
                put(ReportField.FILE_PATH, laterProfile.filesDir.absolutePath)
                put(ReportField.USER_CRASH_DATE, timestamp)
                put(ReportField.STACK_TRACE, "java.lang.IllegalStateException: crash before update")
            }
        val unavailablePackageManager = mockk<PackageManager>()
        every { unavailablePackageManager.getPackageInfo(any<String>(), any<Int>()) } throws RuntimeException("Package manager has died")
        val crashContext =
            object : ContextWrapper(laterProfile) {
                override fun getPackageManager(): PackageManager = unavailablePackageManager
            }
        // ACRA retains the other fields when a collector fails, so this is a valid pending report.
        assertFailsWith<CollectorException> { PackageManagerCollector().collect(crashContext, config, ReportBuilder(), data) }
        assertFalse(data.containsKey(ReportField.APP_VERSION_CODE))
        CrashReportPersister().store(data, report)
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(application, scheduler)
        processReports(laterProfile, scheduler)

        assertFalse(report.exists(), "An update must discard old reports even when version collection failed")
        verify(exactly = 0) { scheduler.scheduleReports(report, any()) }
    }

    @Test
    fun `a stale shared marker does not discard another profile's current-version approved report`() {
        val config = CrashReportService.acraCoreConfigBuilder.build()
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        SharedPreferencesFactory(application, config).create().edit(commit = true) {
            putLong(ACRA.PREF_LAST_VERSION_NR, version - 1)
        }
        // An early crash was approved before startup persisted the new version marker.
        val report = pendingReport(application, approved = true)
        val newProfile = profile("p_after_crash")
        val scheduler = mockk<SchedulerStarter>(relaxed = true)

        processReports(newProfile, scheduler)

        assertTrue(report.exists(), "A stale marker must not delete another profile's current-version report")
    }

    private fun profile(id: String): Context = ProfileContextWrapper.create(application, ProfileId(id), File(application.dataDir, id))

    private fun pendingReport(
        context: Context,
        approved: Boolean,
    ): File {
        val locator = ReportLocator(context)
        val folder = if (approved) locator.approvedFolder else locator.unapprovedFolder
        val timestamp = SimpleDateFormat(ACRAConstants.DATE_TIME_FORMAT_STRING, Locale.ENGLISH).format(Date(0))
        val report = File(folder, timestamp + ACRAConstants.REPORTFILE_EXTENSION)
        val data =
            CrashReportData().apply {
                put(ReportField.FILE_PATH, context.filesDir.absolutePath)
                put(ReportField.USER_CRASH_DATE, timestamp)
                put(
                    ReportField.APP_VERSION_CODE,
                    PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0)),
                )
            }
        CrashReportPersister().store(data, report)
        return report
    }

    private fun markCurrentVersion(
        context: Context,
        config: CoreConfiguration,
    ) {
        val version = PackageInfoCompat.getLongVersionCode(application.packageManager.getPackageInfo(application.packageName, 0))
        SharedPreferencesFactory(context, config).create().edit(commit = true) {
            putLong(ACRA.PREF_LAST_VERSION_NR, version)
        }
    }

    private fun processReports(
        context: Context,
        scheduler: SchedulerStarter,
    ) {
        val builder = CrashReportService.acraCoreConfigBuilder
        val loader = StartupThreadTracker(builder.build().pluginLoader)
        val config = builder.withPluginLoader(loader).build()
        StartupProcessorExecutor(context, config, scheduler).processReports(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(loader.started.await(10, TimeUnit.SECONDS), "ACRA startup worker did not start")
        loader.worker.join(10_000)
        assertFalse(loader.worker.isAlive, "ACRA startup worker did not finish")
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
