// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.AnalyticsEvent
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.editPreferences
import com.ichi2.testutils.targetContext
import com.ichi2.widget.AnalyticsWidgetProvider
import com.ichi2.widget.AppWidgetIds
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@RunWith(AndroidJUnit4::class)
class AnalyticalWidgetProviderTest : AndroidTest {
    @Before
    fun setUp() {
        mockkObject(Analytics)
        every { Analytics.send(any()) } answers { }
    }

    @After
    fun tearDown() {
        unmockkObject(Analytics)
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
    fun `update does not list storage directories`() {
        editPreferences { putString(CollectionHelper.PREF_COLLECTION_PATH, File(targetContext.filesDir, "AnkiDroid").path) }
        val context = StorageDirectoriesSpy(targetContext)
        val widgetProvider = TestWidgetProvider()

        widgetProvider.receiveUpdate(context)

        advanceMainLooperUntil { widgetProvider.updated }
        assertFalse(context.listedStorageDirectories, "storage directories were listed")
    }

    @Test
    fun `update is skipped until storage is decided`() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.receiveUpdate(targetContext)

        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(widgetProvider.updated, "widget updated before storage was decided")
    }

    private fun TestWidgetProvider.receiveUpdate(context: Context) =
        onReceive(
            context,
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

    private class StorageDirectoriesSpy(
        base: Context,
    ) : ContextWrapper(base) {
        @Volatile
        var listedStorageDirectories = false

        override fun getExternalFilesDirs(type: String?): Array<File> {
            listedStorageDirectories = true
            return super.getExternalFilesDirs(type)
        }
    }

    private class TestWidgetProvider : AnalyticsWidgetProvider() {
        @Volatile
        var updated = false

        override fun performUpdate(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
        ) {
            updated = true
        }
    }
}
