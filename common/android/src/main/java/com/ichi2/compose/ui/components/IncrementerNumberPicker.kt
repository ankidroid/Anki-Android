// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>

package com.ichi2.compose.ui.components

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnAttach
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.textfield.TextInputEditText
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.databinding.DialogIncrementerInputBinding
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.AnkiDroidThemeProvider
import com.ichi2.utils.NonLeadingZeroInputFilter
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import com.ichi2.anki.common.android.R as CommonR

/** Edits an integer with large plus/minus buttons and range validation. */
@Composable
fun IncrementerNumberPicker(
    state: NumberPickerState,
    modifier: Modifier = Modifier,
    requestFocus: Boolean = false,
    onDone: (() -> Unit)? = null,
    onBindInput: (EditText) -> Unit = {},
) {
    val text = state.input.text.toString()
    val value = text.toIntOrNull()
    val validation = NumberPickerState.validate(text, value, state.minimum, state.maximum)
    Row(
        modifier.width(IntrinsicSize.Min).padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlusMinusButton(
            increment = false,
            enabled =
                validation == NumberPickerState.Companion.ValidationResult.OVERFLOW ||
                    (validation == NumberPickerState.Companion.ValidationResult.VALID && value!! > state.minimum),
            onClick = { state.step(false) },
        )
        AndroidView(
            factory = { context ->
                NumberPickerInput(context, state, requestFocus, onBindInput).let { input ->
                    input.binding.root.apply { tag = input }
                }
            },
            modifier = Modifier.weight(1f),
            update = { view -> (view.tag as NumberPickerInput).update(onDone) },
        )
        PlusMinusButton(
            increment = true,
            enabled =
                validation == NumberPickerState.Companion.ValidationResult.UNDERFLOW ||
                    (validation == NumberPickerState.Companion.ValidationResult.VALID && value!! < state.maximum),
            onClick = { state.step(true) },
        )
    }
}

/** Keeps the numeric field's text, selection and validation state in sync. */
private class NumberPickerInput(
    context: Context,
    private val state: NumberPickerState,
    requestFocus: Boolean,
    onBindInput: (EditText) -> Unit,
) {
    val binding = DialogIncrementerInputBinding.inflate(LayoutInflater.from(context))
    private val edit get() = binding.root.editText as NumberPickerEditText
    private var updating = false
    private var onDone: (() -> Unit)? = null

    init {
        edit.inputType = InputType.TYPE_CLASS_NUMBER
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        edit.filters += InputFilter.LengthFilter(state.maximum.toString().length)
        if (requestFocus) edit.requestFocus()
        edit.setText(state.input.text.toString())
        edit.setSelection(
            state.input.selection.start
                .coerceIn(0, edit.length()),
            state.input.selection.end
                .coerceIn(0, edit.length()),
        )
        onBindInput(edit)
        edit.filters += NonLeadingZeroInputFilter
        recordInput()
        edit.selectionChanged = {
            if (!updating && edit.text.toString() == state.input.text.toString()) recordSelection()
        }
        edit.doAfterTextChanged {
            if (!updating) {
                recordInput()
                updateError(force = true)
            }
        }
        if (requestFocus) {
            val textAtBind = state.input.text.toString()
            val selectionAtBind = state.input.selection
            edit.doOnAttach {
                it.post {
                    if (it.isAttachedToWindow) {
                        // Attaching the native field can clear focus and collapse its selection.
                        val selection = if (state.input.text.toString() == textAtBind) selectionAtBind else state.input.selection
                        it.requestFocus()
                        edit.setSelection(selection.start.coerceIn(0, edit.length()), selection.end.coerceIn(0, edit.length()))
                        context.getSystemService<InputMethodManager>()?.showSoftInput(it, 0)
                    }
                }
            }
        }
        updateError()
    }

    /** Copies the field's cursor position and selection into [state]. */
    private fun recordSelection() {
        val selection = TextRange(edit.selectionStart.coerceIn(0, edit.length()), edit.selectionEnd.coerceIn(0, edit.length()))
        if (state.input.selection != selection) state.input.edit { this.selection = selection }
    }

    /** Copies the field's text and selection into [state] and reports validity. */
    private fun recordInput() {
        val text = edit.text.toString()
        state.input.edit {
            if (asCharSequence().toString() != text) replace(0, length, text)
            selection = TextRange(edit.selectionStart.coerceIn(0, edit.length()), edit.selectionEnd.coerceIn(0, edit.length()))
        }
        state.notifyValidity()
    }

    /** Applies the current text, selection, errors and confirmation action to the field. */
    fun update(onDone: (() -> Unit)?) {
        if (this.onDone !== onDone) {
            this.onDone = onDone
            if (onDone == null) {
                edit.setOnEditorActionListener(null)
            } else {
                edit.setOnEditorActionListener { _, action, event ->
                    val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER
                    if (action == EditorInfo.IME_ACTION_DONE || enter) {
                        if ((event == null || event.action == KeyEvent.ACTION_UP) && state.value != null) this.onDone?.invoke()
                        true
                    } else {
                        false
                    }
                }
            }
        }
        val text = state.input.text.toString()
        val selection = state.input.selection
        val changed = edit.text.toString() != text
        updating = true
        try {
            if (changed) edit.setText(text)
            if (edit.selectionStart != selection.start || edit.selectionEnd != selection.end) {
                edit.setSelection(selection.start.coerceIn(0, edit.length()), selection.end.coerceIn(0, edit.length()))
            }
        } finally {
            updating = false
        }
        if (edit.text.toString() != state.input.text.toString()) recordInput()
        updateError(force = changed)
    }

    /** Shows or clears the input's validation error. */
    private fun updateError(force: Boolean = false) {
        val text = state.input.text.toString()
        val error =
            when (NumberPickerState.validate(text, text.toIntOrNull(), state.minimum, state.maximum)) {
                NumberPickerState.Companion.ValidationResult.UNDERFLOW ->
                    binding.root.context.getString(
                        CommonString.minimum_value_is,
                        state.minimum,
                    )
                NumberPickerState.Companion.ValidationResult.OVERFLOW ->
                    binding.root.context.getString(
                        CommonString.maximum_value_is,
                        state.maximum,
                    )
                NumberPickerState.Companion.ValidationResult.INVALID -> binding.root.context.getString(CommonString.invalid_value)
                else -> null
            }
        if (force || binding.root.error?.toString() != error || binding.root.isErrorEnabled != (error != null)) {
            binding.root.error = null
            binding.root.isErrorEnabled = false
            if (error != null) binding.root.error = error
        }
    }
}

/** Text field that reports cursor and selection changes. */
internal class NumberPickerEditText
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : TextInputEditText(context, attrs) {
        /** Called when the cursor position or selection changes. */
        var selectionChanged: (() -> Unit)? = null

        override fun onSelectionChanged(
            selStart: Int,
            selEnd: Int,
        ) {
            super.onSelectionChanged(selStart, selEnd)
            selectionChanged?.invoke()
        }
    }

/** A circular plus or minus button. */
@Composable
private fun PlusMinusButton(
    increment: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val overlay = remember(context) { ElevationOverlayProvider(context) }
    val elevation = with(LocalDensity.current) { if (enabled) 1.dp.toPx() else 0f }
    val parentElevation = overlay.getParentAbsoluteElevation(view)
    val label = stringResource(if (increment) CommonString.plus_sign else CommonString.minus_sign)
    val iconResource = if (increment) CommonR.drawable.ic_add else CommonR.drawable.ic_remove
    val icon =
        remember(context, iconResource) {
            val drawable = AppCompatResources.getDrawable(context, iconResource)!!.mutate().apply { setTint(AndroidColor.WHITE) }
            object : Painter() {
                override val intrinsicSize = Size.Unspecified

                override fun DrawScope.onDraw() {
                    drawable.setBounds(0, 0, size.width.roundToInt(), size.height.roundToInt())
                    drawIntoCanvas { drawable.draw(it.nativeCanvas) }
                }
            }
        }
    val background =
        remember(context, elevation, parentElevation) {
            var color = 0
            context.withStyledAttributes(attrs = intArrayOf(CommonR.attr.incrementerButtonBackground)) { color = getColor(0, 0) }
            Color(overlay.compositeOverlayIfNeeded(color, elevation + parentElevation))
        }
    FilledIconButton(
        onClick = onClick,
        modifier =
            Modifier
                .padding(top = 4.dp)
                .size(48.dp)
                .shadow(if (enabled) 1.dp else 0.dp, CircleShape, clip = false)
                .semantics { contentDescription = label },
        enabled = enabled,
        shape = CircleShape,
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = background,
                contentColor = Color.White,
                disabledContainerColor = background,
                disabledContentColor = Color.White,
            ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = ButtonIconAlignment) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = Color.Unspecified)
        }
    }
}

/** Centers icons, rounding fractional offsets down. */
private val ButtonIconAlignment =
    object : Alignment {
        override fun align(
            size: IntSize,
            space: IntSize,
            layoutDirection: LayoutDirection,
        ): IntOffset = IntOffset((space.width - size.width) / 2, (space.height - size.height) / 2)
    }

/** Previews a valid value. */
@Preview(name = "Value", widthDp = 320)
@Composable
private fun IncrementerNumberPickerPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "30")

/** Previews the minimum value with decrement disabled. */
@Preview(name = "Minimum", widthDp = 320)
@Composable
private fun IncrementerNumberPickerMinimumPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "5")

/** Previews the maximum value with increment disabled. */
@Preview(name = "Maximum", widthDp = 320)
@Composable
private fun IncrementerNumberPickerMaximumPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "60")

/** Previews an empty field with both buttons disabled. */
@Preview(name = "Empty", widthDp = 320)
@Composable
private fun IncrementerNumberPickerEmptyPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "")

/** Previews a value below the minimum. */
@Preview(name = "Below minimum", widthDp = 320)
@Composable
private fun IncrementerNumberPickerBelowMinimumPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "4")

/** Previews a value above the maximum. */
@Preview(name = "Above maximum", widthDp = 320)
@Composable
private fun IncrementerNumberPickerAboveMaximumPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "61")

/** Previews the picker with enlarged text. */
@Preview(name = "Large text", widthDp = 320, fontScale = 2f)
@Composable
private fun IncrementerNumberPickerLargeTextPreview(
    @PreviewParameter(AnkiDroidThemeProvider::class) theme: Theme,
) = NumberPickerPreview(theme, "30")

/** Shows a sample value using the requested theme. */
@Composable
private fun NumberPickerPreview(
    theme: Theme,
    text: String,
) {
    AnkiDroidPreview(theme = theme) {
        Surface(modifier = Modifier.fillMaxWidth()) {
            // Both range-error samples fit the field's two-digit input limit.
            val state = remember(text) { NumberPickerState(minimum = 5, maximum = 60, initialText = text) }
            IncrementerNumberPicker(state, requestFocus = false)
        }
    }
}
