// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.backendError
import anki.decks.DeckTreeNode
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DatabaseErrorDialog.DatabaseErrorDialogType
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import com.ichi2.utils.positiveButton
import kotlinx.coroutines.Dispatchers
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupRestoreTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
    }

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `failed restore reports the error and leaves the original collection open`() {
        val note = addBasicNote("original", "answer")
        val backup = createInvalidBackup()

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.importColpkgListener = ImportColpkgListener { fail("Failed restore reported success") }

            deckPicker.restoreBackupAndWaitForError(backup)

            assertEquals(TR.importingTheProvidedFileIsNotA(), getAlertDialogText(checkDismissed = false))
            // Check before accessing col: that getter would reopen it and hide the regression.
            assertTrue(CollectionManager.isOpenUnsafe(), "Failed restore left the collection closed")

            clickAlertDialogButton { positiveButton }
            assertTrue(CollectionManager.isOpenUnsafe(), "Dismissing the restore error left the collection closed")
            assertEquals(listOf("original", "answer"), col.getNote(note.id).fields.toList())
        }
    }

    @Test
    fun `finishing a restore does not reopen recovery for corruption found before it`() {
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        addBasicNote("original", "answer")
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                throwOnShowError = false
                var restored = false
                deckPicker.importColpkgListener = ImportColpkgListener { restored = true }
                val chooser = deckPicker.openBackupChooser()
                failRefresh = true
                val refresh = deckPicker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                failRefresh = false

                chooser.selectOnlyBackup()
                advanceRobolectricLooperUntil(lazyMessage = { "The restore did not finish" }) { restored }
                advanceRobolectricLooper()

                assertTrue(
                    deckPicker.supportFragmentManager.fragments.none { it is DatabaseErrorDialog },
                    "Corruption found before the restore reopened recovery over the restored collection",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `failed backup restore retains corruption detected in the original collection`() {
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        addBasicNote("original", "answer")
        createInvalidBackup()
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                val chooser = picker.openBackupChooser()
                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                failRefresh = false
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                chooser.selectOnlyBackup()
                advanceRobolectricLooperUntil {
                    val dialog = ShadowDialog.getLatestDialog()
                    dialog is AlertDialog && dialog !== chooser && dialog.isShowing
                }
                clickAlertDialogButton { positiveButton }
                advanceRobolectricLooper()
                assertTrue(
                    DatabaseCorruption.isDetected &&
                        picker.supportFragmentManager.fragments.any {
                            it is DatabaseErrorDialog && it.dialog?.isShowing == true
                        },
                    "A failed restore must still report corruption in the original collection",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `old collection failure finishing during restore does not reopen recovery`() {
        val failNext = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failNext.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addBasicNote("original", "answer")
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                var restored = false
                deckPicker.importColpkgListener = ImportColpkgListener { restored = true }
                val chooser = deckPicker.openBackupChooser()
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    failNext.set(true)
                    val refresh = deckPicker.viewModel.reloadDeckCounts()
                    assertTrue(entered.await(10, TimeUnit.SECONDS), "Old collection refresh did not enter the backend")
                    chooser.selectOnlyBackup()
                    release.countDown()
                    advanceRobolectricLooperUntil(lazyMessage = { "The restore did not finish" }) { restored && refresh.isCompleted }
                    advanceRobolectricLooper()
                    assertTrue(
                        deckPicker.supportFragmentManager.fragments.none { it is DatabaseErrorDialog && it.dialog?.isShowing == true },
                        "An old collection failure must not open recovery over the restored collection",
                    )
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            release.countDown()
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `old collection failure finishing during a replacing import does not reopen recovery`() {
        val failNext = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failNext.compareAndSet(true, false)) {
                        entered.countDown()
                        check(release.await(30, TimeUnit.SECONDS))
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addBasicNote("original", "answer")
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val colpkg = backupDirectory.listFiles()!!.single { it.name.endsWith(".colpkg") }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                var imported = false
                deckPicker.importColpkgListener = ImportColpkgListener { imported = true }
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    failNext.set(true)
                    val refresh = deckPicker.viewModel.reloadDeckCounts()
                    assertTrue(entered.await(10, TimeUnit.SECONDS))
                    deckPicker.importReplace(colpkg.path)
                    release.countDown()
                    advanceRobolectricLooperUntil(lazyMessage = { "The import did not finish" }) { imported && refresh.isCompleted }
                    advanceRobolectricLooper()
                    assertTrue(
                        deckPicker.supportFragmentManager.fragments.none { it is DatabaseErrorDialog && it.dialog?.isShowing == true },
                        "An old collection failure must not open recovery over the imported collection",
                    )
                } finally {
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            release.countDown()
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `a refresh queued during a restore reports corruption found afterward`() {
        val importStarted = CountDownLatch(1)
        val finishImport = CountDownLatch(1)
        val failRefresh = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    importStarted.countDown()
                    check(finishImport.await(30, TimeUnit.SECONDS))
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                }

                override fun deckTree(now: Long): DeckTreeNode {
                    if (failRefresh.getAndSet(false)) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    }
                    return super.deckTree(now)
                }
            }
        }
        addBasicNote("original", "answer")
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                var restored = false
                deckPicker.importColpkgListener = ImportColpkgListener { restored = true }
                val previousDispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
                try {
                    deckPicker.restoreFromBackup(backup.path)
                    assertTrue(importStarted.await(10, TimeUnit.SECONDS), "The restore did not start")
                    failRefresh.set(true)
                    val refresh = deckPicker.viewModel.reloadDeckCounts()
                    finishImport.countDown()
                    advanceRobolectricLooperUntil {
                        restored && refresh.isCompleted &&
                            deckPicker.viewModel.loadDeckCounts?.isCompleted == true
                    }
                    advanceRobolectricLooper()

                    assertTrue(
                        deckPicker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>().any {
                            it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED && it.dialog?.isShowing == true
                        },
                        "The refresh first read the restored collection and must report its corruption",
                    )
                } finally {
                    finishImport.countDown()
                    CollectionManager.setTestDispatcher(previousDispatcher)
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `failed backup restore from recovery keeps the corruption diagnosis`() {
        var failRefresh = false
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode =
                    if (failRefresh) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.deckTree(now)
                    }
            }
        }
        addBasicNote("original", "answer")
        createInvalidBackup()
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { picker ->
                advanceRobolectricLooperUntil { picker.viewModel.loadDeckCounts?.isCompleted == true }
                throwOnShowError = false
                failRefresh = true
                val firstRefresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { firstRefresh.isCompleted }
                clickAlertDialogButton { positiveButton }
                advanceRobolectricLooper()
                val options = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                val restoreLabel = picker.getString(CommonString.backup_restore)
                val list = options.listView
                val restoreIndex = (0 until list.count).single { list.adapter.getItem(it) == restoreLabel }
                assertTrue(list.performItemClick(list.getChildAt(restoreIndex), restoreIndex, list.adapter.getItemId(restoreIndex)))
                advanceRobolectricLooper()
                val chooser = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
                failRefresh = true
                val refresh = picker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                failRefresh = false
                assertEquals(1, picker.viewModel.pendingErrors.value.size)
                chooser.selectOnlyBackup()
                advanceRobolectricLooperUntil {
                    val dialog = ShadowDialog.getLatestDialog()
                    dialog is AlertDialog && dialog !== chooser && dialog.isShowing
                }
                assertEquals(TR.importingTheProvidedFileIsNotA(), getAlertDialogText(checkDismissed = false))
                clickAlertDialogButton { positiveButton }
                advanceRobolectricLooper()
                assertTrue(
                    DatabaseCorruption.isDetected &&
                        picker.supportFragmentManager.fragments.any {
                            it is DatabaseErrorDialog &&
                                it.requireDialogType() == DatabaseErrorDialogType.DIALOG_LOAD_FAILED &&
                                it.dialog?.isShowing == true
                        },
                    "A failed restore must still report corruption in the original collection",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `a restored collection that cannot be opened is reported once`() {
        val restored = AtomicBoolean(false)
        backendOverride.replaceWith {
            object : Backend() {
                override fun importCollectionPackage(
                    colPath: String,
                    backupPath: String,
                    mediaFolder: String,
                    mediaDb: String,
                ) {
                    super.importCollectionPackage(colPath, backupPath, mediaFolder, mediaDb)
                    restored.set(true)
                }

                override fun openCollection(
                    collectionPath: String,
                    mediaFolderPath: String,
                    mediaDbPath: String,
                ) {
                    if (restored.get()) throw BackendIoException(backendError { message = "cannot open the collection" })
                    super.openCollection(collectionPath, mediaFolderPath, mediaDbPath)
                }
            }
        }
        addBasicNote()
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
        val backup = backupDirectory.listFiles()!!.single { it.extension == "colpkg" }
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())

        controller.get().restoreFromBackup(backup.path)
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        ShadowDialog.getLatestDialog().dismiss()
        advanceRobolectricLooper()
        val shownDialogs = ShadowDialog.getShownDialogs().size
        controller
            .pause()
            .stop()
            .start()
            .resume()
        advanceRobolectricLooper()

        assertEquals(shownDialogs, ShadowDialog.getShownDialogs().size, "Returning to the Deck Picker reported the failed open again")
    }

    private fun createInvalidBackup(): File {
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        val backup = File(backupDirectory, "backup-2020-07-07-07.00.00.colpkg")
        // A valid ZIP missing its collection produces the localized invalid-backup error.
        ZipOutputStream(backup.outputStream()).use { }
        return backup
    }

    private fun DeckPicker.openBackupChooser(): AlertDialog {
        assertTrue(shadowOf(this).clickMenuItem(R.id.action_restore_backup))
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
        return assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
    }

    private fun AlertDialog.selectOnlyBackup() {
        assertEquals(1, listView.count)
        assertTrue(
            listView.performItemClick(listView.getChildAt(0), 0, listView.adapter.getItemId(0)),
            "Selecting the backup did not invoke its restore listener",
        )
    }

    private fun DeckPicker.restoreBackupAndWaitForError(backup: File) {
        val chooser = openBackupChooser()
        chooser.selectOnlyBackup()

        advanceRobolectricLooperUntil(lazyMessage = { "Expected a restore error dialog after selecting ${backup.name}" }) {
            val dialog = ShadowDialog.getLatestDialog()
            dialog is AlertDialog && dialog !== chooser && dialog.isShowing
        }
    }
}
