// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.progress

import com.ichi2.anki.ProgressContext
import com.ichi2.anki.withProgress
import kotlinx.coroutines.CoroutineScope
import net.ankiweb.rsdroid.Backend

/**
 * Bridges the backend progress polling system into [ProgressScope].
 *
 * @param backend the Anki backend instance to poll for progress
 * @param extractProgress lambda to extract progress data from the backend
 * @param block the operation to execute
 */
suspend fun <T> ProgressScope.withBackendProgress(
    backend: Backend,
    progressContext: ProgressContext = ProgressContext(),
    extractProgress: ProgressContext.() -> Unit,
    block: suspend CoroutineScope.() -> T,
): T =
    backend.withProgress(
        progressContext = progressContext,
        extractProgress = extractProgress,
        updateUi = {
            updateProgress(
                message = text?.let(ProgressText::Raw),
                amount = amount,
            )
        },
        block = block,
    )
