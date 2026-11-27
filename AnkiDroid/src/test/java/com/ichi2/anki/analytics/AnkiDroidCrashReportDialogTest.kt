// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.analytics

import android.app.Application
import android.content.DialogInterface
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.anki.multiprofile.ProfileContextWrapper
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.acra.config.CoreConfigurationBuilder
import org.acra.file.ReportLocator
import org.acra.interaction.DialogInteraction
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class AnkiDroidCrashReportDialogTest {
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cancel removes the pending reports of the profile that crashed`() {
        val profile = ProfileContextWrapper.create(application, ProfileId("p_ask"), File(application.dataDir, "p_ask"))
        val folder = ReportLocator(profile).unapprovedFolder
        val older = File(folder, "older.stacktrace").apply { writeText("{}") }
        val current = File(folder, "current.stacktrace").apply { writeText("{}") }

        Robolectric.buildActivity(AnkiDroidCrashReportDialog::class.java, dialogIntent(current)).setup().use { controller ->
            controller.get().cancel()
            waitForDeletion(current, older)

            assertFalse(current.exists(), "Cancel must remove the displayed report")
            assertFalse(older.exists(), "Cancel must remove older reports from the same profile")
        }
    }

    @Test
    fun `cancel on a reused dialog removes each profile's reports`() {
        val (earlier, current) = reportsFromTwoProfiles()

        Robolectric.buildActivity(AnkiDroidCrashReportDialog::class.java, dialogIntent(earlier)).setup().use { controller ->
            controller.newIntent(dialogIntent(current))
            controller.get().cancel()
            waitForDeletion(earlier, current)

            assertFalse(current.exists(), "Cancel must remove the report the dialog was last opened for")
            assertFalse(earlier.exists(), "Cancel must remove the report the dialog was first opened for")
        }
    }

    @Test
    fun `cancel after recreation removes each profile's reports`() {
        val (earlier, current) = reportsFromTwoProfiles()

        Robolectric.buildActivity(AnkiDroidCrashReportDialog::class.java, dialogIntent(earlier)).setup().use { controller ->
            controller.newIntent(dialogIntent(current))
            controller.recreate()
            controller.get().cancel()
            waitForDeletion(earlier, current)

            assertFalse(current.exists(), "Cancel must remove the report the dialog was last opened for")
            assertFalse(earlier.exists(), "Cancel must remove the report the dialog was first opened for")
        }
    }

    private fun reportsFromTwoProfiles(): Pair<File, File> {
        val otherProfile = ProfileContextWrapper.create(application, ProfileId("p_other"), File(application.dataDir, "p_other"))
        val earlier = File(ReportLocator(otherProfile).unapprovedFolder, "earlier.stacktrace").apply { writeText("{}") }
        val current = File(ReportLocator(application).unapprovedFolder, "current.stacktrace").apply { writeText("{}") }
        return earlier to current
    }

    private fun dialogIntent(report: File) =
        Intent(application, AnkiDroidCrashReportDialog::class.java)
            .putExtra(DialogInteraction.EXTRA_REPORT_CONFIG, CrashReportService.acraCoreConfigBuilder.build())
            .putExtra(DialogInteraction.EXTRA_REPORT_FILE, report)

    private fun AnkiDroidCrashReportDialog.cancel() = onClick(mockk<DialogInterface>(relaxed = true), DialogInterface.BUTTON_NEGATIVE)

    private fun waitForDeletion(vararg reports: File) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (reports.any { it.exists() } && System.nanoTime() < deadline) Thread.sleep(10)
    }
}
