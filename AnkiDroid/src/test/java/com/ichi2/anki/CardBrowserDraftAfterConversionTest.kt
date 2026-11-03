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
class CardBrowserDraftAfterConversionTest : RobolectricTest() {
    @Test
    fun `recreating after a field is added preserves the draft`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Expanded", arrayOf("Front", "Back", "Extra"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Front text", "Saved back")
                val cardId = note.firstCard().id
                ensureCollectionLoadIsSynchronous()
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, Intent())
                val browser = controller.get()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val editor = requireNotNull(browser.fragment)
                requireNotNull(editor.editFieldAt(1)).setText("Unsaved back")

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = 1
                            newFields[2] = -1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertEquals(note.id, col.getCard(cardId).nid)
                assertEquals(listOf("Front text", "Saved back", ""), col.getNote(note.id).fields)

                controller.recreate()
                advanceRobolectricLooper()

                val restored = requireNotNull(controller.get().fragment)
                assertEquals("Unsaved back", restored.editFieldAt(1)?.text?.toString())
            }
        }

    @Test
    fun `recreation retains a draft when a field is inserted before it`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Inserted field", arrayOf("Front", "Extra", "Back"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Front text", "Saved back")
                val cardId = note.firstCard().id
                ensureCollectionLoadIsSynchronous()
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, Intent())
                val browser = controller.get()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                requireNotNull(browser.fragment?.editFieldAt(1)).setText("Unsaved back")

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = -1
                            newFields[2] = 1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertEquals(note.id, col.getCard(cardId).nid)
                assertEquals(listOf("Front text", "", "Saved back"), col.getNote(note.id).fields)

                controller.recreate()
                advanceRobolectricLooper()

                val restored = requireNotNull(controller.get().fragment)
                val visible = (0..2).mapNotNull { restored.editFieldAt(it)?.text?.toString() }
                assertEquals(true, "Unsaved back" in visible, "the user's draft remains accessible")
            }
        }

    @Test
    fun `recreation retains a draft in an empty field moved by conversion`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Inserted field", arrayOf("Front", "Extra", "Back"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Front text", "")
                val cardId = note.firstCard().id
                ensureCollectionLoadIsSynchronous()
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, Intent())
                val browser = controller.get()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                requireNotNull(browser.fragment?.editFieldAt(1)).setText("Unsaved back")

                withCol {
                    val target = notetypes.byName(type)!!.id
                    val input =
                        notetypes.changeNotetypeInfo(note.noteTypeId, target).input.copy {
                            noteIds.add(note.id)
                            newFields[0] = 0
                            newFields[1] = -1
                            newFields[2] = 1
                            newTemplates[0] = 0
                        }
                    notetypes.changeNotetypeOfNotes(input)
                }
                assertEquals(note.id, col.getCard(cardId).nid)
                assertEquals(listOf("Front text", "", ""), col.getNote(note.id).fields)

                controller.recreate()
                advanceRobolectricLooper()

                val restored = requireNotNull(controller.get().fragment)
                val visible = (0..2).mapNotNull { restored.editFieldAt(it)?.text?.toString() }
                assertEquals(true, "Unsaved back" in visible, "the user's draft remains accessible")
            }
        }
}
