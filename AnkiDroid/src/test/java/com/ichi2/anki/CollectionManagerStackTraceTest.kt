// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbLockedException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowBuild
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CollectionManagerStackTraceTest : RobolectricTest() {
    @Test
    fun `opening failure records the withCol caller`() =
        runTest {
            val originalFingerprint = Build.FINGERPRINT
            // Exercise the real dispatcher switch: Robolectric's synchronous queue retains the caller.
            ShadowBuild.setFingerprint("collection-stack-trace-test")
            try {
                CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED

                val failure = assertFailsWith<BackendDbLockedException> { requestCollection() }

                assertFalse(failure.stackTrace.any { it.methodName == "requestCollection" })
                val caller = failure.suppressed.single()
                assertTrue(caller.stackTrace.any { it.methodName == "requestCollection" })
            } finally {
                ShadowBuild.setFingerprint(originalFingerprint)
            }
        }

    private suspend fun requestCollection(): Nothing = CollectionManager.withCol { error("opening should fail first") }
}
