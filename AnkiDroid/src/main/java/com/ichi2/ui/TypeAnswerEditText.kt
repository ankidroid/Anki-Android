// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.google.android.material.textfield.TextInputEditText

/** The Material answer field, including support for `{{nosuggest:type:}}`. */
class TypeAnswerEditText(
    context: Context,
    attrs: AttributeSet?,
) : TextInputEditText(context, attrs) {
    /**
     * Disable suggestions using TYPE_NULL, following the Reword app's approach (Issue 10352).
     * Other suggestion settings did not work with Gboard, and password input types could
     * prompt the password manager.
     *
     * This changes [EditorInfo] when connecting to the IME. Callers must restart input
     * after changing this flag if the keyboard is already connected.
     */
    var noSuggest: Boolean = false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs).also { connection ->
            if (connection != null && noSuggest) outAttrs.inputType = InputType.TYPE_NULL
        }
}
