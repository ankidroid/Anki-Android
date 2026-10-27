// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.storage.grantedStoragePermissions
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.targetContext
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetIds
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class AnalyticalWidgetProviderTest : AndroidTest {
    @Before
    fun setUp() {
        mockkObject(Analytics)
        every { Analytics.send(any()) } answers { }
        mockkStatic(STORAGE_CHECKS)
    }

    @After
    fun tearDown() {
        unmockkObject(Analytics)
        unmockkStatic(STORAGE_CHECKS)
    }

    @Test
    fun testAnalyticsEventLogging() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.onEnabled(targetContext)

        verify {
            Analytics.send(AnalyticsEvent.WidgetEnabled("TestWidgetProvider"))
        }
    }

    @Test
    fun `storage check runs off the main thread`() {
        val checkedOnMainThread = CompletableFuture<Boolean>()
        every { grantedStoragePermissions(any()) } answers {
            checkedOnMainThread.complete(Looper.getMainLooper().isCurrentThread)
            true
        }
        val widgetProvider = TestWidgetProvider()

        widgetProvider.receiveUpdate()

        assertFalse(checkedOnMainThread.get(5, TimeUnit.SECONDS), "storage check ran on the main thread")
        advanceMainLooperUntil { widgetProvider.updatedOnMainThread != null }
        assertEquals(true, widgetProvider.updatedOnMainThread, "widget updated off the main thread")
    }

    private fun TestWidgetProvider.receiveUpdate() =
        onReceive(
            targetContext,
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(1)),
        )

    private fun advanceMainLooperUntil(condition: () -> Boolean) {
        val start = TimeSource.Monotonic.markNow()
        while (!condition()) {
            check(start.elapsedNow() < 5.seconds) { "condition not met after 5s" }
            Thread.sleep(10)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class TestWidgetProvider : AnalyticsWidgetProvider() {
        @Volatile
        var updatedOnMainThread: Boolean? = null

        override fun performUpdate(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
        ) {
            updatedOnMainThread = Looper.getMainLooper().isCurrentThread
        }
    }

    companion object {
        private const val STORAGE_CHECKS = "com.ichi2.anki.common.storage.LegacyStorageKt"
    }
}
