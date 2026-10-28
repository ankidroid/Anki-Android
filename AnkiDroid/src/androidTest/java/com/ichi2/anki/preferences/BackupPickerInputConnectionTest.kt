// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.testutil.TestInputMethodRule
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.utils.negativeButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Deliver input and Done through Android's actual IME connection. */
class BackupPickerInputConnectionTest {
    @get:Rule
    val keyboard = TestInputMethodRule()

    @Test
    fun doneDoesNotSaveOrDismissTheBackupPicker() {
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
            keyboard.perform("commit", "1")
            waitUntil(message = { "Numeric IME input was not appended at the cursor" }) {
                fixture.editableNode().text.toString() == "301"
            }
            keyboard.perform("done")
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                assertEquals("30", preference.text)
                dialog.negativeButton.performClick()
            }
        }
    }
}
