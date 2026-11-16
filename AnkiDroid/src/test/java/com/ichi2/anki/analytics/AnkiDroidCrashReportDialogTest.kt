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
    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `cancel removes the pending reports of the profile that crashed`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(ACRA::class)
        every { ACRA.init(any<Application>(), any<CoreConfigurationBuilder>(), any()) } returns Unit
        with(application) { initializeAcraCrashReporter() }
        val profile = ProfileContextWrapper.create(application, ProfileId("p_ask"), File(application.dataDir, "p_ask"))
        val folder = ReportLocator(profile).unapprovedFolder
        val older = File(folder, "older.stacktrace").apply { writeText("{}") }
        val current = File(folder, "current.stacktrace").apply { writeText("{}") }
        val intent =
            Intent(application, AnkiDroidCrashReportDialog::class.java)
                .putExtra(DialogInteraction.EXTRA_REPORT_CONFIG, CrashReportService.acraCoreConfigBuilder.build())
                .putExtra(DialogInteraction.EXTRA_REPORT_FILE, current)

        Robolectric.buildActivity(AnkiDroidCrashReportDialog::class.java, intent).setup().use { controller ->
            controller.get().onClick(mockk<DialogInterface>(relaxed = true), DialogInterface.BUTTON_NEGATIVE)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while ((current.exists() || older.exists()) && System.nanoTime() < deadline) Thread.sleep(10)

            assertFalse(current.exists(), "Cancel must remove the displayed report")
            assertFalse(older.exists(), "Cancel must remove older reports from the same profile")
        }
    }
}
