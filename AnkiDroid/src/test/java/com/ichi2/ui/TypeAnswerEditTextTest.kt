// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.os.LocaleList
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.enums.DayTheme
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TypeAnswerEditTextTest : RobolectricTest() {
    @Test
    fun `nosuggest preserves a real editor and Done action in both answer fields`() {
        withAnswerFields { field, setNoSuggest ->
            setNoSuggest(true)
            val info = EditorInfo()
            val connection = assertNotNull(field.onCreateInputConnection(info))

            // TYPE_NULL on the view itself makes this false, suppressing the cursor and preventing
            // the keyboard from reopening after a tap or returning from the keyboard picker.
            assertTrue(field.onCheckIsTextEditor())
            assertEquals(INPUT_TYPE, field.inputType)
            assertEquals(InputType.TYPE_NULL, info.inputType)
            assertEquals(EditorInfo.IME_ACTION_DONE, info.imeOptions and EditorInfo.IME_MASK_ACTION)
            assertEquals(LocaleList.forLanguageTags("fr"), info.hintLocales)

            connection.commitText("été", 1)
            assertEquals("été", field.text.toString())
            var done = false
            field.setOnEditorActionListener { _, actionId, _ ->
                done = actionId == EditorInfo.IME_ACTION_DONE
                done
            }
            connection.performEditorAction(EditorInfo.IME_ACTION_DONE)
            assertTrue(done)
        }
    }

    @Test
    fun `moving to a normal answer restores its keyboard without losing text or selection`() {
        withAnswerFields { field, setNoSuggest ->
            setNoSuggest(true)
            field.setText("été")
            field.setSelection(1)
            assertNotNull(field.onCreateInputConnection(EditorInfo()))

            setNoSuggest(false)
            val info = EditorInfo()
            assertNotNull(field.onCreateInputConnection(info))
            assertEquals(INPUT_TYPE, info.inputType)
            assertEquals("été", field.text.toString())
            assertEquals(1, field.selectionStart)
            assertEquals(1, info.initialSelStart)
        }
    }

    private fun withAnswerFields(block: (EditText, (Boolean) -> Unit) -> Unit) {
        targetContext.setTheme(DayTheme.LIGHT.styleResId)
        val legacy = FixedEditText(targetContext)
        val material = TypeAnswerEditText(targetContext, null)
        for (field in listOf(legacy, material)) {
            field.inputType = INPUT_TYPE
            field.imeOptions = EditorInfo.IME_ACTION_DONE
            field.imeHintLocales = LocaleList.forLanguageTags("fr")
            block(field) { enabled ->
                when (field) {
                    is FixedEditText -> field.noSuggest = enabled
                    is TypeAnswerEditText -> field.noSuggest = enabled
                }
            }
        }
    }

    companion object {
        private const val INPUT_TYPE = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }
}
