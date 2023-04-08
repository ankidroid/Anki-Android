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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
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
@Deprecated("This method is deprecated as it is not type-safe. Use setTitle() instead.", ReplaceWith("setTitle(stringRes ?: text)"))
fun AlertDialog.Builder.title(@StringRes stringRes: Int? = null, text: String? = null): AlertDialog.Builder {
    if (stringRes == null && text == null) {
        throw IllegalArgumentException("either `stringRes` or `text` must be set")
    }
    return if (stringRes != null) {
        setTitle(stringRes)
    } else {
        setTitle(text)
    }
}

@Deprecated("This method is deprecated as it is not type-safe. Use setMessage() instead.", ReplaceWith("setMessage(stringRes ?: text)"))
fun AlertDialog.Builder.message(@StringRes stringRes: Int? = null, text: CharSequence? = null): AlertDialog.Builder {
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
@Deprecated("This method is deprecated as it is redundant. Use setIconAttribute() instead.", ReplaceWith("setIconAttribute(res)"))
fun AlertDialog.Builder.iconAttr(
    @DrawableRes res: Int
) = apply {
    return this.setIcon(Themes.getResFromAttr(this.context, res))
}

@Deprecated("This method is deprecated as it is redundant. Use setPositiveButton() instead.", ReplaceWith("setPositiveButton(stringRes ?: text, click)"))
fun AlertDialog.Builder.positiveButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null
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

@Deprecated("This method is deprecated as it is redundant. Use setNeutralButton() instead.", ReplaceWith("setNeutralButton(stringRes ?: text, click)"))
fun AlertDialog.Builder.neutralButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null
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

@Deprecated("This method is deprecated as it is redundant. Use setNegativeButton() instead.", ReplaceWith("setNegativeButton(stringRes ?: text, click)"))
fun AlertDialog.Builder.negativeButton(
    @StringRes stringRes: Int? = null,
    text: CharSequence? = null,
    click: DialogInterfaceListener? = null
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

@Deprecated("This method is deprecated as it is redundant. Use setCancelable() instead.", ReplaceWith("setCancelable(cancelable)"))
fun AlertDialog.Builder.cancelable(cancelable: Boolean): AlertDialog.Builder {
    return this.setCancelable(cancelable)
}

/**
 * Executes the provided block, then creates an [AlertDialog] with the arguments supplied
 * and immediately displays the dialog
 */
@Deprecated("This method is deprecated. Use showAlertDialog { ... } instead.")
inline fun AlertDialog.Builder.show(block: AlertDialog.Builder.() -> Unit): AlertDialog.Builder = apply {
    this.block()
    this.show()
}
