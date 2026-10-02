// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Temporarily selects the test APK's IME and restores the previous keyboard even on failure. */
class TestInputMethodRule(
    private val executeShellCommand: (String) -> String = ::executeImeCommand,
) : TestRule {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val testPackage get() = instrumentation.context.packageName
    private val uri get() = "content://$testPackage.ime".toUri()

    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val previous = executeShellCommand("settings get secure default_input_method").trim()
                val ime = "$testPackage/${TestInputMethod::class.java.name}"
                val wasEnabled = executeShellCommand("ime list -s").lineSequence().any { it.trim() == ime }
                val failures = mutableListOf<Throwable>()
                try {
                    executeShellCommand("ime enable $ime")
                    executeShellCommand("ime set $ime")
                    assertEquals(ime, executeShellCommand("settings get secure default_input_method").trim())
                    base.evaluate()
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                // Attempt each cleanup independently, preserving the original test failure.
                try {
                    if (previous.isNotEmpty() && previous != "null") {
                        val restoreOutput = executeShellCommand("ime set $previous")
                        assertEquals(
                            previous,
                            executeShellCommand("settings get secure default_input_method").trim(),
                            "Failed to restore keyboard $previous: $restoreOutput",
                        )
                    }
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                try {
                    if (!wasEnabled) {
                        val disableOutput = executeShellCommand("ime disable $ime")
                        assertFalse(
                            executeShellCommand("ime list -s").lineSequence().any { it.trim() == ime },
                            "Failed to disable test keyboard $ime: $disableOutput",
                        )
                    }
                } catch (failure: Throwable) {
                    failures.add(failure)
                }
                MultipleFailureException.assertEmpty(failures)
            }
        }

    private fun snapshot(): Bundle = assertNotNull(instrumentation.context.contentResolver.call(uri, "editor", null, null))

    /** Chromium and the keyboard bridge update asynchronously; match all requested properties in one snapshot. */
    fun awaitEditor(
        inputType: Int? = null,
        imeAction: Int? = null,
    ): EditorInfo {
        var info: EditorInfo? = null
        waitUntil(message = {
            val actualAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            "IME expected inputType=$inputType, action=$imeAction; " +
                "received inputType=${info?.inputType}, action=$actualAction, package=${info?.packageName}"
        }) {
            info = BundleCompat.getParcelable(snapshot(), "editor", EditorInfo::class.java)
            val editor = info ?: return@waitUntil false
            val matchesInputType =
                if (inputType == null) {
                    editor.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                        editor.inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                } else {
                    editor.inputType == inputType
                }
            editor.packageName == instrumentation.targetContext.packageName && matchesInputType &&
                (imeAction == null || editor.imeOptions and EditorInfo.IME_MASK_ACTION == imeAction)
        }
        return assertNotNull(info)
    }

    fun perform(
        action: String,
        text: String? = null,
    ) {
        val result = instrumentation.context.contentResolver.call(uri, action, text, null)
        assertTrue(result?.getBoolean("accepted") == true, "IME rejected $action")
    }
}

// Commands need no shell syntax or stdin; the rule verifies the resulting keyboard state.
@SuppressLint("DiscouragedApi")
private fun executeImeCommand(command: String): String =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).executeShellCommand(command)
