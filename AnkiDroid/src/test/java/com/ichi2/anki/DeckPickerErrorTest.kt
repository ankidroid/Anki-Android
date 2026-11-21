// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import android.content.Intent
import android.view.KeyEvent
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
import anki.scheduler.CongratsInfoResponse
import app.cash.turbine.test
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.crashreporting.CrashReporter
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.deckpicker.DeckPickerViewModel
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerConfirmDeleteDeckDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.dialogs.startDeletingEmptyCards
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.widgets.DeckAdapter
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.rules.BackendOverrideRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerErrorTest : RobolectricTest() {
    @get:Rule
    val backendOverride = BackendOverrideRule()

    private val failure =
        BackendIoException(
            backendError {
                kind = BackendError.Kind.IO_ERROR
                message = "Operation not permitted (os error 1)"
            },
        )

    @Test
    fun `failed deck deletion while stopped shows an error on resume`() {
        backendOverride.replaceWith {
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
            advanceRobolectricLooper()
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
        backendOverride.replaceWith {
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
    fun `a recovery dialog closed during recreation does not hold back later errors`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun removeDecks(dids: Iterable<Long>): OpChangesWithCount = throw failure
            }
        }
        val deckId = addDeck("Deck")
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
        throwOnShowError = false
        val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
        val original = controller.get()
        original.viewModel.awaitStartup()
        original.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
        advanceRobolectricLooper()

        original.supportFragmentManager.fragments
            .filterIsInstance<DatabaseErrorDialog>()
            .single()
            .dismiss()
        controller.recreate()
        advanceRobolectricLooper()
        val deletion = controller.get().deleteDeck(deckId)
        advanceRobolectricLooperUntil { deletion.isCompleted }

        assertErrorShown()
    }

    @Test
    fun `error shown again after recreation is reported once`() {
        val crashReporter = mock<CrashReporter>()
        val originalReporter = CrashReportService.getReporter()
        CrashReportService.setReporter(crashReporter)
        try {
            backendOverride.replaceWith {
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
        backendOverride.replaceWith {
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
    fun `corrupt deletion does not queue a second recovery prompt`() =
        withCorruptDeletionRecovery { deckPicker, _ ->
            advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
            advanceRobolectricLooper()

            assertTrue(
                deckPicker.viewModel.pendingErrors.value
                    .isEmpty(),
            )
        }

    @Test
    fun `a corruption found while recovery is open does not reopen it`() =
        withCorruptDeletionRecovery { deckPicker, recovery ->
            val refresh = deckPicker.viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { refresh.isCompleted }
            advanceRobolectricLooper()

            recovery.dismiss()
            advanceRobolectricLooperUntil {
                deckPicker.viewModel.pendingErrors.value
                    .isEmpty()
            }

            assertTrue(deckPicker.supportFragmentManager.fragments.none { it is DatabaseErrorDialog })
        }

    @Test
    fun `a corruption found while browsing backups after recovery closed opens recovery`() =
        withCorruptDeletionRecovery { deckPicker, recovery ->
            val recoveries = { deckPicker.supportFragmentManager.fragments.filterIsInstance<DatabaseErrorDialog>() }
            recovery.dismiss()
            advanceRobolectricLooperUntil { recoveries().isEmpty() }
            assertTrue(shadowOf(deckPicker).clickMenuItem(R.id.action_restore_backup))
            (recoveries().single().requireDialog() as AlertDialog).performPositiveClick()
            advanceRobolectricLooper()

            val refresh = deckPicker.viewModel.reloadDeckCounts()
            advanceRobolectricLooperUntil { refresh.isCompleted }
            deckPicker.dismissAllDialogFragments()
            advanceRobolectricLooper()

            try {
                assertTrue(recoveries().any { it.dialog?.isShowing == true })
            } finally {
                deckPicker.dismissAllDialogFragments()
            }
        }

    @Test
    fun `a successful database check does not reopen recovery for corruption found before it`() {
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
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 1) { deckPicker ->
                throwOnShowError = false
                deckPicker.showDatabaseErrorDialog(DatabaseErrorDialog.DatabaseErrorDialogType.DIALOG_CONFIRM_DATABASE_CHECK)
                advanceRobolectricLooper()
                val confirmation =
                    deckPicker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .single()
                failRefresh = true
                val refresh = deckPicker.viewModel.reloadDeckCounts()
                advanceRobolectricLooperUntil { refresh.isCompleted }
                failRefresh = false

                (confirmation.requireDialog() as AlertDialog).performPositiveClick()
                advanceRobolectricLooper()

                assertTrue(
                    deckPicker.supportFragmentManager.fragments.none { it is DatabaseErrorDialog },
                    "Corruption found before the database check reopened recovery",
                )
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    @Test
    fun `failed deck deletion shows an error`() {
        backendOverride.replaceWith {
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
        backendOverride.replaceWith {
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
        backendOverride.replaceWith {
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
        backendOverride.replaceWith {
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
    @Config(qualifiers = "xlarge")
    fun `failed filtered deck rebuild still shows the deck it selected`() =
        assertFailedFilteredDeckActionShowsSelection(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD) { failing ->
            object : Backend() {
                override fun rebuildFilteredDeck(did: Long): OpChangesWithCount =
                    if (failing()) throw failure else super.rebuildFilteredDeck(did)
            }
        }

    @Test
    @Config(qualifiers = "xlarge")
    fun `failed filtered deck emptying still shows the deck it selected`() =
        assertFailedFilteredDeckActionShowsSelection(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY) { failing ->
            object : Backend() {
                override fun emptyFilteredDeck(did: Long): OpChanges = if (failing()) throw failure else super.emptyFilteredDeck(did)
            }
        }

    @Test
    @Config(qualifiers = "xlarge")
    fun `Shift+Del asks first while the side panel shows no deck`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun congratsInfo(): CongratsInfoResponse = throw failure
            }
        }
        val selected = addDeck("Selected", setAsSelected = true)
        addNoteToDeck(selected)
        throwOnShowError = false

        withDeckPicker(deckCount = 0) { deckPicker ->
            advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true && deckPicker.fragment != null }
            advanceRobolectricLooper()
            assertNull(deckPicker.fragment!!.shownDeckId)

            deckPicker.onKeyUp(KeyEvent.KEYCODE_DEL, KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL, 0, KeyEvent.META_SHIFT_ON))
            advanceRobolectricLooperUntil { deckPicker.viewModel.progressManager.progress.value is ViewModelProgress.Idle }

            assertNotNull(col.decks.getLegacy(selected), "Shift+Del deleted a deck the side panel did not show")
            assertTrue(deckPicker.supportFragmentManager.fragments.any { it is DeckPickerConfirmDeleteDeckDialog })
        }
    }

    @Test
    fun `failed selection of a new deck shows an error`() {
        backendOverride.replaceWith {
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
        backendOverride.replaceWith {
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
    fun `a deck list update that keeps failing is retried once after its error is dismissed`() {
        var failDeckTree = false
        var failedUpdates = 0
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (!failDeckTree) return super.deckTree(now)
                    failedUpdates++
                    throw failure
                }
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            failDeckTree = true
            deckPicker.viewModel.updateDeckList()
            assertErrorShown()

            ShadowDialog.getLatestDialog().dismiss()
            assertErrorShown()
            assertEquals(2, failedUpdates)

            ShadowDialog.getLatestDialog().dismiss()
            advanceRobolectricLooper()
            advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
            advanceRobolectricLooper()
            assertEquals(2, failedUpdates, "The retried update must not be retried again")
            assertTrue(
                deckPicker.viewModel.pendingErrors.value
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `a replaced deck list update does not show its error`() {
        val failNextRead = AtomicBoolean(false)
        val readStarted = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        backendOverride.replaceWith {
            object : Backend() {
                override fun deckTree(now: Long): DeckTreeNode {
                    if (failNextRead.getAndSet(false)) {
                        readStarted.countDown()
                        check(finishRead.await(30, TimeUnit.SECONDS))
                        throw failure
                    }
                    return super.deckTree(now)
                }
            }
        }

        withDeckPicker(deckCount = 0) { deckPicker ->
            advanceRobolectricLooperUntil { deckPicker.viewModel.loadDeckCounts?.isCompleted == true }
            throwOnShowError = false
            val dispatcher = CollectionManager.setTestDispatcher(Dispatchers.IO.limitedParallelism(1), useReentrantLock = false)
            try {
                failNextRead.set(true)
                val replaced = deckPicker.viewModel.reloadDeckCounts()
                assertTrue(readStarted.await(10, TimeUnit.SECONDS))
                val latest = deckPicker.viewModel.reloadDeckCounts()
                finishRead.countDown()
                advanceRobolectricLooperUntil { replaced.isCompleted && latest.isCompleted }
                advanceRobolectricLooper()

                assertTrue(
                    deckPicker.viewModel.pendingErrors.value
                        .isEmpty(),
                )
            } finally {
                finishRead.countDown()
                CollectionManager.setTestDispatcher(dispatcher)
            }
        }
    }

    @Test
    fun `failed deck expand toggle shows an error and keeps the saved state`() {
        backendOverride.replaceWith {
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
    fun `corruption while collapsing a deck opens recovery`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun setDeckCollapsed(
                    deckId: Long,
                    collapsed: Boolean,
                    scope: SetDeckCollapsedRequest.Scope,
                ): OpChanges = throw corruption()
            }
        }
        val parent = addDeck("Parent")
        addDeck("Parent::Child")

        assertCorruptionOpensRecovery {
            val adapter = deckPickerBinding.decks.adapter as DeckAdapter
            advanceRobolectricLooperUntil { adapter.currentList.any { it.did == parent } }
            val row = deckPickerBinding.decks.findViewHolderForAdapterPosition(adapter.currentList.indexOfFirst { it.did == parent })
            (row as DeckAdapter.ViewHolder).binding.deckExpander.performClick()
        }
    }

    @Test
    fun `corruption while rebuilding a filtered deck from the menu opens recovery`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun rebuildFilteredDeck(did: Long): OpChangesWithCount = throw corruption()
            }
        }
        val deckId = addDynamicDeck("Filtered")

        assertCorruptionOpensRecovery { selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_REBUILD, deckId) }
    }

    @Test
    fun `corruption while emptying a filtered deck from the menu opens recovery`() {
        backendOverride.replaceWith {
            object : Backend() {
                override fun emptyFilteredDeck(did: Long): OpChanges = throw corruption()
            }
        }
        val deckId = addDynamicDeck("Filtered")

        assertCorruptionOpensRecovery { selectContextMenuOption(DeckPickerContextMenuOption.CUSTOM_STUDY_EMPTY, deckId) }
    }

    @Test
    fun `failed empty cards deletion shows an error`() {
        backendOverride.replaceWith {
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

    private fun <T> assertFailedSelectionSkips(
        followUp: DeckPickerViewModel.() -> Flow<T>,
        request: DeckPickerViewModel.(DeckId) -> Job,
    ) = runTest {
        backendOverride.replaceWith {
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

    private fun assertFailedFilteredDeckActionShowsSelection(
        option: DeckPickerContextMenuOption,
        backend: (failing: () -> Boolean) -> Backend,
    ) {
        var failing = false
        backendOverride.replaceWith { backend { failing } }
        addNoteToDeck(DEFAULT_DECK_ID)
        val filtered = addDynamicDeck("Filtered")
        col.decks.select(DEFAULT_DECK_ID)

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            advanceRobolectricLooperUntil { deckPicker.fragment?.viewModel?.selectedDeckId == DEFAULT_DECK_ID }
            failing = true
            deckPicker.selectContextMenuOption(option, filtered)

            assertErrorShown()
            assertEquals(filtered, col.decks.selected())
            advanceRobolectricLooperUntil { deckPicker.fragment?.viewModel?.selectedDeckId == filtered }
        }
    }

    private fun withCorruptDeletionRecovery(block: (DeckPicker, DatabaseErrorDialog) -> Unit) {
        val corruption = BackendDbCorruptException(backendError { message = "database disk image is malformed" })
        var corrupt = false
        backendOverride.replaceWith {
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

    private fun assertCorruptionOpensRecovery(start: DeckPicker.() -> Unit) {
        val previouslyDetected = DatabaseCorruption.isDetected
        DatabaseCorruption.isDetected = false
        try {
            withDeckPicker(deckCount = 0) { deckPicker ->
                throwOnShowError = false
                val recoveryShowing = {
                    deckPicker.supportFragmentManager.fragments
                        .filterIsInstance<DatabaseErrorDialog>()
                        .any { it.dialog?.isShowing == true }
                }
                deckPicker.start()
                advanceRobolectricLooperUntil { recoveryShowing() }
                advanceRobolectricLooper()

                assertTrue(recoveryShowing(), "The corruption error was dismissed before it could be read")
            }
        } finally {
            DatabaseCorruption.isDetected = previouslyDetected
        }
    }

    private fun corruption() = BackendDbCorruptException(backendError { message = "database disk image is malformed" })

    private fun DeckPicker.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) = supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(deckId, option))

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
