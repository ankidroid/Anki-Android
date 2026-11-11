// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews
import androidx.appcompat.R as AppCompatR
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
    val dialogPadding =
        PaddingValues(
            start = 24.dp,
            top = dimensionResource(AppCompatR.dimen.abc_dialog_padding_top_material),
            end = 24.dp,
            bottom = dimensionResource(MaterialR.dimen.m3_alert_dialog_action_bottom_padding),
        )
    val actionTopPadding = dimensionResource(MaterialR.dimen.m3_alert_dialog_action_top_padding)
    val buttonSpacing = dimensionResource(MaterialR.dimen.m3_btn_dialog_btn_spacing)
    val buttonMinWidth = dimensionResource(MaterialR.dimen.m3_btn_dialog_btn_min_width)

    fun confirmIfValid() {
        if (!enabled) return
        submissionAttempted = true
        if (errorMessageFor(input.text) == null) onConfirm(input.text)
    }
    AnkiDroidDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(alertDialogWidthFraction()),
        properties = properties,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Layout(modifier = Modifier.padding(dialogPadding), content = {
                // Match the custom input panel in dialog_versatile_text_preference.xml.
                Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
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
                    OutlinedTextField(
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
                        isError = displayedError != null,
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = actionTopPadding),
                    horizontalArrangement = Arrangement.spacedBy(buttonSpacing, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.widthIn(min = buttonMinWidth),
                    ) { Text(dismissText) }
                    TextButton(
                        onClick = ::confirmIfValid,
                        modifier = Modifier.widthIn(min = buttonMinWidth),
                        enabled = enabled && (!validateOnInput || errorMessage == null),
                    ) { Text(confirmText) }
                }
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

/** @see TextInputDialog */
@DialogPreviews
@Composable
private fun TextInputDialogPreview() {
    AnkiDroidPreview {
        TextInputDialog("Rename deck", "Deck name", "Rename", {}, {}, maxLengthCounter = 50)
    }
}
