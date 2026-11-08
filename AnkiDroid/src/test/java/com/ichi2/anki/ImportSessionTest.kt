// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ImportSessionTest : RobolectricTest() {
    @Test
    fun `closing an import waits for its backend operation to return`() =
        runTest {
            val session = assertNotNull(ImportSession.acquire(targetContext))
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val operation =
                launch {
                    ImportSession.withOperation {
                        started.complete(Unit)
                        finish.await()
                    }
                }
            started.await()

            session.finish()

            assertNull(ImportSession.acquire(targetContext))
            finish.complete(Unit)
            operation.join()
            assertNotNull(ImportSession.acquire(targetContext)).finish()
        }

    @Test
    fun `late cleanup cannot release a newer import`() {
        val previous = assertNotNull(ImportSession.acquire(targetContext))
        previous.finish()
        val current = assertNotNull(ImportSession.acquire(targetContext))

        previous.finish()

        assertNull(ImportSession.acquire(targetContext))
        current.finish()
        assertNotNull(ImportSession.acquire(targetContext)).finish()
    }
}
