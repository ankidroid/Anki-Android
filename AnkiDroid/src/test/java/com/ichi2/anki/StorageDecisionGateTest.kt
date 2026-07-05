// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.storage.StorageDecision
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/**
 * Proves the storage-decision gate in [CollectionManager.ensureOpenInner] is wired up. In production
 * [CollectionHelper.storageDecision] always returns [com.ichi2.anki.storage.StorageDecision.Decided], so this never fires;
 * here we force [com.ichi2.anki.storage.StorageDecision.Undecided] via the test override.
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

    /** No collection access should be attempted: a crash report would otherwise be generated */
    @Test
    fun `startup failure is StorageUndecided when storage is undecided`() {
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        try {
            val failure = InitialActivity.getStartupFailureType { true }
            assertEquals(InitialActivity.StartupFailure.StorageUndecided, failure)
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
        }
    }

    /**
     * A storage failure at startup ([SystemStorageException]: OS bug/SD card issue) must not
     * masquerade as the expected 'storage not configured' state when opening the collection.
     */
    @Test
    fun `opening the collection reports a recorded startup storage failure`() {
        val failure = SystemStorageException.build("simulated getExternalFilesDir failure")
        CollectionHelper.storageDecisionTestOverride = StorageDecision.Undecided
        CollectionHelper.systemStorageFailure = failure
        try {
            val thrown = assertFailsWith<SystemStorageException> { CollectionManager.getColUnsafe() }
            assertSame(failure, thrown)
        } finally {
            CollectionHelper.storageDecisionTestOverride = null
            CollectionHelper.systemStorageFailure = null
        }
    }
}
