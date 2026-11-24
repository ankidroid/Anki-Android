// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import anki.backend.backendError
import com.google.protobuf.ByteString
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeckPickerReloadReadErrorTest : JvmTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    @Test
    fun `a current deck read failure during reload does not crash`() {
        val failRead = AtomicBoolean(false)
        val readFailed = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString {
                    if (failRead.get()) {
                        readFailed.set(true)
                        throw BackendInvalidInputException(backendError { message = "current deck read failed" })
                    }
                    return super.getDeckLegacy(did)
                }
            }
        }
        addDeck("Existing")
        runTest {
            val viewModel = DeckPickerViewModel()
            viewModel.reloadDeckCounts().join()
            failRead.set(true)
            val reload = viewModel.reloadDeckCounts()
            reload.join()
            failRead.set(false)
            assertTrue(readFailed.get(), "Reload must attempt the failed deck read")
            assertFalse(reload.isCancelled, "A deck read error must be handled by the reload")
        }
    }
}
