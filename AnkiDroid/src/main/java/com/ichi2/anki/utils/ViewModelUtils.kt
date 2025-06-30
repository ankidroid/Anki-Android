/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

enum class InitStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
}

interface ViewModelDelayedInitializer {
    /** Flow to track initialization status */
    val initStatus: MutableStateFlow<InitStatus>

    val scope: CoroutineScope

    // TODO: Docs
    fun delayedInit(block: suspend () -> Unit) {
        scope.launch {
            initStatus.value = InitStatus.IN_PROGRESS
            try {
                Timber.d("init started")
                block()
                Timber.d("init completed")
                initStatus.value = InitStatus.COMPLETED
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize data: ${e.message}")
                initStatus.value = InitStatus.FAILED
            }
        }
    }
}
