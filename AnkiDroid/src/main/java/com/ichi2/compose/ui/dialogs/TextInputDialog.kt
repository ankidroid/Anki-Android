// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.DialogProperties
import com.google.android.material.textfield.TextInputLayout
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews
import com.google.android.material.R as MaterialR

/**
 * Dialog asking the user for text. Compose counterpart of
 * the View based `AlertDialog.input(...)` helper.
 *
 * @param onConfirm called with the text, but only while it is valid
 * @param initialText initial value shown in the field
 * @param initialSelection initial cursor position or selected range; defaults to the end of [initialText]
 * @param errorMessageFor error to show for the current input, null when valid
 * @param helperText shown when no error is displayed; an empty string reserves feedback space
 * @param description optional context below the title
 * @param transformInput applied to text edits, not cursor-only changes
 * @param validateOnInput when false, validate on confirmation instead of while editing
 * @param enabled whether confirmation is allowed from the button or keyboard
 * @param singleLine whether the field is restricted to one line; multiline input uses the Enter key for newlines
 */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String = stringResource(CommonString.dialog_cancel),
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
    maxLengthCounter: Int? = null,
    lengthOf: (String) -> Int = { it.length },
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    errorMessageFor: (String) -> String? = { null },
    helperText: String? = null,
    description: String? = null,
    transformInput: (TextFieldValue) -> TextFieldValue = { it },
    validateOnInput: Boolean = true,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = initialSelection))
    }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    var submissionAttempted by rememberSaveable { mutableStateOf(false) }
    val errorMessage = if (validateOnInput || submissionAttempted) errorMessageFor(input.text) else null
    val displayedError = errorMessage?.takeIf { hasUserEdited || submissionAttempted }
    val supportingMessage = displayedError ?: helperText
    val hasSupportingText = maxLengthCounter != null || supportingMessage != null || !validateOnInput
    val focusRequester = remember { FocusRequester() }
    val fieldMetrics = dialogFieldMetrics(label, singleLine)

    fun confirmIfValid() {
        if (!enabled) return
        submissionAttempted = true
        if (errorMessageFor(input.text) == null) onConfirm(input.text)
    }
    AnkiDroidDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.width(alertDialogWidth()),
        properties = properties,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            shadowElevation = ankiDroidDialogElevation(),
        ) {
            Layout(modifier = Modifier.padding(ankiDroidDialogContentPadding()), content = {
                // Match the custom input panel in dialog_versatile_text_preference.xml.
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = fieldMetrics.panelBottomPadding)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor,
                        modifier = Modifier.padding(bottom = if (description == null) 16.dp else 8.dp),
                    )
                    if (description != null) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AlertDialogDefaults.textContentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                    DialogTextField(
                        value = input,
                        onValueChange = { proposed ->
                            if (input.text != proposed.text) {
                                input = transformInput(proposed)
                                hasUserEdited = true
                                submissionAttempted = false
                            } else {
                                input = proposed
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        label = { Text(label, Modifier.testTag("text_input_label")) },
                        errorMessage = displayedError,
                        metrics = fieldMetrics,
                        supportingText =
                            if (!hasSupportingText) {
                                null
                            } else {
                                {
                                    Row(Modifier.fillMaxWidth()) {
                                        if (supportingMessage != null || !validateOnInput) {
                                            Text(supportingMessage.orEmpty(), Modifier.weight(1f))
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        if (maxLengthCounter != null) Text("${lengthOf(input.text)}/$maxLengthCounter")
                                    }
                                }
                            },
                        singleLine = singleLine,
                        minLines = if (singleLine) 1 else 4,
                        maxLines = if (singleLine) 1 else 8,
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = capitalization,
                                imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                            ),
                        keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
                    )
                }
                AnkiDroidDialogButtons(
                    confirmText = confirmText,
                    dismissText = dismissText,
                    onConfirm = ::confirmIfValid,
                    onDismissRequest = onDismissRequest,
                    confirmEnabled = enabled && (!validateOnInput || errorMessage == null),
                )
            }) { children, constraints ->
                val actions = children[1].measure(constraints.copy(minHeight = 0))
                val body =
                    children[0].measure(
                        constraints.copy(minHeight = 0, maxHeight = (constraints.maxHeight - actions.height).coerceAtLeast(0)),
                    )
                layout(constraints.maxWidth, body.height + actions.height) {
                    body.placeRelative(0, 0)
                    actions.placeRelative(0, body.height)
                }
            }
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
        }
    }
}

private data class DialogFieldMetrics(
    val textStyle: TextStyle,
    val contentPadding: PaddingValues,
    val labelInset: Dp,
    val minHeight: Dp,
    val panelBottomPadding: Dp,
    val focusedStroke: Dp,
    val unfocusedStroke: Dp,
)

/** Resolve the existing preference field's XML styles before entering the Compose dialog window. */
@Composable
private fun dialogFieldMetrics(
    label: String,
    singleLine: Boolean,
): DialogFieldMetrics {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    return remember(context, configuration, density, label, singleLine) {
        val materialTheme = TypedValue()
        // Compose-only previews can lack an XML theme. Use AnkiDroid's default for the reference field.
        val themedContext =
            if (context.theme.resolveAttribute(MaterialR.attr.isMaterial3Theme, materialTheme, true) && materialTheme.data != 0) {
                context
            } else {
                ContextThemeWrapper(context, DayTheme.LIGHT.styleResId)
            }
        val panel = LayoutInflater.from(themedContext).inflate(R.layout.dialog_versatile_text_preference, null)
        val inputLayout = panel.findViewById<TextInputLayout>(R.id.text_input_layout)
        val edit = panel.findViewById<EditText>(android.R.id.edit)
        inputLayout.hint = label
        edit.setSingleLine(singleLine)
        with(density) {
            DialogFieldMetrics(
                textStyle =
                    TextStyle(
                        fontFamily = FontFamily(edit.typeface),
                        fontSize = edit.textSize.toSp(),
                        letterSpacing = edit.letterSpacing.em,
                        lineHeight = TextUnit.Unspecified,
                        platformStyle = PlatformTextStyle(includeFontPadding = edit.includeFontPadding),
                    ),
                contentPadding =
                    PaddingValues(
                        edit.paddingStart.toDp(),
                        edit.paddingTop.toDp(),
                        edit.paddingEnd.toDp(),
                        edit.paddingBottom.toDp(),
                    ),
                labelInset = ((edit.parent as View).layoutParams as ViewGroup.MarginLayoutParams).topMargin.toDp(),
                minHeight = edit.minimumHeight.toDp(),
                panelBottomPadding = panel.paddingBottom.toDp(),
                focusedStroke = inputLayout.boxStrokeWidthFocused.toDp(),
                unfocusedStroke = inputLayout.boxStrokeWidth.toDp(),
            )
        }
    }
}

/** An outlined field using the existing preference field's resolved dimensions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier,
    label: @Composable () -> Unit,
    errorMessage: String?,
    metrics: DialogFieldMetrics,
    supportingText: (@Composable () -> Unit)?,
    singleLine: Boolean,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val isError = errorMessage != null
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                modifier
                    .semantics(mergeDescendants = true) { errorMessage?.let { error(it) } }
                    .padding(top = metrics.labelInset)
                    .defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = metrics.minHeight),
            textStyle =
                metrics.textStyle.copy(
                    color =
                        if (isError) {
                            colors.errorTextColor
                        } else if (focused) {
                            colors.focusedTextColor
                        } else {
                            colors.unfocusedTextColor
                        },
                ),
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            interactionSource = interactionSource,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value.text,
                    innerTextField = {
                        Layout(content = innerTextField) { children, constraints ->
                            val text = children.single().measure(constraints.copy(minHeight = 0))
                            val padding =
                                metrics.contentPadding.calculateTopPadding().roundToPx() +
                                    metrics.contentPadding.calculateBottomPadding().roundToPx()
                            val minContentHeight = (metrics.minHeight.roundToPx() - padding).coerceAtLeast(0)
                            val height = constraints.constrainHeight(maxOf(text.height, minContentHeight))
                            layout(text.width, height) {
                                // TextView centers with integer division, including an odd spare pixel.
                                text.placeRelative(0, (height - text.height) / 2)
                            }
                        }
                    },
                    enabled = true,
                    singleLine = singleLine,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interactionSource,
                    isError = isError,
                    label = label,
                    supportingText = supportingText,
                    colors = colors,
                    contentPadding = metrics.contentPadding,
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true,
                            isError = isError,
                            interactionSource = interactionSource,
                            colors = colors,
                            focusedBorderThickness = metrics.focusedStroke,
                            unfocusedBorderThickness = metrics.unfocusedStroke,
                        )
                    },
                )
            },
        )
    }
}

/** @see TextInputDialog */
@DialogPreviews
@Composable
private fun TextInputDialogPreview() {
    AnkiDroidPreview {
        TextInputDialog("Rename deck", "Deck name", "Rename", {}, {}, maxLengthCounter = 50)
    }
}
