// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.crashreporting.CrashReportService.sendExceptionReport
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AnkiDroidAppTest {
    @Test
    fun `terminating application unregisters its lifecycle observer`() {
        val application = ApplicationProvider.getApplicationContext<AnkiDroidApp>()
        val lifecycle = ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry
        val otherObserver = LifecycleEventObserver { _, _ -> }
        lifecycle.addObserver(otherObserver)
        try {
            val observerCount = lifecycle.observerCount

            application.onTerminate()

            assertEquals(observerCount - 1, lifecycle.observerCount)
            lifecycle.removeObserver(otherObserver)
            assertEquals(observerCount - 2, lifecycle.observerCount)
        } finally {
            lifecycle.removeObserver(otherObserver)
        }
    }

    @Test
    fun reportingDoesNotThrowException() {
        assertDoesNotThrow { sendExceptionReport("Test", "AnkiDroidAppTest") }
    }

    @Test
    fun reportingWithNullMessageDoesNotFail() {
        val message: String? = null
        // It's meant to be non-null, but it's developer-defined, and we don't want a crash in the reporting dialog
        //noinspection ConstantConditions
        assertDoesNotThrow { sendExceptionReport(message, "AnkiDroidAppTest") }
    }
}
