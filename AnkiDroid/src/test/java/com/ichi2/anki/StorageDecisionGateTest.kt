// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.storage.StorageDecision
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

/**
 * Proves the storage-decision gate in [CollectionManager.ensureOpenInner] is wired up.
 * Uses the test override to force [com.ichi2.anki.storage.StorageDecision.Undecided],
 * since [CollectionHelper.storageDecision] otherwise depends on real shared preferences.
 */
@RunWith(AndroidJUnit4::class)
class StorageDecisionGateTest : RobolectricTest() {
    @Test
    fun `opening the collection throws when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        try {
            assertFailsWith<StorageNotConfiguredException> { CollectionManager.getColUnsafe() }
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
        }
    }
}
