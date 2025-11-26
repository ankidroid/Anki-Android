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

import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.OnClickListener
import android.text.InputFilter
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.R
import com.ichi2.themes.Themes
import com.ichi2.ui.FixedTextView
import com.ichi2.utils.HandlerUtils.executeOnMainThread
import timber.log.Timber

/** Wraps [DialogInterface.OnClickListener] as we don't need the `which` parameter */
typealias DialogInterfaceListener = (DialogInterface) -> Unit

fun DialogInterfaceListener.toClickListener(): OnClickListener = OnClickListener { dialog: DialogInterface, _ -> this(dialog) }

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

fun AlertDialog.Builder.cancelable(cancelable: Boolean): AlertDialog.Builder = this.setCancelable(cancelable)

/**
 * Executes the provided block, then creates an [AlertDialog] with the arguments supplied
 * and immediately displays the dialog
 */
inline fun <T : AlertDialog.Builder> T.show(
    enableEnterKeyHandler: Boolean = false, // Make it opt-in
    block: T.() -> Unit,
): AlertDialog {
    this.apply { block() }
    val dialog = this.show()
    return if (enableEnterKeyHandler) {
        dialog.setupEnterKeyHandler()
    } else {
        dialog
    }
}

/**
 * Extension function to configure an AlertDialog to handle the Enter key press event.
 * This will make the Enter key directly trigger the positive button action instead of just selecting it.
 */
fun AlertDialog.setupEnterKeyHandler(): AlertDialog {
    this.setOnKeyListener { dialog, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
            // Get the positive button and simulate a click
            val positiveButton = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
            if (positiveButton != null && positiveButton.isEnabled) {
                positiveButton.performClick()
                return@setOnKeyListener true
            }
        }
        false
    }
    return this
}

/**
 * Creates an [AlertDialog] from the [AlertDialog.Builder] instance, then executes [block] with it.
 */
fun AlertDialog.Builder.createAndApply(block: AlertDialog.() -> Unit): AlertDialog =
    create().apply {
        block()
    }

/**
 * Executes [block] on the [AlertDialog.Builder] instance and returns the initialized [AlertDialog].
 */
fun <T : AlertDialog.Builder> T.create(block: T.() -> Unit): AlertDialog {
    block()
    return create()
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

fun AlertDialog.getCheckBoxPrompt(): CheckBox =
    requireNotNull(findViewById(R.id.checkbox)) {
        "CheckBox prompt is not available. Forgot to call AlertDialog.Builder.checkBoxPrompt()?"
    }

/**
 * Sets a custom view for the dialog.
 *
 * @param view the view to display in the dialog
 * @param paddingStart the start padding in pixels
 * @param paddingTop the top padding in pixels
 * @param paddingEnd the end padding in pixels
 * @param paddingBottom the bottom padding in pixels
 *
 * @see [AlertDialog.Builder.setView]
 * @see [View.setPaddingRelative]
 */
fun AlertDialog.Builder.customView(
    view: View,
    paddingTop: Int = 0,
    paddingBottom: Int = 0,
    paddingStart: Int = 0,
    paddingEnd: Int = 0,
): AlertDialog.Builder {
    val container = FrameLayout(context)

    val containerParams =
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )

    container.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)
    container.addView(view, containerParams)
    setView(container)

    return this
}

fun AlertDialog.Builder.customListAdapter(adapter: RecyclerView.Adapter<*>) {
    val recyclerView = LayoutInflater.from(context).inflate(R.layout.dialog_generic_recycler_view, null, false) as RecyclerView
    recyclerView.adapter = adapter
    recyclerView.layoutManager = LinearLayoutManager(context)
    this.setView(recyclerView)
}

/**
 * Adds a RecyclerView with a custom adapter and decoration to the AlertDialog.
 * @param adapter The adapter for the RecyclerView.
 * @param context The context used to access resources and LayoutInflater.
 */
fun AlertDialog.Builder.customListAdapterWithDecoration(
    adapter: RecyclerView.Adapter<*>,
    context: Context,
) {
    val recyclerView = LayoutInflater.from(context).inflate(R.layout.dialog_generic_recycler_view, null, false) as RecyclerView
    recyclerView.adapter = adapter
    recyclerView.layoutManager = LinearLayoutManager(context)
    val dividerItemDecoration = DividerItemDecoration(recyclerView.context, LinearLayoutManager.VERTICAL)
    recyclerView.addItemDecoration(dividerItemDecoration)
    this.setView(recyclerView)
}

/**
 * Note: using [waitForPositiveButton] = true doesn't automatically close the dialog and it
 * requires a manual call to [android.app.Dialog.dismiss] inside the callback listening for text
 * input to replicate the standard dialog behavior.
 *
 * @param hint The hint text to be displayed to the user
 * @param prefill The text to initially appear in the [EditText]
 * @param allowEmpty If true, [DialogInterface.BUTTON_POSITIVE] is disabled if the [EditText] is empty
 * @param displayKeyboard Whether to open the keyboard when the dialog appears
 * @param callback if [waitForPositiveButton], called when [positiveButton] is pressed, otherwise
 *  called whenever the text is changed
 * @param maxLength if set, the user may not enter more than the supplied number of digits
 * @param inputType see [EditText.setInputType]
 * @param waitForPositiveButton MaterialDialog compat: if `false` [callback] is called on input
 * if `true` [callback] is called when [positiveButton] is pressed
 */
fun AlertDialog.input(
    hint: String? = null,
    inputType: Int? = null,
    prefill: CharSequence? = null,
    allowEmpty: Boolean = false,
    maxLength: Int? = null,
    displayKeyboard: Boolean = false,
    waitForPositiveButton: Boolean = true,
    callback: (AlertDialog, CharSequence) -> Unit,
): AlertDialog {
    // Builder.setView() may not be called before show()
    if (!this.isShowing) throw IllegalStateException("input() requires .show()")

    getInputTextLayout().hint = hint

    getInputField().apply {
        if (displayKeyboard) {
            AndroidUiUtils.setFocusAndOpenKeyboard(this, window!!)
        }

        inputType?.let { this.inputType = it }

        if (!waitForPositiveButton) {
            doOnTextChanged { text, _, _, _ ->
                callback(this@input, text ?: "")
            }
        } else {
            positiveButton.setOnClickListener { callback(this@input, this.text.toString()) }
        }

        if (!allowEmpty) {
            // this is called after callback() so allowEmpty takes priority
            doOnTextChanged { text, _, _, _ ->
                if (waitForPositiveButton) {
                    // this is the only validation filter we apply - toggle on or off
                    this@input.positiveButton.isEnabled = !text.isNullOrEmpty()
                } else if (text.isNullOrEmpty()) {
                    // potentially other filters in `waitForPositiveButton`.
                    // WARN: this could be buggy as it does not toggle the button back on
                    this@input.positiveButton.isEnabled = false
                }
            }
        }

        maxLength?.let { filters += InputFilter.LengthFilter(it) }

        requestFocus()
        // this calls callback(this, prefill). positiveButton may be disabled if there's no prefill
        setText(prefill)
        moveCursorToEnd()
    }
    return this
}

/**
 * @return the layout for the input text of the dialog
 * @throws IllegalArgumentException if the dialog does not contain [R.id.dialog_text_input_layout]]
 */
fun AlertDialog.getInputTextLayout() =
    requireNotNull(findViewById<TextInputLayout>(R.id.dialog_text_input_layout)) {
        "view must be dialog_generic_text_input"
    }

/**
 * @return the [EditText] of the dialog
 * @throws IllegalArgumentException if the dialog does not contain [R.id.dialog_text_input_layout]]
 */
fun AlertDialog.getInputField() = getInputTextLayout().editText!!

/** @see AlertDialog.getButton */
val AlertDialog.positiveButton: Button
    get() = getButton(DialogInterface.BUTTON_POSITIVE)
val AlertDialog.negativeButton: Button
    get() = getButton(DialogInterface.BUTTON_NEGATIVE)
val AlertDialog.neutralButton: Button?
    get() = getButton(DialogInterface.BUTTON_NEUTRAL)

/**
 * Extension function for AlertDialog.Builder to set a list of items.
 * Items are not displayed if [AlertDialog.Builder.setMessage] has been called
 *
 * @param items The items to display in the list.
 * @param onClick A lambda function that is invoked when an item is clicked.
 */
fun AlertDialog.Builder.listItems(
    items: List<CharSequence>,
    onClick: (dialog: DialogInterface, index: Int) -> Unit,
): AlertDialog.Builder =
    this.setItems(items.toTypedArray()) { dialog, which ->
        onClick(dialog, which)
    }

/**
 * Extension workaround for Displaying ListView & Message Together
 * Alert Dialog Doesn't allow message and listview together so a customView is used.
 *
 * @param message The message which you want to display in the dialog
 * @param items The items to display in the list.
 * @param onClick A lambda function that is invoked when an item is clicked.
 */
fun AlertDialog.Builder.listItemsAndMessage(
    message: String?,
    items: List<CharSequence>,
    onClick: (dialog: DialogInterface, index: Int) -> Unit,
): AlertDialog.Builder {
    val dialogView = View.inflate(this.context, R.layout.dialog_listview_message, null)
    dialogView.findViewById<FixedTextView>(R.id.dialog_message).text = message

    val listView = dialogView.findViewById<ListView>(R.id.dialog_list_view)
    listView.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, items)

    val dialog = this.create()
    listView.setOnItemClickListener { _, _, index, _ ->
        onClick(dialog, index)
    }
    return this.setView(dialogView)
}

/**
 * Adds a custom title view to the dialog with a 'help' icon. Typically used to open the Anki Manual
 *
 * **Example:**
 * ```kotlin
 * MaterialAlertDialogBuilder(context).create {
 *     titleWithHelpIcon(stringRes = R.string.reset_card_dialog_title) {
 *         requireActivity().openUrl(Uri.parse(getString(R.string.link_manual)))
 *     }
 * }
 * ```
 *
 * @param block action executed when the help icon is clicked
 *
 */
fun AlertDialog.Builder.titleWithHelpIcon(
    @StringRes stringRes: Int? = null,
    text: String? = null,
    block: View.OnClickListener,
) {
    // setup the view for the dialog
    val customTitleView = LayoutInflater.from(context).inflate(R.layout.alert_dialog_title_with_help, null, false)
    setCustomTitle(customTitleView)

    // apply a custom title
    val titleTextView = customTitleView.findViewById<TextView>(android.R.id.title)

    if (stringRes != null) {
        titleTextView.setText(stringRes)
    } else if (text != null) {
        titleTextView.text = text
    }

    // set the action when clicking the help icon
    customTitleView.findViewById<ImageView>(R.id.help_icon).setOnClickListener { v ->
        Timber.i("dialog help icon click")
        block.onClick(v)
    }
}

/** Calls [AlertDialog.dismiss], ignoring errors */
fun AlertDialog.dismissSafely() {
    // The exception will be uncaught if not run on the main thread.
    executeOnMainThread {
        try {
            // safer to catch the exception to be sure dismiss() was called
            dismiss()
        } catch (e: IllegalArgumentException) {
            if (window == null || !isShowing) {
                Timber.d(e, "Dialog not attached to window manager")
                return@executeOnMainThread
            }
            Timber.w(e, "Dialog not attached to window manager")
        }
    }
}
