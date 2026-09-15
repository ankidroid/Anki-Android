// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.testutils.ext

import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

val Snackbar.text: String?
    get() =
        this.view
            .findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
            ?.text
            ?.toString()
