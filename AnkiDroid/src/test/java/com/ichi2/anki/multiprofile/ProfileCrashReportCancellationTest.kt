// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multiprofile

import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.acraCoreConfigBuilder
import com.ichi2.anki.analytics.AnkiDroidCrashReportDialog
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.initializeAcraCrashReporter
import com.ichi2.testutils.EmptyApplication
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.data.CrashReportData
import org.acra.file.CrashReportPersister
import org.acra.file.ReportLocator
import org.acra.interaction.DialogInteraction
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(application = EmptyApplication::class)
class ProfileCrashReportCancellationTest {
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cancelling a default-profile report preserves another profile's deferred report`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        val profile = ProfileContextWrapper.create(application, ProfileId("p_ask"), File(application.dataDir, "p_ask"))
        val deferred = report(profile, "deferred.stacktrace")
        val sameProfile = report(application, "same-profile.stacktrace")
        val current = report(application, "current.stacktrace")
        // Bulk cancellation processes the older deferred report before the current one.
        assertTrue(deferred.setLastModified(1_000))
        assertTrue(sameProfile.setLastModified(1_500))
        assertTrue(current.setLastModified(2_000))
        val intent =
            Intent(application, AnkiDroidCrashReportDialog::class.java)
                .putExtra(DialogInteraction.EXTRA_REPORT_CONFIG, CrashReportService.acraCoreConfigBuilder.build())
                .putExtra(DialogInteraction.EXTRA_REPORT_FILE, current)

        Robolectric.buildActivity(AnkiDroidCrashReportDialog::class.java, intent).setup().use { controller ->
            controller.get().onClick(mockk<DialogInterface>(relaxed = true), DialogInterface.BUTTON_NEGATIVE)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (current.exists() && System.nanoTime() < deadline) Thread.sleep(10)

            assertFalse(current.exists(), "Cancel must remove the displayed report")
            assertFalse(sameProfile.exists(), "Cancel must still clear older reports from the same profile")
            assertTrue(deferred.exists(), "Cancel must preserve a different profile's deferred report")
        }
    }

    private fun report(
        context: Context,
        name: String,
    ): File {
        val file = File(ReportLocator(context).unapprovedFolder, name)
        val data = CrashReportData().apply { put(ReportField.FILE_PATH, context.filesDir.absolutePath) }
        CrashReportPersister().store(data, file)
        return file
    }
}
