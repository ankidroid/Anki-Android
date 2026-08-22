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

/** Progress state observed by the UI. See [ProgressManager] for concurrent-op semantics. */
sealed interface ViewModelProgress {
    data object Idle : ViewModelProgress

    data class Active(
        val message: String? = null,
        val amount: ProgressContext.Amount? = null,
        val cancellable: Boolean = false,
        val formatAmount: (ProgressContext.Amount) -> String =
            { (current, max) -> "$current/$max" },
        val separator: String = " ",
    ) : ViewModelProgress
}
