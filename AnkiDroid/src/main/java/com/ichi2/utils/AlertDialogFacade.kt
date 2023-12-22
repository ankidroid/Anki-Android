/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

@file:Suppress("unused")

package com.ichi2.utils

import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.themes.Themes

/** Wraps [DialogInterface.OnClickListener] as we don't need the `which` parameter */
typealias DialogInterfaceListener = (DialogInterface) -> Unit

fun DialogInterfaceListener.toClickListener(): OnClickListener {
    return OnClickListener { dialog: DialogInterface, _ -> this(dialog) }
}

/*
 * Allows easier transformations from [MaterialDialog] to [AlertDialog].
 * Inline this file when material dialog is removed
 */
fun AlertDialog.Builder.title(
    @StringRes stringRes: Int? = null,
    text: String? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        setTitle(stringRes)
    } else {
        setTitle(text)
    }
}

fun AlertDialog.Builder.message(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        setMessage(stringRes)
    } else {
        setMessage(text)
    }
}

/**
 * Shows an icon to the left of the dialog title.
 */
fun AlertDialog.Builder.iconAttr(
    @DrawableRes res: Int,
) = apply {
    return this.setIcon(Themes.getResFromAttr(this.context, res))
}

fun AlertDialog.Builder.positiveButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setPositiveButton(stringRes, click?.toClickListener())
    } else {
        this.setPositiveButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.neutralButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setNeutralButton(stringRes, click?.toClickListener())
    } else {
        this.setNeutralButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.negativeButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        this.setNegativeButton(stringRes, click?.toClickListener())
    } else {
        this.setNegativeButton(text, click?.toClickListener())
    }
}

fun AlertDialog.Builder.cancelable(cancelable: Boolean): AlertDialog.Builder {
    return this.setCancelable(cancelable)
}

/**
 * Executes the provided block, then creates an [AlertDialog] with the arguments supplied
 * and immediately displays the dialog
 */
inline fun AlertDialog.Builder.show(block: AlertDialog.Builder.() -> Unit): AlertDialog.Builder =
    apply {
        this.block()
        this.show()
    }

/**
 * Adds a checkbox to the dialog, whilst continuing to display the value of [message]
 * @param stringRes The string resource to display for the checkbox label.
 * @param text The literal string to display for the checkbox label.
 * @param isCheckedDefault Whether or not the checkbox is initially checked.
 * @param onToggle A listener invoked when the checkbox is checked or unchecked.
 */
fun AlertDialog.Builder.checkBoxPrompt(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    isCheckedDefault: Boolean = false,
    onToggle: (checked: Boolean) -> Unit,
): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    val checkBoxView = View.inflate(this.context, R.layout.alert_dialog_checkbox, null)
    val checkBox = checkBoxView.findViewById<CheckBox>(R.id.checkbox)

    val checkBoxLabel = if (stringRes != null) context.getString(stringRes) else text
    checkBox.text = checkBoxLabel
    checkBox.isChecked = isCheckedDefault

    checkBox.setOnCheckedChangeListener { _, isChecked ->
        onToggle(isChecked)
    }

    return this.setView(checkBoxView)
}

fun AlertDialog.Builder.customView(
    view: View,
    paddingTop: Int = 0,
    paddingBottom: Int = 0,
    paddingLeft: Int = 0,
    paddingRight: Int = 0,
): AlertDialog.Builder {
    val container = FrameLayout(context)

    val containerParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )

    container.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    container.addView(view, containerParams)
    setView(container)

    return this
}
