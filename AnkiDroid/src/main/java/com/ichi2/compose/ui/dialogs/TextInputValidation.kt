// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Validation feedback and actions shared by the dialog's text field, button and keyboard. */
internal class TextInputValidation(
    val errorMessage: CharSequence?,
    val canConfirm: Boolean,
    val onTextChanged: () -> Unit,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
)

/**
 * Manages validation feedback and confirmation for [TextInputDialog].
 *
 * @param text reads the current input, including edits made while confirmation is suspended
 * @param enabled whether confirmation is allowed; validation can still run when disabled
 * @param validateOnInput whether to run validation before a confirmation attempt
 * @param errorMessageFor synchronous validator; returns an error message or null when valid
 * @param asyncErrorMessageFor optional suspending validator; returns an error message or null when valid.
 *   It can run as the input changes and is rechecked on confirmation.
 * @param validationKey change this when external data used by asynchronous validation changes
 * @param onConfirm called with the validated text. After asynchronous validation, the current text must
 *   still match the submitted text, the validation key must be unchanged, and confirmation must still be enabled.
 * @return feedback and actions for the dialog. Call `onTextChanged` after each text edit.
 */
@Composable
internal fun rememberTextInputValidation(
    text: () -> String,
    enabled: Boolean,
    validateOnInput: Boolean,
    errorMessageFor: (String) -> CharSequence?,
    asyncErrorMessageFor: (suspend (String) -> CharSequence?)?,
    validationKey: Any?,
    onConfirm: (String) -> Unit,
): TextInputValidation {
    var hasUserEdited by rememberSaveable { mutableStateOf(false) }
    var submissionAttempted by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentText by rememberUpdatedState(text)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentAsyncValidator by rememberUpdatedState(asyncErrorMessageFor)
    val currentValidationKey by rememberUpdatedState(validationKey)
    var validatedInput by remember { mutableStateOf<String?>(null) }
    var asyncError by remember { mutableStateOf<CharSequence?>(null) }
    var submitting by remember { mutableStateOf(false) }
    // Async errors are not saveable; recheck a deferred submission after state restoration.
    var restoreDeferredValidation by remember { mutableStateOf(!validateOnInput && submissionAttempted) }
    val input = text()
    val hasAsyncValidator = asyncErrorMessageFor != null
    val syncError = if (validateOnInput || submissionAttempted) errorMessageFor(input) else null
    val pendingValidation = validateOnInput && hasAsyncValidator && syncError == null && validatedInput != input
    val errorMessage = syncError ?: asyncError.takeIf { hasAsyncValidator && validatedInput == input }
    LaunchedEffect(input, syncError, validationKey, validateOnInput, hasAsyncValidator) {
        validatedInput = null
        asyncError = null
        if (hasAsyncValidator && (validateOnInput || (restoreDeferredValidation && submissionAttempted)) && syncError == null) {
            asyncError = currentAsyncValidator?.invoke(input)
            validatedInput = input
        }
        restoreDeferredValidation = false
    }

    fun confirmIfValid() {
        if (!enabled || submitting) return
        submissionAttempted = true
        val submittedText = currentText()
        val submittedValidationKey = currentValidationKey
        if (errorMessageFor(submittedText) != null) return
        val validate = currentAsyncValidator
        if (validate == null) {
            onConfirm(submittedText)
        } else {
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                submitting = true
                try {
                    val error = validate(submittedText)
                    ensureActive()
                    if (currentText() == submittedText && currentValidationKey == submittedValidationKey) {
                        asyncError = error
                        validatedInput = submittedText
                        if (error == null && currentEnabled) currentOnConfirm(submittedText)
                    }
                } finally {
                    submitting = false
                }
            }
        }
    }

    return TextInputValidation(
        errorMessage = errorMessage?.takeIf { hasUserEdited || submissionAttempted },
        canConfirm = enabled && !submitting && !pendingValidation && (!validateOnInput || errorMessage == null),
        onTextChanged = {
            hasUserEdited = true
            submissionAttempted = false
            restoreDeferredValidation = false
        },
        onConfirm = ::confirmIfValid,
        onDismiss = { scope.coroutineContext.cancelChildren() },
    )
}
