// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.backend.BackendError
import anki.backend.backendError
import anki.collection.OpChanges
import anki.decks.SetDeckCollapsedRequest
import com.ichi2.testutils.rules.BackendOverrideRule
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DeckPickerCollapseErrorTest : RobolectricTest() {
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
    fun `a failed toggle shows an error and keeps the saved state`() {
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

    private fun assertErrorShown() {
        advanceRobolectricLooperUntil { (ShadowDialog.getLatestDialog() as? AlertDialog)?.isShowing == true }
        assertEquals(failure.localizedMessage, getAlertDialogText(checkDismissed = true))
    }
}
