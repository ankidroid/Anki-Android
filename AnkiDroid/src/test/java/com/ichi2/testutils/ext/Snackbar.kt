// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.testutils.ext

import android.app.Activity
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

val Activity.snackbarText: String?
    get() = findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.toString()

val Fragment.snackbarText: String?
    get() = requireActivity().snackbarText

val Snackbar.text: String?
    get() =
        this.view
            .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text
            ?.toString()
