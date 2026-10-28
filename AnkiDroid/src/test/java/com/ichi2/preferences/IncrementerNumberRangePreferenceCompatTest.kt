// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Rakshita Chauhan <chauhanrakshita64@gmail.com>

package com.ichi2.preferences

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.os.bundleOf
import androidx.preference.PreferenceFragmentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.SettingsFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.compose.ui.components.NumberPickerState.Companion.ValidationResult
import com.ichi2.compose.ui.components.NumberPickerState.Companion.validate
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class IncrementerNumberRangePreferenceCompatTest : RobolectricTest() {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun `backup interval starts with its current non-persistent value and saves an integer`() {
        withBackupIntervalDialog { preference, dialog ->
            val input = dialog.findViewById<EditText>(android.R.id.edit)!!
            assertEquals("30", input.text.toString())
            var selectedValue: Any? = null
            preference.setOnPreferenceChangeListener { _, value ->
                selectedValue = value
                true
            }
            input.setText("31")
            dialog.positiveButton.performClick()
            advanceRobolectricLooper()
            assertEquals(31, selectedValue)
            assertEquals("31", preference.text)
        }
    }

    @Test
    fun `cancelling or rejecting a preference change keeps its current value`() {
        withBackupIntervalDialog { preference, dialog ->
            preference.setOnPreferenceChangeListener { _, _ -> error("Cancel must not save") }
            dialog.findViewById<EditText>(android.R.id.edit)!!.setText("31")
            dialog.negativeButton.performClick()
            advanceRobolectricLooper()
            assertEquals("30", preference.text)
        }
        withBackupIntervalDialog { preference, dialog ->
            preference.setOnPreferenceChangeListener { _, _ -> false }
            dialog.findViewById<EditText>(android.R.id.edit)!!.setText("31")
            dialog.positiveButton.performClick()
            advanceRobolectricLooper()
            assertEquals("30", preference.text)
        }
    }

    @Test
    fun `numeric keyboard starts at the end and Done does not save or dismiss`() {
        withBackupIntervalDialog { preference, dialog ->
            val input = dialog.findViewById<EditText>(android.R.id.edit)!!
            assertTrue(input.hasFocus())
            assertEquals(2, input.selectionStart)
            assertEquals(2, input.selectionEnd)
            val editor = EditorInfo()
            assertNotNull(input.onCreateInputConnection(editor))
            assertEquals(InputType.TYPE_CLASS_NUMBER, editor.inputType)
            assertEquals(EditorInfo.IME_ACTION_DONE, editor.imeOptions)
            input.setText("31")
            input.onEditorAction(EditorInfo.IME_ACTION_DONE)
            assertTrue(dialog.isShowing)
            assertEquals("30", preference.text)
            dialog.negativeButton.performClick()
        }
    }

    @Test
    fun `range errors disable confirmation and allow stepping back into range`() {
        withBackupIntervalDialog { _, dialog ->
            val input = dialog.findViewById<EditText>(android.R.id.edit)!!
            val decrement = composeRule.onNodeWithContentDescription(getResourceString(CommonString.minus_sign))
            val increment = composeRule.onNodeWithContentDescription(getResourceString(CommonString.plus_sign))
            input.setText("4")
            composeRule.waitForIdle()
            assertEquals(targetContext.getString(CommonString.minimum_value_is, 5), input.createAccessibilityNodeInfo().error)
            assertFalse(dialog.positiveButton.isEnabled)
            decrement.assertIsNotEnabled()
            increment.assertIsEnabled().performClick()
            composeRule.waitForIdle()
            assertEquals("5", input.text.toString())
            assertTrue(dialog.positiveButton.isEnabled)
            decrement.assertIsNotEnabled()
            dialog.negativeButton.performClick()
        }
    }

    @Test
    fun `paste filters keep numeric input length and leading zero behavior`() {
        withBackupIntervalDialog { _, dialog ->
            val input = dialog.findViewById<EditText>(android.R.id.edit)!!

            fun paste(text: String) {
                input.text.replace(0, input.length(), text)
            }
            paste("123456")
            assertEquals("12345", input.text.toString())
            paste("01")
            assertEquals("", input.text.toString())
            paste("abc12")
            assertEquals("12", input.text.toString())
            paste("")
            assertFalse(dialog.positiveButton.isEnabled)
            dialog.negativeButton.performClick()
        }
    }

    @Test
    fun `preference input binding callbacks are preserved`() {
        var bound = false
        withBackupIntervalDialog(setup = { preference ->
            preference.setOnBindEditTextListener { input ->
                bound = true
                input.setSelection(1)
            }
        }) { _, dialog ->
            assertTrue(bound)
            assertEquals(1, dialog.findViewById<EditText>(android.R.id.edit)!!.selectionStart)
            dialog.negativeButton.performClick()
        }
    }

    private fun withBackupIntervalDialog(
        setup: (NumberRangePreferenceCompat) -> Unit = {},
        block: (NumberRangePreferenceCompat, AlertDialog) -> Unit,
    ) {
        val activity = startRegularActivity<AnkiActivity>()
        val fragment = BackupPreferencesFragment()
        activity.supportFragmentManager
            .beginTransaction()
            .add(fragment, "preferences")
            .commitNow()
        val preference =
            fragment.requirePreference<IncrementerNumberRangePreferenceCompat>(
                R.string.pref_minutes_between_automatic_backups_key,
            )
        preference.setValue(30)
        setup(preference)
        val picker = preference.makeDialogFragment()
        picker.arguments = bundleOf(SettingsFragment.ARG_PREFERENCE_KEY to preference.key)
        @Suppress("DEPRECATION") // Same preference-dialog contract as SettingsFragment.
        picker.setTargetFragment(fragment, 0)
        picker.showNow(activity.supportFragmentManager, "picker")
        advanceRobolectricLooper()
        composeRule.waitForIdle()
        block(preference, picker.requireDialog() as AlertDialog)
        advanceRobolectricLooper()
        activity.finish()
    }

    class BackupPreferencesFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(
            savedInstanceState: Bundle?,
            rootKey: String?,
        ) {
            addPreferencesFromResource(R.xml.preferences_backup_limits)
        }
    }

    @Test
    fun testValidationLogic() {
        val min = 30
        val max = 99999

        assertThat(
            "A number within the min/max range should be VALID",
            validate("60", 60, min, max),
            IsEqual(ValidationResult.VALID),
        )

        assertThat(
            "An empty string should return EMPTY state",
            validate("", null, min, max),
            IsEqual(ValidationResult.EMPTY),
        )

        assertThat(
            "A number greater than the maximum should return OVERFLOW",
            validate("100000", 100000, min, max),
            IsEqual(ValidationResult.OVERFLOW),
        )

        assertThat(
            "A number less than the minimum should return UNDERFLOW",
            validate("29", 29, min, max),
            IsEqual(ValidationResult.UNDERFLOW),
        )

        assertThat(
            "A number exceeding Integer limits should return INVALID",
            validate("9999999999", null, min, max),
            IsEqual(ValidationResult.INVALID),
        )
    }
}
