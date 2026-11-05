// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.annotation.SuppressLint
import android.content.ComponentName
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

/** Access to the keyboard settings and IME commands needed by instrumentation tests. */
class TestInputMethodSettings(
    private val executeCommand: (String) -> String = Companion::executeShellCommand,
) {
    /** Null represents an unset secure setting, which must be deleted when restored. */
    var showWithHardwareKeyboard: Boolean?
        get() =
            when (val value = executeCommand("settings get secure show_ime_with_hard_keyboard").trim()) {
                "null" -> null
                "0" -> false
                "1" -> true
                else -> error("Unexpected show_ime_with_hard_keyboard value: $value")
            }
        set(value) {
            executeCommand(
                when (value) {
                    null -> "settings delete secure show_ime_with_hard_keyboard"
                    false -> "settings put secure show_ime_with_hard_keyboard 0"
                    true -> "settings put secure show_ime_with_hard_keyboard 1"
                },
            )
        }

    val exposesHardwareKeyboardSetting: Boolean
        get() = executeCommand("dumpsys input_method").contains(SHOW_WITH_HARDWARE_KEYBOARD)

    // InputMethodService's cached FALSE is 1; the secure setting itself uses 0 for false.
    val hidesWithHardwareKeyboard: Boolean
        get() = executeCommand("dumpsys input_method").contains("${SHOW_WITH_HARDWARE_KEYBOARD}1")

    val defaultInputMethod: ComponentName?
        get() =
            executeCommand("settings get secure default_input_method")
                .trim()
                .takeUnless { it.isEmpty() || it == "null" }
                ?.let { checkNotNull(ComponentName.unflattenFromString(it)) { "Invalid input method: $it" } }

    fun isEnabled(ime: ComponentName): Boolean =
        executeCommand("ime list -s").lineSequence().any { ComponentName.unflattenFromString(it.trim()) == ime }

    fun enable(ime: ComponentName): String = executeCommand("ime enable ${ime.commandArgument()}")

    fun select(ime: ComponentName): String = executeCommand("ime set ${ime.commandArgument()}")

    fun disable(ime: ComponentName): String = executeCommand("ime disable ${ime.commandArgument()}")

    private fun ComponentName.commandArgument(): String {
        val id = flattenToString()
        require(id.matches(Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))) { "Invalid input method: $id" }
        return id
    }

    private companion object {
        const val SHOW_WITH_HARDWARE_KEYBOARD = "mShowImeWithHardKeyboard="

        // Only fixed settings/IME commands with validated arguments; no shell syntax or stdin.
        @SuppressLint("DiscouragedApi")
        private fun executeShellCommand(command: String): String =
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(command)
    }
}
