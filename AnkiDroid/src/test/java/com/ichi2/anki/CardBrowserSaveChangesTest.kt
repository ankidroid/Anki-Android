// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.withSplitPaneUi
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
class CardBrowserSaveChangesTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Test
    fun `cancelling save changes keeps the edited row highlighted`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val editedRow = requireNotNull(browser.viewModel.paneRow)
            val noteId = editor.editorNote!!.id
            editor.editFieldAt(1)!!.setText("edited")
            val otherPosition = browser.viewModel.cards.indexOfFirst { it != editedRow }

            browser.clickRowAtPosition(otherPosition)
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertThat("the edited note stays in the editor", browser.fragment!!.editorNote!!.id, equalTo(noteId))
            assertThat("the highlighted row matches the editor", browser.viewModel.paneRow, equalTo(editedRow))
        }

    @Test
    fun `a failed save keeps the edited row highlighted`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            throwOnShowError = false
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val editedRow = requireNotNull(browser.viewModel.paneRow)
            editor.editFieldAt(1)!!.setText("edited")
            col.removeNotes(noteIds = listOf(editor.editorNote!!.id))
            val otherPosition = browser.viewModel.cards.indexOfFirst { it != editedRow }

            browser.clickRowAtPosition(otherPosition)
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE).performClick()
            advanceRobolectricLooper()

            assertThat("the highlighted row matches the editor", browser.viewModel.paneRow, equalTo(editedRow))
        }

    @Test
    fun `cancelling save changes highlights no other search result`() =
        withSplitPaneUi {
            setCardBrowserFragmented(true)
            val browser = getBrowserWithNotes(2)
            browser.viewModel.setQuery("deck:Default", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooper()
            val editor = requireNotNull(browser.fragment)
            val noteId = editor.editorNote!!.id
            editor.editFieldAt(1)!!.setText("edited")

            browser.viewModel.setQuery("-nid:$noteId", forceRefresh = true)
            browser.waitForSearchResults()
            advanceRobolectricLooperUntil { ShadowDialog.getLatestDialog()?.isShowing == true }
            val otherRow = browser.viewModel.cards.single()
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEUTRAL).performClick()
            advanceRobolectricLooper()

            assertThat("the edited note stays in the editor", browser.fragment!!.editorNote!!.id, equalTo(noteId))
            assertThat("the other note is not highlighted", browser.viewModel.paneRow, not(equalTo(otherRow)))
        }
}
