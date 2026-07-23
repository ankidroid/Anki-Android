/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
                message = text,
                amount = amount,
            )
        },
        block = block,
    )
