// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.testutil.waitUntil
import com.ichi2.preferences.IncrementerNumberRangePreferenceCompat
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertNull

/** Exercise the actual accessibility tree and soft keyboard, including Compose virtual nodes. */
@RunWith(AndroidJUnit4::class)
class BackupPickerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    @Test
    fun keyboardInputBackAndCancelKeepTheirBehavior() =
        withPicker { scenario, preference, dialog ->
            awaitKeyboard(scenario, dialog, visible = true)
            val input = editableNode()
            assertTrue(input.isFocused)
            assertEquals(InputType.TYPE_CLASS_NUMBER, input.inputType)
            assertEquals(2, input.textSelectionStart)
            assertEquals(2, input.textSelectionEnd)
            val snapshot =
                JSONObject()
                    .put(
                        "inputType",
                        input.inputType,
                    ).put("selectionStart", input.textSelectionStart)
                    .put("selectionEnd", input.textSelectionEnd)
            val artifacts = File(instrumentation.targetContext.filesDir, "backup-picker").apply { mkdirs() }
            File(artifacts, "keyboard.json").writeText(snapshot.toString(2))
            assertTrue(device.takeScreenshot(File(artifacts, "keyboard.png")))
            replaceText("31")
            assertEquals("31", editableNode().text.toString())
            scenario.onActivity {
                assertTrue(dialog.isShowing)
                assertEquals("30", preference.text)
            }
            device.pressBack()
            awaitKeyboard(scenario, dialog, visible = false)
            scenario.onActivity { assertTrue(dialog.isShowing) }
            device.pressBack()
            scenario.onActivity {
                assertFalse(dialog.isShowing)
                assertEquals("30", preference.text)
            }
        }

    @Test
    fun accessibilityLabelsTouchTargetsAndRangeErrorsArePreserved() =
        withPicker { scenario, _, dialog ->
            awaitKeyboard(scenario, dialog, visible = true)
            val context = instrumentation.targetContext
            for (label in listOf(CommonString.minus_sign, CommonString.plus_sign)) {
                val node = labeledNode(context.getString(label))
                assertTrue(node.isClickable)
                assertTrue(node.isEnabled)
                val bounds = Rect().also(node::getBoundsInScreen)
                assertTrue(bounds.width() / context.resources.displayMetrics.density >= 48)
                assertTrue(bounds.height() / context.resources.displayMetrics.density >= 48)
            }
            replaceText("4")
            waitUntil(message = { "Expected accessible range error" }) { !editableNode().error.isNullOrEmpty() }
            assertEquals(context.getString(CommonString.minimum_value_is, 5), editableNode().error)
            scenario.onActivity { assertFalse(dialog.positiveButton.isEnabled) }
            assertFalse(labeledNode(context.getString(CommonString.minus_sign)).isEnabled)
            assertTrue(labeledNode(context.getString(CommonString.plus_sign)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitUntil(message = { "Expected minimum value after increment" }) { editableNode().text.toString() == "5" }
            assertNull(editableNode().error)
            scenario.onActivity { dialog.negativeButton.performClick() }
        }

    @Test
    fun recreationRestoresUnconfirmedTextAndSelection() =
        withPicker { scenario, _, _ ->
            replaceText("123")
            assertTrue(
                editableNode().performAction(
                    AccessibilityNodeInfo.ACTION_SET_SELECTION,
                    Bundle().apply {
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 1)
                        putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 2)
                    },
                ),
            )
            waitUntil(message = { "Expected selected range before recreation" }) {
                editableNode().let { it.textSelectionStart == 1 && it.textSelectionEnd == 2 }
            }
            scenario.recreate()
            waitUntil(message = { "Expected restored text and selection after attachment" }) {
                editableNode().let { it.text.toString() == "123" && it.textSelectionStart == 1 && it.textSelectionEnd == 2 }
            }
            val input = editableNode()
            assertEquals("123", input.text.toString())
            assertEquals(1, input.textSelectionStart)
            assertEquals(2, input.textSelectionEnd)
            scenario.onActivity { activity ->
                val parent = activity.fragment as PreferencesFragment
                val fragment = parent.childFragmentManager.findFragmentById(R.id.settings_container) as BackupLimitsSettingsFragment
                assertEquals(
                    "30",
                    fragment
                        .requirePreference<IncrementerNumberRangePreferenceCompat>(
                            R.string.pref_minutes_between_automatic_backups_key,
                        ).text,
                )
            }
        }

    internal fun editableNode(): AccessibilityNodeInfo = findNode { it.isEditable }

    private fun labeledNode(label: String): AccessibilityNodeInfo =
        findNode { it.contentDescription?.toString() == label && it.isClickable }

    private fun findNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        fun search(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (predicate(node)) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { search(it)?.let { found -> return found } }
            return null
        }
        var found: AccessibilityNodeInfo? = null
        waitUntil(message = { "Picker accessibility node missing" }) {
            found = instrumentation.uiAutomation.rootInActiveWindow?.let(::search)
            found != null
        }
        return found!!
    }

    private fun replaceText(text: String) {
        assertTrue(
            editableNode().performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                },
            ),
        )
        waitUntil(message = { "Expected edited text $text" }) { editableNode().text.toString() == text }
    }

    private fun awaitKeyboard(
        scenario: ActivityScenario<PreferencesActivity>,
        dialog: AlertDialog,
        visible: Boolean,
    ) {
        waitUntil(message = { "Expected keyboard visibility $visible" }) {
            var matches = false
            scenario.onActivity {
                matches = ViewCompat.getRootWindowInsets(dialog.window!!.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == visible
            }
            matches
        }
    }

    @SuppressLint("DiscouragedApi") // Only simple settings commands; no shell syntax or stdin.
    internal fun withPicker(block: (ActivityScenario<PreferencesActivity>, IncrementerNumberRangePreferenceCompat, AlertDialog) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
        device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        try {
            val intent = PreferencesActivity.getIntent(context, BackupLimitsSettingsFragment::class)
            ActivityScenario.launch<PreferencesActivity>(intent).use { scenario ->
                lateinit var preference: IncrementerNumberRangePreferenceCompat
                lateinit var dialog: AlertDialog
                scenario.onActivity { activity ->
                    val parent = activity.fragment as PreferencesFragment
                    val fragment = parent.childFragmentManager.findFragmentById(R.id.settings_container) as BackupLimitsSettingsFragment
                    preference = fragment.requirePreference(R.string.pref_minutes_between_automatic_backups_key)
                    preference.setValue(30)
                    fragment.onDisplayPreferenceDialog(preference)
                    parent.childFragmentManager.executePendingTransactions()
                    dialog =
                        (
                            parent.childFragmentManager.findFragmentByTag(
                                "androidx.preference.PreferenceFragment.DIALOG",
                            ) as DialogFragment
                        ).requireDialog() as AlertDialog
                }
                block(scenario, preference, dialog)
            }
        } finally {
            device.executeShellCommand(
                if (previous ==
                    "null"
                ) {
                    "settings delete secure show_ime_with_hard_keyboard"
                } else {
                    "settings put secure show_ime_with_hard_keyboard $previous"
                },
            )
        }
    }
}
