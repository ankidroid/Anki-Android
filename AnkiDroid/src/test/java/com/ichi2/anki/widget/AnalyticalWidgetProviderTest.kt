// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

import android.appwidget.AppWidgetManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.analytics.Analytics
import com.ichi2.anki.common.analytics.UsageAnalytics
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

@RunWith(AndroidJUnit4::class)
class AnalyticalWidgetProviderTest : RobolectricTest() {
    @Before
    override fun setUp() {
        super.setUp()
        mockkObject(Analytics)
        every { Analytics.sendAnalyticsEvent(any(), any()) } answers { }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkObject(Analytics)
    }

    @Test
    fun testAnalyticsEventLogging() {
        val widgetProvider = TestWidgetProvider()

        widgetProvider.onEnabled(targetContext)

        verify { Analytics.sendAnalyticsEvent("TestWidgetProvider", "enabled") }
    }

    private class TestWidgetProvider : AnalyticsWidgetProvider() {
        override fun performUpdate(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: AppWidgetIds,
            usageAnalytics: UsageAnalytics,
        ) {
            // Do nothing
        }
    }
}
