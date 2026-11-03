// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.testutils.withSplitPaneUiAsync
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CardBrowserDraftRemovedFieldTest : RobolectricTest() {
    @Test
    fun `recreation does not move a removed draft to an unrelated field`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val original = addStandardNoteType("Original", arrayOf("Front", "Memo", "Back"), "{{Front}}", "{{Back}}")
                val type = addStandardNoteType("Replacement", arrayOf("Front", "Extra"), "{{Front}}", "{{Front}}")
                val note =
                    col.newNote(col.notetypes.byName(original)!!).apply {
                        setField(0, "Front text")
                        setField(1, "Memo text")
                        setField(2, "")
                        col.addNote(this, col.decks.selected())
                    }
                val cardId = note.firstCard().id
                ensureCollectionLoadIsSynchronous()
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, Intent())
                val browser = controller.get()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                requireNotNull(browser.fragment?.editFieldAt(2)).setText("Unsaved back")

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = -1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertEquals(note.id, col.getCard(cardId).nid)
                assertEquals(listOf("Front text", ""), col.getNote(note.id).fields)

                controller.recreate()
                advanceRobolectricLooper()

                val restored = requireNotNull(controller.get().fragment)
                assertEquals(
                    false,
                    restored.editFieldAt(1)?.text?.toString() == "Unsaved back",
                    "the removed draft must not replace an unrelated field",
                )
            }
        }
}
