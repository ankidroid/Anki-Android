// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>

package com.ichi2.preferences

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextRange
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceDialogFragmentCompat
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.components.IncrementerNumberPicker
import com.ichi2.compose.ui.components.NumberPickerState
import com.ichi2.utils.numberRangeTitle
import com.ichi2.utils.positiveButton

/** Marker class to be used in preferences */
class IncrementerNumberRangePreferenceCompat :
    NumberRangePreferenceCompat,
    DialogFragmentProvider {
    private var inputBindingListener: EditTextPreference.OnBindEditTextListener? = null

    override fun setOnBindEditTextListener(listener: EditTextPreference.OnBindEditTextListener?) {
        super.setOnBindEditTextListener(listener)
        inputBindingListener = listener
    }

    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    class IncrementerNumberRangeDialogFragmentCompat : PreferenceDialogFragmentCompat() {
        private var picker: NumberPickerState? = null
        private var savedInput: Bundle? = null
        private var pausedInput: Bundle? = null
        private val numberRangePreference get() = preference as IncrementerNumberRangePreferenceCompat

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            savedInput = savedInstanceState
        }

        override fun onCreateDialogView(context: Context): View =
            NumberPickerState(
                numberRangePreference.min,
                numberRangePreference.max,
                savedInput?.getString(STATE_TEXT) ?: numberRangePreference.text.orEmpty(),
                savedInput?.let { TextRange(it.getInt(STATE_SELECTION_START), it.getInt(STATE_SELECTION_END)) }
                    ?: TextRange(numberRangePreference.text.orEmpty().length),
            ).let { state ->
                picker = state
                ComposeView(context).apply {
                    setContent {
                        AnkiDroidTheme {
                            IncrementerNumberPicker(state, requestFocus = true, onBindInput = {
                                numberRangePreference.inputBindingListener?.onBindEditText(it)
                            })
                        }
                    }
                }
            }

        override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
            super.onPrepareDialogBuilder(builder)
            builder.numberRangeTitle(preference.dialogTitle ?: preference.title, requireContext())
        }

        override fun needInputMethod(): Boolean = true

        override fun onSaveInstanceState(outState: Bundle) {
            super.onSaveInstanceState(outState)
            pausedInput?.let {
                outState.putAll(it)
                return
            }
            saveInput(outState)
        }

        override fun onPause() {
            // Retain the edited text and selection before the dialog view is stopped.
            pausedInput = Bundle().also(::saveInput)
            super.onPause()
        }

        override fun onResume() {
            super.onResume()
            pausedInput = null
        }

        private fun saveInput(outState: Bundle) {
            picker?.input?.let {
                outState.putString(STATE_TEXT, it.text.toString())
                outState.putInt(STATE_SELECTION_START, it.selection.start)
                outState.putInt(STATE_SELECTION_END, it.selection.end)
            }
        }

        override fun onStart() {
            super.onStart()
            dialog?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            val positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton
            positiveButton?.text = TR.actionsSave()
            picker!!.onValidityChanged = { positiveButton?.isEnabled = it }
            positiveButton?.isEnabled = picker?.value != null
        }

        override fun onDialogClosed(positiveResult: Boolean) {
            if (!positiveResult) return
            val value = picker?.value ?: return
            if (numberRangePreference.callChangeListener(value)) numberRangePreference.setValue(value)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            picker?.onValidityChanged = {}
            picker = null
        }

        private companion object {
            const val STATE_TEXT = "picker.text"
            const val STATE_SELECTION_START = "picker.selectionStart"
            const val STATE_SELECTION_END = "picker.selectionEnd"
        }
    }

    override fun makeDialogFragment() = IncrementerNumberRangeDialogFragmentCompat()
}
