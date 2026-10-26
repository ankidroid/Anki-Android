// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.decks.DeckTreeNode
import anki.decks.SetDeckCollapsedRequest
import anki.scheduler.UnburyDeckRequest
import app.cash.turbine.test
import com.google.protobuf.ByteString
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.dialogs.startDeletingEmptyCards
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.libanki.DeckId
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerErrorTest : RobolectricTest() {
    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `failed deck deletion while stopped shows an error on resume`() {
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        val deckId = addDeck("Deck")
        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            val lifecycle = deckPicker.lifecycle as LifecycleRegistry
            // ViewModel work can finish after the activity stops collecting its error flow.
            lifecycle.currentState = Lifecycle.State.CREATED
            advanceRobolectricLooperUntil { deckPicker.viewModel.onError.subscriptionCount.value == 0 }
            try {
                val job = deckPicker.deleteDeck(deckId)
                advanceRobolectricLooperUntil { job.isCompleted }
            } finally {
                lifecycle.currentState = Lifecycle.State.RESUMED
            }
            assertErrorShown()
        }
    }

    @Test
    fun `unacknowledged deck deletion error survives activity recreation`() {
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        val deckId = addDeck("Deck")
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false

        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            lateinit var retainedViewModel: DeckPickerViewModel
            lateinit var originalDialog: AlertDialog
            scenario.onActivity { deckPicker ->
                deckPicker.viewModel.awaitStartup()
                retainedViewModel = deckPicker.viewModel
                val job = deckPicker.deleteDeck(deckId)
                advanceRobolectricLooperUntil { job.isCompleted }
                assertErrorShown()
                originalDialog = ShadowDialog.getLatestDialog() as AlertDialog
            }

            // Recreate without dismissing the error, as for a font-scale configuration change.
            scenario.recreate()
            scenario.onActivity { deckPicker ->
                assertSame(retainedViewModel, deckPicker.viewModel)
                deckPicker.viewModel.awaitStartup()
                advanceRobolectricLooper()
                val restoredDialog = ShadowDialog.getLatestDialog() as? AlertDialog
                assertNotNull(restoredDialog, "The unacknowledged error must be shown again")
                assertNotSame(originalDialog, restoredDialog, "The old activity's dialog must not count as restored")
                assertTrue(restoredDialog.isShowing)
                assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
            }
        }
    }

    @Test
    fun `error shown again after recreation is reported once`() {
        val crashReporter = mock<CrashReporter>()
        val originalReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
        try {
            useBackend {
                object : Backend() {
                    override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw IllegalStateException("deletion failed")
                }
            }
            val deckId = addDeck("Deck")
            setIntroductionSlidesShown(true)
            BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
            targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
            throwOnShowError = false

            ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
                scenario.onActivity { deckPicker ->
                    deckPicker.viewModel.awaitStartup()
                    deckPicker.deleteDeck(deckId)
                    advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                }
                scenario.recreate()
                scenario.onActivity { deckPicker ->
                    deckPicker.viewModel.awaitStartup()
                    advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
                    ShadowDialog.getLatestDialog().dismiss()
                    advanceRobolectricLooper()
                }
            }

            verify(crashReporter).sendExceptionReport(any<Throwable>(), anyOrNull(), anyOrNull(), any())
        } finally {
            CrashReportService.setReporter(originalReporter)
        }
    }

    @Test
    fun `database corruption during deck deletion opens recovery dialog`() {
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount =
                    throw BackendDbCorruptException(
                        backendError { message = "database disk image is malformed" },
                    )
            }
        }
        val deckId = addDeck("Deck")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                throwOnShowError = false
                val job = deckPicker.deleteDeck(deckId)
                advanceRobolectricLooperUntil {
                    job.isCompleted && (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true
                }

                assertAll(
                    { assertTrue(DatabaseCorruption.isDetected, "Backend corruption must be recorded") },
                    {
                        assertNotNull(
                            deckPicker.supportFragmentManager.fragments
                                .filterIsInstance<DatabaseErrorDialog>()
                                .singleOrNull(),
                            "Backend corruption must open the recovery dialog, not a generic error alert",
                        )
                    },
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `error message waits until database recovery is closed`() =
        withCorruptDeletionRecovery { deckPicker, recovery ->
            val recoveryDialog = recovery.requireDialog()
            deckPicker.viewModel.onError.tryEmit("Backup failed")
            advanceRobolectricLooper()
            assertSame(recoveryDialog, ShadowDialog.getLatestDialog())

            recovery.dismiss()
            advanceRobolectricLooperUntil {
                val latest = ShadowDialog.getLatestDialog()
                latest !== recoveryDialog && latest?.isShowing == true
            }
            assertEquals("Backup failed", getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `error message waiting for recovery is not shown while stopped`() =
        withCorruptDeletionRecovery { deckPicker, recovery ->
            val recoveryDialog = recovery.requireDialog()
            deckPicker.viewModel.onError.tryEmit("Backup failed")
            advanceRobolectricLooper()

            val lifecycle = deckPicker.lifecycle as LifecycleRegistry
            lifecycle.currentState = Lifecycle.State.CREATED
            try {
                recovery.dismiss()
                advanceRobolectricLooper()
                assertSame(recoveryDialog, ShadowDialog.getLatestDialog())
            } finally {
                lifecycle.currentState = Lifecycle.State.RESUMED
            }
            advanceRobolectricLooperUntil {
                val latest = ShadowDialog.getLatestDialog()
                latest !== recoveryDialog && latest?.isShowing == true
            }
            assertEquals("Backup failed", getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `error message waits for a recovery dialog opened while stopped`() =
        withCorruptDeletionRecovery { deckPicker, recovery ->
            deckPicker.viewModel.onError.tryEmit("Backup failed")
            advanceRobolectricLooper()
            val lifecycle = deckPicker.lifecycle as LifecycleRegistry
            lifecycle.currentState = Lifecycle.State.CREATED
            recovery.dismiss()
            advanceRobolectricLooper()
            deckPicker.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_LOAD_FAILED)
            advanceRobolectricLooper()
            val nextRecovery =
                deckPicker.supportFragmentManager.fragments
                    .filterIsInstance<DatabaseErrorDialog>()
                    .single()
            val nextRecoveryDialog = nextRecovery.requireDialog()

            lifecycle.currentState = Lifecycle.State.RESUMED
            advanceRobolectricLooper()
            assertSame(nextRecoveryDialog, ShadowDialog.getLatestDialog())

            nextRecovery.dismiss()
            advanceRobolectricLooperUntil {
                val latest = ShadowDialog.getLatestDialog()
                latest !== nextRecoveryDialog && latest?.isShowing == true
            }
            assertEquals("Backup failed", getAlertDialogText(checkDismissed = true))
        }

    @Test
    fun `failed deck deletion shows an error`() {
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        val deckId = addDeck("Deck")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.selectContextMenuOption(DeckPickerContextMenuOption.DELETE_DECK, deckId)

            assertErrorShown()
        }
    }

    @Test
    @Config(qualifiers = "xlarge")
    fun `failed selected deck deletion shows an error`() {
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        addDeck("Deck", setAsSelected = true)

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            shadowOf(deckPicker).clickMenuItem(R.id.action_deck_delete)

            assertErrorShown()
        }
    }

    @Test
    fun `failed filtered deck rebuild shows an error`() {
        useBackend {
            object : Backend() {
                override fun rebuildFilteredDeck(did: Long): OpChangesWithCount = throw failure
            }
        }
        val deckId = addDynamicDeck("Filtered")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD, deckId)

            assertErrorShown()
        }
    }

    @Test
    fun `failed filtered deck emptying shows an error`() {
        useBackend {
            object : Backend() {
                override fun emptyFilteredDeck(did: Long): OpChanges = throw failure
            }
        }
        val deckId = addDynamicDeck("Filtered")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY, deckId)

            assertErrorShown()
        }
    }

    @Test
    fun `failed selection of a new deck shows an error`() {
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = throw failure
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.showCreateDeckDialog()
            (ShadowDialog.getLatestDialog() as AlertDialog).apply {
                input = "Deck"
                performPositiveClick()
            }

            assertErrorShown()
        }
    }

    @Test
    fun `failed selection does not open the right click context menu`() =
        assertFailedSelectionSkips({ flowOfShowRightClickContextMenu }) { deckId ->
            requestRightClickContextMenu(deckId, x = 10f, y = 20f)
        }

    @Test
    fun `failed selection of a created deck does not update the screen`() =
        assertFailedSelectionSkips({ flowOfCreatedDeckSelected }) { deckId -> selectCreatedDeck(deckId) }

    @Test
    fun `failed deck list update shows an error`() {
        var failDeckTree = false
        useBackend {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode = if (failDeckTree) throw failure else super.deckTree(now)
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            failDeckTree = true
            deckPicker.viewModel.updateDeckList()

            assertErrorShown()
        }
    }

    @Test
    fun `deck list keeps its decks when filtering cannot read the current deck`() {
        var failRead = false
        useBackend {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString = if (failRead) throw failure else super.getDeckLegacy(did)
            }
        }
        val deckId = addDeck("Existing")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            failRead = true
            deckPicker.viewModel.updateDeckFilter("no matching deck")

            assertErrorShown()
            val visibleDecks = deckPicker.viewModel.flowOfDeckList.value.data
            assertTrue(visibleDecks.any { it.did == deckId })
        }
    }

    @Test
    fun `corruption while opening deck options opens recovery dialog`() {
        var failedDeck: DeckId? = null
        useBackend {
            object : Backend() {
                override fun getDeckLegacy(did: Long): ByteString =
                    if (did == failedDeck) {
                        throw BackendDbCorruptException(backendError { message = "database disk image is malformed" })
                    } else {
                        super.getDeckLegacy(did)
                    }
            }
        }
        val deckId = addDeck("Deck")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                throwOnShowError = false
                failedDeck = deckId
                deckPicker.viewModel.openDeckOptions(deckId)

                advanceRobolectricLooperUntil {
                    DatabaseCorruption.isDetected &&
                        deckPicker.supportFragmentManager.fragments.any { it is DatabaseErrorDialog }
                }
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `failed unbury while stopped shows an error on resume`() {
        useBackend {
            object : Backend() {
                override fun unburyDeck(
                    deckId: Long,
                    mode: UnburyDeckRequest.Mode,
                ): OpChanges = throw failure
            }
        }
        val deckId = addDeck("Deck")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            val lifecycle = deckPicker.lifecycle as LifecycleRegistry
            lifecycle.currentState = Lifecycle.State.CREATED
            advanceRobolectricLooperUntil { deckPicker.viewModel.onError.subscriptionCount.value == 0 }
            try {
                val job = deckPicker.viewModel.unburyDeck(deckId)
                advanceRobolectricLooperUntil { job.isCompleted }
            } finally {
                lifecycle.currentState = Lifecycle.State.RESUMED
            }

            assertErrorShown()
        }
    }

    @Test
    fun `failed deck expand toggle shows an error and keeps the saved state`() {
        useBackend {
            object : Backend() {
                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges = throw failure
            }
        }
        val parent = addDeck("Parent")
        addDeck("Parent::Child")

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
            val job = deckPicker.viewModel.toggleDeckExpand(parent)
            advanceRobolectricLooperUntil { job.isCompleted }

            assertErrorShown()
            assertEquals(
                col.decks.get(parent)!!.collapsed,
                deckPicker.viewModel.dueTree
                    ?.find(parent)
                    ?.collapsed,
            )
        }
    }

    @Test
    fun `failed empty cards deletion shows an error`() {
        useBackend {
            object : Backend() {
                override fun removeCards(cardIds: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        addStandardNoteType("Empty", arrayOf("Front"), "", "")
        addNoteUsingNoteTypeName("Empty", "Front")
        val report = col.getEmptyCards()

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.startDeletingEmptyCards(report, keepNotes = false)

            assertErrorShown()
        }
    }

    private fun useBackend(backend: () -> Backend) {
        runBlocking { CollectionManager.discardBackend() }
        BackendFactory.setOverride { backend() }
    }

    private fun <T> assertFailedSelectionSkips(
        followUp: DeckPickerViewModel.() -> Flow<T>,
        request: DeckPickerViewModel.(DeckId) -> Job,
    ) = runTest {
        useBackend {
            object : Backend() {
                override fun setCurrentDeck(did: Long): OpChanges = throw failure
            }
        }
        val deckId = addDeck("Target")
        val viewModel = DeckPickerViewModel()

        viewModel.followUp().test {
            viewModel.request(deckId).join()

            expectNoEvents()
        }
        assertEquals(1, viewModel.pendingErrors.value.size)
    }

    private fun withCorruptDeletionRecovery(block: (DeckPicker, DatabaseErrorDialog) -> Unit) {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        var corrupt = false
        useBackend {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw corruption

                override fun deckTree(now: Long): DeckTreeNode = if (corrupt) throw corruption else super.deckTree(now)
            }
        }
        val deckId = addDeck("Deck")
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                throwOnShowError = false
                corrupt = true
                val deletion = deckPicker.deleteDeck(deckId)
                val recoveries = { deckPicker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>() }
                advanceRobolectricLooperUntil { deletion.isCompleted && recoveries().any { it.dialog?.isShowing == true } }

                block(deckPicker, recoveries().single())
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun DeckPicker.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) = supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(deckId, option))

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
