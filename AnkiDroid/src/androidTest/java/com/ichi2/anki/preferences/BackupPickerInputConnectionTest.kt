// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.utils.positiveButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Deliver input and Done through Android's actual IME connection. */
class BackupPickerInputConnectionTest {
    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun doneKeepsInvalidInputOpenAndSavesAValidIntegerOnce() {
        val fixture = BackupPickerTest()
        fixture.withPicker { scenario, preference, dialog ->
            val editor = keyboard.awaitEditor(InputType.TYPE_CLASS_NUMBER, imeAction = EditorInfo.IME_ACTION_DONE)
            assertEquals(EditorInfo.IME_ACTION_DONE, editor.imeOptions)
            assertEquals(2, editor.initialSelStart)
            assertEquals(2, editor.initialSelEnd)
            val artifacts = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "backup-picker").apply { mkdirs() }
            File(artifacts, "input-connection.json").writeText(
                JSONObject()
                    .put("inputType", editor.inputType)
                    .put("imeOptions", editor.imeOptions)
                    .put("selectionStart", editor.initialSelStart)
                    .put("selectionEnd", editor.initialSelEnd)
                    .toString(2),
            )
            val savedValues = mutableListOf<Any?>()
            scenario.onActivity {
                preference.setOnPreferenceChangeListener { _, value ->
                    savedValues.add(value)
                    true
                }
                dialog.findViewById<EditText>(android.R.id.edit)!!.selectAll()
            }
            keyboard.perform("commit", "4")
            waitUntil(message = { "Numeric IME did not enter the invalid value" }) {
                fixture.editableNode().text.toString() == "4"
            }
            keyboard.perform("done")
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                assertFalse(dialog.positiveButton.isEnabled)
                assertEquals("30", preference.text)
                assertTrue(savedValues.isEmpty())
                dialog.findViewById<EditText>(android.R.id.edit)!!.selectAll()
            }
            keyboard.perform("commit", "31")
            waitUntil(message = { "Numeric IME did not enter the valid value" }) {
                fixture.editableNode().text.toString() == "31"
            }
            keyboard.perform("done")
            waitUntil(message = { "Done did not confirm the valid backup value" }) {
                var closed = false
                scenario.onActivity { closed = !dialog.isShowing }
                closed
            }
            scenario.onActivity {
                assertFalse(dialog.isShowing)
                assertEquals("31", preference.text)
                assertEquals(listOf(31), savedValues)
            }
        }
    }
}
