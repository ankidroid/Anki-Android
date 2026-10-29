// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowEnvironment
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
@OptIn(DelicateCoroutinesApi::class)
class WidgetStatusTest : RobolectricTest() {
    private val repository = mockk<WidgetRepository>(relaxed = true)
    private val updater = mockk<(Context) -> Unit>(relaxed = true)

    @Test
    fun `disabled widgets do not open the collection`() =
        withWidgetStatus {
            WidgetStatus.updateInBackground(targetContext)
            advanceUntilIdle()

            assertFalse(CollectionManager.isOpenUnsafe())
            verify(exactly = 0) { repository.storeSmallWidgetStatus(any()) }
            verify(exactly = 0) { updater(any()) }
        }

    @Test
    fun `enabled widgets invoke the registered updater`() =
        withWidgetStatus {
            addBasicNote("front", "back")
            targetContext.sharedPrefs().edit { putBoolean("widgetSmallEnabled", true) }
            WidgetStatus.updateInBackground(targetContext)
            advanceUntilIdle()
            verify(exactly = 1) { repository.storeSmallWidgetStatus(match { it.dueCardsCount == 1 }) }
            verify(exactly = 1) { updater(targetContext) }
        }

    private fun withWidgetStatus(block: suspend TestScope.() -> Unit) =
        runTest {
            val originalRepository = widgetRepository
            val application = getApplicationContext<Application>()
            val originalStorageState = Environment.getExternalStorageState()
            ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED)
            targetContext.sharedPrefs().edit { putBoolean("widgetSmallEnabled", false) }
            mockkObject(GlobalScope)
            // Run the real background job on this test's scheduler, and await it before cleanup.
            every { GlobalScope.coroutineContext } returns coroutineContext
            WidgetRepository.register(repository)
            with(application) { WidgetStatus.register(updater) }
            try {
                block()
            } finally {
                advanceUntilIdle()
                ShadowEnvironment.setExternalStorageState(originalStorageState)
                unmockkObject(GlobalScope)
                WidgetRepository.register(originalRepository)
                with(application) {
                    WidgetStatus.register { context -> AnkiDroidWidgetSmall.UpdateService().doUpdate(context) }
                }
            }
        }
}
