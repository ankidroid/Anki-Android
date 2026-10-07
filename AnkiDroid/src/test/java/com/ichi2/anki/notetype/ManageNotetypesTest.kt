// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Chaitanya Medidar <2023.chaitanya.medidar@ves.ac.in>

package com.ichi2.anki.notetype

import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@RunWith(AndroidJUnit4::class)
class ManageNotetypesTest : RobolectricTest() {
    @Test
    fun `rename note type - whitespace only name keeps Rename button disabled`() =
        runTest {
            ensureCollectionLoadIsSynchronous()
            addStandardNoteType(TEST_NOTE_TYPE_NAME, arrayOf("front", "back"), "", "")

            val activity = startRegularActivity<ManageNotetypes>()
            val firstNoteType =
                activity.viewModel.state.value.noteTypes
                    .first { it.name == TEST_NOTE_TYPE_NAME }
            activity.renameNotetype(firstNoteType)

            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val nameInput = dialog.findViewById<EditText>(R.id.dialog_text_input)!!
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            // whitespace-only should disable the Rename button
            nameInput.setText("   ")
            assertThat("Rename button disabled for whitespace-only name", positiveButton.isEnabled, equalTo(false))

            // a valid new name should enable the button
            nameInput.setText("Renamed Note Type")
            assertThat("Rename button enabled for a valid name", positiveButton.isEnabled, equalTo(true))

            // empty string should disable it again
            nameInput.setText("")
            assertThat("Rename button disabled for empty name", positiveButton.isEnabled, equalTo(false))
        }

    companion object {
        private const val TEST_NOTE_TYPE_NAME = "BasicTestNoteType1"
    }
}
