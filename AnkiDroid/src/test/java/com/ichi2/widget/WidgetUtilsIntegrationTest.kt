// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** @see getAppWidgetManager */
@RunWith(AndroidJUnit4::class)
class WidgetUtilsIntegrationTest : RobolectricTest() {
    // on a Supernote A5X, AppWidgetManager.getInstance(context) returns null
    @Test
    fun `app runs with no widget manager`() {
        super.startRegularActivity<DeckPicker>() // WARN: This didn't crash before fixes applied
        WidgetPermissionReceiver().onReceive(targetContext, Intent())
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            mockkStatic(AppWidgetManager::class)
            every { AppWidgetManager.getInstance(any()) } answers { null }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            unmockkStatic(AppWidgetManager::class)
        }
    }
}
