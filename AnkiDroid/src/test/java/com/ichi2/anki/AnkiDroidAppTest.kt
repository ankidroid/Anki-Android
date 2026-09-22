/*
 *  Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
