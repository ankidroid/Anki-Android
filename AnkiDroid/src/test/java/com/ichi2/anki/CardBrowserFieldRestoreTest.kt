// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.notetypes.copy
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.noteeditor.FieldState
import com.ichi2.testutils.withSplitPaneUiAsync
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CardBrowserFieldRestoreTest : RobolectricTest() {
    @Test
    fun `a field added before process death shows its stored text`() =
        runTest {
            withSplitPaneUiAsync {
                setCardBrowserFragmented(true)
                val type = addStandardNoteType("Inserted field", arrayOf("Front", "Extra", "Back"), "{{Front}}", "{{Back}}")
                val note = addBasicNote("Front text", "Back text")
                ensureCollectionLoadIsSynchronous()
                val controller = startActivityControllerNormallyOpenCollectionWithIntent(CardBrowser::class.java, Intent())
                val browser = controller.get()
                browser.viewModel.setQuery("deck:Default", forceRefresh = true)
                browser.waitForSearchResults()
                advanceRobolectricLooper()
                val savedBackId = requireNotNull(browser.fragment).fieldLineAt(1).id

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

                var repeated = false
                mockkStatic(View::class)
                every { View.generateViewId() } answers {
                    val fromFieldState = Throwable().stackTrace.any { it.className.startsWith(FieldState::class.java.name) }
                    if (fromFieldState && !repeated) {
                        repeated = true
                        savedBackId
                    } else {
                        callOriginal()
                    }
                }
                try {
                    controller.recreate()
                    advanceRobolectricLooper()
                } finally {
                    unmockkStatic(View::class)
                }

                val restored = requireNotNull(controller.get().fragment)
                assertEquals(true, repeated, "a saved id was handed out again")
                assertEquals(listOf("Front text", "", "Back text"), (0..2).map { restored.editFieldAt(it)?.text?.toString() })
            }
        }

    private fun NoteEditorFragment.fieldLineAt(index: Int): FieldEditLine =
        generateSequence(requireNotNull(editFieldAt(index)).parent) { it.parent }.filterIsInstance<FieldEditLine>().first()
}
