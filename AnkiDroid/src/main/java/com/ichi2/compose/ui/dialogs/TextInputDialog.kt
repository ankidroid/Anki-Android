// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ichi2.anki.CommonString
import com.ichi2.compose.ui.preview.AnkiDroidPreview
import com.ichi2.compose.ui.preview.DialogPreviews

/**
 * Dialog asking the user for a single line of text. Compose counterpart of
 * the View based `AlertDialog.input(...)` helper.
 *
 * @param onConfirm called with the text, but only while it is valid
 * @param initialText initial value shown in the field
 * @param initialSelection initial cursor position or selected range; defaults to the end of [initialText]
 * @param errorMessageFor error to show for the current input, null when valid
 * @param helperText shown when no error is displayed; an empty string reserves feedback space
 * @param transformInput applied to text edits, not cursor-only changes
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
    transformInput: (TextFieldValue) -> TextFieldValue = { it },
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, selection = initialSelection))
    }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    val errorMessage = errorMessageFor(input.text)
    val displayedError = errorMessage?.takeIf { hasUserEdited }
    val supportingMessage = displayedError ?: helperText
    val hasSupportingText = maxLengthCounter != null || supportingMessage != null
    val focusRequester = remember { FocusRequester() }

    fun confirmIfValid() {
        if (errorMessageFor(input.text) == null) onConfirm(input.text)
    }
    AnkiDroidDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = AlertDialogDefaults.titleContentColor,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { proposed ->
                        if (input.text != proposed.text) {
                            input = transformInput(proposed)
                            hasUserEdited = true
                        } else {
                            input = proposed
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text(label) },
                    isError = displayedError != null,
                    supportingText =
                        if (!hasSupportingText) {
                            null
                        } else {
                            {
                                Row(Modifier.fillMaxWidth()) {
                                    if (supportingMessage != null) {
                                        Text(supportingMessage.orEmpty(), Modifier.weight(1f))
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    if (maxLengthCounter != null) Text("${lengthOf(input.text)}/$maxLengthCounter")
                                }
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = if (hasSupportingText) 8.dp else 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismissRequest) { Text(dismissText) }
                    TextButton(
                        onClick = ::confirmIfValid,
                        enabled = errorMessage == null,
                    ) { Text(confirmText) }
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
