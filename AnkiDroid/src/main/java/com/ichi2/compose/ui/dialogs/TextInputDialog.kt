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
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ichi2.anki.R
import com.ichi2.compose.theme.AnkiDroidTheme
import com.ichi2.compose.ui.preview.ThemePreviews

/**
 * Dialog asking the user for a single line of text. Compose counterpart of
 * the View based `AlertDialog.input(...)` helper.
 *
 * In previews the card renders inline, because dialog windows don't show up
 * in the static preview pane.
 *
 * @param onConfirm called with the text, but only while it is valid
 * @param errorMessageFor error to show for the current input, null when valid
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String = stringResource(R.string.dialog_cancel),
    initialText: String = "",
    maxLengthCounter: Int? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    errorMessageFor: (String) -> String? = { null },
) {
    if (LocalInspectionMode.current) {
        TextInputDialogCard(
            title = title,
            label = label,
            confirmText = confirmText,
            onConfirm = onConfirm,
            onDismissRequest = onDismissRequest,
            dismissText = dismissText,
            initialText = initialText,
            maxLengthCounter = maxLengthCounter,
            capitalization = capitalization,
            errorMessageFor = errorMessageFor,
        )
    } else {
        BasicAlertDialog(onDismissRequest = onDismissRequest) {
            TextInputDialogCard(
                title = title,
                label = label,
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismissRequest = onDismissRequest,
                dismissText = dismissText,
                initialText = initialText,
                maxLengthCounter = maxLengthCounter,
                capitalization = capitalization,
                errorMessageFor = errorMessageFor,
            )
        }
    }
}

/** The dialog card itself. Metrics match Material3's own AlertDialog. */
@Composable
private fun TextInputDialogCard(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismissRequest: () -> Unit,
    dismissText: String,
    initialText: String,
    maxLengthCounter: Int?,
    capitalization: KeyboardCapitalization,
    errorMessageFor: (String) -> String?,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    val errorMessage = errorMessageFor(text)
    val displayedError = errorMessage?.takeIf { hasUserEdited }

    fun confirmIfValid() {
        if (errorMessageFor(text) == null) onConfirm(text)
    }

    val focusRequester = remember { FocusRequester() }

    Surface(
        modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = AlertDialogDefaults.titleContentColor,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    hasUserEdited = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                label = { Text(label) },
                isError = displayedError != null,
                supportingText =
                    if (displayedError == null && maxLengthCounter == null) {
                        null
                    } else {
                        {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                if (displayedError != null) {
                                    Text(displayedError, modifier = Modifier.weight(1f))
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                                if (maxLengthCounter != null) {
                                    Text("${text.length}/$maxLengthCounter")
                                }
                            }
                        }
                    },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = capitalization,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { confirmIfValid() }),
            )
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(dismissText)
                }
                TextButton(
                    onClick = ::confirmIfValid,
                    enabled = errorMessage == null,
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

@ThemePreviews
@Composable
private fun TextInputDialogPreview() {
    AnkiDroidTheme {
        TextInputDialog(
            title = "Rename deck",
            label = "Deck name",
            confirmText = "Rename",
            onConfirm = {},
            onDismissRequest = {},
            maxLengthCounter = 50,
        )
    }
}
