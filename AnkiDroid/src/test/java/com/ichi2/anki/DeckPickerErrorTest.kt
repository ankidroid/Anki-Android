// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.collection.OpChanges
import anki.collection.OpChangesWithCount
import anki.decks.DeckTreeNode
import anki.decks.SetDeckCollapsedRequest
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.dialogs.DatabaseErrorDialog
import com.ichi2.anki.dialogs.DeckPickerContextMenu.DeckPickerContextMenuOption
import com.ichi2.anki.dialogs.DeckPickerContextMenuResult
import com.ichi2.anki.dialogs.setDeckPickerContextMenuResult
import com.ichi2.anki.dialogs.startDeletingEmptyCards
import com.ichi2.anki.dialogs.utils.input
import com.ichi2.anki.dialogs.utils.performPositiveClick
import com.ichi2.anki.libanki.DeckId
import kotlinx.coroutines.runBlocking
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendException.BackendDbException.BackendDbCorruptException
import net.ankiweb.rsdroid.BackendFactory
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    private fun DeckPicker.selectContextMenuOption(
        option: DeckPickerContextMenuOption,
        deckId: DeckId,
    ) = supportFragmentManager.setDeckPickerContextMenuResult(DeckPickerContextMenuResult(deckId, option))

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
