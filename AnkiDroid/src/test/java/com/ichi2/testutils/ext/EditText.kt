// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import kotlin.test.assertNotNull

/** Connect an IME to this field, failing if it no longer behaves as a text editor. */
fun EditText.requireInputConnection(editorInfo: EditorInfo = EditorInfo()): InputConnection =
    assertNotNull(onCreateInputConnection(editorInfo), "${javaClass.simpleName} must provide an input connection")
