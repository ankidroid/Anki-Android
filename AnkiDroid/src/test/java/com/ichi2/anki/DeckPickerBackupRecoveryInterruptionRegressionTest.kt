// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Job
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowBuild
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupRecoveryInterruptionRegressionTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `a late backup error keeps recovery open`() {
        val corruptRefresh = AtomicBoolean(false)
        val backupFinished = AtomicBoolean(false)
        val releaseBackup = CountDownLatch(1)
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (corruptRefresh.getAndSet(false)) throw corruption else super.deckTree(now)

                override fun createBackup(
                    backupFolder: String,
                    force: Boolean,
                    waitForCompletion: Boolean,
                ): Boolean = false

                override fun awaitBackupCompletion() {
                    check(releaseBackup.await(30, TimeUnit.SECONDS)) { "Recovery options did not open before backup completion" }
                    backupFinished.set(true)
                    throw corruption
                }
            }
        }
        addDeck("Target")
        val originalFingerprint = Build.FINGERPRINT
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                advanceRobolectricLooper()
                throwOnShowError = false
                ShadowBuild.setFingerprint("backup-recovery-review")
                picker.viewModel.onScreenResumed()
                val existingJobs =
                    picker.viewModel.viewModelScope.coroutineContext[Job]!!
                        .children
                        .toSet()
                corruptRefresh.set(true)
                picker.viewModel.updateDeckList()
                advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED) != null }
                (recovery(picker, DatabaseErrorDialogType.DIALOG_LOAD_FAILED)!!.requireDialog() as AlertDialog).performPositiveClick()
                advanceRobolectricLooperUntil { recovery(picker, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING) != null }
                val options = recovery(picker, DatabaseErrorDialogType.DIALOG_ERROR_HANDLING)!!
                val backupJobs =
                    picker.viewModel.viewModelScope.coroutineContext[Job]!!
                        .children
                        .filter { it !in existingJobs }
                        .toList()
                assertTrue(backupJobs.isNotEmpty(), "An automatic backup must still be waiting")
                releaseBackup.countDown()
                advanceRobolectricLooperUntil { backupFinished.get() && backupJobs.all { it.isCompleted } }
                advanceRobolectricLooper()
                assertTrue(options.dialog?.isShowing == true, "The backup error must not close recovery options")
                assertSame(
                    options,
                    picker.supportFragmentManager.findFragmentByTag(DIALOG_FRAGMENT_TAG),
                    "An automatic backup failure must not replace recovery options with a second recovery prompt",
                )
            }
        } finally {
            releaseBackup.countDown()
            ShadowBuild.setFingerprint(originalFingerprint)
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun recovery(
        picker: DeckPicker,
        type: DatabaseErrorDialogType,
    ) = picker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().singleOrNull {
        BundleCompat.getParcelable(it.requireArguments(), "dialog", DatabaseErrorDialogType::class.java) == type
    }
}
